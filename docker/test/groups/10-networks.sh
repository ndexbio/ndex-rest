#!/usr/bin/env bash
# Networks: account setup, CX1 and CX2 upload, retrieval, and who may read what.
#
# Sourced by integration-test.sh, never executed on its own: the whole suite shares one shell, so
# this group sees the harness helpers and the fixtures earlier groups created, and leaves its own
# behind for the groups that follow.

step "Creating test user"
CALL_NUM=$((CALL_NUM+1))
echo "  API call ${CALL_NUM}: POST /v2/user"

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
echo "  API call ${CALL_NUM}: GET /user/authenticate"

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
  echo "  API call ${CALL_NUM}: POST /v2/network?visibility=${VISIBILITY}  [${NETWORK_LABEL}]"

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
  echo "  API call ${CALL_NUM}: GET /v3/networks/${UUID}"

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
  NETWORK_LABEL="$(basename "${CX2_FILE}")"
  # invalid-*.cx2 fixtures deliberately fail CX2 validation; they are uploaded on their own in the
  # dedicated #161 step further down, not as part of this happy-path batch. Skipped before
  # CX2_INDEX increments so the index the PRIVATE selection below depends on does not shift.
  [[ "${NETWORK_LABEL}" == invalid-* ]] && continue
  CX2_INDEX=$((CX2_INDEX + 1))

  if [[ ${CX2_INDEX} -eq 3 ]]; then
    VISIBILITY="PRIVATE"
  else
    VISIBILITY="PUBLIC"
  fi

  CALL_NUM=$((CALL_NUM+1))
  echo "  API call ${CALL_NUM}: POST /v3/networks?visibility=${VISIBILITY}  [${NETWORK_LABEL}]"

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
  echo "  API call ${CALL_NUM}: GET /v3/networks/${UUID}"

  V3_HTTP=$(curl -s -o /dev/null -w "%{http_code}" \
    -u "${TEST_USER}:${TEST_PASS}" \
    "${BASE_URL}/v3/networks/${UUID}")

  if [[ "${V3_HTTP}" == "200" ]]; then
    api_pass "GET /v3/networks/${UUID} → 200 OK (CX2 stream retrieved)"
  else
    api_fail "GET /v3/networks/${UUID} → HTTP ${V3_HTTP} (expected 200)"
  fi
done

# ── STEP: Large multipart/form-data upload (regression for HTTP 413) ─────────
#
# Reproduces the scenario from issue #152: a CX2 network larger than Tomcat's
# default maxPostSize (2 MB) must be accepted when uploaded as multipart/form-data
# via POST /v3/networks.  The fix is <multipart-config> in web.xml; without it
# Tomcat rejects the request with HTTP 413.
#
# The fixture large-upload-test.cx2 (~2.7 MB) is intentionally larger than the
# default 2 MB limit so this test would fail on an unfixed server.

LARGE_FIXTURE="${FIXTURES_DIR}/large/large-upload-test.cx2"

step "Large multipart upload regression: POST /v3/networks (multipart/form-data, ~2.7 MB)"

CALL_NUM=$((CALL_NUM+1))
echo "  API call ${CALL_NUM}: POST /v3/networks (multipart, $(wc -c < "${LARGE_FIXTURE}") bytes)"

LARGE_UPLOAD_RESPONSE=$(curl -s -w "\n%{http_code}" -X POST \
  -u "${TEST_USER}:${TEST_PASS}" \
  -F "CXNetworkStream=@${LARGE_FIXTURE};type=application/json" \
  "${BASE_URL}/v3/networks?visibility=PUBLIC")
LARGE_UPLOAD_HTTP=$(echo "${LARGE_UPLOAD_RESPONSE}" | tail -1)
LARGE_UPLOAD_BODY=$(echo "${LARGE_UPLOAD_RESPONSE}" | head -1)

if [[ "${LARGE_UPLOAD_HTTP}" == "201" ]]; then
  LARGE_UUID=$(echo "${LARGE_UPLOAD_BODY}" | grep -o '"uuid":"[^"]*"' | head -1 | cut -d'"' -f4)
  api_pass "POST /v3/networks (multipart ~2.7 MB) → 201 Created (UUID: ${LARGE_UUID})"
else
  api_fail "POST /v3/networks (multipart ~2.7 MB) → HTTP ${LARGE_UPLOAD_HTTP} (expected 201, regression for HTTP 413). Body: ${LARGE_UPLOAD_BODY:0:300}"
