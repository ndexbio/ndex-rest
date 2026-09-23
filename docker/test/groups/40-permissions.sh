#!/usr/bin/env bash
# Permissions: folder grants propagating to nested folders, networks and shortcuts.
#
# Sourced by integration-test.sh, never executed on its own: the whole suite shares one shell, so
# this group sees the harness helpers and the fixtures earlier groups created, and leaves its own
# behind for the groups that follow.

# ══════════════════════════════════════════════════════════════════════════════
# GROUP 1 (final): folder permission propagation — issue #165
#
# Folder READ/WRITE must resolve live up the ancestor chain. The ordering below is
# the one that used to fail: GRANT FIRST on an empty folder, then add children.
# Under the old copy-down implementation the grant snapshotted an empty tree and
# nothing added afterwards was ever covered.
#
# Five caller classes, because they take genuinely different code paths:
#   owner      ndextest   — owns the folder
#   WRITE      ndextest2  — inherited write
#   READ       ndextest3  — inherited read, must never gain write
#   no-grant   ndextest4  — authenticated, zero rows (the EXISTS-miss branch)
#   anonymous  no auth    — userId is null, a different SQL branch entirely
# ══════════════════════════════════════════════════════════════════════════════
if [[ -z "${REMOTE_NDEX_URL}" ]]; then

step "Folder permissions propagate to nested objects (#165)"

TEST_USER3="ndextest3"; TEST_PASS3="NDExTest3!"
TEST_USER4="ndextest4"; TEST_PASS4="NDExTest4!"
for U in "${TEST_USER3}:${TEST_PASS3}" "${TEST_USER4}:${TEST_PASS4}"; do
  UN="${U%%:*}"; UP="${U##*:}"
  curl -s -o /dev/null -X POST -u "${TEST_USER}:${TEST_PASS}" -H "Content-Type: application/json" \
    -d "{\"userName\":\"${UN}\",\"password\":\"${UP}\",\"emailAddress\":\"${UN}@ndex-integration.local\",\"firstName\":\"NDEx\",\"lastName\":\"Test\"}" \
    "${BASE_URL}/v2/user" || true
done
P_UID2=$(curl -s -u "${TEST_USER}:${TEST_PASS}" "${BASE_URL}/v2/user?username=${TEST_USER2}" | grep -oE '"externalId":"[0-9a-f-]{36}"' | cut -d'"' -f4 || true)
P_UID3=$(curl -s -u "${TEST_USER}:${TEST_PASS}" "${BASE_URL}/v2/user?username=${TEST_USER3}" | grep -oE '"externalId":"[0-9a-f-]{36}"' | cut -d'"' -f4 || true)
[[ -n "${P_UID2}" && -n "${P_UID3}" ]] || api_fail "#165: could not resolve grantee user ids"

# ── the folder, shared BEFORE it has any content ──────────────────────────────
P_FOLDER=$(curl -s -X POST -u "${TEST_USER}:${TEST_PASS}" -H "Content-Type: application/json" \
  -d '{"name":"perm-propagation-165"}' "${BASE_URL}/v3/files/folders/" \
  | grep -oiE '"uuid"[[:space:]]*:[[:space:]]*"[0-9a-f-]{36}"' | grep -oiE '[0-9a-f-]{36}' | head -1 || true)
[[ -n "${P_FOLDER}" ]] || api_fail "#165: could not create shared folder"

P_GRANT_HTTP=$(curl -s -o /dev/null -w "%{http_code}" -X POST -u "${TEST_USER}:${TEST_PASS}" \
  -H "Content-Type: application/json" \
  -d "{\"files\":{\"${P_FOLDER}\":\"FOLDER\"},\"members\":{\"${P_UID2}\":\"WRITE\",\"${P_UID3}\":\"READ\"}}" \
  "${BASE_URL}/v3/files/sharing/members")
[[ "${P_GRANT_HTTP}" =~ ^2 ]] || api_fail "#165: grant on empty folder failed with HTTP ${P_GRANT_HTTP}"
api_pass "granted WRITE+READ on an empty folder (children added afterwards)"

# ── children created AFTER the grant ──────────────────────────────────────────
P_NET_B=$(curl -s -X POST -u "${TEST_USER2}:${TEST_PASS2}" -H "Content-Type: application/json" \
  --data-binary "@${FIXTURES_DIR}/C. burnetii Network.cx2" \
  "${BASE_URL}/v3/networks?folderId=${P_FOLDER}" \
  | grep -oiE '"uuid"[[:space:]]*:[[:space:]]*"[0-9a-f-]{36}"' | grep -oiE '[0-9a-f-]{36}' | head -1 || true)
[[ -n "${P_NET_B}" ]] || api_fail "#165: WRITE grantee could not upload into the shared folder"
api_pass "WRITE grantee can place a network via ?folderId= (inherited write honored)"

P_MOVE_HTTP=$(curl -s -o /dev/null -w "%{http_code}" -X POST -u "${TEST_USER}:${TEST_PASS}" \
  -H "Content-Type: application/json" \
  -d "{\"targetFolder\":\"${P_FOLDER}\",\"networks\":[\"${V2_PRIV_UUID}\"]}" \
  "${BASE_URL}/v3/batch/networks/move")
[[ "${P_MOVE_HTTP}" =~ ^2 ]] || api_fail "#165: owner move into own folder returned ${P_MOVE_HTTP}"

P_SUB=$(curl -s -X POST -u "${TEST_USER}:${TEST_PASS}" -H "Content-Type: application/json" \
  -d "{\"name\":\"perm-sub-165\",\"parent\":\"${P_FOLDER}\"}" "${BASE_URL}/v3/files/folders/" \
  | grep -oiE '"uuid"[[:space:]]*:[[:space:]]*"[0-9a-f-]{36}"' | grep -oiE '[0-9a-f-]{36}' | head -1 || true)
[[ -n "${P_SUB}" ]] || api_fail "#165: could not create subfolder"

