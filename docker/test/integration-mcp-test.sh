#!/usr/bin/env bash
# NDEx MCP Integration Test — standalone, no dependency on integration-test.sh
#
# Usage: ./integration-mcp-test.sh [--skip-build] [--remote-ndex-url <url>]
#
#   --skip-build           Skip 'make docker' (reuse existing local image). Only
#                          applies when running against a local container (no --remote-ndex-url).
#   --remote-ndex-url URL  Run tests against an already-running NDEx instance at URL.
#
# Validates all 15 MCP tools end-to-end:
#   manifest → get_connection_status → anon-allowed tools → auth barriers → create/update → profile/properties →
#   systemproperties → download → get_user_networks → get_user_info → share → folder management → delete
#
# Exits 0 if all 41 API calls pass, exits 1 on the first failure.
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

TOTAL_API_CALLS=41
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

# Issue a single MCP tools/call. Populates MCP_HTTP (HTTP status) and MCP_JSON (JSON-RPC result).
# Usage: mcp_call PAYLOAD [-u USER:PASS]
# MCP uses Streamable HTTP Streamable transport — a session must be established first via
# mcp_initialize. Responses arrive as SSE (data: <json>) or plain JSON.
MCP_HTTP=""
MCP_JSON=""
MCP_SESSION_ID=""
mcp_initialize() {
  local raw
  # curl -si: -s silent, -i include response headers in stdout
  raw=$(curl -si \
    -X POST \
    -H "Content-Type: application/json" \
    -H "Accept: application/json, text/event-stream" \
    -d '{"jsonrpc":"2.0","id":"init","method":"initialize","params":{"protocolVersion":"2025-03-26","capabilities":{},"clientInfo":{"name":"integration-test","version":"1.0.0"}}}' \
    "${BASE_URL}/mcp")
  MCP_HTTP=$(echo "${raw}" | awk 'NR==1{print $2}')
  MCP_SESSION_ID=$(echo "${raw}" | grep -i "^mcp-session-id:" | awk '{print $2}' | tr -d '\r' || true)
}

mcp_call() {
  local payload="$1"
  local auth_args="${2:-}"
  # session_header is intentionally unquoted so "-H Mcp-Session-Id:value" word-splits correctly.
  local session_header=""
  [[ -n "${MCP_SESSION_ID:-}" ]] && session_header="-H Mcp-Session-Id:${MCP_SESSION_ID}"
  local raw
  # auth_args and session_header are intentionally unquoted — each word-splits into separate curl args.
  raw=$(curl -s -w "\n%{http_code}" ${auth_args} ${session_header} \
    -X POST \
    -H "Content-Type: application/json" \
    -H "Accept: application/json, text/event-stream" \
    -d "${payload}" \
    "${BASE_URL}/mcp")
  MCP_HTTP=$(echo "${raw}" | tail -1)
  MCP_JSON=$(echo "${raw}" | grep '^data: ' | head -1 | cut -c7- || true)
  [[ -n "${MCP_JSON}" ]] || MCP_JSON=$(echo "${raw}" | grep '^{' | head -1 || true)
}

# Assert HTTP 200 and no isError:true in the MCP result.
mcp_pass() {
  local label="$1"
  [[ "${MCP_HTTP}" == "200" ]] \
    || api_fail "${label} → HTTP ${MCP_HTTP} (expected 200). JSON: ${MCP_JSON:0:300}"
  if echo "${MCP_JSON}" | grep -q '"isError":true'; then
    api_fail "${label} → isError:true. JSON: ${MCP_JSON:0:300}"
  fi
  api_pass "${label} → HTTP 200, isError:false"
}

# Assert HTTP 200 and isError:true present (expected no-auth rejection path).
# No-auth to an MCP tool returns HTTP 200 at the transport layer (the filter passes through
# with user=null) and isError:true in the JSON-RPC result from toolsService.unauthorizedResult().
mcp_fail_expected() {
  local label="$1"
  [[ "${MCP_HTTP}" == "200" ]] \
    || api_fail "${label} → HTTP ${MCP_HTTP} (expected 200 at MCP layer). JSON: ${MCP_JSON:0:300}"
  echo "${MCP_JSON}" | grep -q '"isError":true' \
    || api_fail "${label} → expected isError:true but got: ${MCP_JSON:0:300}"
  api_pass "${label} → HTTP 200, isError:true (expected rejection)"
}

# ── Cleanup trap ──────────────────────────────────────────────────────────────