fi

echo "  Polling GET /v3/networks/${LARGE_UUID}/summary until completed:true..."
ELAPSED=0
while true; do
  SUMMARY_BODY=$(curl -s -u "${TEST_USER}:${TEST_PASS}" "${BASE_URL}/v3/networks/${LARGE_UUID}/summary")
  if echo "${SUMMARY_BODY}" | grep -q '"completed":true'; then
    echo "  large upload network ${LARGE_UUID} — completed"
    break
  fi
  if [[ ${ELAPSED} -ge ${LOAD_TIMEOUT} ]]; then
    api_fail "large upload network ${LARGE_UUID} did not complete within ${LOAD_TIMEOUT}s. Last: ${SUMMARY_BODY:0:300}"
  fi
  echo -e "  ${CYAN}Waiting for large upload network... (${ELAPSED}s)${NC}"
  sleep 5
  ELAPSED=$((ELAPSED + 5))
done

CALL_NUM=$((CALL_NUM+1))
echo "  API call ${CALL_NUM}: GET /v3/networks/${LARGE_UUID}"
LARGE_GET_HTTP=$(curl -s -o /dev/null -w "%{http_code}" \
  -u "${TEST_USER}:${TEST_PASS}" \
  "${BASE_URL}/v3/networks/${LARGE_UUID}")
if [[ "${LARGE_GET_HTTP}" == "200" ]]; then
  api_pass "GET /v3/networks/${LARGE_UUID} → 200 OK (large multipart-uploaded network retrieved)"
else
  api_fail "GET /v3/networks/${LARGE_UUID} → HTTP ${LARGE_GET_HTTP} (expected 200)"
fi

# ── STEP: Private network — anonymous access denied ──────────────────────────

step "Asserting anonymous clients cannot retrieve private networks"
echo "  Private v2 network (WP5434): ${V2_PRIV_UUID}"
echo "  Private v3 network (ChEMBL):  ${V3_PRIV_UUID}"

CALL_NUM=$((CALL_NUM+1))
echo "  API call ${CALL_NUM}: GET /v3/networks/${V2_PRIV_UUID} (no auth, expect 401)"
ANON_HTTP=$(curl -s -o /dev/null -w "%{http_code}" "${BASE_URL}/v3/networks/${V2_PRIV_UUID}")
if [[ "${ANON_HTTP}" == "401" ]]; then
  api_pass "GET /v3/networks/${V2_PRIV_UUID} (anon) → 401 Unauthorized (private v2 network blocked)"
else
  api_fail "GET /v3/networks/${V2_PRIV_UUID} (anon) → HTTP ${ANON_HTTP} (expected 401 for private network)"
fi

CALL_NUM=$((CALL_NUM+1))
echo "  API call ${CALL_NUM}: GET /v3/networks/${V3_PRIV_UUID} (no auth, expect 401)"
ANON_HTTP=$(curl -s -o /dev/null -w "%{http_code}" "${BASE_URL}/v3/networks/${V3_PRIV_UUID}")
if [[ "${ANON_HTTP}" == "401" ]]; then
  api_pass "GET /v3/networks/${V3_PRIV_UUID} (anon) → 401 Unauthorized (private v3 network blocked)"
else
  api_fail "GET /v3/networks/${V3_PRIV_UUID} (anon) → HTTP ${ANON_HTTP} (expected 401 for private network)"
fi

CALL_NUM=$((CALL_NUM+1))
echo "  API call ${CALL_NUM}: GET /v2/network/${V2_PRIV_UUID}/summary (no auth, expect 401)"
ANON_HTTP=$(curl -s -o /dev/null -w "%{http_code}" "${BASE_URL}/v2/network/${V2_PRIV_UUID}/summary")
if [[ "${ANON_HTTP}" == "401" ]]; then
  api_pass "GET /v2/network/${V2_PRIV_UUID}/summary (anon) → 401 Unauthorized (private v2 summary blocked)"
else
  api_fail "GET /v2/network/${V2_PRIV_UUID}/summary (anon) → HTTP ${ANON_HTTP} (expected 401 for private network)"
fi

