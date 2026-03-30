#!/usr/bin/env bash
# init-postgres.sh — One-time NDEx PostgreSQL initialization.
# Called by entrypoint.sh on first boot only (after PostgreSQL is running).
# Runs as root; uses gosu to execute psql as the postgres OS user.
#
# Required env vars (set by start.sh / entrypoint.sh before calling this script):
#   SQL_DIR             — path to directory containing SQL schema files
#   PG_PORT             — PostgreSQL port
#   PG_NDEX_USERNAME    — login role name for the NDEx application (default: ndexserver)
#   PG_NDEX_PASSWORD    — password for the NDEx application role
set -euo pipefail

SQL_DIR="${SQL_DIR:-/opt/ndex-install/sql}"
PG_PORT="${PG_PORT:-5432}"
PG_NDEX_USERNAME="${PG_NDEX_USERNAME:-ndexserver}"
PG_NDEX_PASSWORD="${PG_NDEX_PASSWORD:-}"

_psql_super() {
  # Connect via unix socket (auth-local=trust — no password needed for postgres OS user)
  gosu postgres psql -v ON_ERROR_STOP=1 --port "${PG_PORT}" --username postgres "$@"
}

_psql_ndex() {
  PGPASSWORD="${PG_NDEX_PASSWORD}" psql -v ON_ERROR_STOP=1 \
    --host 127.0.0.1 --port "${PG_PORT}" \
    --username "${PG_NDEX_USERNAME}" "$@"
}

# Remove PG12-incompatible directives from SQL on stdin:
#   WITH (OIDS = FALSE)   — removed in PG 12 (multi-line block)
#   SET default_with_oids — removed in PG 12 (single-line)
_strip_compat() {
  perl -0777 -pe '
    s/\s*WITH\s*\(\s*OIDS\s*=\s*FALSE\s*\)//g;
    s/[ \t]*SET\s+default_with_oids[^\n]*\n//g;
  '
}

echo "==> Creating NDEx database role and database..."
_psql_super <<-EOSQL
    CREATE ROLE ${PG_NDEX_USERNAME} WITH LOGIN PASSWORD '${PG_NDEX_PASSWORD}';
    CREATE DATABASE ndex WITH OWNER ${PG_NDEX_USERNAME} ENCODING 'UTF8';
EOSQL

echo "==> Loading NDEx base schema (v2.5.3)..."
# Run as postgres superuser (required for CREATE EXTENSION statements).
# Strip PG12-incompatible directives before loading.
_strip_compat < "${SQL_DIR}/ndex_db_schema_v2_5_3.sql" | _psql_super --dbname ndex

# Set default search_path so NDEx JDBC queries resolve tables in the core schema
# without fully-qualifying every table name.
_psql_super --dbname ndex -c "ALTER DATABASE ndex SET search_path TO core, public;"

echo "==> Applying schema updates in order..."
# Discover all schema_update_*.sql files, sort lexicographically, and apply only
# those whose from-version is >= the base schema version (skip old migration files
# that predated the base schema and would fail on the current table structure).
# Filename format: schema_update_<FROM>_to_<TO>.sql
SCHEMA_BASE_VERSION="2.5.3"
while IFS= read -r -d '' f; do
  fname=$(basename "${f}")
  from_ver=$(echo "${fname}" | sed 's/schema_update_\([0-9.]*\)_to_.*/\1/')
  # Apply if from_ver >= base (e.g. 2.5.4 → apply) OR
  # if base starts with from_ver (e.g. from_ver=2.5, base=2.5.3 → same minor series → apply).
  # The second condition handles files like schema_update_2.5_to_2.6.sql where
  # the from-version names the minor series rather than a specific patch.
  if printf '%s\n' "${SCHEMA_BASE_VERSION}" "${from_ver}" | sort -V -C 2>/dev/null || \
     echo "${SCHEMA_BASE_VERSION}" | grep -q "^${from_ver}[.]"; then
    echo "    -> ${fname}"
    _strip_compat < "${f}" | _psql_ndex --dbname ndex
  else
    echo "    (skip ${fname} — predates base schema ${SCHEMA_BASE_VERSION})"
  fi
done < <(find "${SQL_DIR}" -maxdepth 1 -name "schema_update_*.sql" -print0 | sort -z)

echo "==> NDEx database initialized successfully."