cleanup() {
  [[ -z "${REMOTE_NDEX_URL}" ]] || return 0
  echo ""
  echo -e "${CYAN}=== Cleanup ===${NC}"
  echo -e "${CYAN}  Removing container '${CONTAINER_NAME}'...${NC}"
  docker rm -fv "${CONTAINER_NAME}" 2>/dev/null || true

  if docker inspect "${CONTAINER_NAME}" &>/dev/null; then
    echo -e "  ${RED}WARNING: Container '${CONTAINER_NAME}' still present — manual cleanup may be needed${NC}"
    echo -e "    Run: docker rm -fv ${CONTAINER_NAME}"
  else
    echo -e "  ${GREEN}✓ Container '${CONTAINER_NAME}' removed${NC}"
  fi
}
trap cleanup EXIT

# ── Server startup ────────────────────────────────────────────────────────────

if [[ -n "${REMOTE_NDEX_URL}" ]]; then
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
  echo "  Running: docker run --platform linux/amd64 -d --name ${CONTAINER_NAME} -p 8080:8080 ..."
  docker run --platform linux/amd64 -d \
    --name "${CONTAINER_NAME}" \
    -p 8080:8080 \
    ndexbio/ndex-rest \
    --ndex --postgres --keycloak --solr --mailhog
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
  api_pass "POST /v2/user → 409 (user: ${TEST_USER} already exists)"
else
  api_fail "POST /v2/user → HTTP ${USER_HTTP} (expected 201). Body: ${USER_BODY:0:300}"
fi

TEST_USER2="ndextest2"
TEST_USER2_PASS="NDExTest2!"
TEST_USER2_EMAIL="ndextest2@ndex-integration.local"
TEST_USER2_UUID=""

CALL_NUM=$((CALL_NUM+1))
echo "  API call ${CALL_NUM}/${TOTAL_API_CALLS}: POST /v2/user [ndextest2]"

USER2_RESPONSE=$(curl -s -w "\n%{http_code}" -X POST "${BASE_URL}/v2/user" \
  -H "Content-Type: application/json" \
  -d "{\"userName\":\"${TEST_USER2}\",\"password\":\"${TEST_USER2_PASS}\",\"emailAddress\":\"${TEST_USER2_EMAIL}\",\"firstName\":\"NDEx\",\"lastName\":\"Test2\"}")
USER2_HTTP=$(echo "${USER2_RESPONSE}" | tail -1)
USER2_BODY=$(echo "${USER2_RESPONSE}" | head -1)

if [[ "${USER2_HTTP}" == "201" ]]; then
  api_pass "POST /v2/user → 201 Created (user: ${TEST_USER2})"
elif [[ "${USER2_HTTP}" == "409" ]]; then
  api_pass "POST /v2/user → 409 (user: ${TEST_USER2} already exists)"
else
  api_fail "POST /v2/user (ndextest2) → HTTP ${USER2_HTTP}. Body: ${USER2_BODY:0:300}"
fi

TEST_USER2_UUID=$(curl -s "${BASE_URL}/v2/user?username=${TEST_USER2}" \
  | grep -o '"externalId":"[^"]*"' | head -1 | cut -d'"' -f4 || true)
[[ -n "${TEST_USER2_UUID}" ]] || api_fail "Could not resolve UUID for ${TEST_USER2} via GET /v2/user?username=${TEST_USER2}"

# ── STEP: Upload 2 CX2 test networks (1 public, 1 private) ───────────────────

step "Uploading 2 CX2 test networks via POST /v3/networks"

MCP_PUBLIC_UUID=""
MCP_PRIVATE_UUID=""
CX2_FIXTURE="${FIXTURES_DIR}/C. burnetii Network.cx2"

CALL_NUM=$((CALL_NUM+1))
echo "  API call ${CALL_NUM}/${TOTAL_API_CALLS}: POST /v3/networks?visibility=PUBLIC  [C. burnetii Network.cx2]"
PUB_RESPONSE=$(curl -s -w "\n%{http_code}" -X POST \
  -u "${TEST_USER}:${TEST_PASS}" \
  -H "Content-Type: application/json" \
  --data-binary "@${CX2_FIXTURE}" \
  "${BASE_URL}/v3/networks?visibility=PUBLIC")
PUB_HTTP=$(echo "${PUB_RESPONSE}" | tail -1)
PUB_BODY=$(echo "${PUB_RESPONSE}" | head -1)

if [[ "${PUB_HTTP}" == "201" ]]; then
  MCP_PUBLIC_UUID=$(echo "${PUB_BODY}" | grep -o '"uuid":"[^"]*"' | head -1 | cut -d'"' -f4)
  api_pass "POST /v3/networks → 201 Created (UUID: ${MCP_PUBLIC_UUID}, PUBLIC)"
else
  api_fail "POST /v3/networks → HTTP ${PUB_HTTP} (expected 201). Body: ${PUB_BODY:0:300}"
fi