CALL_NUM=$((CALL_NUM+1))
echo "  API call ${CALL_NUM}: GET /v3/networks/${V3_PRIV_UUID}/summary (no auth, expect 401)"
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
echo "  API call ${CALL_NUM}: GET /v3/networks/${V2_PUB_UUID} (no auth, expect 200)"
ANON_HTTP=$(curl -s -o /dev/null -w "%{http_code}" "${BASE_URL}/v3/networks/${V2_PUB_UUID}")
if [[ "${ANON_HTTP}" == "200" ]]; then
  api_pass "GET /v3/networks/${V2_PUB_UUID} (anon) → 200 OK (public v2 network accessible)"
else
  api_fail "GET /v3/networks/${V2_PUB_UUID} (anon) → HTTP ${ANON_HTTP} (expected 200 for public network)"
fi

CALL_NUM=$((CALL_NUM+1))
echo "  API call ${CALL_NUM}: GET /v3/networks/${V3_PUB_UUID} (no auth, expect 200)"
ANON_HTTP=$(curl -s -o /dev/null -w "%{http_code}" "${BASE_URL}/v3/networks/${V3_PUB_UUID}")
if [[ "${ANON_HTTP}" == "200" ]]; then
  api_pass "GET /v3/networks/${V3_PUB_UUID} (anon) → 200 OK (public v3 network accessible)"
else
  api_fail "GET /v3/networks/${V3_PUB_UUID} (anon) → HTTP ${ANON_HTTP} (expected 200 for public network)"
fi

# ── STEP: Private network — authenticated owner access allowed ───────────────

step "Asserting authenticated owner can retrieve their private networks"

CALL_NUM=$((CALL_NUM+1))
echo "  API call ${CALL_NUM}: GET /v3/networks/${V2_PRIV_UUID} (auth, expect 200)"
AUTH_HTTP=$(curl -s -o /dev/null -w "%{http_code}" \
  -u "${TEST_USER}:${TEST_PASS}" \
  "${BASE_URL}/v3/networks/${V2_PRIV_UUID}")
if [[ "${AUTH_HTTP}" == "200" ]]; then
  api_pass "GET /v3/networks/${V2_PRIV_UUID} (auth) → 200 OK (owner can retrieve private v2 network)"
else
  api_fail "GET /v3/networks/${V2_PRIV_UUID} (auth) → HTTP ${AUTH_HTTP} (expected 200 for owner)"
fi

CALL_NUM=$((CALL_NUM+1))
echo "  API call ${CALL_NUM}: GET /v3/networks/${V3_PRIV_UUID} (auth, expect 200)"
AUTH_HTTP=$(curl -s -o /dev/null -w "%{http_code}" \
  -u "${TEST_USER}:${TEST_PASS}" \
  "${BASE_URL}/v3/networks/${V3_PRIV_UUID}")
if [[ "${AUTH_HTTP}" == "200" ]]; then
  api_pass "GET /v3/networks/${V3_PRIV_UUID} (auth) → 200 OK (owner can retrieve private v3 network)"
else
  api_fail "GET /v3/networks/${V3_PRIV_UUID} (auth) → HTTP ${AUTH_HTTP} (expected 200 for owner)"
fi

# ── STEP: v2 Solr search ─────────────────────────────────────────────────────


# ── STEP: a rename must reach the search index ───────────────────────────────
# Every mutation endpoint used to skip the re-index when the network's index level was NONE — which
# is the column default, so in practice a rename never reached Solr. The name changed in Postgres
# and search kept matching the old one. Result names are read from Postgres, so the stale document
# was invisible in the UI: the network looked correctly named right up until you searched for it.
#
# The attribute assertions are the other half. The rebuild picks CX1 or CX2 aspect files, and a
# fresh document is composed on every rebuild, so choosing the wrong one silently drops every
# attribute that only the CX2 path contributes. Asserting the name alone would let that ship.

# The endpoint overwrites name, description, version, visibility and properties from the payload, so
# a partial body would blank the rest — including the properties that carry `organism`. Round-tripping
# the current summary with only the name replaced is what the web UI does, and it keeps the attribute
# assertions meaningful.
rn_rename() { # uuid new-name -> exits the suite on failure
  local body payload code
  body=$(curl -s -u "${TEST_USER}:${TEST_PASS}" "${BASE_URL}/v2/network/$1/summary")
  payload=$(echo "${body}" | sed "s/\"name\":\"[^\"]*\"/\"name\":\"$2\"/")
  grep -q "$2" <<<"${payload}" \
    || api_fail "could not set the name in the round-tripped summary for $1. Body: ${body:0:300}"
  code=$(curl -s -o /dev/null -w "%{http_code}" -X PUT -u "${TEST_USER}:${TEST_PASS}" \
    -H "Content-Type: application/json" -d "${payload}" \
    "${BASE_URL}/v2/network/$1/summary")
  [[ "${code}" =~ ^2 ]] || api_fail "PUT /v2/network/{id}/summary (rename to $2) → HTTP ${code}"
}