# Shortcut to a PRIVATE network of the folder owner that lives OUTSIDE the shared
# subtree. It is reachable only through the same-owner shortcut seed, so this cell
# fails if that rule is missing.
P_SC=$(curl -s -X POST -u "${TEST_USER}:${TEST_PASS}" -H "Content-Type: application/json" \
  -d "{\"name\":\"perm-sc-165\",\"parent\":\"${P_FOLDER}\",\"target\":\"${V3_PRIV_UUID}\",\"targetType\":\"NETWORK\"}" \
  "${BASE_URL}/v3/files/shortcuts/" \
  | grep -oiE '"uuid"[[:space:]]*:[[:space:]]*"[0-9a-f-]{36}"' | grep -oiE '[0-9a-f-]{36}' | head -1 || true)
[[ -n "${P_SC}" ]] || api_fail "#165: could not create shortcut in shared folder"
api_pass "children created after the grant: network, subfolder, shortcut"

# ── helpers ───────────────────────────────────────────────────────────────────
p_code() { # auth(or empty) method url
  if [[ -z "$1" ]]; then curl -s -o /dev/null -w "%{http_code}" -X "$2" "$3"
  else curl -s -o /dev/null -w "%{http_code}" -u "$1" -X "$2" "$3"; fi
}
p_listed() { # auth(or empty) uuid -> yes|no
  local body
  if [[ -z "$1" ]]; then body=$(curl -s "${BASE_URL}/v3/files/folders/${P_FOLDER}/list")
  else body=$(curl -s -u "$1" "${BASE_URL}/v3/files/folders/${P_FOLDER}/list"); fi
  if grep -q "$2" <<<"${body}"; then echo yes; else echo no; fi
}
p_expect() { # label actual expected
  [[ "$2" == "$3" ]] || api_fail "#165: $1 — got '$2', expected '$3'"
  api_pass "$1 = $3"
}

A_AUTH="${TEST_USER}:${TEST_PASS}"
B_AUTH="${TEST_USER2}:${TEST_PASS2}"
C_AUTH="${TEST_USER3}:${TEST_PASS3}"
D_AUTH="${TEST_USER4}:${TEST_PASS4}"

# ── the headline assertion: no re-grant anywhere ──────────────────────────────
for PAIR in "owner:${A_AUTH}" "write:${B_AUTH}" "read:${C_AUTH}"; do
  CLS="${PAIR%%:*}"; AUTH="${PAIR#*:}"
  p_expect "${CLS} sees the WRITE-grantee's network in /list" "$(p_listed "${AUTH}" "${P_NET_B}")" yes
  p_expect "${CLS} sees the owner's moved network in /list"   "$(p_listed "${AUTH}" "${V2_PRIV_UUID}")" yes
  p_expect "${CLS} sees the subfolder in /list"               "$(p_listed "${AUTH}" "${P_SUB}")" yes
  p_expect "${CLS} sees the shortcut in /list"                "$(p_listed "${AUTH}" "${P_SC}")" yes
  p_expect "${CLS} GET nested network"        "$(p_code "${AUTH}" GET "${BASE_URL}/v3/networks/${P_NET_B}")" 200
  p_expect "${CLS} GET nested network summary" "$(p_code "${AUTH}" GET "${BASE_URL}/v3/networks/${P_NET_B}/summary")" 200
  p_expect "${CLS} GET subfolder"             "$(p_code "${AUTH}" GET "${BASE_URL}/v3/files/folders/${P_SUB}")" 200
  p_expect "${CLS} GET shortcut"              "$(p_code "${AUTH}" GET "${BASE_URL}/v3/files/shortcuts/${P_SC}")" 200
  p_expect "${CLS} GET shortcut target via same-owner seed" \
      "$(p_code "${AUTH}" GET "${BASE_URL}/v3/networks/${V3_PRIV_UUID}")" 200
done

# the folder owner must see the collaborator's contribution — the reciprocal case
p_expect "owner can read the WRITE-grantee's network" "$(p_code "${A_AUTH}" GET "${BASE_URL}/v3/networks/${P_NET_B}")" 200

# ── negative classes: no grant, and anonymous ─────────────────────────────────
for PAIR in "no-grant:${D_AUTH}" "anonymous:"; do
  CLS="${PAIR%%:*}"; AUTH="${PAIR#*:}"
  p_expect "${CLS} does NOT see the nested network in /list" "$(p_listed "${AUTH}" "${P_NET_B}")" no
  p_expect "${CLS} does NOT see the subfolder in /list"      "$(p_listed "${AUTH}" "${P_SUB}")" no
  p_expect "${CLS} GET nested network denied" "$(p_code "${AUTH}" GET "${BASE_URL}/v3/networks/${P_NET_B}")" 401
  p_expect "${CLS} GET subfolder denied"      "$(p_code "${AUTH}" GET "${BASE_URL}/v3/files/folders/${P_SUB}")" 401
done

# ── /list and /count must agree ───────────────────────────────────────────────
for PAIR in "owner:${A_AUTH}" "write:${B_AUTH}" "read:${C_AUTH}"; do
  CLS="${PAIR%%:*}"; AUTH="${PAIR#*:}"
  CNT=$(curl -s -u "${AUTH}" "${BASE_URL}/v3/files/folders/${P_FOLDER}/count")
  CNT_N=$(grep -oE '"network"[[:space:]]*:[[:space:]]*[0-9]+' <<<"${CNT}" | grep -oE '[0-9]+$' || true)
  CNT_F=$(grep -oE '"folder"[[:space:]]*:[[:space:]]*[0-9]+' <<<"${CNT}" | grep -oE '[0-9]+$' || true)
  CNT_S=$(grep -oE '"shortcut"[[:space:]]*:[[:space:]]*[0-9]+' <<<"${CNT}" | grep -oE '[0-9]+$' || true)
  p_expect "${CLS} /count networks agrees with /list"  "${CNT_N}" 2
  p_expect "${CLS} /count folders agrees with /list"   "${CNT_F}" 1
  p_expect "${CLS} /count shortcuts agrees with /list" "${CNT_S}" 1
