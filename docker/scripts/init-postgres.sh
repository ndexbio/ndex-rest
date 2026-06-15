#!/usr/bin/env bash
# init-postgres.sh — Initialize the local PostgreSQL cluster.
# Called by start.sh --postgres service method on first boot only.
# Sets up PGDATA, starts postgres, writes superuser password to /etc/pg.otp.
# Postgres is left running so that subsequent service methods (--keycloak, --ndex)
# can create their roles/databases; start.sh stops it before supervisord takes over.
#
# Required env vars (set by start.sh):
#   PGDATA    — path to postgres data directory
#   PG_PORT   — port postgres listens on

set -euo pipefail

PGDATA="${PGDATA:-/apps/postgres/data}"
PG_PORT="${PG_PORT:-5432}"

echo "==> Initializing PostgreSQL cluster..."
gosu postgres initdb \
  -D "${PGDATA}" \
  --auth-host=trust \
  --auth-local=trust \
  --data-checksums \
  -U postgres

echo "==> Starting PostgreSQL (temp — trust auth)..."
gosu postgres pg_ctl -D "${PGDATA}" -o "-p ${PG_PORT} -h '127.0.0.1,::1'" start -w

_wait=0
until pg_isready -h 127.0.0.1 -p "${PG_PORT}" -U postgres -q; do
  sleep 1; (( _wait++ )) || true
  (( _wait % 5 == 0 )) && echo "==> Waiting for PostgreSQL... (${_wait}s)"
done

# Generate superuser password and switch to scram-sha-256 auth
PG_SUPERUSER_PASSWORD=$(tr -dc 'A-Za-z0-9' < /dev/urandom | head -c 16 || true)
PGPASSWORD="" psql -h 127.0.0.1 -p "${PG_PORT}" -U postgres \
  -c "ALTER USER postgres PASSWORD '${PG_SUPERUSER_PASSWORD}';"

# Apply pg_hba.conf — hardened default or user-provided override
if [[ "$(cat /apps/postgres/config/pg_hba.conf 2>/dev/null)" == "# NDEX DEFAULT - INTENTIONALLY BLANK" ]]; then
  cat > "${PGDATA}/pg_hba.conf" <<HBA
# NDEX GENERATED - DO NOT EDIT
# NDEx deploy image — hardened pg_hba.conf (no trust)
local   all    all                      scram-sha-256
host    all    all    127.0.0.1/32      scram-sha-256
host    all    all    ::1/128           scram-sha-256
HBA
else
  cp /apps/postgres/config/pg_hba.conf "${PGDATA}/pg_hba.conf"
fi
gosu postgres pg_ctl -D "${PGDATA}" reload

printf 'user: postgres\npassword: %s\n' "${PG_SUPERUSER_PASSWORD}" > /etc/pg.otp
chmod 600 /etc/pg.otp
echo "==> PostgreSQL ready. Superuser password written to /etc/pg.otp (auto-deleted in 2 hours)."