step "Renaming a network re-indexes it, and its attributes survive"

RN_FIXTURE="${FIXTURES_DIR}/ChEMBL - All compounds vs yeast targets.cx2"
RN_A="RenameProbeAlpha${RANDOM}${RANDOM}"
RN_B="RenameProbeBeta${RANDOM}${RANDOM}"
RN_ORGANISM="Canis"          # from the fixture's networkAttributes, indexed only via the CX2 path

CALL_NUM=$((CALL_NUM+1))
echo "  API call ${CALL_NUM}: POST /v3/networks?visibility=PRIVATE  [rename probe, 469 nodes]"
RN_RESP=$(curl -s -w "\n%{http_code}" -X POST -u "${TEST_USER}:${TEST_PASS}" \
  -H "Content-Type: application/json" --data-binary "@${RN_FIXTURE}" \
  "${BASE_URL}/v3/networks?visibility=PRIVATE")
RN_HTTP=$(echo "${RN_RESP}" | tail -1); RN_BODY=$(echo "${RN_RESP}" | head -1)
[[ "${RN_HTTP}" == "201" ]] || api_fail "POST /v3/networks (rename probe) → HTTP ${RN_HTTP}. Body: ${RN_BODY:0:300}"
RN_UUID=$(echo "${RN_BODY}" | grep -o '"uuid":"[^"]*"' | head -1 | cut -d'"' -f4)
[[ -n "${RN_UUID}" ]] || api_fail "no uuid in rename-probe create body. Body: ${RN_BODY:0:300}"

RN_ELAPSED=0
while ! curl -s -u "${TEST_USER}:${TEST_PASS}" "${BASE_URL}/v3/networks/${RN_UUID}/summary" \
     | grep -q '"completed":true'; do
  [[ ${RN_ELAPSED} -ge ${LOAD_TIMEOUT} ]] && api_fail "rename probe ${RN_UUID} did not complete within ${LOAD_TIMEOUT}s"
  sleep 5; (( RN_ELAPSED += 5 )) || true
  echo "  Waiting for rename probe ${RN_UUID}... (${RN_ELAPSED}s)"
done

CALL_NUM=$((CALL_NUM+1))
echo "  API call ${CALL_NUM}: search files (organism indexed at upload)"
poll_files_until_present PRIVATE "${RN_ORGANISM}" "${RN_UUID}" "rename probe organism at upload"
api_pass "upload indexed the network's organism attribute"

CALL_NUM=$((CALL_NUM+1))
echo "  API call ${CALL_NUM}: PUT /v2/network/${RN_UUID}/summary (rename → ${RN_A})"
rn_rename "${RN_UUID}" "${RN_A}"

CALL_NUM=$((CALL_NUM+1))
echo "  API call ${CALL_NUM}: search files (renamed network findable by its new name)"
poll_files_until_present PRIVATE "${RN_A}" "${RN_UUID}" "rename reached the index"
api_pass "rename reached the search index: the new name matches"

CALL_NUM=$((CALL_NUM+1))
echo "  API call ${CALL_NUM}: search files (organism still indexed after the rename)"
poll_files_until_present PRIVATE "${RN_ORGANISM}" "${RN_UUID}" "rename preserved organism"
api_pass "the re-index preserved the network's organism attribute"

CALL_NUM=$((CALL_NUM+1))
echo "  API call ${CALL_NUM}: ndex-nfs holds exactly one document for the renamed network"
RN_COUNT=$(docker exec "${CONTAINER_NAME}" bash -c \
  "curl -s 'http://localhost:8983/solr/ndex-nfs/select?q=uuid:${RN_UUID}&rows=0&wt=json'" 2>/dev/null \
  | grep -oE '"numFound":[0-9]+' | grep -oE '[0-9]+$' || true)
[[ "${RN_COUNT}" == "1" ]] \
  || api_fail "rename duplicated the document: numFound=${RN_COUNT:-unset} for ${RN_UUID}"
api_pass "the rename replaced the document rather than adding a second copy"

CALL_NUM=$((CALL_NUM+1))
echo "  API call ${CALL_NUM}: PUT /v2/network/${RN_UUID}/summary (rename again → ${RN_B})"
rn_rename "${RN_UUID}" "${RN_B}"

