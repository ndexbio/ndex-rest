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
#   3  First boot only: seed /apps/ndex/config/, read DB credentials from
#      /etc/ndex_db_user.otp (written by start.sh --postgres), extract Keycloak
#      RSA public key from /apps/ndex/config/cert.pem (written by start.sh --keycloak),
#      fill all __PLACEHOLDER__ values in ndex.properties.
#   4  Ensure NDEx data subdirectories exist; copy support files to NdexRoot/conf/.
#   5  First boot only: build ndex-object-model:3.0.0-SNAPSHOT if not in Maven cache
#   6  exec mvn jetty:run from /workspaces/ndex-rest (becomes PID 1)
set -euo pipefail

NDEX_PORT="${NDEX_PORT:-8080}"

echo ""
echo "========================================================"
echo "  NDEx Devcontainer — Starting"
echo "========================================================"
echo ""

# ── Phase 1: Start supporting services ───────────────────────────────────────
echo "==> Starting support services (postgres, keycloak, solr, mailhog)..."
/usr/local/bin/start.sh --postgres --keycloak --solr --mailhog &

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
# Guarded by .initialized sentinel — same pattern as start.sh's _seed_config.
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

  # Read NDEx DB credentials from OTP file written by start.sh --postgres.
  # Format (two lines): NdexDBUsername=<user>  /  NdexDBDBPassword=<password>
  if [[ ! -f /etc/ndex_db_user.otp ]]; then
    echo "ERROR: /etc/ndex_db_user.otp not found — start.sh --postgres did not complete" >&2
    exit 1
  fi
  NDEX_DB_USER=$(grep '^NdexDBUsername='    /etc/ndex_db_user.otp | cut -d= -f2)
  NDEX_DB_PASSWORD=$(grep '^NdexDBDBPassword=' /etc/ndex_db_user.otp | cut -d= -f2)

  # Extract Keycloak RSA public key (base64 DER) from cert written by start.sh --keycloak.
  if [[ ! -f /apps/ndex/config/cert.pem ]]; then
    echo "ERROR: /apps/ndex/config/cert.pem not found — start.sh --keycloak did not complete" >&2
    exit 1
  fi
  KC_PUBLIC_KEY=$(openssl x509 -in /apps/ndex/config/cert.pem -pubkey -noout \
    | openssl rsa -pubin -pubout -outform DER | base64 -w 0)

  # Fill all placeholders in ndex.properties
  sed -i "s|__NDEX_DB_USER__|${NDEX_DB_USER}|g"          /apps/ndex/config/ndex.properties
  sed -i "s|__NDEX_DB_PASSWORD__|${NDEX_DB_PASSWORD}|g"  /apps/ndex/config/ndex.properties
  sed -i "s|__KEYCLOAK_PUBLIC_KEY__|${KC_PUBLIC_KEY}|g"  /apps/ndex/config/ndex.properties
  # Update HostURI port (template hardcodes 8080; NDEX_PORT env var may differ)
  sed -i "s|HostURI=http://localhost:8080|HostURI=http://localhost:${NDEX_PORT}|g" \
    /apps/ndex/config/ndex.properties

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

# ── Phase 6: Launch Jetty ─────────────────────────────────────────────────────
echo ""
echo "========================================================"
echo "  NDEx Devcontainer Ready!"
echo "  NDEx API (Jetty):  http://localhost:${NDEX_PORT}/v3"
echo "  Config:            /apps/ndex/config/ndex.properties"
echo "  Hot reload:        active (Jetty scans every 5s)"
echo "========================================================"
echo ""
export ndexConfigurationPath=/apps/ndex/config/ndex.properties
cd /workspaces/ndex-rest
exec mvn jetty:run -Dlogback.configurationFile=/apps/ndex/default/config/logback.xml
