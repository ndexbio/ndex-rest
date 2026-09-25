#!/usr/bin/env bash
# Files and index maintenance: folder and shortcut listing, visibility, access keys, type filters,
# networkset compatibility, and the reindex paths that keep them searchable.
#
# Sourced by integration-test.sh, never executed on its own: the whole suite shares one shell, so
# this group sees the harness helpers and the fixtures earlier groups created, and leaves its own
# behind for the groups that follow.

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
  echo "  API call ${CALL_NUM}: GET /v3/networks/${V3_PUB_UUID}/summary (auth, expect UNLISTED)"
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
  echo "  API call ${CALL_NUM}: POST /v3/search/files?visibility=PUBLIC (anon, expect UUID absent — Solr doc updated to UNLISTED)"
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
echo "  API call ${CALL_NUM}: POST /v2/user (auth as ${TEST_USER}) to ensure ${TEST_USER2} exists"
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
echo "  API call ${CALL_NUM}: POST /v2/batch/network/summary (anon, expect exactly 1 row)"
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
echo "  API call ${CALL_NUM}: POST /v2/batch/network/summary (auth ${TEST_USER2}, expect exactly 1 row)"
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
echo "  API call ${CALL_NUM}: GET /v2/network/${V2_PRIV_UUID}/permission?type=user (auth owner, expect 200)"
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

# ── STEP: Invalid network omits name in folder listing (#161) ────────────────
# A network whose CX2 fails validation never gets a name: CX2NetworkLoader.persistCXNetwork()
# validates before it reaches saveCX2NetworkEntry, so summary.setName() is never called and
# network.name stays NULL. CX2NetworkLoadingTask then marks the row complete+invalid with an
# errorMessage but never touches name. FileItemSummary is @JsonInclude(NON_NULL), so /list omits
# the "name" key entirely rather than emitting null — the behavior reported in issue #161 and now
# documented as optional-for-NETWORK in the FileItemSummary swagger schema.
#
# Two paired assertions keep each other honest: the invalid network's entry must have NO name (and
# must carry the expected validation errorMessage, proving the fixture really failed to load), while
# a known-valid network in the SAME response must still carry its exact name (proving the endpoint
# did not simply stop emitting the field).
step "Invalid network omits name in folder listing (#161)"

# The listing has no nested objects — attributes is left null in the default format=update view — so
# entries can be split on '},{' and selected by UUID without jq/python.
name161_entry_for_uuid() {  # $1 = /list body, $2 = uuid
  echo "$1" | sed 's/},[[:space:]]*{/}\
{/g' | grep "$2"
}

NAME161_FIXTURE="${FIXTURES_DIR}/invalid-undeclared-network-attribute.cx2"
# Matched as a substring so a wrapped/prefixed message still passes, while still pinning the failure
# to the specific attributeDeclarations check the fixture is built to trip.
NAME161_EXPECTED_ERROR="not declared in attributeDeclarations"
NAME161_VALID_NAME="BindingDB - All compounds vs yeast targets"

CALL_NUM=$((CALL_NUM+1))
echo "  API call ${CALL_NUM}: POST /v3/networks (invalid CX2: undeclared networkAttributes name)"
NAME161_UP_RESP=$(curl -s -w "\n%{http_code}" -X POST \
  -u "${TEST_USER}:${TEST_PASS}" \
  -H "Content-Type: application/json" \
  --data-binary "@${NAME161_FIXTURE}" \
  "${BASE_URL}/v3/networks?visibility=PUBLIC")
NAME161_UP_HTTP=$(echo "${NAME161_UP_RESP}" | tail -1)
NAME161_UP_BODY=$(echo "${NAME161_UP_RESP}" | head -1)
[[ "${NAME161_UP_HTTP}" == "201" ]] \
  || api_fail "POST /v3/networks (invalid fixture) → HTTP ${NAME161_UP_HTTP}. Body: ${NAME161_UP_BODY:0:300}"
NAME161_UUID=$(echo "${NAME161_UP_BODY}" | grep -o '"uuid":"[^"]*"' | head -1 | cut -d'"' -f4)
[[ -n "${NAME161_UUID}" ]] \
  || api_fail "Could not parse uuid from invalid-network upload. Body: ${NAME161_UP_BODY:0:300}"
api_pass "POST /v3/networks (invalid CX2) → 201 Created (UUID: ${NAME161_UUID})"

# The load failure path still sets iscomplete=true, so the summary converges on completed:true.
NAME161_ELAPSED=0
while true; do
  NAME161_SUMM=$(curl -s -u "${TEST_USER}:${TEST_PASS}" "${BASE_URL}/v3/networks/${NAME161_UUID}/summary")
  echo "${NAME161_SUMM}" | grep -q '"completed":true' && break
  [[ ${NAME161_ELAPSED} -ge ${LOAD_TIMEOUT} ]] \
    && api_fail "invalid network ${NAME161_UUID} did not complete within ${LOAD_TIMEOUT}s. Last: ${NAME161_SUMM:0:300}"
  echo -e "  ${CYAN}Waiting for invalid network ${NAME161_UUID} to finish loading... (${NAME161_ELAPSED}s)${NC}"
  sleep 5; NAME161_ELAPSED=$((NAME161_ELAPSED + 5))
done

# completed:true is not the end of the story: the load-failure path sets it before the load task
# itself is marked done. Settle the queue here so the row has stopped moving before the step
# continues.
wait_for_task_queue_drain "the invalid network completed"
echo "  invalid network ${NAME161_UUID} — completed (load failed as intended), queue drained"

CALL_NUM=$((CALL_NUM+1))
echo "  API call ${CALL_NUM}: POST /v3/files/folders/ (create #161 listing folder)"
NAME161_FOLDER_RESP=$(curl -s -w "\n%{http_code}" -X POST \
  -u "${TEST_USER}:${TEST_PASS}" \
  -H "Content-Type: application/json" \
  -d '{"name":"161 invalid network name listing"}' \
  "${BASE_URL}/v3/files/folders/")
NAME161_FOLDER_HTTP=$(echo "${NAME161_FOLDER_RESP}" | tail -1)
NAME161_FOLDER_BODY=$(echo "${NAME161_FOLDER_RESP}" | head -1)
[[ "${NAME161_FOLDER_HTTP}" == "201" ]] \
  || api_fail "POST /v3/files/folders/ (#161) → HTTP ${NAME161_FOLDER_HTTP}. Body: ${NAME161_FOLDER_BODY:0:300}"
NAME161_FOLDER_ID=$(echo "${NAME161_FOLDER_BODY}" | grep -oiE '"uuid"[[:space:]]*:[[:space:]]*"[0-9a-f-]{36}"' | grep -oiE '[0-9a-f-]{36}' | head -1)
[[ -n "${NAME161_FOLDER_ID}" ]] \
  || api_fail "Could not parse #161 folder UUID. Body: ${NAME161_FOLDER_BODY:0:300}"
api_pass "POST /v3/files/folders/ → 201 Created (folder ${NAME161_FOLDER_ID})"

# Park the invalid network and the known-valid BindingDB network side by side so one /list response
# carries both. The F10 step immediately below re-moves V3_PUB_UUID into its own folder, so the
# borrow window is a single step and no later assertion sees BindingDB displaced.
CALL_NUM=$((CALL_NUM+1))
echo "  API call ${CALL_NUM}: POST /v3/batch/networks/move (invalid + valid network into #161 folder)"
NAME161_MOVE_HTTP=$(curl -s -o /dev/null -w "%{http_code}" -X POST \
  -u "${TEST_USER}:${TEST_PASS}" -H "Content-Type: application/json" \
  -d "{\"targetFolder\":\"${NAME161_FOLDER_ID}\",\"networks\":[\"${NAME161_UUID}\",\"${V3_PUB_UUID}\"]}" \
  "${BASE_URL}/v3/batch/networks/move")
[[ "${NAME161_MOVE_HTTP}" == "200" || "${NAME161_MOVE_HTTP}" == "204" ]] \
  || api_fail "POST /v3/batch/networks/move (#161) → HTTP ${NAME161_MOVE_HTTP}"
api_pass "Moved invalid (${NAME161_UUID}) + valid (${V3_PUB_UUID}) networks into #161 folder"

# The move is what actually endangers the assertion below. POST /v3/batch/networks/move reindexes
# each moved network (BatchService.moveNetworksToFolder -> createFileIndex), queuing an async
# SolrTaskRebuildFileIdx whose failure handler writes to network.error. Read the listing before that
# task lands and the step passes; read it after and it sees whatever the index task left behind. So
# drain again HERE, after the move, not just after the load.
#
# Deliberately NOT a poll for "the message looks right": that would also hide a genuine regression in
# which the index error wins and the validation message is lost. This waits for the settled state and
# then asserts on whatever it actually is — which is what makes the assertion below a regression test
# for the error-precedence rule in SolrTaskRebuildFileIdx rather than a coin flip.
wait_for_task_queue_drain "the #161 batch move"

# The invalid load forces the network PRIVATE, so the listing must be read as the owner — an
# anonymous caller would get an empty list and the "no name" assertion would pass vacuously.
NAME161_LIST=$(curl -s -u "${TEST_USER}:${TEST_PASS}" "${BASE_URL}/v3/files/folders/${NAME161_FOLDER_ID}/list")
# '|| true': a missing entry is asserted on explicitly below, and an unmatched grep inside a
# command substitution would otherwise abort the script under 'set -e'.
NAME161_BAD_ENTRY=$(name161_entry_for_uuid "${NAME161_LIST}" "${NAME161_UUID}" || true)
NAME161_GOOD_ENTRY=$(name161_entry_for_uuid "${NAME161_LIST}" "${V3_PUB_UUID}" || true)

CALL_NUM=$((CALL_NUM+1))
echo "  API call ${CALL_NUM}: GET .../list (owner) — invalid network entry has NO name"
[[ -n "${NAME161_BAD_ENTRY}" ]] \
  || api_fail "#161: no /list entry for invalid network ${NAME161_UUID}. Body: ${NAME161_LIST:0:600}"
echo "${NAME161_BAD_ENTRY}" | grep -q '"type"[[:space:]]*:[[:space:]]*"NETWORK"' \
  || api_fail "#161: invalid network entry is not type=NETWORK. Entry: ${NAME161_BAD_ENTRY:0:400}"
echo "${NAME161_BAD_ENTRY}" | grep -qF "${NAME161_EXPECTED_ERROR}" \
  || api_fail "#161: invalid network entry lacks a CX2 validation errorMessage containing '${NAME161_EXPECTED_ERROR}' — the fixture may no longer be invalid, so a missing name would prove nothing. Entry: ${NAME161_BAD_ENTRY:0:400}"
echo "${NAME161_BAD_ENTRY}" | grep -q '"name"' \
  && api_fail "#161 REGRESSION: invalid network entry emitted a \"name\" key; it must be omitted (FileItemSummary is @JsonInclude(NON_NULL) and name is optional for type=NETWORK). Entry: ${NAME161_BAD_ENTRY:0:400}"
api_pass "GET .../list → invalid network entry: type=NETWORK, validation errorMessage present, NO \"name\" key (#161)"

CALL_NUM=$((CALL_NUM+1))
echo "  API call ${CALL_NUM}: GET .../list (owner) — valid network entry still has its name"
[[ -n "${NAME161_GOOD_ENTRY}" ]] \
  || api_fail "#161: no /list entry for valid network ${V3_PUB_UUID}. Body: ${NAME161_LIST:0:600}"
echo "${NAME161_GOOD_ENTRY}" | grep -qE "\"name\"[[:space:]]*:[[:space:]]*\"${NAME161_VALID_NAME}\"" \
  || api_fail "#161: valid network entry is missing \"name\":\"${NAME161_VALID_NAME}\" — the listing stopped emitting name for ALL networks, so the assertion above proves nothing. Entry: ${NAME161_GOOD_ENTRY:0:400}"
api_pass "GET .../list → valid network entry still carries \"name\":\"${NAME161_VALID_NAME}\" (name omission is invalid-only)"

# ── STEP: Folder list/count per-child visibility (F10) ───────────────────────
# A folder's visibility is independent of its children's. A folder the caller can
# read must NOT leak the metadata of PRIVATE children they cannot see, and /count
# must match /list. A valid folder access key grants the folder's full contents.
step "Folder list/count enforces per-child visibility (F10, anonymous)"

# Create a folder owned by TEST_USER; put one PUBLIC and one PRIVATE network in it.
CALL_NUM=$((CALL_NUM+1))
echo "  API call ${CALL_NUM}: POST /v3/files/folders/ (create test folder)"
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
F10_FOLDER_ID=$(echo "${F10_FOLDER_BODY}" | grep -oiE '"uuid"[[:space:]]*:[[:space:]]*"[0-9a-f-]{36}"' | grep -oiE '[0-9a-f-]{36}' | head -1 || true)
if [[ -z "${F10_FOLDER_ID}" ]]; then
  api_fail "Could not parse folder UUID from create response. Body: ${F10_FOLDER_BODY:0:300}"
fi
api_pass "POST /v3/files/folders/ → 201 Created (folder ${F10_FOLDER_ID})"

CALL_NUM=$((CALL_NUM+1))
echo "  API call ${CALL_NUM}: POST /v3/files/setvisibility (folder → PUBLIC)"
F10_VIS_HTTP=$(curl -s -o /dev/null -w "%{http_code}" -X POST \
  -u "${TEST_USER}:${TEST_PASS}" -H "Content-Type: application/json" \
  -d "{\"visibility\":\"PUBLIC\",\"files\":{\"${F10_FOLDER_ID}\":\"FOLDER\"}}" \
  "${BASE_URL}/v3/batch/files/setvisibility")
[[ "${F10_VIS_HTTP}" == "200" || "${F10_VIS_HTTP}" == "204" ]] \
  || api_fail "POST /v3/files/setvisibility (PUBLIC) → HTTP ${F10_VIS_HTTP}"
api_pass "Folder set PUBLIC"

CALL_NUM=$((CALL_NUM+1))
echo "  API call ${CALL_NUM}: POST /v3/networks/move (public + private into folder)"
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
echo "  API call ${CALL_NUM}: GET .../list + /count (anon) — PRIVATE child absent, network=1"
F10_ANON_LIST=$(curl -s "${BASE_URL}/v3/files/folders/${F10_FOLDER_ID}/list")
F10_ANON_NET=$(curl -s "${BASE_URL}/v3/files/folders/${F10_FOLDER_ID}/count" | grep -oE '"network"[[:space:]]*:[[:space:]]*[0-9]+' | grep -oE '[0-9]+$' || true)
echo "${F10_ANON_LIST}" | grep -q "${V3_PRIV_UUID}" \
  && api_fail "anon /list LEAKED private child ${V3_PRIV_UUID}. Body: ${F10_ANON_LIST:0:400}"
{ echo "${F10_ANON_LIST}" | grep -q "${V3_PUB_UUID}" && [[ "${F10_ANON_NET}" == "1" ]]; } \
  || api_fail "anon view wrong: net=${F10_ANON_NET}, list=${F10_ANON_LIST:0:400}"
api_pass "anon → /list PUBLIC child only (PRIVATE absent); /count network=1"

# An AUTHENTICATED non-owner must also see only the public child (created anonymously here — the
# server is still in default mode; the AUTHENTICATED_USER_ONLY step runs later).
CALL_NUM=$((CALL_NUM+1))
echo "  API call ${CALL_NUM}: POST /v2/user (create non-owner ${TEST_USER2})"
U2_HTTP=$(curl -s -o /dev/null -w "%{http_code}" -X POST "${BASE_URL}/v2/user" \
  -H "Content-Type: application/json" \
  -d "{\"userName\":\"${TEST_USER2}\",\"password\":\"${TEST_PASS2}\",\"emailAddress\":\"${TEST_EMAIL2}\",\"firstName\":\"NDEx\",\"lastName\":\"Test2\"}")
[[ "${U2_HTTP}" == "201" || "${U2_HTTP}" == "409" ]] || api_fail "POST /v2/user (${TEST_USER2}) → HTTP ${U2_HTTP}"
api_pass "non-owner user ${TEST_USER2} ready"

