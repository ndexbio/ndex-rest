#!/usr/bin/env bash
# NDEx Docker Integration Test
# Usage: ./integration-test.sh [--skip-build] [--remote-ndex-url <url>]
#
#   --skip-build           Skip 'make docker' (reuse existing local image). Only
#                          applies when running against a local container (no --remote-ndex-url).
#   --remote-ndex-url URL  Run API tests against an already-running NDEx instance at URL.
#                          
#
# Validates the full API lifecycle across both v2 and v3 endpoints:
#   user creation → v2 CX1 upload (2 public + 1 private) → v2 summary poll →
#   v3 CX2 retrieve → v3 CX2 upload (2 public + 1 private) → v3 summary poll →
#   v3 CX2 retrieve → private network access control → public anonymous access →
#   v2 Solr search → v3 Solr search → v3 search fq-injection guard →
#   v2 neighborhood query (SSL context)
#
# Exits 0 if all API calls pass, exits 1 on the first failure.
# Deps: docker, make, curl (no python, no jq, no uv)

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
FIXTURES_DIR="${SCRIPT_DIR}/fixtures"
REPO_DIR="$(cd "${SCRIPT_DIR}/../.." && pwd)"
BASE_URL="http://localhost:8080"

CONTAINER_NAME="ndex-integration-test"
TEST_USER="ndextest"
TEST_PASS="NDExTest1!"
TEST_EMAIL="ndextest@ndex-integration.local"
TEST_USER2="ndextest2"
TEST_PASS2="NDExTest2!"
TEST_EMAIL2="ndextest2@ndex-integration.local"

TOTAL_API_CALLS=144
PASSED=0
CALL_NUM=0
STEP_NUM=0
LOAD_TIMEOUT=90

SKIP_BUILD=false
REMOTE_NDEX_URL=""

while [[ $# -gt 0 ]]; do
  case "$1" in
    --skip-build)
      SKIP_BUILD=true; shift ;;
    --remote-ndex-url)
      [[ -n "${2:-}" ]] || { echo "ERROR: --remote-ndex-url requires a URL argument" >&2; exit 1; }
      REMOTE_NDEX_URL="$2"; BASE_URL="${REMOTE_NDEX_URL}"; shift 2 ;;
    *)
      echo "ERROR: Unknown argument: $1" >&2
      echo "Usage: $0 [--skip-build] [--remote-ndex-url <url>]" >&2
      exit 1 ;;
  esac
done

# ── Colors ────────────────────────────────────────────────────────────────────
RED='\033[0;31m'
GREEN='\033[0;32m'
CYAN='\033[0;36m'
BOLD='\033[1m'
NC='\033[0m'

# ── Helpers ───────────────────────────────────────────────────────────────────

step() {
  STEP_NUM=$((STEP_NUM + 1))
  echo ""
  echo -e "${BOLD}=== STEP ${STEP_NUM}: $1 ===${NC}"
}

api_pass() {
  PASSED=$((PASSED + 1))
  echo -e "  ${GREEN}✓ PASS${NC}: $1"
}

api_fail() {
  local reason="$1"
  local remaining=$(( TOTAL_API_CALLS - PASSED ))
  echo ""
  echo -e "  ${RED}✗ FAIL${NC}: ${reason}"
  echo ""
  echo -e "${RED}${BOLD}TEST FAILED${NC}"
  echo -e "  Passed : ${PASSED} / ${TOTAL_API_CALLS}"
  echo -e "  Remaining unrun: ${remaining}"
  echo -e "  Reason : ${reason}"
  exit 1
}

# Poll POST /v3/search/files (as TEST_USER) until the given uuid IS present in the results
# for the given visibility core, or api_fail after LOAD_TIMEOUT. Args: visibility searchString uuid label
poll_files_until_present() {
  local vis="$1" q="$2" uuid="$3" label="$4" elapsed=0 resp http body
  while true; do
    resp=$(curl -s -w "\n%{http_code}" -X POST -u "${TEST_USER}:${TEST_PASS}" \
      -H "Content-Type: application/json" -d "{\"searchString\":\"${q}\"}" \
      "${BASE_URL}/v3/search/files?visibility=${vis}&start=0&size=25")
    http=$(echo "${resp}" | tail -1); body=$(echo "${resp}" | head -1)
    [[ "${http}" == "200" ]] || api_fail "${label}: search visibility=${vis} → HTTP ${http}. Body: ${body:0:300}"
    echo "${body}" | grep -q "${uuid}" && break
    [[ ${elapsed} -ge ${LOAD_TIMEOUT} ]] && api_fail "${label}: uuid ${uuid} not found in visibility=${vis} within ${LOAD_TIMEOUT}s. Body: ${body:0:400}"
    sleep 3; (( elapsed += 3 )) || true
    echo "  Waiting for ${vis}-nfs Solr index... (${elapsed}s)"
  done
}

# Poll until the given uuid is ABSENT from the given visibility core (converges after the
# old-core soft commit), or api_fail after LOAD_TIMEOUT. Args: visibility searchString uuid label
poll_files_until_absent() {
  local vis="$1" q="$2" uuid="$3" label="$4" elapsed=0 resp http body
  while true; do
    resp=$(curl -s -w "\n%{http_code}" -X POST -u "${TEST_USER}:${TEST_PASS}" \
      -H "Content-Type: application/json" -d "{\"searchString\":\"${q}\"}" \
      "${BASE_URL}/v3/search/files?visibility=${vis}&start=0&size=25")
    http=$(echo "${resp}" | tail -1); body=$(echo "${resp}" | head -1)
    [[ "${http}" == "200" ]] || api_fail "${label}: search visibility=${vis} → HTTP ${http}. Body: ${body:0:300}"
    echo "${body}" | grep -q "${uuid}" || break
    [[ ${elapsed} -ge ${LOAD_TIMEOUT} ]] && api_fail "${label}: uuid ${uuid} still present in visibility=${vis} after ${LOAD_TIMEOUT}s (stale/orphaned index entry in old core). Body: ${body:0:400}"
    sleep 3; (( elapsed += 3 )) || true
    echo "  Waiting for ${vis}-nfs Solr drop to converge... (${elapsed}s)"
  done
}

# Assert that a retired group endpoint returns HTTP 501. Always sends valid auth so the
# request passes the auth filter and reaches the (501-throwing) resource method.
# Usage: assert_group_501 <METHOD> <URL> [extra curl args...]
assert_group_501() {
  local method="$1"; local url="$2"; shift 2
  CALL_NUM=$((CALL_NUM+1))
  echo "  API call ${CALL_NUM}/${TOTAL_API_CALLS}: ${method} ${url} (expect 501)"
  local code
  code=$(curl -s -o /dev/null -w "%{http_code}" -X "${method}" -u "${TEST_USER}:${TEST_PASS}" "$@" "${url}")
  if [[ "${code}" == "501" ]]; then
    api_pass "${method} ${url} → 501 (group feature removed)"
  else
    api_fail "${method} ${url} → HTTP ${code} (expected 501)"
  fi
}

# Assert a /v2/networkset endpoint executes and returns the expected success code. The network set
# endpoints are a folder-backed compatibility layer, so every one of them is live; this replaced an
# assert_networkset_501 helper from the releases where the writes were retired.
# Usage: assert_networkset_ok <METHOD> <URL> <EXPECTED_CODE> [extra curl args...]
assert_networkset_ok() {
  local method="$1"; local url="$2"; local expected="$3"; shift 3
  CALL_NUM=$((CALL_NUM+1))
  echo "  API call ${CALL_NUM}/${TOTAL_API_CALLS}: ${method} ${url} (expect ${expected})"
  local code
  code=$(curl -s -o /dev/null -w "%{http_code}" -X "${method}" -u "${TEST_USER}:${TEST_PASS}" "$@" "${url}")
  if [[ "${code}" == "${expected}" ]]; then
    api_pass "${method} ${url} → ${expected}"
  else
    api_fail "${method} ${url} → HTTP ${code} (expected ${expected})"
  fi
}

# Run a single SQL statement against the container's ndex DB and echo the tuples-only result. Used to
# assert storage-level facts an API response cannot show — e.g. that a shortcut row is physically gone
# rather than flagged is_deleted, or that a network's parent really is NULL.
# Usage: psql_ndex "<sql>"
psql_ndex() {
  docker exec "${CONTAINER_NAME}" bash -c "
    DB_USER=\$(grep '^NdexDBUsername=' /apps/ndex/config/ndex.properties | cut -d= -f2-)
    DB_PASS=\$(grep '^NdexDBDBPassword=' /apps/ndex/config/ndex.properties | cut -d= -f2-)
    PGPASSWORD=\"\$DB_PASS\" psql -tA -h 127.0.0.1 -p 5432 -U \"\$DB_USER\" -d ndex -c \"$1\"
  " 2>/dev/null | tr -d '[:space:]'
}

# ── Cleanup trap ──────────────────────────────────────────────────────────────

_remove_test_containers() {
  docker rm -fv "${CONTAINER_NAME}" 2>/dev/null || true
  docker rm -fv "ndex-pg-corrupt-test" 2>/dev/null || true
  docker rm -fv "ndex-pg-wipe-test" 2>/dev/null || true
}

cleanup() {
  [[ -z "${REMOTE_NDEX_URL}" ]] || return 0
  echo ""
  echo -e "${CYAN}=== Cleanup ===${NC}"
  _remove_test_containers
  rm -f "${TMP_CATALINA_TOML:-}"
  if docker inspect "${CONTAINER_NAME}" &>/dev/null; then
    echo -e "  ${RED}WARNING: Container '${CONTAINER_NAME}' still present — manual cleanup may be needed${NC}"
    echo -e "    Run: docker rm -fv ${CONTAINER_NAME}"
  else
    echo -e "  ${GREEN}✓ Test containers removed${NC}"
  fi
}
trap cleanup EXIT

# ── Pre-run cleanup: remove any stale containers from a prior failed run ──────
if [[ -z "${REMOTE_NDEX_URL}" ]]; then
  _remove_test_containers
fi


if [[ -n "${REMOTE_NDEX_URL}" ]]; then
  # ── Remote mode: target already-running NDEx ────────────────────────────────
  echo ""
  echo "  Mode: REMOTE — targeting ${BASE_URL}"
  echo "  Skipping Docker build, container start, and readiness poll."

  step "Checking remote NDEx at ${BASE_URL}"
  MAX_WAIT=60
  ELAPSED=0
  until curl -s -o /dev/null -w "%{http_code}" "${BASE_URL}/v2/user" \
        | grep -qE '^[2-9][0-9]{2}$|^401$'; do
    if [[ ${ELAPSED} -ge ${MAX_WAIT} ]]; then
      api_fail "Remote NDEx at ${BASE_URL} did not respond within ${MAX_WAIT}s"
    fi
    echo -e "  ${CYAN}Waiting for response... (${ELAPSED}s)${NC}"
    sleep 5; ELAPSED=$((ELAPSED + 5))
  done
  echo "  Remote NDEx is responding."
else
  # ── Local container mode ────────────────────────────────────────────────────
  step "Building Docker image"
  if [[ "${SKIP_BUILD}" == "true" ]]; then
    echo "  --skip-build set, skipping make docker"
  else
    echo "  Running: make docker (from ${REPO_DIR})"
    make -C "${REPO_DIR}" docker
    echo "  Image built successfully"
  fi

  step "Starting ephemeral container"
  docker rm -fv "${CONTAINER_NAME}" 2>/dev/null || true
  TMP_CATALINA_TOML=$(mktemp /tmp/ndex-catalina-opts-XXXXXX)
  printf 'ndex_catalina_opts = "-Xms64m -Xmx256m -XX:+ExitOnOutOfMemoryError"\n' \
    > "${TMP_CATALINA_TOML}"
  echo "  Running: docker run -d --name ${CONTAINER_NAME} -p 8080:8080 ..."
  docker run -d \
    --name "${CONTAINER_NAME}" \
    -p 8080:8080 \
    -v "${TMP_CATALINA_TOML}:/tmp/catalina-opts.toml:ro" \
    ndexbio/ndex-rest \
    --ndex --postgres --keycloak --solr --mailhog \
    --config /tmp/catalina-opts.toml
  echo "  Container started (ID: $(docker inspect -f '{{.Id}}' "${CONTAINER_NAME}" | cut -c1-12))"

  step "Waiting for NDEx to be ready"
  MAX_WAIT=120
  ELAPSED=0
  until docker logs "${CONTAINER_NAME}" 2>&1 | grep -q "NDEx Deploy Container Ready"; do
    if [[ ${ELAPSED} -ge ${MAX_WAIT} ]]; then
      echo ""
      echo "  Last 30 lines of container log:"
      docker logs "${CONTAINER_NAME}" 2>&1 | tail -30
      api_fail "Container did not reach Ready state within ${MAX_WAIT}s"
    fi
    echo -e "  ${CYAN}Container initializing... (${ELAPSED}s elapsed)${NC}"
    sleep 5
    ELAPSED=$((ELAPSED + 5))
  done
  echo "  Container is ready!"

  # Assert ndex_catalina_opts from config.toml reached the Tomcat JVM
  CAT_JVM_FLAGS=$(docker exec "${CONTAINER_NAME}" bash -c \
    'cat /proc/$(supervisorctl pid ndex 2>/dev/null)/cmdline 2>/dev/null | tr "\0" "\n"' \
    2>/dev/null || echo "")
  if echo "${CAT_JVM_FLAGS}" | grep -q "Xmx256m"; then
    echo -e "  ${GREEN}✓${NC}: Tomcat JVM contains Xmx256m (ndex_catalina_opts applied)"
  else
    api_fail "ndex_catalina_opts: JVM flags missing Xmx256m. Got: '${CAT_JVM_FLAGS}'"
  fi
fi

# ── STEP: Create test user ────────────────────────────────────────────────────

step "Creating test user"
CALL_NUM=$((CALL_NUM+1))
echo "  API call ${CALL_NUM}/${TOTAL_API_CALLS}: POST /v2/user"

USER_RESPONSE=$(curl -s -w "\n%{http_code}" -X POST "${BASE_URL}/v2/user" \
  -H "Content-Type: application/json" \
  -d "{
    \"userName\": \"${TEST_USER}\",
    \"password\": \"${TEST_PASS}\",
    \"emailAddress\": \"${TEST_EMAIL}\",
    \"firstName\": \"NDEx\",
    \"lastName\": \"Test\"
  }")
USER_HTTP=$(echo "${USER_RESPONSE}" | tail -1)
USER_BODY=$(echo "${USER_RESPONSE}" | head -1)

if [[ "${USER_HTTP}" == "201" ]]; then
  api_pass "POST /v2/user → 201 Created (user: ${TEST_USER})"
elif [[ "${USER_HTTP}" == "409" ]]; then
  api_pass "POST /v2/user → 409 (user: ${TEST_USER} already exists — restart test, data persisted)"
else
  api_fail "POST /v2/user → HTTP ${USER_HTTP} (expected 201). Body: ${USER_BODY:0:300}"
fi

# ── STEP: Verify Basic Auth ───────────────────────────────────────────────────

step "Verifying Basic Auth login"
CALL_NUM=$((CALL_NUM+1))
echo "  API call ${CALL_NUM}/${TOTAL_API_CALLS}: GET /user/authenticate"

AUTH_HTTP=$(curl -s -o /dev/null -w "%{http_code}" \
  -u "${TEST_USER}:${TEST_PASS}" \
  "${BASE_URL}/user/authenticate")

if [[ "${AUTH_HTTP}" == "200" ]]; then
  api_pass "GET /user/authenticate → 200 OK (Basic Auth confirmed)"
else
  api_fail "GET /user/authenticate → HTTP ${AUTH_HTTP} (expected 200)"
fi

# ── STEP: Upload 3 CX1 networks via v2 ───────────────────────────────────────

step "Uploading 3 CX1 networks via POST /v2/network (2 public, 1 private)"

