#!/usr/bin/env bash
# Search: v2 network search, v3 file search, filter-injection handling and result ranking.
#
# Sourced by integration-test.sh, never executed on its own: the whole suite shares one shell, so
# this group sees the harness helpers and the fixtures earlier groups created, and leaves its own
# behind for the groups that follow.

step "Searching v2 networks via POST /v2/search/network"

CALL_NUM=$((CALL_NUM+1))
echo "  API call ${CALL_NUM}: POST /v2/search/network?searchString=WP1984 (anon, expect 200 + UUID)"

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
# which queries the ndex-nfs core directly. Requires authentication.
CALL_NUM=$((CALL_NUM+1))
echo "  API call ${CALL_NUM}: POST /v3/search/files?visibility=PUBLIC (auth, expect 200 + UUID)"

# Poll until the UUID appears — the Solr commit can be async (especially
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
  echo "  Waiting for Solr index... (${ELAPSED}s)"
done
api_pass "POST /v3/search/files → 200 OK, BindingDB UUID found in results (CX2 indexing confirmed)"

# ── STEP: v3 /search/files neutralizes Solr filter injection (F4) ────────────
# A crafted accountName that tries to OR-in a match-all clause must be escaped so it
# cannot widen results past the owner filter. BindingDB (public, confirmed indexed by
# the previous step) must NOT appear: the escaped accountName is a single literal,
# non-existent owner. A regression (unescaped value) would collapse the filter to *:*
# and leak BindingDB. The escaped query must also stay valid Solr syntax (HTTP 200).
step "Verifying v3 /search/files neutralizes Solr filter injection (accountName)"

INJECT_BODY='{"searchString":"*:*","accountName":"zzz\") OR (*:*) OR (owner:\"zzz"}'

CALL_NUM=$((CALL_NUM+1))
echo "  API call ${CALL_NUM}: POST /v3/search/files (auth, accountName injection, expect 200 + BindingDB absent)"
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
echo "  API call ${CALL_NUM}: POST /v3/search/files (anon, accountName injection, expect 200 + BindingDB absent)"
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
  echo "  API call ${CALL_NUM}: POST /v3/networks?visibility=PUBLIC  [${RANK_LABEL}]"

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
echo "  API call ${CALL_NUM}: POST /v3/search/files?visibility=PUBLIC (searchString=EdgelessRankProbe)"

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

# ── STEP: One ranked result set spanning PUBLIC and PRIVATE ──────────────────
# The payoff of a single core. Three networks carry the same query tokens in their names and differ
# only in how much other text sits alongside them, so BM25 field-length normalization fixes their
# relative order: the shortest name ranks first. The middle one is PRIVATE and owned by the caller.
#
# Two cores could not produce this list. Each computed IDF from its own corpus, so a score from the
# public core and a score from the private core were not comparable, and a client merging two
# responses could only concatenate them — the private network would land at one end, never between
# the two public ones. Position 2 is therefore the assertion that matters.
#
# The search deliberately sends no visibility parameter: absence means everything the caller may
# see, which is what puts both partitions in one ranked list.

step "One ranked result set interleaves PUBLIC and PRIVATE networks"

CV_PUB_TOP=""; CV_PRIV=""; CV_PUB_BOTTOM=""
CV_INDEX=0
for CV_FILE in "${FIXTURES_DIR}/ranking/crossvis-1-public.cx2" \
               "${FIXTURES_DIR}/ranking/crossvis-2-private.cx2" \
               "${FIXTURES_DIR}/ranking/crossvis-3-public.cx2"; do
  CV_INDEX=$((CV_INDEX+1))
  if [[ ${CV_INDEX} -eq 2 ]]; then CV_VIS="PRIVATE"; else CV_VIS="PUBLIC"; fi

  CALL_NUM=$((CALL_NUM+1))
  echo "  API call ${CALL_NUM}: POST /v3/networks?visibility=${CV_VIS}  [$(basename "${CV_FILE}")]"
  CV_RESP=$(curl -s -w "\n%{http_code}" -X POST -u "${TEST_USER}:${TEST_PASS}" \
    -H "Content-Type: application/json" --data-binary "@${CV_FILE}" \
    "${BASE_URL}/v3/networks?visibility=${CV_VIS}")
  CV_HTTP=$(echo "${CV_RESP}" | tail -1); CV_BODY=$(echo "${CV_RESP}" | head -1)
  [[ "${CV_HTTP}" == "201" ]] \
    || api_fail "POST /v3/networks (${CV_VIS} cross-vis probe) → HTTP ${CV_HTTP}. Body: ${CV_BODY:0:300}"
  CV_UUID=$(echo "${CV_BODY}" | grep -o '"uuid":"[^"]*"' | head -1 | cut -d'"' -f4)
  [[ -n "${CV_UUID}" ]] || api_fail "no uuid in cross-vis probe create body. Body: ${CV_BODY:0:300}"

  case ${CV_INDEX} in
    1) CV_PUB_TOP="${CV_UUID}" ;;
    2) CV_PRIV="${CV_UUID}" ;;
    3) CV_PUB_BOTTOM="${CV_UUID}" ;;
  esac