done

# ── writes: only owner and WRITE ──────────────────────────────────────────────
p_expect "owner can PUT the nested network" \
  "$(curl -s -o /dev/null -w '%{http_code}' -X PUT -u "${A_AUTH}" -H 'Content-Type: application/json' \
     --data-binary "@${FIXTURES_DIR}/C. burnetii Network.cx2" "${BASE_URL}/v3/networks/${P_NET_B}")" 200
p_expect "READ grantee CANNOT PUT (read must never confer write)" \
  "$(curl -s -o /dev/null -w '%{http_code}' -X PUT -u "${C_AUTH}" -H 'Content-Type: application/json' \
     --data-binary "@${FIXTURES_DIR}/C. burnetii Network.cx2" "${BASE_URL}/v3/networks/${P_NET_B}")" 401

# ── placement authorization (the ?folderId= hole) ─────────────────────────────
p_expect "READ grantee CANNOT place a network into the shared folder" \
  "$(curl -s -o /dev/null -w '%{http_code}' -X POST -u "${C_AUTH}" -H 'Content-Type: application/json' \
     --data-binary "@${FIXTURES_DIR}/C. burnetii Network.cx2" "${BASE_URL}/v3/networks?folderId=${P_FOLDER}")" 401
p_expect "no-grant user CANNOT place a network into a stranger's folder" \
  "$(curl -s -o /dev/null -w '%{http_code}' -X POST -u "${D_AUTH}" -H 'Content-Type: application/json' \
     --data-binary "@${FIXTURES_DIR}/C. burnetii Network.cx2" "${BASE_URL}/v3/networks?folderId=${P_FOLDER}")" 401
p_expect "no-grant user CANNOT move networks into a stranger's folder" \
  "$(curl -s -o /dev/null -w '%{http_code}' -X POST -u "${D_AUTH}" -H 'Content-Type: application/json' \
     -d "{\"targetFolder\":\"${P_FOLDER}\",\"networks\":[\"${V3_PUB_UUID}\"]}" \
     "${BASE_URL}/v3/batch/networks/move")" 401

# Anonymous placement, not just under-privileged placement. The folder is authorized before
# the upload is stored, so assert the status AND that nothing was written — a 401 returned
# after the network row was created would still be a hole.
P_NETS_BEFORE=$(psql_ndex "SELECT count(*) FROM core.network WHERE parent='${P_FOLDER}'")
p_expect "ANONYMOUS caller CANNOT place a network into a folder" \
  "$(curl -s -o /dev/null -w '%{http_code}' -X POST -H 'Content-Type: application/json' \
     --data-binary "@${FIXTURES_DIR}/C. burnetii Network.cx2" "${BASE_URL}/v3/networks?folderId=${P_FOLDER}")" 401
p_expect "ANONYMOUS caller CANNOT move networks into a folder" \
  "$(curl -s -o /dev/null -w '%{http_code}' -X POST -H 'Content-Type: application/json' \
     -d "{\"targetFolder\":\"${P_FOLDER}\",\"networks\":[\"${V3_PUB_UUID}\"]}" \
     "${BASE_URL}/v3/batch/networks/move")" 401
P_NETS_AFTER=$(psql_ndex "SELECT count(*) FROM core.network WHERE parent='${P_FOLDER}'")
p_expect "the rejected anonymous placements created nothing" "${P_NETS_AFTER}" "${P_NETS_BEFORE}"

# ── sharing input validation ──────────────────────────────────────────────────
p_expect "granting an unsupported permission is rejected, not silently stored" \
  "$(curl -s -o /dev/null -w '%{http_code}' -X POST -u "${A_AUTH}" -H 'Content-Type: application/json' \
     -d "{\"files\":{\"${P_FOLDER}\":\"FOLDER\"},\"members\":{\"${P_UID3}\":\"ADMIN\"}}" \
     "${BASE_URL}/v3/files/sharing/members")" 400

# Sibling rejections on the same services, which share the failure mode above: throwing the JAX-RS
# BadRequestException instead of NDEx's own means the catch-all ExceptionMapper<Throwable> reports a
# 500 "Uncaught exception" rather than the 400 the caller should see.
p_expect "creating a folder with no body is a 400, not a 500" \
  "$(curl -s -o /dev/null -w '%{http_code}' -X POST -u "${A_AUTH}" -H 'Content-Type: application/json' \
     -d '{}' "${BASE_URL}/v3/files/folders/")" 400
p_expect "creating a folder with an empty name is a 400, not a 500" \
  "$(curl -s -o /dev/null -w '%{http_code}' -X POST -u "${A_AUTH}" -H 'Content-Type: application/json' \
     -d '{"name":"  "}' "${BASE_URL}/v3/files/folders/")" 400
p_expect "a restore request naming nothing is a 400, not a 500" \
  "$(curl -s -o /dev/null -w '%{http_code}' -X POST -u "${A_AUTH}" -H 'Content-Type: application/json' \
     -d '{}' "${BASE_URL}/v3/files/trash/restore")" 400

# ── cross-owner shortcut must grant nothing (the escalation guard) ────────────
# The same-owner seed lets a folder grant reach a network that is only *referenced*
# from the folder by one of its owner's shortcuts. The guard is that the shortcut's
# owner must equal the network's owner — otherwise a folder owner could hand out
# access to a network they do not own simply by dropping a shortcut to it.
#
# To exercise the guard the target must sit OUTSIDE the shared subtree and be owned
# by someone other than the shortcut's creator, and the probing user must genuinely
# HOLD a grant on the folder. Probing with a user who has no grant at all would
# return 401 whether or not the guard exists, and prove nothing.
P_FOREIGN=$(curl -s -X POST -u "${B_AUTH}" -H 'Content-Type: application/json' \
  --data-binary "@${FIXTURES_DIR}/C. burnetii Network.cx2" \
  "${BASE_URL}/v3/networks" \
  | grep -oiE '"uuid"[[:space:]]*:[[:space:]]*"[0-9a-f-]{36}"' | grep -oiE '[0-9a-f-]{36}' | head -1 || true)