CALL_NUM=$((CALL_NUM+1))
echo "  API call ${CALL_NUM}/${TOTAL_API_CALLS}: POST /v3/networks?visibility=PRIVATE  [C. burnetii Network.cx2]"
PRIV_RESPONSE=$(curl -s -w "\n%{http_code}" -X POST \
  -u "${TEST_USER}:${TEST_PASS}" \
  -H "Content-Type: application/json" \
  --data-binary "@${CX2_FIXTURE}" \
  "${BASE_URL}/v3/networks?visibility=PRIVATE")
PRIV_HTTP=$(echo "${PRIV_RESPONSE}" | tail -1)
PRIV_BODY=$(echo "${PRIV_RESPONSE}" | head -1)

if [[ "${PRIV_HTTP}" == "201" ]]; then
  MCP_PRIVATE_UUID=$(echo "${PRIV_BODY}" | grep -o '"uuid":"[^"]*"' | head -1 | cut -d'"' -f4)
  api_pass "POST /v3/networks → 201 Created (UUID: ${MCP_PRIVATE_UUID}, PRIVATE)"
else
  api_fail "POST /v3/networks → HTTP ${PRIV_HTTP} (expected 201). Body: ${PRIV_BODY:0:300}"
fi

# ── STEP: Poll until both setup networks complete ─────────────────────────────

step "Polling setup networks until completed:true"
echo "  Polling GET /v2/network/{uuid}/summary until completed:true..."

for UUID in "${MCP_PUBLIC_UUID}" "${MCP_PRIVATE_UUID}"; do
  ELAPSED=0
  while true; do
    SUMMARY_BODY=$(curl -s -u "${TEST_USER}:${TEST_PASS}" "${BASE_URL}/v2/network/${UUID}/summary")
    if echo "${SUMMARY_BODY}" | grep -q '"completed":true'; then
      echo "  Network ${UUID} — completed"
      break
    fi
    if [[ ${ELAPSED} -ge ${LOAD_TIMEOUT} ]]; then
      api_fail "Setup network ${UUID} did not complete within ${LOAD_TIMEOUT}s. Last: ${SUMMARY_BODY:0:300}"
    fi
    echo -e "  ${CYAN}Waiting for network ${UUID}... (${ELAPSED}s)${NC}"
    sleep 5; (( ELAPSED += 5 )) || true
  done
done
echo "  Both setup networks confirmed complete"

# ── STEP: MCP Manifest ────────────────────────────────────────────────────────

step "MCP Manifest and Session Init"
CALL_NUM=$((CALL_NUM+1))
echo "  API call ${CALL_NUM}/${TOTAL_API_CALLS}: GET /mcp/manifest"

MANIFEST_HTTP=$(curl -s -o /dev/null -w "%{http_code}" "${BASE_URL}/mcp/manifest")
if [[ "${MANIFEST_HTTP}" == "200" ]]; then
  api_pass "GET /mcp/manifest → 200 OK (public, no auth)"
else
  api_fail "GET /mcp/manifest → HTTP ${MANIFEST_HTTP} (expected 200)"
fi

echo "  [setup] POST /mcp initialize — establishing MCP session"
mcp_initialize
[[ "${MCP_HTTP}" == "200" ]] \
  || api_fail "MCP initialize → HTTP ${MCP_HTTP} (expected 200)"
[[ -n "${MCP_SESSION_ID}" ]] \
  || api_fail "MCP initialize did not return a Mcp-Session-Id header"
echo "  Session ID: ${MCP_SESSION_ID}"

# ── STEP: MCP get_connection_status ──────────────────────────────────────────

step "MCP get_connection_status: anonymous and authenticated"

CALL_NUM=$((CALL_NUM+1))
echo "  API call ${CALL_NUM}/${TOTAL_API_CALLS}: get_connection_status (anon)"
mcp_call '{"jsonrpc":"2.0","id":"mcp-cs-anon","method":"tools/call","params":{"name":"get_connection_status","arguments":{}}}'
mcp_pass "get_connection_status (anon)"
echo "${MCP_JSON}" | grep -q '"authenticated":false' \
  || api_fail "get_connection_status anon → expected authenticated:false: ${MCP_JSON:0:300}"
echo "${MCP_JSON}" | grep -q '"username":"anonymous"' \
  || api_fail "get_connection_status anon → expected username:anonymous: ${MCP_JSON:0:300}"
echo "${MCP_JSON}" | grep -q '"server":' \
  || api_fail "get_connection_status anon → expected server field: ${MCP_JSON:0:300}"

CALL_NUM=$((CALL_NUM+1))
echo "  API call ${CALL_NUM}/${TOTAL_API_CALLS}: get_connection_status (auth)"
mcp_call '{"jsonrpc":"2.0","id":"mcp-cs-auth","method":"tools/call","params":{"name":"get_connection_status","arguments":{}}}' \
  "-u ${TEST_USER}:${TEST_PASS}"
