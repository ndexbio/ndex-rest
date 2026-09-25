#!/usr/bin/env bash
# Shared assertions, polling and cleanup for the integration suite.
#
# Sourced by integration-test.sh before any group. Defines the helpers every group calls and
# registers the cleanup trap that removes test containers and volumes on exit.

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
  echo ""
  echo -e "  ${RED}✗ FAIL${NC}: ${reason}"
  echo ""
  echo -e "${RED}${BOLD}TEST FAILED${NC}"
  echo -e "  API calls attempted : ${CALL_NUM}"
  echo -e "  Assertions passed   : ${PASSED}"
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
    echo "  Waiting for the visibility=${vis} Solr index... (${elapsed}s)"
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
    [[ ${elapsed} -ge ${LOAD_TIMEOUT} ]] && api_fail "${label}: uuid ${uuid} still present in visibility=${vis} after ${LOAD_TIMEOUT}s (stale index entry — the re-index did not replace it). Body: ${body:0:400}"
    sleep 3; (( elapsed += 3 )) || true
    echo "  Waiting for the visibility=${vis} Solr drop to converge... (${elapsed}s)"
  done
}

# Assert that a retired group endpoint returns HTTP 501. Always sends valid auth so the
# request passes the auth filter and reaches the (501-throwing) resource method.
# Usage: assert_group_501 <METHOD> <URL> [extra curl args...]
assert_group_501() {
  local method="$1"; local url="$2"; shift 2
  CALL_NUM=$((CALL_NUM+1))
  echo "  API call ${CALL_NUM}: ${method} ${url} (expect 501)"
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
  echo "  API call ${CALL_NUM}: ${method} ${url} (expect ${expected})"
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

# Block until no system task is in flight, so a DB row a test is about to assert on has stopped
# moving. Solr indexing is queued asynchronously and several of its failure handlers write to
# network.error, so any assertion on that column has to be made against a settled row rather than
# against whichever write happened to land first. Args: label (used in the timeout message)
wait_for_task_queue_drain() {
  local label="$1"
  local elapsed=0
  until [[ "$(psql_ndex "SELECT count(*) FROM core.task WHERE status IN ('QUEUED','PROCESSING')")" == "0" ]]; do
    [[ ${elapsed} -ge ${LOAD_TIMEOUT} ]] \
      && api_fail "background tasks still pending ${LOAD_TIMEOUT}s after ${label}; the assertion that follows would be racing them"
    echo -e "  ${CYAN}Waiting for background tasks to drain (${label})... (${elapsed}s)${NC}"
    sleep 2; elapsed=$((elapsed + 2))
  done
}

# ── Cleanup trap ──────────────────────────────────────────────────────────────

_remove_test_containers() {
  docker rm -fv "${CONTAINER_NAME}" 2>/dev/null || true
  docker rm -fv "ndex-pg-corrupt-test" 2>/dev/null || true
  docker rm -fv "ndex-pg-wipe-test" 2>/dev/null || true
  # Named volumes are not removed by `docker rm -v`; they need an explicit call, and a
  # stale one would silently turn the next run's fresh install into an upgrade.
  docker volume rm "${FUNCTIONAL_VOLUME}" 2>/dev/null || true
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