[[ -n "${P_FOREIGN}" ]] || api_fail "#165: could not create the cross-owner fixture network"

# Wait out CX2 processing before probing. An in-flight upload answers 404, which would be
# indistinguishable here from "denied" and would make the guard assertions below meaningless.
P_WAIT=0
until curl -s -u "${B_AUTH}" "${BASE_URL}/v3/networks/${P_FOREIGN}/summary" | grep -q '"completed":true'; do
  P_WAIT=$((P_WAIT+2))
  [[ ${P_WAIT} -ge ${LOAD_TIMEOUT} ]] && api_fail "#165: cross-owner fixture did not finish loading in ${LOAD_TIMEOUT}s"
  sleep 2
done

# sanity: the READ grantee cannot reach it before any shortcut exists
p_expect "cross-owner fixture starts unreachable to the READ grantee" \
  "$(p_code "${C_AUTH}" GET "${BASE_URL}/v3/networks/${P_FOREIGN}")" 401

# Creating a shortcut requires read access to its target (validateShortcutTarget), so the folder
# owner must first be given read on ndextest2's network directly. That direct grant is deliberately
# NOT transitive: it lets the owner reference the network, and the cross-owner guard below is what
# must stop that reference from leaking it onward to the folder's grantees.
P_UID1=$(curl -s -u "${A_AUTH}" "${BASE_URL}/v2/user?username=${TEST_USER}" | grep -oE '"externalId":"[0-9a-f-]{36}"' | cut -d'"' -f4 || true)
[[ -n "${P_UID1}" ]] || api_fail "#165: could not resolve the folder owner's user id"
curl -s -o /dev/null -X POST -u "${B_AUTH}" -H 'Content-Type: application/json' \
  -d "{\"files\":{\"${P_FOREIGN}\":\"NETWORK\"},\"members\":{\"${P_UID1}\":\"READ\"}}" \
  "${BASE_URL}/v3/files/sharing/members" || true
p_expect "folder owner can read the foreign network after a DIRECT share" \
  "$(p_code "${A_AUTH}" GET "${BASE_URL}/v3/networks/${P_FOREIGN}")" 200

# the folder OWNER drops a shortcut to ndextest2's network into the shared folder
P_XSC=$(curl -s -X POST -u "${A_AUTH}" -H "Content-Type: application/json" \
  -d "{\"name\":\"perm-xowner-165\",\"parent\":\"${P_FOLDER}\",\"target\":\"${P_FOREIGN}\",\"targetType\":\"NETWORK\"}" \
  "${BASE_URL}/v3/files/shortcuts/" \
  | grep -oiE '"uuid"[[:space:]]*:[[:space:]]*"[0-9a-f-]{36}"' | grep -oiE '[0-9a-f-]{36}' | head -1 || true)
[[ -n "${P_XSC}" ]] || api_fail "#165: could not create the cross-owner shortcut"

p_expect "cross-owner shortcut does NOT grant the READ grantee access to its target" \
  "$(p_code "${C_AUTH}" GET "${BASE_URL}/v3/networks/${P_FOREIGN}")" 401
# control: the network is genuinely reachable by its own owner, so the 401 above is
# the guard doing its job rather than the fixture simply being broken
p_expect "cross-owner fixture is still reachable by the user who owns it" \
  "$(p_code "${B_AUTH}" GET "${BASE_URL}/v3/networks/${P_FOREIGN}")" 200

# ── DB evidence: one row covers the whole subtree, no copy-down ───────────────
P_FP_ROWS=$(psql_ndex "SELECT count(*) FROM core.folder_permission fp JOIN core.folder f ON f.\\\"UUID\\\"=fp.folder_id WHERE f.name IN ('perm-propagation-165','perm-sub-165')")
p_expect "grant stored on the named folder only (no copy-down to the subfolder)" "${P_FP_ROWS}" 2
P_UNM_ROWS=$(psql_ndex "SELECT count(*) FROM core.user_network_membership WHERE network_id='${P_NET_B}'")
p_expect "no membership rows fabricated for the nested network" "${P_UNM_ROWS}" 0

# ── search resolves folder permissions at query time ──────────────────────────
# Search has no traversal gate to authorize against — it queries Solr directly — so it used
# to answer from an access list copied onto each document at index time. That copy went
# stale the moment a folder was shared, moved into, or revoked. The index now stores no
# permissions at all: the ids a caller can reach are resolved from the database per query.
#
# Searching by UUID isolates exactly one document (uuid is a query field), so a yes/no here
# is a permission answer rather than a relevance or pagination artifact.
p_search() { # auth(or empty) uuid [visibility, or ANY to omit the parameter] -> yes|no
  local vis="${3:-PRIVATE}" q body
  # ANY sends no visibility, which is the request for everything the caller may see. It reaches the
  # permission filter alone, with no partition clause narrowing the result first.
  [[ "${vis}" == "ANY" ]] && q="" || q="visibility=${vis}&"
  if [[ -z "$1" ]]; then
    body=$(curl -s -X POST -H 'Content-Type: application/json' -d "{\"searchString\":\"$2\"}" \
      "${BASE_URL}/v3/search/files?${q}start=0&size=50" || true)
  else
    body=$(curl -s -X POST -u "$1" -H 'Content-Type: application/json' -d "{\"searchString\":\"$2\"}" \
      "${BASE_URL}/v3/search/files?${q}start=0&size=50" || true)
  fi
  if grep -q "$2" <<<"${body}"; then echo yes; else echo no; fi
}

