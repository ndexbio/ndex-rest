#!/usr/bin/env bash
# NDEx upgrade test — current released image → current local build.
#
# Usage: ./migration-test.sh [--skip-build]
#
# Runs as GROUP 2 of the integration suite, after the functional group has finished
# and its container and volume are gone. The two groups never share state: this one
# uses its own named volume, which is the only volume in the suite that deliberately
# survives a container swap.
#
#   phase A   the released image   seed a shared folder and its contents
#             stop + remove container, KEEP the volume
#   phase B   locally built image on the SAME volume, assert the upgrade
#
# The point is the upgrade path a real deployment takes: an instance running the
# released version, carrying data written by it, must come up on this build with its
# data intact, its schema advanced, and folder permissions resolving correctly.
# Seeding through the released image produces real stored state rather than fixtures
# shaped to suit the migration.
#
# Deps: docker, curl. Exits 0 on success, 1 on the first failed assertion.
set -uo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
FIXTURES_DIR="${SCRIPT_DIR}/fixtures"
REPO_DIR="$(cd "${SCRIPT_DIR}/../.." && pwd)"

BASE_IMAGE="ndexbio/ndex-rest:3.0.4"   # current released version
CURRENT_IMAGE="ndexbio/ndex-rest"
CONTAINER="ndex-migration-test"
VOLUME="ndex-it-migration-data"
PORT=8080
BASE_URL="http://localhost:${PORT}"

OWNER="mig-owner"; OWNER_PW="MigOwner1!"
GRANTEE="mig-grantee"; GRANTEE_PW="MigGrantee1!"

SKIP_BUILD=false
[[ "${1:-}" == "--skip-build" ]] && SKIP_BUILD=true

RED='\033[0;31m'; GREEN='\033[0;32m'; CYAN='\033[0;36m'; BOLD='\033[1m'; NC='\033[0m'
PASSED=0

step() { echo ""; echo -e "${BOLD}=== $1 ===${NC}"; }
ok()   { PASSED=$((PASSED+1)); echo -e "  ${GREEN}✓ PASS${NC}: $1"; }
die()  { echo ""; echo -e "  ${RED}✗ FAIL${NC}: $1"; echo -e "${RED}${BOLD}MIGRATION TEST FAILED${NC} (after ${PASSED} passing assertions)"; exit 1; }
expect() { [[ "$2" == "$3" ]] || die "$1 — got '$2', expected '$3'"; ok "$1 = $3"; }

cleanup() {
  echo ""
  echo -e "${CYAN}=== Cleanup (group 2) ===${NC}"
  docker rm -fv "${CONTAINER}" 2>/dev/null || true
  # A named volume is NOT removed by `docker rm -v`; it needs an explicit call. That
  # is exactly the property phase A relies on, so it must be undone deliberately here.
  docker volume rm "${VOLUME}" 2>/dev/null || true
  if docker volume inspect "${VOLUME}" &>/dev/null; then
    echo -e "  ${RED}WARNING: volume '${VOLUME}' still present — run: docker volume rm ${VOLUME}${NC}"
  else
    echo -e "  ${GREEN}✓ migration container and volume removed${NC}"
  fi
}
trap cleanup EXIT

# Purge up front too: a killed run leaves a volume behind, and a stale one would turn
# the next "fresh install" into an accidental upgrade.
docker rm -fv "${CONTAINER}" 2>/dev/null || true
docker volume rm "${VOLUME}" 2>/dev/null || true

psql_mig() {
  docker exec "${CONTAINER}" bash -c "
    DB_USER=\$(grep '^NdexDBUsername=' /apps/ndex/config/ndex.properties | cut -d= -f2-)
    DB_PASS=\$(grep '^NdexDBDBPassword=' /apps/ndex/config/ndex.properties | cut -d= -f2-)
    PGPASSWORD=\"\$DB_PASS\" psql -tA -h 127.0.0.1 -p 5432 -U \"\$DB_USER\" -d ndex -c \"$1\"
  " 2>/dev/null | tr -d '[:space:]'
}