done
api_pass "uploaded 3 cross-visibility probes: PUBLIC ${CV_PUB_TOP}, PRIVATE ${CV_PRIV}, PUBLIC ${CV_PUB_BOTTOM}"

for CV_UUID in "${CV_PUB_TOP}" "${CV_PRIV}" "${CV_PUB_BOTTOM}"; do
  ELAPSED=0
  while ! curl -s -u "${TEST_USER}:${TEST_PASS}" "${BASE_URL}/v3/networks/${CV_UUID}/summary" \
       | grep -q '"completed":true'; do
    [[ ${ELAPSED} -ge ${LOAD_TIMEOUT} ]] \
      && api_fail "cross-vis probe ${CV_UUID} did not complete within ${LOAD_TIMEOUT}s"
    sleep 5; (( ELAPSED += 5 )) || true
    echo "  Waiting for cross-vis probe ${CV_UUID}... (${ELAPSED}s)"
  done
done

CALL_NUM=$((CALL_NUM+1))
echo "  API call ${CALL_NUM}: POST /v3/search/files (no visibility — searchString=CrossVisRankProbe kinase signaling)"

ELAPSED=0
while true; do
  CV_SEARCH=$(curl -s -w "\n%{http_code}" -X POST -u "${TEST_USER}:${TEST_PASS}" \
    -H "Content-Type: application/json" \
    -d '{"searchString":"CrossVisRankProbe kinase signaling"}' \
    "${BASE_URL}/v3/search/files?start=0&size=10")
  CV_HTTP=$(echo "${CV_SEARCH}" | tail -1); CV_BODY=$(echo "${CV_SEARCH}" | head -1)
  [[ "${CV_HTTP}" == "200" ]] \
    || api_fail "POST /v3/search/files (cross-vis) → HTTP ${CV_HTTP}. Body: ${CV_BODY:0:300}"
  CV_ORDER=$(echo "${CV_BODY}" | grep -oE '"uuid":"[^"]*"' | cut -d'"' -f4 || true)
  CV_P1=$(echo "${CV_ORDER}" | grep -n "^${CV_PUB_TOP}$"    | head -1 | cut -d: -f1 || true)
  CV_P2=$(echo "${CV_ORDER}" | grep -n "^${CV_PRIV}$"       | head -1 | cut -d: -f1 || true)
  CV_P3=$(echo "${CV_ORDER}" | grep -n "^${CV_PUB_BOTTOM}$" | head -1 | cut -d: -f1 || true)
  [[ -n "${CV_P1}" && -n "${CV_P2}" && -n "${CV_P3}" ]] && break
  [[ ${ELAPSED} -ge ${LOAD_TIMEOUT} ]] \
    && api_fail "one ranked result set did not contain all 3 cross-vis probes within ${LOAD_TIMEOUT}s (public=${CV_P1:-absent} private=${CV_P2:-absent} public=${CV_P3:-absent}). Body: ${CV_BODY:0:500}"
  sleep 3; (( ELAPSED += 3 )) || true
  echo "  Waiting for cross-vis probes to index... (${ELAPSED}s)"
done
api_pass "one authenticated search returned both partitions in a single result set (positions ${CV_P1}, ${CV_P2}, ${CV_P3})"

