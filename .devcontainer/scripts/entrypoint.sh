#!/usr/bin/env bash
# entrypoint.sh — NDEx all-in-one container startup.
#
# Phases:
#   0  Parse config.toml
#   1  Create /data directory structure
#   2  Init PostgreSQL data directory (first boot only)
#   3  Start PostgreSQL (temporary, via pg_ctl)
#   4  Apply database schema (first boot only)
#   5  Copy Solr configsets (first boot only)
#   6  Copy NDEx support files (every boot)
#   7  Generate RSA key pair and patch Keycloak realm JSON
#   8  Generate ndex.properties
#   9  Write initialization sentinel (first boot only)
#  10  Stop temporary PostgreSQL
#  11  Export env vars for supervisord
#  12  exec supervisord (becomes PID 1 process manager)
set -euo pipefail

# ── TOML helper ──────────────────────────────────────────────────────────────
# Usage: toml_get "section.key" [default]
toml_get() {
  local keypath="$1"
  local default="${2:-}"
  python3 - <<PYEOF 2>/dev/null || echo "${default}"
import toml, sys
try:
    c = toml.load("${NDEX_CONFIG}")
    keys = "${keypath}".split(".")
    v = c
    for k in keys:
        v = v[k]
    val = str(v).lower() if isinstance(v, bool) else str(v)
    print(val)
except Exception:
    print("${default}")
PYEOF
}

# ── Phase 0: Parse config ─────────────────────────────────────────────────────
echo ""
echo "========================================================"
echo "  NDEx Container — Starting Up"
echo "========================================================"
echo ""

NDEX_CONFIG="${NDEX_CONFIG:-/etc/ndex/config.toml}"
if [[ ! -f "${NDEX_CONFIG}" ]]; then
  echo "WARN: ${NDEX_CONFIG} not found — using defaults from /etc/ndex/config.toml.example"
  NDEX_CONFIG=/etc/ndex/config.toml.example
fi
echo "==> Config: ${NDEX_CONFIG}"

DATA_ROOT=$(toml_get "data.root" "/data")
PG_PORT=$(toml_get "postgres.port" "5432")
PG_SUPERUSER_PASSWORD=$(toml_get "postgres.superuser_password" "postgres")
PG_NDEX_PASSWORD=$(toml_get "postgres.ndexserver_password" "password")
KC_PORT=$(toml_get "keycloak.port" "8085")
KC_MGMT_PORT=$(toml_get "keycloak.management_port" "9000")
KC_ADMIN_USER=$(toml_get "keycloak.admin_username" "admin")
KC_ADMIN_PASSWORD=$(toml_get "keycloak.admin_password" "admin")
KC_ISSUER_URL=$(toml_get "keycloak.issuer_url" "http://localhost:${KC_PORT}")
KC_REALM_JSON=$(toml_get "keycloak.realm_json" "/opt/ndex-install/ndex-realm.json")
SOLR_PORT=$(toml_get "solr.port" "8983")
SMTP_PORT=$(toml_get "smtp.port" "1025")
MAILHOG_UI_PORT=$(toml_get "smtp.ui_port" "8025")
SMTP_FROM=$(toml_get "smtp.from_address" "dev-support@ndexbio.org")
NDEX_PORT=$(toml_get "ndex.port" "8080")
NDEX_AUTOSTART="${NDEX_AUTOSTART:-false}"
NDEX_LOG_LEVEL=$(toml_get "ndex.log_level" "INFO")
NDEX_SYSTEM_USER=$(toml_get "ndex.system_user" "ndexadministrator")
NDEX_SYSTEM_USER_PASSWORD=$(toml_get "ndex.system_user_password" "admin")
NDEX_SYSTEM_USER_EMAIL=$(toml_get "ndex.system_user_email" "dev-support@ndexbio.org")
NDEX_STORAGE_LIMIT=$(toml_get "ndex.user_storage_limit_gb" "50")
NEIGHBORHOOD_QUERY_URL=$(toml_get "ndex.neighborhood_query_url" "")
ADVANCED_QUERY_URL=$(toml_get "ndex.advanced_query_url" "")
NDEX_FEEDBACK_EMAIL=$(toml_get "ndex.email.feedback_address" "support@ndexbio.org")
NDEX_FORGOT_PASSWORD_EMAIL=$(toml_get "ndex.email.forgot_password_address" "support@ndexbio.org")

PGDATA="${DATA_ROOT}/postgres"
SENTINEL="${DATA_ROOT}/.initialized"

# ── Phase 1: Create /data directory structure ─────────────────────────────────
echo "==> Preparing data directories under ${DATA_ROOT}..."
mkdir -p \
  "${DATA_ROOT}/postgres" \
  "${DATA_ROOT}/keycloak" \
  "${DATA_ROOT}/solr/data/configsets" \
  "${DATA_ROOT}/ndex/conf" \
  "${DATA_ROOT}/ndex/data" \
  "${DATA_ROOT}/ndex/img/background" \
  "${DATA_ROOT}/ndex/img/foreground" \
  "${DATA_ROOT}/ndex/importer_exporter"