start_container() { # image
  docker run -d --name "${CONTAINER}" -p "${PORT}:8080" -v "${VOLUME}:/apps" "$1" \
    --ndex --postgres --keycloak --solr --mailhog > /dev/null
  local waited=0
  until docker logs "${CONTAINER}" 2>&1 | grep -q "NDEx Deploy Container Ready"; do
    if [[ ${waited} -ge 180 ]]; then
      docker logs "${CONTAINER}" 2>&1 | tail -30
      die "container from $1 did not become ready within 180s"
    fi
    sleep 3; waited=$((waited+3))
  done
  until curl -s -o /dev/null "${BASE_URL}/v2/admin/status"; do sleep 2; done
}

uuid_of() { grep -oiE '"uuid"[[:space:]]*:[[:space:]]*"[0-9a-f-]{36}"' | grep -oiE '[0-9a-f-]{36}' | head -1; }
code()    { curl -s -o /dev/null -w '%{http_code}' "$@"; }
listed()  { if curl -s -u "$1" "${BASE_URL}/v3/files/folders/$2/list" | grep -q "$3"; then echo yes; else echo no; fi; }
# Searching by UUID isolates one document (uuid is a query field), so this is a permission
# answer rather than a relevance artifact.
found()   { if curl -s -u "$1" -X POST -H 'Content-Type: application/json' -d "{\"searchString\":\"$2\"}" \
                 "${BASE_URL}/v3/search/files?visibility=PRIVATE&start=0&size=50" | grep -q "$2"; then echo yes; else echo no; fi; }

# ══════════════════════════════════════════════════════════════════════════════
step "Phase A — seed on the released image (${BASE_IMAGE})"
docker pull "${BASE_IMAGE}" > /dev/null 2>&1 || die "could not pull ${BASE_IMAGE}"
start_container "${BASE_IMAGE}"
ok "released image ready on a fresh volume"

for U in "${OWNER}:${OWNER_PW}" "${GRANTEE}:${GRANTEE_PW}"; do
  UN="${U%%:*}"; UP="${U##*:}"
  curl -s -o /dev/null -X POST -H 'Content-Type: application/json' \
    -d "{\"userName\":\"${UN}\",\"password\":\"${UP}\",\"emailAddress\":\"${UN}@ndex-migration.local\",\"firstName\":\"Mig\",\"lastName\":\"Test\"}" \
    "${BASE_URL}/v2/user"
done
G_UID=$(curl -s -u "${OWNER}:${OWNER_PW}" "${BASE_URL}/v2/user?username=${GRANTEE}" | grep -oE '"externalId":"[0-9a-f-]{36}"' | cut -d'"' -f4)
[[ -n "${G_UID}" ]] || die "could not resolve grantee id on the released image"

# The ticket's failing order: grant on an empty folder, then add content.
M_FOLDER=$(curl -s -X POST -u "${OWNER}:${OWNER_PW}" -H 'Content-Type: application/json' \
  -d '{"name":"mig-shared"}' "${BASE_URL}/v3/files/folders/" | uuid_of)
[[ -n "${M_FOLDER}" ]] || die "could not create folder on the released image"
curl -s -o /dev/null -X POST -u "${OWNER}:${OWNER_PW}" -H 'Content-Type: application/json' \
  -d "{\"files\":{\"${M_FOLDER}\":\"FOLDER\"},\"members\":{\"${G_UID}\":\"WRITE\"}}" \
  "${BASE_URL}/v3/files/sharing/members"

M_NET=$(curl -s -X POST -u "${OWNER}:${OWNER_PW}" -H 'Content-Type: application/json' \
  --data-binary "@${FIXTURES_DIR}/C. burnetii Network.cx2" \
  "${BASE_URL}/v3/networks?folderId=${M_FOLDER}" | uuid_of)
[[ -n "${M_NET}" ]] || die "could not upload network into the folder on the released image"
M_SUB=$(curl -s -X POST -u "${OWNER}:${OWNER_PW}" -H 'Content-Type: application/json' \
  -d "{\"name\":\"mig-sub\",\"parent\":\"${M_FOLDER}\"}" "${BASE_URL}/v3/files/folders/" | uuid_of)