CALL_NUM=$((CALL_NUM+1))
echo "  API call ${CALL_NUM}: GET .../list + /count (authenticated non-owner) — PRIVATE child absent, network=1"
F10_U2_LIST=$(curl -s -u "${TEST_USER2}:${TEST_PASS2}" "${BASE_URL}/v3/files/folders/${F10_FOLDER_ID}/list")
F10_U2_NET=$(curl -s -u "${TEST_USER2}:${TEST_PASS2}" "${BASE_URL}/v3/files/folders/${F10_FOLDER_ID}/count" | grep -oE '"network"[[:space:]]*:[[:space:]]*[0-9]+' | grep -oE '[0-9]+$' || true)
echo "${F10_U2_LIST}" | grep -q "${V3_PRIV_UUID}" \
  && api_fail "authenticated non-owner /list LEAKED private child ${V3_PRIV_UUID}. Body: ${F10_U2_LIST:0:400}"
{ echo "${F10_U2_LIST}" | grep -q "${V3_PUB_UUID}" && [[ "${F10_U2_NET}" == "1" ]]; } \
  || api_fail "authenticated non-owner view wrong: net=${F10_U2_NET}, list=${F10_U2_LIST:0:400}"
api_pass "authenticated non-owner → /list PUBLIC child only (PRIVATE absent); /count network=1"

CALL_NUM=$((CALL_NUM+1))
echo "  API call ${CALL_NUM}: GET .../list + /count (owner) — both children, network=2"
F10_OWNER_LIST=$(curl -s -u "${TEST_USER}:${TEST_PASS}" "${BASE_URL}/v3/files/folders/${F10_FOLDER_ID}/list")
F10_OWNER_NET=$(curl -s -u "${TEST_USER}:${TEST_PASS}" "${BASE_URL}/v3/files/folders/${F10_FOLDER_ID}/count" | grep -oE '"network"[[:space:]]*:[[:space:]]*[0-9]+' | grep -oE '[0-9]+$' || true)
{ echo "${F10_OWNER_LIST}" | grep -q "${V3_PUB_UUID}" && echo "${F10_OWNER_LIST}" | grep -q "${V3_PRIV_UUID}" && [[ "${F10_OWNER_NET}" == "2" ]]; } \
  || api_fail "owner view wrong: net=${F10_OWNER_NET}, list=${F10_OWNER_LIST:0:400}"
api_pass "owner → /list both children; /count network=2"

# A valid access key must return ALL children even when the folder is independently readable
# (PUBLIC) — the key takes precedence over per-child filtering.
CALL_NUM=$((CALL_NUM+1))
echo "  API call ${CALL_NUM}: POST /v3/files/sharing/share (enable folder access key)"
F10_SHARE_BODY=$(curl -s -X POST -u "${TEST_USER}:${TEST_PASS}" -H "Content-Type: application/json" \
  -d "{\"files\":{\"${F10_FOLDER_ID}\":\"FOLDER\"}}" \
  "${BASE_URL}/v3/files/sharing/share")
F10_KEY=$(echo "${F10_SHARE_BODY}" | sed -E 's/.*:[[:space:]]*"([^"]+)".*/\1/')
[[ -n "${F10_KEY}" && "${F10_KEY}" != "${F10_SHARE_BODY}" ]] || api_fail "Could not parse access key. Body: ${F10_SHARE_BODY:0:300}"
api_pass "Folder access key enabled"

CALL_NUM=$((CALL_NUM+1))
echo "  API call ${CALL_NUM}: GET .../list + /count?accesskey on PUBLIC (readable) folder (anon) — ALL children, network=2"
F10_PUBKEY_LIST=$(curl -s "${BASE_URL}/v3/files/folders/${F10_FOLDER_ID}/list?accesskey=${F10_KEY}")
F10_PUBKEY_NET=$(curl -s "${BASE_URL}/v3/files/folders/${F10_FOLDER_ID}/count?accesskey=${F10_KEY}" | grep -oE '"network"[[:space:]]*:[[:space:]]*[0-9]+' | grep -oE '[0-9]+$' || true)
{ echo "${F10_PUBKEY_LIST}" | grep -q "${V3_PUB_UUID}" && echo "${F10_PUBKEY_LIST}" | grep -q "${V3_PRIV_UUID}" && [[ "${F10_PUBKEY_NET}" == "2" ]]; } \
  || api_fail "access-key precedence on readable folder wrong: net=${F10_PUBKEY_NET}, list=${F10_PUBKEY_LIST:0:400}"
api_pass "anon + access key on PUBLIC folder → /list all children (incl. PRIVATE); /count network=2 (key precedence)"

# ---- Phase B: PRIVATE folder + access key — key grants ALL contents ----
CALL_NUM=$((CALL_NUM+1))
echo "  API call ${CALL_NUM}: POST /v3/files/setvisibility (folder → PRIVATE)"
F10_VIS2_HTTP=$(curl -s -o /dev/null -w "%{http_code}" -X POST \
  -u "${TEST_USER}:${TEST_PASS}" -H "Content-Type: application/json" \
  -d "{\"visibility\":\"PRIVATE\",\"files\":{\"${F10_FOLDER_ID}\":\"FOLDER\"}}" \
  "${BASE_URL}/v3/batch/files/setvisibility")
[[ "${F10_VIS2_HTTP}" == "200" || "${F10_VIS2_HTTP}" == "204" ]] \
  || api_fail "POST /v3/files/setvisibility (PRIVATE) → HTTP ${F10_VIS2_HTTP}"
api_pass "Folder set PRIVATE"

CALL_NUM=$((CALL_NUM+1))
echo "  API call ${CALL_NUM}: GET .../list + /count (anon, no key) — PRIVATE folder must be 401"
F10_NOKEY_LIST_HTTP=$(curl -s -o /dev/null -w "%{http_code}" "${BASE_URL}/v3/files/folders/${F10_FOLDER_ID}/list")
F10_NOKEY_COUNT_HTTP=$(curl -s -o /dev/null -w "%{http_code}" "${BASE_URL}/v3/files/folders/${F10_FOLDER_ID}/count")
{ [[ "${F10_NOKEY_LIST_HTTP}" == "401" ]] && [[ "${F10_NOKEY_COUNT_HTTP}" == "401" ]]; } \
  || api_fail "anon on PRIVATE folder (no key) → list=${F10_NOKEY_LIST_HTTP}, count=${F10_NOKEY_COUNT_HTTP} (expected 401/401)"
api_pass "anon /list + /count on PRIVATE folder (no key) → 401"

# The access key was enabled in Phase A (while PUBLIC); it still grants full contents now that the
# folder is PRIVATE.
CALL_NUM=$((CALL_NUM+1))
echo "  API call ${CALL_NUM}: GET .../list + /count?accesskey (anon) — ALL children, network=2"
F10_KEY_LIST=$(curl -s "${BASE_URL}/v3/files/folders/${F10_FOLDER_ID}/list?accesskey=${F10_KEY}")
F10_KEY_NET=$(curl -s "${BASE_URL}/v3/files/folders/${F10_FOLDER_ID}/count?accesskey=${F10_KEY}" | grep -oE '"network"[[:space:]]*:[[:space:]]*[0-9]+' | grep -oE '[0-9]+$' || true)
{ echo "${F10_KEY_LIST}" | grep -q "${V3_PUB_UUID}" && echo "${F10_KEY_LIST}" | grep -q "${V3_PRIV_UUID}" && [[ "${F10_KEY_NET}" == "2" ]]; } \
  || api_fail "access-key view wrong: net=${F10_KEY_NET}, list=${F10_KEY_LIST:0:400}"
api_pass "anon + access key → /list all children (incl. PRIVATE); /count network=2"

# ── STEP: Pagination on the folder listing (#168) ────────────────────────────
# /list gained start/size. The contract easiest to break silently is the one asserted first: an
# unparameterised call must still return everything, because every existing client calls it bare —
# the ndex3 web app, the MCP browse tool, and most of this script. The rest pins the window itself
# and the two edge answers that must not become errors.
#
# The F10 folder is the fixture at this point: exactly two children, both networks, caller is owner.
# Entries carry no nested objects in the default format=update view (see the #161 note above), so a
# count of '},{' separators is a reliable entry count without jq.
step "Folder listing pagination: start/size (#168)"

CALL_NUM=$((CALL_NUM+1))
echo "  API call ${CALL_NUM}: GET .../list (owner, no paging params) — must stay unbounded"
P168_ALL=$(curl -s -u "${TEST_USER}:${TEST_PASS}" "${BASE_URL}/v3/files/folders/${F10_FOLDER_ID}/list")
{ echo "${P168_ALL}" | grep -q "${V3_PUB_UUID}" && echo "${P168_ALL}" | grep -q "${V3_PRIV_UUID}"; } \
  || api_fail "#168: bare /list must stay unbounded. Body: ${P168_ALL:0:400}"
api_pass "GET .../list with no paging params → still every child (backward compatible)"

CALL_NUM=$((CALL_NUM+1))
echo "  API call ${CALL_NUM}: GET .../list?size=-1 — explicit 'all items'"
P168_NEG1=$(curl -s -u "${TEST_USER}:${TEST_PASS}" "${BASE_URL}/v3/files/folders/${F10_FOLDER_ID}/list?size=-1")
{ echo "${P168_NEG1}" | grep -q "${V3_PUB_UUID}" && echo "${P168_NEG1}" | grep -q "${V3_PRIV_UUID}"; } \
  || api_fail "#168: size=-1 must return every item. Body: ${P168_NEG1:0:400}"
api_pass "GET .../list?size=-1 → every item (matches the bare call)"

# Two single-item pages must PARTITION the folder: one child each, and not the same child twice.
# Without a stable tiebreaker in the ORDER BY this is exactly what breaks, and it breaks silently.
CALL_NUM=$((CALL_NUM+1))
echo "  API call ${CALL_NUM}: GET .../list?start=0&size=1 and ?start=1&size=1 — disjoint pages"
P168_P1=$(curl -s -u "${TEST_USER}:${TEST_PASS}" "${BASE_URL}/v3/files/folders/${F10_FOLDER_ID}/list?start=0&size=1")
P168_P2=$(curl -s -u "${TEST_USER}:${TEST_PASS}" "${BASE_URL}/v3/files/folders/${F10_FOLDER_ID}/list?start=1&size=1")
P168_P1_PUB=$(echo "${P168_P1}" | grep -c "${V3_PUB_UUID}" || true)
P168_P1_PRIV=$(echo "${P168_P1}" | grep -c "${V3_PRIV_UUID}" || true)
P168_P2_PUB=$(echo "${P168_P2}" | grep -c "${V3_PUB_UUID}" || true)
P168_P2_PRIV=$(echo "${P168_P2}" | grep -c "${V3_PRIV_UUID}" || true)
[[ $((P168_P1_PUB + P168_P1_PRIV)) == "1" && $((P168_P2_PUB + P168_P2_PRIV)) == "1" ]] \
  || api_fail "#168: each size=1 page must hold exactly one child. p1=${P168_P1:0:200} p2=${P168_P2:0:200}"
[[ $((P168_P1_PUB + P168_P2_PUB)) == "1" && $((P168_P1_PRIV + P168_P2_PRIV)) == "1" ]] \
  || api_fail "#168: consecutive pages repeated or skipped a child. p1=${P168_P1:0:200} p2=${P168_P2:0:200}"
api_pass "GET .../list?start&size → consecutive pages partition the folder (no repeat, no gap)"

CALL_NUM=$((CALL_NUM+1))
echo "  API call ${CALL_NUM}: GET .../list?start=999 — past the end is an empty array, not an error"
P168_PAST_RESP=$(curl -s -w "\n%{http_code}" -u "${TEST_USER}:${TEST_PASS}" \
  "${BASE_URL}/v3/files/folders/${F10_FOLDER_ID}/list?start=999")
P168_PAST_HTTP=$(echo "${P168_PAST_RESP}" | tail -1); P168_PAST_BODY=$(echo "${P168_PAST_RESP}" | head -1)
{ [[ "${P168_PAST_HTTP}" == "200" ]] && echo "${P168_PAST_BODY}" | grep -qE '^\[[[:space:]]*\]$'; } \
  || api_fail "#168: start past the end → HTTP ${P168_PAST_HTTP}, body ${P168_PAST_BODY:0:200} (expected 200 and [])"
api_pass "GET .../list?start=999 → 200 with an empty array (not 404, not an error)"

CALL_NUM=$((CALL_NUM+1))
echo "  API call ${CALL_NUM}: GET .../list?start=-1 — out-of-range offset rejected"
P168_BAD_HTTP=$(curl -s -o /dev/null -w "%{http_code}" -u "${TEST_USER}:${TEST_PASS}" \
  "${BASE_URL}/v3/files/folders/${F10_FOLDER_ID}/list?start=-1")
[[ "${P168_BAD_HTTP}" == "400" ]] || api_fail "#168: start=-1 → HTTP ${P168_BAD_HTTP} (expected 400)"
api_pass "GET .../list?start=-1 → 400"

# The endpoint has three service branches — home, access-key, and readable — and the window has to be
# threaded through all of them. The assertions above only exercised the readable one.
CALL_NUM=$((CALL_NUM+1))
echo "  API call ${CALL_NUM}: GET .../folders/home/list?size=1 — home branch honors the window"
P168_HOME_RESP=$(curl -s -w "\n%{http_code}" -u "${TEST_USER}:${TEST_PASS}" \
  "${BASE_URL}/v3/files/folders/home/list?size=1")
P168_HOME_HTTP=$(echo "${P168_HOME_RESP}" | tail -1); P168_HOME_BODY=$(echo "${P168_HOME_RESP}" | head -1)
# '|| true': under `set -o pipefail` a grep that matches nothing exits 1 and would abort the run.
P168_HOME_SEPS=$(echo "${P168_HOME_BODY}" | grep -o '},[[:space:]]*{' | wc -l | tr -d ' ' || true)
{ [[ "${P168_HOME_HTTP}" == "200" ]] && [[ "${P168_HOME_SEPS}" == "0" ]]; } \
  || api_fail "#168: home/list?size=1 → HTTP ${P168_HOME_HTTP}, ${P168_HOME_SEPS} separators. Body: ${P168_HOME_BODY:0:300}"
api_pass "GET /v3/files/folders/home/list?size=1 → at most one entry (home branch honors size)"

CALL_NUM=$((CALL_NUM+1))
echo "  API call ${CALL_NUM}: GET .../list?accesskey&size=1 (anon) — access-key branch honors the window"
P168_KEY_PAGE=$(curl -s "${BASE_URL}/v3/files/folders/${F10_FOLDER_ID}/list?accesskey=${F10_KEY}&size=1")
P168_KEY_SEPS=$(echo "${P168_KEY_PAGE}" | grep -o '},[[:space:]]*{' | wc -l | tr -d ' ' || true)
[[ "${P168_KEY_SEPS}" == "0" ]] \
  || api_fail "#168: key-filtered /list?size=1 returned ${P168_KEY_SEPS} separators. Body: ${P168_KEY_PAGE:0:300}"
{ echo "${P168_KEY_PAGE}" | grep -q "${V3_PUB_UUID}" || echo "${P168_KEY_PAGE}" | grep -q "${V3_PRIV_UUID}"; } \
  || api_fail "#168: key-filtered /list?size=1 returned no child. Body: ${P168_KEY_PAGE:0:300}"
api_pass "GET .../list?accesskey&size=1 → exactly one child (access-key branch honors size)"

# ── STEP: Access key follows the folder hierarchy + same-owner shortcut resolution (G11, #133/#137) ──
# A network is reachable by an ANCESTOR folder's access key (accrual up the folder chain), AND — for
# backwards compatibility with the v3 networkset migration — by a SAME-OWNER NETWORK shortcut that lives
# in a keyed folder even though the target network sits elsewhere (e.g. Home). Such shortcuts are also
# surfaced in the key-authorized /list and /count views.
step "Access key: ancestor-folder accrual + same-owner shortcut resolution (G11, #133/#137)"