V2_UUIDS=()
V2_PRIV_UUID=""
CX_INDEX=0
for CX_FILE in "${FIXTURES_DIR}"/*.cx; do
  CX_INDEX=$((CX_INDEX + 1))
  NETWORK_LABEL="$(basename "${CX_FILE}")"

  if [[ ${CX_INDEX} -eq 3 ]]; then
    VISIBILITY="PRIVATE"
  else
    VISIBILITY="PUBLIC"
  fi

  CALL_NUM=$((CALL_NUM+1))
  echo "  API call ${CALL_NUM}/${TOTAL_API_CALLS}: POST /v2/network?visibility=${VISIBILITY}  [${NETWORK_LABEL}]"

  UPLOAD_RESPONSE=$(curl -s -w "\n%{http_code}" -X POST \
    -u "${TEST_USER}:${TEST_PASS}" \
    -H "Content-Type: application/json" \
    --data-binary "@${CX_FILE}" \
    "${BASE_URL}/v2/network?visibility=${VISIBILITY}")
  UPLOAD_HTTP=$(echo "${UPLOAD_RESPONSE}" | tail -1)
  UPLOAD_BODY=$(echo "${UPLOAD_RESPONSE}" | head -1)

  if [[ "${UPLOAD_HTTP}" == "201" ]]; then
    UUID=$(echo "${UPLOAD_BODY}" | awk -F/ '{print $NF}')
    V2_UUIDS+=("${UUID}")
    if [[ ${CX_INDEX} -eq 3 ]]; then
      V2_PRIV_UUID="${UUID}"
      api_pass "POST /v2/network → 201 Created (UUID: ${UUID}, PRIVATE)"
    else
      api_pass "POST /v2/network → 201 Created (UUID: ${UUID}, PUBLIC)"
    fi
  else
    api_fail "POST /v2/network → HTTP ${UPLOAD_HTTP} for '${NETWORK_LABEL}'. Body: ${UPLOAD_BODY:0:300}"
  fi
done

# ── STEP: Poll v2 summary until completed:true ────────────────────────────────

step "Polling v2 network summary until all 3 CX1 networks are complete"
echo "  Polling GET /v2/network/{uuid}/summary (Basic Auth) until completed:true..."

for UUID in "${V2_UUIDS[@]}"; do
  ELAPSED=0
  while true; do
    SUMMARY_BODY=$(curl -s -u "${TEST_USER}:${TEST_PASS}" "${BASE_URL}/v2/network/${UUID}/summary")
    if echo "${SUMMARY_BODY}" | grep -q '"completed":true'; then
      echo "  v2 network ${UUID} — completed"
      break
    fi
    if [[ ${ELAPSED} -ge ${LOAD_TIMEOUT} ]]; then
      api_fail "v2 network ${UUID} did not complete within ${LOAD_TIMEOUT}s. Last: ${SUMMARY_BODY:0:300}"
    fi
    echo -e "  ${CYAN}Waiting for v2 network ${UUID}... (${ELAPSED}s)${NC}"
    sleep 5
    ELAPSED=$((ELAPSED + 5))
  done
done
echo "  All 3 v2 networks confirmed complete"

# ── STEP: Retrieve v2 networks via v3 endpoint ───────────────────────────────

step "Retrieving v2-uploaded CX1 networks as CX2 via GET /v3/networks/{uuid}"

for i in "${!V2_UUIDS[@]}"; do
  UUID="${V2_UUIDS[$i]}"
  CALL_NUM=$((CALL_NUM+1))
  echo "  API call ${CALL_NUM}/${TOTAL_API_CALLS}: GET /v3/networks/${UUID}"

  V3_HTTP=$(curl -s -o /dev/null -w "%{http_code}" \
    -u "${TEST_USER}:${TEST_PASS}" \
    "${BASE_URL}/v3/networks/${UUID}")

  if [[ "${V3_HTTP}" == "200" ]]; then
    api_pass "GET /v3/networks/${UUID} → 200 OK (CX2 stream available for v2-uploaded network)"
  else
    api_fail "GET /v3/networks/${UUID} → HTTP ${V3_HTTP} (expected 200)"
  fi
done

# ── STEP: Upload 3 CX2 networks via v3 ───────────────────────────────────────

step "Uploading 3 CX2 networks via POST /v3/networks (2 public, 1 private)"

V3_UUIDS=()
V3_PRIV_UUID=""
CX2_INDEX=0
for CX2_FILE in "${FIXTURES_DIR}"/*.cx2; do
  CX2_INDEX=$((CX2_INDEX + 1))
  NETWORK_LABEL="$(basename "${CX2_FILE}")"

  if [[ ${CX2_INDEX} -eq 3 ]]; then
    VISIBILITY="PRIVATE"
  else
    VISIBILITY="PUBLIC"
  fi

  CALL_NUM=$((CALL_NUM+1))
  echo "  API call ${CALL_NUM}/${TOTAL_API_CALLS}: POST /v3/networks?visibility=${VISIBILITY}  [${NETWORK_LABEL}]"

  UPLOAD_RESPONSE=$(curl -s -w "\n%{http_code}" -X POST \
    -u "${TEST_USER}:${TEST_PASS}" \
    -H "Content-Type: application/json" \
    --data-binary "@${CX2_FILE}" \
    "${BASE_URL}/v3/networks?visibility=${VISIBILITY}")
  UPLOAD_HTTP=$(echo "${UPLOAD_RESPONSE}" | tail -1)
  UPLOAD_BODY=$(echo "${UPLOAD_RESPONSE}" | head -1)

  if [[ "${UPLOAD_HTTP}" == "201" ]]; then
    UUID=$(echo "${UPLOAD_BODY}" | grep -o '"uuid":"[^"]*"' | head -1 | cut -d'"' -f4)
    V3_UUIDS+=("${UUID}")
    if [[ ${CX2_INDEX} -eq 3 ]]; then
      V3_PRIV_UUID="${UUID}"
      api_pass "POST /v3/networks → 201 Created (UUID: ${UUID}, PRIVATE)"
    else
      api_pass "POST /v3/networks → 201 Created (UUID: ${UUID}, PUBLIC)"
    fi
  else
    api_fail "POST /v3/networks → HTTP ${UPLOAD_HTTP} for '${NETWORK_LABEL}'. Body: ${UPLOAD_BODY:0:300}"
  fi
done

# ── STEP: Poll v3 summary until completed:true ───────────────────────────────

step "Polling v3 network summary until all 3 CX2 networks are complete"
echo "  Polling GET /v3/networks/{uuid}/summary (Basic Auth) until completed:true..."

for UUID in "${V3_UUIDS[@]}"; do
  ELAPSED=0
  while true; do
    SUMMARY_BODY=$(curl -s -u "${TEST_USER}:${TEST_PASS}" "${BASE_URL}/v3/networks/${UUID}/summary")
    if echo "${SUMMARY_BODY}" | grep -q '"completed":true'; then
      echo "  v3 network ${UUID} — completed"
      break
    fi
    if [[ ${ELAPSED} -ge ${LOAD_TIMEOUT} ]]; then
      api_fail "v3 network ${UUID} did not complete within ${LOAD_TIMEOUT}s. Last: ${SUMMARY_BODY:0:300}"
    fi
    echo -e "  ${CYAN}Waiting for v3 network ${UUID}... (${ELAPSED}s)${NC}"
    sleep 5
    ELAPSED=$((ELAPSED + 5))
  done
done
echo "  All 3 v3 networks confirmed complete"

# ── STEP: Retrieve v3 networks ───────────────────────────────────────────────

step "Retrieving v3 networks via GET /v3/networks/{uuid}"

for i in "${!V3_UUIDS[@]}"; do
  UUID="${V3_UUIDS[$i]}"
  CALL_NUM=$((CALL_NUM+1))
  echo "  API call ${CALL_NUM}/${TOTAL_API_CALLS}: GET /v3/networks/${UUID}"

  V3_HTTP=$(curl -s -o /dev/null -w "%{http_code}" \
    -u "${TEST_USER}:${TEST_PASS}" \
    "${BASE_URL}/v3/networks/${UUID}")

  if [[ "${V3_HTTP}" == "200" ]]; then
    api_pass "GET /v3/networks/${UUID} → 200 OK (CX2 stream retrieved)"
  else
    api_fail "GET /v3/networks/${UUID} → HTTP ${V3_HTTP} (expected 200)"
  fi
done

# ── STEP: Private network — anonymous access denied ──────────────────────────

step "Asserting anonymous clients cannot retrieve private networks"
echo "  Private v2 network (WP5434): ${V2_PRIV_UUID}"
echo "  Private v3 network (ChEMBL):  ${V3_PRIV_UUID}"

CALL_NUM=$((CALL_NUM+1))
echo "  API call ${CALL_NUM}/${TOTAL_API_CALLS}: GET /v3/networks/${V2_PRIV_UUID} (no auth, expect 401)"
ANON_HTTP=$(curl -s -o /dev/null -w "%{http_code}" "${BASE_URL}/v3/networks/${V2_PRIV_UUID}")
if [[ "${ANON_HTTP}" == "401" ]]; then
  api_pass "GET /v3/networks/${V2_PRIV_UUID} (anon) → 401 Unauthorized (private v2 network blocked)"
else
  api_fail "GET /v3/networks/${V2_PRIV_UUID} (anon) → HTTP ${ANON_HTTP} (expected 401 for private network)"
fi

CALL_NUM=$((CALL_NUM+1))
echo "  API call ${CALL_NUM}/${TOTAL_API_CALLS}: GET /v3/networks/${V3_PRIV_UUID} (no auth, expect 401)"
ANON_HTTP=$(curl -s -o /dev/null -w "%{http_code}" "${BASE_URL}/v3/networks/${V3_PRIV_UUID}")
if [[ "${ANON_HTTP}" == "401" ]]; then
  api_pass "GET /v3/networks/${V3_PRIV_UUID} (anon) → 401 Unauthorized (private v3 network blocked)"
else
  api_fail "GET /v3/networks/${V3_PRIV_UUID} (anon) → HTTP ${ANON_HTTP} (expected 401 for private network)"
fi

CALL_NUM=$((CALL_NUM+1))
echo "  API call ${CALL_NUM}/${TOTAL_API_CALLS}: GET /v2/network/${V2_PRIV_UUID}/summary (no auth, expect 401)"
ANON_HTTP=$(curl -s -o /dev/null -w "%{http_code}" "${BASE_URL}/v2/network/${V2_PRIV_UUID}/summary")
if [[ "${ANON_HTTP}" == "401" ]]; then
  api_pass "GET /v2/network/${V2_PRIV_UUID}/summary (anon) → 401 Unauthorized (private v2 summary blocked)"
else
  api_fail "GET /v2/network/${V2_PRIV_UUID}/summary (anon) → HTTP ${ANON_HTTP} (expected 401 for private network)"
fi

CALL_NUM=$((CALL_NUM+1))
echo "  API call ${CALL_NUM}/${TOTAL_API_CALLS}: GET /v3/networks/${V3_PRIV_UUID}/summary (no auth, expect 401)"
ANON_HTTP=$(curl -s -o /dev/null -w "%{http_code}" "${BASE_URL}/v3/networks/${V3_PRIV_UUID}/summary")
if [[ "${ANON_HTTP}" == "401" ]]; then
  api_pass "GET /v3/networks/${V3_PRIV_UUID}/summary (anon) → 401 Unauthorized (private v3 summary blocked)"
else
  api_fail "GET /v3/networks/${V3_PRIV_UUID}/summary (anon) → HTTP ${ANON_HTTP} (expected 401 for private network)"
fi

# ── STEP: Public network — anonymous access allowed ──────────────────────────

step "Asserting anonymous clients can retrieve public networks"

V2_PUB_UUID="${V2_UUIDS[0]}"
V3_PUB_UUID="${V3_UUIDS[0]}"

CALL_NUM=$((CALL_NUM+1))
echo "  API call ${CALL_NUM}/${TOTAL_API_CALLS}: GET /v3/networks/${V2_PUB_UUID} (no auth, expect 200)"
ANON_HTTP=$(curl -s -o /dev/null -w "%{http_code}" "${BASE_URL}/v3/networks/${V2_PUB_UUID}")
if [[ "${ANON_HTTP}" == "200" ]]; then
  api_pass "GET /v3/networks/${V2_PUB_UUID} (anon) → 200 OK (public v2 network accessible)"
else
  api_fail "GET /v3/networks/${V2_PUB_UUID} (anon) → HTTP ${ANON_HTTP} (expected 200 for public network)"
fi

CALL_NUM=$((CALL_NUM+1))
echo "  API call ${CALL_NUM}/${TOTAL_API_CALLS}: GET /v3/networks/${V3_PUB_UUID} (no auth, expect 200)"
ANON_HTTP=$(curl -s -o /dev/null -w "%{http_code}" "${BASE_URL}/v3/networks/${V3_PUB_UUID}")
if [[ "${ANON_HTTP}" == "200" ]]; then
  api_pass "GET /v3/networks/${V3_PUB_UUID} (anon) → 200 OK (public v3 network accessible)"
else
  api_fail "GET /v3/networks/${V3_PUB_UUID} (anon) → HTTP ${ANON_HTTP} (expected 200 for public network)"
fi

# ── STEP: Private network — authenticated owner access allowed ───────────────

step "Asserting authenticated owner can retrieve their private networks"

CALL_NUM=$((CALL_NUM+1))
echo "  API call ${CALL_NUM}/${TOTAL_API_CALLS}: GET /v3/networks/${V2_PRIV_UUID} (auth, expect 200)"
AUTH_HTTP=$(curl -s -o /dev/null -w "%{http_code}" \
  -u "${TEST_USER}:${TEST_PASS}" \
  "${BASE_URL}/v3/networks/${V2_PRIV_UUID}")
if [[ "${AUTH_HTTP}" == "200" ]]; then
  api_pass "GET /v3/networks/${V2_PRIV_UUID} (auth) → 200 OK (owner can retrieve private v2 network)"
else
  api_fail "GET /v3/networks/${V2_PRIV_UUID} (auth) → HTTP ${AUTH_HTTP} (expected 200 for owner)"
fi

CALL_NUM=$((CALL_NUM+1))
echo "  API call ${CALL_NUM}/${TOTAL_API_CALLS}: GET /v3/networks/${V3_PRIV_UUID} (auth, expect 200)"
AUTH_HTTP=$(curl -s -o /dev/null -w "%{http_code}" \
  -u "${TEST_USER}:${TEST_PASS}" \
  "${BASE_URL}/v3/networks/${V3_PRIV_UUID}")
if [[ "${AUTH_HTTP}" == "200" ]]; then
  api_pass "GET /v3/networks/${V3_PRIV_UUID} (auth) → 200 OK (owner can retrieve private v3 network)"
else
  api_fail "GET /v3/networks/${V3_PRIV_UUID} (auth) → HTTP ${AUTH_HTTP} (expected 200 for owner)"
fi

# ── STEP: v2 Solr search ─────────────────────────────────────────────────────

step "Searching v2 networks via POST /v2/search/network"

CALL_NUM=$((CALL_NUM+1))
echo "  API call ${CALL_NUM}/${TOTAL_API_CALLS}: POST /v2/search/network?searchString=WP1984 (anon, expect 200 + UUID)"

# Poll until the UUID appears — defensive against any Solr commit latency.
ELAPSED=0
SEARCH_BODY=""
SEARCH_HTTP=""
while true; do
  SEARCH_RESPONSE=$(curl -s -w "\n%{http_code}" -X POST \
    -H "Content-Type: application/json" \
    -d '{"searchString":"WP1984"}' \
    "${BASE_URL}/v2/search/network?start=0&size=10")
  SEARCH_HTTP=$(echo "${SEARCH_RESPONSE}" | tail -1)
  SEARCH_BODY=$(echo "${SEARCH_RESPONSE}" | head -1)
  if [[ "${SEARCH_HTTP}" != "200" ]]; then
    api_fail "POST /v2/search/network → HTTP ${SEARCH_HTTP} (expected 200). Body: ${SEARCH_BODY:0:300}"
  fi
  if echo "${SEARCH_BODY}" | grep -q "${V2_UUIDS[0]}"; then
    break
  fi
  if [[ ${ELAPSED} -ge ${LOAD_TIMEOUT} ]]; then
    api_fail "POST /v2/search/network → 200 OK but UUID ${V2_UUIDS[0]} not found within ${LOAD_TIMEOUT}s. Body: ${SEARCH_BODY:0:500}"
  fi
  sleep 3; (( ELAPSED += 3 )) || true
  echo "  Waiting for ndex-networks Solr index... (${ELAPSED}s)"
done
api_pass "POST /v2/search/network → 200 OK, WP1984 UUID found in results (Solr reindex confirmed)"

# ── STEP: v3 Solr search ─────────────────────────────────────────────────────

step "Searching v3-uploaded CX2 networks via POST /v3/search/files (authenticated)"

# V3_UUIDS[0] = BindingDB (first public CX2 network). Use the new v3 global search endpoint
# which queries public-nfs directly. Requires authentication.
CALL_NUM=$((CALL_NUM+1))
echo "  API call ${CALL_NUM}/${TOTAL_API_CALLS}: POST /v3/search/files?visibility=PUBLIC (auth, expect 200 + UUID)"

# Poll until the UUID appears — public-nfs Solr commit can be async (especially
# with bind-mounted data directories where host filesystem I/O adds latency).
ELAPSED=0
SEARCH_BODY=""
SEARCH_HTTP=""
while true; do
  SEARCH_RESPONSE=$(curl -s -w "\n%{http_code}" -X POST \
    -u "${TEST_USER}:${TEST_PASS}" \
    -H "Content-Type: application/json" \
    -d '{"searchString":"BindingDB"}' \
    "${BASE_URL}/v3/search/files?visibility=PUBLIC&start=0&size=10")
  SEARCH_HTTP=$(echo "${SEARCH_RESPONSE}" | tail -1)
  SEARCH_BODY=$(echo "${SEARCH_RESPONSE}" | head -1)
  if [[ "${SEARCH_HTTP}" != "200" ]]; then
    api_fail "POST /v3/search/files (BindingDB) → HTTP ${SEARCH_HTTP} (expected 200). Body: ${SEARCH_BODY:0:300}"
  fi
  if echo "${SEARCH_BODY}" | grep -q "${V3_UUIDS[0]}"; then
    break
  fi
  if [[ ${ELAPSED} -ge ${LOAD_TIMEOUT} ]]; then
    api_fail "POST /v3/search/files (BindingDB) → 200 OK but UUID ${V3_UUIDS[0]} not found within ${LOAD_TIMEOUT}s. Body: ${SEARCH_BODY:0:500}"
  fi
  sleep 3; (( ELAPSED += 3 )) || true
  echo "  Waiting for public-nfs Solr index... (${ELAPSED}s)"
done
api_pass "POST /v3/search/files → 200 OK, BindingDB UUID found in results (CX2 public-nfs confirmed)"

# ── STEP: v3 /search/files neutralizes Solr filter injection (F4) ────────────
# A crafted accountName that tries to OR-in a match-all clause must be escaped so it
# cannot widen results past the owner filter. BindingDB (public, confirmed indexed by
# the previous step) must NOT appear: the escaped accountName is a single literal,
# non-existent owner. A regression (unescaped value) would collapse the filter to *:*
# and leak BindingDB. The escaped query must also stay valid Solr syntax (HTTP 200).
step "Verifying v3 /search/files neutralizes Solr filter injection (accountName)"

INJECT_BODY='{"searchString":"*:*","accountName":"zzz\") OR (*:*) OR (owner:\"zzz"}'

CALL_NUM=$((CALL_NUM+1))
echo "  API call ${CALL_NUM}/${TOTAL_API_CALLS}: POST /v3/search/files (auth, accountName injection, expect 200 + BindingDB absent)"
INJ_RESP=$(curl -s -w "\n%{http_code}" -X POST \
  -u "${TEST_USER}:${TEST_PASS}" \
  -H "Content-Type: application/json" \
  -d "${INJECT_BODY}" \
  "${BASE_URL}/v3/search/files?visibility=PUBLIC&start=0&size=10")
INJ_HTTP=$(echo "${INJ_RESP}" | tail -1)
INJ_BODY=$(echo "${INJ_RESP}" | head -1)
if [[ "${INJ_HTTP}" != "200" ]]; then
  api_fail "POST /v3/search/files (accountName injection, auth) → HTTP ${INJ_HTTP} (expected 200; escaped value must remain valid Solr syntax). Body: ${INJ_BODY:0:300}"
fi
if echo "${INJ_BODY}" | grep -q "${V3_UUIDS[0]}"; then
  api_fail "POST /v3/search/files (accountName injection, auth) → 200 but BindingDB UUID ${V3_UUIDS[0]} leaked — injection widened results to *:* (fq not escaped). Body: ${INJ_BODY:0:400}"
fi
api_pass "POST /v3/search/files (accountName injection, auth) → 200 OK, no result widening (fq injection neutralized)"

CALL_NUM=$((CALL_NUM+1))
echo "  API call ${CALL_NUM}/${TOTAL_API_CALLS}: POST /v3/search/files (anon, accountName injection, expect 200 + BindingDB absent)"
INJ_ANON_RESP=$(curl -s -w "\n%{http_code}" -X POST \
  -H "Content-Type: application/json" \
  -d "${INJECT_BODY}" \
  "${BASE_URL}/v3/search/files?visibility=PUBLIC&start=0&size=10")
INJ_ANON_HTTP=$(echo "${INJ_ANON_RESP}" | tail -1)
INJ_ANON_BODY=$(echo "${INJ_ANON_RESP}" | head -1)
if [[ "${INJ_ANON_HTTP}" != "200" ]]; then
  api_fail "POST /v3/search/files (accountName injection, anon) → HTTP ${INJ_ANON_HTTP} (expected 200). Body: ${INJ_ANON_BODY:0:300}"
fi
if echo "${INJ_ANON_BODY}" | grep -q "${V3_UUIDS[0]}"; then
  api_fail "POST /v3/search/files (accountName injection, anon) → 200 but BindingDB leaked — injection widened results. Body: ${INJ_ANON_BODY:0:400}"
fi
api_pass "POST /v3/search/files (accountName injection, anon) → 200 OK, no result widening"

# ── STEP: Edgeless network search ranking (issue #116) ───────────────────────
# Upload two networks that share the unique token "EdgelessRankProbe" — one WITH
# edges, one edgeless (0 edges, but a deliberately STRONGER text match). Without
# the edgeCount demotion boost the edgeless network would rank first on text
# relevance; the boost must push the edged network above it.

step "Verifying edgeless networks are demoted in search ranking (issue #116)"

RANK_DIR="${FIXTURES_DIR}/ranking"
RANK_EDGED_UUID=""
RANK_EDGELESS_UUID=""

for RANK_FILE in "${RANK_DIR}/edged-rank-probe.cx2" "${RANK_DIR}/edgeless-rank-probe.cx2"; do
  RANK_LABEL="$(basename "${RANK_FILE}")"
  CALL_NUM=$((CALL_NUM+1))
  echo "  API call ${CALL_NUM}/${TOTAL_API_CALLS}: POST /v3/networks?visibility=PUBLIC  [${RANK_LABEL}]"

  RANK_UPLOAD=$(curl -s -w "\n%{http_code}" -X POST \
    -u "${TEST_USER}:${TEST_PASS}" \
    -H "Content-Type: application/json" \
    --data-binary "@${RANK_FILE}" \
    "${BASE_URL}/v3/networks?visibility=PUBLIC")
  RANK_HTTP=$(echo "${RANK_UPLOAD}" | tail -1)
  RANK_BODY=$(echo "${RANK_UPLOAD}" | head -1)

  if [[ "${RANK_HTTP}" != "201" ]]; then
    api_fail "POST /v3/networks → HTTP ${RANK_HTTP} for '${RANK_LABEL}'. Body: ${RANK_BODY:0:300}"
  fi
  RANK_UUID=$(echo "${RANK_BODY}" | grep -o '"uuid":"[^"]*"' | head -1 | cut -d'"' -f4)
  if [[ "${RANK_LABEL}" == "edged-rank-probe.cx2" ]]; then
    RANK_EDGED_UUID="${RANK_UUID}"
  else
    RANK_EDGELESS_UUID="${RANK_UUID}"
  fi
  api_pass "POST /v3/networks → 201 Created (UUID: ${RANK_UUID}, ${RANK_LABEL})"
done

# Wait until both rank-probe networks finish processing.
for UUID in "${RANK_EDGED_UUID}" "${RANK_EDGELESS_UUID}"; do
  ELAPSED=0
  while true; do
    SUMMARY_BODY=$(curl -s -u "${TEST_USER}:${TEST_PASS}" "${BASE_URL}/v3/networks/${UUID}/summary")
    if echo "${SUMMARY_BODY}" | grep -q '"completed":true'; then
      break
    fi
    if [[ ${ELAPSED} -ge ${LOAD_TIMEOUT} ]]; then
      api_fail "rank-probe network ${UUID} did not complete within ${LOAD_TIMEOUT}s. Last: ${SUMMARY_BODY:0:300}"
    fi
    sleep 5; (( ELAPSED += 5 )) || true
    echo "  Waiting for rank-probe network ${UUID}... (${ELAPSED}s)"
  done
done

CALL_NUM=$((CALL_NUM+1))
echo "  API call ${CALL_NUM}/${TOTAL_API_CALLS}: POST /v3/search/files?visibility=PUBLIC (searchString=EdgelessRankProbe)"

# Poll until both networks are indexed, then assert the edged network ranks first.
ELAPSED=0
while true; do
  RANK_SEARCH=$(curl -s -w "\n%{http_code}" -X POST \
    -u "${TEST_USER}:${TEST_PASS}" \
    -H "Content-Type: application/json" \
    -d '{"searchString":"EdgelessRankProbe"}' \
    "${BASE_URL}/v3/search/files?visibility=PUBLIC&start=0&size=10")
  RANK_SEARCH_HTTP=$(echo "${RANK_SEARCH}" | tail -1)
  RANK_SEARCH_BODY=$(echo "${RANK_SEARCH}" | head -1)
  if [[ "${RANK_SEARCH_HTTP}" != "200" ]]; then
    api_fail "POST /v3/search/files (EdgelessRankProbe) → HTTP ${RANK_SEARCH_HTTP}. Body: ${RANK_SEARCH_BODY:0:300}"
  fi
  # Ordered list of result UUIDs (rank order is preserved by the search provider).
  # `|| true` keeps a "no match yet" grep (exit 1) from tripping `set -e`/pipefail
  # while the just-uploaded networks are still being indexed.
  RANK_ORDER=$(echo "${RANK_SEARCH_BODY}" | grep -oE '"uuid":"[^"]*"' | cut -d'"' -f4 || true)
  EDGED_POS=$(echo "${RANK_ORDER}" | grep -n "^${RANK_EDGED_UUID}$" | head -1 | cut -d: -f1 || true)
  EDGELESS_POS=$(echo "${RANK_ORDER}" | grep -n "^${RANK_EDGELESS_UUID}$" | head -1 | cut -d: -f1 || true)
  if [[ -n "${EDGED_POS}" && -n "${EDGELESS_POS}" ]]; then
    break
  fi
  if [[ ${ELAPSED} -ge ${LOAD_TIMEOUT} ]]; then
    api_fail "Both rank-probe networks not found in search within ${LOAD_TIMEOUT}s. Body: ${RANK_SEARCH_BODY:0:500}"
  fi
  sleep 3; (( ELAPSED += 3 )) || true
  echo "  Waiting for rank-probe networks to index... (${ELAPSED}s)"
done

if [[ ${EDGED_POS} -lt ${EDGELESS_POS} ]]; then
  api_pass "Edged network (pos ${EDGED_POS}) ranks above edgeless network (pos ${EDGELESS_POS}) — edgeless demotion confirmed"
else
  api_fail "Edgeless network (pos ${EDGELESS_POS}) ranked at/above edged network (pos ${EDGED_POS}) — edgeCount boost not applied"
fi

# ── STEP: Neighborhood query — SSL context fix (local container only) ─────────

if [[ -z "${REMOTE_NDEX_URL}" ]]; then
  step "Verifying neighborhood query endpoint initializes SSL context correctly"

  echo "  Injecting NeighborhoodQueryURL into ndex.properties and starting mock stub..."
  docker exec "${CONTAINER_NAME}" bash -c \
    "echo 'NeighborhoodQueryURL=http://localhost:8284/query/v1/network/' >> /apps/ndex/config/ndex.properties"

  # Node.js stub: drains the request body before responding (avoids RST),
  # handles multiple connections without re-arming, no race between v2 and v3 calls.
  docker exec -d "${CONTAINER_NAME}" node -e "
const http = require('http');
http.createServer((req, res) => {
  req.resume();
  req.on('end', () => {
    res.writeHead(200, {'Content-Type': 'application/json', 'Content-Length': '2'});
    res.end('[]');
  });
}).listen(8284);
"

  docker exec "${CONTAINER_NAME}" supervisorctl restart ndex

  echo "  Tomcat restart issued — waiting for NDEx to become responsive..."
  MAX_WAIT=90
  ELAPSED=0
  until curl -s -o /dev/null -w "%{http_code}" "${BASE_URL}/v2/user" \
        | grep -qE '^[2-9][0-9]{2}$|^401$|^400$'; do
    if [[ ${ELAPSED} -ge ${MAX_WAIT} ]]; then
      api_fail "NDEx did not respond within ${MAX_WAIT}s after Tomcat restart"
    fi
    echo -e "  ${CYAN}Waiting for Tomcat restart... (${ELAPSED}s)${NC}"
    sleep 5; (( ELAPSED += 5 )) || true
  done
  echo "  Tomcat is ready."

  CALL_NUM=$((CALL_NUM+1))
  QUERY_UUID="${V2_UUIDS[0]}"
  echo "  API call ${CALL_NUM}/${TOTAL_API_CALLS}: POST /v2/search/network/${QUERY_UUID}/query (auth, expect 200)"

  QUERY_RESPONSE=$(curl -s -w "\n%{http_code}" -X POST \
    -u "${TEST_USER}:${TEST_PASS}" \
    -H "Content-Type: application/json" \
    -d '{"searchString":"EGFR","searchDepth":1}' \
    "${BASE_URL}/v2/search/network/${QUERY_UUID}/query")
  QUERY_HTTP=$(echo "${QUERY_RESPONSE}" | tail -1)
  QUERY_BODY=$(echo "${QUERY_RESPONSE}" | head -1)

  if [[ "${QUERY_HTTP}" == "200" ]]; then
    api_pass "POST /v2/search/network/${QUERY_UUID}/query → 200 OK (SSL context initialized, stub proxied correctly)"
  else
    api_fail "POST /v2/search/network/${QUERY_UUID}/query → HTTP ${QUERY_HTTP}. Body: ${QUERY_BODY:0:400}"
  fi

  CALL_NUM=$((CALL_NUM+1))
  QUERY_UUID_V3="${V3_UUIDS[0]}"
  echo "  API call ${CALL_NUM}/${TOTAL_API_CALLS}: POST /v3/search/networks/${QUERY_UUID_V3}/query (auth, expect 200)"

  QUERY_V3_RESPONSE=$(curl -s -w "\n%{http_code}" -X POST \
    -u "${TEST_USER}:${TEST_PASS}" \
    -H "Content-Type: application/json" \
    -d '{"searchString":"EGFR","searchDepth":1}' \
    "${BASE_URL}/v3/search/networks/${QUERY_UUID_V3}/query")
  QUERY_V3_HTTP=$(echo "${QUERY_V3_RESPONSE}" | tail -1)
  QUERY_V3_BODY=$(echo "${QUERY_V3_RESPONSE}" | head -1)

  if [[ "${QUERY_V3_HTTP}" == "200" ]]; then
    api_pass "POST /v3/search/networks/${QUERY_UUID_V3}/query → 200 OK (SSL context initialized, stub proxied correctly)"
  else
    api_fail "POST /v3/search/networks/${QUERY_UUID_V3}/query → HTTP ${QUERY_V3_HTTP}. Body: ${QUERY_V3_BODY:0:400}"
  fi
fi

# ── STEP: Reindex endpoint clears prior index error ───────────────────────────

if [[ -z "${REMOTE_NDEX_URL}" ]]; then
  step "Reindex endpoint clears prior index error from network"

  # Inject an index-failure error directly into the DB (simulates a prior failed reindex)
  docker exec "${CONTAINER_NAME}" bash -c "
    DB_USER=\$(grep '^NdexDBUsername=' /apps/ndex/config/ndex.properties | cut -d= -f2-)
    DB_PASS=\$(grep '^NdexDBDBPassword=' /apps/ndex/config/ndex.properties | cut -d= -f2-)
    PGPASSWORD=\"\$DB_PASS\" psql -h 127.0.0.1 -p 5432 -U \"\$DB_USER\" -d ndex \
      -c \"UPDATE network SET error = 'Failed to create Index on network. Cause: test' WHERE \\\"UUID\\\" = '${V3_UUIDS[0]}'\"
  "

  CALL_NUM=$((CALL_NUM+1))
  echo "  API call ${CALL_NUM}/${TOTAL_API_CALLS}: GET /v3/networks/${V3_UUIDS[0]}/summary (expect errorMessage set)"
  PRE_RESP=$(curl -s -w "\n%{http_code}" -u "${TEST_USER}:${TEST_PASS}" \
    "${BASE_URL}/v3/networks/${V3_UUIDS[0]}/summary")
  PRE_HTTP=$(echo "${PRE_RESP}" | tail -1)
  PRE_BODY=$(echo "${PRE_RESP}" | head -1)
  if [[ "${PRE_HTTP}" == "200" ]] && echo "${PRE_BODY}" | grep -q "Failed to create Index"; then
    api_pass "GET /v3/networks/${V3_UUIDS[0]}/summary → 200 OK, errorMessage contains index failure text"
  else
    api_fail "GET /v3/networks/${V3_UUIDS[0]}/summary → HTTP ${PRE_HTTP}. Expected errorMessage with index failure. Body: ${PRE_BODY:0:400}"
  fi

  CALL_NUM=$((CALL_NUM+1))
  echo "  API call ${CALL_NUM}/${TOTAL_API_CALLS}: GET /v3/admin/reindex-v3?password=changeme (expect 200)"
  REINDEX_HTTP=$(curl -s -o /dev/null -w "%{http_code}" \
    "${BASE_URL}/v3/admin/reindex-v3?password=changeme")
  if [[ "${REINDEX_HTTP}" == "200" ]]; then
    api_pass "GET /v3/admin/reindex-v3 → 200 OK"
  else
    api_fail "GET /v3/admin/reindex-v3 → HTTP ${REINDEX_HTTP} (expected 200)"
  fi

  CALL_NUM=$((CALL_NUM+1))
  echo "  API call ${CALL_NUM}/${TOTAL_API_CALLS}: GET /v3/networks/${V3_UUIDS[0]}/summary (expect errorMessage cleared)"
  POST_RESP=$(curl -s -w "\n%{http_code}" -u "${TEST_USER}:${TEST_PASS}" \
    "${BASE_URL}/v3/networks/${V3_UUIDS[0]}/summary")
  POST_HTTP=$(echo "${POST_RESP}" | tail -1)
  POST_BODY=$(echo "${POST_RESP}" | head -1)
  if [[ "${POST_HTTP}" == "200" ]] && ! echo "${POST_BODY}" | grep -q "Failed to create Index"; then
    api_pass "GET /v3/networks/${V3_UUIDS[0]}/summary → 200 OK, errorMessage cleared after successful reindex"
  else
    api_fail "GET /v3/networks/${V3_UUIDS[0]}/summary → HTTP ${POST_HTTP}. errorMessage was not cleared. Body: ${POST_BODY:0:400}"
  fi
fi

# ── STEP: unlist-public-none converts PUBLIC/NONE networks to UNLISTED ────────

if [[ -z "${REMOTE_NDEX_URL}" ]]; then
  step "SolrIndexBuilder unlist-public-none converts PUBLIC+NONE networks to UNLISTED"

  # Force the BindingDB network to solr_idx_lvl='NONE' so it is a candidate.
  docker exec "${CONTAINER_NAME}" bash -c "
    DB_USER=\$(grep '^NdexDBUsername=' /apps/ndex/config/ndex.properties | cut -d= -f2-)
    DB_PASS=\$(grep '^NdexDBDBPassword=' /apps/ndex/config/ndex.properties | cut -d= -f2-)
    PGPASSWORD=\"\$DB_PASS\" psql -h 127.0.0.1 -p 5432 -U \"\$DB_USER\" -d ndex \
      -c \"UPDATE network SET solr_idx_lvl = 'NONE' WHERE \\\"UUID\\\" = '${V3_PUB_UUID}'\"
  "

  docker exec "${CONTAINER_NAME}" bash -c "
    ndexConfigurationPath=/apps/ndex/config/ndex.properties \
    java -cp '/usr/local/tomcat/webapps/ROOT/WEB-INF/lib/*:/usr/local/tomcat/webapps/ROOT/WEB-INF/classes' \
      org.ndexbio.common.solr.SolrIndexBuilder unlist-public-none
  "

  # Assert DB row was flipped to UNLISTED.
  DB_VISIBILITY=$(docker exec "${CONTAINER_NAME}" bash -c "
    DB_USER=\$(grep '^NdexDBUsername=' /apps/ndex/config/ndex.properties | cut -d= -f2-)
    DB_PASS=\$(grep '^NdexDBDBPassword=' /apps/ndex/config/ndex.properties | cut -d= -f2-)
    PGPASSWORD=\"\$DB_PASS\" psql -h 127.0.0.1 -p 5432 -U \"\$DB_USER\" -d ndex -tA \
      -c \"SELECT visibility FROM network WHERE \\\"UUID\\\" = '${V3_PUB_UUID}'\"
  ")
  if [[ "${DB_VISIBILITY}" == "UNLISTED" ]]; then
    echo "  DB check passed: visibility='UNLISTED' for network ${V3_PUB_UUID}"
  else
    api_fail "DB check failed: expected visibility='UNLISTED' but got '${DB_VISIBILITY}' for network ${V3_PUB_UUID}"
  fi

  CALL_NUM=$((CALL_NUM+1))
  echo "  API call ${CALL_NUM}/${TOTAL_API_CALLS}: GET /v3/networks/${V3_PUB_UUID}/summary (auth, expect UNLISTED)"
  SUMM_RESP=$(curl -s -w "\n%{http_code}" -u "${TEST_USER}:${TEST_PASS}" \
    "${BASE_URL}/v3/networks/${V3_PUB_UUID}/summary")
  SUMM_HTTP=$(echo "${SUMM_RESP}" | tail -1)
  SUMM_BODY=$(echo "${SUMM_RESP}" | head -1)
  if [[ "${SUMM_HTTP}" == "200" ]] && echo "${SUMM_BODY}" | grep -q '"UNLISTED"'; then
    api_pass "GET /v3/networks/${V3_PUB_UUID}/summary (auth) → 200 OK, visibility=UNLISTED"
  else
    api_fail "GET /v3/networks/${V3_PUB_UUID}/summary (auth) → HTTP ${SUMM_HTTP}. Expected visibility=UNLISTED. Body: ${SUMM_BODY:0:400}"
  fi

  # Verify Solr was re-indexed: an anonymous PUBLIC search must no longer find this UUID.
  # Authenticated owners still see their own UNLISTED networks (userAdmin filter), so
  # anonymous is the right caller — it uses the pure "exclude UNLISTED" Solr filter.
  CALL_NUM=$((CALL_NUM+1))
  echo "  API call ${CALL_NUM}/${TOTAL_API_CALLS}: POST /v3/search/files?visibility=PUBLIC (anon, expect UUID absent — Solr doc updated to UNLISTED)"
  SEARCH_RESP=$(curl -s -w "\n%{http_code}" \
    -X POST -H "Content-Type: application/json" \
    -d "{\"searchString\":\"BindingDB\"}" \
    "${BASE_URL}/v3/search/files?visibility=PUBLIC")
  SEARCH_HTTP=$(echo "${SEARCH_RESP}" | tail -1)
  SEARCH_BODY=$(echo "${SEARCH_RESP}" | head -1)
  if [[ "${SEARCH_HTTP}" == "200" ]] && ! echo "${SEARCH_BODY}" | grep -q "${V3_PUB_UUID}"; then
    api_pass "POST /v3/search/files?visibility=PUBLIC (anon) → 200 OK, UUID absent (Solr doc updated to UNLISTED)"
  else
    api_fail "POST /v3/search/files?visibility=PUBLIC (anon) → HTTP ${SEARCH_HTTP}, UUID still present (Solr re-index did not run or visibility field not updated). Body: ${SEARCH_BODY:0:400}"
  fi

# ── STEP: Readability cardinality regression on batch network summary ───────

step "Asserting batch summary readability returns exactly one row for anon and authenticated non-owner"

CALL_NUM=$((CALL_NUM+1))
echo "  API call ${CALL_NUM}/${TOTAL_API_CALLS}: POST /v2/user (auth as ${TEST_USER}) to ensure ${TEST_USER2} exists"
AUTH_CREATE_HTTP=$(curl -s -o /dev/null -w "%{http_code}" -X POST \
  -u "${TEST_USER}:${TEST_PASS}" \
  -H "Content-Type: application/json" \
  -d "{\"userName\":\"${TEST_USER2}\",\"password\":\"${TEST_PASS2}\",\"emailAddress\":\"${TEST_EMAIL2}\",\"firstName\":\"NDEx\",\"lastName\":\"Test2\"}" \
  "${BASE_URL}/v2/user")
if [[ "${AUTH_CREATE_HTTP}" == "201" || "${AUTH_CREATE_HTTP}" == "409" ]]; then
  api_pass "POST /v2/user (auth) → ${AUTH_CREATE_HTTP} (secondary test user is available)"
else
  api_fail "POST /v2/user (auth) → HTTP ${AUTH_CREATE_HTTP} (expected 201 or 409)"
fi

BATCH_REQ="[\"${V2_UUIDS[0]}\",\"${V2_PRIV_UUID}\"]"

CALL_NUM=$((CALL_NUM+1))
echo "  API call ${CALL_NUM}/${TOTAL_API_CALLS}: POST /v2/batch/network/summary (anon, expect exactly 1 row)"
BATCH_RESPONSE=$(curl -s -w "\n%{http_code}" -X POST \
  -H "Content-Type: application/json" \
  -d "${BATCH_REQ}" \
  "${BASE_URL}/v2/batch/network/summary")
BATCH_HTTP=$(echo "${BATCH_RESPONSE}" | tail -1)
BATCH_BODY=$(echo "${BATCH_RESPONSE}" | head -1)
if [[ "${BATCH_HTTP}" != "200" ]]; then
  api_fail "POST /v2/batch/network/summary (anon) → HTTP ${BATCH_HTTP} (expected 200). Body: ${BATCH_BODY:0:300}"
fi
ANON_ROW_COUNT=$(echo "${BATCH_BODY}" | grep -o '"externalId"' | wc -l | tr -d ' ')
if [[ "${ANON_ROW_COUNT}" == "1" ]] && echo "${BATCH_BODY}" | grep -q "${V2_UUIDS[0]}" && ! echo "${BATCH_BODY}" | grep -q "${V2_PRIV_UUID}"; then
  api_pass "POST /v2/batch/network/summary (anon) → exactly 1 row (public only)"
else
  api_fail "POST /v2/batch/network/summary (anon) returned ${ANON_ROW_COUNT} rows or wrong UUIDs. Body: ${BATCH_BODY:0:500}"
fi

CALL_NUM=$((CALL_NUM+1))
echo "  API call ${CALL_NUM}/${TOTAL_API_CALLS}: POST /v2/batch/network/summary (auth ${TEST_USER2}, expect exactly 1 row)"
BATCH_RESPONSE=$(curl -s -w "\n%{http_code}" -X POST \
  -u "${TEST_USER2}:${TEST_PASS2}" \
  -H "Content-Type: application/json" \
  -d "${BATCH_REQ}" \
  "${BASE_URL}/v2/batch/network/summary")
BATCH_HTTP=$(echo "${BATCH_RESPONSE}" | tail -1)
BATCH_BODY=$(echo "${BATCH_RESPONSE}" | head -1)
if [[ "${BATCH_HTTP}" != "200" ]]; then
  api_fail "POST /v2/batch/network/summary (auth ${TEST_USER2}) → HTTP ${BATCH_HTTP} (expected 200). Body: ${BATCH_BODY:0:300}"
fi
AUTH_ROW_COUNT=$(echo "${BATCH_BODY}" | grep -o '"externalId"' | wc -l | tr -d ' ')
if [[ "${AUTH_ROW_COUNT}" == "1" ]] && echo "${BATCH_BODY}" | grep -q "${V2_UUIDS[0]}" && ! echo "${BATCH_BODY}" | grep -q "${V2_PRIV_UUID}"; then
  api_pass "POST /v2/batch/network/summary (auth ${TEST_USER2}) → exactly 1 row (public only)"
else
  api_fail "POST /v2/batch/network/summary (auth ${TEST_USER2}) returned ${AUTH_ROW_COUNT} rows or wrong UUIDs. Body: ${BATCH_BODY:0:500}"
fi
fi

# ── STEP: NDEx group feature removed — every group endpoint returns HTTP 501 ──

step "Group feature removed: group endpoints return 501; surviving user paths still work"

GROUP_DUMMY_UUID="00000000-0000-0000-0000-000000000001"

# /v2/group resource — all methods retired
assert_group_501 POST   "${BASE_URL}/v2/group" -H "Content-Type: application/json" -d '{}'
assert_group_501 GET    "${BASE_URL}/v2/group/${GROUP_DUMMY_UUID}"
assert_group_501 GET    "${BASE_URL}/v2/group/${GROUP_DUMMY_UUID}/membership"
assert_group_501 GET    "${BASE_URL}/v2/group/${GROUP_DUMMY_UUID}/permission"
assert_group_501 POST   "${BASE_URL}/v2/group/${GROUP_DUMMY_UUID}/permissionrequest" -H "Content-Type: application/json" -d '{}'

# v1 /group resource
assert_group_501 GET    "${BASE_URL}/group/${GROUP_DUMMY_UUID}"

# group search + batch
assert_group_501 POST   "${BASE_URL}/v2/search/group" -H "Content-Type: application/json" -d '{"searchString":"x"}'
assert_group_501 POST   "${BASE_URL}/v2/batch/group" -H "Content-Type: application/json" -d "[\"${GROUP_DUMMY_UUID}\"]"

# user-side group membership / JoinGroup endpoints
assert_group_501 GET    "${BASE_URL}/v2/user/${GROUP_DUMMY_UUID}/membership"
assert_group_501 POST   "${BASE_URL}/v2/user/${GROUP_DUMMY_UUID}/membershiprequest" -H "Content-Type: application/json" -d '{}'
assert_group_501 GET    "${BASE_URL}/user/${GROUP_DUMMY_UUID}/group/READ/0/100"

# mixed network-permission endpoints: the group branch is retired (501), user branch survives
assert_group_501 GET    "${BASE_URL}/v2/network/${V2_PRIV_UUID}/permission?type=group"
assert_group_501 DELETE "${BASE_URL}/v2/network/${V2_PRIV_UUID}/permission?groupid=${GROUP_DUMMY_UUID}"

# regression: the user permission branch on the same endpoint still works for the owner
CALL_NUM=$((CALL_NUM+1))
echo "  API call ${CALL_NUM}/${TOTAL_API_CALLS}: GET /v2/network/${V2_PRIV_UUID}/permission?type=user (auth owner, expect 200)"
PERM_USER_HTTP=$(curl -s -o /dev/null -w "%{http_code}" -u "${TEST_USER}:${TEST_PASS}" \
  "${BASE_URL}/v2/network/${V2_PRIV_UUID}/permission?type=user")
if [[ "${PERM_USER_HTTP}" == "200" ]]; then
  api_pass "GET /v2/network/${V2_PRIV_UUID}/permission?type=user (owner) → 200 (user permission path intact)"
else
  api_fail "GET /v2/network/${V2_PRIV_UUID}/permission?type=user (owner) → HTTP ${PERM_USER_HTTP} (expected 200)"
fi

# NOTE: the /v2/networkset endpoints are all live and folder-backed. They are exercised as one ordered
# round-trip (create → add members → update → read → remove members → delete) in the dedicated step
# further down, which also cross-checks each write against the v3 folder endpoints. Nothing about
# network sets asserts HTTP 501 any more.

# ── STEP: Folder list/count per-child visibility (F10) ───────────────────────
# A folder's visibility is independent of its children's. A folder the caller can
# read must NOT leak the metadata of PRIVATE children they cannot see, and /count
# must match /list. A valid folder access key grants the folder's full contents.
step "Folder list/count enforces per-child visibility (F10, anonymous)"

# Create a folder owned by TEST_USER; put one PUBLIC and one PRIVATE network in it.
CALL_NUM=$((CALL_NUM+1))
echo "  API call ${CALL_NUM}/${TOTAL_API_CALLS}: POST /v3/files/folders/ (create test folder)"
F10_FOLDER_RESP=$(curl -s -w "\n%{http_code}" -X POST \
  -u "${TEST_USER}:${TEST_PASS}" \
  -H "Content-Type: application/json" \
  -d '{"name":"F10 visibility test"}' \
  "${BASE_URL}/v3/files/folders/")
F10_FOLDER_HTTP=$(echo "${F10_FOLDER_RESP}" | tail -1)
F10_FOLDER_BODY=$(echo "${F10_FOLDER_RESP}" | head -1)
if [[ "${F10_FOLDER_HTTP}" != "201" ]]; then
  api_fail "POST /v3/files/folders/ → HTTP ${F10_FOLDER_HTTP} (expected 201). Body: ${F10_FOLDER_BODY:0:300}"
fi
F10_FOLDER_ID=$(echo "${F10_FOLDER_BODY}" | grep -oiE '"uuid"[[:space:]]*:[[:space:]]*"[0-9a-f-]{36}"' | grep -oiE '[0-9a-f-]{36}' | head -1)
if [[ -z "${F10_FOLDER_ID}" ]]; then
  api_fail "Could not parse folder UUID from create response. Body: ${F10_FOLDER_BODY:0:300}"
fi
api_pass "POST /v3/files/folders/ → 201 Created (folder ${F10_FOLDER_ID})"

CALL_NUM=$((CALL_NUM+1))
echo "  API call ${CALL_NUM}/${TOTAL_API_CALLS}: POST /v3/files/setvisibility (folder → PUBLIC)"
F10_VIS_HTTP=$(curl -s -o /dev/null -w "%{http_code}" -X POST \
  -u "${TEST_USER}:${TEST_PASS}" -H "Content-Type: application/json" \
  -d "{\"visibility\":\"PUBLIC\",\"files\":{\"${F10_FOLDER_ID}\":\"FOLDER\"}}" \
  "${BASE_URL}/v3/batch/files/setvisibility")
[[ "${F10_VIS_HTTP}" == "200" || "${F10_VIS_HTTP}" == "204" ]] \
  || api_fail "POST /v3/files/setvisibility (PUBLIC) → HTTP ${F10_VIS_HTTP}"
api_pass "Folder set PUBLIC"

CALL_NUM=$((CALL_NUM+1))
echo "  API call ${CALL_NUM}/${TOTAL_API_CALLS}: POST /v3/networks/move (public + private into folder)"
F10_MOVE_HTTP=$(curl -s -o /dev/null -w "%{http_code}" -X POST \
  -u "${TEST_USER}:${TEST_PASS}" -H "Content-Type: application/json" \
  -d "{\"targetFolder\":\"${F10_FOLDER_ID}\",\"networks\":[\"${V3_PUB_UUID}\",\"${V3_PRIV_UUID}\"]}" \
  "${BASE_URL}/v3/batch/networks/move")
[[ "${F10_MOVE_HTTP}" == "200" || "${F10_MOVE_HTTP}" == "204" ]] \
  || api_fail "POST /v3/networks/move → HTTP ${F10_MOVE_HTTP}"
api_pass "Moved PUBLIC (${V3_PUB_UUID}) + PRIVATE (${V3_PRIV_UUID}) networks into folder"

# ---- Phase A: PUBLIC folder — an ANONYMOUS caller sees only the public child ----
# This step runs BEFORE the AUTHENTICATED_USER_ONLY=true step below, so the server still permits
# anonymous access to @PermitAll endpoints — exercising the real public-server leak scenario.
CALL_NUM=$((CALL_NUM+1))
echo "  API call ${CALL_NUM}/${TOTAL_API_CALLS}: GET .../list + /count (anon) — PRIVATE child absent, network=1"
F10_ANON_LIST=$(curl -s "${BASE_URL}/v3/files/folders/${F10_FOLDER_ID}/list")
F10_ANON_NET=$(curl -s "${BASE_URL}/v3/files/folders/${F10_FOLDER_ID}/count" | grep -oE '"network"[[:space:]]*:[[:space:]]*[0-9]+' | grep -oE '[0-9]+$')
echo "${F10_ANON_LIST}" | grep -q "${V3_PRIV_UUID}" \
  && api_fail "anon /list LEAKED private child ${V3_PRIV_UUID}. Body: ${F10_ANON_LIST:0:400}"
{ echo "${F10_ANON_LIST}" | grep -q "${V3_PUB_UUID}" && [[ "${F10_ANON_NET}" == "1" ]]; } \
  || api_fail "anon view wrong: net=${F10_ANON_NET}, list=${F10_ANON_LIST:0:400}"
api_pass "anon → /list PUBLIC child only (PRIVATE absent); /count network=1"

# An AUTHENTICATED non-owner must also see only the public child (created anonymously here — the
# server is still in default mode; the AUTHENTICATED_USER_ONLY step runs later).
CALL_NUM=$((CALL_NUM+1))
echo "  API call ${CALL_NUM}/${TOTAL_API_CALLS}: POST /v2/user (create non-owner ${TEST_USER2})"
U2_HTTP=$(curl -s -o /dev/null -w "%{http_code}" -X POST "${BASE_URL}/v2/user" \
  -H "Content-Type: application/json" \
  -d "{\"userName\":\"${TEST_USER2}\",\"password\":\"${TEST_PASS2}\",\"emailAddress\":\"${TEST_EMAIL2}\",\"firstName\":\"NDEx\",\"lastName\":\"Test2\"}")
[[ "${U2_HTTP}" == "201" || "${U2_HTTP}" == "409" ]] || api_fail "POST /v2/user (${TEST_USER2}) → HTTP ${U2_HTTP}"
api_pass "non-owner user ${TEST_USER2} ready"

CALL_NUM=$((CALL_NUM+1))
echo "  API call ${CALL_NUM}/${TOTAL_API_CALLS}: GET .../list + /count (authenticated non-owner) — PRIVATE child absent, network=1"
F10_U2_LIST=$(curl -s -u "${TEST_USER2}:${TEST_PASS2}" "${BASE_URL}/v3/files/folders/${F10_FOLDER_ID}/list")
F10_U2_NET=$(curl -s -u "${TEST_USER2}:${TEST_PASS2}" "${BASE_URL}/v3/files/folders/${F10_FOLDER_ID}/count" | grep -oE '"network"[[:space:]]*:[[:space:]]*[0-9]+' | grep -oE '[0-9]+$')
echo "${F10_U2_LIST}" | grep -q "${V3_PRIV_UUID}" \
  && api_fail "authenticated non-owner /list LEAKED private child ${V3_PRIV_UUID}. Body: ${F10_U2_LIST:0:400}"
{ echo "${F10_U2_LIST}" | grep -q "${V3_PUB_UUID}" && [[ "${F10_U2_NET}" == "1" ]]; } \
  || api_fail "authenticated non-owner view wrong: net=${F10_U2_NET}, list=${F10_U2_LIST:0:400}"
api_pass "authenticated non-owner → /list PUBLIC child only (PRIVATE absent); /count network=1"

CALL_NUM=$((CALL_NUM+1))
echo "  API call ${CALL_NUM}/${TOTAL_API_CALLS}: GET .../list + /count (owner) — both children, network=2"
F10_OWNER_LIST=$(curl -s -u "${TEST_USER}:${TEST_PASS}" "${BASE_URL}/v3/files/folders/${F10_FOLDER_ID}/list")
F10_OWNER_NET=$(curl -s -u "${TEST_USER}:${TEST_PASS}" "${BASE_URL}/v3/files/folders/${F10_FOLDER_ID}/count" | grep -oE '"network"[[:space:]]*:[[:space:]]*[0-9]+' | grep -oE '[0-9]+$')
{ echo "${F10_OWNER_LIST}" | grep -q "${V3_PUB_UUID}" && echo "${F10_OWNER_LIST}" | grep -q "${V3_PRIV_UUID}" && [[ "${F10_OWNER_NET}" == "2" ]]; } \
  || api_fail "owner view wrong: net=${F10_OWNER_NET}, list=${F10_OWNER_LIST:0:400}"
api_pass "owner → /list both children; /count network=2"

# A valid access key must return ALL children even when the folder is independently readable
# (PUBLIC) — the key takes precedence over per-child filtering.
CALL_NUM=$((CALL_NUM+1))
echo "  API call ${CALL_NUM}/${TOTAL_API_CALLS}: POST /v3/files/sharing/share (enable folder access key)"
F10_SHARE_BODY=$(curl -s -X POST -u "${TEST_USER}:${TEST_PASS}" -H "Content-Type: application/json" \
  -d "{\"files\":{\"${F10_FOLDER_ID}\":\"FOLDER\"}}" \
  "${BASE_URL}/v3/files/sharing/share")
F10_KEY=$(echo "${F10_SHARE_BODY}" | sed -E 's/.*:[[:space:]]*"([^"]+)".*/\1/')
[[ -n "${F10_KEY}" && "${F10_KEY}" != "${F10_SHARE_BODY}" ]] || api_fail "Could not parse access key. Body: ${F10_SHARE_BODY:0:300}"
api_pass "Folder access key enabled"