CALL_NUM=$((CALL_NUM+1))
echo "  API call ${CALL_NUM}: search files (second name matches, first no longer does)"
poll_files_until_present PRIVATE "${RN_B}" "${RN_UUID}" "second rename indexing"
poll_files_until_absent  PRIVATE "${RN_A}" "${RN_UUID}" "first name stale doc"
api_pass "a second rename replaced the name again: the previous one stops matching"

# ── STEP: a visibility change must reach the index too ───────────────────────
# Same gate, different endpoint. /systemproperty writes a field the document actually stores, so a
# skipped re-index leaves the document claiming the old visibility.

step "Changing visibility via /systemproperty re-indexes the network"

CALL_NUM=$((CALL_NUM+1))
echo "  API call ${CALL_NUM}: PUT /v2/network/${RN_UUID}/systemproperty (visibility=PUBLIC)"
RN_VIS=$(curl -s -o /dev/null -w "%{http_code}" -X PUT -u "${TEST_USER}:${TEST_PASS}" \
  -H "Content-Type: application/json" -d '{"visibility":"PUBLIC"}' \
  "${BASE_URL}/v2/network/${RN_UUID}/systemproperty")
[[ "${RN_VIS}" =~ ^2 ]] || api_fail "PUT /v2/network/{id}/systemproperty → HTTP ${RN_VIS}"

CALL_NUM=$((CALL_NUM+1))
echo "  API call ${CALL_NUM}: search files visibility=PUBLIC (document carries the new visibility)"
poll_files_until_present PUBLIC "${RN_B}" "${RN_UUID}" "visibility change indexing"
poll_files_until_absent  PRIVATE "${RN_B}" "${RN_UUID}" "visibility change old partition"
api_pass "the visibility change reached the index: PUBLIC matches, PRIVATE no longer does"

# ── STEP: a partial summary body is a 400, not a 500 ─────────────────────────
# PUT /v2/network/{id}/summary overwrites name, description, version, visibility and properties from
# the payload. A body missing visibility used to reach an unguarded getVisibility().toString() in the
# DAO and surface as a server error, which tells a client nothing about what it got wrong.

step "A partial network summary is rejected as a client error"

CALL_NUM=$((CALL_NUM+1))
echo "  API call ${CALL_NUM}: PUT /v2/network/${RN_UUID}/summary (name only — no visibility)"
PB_CODE=$(curl -s -o /dev/null -w "%{http_code}" -X PUT -u "${TEST_USER}:${TEST_PASS}" \
  -H "Content-Type: application/json" -d '{"name":"PartialBodyProbe"}' \
  "${BASE_URL}/v2/network/${RN_UUID}/summary")
[[ "${PB_CODE}" == "400" ]] \
  || api_fail "a summary without visibility should be 400, got HTTP ${PB_CODE}"
api_pass "PUT /v2/network/{id}/summary without visibility → 400"

CALL_NUM=$((CALL_NUM+1))
echo "  API call ${CALL_NUM}: search files (the rejected request changed nothing)"
poll_files_until_present PUBLIC "${RN_B}" "${RN_UUID}" "name unchanged after the rejected partial body"
api_pass "the rejected request left the network's name untouched"

# ── STEP: updating a network's content re-indexes it ─────────────────────────
# The CX1 update path carried the same index-level gate, falling back to a node-core-only rebuild
# that skipped the file document. Replacing the content changes the network's name, so the index has
# to follow it.

step "Replacing a network's CX1 content re-indexes its new name"

CALL_NUM=$((CALL_NUM+1))
echo "  API call ${CALL_NUM}: POST /v2/network (CX1 upload for the content-update probe)"
CU_LOC=$(curl -s -X POST -u "${TEST_USER}:${TEST_PASS}" -H "Content-Type: application/json" \
  --data-binary "@${FIXTURES_DIR}/WP1984 - Integrated breast cancer pathway - Homo sapiens.cx" \
  "${BASE_URL}/v2/network")
CU_UUID=$(echo "${CU_LOC}" | awk -F/ '{print $NF}' | tr -d '\r\n')
[[ "${CU_UUID}" =~ ^[0-9a-f-]{36}$ ]] || api_fail "no uuid from CX1 upload. Response: ${CU_LOC:0:200}"