# Never-downgrade fixture: a direct WRITE on a network whose folder grants only READ.
M_NET2=$(curl -s -X POST -u "${OWNER}:${OWNER_PW}" -H 'Content-Type: application/json' \
  --data-binary "@${FIXTURES_DIR}/C. burnetii Network.cx2" "${BASE_URL}/v3/networks" | uuid_of)
M_RFOLDER=$(curl -s -X POST -u "${OWNER}:${OWNER_PW}" -H 'Content-Type: application/json' \
  -d '{"name":"mig-readonly"}' "${BASE_URL}/v3/files/folders/" | uuid_of)
curl -s -o /dev/null -X POST -u "${OWNER}:${OWNER_PW}" -H 'Content-Type: application/json' \
  -d "{\"files\":{\"${M_RFOLDER}\":\"FOLDER\"},\"members\":{\"${G_UID}\":\"READ\"}}" \
  "${BASE_URL}/v3/files/sharing/members"
curl -s -o /dev/null -X POST -u "${OWNER}:${OWNER_PW}" -H 'Content-Type: application/json' \
  -d "{\"targetFolder\":\"${M_RFOLDER}\",\"networks\":[\"${M_NET2}\"]}" "${BASE_URL}/v3/batch/networks/move"
curl -s -o /dev/null -X POST -u "${OWNER}:${OWNER_PW}" -H 'Content-Type: application/json' \
  -d "{\"files\":{\"${M_NET2}\":\"NETWORK\"},\"members\":{\"${G_UID}\":\"WRITE\"}}" \
  "${BASE_URL}/v3/files/sharing/members"
ok "seeded: shared folder, its contents, and a direct-WRITE-under-READ fixture"

# Let indexing finish on the OLD image before the container goes away. NdexServerQueue persists
# its tasks in the database, so a task still queued here would replay under the new build and
# write parentUuid onto the document before the backfill ran — making the pre-reindex assertion
# below pass or fail depending on timing rather than on behaviour.
for N in "${M_NET}" "${M_NET2}"; do
  WAITED=0
  until curl -s -u "${OWNER}:${OWNER_PW}" "${BASE_URL}/v3/networks/${N}/summary" | grep -q '"completed":true'; do
    WAITED=$((WAITED+2))
    [[ ${WAITED} -ge 180 ]] && die "network ${N} did not finish loading on the released image"
    sleep 2
  done
done
until [[ "$(psql_mig "SELECT count(*) FROM core.task WHERE status='QUEUED' OR status='PROCESSING'")" == "0" ]]; do
  WAITED=$((WAITED+2))
  [[ ${WAITED} -ge 240 ]] && die "index tasks were still pending on the released image"
  sleep 2
done
ok "indexing drained on the released image, so nothing replays under the new build"

# Record the pre-upgrade facts the post-upgrade assertions are measured against.
PRE_SCHEMA=$(psql_mig "SELECT version FROM core.schema_version ORDER BY applied_at DESC LIMIT 1")
PRE_UNM=$(psql_mig "SELECT count(*) FROM core.user_network_membership")
PRE_NETS=$(psql_mig "SELECT count(*) FROM core.network WHERE is_deleted=false")
echo "  pre-upgrade: schema=${PRE_SCHEMA}, networks=${PRE_NETS}, membership rows=${PRE_UNM}"
[[ -n "${PRE_SCHEMA}" ]] || die "could not read the pre-upgrade schema version"

# ══════════════════════════════════════════════════════════════════════════════
step "Phase B — swap to the current build on the SAME volume"
if [[ "${SKIP_BUILD}" != "true" ]]; then
  ( cd "${REPO_DIR}" && make docker > /dev/null 2>&1 ) || die "make docker failed"
fi
docker rm -f "${CONTAINER}" > /dev/null 2>&1   # named volume survives this by design
start_container "${CURRENT_IMAGE}"
ok "current build started on the pre-existing data volume"

expect "schema advanced to the current version in one boot" \
  "$(psql_mig "SELECT version FROM core.schema_version ORDER BY applied_at DESC LIMIT 1")" "3.0.5"