# Nest the PRIVATE network one level deeper: a subfolder under the (keyed) F10 folder. The network's
# key access must now be resolved via the GRANDPARENT folder's key.
CALL_NUM=$((CALL_NUM+1))
echo "  API call ${CALL_NUM}: POST /v3/files/folders/ (subfolder under keyed folder)"
G11_SUB_RESP=$(curl -s -w "\n%{http_code}" -X POST -u "${TEST_USER}:${TEST_PASS}" \
  -H "Content-Type: application/json" \
  -d "{\"name\":\"G11 subfolder\",\"parent\":\"${F10_FOLDER_ID}\"}" \
  "${BASE_URL}/v3/files/folders/")
G11_SUB_HTTP=$(echo "${G11_SUB_RESP}" | tail -1); G11_SUB_BODY=$(echo "${G11_SUB_RESP}" | head -1)
[[ "${G11_SUB_HTTP}" == "201" ]] || api_fail "create subfolder → HTTP ${G11_SUB_HTTP}. Body: ${G11_SUB_BODY:0:300}"
G11_SUB_ID=$(echo "${G11_SUB_BODY}" | grep -oiE '"uuid"[[:space:]]*:[[:space:]]*"[0-9a-f-]{36}"' | grep -oiE '[0-9a-f-]{36}' | head -1 || true)
[[ -n "${G11_SUB_ID}" ]] || api_fail "no uuid in subfolder create. Body: ${G11_SUB_BODY:0:300}"
api_pass "subfolder ${G11_SUB_ID} created under keyed folder ${F10_FOLDER_ID}"

CALL_NUM=$((CALL_NUM+1))
echo "  API call ${CALL_NUM}: POST /v3/batch/networks/move (private net into subfolder)"
G11_MOVE_HTTP=$(curl -s -o /dev/null -w "%{http_code}" -X POST -u "${TEST_USER}:${TEST_PASS}" \
  -H "Content-Type: application/json" \
  -d "{\"targetFolder\":\"${G11_SUB_ID}\",\"networks\":[\"${V3_PRIV_UUID}\"]}" \
  "${BASE_URL}/v3/batch/networks/move")
[[ "${G11_MOVE_HTTP}" == "200" || "${G11_MOVE_HTTP}" == "204" ]] \
  || api_fail "move private net into subfolder → HTTP ${G11_MOVE_HTTP}"
api_pass "PRIVATE network ${V3_PRIV_UUID} moved into subfolder (grandparent holds the key)"

CALL_NUM=$((CALL_NUM+1))
echo "  API call ${CALL_NUM}: GET /v3/networks/{priv}/summary?accesskey=<ancestor key> (anon, expect 200)"
G11_SUMM_RESP=$(curl -s -w "\n%{http_code}" "${BASE_URL}/v3/networks/${V3_PRIV_UUID}/summary?accesskey=${F10_KEY}")
G11_SUMM_HTTP=$(echo "${G11_SUMM_RESP}" | tail -1); G11_SUMM_BODY=$(echo "${G11_SUMM_RESP}" | head -1)
{ [[ "${G11_SUMM_HTTP}" == "200" ]] && echo "${G11_SUMM_BODY}" | grep -q "${V3_PRIV_UUID}"; } \
  || api_fail "ancestor-key network access wrong: HTTP ${G11_SUMM_HTTP}, body ${G11_SUMM_BODY:0:300}"
api_pass "anon + ANCESTOR folder key → GET private network summary 200 (accrual up the folder chain)"

CALL_NUM=$((CALL_NUM+1))
echo "  API call ${CALL_NUM}: GET /v3/networks/{priv}/summary (anon, no key, expect 401)"
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
echo "  API call ${CALL_NUM}: POST /v3/files/shortcuts/ (shortcut in keyed folder → private Home network)"
G11_SC_RESP=$(curl -s -w "\n%{http_code}" -X POST -u "${TEST_USER}:${TEST_PASS}" \
  -H "Content-Type: application/json" \
  -d "{\"name\":\"G11 shortcut\",\"parent\":\"${F10_FOLDER_ID}\",\"target\":\"${V2_PRIV_UUID}\",\"targetType\":\"NETWORK\"}" \
  "${BASE_URL}/v3/files/shortcuts/")
G11_SC_HTTP=$(echo "${G11_SC_RESP}" | tail -1); G11_SC_BODY=$(echo "${G11_SC_RESP}" | head -1)
[[ "${G11_SC_HTTP}" == "201" ]] || api_fail "create shortcut → HTTP ${G11_SC_HTTP}. Body: ${G11_SC_BODY:0:300}"
G11_SC_ID=$(echo "${G11_SC_BODY}" | grep -oiE '"uuid"[[:space:]]*:[[:space:]]*"[0-9a-f-]{36}"' | grep -oiE '[0-9a-f-]{36}' | head -1 || true)
[[ -n "${G11_SC_ID}" ]] || api_fail "no uuid in create-shortcut response. Body: ${G11_SC_BODY:0:300}"
api_pass "shortcut ${G11_SC_ID} created in keyed folder → private network ${V2_PRIV_UUID}"

# Change 3: key-authorized /list + /count now INCLUDE the same-owner NETWORK shortcut.
CALL_NUM=$((CALL_NUM+1))
echo "  API call ${CALL_NUM}: GET .../list + /count?accesskey (anon) — shortcut INCLUDED, shortcut count >=1"
G11_KEY_LIST=$(curl -s "${BASE_URL}/v3/files/folders/${F10_FOLDER_ID}/list?accesskey=${F10_KEY}")
G11_KEY_SC=$(curl -s "${BASE_URL}/v3/files/folders/${F10_FOLDER_ID}/count?accesskey=${F10_KEY}" | grep -oE '"shortcut"[[:space:]]*:[[:space:]]*[0-9]+' | grep -oE '[0-9]+$' || true)
echo "${G11_KEY_LIST}" | grep -q "${G11_SC_ID}" \
  || api_fail "key /list should include same-owner shortcut ${G11_SC_ID}. Body: ${G11_KEY_LIST:0:400}"
[[ "${G11_KEY_SC:-0}" -ge 1 ]] \
  || api_fail "key /count shortcut expected >=1, got ${G11_KEY_SC}"
api_pass "anon + key → /list includes same-owner NETWORK shortcut; /count shortcut>=1 (Change 3, #133/#137)"

# Change 1 (GET): the key grants anonymous read to the shortcut's target network (was 401 before the fix).
CALL_NUM=$((CALL_NUM+1))
echo "  API call ${CALL_NUM}: GET /v3/networks/{shortcut-target}?accesskey (anon, expect 200)"
G11_SCGET_HTTP=$(curl -s -o /dev/null -w "%{http_code}" "${BASE_URL}/v3/networks/${V2_PRIV_UUID}?accesskey=${F10_KEY}")
[[ "${G11_SCGET_HTTP}" == "200" ]] \
  || api_fail "anon + key via shortcut → GET network HTTP ${G11_SCGET_HTTP} (expected 200)"
api_pass "anon + key → GET private network via same-owner shortcut 200 (Change 1, #133/#137)"

# Negative controls: no key and a wrong key must stay 401 (the shortcut alone grants nothing).
CALL_NUM=$((CALL_NUM+1))
echo "  API call ${CALL_NUM}: GET /v3/networks/{shortcut-target} (anon no-key / wrong-key, expect 401)"
G11_SCNO_HTTP=$(curl -s -o /dev/null -w "%{http_code}" "${BASE_URL}/v3/networks/${V2_PRIV_UUID}")
G11_SCWRONG_HTTP=$(curl -s -o /dev/null -w "%{http_code}" "${BASE_URL}/v3/networks/${V2_PRIV_UUID}?accesskey=not-a-real-key")
{ [[ "${G11_SCNO_HTTP}" == "401" ]] && [[ "${G11_SCWRONG_HTTP}" == "401" ]]; } \
  || api_fail "shortcut-target negative controls wrong: no-key=${G11_SCNO_HTTP}, wrong-key=${G11_SCWRONG_HTTP} (expected 401/401)"
api_pass "anon no-key / wrong-key → GET shortcut-target network 401 (negative controls)"

# Change 1 (search): the same accessKeyIsValid path backs the per-network search endpoints (stub proxied).
CALL_NUM=$((CALL_NUM+1))
echo "  API call ${CALL_NUM}: POST /v3/search/networks/{shortcut-target}/query?accesskey (anon, expect 200)"
G11_SCQ_HTTP=$(curl -s -o /dev/null -w "%{http_code}" -X POST \
  -H "Content-Type: application/json" -d '{"searchString":"EGFR","searchDepth":1}' \
  "${BASE_URL}/v3/search/networks/${V2_PRIV_UUID}/query?accesskey=${F10_KEY}")
[[ "${G11_SCQ_HTTP}" == "200" ]] \
  || api_fail "anon + key via shortcut → search query HTTP ${G11_SCQ_HTTP} (expected 200)"
api_pass "anon + key → POST search query on shortcut-target network 200 (Change 1, #133/#137)"

# Change 2 (batch summary): one call spanning a REAL network (V3_PRIV, reached via the ancestor folder
# key) and a SHORTCUT network (V2_PRIV, reached via the same-owner shortcut) — both must be returned.
CALL_NUM=$((CALL_NUM+1))
echo "  API call ${CALL_NUM}: POST /v3/batch/networks/summary?accesskey (anon) — real + shortcut network both present"
G11_BATCH=$(curl -s -X POST -H "Content-Type: application/json" \
  -d "[\"${V3_PRIV_UUID}\",\"${V2_PRIV_UUID}\"]" \
  "${BASE_URL}/v3/batch/networks/summary?accesskey=${F10_KEY}")
{ echo "${G11_BATCH}" | grep -q "${V3_PRIV_UUID}" && echo "${G11_BATCH}" | grep -q "${V2_PRIV_UUID}"; } \
  || api_fail "batch summary + key should include real (${V3_PRIV_UUID}) and shortcut (${V2_PRIV_UUID}) networks. Body: ${G11_BATCH:0:400}"
api_pass "anon + key → batch summary returns both the ancestor-key network and the shortcut-key network (Change 2)"

# Negative: batch summary WITHOUT a key returns neither private network to an anonymous caller.
CALL_NUM=$((CALL_NUM+1))
echo "  API call ${CALL_NUM}: POST /v3/batch/networks/summary (anon, no key) — neither private network present"
G11_BATCH_NOKEY=$(curl -s -X POST -H "Content-Type: application/json" \
  -d "[\"${V3_PRIV_UUID}\",\"${V2_PRIV_UUID}\"]" \
  "${BASE_URL}/v3/batch/networks/summary")
{ echo "${G11_BATCH_NOKEY}" | grep -q "${V3_PRIV_UUID}" || echo "${G11_BATCH_NOKEY}" | grep -q "${V2_PRIV_UUID}"; } \
  && api_fail "batch summary without key LEAKED a private network. Body: ${G11_BATCH_NOKEY:0:400}"
api_pass "anon + no key → batch summary omits both private networks (negative control)"

# The owner (no key) still sees the shortcut in the identity-based view (no-key path unchanged).
CALL_NUM=$((CALL_NUM+1))
echo "  API call ${CALL_NUM}: GET .../list (owner, no key) — shortcut PRESENT (no-key path unchanged)"
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
echo "  API call ${CALL_NUM}: POST /v3/files/folders/ (create with visibility=PUBLIC)"
VIS_F_RESP=$(curl -s -w "\n%{http_code}" -X POST -u "${TEST_USER}:${TEST_PASS}" \
  -H "Content-Type: application/json" \
  -d '{"name":"vis-folder","visibility":"PUBLIC"}' \
  "${BASE_URL}/v3/files/folders/")
VIS_F_HTTP=$(echo "${VIS_F_RESP}" | tail -1); VIS_F_BODY=$(echo "${VIS_F_RESP}" | head -1)
[[ "${VIS_F_HTTP}" == "201" ]] || api_fail "create folder (visibility=PUBLIC) → HTTP ${VIS_F_HTTP}. Body: ${VIS_F_BODY:0:300}"
VIS_F_ID=$(echo "${VIS_F_BODY}" | grep -oiE '"uuid"[[:space:]]*:[[:space:]]*"[0-9a-f-]{36}"' | grep -oiE '[0-9a-f-]{36}' | head -1 || true)
[[ -n "${VIS_F_ID}" ]] || api_fail "no uuid in create-folder response. Body: ${VIS_F_BODY:0:300}"
api_pass "POST folder with visibility=PUBLIC → 201 (folder ${VIS_F_ID})"

# --- Folder: read path populates visibility (GET {id} + list-mine) ---
CALL_NUM=$((CALL_NUM+1))
echo "  API call ${CALL_NUM}: GET /v3/files/folders/{id} (visibility populated)"
VIS_F_GET=$(curl -s -u "${TEST_USER}:${TEST_PASS}" "${BASE_URL}/v3/files/folders/${VIS_F_ID}")
echo "${VIS_F_GET}" | grep -qE '"visibility"[[:space:]]*:[[:space:]]*"PUBLIC"' \
  || api_fail "GET folder did not report visibility=PUBLIC. Body: ${VIS_F_GET:0:300}"
api_pass "GET folder reports visibility=PUBLIC"

# GET /v3/files/folders was removed by #163; the folder listing replaces it. format=compact is the
# view that carries visibility, description and creationTime -- despite the name it is the FULLER of
# the two, so do not "fix" this back to the default format=update.
CALL_NUM=$((CALL_NUM+1))
echo "  API call ${CALL_NUM}: GET /v3/files/folders/home/list?type=folder&format=compact (listing reports visibility)"
VIS_F_LIST=$(curl -s -u "${TEST_USER}:${TEST_PASS}" \
  "${BASE_URL}/v3/files/folders/home/list?type=folder&format=compact")
echo "${VIS_F_LIST}" | grep -q "${VIS_F_ID}" \
  || api_fail "home folder listing omitted the folder just created (${VIS_F_ID}). Body: ${VIS_F_LIST:0:400}"
echo "${VIS_F_LIST}" | grep -q '"visibility"' \
  || api_fail "folder listing did not report a visibility field. Body: ${VIS_F_LIST:0:400}"
api_pass "GET /v3/files/folders/home/list reports visibility"

# creationTime rides in attributes because FileItemSummary has no such field (#163). This assertion is
# the contract the ndex-java-client maps against; if it breaks, that client silently loses the field.
CALL_NUM=$((CALL_NUM+1))
echo "  API call ${CALL_NUM}: GET /v3/files/folders/home/list (compact carries attributes.creationTime)"
echo "${VIS_F_LIST}" | grep -q '"creationTime"' \
  || api_fail "compact folder listing did not carry attributes.creationTime. Body: ${VIS_F_LIST:0:400}"
api_pass "compact folder listing carries creationTime in attributes"


# --- Folder: omitted visibility defaults to PRIVATE ---
CALL_NUM=$((CALL_NUM+1))
echo "  API call ${CALL_NUM}: POST /v3/files/folders/ (no visibility → default PRIVATE)"
VIS_FD_RESP=$(curl -s -w "\n%{http_code}" -X POST -u "${TEST_USER}:${TEST_PASS}" \
  -H "Content-Type: application/json" -d '{"name":"vis-folder-default"}' \
  "${BASE_URL}/v3/files/folders/")
VIS_FD_HTTP=$(echo "${VIS_FD_RESP}" | tail -1); VIS_FD_BODY=$(echo "${VIS_FD_RESP}" | head -1)
[[ "${VIS_FD_HTTP}" == "201" ]] || api_fail "create folder (no visibility) → HTTP ${VIS_FD_HTTP}"
VIS_FD_ID=$(echo "${VIS_FD_BODY}" | grep -oiE '"uuid"[[:space:]]*:[[:space:]]*"[0-9a-f-]{36}"' | grep -oiE '[0-9a-f-]{36}' | head -1 || true)
CALL_NUM=$((CALL_NUM+1))
echo "  API call ${CALL_NUM}: GET /v3/files/folders/{id} (default visibility=PRIVATE)"
VIS_FD_GET=$(curl -s -u "${TEST_USER}:${TEST_PASS}" "${BASE_URL}/v3/files/folders/${VIS_FD_ID}")
echo "${VIS_FD_GET}" | grep -qE '"visibility"[[:space:]]*:[[:space:]]*"PRIVATE"' \
  || api_fail "folder created without visibility did not default to PRIVATE. Body: ${VIS_FD_GET:0:300}"