CALL_NUM=$((CALL_NUM+1))
echo "  API call ${CALL_NUM}/${TOTAL_API_CALLS}: GET .../list + /count?accesskey on PUBLIC (readable) folder (anon) — ALL children, network=2"
F10_PUBKEY_LIST=$(curl -s "${BASE_URL}/v3/files/folders/${F10_FOLDER_ID}/list?accesskey=${F10_KEY}")
F10_PUBKEY_NET=$(curl -s "${BASE_URL}/v3/files/folders/${F10_FOLDER_ID}/count?accesskey=${F10_KEY}" | grep -oE '"network"[[:space:]]*:[[:space:]]*[0-9]+' | grep -oE '[0-9]+$')
{ echo "${F10_PUBKEY_LIST}" | grep -q "${V3_PUB_UUID}" && echo "${F10_PUBKEY_LIST}" | grep -q "${V3_PRIV_UUID}" && [[ "${F10_PUBKEY_NET}" == "2" ]]; } \
  || api_fail "access-key precedence on readable folder wrong: net=${F10_PUBKEY_NET}, list=${F10_PUBKEY_LIST:0:400}"
api_pass "anon + access key on PUBLIC folder → /list all children (incl. PRIVATE); /count network=2 (key precedence)"

# ---- Phase B: PRIVATE folder + access key — key grants ALL contents ----
CALL_NUM=$((CALL_NUM+1))
echo "  API call ${CALL_NUM}/${TOTAL_API_CALLS}: POST /v3/files/setvisibility (folder → PRIVATE)"
F10_VIS2_HTTP=$(curl -s -o /dev/null -w "%{http_code}" -X POST \
  -u "${TEST_USER}:${TEST_PASS}" -H "Content-Type: application/json" \
  -d "{\"visibility\":\"PRIVATE\",\"files\":{\"${F10_FOLDER_ID}\":\"FOLDER\"}}" \
  "${BASE_URL}/v3/batch/files/setvisibility")
