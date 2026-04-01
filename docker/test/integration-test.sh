#!/usr/bin/env bash
# NDEx Docker Integration Test
# Usage: ./integration-test.sh [--skip-build]
#
# Builds the NDEx Docker image, runs an ephemeral monolithic container, and
# validates the full API lifecycle across both v2 and v3 endpoints:
#   user creation → v2 CX1 upload (2 public + 1 private) → v2 summary poll →
#   v3 CX2 retrieve → v3 CX2 upload (2 public + 1 private) → v3 summary poll →
#   v3 CX2 retrieve → private network access control → public anonymous access
#
# Exits 0 if all 22 API calls pass, exits 1 on the first failure.
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

TOTAL_API_CALLS=22
PASSED=0
LOAD_TIMEOUT=90

# ── Colors ────────────────────────────────────────────────────────────────────
RED='\033[0;31m'
GREEN='\033[0;32m'
CYAN='\033[0;36m'
BOLD='\033[1m'
NC='\033[0m'

# ── Helpers ───────────────────────────────────────────────────────────────────

step() {
  echo ""
  echo -e "${BOLD}=== STEP $1: $2 ===${NC}"
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

# ── Cleanup trap ──────────────────────────────────────────────────────────────

cleanup() {
  echo ""
  echo -e "${CYAN}=== Cleanup ===${NC}"
  echo -e "${CYAN}  Stopping container '${CONTAINER_NAME}'...${NC}"
  docker stop "${CONTAINER_NAME}" 2>/dev/null || true
  docker rm -v "${CONTAINER_NAME}" 2>/dev/null || true

  if docker inspect "${CONTAINER_NAME}" &>/dev/null; then
    echo -e "  ${RED}WARNING: Container '${CONTAINER_NAME}' still present — manual cleanup may be needed${NC}"
    echo -e "    Run: docker rm -fv ${CONTAINER_NAME}"
  else
    echo -e "  ${GREEN}✓ Container '${CONTAINER_NAME}' removed${NC}"
    echo -e "  ${GREEN}✓ Anonymous volumes cleaned up${NC}"
  fi
}
trap cleanup EXIT

# ── STEP 1: Build ─────────────────────────────────────────────────────────────

step 1 "Building Docker image"
if [[ "${1:-}" == "--skip-build" ]]; then
  echo "  --skip-build flag set, skipping make docker"
else
  echo "  Running: make docker (from ${REPO_DIR})"
  make -C "${REPO_DIR}" docker
  echo "  Image built successfully"
fi

# ── STEP 2: Start container ───────────────────────────────────────────────────

step 2 "Starting ephemeral container"

docker rm -fv "${CONTAINER_NAME}" 2>/dev/null || true

echo "  Running: docker run -d --rm --name ${CONTAINER_NAME} -p 8080:8080 ..."
docker run -d --rm \
  --name "${CONTAINER_NAME}" \
  -p 8080:8080 \
  ndexbio/ndex-rest \
  --ndex --postgres --keycloak --solr --mailhog

echo "  Container started (ID: $(docker inspect -f '{{.Id}}' "${CONTAINER_NAME}" | cut -c1-12))"

# ── STEP 3: Wait for Ready banner ─────────────────────────────────────────────

step 3 "Waiting for NDEx to be ready"

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

# ── STEP 4: Create test user ──────────────────────────────────────────────────

step 4 "Creating test user"
echo "  API call 1/${TOTAL_API_CALLS}: POST /v2/user"

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
else
  api_fail "POST /v2/user → HTTP ${USER_HTTP} (expected 201). Body: ${USER_BODY:0:300}"
fi

# ── STEP 5: Verify Basic Auth ─────────────────────────────────────────────────

step 5 "Verifying Basic Auth login"
echo "  API call 2/${TOTAL_API_CALLS}: GET /user/authenticate"

AUTH_HTTP=$(curl -s -o /dev/null -w "%{http_code}" \
  -u "${TEST_USER}:${TEST_PASS}" \
  "${BASE_URL}/user/authenticate")

if [[ "${AUTH_HTTP}" == "200" ]]; then
  api_pass "GET /user/authenticate → 200 OK (Basic Auth confirmed)"
else
  api_fail "GET /user/authenticate → HTTP ${AUTH_HTTP} (expected 200)"
fi

# ── STEP 6: Upload 3 CX1 networks via v2 ─────────────────────────────────────

step 6 "Uploading 3 CX1 networks via POST /v2/network (2 public, 1 private)"

V2_UUIDS=()
V2_PRIV_UUID=""
CX_INDEX=0
for CX_FILE in "${FIXTURES_DIR}"/*.cx; do
  CX_INDEX=$((CX_INDEX + 1))
  API_CALL_NUM=$((2 + CX_INDEX))
  NETWORK_LABEL="$(basename "${CX_FILE}")"

  if [[ ${CX_INDEX} -eq 3 ]]; then
    VISIBILITY="PRIVATE"
  else
    VISIBILITY="PUBLIC"
  fi

  echo "  API call ${API_CALL_NUM}/${TOTAL_API_CALLS}: POST /v2/network?visibility=${VISIBILITY}  [${NETWORK_LABEL}]"

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

# ── STEP 7: Poll v2 summary until completed:true ──────────────────────────────

step 7 "Polling v2 network summary until all 3 CX1 networks are complete"
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

# ── STEP 8: Retrieve v2 networks via v3 endpoint ─────────────────────────────

step 8 "Retrieving v2-uploaded CX1 networks as CX2 via GET /v3/networks/{uuid}"

for i in "${!V2_UUIDS[@]}"; do
  UUID="${V2_UUIDS[$i]}"
  API_CALL_NUM=$((5 + i + 1))
  echo "  API call ${API_CALL_NUM}/${TOTAL_API_CALLS}: GET /v3/networks/${UUID}"

  V3_HTTP=$(curl -s -o /dev/null -w "%{http_code}" \
    -u "${TEST_USER}:${TEST_PASS}" \
    "${BASE_URL}/v3/networks/${UUID}")

  if [[ "${V3_HTTP}" == "200" ]]; then
    api_pass "GET /v3/networks/${UUID} → 200 OK (CX2 stream available for v2-uploaded network)"
  else
    api_fail "GET /v3/networks/${UUID} → HTTP ${V3_HTTP} (expected 200)"
  fi
done

# ── STEP 9: Upload 3 CX2 networks via v3 ─────────────────────────────────────

step 9 "Uploading 3 CX2 networks via POST /v3/networks (2 public, 1 private)"

V3_UUIDS=()
V3_PRIV_UUID=""
CX2_INDEX=0
for CX2_FILE in "${FIXTURES_DIR}"/*.cx2; do
  CX2_INDEX=$((CX2_INDEX + 1))
  API_CALL_NUM=$((8 + CX2_INDEX))
  NETWORK_LABEL="$(basename "${CX2_FILE}")"

  if [[ ${CX2_INDEX} -eq 3 ]]; then
    VISIBILITY="PRIVATE"
  else
    VISIBILITY="PUBLIC"
  fi

  echo "  API call ${API_CALL_NUM}/${TOTAL_API_CALLS}: POST /v3/networks?visibility=${VISIBILITY}  [${NETWORK_LABEL}]"

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

# ── STEP 10: Poll v3 summary until completed:true ─────────────────────────────

step 10 "Polling v3 network summary until all 3 CX2 networks are complete"
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

# ── STEP 11: Retrieve v3 networks ─────────────────────────────────────────────

step 11 "Retrieving v3 networks via GET /v3/networks/{uuid}"

for i in "${!V3_UUIDS[@]}"; do
  UUID="${V3_UUIDS[$i]}"
  API_CALL_NUM=$((11 + i + 1))
  echo "  API call ${API_CALL_NUM}/${TOTAL_API_CALLS}: GET /v3/networks/${UUID}"

  V3_HTTP=$(curl -s -o /dev/null -w "%{http_code}" \
    -u "${TEST_USER}:${TEST_PASS}" \
    "${BASE_URL}/v3/networks/${UUID}")

  if [[ "${V3_HTTP}" == "200" ]]; then
    api_pass "GET /v3/networks/${UUID} → 200 OK (CX2 stream retrieved)"
  else
    api_fail "GET /v3/networks/${UUID} → HTTP ${V3_HTTP} (expected 200)"
  fi
done

# ── STEP 12: Private network — anonymous access denied ───────────────────────

step 12 "Asserting anonymous clients cannot retrieve private networks"
echo "  Private v2 network (WP5434): ${V2_PRIV_UUID}"
echo "  Private v3 network (ChEMBL):  ${V3_PRIV_UUID}"

echo "  API call 15/${TOTAL_API_CALLS}: GET /v3/networks/${V2_PRIV_UUID} (no auth, expect 401)"
ANON_HTTP=$(curl -s -o /dev/null -w "%{http_code}" "${BASE_URL}/v3/networks/${V2_PRIV_UUID}")
if [[ "${ANON_HTTP}" == "401" ]]; then
  api_pass "GET /v3/networks/${V2_PRIV_UUID} (anon) → 401 Unauthorized (private v2 network blocked)"
else
  api_fail "GET /v3/networks/${V2_PRIV_UUID} (anon) → HTTP ${ANON_HTTP} (expected 401 for private network)"
fi

echo "  API call 16/${TOTAL_API_CALLS}: GET /v3/networks/${V3_PRIV_UUID} (no auth, expect 401)"
ANON_HTTP=$(curl -s -o /dev/null -w "%{http_code}" "${BASE_URL}/v3/networks/${V3_PRIV_UUID}")
if [[ "${ANON_HTTP}" == "401" ]]; then
  api_pass "GET /v3/networks/${V3_PRIV_UUID} (anon) → 401 Unauthorized (private v3 network blocked)"
else
  api_fail "GET /v3/networks/${V3_PRIV_UUID} (anon) → HTTP ${ANON_HTTP} (expected 401 for private network)"
fi

echo "  API call 17/${TOTAL_API_CALLS}: GET /v2/network/${V2_PRIV_UUID}/summary (no auth, expect 401)"
ANON_HTTP=$(curl -s -o /dev/null -w "%{http_code}" "${BASE_URL}/v2/network/${V2_PRIV_UUID}/summary")
if [[ "${ANON_HTTP}" == "401" ]]; then
  api_pass "GET /v2/network/${V2_PRIV_UUID}/summary (anon) → 401 Unauthorized (private v2 summary blocked)"
else
  api_fail "GET /v2/network/${V2_PRIV_UUID}/summary (anon) → HTTP ${ANON_HTTP} (expected 401 for private network)"
fi

echo "  API call 18/${TOTAL_API_CALLS}: GET /v3/networks/${V3_PRIV_UUID}/summary (no auth, expect 401)"
ANON_HTTP=$(curl -s -o /dev/null -w "%{http_code}" "${BASE_URL}/v3/networks/${V3_PRIV_UUID}/summary")
if [[ "${ANON_HTTP}" == "401" ]]; then
  api_pass "GET /v3/networks/${V3_PRIV_UUID}/summary (anon) → 401 Unauthorized (private v3 summary blocked)"
else
  api_fail "GET /v3/networks/${V3_PRIV_UUID}/summary (anon) → HTTP ${ANON_HTTP} (expected 401 for private network)"
fi

# ── STEP 13: Public network — anonymous access allowed ────────────────────────

step 13 "Asserting anonymous clients can retrieve public networks"

V2_PUB_UUID="${V2_UUIDS[0]}"
V3_PUB_UUID="${V3_UUIDS[0]}"

echo "  API call 19/${TOTAL_API_CALLS}: GET /v3/networks/${V2_PUB_UUID} (no auth, expect 200)"
ANON_HTTP=$(curl -s -o /dev/null -w "%{http_code}" "${BASE_URL}/v3/networks/${V2_PUB_UUID}")
if [[ "${ANON_HTTP}" == "200" ]]; then
  api_pass "GET /v3/networks/${V2_PUB_UUID} (anon) → 200 OK (public v2 network accessible)"
else
  api_fail "GET /v3/networks/${V2_PUB_UUID} (anon) → HTTP ${ANON_HTTP} (expected 200 for public network)"
fi

echo "  API call 20/${TOTAL_API_CALLS}: GET /v3/networks/${V3_PUB_UUID} (no auth, expect 200)"
ANON_HTTP=$(curl -s -o /dev/null -w "%{http_code}" "${BASE_URL}/v3/networks/${V3_PUB_UUID}")
if [[ "${ANON_HTTP}" == "200" ]]; then
  api_pass "GET /v3/networks/${V3_PUB_UUID} (anon) → 200 OK (public v3 network accessible)"
else
  api_fail "GET /v3/networks/${V3_PUB_UUID} (anon) → HTTP ${ANON_HTTP} (expected 200 for public network)"
fi

# ── STEP 14: Private network — authenticated owner access allowed ─────────────

step 14 "Asserting authenticated owner can retrieve their private networks"

echo "  API call 21/${TOTAL_API_CALLS}: GET /v3/networks/${V2_PRIV_UUID} (auth, expect 200)"
AUTH_HTTP=$(curl -s -o /dev/null -w "%{http_code}" \
  -u "${TEST_USER}:${TEST_PASS}" \
  "${BASE_URL}/v3/networks/${V2_PRIV_UUID}")
if [[ "${AUTH_HTTP}" == "200" ]]; then
  api_pass "GET /v3/networks/${V2_PRIV_UUID} (auth) → 200 OK (owner can retrieve private v2 network)"
else
  api_fail "GET /v3/networks/${V2_PRIV_UUID} (auth) → HTTP ${AUTH_HTTP} (expected 200 for owner)"
fi

echo "  API call 22/${TOTAL_API_CALLS}: GET /v3/networks/${V3_PRIV_UUID} (auth, expect 200)"
AUTH_HTTP=$(curl -s -o /dev/null -w "%{http_code}" \
  -u "${TEST_USER}:${TEST_PASS}" \
  "${BASE_URL}/v3/networks/${V3_PRIV_UUID}")
if [[ "${AUTH_HTTP}" == "200" ]]; then
  api_pass "GET /v3/networks/${V3_PRIV_UUID} (auth) → 200 OK (owner can retrieve private v3 network)"
else
  api_fail "GET /v3/networks/${V3_PRIV_UUID} (auth) → HTTP ${AUTH_HTTP} (expected 200 for owner)"
fi

# ── Summary ───────────────────────────────────────────────────────────────────

echo ""
echo -e "${GREEN}${BOLD}================================================${NC}"
echo -e "${GREEN}${BOLD}  ✓ ALL ${PASSED} API CALLS PASSED — TEST PASSED${NC}"
echo -e "${GREEN}${BOLD}================================================${NC}"
exit 0