CU_ELAPSED=0
while ! curl -s -u "${TEST_USER}:${TEST_PASS}" "${BASE_URL}/v2/network/${CU_UUID}/summary" \
     | grep -q '"completed":true'; do
  [[ ${CU_ELAPSED} -ge ${LOAD_TIMEOUT} ]] && api_fail "content-update probe ${CU_UUID} did not complete within ${LOAD_TIMEOUT}s"
  sleep 5; (( CU_ELAPSED += 5 )) || true
  echo "  Waiting for content-update probe ${CU_UUID}... (${CU_ELAPSED}s)"
done

CALL_NUM=$((CALL_NUM+1))
echo "  API call ${CALL_NUM}: search files (original CX1 name indexed)"
poll_files_until_present PRIVATE "Integrated breast cancer pathway" "${CU_UUID}" "CX1 name at upload"
api_pass "the CX1 upload indexed the network under its original name"

CALL_NUM=$((CALL_NUM+1))
echo "  API call ${CALL_NUM}: PUT /v2/network/${CU_UUID} (replace content — different network name)"
CU_PUT=$(curl -s -o /dev/null -w "%{http_code}" -X PUT -u "${TEST_USER}:${TEST_PASS}" \
  -H "Content-Type: application/json" \
  --data-binary "@${FIXTURES_DIR}/WP4255 - Non-small cell lung cancer - Homo sapiens.cx" \
  "${BASE_URL}/v2/network/${CU_UUID}")
[[ "${CU_PUT}" =~ ^2 ]] || api_fail "PUT /v2/network/{id} (content update) → HTTP ${CU_PUT}"

CU_ELAPSED=0
while ! curl -s -u "${TEST_USER}:${TEST_PASS}" "${BASE_URL}/v2/network/${CU_UUID}/summary" \
     | grep -q '"completed":true'; do
  [[ ${CU_ELAPSED} -ge ${LOAD_TIMEOUT} ]] && api_fail "content update of ${CU_UUID} did not complete within ${LOAD_TIMEOUT}s"
  sleep 5; (( CU_ELAPSED += 5 )) || true
  echo "  Waiting for the content update to finish... (${CU_ELAPSED}s)"
done

CALL_NUM=$((CALL_NUM+1))
echo "  API call ${CALL_NUM}: search files (content update reached the index)"
# Only the positive direction is asserted here. These fixtures are real pathway names sharing most of
# their vocabulary ("cancer", "pathway"), and the query parser matches any term, so an "old name is
# gone" check would fail on the replacement's own words rather than on a stale document. That the
# rebuild replaces rather than duplicates is pinned by the rename step above, which uses tokens that
# exist nowhere else.
poll_files_until_present PRIVATE "Non-small cell lung cancer" "${CU_UUID}" "content update indexing"
api_pass "the content update re-indexed the network under its new name"

# ── STEP: updating aspects re-indexes the network ────────────────────────────
# PUT /{id}/aspects carried the same gate, and the networkAttributes aspect is where the name lives,
# so a skipped rebuild left the index describing the network as it was before the aspect update.

step "Updating the networkAttributes aspect re-indexes the network"

AU_NAME="AspectUpdateProbe${RANDOM}${RANDOM}"
CALL_NUM=$((CALL_NUM+1))
echo "  API call ${CALL_NUM}: PUT /v2/network/${CU_UUID}/aspects (networkAttributes → ${AU_NAME})"
AU_CODE=$(curl -s -o /dev/null -w "%{http_code}" -X PUT -u "${TEST_USER}:${TEST_PASS}" \
  -H "Content-Type: application/json" \
  -d "[{\"numberVerification\":[{\"longNumber\":281474976710655}]},
       {\"metaData\":[{\"name\":\"networkAttributes\",\"elementCount\":1,\"version\":\"1.0\"}]},
       {\"networkAttributes\":[{\"n\":\"name\",\"d\":\"string\",\"v\":\"${AU_NAME}\"}]},
       {\"status\":[{\"error\":\"\",\"success\":true}]}]" \
  "${BASE_URL}/v2/network/${CU_UUID}/aspects")
[[ "${AU_CODE}" =~ ^2 ]] || api_fail "PUT /v2/network/{id}/aspects → HTTP ${AU_CODE}"

CALL_NUM=$((CALL_NUM+1))
echo "  API call ${CALL_NUM}: search files (aspect update reached the index)"
poll_files_until_present PRIVATE "${AU_NAME}" "${CU_UUID}" "aspect update indexing"
api_pass "the aspect update re-indexed the network under its new name"