[[ "${F10_VIS2_HTTP}" == "200" || "${F10_VIS2_HTTP}" == "204" ]] \
  || api_fail "POST /v3/files/setvisibility (PRIVATE) → HTTP ${F10_VIS2_HTTP}"
api_pass "Folder set PRIVATE"

CALL_NUM=$((CALL_NUM+1))
echo "  API call ${CALL_NUM}/${TOTAL_API_CALLS}: GET .../list + /count (anon, no key) — PRIVATE folder must be 401"
F10_NOKEY_LIST_HTTP=$(curl -s -o /dev/null -w "%{http_code}" "${BASE_URL}/v3/files/folders/${F10_FOLDER_ID}/list")
F10_NOKEY_COUNT_HTTP=$(curl -s -o /dev/null -w "%{http_code}" "${BASE_URL}/v3/files/folders/${F10_FOLDER_ID}/count")
{ [[ "${F10_NOKEY_LIST_HTTP}" == "401" ]] && [[ "${F10_NOKEY_COUNT_HTTP}" == "401" ]]; } \
  || api_fail "anon on PRIVATE folder (no key) → list=${F10_NOKEY_LIST_HTTP}, count=${F10_NOKEY_COUNT_HTTP} (expected 401/401)"
api_pass "anon /list + /count on PRIVATE folder (no key) → 401"

# The access key was enabled in Phase A (while PUBLIC); it still grants full contents now that the
# folder is PRIVATE.
CALL_NUM=$((CALL_NUM+1))
echo "  API call ${CALL_NUM}/${TOTAL_API_CALLS}: GET .../list + /count?accesskey (anon) — ALL children, network=2"
F10_KEY_LIST=$(curl -s "${BASE_URL}/v3/files/folders/${F10_FOLDER_ID}/list?accesskey=${F10_KEY}")
F10_KEY_NET=$(curl -s "${BASE_URL}/v3/files/folders/${F10_FOLDER_ID}/count?accesskey=${F10_KEY}" | grep -oE '"network"[[:space:]]*:[[:space:]]*[0-9]+' | grep -oE '[0-9]+$')
{ echo "${F10_KEY_LIST}" | grep -q "${V3_PUB_UUID}" && echo "${F10_KEY_LIST}" | grep -q "${V3_PRIV_UUID}" && [[ "${F10_KEY_NET}" == "2" ]]; } \
  || api_fail "access-key view wrong: net=${F10_KEY_NET}, list=${F10_KEY_LIST:0:400}"
api_pass "anon + access key → /list all children (incl. PRIVATE); /count network=2"

# ── STEP: Access key follows the folder hierarchy + same-owner shortcut resolution (G11, #133/#137) ──
# A network is reachable by an ANCESTOR folder's access key (accrual up the folder chain), AND — for
# backwards compatibility with the v3 networkset migration — by a SAME-OWNER NETWORK shortcut that lives
# in a keyed folder even though the target network sits elsewhere (e.g. Home). Such shortcuts are also
# surfaced in the key-authorized /list and /count views.
step "Access key: ancestor-folder accrual + same-owner shortcut resolution (G11, #133/#137)"

# Nest the PRIVATE network one level deeper: a subfolder under the (keyed) F10 folder. The network's
# key access must now be resolved via the GRANDPARENT folder's key.
CALL_NUM=$((CALL_NUM+1))
echo "  API call ${CALL_NUM}/${TOTAL_API_CALLS}: POST /v3/files/folders/ (subfolder under keyed folder)"
G11_SUB_RESP=$(curl -s -w "\n%{http_code}" -X POST -u "${TEST_USER}:${TEST_PASS}" \
  -H "Content-Type: application/json" \
  -d "{\"name\":\"G11 subfolder\",\"parent\":\"${F10_FOLDER_ID}\"}" \
  "${BASE_URL}/v3/files/folders/")
G11_SUB_HTTP=$(echo "${G11_SUB_RESP}" | tail -1); G11_SUB_BODY=$(echo "${G11_SUB_RESP}" | head -1)
[[ "${G11_SUB_HTTP}" == "201" ]] || api_fail "create subfolder → HTTP ${G11_SUB_HTTP}. Body: ${G11_SUB_BODY:0:300}"
G11_SUB_ID=$(echo "${G11_SUB_BODY}" | grep -oiE '"uuid"[[:space:]]*:[[:space:]]*"[0-9a-f-]{36}"' | grep -oiE '[0-9a-f-]{36}' | head -1)
[[ -n "${G11_SUB_ID}" ]] || api_fail "no uuid in subfolder create. Body: ${G11_SUB_BODY:0:300}"
api_pass "subfolder ${G11_SUB_ID} created under keyed folder ${F10_FOLDER_ID}"

CALL_NUM=$((CALL_NUM+1))
echo "  API call ${CALL_NUM}/${TOTAL_API_CALLS}: POST /v3/batch/networks/move (private net into subfolder)"
G11_MOVE_HTTP=$(curl -s -o /dev/null -w "%{http_code}" -X POST -u "${TEST_USER}:${TEST_PASS}" \
  -H "Content-Type: application/json" \
  -d "{\"targetFolder\":\"${G11_SUB_ID}\",\"networks\":[\"${V3_PRIV_UUID}\"]}" \
  "${BASE_URL}/v3/batch/networks/move")
[[ "${G11_MOVE_HTTP}" == "200" || "${G11_MOVE_HTTP}" == "204" ]] \
  || api_fail "move private net into subfolder → HTTP ${G11_MOVE_HTTP}"
api_pass "PRIVATE network ${V3_PRIV_UUID} moved into subfolder (grandparent holds the key)"

CALL_NUM=$((CALL_NUM+1))
echo "  API call ${CALL_NUM}/${TOTAL_API_CALLS}: GET /v3/networks/{priv}/summary?accesskey=<ancestor key> (anon, expect 200)"
G11_SUMM_RESP=$(curl -s -w "\n%{http_code}" "${BASE_URL}/v3/networks/${V3_PRIV_UUID}/summary?accesskey=${F10_KEY}")
G11_SUMM_HTTP=$(echo "${G11_SUMM_RESP}" | tail -1); G11_SUMM_BODY=$(echo "${G11_SUMM_RESP}" | head -1)
{ [[ "${G11_SUMM_HTTP}" == "200" ]] && echo "${G11_SUMM_BODY}" | grep -q "${V3_PRIV_UUID}"; } \
  || api_fail "ancestor-key network access wrong: HTTP ${G11_SUMM_HTTP}, body ${G11_SUMM_BODY:0:300}"
api_pass "anon + ANCESTOR folder key → GET private network summary 200 (accrual up the folder chain)"

CALL_NUM=$((CALL_NUM+1))
echo "  API call ${CALL_NUM}/${TOTAL_API_CALLS}: GET /v3/networks/{priv}/summary (anon, no key, expect 401)"
G11_NOKEY_HTTP=$(curl -s -o /dev/null -w "%{http_code}" "${BASE_URL}/v3/networks/${V3_PRIV_UUID}/summary")
[[ "${G11_NOKEY_HTTP}" == "401" ]] \
  || api_fail "anon no-key on PRIVATE network summary → HTTP ${G11_NOKEY_HTTP} (expected 401)"
api_pass "anon + no key → GET private network summary 401 (negative control)"

# Same-owner NETWORK shortcut resolution (#133/#137): put a shortcut in the keyed folder pointing at a
# PRIVATE network that lives in Home — reachable by this key ONLY through the shortcut. The target is
# owned by TEST_USER (same owner as the folder), satisfying the same-owner guard. The key must now
# (Change 3) surface the shortcut in the key /list + /count, and (Changes 1 & 2) grant anonymous read to
# the target network via GET, search, and batch summary.
CALL_NUM=$((CALL_NUM+1))
echo "  API call ${CALL_NUM}/${TOTAL_API_CALLS}: POST /v3/files/shortcuts/ (shortcut in keyed folder → private Home network)"
G11_SC_RESP=$(curl -s -w "\n%{http_code}" -X POST -u "${TEST_USER}:${TEST_PASS}" \
  -H "Content-Type: application/json" \
  -d "{\"name\":\"G11 shortcut\",\"parent\":\"${F10_FOLDER_ID}\",\"target\":\"${V2_PRIV_UUID}\",\"targetType\":\"NETWORK\"}" \
  "${BASE_URL}/v3/files/shortcuts/")
G11_SC_HTTP=$(echo "${G11_SC_RESP}" | tail -1); G11_SC_BODY=$(echo "${G11_SC_RESP}" | head -1)
[[ "${G11_SC_HTTP}" == "201" ]] || api_fail "create shortcut → HTTP ${G11_SC_HTTP}. Body: ${G11_SC_BODY:0:300}"
G11_SC_ID=$(echo "${G11_SC_BODY}" | grep -oiE '"uuid"[[:space:]]*:[[:space:]]*"[0-9a-f-]{36}"' | grep -oiE '[0-9a-f-]{36}' | head -1)
[[ -n "${G11_SC_ID}" ]] || api_fail "no uuid in create-shortcut response. Body: ${G11_SC_BODY:0:300}"
api_pass "shortcut ${G11_SC_ID} created in keyed folder → private network ${V2_PRIV_UUID}"

# Change 3: key-authorized /list + /count now INCLUDE the same-owner NETWORK shortcut.
CALL_NUM=$((CALL_NUM+1))
echo "  API call ${CALL_NUM}/${TOTAL_API_CALLS}: GET .../list + /count?accesskey (anon) — shortcut INCLUDED, shortcut count >=1"
G11_KEY_LIST=$(curl -s "${BASE_URL}/v3/files/folders/${F10_FOLDER_ID}/list?accesskey=${F10_KEY}")
G11_KEY_SC=$(curl -s "${BASE_URL}/v3/files/folders/${F10_FOLDER_ID}/count?accesskey=${F10_KEY}" | grep -oE '"shortcut"[[:space:]]*:[[:space:]]*[0-9]+' | grep -oE '[0-9]+$')
echo "${G11_KEY_LIST}" | grep -q "${G11_SC_ID}" \
  || api_fail "key /list should include same-owner shortcut ${G11_SC_ID}. Body: ${G11_KEY_LIST:0:400}"
[[ "${G11_KEY_SC:-0}" -ge 1 ]] \
  || api_fail "key /count shortcut expected >=1, got ${G11_KEY_SC}"
api_pass "anon + key → /list includes same-owner NETWORK shortcut; /count shortcut>=1 (Change 3, #133/#137)"

# Change 1 (GET): the key grants anonymous read to the shortcut's target network (was 401 before the fix).
CALL_NUM=$((CALL_NUM+1))
echo "  API call ${CALL_NUM}/${TOTAL_API_CALLS}: GET /v3/networks/{shortcut-target}?accesskey (anon, expect 200)"
G11_SCGET_HTTP=$(curl -s -o /dev/null -w "%{http_code}" "${BASE_URL}/v3/networks/${V2_PRIV_UUID}?accesskey=${F10_KEY}")
[[ "${G11_SCGET_HTTP}" == "200" ]] \
  || api_fail "anon + key via shortcut → GET network HTTP ${G11_SCGET_HTTP} (expected 200)"
