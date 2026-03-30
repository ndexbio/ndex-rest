#!/usr/bin/env bash
# start.sh — NDEx deploy image container startup.
#
# Usage: start.sh [--ndex] [--postgres] [--keycloak] [--solr] [--mailhog]
#
# Each flag enables the corresponding service. Combine flags to run multiple
# services in one container (monolithic mode). Run with a single flag for
# distributed deployment.
#
# Phases:
#   1  Parse flags
#   2  Seed operational config dirs from defaults (first run per service)
#   3  Setup postgres — credential fills (config paths) + data init (sentinel-guarded)
#   4  Setup keycloak
#   5  Setup solr (first boot only)
#   6  Setup mailhog
#   7  Setup ndex
#   8  Launch OTP cleanup daemon
#   9  Assemble supervisord.conf
#  10  Exec supervisord (PID 1)
set -euo pipefail

# ── Phase 1: Parse flags ──────────────────────────────────────────────────────
ENABLE_NDEX=false
ENABLE_POSTGRES=false
ENABLE_KEYCLOAK=false
ENABLE_SOLR=false
ENABLE_MAILHOG=false

for arg in "$@"; do
  case "${arg}" in
    --ndex)     ENABLE_NDEX=true ;;
    --postgres) ENABLE_POSTGRES=true ;;
    --keycloak) ENABLE_KEYCLOAK=true ;;
    --solr)     ENABLE_SOLR=true ;;
    --mailhog)  ENABLE_MAILHOG=true ;;
    *) echo "Unknown flag: ${arg}" >&2; exit 1 ;;
  esac
done

if [[ "${ENABLE_NDEX}" == "false" && "${ENABLE_POSTGRES}" == "false" && \
      "${ENABLE_KEYCLOAK}" == "false" && "${ENABLE_SOLR}" == "false" && \
      "${ENABLE_MAILHOG}" == "false" ]]; then
  echo "ERROR: No services specified. Provide at least one flag: --ndex --postgres --keycloak --solr --mailhog" >&2
  exit 1
fi

echo ""
echo "========================================================"
echo "  NDEx Deploy Container — Starting Up"
echo "  Enabled: $(
  flags=()
  [[ "${ENABLE_POSTGRES}" == "true" ]] && flags+=("postgres")
  [[ "${ENABLE_KEYCLOAK}" == "true" ]] && flags+=("keycloak")
  [[ "${ENABLE_SOLR}" == "true" ]]     && flags+=("solr")
  [[ "${ENABLE_MAILHOG}" == "true" ]]  && flags+=("mailhog")
  [[ "${ENABLE_NDEX}" == "true" ]]     && flags+=("ndex")
  echo "${flags[*]}"
)"
echo "========================================================"
echo ""

# ── Helper: generate a random 16-char alphanumeric password ──────────────────
_gen_password() {
  # head exits after 16 bytes, causing tr to receive SIGPIPE; || true suppresses
  # the resulting pipefail exit so the function returns the generated password.
  tr -dc 'A-Za-z0-9' < /dev/urandom | head -c 16 || true
}

# ── Helper: ensure keycloak.conf present with NDEx deployment settings ────────
# -- /apps/keycloak/default/config/keycloak.conf is created in Dockerfile, it copies it from the keycloak installation in the image
_prepare_kc_conf() {
  [[ -f /apps/keycloak/config/keycloak.conf ]] || \
    cp /apps/keycloak/default/config/keycloak.conf /apps/keycloak/config/keycloak.conf
  if ! grep -q '^db=postgres' /apps/keycloak/config/keycloak.conf; then
    cat >> /apps/keycloak/config/keycloak.conf <<'KCSETTINGS'

# NDEx deployment settings — managed by start.sh
db=postgres
db-url=jdbc:postgresql://127.0.0.1:5432/keycloak
db-username=__KC_DB_USER__
db-password=__KC_DB_PASSWORD__
http-port=8085
http-management-port=9000
hostname-url=http://localhost:8085
hostname-admin-url=http://localhost:8085
health-enabled=true
log-level=WARN
KCSETTINGS
  fi
}

