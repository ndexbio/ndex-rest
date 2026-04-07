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
#      /etc/pg.otp (written by start.sh --postgres), create ndex role/DB, load SQL
#      schema, extract Keycloak RSA public key from /apps/keycloak/config/cert.pem
#      (written by start.sh --keycloak), substitute all 8 __PLACEHOLDER__ values in
#      ndex.properties.
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

# ── Phase 3: First-boot ndex.properties configuration ─────────────────────────
# Guarded by .initialized sentinel — same pattern as start.sh's Phase 7.
# On subsequent boots the sentinel exists and ndex.properties already has the
# correct credentials. start.sh skips postgres re-init so no OTP files are
# written after first boot.
if [[ ! -f /apps/ndex/config/.initialized ]]; then
  echo "==> First boot — configuring NDEx..."

  # Seed /apps/ndex/config/ from deploy image defaults.
  # Copies: ndex.properties (with __PLACEHOLDERS__), ndex_importer_exporter.json,
  # forgot-password.txt from /apps/ndex/default/config/.
  mkdir -p /apps/ndex/config
  cp -r /apps/ndex/default/config/. /apps/ndex/config/

  # Generate NDEx DB credentials using the PG superuser password written by start.sh --postgres.
  # (start.sh --ndex is NOT called in devcontainer — Tomcat must not start alongside Jetty.)
  if [[ ! -f /etc/pg.otp ]]; then
    echo "ERROR: /etc/pg.otp not found — start.sh --postgres did not complete" >&2
    exit 1
  fi
  PG_SUPER_PASS=$(grep '^password:' /etc/pg.otp | cut -d' ' -f2)

  DB_HOST="127.0.0.1"
  DB_PORT="5432"
  DB_NAME="ndex"
  DB_USER="ndexserver"
  DB_PASS=$(tr -dc 'A-Za-z0-9' < /dev/urandom | head -c 16 || true)

  PGPASSWORD="${PG_SUPER_PASS}" psql -h "${DB_HOST}" -p "${DB_PORT}" -U postgres \
    -c "CREATE ROLE ${DB_USER} WITH LOGIN PASSWORD '${DB_PASS}';" \
    -c "CREATE DATABASE ${DB_NAME} WITH OWNER ${DB_USER} ENCODING 'UTF8';"

  # Load NDEx schema into the new database.
  # Base schema requires superuser (CREATE EXTENSION dblink); updates run as ndexserver.
  SQL_DIR=/opt/ndex-install/sql
  _ndex_psql() {
    PGPASSWORD="${DB_PASS}" psql -v ON_ERROR_STOP=1 \
      -h "${DB_HOST}" -p "${DB_PORT}" -U "${DB_USER}" -d "${DB_NAME}" "$@"
  }
  _super_psql() {
    PGPASSWORD="${PG_SUPER_PASS}" psql -v ON_ERROR_STOP=1 \
      -h "${DB_HOST}" -p "${DB_PORT}" -U postgres -d "${DB_NAME}" "$@"
  }
  _strip_compat() {
    perl -0777 -pe '
      s/\s*WITH\s*\(\s*OIDS\s*=\s*FALSE\s*\)//g;
      s/[ \t]*SET\s+default_with_oids[^\n]*\n//g;
      s/[ \t]*COMMENT ON EXTENSION[^\n]*\n//g;
    '
  }
  echo "==> Loading NDEx schema..."
  _strip_compat < "${SQL_DIR}/ndex_db_schema_v2_5_3.sql" | _super_psql
  _super_psql -c "ALTER DATABASE ${DB_NAME} SET search_path TO core, public;"
  SCHEMA_BASE_VERSION="2.5.3"
  while IFS= read -r -d '' f; do
    fname=$(basename "${f}")
    from_ver=$(echo "${fname}" | sed 's/schema_update_\([0-9.]*\)_to_.*/\1/')
    if printf '%s\n' "${SCHEMA_BASE_VERSION}" "${from_ver}" | sort -V -C 2>/dev/null || \
       echo "${SCHEMA_BASE_VERSION}" | grep -q "^${from_ver}[.]"; then
      echo "    -> ${fname}"
      _strip_compat < "${f}" | _ndex_psql
    else
      echo "    (skip ${fname} — predates base schema ${SCHEMA_BASE_VERSION})"
    fi
  done < <(find "${SQL_DIR}" -maxdepth 1 -name "schema_update_*.sql" -print0 | sort -z)
  echo "==> NDEx schema initialized."

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
fi

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
echo ""
echo "========================================================"
echo "  NDEx Devcontainer Ready!"
echo "  Core services: postgres, keycloak, solr, mailhog — RUNNING"
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