# Wait for indexing ONCE, as someone who can already see the document. Everything asserted
# afterwards is a permission question rather than an indexing one: because reachability is
# recomputed on every query, a grantee must see it on the very next request — no re-share,
# no reindex, no convergence window.
p_wait_indexed() { # auth uuid label [visibility]
  local elapsed=0
  until [[ "$(p_search "$1" "$2" "${4:-PRIVATE}")" == yes ]]; do
    [[ ${elapsed} -ge ${LOAD_TIMEOUT} ]] && api_fail "#165 search: $3 ($2) never reached the ${4:-PRIVATE} partition within ${LOAD_TIMEOUT}s"
    sleep 3; (( elapsed += 3 )) || true
    echo "  Waiting for $3 to be indexed and visible under ${4:-PRIVATE}... (${elapsed}s)"
  done
}

# Waited for as its OWNER (ndextest2 uploaded it), whose match does not depend on parentUuid.
# That keeps "the document is indexed" separate from "folder propagation works" — the
# assertions immediately below are the ones that test propagation.
p_wait_indexed "${B_AUTH}" "${P_NET_B}" "network added to the shared folder"
api_pass "network added after the grant reached the index"

# A grantee finds folder-propagated content with no re-share and no reindex. The moved
# network additionally proves reindex-on-move: /v3/batch/networks/move must rewrite that
# document, or it stays findable under the folder it left and invisible in the new one.
for PAIR in "owner:${A_AUTH}" "write:${B_AUTH}" "read:${C_AUTH}"; do
  CLS="${PAIR%%:*}"; AUTH="${PAIR#*:}"
  p_expect "${CLS} FINDS the network added after the grant" "$(p_search "${AUTH}" "${P_NET_B}")" yes
  p_expect "${CLS} FINDS the network moved into the folder" "$(p_search "${AUTH}" "${V2_PRIV_UUID}")" yes
  p_expect "${CLS} FINDS the subfolder"                     "$(p_search "${AUTH}" "${P_SUB}")" yes
  p_expect "${CLS} FINDS the shortcut in the shared folder" "$(p_search "${AUTH}" "${P_SC}")" yes
done

# No-grant and anonymous callers find none of it.
for PAIR in "no-grant:${D_AUTH}" "anonymous:"; do
  CLS="${PAIR%%:*}"; AUTH="${PAIR#*:}"
  p_expect "${CLS} does NOT find the nested network" "$(p_search "${AUTH}" "${P_NET_B}")" no
  p_expect "${CLS} does NOT find the subfolder"      "$(p_search "${AUTH}" "${P_SUB}")" no
  p_expect "${CLS} does NOT find the shortcut"       "$(p_search "${AUTH}" "${P_SC}")" no
done

# Search, fetch-by-id and /list must give the SAME answer for the same shortcut and caller.
# Unifying that rule is the point of the change; asserting the three together means a
# divergence cannot slip through as three separately-passing checks.
for PAIR in "owner:${A_AUTH}" "write:${B_AUTH}" "read:${C_AUTH}"; do
  CLS="${PAIR%%:*}"; AUTH="${PAIR#*:}"
  p_expect "${CLS} shortcut: search agrees with /list" \
    "$(p_search "${AUTH}" "${P_SC}")" "$(p_listed "${AUTH}" "${P_SC}")"
  p_expect "${CLS} shortcut: fetch-by-id agrees too" \
    "$(p_code "${AUTH}" GET "${BASE_URL}/v3/files/shortcuts/${P_SC}")" 200
done
for PAIR in "no-grant:${D_AUTH}" "anonymous:"; do
  CLS="${PAIR%%:*}"; AUTH="${PAIR#*:}"
  p_expect "${CLS} shortcut: search agrees with /list (both no)" \
    "$(p_search "${AUTH}" "${P_SC}")" "$(p_listed "${AUTH}" "${P_SC}")"
  p_expect "${CLS} shortcut fetch-by-id denied" \
    "$(p_code "${AUTH}" GET "${BASE_URL}/v3/files/shortcuts/${P_SC}")" 401
done

# The same-owner shortcut seed makes a network findable that has no granted folder above
# it — its own parent is elsewhere in the tree, so no parentUuid clause can reach it.
p_wait_indexed "${A_AUTH}" "${V3_PRIV_UUID}" "same-owner shortcut target"
p_expect "READ grantee FINDS a network reachable only via a same-owner shortcut" \
  "$(p_search "${C_AUTH}" "${V3_PRIV_UUID}")" yes

# The cross-owner guard, in search form. The shortcut sits in a folder the READ grantee can
# reach, so only the same-owner guard can be keeping its target out of their results.
p_wait_indexed "${B_AUTH}" "${P_FOREIGN}" "cross-owner fixture"
p_expect "cross-owner shortcut does NOT make its target findable to the READ grantee" \
  "$(p_search "${C_AUTH}" "${P_FOREIGN}")" no
p_expect "the cross-owner target is still findable by the user who owns it" \
  "$(p_search "${B_AUTH}" "${P_FOREIGN}")" yes

# parentUuid must actually be on the document — the whole query-time scheme rests on it.
P_PARENT_HITS=$(docker exec "${CONTAINER_NAME}" bash -c \
  "curl -s 'http://localhost:8983/solr/ndex-nfs/select?q=uuid:${P_NET_B}&fq=parentUuid:${P_FOLDER}&rows=0&wt=json'" 2>/dev/null \
  | grep -oE '"numFound":[0-9]+' | grep -oE '[0-9]+$' || true)
p_expect "network document carries parentUuid for its folder" "${P_PARENT_HITS}" 1

# UNLISTED is a listing rule, not an access rule: no grant of any kind may make an unlisted
# file searchable by anyone but its owner, and it lives in a shared folder here precisely so
# that folder propagation gets its chance to leak it.
# The fixture is the folder owner's own network, already sitting in the shared folder, so
# the grantees hold inherited access to it and folder propagation gets a genuine chance to
# surface it. Only the owner may set visibility, which is why this one and not the
# collaborator's upload.
curl -s -o /dev/null -X POST -u "${A_AUTH}" -H 'Content-Type: application/json' \
  -d "{\"visibility\":\"UNLISTED\",\"files\":{\"${V2_PRIV_UUID}\":\"NETWORK\"}}" \
  "${BASE_URL}/v3/batch/files/setvisibility" || true