# ── Helper: seed config dir from default if no sentinel present ──────────────
_seed_config() {
  local svc="$1"
  local config_dir="/apps/${svc}/config"
  local default_dir="/apps/${svc}/default/config"
  if [[ ! -f "${config_dir}/.initialized" ]]; then
    echo "==> Seeding /apps/${svc}/config/ from defaults..."
    cp -r "${default_dir}/." "${config_dir}/"
    touch "${config_dir}/.initialized"
  fi
}

# ── Phase 2: Seed operational config dirs from defaults ──────────────────────
[[ "${ENABLE_NDEX}" == "true" ]]     && _seed_config ndex
[[ "${ENABLE_POSTGRES}" == "true" ]] && _seed_config postgres
[[ "${ENABLE_KEYCLOAK}" == "true" ]] && _seed_config keycloak
[[ "${ENABLE_SOLR}" == "true" ]]     && _seed_config solr
[[ "${ENABLE_MAILHOG}" == "true" ]]  && _seed_config mailhog

# ── Phase 3: Setup PostgreSQL ─────────────────────────────────────────────────
if [[ "${ENABLE_POSTGRES}" == "true" ]]; then
  PGDATA=/apps/postgres/data
  PG_PORT=5432

  if [[ ! -f "${PGDATA}/.initialized" ]]; then
    echo "==> Initializing PostgreSQL..."

    # ── Generate NDEx DB credentials ──────────────────────────────────────────
    NDEX_DB_USER="ndexserver"
    NDEX_DB_PASSWORD=$(_gen_password)
    if [[ "${ENABLE_NDEX}" == "true" ]]; then
      sed -i "s|__NDEX_DB_USER__|${NDEX_DB_USER}|g"         /apps/ndex/config/ndex.properties
      sed -i "s|__NDEX_DB_PASSWORD__|${NDEX_DB_PASSWORD}|g" /apps/ndex/config/ndex.properties
    else
      printf 'NdexDBUsername=%s\nNdexDBDBPassword=%s\n' "${NDEX_DB_USER}" "${NDEX_DB_PASSWORD}" \
        > /etc/ndex_db_user.otp
      chmod 600 /etc/ndex_db_user.otp
      echo "NOTICE: NDEx is not on this container — DB credentials written to /etc/ndex_db_user.otp"
      echo "        Set NdexDBUsername and NdexDBDBPassword in /apps/ndex/config/ndex.properties"
      echo "        on the NDEx container before starting it."
    fi

    # ── Generate Keycloak DB credentials ──────────────────────────────────────
    KC_DB_USER="keycloak"
    KC_DB_PASSWORD=$(_gen_password)
    if [[ "${ENABLE_KEYCLOAK}" == "true" ]]; then
      _prepare_kc_conf
      sed -i "s|__KC_DB_USER__|${KC_DB_USER}|g"         /apps/keycloak/config/keycloak.conf
      sed -i "s|__KC_DB_PASSWORD__|${KC_DB_PASSWORD}|g" /apps/keycloak/config/keycloak.conf
    else
      printf 'db-username=%s\ndb-password=%s\n' "${KC_DB_USER}" "${KC_DB_PASSWORD}" \
        > /etc/keycloak_db_user.otp
      chmod 600 /etc/keycloak_db_user.otp
      echo "NOTICE: Keycloak is not on this container — DB credentials written to /etc/keycloak_db_user.otp"
      echo "        Set db-username and db-password in /apps/keycloak/config/keycloak.conf"
      echo "        on the Keycloak container before starting it."
    fi

    gosu postgres initdb \
      -D "${PGDATA}" \
      --auth-host=scram-sha-256 \
      --auth-local=trust \
      -U postgres

    gosu postgres pg_ctl \
      -D "${PGDATA}" \
      -o "-p ${PG_PORT} -h '127.0.0.1,::1'" \
      start -w

    _wait=0
    until pg_isready -h 127.0.0.1 -p "${PG_PORT}" -U postgres -q; do
      sleep 1; _wait=$(( _wait + 1 ))
      (( _wait % 5 == 0 )) && echo "==> Waiting for PostgreSQL... (${_wait}s)"
    done

    SQL_DIR=/opt/ndex-install/sql \
    PG_PORT="${PG_PORT}" \
    PG_NDEX_USERNAME="${NDEX_DB_USER}" \
    PG_NDEX_PASSWORD="${NDEX_DB_PASSWORD}" \
      bash /usr/local/bin/init-postgres.sh

    # Create keycloak DB unconditionally — keycloak may be on a separate container
    gosu postgres psql -v ON_ERROR_STOP=1 --port "${PG_PORT}" --username postgres <<EOSQL
      CREATE ROLE ${KC_DB_USER} WITH LOGIN PASSWORD '${KC_DB_PASSWORD}';
      CREATE DATABASE keycloak WITH OWNER ${KC_DB_USER} ENCODING 'UTF8';
