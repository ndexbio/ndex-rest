#!/usr/bin/env bash
# dev-entrypoint.sh — Devcontainer startup.
#
# Delegates all service initialization (postgres, keycloak, solr, mailhog) to the
# deploy image's start.sh, waits for services to reach RUNNING state, configures
# ndex.properties on first boot, then launches Jetty with live source code.
#
# Port env vars (set in devcontainer.json containerEnv):
#   NDEX_PORT — Jetty listen port; used in ndex.properties HostURI (default: 8080)
#               Set this env var AND add the port to forwardPorts in devcontainer.json
#               to expose the NDEx API to the host. Other service ports (Keycloak 8085,
#               PostgreSQL 5432, Solr 8983, MailHog 8025/1025, Keycloak mgmt 9000) are
#               always available inside the container. To expose any of them, add the
#               corresponding env var (e.g. "KEYCLOAK_PORT": "8085") to containerEnv
#               AND add that port to forwardPorts, then rebuild.
#
# Phases:
#   1  Start postgres, keycloak, solr, mailhog via start.sh (background)
#   2  Wait for all services RUNNING (sentinel: /tmp/ndex-services-ready)
#   3  First boot only: seed /apps/ndex/config/, generate NDEx DB credentials from
#      /etc/pg.otp (written by start.sh --postgres), create ndex role/DB (with existence
#      checks), install extensions as superuser, substitute all 8 __PLACEHOLDER__ values
#      in ndex.properties.  Every boot: call schema_upgrade.sh to apply any pending SQL
#      updates.
#   4  Ensure NDEx data subdirectories exist; copy support files to NdexRoot/conf/.
#   5  First boot only: build ndex-object-model:3.0.0-SNAPSHOT if not in Maven cache
#   6  Print ready banner; exec sleep infinity (NDEx started manually via ndex-server.sh)
set -euo pipefail

NDEX_PORT="${NDEX_PORT:-8080}"

echo ""
echo "========================================================"
echo "  NDEx Devcontainer — Starting"
echo "========================================================"
echo ""

# ── Phase 1: Start supporting services ───────────────────────────────────────
echo "==> Starting support services (postgres, keycloak, solr, mailhog)..."
/usr/local/bin/start.sh --postgres --keycloak --solr --mailhog --disable-credential-removal &

# ── Phase 2: Wait for services ready ─────────────────────────────────────────
echo "==> Waiting for all services to reach RUNNING state..."
TIMEOUT=300
ELAPSED=0
while [[ ! -f /tmp/ndex-services-ready ]]; do
  if (( ELAPSED >= TIMEOUT )); then
    echo "ERROR: Services did not reach RUNNING state within ${TIMEOUT}s" >&2
    exit 1
  fi
  sleep 3
  (( ELAPSED += 3 )) || true
done
echo "==> All services RUNNING."

# Wait for PostgreSQL to accept connections (supervisord marks RUNNING on process start,
# not TCP readiness — critical on restart when there is no init-time postgres).
echo -n "==> Waiting for PostgreSQL to accept connections..."
PG_WAIT=0
until pg_isready -h 127.0.0.1 -p 5432 -U postgres -q 2>/dev/null; do
  sleep 1; (( PG_WAIT++ )) || true
  (( PG_WAIT % 10 == 0 )) && echo -n " (${PG_WAIT}s)"
done
echo " ready."

# ── Phase 3: First-boot ndex.properties configuration / every-boot schema upgrade ──
# On first boot: seed config, create role/DB (with existence checks + extension setup),
# substitute all 8 placeholders in ndex.properties.
# Every boot: call schema_upgrade.sh to apply any pending SQL updates.
DB_HOST="127.0.0.1"
DB_PORT="5432"
DB_NAME="ndex"
DB_USER="ndexserver"

if [[ ! -f /apps/ndex/config/.initialized ]]; then
  echo "==> First boot — configuring NDEx..."

  # Seed /apps/ndex/config/ from deploy image defaults.
  mkdir -p /apps/ndex/config
  cp -r /apps/ndex/default/config/. /apps/ndex/config/

  # Generate NDEx DB credentials from PG superuser password written by start.sh --postgres.
  # (start.sh --ndex is NOT called in devcontainer — Tomcat must not start alongside Jetty.)
  if [[ ! -f /etc/pg.otp ]]; then
    echo "ERROR: /etc/pg.otp not found — start.sh --postgres did not complete" >&2
    exit 1
  fi
  PG_SUPER_PASS=$(grep '^password:' /etc/pg.otp | cut -d' ' -f2)
  DB_PASS=$(tr -dc 'A-Za-z0-9' < /dev/urandom | head -c 16 || true)

  role_exists=$(PGPASSWORD="${PG_SUPER_PASS}" psql -h "${DB_HOST}" -p "${DB_PORT}" -U postgres \
    -tAc "SELECT 1 FROM pg_roles WHERE rolname='${DB_USER}'" 2>/dev/null || true)
  if [[ "${role_exists}" != "1" ]]; then
    PGPASSWORD="${PG_SUPER_PASS}" psql -h "${DB_HOST}" -p "${DB_PORT}" -U postgres \
      -c "CREATE ROLE ${DB_USER} WITH LOGIN PASSWORD '${DB_PASS}';"
  fi

  db_exists=$(PGPASSWORD="${PG_SUPER_PASS}" psql -h "${DB_HOST}" -p "${DB_PORT}" -U postgres \
    -tAc "SELECT 1 FROM pg_database WHERE datname='${DB_NAME}'" 2>/dev/null || true)
  if [[ "${db_exists}" != "1" ]]; then
    PGPASSWORD="${PG_SUPER_PASS}" psql -h "${DB_HOST}" -p "${DB_PORT}" -U postgres \
      -c "CREATE DATABASE ${DB_NAME} WITH OWNER ${DB_USER} ENCODING 'UTF8';"
    # Install required extensions as superuser (once on fresh DB)
    PGPASSWORD="${PG_SUPER_PASS}" psql -h "${DB_HOST}" -p "${DB_PORT}" -U postgres \
      --dbname "${DB_NAME}" <<SQL