if [[ ${CV_P1} -lt ${CV_P2} && ${CV_P2} -lt ${CV_P3} ]]; then
  api_pass "PRIVATE network is interleaved between the two PUBLIC ones (${CV_P1} < ${CV_P2} < ${CV_P3}) — one comparable ranking across visibilities"
else
  api_fail "cross-visibility ranking is wrong: expected public(${CV_P1}) < private(${CV_P2}) < public(${CV_P3}). Concatenated result sets would put the private network at an end. Body: ${CV_BODY:0:500}"
fi

# The control: the same query without credentials must keep the two public networks in the same
# relative order and drop the private one. That distinguishes a genuine ranking from a list that
# merely happens to contain everything.
CALL_NUM=$((CALL_NUM+1))
echo "  API call ${CALL_NUM}: POST /v3/search/files (anonymous, same searchString)"
CV_ANON=$(curl -s -w "\n%{http_code}" -X POST -H "Content-Type: application/json" \
  -d '{"searchString":"CrossVisRankProbe kinase signaling"}' \
  "${BASE_URL}/v3/search/files?start=0&size=10")
CV_ANON_HTTP=$(echo "${CV_ANON}" | tail -1); CV_ANON_BODY=$(echo "${CV_ANON}" | head -1)
[[ "${CV_ANON_HTTP}" == "200" ]] \
  || api_fail "POST /v3/search/files (anonymous cross-vis) → HTTP ${CV_ANON_HTTP}. Body: ${CV_ANON_BODY:0:300}"
CV_ANON_ORDER=$(echo "${CV_ANON_BODY}" | grep -oE '"uuid":"[^"]*"' | cut -d'"' -f4 || true)
CV_A1=$(echo "${CV_ANON_ORDER}" | grep -n "^${CV_PUB_TOP}$"    | head -1 | cut -d: -f1 || true)
CV_A3=$(echo "${CV_ANON_ORDER}" | grep -n "^${CV_PUB_BOTTOM}$" | head -1 | cut -d: -f1 || true)
echo "${CV_ANON_ORDER}" | grep -q "^${CV_PRIV}$" \
  && api_fail "anonymous search returned the PRIVATE network ${CV_PRIV}. Body: ${CV_ANON_BODY:0:500}"
[[ -n "${CV_A1}" && -n "${CV_A3}" && ${CV_A1} -lt ${CV_A3} ]] \
  || api_fail "anonymous search lost the public ordering: expected ${CV_PUB_TOP} before ${CV_PUB_BOTTOM}, got positions ${CV_A1:-absent} and ${CV_A3:-absent}. Body: ${CV_ANON_BODY:0:500}"
api_pass "anonymous search drops the PRIVATE network and keeps the two PUBLIC ones in the same order (${CV_A1} < ${CV_A3})"


# ── STEP: a permission on a search asks what the caller may change ────────────
# An anonymous caller holds no permission on anything, so WRITE and ADMIN must come back empty. The
# public arm of the permission filter is withheld from those searches for everyone, which is what
# makes this true rather than a side effect of anonymity.
#
# The two controls are the point of the step: without them, "empty" also passes when the endpoint is
# broken outright. The anonymous READ-equivalent search must still find the public probes, and the
# owner's own WRITE search must still find what they own.

step "A permission on a search asks what the caller may change"

# uuid count in a /v3/search/files body — the response also carries numFound, which is asserted below.
# The no-match case is the one being measured here, so grep's exit 1 has to stay out of the pipeline:
# under pipefail it would fail the command substitution and take the script with it.
cv_hits() { # body -> count
  local matches
  matches=$(grep -oE '"uuid":"[^"]*"' <<<"$1" || true)
  if [[ -z "${matches}" ]]; then echo 0; else grep -c '' <<<"${matches}"; fi
}

# The probe token alone, so the counts asserted below cannot be moved by an unrelated document that
# happens to carry "kinase" or "signaling" — the token exists only in the three probe fixtures.
cv_search() { # auth(or empty) permission(or empty) -> body
  local auth="$1" perm="$2" payload
  if [[ -z "${perm}" ]]; then
    payload="{\"searchString\":\"CrossVisRankProbe\"}"
  else
    payload="{\"searchString\":\"CrossVisRankProbe\",\"permission\":\"${perm}\"}"
  fi
  if [[ -z "${auth}" ]]; then
    curl -s -X POST -H 'Content-Type: application/json' -d "${payload}" \
      "${BASE_URL}/v3/search/files?start=0&size=10"
  else
    curl -s -X POST -u "${auth}" -H 'Content-Type: application/json' -d "${payload}" \
      "${BASE_URL}/v3/search/files?start=0&size=10"
  fi
}