mcp_pass "get_connection_status (auth)"
echo "${MCP_JSON}" | grep -q '"authenticated":true' \
  || api_fail "get_connection_status auth → expected authenticated:true: ${MCP_JSON:0:300}"
echo "${MCP_JSON}" | grep -q "\"username\":\"${TEST_USER}\"" \
  || api_fail "get_connection_status auth → expected username:${TEST_USER}: ${MCP_JSON:0:300}"

# ── STEP: MCP anon-allowed tools ──────────────────────────────────────────────

step "MCP anon-allowed tools: search_network, get_network_summary"
echo "  Both tools must work unauthenticated on public data and also with auth."
echo "  get_network_summary on a private network without auth must reject."

CALL_NUM=$((CALL_NUM+1))
echo "  API call ${CALL_NUM}/${TOTAL_API_CALLS}: search_network (anon)"
mcp_call '{"jsonrpc":"2.0","id":"mcp-5","method":"tools/call","params":{"name":"search_network","arguments":{"searchString":"burnetii","size":5}}}'
mcp_pass "search_network (anon)"

CALL_NUM=$((CALL_NUM+1))
echo "  API call ${CALL_NUM}/${TOTAL_API_CALLS}: search_network (auth)"
mcp_call '{"jsonrpc":"2.0","id":"mcp-6","method":"tools/call","params":{"name":"search_network","arguments":{"searchString":"burnetii","size":5}}}' \
  "-u ${TEST_USER}:${TEST_PASS}"
mcp_pass "search_network (auth)"

CALL_NUM=$((CALL_NUM+1))
echo "  API call ${CALL_NUM}/${TOTAL_API_CALLS}: get_network_summary public (anon)"
mcp_call '{"jsonrpc":"2.0","id":"mcp-7","method":"tools/call","params":{"name":"get_network_summary","arguments":{"networkId":"'"${MCP_PUBLIC_UUID}"'"}}}'
mcp_pass "get_network_summary public (anon)"

CALL_NUM=$((CALL_NUM+1))
echo "  API call ${CALL_NUM}/${TOTAL_API_CALLS}: get_network_summary public (auth)"
mcp_call '{"jsonrpc":"2.0","id":"mcp-8","method":"tools/call","params":{"name":"get_network_summary","arguments":{"networkId":"'"${MCP_PUBLIC_UUID}"'"}}}' \
  "-u ${TEST_USER}:${TEST_PASS}"
mcp_pass "get_network_summary public (auth)"

CALL_NUM=$((CALL_NUM+1))
echo "  API call ${CALL_NUM}/${TOTAL_API_CALLS}: get_network_summary private (anon — expect rejection)"
mcp_call '{"jsonrpc":"2.0","id":"mcp-9","method":"tools/call","params":{"name":"get_network_summary","arguments":{"networkId":"'"${MCP_PRIVATE_UUID}"'"}}}'
mcp_fail_expected "get_network_summary private (anon)"

CALL_NUM=$((CALL_NUM+1))
echo "  API call ${CALL_NUM}/${TOTAL_API_CALLS}: get_network_summary private (auth/owner)"
mcp_call '{"jsonrpc":"2.0","id":"mcp-10","method":"tools/call","params":{"name":"get_network_summary","arguments":{"networkId":"'"${MCP_PRIVATE_UUID}"'"}}}' \
  "-u ${TEST_USER}:${TEST_PASS}"
mcp_pass "get_network_summary private (auth/owner)"

# ── STEP: MCP auth barrier — no-auth rejection ────────────────────────────────

step "MCP auth barrier: no credentials must be rejected by all auth-required tools"
echo "  User=null check fires before any service call, so minimal args are fine."

CALL_NUM=$((CALL_NUM+1))
echo "  API call ${CALL_NUM}/${TOTAL_API_CALLS}: create_network (no auth)"
mcp_call '{"jsonrpc":"2.0","id":"mcp-11","method":"tools/call","params":{"name":"create_network","arguments":{"cx2Network":"[]","cx2NetworkSize":2,"cx2NetworkChunkTotalCount":1,"cx2NetworkCurrentChunkNumber":1}}}'
mcp_fail_expected "create_network (no auth)"

CALL_NUM=$((CALL_NUM+1))
echo "  API call ${CALL_NUM}/${TOTAL_API_CALLS}: update_network (no auth)"
mcp_call '{"jsonrpc":"2.0","id":"mcp-12","method":"tools/call","params":{"name":"update_network","arguments":{"networkId":"'"${MCP_PUBLIC_UUID}"'","cx2Network":"[]","cx2NetworkSize":2,"cx2NetworkChunkTotalCount":1,"cx2NetworkCurrentChunkNumber":1}}}'
mcp_fail_expected "update_network (no auth)"

