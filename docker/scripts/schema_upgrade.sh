#!/usr/bin/env bash
# schema_upgrade.sh — NDEx PostgreSQL schema initializer and upgrader.
#
# Usage: schema_upgrade.sh <host> <port> <db> <ndex_user> <ndex_pass>
#
# Caller responsibilities (start.sh ensures these before invoking):
#   - PostgreSQL is running and reachable at <host>:<port>
#   - <db> database exists
#   - <ndex_user> role exists and has access to <db>
#   - plpgsql and dblink extensions are installed in <db> (done by start.sh for
#     local PG when the database is first created)
#
# On every invocation this script determines the current schema state and
# advances the database to the latest version:
#
#   Path 1 — v0 migration (core.schema_version table absent):
#     If core.network also absent: load base schema (ndex_db_schema_v2_5_3.sql)
#     Apply all schema_update_*.sql files where from_ver >= 2.5.3 in sorted order.
#     The 2.5.5→3.0 update creates core.schema_version and inserts the 3.0 version row.
#
#   Path 2 — tracked upgrade (core.schema_version table present):
#     Query current version from core.schema_version.
#     Apply schema_update_A_to_B.sql files where from_ver == current_ver, inserting a new to_ver row
#     after each. Repeat the whole sweep until a pass applies nothing, so a multi-step upgrade always
#     completes in one run regardless of how the filenames happen to sort.
#
# All update files use IF NOT EXISTS / IF EXISTS guards and are therefore idempotent.
set -euo pipefail

HOST="$1"
PORT="$2"
DB="$3"
USER="$4"
PASS="$5"

SQL_DIR=/opt/ndex-install/sql
BASE_VERSION="2.5.3"

_psql() {
  PGPASSWORD="${PASS}" psql -v ON_ERROR_STOP=1 \
    --host "${HOST}" --port "${PORT}" --username "${USER}" --dbname "${DB}" "$@"
}

_strip_compat() {
  perl -0777 -pe '
    s/\s*WITH\s*\(\s*OIDS\s*=\s*FALSE\s*\)//g;
    s/[ \t]*SET\s+default_with_oids[^\n]*\n//g;
    s/[ \t]*COMMENT ON EXTENSION[^\n]*\n//g;
  '
}

# ── Detect schema state ───────────────────────────────────────────────────────
version_table_exists=$(_psql -tAc \
  "SELECT 1 FROM information_schema.tables \
   WHERE table_schema='core' AND table_name='schema_version'" \
  2>/dev/null || true)

if [[ "${version_table_exists}" != "1" ]]; then
  # ── Path 1: v0 migration ─────────────────────────────────────────────────────
  echo "==> No schema version tracking found — running v0 migration..."

  network_exists=$(_psql -tAc \
    "SELECT 1 FROM information_schema.tables \
     WHERE table_schema='core' AND table_name='network'" \
    2>/dev/null || true)

  if [[ "${network_exists}" != "1" ]]; then
    echo "==> Loading NDEx base schema (v${BASE_VERSION})..."
    _strip_compat < "${SQL_DIR}/ndex_db_schema_v2_5_3.sql" | _psql
    _psql -c "ALTER DATABASE \"${DB}\" SET search_path TO core, public;"
  fi

  # Apply all update files from BASE_VERSION forward (in sorted order).
  # The 2.5.5→3.0 file creates core.schema_version.
  last_ver="${BASE_VERSION}"
  while IFS= read -r -d '' f; do
    fname=$(basename "${f}")
    from_ver=$(echo "${fname}" | sed 's/schema_update_\([0-9.]*\)_to_.*/\1/')
    to_ver=$(echo "${fname}"   | sed 's/schema_update_[0-9.]*_to_\([0-9.]*\)\.sql/\1/')
    if printf '%s\n' "${BASE_VERSION}" "${from_ver}" | sort -V -C 2>/dev/null || \
       echo "${BASE_VERSION}" | grep -q "^${from_ver}[.]"; then
      echo "==> Applying ${fname} (${from_ver} → ${to_ver})..."
      _strip_compat < "${f}" | _psql
      # Only advance last_ver if to_ver is higher (side-chain files like 2.5→2.6
      # sort after 3.0 lexicographically but are not version-higher than 3.0)
      if printf '%s\n' "${last_ver}" "${to_ver}" | sort -V -C 2>/dev/null && \
         [[ "${to_ver}" != "${last_ver}" ]]; then
        last_ver="${to_ver}"
      fi
    else
      echo "    (skip ${fname} — predates base schema v${BASE_VERSION})"
    fi
  done < <(find "${SQL_DIR}" -maxdepth 1 -name "schema_update_*.sql" -print0 | sort -z)

  # core.schema_version was created and populated by the 2.5.5→3.0 update script.
  echo "==> V0 migration complete. Schema at v${last_ver}."  

else
  # ── Path 2: tracked upgrade ───────────────────────────────────────────────────
  current_ver=$(_psql -tAc \
    "SELECT version FROM core.schema_version ORDER BY applied_at DESC LIMIT 1" \
    2>/dev/null || true)
  echo "==> NDEx schema version: ${current_ver}"

  # Repeat until a full pass applies nothing.
  #
  # A single pass is not enough: files are visited in filename order, which is not upgrade order.
  # `schema_update_3.0.3_to_3.0.5.sql` sorts BEFORE `schema_update_3.0_to_3.0.3.sql` ('.' = 0x2E
  # precedes '_' = 0x5F), so starting at 3.0 the first file is skipped as not-yet-applicable, the
  # second advances the version to 3.0.3, and the pass ends — leaving the database one step short
  # and needing another container start to finish. Looping makes the result independent of how the
  # files happen to sort, so future migrations cannot reintroduce the problem by name alone.
  applied=0
  passes=0
  while :; do
    applied_this_pass=0
    while IFS= read -r -d '' f; do
      fname=$(basename "${f}")
      from_ver=$(echo "${fname}" | sed 's/schema_update_\([0-9.]*\)_to_.*/\1/')
      to_ver=$(echo "${fname}"   | sed 's/schema_update_[0-9.]*_to_\([0-9.]*\)\.sql/\1/')
      if [[ "${from_ver}" == "${current_ver}" ]]; then
        echo "==> Applying ${fname} (${from_ver} → ${to_ver})..."
        _strip_compat < "${f}" | _psql
        _psql -c "INSERT INTO core.schema_version (version) VALUES ('${to_ver}');"
        current_ver="${to_ver}"
        (( applied++ )) || true
        (( applied_this_pass++ )) || true
      fi
    done < <(find "${SQL_DIR}" -maxdepth 1 -name "schema_update_*.sql" -print0 | sort -z)

    [[ "${applied_this_pass}" -eq 0 ]] && break

    # Guard against a malformed chain (e.g. a file whose from_ver equals its to_ver) spinning forever.
    (( passes++ )) || true
    if [[ "${passes}" -gt 50 ]]; then
      echo "ERROR: schema upgrade did not converge after ${passes} passes (currently v${current_ver})." >&2
      exit 1
    fi
  done

  if [[ "${applied}" -eq 0 ]]; then
    echo "==> NDEx schema is up to date (v${current_ver})."
  else
    echo "==> NDEx schema updated to v${current_ver} (${applied} update(s) applied)."
  fi
fi