# Control: the same query without a permission must find the two public probes, so an empty result
# below means the permission was applied rather than the query failing to match.
CALL_NUM=$((CALL_NUM+1))
echo "  API call ${CALL_NUM}: POST /v3/search/files (anonymous, no permission) — control"
CV_CTL=$(cv_search "" "")
CV_CTL_N=$(grep -oE '"numFound":[0-9]+' <<<"${CV_CTL}" | grep -oE '[0-9]+$' || echo unset)
[[ "${CV_CTL_N}" == "2" ]] \
  || api_fail "anonymous control search should find the 2 public probes, got numFound=${CV_CTL_N}. Body: ${CV_CTL:0:400}"
api_pass "anonymous search with no permission finds the 2 public probes (control)"

for CV_PERM in WRITE ADMIN; do
  CALL_NUM=$((CALL_NUM+1))
  echo "  API call ${CALL_NUM}: POST /v3/search/files (anonymous, permission=${CV_PERM})"
  CV_BODY=$(cv_search "" "${CV_PERM}")
  CV_N=$(grep -oE '"numFound":[0-9]+' <<<"${CV_BODY}" | grep -oE '[0-9]+$' || echo unset)
  CV_UUIDS_SEEN=$(cv_hits "${CV_BODY}")
  [[ "${CV_N}" == "0" && "${CV_UUIDS_SEEN}" == "0" ]] \
    || api_fail "anonymous permission=${CV_PERM} search must return nothing, got numFound=${CV_N} with ${CV_UUIDS_SEEN} file(s). Body: ${CV_BODY:0:400}"
  api_pass "anonymous permission=${CV_PERM} search returns an empty result set"
done

# Control: the same permission, with credentials, still finds what the caller owns — so the empty
# results above are the caller's lack of permission rather than a permission filter that drops
# everything it touches.
CALL_NUM=$((CALL_NUM+1))
echo "  API call ${CALL_NUM}: POST /v3/search/files (authenticated owner, permission=WRITE) — control"
CV_OWN=$(cv_search "${TEST_USER}:${TEST_PASS}" WRITE)
CV_OWN_N=$(grep -oE '"numFound":[0-9]+' <<<"${CV_OWN}" | grep -oE '[0-9]+$' || echo unset)
[[ "${CV_OWN_N}" == "3" ]] \
  || api_fail "the owner's permission=WRITE search should find all 3 probes they own, got numFound=${CV_OWN_N}. Body: ${CV_OWN:0:400}"
api_pass "the owner's permission=WRITE search still finds the 3 probes they own (control)"


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
  echo "  API call ${CALL_NUM}: POST /v2/search/network/${QUERY_UUID}/query (auth, expect 200)"

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
  echo "  API call ${CALL_NUM}: POST /v3/search/networks/${QUERY_UUID_V3}/query (auth, expect 200)"

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
  echo "  API call ${CALL_NUM}: GET /v3/networks/${V3_UUIDS[0]}/summary (expect errorMessage set)"
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
  echo "  API call ${CALL_NUM}: GET /v3/admin/reindex-v3?password=changeme (expect 200)"
  REINDEX_HTTP=$(curl -s -o /dev/null -w "%{http_code}" \
    "${BASE_URL}/v3/admin/reindex-v3?password=changeme")
  if [[ "${REINDEX_HTTP}" == "200" ]]; then
    api_pass "GET /v3/admin/reindex-v3 → 200 OK"
  else
    api_fail "GET /v3/admin/reindex-v3 → HTTP ${REINDEX_HTTP} (expected 200)"
  fi

  CALL_NUM=$((CALL_NUM+1))
  echo "  API call ${CALL_NUM}: GET /v3/networks/${V3_UUIDS[0]}/summary (expect errorMessage cleared)"
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