p_wait_indexed "${A_AUTH}" "${V2_PRIV_UUID}" "UNLISTED network" "PUBLIC"
p_expect "UNLISTED network IS searchable by its owner" \
  "$(p_search "${A_AUTH}" "${V2_PRIV_UUID}" PUBLIC)" yes
p_expect "UNLISTED network IS searchable by its owner with visibility omitted" \
  "$(p_search "${A_AUTH}" "${V2_PRIV_UUID}" ANY)" yes
# Both are asserted for every class: visibility=PUBLIC narrows to the partition that holds UNLISTED
# documents, while omitting it narrows nothing at all. Only the permission filter separates a grantee
# from an unlisted file in either case, and it is the scope clauses' pinning to PRIVATE that does it.
for PAIR in "write-grantee:${B_AUTH}" "read-grantee:${C_AUTH}" "no-grant:${D_AUTH}" "anonymous:"; do
  CLS="${PAIR%%:*}"; AUTH="${PAIR#*:}"
  p_expect "UNLISTED stays unlisted for ${CLS}, despite the folder grant" \
    "$(p_search "${AUTH}" "${V2_PRIV_UUID}" PUBLIC)" no
  p_expect "UNLISTED stays unlisted for ${CLS} with visibility omitted" \
    "$(p_search "${AUTH}" "${V2_PRIV_UUID}" ANY)" no
done
# ...and it is still openable by id — unlisted restricts listing, never access.
p_expect "the WRITE grantee can still OPEN the unlisted network by id" \
  "$(p_code "${B_AUTH}" GET "${BASE_URL}/v3/networks/${V2_PRIV_UUID}")" 200
curl -s -o /dev/null -X POST -u "${A_AUTH}" -H 'Content-Type: application/json' \
  -d "{\"visibility\":\"PRIVATE\",\"files\":{\"${V2_PRIV_UUID}\":\"NETWORK\"}}" \
  "${BASE_URL}/v3/batch/files/setvisibility" || true
p_wait_indexed "${A_AUTH}" "${V2_PRIV_UUID}" "network restored to PRIVATE"

# The same rule, for the two types a folder grant reaches by their OWN uuid rather than through a
# parent. The network above is reached by parentUuid, so it exercises a different scope clause than
# these do: grantedFolderIds expands to every descendant, and readableShortcutIds covers shortcuts
# sitting in a granted folder. Both clauses sit inside the same PRIVATE pin, and that pin is the only
# thing keeping an unlisted folder or shortcut out of a grantee's results.
#
# Both fixtures are asserted FOUND by all three granted classes earlier in this group, so a "no" here
# is the visibility flip and nothing else.
for ENT in "subfolder:${P_SUB}:folders" "shortcut:${P_SC}:shortcuts"; do
  E_LBL="${ENT%%:*}"; E_REST="${ENT#*:}"; E_ID="${E_REST%%:*}"; E_PATH="${E_REST##*:}"

  # The parent is resent on every PUT: both update DAOs write parent unconditionally, so omitting it
  # moves the item to the root and out of the shared folder — which would make the assertions below
  # pass for the wrong reason, measuring a reparent rather than the visibility flip.
  curl -s -o /dev/null -X PUT -u "${A_AUTH}" -H 'Content-Type: application/json' \
    -d "{\"visibility\":\"UNLISTED\",\"parent\":\"${P_FOLDER}\"}" \
    "${BASE_URL}/v3/files/${E_PATH}/${E_ID}" || true
  p_wait_indexed "${A_AUTH}" "${E_ID}" "UNLISTED ${E_LBL}" "PUBLIC"

  p_expect "UNLISTED ${E_LBL} IS searchable by its owner" \
    "$(p_search "${A_AUTH}" "${E_ID}" PUBLIC)" yes
  p_expect "UNLISTED ${E_LBL} IS searchable by its owner with visibility omitted" \
    "$(p_search "${A_AUTH}" "${E_ID}" ANY)" yes

  for PAIR in "write-grantee:${B_AUTH}" "read-grantee:${C_AUTH}" "no-grant:${D_AUTH}" "anonymous:"; do
    CLS="${PAIR%%:*}"; AUTH="${PAIR#*:}"
    p_expect "UNLISTED ${E_LBL} stays unlisted for ${CLS}, despite the folder grant" \
      "$(p_search "${AUTH}" "${E_ID}" PUBLIC)" no
    p_expect "UNLISTED ${E_LBL} stays unlisted for ${CLS} with visibility omitted" \
      "$(p_search "${AUTH}" "${E_ID}" ANY)" no
  done

  # ...and still openable by id, the same as the network: unlisted restricts listing, never access.
  p_expect "the WRITE grantee can still OPEN the unlisted ${E_LBL} by id" \
    "$(p_code "${B_AUTH}" GET "${BASE_URL}/v3/files/${E_PATH}/${E_ID}")" 200

  # Restored, because later assertions in this group expect both back in the PRIVATE partition.
  curl -s -o /dev/null -X PUT -u "${A_AUTH}" -H 'Content-Type: application/json' \
    -d "{\"visibility\":\"PRIVATE\",\"parent\":\"${P_FOLDER}\"}" \
    "${BASE_URL}/v3/files/${E_PATH}/${E_ID}" || true
  p_wait_indexed "${A_AUTH}" "${E_ID}" "${E_LBL} restored to PRIVATE"
done