CALL_NUM=$((CALL_NUM+1))
echo "  API call ${CALL_NUM}/${TOTAL_API_CALLS}: delete_network (no auth, fake UUID)"
mcp_call '{"jsonrpc":"2.0","id":"mcp-13","method":"tools/call","params":{"name":"delete_network","arguments":{"networkId":"00000000-0000-0000-0000-000000000000"}}}'
mcp_fail_expected "delete_network (no auth)"

CALL_NUM=$((CALL_NUM+1))
echo "  API call ${CALL_NUM}/${TOTAL_API_CALLS}: update_network_profile (no auth)"
mcp_call '{"jsonrpc":"2.0","id":"mcp-14","method":"tools/call","params":{"name":"update_network_profile","arguments":{"networkId":"'"${MCP_PUBLIC_UUID}"'","name":"x","visibility":"PUBLIC"}}}'
mcp_fail_expected "update_network_profile (no auth)"

CALL_NUM=$((CALL_NUM+1))
echo "  API call ${CALL_NUM}/${TOTAL_API_CALLS}: set_network_properties (no auth)"
mcp_call '{"jsonrpc":"2.0","id":"mcp-15","method":"tools/call","params":{"name":"set_network_properties","arguments":{"networkId":"'"${MCP_PUBLIC_UUID}"'","properties":[]}}}'
mcp_fail_expected "set_network_properties (no auth)"

CALL_NUM=$((CALL_NUM+1))
echo "  API call ${CALL_NUM}/${TOTAL_API_CALLS}: set_network_systemproperties (no auth)"
mcp_call '{"jsonrpc":"2.0","id":"mcp-16","method":"tools/call","params":{"name":"set_network_systemproperties","arguments":{"networkId":"'"${MCP_PUBLIC_UUID}"'","visibility":"PUBLIC"}}}'
mcp_fail_expected "set_network_systemproperties (no auth)"

CALL_NUM=$((CALL_NUM+1))
echo "  API call ${CALL_NUM}/${TOTAL_API_CALLS}: get_folder mode=list (no auth)"
mcp_call '{"jsonrpc":"2.0","id":"mcp-17","method":"tools/call","params":{"name":"get_folder","arguments":{"mode":"list"}}}'
mcp_fail_expected "get_folder mode=list (no auth)"

CALL_NUM=$((CALL_NUM+1))
echo "  API call ${CALL_NUM}/${TOTAL_API_CALLS}: manage_folder mode=create (no auth)"
mcp_call '{"jsonrpc":"2.0","id":"mcp-18","method":"tools/call","params":{"name":"manage_folder","arguments":{"mode":"create","name":{"waived":false,"parameter":"barrier-test"}}}}'
mcp_fail_expected "manage_folder mode=create (no auth)"

CALL_NUM=$((CALL_NUM+1))
echo "  API call ${CALL_NUM}/${TOTAL_API_CALLS}: share_network (no auth)"
mcp_call '{"jsonrpc":"2.0","id":"mcp-sn-noauth","method":"tools/call","params":{"name":"share_network","arguments":{"networkId":"'"${MCP_PUBLIC_UUID}"'","userId":"00000000-0000-0000-0000-000000000000","permission":"READ"}}}'
mcp_fail_expected "share_network (no auth)"

CALL_NUM=$((CALL_NUM+1))
echo "  API call ${CALL_NUM}/${TOTAL_API_CALLS}: get_user_networks (no auth)"
mcp_call '{"jsonrpc":"2.0","id":"mcp-gun-noauth","method":"tools/call","params":{"name":"get_user_networks","arguments":{}}}'
mcp_fail_expected "get_user_networks (no auth)"

CALL_NUM=$((CALL_NUM+1))
echo "  API call ${CALL_NUM}/${TOTAL_API_CALLS}: get_user_info (no auth)"
mcp_call '{"jsonrpc":"2.0","id":"mcp-gui-noauth","method":"tools/call","params":{"name":"get_user_info","arguments":{}}}'
mcp_fail_expected "get_user_info (no auth)"

# ── STEP: MCP create_network + update_network (auth) ─────────────────────────

step "MCP create_network and update_network (auth)"
echo "  Preparing CX2 content from fixture..."

MCP_CX2_FILE="${FIXTURES_DIR}/C. burnetii Network.cx2"
MCP_CX2_SIZE=$(wc -c < "${MCP_CX2_FILE}" | tr -d ' ')
# Escape for embedding as a JSON string value: backslash first, then double-quote, then strip newlines.
MCP_CX2_ESCAPED=$(sed 's/\\/\\\\/g; s/"/\\"/g' "${MCP_CX2_FILE}" | tr -d '\n\r')

MCP_NETWORK_UUID=""

