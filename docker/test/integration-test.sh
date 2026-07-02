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
#   v2 Solr search → v3 Solr search → v2 neighborhood query (SSL context)
#
# Exits 0 if all 29 API calls pass, exits 1 on the first failure.
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

TOTAL_API_CALLS=43
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

# ── STEP: AUTHENTICATED_USER_ONLY blocks anonymous POST /v2/user ─────────────

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