api_pass "folder without visibility defaults to PRIVATE"

# --- Folder: update accepts visibility ---
CALL_NUM=$((CALL_NUM+1))
echo "  API call ${CALL_NUM}: PUT /v3/files/folders/{id} (visibility=UNLISTED)"
VIS_F_PUT=$(curl -s -o /dev/null -w "%{http_code}" -X PUT -u "${TEST_USER}:${TEST_PASS}" \
  -H "Content-Type: application/json" -d '{"visibility":"UNLISTED"}' \
  "${BASE_URL}/v3/files/folders/${VIS_F_ID}")
[[ "${VIS_F_PUT}" == "204" || "${VIS_F_PUT}" == "200" ]] || api_fail "PUT folder visibility → HTTP ${VIS_F_PUT}"
CALL_NUM=$((CALL_NUM+1))
echo "  API call ${CALL_NUM}: GET /v3/files/folders/{id} (visibility now UNLISTED)"
VIS_F_GET2=$(curl -s -u "${TEST_USER}:${TEST_PASS}" "${BASE_URL}/v3/files/folders/${VIS_F_ID}")
echo "${VIS_F_GET2}" | grep -qE '"visibility"[[:space:]]*:[[:space:]]*"UNLISTED"' \
  || api_fail "folder update did not change visibility to UNLISTED. Body: ${VIS_F_GET2:0:300}"
api_pass "PUT folder visibility=UNLISTED applied; GET reports UNLISTED"

# --- Shortcut: write path accepts visibility on create (target the vis-folder) ---
CALL_NUM=$((CALL_NUM+1))
echo "  API call ${CALL_NUM}: POST /v3/files/shortcuts/ (visibility=PUBLIC)"
VIS_S_RESP=$(curl -s -w "\n%{http_code}" -X POST -u "${TEST_USER}:${TEST_PASS}" \
  -H "Content-Type: application/json" \
  -d "{\"name\":\"vis-shortcut\",\"target\":\"${VIS_F_ID}\",\"targetType\":\"FOLDER\",\"visibility\":\"PUBLIC\"}" \
  "${BASE_URL}/v3/files/shortcuts/")
VIS_S_HTTP=$(echo "${VIS_S_RESP}" | tail -1); VIS_S_BODY=$(echo "${VIS_S_RESP}" | head -1)
[[ "${VIS_S_HTTP}" == "201" ]] || api_fail "create shortcut (visibility=PUBLIC) → HTTP ${VIS_S_HTTP}. Body: ${VIS_S_BODY:0:300}"
VIS_S_ID=$(echo "${VIS_S_BODY}" | grep -oiE '"uuid"[[:space:]]*:[[:space:]]*"[0-9a-f-]{36}"' | grep -oiE '[0-9a-f-]{36}' | head -1 || true)
[[ -n "${VIS_S_ID}" ]] || api_fail "no uuid in create-shortcut response. Body: ${VIS_S_BODY:0:300}"
CALL_NUM=$((CALL_NUM+1))
echo "  API call ${CALL_NUM}: GET /v3/files/shortcuts/{id} (visibility populated)"
VIS_S_GET=$(curl -s -u "${TEST_USER}:${TEST_PASS}" "${BASE_URL}/v3/files/shortcuts/${VIS_S_ID}")
echo "${VIS_S_GET}" | grep -qE '"visibility"[[:space:]]*:[[:space:]]*"PUBLIC"' \
  || api_fail "GET shortcut did not report visibility=PUBLIC. Body: ${VIS_S_GET:0:300}"
api_pass "POST shortcut visibility=PUBLIC → 201; GET reports PUBLIC (shortcut ${VIS_S_ID})"

# --- Shortcut: update accepts visibility ---
CALL_NUM=$((CALL_NUM+1))
echo "  API call ${CALL_NUM}: PUT /v3/files/shortcuts/{id} (visibility=PRIVATE)"
VIS_S_PUT=$(curl -s -o /dev/null -w "%{http_code}" -X PUT -u "${TEST_USER}:${TEST_PASS}" \
  -H "Content-Type: application/json" -d '{"visibility":"PRIVATE"}' \
  "${BASE_URL}/v3/files/shortcuts/${VIS_S_ID}")
[[ "${VIS_S_PUT}" == "204" || "${VIS_S_PUT}" == "200" ]] || api_fail "PUT shortcut visibility → HTTP ${VIS_S_PUT}"
CALL_NUM=$((CALL_NUM+1))
echo "  API call ${CALL_NUM}: GET /v3/files/shortcuts/{id} (visibility now PRIVATE)"
VIS_S_GET2=$(curl -s -u "${TEST_USER}:${TEST_PASS}" "${BASE_URL}/v3/files/shortcuts/${VIS_S_ID}")
echo "${VIS_S_GET2}" | grep -qE '"visibility"[[:space:]]*:[[:space:]]*"PRIVATE"' \
  || api_fail "shortcut update did not change visibility to PRIVATE. Body: ${VIS_S_GET2:0:300}"
api_pass "PUT shortcut visibility=PRIVATE applied; GET reports PRIVATE"

# ── /list type filter: shortcut is a first-class type (#163) ─────────────────────────────────────────
# Fixtures: VIS_F_ID is a folder at home root; VIS_S_ID is a shortcut at home root POINTING AT it.
# A shortcut counts as a way of seeing whatever it points at, so:
#   type=folder   -> the folder AND the shortcut pointing at it
#   type=shortcut -> the shortcut only (this used to return an empty array)
#   type=network  -> neither, since nothing here is or points at a network
step "Folder listing type filter: folder / shortcut / network (#163)"

CALL_NUM=$((CALL_NUM+1))
echo "  API call ${CALL_NUM}: GET /v3/files/folders/home/list?type=shortcut — must return the shortcut"
T_SC=$(curl -s -u "${TEST_USER}:${TEST_PASS}" "${BASE_URL}/v3/files/folders/home/list?type=shortcut")
echo "${T_SC}" | grep -q "${VIS_S_ID}" \
  || api_fail "type=shortcut omitted shortcut ${VIS_S_ID}; the filter is still matching on target_type. Body: ${T_SC:0:400}"
echo "${T_SC}" | grep -q "${VIS_F_ID}" \
  && api_fail "type=shortcut must not return the folder ${VIS_F_ID}. Body: ${T_SC:0:400}"
api_pass "type=shortcut returns shortcuts and nothing else"

CALL_NUM=$((CALL_NUM+1))
echo "  API call ${CALL_NUM}: GET /v3/files/folders/home/list?type=folder — folder AND the shortcut to it"
T_FD=$(curl -s -u "${TEST_USER}:${TEST_PASS}" "${BASE_URL}/v3/files/folders/home/list?type=folder")
echo "${T_FD}" | grep -q "${VIS_F_ID}" \
  || api_fail "type=folder omitted folder ${VIS_F_ID}. Body: ${T_FD:0:400}"
echo "${T_FD}" | grep -q "${VIS_S_ID}" \
  || api_fail "type=folder must also return the folder-targeted shortcut ${VIS_S_ID}. Body: ${T_FD:0:400}"
api_pass "type=folder returns folders plus shortcuts pointing at folders"

CALL_NUM=$((CALL_NUM+1))
echo "  API call ${CALL_NUM}: GET /v3/files/folders/home/list?type=network — neither fixture"
T_NW=$(curl -s -u "${TEST_USER}:${TEST_PASS}" "${BASE_URL}/v3/files/folders/home/list?type=network")
echo "${T_NW}" | grep -q "${VIS_F_ID}" \
  && api_fail "type=network must not return the folder ${VIS_F_ID}. Body: ${T_NW:0:400}"
echo "${T_NW}" | grep -q "${VIS_S_ID}" \
  && api_fail "type=network must not return a folder-targeted shortcut ${VIS_S_ID}. Body: ${T_NW:0:400}"
api_pass "type=network excludes folders and folder-targeted shortcuts"

# ── Removed endpoints leave no trace in the generated OpenAPI spec (#163) ────────────────────────────
# The spec is generated at runtime from the @Operation annotations, so the deleted GET handlers can
# only vanish from it if their annotations are truly gone. The summaries below were unique to them.
# The sibling POST creates share the same paths and must survive, which is what proves this assertion
# is testing the operations rather than the paths.
step "Removed list-mine endpoints are absent from OpenAPI (#163)"

CALL_NUM=$((CALL_NUM+1))
echo "  API call ${CALL_NUM}: GET /openapi.json — removed GET operations must be gone"
OPENAPI_BODY=$(curl -s "${BASE_URL}/openapi.json")
[[ -n "${OPENAPI_BODY}" ]] || api_fail "GET /openapi.json returned an empty body"

# Substring tests rather than `echo | grep -q`: the spec body is large, and grep -q exits on its first
# match, which SIGPIPEs the echo still writing into it. Under `set -o pipefail` that turns a successful
# match into a failed pipeline. Keep these as pure-bash tests.
[[ "${OPENAPI_BODY}" == *"List My Folders"* ]] \
  && api_fail "GET /v3/files/folders is still documented in OpenAPI (summary 'List My Folders')"
[[ "${OPENAPI_BODY}" == *"List my Shortcuts"* ]] \
  && api_fail "GET /v3/files/shortcuts is still documented in OpenAPI (summary 'List my Shortcuts')"
api_pass "neither removed list-mine operation appears in the OpenAPI spec"

CALL_NUM=$((CALL_NUM+1))
echo "  API call ${CALL_NUM}: GET /openapi.json — sibling POST creates on the same paths survive"
[[ "${OPENAPI_BODY}" == *"Create a Folder"* ]] \
  || api_fail "POST /v3/files/folders vanished from OpenAPI; the removal took the whole path with it"
[[ "${OPENAPI_BODY}" == *"Create a Shortcut"* ]] \
  || api_fail "POST /v3/files/shortcuts vanished from OpenAPI; the removal took the whole path with it"
[[ "${OPENAPI_BODY}" == *"List items in a folder"* ]] \
  || api_fail "the replacement listing operation is missing from OpenAPI"
api_pass "POST creates and the replacement listing operation are still documented"

# ── STEP: Visibility change rewrites the index document in place ──────────────
# Proves the reviewer's concern on PR #129: a PRIVATE→PUBLIC update moves the entry between
# two cores with no orphan left behind. Search is
# async (soft commit ≤5s), so each assertion polls until convergence.
step "Visibility change rewrites the folder/shortcut index document in place"

VM_FOLDER_NAME="vismovefolder${RANDOM}${RANDOM}"
VM_SHORTCUT_NAME="vismoveshortcut${RANDOM}${RANDOM}"

# --- Folder: create PRIVATE, confirm indexed as PRIVATE ---
CALL_NUM=$((CALL_NUM+1))
echo "  API call ${CALL_NUM}: POST /v3/files/folders/ (create PRIVATE for reindex-move test)"
VM_F_RESP=$(curl -s -w "\n%{http_code}" -X POST -u "${TEST_USER}:${TEST_PASS}" \
  -H "Content-Type: application/json" -d "{\"name\":\"${VM_FOLDER_NAME}\"}" \
  "${BASE_URL}/v3/files/folders/")
VM_F_HTTP=$(echo "${VM_F_RESP}" | tail -1); VM_F_BODY=$(echo "${VM_F_RESP}" | head -1)
[[ "${VM_F_HTTP}" == "201" ]] || api_fail "create move-test folder → HTTP ${VM_F_HTTP}. Body: ${VM_F_BODY:0:300}"
VM_F_ID=$(echo "${VM_F_BODY}" | grep -oiE '"uuid"[[:space:]]*:[[:space:]]*"[0-9a-f-]{36}"' | grep -oiE '[0-9a-f-]{36}' | head -1 || true)
[[ -n "${VM_F_ID}" ]] || api_fail "no uuid in move-test folder create body. Body: ${VM_F_BODY:0:300}"

CALL_NUM=$((CALL_NUM+1))
echo "  API call ${CALL_NUM}: search files visibility=PRIVATE (folder indexed as PRIVATE)"
poll_files_until_present "PRIVATE" "${VM_FOLDER_NAME}" "${VM_F_ID}" "folder pre-move"
api_pass "folder ${VM_F_ID} indexed under PRIVATE"

# --- Folder: flip to PUBLIC, confirm it now reads PUBLIC and no longer matches PRIVATE ---
CALL_NUM=$((CALL_NUM+1))
echo "  API call ${CALL_NUM}: PUT /v3/files/folders/{id} visibility=PUBLIC"
VM_F_PUT=$(curl -s -o /dev/null -w "%{http_code}" -X PUT -u "${TEST_USER}:${TEST_PASS}" \
  -H "Content-Type: application/json" -d '{"visibility":"PUBLIC"}' \
  "${BASE_URL}/v3/files/folders/${VM_F_ID}")
[[ "${VM_F_PUT}" == "204" || "${VM_F_PUT}" == "200" ]] || api_fail "PUT move-test folder visibility → HTTP ${VM_F_PUT}"

CALL_NUM=$((CALL_NUM+1))
echo "  API call ${CALL_NUM}: search files visibility=PUBLIC (folder now reads as PUBLIC)"
poll_files_until_present "PUBLIC" "${VM_FOLDER_NAME}" "${VM_F_ID}" "folder post-move (new visibility)"
CALL_NUM=$((CALL_NUM+1))
echo "  API call ${CALL_NUM}: search files visibility=PRIVATE (folder no longer matches PRIVATE)"
poll_files_until_absent "PRIVATE" "${VM_FOLDER_NAME}" "${VM_F_ID}" "folder post-move (old visibility)"

# The upsert assertion: re-indexing replaces the document rather than adding a second copy. Without
# this, a rebuild that stopped replacing would still satisfy both polls above.
CALL_NUM=$((CALL_NUM+1))
echo "  API call ${CALL_NUM}: ndex-nfs holds exactly one document for the folder"
VM_F_COUNT=$(docker exec "${CONTAINER_NAME}" bash -c \
  "curl -s 'http://localhost:8983/solr/ndex-nfs/select?q=uuid:${VM_F_ID}&rows=0&wt=json'" 2>/dev/null \
  | grep -oE '"numFound":[0-9]+' | grep -oE '[0-9]+$' || true)
[[ "${VM_F_COUNT}" == "1" ]] \
  || api_fail "visibility change duplicated the folder document: numFound=${VM_F_COUNT:-unset} for ${VM_F_ID}"
api_pass "folder visibility PRIVATE→PUBLIC rewritten in place: matches PUBLIC, no longer matches PRIVATE, exactly one document"

# --- Shortcut: create PRIVATE (target the move-test folder), confirm indexed as PRIVATE ---
CALL_NUM=$((CALL_NUM+1))
echo "  API call ${CALL_NUM}: POST /v3/files/shortcuts/ (create PRIVATE for reindex-move test)"
VM_S_RESP=$(curl -s -w "\n%{http_code}" -X POST -u "${TEST_USER}:${TEST_PASS}" \
  -H "Content-Type: application/json" \
  -d "{\"name\":\"${VM_SHORTCUT_NAME}\",\"target\":\"${VM_F_ID}\",\"targetType\":\"FOLDER\"}" \
  "${BASE_URL}/v3/files/shortcuts/")
VM_S_HTTP=$(echo "${VM_S_RESP}" | tail -1); VM_S_BODY=$(echo "${VM_S_RESP}" | head -1)
[[ "${VM_S_HTTP}" == "201" ]] || api_fail "create move-test shortcut → HTTP ${VM_S_HTTP}. Body: ${VM_S_BODY:0:300}"
VM_S_ID=$(echo "${VM_S_BODY}" | grep -oiE '"uuid"[[:space:]]*:[[:space:]]*"[0-9a-f-]{36}"' | grep -oiE '[0-9a-f-]{36}' | head -1 || true)
[[ -n "${VM_S_ID}" ]] || api_fail "no uuid in move-test shortcut create body. Body: ${VM_S_BODY:0:300}"