EOSQL

    PG_SUPERUSER_PASSWORD=$(_gen_password)
    gosu postgres psql -v ON_ERROR_STOP=1 --port "${PG_PORT}" --username postgres \
      -c "ALTER USER postgres PASSWORD '${PG_SUPERUSER_PASSWORD}';"

    # pg_hba.conf — if the seeded file still contains the default sentinel, generate
    # the hardened config; otherwise the user has customized it, so apply it as-is.
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
    echo "==> PostgreSQL initialized. Superuser password written to /etc/pg.otp (auto-deleted in 2 hours)."

    gosu postgres pg_ctl -D "${PGDATA}" stop -m fast -w

    touch "${PGDATA}/.initialized"
  fi
fi

# ── Phase 4: Setup Keycloak ───────────────────────────────────────────────────
if [[ "${ENABLE_KEYCLOAK}" == "true" ]]; then
  # Ensure keycloak.conf present with NDEx settings — guards ENABLE_POSTGRES=false path; no-op if Phase 3 ran
  _prepare_kc_conf

  # Symlink must always exist (every boot)
  if [[ -d /opt/keycloak/data && ! -L /opt/keycloak/data ]]; then
    cp -r /opt/keycloak/data/. /apps/keycloak/data/ 2>/dev/null || true
    rm -rf /opt/keycloak/data
  fi
  [[ ! -L /opt/keycloak/data ]] && ln -s /apps/keycloak/data /opt/keycloak/data

  # RSA key pair — idempotent (file existence); writes /apps/ndex/config/
  KC_KEY_DIR=/apps/ndex/config
  mkdir -p "${KC_KEY_DIR}"
  if [[ -f "${KC_KEY_DIR}/priv.key" && -f "${KC_KEY_DIR}/cert.pem" ]]; then
    echo "==> Using existing RSA key pair from ${KC_KEY_DIR}/"
  else
    echo "==> Generating RSA key pair..."
    openssl genpkey -algorithm RSA -pkeyopt rsa_keygen_bits:2048 \
      -out "${KC_KEY_DIR}/priv.key" 2>&1 \
      || { echo "ERROR: Failed to generate RSA private key" >&2; exit 1; }
    openssl req -x509 -new -key "${KC_KEY_DIR}/priv.key" \
      -days 3650 -subj "/CN=ndex-keycloak" \
      -out "${KC_KEY_DIR}/cert.pem" 2>&1 \
      || { echo "ERROR: Failed to generate self-signed certificate" >&2; exit 1; }
    echo "==> RSA key pair written to ${KC_KEY_DIR}/"
  fi

  # Bootstrap admin — idempotent (grep); writes /apps/keycloak/config/
  if ! grep -q '^bootstrap-admin-username' /apps/keycloak/config/keycloak.conf; then
    KC_ADMIN_PASSWORD=$(_gen_password)
    printf 'bootstrap-admin-username=admin\nbootstrap-admin-password=%s\n' "${KC_ADMIN_PASSWORD}" \
      >> /apps/keycloak/config/keycloak.conf
    printf 'user: admin\npassword: %s\nurl: http://localhost:8085/admin\n' \
      "${KC_ADMIN_PASSWORD}" > /etc/keycloak.otp
    chmod 600 /etc/keycloak.otp
    echo "==> Keycloak admin password written to /etc/keycloak.otp (auto-deleted in 2 hours)."
  fi

  # Every-boot copy (after bootstrap admin may have been appended — single copy sufficient)
  cp -r /apps/keycloak/config/providers/. /opt/keycloak/providers/ 2>/dev/null || true
  cp /apps/keycloak/config/keycloak.conf /opt/keycloak/conf/keycloak.conf
  chmod 600 /opt/keycloak/conf/keycloak.conf

  # Keycloak DATA init — all writes in this block are under /apps/keycloak/data/
  if [[ ! -f /apps/keycloak/data/.initialized ]]; then
    echo "==> Setting up Keycloak..."
    KC_PRIV_PEM=$(cat "${KC_KEY_DIR}/priv.key")
    KC_CERT_PEM=$(cat "${KC_KEY_DIR}/cert.pem")
    mkdir -p /apps/keycloak/data/import

    jq --arg priv "${KC_PRIV_PEM}" --arg cert "${KC_CERT_PEM}" \
      '.components["org.keycloak.keys.KeyProvider"] += [{
        "name": "ndex-rsa-signing-key",
        "providerId": "rsa",
        "subComponents": {},
        "config": {
          "privateKey": [$priv],
          "certificate": [$cert],
          "active": ["true"],
          "enabled": ["true"],
          "priority": ["200"],
          "algorithm": ["RS256"]
        }
      }]' /apps/keycloak/config/ndex-realm.json > /apps/keycloak/data/import/ndex-realm.json
    echo "==> Realm JSON patched with RSA key pair."

    touch /apps/keycloak/data/.initialized
  fi

  # Extract public key in base64-DER format for ndex.properties (every boot)
  if [[ -f "${KC_KEY_DIR}/cert.pem" ]]; then
    KEYCLOAK_PUBLIC_KEY=$(openssl x509 -in "${KC_KEY_DIR}/cert.pem" -pubkey -noout \
      | openssl rsa -pubin -pubout -outform DER | base64 -w 0)
    export KEYCLOAK_PUBLIC_KEY
    echo "==> RSA public key ready (${#KEYCLOAK_PUBLIC_KEY} chars)."
  fi