CREATE SCHEMA IF NOT EXISTS core;
ALTER SCHEMA core OWNER TO ${DB_USER};
CREATE EXTENSION IF NOT EXISTS plpgsql WITH SCHEMA pg_catalog;
COMMENT ON EXTENSION plpgsql IS 'PL/pgSQL procedural language';
CREATE EXTENSION IF NOT EXISTS dblink WITH SCHEMA core;
COMMENT ON EXTENSION dblink IS 'connect to other PostgreSQL databases from within a database';
SQL
  fi

  # Extract Keycloak RSA public key (base64 DER) from cert written by start.sh --keycloak.
  if [[ ! -f /apps/keycloak/config/cert.pem ]]; then
    echo "ERROR: /apps/keycloak/config/cert.pem not found — start.sh --keycloak did not complete" >&2
    exit 1
  fi
  KC_PUBLIC_KEY=$(openssl x509 -in /apps/keycloak/config/cert.pem -pubkey -noout \
    | openssl rsa -pubin -pubout -outform DER | base64 -w 0)

  # Substitute all 8 placeholders in ndex.properties.
  sed -i "s|__NDEX_DB_URL__|jdbc:postgresql://${DB_HOST}:${DB_PORT}/${DB_NAME}|g" \
    /apps/ndex/config/ndex.properties
  sed -i "s|__NDEX_DB_USER__|${DB_USER}|g"                          /apps/ndex/config/ndex.properties
  sed -i "s|__NDEX_DB_PASSWORD__|${DB_PASS}|g"                      /apps/ndex/config/ndex.properties
  sed -i "s|__NDEX_SMTP_HOST__|localhost|g"                         /apps/ndex/config/ndex.properties
  sed -i "s|__NDEX_SOLR_URL__|http://localhost:8983/solr|g"         /apps/ndex/config/ndex.properties
  sed -i "s|__NDEX_HOST_URI__|http://localhost:${NDEX_PORT}|g"      /apps/ndex/config/ndex.properties
  sed -i "s|__NDEX_KEYCLOAK_ISSUER__|http://localhost:8085/realms/ndex|g" \
    /apps/ndex/config/ndex.properties
  sed -i "s|__NDEX_KEYCLOAK_PUBLIC_KEY__|${KC_PUBLIC_KEY}|g"        /apps/ndex/config/ndex.properties

  touch /apps/ndex/config/.initialized
  echo "==> ndex.properties configured at /apps/ndex/config/ndex.properties"
else
  # Re-boot: parse password from already-substituted ndex.properties
  DB_PASS=$(grep '^NdexDBDBPassword=' /apps/ndex/config/ndex.properties | cut -d= -f2-)
fi

# ── Every boot: apply schema upgrades ─────────────────────────────────────────
/usr/local/bin/schema_upgrade.sh "${DB_HOST}" "${DB_PORT}" "${DB_NAME}" "${DB_USER}" "${DB_PASS}"

# ── Phase 4: NDEx data directories ───────────────────────────────────────────
# NdexRoot=/apps/ndex/data — subdirs required by the NDEx Java server.
mkdir -p \
  /apps/ndex/data/conf \
  /apps/ndex/data/img/background \
  /apps/ndex/data/img/foreground \
  /apps/ndex/data/importer_exporter

# Support files NDEx reads from NdexRoot/conf/ at runtime.
cp /apps/ndex/config/ndex_importer_exporter.json /apps/ndex/data/conf/
cp /apps/ndex/config/forgot-password.txt         /apps/ndex/data/conf/

# ── Phase 5: ndex-object-model ────────────────────────────────────────────────
# ndex-object-model:3.0.0-SNAPSHOT is not published to any remote Maven repo;
# it must be built from source. Build once; result is cached in /root/.m2 and reused
# on subsequent boots of the same container (or a new container with /root/.m2 bind-mounted).
NDX_OBJ_JAR="/root/.m2/repository/org/ndexbio/ndex-object-model/3.0.0-SNAPSHOT/ndex-object-model-3.0.0-SNAPSHOT.jar"
if [[ ! -f "${NDX_OBJ_JAR}" ]]; then
  echo "==> Building ndex-object-model (first boot only)..."
  /usr/local/bin/build-ndex-object-model.sh
  echo "==> ndex-object-model installed."
fi

# ── Phase 6: Ready — NDEx must be started manually ───────────────────────────
touch /tmp/ndex-dev-ready
echo ""
echo "========================================================"
echo "  NDEx Devcontainer Ready!"
echo "  Core services: postgres, keycloak, solr, mailhog — RUNNING"
echo "  ndex-object-model — INSTALLED"
echo ""
echo "  To start the NDEx API server, open a terminal in the"
echo "  container and run:"
echo ""
echo "    ndex-server.sh start"
echo ""
echo "  To stop it:  ndex-server.sh stop"
echo "  Logs:        /apps/ndex/data/ndex.log"
echo "========================================================"
echo ""
exec sleep infinity