chown postgres:postgres "${DATA_ROOT}/postgres"
# Solr data owned by the solr user created in the Dockerfile
chown -R solr:solr "${DATA_ROOT}/solr"

# Redirect Keycloak's data directory to the persistent volume.
# On first boot, copy any files Keycloak ships in its data/ dir (e.g. built
# provider caches). On subsequent boots the symlink already exists.
if [[ -d /opt/keycloak/data && ! -L /opt/keycloak/data ]]; then
  cp -r /opt/keycloak/data/. "${DATA_ROOT}/keycloak/" 2>/dev/null || true
  rm -rf /opt/keycloak/data
fi
if [[ ! -L /opt/keycloak/data ]]; then
  ln -s "${DATA_ROOT}/keycloak" /opt/keycloak/data
fi

# ── Phase 2: Initialize PostgreSQL data directory (first boot only) ───────────
if [[ ! -f "${PGDATA}/PG_VERSION" ]]; then
  echo "==> Initializing PostgreSQL data directory..."
  gosu postgres /usr/lib/postgresql/14/bin/initdb \
    -D "${PGDATA}" \
    --auth-host=md5 \
    --auth-local=trust \
    -U postgres

  # Allow ndexserver to authenticate via md5 from localhost (TCP connections used
  # by Keycloak and NDEx running in the same container).
  cat >> "${PGDATA}/pg_hba.conf" <<'HBA'
# ndexserver — used by Keycloak (keycloak db) and NDEx (ndex db)
host    all    ndexserver    127.0.0.1/32    md5
host    all    ndexserver    ::1/128         md5
HBA
fi

# ── Phase 3: Start PostgreSQL ─────────────────────────────────────────────────
echo "==> Starting PostgreSQL on port ${PG_PORT}..."
gosu postgres /usr/lib/postgresql/14/bin/pg_ctl \
  -D "${PGDATA}" \
  -l "${DATA_ROOT}/postgres/postgresql.log" \
  -o "-p ${PG_PORT} -h '127.0.0.1,::1'" \
  start -w

until pg_isready -h 127.0.0.1 -p "${PG_PORT}" -U postgres -q; do sleep 1; done
echo "==> PostgreSQL is ready."

# ── Phase 4: Apply database schema (first boot only) ──────────────────────────
if [[ ! -f "${SENTINEL}" ]]; then
  echo "==> Initializing NDEx database schema..."
  SQL_DIR=/opt/ndex-install/sql \
  PG_PORT="${PG_PORT}" \
  PG_NDEX_PASSWORD="${PG_NDEX_PASSWORD}" \
    bash /usr/local/bin/init-postgres.sh
fi

# ── Phase 5: Copy Solr configsets (first boot only) ───────────────────────────
if [[ ! -f "${SENTINEL}" ]]; then
  echo "==> Installing Solr configsets..."
  # solr.xml belongs at SOLR_HOME (/data/solr/data/), not inside configsets/
  cp /opt/ndex-install/solr-configsets/solr.xml "${DATA_ROOT}/solr/data/"
  # Copy the 6 named configsets (skip README and solr.xml at root of solr-configsets/)
  for cs in ndex-networks ndex-groups ndex-nodes ndex-users public-nfs private-nfs; do
    cp -r "/opt/ndex-install/solr-configsets/${cs}" "${DATA_ROOT}/solr/data/configsets/"
  done
  # ndex-nodes-template = copy of ndex-nodes (used as template for per-network node indices)
  cp -r "${DATA_ROOT}/solr/data/configsets/ndex-nodes" \
        "${DATA_ROOT}/solr/data/configsets/ndex-nodes-template"
  chown -R solr:solr "${DATA_ROOT}/solr"
fi

# ── Phase 6: Copy NDEx support files (every boot) ─────────────────────────────
cp /opt/ndex-install/config/ndex_importer_exporter.json "${DATA_ROOT}/ndex/conf/"
cp /opt/ndex-install/config/forgot-password.txt         "${DATA_ROOT}/ndex/conf/"