api_pass "anon + key → GET private network via same-owner shortcut 200 (Change 1, #133/#137)"

# Negative controls: no key and a wrong key must stay 401 (the shortcut alone grants nothing).
CALL_NUM=$((CALL_NUM+1))
echo "  API call ${CALL_NUM}/${TOTAL_API_CALLS}: GET /v3/networks/{shortcut-target} (anon no-key / wrong-key, expect 401)"
G11_SCNO_HTTP=$(curl -s -o /dev/null -w "%{http_code}" "${BASE_URL}/v3/networks/${V2_PRIV_UUID}")
G11_SCWRONG_HTTP=$(curl -s -o /dev/null -w "%{http_code}" "${BASE_URL}/v3/networks/${V2_PRIV_UUID}?accesskey=not-a-real-key")
{ [[ "${G11_SCNO_HTTP}" == "401" ]] && [[ "${G11_SCWRONG_HTTP}" == "401" ]]; } \
  || api_fail "shortcut-target negative controls wrong: no-key=${G11_SCNO_HTTP}, wrong-key=${G11_SCWRONG_HTTP} (expected 401/401)"
api_pass "anon no-key / wrong-key → GET shortcut-target network 401 (negative controls)"

# Change 1 (search): the same accessKeyIsValid path backs the per-network search endpoints (stub proxied).
CALL_NUM=$((CALL_NUM+1))
echo "  API call ${CALL_NUM}/${TOTAL_API_CALLS}: POST /v3/search/networks/{shortcut-target}/query?accesskey (anon, expect 200)"
G11_SCQ_HTTP=$(curl -s -o /dev/null -w "%{http_code}" -X POST \
  -H "Content-Type: application/json" -d '{"searchString":"EGFR","searchDepth":1}' \
  "${BASE_URL}/v3/search/networks/${V2_PRIV_UUID}/query?accesskey=${F10_KEY}")
[[ "${G11_SCQ_HTTP}" == "200" ]] \
  || api_fail "anon + key via shortcut → search query HTTP ${G11_SCQ_HTTP} (expected 200)"
api_pass "anon + key → POST search query on shortcut-target network 200 (Change 1, #133/#137)"

# Change 2 (batch summary): one call spanning a REAL network (V3_PRIV, reached via the ancestor folder
# key) and a SHORTCUT network (V2_PRIV, reached via the same-owner shortcut) — both must be returned.
CALL_NUM=$((CALL_NUM+1))
echo "  API call ${CALL_NUM}/${TOTAL_API_CALLS}: POST /v3/batch/networks/summary?accesskey (anon) — real + shortcut network both present"
G11_BATCH=$(curl -s -X POST -H "Content-Type: application/json" \
  -d "[\"${V3_PRIV_UUID}\",\"${V2_PRIV_UUID}\"]" \
  "${BASE_URL}/v3/batch/networks/summary?accesskey=${F10_KEY}")
{ echo "${G11_BATCH}" | grep -q "${V3_PRIV_UUID}" && echo "${G11_BATCH}" | grep -q "${V2_PRIV_UUID}"; } \
  || api_fail "batch summary + key should include real (${V3_PRIV_UUID}) and shortcut (${V2_PRIV_UUID}) networks. Body: ${G11_BATCH:0:400}"
api_pass "anon + key → batch summary returns both the ancestor-key network and the shortcut-key network (Change 2)"

# Negative: batch summary WITHOUT a key returns neither private network to an anonymous caller.
CALL_NUM=$((CALL_NUM+1))
echo "  API call ${CALL_NUM}/${TOTAL_API_CALLS}: POST /v3/batch/networks/summary (anon, no key) — neither private network present"
G11_BATCH_NOKEY=$(curl -s -X POST -H "Content-Type: application/json" \
  -d "[\"${V3_PRIV_UUID}\",\"${V2_PRIV_UUID}\"]" \
  "${BASE_URL}/v3/batch/networks/summary")
{ echo "${G11_BATCH_NOKEY}" | grep -q "${V3_PRIV_UUID}" || echo "${G11_BATCH_NOKEY}" | grep -q "${V2_PRIV_UUID}"; } \
  && api_fail "batch summary without key LEAKED a private network. Body: ${G11_BATCH_NOKEY:0:400}"
api_pass "anon + no key → batch summary omits both private networks (negative control)"

# The owner (no key) still sees the shortcut in the identity-based view (no-key path unchanged).
CALL_NUM=$((CALL_NUM+1))
echo "  API call ${CALL_NUM}/${TOTAL_API_CALLS}: GET .../list (owner, no key) — shortcut PRESENT (no-key path unchanged)"
G11_OWNER_LIST=$(curl -s -u "${TEST_USER}:${TEST_PASS}" "${BASE_URL}/v3/files/folders/${F10_FOLDER_ID}/list")
echo "${G11_OWNER_LIST}" | grep -q "${G11_SC_ID}" \
  || api_fail "owner /list (no key) should include shortcut ${G11_SC_ID}. Body: ${G11_OWNER_LIST:0:400}"
api_pass "owner (no key) → /list includes the shortcut (identity-based view unchanged)"

# ── STEP: Folder/Shortcut visibility — write path accepts it, read path reports it ──
# Runs while anonymous access is still allowed (all calls here are authenticated as
# TEST_USER, so they also work after the AUTHENTICATED_USER_ONLY flip below).
step "Folder/Shortcut visibility: create/update accept it, reads report it"

# --- Folder: write path accepts visibility on create ---
CALL_NUM=$((CALL_NUM+1))
echo "  API call ${CALL_NUM}/${TOTAL_API_CALLS}: POST /v3/files/folders/ (create with visibility=PUBLIC)"
VIS_F_RESP=$(curl -s -w "\n%{http_code}" -X POST -u "${TEST_USER}:${TEST_PASS}" \
  -H "Content-Type: application/json" \
  -d '{"name":"vis-folder","visibility":"PUBLIC"}' \
  "${BASE_URL}/v3/files/folders/")
VIS_F_HTTP=$(echo "${VIS_F_RESP}" | tail -1); VIS_F_BODY=$(echo "${VIS_F_RESP}" | head -1)
[[ "${VIS_F_HTTP}" == "201" ]] || api_fail "create folder (visibility=PUBLIC) → HTTP ${VIS_F_HTTP}. Body: ${VIS_F_BODY:0:300}"
VIS_F_ID=$(echo "${VIS_F_BODY}" | grep -oiE '"uuid"[[:space:]]*:[[:space:]]*"[0-9a-f-]{36}"' | grep -oiE '[0-9a-f-]{36}' | head -1)
[[ -n "${VIS_F_ID}" ]] || api_fail "no uuid in create-folder response. Body: ${VIS_F_BODY:0:300}"
api_pass "POST folder with visibility=PUBLIC → 201 (folder ${VIS_F_ID})"

# --- Folder: read path populates visibility (GET {id} + list-mine) ---
CALL_NUM=$((CALL_NUM+1))
echo "  API call ${CALL_NUM}/${TOTAL_API_CALLS}: GET /v3/files/folders/{id} (visibility populated)"
VIS_F_GET=$(curl -s -u "${TEST_USER}:${TEST_PASS}" "${BASE_URL}/v3/files/folders/${VIS_F_ID}")
echo "${VIS_F_GET}" | grep -qE '"visibility"[[:space:]]*:[[:space:]]*"PUBLIC"' \
  || api_fail "GET folder did not report visibility=PUBLIC. Body: ${VIS_F_GET:0:300}"
api_pass "GET folder reports visibility=PUBLIC"

CALL_NUM=$((CALL_NUM+1))
echo "  API call ${CALL_NUM}/${TOTAL_API_CALLS}: GET /v3/files/folders/ (list-mine reports visibility)"
VIS_F_LIST=$(curl -s -u "${TEST_USER}:${TEST_PASS}" "${BASE_URL}/v3/files/folders/")
echo "${VIS_F_LIST}" | grep -q '"visibility"' \
  || api_fail "list-mine folders did not report a visibility field. Body: ${VIS_F_LIST:0:400}"
api_pass "GET list-mine folders reports visibility"

# --- Folder: omitted visibility defaults to PRIVATE ---
CALL_NUM=$((CALL_NUM+1))
echo "  API call ${CALL_NUM}/${TOTAL_API_CALLS}: POST /v3/files/folders/ (no visibility → default PRIVATE)"
VIS_FD_RESP=$(curl -s -w "\n%{http_code}" -X POST -u "${TEST_USER}:${TEST_PASS}" \
  -H "Content-Type: application/json" -d '{"name":"vis-folder-default"}' \
  "${BASE_URL}/v3/files/folders/")
VIS_FD_HTTP=$(echo "${VIS_FD_RESP}" | tail -1); VIS_FD_BODY=$(echo "${VIS_FD_RESP}" | head -1)
[[ "${VIS_FD_HTTP}" == "201" ]] || api_fail "create folder (no visibility) → HTTP ${VIS_FD_HTTP}"
VIS_FD_ID=$(echo "${VIS_FD_BODY}" | grep -oiE '"uuid"[[:space:]]*:[[:space:]]*"[0-9a-f-]{36}"' | grep -oiE '[0-9a-f-]{36}' | head -1)
CALL_NUM=$((CALL_NUM+1))
echo "  API call ${CALL_NUM}/${TOTAL_API_CALLS}: GET /v3/files/folders/{id} (default visibility=PRIVATE)"
VIS_FD_GET=$(curl -s -u "${TEST_USER}:${TEST_PASS}" "${BASE_URL}/v3/files/folders/${VIS_FD_ID}")
echo "${VIS_FD_GET}" | grep -qE '"visibility"[[:space:]]*:[[:space:]]*"PRIVATE"' \
  || api_fail "folder created without visibility did not default to PRIVATE. Body: ${VIS_FD_GET:0:300}"
api_pass "folder without visibility defaults to PRIVATE"

# --- Folder: update accepts visibility ---
CALL_NUM=$((CALL_NUM+1))
echo "  API call ${CALL_NUM}/${TOTAL_API_CALLS}: PUT /v3/files/folders/{id} (visibility=UNLISTED)"
VIS_F_PUT=$(curl -s -o /dev/null -w "%{http_code}" -X PUT -u "${TEST_USER}:${TEST_PASS}" \
  -H "Content-Type: application/json" -d '{"visibility":"UNLISTED"}' \
  "${BASE_URL}/v3/files/folders/${VIS_F_ID}")
[[ "${VIS_F_PUT}" == "204" || "${VIS_F_PUT}" == "200" ]] || api_fail "PUT folder visibility → HTTP ${VIS_F_PUT}"
CALL_NUM=$((CALL_NUM+1))
echo "  API call ${CALL_NUM}/${TOTAL_API_CALLS}: GET /v3/files/folders/{id} (visibility now UNLISTED)"
VIS_F_GET2=$(curl -s -u "${TEST_USER}:${TEST_PASS}" "${BASE_URL}/v3/files/folders/${VIS_F_ID}")
echo "${VIS_F_GET2}" | grep -qE '"visibility"[[:space:]]*:[[:space:]]*"UNLISTED"' \
  || api_fail "folder update did not change visibility to UNLISTED. Body: ${VIS_F_GET2:0:300}"
api_pass "PUT folder visibility=UNLISTED applied; GET reports UNLISTED"

# --- Shortcut: write path accepts visibility on create (target the vis-folder) ---
CALL_NUM=$((CALL_NUM+1))
echo "  API call ${CALL_NUM}/${TOTAL_API_CALLS}: POST /v3/files/shortcuts/ (visibility=PUBLIC)"
VIS_S_RESP=$(curl -s -w "\n%{http_code}" -X POST -u "${TEST_USER}:${TEST_PASS}" \
  -H "Content-Type: application/json" \
  -d "{\"name\":\"vis-shortcut\",\"target\":\"${VIS_F_ID}\",\"targetType\":\"FOLDER\",\"visibility\":\"PUBLIC\"}" \
  "${BASE_URL}/v3/files/shortcuts/")
VIS_S_HTTP=$(echo "${VIS_S_RESP}" | tail -1); VIS_S_BODY=$(echo "${VIS_S_RESP}" | head -1)
[[ "${VIS_S_HTTP}" == "201" ]] || api_fail "create shortcut (visibility=PUBLIC) → HTTP ${VIS_S_HTTP}. Body: ${VIS_S_BODY:0:300}"
VIS_S_ID=$(echo "${VIS_S_BODY}" | grep -oiE '"uuid"[[:space:]]*:[[:space:]]*"[0-9a-f-]{36}"' | grep -oiE '[0-9a-f-]{36}' | head -1)
[[ -n "${VIS_S_ID}" ]] || api_fail "no uuid in create-shortcut response. Body: ${VIS_S_BODY:0:300}"
CALL_NUM=$((CALL_NUM+1))
echo "  API call ${CALL_NUM}/${TOTAL_API_CALLS}: GET /v3/files/shortcuts/{id} (visibility populated)"
VIS_S_GET=$(curl -s -u "${TEST_USER}:${TEST_PASS}" "${BASE_URL}/v3/files/shortcuts/${VIS_S_ID}")
echo "${VIS_S_GET}" | grep -qE '"visibility"[[:space:]]*:[[:space:]]*"PUBLIC"' \
  || api_fail "GET shortcut did not report visibility=PUBLIC. Body: ${VIS_S_GET:0:300}"
api_pass "POST shortcut visibility=PUBLIC → 201; GET reports PUBLIC (shortcut ${VIS_S_ID})"

# --- Shortcut: update accepts visibility ---
CALL_NUM=$((CALL_NUM+1))
echo "  API call ${CALL_NUM}/${TOTAL_API_CALLS}: PUT /v3/files/shortcuts/{id} (visibility=PRIVATE)"
VIS_S_PUT=$(curl -s -o /dev/null -w "%{http_code}" -X PUT -u "${TEST_USER}:${TEST_PASS}" \
  -H "Content-Type: application/json" -d '{"visibility":"PRIVATE"}' \
  "${BASE_URL}/v3/files/shortcuts/${VIS_S_ID}")
[[ "${VIS_S_PUT}" == "204" || "${VIS_S_PUT}" == "200" ]] || api_fail "PUT shortcut visibility → HTTP ${VIS_S_PUT}"
CALL_NUM=$((CALL_NUM+1))
echo "  API call ${CALL_NUM}/${TOTAL_API_CALLS}: GET /v3/files/shortcuts/{id} (visibility now PRIVATE)"
VIS_S_GET2=$(curl -s -u "${TEST_USER}:${TEST_PASS}" "${BASE_URL}/v3/files/shortcuts/${VIS_S_ID}")
echo "${VIS_S_GET2}" | grep -qE '"visibility"[[:space:]]*:[[:space:]]*"PRIVATE"' \
  || api_fail "shortcut update did not change visibility to PRIVATE. Body: ${VIS_S_GET2:0:300}"
api_pass "PUT shortcut visibility=PRIVATE applied; GET reports PRIVATE"

# ── STEP: Visibility change fully reindexes in Solr (drop from old core, add to new) ──
# Proves the reviewer's concern on PR #129: a PRIVATE→PUBLIC update moves the entry between
# the private-nfs and public-nfs cores with no orphaned copy left in the old core. Search is
# async (soft commit ≤5s), so each assertion polls until convergence.
step "Visibility change reindexes folder/shortcut across Solr cores (no orphan)"

VM_FOLDER_NAME="vismovefolder${RANDOM}${RANDOM}"
VM_SHORTCUT_NAME="vismoveshortcut${RANDOM}${RANDOM}"

# --- Folder: create PRIVATE, confirm indexed in private-nfs ---
CALL_NUM=$((CALL_NUM+1))
echo "  API call ${CALL_NUM}/${TOTAL_API_CALLS}: POST /v3/files/folders/ (create PRIVATE for reindex-move test)"
VM_F_RESP=$(curl -s -w "\n%{http_code}" -X POST -u "${TEST_USER}:${TEST_PASS}" \
  -H "Content-Type: application/json" -d "{\"name\":\"${VM_FOLDER_NAME}\"}" \
  "${BASE_URL}/v3/files/folders/")
VM_F_HTTP=$(echo "${VM_F_RESP}" | tail -1); VM_F_BODY=$(echo "${VM_F_RESP}" | head -1)
[[ "${VM_F_HTTP}" == "201" ]] || api_fail "create move-test folder → HTTP ${VM_F_HTTP}. Body: ${VM_F_BODY:0:300}"
VM_F_ID=$(echo "${VM_F_BODY}" | grep -oiE '"uuid"[[:space:]]*:[[:space:]]*"[0-9a-f-]{36}"' | grep -oiE '[0-9a-f-]{36}' | head -1)
[[ -n "${VM_F_ID}" ]] || api_fail "no uuid in move-test folder create body. Body: ${VM_F_BODY:0:300}"

CALL_NUM=$((CALL_NUM+1))
echo "  API call ${CALL_NUM}/${TOTAL_API_CALLS}: search files visibility=PRIVATE (folder indexed in private-nfs)"
poll_files_until_present "PRIVATE" "${VM_FOLDER_NAME}" "${VM_F_ID}" "folder pre-move"
api_pass "folder ${VM_F_ID} indexed under PRIVATE (private-nfs)"

# --- Folder: flip to PUBLIC, confirm moved to public-nfs and dropped from private-nfs ---
CALL_NUM=$((CALL_NUM+1))
echo "  API call ${CALL_NUM}/${TOTAL_API_CALLS}: PUT /v3/files/folders/{id} visibility=PUBLIC"
VM_F_PUT=$(curl -s -o /dev/null -w "%{http_code}" -X PUT -u "${TEST_USER}:${TEST_PASS}" \
  -H "Content-Type: application/json" -d '{"visibility":"PUBLIC"}' \
  "${BASE_URL}/v3/files/folders/${VM_F_ID}")
[[ "${VM_F_PUT}" == "204" || "${VM_F_PUT}" == "200" ]] || api_fail "PUT move-test folder visibility → HTTP ${VM_F_PUT}"

CALL_NUM=$((CALL_NUM+1))
echo "  API call ${CALL_NUM}/${TOTAL_API_CALLS}: search files visibility=PUBLIC (folder now in public-nfs)"
poll_files_until_present "PUBLIC" "${VM_FOLDER_NAME}" "${VM_F_ID}" "folder post-move (new core)"
CALL_NUM=$((CALL_NUM+1))
echo "  API call ${CALL_NUM}/${TOTAL_API_CALLS}: search files visibility=PRIVATE (folder dropped from private-nfs)"
poll_files_until_absent "PRIVATE" "${VM_FOLDER_NAME}" "${VM_F_ID}" "folder post-move (old core)"
api_pass "folder visibility PRIVATE→PUBLIC fully reindexed: present in public-nfs, absent from private-nfs (no orphan)"

# --- Shortcut: create PRIVATE (target the move-test folder), confirm indexed in private-nfs ---
CALL_NUM=$((CALL_NUM+1))
echo "  API call ${CALL_NUM}/${TOTAL_API_CALLS}: POST /v3/files/shortcuts/ (create PRIVATE for reindex-move test)"
VM_S_RESP=$(curl -s -w "\n%{http_code}" -X POST -u "${TEST_USER}:${TEST_PASS}" \
  -H "Content-Type: application/json" \
  -d "{\"name\":\"${VM_SHORTCUT_NAME}\",\"target\":\"${VM_F_ID}\",\"targetType\":\"FOLDER\"}" \
  "${BASE_URL}/v3/files/shortcuts/")
VM_S_HTTP=$(echo "${VM_S_RESP}" | tail -1); VM_S_BODY=$(echo "${VM_S_RESP}" | head -1)
[[ "${VM_S_HTTP}" == "201" ]] || api_fail "create move-test shortcut → HTTP ${VM_S_HTTP}. Body: ${VM_S_BODY:0:300}"
VM_S_ID=$(echo "${VM_S_BODY}" | grep -oiE '"uuid"[[:space:]]*:[[:space:]]*"[0-9a-f-]{36}"' | grep -oiE '[0-9a-f-]{36}' | head -1)
[[ -n "${VM_S_ID}" ]] || api_fail "no uuid in move-test shortcut create body. Body: ${VM_S_BODY:0:300}"

CALL_NUM=$((CALL_NUM+1))
echo "  API call ${CALL_NUM}/${TOTAL_API_CALLS}: search files visibility=PRIVATE (shortcut indexed in private-nfs)"
poll_files_until_present "PRIVATE" "${VM_SHORTCUT_NAME}" "${VM_S_ID}" "shortcut pre-move"
api_pass "shortcut ${VM_S_ID} indexed under PRIVATE (private-nfs)"

# --- Shortcut: flip to PUBLIC, confirm moved to public-nfs and dropped from private-nfs ---
CALL_NUM=$((CALL_NUM+1))
echo "  API call ${CALL_NUM}/${TOTAL_API_CALLS}: PUT /v3/files/shortcuts/{id} visibility=PUBLIC"
VM_S_PUT=$(curl -s -o /dev/null -w "%{http_code}" -X PUT -u "${TEST_USER}:${TEST_PASS}" \
  -H "Content-Type: application/json" -d '{"visibility":"PUBLIC"}' \
  "${BASE_URL}/v3/files/shortcuts/${VM_S_ID}")
[[ "${VM_S_PUT}" == "204" || "${VM_S_PUT}" == "200" ]] || api_fail "PUT move-test shortcut visibility → HTTP ${VM_S_PUT}"