expect "no data was lost across the upgrade" \
  "$(psql_mig "SELECT count(*) FROM core.network WHERE is_deleted=false")" "${PRE_NETS}"
expect "network_parent_idx exists" \
  "$(psql_mig "SELECT count(*) FROM pg_indexes WHERE schemaname='core' AND indexname='network_parent_idx'")" 1
expect "membership archive captured the pre-upgrade rows" \
  "$(psql_mig "SELECT count(*) FROM core.user_network_membership_archive")" "${PRE_UNM}"
expect "never-downgrade: direct WRITE under a READ-only folder survived cleanup" \
  "$(psql_mig "SELECT count(*) FROM core.user_network_membership WHERE network_id='${M_NET2}' AND permission_type::text='WRITE'")" 1

# The headline: data seeded on the broken version now resolves, with NO re-grant.
expect "grantee sees the network added after the grant, with no re-share" "$(listed "${GRANTEE}:${GRANTEE_PW}" "${M_FOLDER}" "${M_NET}")" yes
expect "grantee sees the subfolder added after the grant, with no re-share" "$(listed "${GRANTEE}:${GRANTEE_PW}" "${M_FOLDER}" "${M_SUB}")" yes
expect "grantee can open the network added after the grant" \
  "$(code -u "${GRANTEE}:${GRANTEE_PW}" "${BASE_URL}/v3/networks/${M_NET}")" 200
expect "grantee can open the subfolder added after the grant" \
  "$(code -u "${GRANTEE}:${GRANTEE_PW}" "${BASE_URL}/v3/files/folders/${M_SUB}")" 200
expect "owner still sees their own network" \
  "$(code -u "${OWNER}:${OWNER_PW}" "${BASE_URL}/v3/networks/${M_NET}")" 200

# ── the one operator action this release requires ─────────────────────────────
# Documents written by the released image carry the old copied-on access list and no
# parentUuid, so folder-propagated search cannot work until they are rewritten. Asserting
# the "before" state keeps the upgrade note honest: it is a real requirement, not boilerplate.
expect "before the reindex, the grantee cannot yet FIND the shared network" \
  "$(found "${GRANTEE}:${GRANTEE_PW}" "${M_NET}")" no

MIG_PW=$(docker exec "${CONTAINER}" bash -c "grep '^MigrationPassword=' /apps/ndex/config/ndex.properties | cut -d= -f2-" | tr -d '[:space:]')
[[ -n "${MIG_PW}" ]] || die "could not read MigrationPassword for the backfill reindex"
REIDX_HTTP=$(code "${BASE_URL}/v3/admin/reindex-v3?password=${MIG_PW}")
expect "the documented backfill reindex succeeds" "${REIDX_HTTP}" 200

expect "after the reindex, the grantee FINDS a network shared before the upgrade" \
  "$(found "${GRANTEE}:${GRANTEE_PW}" "${M_NET}")" yes
expect "the owner finds it too" \
  "$(found "${OWNER}:${OWNER_PW}" "${M_NET}")" yes
expect "the backfill wrote parentUuid onto the network document" \
  "$(docker exec "${CONTAINER}" bash -c "curl -s 'http://localhost:8983/solr/private-nfs/select?q=uuid:${M_NET}&fq=parentUuid:${M_FOLDER}&rows=0&wt=json'" 2>/dev/null | grep -oE '"numFound":[0-9]+' | grep -oE '[0-9]+$')" 1
expect "the rebuild dropped the stale access list from the document" \
  "$(docker exec "${CONTAINER}" bash -c "curl -s 'http://localhost:8983/solr/private-nfs/select?q=uuid:${M_NET}&fq=userRead:*&rows=0&wt=json'" 2>/dev/null | grep -oE '"numFound":[0-9]+' | grep -oE '[0-9]+$')" 0

echo ""
echo -e "${GREEN}${BOLD}================================================${NC}"
echo -e "${GREEN}${BOLD}  ✓ MIGRATION TEST PASSED (${PASSED} assertions)${NC}"
echo -e "${GREEN}${BOLD}================================================${NC}"
exit 0
