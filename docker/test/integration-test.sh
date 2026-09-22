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
#
# Adding a test: call `step "..."`, then for each API call bump CALL_NUM, echo the progress line, and
# assert with api_pass/api_fail. There is no total to update — see the counter notes below.

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
FIXTURES_DIR="${SCRIPT_DIR}/fixtures"
REPO_DIR="$(cd "${SCRIPT_DIR}/../.." && pwd)"
BASE_URL="http://localhost:8080"

CONTAINER_NAME="ndex-integration-test"
# One volume per group. Group 2 (migration) uses its own; the two never share state.
FUNCTIONAL_VOLUME="ndex-it-functional-data"
TEST_USER="ndextest"
TEST_PASS="NDExTest1!"
TEST_EMAIL="ndextest@ndex-integration.local"
TEST_USER2="ndextest2"
TEST_PASS2="NDExTest2!"
TEST_EMAIL2="ndextest2@ndex-integration.local"

# ── Counters ──────────────────────────────────────────────────────────────────
# Both counters are self-maintaining: ADDING OR REMOVING API CALLS REQUIRES NO BOOKKEEPING HERE.
# There is deliberately no hand-maintained expected total — see the note below on why one cannot
# exist. Just call `step`, bump CALL_NUM, and call api_pass/api_fail; the totals follow.
#
#   CALL_NUM — numbered progress lines emitted so far ("API call N: ..."). Incremented by hand at
#              each call site, and internally by the assert_* helpers.
#   PASSED   — assertions that reported success, i.e. api_pass invocations. The two postgres
#              resilience checks near the end print a "✓ PASS" line directly instead of calling
#              api_pass, because they are container-level checks rather than API calls; they are
#              intentionally not counted here.
#
# PASSED IS LEGITIMATELY LOWER THAN CALL_NUM, so do not "fix" a mismatch between them. Some steps
# make two numbered calls and then assert on both together under a single api_pass (e.g. the paired
# /list + /count checks, and the visibility round-trips that set then read back). At the time of
# writing a full local run ends at CALL_NUM=148 with PASSED=134. Both numbers are reported at the
# end so the gap is visible rather than surprising.
#
# Why there is no expected-total constant: a total would have to be known before the first call, but
# the real count is only knowable by running. Loop-driven call sites (the v2/v3 upload and retrieve
# loops, the ranking-fixture loop) each execute their single increment several times, the assert_*
# helpers increment once per invocation, and whole blocks are skipped under --remote-ndex-url. A
# static count of increment sites in this file gives 126 against an actual 148, and any hardcoded
# number silently rots the moment a call is added. A previous constant here drifted for exactly that
# reason and was mistaken for a real defect, so progress is now reported as a plain sequence number.
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

# ── Harness and test groups ───────────────────────────────────────────────────
# Sourced rather than executed: the suite runs in one shell so fixtures created by an earlier
# group stay visible to a later one. Order is therefore significant.
source "${SCRIPT_DIR}/lib/harness.sh"


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
    -v "${FUNCTIONAL_VOLUME}:/apps" \
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

source "${SCRIPT_DIR}/groups/10-networks.sh"
source "${SCRIPT_DIR}/groups/20-search.sh"
source "${SCRIPT_DIR}/groups/30-files.sh"
source "${SCRIPT_DIR}/groups/40-permissions.sh"

# ══════════════════════════════════════════════════════════════════════════════
# GROUP 2 — upgrade test: current released image → this build
#
# Runs only after group 1 is completely torn down, on its own separate volume, so
# neither group can contaminate the other's state or obscure its failures. Skipped
# in remote mode along with every other container-only step.
# ══════════════════════════════════════════════════════════════════════════════
if [[ -z "${REMOTE_NDEX_URL}" ]]; then
  step "Tearing down group 1 before the migration group"
  _remove_test_containers
  rm -f "${TMP_CATALINA_TOML:-}"
  echo "  group 1 container and volume removed"

  step "GROUP 2: upgrade test (released image → current build)"
  # The image is already built by group 1; don't rebuild it.
  MIGRATION_SPANS="${MIGRATION_SPANS:-3.0.4 3.0.6}"
  MIGRATION_OK=true
  for MIG_BASE in ${MIGRATION_SPANS}; do
    echo ""
    echo "── upgrade span ${MIG_BASE} → current ──"
    "${SCRIPT_DIR}/migration-test.sh" --skip-build --base "${MIG_BASE}" || { MIGRATION_OK=false; break; }
  done
  if [[ "${MIGRATION_OK}" == true ]]; then
    api_pass "upgrade from the released image preserves data and advances the schema"
  else
    api_fail "migration test failed — see output above"
  fi
fi

# ── Summary ───────────────────────────────────────────────────────────────────
# Both counters are printed because they measure different things and differ by design — see the
# counter notes at the top of this file before "correcting" either number.

echo ""
echo -e "${GREEN}${BOLD}================================================${NC}"
echo -e "${GREEN}${BOLD}  ✓ TEST PASSED${NC}"
echo -e "${GREEN}${BOLD}    API calls attempted : ${CALL_NUM}${NC}"
echo -e "${GREEN}${BOLD}    Assertions passed   : ${PASSED}${NC}"
echo -e "${GREEN}${BOLD}================================================${NC}"
exit 0