CALL_NUM=$((CALL_NUM+1))
echo "  API call ${CALL_NUM}/${TOTAL_API_CALLS}: search files visibility=PUBLIC (shortcut now in public-nfs)"
poll_files_until_present "PUBLIC" "${VM_SHORTCUT_NAME}" "${VM_S_ID}" "shortcut post-move (new core)"
CALL_NUM=$((CALL_NUM+1))
echo "  API call ${CALL_NUM}/${TOTAL_API_CALLS}: search files visibility=PRIVATE (shortcut dropped from private-nfs)"
poll_files_until_absent "PRIVATE" "${VM_SHORTCUT_NAME}" "${VM_S_ID}" "shortcut post-move (old core)"
api_pass "shortcut visibility PRIVATE→PUBLIC fully reindexed: present in public-nfs, absent from private-nfs (no orphan)"

# ── STEP: /v2/networkset round-trip on the folder-backed compatibility layer ──
# The network set feature is retired as storage but preserved as an API: a network set id IS a folder
# id, and every /v2/networkset endpoint performs folder/shortcut operations internally. So this step
# drives the whole legacy lifecycle against live endpoints — create, add members, update, read,
# access key, remove members, delete — and after each write cross-checks the v3 folder endpoints,
# proving both surfaces see one dataset rather than two. Nothing here needs seeded data: earlier
# releases had to INSERT into the frozen network_set tables because no endpoint could create a set.
# Runs BEFORE the AUTHENTICATED_USER_ONLY flip below so the anonymous read paths are still exercised.