fi

# ── Phase 5: Setup Solr ───────────────────────────────────────────────────────
if [[ "${ENABLE_SOLR}" == "true" ]]; then
  if [[ ! -f /apps/solr/data/.initialized ]]; then
    echo "==> Setting up Solr..."

    # solr.xml must be at SOLR_HOME
    cp /apps/solr/config/solr.xml /apps/solr/data/
    chown solr:solr /apps/solr/data/solr.xml

    # Copy configsets (extracted from WAR at image build time)
    mkdir -p /apps/solr/data/configsets
    for cs in ndex-networks ndex-groups ndex-nodes ndex-users public-nfs private-nfs; do
      cp -r "/opt/ndex-install/solr-configsets/${cs}" /apps/solr/data/configsets/
    done
    # ndex-nodes-template = copy of ndex-nodes (template for per-network node indices)
    cp -r /apps/solr/data/configsets/ndex-nodes /apps/solr/data/configsets/ndex-nodes-template
    chown -R solr:solr /apps/solr/data

    # No security.json — Solr listens on 127.0.0.1 inside the container only,
    # and the NDEx SolrJ client does not support credentials. Authentication is
    # unnecessary when both services share the same network namespace.
    echo "==> Solr initialized (no authentication — localhost only)."

    touch /apps/solr/data/.initialized
  fi
fi