# ── Phase 7: Generate RSA key pair and patch Keycloak realm JSON ──────────────
echo "==> Generating RSA key pair for Keycloak realm signing..."
if [[ ! -f "${SENTINEL}" ]]; then
  # Generate RSA private key (PKCS8 PEM) and self-signed certificate
  KC_KEY_DIR="${DATA_ROOT}/ndex/conf"
  mkdir -p "${KC_KEY_DIR}"

  # Check if user has provided their own key pair
  if [[ -f "${KC_KEY_DIR}/priv.key" && -f "${KC_KEY_DIR}/cert.pem" ]]; then
    echo "==> Using existing RSA key pair from ${KC_KEY_DIR}/"
  else
    echo "==> Generating new RSA key pair..."
    openssl genpkey -algorithm RSA -pkeyopt rsa_keygen_bits:2048 \
      -out "${KC_KEY_DIR}/priv.key" 2>/dev/null
    openssl req -x509 -new -key "${KC_KEY_DIR}/priv.key" \
      -days 3650 -subj "/CN=ndex-keycloak" \
      -out "${KC_KEY_DIR}/cert.pem" 2>/dev/null
    echo "==> RSA key pair generated."
  fi

  # Patch realm JSON: inject rsa key provider component with inline PEM
  KC_PRIV_PEM=$(cat "${KC_KEY_DIR}/priv.key")
  KC_CERT_PEM=$(cat "${KC_KEY_DIR}/cert.pem")
  KC_REALM_PATCHED="${DATA_ROOT}/keycloak/import/$(basename "${KC_REALM_JSON}")"
  mkdir -p "${DATA_ROOT}/keycloak/import"

  jq --arg priv "${KC_PRIV_PEM}" --arg cert "${KC_CERT_PEM}" \
    '.components["org.keycloak.keys.KeyProvider"] += [{
      "name": "ndex-rsa-signing-key",
      "providerId": "rsa",
      "providerType": "org.keycloak.keys.KeyProvider",
      "parentId": "ndex",
      "config": {
        "privateKey": [$priv],
        "certificate": [$cert],
        "active": ["true"],
        "enabled": ["true"],
        "priority": ["200"],
        "algorithm": ["RS256"]
      }
    }]' "${KC_REALM_JSON}" > "${KC_REALM_PATCHED}"
  echo "==> Realm JSON patched with RSA key pair."
fi

# Extract public key in base64-DER format (what NDEx expects in ndex.properties)
KC_KEY_DIR="${DATA_ROOT}/ndex/conf"
if [[ ! -f "${KC_KEY_DIR}/cert.pem" ]]; then
  echo "ERROR: ${KC_KEY_DIR}/cert.pem not found — cannot extract RSA public key." >&2
  exit 1
fi
KEYCLOAK_PUBLIC_KEY=$(openssl x509 -in "${KC_KEY_DIR}/cert.pem" -pubkey -noout \
  | openssl rsa -pubin -pubout -outform DER | base64 -w 0)
echo "==> RSA public key extracted (${#KEYCLOAK_PUBLIC_KEY} chars)."

# ── Phase 8: Generate ndex.properties ────────────────────────────────────────
echo "==> Generating ndex.properties..."
export KEYCLOAK_PUBLIC_KEY
export KEYCLOAK_PORT="${KC_PORT}"
export KC_ISSUER_URL
export NDEX_API_PORT="${NDEX_PORT}"
export PG_PORT PG_NDEX_PASSWORD
export SOLR_PORT SMTP_PORT SMTP_FROM
export DATA_ROOT
export NDEX_LOG_LEVEL
export NDEX_SYSTEM_USER NDEX_SYSTEM_USER_PASSWORD NDEX_SYSTEM_USER_EMAIL
export NDEX_STORAGE_LIMIT
export NEIGHBORHOOD_QUERY_URL ADVANCED_QUERY_URL
export NDEX_FEEDBACK_EMAIL NDEX_FORGOT_PASSWORD_EMAIL
bash /usr/local/bin/generate-ndex-config.sh

# ── Phase 9: Write sentinel (first boot only) ─────────────────────────────────
if [[ ! -f "${SENTINEL}" ]]; then
  touch "${SENTINEL}"
  echo "==> First-boot initialization complete."
fi

# ── Phase 10: Stop temporary PostgreSQL ──────────────────────────────────────
# supervisord will restart it as a managed process.
echo "==> Stopping temporary PostgreSQL..."
gosu postgres /usr/lib/postgresql/14/bin/pg_ctl -D "${PGDATA}" stop -m fast -w

# ── Phase 11: Export env vars for supervisord %(ENV_...)s interpolation ────────
export PG_PORT
export KC_DB_PASSWORD="${PG_NDEX_PASSWORD}"
export KC_ADMIN_USER KC_ADMIN_PASSWORD KC_ISSUER_URL
export KC_HTTP_PORT="${KC_PORT}"
export KC_MGMT_PORT="${KC_MGMT_PORT}"
export SOLR_PORT SMTP_PORT MAILHOG_UI_PORT
export NDEX_AUTOSTART
export ndexConfigurationPath="${DATA_ROOT}/ndex/conf/ndex.properties"

# ── Phase 12: Hand off to supervisord ────────────────────────────────────────
echo ""
echo "========================================================"
echo "  NDEx Container Ready!"
echo ""
echo "  Services (starting via supervisord):"
echo "    NDEx API (Jetty):  http://localhost:${NDEX_PORT}"
echo "    Keycloak:          http://localhost:${KC_PORT}  (${KC_ADMIN_USER}/**)"
echo "    MailHog UI:        http://localhost:${MAILHOG_UI_PORT}"
echo "    Solr Admin:        http://localhost:${SOLR_PORT}/solr"
echo "    PostgreSQL:        localhost:${PG_PORT}  (ndexserver/***)"
echo ""
if [[ "${NDEX_AUTOSTART}" == "false" ]]; then
  echo "  NDEx server: NOT auto-started (devcontainer mode)"
  echo "  To start:    cd /workspaces/ndex-rest && mvn jetty:run"
else
  echo "  NDEx server: auto-starting"
fi
echo "========================================================"
echo ""

exec /usr/bin/supervisord -c /etc/supervisord.conf -n