if [[ -z "${REMOTE_NDEX_URL}" ]]; then
  step "/v2/networkset round-trip (folder-backed: create → members → read → key → delete)"

  # Deliberately disjoint tokens, not "X" and "X (renamed)": the rename assertions check that the OLD
  # name stops matching in Solr, which a name containing the old one as a substring could never show.
  NS_NAME="NSRoundtripAlpha"
  NS_NAME_2="NSRoundtripBeta"
  NS_DESC="folder-backed networkset round-trip"

  NS_OWNER_ID=$(curl -s "${BASE_URL}/v2/user?username=${TEST_USER}" \
    | grep -oiE '"externalId"[[:space:]]*:[[:space:]]*"[0-9a-f-]{36}"' | grep -oiE '[0-9a-f-]{36}' | head -1)
  [[ -n "${NS_OWNER_ID}" ]] || api_fail "could not resolve ${TEST_USER} UUID from GET /v2/user?username"

  # ── 1) POST /v2/networkset → creates a FOLDER at the owner's home root ──────────────────────────
  CALL_NUM=$((CALL_NUM+1))
  echo "  API call ${CALL_NUM}/${TOTAL_API_CALLS}: POST /v2/networkset (create) — expect 201"
  NS_CREATE=$(curl -s -w "\n%{http_code}" -X POST -u "${TEST_USER}:${TEST_PASS}" \
    -H "Content-Type: application/json" \
    -d "{\"name\":\"${NS_NAME}\",\"description\":\"${NS_DESC}\"}" "${BASE_URL}/v2/networkset")
  NS_CREATE_HTTP=$(echo "${NS_CREATE}" | tail -1); NS_CREATE_BODY=$(echo "${NS_CREATE}" | head -1)
  [[ "${NS_CREATE_HTTP}" == "201" ]] \
    || api_fail "POST /v2/networkset → HTTP ${NS_CREATE_HTTP} (expected 201). Body: ${NS_CREATE_BODY:0:400}"
  NS_ID=$(echo "${NS_CREATE_BODY}" | grep -oiE '[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}' | head -1)
  [[ -n "${NS_ID}" ]] || api_fail "POST /v2/networkset returned no set id. Body: ${NS_CREATE_BODY:0:400}"
  api_pass "POST /v2/networkset → 201, set ${NS_ID} created"

  # The set id must BE a folder id, with the posted name/description and no parent (home root).
  CALL_NUM=$((CALL_NUM+1))
  echo "  API call ${CALL_NUM}/${TOTAL_API_CALLS}: GET /v3/files/folders/${NS_ID} — same object via v3"
  NS_F=$(curl -s -w "\n%{http_code}" -u "${TEST_USER}:${TEST_PASS}" "${BASE_URL}/v3/files/folders/${NS_ID}")
  NS_F_HTTP=$(echo "${NS_F}" | tail -1); NS_F_BODY=$(echo "${NS_F}" | head -1)
  [[ "${NS_F_HTTP}" == "200" ]] \
    || api_fail "GET /v3/files/folders/{setid} → HTTP ${NS_F_HTTP} (a set id must be a folder id). Body: ${NS_F_BODY:0:400}"
  echo "${NS_F_BODY}" | grep -q "${NS_NAME}" || api_fail "v3 folder is missing the posted name. Body: ${NS_F_BODY:0:400}"
  echo "${NS_F_BODY}" | grep -q "${NS_DESC}" || api_fail "v3 folder is missing the posted description. Body: ${NS_F_BODY:0:400}"
  NS_PARENT=$(psql_ndex "SELECT COALESCE(parent::text,'NULL') FROM folder WHERE \\\"UUID\\\"='${NS_ID}';")
  [[ "${NS_PARENT}" == "NULL" ]] || api_fail "a new set must sit at home root (parent IS NULL), got '${NS_PARENT}'"
  api_pass "POST /v2/networkset created a v3 folder at home root with the posted name/description"

  # Solr: v2-created sets must be searchable through v3. Indexing is async, hence the poll.
  poll_files_until_present PRIVATE "${NS_NAME}" "${NS_ID}" "networkset create indexing"
  api_pass "new set is indexed in private-nfs and findable via POST /v3/search/files"

  # ── 2) POST /{id}/members → adds each network as a SHORTCUT ─────────────────────────────────────
  CALL_NUM=$((CALL_NUM+1))
  echo "  API call ${CALL_NUM}/${TOTAL_API_CALLS}: POST /v2/networkset/${NS_ID}/members (2 networks) — expect 201"
  NS_ADD_HTTP=$(curl -s -o /dev/null -w "%{http_code}" -X POST -u "${TEST_USER}:${TEST_PASS}" \
    -H "Content-Type: application/json" \
    -d "[\"${V2_PUB_UUID}\",\"${V2_PRIV_UUID}\"]" "${BASE_URL}/v2/networkset/${NS_ID}/members")
  [[ "${NS_ADD_HTTP}" == "201" ]] || api_fail "POST /v2/networkset/{id}/members → HTTP ${NS_ADD_HTTP} (expected 201)"
  api_pass "POST /v2/networkset/{id}/members → 201, 2 networks added"

  # v3 must report one NETWORK-target shortcut per posted network, named after the NETWORK (not its
  # UUID — earlier releases named shortcuts networkId.toString()), and count them.
  CALL_NUM=$((CALL_NUM+1))
  echo "  API call ${CALL_NUM}/${TOTAL_API_CALLS}: GET /v3/files/folders/${NS_ID}/list — shortcuts per member"
  NS_LIST=$(curl -s -u "${TEST_USER}:${TEST_PASS}" "${BASE_URL}/v3/files/folders/${NS_ID}/list?format=compact")
  echo "${NS_LIST}" | grep -q "SHORTCUT" || api_fail "v3 list shows no shortcut children. Body: ${NS_LIST:0:500}"
  echo "${NS_LIST}" | grep -q "${V2_PUB_UUID}" || api_fail "v3 list is missing a shortcut to ${V2_PUB_UUID}. Body: ${NS_LIST:0:500}"
  echo "${NS_LIST}" | grep -q "${V2_PRIV_UUID}" || api_fail "v3 list is missing a shortcut to ${V2_PRIV_UUID}. Body: ${NS_LIST:0:500}"
  NS_SC_PUB=$(psql_ndex "SELECT \\\"UUID\\\" FROM shortcut WHERE parent='${NS_ID}' AND target='${V2_PUB_UUID}' AND is_deleted=false;")
  NS_SC_PRIV=$(psql_ndex "SELECT \\\"UUID\\\" FROM shortcut WHERE parent='${NS_ID}' AND target='${V2_PRIV_UUID}' AND is_deleted=false;")
  [[ -n "${NS_SC_PUB}" && -n "${NS_SC_PRIV}" ]] || api_fail "expected a shortcut row per member (got '${NS_SC_PUB}' / '${NS_SC_PRIV}')"
  NS_SC_NAME=$(psql_ndex "SELECT name FROM shortcut WHERE \\\"UUID\\\"='${NS_SC_PUB}';")
  [[ "${NS_SC_NAME}" != "${V2_PUB_UUID}" ]] \
    || api_fail "member shortcut is named after the target UUID; it must be named after the network"
  api_pass "members are v3 shortcuts named after their target network, visible in /v3/files/folders/{id}/list"

  CALL_NUM=$((CALL_NUM+1))
  echo "  API call ${CALL_NUM}/${TOTAL_API_CALLS}: GET /v3/files/folders/${NS_ID}/count — shortcut=2"
  NS_COUNT=$(curl -s -u "${TEST_USER}:${TEST_PASS}" "${BASE_URL}/v3/files/folders/${NS_ID}/count")
  echo "${NS_COUNT}" | grep -qE '"shortcut"[[:space:]]*:[[:space:]]*2' \
    || api_fail "expected shortcut count 2 in /count. Body: ${NS_COUNT:0:300}"
  api_pass "GET /v3/files/folders/{id}/count reports shortcut=2"

  # Each member shortcut must be indexed in its own right, not just the folder.
  poll_files_until_present PRIVATE "${NS_SC_PUB}" "${NS_SC_PUB}" "member shortcut indexing"
  api_pass "member shortcuts are indexed individually in private-nfs"

  # Rejects the whole request when any posted id is unreadable — nothing partially created.
  CALL_NUM=$((CALL_NUM+1))
  echo "  API call ${CALL_NUM}/${TOTAL_API_CALLS}: POST /v2/networkset/${NS_ID}/members with an unreadable id — expect 4xx"
  NS_BAD_UUID="66666666-6666-6666-6666-666666666666"
  NS_BAD_HTTP=$(curl -s -o /dev/null -w "%{http_code}" -X POST -u "${TEST_USER}:${TEST_PASS}" \
    -H "Content-Type: application/json" \
    -d "[\"${V2_PUB_UUID}\",\"${NS_BAD_UUID}\"]" "${BASE_URL}/v2/networkset/${NS_ID}/members")
  [[ "${NS_BAD_HTTP}" =~ ^4 ]] || api_fail "POST members with an unreadable id → HTTP ${NS_BAD_HTTP} (expected 4xx)"
  NS_SC_TOTAL=$(psql_ndex "SELECT count(*) FROM shortcut WHERE parent='${NS_ID}' AND is_deleted=false;")
  [[ "${NS_SC_TOTAL}" == "2" ]] \
    || api_fail "a rejected member list must create nothing; shortcut count is ${NS_SC_TOTAL} (expected 2)"
  api_pass "POST members validates every target first: one bad id rejects the request and creates nothing"

  # ── 3) PUT /{id} → renames the folder, preserving its parent ────────────────────────────────────
  assert_networkset_ok PUT "${BASE_URL}/v2/networkset/${NS_ID}" 204 \
    -H "Content-Type: application/json" -d "{\"name\":\"${NS_NAME_2}\"}"

  CALL_NUM=$((CALL_NUM+1))
  echo "  API call ${CALL_NUM}/${TOTAL_API_CALLS}: GET /v3/files/folders/${NS_ID} — renamed, parent unchanged"
  NS_F2=$(curl -s -u "${TEST_USER}:${TEST_PASS}" "${BASE_URL}/v3/files/folders/${NS_ID}")
  echo "${NS_F2}" | grep -q "${NS_NAME_2}" || api_fail "v3 folder does not show the new name. Body: ${NS_F2:0:400}"
  NS_PARENT2=$(psql_ndex "SELECT COALESCE(parent::text,'NULL') FROM folder WHERE \\\"UUID\\\"='${NS_ID}';")
  [[ "${NS_PARENT2}" == "NULL" ]] \
    || api_fail "PUT must preserve the parent; it changed to '${NS_PARENT2}' (a nested set would be moved to root)"
  api_pass "PUT /v2/networkset/{id} renamed the folder and left its parent untouched"

  # A rename must drop the stale doc from BOTH cores before re-indexing, so the old name stops matching.
  poll_files_until_present PRIVATE "${NS_NAME_2}" "${NS_ID}" "networkset rename indexing"
  poll_files_until_absent PRIVATE "${NS_NAME}" "${NS_ID}" "networkset rename stale doc"
  api_pass "rename re-indexed the set: new name matches, old name no longer does"

  # PUT is an upsert, so it has to tell apart "this id is free" from "this id is taken by someone else".
  # A non-owner must get 401 rather than a 500 from a primary-key violation on an attempted create, and
  # the rejected request must not have written anything.
  CALL_NUM=$((CALL_NUM+1))
  echo "  API call ${CALL_NUM}/${TOTAL_API_CALLS}: PUT /v2/networkset/${NS_ID} (non-owner ${TEST_USER2}) — 401, not 500"
  NS_PUT_OTHER_HTTP=$(curl -s -o /dev/null -w "%{http_code}" -X PUT -u "${TEST_USER2}:${TEST_PASS2}" \
    -H "Content-Type: application/json" -d '{"name":"hijacked"}' "${BASE_URL}/v2/networkset/${NS_ID}")
  [[ "${NS_PUT_OTHER_HTTP}" == "401" ]] \
    || api_fail "non-owner PUT → HTTP ${NS_PUT_OTHER_HTTP} (expected 401; a 500 means it tried to create over an existing id)"
  NS_NAME_AFTER=$(psql_ndex "SELECT name FROM folder WHERE \\\"UUID\\\"='${NS_ID}';")
  [[ "${NS_NAME_AFTER}" == "${NS_NAME_2}" ]] \
    || api_fail "a rejected PUT must write nothing, but the folder name is now '${NS_NAME_AFTER}'"
  api_pass "PUT /v2/networkset/{id} (non-owner) → 401 and wrote nothing (upsert does not create over a taken id)"

  # ── 4) PUT /{id}/accesskey → enables/disables the FOLDER's key ──────────────────────────────────
  CALL_NUM=$((CALL_NUM+1))
  echo "  API call ${CALL_NUM}/${TOTAL_API_CALLS}: PUT /v2/networkset/${NS_ID}/accesskey?action=enable — expect 200 + key"
  NS_KEY_BODY=$(curl -s -u "${TEST_USER}:${TEST_PASS}" -X PUT "${BASE_URL}/v2/networkset/${NS_ID}/accesskey?action=enable")
  NS_KEY=$(echo "${NS_KEY_BODY}" | grep -oE '"accessKey"[[:space:]]*:[[:space:]]*"[^"]+"' | sed 's/.*"\([^"]*\)"$/\1/')
  [[ -n "${NS_KEY}" ]] || api_fail "enable did not return an accessKey. Body: ${NS_KEY_BODY:0:300}"
  api_pass "PUT /v2/networkset/{id}/accesskey?action=enable → 200, key issued"

  # The v2 and v3 key surfaces are the same folder row.
  CALL_NUM=$((CALL_NUM+1))
  echo "  API call ${CALL_NUM}/${TOTAL_API_CALLS}: GET /v3/files/folders/${NS_ID}/accesskey — identical key"
  NS_V3KEY=$(curl -s -u "${TEST_USER}:${TEST_PASS}" "${BASE_URL}/v3/files/folders/${NS_ID}/accesskey")
  echo "${NS_V3KEY}" | grep -q "${NS_KEY}" \
    || api_fail "v3 folder accesskey differs from the v2 network set key. Body: ${NS_V3KEY:0:300}"
  api_pass "the v2 network set key and the v3 folder key are the same value"

  # Disable preserves the key value, so re-enabling returns the same string (idempotent enable).
  CALL_NUM=$((CALL_NUM+1))
  echo "  API call ${CALL_NUM}/${TOTAL_API_CALLS}: PUT /v2/networkset/${NS_ID}/accesskey?action=disable — expect 204"
  NS_DIS_HTTP=$(curl -s -o /dev/null -w "%{http_code}" -u "${TEST_USER}:${TEST_PASS}" -X PUT \
    "${BASE_URL}/v2/networkset/${NS_ID}/accesskey?action=disable")
  [[ "${NS_DIS_HTTP}" == "204" ]] || api_fail "disable → HTTP ${NS_DIS_HTTP} (expected 204)"
  CALL_NUM=$((CALL_NUM+1))
  echo "  API call ${CALL_NUM}/${TOTAL_API_CALLS}: PUT /v2/networkset/${NS_ID}/accesskey?action=enable — same key back"
  NS_KEY2=$(curl -s -u "${TEST_USER}:${TEST_PASS}" -X PUT "${BASE_URL}/v2/networkset/${NS_ID}/accesskey?action=enable" \
    | grep -oE '"accessKey"[[:space:]]*:[[:space:]]*"[^"]+"' | sed 's/.*"\([^"]*\)"$/\1/')
  [[ "${NS_KEY2}" == "${NS_KEY}" ]] \
    || api_fail "re-enabling must return the same key ('${NS_KEY2}' vs '${NS_KEY}')"
  api_pass "disable preserves the key value; re-enabling is idempotent and returns the same string"

  # An invalid action is rejected rather than being treated as one of the two.
  CALL_NUM=$((CALL_NUM+1))
  echo "  API call ${CALL_NUM}/${TOTAL_API_CALLS}: PUT /v2/networkset/${NS_ID}/accesskey?action=bogus — expect 4xx"
  NS_ACT_HTTP=$(curl -s -o /dev/null -w "%{http_code}" -u "${TEST_USER}:${TEST_PASS}" -X PUT \
    "${BASE_URL}/v2/networkset/${NS_ID}/accesskey?action=bogus")
  [[ "${NS_ACT_HTTP}" =~ ^4 ]] || api_fail "action=bogus → HTTP ${NS_ACT_HTTP} (expected 4xx)"
  api_pass "PUT accesskey rejects an action other than enable/disable"

  # ── 5) GET /{id} → folder read, members = shortcut targets + real children ──────────────────────
  # A set IS a folder, so folder visibility governs the read. New sets are created PRIVATE (as were all
  # sets the v3 migration converted), so an anonymous read is refused. This is a deliberate departure
  # from the legacy behavior, where network_set had no visibility column and every set header was world
  # readable: serving the same row under weaker rules on /v2 than on /v3 would let anyone who knows a
  # folder id read its name and description without authenticating.
  # The three requests below hit the SAME url against the SAME PRIVATE folder and differ only in who is
  # asking. That is the assertion: access is decided by the underlying folder's visibility plus the
  # caller's rights to it, not by anything network-set specific. Confirm the stored visibility first, so
  # a later default change cannot make these pass for the wrong reason.
  NS_VIS_DB=$(psql_ndex "SELECT visibility FROM folder WHERE \\\"UUID\\\"='${NS_ID}';")
  [[ "${NS_VIS_DB}" == "PRIVATE" ]] \
    || api_fail "expected the folder behind the set to be PRIVATE, got '${NS_VIS_DB}' — the visibility assertions below would be meaningless"

  CALL_NUM=$((CALL_NUM+1))
  echo "  API call ${CALL_NUM}/${TOTAL_API_CALLS}: GET /v2/networkset/${NS_ID} (anon, folder is PRIVATE) — 401"
  NS_ANON_PRIV_HTTP=$(curl -s -o /dev/null -w "%{http_code}" "${BASE_URL}/v2/networkset/${NS_ID}")
  [[ "${NS_ANON_PRIV_HTTP}" == "401" ]] \
    || api_fail "anon read of a PRIVATE set → HTTP ${NS_ANON_PRIV_HTTP} (expected 401; folder visibility governs)"

  # A signed-in NON-OWNER with no permission on the folder is refused too. This is what proves the gate is
  # folder read-access, not merely "reject anonymous".
  CALL_NUM=$((CALL_NUM+1))
  echo "  API call ${CALL_NUM}/${TOTAL_API_CALLS}: GET /v2/networkset/${NS_ID} (non-owner ${TEST_USER2}, PRIVATE) — 401"
  NS_OTHER_PRIV_HTTP=$(curl -s -o /dev/null -w "%{http_code}" -u "${TEST_USER2}:${TEST_PASS2}" \
    "${BASE_URL}/v2/networkset/${NS_ID}")
  [[ "${NS_OTHER_PRIV_HTTP}" == "401" ]] \
    || api_fail "non-owner read of a PRIVATE set → HTTP ${NS_OTHER_PRIV_HTTP} (expected 401)"

  # The owner reading that identical url succeeds, and gets the real content.
  CALL_NUM=$((CALL_NUM+1))
  echo "  API call ${CALL_NUM}/${TOTAL_API_CALLS}: GET /v2/networkset/${NS_ID} (owner, same PRIVATE set) — 200"
  NS_OWNER_PRIV=$(curl -s -w "\n%{http_code}" -u "${TEST_USER}:${TEST_PASS}" "${BASE_URL}/v2/networkset/${NS_ID}")
  NS_OWNER_PRIV_HTTP=$(echo "${NS_OWNER_PRIV}" | tail -1); NS_OWNER_PRIV_BODY=$(echo "${NS_OWNER_PRIV}" | head -1)
  [[ "${NS_OWNER_PRIV_HTTP}" == "200" ]] \
    || api_fail "the OWNER must be able to read their own PRIVATE set → HTTP ${NS_OWNER_PRIV_HTTP}. Body: ${NS_OWNER_PRIV_BODY:0:400}"
  echo "${NS_OWNER_PRIV_BODY}" | grep -q "${NS_NAME_2}" \
    || api_fail "owner read of a PRIVATE set returned no set name. Body: ${NS_OWNER_PRIV_BODY:0:400}"
  echo "${NS_OWNER_PRIV_BODY}" | grep -q "${V2_PRIV_UUID}" \
    || api_fail "owner read of a PRIVATE set is missing their own PRIVATE member. Body: ${NS_OWNER_PRIV_BODY:0:400}"
  api_pass "same PRIVATE set, same url: anon → 401, non-owner → 401, owner → 200 with content (the underlying folder's visibility governs access)"

  # Making the folder PUBLIC is the supported way to restore anonymous set reads. Member filtering still
  # applies: a PUBLIC set does not expose the caller's unreadable members.
  CALL_NUM=$((CALL_NUM+1))
  echo "  API call ${CALL_NUM}/${TOTAL_API_CALLS}: PUT /v3/files/folders/${NS_ID} visibility=PUBLIC"
  NS_VIS_HTTP=$(curl -s -o /dev/null -w "%{http_code}" -X PUT -u "${TEST_USER}:${TEST_PASS}" \
    -H "Content-Type: application/json" \
    -d "{\"name\":\"${NS_NAME_2}\",\"visibility\":\"PUBLIC\"}" "${BASE_URL}/v3/files/folders/${NS_ID}")
  [[ "${NS_VIS_HTTP}" =~ ^2 ]] || api_fail "PUT folder visibility=PUBLIC → HTTP ${NS_VIS_HTTP}"

  CALL_NUM=$((CALL_NUM+1))
  echo "  API call ${CALL_NUM}/${TOTAL_API_CALLS}: GET /v2/networkset/${NS_ID} (anon, set now PUBLIC) — public member only"
  NS_ANON=$(curl -s -w "\n%{http_code}" "${BASE_URL}/v2/networkset/${NS_ID}")
  NS_ANON_HTTP=$(echo "${NS_ANON}" | tail -1); NS_ANON_BODY=$(echo "${NS_ANON}" | head -1)
  [[ "${NS_ANON_HTTP}" == "200" ]] || api_fail "GET /v2/networkset (anon, PUBLIC) → HTTP ${NS_ANON_HTTP}. Body: ${NS_ANON_BODY:0:400}"
  echo "${NS_ANON_BODY}" | grep -q "${NS_NAME_2}" || api_fail "anon read missing set name. Body: ${NS_ANON_BODY:0:400}"
  echo "${NS_ANON_BODY}" | grep -q "${V2_PUB_UUID}" || api_fail "anon read missing PUBLIC member. Body: ${NS_ANON_BODY:0:400}"
  echo "${NS_ANON_BODY}" | grep -q "${V2_PRIV_UUID}" && api_fail "anon read LEAKED PRIVATE member. Body: ${NS_ANON_BODY:0:400}"
  api_pass "GET /v2/networkset (anon) on a PUBLIC set → 200, PUBLIC member only (PRIVATE filtered)"

  # Restore PRIVATE so the delete-path assertions below exercise the default shape.
  curl -s -o /dev/null -X PUT -u "${TEST_USER}:${TEST_PASS}" -H "Content-Type: application/json" \
    -d "{\"name\":\"${NS_NAME_2}\",\"visibility\":\"PRIVATE\"}" "${BASE_URL}/v3/files/folders/${NS_ID}"

  CALL_NUM=$((CALL_NUM+1))
  echo "  API call ${CALL_NUM}/${TOTAL_API_CALLS}: GET /v2/networkset/${NS_ID}?accesskey (anon) — key-unlocked members"
  NS_KEYED=$(curl -s -w "\n%{http_code}" "${BASE_URL}/v2/networkset/${NS_ID}?accesskey=${NS_KEY}")
  NS_KEYED_HTTP=$(echo "${NS_KEYED}" | tail -1); NS_KEYED_BODY=$(echo "${NS_KEYED}" | head -1)
  [[ "${NS_KEYED_HTTP}" == "200" ]] || api_fail "GET /v2/networkset?accesskey (anon) → HTTP ${NS_KEYED_HTTP}. Body: ${NS_KEYED_BODY:0:400}"
  # The key reaches same-owner network shortcuts, so both members unlock (both belong to TEST_USER).
  echo "${NS_KEYED_BODY}" | grep -q "${V2_PUB_UUID}" || api_fail "keyed read missing PUBLIC member. Body: ${NS_KEYED_BODY:0:400}"
  echo "${NS_KEYED_BODY}" | grep -q "${V2_PRIV_UUID}" || api_fail "keyed read missing PRIVATE member. Body: ${NS_KEYED_BODY:0:400}"
  api_pass "GET /v2/networkset?accesskey (anon) → 200, the key unlocks its same-owner member networks"

  # The same key must reach the member NETWORK itself, which is what keeps pre-migration set keys
  # working: the resolver seeds the ancestor walk from same-owner NETWORK shortcuts.
  CALL_NUM=$((CALL_NUM+1))
  echo "  API call ${CALL_NUM}/${TOTAL_API_CALLS}: GET /v2/network/${V2_PRIV_UUID}?accesskey (anon) — key reaches the member"
  NS_NETKEY_HTTP=$(curl -s -o /dev/null -w "%{http_code}" "${BASE_URL}/v2/network/${V2_PRIV_UUID}/summary?accesskey=${NS_KEY}")
  [[ "${NS_NETKEY_HTTP}" == "200" ]] \
    || api_fail "a set's key must reach its member networks through their shortcuts → HTTP ${NS_NETKEY_HTTP}"
  api_pass "a network set access key still reaches member networks via same-owner shortcuts (#133/#137)"

  CALL_NUM=$((CALL_NUM+1))
  # The owner's 200 on a PRIVATE set is already asserted above; this checks the legacy-only fields, which
  # have no folder equivalent and must be reported as defaults rather than invented.
  echo "  API call ${CALL_NUM}/${TOTAL_API_CALLS}: GET /v2/networkset/${NS_ID} (owner) — legacy field defaults"
  NS_OWNER=$(curl -s -w "\n%{http_code}" -u "${TEST_USER}:${TEST_PASS}" "${BASE_URL}/v2/networkset/${NS_ID}")
  NS_OWNER_HTTP=$(echo "${NS_OWNER}" | tail -1); NS_OWNER_BODY=$(echo "${NS_OWNER}" | head -1)
  [[ "${NS_OWNER_HTTP}" == "200" ]] || api_fail "GET /v2/networkset (owner) → HTTP ${NS_OWNER_HTTP}. Body: ${NS_OWNER_BODY:0:400}"
  echo "${NS_OWNER_BODY}" | grep -q "${V2_PUB_UUID}" || api_fail "owner read missing PUBLIC member. Body: ${NS_OWNER_BODY:0:400}"
  echo "${NS_OWNER_BODY}" | grep -q "${V2_PRIV_UUID}" || api_fail "owner read missing own PRIVATE member. Body: ${NS_OWNER_BODY:0:400}"
  echo "${NS_OWNER_BODY}" | grep -qE '"showcased"[[:space:]]*:[[:space:]]*true' \
    && api_fail "showcased must always be false on a folder-backed set. Body: ${NS_OWNER_BODY:0:400}"
  echo "${NS_OWNER_BODY}" | grep -qE '"properties"[[:space:]]*:[[:space:]]*\{\}' \
    || api_fail "properties should render as an empty object, not be omitted or populated. Body: ${NS_OWNER_BODY:0:400}"
  echo "${NS_OWNER_BODY}" | grep -q '"doi"' \
    && api_fail "doi has no folder equivalent and must be absent. Body: ${NS_OWNER_BODY:0:400}"
  api_pass "GET /v2/networkset (owner) → showcased=false, properties={}, doi absent (legacy-only fields not fabricated)"

  # ── 6) GET /{id}/accesskey → read-only, owner-only, 404 before 401 ──────────────────────────────
  CALL_NUM=$((CALL_NUM+1))
  echo "  API call ${CALL_NUM}/${TOTAL_API_CALLS}: GET /v2/networkset/${NS_ID}/accesskey (owner) — key returned"
  NS_AK=$(curl -s -w "\n%{http_code}" -u "${TEST_USER}:${TEST_PASS}" "${BASE_URL}/v2/networkset/${NS_ID}/accesskey")
  NS_AK_HTTP=$(echo "${NS_AK}" | tail -1); NS_AK_BODY=$(echo "${NS_AK}" | head -1)
  [[ "${NS_AK_HTTP}" == "200" ]] || api_fail "GET accesskey (owner) → HTTP ${NS_AK_HTTP}. Body: ${NS_AK_BODY:0:300}"
  echo "${NS_AK_BODY}" | grep -q "${NS_KEY}" || api_fail "GET accesskey did not return the key. Body: ${NS_AK_BODY:0:300}"
  api_pass "GET /v2/networkset/{id}/accesskey (owner) → 200, returns the folder's key"

  CALL_NUM=$((CALL_NUM+1))
  echo "  API call ${CALL_NUM}/${TOTAL_API_CALLS}: GET /v2/networkset/${NS_ID}/accesskey (non-owner) — 401"
  NS_AK2_HTTP=$(curl -s -o /dev/null -w "%{http_code}" -u "${TEST_USER2}:${TEST_PASS2}" \
    "${BASE_URL}/v2/networkset/${NS_ID}/accesskey")
  [[ "${NS_AK2_HTTP}" == "401" ]] || api_fail "GET accesskey (non-owner) → HTTP ${NS_AK2_HTTP} (expected 401)"
  api_pass "GET /v2/networkset/{id}/accesskey (non-owner) → 401 (only the owner may read the key)"

  CALL_NUM=$((CALL_NUM+1))
  NS_MISSING_ID="99999999-9999-9999-9999-999999999999"
  echo "  API call ${CALL_NUM}/${TOTAL_API_CALLS}: GET /v2/networkset/${NS_MISSING_ID}/accesskey — 404 not 401"
  NS_AK_MISS_HTTP=$(curl -s -o /dev/null -w "%{http_code}" -u "${TEST_USER}:${TEST_PASS}" \
    "${BASE_URL}/v2/networkset/${NS_MISSING_ID}/accesskey")
  [[ "${NS_AK_MISS_HTTP}" == "404" ]] || api_fail "GET accesskey (missing set) → HTTP ${NS_AK_MISS_HTTP} (expected 404)"
  api_pass "GET /v2/networkset/{id}/accesskey (missing set) → 404 (existence resolved before ownership)"

  # A GET must never mint a key as a side effect, which is what earlier releases did by calling
  # enableFolderAccessKey from the read path.
  CALL_NUM=$((CALL_NUM+1))
  echo "  API call ${CALL_NUM}/${TOTAL_API_CALLS}: POST /v2/networkset (2nd set) — GET accesskey must not create a key"
  NS_ID2=$(curl -s -X POST -u "${TEST_USER}:${TEST_PASS}" -H "Content-Type: application/json" \
    -d '{"name":"NS Keyless Set"}' "${BASE_URL}/v2/networkset" \
    | grep -oiE '[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}' | head -1)
  [[ -n "${NS_ID2}" ]] || api_fail "could not create the second network set"
  curl -s -o /dev/null -u "${TEST_USER}:${TEST_PASS}" "${BASE_URL}/v2/networkset/${NS_ID2}/accesskey"
  NS_KEYON=$(psql_ndex "SELECT access_key_is_on FROM folder WHERE \\\"UUID\\\"='${NS_ID2}';")
  [[ "${NS_KEYON}" == "f" ]] \
    || api_fail "GET accesskey enabled a key as a side effect (access_key_is_on='${NS_KEYON}')"
  api_pass "GET /v2/networkset/{id}/accesskey is read-only: it does not create or enable a key"

  # ── 7) GET /v2/user/{id}/networksets + networkcount ─────────────────────────────────────────────
  CALL_NUM=$((CALL_NUM+1))
  echo "  API call ${CALL_NUM}/${TOTAL_API_CALLS}: GET /v2/user/${NS_OWNER_ID}/networksets (owner) — both sets"
  NSU_OWNER=$(curl -s -w "\n%{http_code}" -u "${TEST_USER}:${TEST_PASS}" "${BASE_URL}/v2/user/${NS_OWNER_ID}/networksets")
  NSU_OWNER_HTTP=$(echo "${NSU_OWNER}" | tail -1); NSU_OWNER_BODY=$(echo "${NSU_OWNER}" | head -1)
  [[ "${NSU_OWNER_HTTP}" == "200" ]] || api_fail "GET /v2/user/{id}/networksets → HTTP ${NSU_OWNER_HTTP}. Body: ${NSU_OWNER_BODY:0:400}"
  echo "${NSU_OWNER_BODY}" | grep -q "${NS_ID}" || api_fail "set ${NS_ID} missing from the user's list. Body: ${NSU_OWNER_BODY:0:500}"
  echo "${NSU_OWNER_BODY}" | grep -q "${NS_ID2}" || api_fail "set ${NS_ID2} missing from the user's list. Body: ${NSU_OWNER_BODY:0:500}"
  api_pass "GET /v2/user/{id}/networksets (owner) → 200, lists the user's folder-backed sets"

  CALL_NUM=$((CALL_NUM+1))
  echo "  API call ${CALL_NUM}/${TOTAL_API_CALLS}: GET /v2/user/${NS_OWNER_ID}/networksets?summary=true — members omitted as []"
  NSU_SUM=$(curl -s -u "${TEST_USER}:${TEST_PASS}" "${BASE_URL}/v2/user/${NS_OWNER_ID}/networksets?summary=true")
  echo "${NSU_SUM}" | grep -q "${NS_ID}" || api_fail "summary list missing set ${NS_ID}. Body: ${NSU_SUM:0:400}"
  echo "${NSU_SUM}" | grep -q "${V2_PUB_UUID}" && api_fail "summary=true must omit member network ids. Body: ${NSU_SUM:0:400}"
  # Present-but-empty, not absent: NetworkSet pre-allocates the collection.
  echo "${NSU_SUM}" | grep -qE '"networks"[[:space:]]*:[[:space:]]*\[\]' \
    || api_fail "summary=true should render \"networks\":[] rather than omitting the field. Body: ${NSU_SUM:0:400}"
  api_pass "GET /v2/user/{id}/networksets?summary=true → set headers with \"networks\":[]"

  CALL_NUM=$((CALL_NUM+1))
  echo "  API call ${CALL_NUM}/${TOTAL_API_CALLS}: GET /v2/user/${NS_OWNER_ID}/networksets?showcase=true — documented no-op"
  NSU_SHOW=$(curl -s -u "${TEST_USER}:${TEST_PASS}" "${BASE_URL}/v2/user/${NS_OWNER_ID}/networksets?showcase=true")
  # Folders have no showcase flag, so the parameter cannot filter; it must not silently empty the list.
  echo "${NSU_SHOW}" | grep -q "${NS_ID}" \
    || api_fail "showcase=true is a documented no-op and must not filter the list. Body: ${NSU_SHOW:0:400}"
  api_pass "GET /v2/user/{id}/networksets?showcase=true → no-op filter (folders have no showcase flag)"

  # networkSetCount must agree with the unpaged list length, or an account page contradicts itself.
  CALL_NUM=$((CALL_NUM+1))
  echo "  API call ${CALL_NUM}/${TOTAL_API_CALLS}: GET /v2/user/${NS_OWNER_ID}/networkcount — networkSetCount agrees with the list"
  NS_CNT_BODY=$(curl -s -u "${TEST_USER}:${TEST_PASS}" "${BASE_URL}/v2/user/${NS_OWNER_ID}/networkcount")
  NS_SET_COUNT=$(echo "${NS_CNT_BODY}" | grep -oE '"networkSetCount"[[:space:]]*:[[:space:]]*[0-9]+' | grep -oE '[0-9]+$')
  [[ -n "${NS_SET_COUNT}" ]] || api_fail "networkcount response has no networkSetCount. Body: ${NS_CNT_BODY:0:300}"
  NS_LIST_LEN=$(curl -s -u "${TEST_USER}:${TEST_PASS}" "${BASE_URL}/v2/user/${NS_OWNER_ID}/networksets?summary=true" \
    | grep -oE '"externalId"' | wc -l | tr -d '[:space:]')
  [[ "${NS_SET_COUNT}" == "${NS_LIST_LEN}" ]] \
    || api_fail "networkSetCount (${NS_SET_COUNT}) must equal the networksets list length (${NS_LIST_LEN})"
  api_pass "networkSetCount (${NS_SET_COUNT}) equals the unpaged /networksets length"

  # ── 8) PUT /{id}/systemproperty → showcase is a documented no-op ────────────────────────────────
  NS_BEFORE=$(curl -s -u "${TEST_USER}:${TEST_PASS}" "${BASE_URL}/v2/networkset/${NS_ID}")
  assert_networkset_ok PUT "${BASE_URL}/v2/networkset/${NS_ID}/systemproperty" 204 \
    -H "Content-Type: application/json" -d '{"showcase":true}'
  NS_AFTER=$(curl -s -u "${TEST_USER}:${TEST_PASS}" "${BASE_URL}/v2/networkset/${NS_ID}")
  echo "${NS_AFTER}" | grep -qE '"showcased"[[:space:]]*:[[:space:]]*true' \
    && api_fail "showcase must be a no-op; the set now reports showcased=true"
  # A no-op writes nothing, so the whole representation — modificationTime included — must be identical.
  [[ "${NS_AFTER}" == "${NS_BEFORE}" ]] \
    || api_fail "systemproperty is a no-op but the set changed.\n  before: ${NS_BEFORE:0:300}\n  after:  ${NS_AFTER:0:300}"
  api_pass "PUT /v2/networkset/{id}/systemproperty accepts showcase and changes nothing (identical representation)"

  # ── 9) DELETE /{id}/members → shortcuts physically deleted; real children reparented ────────────
  CALL_NUM=$((CALL_NUM+1))
  echo "  API call ${CALL_NUM}/${TOTAL_API_CALLS}: DELETE /v2/networkset/${NS_ID}/members [private] — expect 204"
  NS_DELM_HTTP=$(curl -s -o /dev/null -w "%{http_code}" -X DELETE -u "${TEST_USER}:${TEST_PASS}" \
    -H "Content-Type: application/json" -d "[\"${V2_PRIV_UUID}\"]" "${BASE_URL}/v2/networkset/${NS_ID}/members")
  [[ "${NS_DELM_HTTP}" == "204" ]] || api_fail "DELETE members → HTTP ${NS_DELM_HTTP} (expected 204)"

  # The shortcut must be PHYSICALLY gone, not soft-deleted: a soft delete would leave a trash entry for
  # every network a client removed from a set.
  NS_SC_ROWS=$(psql_ndex "SELECT count(*) FROM shortcut WHERE \\\"UUID\\\"='${NS_SC_PRIV}';")
  [[ "${NS_SC_ROWS}" == "0" ]] \
    || api_fail "member shortcut row survives (count=${NS_SC_ROWS}); is_deleted=true means the soft path ran"
  CALL_NUM=$((CALL_NUM+1))
  echo "  API call ${CALL_NUM}/${TOTAL_API_CALLS}: GET /v3/files/shortcuts/${NS_SC_PRIV} — 404"
  NS_SC_HTTP=$(curl -s -o /dev/null -w "%{http_code}" -u "${TEST_USER}:${TEST_PASS}" \
    "${BASE_URL}/v3/files/shortcuts/${NS_SC_PRIV}")
  [[ "${NS_SC_HTTP}" == "404" ]] || api_fail "removed shortcut still resolves → HTTP ${NS_SC_HTTP} (expected 404)"
  CALL_NUM=$((CALL_NUM+1))
  echo "  API call ${CALL_NUM}/${TOTAL_API_CALLS}: GET /v3/files/trash — removed shortcut must NOT be there"
  NS_TRASH=$(curl -s -u "${TEST_USER}:${TEST_PASS}" "${BASE_URL}/v3/files/trash")
  echo "${NS_TRASH}" | grep -q "${NS_SC_PRIV}" \
    && api_fail "removing a member left a trash entry; the shortcut must be deleted permanently"
  # The member NETWORK itself is untouched — only the reference was removed.
  CALL_NUM=$((CALL_NUM+1))
  echo "  API call ${CALL_NUM}/${TOTAL_API_CALLS}: GET /v2/network/${V2_PRIV_UUID}/summary — network survives"
  NS_NET_HTTP=$(curl -s -o /dev/null -w "%{http_code}" -u "${TEST_USER}:${TEST_PASS}" \
    "${BASE_URL}/v2/network/${V2_PRIV_UUID}/summary")
  [[ "${NS_NET_HTTP}" == "200" ]] || api_fail "removing a member must not delete the network → HTTP ${NS_NET_HTTP}"
  NS_COUNT2=$(curl -s -u "${TEST_USER}:${TEST_PASS}" "${BASE_URL}/v3/files/folders/${NS_ID}/count")
  echo "${NS_COUNT2}" | grep -qE '"shortcut"[[:space:]]*:[[:space:]]*1' \
    || api_fail "shortcut count should drop to 1. Body: ${NS_COUNT2:0:300}"
  poll_files_until_absent PRIVATE "${NS_SC_PRIV}" "${NS_SC_PRIV}" "removed member shortcut de-indexing"
  api_pass "DELETE members deletes the shortcut permanently (no trash entry), leaves the network, de-indexes it"

  # A network parented directly in the set is data, not a reference: it is MOVED to home root.
  CALL_NUM=$((CALL_NUM+1))
  echo "  API call ${CALL_NUM}/${TOTAL_API_CALLS}: POST /v3/batch/networks/move ${V2_PRIV_UUID} into the set"
  NS_MOVE_HTTP=$(curl -s -o /dev/null -w "%{http_code}" -X POST -u "${TEST_USER}:${TEST_PASS}" \
    -H "Content-Type: application/json" \
    -d "{\"networks\":[\"${V2_PRIV_UUID}\"],\"targetFolder\":\"${NS_ID}\"}" "${BASE_URL}/v3/batch/networks/move")
  [[ "${NS_MOVE_HTTP}" =~ ^2 ]] || api_fail "batch move into the set → HTTP ${NS_MOVE_HTTP}"
  # The union read must surface it as a member even though no shortcut points at it.
  NS_UNION=$(curl -s -u "${TEST_USER}:${TEST_PASS}" "${BASE_URL}/v2/networkset/${NS_ID}")
  echo "${NS_UNION}" | grep -q "${V2_PRIV_UUID}" \
    || api_fail "a network parented in the set must appear as a member. Body: ${NS_UNION:0:400}"
  api_pass "a network moved into the set appears in 'networks' (members = shortcut targets + real children)"

  CALL_NUM=$((CALL_NUM+1))
  echo "  API call ${CALL_NUM}/${TOTAL_API_CALLS}: DELETE /v2/networkset/${NS_ID}/members [real child] — reparented to null"
  NS_DELM2_HTTP=$(curl -s -o /dev/null -w "%{http_code}" -X DELETE -u "${TEST_USER}:${TEST_PASS}" \
    -H "Content-Type: application/json" -d "[\"${V2_PRIV_UUID}\"]" "${BASE_URL}/v2/networkset/${NS_ID}/members")
  [[ "${NS_DELM2_HTTP}" == "204" ]] || api_fail "DELETE members (real child) → HTTP ${NS_DELM2_HTTP} (expected 204)"
  NS_NET_PARENT=$(psql_ndex "SELECT COALESCE(parent::text,'NULL') FROM network WHERE \\\"UUID\\\"='${V2_PRIV_UUID}';")
  [[ "${NS_NET_PARENT}" == "NULL" ]] \
    || api_fail "a real child network must be reparented to NULL (home root), parent is '${NS_NET_PARENT}'"
  NS_NET_ALIVE=$(psql_ndex "SELECT is_deleted FROM network WHERE \\\"UUID\\\"='${V2_PRIV_UUID}';")
  [[ "${NS_NET_ALIVE}" == "f" ]] || api_fail "removing a real child must not delete the network (is_deleted='${NS_NET_ALIVE}')"
  NS_UNION2=$(curl -s -u "${TEST_USER}:${TEST_PASS}" "${BASE_URL}/v2/networkset/${NS_ID}")
  echo "${NS_UNION2}" | grep -q "${V2_PRIV_UUID}" \
    && api_fail "the moved-out network must no longer be a member. Body: ${NS_UNION2:0:400}"
  CALL_NUM=$((CALL_NUM+1))
  echo "  API call ${CALL_NUM}/${TOTAL_API_CALLS}: GET /v3/users/${NS_OWNER_ID}/home — moved network is at home root"
  NS_HOME=$(curl -s -u "${TEST_USER}:${TEST_PASS}" "${BASE_URL}/v3/users/${NS_OWNER_ID}/home")
  echo "${NS_HOME}" | grep -q "${V2_PRIV_UUID}" \
    || api_fail "the moved network should now appear at home root. Body: ${NS_HOME:0:500}"
  api_pass "DELETE members moves a real child network to home root instead of deleting it"

  # ── 10) DELETE /{id} → trashes the folder and its remaining contents ────────────────────────────
  NS_SC_REMAINING=$(psql_ndex "SELECT \\\"UUID\\\" FROM shortcut WHERE parent='${NS_ID}' AND is_deleted=false;")
  CALL_NUM=$((CALL_NUM+1))
  echo "  API call ${CALL_NUM}/${TOTAL_API_CALLS}: DELETE /v2/networkset/${NS_ID} — expect 204"
  NS_DEL_HTTP=$(curl -s -o /dev/null -w "%{http_code}" -X DELETE -u "${TEST_USER}:${TEST_PASS}" \
    "${BASE_URL}/v2/networkset/${NS_ID}")
  [[ "${NS_DEL_HTTP}" == "204" ]] || api_fail "DELETE /v2/networkset/{id} → HTTP ${NS_DEL_HTTP} (expected 204)"

  CALL_NUM=$((CALL_NUM+1))
  echo "  API call ${CALL_NUM}/${TOTAL_API_CALLS}: GET /v2/networkset/${NS_ID} after delete — 404"
  NS_GONE_HTTP=$(curl -s -o /dev/null -w "%{http_code}" -u "${TEST_USER}:${TEST_PASS}" "${BASE_URL}/v2/networkset/${NS_ID}")
  [[ "${NS_GONE_HTTP}" == "404" ]] || api_fail "deleted set still readable → HTTP ${NS_GONE_HTTP} (expected 404)"

  # A trashed set must stay unreadable even to a valid access key: the folder read applies no
  # is_deleted filter and neither does key validation, so existence has to be resolved first.
  CALL_NUM=$((CALL_NUM+1))
  echo "  API call ${CALL_NUM}/${TOTAL_API_CALLS}: GET /v2/networkset/${NS_ID}?accesskey after delete — 404 not 200"
  NS_GONE_KEY_HTTP=$(curl -s -o /dev/null -w "%{http_code}" "${BASE_URL}/v2/networkset/${NS_ID}?accesskey=${NS_KEY}")
  [[ "${NS_GONE_KEY_HTTP}" == "404" ]] \
    || api_fail "a trashed set is readable with its access key → HTTP ${NS_GONE_KEY_HTTP} (expected 404)"
  api_pass "a deleted (trashed) set returns 404, including to a holder of its access key"

  CALL_NUM=$((CALL_NUM+1))
  echo "  API call ${CALL_NUM}/${TOTAL_API_CALLS}: GET /v3/files/folders/${NS_ID} after delete — 404"
  NS_FGONE_HTTP=$(curl -s -o /dev/null -w "%{http_code}" -u "${TEST_USER}:${TEST_PASS}" \
    "${BASE_URL}/v3/files/folders/${NS_ID}")
  [[ "${NS_FGONE_HTTP}" == "404" ]] || api_fail "the v2 delete did not remove the folder → HTTP ${NS_FGONE_HTTP}"
  api_pass "DELETE /v2/networkset/{id} deleted the underlying v3 folder"

  # The cascade must un-index the folder AND the descendants it took with it.
  poll_files_until_absent PRIVATE "${NS_NAME_2}" "${NS_ID}" "deleted set de-indexing"
  if [[ -n "${NS_SC_REMAINING}" ]]; then
    poll_files_until_absent PRIVATE "${NS_SC_REMAINING}" "${NS_SC_REMAINING}" "cascaded shortcut de-indexing"
    api_pass "the cascading delete un-indexed the folder and its remaining member shortcut"
  else
    api_pass "the cascading delete un-indexed the deleted folder"
  fi

  # Nothing in this step may have touched the frozen legacy tables.
  NS_FROZEN=$(psql_ndex "SELECT count(*) FROM network_set;")
  [[ "${NS_FROZEN}" == "0" ]] \
    || api_fail "the frozen network_set table gained ${NS_FROZEN} row(s); network sets must be folder-backed only"
  api_pass "the frozen network_set / network_set_member tables were never written"

  # Clean up the second set so later steps see a tidy account page.
  curl -s -o /dev/null -X DELETE -u "${TEST_USER}:${TEST_PASS}" "${BASE_URL}/v2/networkset/${NS_ID2}"