# Direct and inherited grants are a union — most permissive wins and neither lowers the
# other. Easy to regress into an override, so assert both directions.
# The subject is the folder owner's network inside the shared folder: the WRITE grantee holds
# only inherited write on it (they do not own it), so a PUT by them genuinely measures
# inheritance rather than ownership.
P_ADD_HTTP=$(curl -s -o /dev/null -w '%{http_code}' -X POST -u "${A_AUTH}" -H 'Content-Type: application/json' \
  -d "{\"files\":{\"${V2_PRIV_UUID}\":\"NETWORK\"},\"members\":{\"${P_UID3}\":\"READ\"}}" \
  "${BASE_URL}/v3/files/sharing/members")
[[ "${P_ADD_HTTP}" =~ ^2 ]] || api_fail "#165 search: direct READ grant failed with HTTP ${P_ADD_HTTP}"
p_expect "a direct READ alongside an inherited READ still reads" \
  "$(p_code "${C_AUTH}" GET "${BASE_URL}/v3/networks/${V2_PRIV_UUID}")" 200
p_expect "a direct READ for one user does NOT lower another's inherited WRITE" \
  "$(curl -s -o /dev/null -w '%{http_code}' -X PUT -u "${B_AUTH}" -H 'Content-Type: application/json' \
     --data-binary "@${FIXTURES_DIR}/C. burnetii Network.cx2" "${BASE_URL}/v3/networks/${V2_PRIV_UUID}")" 200
p_expect "and the direct READ still confers no write" \
  "$(curl -s -o /dev/null -w '%{http_code}' -X PUT -u "${C_AUTH}" -H 'Content-Type: application/json' \
     --data-binary "@${FIXTURES_DIR}/C. burnetii Network.cx2" "${BASE_URL}/v3/networks/${V2_PRIV_UUID}")" 401

# The accepted PUT above re-runs CX2 processing, and a network mid-reload answers 404. Settle
# before the revoke assertions, or a 404 would masquerade as a permission result.
P_SETTLE=0
until curl -s -u "${A_AUTH}" "${BASE_URL}/v3/networks/${V2_PRIV_UUID}/summary" | grep -q '"completed":true'; do
  P_SETTLE=$((P_SETTLE+2))
  [[ ${P_SETTLE} -ge ${LOAD_TIMEOUT} ]] && api_fail "#165 search: network did not finish reloading after the PUT"
  sleep 2
done

# Revoking the folder grant takes effect immediately — no reindex, no window — and leaves
# the direct grant standing. Revoke is a null permission on the same endpoint.
P_REVOKE_HTTP=$(curl -s -o /dev/null -w '%{http_code}' -X POST -u "${A_AUTH}" -H 'Content-Type: application/json' \
  -d "{\"files\":{\"${P_FOLDER}\":\"FOLDER\"},\"members\":{\"${P_UID3}\":null}}" \
  "${BASE_URL}/v3/files/sharing/members")
[[ "${P_REVOKE_HTTP}" =~ ^2 ]] || api_fail "#165 search: revoke returned HTTP ${P_REVOKE_HTTP}"
p_expect "revoke hides the subfolder from search immediately (no reindex)" \
  "$(p_search "${C_AUTH}" "${P_SUB}")" no
p_expect "revoke leaves the DIRECT grant on the network standing" \
  "$(p_code "${C_AUTH}" GET "${BASE_URL}/v3/networks/${V2_PRIV_UUID}")" 200
p_expect "and the directly-granted network is still findable" \
  "$(p_search "${C_AUTH}" "${V2_PRIV_UUID}")" yes
p_expect "but the network they only reached through the folder is gone from search" \
  "$(p_search "${C_AUTH}" "${P_NET_B}")" no

# ── a grant on the NETWORK itself, with no folder grant anywhere above it ─────
# The regression guard for removing the indexed access lists. A direct grant is the one reachability
# path that owes nothing to the folder hierarchy: the folder above this network is PRIVATE and shared
# with nobody, so if fetch, shared-with-me or search ever start requiring a folder grant, the network
# silently vanishes for the person it was actually shared with.
D_FOLDER=$(curl -s -X POST -u "${A_AUTH}" -H 'Content-Type: application/json' \
  -d '{"name":"direct-grant-only-165"}' "${BASE_URL}/v3/files/folders/" \
  | grep -oiE '"uuid"[[:space:]]*:[[:space:]]*"[0-9a-f-]{36}"' | grep -oiE '[0-9a-f-]{36}' | head -1 || true)
[[ -n "${D_FOLDER}" ]] || api_fail "#165 direct-grant: could not create the unshared private folder"

D_NET=$(curl -s -X POST -u "${A_AUTH}" -H 'Content-Type: application/json' \
  --data-binary "@${FIXTURES_DIR}/C. burnetii Network.cx2" \
  "${BASE_URL}/v3/networks?folderId=${D_FOLDER}" \
  | grep -oiE '"uuid"[[:space:]]*:[[:space:]]*"[0-9a-f-]{36}"' | grep -oiE '[0-9a-f-]{36}' | head -1 || true)
[[ -n "${D_NET}" ]] || api_fail "#165 direct-grant: could not upload the network"
D_WAIT=0
until curl -s -u "${A_AUTH}" "${BASE_URL}/v3/networks/${D_NET}/summary" | grep -q '"completed":true'; do
  D_WAIT=$((D_WAIT+2))
  [[ ${D_WAIT} -ge ${LOAD_TIMEOUT} ]] && api_fail "#165 direct-grant: network did not finish loading"
  sleep 2
done

# The ONLY grants are on the network: READ to userc, WRITE to userb. Nothing on D_FOLDER.
D_SHARE=$(curl -s -o /dev/null -w '%{http_code}' -X POST -u "${A_AUTH}" -H 'Content-Type: application/json' \
  -d "{\"files\":{\"${D_NET}\":\"NETWORK\"},\"members\":{\"${P_UID3}\":\"READ\",\"${P_UID2}\":\"WRITE\"}}" \
  "${BASE_URL}/v3/files/sharing/members")