CALL_NUM=$((CALL_NUM+1))
echo "  API call ${CALL_NUM}: search files visibility=PRIVATE (shortcut indexed as PRIVATE)"
poll_files_until_present "PRIVATE" "${VM_SHORTCUT_NAME}" "${VM_S_ID}" "shortcut pre-move"
api_pass "shortcut ${VM_S_ID} indexed under PRIVATE"

# --- Shortcut: flip to PUBLIC, confirm it now reads PUBLIC and no longer matches PRIVATE ---
CALL_NUM=$((CALL_NUM+1))
echo "  API call ${CALL_NUM}: PUT /v3/files/shortcuts/{id} visibility=PUBLIC"
VM_S_PUT=$(curl -s -o /dev/null -w "%{http_code}" -X PUT -u "${TEST_USER}:${TEST_PASS}" \
  -H "Content-Type: application/json" -d '{"visibility":"PUBLIC"}' \
  "${BASE_URL}/v3/files/shortcuts/${VM_S_ID}")
[[ "${VM_S_PUT}" == "204" || "${VM_S_PUT}" == "200" ]] || api_fail "PUT move-test shortcut visibility → HTTP ${VM_S_PUT}"

CALL_NUM=$((CALL_NUM+1))
echo "  API call ${CALL_NUM}: search files visibility=PUBLIC (shortcut now reads as PUBLIC)"
poll_files_until_present "PUBLIC" "${VM_SHORTCUT_NAME}" "${VM_S_ID}" "shortcut post-move (reads new visibility)"
CALL_NUM=$((CALL_NUM+1))
echo "  API call ${CALL_NUM}: search files visibility=PRIVATE (shortcut no longer matches PRIVATE)"
poll_files_until_absent "PRIVATE" "${VM_SHORTCUT_NAME}" "${VM_S_ID}" "shortcut post-move (old visibility gone)"
api_pass "shortcut visibility PRIVATE→PUBLIC rewritten in place: matches PUBLIC, no longer matches PRIVATE"

# ── STEP: issue #162 — searchFiles must report visibility on FOLDER results ───
# NETWORK items in a /v3/search/files response carry "visibility"; FOLDER items omit the key
# entirely, so clients cannot tell a public folder from a private one (ndexbio/ndex3#52). Cause:
# NFSSearchProvider.mapFolderToSummary never calls setVisibility, and FileItemSummary is
# @JsonInclude(NON_NULL), so the unset field is dropped rather than serialized as null. The
# NETWORK mapper and the shortcut DAO both set it, making FOLDER the lone outlier.
#
# Both partitions are covered (PUBLIC and PRIVATE) so the fix cannot be satisfied by emitting a
# hardcoded constant.
#
# The folder names MUST stay unique and randomized. `visibility=PUBLIC` narrows to the partition
# the old public core held, which is PUBLIC *or* UNLISTED, and the permission filter admits the
# caller's own documents whatever their visibility. So an authenticated owner searching
# visibility=PUBLIC legitimately gets their own UNLISTED folders back — and TEST_USER owns one by
# now (the vis-folder flipped to UNLISTED earlier). A broader searchString would match those too
# and make the assertion below meaningless.
step "searchFiles reports visibility on FOLDER results (issue #162)"

F162_PUB_NAME="visfolder162pub${RANDOM}${RANDOM}"
F162_PRIV_NAME="visfolder162priv${RANDOM}${RANDOM}"

# --- PUBLIC folder: must report visibility=PUBLIC in search results ---
CALL_NUM=$((CALL_NUM+1))
echo "  API call ${CALL_NUM}: POST /v3/files/folders/ (create PUBLIC folder for issue #162)"
F162_PUB_RESP=$(curl -s -w "\n%{http_code}" -X POST -u "${TEST_USER}:${TEST_PASS}" \
  -H "Content-Type: application/json" \
  -d "{\"name\":\"${F162_PUB_NAME}\",\"visibility\":\"PUBLIC\"}" \
  "${BASE_URL}/v3/files/folders/")
F162_PUB_HTTP=$(echo "${F162_PUB_RESP}" | tail -1); F162_PUB_CREATE_BODY=$(echo "${F162_PUB_RESP}" | head -1)
[[ "${F162_PUB_HTTP}" == "201" ]] || api_fail "create issue-#162 PUBLIC folder → HTTP ${F162_PUB_HTTP}. Body: ${F162_PUB_CREATE_BODY:0:300}"
F162_PUB_ID=$(echo "${F162_PUB_CREATE_BODY}" | grep -oiE '"uuid"[[:space:]]*:[[:space:]]*"[0-9a-f-]{36}"' | grep -oiE '[0-9a-f-]{36}' | head -1)
[[ -n "${F162_PUB_ID}" ]] || api_fail "no uuid in issue-#162 PUBLIC folder create body. Body: ${F162_PUB_CREATE_BODY:0:300}"

CALL_NUM=$((CALL_NUM+1))
echo "  API call ${CALL_NUM}: search files visibility=PUBLIC (folder indexed as PUBLIC)"
poll_files_until_present "PUBLIC" "${F162_PUB_NAME}" "${F162_PUB_ID}" "issue #162 public folder"

# No `type` in the request body — this mirrors the anonymous curl in the ticket, which went
# through the untyped search path.
CALL_NUM=$((CALL_NUM+1))
echo "  API call ${CALL_NUM}: POST /v3/search/files?visibility=PUBLIC (FOLDER item must carry visibility)"
F162_PUB_SEARCH=$(curl -s -w "\n%{http_code}" -X POST -u "${TEST_USER}:${TEST_PASS}" \
  -H "Content-Type: application/json" -d "{\"searchString\":\"${F162_PUB_NAME}\"}" \
  "${BASE_URL}/v3/search/files?visibility=PUBLIC&start=0&size=25")
F162_PUB_SEARCH_HTTP=$(echo "${F162_PUB_SEARCH}" | tail -1); F162_PUB_BODY=$(echo "${F162_PUB_SEARCH}" | head -1)
[[ "${F162_PUB_SEARCH_HTTP}" == "200" ]] || api_fail "search visibility=PUBLIC → HTTP ${F162_PUB_SEARCH_HTTP}. Body: ${F162_PUB_BODY:0:300}"
# Sanity first, so a Solr miss or a stray network cannot masquerade as the visibility bug.
echo "${F162_PUB_BODY}" | grep -q "${F162_PUB_ID}" \
  || api_fail "issue #162: folder ${F162_PUB_ID} missing from its own search results. Body: ${F162_PUB_BODY:0:400}"
echo "${F162_PUB_BODY}" | grep -qE '"type"[[:space:]]*:[[:space:]]*"FOLDER"' \
  || api_fail "issue #162: no FOLDER item in results for ${F162_PUB_NAME}. Body: ${F162_PUB_BODY:0:400}"
echo "${F162_PUB_BODY}" | grep -qE '"type"[[:space:]]*:[[:space:]]*"NETWORK"' \
  && api_fail "issue #162: unexpected NETWORK item matched ${F162_PUB_NAME}; its visibility would mask the folder's. Body: ${F162_PUB_BODY:0:400}"
echo "${F162_PUB_BODY}" | grep -qE '"visibility"[[:space:]]*:[[:space:]]*"PUBLIC"' \
  || api_fail "REGRESSION (issue #162): FOLDER item in /v3/search/files results has no visibility field. Body: ${F162_PUB_BODY:0:600}"
api_pass "search visibility=PUBLIC → FOLDER item reports visibility=PUBLIC"

# --- PRIVATE folder: must report visibility=PRIVATE (not a hardcoded PUBLIC) ---
CALL_NUM=$((CALL_NUM+1))
echo "  API call ${CALL_NUM}: POST /v3/files/folders/ (create folder, default visibility=PRIVATE)"
F162_PRIV_RESP=$(curl -s -w "\n%{http_code}" -X POST -u "${TEST_USER}:${TEST_PASS}" \
  -H "Content-Type: application/json" -d "{\"name\":\"${F162_PRIV_NAME}\"}" \
  "${BASE_URL}/v3/files/folders/")
F162_PRIV_HTTP=$(echo "${F162_PRIV_RESP}" | tail -1); F162_PRIV_CREATE_BODY=$(echo "${F162_PRIV_RESP}" | head -1)
[[ "${F162_PRIV_HTTP}" == "201" ]] || api_fail "create issue-#162 PRIVATE folder → HTTP ${F162_PRIV_HTTP}. Body: ${F162_PRIV_CREATE_BODY:0:300}"
F162_PRIV_ID=$(echo "${F162_PRIV_CREATE_BODY}" | grep -oiE '"uuid"[[:space:]]*:[[:space:]]*"[0-9a-f-]{36}"' | grep -oiE '[0-9a-f-]{36}' | head -1)
[[ -n "${F162_PRIV_ID}" ]] || api_fail "no uuid in issue-#162 PRIVATE folder create body. Body: ${F162_PRIV_CREATE_BODY:0:300}"

CALL_NUM=$((CALL_NUM+1))
echo "  API call ${CALL_NUM}: search files visibility=PRIVATE (folder indexed as PRIVATE)"
poll_files_until_present "PRIVATE" "${F162_PRIV_NAME}" "${F162_PRIV_ID}" "issue #162 private folder"

CALL_NUM=$((CALL_NUM+1))
echo "  API call ${CALL_NUM}: POST /v3/search/files?visibility=PRIVATE (FOLDER item must carry visibility)"
F162_PRIV_SEARCH=$(curl -s -w "\n%{http_code}" -X POST -u "${TEST_USER}:${TEST_PASS}" \
  -H "Content-Type: application/json" -d "{\"searchString\":\"${F162_PRIV_NAME}\"}" \
  "${BASE_URL}/v3/search/files?visibility=PRIVATE&start=0&size=25")
F162_PRIV_SEARCH_HTTP=$(echo "${F162_PRIV_SEARCH}" | tail -1); F162_PRIV_BODY=$(echo "${F162_PRIV_SEARCH}" | head -1)
[[ "${F162_PRIV_SEARCH_HTTP}" == "200" ]] || api_fail "search visibility=PRIVATE → HTTP ${F162_PRIV_SEARCH_HTTP}. Body: ${F162_PRIV_BODY:0:300}"
echo "${F162_PRIV_BODY}" | grep -q "${F162_PRIV_ID}" \
  || api_fail "issue #162: folder ${F162_PRIV_ID} missing from its own search results. Body: ${F162_PRIV_BODY:0:400}"
echo "${F162_PRIV_BODY}" | grep -qE '"type"[[:space:]]*:[[:space:]]*"FOLDER"' \
  || api_fail "issue #162: no FOLDER item in results for ${F162_PRIV_NAME}. Body: ${F162_PRIV_BODY:0:400}"
echo "${F162_PRIV_BODY}" | grep -qE '"type"[[:space:]]*:[[:space:]]*"NETWORK"' \
  && api_fail "issue #162: unexpected NETWORK item matched ${F162_PRIV_NAME}; its visibility would mask the folder's. Body: ${F162_PRIV_BODY:0:400}"
echo "${F162_PRIV_BODY}" | grep -qE '"visibility"[[:space:]]*:[[:space:]]*"PRIVATE"' \
  || api_fail "REGRESSION (issue #162): PRIVATE FOLDER item in /v3/search/files results does not report visibility=PRIVATE. Body: ${F162_PRIV_BODY:0:600}"
api_pass "search visibility=PRIVATE → FOLDER item reports visibility=PRIVATE"