fi

# ── STEP: a legacy queued task row does not block startup ────────────────────
# Queued-but-unrun tasks are replayed at servlet init (populateQueuedTasksFromDB →
# NdexSystemTask.createSystemTask). A SYS_SOLR_DELETE_NETWORK row written before the
# globalIdxOnly/fileType attributes existed carries only its resource, and reconstruction used to
# unbox the missing Boolean into a primitive — one such row threw an NPE and the server never came
# up. This seeds exactly that row, restarts Tomcat, and asserts NDEx still starts.

if [[ -z "${REMOTE_NDEX_URL}" ]]; then
  step "A legacy queued Solr-delete task replays without blocking startup"

  LEGACY_TASK_ID=$(uuidgen | tr '[:upper:]' '[:lower:]')
  echo "  Seeding a legacy QUEUED SYS_SOLR_DELETE_NETWORK row (no attributes): ${LEGACY_TASK_ID}"

  # owneruuid NULL routes it to the system-task path; other_attributes NULL is the legacy shape.
  psql_ndex "INSERT INTO task (\\\"UUID\\\", creation_time, modification_time, status, task_type, owneruuid, is_deleted, other_attributes, resource) VALUES ('${LEGACY_TASK_ID}', now(), now(), 'QUEUED', 'SYS_SOLR_DELETE_NETWORK', NULL, false, NULL, '${V3_PUB_UUID}')" >/dev/null

  SEEDED=$(psql_ndex "SELECT status FROM task WHERE \\\"UUID\\\" = '${LEGACY_TASK_ID}'")
  if [[ "${SEEDED}" != "QUEUED" ]]; then
    api_fail "could not seed the legacy queued task row (status='${SEEDED}')"
  fi
  echo "  Seeded row is QUEUED"

  docker exec "${CONTAINER_NAME}" supervisorctl restart ndex

  echo "  Tomcat restart issued — waiting for NDEx to become responsive..."
  MAX_WAIT=90
  ELAPSED=0
  until curl -s -o /dev/null -w "%{http_code}" "${BASE_URL}/v2/user" \
        | grep -qE '^[2-9][0-9]{2}$|^401$|^400$'; do
    if [[ ${ELAPSED} -ge ${MAX_WAIT} ]]; then
      api_fail "NDEx did not start within ${MAX_WAIT}s after a legacy queued task was replayed — the queue replay aborted startup"
    fi
    echo -e "  ${CYAN}Waiting for Tomcat restart... (${ELAPSED}s)${NC}"
    sleep 5; (( ELAPSED += 5 )) || true
  done
  api_pass "NDEx started with a legacy queued Solr-delete row present (queue replay did not abort startup)"

  # Deliberately not asserting the task's own outcome. A legacy row carries no visibility attribute,
  # so the reconstructed task runs with a null visibility and fails inside the index manager — that
  # is long-standing behaviour and separate from what this step covers, which is that one such row
  # can no longer stop the server from starting.
  psql_ndex "DELETE FROM task WHERE \\\"UUID\\\" = '${LEGACY_TASK_ID}'" >/dev/null
fi

# ── STEP: AUTHENTICATED_USER_ONLY blocks anonymous POST /v2/user ─────────────
# NOTE: this permanently flips the server to AUTHENTICATED_USER_ONLY=true (appends to
# ndex.properties + restarts Tomcat), so it must run AFTER any step that needs anonymous
# access (e.g. the F10 folder-visibility step above).

if [[ -z "${REMOTE_NDEX_URL}" ]]; then
  step "Verifying AUTHENTICATED_USER_ONLY=true blocks anonymous POST /v2/user"

  echo "  Injecting AUTHENTICATED_USER_ONLY=true into ndex.properties and restarting Tomcat..."
  docker exec "${CONTAINER_NAME}" bash -c \
    "echo 'AUTHENTICATED_USER_ONLY=true' >> /apps/ndex/config/ndex.properties"
  docker exec "${CONTAINER_NAME}" supervisorctl restart ndex

  echo "  Tomcat restart issued — waiting for NDEx to become responsive..."
  MAX_WAIT=90
  ELAPSED=0
  until curl -s -o /dev/null -w "%{http_code}" "${BASE_URL}/v2/user" \
        | grep -qE '^[2-9][0-9]{2}$|^401$|^400$'; do
    if [[ ${ELAPSED} -ge ${MAX_WAIT} ]]; then
      api_fail "NDEx did not respond within ${MAX_WAIT}s after Tomcat restart"
    fi
    echo -e "  ${CYAN}Waiting for Tomcat restart... (${ELAPSED}s)${NC}"
    sleep 5; (( ELAPSED += 5 )) || true
  done
  echo "  Tomcat is ready."

  CALL_NUM=$((CALL_NUM+1))
  echo "  API call ${CALL_NUM}/${TOTAL_API_CALLS}: POST /v2/user (no auth, expect 401)"
  ANON_HTTP=$(curl -s -o /dev/null -w "%{http_code}" -X POST \
    -H "Content-Type: application/json" \
    -d "{\"userName\":\"${TEST_USER2}\",\"password\":\"${TEST_PASS2}\",\"emailAddress\":\"${TEST_EMAIL2}\",\"firstName\":\"NDEx\",\"lastName\":\"Test2\"}" \
    "${BASE_URL}/v2/user")
  if [[ "${ANON_HTTP}" == "401" ]]; then
    api_pass "POST /v2/user (anon) → 401 Unauthorized (AUTHENTICATED_USER_ONLY blocks anonymous user creation)"
  else
    api_fail "POST /v2/user (anon) → HTTP ${ANON_HTTP} (expected 401 with AUTHENTICATED_USER_ONLY=true)"
  fi

  CALL_NUM=$((CALL_NUM+1))
  echo "  API call ${CALL_NUM}/${TOTAL_API_CALLS}: POST /v2/user (auth as ${TEST_USER}, expect 201 or 409)"
  AUTH_CREATE_HTTP=$(curl -s -o /dev/null -w "%{http_code}" -X POST \
    -u "${TEST_USER}:${TEST_PASS}" \
    -H "Content-Type: application/json" \
    -d "{\"userName\":\"${TEST_USER2}\",\"password\":\"${TEST_PASS2}\",\"emailAddress\":\"${TEST_EMAIL2}\",\"firstName\":\"NDEx\",\"lastName\":\"Test2\"}" \
    "${BASE_URL}/v2/user")
  if [[ "${AUTH_CREATE_HTTP}" == "201" || "${AUTH_CREATE_HTTP}" == "409" ]]; then
    api_pass "POST /v2/user (auth) → ${AUTH_CREATE_HTTP} (201=new user created, 409=user already exists; authenticated caller works with AUTHENTICATED_USER_ONLY=true)"
  else
    api_fail "POST /v2/user (auth) → HTTP ${AUTH_CREATE_HTTP} (expected 201 or 409 — endpoint must work for authenticated users)"
  fi
fi

# ── STEP: Postgres SIGKILL → crash recovery ───────────────────────────────────

if [[ -z "${REMOTE_NDEX_URL}" ]]; then
  step "PostgreSQL resilience: SIGKILL → crash recovery (Attempt 1)"

  docker kill "${CONTAINER_NAME}"
  echo "  Container sent SIGKILL — restarting..."
  docker start "${CONTAINER_NAME}"

  # Poll until NDEx HTTP endpoint responds
  PG_SIGKILL_ELAPSED=0
  until curl -s -o /dev/null -w "%{http_code}" "${BASE_URL}/v2/user" \
        | grep -qE '^[2-9][0-9]{2}$|^401$|^400$'; do
    if [[ ${PG_SIGKILL_ELAPSED} -ge 180 ]]; then
      api_fail "Container did not recover within 180s after SIGKILL"
    fi
    sleep 5; PG_SIGKILL_ELAPSED=$((PG_SIGKILL_ELAPSED + 5))
    echo -e "  ${CYAN}Waiting for NDEx to respond... (${PG_SIGKILL_ELAPSED}s)${NC}"
  done

  CORRUPTION_LOG=$(docker exec "${CONTAINER_NAME}" bash -c \
    "cat /apps/postgres/corruption.log 2>/dev/null || echo ''")
  if [[ -n "${CORRUPTION_LOG}" ]]; then
    echo "  WARN: unexpected corruption log entry after SIGKILL: ${CORRUPTION_LOG}" >&2
  else
    echo "  Corruption log is empty — crash recovery was transparent (no data corruption)"
  fi

  CALL_NUM=$((CALL_NUM+1))
  echo "  API call ${CALL_NUM}/${TOTAL_API_CALLS}: GET /user/authenticate (after SIGKILL restart, expect 200)"
  SIGKILL_AUTH_HTTP=$(curl -s -o /dev/null -w "%{http_code}" \
    -u "${TEST_USER}:${TEST_PASS}" "${BASE_URL}/user/authenticate")
  if [[ "${SIGKILL_AUTH_HTTP}" == "200" ]]; then
    api_pass "GET /user/authenticate → 200 OK (postgres crash-recovered from SIGKILL, data intact)"
  else
    api_fail "GET /user/authenticate → HTTP ${SIGKILL_AUTH_HTTP} (expected 200 after SIGKILL crash recovery)"
  fi
fi

# ── STEP: Corrupt PGDATA + flag=false → container exits ──────────────────────
# Fixture: docker/test/fixtures/pg-corrupt-state/ — just .initialized sentinel,
# no cluster files. Simulates worst-case: PGDATA claims initialized but is
# unrecoverable. Without --config, reset_data_when_corrupt defaults to false
# → container must exit with a diagnostic message.

if [[ -z "${REMOTE_NDEX_URL}" ]]; then
  step "PostgreSQL resilience: corrupt PGDATA + reset_data_when_corrupt=false → container stops"

  TMP_PG_B=$(mktemp -d /tmp/pg-corrupt-XXXX)
  cp -r "${SCRIPT_DIR}/fixtures/pg-corrupt-state/." "${TMP_PG_B}/"

  docker run -d --name ndex-pg-corrupt-test \
    -v "${TMP_PG_B}:/apps/postgres/data" \
    ndexbio/ndex-rest --postgres

  # All three recovery attempts fail fast; container should exit within 45s
  PG_CORRUPT_ELAPSED=0
  while [[ ${PG_CORRUPT_ELAPSED} -lt 45 ]]; do
    PG_CORRUPT_RUNNING=$(docker inspect -f '{{.State.Running}}' ndex-pg-corrupt-test 2>/dev/null || echo false)
    [[ "${PG_CORRUPT_RUNNING}" == "false" ]] && break
    sleep 3; PG_CORRUPT_ELAPSED=$((PG_CORRUPT_ELAPSED + 3))
  done

  PG_CORRUPT_RUNNING=$(docker inspect -f '{{.State.Running}}' ndex-pg-corrupt-test 2>/dev/null || echo false)
  PG_CORRUPT_LOGS=$(docker logs ndex-pg-corrupt-test 2>&1 | tail -50)
  docker rm -fv ndex-pg-corrupt-test 2>/dev/null || true
  rm -rf "${TMP_PG_B}"

  if [[ "${PG_CORRUPT_RUNNING}" == "false" ]] && echo "${PG_CORRUPT_LOGS}" | grep -qi "reset_data_when_corrupt"; then
    echo -e "  ${GREEN}✓ PASS${NC}: container exited with reset_data_when_corrupt guidance (flag=false confirmed)"
  else
    api_fail "flag=false: expected container to exit within 45s. running=${PG_CORRUPT_RUNNING}. Logs missing 'reset_data_when_corrupt'."
  fi
fi

# ── STEP: Corrupt PGDATA + flag=true → wipe+reinit → postgres up ─────────────
# Same fixture, but config sets reset_data_when_corrupt = true. The wipe fires,
# init-postgres.sh reinitializes the cluster, postgres comes up.

if [[ -z "${REMOTE_NDEX_URL}" ]]; then
  step "PostgreSQL resilience: corrupt PGDATA + reset_data_when_corrupt=true → wipe+reinit"

  TMP_PG_C=$(mktemp -d /tmp/pg-wipe-XXXX)
  cp -r "${SCRIPT_DIR}/fixtures/pg-corrupt-state/." "${TMP_PG_C}/"

  TMP_RESET_TOML=$(mktemp /tmp/ndex-pg-reset-XXXX.toml)
  printf 'reset_data_when_corrupt = true\n' > "${TMP_RESET_TOML}"

  docker run -d --name ndex-pg-wipe-test \
    -v "${TMP_PG_C}:/apps/postgres/data" \
    -v "${TMP_RESET_TOML}:/tmp/pg-reset-config.toml:ro" \
    ndexbio/ndex-rest --postgres --config /tmp/pg-reset-config.toml

  # Poll pg_isready inside the container (wipe+initdb takes ~10-20s)
  PG_WIPE_ELAPSED=0; PG_WIPE_READY=false
  until docker exec ndex-pg-wipe-test \
        pg_isready -h 127.0.0.1 -p 5432 -U postgres -q 2>/dev/null; do
    PG_WIPE_RUNNING=$(docker inspect -f '{{.State.Running}}' ndex-pg-wipe-test 2>/dev/null || echo false)
    if [[ "${PG_WIPE_RUNNING}" == "false" || ${PG_WIPE_ELAPSED} -ge 120 ]]; then break; fi
    sleep 3; PG_WIPE_ELAPSED=$((PG_WIPE_ELAPSED + 3))
  done
  docker exec ndex-pg-wipe-test \
    pg_isready -h 127.0.0.1 -p 5432 -U postgres -q 2>/dev/null && PG_WIPE_READY=true

  PG_WIPE_CORRUPTION=$(docker exec ndex-pg-wipe-test bash -c \
    "cat /apps/postgres/corruption.log 2>/dev/null || echo ''" 2>/dev/null || echo "")
  docker rm -fv ndex-pg-wipe-test 2>/dev/null || true
  rm -rf "${TMP_PG_C}" 2>/dev/null || true  # chown in container transfers ownership; sticky /tmp prevents runner cleanup
  rm -f "${TMP_RESET_TOML}"

  if [[ "${PG_WIPE_READY}" == "true" ]] && echo "${PG_WIPE_CORRUPTION}" | grep -qi 'DATA LOSS\|wiped'; then
    echo -e "  ${GREEN}✓ PASS${NC}: postgres up after wipe+reinit; corruption log confirms DATA LOSS"
  else
    api_fail "flag=true: pg_ready=${PG_WIPE_READY}, log='${PG_WIPE_CORRUPTION}'. Expected postgres up + DATA LOSS entry."
  fi
fi

# ── Summary ───────────────────────────────────────────────────────────────────

echo ""
echo -e "${GREEN}${BOLD}================================================${NC}"
echo -e "${GREEN}${BOLD}  ✓ ALL ${PASSED} API CALLS PASSED — TEST PASSED${NC}"
echo -e "${GREEN}${BOLD}================================================${NC}"
exit 0