CALL_NUM=$((CALL_NUM+1))
echo "  API call ${CALL_NUM}/${TOTAL_API_CALLS}: create_network (auth)"
mcp_call "{\"jsonrpc\":\"2.0\",\"id\":\"mcp-19\",\"method\":\"tools/call\",\"params\":{\"name\":\"create_network\",\"arguments\":{\"cx2Network\":\"${MCP_CX2_ESCAPED}\",\"cx2NetworkSize\":${MCP_CX2_SIZE},\"cx2NetworkChunkTotalCount\":1,\"cx2NetworkCurrentChunkNumber\":1,\"visibility\":\"PRIVATE\"}}}" \
  "-u ${TEST_USER}:${TEST_PASS}"
mcp_pass "create_network (auth)"
MCP_NETWORK_UUID=$(echo "${MCP_JSON}" | grep -o '"networkId":"[^"]*"' | head -1 | cut -d'"' -f4)
[[ -n "${MCP_NETWORK_UUID}" ]] \
  || api_fail "create_network → no networkId in response: ${MCP_JSON:0:300}"

echo "  Polling GET /v2/network/${MCP_NETWORK_UUID}/summary until completed:true..."
ELAPSED=0
while true; do
  SUMMARY=$(curl -s -u "${TEST_USER}:${TEST_PASS}" "${BASE_URL}/v2/network/${MCP_NETWORK_UUID}/summary")
  echo "${SUMMARY}" | grep -q '"completed":true' && break
  [[ ${ELAPSED} -ge ${LOAD_TIMEOUT} ]] \
    && api_fail "MCP-created network ${MCP_NETWORK_UUID} did not complete within ${LOAD_TIMEOUT}s"
  echo -e "  ${CYAN}Waiting for MCP-created network... (${ELAPSED}s)${NC}"
  sleep 5; (( ELAPSED += 5 )) || true
done
echo "  MCP-created network confirmed complete"

CALL_NUM=$((CALL_NUM+1))
echo "  API call ${CALL_NUM}/${TOTAL_API_CALLS}: update_network (auth)"
mcp_call "{\"jsonrpc\":\"2.0\",\"id\":\"mcp-20\",\"method\":\"tools/call\",\"params\":{\"name\":\"update_network\",\"arguments\":{\"networkId\":\"${MCP_NETWORK_UUID}\",\"cx2Network\":\"${MCP_CX2_ESCAPED}\",\"cx2NetworkSize\":${MCP_CX2_SIZE},\"cx2NetworkChunkTotalCount\":1,\"cx2NetworkCurrentChunkNumber\":1}}}" \
  "-u ${TEST_USER}:${TEST_PASS}"
mcp_pass "update_network (auth)"

echo "  Polling GET /v2/network/${MCP_NETWORK_UUID}/summary until updated network completes..."
ELAPSED=0
while true; do
  SUMMARY=$(curl -s -u "${TEST_USER}:${TEST_PASS}" "${BASE_URL}/v2/network/${MCP_NETWORK_UUID}/summary")
  echo "${SUMMARY}" | grep -q '"completed":true' && break
  [[ ${ELAPSED} -ge ${LOAD_TIMEOUT} ]] \
    && api_fail "MCP-updated network ${MCP_NETWORK_UUID} did not complete within ${LOAD_TIMEOUT}s"
  echo -e "  ${CYAN}Waiting for MCP-updated network... (${ELAPSED}s)${NC}"
  sleep 5; (( ELAPSED += 5 )) || true
done
echo "  MCP-updated network confirmed complete"

# ── STEP: MCP profile, properties, systemproperties, download ────────────────

step "MCP update_network_profile, set_network_properties, set_network_systemproperties, download_network"

CALL_NUM=$((CALL_NUM+1))
echo "  API call ${CALL_NUM}/${TOTAL_API_CALLS}: update_network_profile (auth)"
mcp_call '{"jsonrpc":"2.0","id":"mcp-21","method":"tools/call","params":{"name":"update_network_profile","arguments":{"networkId":"'"${MCP_NETWORK_UUID}"'","name":"MCP Integration Test","visibility":"PRIVATE","description":"Created by integration-mcp-test.sh"}}}' \
  "-u ${TEST_USER}:${TEST_PASS}"
mcp_pass "update_network_profile (auth)"

CALL_NUM=$((CALL_NUM+1))
echo "  API call ${CALL_NUM}/${TOTAL_API_CALLS}: set_network_properties (auth)"
mcp_call '{"jsonrpc":"2.0","id":"mcp-22","method":"tools/call","params":{"name":"set_network_properties","arguments":{"networkId":"'"${MCP_NETWORK_UUID}"'","properties":[{"predicateString":"author","value":"MCP Test"},{"predicateString":"organism","value":"Test species"}]}}}' \
  "-u ${TEST_USER}:${TEST_PASS}"
mcp_pass "set_network_properties (auth)"