# ── STEP: isCertified is reported on file listings (#194) ────────────────────
# Network entries in the v3 file listings now carry isCertified alongside doi, so a client can tell
# a certified network from a "pre-certified" one (DOI minted, reference not yet added) without
# fetching a full summary per row. Five DAO mappers produce those entries and each is reached by a
# different caller, so each needs its own call: listItemsInFolderOrHome (folder /list),
# listPublicRootItemsOfUser (anonymous home), listSharedNetworks (shared-with-me),
# listNetworksSharedBySpecificUser (another user's home) and listTrashedItemsOfUser (trash), plus
# NFSSearchProvider for search — which also keeps a legacy attributes.isCertified copy.
#
# The value is always present for type=NETWORK and absent only for FOLDER/SHORTCUT, which have no
# certification state, so the folder assertion below is what gives "absent" its meaning.
#
# The network stays at home root: the home listings select parent IS NULL, so a network moved into a
# folder would never appear in them and those assertions would pass vacuously. certified and ndexdoi
# are set with psql because the only API that certifies a network is the DOI mint, which needs an
# EZID service this container does not run.
if [[ -z "${REMOTE_NDEX_URL}" ]]; then
  step "isCertified is reported on file listings (#194)"

  CERT_FOLDER_NAME="certfolder194${RANDOM}${RANDOM}"

  CALL_NUM=$((CALL_NUM+1))
  echo "  API call ${CALL_NUM}: POST /v3/networks?visibility=PUBLIC (#194 subject at home root)"
  CERT_UP=$(curl -s -w "\n%{http_code}" -X POST -u "${TEST_USER}:${TEST_PASS}" \
    -H "Content-Type: application/json" --data-binary "@${FIXTURES_DIR}/C. burnetii Network.cx2" \
    "${BASE_URL}/v3/networks?visibility=PUBLIC")
  CERT_UP_HTTP=$(echo "${CERT_UP}" | tail -1); CERT_UP_BODY=$(echo "${CERT_UP}" | head -1)
  [[ "${CERT_UP_HTTP}" == "201" ]] || api_fail "#194: POST /v3/networks → HTTP ${CERT_UP_HTTP}. Body: ${CERT_UP_BODY:0:300}"
  CERT_NET=$(echo "${CERT_UP_BODY}" | grep -o '"uuid":"[^"]*"' | head -1 | cut -d'"' -f4)
  [[ -n "${CERT_NET}" ]] || api_fail "#194: no uuid in network create body. Body: ${CERT_UP_BODY:0:300}"
  api_pass "POST /v3/networks → 201 Created (#194 subject ${CERT_NET})"

  CERT_WAIT=0
  until curl -s -u "${TEST_USER}:${TEST_PASS}" "${BASE_URL}/v3/networks/${CERT_NET}/summary" | grep -q '"completed":true'; do
    CERT_WAIT=$((CERT_WAIT+2))
    [[ ${CERT_WAIT} -ge ${LOAD_TIMEOUT} ]] && api_fail "#194: subject ${CERT_NET} did not finish loading in ${LOAD_TIMEOUT}s"
    sleep 2
  done
  wait_for_task_queue_drain "the #194 network upload"

  CALL_NUM=$((CALL_NUM+1))
  echo "  API call ${CALL_NUM}: POST /v3/files/folders/ (#194 folder — folders carry no certification state)"
  CERT_F_RESP=$(curl -s -w "\n%{http_code}" -X POST -u "${TEST_USER}:${TEST_PASS}" \
    -H "Content-Type: application/json" \
    -d "{\"name\":\"${CERT_FOLDER_NAME}\",\"visibility\":\"PUBLIC\"}" \
    "${BASE_URL}/v3/files/folders/")
  CERT_F_HTTP=$(echo "${CERT_F_RESP}" | tail -1); CERT_F_BODY=$(echo "${CERT_F_RESP}" | head -1)
  [[ "${CERT_F_HTTP}" == "201" ]] || api_fail "#194: create folder → HTTP ${CERT_F_HTTP}. Body: ${CERT_F_BODY:0:300}"
  CERT_FOLDER=$(echo "${CERT_F_BODY}" | grep -oiE '"uuid"[[:space:]]*:[[:space:]]*"[0-9a-f-]{36}"' | grep -oiE '[0-9a-f-]{36}' | head -1)
  [[ -n "${CERT_FOLDER}" ]] || api_fail "#194: no uuid in folder create body. Body: ${CERT_F_BODY:0:300}"

  # A freshly uploaded network is certified=false, and false must be PRESENT rather than omitted —
  # that is what separates "not certified" from "this endpoint does not report it".
  CALL_NUM=$((CALL_NUM+1))
  echo "  API call ${CALL_NUM}: GET /v3/files/folders/home/list — uncertified network reports isCertified:false"
  CERT_LIST=$(curl -s -w "\n%{http_code}" -u "${TEST_USER}:${TEST_PASS}" "${BASE_URL}/v3/files/folders/home/list")
  CERT_LIST_HTTP=$(echo "${CERT_LIST}" | tail -1); CERT_LIST_BODY=$(echo "${CERT_LIST}" | head -1)
  [[ "${CERT_LIST_HTTP}" == "200" ]] || api_fail "#194: GET home/list → HTTP ${CERT_LIST_HTTP}. Body: ${CERT_LIST_BODY:0:300}"
  CERT_ENTRY=$(name161_entry_for_uuid "${CERT_LIST_BODY}" "${CERT_NET}" || true)
  [[ -n "${CERT_ENTRY}" ]] || api_fail "#194: subject ${CERT_NET} missing from home/list. Body: ${CERT_LIST_BODY:0:400}"
  echo "${CERT_ENTRY}" | grep -qE '"isCertified"[[:space:]]*:[[:space:]]*false' \
    || api_fail "REGRESSION (#194): an uncertified network omits isCertified in the folder listing; it must be present as false. Entry: ${CERT_ENTRY:0:400}"
  api_pass "GET /v3/files/folders/home/list → uncertified network reports isCertified:false"

  # Seed only the DOI — minting one needs an EZID service this container does not run. Certification
  # itself goes through the real endpoint, which is what queues the reindex that #197 was about. The
  # DOI stays on the row because isCertified is only meaningful next to doi, and the trash assertion
  # below covers both columns the trash projection newly selects.
  psql_ndex "UPDATE network SET ndexdoi = '10.18119/ndex-it-194' WHERE \\\"UUID\\\" = '${CERT_NET}'" >/dev/null

  CALL_NUM=$((CALL_NUM+1))
  echo "  API call ${CALL_NUM}: PUT /v2/network/{networkid}/reference — certifies the pre-certified network"
  CERT_REF_HTTP=$(curl -s -o /dev/null -w "%{http_code}" -X PUT -u "${TEST_USER}:${TEST_PASS}" \
    -H "Content-Type: application/json" -d '{"reference":"NDEx integration test reference"}' \
    "${BASE_URL}/v2/network/${CERT_NET}/reference")
  [[ "${CERT_REF_HTTP}" == "200" || "${CERT_REF_HTTP}" == "204" ]] \
    || api_fail "#197: PUT /v2/network/${CERT_NET}/reference → HTTP ${CERT_REF_HTTP}"
  CERT_DB=$(psql_ndex "SELECT certified FROM network WHERE \\\"UUID\\\" = '${CERT_NET}'")
  [[ "${CERT_DB}" == "t" ]] || api_fail "#194: the reference endpoint did not set certified=true on ${CERT_NET} (got '${CERT_DB}')"
  api_pass "PUT /v2/network/{networkid}/reference → certified=true"

  # Certification queues a global reindex. That reindex used to throw on a duplicate name field and,
  # because the exception was a RuntimeException that escaped the task's catch, left the network at
  # completed:false with no error recorded and its Solr document already deleted (#197).
  wait_for_task_queue_drain "the #197 certification reindex"

  CALL_NUM=$((CALL_NUM+1))
  echo "  API call ${CALL_NUM}: GET /v3/networks/{networkid}/summary — completed:true after certification"
  CERT_SUMM=$(curl -s -u "${TEST_USER}:${TEST_PASS}" "${BASE_URL}/v3/networks/${CERT_NET}/summary")
  echo "${CERT_SUMM}" | grep -q '"completed":true' \
    || api_fail "REGRESSION (#197): certified network ${CERT_NET} stuck at completed:false — the reindex queued by certification failed. Summary: ${CERT_SUMM:0:400}"
  api_pass "GET /v3/networks/{networkid}/summary → completed:true after certification (#197)"

  CALL_NUM=$((CALL_NUM+1))
  echo "  API call ${CALL_NUM}: GET /v3/files/folders/home/list — certified network reports isCertified:true, folder omits it"
  CERT_LIST=$(curl -s -w "\n%{http_code}" -u "${TEST_USER}:${TEST_PASS}" "${BASE_URL}/v3/files/folders/home/list")
  CERT_LIST_HTTP=$(echo "${CERT_LIST}" | tail -1); CERT_LIST_BODY=$(echo "${CERT_LIST}" | head -1)
  [[ "${CERT_LIST_HTTP}" == "200" ]] || api_fail "#194: GET home/list → HTTP ${CERT_LIST_HTTP}. Body: ${CERT_LIST_BODY:0:300}"
  CERT_ENTRY=$(name161_entry_for_uuid "${CERT_LIST_BODY}" "${CERT_NET}" || true)
  [[ -n "${CERT_ENTRY}" ]] || api_fail "#194: subject ${CERT_NET} missing from home/list. Body: ${CERT_LIST_BODY:0:400}"
  echo "${CERT_ENTRY}" | grep -qE '"isCertified"[[:space:]]*:[[:space:]]*true' \
    || api_fail "#194: certified network does not report isCertified:true in the folder listing. Entry: ${CERT_ENTRY:0:400}"
  echo "${CERT_ENTRY}" | grep -qE '"doi"[[:space:]]*:[[:space:]]*"10\.18119/ndex-it-194"' \
    || api_fail "#194: certified network lost its doi in the folder listing; isCertified cannot be read without it. Entry: ${CERT_ENTRY:0:400}"
  # Same response, so the folder cannot be omitting the key just because the endpoint stopped emitting it.
  CERT_F_ENTRY=$(name161_entry_for_uuid "${CERT_LIST_BODY}" "${CERT_FOLDER}" || true)
  [[ -n "${CERT_F_ENTRY}" ]] || api_fail "#194: folder ${CERT_FOLDER} missing from home/list. Body: ${CERT_LIST_BODY:0:400}"
  echo "${CERT_F_ENTRY}" | grep -q '"isCertified"' \
    && api_fail "#194: a FOLDER entry emitted isCertified; folders have no certification state and must omit the key. Entry: ${CERT_F_ENTRY:0:400}"
  api_pass "GET /v3/files/folders/home/list → certified network reports isCertified:true with its doi; FOLDER entry omits the key"

  # A NULL column is what a row predating the certified column looks like. It must read as false
  # rather than dropping the key, so that "absent" keeps meaning "not a network".
  psql_ndex "UPDATE network SET certified = NULL WHERE \\\"UUID\\\" = '${CERT_NET}'" >/dev/null
  CERT_DB=$(psql_ndex "SELECT COALESCE(certified::text,'NULL') FROM network WHERE \\\"UUID\\\" = '${CERT_NET}'")
  [[ "${CERT_DB}" == "NULL" ]] || api_fail "#194: could not set certified=NULL on ${CERT_NET} (got '${CERT_DB}')"

  CALL_NUM=$((CALL_NUM+1))
  echo "  API call ${CALL_NUM}: GET /v3/files/folders/home/list — a NULL certified column reads as false"
  CERT_LIST_BODY=$(curl -s -u "${TEST_USER}:${TEST_PASS}" "${BASE_URL}/v3/files/folders/home/list")
  CERT_ENTRY=$(name161_entry_for_uuid "${CERT_LIST_BODY}" "${CERT_NET}" || true)
  [[ -n "${CERT_ENTRY}" ]] || api_fail "#194: subject ${CERT_NET} missing from home/list. Body: ${CERT_LIST_BODY:0:400}"
  echo "${CERT_ENTRY}" | grep -qE '"isCertified"[[:space:]]*:[[:space:]]*false' \
    || api_fail "REGRESSION (#194): a NULL certified column made isCertified disappear; it must read as false. Entry: ${CERT_ENTRY:0:400}"
  api_pass "GET /v3/files/folders/home/list → NULL certified column reports isCertified:false"

  psql_ndex "UPDATE network SET certified = true WHERE \\\"UUID\\\" = '${CERT_NET}'" >/dev/null

  CALL_NUM=$((CALL_NUM+1))
  echo "  API call ${CALL_NUM}: GET /v3/users/{userid}/home (anonymous) — isCertified:true"
  CERT_OWNER_ID=$(curl -s "${BASE_URL}/v2/user?username=${TEST_USER}" \
    | grep -oiE '"externalId"[[:space:]]*:[[:space:]]*"[0-9a-f-]{36}"' | grep -oiE '[0-9a-f-]{36}' | head -1 || true)
  [[ -n "${CERT_OWNER_ID}" ]] || api_fail "#194: could not resolve ${TEST_USER} UUID"
  CERT_ANON_BODY=$(curl -s "${BASE_URL}/v3/users/${CERT_OWNER_ID}/home")
  CERT_ENTRY=$(name161_entry_for_uuid "${CERT_ANON_BODY}" "${CERT_NET}" || true)
  [[ -n "${CERT_ENTRY}" ]] || api_fail "#194: subject missing from the anonymous home listing (it is PUBLIC and at home root). Body: ${CERT_ANON_BODY:0:400}"
  echo "${CERT_ENTRY}" | grep -qE '"isCertified"[[:space:]]*:[[:space:]]*true' \
    || api_fail "#194: anonymous home listing does not report isCertified:true. Entry: ${CERT_ENTRY:0:400}"
  api_pass "GET /v3/users/{userid}/home (anonymous) → isCertified:true"

  CALL_NUM=$((CALL_NUM+1))
  echo "  API call ${CALL_NUM}: POST /v3/files/sharing/members — grant ${TEST_USER2} READ on the certified network"
  CERT_U2_ID=$(curl -s "${BASE_URL}/v2/user?username=${TEST_USER2}" \
    | grep -oiE '"externalId"[[:space:]]*:[[:space:]]*"[0-9a-f-]{36}"' | grep -oiE '[0-9a-f-]{36}' | head -1 || true)
  [[ -n "${CERT_U2_ID}" ]] || api_fail "#194: could not resolve ${TEST_USER2} UUID"
  CERT_GRANT_HTTP=$(curl -s -o /dev/null -w "%{http_code}" -X POST -u "${TEST_USER}:${TEST_PASS}" \
    -H "Content-Type: application/json" \
    -d "{\"files\":{\"${CERT_NET}\":\"NETWORK\"},\"members\":{\"${CERT_U2_ID}\":\"READ\"}}" \
    "${BASE_URL}/v3/files/sharing/members")
  [[ "${CERT_GRANT_HTTP}" == "200" || "${CERT_GRANT_HTTP}" == "204" ]] \
    || api_fail "#194: sharing grant → HTTP ${CERT_GRANT_HTTP}"
  api_pass "granted ${TEST_USER2} READ on the certified network"

  CALL_NUM=$((CALL_NUM+1))
  echo "  API call ${CALL_NUM}: GET /v3/files/sharing/list (${TEST_USER2}) — isCertified:true"
  CERT_SHARED_BODY=$(curl -s -u "${TEST_USER2}:${TEST_PASS2}" "${BASE_URL}/v3/files/sharing/list")
  CERT_ENTRY=$(name161_entry_for_uuid "${CERT_SHARED_BODY}" "${CERT_NET}" || true)
  [[ -n "${CERT_ENTRY}" ]] || api_fail "#194: subject missing from shared-with-me. Body: ${CERT_SHARED_BODY:0:400}"
  echo "${CERT_ENTRY}" | grep -qE '"isCertified"[[:space:]]*:[[:space:]]*true' \
    || api_fail "#194: shared-with-me listing does not report isCertified:true. Entry: ${CERT_ENTRY:0:400}"
  api_pass "GET /v3/files/sharing/list → isCertified:true"

  CALL_NUM=$((CALL_NUM+1))
  echo "  API call ${CALL_NUM}: GET /v3/users/{userid}/home as ${TEST_USER2} — isCertified:true"
  CERT_U2_HOME=$(curl -s -u "${TEST_USER2}:${TEST_PASS2}" "${BASE_URL}/v3/users/${CERT_OWNER_ID}/home")
  CERT_ENTRY=$(name161_entry_for_uuid "${CERT_U2_HOME}" "${CERT_NET}" || true)
  [[ -n "${CERT_ENTRY}" ]] || api_fail "#194: subject missing from another user's view of the owner's home. Body: ${CERT_U2_HOME:0:400}"
  echo "${CERT_ENTRY}" | grep -qE '"isCertified"[[:space:]]*:[[:space:]]*true' \
    || api_fail "#194: signed-in non-owner home listing does not report isCertified:true. Entry: ${CERT_ENTRY:0:400}"
  api_pass "GET /v3/users/{userid}/home (signed-in non-owner) → isCertified:true"

  # Search maps from the v2 NetworkSummary rather than the DAO listings, and keeps a legacy
  # attributes.isCertified copy, so both the top-level field and the alias are asserted.
  CALL_NUM=$((CALL_NUM+1))
  echo "  API call ${CALL_NUM}: POST /v3/search/files — isCertified:true at top level and in attributes"
  poll_files_until_present "PUBLIC" "burnetii" "${CERT_NET}" "#194 subject indexing"
  CERT_SEARCH=$(curl -s -w "\n%{http_code}" -X POST -u "${TEST_USER}:${TEST_PASS}" \
    -H "Content-Type: application/json" -d '{"searchString":"burnetii"}' \
    "${BASE_URL}/v3/search/files?visibility=PUBLIC&start=0&size=25")
  CERT_SEARCH_HTTP=$(echo "${CERT_SEARCH}" | tail -1); CERT_SEARCH_BODY=$(echo "${CERT_SEARCH}" | head -1)
  [[ "${CERT_SEARCH_HTTP}" == "200" ]] || api_fail "#194: search → HTTP ${CERT_SEARCH_HTTP}. Body: ${CERT_SEARCH_BODY:0:300}"
  CERT_ENTRY=$(name161_entry_for_uuid "${CERT_SEARCH_BODY}" "${CERT_NET}" || true)
  [[ -n "${CERT_ENTRY}" ]] || api_fail "#194: subject missing from its own search results. Body: ${CERT_SEARCH_BODY:0:400}"
  # Two occurrences: the top-level field and the legacy attributes alias, which must not diverge.
  CERT_HITS=$(echo "${CERT_ENTRY}" | grep -oE '"isCertified"[[:space:]]*:[[:space:]]*true' | wc -l | tr -d ' ' || true)
  [[ "${CERT_HITS}" == "2" ]] \
    || api_fail "#194: search result must carry isCertified:true both at the top level and in attributes, found ${CERT_HITS}. Entry: ${CERT_ENTRY:0:500}"
  api_pass "POST /v3/search/files → isCertified:true at top level and in attributes"

  # Trash last: it removes the network from every other listing. The trash projection did not select
  # certified or ndexdoi before this change, so trashed networks were the one network-bearing listing
  # that omitted them.
  CALL_NUM=$((CALL_NUM+1))
  echo "  API call ${CALL_NUM}: DELETE /v3/networks/{networkid} then GET /v3/files/trash — isCertified:true and doi reported"
  CERT_DEL_HTTP=$(curl -s -o /dev/null -w "%{http_code}" -X DELETE -u "${TEST_USER}:${TEST_PASS}" \
    "${BASE_URL}/v3/networks/${CERT_NET}")
  [[ "${CERT_DEL_HTTP}" == "200" || "${CERT_DEL_HTTP}" == "204" ]] \
    || api_fail "#194: soft delete → HTTP ${CERT_DEL_HTTP}"
  CERT_TRASH_BODY=$(curl -s -u "${TEST_USER}:${TEST_PASS}" "${BASE_URL}/v3/files/trash")
  CERT_ENTRY=$(name161_entry_for_uuid "${CERT_TRASH_BODY}" "${CERT_NET}" || true)
  [[ -n "${CERT_ENTRY}" ]] || api_fail "#194: subject missing from /v3/files/trash after a soft delete. Body: ${CERT_TRASH_BODY:0:400}"
  echo "${CERT_ENTRY}" | grep -qE '"isCertified"[[:space:]]*:[[:space:]]*true' \
    || api_fail "REGRESSION (#194): trash listing omits isCertified; every type=NETWORK entry must report it. Entry: ${CERT_ENTRY:0:400}"
  echo "${CERT_ENTRY}" | grep -qE '"doi"[[:space:]]*:[[:space:]]*"10\.18119/ndex-it-194"' \
    || api_fail "REGRESSION (#194): trash listing omits doi; isCertified cannot be interpreted without it. Entry: ${CERT_ENTRY:0:400}"
  api_pass "GET /v3/files/trash → trashed network reports isCertified:true and its doi"