# ── Phase 6: Setup MailHog ────────────────────────────────────────────────────
if [[ "${ENABLE_MAILHOG}" == "true" ]]; then
  if [[ ! -f /apps/mailhog/config/auth ]]; then
    echo "==> Setting up MailHog..."

    # Generate MailHog auth file
    MAILHOG_ADMIN_PASSWORD=$(_gen_password)
    MAILHOG_HASH=$(htpasswd -nbB admin "${MAILHOG_ADMIN_PASSWORD}" | cut -d: -f2)
    printf 'admin:%s\n' "${MAILHOG_HASH}" > /apps/mailhog/config/auth

    printf 'user: admin\npassword: %s\nurl: http://localhost:8025\n' \
      "${MAILHOG_ADMIN_PASSWORD}" > /etc/mailhog.otp
    chmod 600 /etc/mailhog.otp
    echo "==> MailHog auth configured. Password written to /etc/mailhog.otp (auto-deleted in 2 hours)."
  fi
fi

# ── Phase 7: Setup NDEx ───────────────────────────────────────────────────────
if [[ "${ENABLE_NDEX}" == "true" ]]; then
  echo "==> Configuring NDEx..."

  # Ensure NDEx data subdirectories exist (NdexRoot subdirs referenced in ndex.properties)
  mkdir -p \
    /apps/ndex/data/conf \
    /apps/ndex/data/img/background \
    /apps/ndex/data/img/foreground \
    /apps/ndex/data/importer_exporter

  # Copy support files NDEx reads from NdexRoot/conf/
  cp /apps/ndex/config/ndex_importer_exporter.json /apps/ndex/data/conf/
  cp /apps/ndex/config/forgot-password.txt         /apps/ndex/data/conf/

  # Fill KEYCLOAK_PUBLIC_KEY placeholder in ndex.properties on first boot
  if [[ -f /apps/ndex/config/.initialized && -n "${KEYCLOAK_PUBLIC_KEY:-}" ]]; then
    sed -i "s|__KEYCLOAK_PUBLIC_KEY__|${KEYCLOAK_PUBLIC_KEY}|g" \
      /apps/ndex/config/ndex.properties
    echo "==> KEYCLOAK_PUBLIC_KEY filled in ndex.properties."
  fi

  # Wire ndexConfigurationPath into Tomcat's JNDI context so the NDEx webapp
  # can resolve java:comp/env/ndexConfigurationPath. The same path is exposed
  # as an OS env var by the supervisord ndex.conf for the Tomcat process.
  NDEX_CONFIG_FILE=/apps/ndex/config/ndex.properties
  mkdir -p /usr/local/tomcat/conf/Catalina/localhost
  cat > /usr/local/tomcat/conf/Catalina/localhost/ROOT.xml <<XML
<?xml version="1.0" encoding="UTF-8"?>
<Context>
  <!-- NDEx configuration file path — consumed by org.ndexbio.rest.Configuration
       as java:comp/env/ndexConfigurationPath. Must match the environment variable
       ndexConfigurationPath set in the supervisord ndex.conf for the Tomcat process. -->
  <Environment name="ndexConfigurationPath"
               value="${NDEX_CONFIG_FILE}"
               type="java.lang.String"
               override="false"/>
</Context>
XML
  echo "==> Tomcat JNDI context written: ndexConfigurationPath → ${NDEX_CONFIG_FILE}"
fi