CALL_NUM=$((CALL_NUM+1))
echo "  API call ${CALL_NUM}/${TOTAL_API_CALLS}: set_network_systemproperties visibility=PUBLIC (auth)"
mcp_call '{"jsonrpc":"2.0","id":"mcp-23","method":"tools/call","params":{"name":"set_network_systemproperties","arguments":{"networkId":"'"${MCP_NETWORK_UUID}"'","visibility":"PUBLIC"}}}' \
  "-u ${TEST_USER}:${TEST_PASS}"
mcp_pass "set_network_systemproperties visibility=PUBLIC (auth)"

CALL_NUM=$((CALL_NUM+1))
echo "  API call ${CALL_NUM}/${TOTAL_API_CALLS}: set_network_systemproperties readonly=false (auth)"
mcp_call '{"jsonrpc":"2.0","id":"mcp-24","method":"tools/call","params":{"name":"set_network_systemproperties","arguments":{"networkId":"'"${MCP_NETWORK_UUID}"'","readonly":false}}}' \
  "-u ${TEST_USER}:${TEST_PASS}"
mcp_pass "set_network_systemproperties readonly=false (auth)"

CALL_NUM=$((CALL_NUM+1))
echo "  API call ${CALL_NUM}/${TOTAL_API_CALLS}: get_network_summary after systemproperties update (auth)"
mcp_call '{"jsonrpc":"2.0","id":"mcp-25","method":"tools/call","params":{"name":"get_network_summary","arguments":{"networkId":"'"${MCP_NETWORK_UUID}"'"}}}' \
  "-u ${TEST_USER}:${TEST_PASS}"
mcp_pass "get_network_summary MCP-created network (auth)"

CALL_NUM=$((CALL_NUM+1))
echo "  API call ${CALL_NUM}/${TOTAL_API_CALLS}: download_network public network (anon)"
# network_summary passes {"modificationTime":0} — Jackson sets Date to epoch.
# file_path does not exist on server, so cache-hit check is skipped; first chunk is returned.
mcp_call '{"jsonrpc":"2.0","id":"mcp-26","method":"tools/call","params":{"name":"download_network","arguments":{"networkId":"'"${MCP_PUBLIC_UUID}"'","network_summary":{"modificationTime":0},"file_path":"/tmp/mcp_dl_anon.cx2","cx2NetworkCurrentChunkNumber":1}}}'
mcp_pass "download_network public (anon)"

CALL_NUM=$((CALL_NUM+1))
echo "  API call ${CALL_NUM}/${TOTAL_API_CALLS}: download_network public network (auth)"
mcp_call '{"jsonrpc":"2.0","id":"mcp-27","method":"tools/call","params":{"name":"download_network","arguments":{"networkId":"'"${MCP_PUBLIC_UUID}"'","network_summary":{"modificationTime":0},"file_path":"/tmp/mcp_dl_auth.cx2","cx2NetworkCurrentChunkNumber":1}}}' \
  "-u ${TEST_USER}:${TEST_PASS}"
mcp_pass "download_network public (auth)"

# ── STEP: MCP get_user_networks ───────────────────────────────────────────────

step "MCP get_user_networks: happy-path"

CALL_NUM=$((CALL_NUM+1))
echo "  API call ${CALL_NUM}/${TOTAL_API_CALLS}: get_user_networks (auth)"
mcp_call '{"jsonrpc":"2.0","id":"mcp-gun","method":"tools/call","params":{"name":"get_user_networks","arguments":{}}}' \
  "-u ${TEST_USER}:${TEST_PASS}"
mcp_pass "get_user_networks (auth)"
echo "${MCP_JSON}" | grep -qE '"count":[1-9][0-9]*' \
  || api_fail "get_user_networks → expected count >= 1 in response: ${MCP_JSON:0:300}"
echo "${MCP_JSON}" | grep -q '"networks":\[' \
  || api_fail "get_user_networks → expected networks array in response: ${MCP_JSON:0:300}"

# ── STEP: MCP get_user_info ───────────────────────────────────────────────────

step "MCP get_user_info: happy-path"

CALL_NUM=$((CALL_NUM+1))
echo "  API call ${CALL_NUM}/${TOTAL_API_CALLS}: get_user_info (auth)"
mcp_call '{"jsonrpc":"2.0","id":"mcp-gui","method":"tools/call","params":{"name":"get_user_info","arguments":{}}}' \
  "-u ${TEST_USER}:${TEST_PASS}"
mcp_pass "get_user_info (auth)"
echo "${MCP_JSON}" | grep -q '"user":{' \
  || api_fail "get_user_info → expected 'user' object in response: ${MCP_JSON:0:300}"
echo "${MCP_JSON}" | grep -q '"network_count":' \
  || api_fail "get_user_info → expected 'network_count' in response: ${MCP_JSON:0:300}"