fi

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
    | grep -oiE '"externalId"[[:space:]]*:[[:space:]]*"[0-9a-f-]{36}"' | grep -oiE '[0-9a-f-]{36}' | head -1 || true)
  [[ -n "${NS_OWNER_ID}" ]] || api_fail "could not resolve ${TEST_USER} UUID from GET /v2/user?username"

  # ── 1) POST /v2/networkset → creates a FOLDER at the owner's home root ──────────────────────────
  CALL_NUM=$((CALL_NUM+1))
  echo "  API call ${CALL_NUM}: POST /v2/networkset (create) — expect 201"
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
  echo "  API call ${CALL_NUM}: GET /v3/files/folders/${NS_ID} — same object via v3"
  NS_F=$(curl -s -w "\n%{http_code}" -u "${TEST_USER}:${TEST_PASS}" "${BASE_URL}/v3/files/folders/${NS_ID}")
  NS_F_HTTP=$(echo "${NS_F}" | tail -1); NS_F_BODY=$(echo "${NS_F}" | head -1)
  [[ "${NS_F_HTTP}" == "200" ]] \
    || api_fail "GET /v3/files/folders/{setid} → HTTP ${NS_F_HTTP} (a set id must be a folder id). Body: ${NS_F_BODY:0:400}"
  echo "${NS_F_BODY}" | grep -q "${NS_NAME}" || api_fail "v3 folder is missing the posted name. Body: ${NS_F_BODY:0:400}"
  echo "${NS_F_BODY}" | grep -q "${NS_DESC}" || api_fail "v3 folder is missing the posted description. Body: ${NS_F_BODY:0:400}"
  NS_PARENT=$(psql_ndex "SELECT COALESCE(parent::text,'NULL') FROM folder WHERE \\\"UUID\\\"='${NS_ID}';")
  [[ "${NS_PARENT}" == "NULL" ]] || api_fail "a new set must sit at home root (parent IS NULL), got '${NS_PARENT}'"
  api_pass "POST /v2/networkset created a v3 folder at home root with the posted name/description"

  CALL_NUM=$((CALL_NUM+1))
  echo "  API call ${CALL_NUM}: GET /v3/files/folders/home/count — folder total matches home root"
  NS_HOME_COUNT=$(curl -s -w "\n%{http_code}" -u "${TEST_USER}:${TEST_PASS}" "${BASE_URL}/v3/files/folders/home/count")
  NS_HOME_COUNT_HTTP=$(echo "${NS_HOME_COUNT}" | tail -1); NS_HOME_COUNT_BODY=$(echo "${NS_HOME_COUNT}" | head -1)
  [[ "${NS_HOME_COUNT_HTTP}" == "200" ]] \
    || api_fail "GET /v3/files/folders/home/count → HTTP ${NS_HOME_COUNT_HTTP}. Body: ${NS_HOME_COUNT_BODY:0:400}"
  NS_HOME_FOLDER_COUNT=$(echo "${NS_HOME_COUNT_BODY}" | grep -oE '"folder"[[:space:]]*:[[:space:]]*[0-9]+' | grep -oE '[0-9]+$')
  NS_ROOT_FOLDER_TOTAL=$(psql_ndex "SELECT count(*) FROM folder WHERE owneruuid='${NS_OWNER_ID}' AND parent IS NULL AND is_deleted=false;")
  { [[ -n "${NS_HOME_FOLDER_COUNT}" ]] && [[ "${NS_HOME_FOLDER_COUNT}" == "${NS_ROOT_FOLDER_TOTAL}" ]]; } \
    || api_fail "GET /v3/files/folders/home/count folder=${NS_HOME_FOLDER_COUNT} but DB root total=${NS_ROOT_FOLDER_TOTAL}. Body: ${NS_HOME_COUNT_BODY:0:400}"
  api_pass "GET /v3/files/folders/home/count matches the owner's root-folder total"

  # Solr: v2-created sets must be searchable through v3. Indexing is async, hence the poll.
  poll_files_until_present PRIVATE "${NS_NAME}" "${NS_ID}" "networkset create indexing"
  api_pass "new set is indexed and findable via POST /v3/search/files"

  # ── 2) POST /{id}/members → adds each network as a SHORTCUT ─────────────────────────────────────
  CALL_NUM=$((CALL_NUM+1))
  echo "  API call ${CALL_NUM}: POST /v2/networkset/${NS_ID}/members (2 networks) — expect 201"
  NS_ADD_HTTP=$(curl -s -o /dev/null -w "%{http_code}" -X POST -u "${TEST_USER}:${TEST_PASS}" \
    -H "Content-Type: application/json" \
    -d "[\"${V2_PUB_UUID}\",\"${V2_PRIV_UUID}\"]" "${BASE_URL}/v2/networkset/${NS_ID}/members")
  [[ "${NS_ADD_HTTP}" == "201" ]] || api_fail "POST /v2/networkset/{id}/members → HTTP ${NS_ADD_HTTP} (expected 201)"
  api_pass "POST /v2/networkset/{id}/members → 201, 2 networks added"

  # v3 must report one NETWORK-target shortcut per posted network, named after the NETWORK (not its
  # UUID — earlier releases named shortcuts networkId.toString()), and count them.
  CALL_NUM=$((CALL_NUM+1))
  echo "  API call ${CALL_NUM}: GET /v3/files/folders/${NS_ID}/list — shortcuts per member"
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
  echo "  API call ${CALL_NUM}: GET /v3/files/folders/${NS_ID}/count — shortcut=2"
  NS_COUNT=$(curl -s -u "${TEST_USER}:${TEST_PASS}" "${BASE_URL}/v3/files/folders/${NS_ID}/count")
  echo "${NS_COUNT}" | grep -qE '"shortcut"[[:space:]]*:[[:space:]]*2' \
    || api_fail "expected shortcut count 2 in /count. Body: ${NS_COUNT:0:300}"
  api_pass "GET /v3/files/folders/{id}/count reports shortcut=2"

  # Each member shortcut must be indexed in its own right, not just the folder.
  poll_files_until_present PRIVATE "${NS_SC_PUB}" "${NS_SC_PUB}" "member shortcut indexing"
  api_pass "member shortcuts are indexed individually"

  # Rejects the whole request when any posted id is unreadable — nothing partially created.
  CALL_NUM=$((CALL_NUM+1))
  echo "  API call ${CALL_NUM}: POST /v2/networkset/${NS_ID}/members with an unreadable id — expect 4xx"
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
  echo "  API call ${CALL_NUM}: GET /v3/files/folders/${NS_ID} — renamed, parent unchanged"
  NS_F2=$(curl -s -u "${TEST_USER}:${TEST_PASS}" "${BASE_URL}/v3/files/folders/${NS_ID}")
  echo "${NS_F2}" | grep -q "${NS_NAME_2}" || api_fail "v3 folder does not show the new name. Body: ${NS_F2:0:400}"
  NS_PARENT2=$(psql_ndex "SELECT COALESCE(parent::text,'NULL') FROM folder WHERE \\\"UUID\\\"='${NS_ID}';")
  [[ "${NS_PARENT2}" == "NULL" ]] \
    || api_fail "PUT must preserve the parent; it changed to '${NS_PARENT2}' (a nested set would be moved to root)"
  api_pass "PUT /v2/networkset/{id} renamed the folder and left its parent untouched"

  # The re-index upserts on uuid, so the renamed document replaces its predecessor and the old name stops matching.
  poll_files_until_present PRIVATE "${NS_NAME_2}" "${NS_ID}" "networkset rename indexing"
  poll_files_until_absent PRIVATE "${NS_NAME}" "${NS_ID}" "networkset rename stale doc"
  api_pass "rename re-indexed the set: new name matches, old name no longer does"

  # PUT is an upsert, so it has to tell apart "this id is free" from "this id is taken by someone else".
  # A non-owner must get 401 rather than a 500 from a primary-key violation on an attempted create, and
  # the rejected request must not have written anything.
  CALL_NUM=$((CALL_NUM+1))
  echo "  API call ${CALL_NUM}: PUT /v2/networkset/${NS_ID} (non-owner ${TEST_USER2}) — 401, not 500"
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
  echo "  API call ${CALL_NUM}: PUT /v2/networkset/${NS_ID}/accesskey?action=enable — expect 200 + key"
  NS_KEY_BODY=$(curl -s -u "${TEST_USER}:${TEST_PASS}" -X PUT "${BASE_URL}/v2/networkset/${NS_ID}/accesskey?action=enable")
  NS_KEY=$(echo "${NS_KEY_BODY}" | grep -oE '"accessKey"[[:space:]]*:[[:space:]]*"[^"]+"' | sed 's/.*"\([^"]*\)"$/\1/')
  [[ -n "${NS_KEY}" ]] || api_fail "enable did not return an accessKey. Body: ${NS_KEY_BODY:0:300}"
  api_pass "PUT /v2/networkset/{id}/accesskey?action=enable → 200, key issued"

  # The v2 and v3 key surfaces are the same folder row.
  CALL_NUM=$((CALL_NUM+1))
  echo "  API call ${CALL_NUM}: GET /v3/files/folders/${NS_ID}/accesskey — identical key"
  NS_V3KEY=$(curl -s -u "${TEST_USER}:${TEST_PASS}" "${BASE_URL}/v3/files/folders/${NS_ID}/accesskey")
  echo "${NS_V3KEY}" | grep -q "${NS_KEY}" \
    || api_fail "v3 folder accesskey differs from the v2 network set key. Body: ${NS_V3KEY:0:300}"
  api_pass "the v2 network set key and the v3 folder key are the same value"

  # Disable preserves the key value, so re-enabling returns the same string (idempotent enable).
  CALL_NUM=$((CALL_NUM+1))
  echo "  API call ${CALL_NUM}: PUT /v2/networkset/${NS_ID}/accesskey?action=disable — expect 204"
  NS_DIS_HTTP=$(curl -s -o /dev/null -w "%{http_code}" -u "${TEST_USER}:${TEST_PASS}" -X PUT \
    "${BASE_URL}/v2/networkset/${NS_ID}/accesskey?action=disable")
  [[ "${NS_DIS_HTTP}" == "204" ]] || api_fail "disable → HTTP ${NS_DIS_HTTP} (expected 204)"
  CALL_NUM=$((CALL_NUM+1))
  echo "  API call ${CALL_NUM}: PUT /v2/networkset/${NS_ID}/accesskey?action=enable — same key back"
  NS_KEY2=$(curl -s -u "${TEST_USER}:${TEST_PASS}" -X PUT "${BASE_URL}/v2/networkset/${NS_ID}/accesskey?action=enable" \
    | grep -oE '"accessKey"[[:space:]]*:[[:space:]]*"[^"]+"' | sed 's/.*"\([^"]*\)"$/\1/')
  [[ "${NS_KEY2}" == "${NS_KEY}" ]] \
    || api_fail "re-enabling must return the same key ('${NS_KEY2}' vs '${NS_KEY}')"
  api_pass "disable preserves the key value; re-enabling is idempotent and returns the same string"

  # An invalid action is rejected rather than being treated as one of the two.
  CALL_NUM=$((CALL_NUM+1))
  echo "  API call ${CALL_NUM}: PUT /v2/networkset/${NS_ID}/accesskey?action=bogus — expect 4xx"
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
  echo "  API call ${CALL_NUM}: GET /v2/networkset/${NS_ID} (anon, folder is PRIVATE) — 401"
  NS_ANON_PRIV_HTTP=$(curl -s -o /dev/null -w "%{http_code}" "${BASE_URL}/v2/networkset/${NS_ID}")
  [[ "${NS_ANON_PRIV_HTTP}" == "401" ]] \
    || api_fail "anon read of a PRIVATE set → HTTP ${NS_ANON_PRIV_HTTP} (expected 401; folder visibility governs)"

  # A signed-in NON-OWNER with no permission on the folder is refused too. This is what proves the gate is
  # folder read-access, not merely "reject anonymous".
  CALL_NUM=$((CALL_NUM+1))
  echo "  API call ${CALL_NUM}: GET /v2/networkset/${NS_ID} (non-owner ${TEST_USER2}, PRIVATE) — 401"
  NS_OTHER_PRIV_HTTP=$(curl -s -o /dev/null -w "%{http_code}" -u "${TEST_USER2}:${TEST_PASS2}" \
    "${BASE_URL}/v2/networkset/${NS_ID}")
  [[ "${NS_OTHER_PRIV_HTTP}" == "401" ]] \
    || api_fail "non-owner read of a PRIVATE set → HTTP ${NS_OTHER_PRIV_HTTP} (expected 401)"

  # The owner reading that identical url succeeds, and gets the real content.
  CALL_NUM=$((CALL_NUM+1))
  echo "  API call ${CALL_NUM}: GET /v2/networkset/${NS_ID} (owner, same PRIVATE set) — 200"
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
  echo "  API call ${CALL_NUM}: PUT /v3/files/folders/${NS_ID} visibility=PUBLIC"
  NS_VIS_HTTP=$(curl -s -o /dev/null -w "%{http_code}" -X PUT -u "${TEST_USER}:${TEST_PASS}" \
    -H "Content-Type: application/json" \
    -d "{\"name\":\"${NS_NAME_2}\",\"visibility\":\"PUBLIC\"}" "${BASE_URL}/v3/files/folders/${NS_ID}")
  [[ "${NS_VIS_HTTP}" =~ ^2 ]] || api_fail "PUT folder visibility=PUBLIC → HTTP ${NS_VIS_HTTP}"

  CALL_NUM=$((CALL_NUM+1))
  echo "  API call ${CALL_NUM}: GET /v2/networkset/${NS_ID} (anon, set now PUBLIC) — public member only"
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
  echo "  API call ${CALL_NUM}: GET /v2/networkset/${NS_ID}?accesskey (anon) — key-unlocked members"
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
  echo "  API call ${CALL_NUM}: GET /v2/network/${V2_PRIV_UUID}?accesskey (anon) — key reaches the member"
  NS_NETKEY_HTTP=$(curl -s -o /dev/null -w "%{http_code}" "${BASE_URL}/v2/network/${V2_PRIV_UUID}/summary?accesskey=${NS_KEY}")
  [[ "${NS_NETKEY_HTTP}" == "200" ]] \
    || api_fail "a set's key must reach its member networks through their shortcuts → HTTP ${NS_NETKEY_HTTP}"
  api_pass "a network set access key still reaches member networks via same-owner shortcuts (#133/#137)"

  CALL_NUM=$((CALL_NUM+1))
  # The owner's 200 on a PRIVATE set is already asserted above; this checks the legacy-only fields, which
  # have no folder equivalent and must be reported as defaults rather than invented.
  echo "  API call ${CALL_NUM}: GET /v2/networkset/${NS_ID} (owner) — legacy field defaults"
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
  echo "  API call ${CALL_NUM}: GET /v2/networkset/${NS_ID}/accesskey (owner) — key returned"
  NS_AK=$(curl -s -w "\n%{http_code}" -u "${TEST_USER}:${TEST_PASS}" "${BASE_URL}/v2/networkset/${NS_ID}/accesskey")
  NS_AK_HTTP=$(echo "${NS_AK}" | tail -1); NS_AK_BODY=$(echo "${NS_AK}" | head -1)
  [[ "${NS_AK_HTTP}" == "200" ]] || api_fail "GET accesskey (owner) → HTTP ${NS_AK_HTTP}. Body: ${NS_AK_BODY:0:300}"
  echo "${NS_AK_BODY}" | grep -q "${NS_KEY}" || api_fail "GET accesskey did not return the key. Body: ${NS_AK_BODY:0:300}"
  api_pass "GET /v2/networkset/{id}/accesskey (owner) → 200, returns the folder's key"

  CALL_NUM=$((CALL_NUM+1))
  echo "  API call ${CALL_NUM}: GET /v2/networkset/${NS_ID}/accesskey (non-owner) — 401"
  NS_AK2_HTTP=$(curl -s -o /dev/null -w "%{http_code}" -u "${TEST_USER2}:${TEST_PASS2}" \
    "${BASE_URL}/v2/networkset/${NS_ID}/accesskey")
  [[ "${NS_AK2_HTTP}" == "401" ]] || api_fail "GET accesskey (non-owner) → HTTP ${NS_AK2_HTTP} (expected 401)"
  api_pass "GET /v2/networkset/{id}/accesskey (non-owner) → 401 (only the owner may read the key)"

  CALL_NUM=$((CALL_NUM+1))
  NS_MISSING_ID="99999999-9999-9999-9999-999999999999"
  echo "  API call ${CALL_NUM}: GET /v2/networkset/${NS_MISSING_ID}/accesskey — 404 not 401"
  NS_AK_MISS_HTTP=$(curl -s -o /dev/null -w "%{http_code}" -u "${TEST_USER}:${TEST_PASS}" \
    "${BASE_URL}/v2/networkset/${NS_MISSING_ID}/accesskey")
  [[ "${NS_AK_MISS_HTTP}" == "404" ]] || api_fail "GET accesskey (missing set) → HTTP ${NS_AK_MISS_HTTP} (expected 404)"
  api_pass "GET /v2/networkset/{id}/accesskey (missing set) → 404 (existence resolved before ownership)"

  # A GET must never mint a key as a side effect, which is what earlier releases did by calling
  # enableFolderAccessKey from the read path.
  CALL_NUM=$((CALL_NUM+1))
  echo "  API call ${CALL_NUM}: POST /v2/networkset (2nd set) — GET accesskey must not create a key"
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
  echo "  API call ${CALL_NUM}: GET /v2/user/${NS_OWNER_ID}/networksets (owner) — both sets"
  NSU_OWNER=$(curl -s -w "\n%{http_code}" -u "${TEST_USER}:${TEST_PASS}" "${BASE_URL}/v2/user/${NS_OWNER_ID}/networksets")
  NSU_OWNER_HTTP=$(echo "${NSU_OWNER}" | tail -1); NSU_OWNER_BODY=$(echo "${NSU_OWNER}" | head -1)
  [[ "${NSU_OWNER_HTTP}" == "200" ]] || api_fail "GET /v2/user/{id}/networksets → HTTP ${NSU_OWNER_HTTP}. Body: ${NSU_OWNER_BODY:0:400}"
  echo "${NSU_OWNER_BODY}" | grep -q "${NS_ID}" || api_fail "set ${NS_ID} missing from the user's list. Body: ${NSU_OWNER_BODY:0:500}"
  echo "${NSU_OWNER_BODY}" | grep -q "${NS_ID2}" || api_fail "set ${NS_ID2} missing from the user's list. Body: ${NSU_OWNER_BODY:0:500}"
  api_pass "GET /v2/user/{id}/networksets (owner) → 200, lists the user's folder-backed sets"

  CALL_NUM=$((CALL_NUM+1))
  echo "  API call ${CALL_NUM}: GET /v2/user/${NS_OWNER_ID}/networksets?summary=true — members omitted as []"
  NSU_SUM=$(curl -s -u "${TEST_USER}:${TEST_PASS}" "${BASE_URL}/v2/user/${NS_OWNER_ID}/networksets?summary=true")
  echo "${NSU_SUM}" | grep -q "${NS_ID}" || api_fail "summary list missing set ${NS_ID}. Body: ${NSU_SUM:0:400}"
  echo "${NSU_SUM}" | grep -q "${V2_PUB_UUID}" && api_fail "summary=true must omit member network ids. Body: ${NSU_SUM:0:400}"
  # Present-but-empty, not absent: NetworkSet pre-allocates the collection.
  echo "${NSU_SUM}" | grep -qE '"networks"[[:space:]]*:[[:space:]]*\[\]' \
    || api_fail "summary=true should render \"networks\":[] rather than omitting the field. Body: ${NSU_SUM:0:400}"
  api_pass "GET /v2/user/{id}/networksets?summary=true → set headers with \"networks\":[]"

  CALL_NUM=$((CALL_NUM+1))
  echo "  API call ${CALL_NUM}: GET /v2/user/${NS_OWNER_ID}/networksets?showcase=true — documented no-op"
  NSU_SHOW=$(curl -s -u "${TEST_USER}:${TEST_PASS}" "${BASE_URL}/v2/user/${NS_OWNER_ID}/networksets?showcase=true")
  # Folders have no showcase flag, so the parameter cannot filter; it must not silently empty the list.
  echo "${NSU_SHOW}" | grep -q "${NS_ID}" \
    || api_fail "showcase=true is a documented no-op and must not filter the list. Body: ${NSU_SHOW:0:400}"
  api_pass "GET /v2/user/{id}/networksets?showcase=true → no-op filter (folders have no showcase flag)"

  # networkSetCount must agree with the unpaged list length, or an account page contradicts itself.
  CALL_NUM=$((CALL_NUM+1))
  echo "  API call ${CALL_NUM}: GET /v2/user/${NS_OWNER_ID}/networkcount — networkSetCount agrees with the list"
  NS_CNT_BODY=$(curl -s -u "${TEST_USER}:${TEST_PASS}" "${BASE_URL}/v2/user/${NS_OWNER_ID}/networkcount")
  NS_SET_COUNT=$(echo "${NS_CNT_BODY}" | grep -oE '"networkSetCount"[[:space:]]*:[[:space:]]*[0-9]+' | grep -oE '[0-9]+$' || true)
  [[ -n "${NS_SET_COUNT}" ]] || api_fail "networkcount response has no networkSetCount. Body: ${NS_CNT_BODY:0:300}"
  NS_LIST_LEN=$(curl -s -u "${TEST_USER}:${TEST_PASS}" "${BASE_URL}/v2/user/${NS_OWNER_ID}/networksets?summary=true" \
    | grep -oE '"externalId"' | wc -l | tr -d '[:space:]')
  [[ "${NS_SET_COUNT}" == "${NS_LIST_LEN}" ]] \
    || api_fail "networkSetCount (${NS_SET_COUNT}) must equal the networksets list length (${NS_LIST_LEN})"
  api_pass "networkSetCount (${NS_SET_COUNT}) equals the unpaged /networksets length"

  # ── 7b) Only home-root folders are network sets (issue #164) ───────────────────────────────────
  # A network set is always created at the owner's home root, so a folder nested inside another
  # folder is not a set: it must be absent from /v2/user/{id}/networksets and from networkSetCount.
  # The any-depth listing is deliberately NOT scoped this way. GET /v3/files/folders was removed by
  # #163, so that guarantee is now asserted in integration-mcp-test.sh via get_folder mode=list;
  # /v3/files/folders/home/list is home-root only and cannot stand in for it here.
  CALL_NUM=$((CALL_NUM+1))
  echo "  API call ${CALL_NUM}: POST /v3/files/folders/ — sub-folder nested under set ${NS_ID}"
  NS_SUB_RESP=$(curl -s -w "\n%{http_code}" -X POST -u "${TEST_USER}:${TEST_PASS}" \
    -H "Content-Type: application/json" \
    -d "{\"name\":\"NS sub-folder\",\"parent\":\"${NS_ID}\"}" \
    "${BASE_URL}/v3/files/folders/")
  NS_SUB_HTTP=$(echo "${NS_SUB_RESP}" | tail -1); NS_SUB_BODY=$(echo "${NS_SUB_RESP}" | head -1)
  [[ "${NS_SUB_HTTP}" == "201" ]] \
    || api_fail "create nested sub-folder → HTTP ${NS_SUB_HTTP} (expected 201). Body: ${NS_SUB_BODY:0:300}"
  NS_SUB_ID=$(echo "${NS_SUB_BODY}" | grep -oiE '[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}' | head -1 || true)
  [[ -n "${NS_SUB_ID}" ]] || api_fail "no uuid in sub-folder create response. Body: ${NS_SUB_BODY:0:300}"
  api_pass "sub-folder ${NS_SUB_ID} created under root set ${NS_ID}"

  CALL_NUM=$((CALL_NUM+1))
  echo "  API call ${CALL_NUM}: GET /v2/user/${NS_OWNER_ID}/networksets — nested folder must NOT be listed"
  NSU_NESTED=$(curl -s -u "${TEST_USER}:${TEST_PASS}" "${BASE_URL}/v2/user/${NS_OWNER_ID}/networksets?summary=true")
  echo "${NSU_NESTED}" | grep -q "${NS_ID}" \
    || api_fail "root set ${NS_ID} disappeared from the list. Body: ${NSU_NESTED:0:500}"
  echo "${NSU_NESTED}" | grep -q "${NS_SUB_ID}" \
    && api_fail "nested folder ${NS_SUB_ID} must not be listed as a network set. Body: ${NSU_NESTED:0:500}"
  api_pass "GET /v2/user/{id}/networksets lists the root set and omits the nested folder (issue #164)"

  # The count is scoped the same way, or the account page contradicts the list it describes.
  CALL_NUM=$((CALL_NUM+1))
  echo "  API call ${CALL_NUM}: GET /v2/user/${NS_OWNER_ID}/networkcount — count still matches the list"
  NS_SET_COUNT2=$(curl -s -u "${TEST_USER}:${TEST_PASS}" "${BASE_URL}/v2/user/${NS_OWNER_ID}/networkcount" \
    | grep -oE '"networkSetCount"[[:space:]]*:[[:space:]]*[0-9]+' | grep -oE '[0-9]+$' || true)
  NS_LIST_LEN2=$(echo "${NSU_NESTED}" | grep -oE '"externalId"' | wc -l | tr -d '[:space:]')
  [[ "${NS_SET_COUNT2}" == "${NS_LIST_LEN2}" ]] \
    || api_fail "with a nested folder present: networkSetCount (${NS_SET_COUNT2}) != list length (${NS_LIST_LEN2})"
  api_pass "networkSetCount (${NS_SET_COUNT2}) still equals the /networksets length with a nested folder present"

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
  echo "  API call ${CALL_NUM}: DELETE /v2/networkset/${NS_ID}/members [private] — expect 204"
  NS_DELM_HTTP=$(curl -s -o /dev/null -w "%{http_code}" -X DELETE -u "${TEST_USER}:${TEST_PASS}" \
    -H "Content-Type: application/json" -d "[\"${V2_PRIV_UUID}\"]" "${BASE_URL}/v2/networkset/${NS_ID}/members")
  [[ "${NS_DELM_HTTP}" == "204" ]] || api_fail "DELETE members → HTTP ${NS_DELM_HTTP} (expected 204)"

  # The shortcut must be PHYSICALLY gone, not soft-deleted: a soft delete would leave a trash entry for
  # every network a client removed from a set.
  NS_SC_ROWS=$(psql_ndex "SELECT count(*) FROM shortcut WHERE \\\"UUID\\\"='${NS_SC_PRIV}';")
  [[ "${NS_SC_ROWS}" == "0" ]] \
    || api_fail "member shortcut row survives (count=${NS_SC_ROWS}); is_deleted=true means the soft path ran"
  CALL_NUM=$((CALL_NUM+1))
  echo "  API call ${CALL_NUM}: GET /v3/files/shortcuts/${NS_SC_PRIV} — 404"
  NS_SC_HTTP=$(curl -s -o /dev/null -w "%{http_code}" -u "${TEST_USER}:${TEST_PASS}" \
    "${BASE_URL}/v3/files/shortcuts/${NS_SC_PRIV}")
  [[ "${NS_SC_HTTP}" == "404" ]] || api_fail "removed shortcut still resolves → HTTP ${NS_SC_HTTP} (expected 404)"
  CALL_NUM=$((CALL_NUM+1))
  echo "  API call ${CALL_NUM}: GET /v3/files/trash — removed shortcut must NOT be there"
  NS_TRASH=$(curl -s -u "${TEST_USER}:${TEST_PASS}" "${BASE_URL}/v3/files/trash")
  echo "${NS_TRASH}" | grep -q "${NS_SC_PRIV}" \
    && api_fail "removing a member left a trash entry; the shortcut must be deleted permanently"
  # The member NETWORK itself is untouched — only the reference was removed.
  CALL_NUM=$((CALL_NUM+1))
  echo "  API call ${CALL_NUM}: GET /v2/network/${V2_PRIV_UUID}/summary — network survives"
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
  echo "  API call ${CALL_NUM}: POST /v3/batch/networks/move ${V2_PRIV_UUID} into the set"
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
  echo "  API call ${CALL_NUM}: DELETE /v2/networkset/${NS_ID}/members [real child] — reparented to null"
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
  echo "  API call ${CALL_NUM}: GET /v3/users/${NS_OWNER_ID}/home — moved network is at home root"
  NS_HOME=$(curl -s -u "${TEST_USER}:${TEST_PASS}" "${BASE_URL}/v3/users/${NS_OWNER_ID}/home")
  echo "${NS_HOME}" | grep -q "${V2_PRIV_UUID}" \
    || api_fail "the moved network should now appear at home root. Body: ${NS_HOME:0:500}"
  api_pass "DELETE members moves a real child network to home root instead of deleting it"

  # ── 10) DELETE /{id} → trashes the folder and its remaining contents ────────────────────────────
  NS_SC_REMAINING=$(psql_ndex "SELECT \\\"UUID\\\" FROM shortcut WHERE parent='${NS_ID}' AND is_deleted=false;")
  CALL_NUM=$((CALL_NUM+1))
  echo "  API call ${CALL_NUM}: DELETE /v2/networkset/${NS_ID} — expect 204"
  NS_DEL_HTTP=$(curl -s -o /dev/null -w "%{http_code}" -X DELETE -u "${TEST_USER}:${TEST_PASS}" \
    "${BASE_URL}/v2/networkset/${NS_ID}")
  [[ "${NS_DEL_HTTP}" == "204" ]] || api_fail "DELETE /v2/networkset/{id} → HTTP ${NS_DEL_HTTP} (expected 204)"

  CALL_NUM=$((CALL_NUM+1))
  echo "  API call ${CALL_NUM}: GET /v2/networkset/${NS_ID} after delete — 404"
  NS_GONE_HTTP=$(curl -s -o /dev/null -w "%{http_code}" -u "${TEST_USER}:${TEST_PASS}" "${BASE_URL}/v2/networkset/${NS_ID}")
  [[ "${NS_GONE_HTTP}" == "404" ]] || api_fail "deleted set still readable → HTTP ${NS_GONE_HTTP} (expected 404)"

  # A trashed set must stay unreadable even to a valid access key: the folder read applies no
  # is_deleted filter and neither does key validation, so existence has to be resolved first.
  CALL_NUM=$((CALL_NUM+1))
  echo "  API call ${CALL_NUM}: GET /v2/networkset/${NS_ID}?accesskey after delete — 404 not 200"
  NS_GONE_KEY_HTTP=$(curl -s -o /dev/null -w "%{http_code}" "${BASE_URL}/v2/networkset/${NS_ID}?accesskey=${NS_KEY}")
  [[ "${NS_GONE_KEY_HTTP}" == "404" ]] \
    || api_fail "a trashed set is readable with its access key → HTTP ${NS_GONE_KEY_HTTP} (expected 404)"
  api_pass "a deleted (trashed) set returns 404, including to a holder of its access key"

  CALL_NUM=$((CALL_NUM+1))
  echo "  API call ${CALL_NUM}: GET /v3/files/folders/${NS_ID} after delete — 404"
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