# ── Phase 8: Launch OTP cleanup daemon ───────────────────────────────────────
# Scans /etc/*.otp every 5 seconds; deletes files older than 2 hours; exits
# when no OTP files remain.
(
  while true; do
    found=0
    for f in /etc/*.otp; do
      [[ -f "${f}" ]] || continue
      found=1
      age=$(( $(date +%s) - $(stat -c %Y "${f}") ))
      if [[ ${age} -ge 7200 ]]; then
        echo "[otp-cleanup] Purging expired OTP file: ${f}"
        rm -f "${f}"
      fi
    done
    [[ ${found} -eq 0 ]] && exit 0
    sleep 5
  done
) &

# ── Phase 9: Assemble supervisord.conf ────────────────────────────────────────
echo "==> Assembling supervisord.conf..."
SUPERVISORD_CONF=/tmp/supervisord.conf
cat /opt/ndex-supervisord/header.conf > "${SUPERVISORD_CONF}"
[[ "${ENABLE_POSTGRES}" == "true" ]] && cat /opt/ndex-supervisord/postgres.conf >> "${SUPERVISORD_CONF}"
[[ "${ENABLE_KEYCLOAK}" == "true" ]] && cat /opt/ndex-supervisord/keycloak.conf >> "${SUPERVISORD_CONF}"
[[ "${ENABLE_SOLR}" == "true" ]]     && cat /opt/ndex-supervisord/solr.conf     >> "${SUPERVISORD_CONF}"
[[ "${ENABLE_MAILHOG}" == "true" ]]  && cat /opt/ndex-supervisord/mailhog.conf  >> "${SUPERVISORD_CONF}"
[[ "${ENABLE_NDEX}" == "true" ]]     && cat /opt/ndex-supervisord/ndex.conf     >> "${SUPERVISORD_CONF}"

# ── Phase 10: Start supervisord, wait for all services to reach RUNNING ───────
/usr/bin/supervisord -c "${SUPERVISORD_CONF}" -n &
SUPERVISORD_PID=$!

EXPECTED_SERVICES=()
[[ "${ENABLE_POSTGRES}" == "true" ]] && EXPECTED_SERVICES+=(postgres)
[[ "${ENABLE_KEYCLOAK}" == "true" ]] && EXPECTED_SERVICES+=(keycloak)
[[ "${ENABLE_SOLR}"     == "true" ]] && EXPECTED_SERVICES+=(solr)
[[ "${ENABLE_MAILHOG}"  == "true" ]] && EXPECTED_SERVICES+=(mailhog)
[[ "${ENABLE_NDEX}"     == "true" ]] && EXPECTED_SERVICES+=(ndex)

TIMEOUT=300
ELAPSED=0
all_running=false
while (( ELAPSED < TIMEOUT )); do
    all_running=true
    for svc in "${EXPECTED_SERVICES[@]}"; do
        status=$(supervisorctl -c "${SUPERVISORD_CONF}" status "${svc}" 2>/dev/null | awk '{print $2}') || true
        [[ "${status}" != "RUNNING" ]] && { all_running=false; break; }
    done
    [[ "${all_running}" == "true" ]] && break
    echo "  NDEx Container initializing...  (${ELAPSED}s)"
    sleep 3
    (( ELAPSED += 3 )) || true
done

echo ""
if [[ "${all_running}" == "true" ]]; then
    echo "========================================================"
    echo "  NDEx Deploy Container Ready!"
    echo ""
    [[ "${ENABLE_NDEX}"     == "true" ]] && echo "    NDEx API (Tomcat):  http://localhost:8080"
    [[ "${ENABLE_KEYCLOAK}" == "true" ]] && echo "    Keycloak:           http://localhost:8085  (creds: /etc/keycloak.otp)"
    [[ "${ENABLE_MAILHOG}"  == "true" ]] && echo "    MailHog UI:         http://localhost:8025  (creds: /etc/mailhog.otp)"
    [[ "${ENABLE_SOLR}"     == "true" ]] && echo "    Solr Admin:         http://localhost:8983/solr  (no auth)"
    [[ "${ENABLE_POSTGRES}" == "true" ]] && echo "    PostgreSQL:         localhost:5432  (ndexserver pwd in ndex.properties)"
    echo "========================================================"
else
    echo "WARNING: Not all services reached RUNNING state after ${TIMEOUT}s" >&2
    supervisorctl -c "${SUPERVISORD_CONF}" status >&2 || true
fi
echo ""

wait "${SUPERVISORD_PID}"