echo "${MCP_JSON}" | grep -q "\"userName\":\"${TEST_USER}\"" \
  || api_fail "get_user_info → expected userName:${TEST_USER} in response: ${MCP_JSON:0:300}"

# ── STEP: MCP share_network ───────────────────────────────────────────────────

step "MCP share_network: validation and happy-path tests"

CALL_NUM=$((CALL_NUM+1))
echo "  API call ${CALL_NUM}/${TOTAL_API_CALLS}: share_network no subject (auth, expect isError)"
mcp_call '{"jsonrpc":"2.0","id":"mcp-sn-nosubject","method":"tools/call","params":{"name":"share_network","arguments":{"networkId":"'"${MCP_NETWORK_UUID}"'","permission":"READ"}}}' \
  "-u ${TEST_USER}:${TEST_PASS}"
mcp_fail_expected "share_network no userId/groupId (auth)"

CALL_NUM=$((CALL_NUM+1))
echo "  API call ${CALL_NUM}/${TOTAL_API_CALLS}: share_network userId READ (auth)"
mcp_call '{"jsonrpc":"2.0","id":"mcp-sn-read","method":"tools/call","params":{"name":"share_network","arguments":{"networkId":"'"${MCP_NETWORK_UUID}"'","userId":"'"${TEST_USER2_UUID}"'","permission":"READ"}}}' \
  "-u ${TEST_USER}:${TEST_PASS}"
mcp_pass "share_network userId READ (auth)"
echo "${MCP_JSON}" | grep -q '"subjectType":"user"' \
  || api_fail "share_network → expected subjectType:user in response: ${MCP_JSON:0:300}"
echo "${MCP_JSON}" | grep -q '"permission":"READ"' \
  || api_fail "share_network → expected permission:READ in response: ${MCP_JSON:0:300}"

# ── STEP: MCP folder management ───────────────────────────────────────────────

step "MCP folder management: manage_folder create/delete, get_folder list"

MCP_FOLDER_ID=""

CALL_NUM=$((CALL_NUM+1))
echo "  API call ${CALL_NUM}/${TOTAL_API_CALLS}: manage_folder mode=create (auth)"
mcp_call '{"jsonrpc":"2.0","id":"mcp-28","method":"tools/call","params":{"name":"manage_folder","arguments":{"mode":"create","name":{"waived":false,"parameter":"mcp-integration-test-folder"}}}}' \
  "-u ${TEST_USER}:${TEST_PASS}"
mcp_pass "manage_folder mode=create (auth)"
MCP_FOLDER_ID=$(echo "${MCP_JSON}" | grep -o '"folderId":"[^"]*"' | head -1 | cut -d'"' -f4)
[[ -n "${MCP_FOLDER_ID}" ]] \
  || api_fail "manage_folder create → no folderId in response: ${MCP_JSON:0:300}"

CALL_NUM=$((CALL_NUM+1))
echo "  API call ${CALL_NUM}/${TOTAL_API_CALLS}: get_folder mode=list (auth)"
mcp_call '{"jsonrpc":"2.0","id":"mcp-29","method":"tools/call","params":{"name":"get_folder","arguments":{"mode":"list"}}}' \
  "-u ${TEST_USER}:${TEST_PASS}"
mcp_pass "get_folder mode=list (auth)"

CALL_NUM=$((CALL_NUM+1))
echo "  API call ${CALL_NUM}/${TOTAL_API_CALLS}: manage_folder mode=delete (auth)"
mcp_call '{"jsonrpc":"2.0","id":"mcp-30","method":"tools/call","params":{"name":"manage_folder","arguments":{"mode":"delete","folderId":{"waived":false,"parameter":"'"${MCP_FOLDER_ID}"'"}}}}' \
  "-u ${TEST_USER}:${TEST_PASS}"
mcp_pass "manage_folder mode=delete (auth)"

# ── STEP: MCP delete_network (cleanup) ────────────────────────────────────────

step "MCP delete_network — permanent delete of MCP-created network"

CALL_NUM=$((CALL_NUM+1))
echo "  API call ${CALL_NUM}/${TOTAL_API_CALLS}: delete_network permanent (auth)"
mcp_call '{"jsonrpc":"2.0","id":"mcp-31","method":"tools/call","params":{"name":"delete_network","arguments":{"networkId":"'"${MCP_NETWORK_UUID}"'","permanent":true}}}' \
  "-u ${TEST_USER}:${TEST_PASS}"
mcp_pass "delete_network permanent (auth)"

# ── Summary ───────────────────────────────────────────────────────────────────

echo ""
echo -e "${GREEN}${BOLD}================================================${NC}"
echo -e "${GREEN}${BOLD}  ✓ ALL ${PASSED} API CALLS PASSED — TEST PASSED${NC}"
echo -e "${GREEN}${BOLD}================================================${NC}"
exit 0