[[ "${D_SHARE}" =~ ^2 ]] || api_fail "#165 direct-grant: direct network share failed with HTTP ${D_SHARE}"
p_expect "the folder above it is shared with nobody" \
  "$(psql_ndex "SELECT count(*) FROM core.folder_permission WHERE folder_id='${D_FOLDER}'")" 0

p_shared() { # auth -> yes|no : is D_NET in this caller's shared-with-me list?
  if curl -s -u "$1" "${BASE_URL}/v3/files/sharing/list" | grep -q "${D_NET}"; then echo yes; else echo no; fi
}
p_search_write() { # auth uuid -> yes|no : search filtered to WRITE
  local body
  body=$(curl -s -u "$1" -X POST -H 'Content-Type: application/json' \
    -d "{\"searchString\":\"$2\",\"permission\":\"WRITE\"}" \
    "${BASE_URL}/v3/search/files?visibility=PRIVATE&start=0&size=50" || true)
  if grep -q "$2" <<<"${body}"; then echo yes; else echo no; fi
}

p_wait_indexed "${A_AUTH}" "${D_NET}" "directly-granted network"

# owner — reaches it by ownership on all three surfaces
p_expect "owner GETs the directly-granted network"   "$(p_code "${A_AUTH}" GET "${BASE_URL}/v3/networks/${D_NET}")" 200
p_expect "owner FINDS it in search"                  "$(p_search "${A_AUTH}" "${D_NET}")" yes
p_expect "owner can browse the private folder"       "$(p_code "${A_AUTH}" GET "${BASE_URL}/v3/files/folders/${D_FOLDER}/list")" 200

# READ grantee — reaches it by the network grant alone
p_expect "READ grantee GETs the network"             "$(p_code "${C_AUTH}" GET "${BASE_URL}/v3/networks/${D_NET}")" 200
p_expect "READ grantee FINDS it in search"           "$(p_search "${C_AUTH}" "${D_NET}")" yes
p_expect "READ grantee sees it in shared-with-me"    "$(p_shared "${C_AUTH}")" yes
# ...but a grant on a network is not a grant on its folder: the folder stays closed to them.
p_expect "READ grantee still CANNOT browse the folder" \
  "$(p_code "${C_AUTH}" GET "${BASE_URL}/v3/files/folders/${D_FOLDER}/list")" 401

# authenticated with no grant of any kind
p_expect "no-grant user GET denied"                  "$(p_code "${D_AUTH}" GET "${BASE_URL}/v3/networks/${D_NET}")" 401
p_expect "no-grant user does NOT find it in search"  "$(p_search "${D_AUTH}" "${D_NET}")" no
p_expect "no-grant user does not see it in shared-with-me" "$(p_shared "${D_AUTH}")" no
p_expect "no-grant user CANNOT browse the folder"    "$(p_code "${D_AUTH}" GET "${BASE_URL}/v3/files/folders/${D_FOLDER}/list")" 401

# anonymous
p_expect "anonymous GET denied"                      "$(p_code "" GET "${BASE_URL}/v3/networks/${D_NET}")" 401
p_expect "anonymous does NOT find it in search"      "$(p_search "" "${D_NET}")" no
p_expect "shared-with-me requires authentication"    "$(p_code "" GET "${BASE_URL}/v3/files/sharing/list")" 401
p_expect "anonymous CANNOT browse the folder"        "$(p_code "" GET "${BASE_URL}/v3/files/folders/${D_FOLDER}/list")" 401

# A WRITE-filtered search must narrow the direct-grant arm too, not just the folder arm. Before this
# was fixed the arm matched any grant, so a read-only grantee's networks came back from a search that
# asked for WRITE — the filter claimed write access and returned things they could only read.
p_expect "READ grantee is EXCLUDED from a WRITE-filtered search" \
  "$(p_search_write "${C_AUTH}" "${D_NET}")" no
p_expect "WRITE grantee IS included in a WRITE-filtered search" \
  "$(p_search_write "${B_AUTH}" "${D_NET}")" yes
p_expect "owner is included in a WRITE-filtered search" \
  "$(p_search_write "${A_AUTH}" "${D_NET}")" yes
p_expect "no-grant user is excluded from a WRITE-filtered search" \
  "$(p_search_write "${D_AUTH}" "${D_NET}")" no
# ...and the READ grantee is still there unfiltered, so the exclusion above is the filter working
# rather than the network having become unreachable.
p_expect "READ grantee still finds it without a permission filter" \
  "$(p_search "${C_AUTH}" "${D_NET}")" yes

# Revoking the only grant removes it from all three surfaces at once, with no reindex.
D_REVOKE=$(curl -s -o /dev/null -w '%{http_code}' -X POST -u "${A_AUTH}" -H 'Content-Type: application/json' \
  -d "{\"files\":{\"${D_NET}\":\"NETWORK\"},\"members\":{\"${P_UID3}\":null}}" \
  "${BASE_URL}/v3/files/sharing/members")
[[ "${D_REVOKE}" =~ ^2 ]] || api_fail "#165 direct-grant: revoke returned HTTP ${D_REVOKE}"
p_expect "after revoke the grantee GET is denied"      "$(p_code "${C_AUTH}" GET "${BASE_URL}/v3/networks/${D_NET}")" 401
p_expect "after revoke it is gone from their search"   "$(p_search "${C_AUTH}" "${D_NET}")" no
p_expect "after revoke it is gone from shared-with-me" "$(p_shared "${C_AUTH}")" no
p_expect "the owner still reaches it"                  "$(p_code "${A_AUTH}" GET "${BASE_URL}/v3/networks/${D_NET}")" 200


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
  echo "  API call ${CALL_NUM}: POST /v2/user (no auth, expect 401)"
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
  echo "  API call ${CALL_NUM}: POST /v2/user (auth as ${TEST_USER}, expect 201 or 409)"
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
  echo "  API call ${CALL_NUM}: GET /user/authenticate (after SIGKILL restart, expect 200)"
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



