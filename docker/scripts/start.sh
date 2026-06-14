#!/usr/bin/env bash
# start.sh — NDEx deploy image container startup.
#
# Usage: start.sh [--ndex] [--postgres] [--keycloak] [--solr] [--mailhog]
#                 [--config <path>] [--disable-credential-removal]
#
# Each flag enables the corresponding service. Combine flags to run multiple
# services in one container (monolithic mode). Run with a single flag for
# distributed deployment.
#
# --config <path>  Optional TOML config file for external PostgreSQL credentials
#                  and service endpoint overrides. Required when running --ndex or
#                  --keycloak without --postgres (external DB path).
#
# Phases:
#   1  Parse flags + config validation
#   2  Seed operational config dirs from defaults (first run per service)
#   3  Setup postgres — cluster init via init-postgres.sh (sentinel-guarded)
#   4  Setup keycloak — DB init (sentinel-guarded), RSA keys, bootstrap admin
#   5  Setup solr (first boot only)
#   6  Setup mailhog
#   7  Setup ndex — self-contained: config seeding, DB creds, schema, placeholder substitution
#      Stop init-time postgres before supervisord
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
DISABLE_CREDENTIAL_REMOVAL=false
NDEX_CONFIG_FILE=""

while [[ $# -gt 0 ]]; do
  case "$1" in
    --ndex)     ENABLE_NDEX=true ;;
    --postgres) ENABLE_POSTGRES=true ;;
    --keycloak) ENABLE_KEYCLOAK=true ;;
    --solr)     ENABLE_SOLR=true ;;
    --mailhog)  ENABLE_MAILHOG=true ;;
    --disable-credential-removal) DISABLE_CREDENTIAL_REMOVAL=true ;;
    --config)
      shift
      [[ -z "${1:-}" ]] && { echo "ERROR: --config requires a path argument" >&2; exit 1; }
      NDEX_CONFIG_FILE="$1"
      ;;
    *) echo "Unknown flag: $1" >&2; exit 1 ;;
  esac
  shift
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

# ── Helper: read a value from a TOML file ────────────────────────────────────
_toml_get() {
  # Usage: _toml_get <file> <section> <key>
  local file="$1" section="$2" key="$3"
  awk -F' *= *' -v s="[${section}]" -v k="${key}" '
    $0==s          { in_s=1; next }
    /^\[/          { in_s=0 }
    in_s && $1==k  { v=$0; sub(/^[^=]*= */, "", v); gsub(/^"|"$|^'"'"'|'"'"'$/, "", v); print v; exit }
  ' "${file}"
}

# ── Helper: test whether a TOML section exists ────────────────────────────────
_toml_has_section() {
  # Usage: _toml_has_section <file> <section>  — returns 0 if section exists
  grep -q "^\[${2}\]" "${1}"
}

# ── Helper: read a root-level (no-section) value from a TOML file ────────────
_toml_get_root() {
  # Usage: _toml_get_root <file> <key>
  # Reads keys that appear before the first [section] header.
  local file="$1" key="$2"
  awk -F' *= *' -v k="${key}" '
    /^\[/   { exit }
    $1==k   { v=$0; sub(/^[^=]*= */, "", v); gsub(/^"|"$|^'"'"'|'"'"'$/, "", v); print v; exit }
  ' "${file}"
}

# ── Helper: append a timestamped corruption event to the persistent log ───────
_pg_log_corruption_event() {
  # Usage: _pg_log_corruption_event <pgdata> <event-description>
  # Log survives PGDATA wipes — written one level above PGDATA (/apps/postgres/).
  local pgdata="$1" event="$2"
  local logfile
  logfile="$(dirname "${pgdata}")/corruption.log"
  local entry
  entry="$(date -u +"%Y-%m-%dT%H:%M:%SZ") CORRUPTION: ${event}"
  mkdir -p "$(dirname "${logfile}")" 2>/dev/null || true
  printf '%s\n' "${entry}" | tee -a "${logfile}" >&2 || true
}

# ── Helper: start postgres with escalating corruption recovery ────────────────
# Attempts 1 → 2 → 3. Attempt 3 (wipe+reinit) requires reset_data_when_corrupt=true
# in the top-level --config TOML. Always leaves postgres running on return.
_pg_start_resilient() {
  local pgdata="$1" port="$2"
  local startlog="/tmp/pg_resilient_start.log"

  # ── Fast path: already running (e.g. postgres survived container restart) ─
  if pg_isready -h 127.0.0.1 -p "${port}" -U postgres -q 2>/dev/null; then
    echo "==> PostgreSQL is already running."
    return 0
  fi

  # ── Remove stale postmaster.pid left by abrupt shutdown (SIGKILL/OOM) ────
  # After docker kill, postgres has no chance to clean up its pid file.
  # pg_ctl refuses to start cleanly with a stale file; remove it if the
  # recorded process is dead.
  local pidfile="${pgdata}/postmaster.pid"
  if [[ -f "${pidfile}" ]]; then
    local stale_pid
    stale_pid=$(head -1 "${pidfile}" 2>/dev/null || true)
    if [[ -n "${stale_pid}" ]] && ! kill -0 "${stale_pid}" 2>/dev/null; then
      echo "==> Removing stale postmaster.pid (PID ${stale_pid} is no longer running)..."
      rm -f "${pidfile}"
    fi
  fi

  # ── Attempt 1: normal start (crash recovery handles unclean shutdown) ─────
  echo "==> Starting PostgreSQL..."
  if gosu postgres pg_ctl -D "${pgdata}" -o "-p ${port} -h '127.0.0.1,::1'" \
       -l "${startlog}" start -w -t 60 2>&1; then
    echo "==> PostgreSQL started successfully."
    return 0
  fi

  # pg_ctl can report failure even when postgres actually started (e.g. pid
  # race on slow systems). Re-check before escalating to recovery.
  if pg_isready -h 127.0.0.1 -p "${port}" -U postgres -q 2>/dev/null; then
    echo "==> PostgreSQL is running (pg_ctl exited non-zero but server is ready)."
    return 0
  fi

  echo "" >&2
  echo "================================================================" >&2
  echo "WARN: PostgreSQL failed to start — checking for data corruption" >&2
  echo "================================================================" >&2
  cat "${startlog}" >&2 || true
  _pg_log_corruption_event "${pgdata}" "startup failed — attempting recovery"

  # ── Attempt 2: pg_resetwal repairs corrupted WAL or pg_control ───────────
  # A failed Attempt 1 may leave a fresh postmaster.pid (postgres started,
  # created the pid file, then PANICed before cleaning it up). Remove it now
  # so pg_resetwal does not refuse to run.
  if [[ -f "${pidfile}" ]]; then
    local stale_pid2
    stale_pid2=$(head -1 "${pidfile}" 2>/dev/null || true)
    if [[ -n "${stale_pid2}" ]] && ! kill -0 "${stale_pid2}" 2>/dev/null; then
      echo "==> Removing stale postmaster.pid (PID ${stale_pid2}) left by failed startup..." >&2
      rm -f "${pidfile}"
    fi
  fi

  echo "==> [recovery] Attempting pg_resetwal to repair WAL/control corruption..." >&2
  local resetlog="/tmp/pg_resetwal.log"
  if gosu postgres pg_resetwal -f "${pgdata}" > "${resetlog}" 2>&1; then
    echo "WARN: pg_resetwal completed. Database may contain inconsistent data." >&2
    cat "${resetlog}" >&2 || true
    rm -f "${startlog}"
    if gosu postgres pg_ctl -D "${pgdata}" -o "-p ${port} -h '127.0.0.1,::1'" \
         -l "${startlog}" start -w -t 60 2>&1; then
      _pg_log_corruption_event "${pgdata}" "pg_resetwal recovery succeeded — data integrity NOT guaranteed"
      echo "WARN: [recovery] PostgreSQL started after pg_resetwal." >&2
      echo "WARN: [recovery] Database may be inconsistent. Run pg_dump as soon as possible." >&2
      return 0
    fi
    echo "WARN: PostgreSQL still failed after pg_resetwal." >&2
    cat "${startlog}" >&2 || true
  else
    echo "WARN: pg_resetwal failed: $(cat "${resetlog}")" >&2
  fi

  # ── Check reset_data_when_corrupt flag before attempting wipe ─────────────
  local allow_wipe=false
  if [[ -n "${NDEX_CONFIG_FILE:-}" ]]; then
    local flag_val
    flag_val=$(_toml_get_root "${NDEX_CONFIG_FILE}" reset_data_when_corrupt 2>/dev/null || true)
    [[ "${flag_val}" == "true" ]] && allow_wipe=true
  fi

  if [[ "${allow_wipe}" == "false" ]]; then
    _pg_log_corruption_event "${pgdata}" "UNRECOVERABLE — manual intervention required (reset_data_when_corrupt=false)"
    echo "" >&2
    echo "================================================================" >&2
    echo "CRITICAL: PostgreSQL data is corrupt and cannot be automatically" >&2
    echo "CRITICAL: recovered. The container will stop." >&2
    echo "CRITICAL:" >&2
    echo "CRITICAL: To enable automatic data wipe and re-initialization," >&2
    echo "CRITICAL: add the following to the top of your --config TOML file" >&2
    echo "CRITICAL: and restart:" >&2
    echo "CRITICAL:" >&2
    echo "CRITICAL:   reset_data_when_corrupt = true" >&2
    echo "CRITICAL:" >&2
    echo "CRITICAL: WARNING: setting this will permanently delete ALL data in:" >&2
    echo "CRITICAL:   ${pgdata}" >&2
    [[ "${ENABLE_NDEX:-false}"     == "true" ]] && echo "CRITICAL:   /apps/ndex/data/" >&2
    [[ "${ENABLE_KEYCLOAK:-false}" == "true" ]] && echo "CRITICAL:   /apps/keycloak/data/" >&2
    echo "CRITICAL: Consider restoring from backup instead." >&2
    echo "================================================================" >&2
    exit 1
  fi

  # ── Attempt 3: wipe PGDATA + dependent service data, reinitialize ─────────
  echo "" >&2
  echo "================================================================" >&2
  echo "CRITICAL: All recovery attempts failed. Wiping PGDATA — DATA LOSS" >&2
  echo "================================================================" >&2
  _pg_log_corruption_event "${pgdata}" "PGDATA wiped — DATA LOSS, all database contents deleted"

  find "${pgdata}" -mindepth 1 -delete 2>/dev/null || true

  if [[ "${ENABLE_KEYCLOAK:-false}" == "true" ]]; then
    rm -f /apps/keycloak/config/.db_initialized
    rm -rf /apps/keycloak/data/* /apps/keycloak/data/.[!.]* 2>/dev/null || true
  fi
  if [[ "${ENABLE_NDEX:-false}" == "true" ]]; then
    rm -f /apps/ndex/config/.initialized
    rm -rf /apps/ndex/data/* /apps/ndex/data/.[!.]* 2>/dev/null || true
  fi

  echo "==> [recovery] Reinitializing PostgreSQL cluster from scratch..."
  PGDATA="${pgdata}" PG_PORT="${port}" bash /usr/local/bin/init-postgres.sh
  touch "${pgdata}/.initialized"
  echo "==> [recovery] PostgreSQL reinitialized. All prior data has been lost."
}

# ── Config validation ─────────────────────────────────────────────────────────
if [[ -n "${NDEX_CONFIG_FILE}" && ! -f "${NDEX_CONFIG_FILE}" ]]; then
  echo "ERROR: config file not found: ${NDEX_CONFIG_FILE}" >&2; exit 1
fi

if [[ "${ENABLE_POSTGRES}" == "true" && -n "${NDEX_CONFIG_FILE}" ]]; then
  if [[ "${ENABLE_NDEX}" == "true" ]] && _toml_has_section "${NDEX_CONFIG_FILE}" ndexDb; then
    echo "ERROR: [ndexDb] in config conflicts with --postgres + --ndex. Local PG auto-generates NDEx credentials — remove [ndexDb] from config." >&2; exit 1
  fi
  if [[ "${ENABLE_KEYCLOAK}" == "true" ]] && _toml_has_section "${NDEX_CONFIG_FILE}" keycloakDb; then
    echo "ERROR: [keycloakDb] in config conflicts with --postgres + --keycloak. Local PG auto-generates Keycloak credentials — remove [keycloakDb] from config." >&2; exit 1
  fi
fi

if [[ "${ENABLE_NDEX}" == "true" && "${ENABLE_POSTGRES}" == "false" ]]; then
  if [[ -z "${NDEX_CONFIG_FILE}" ]] || ! _toml_has_section "${NDEX_CONFIG_FILE}" ndexDb; then
    echo "ERROR: --ndex requires either --postgres (local DB) or --config with [ndexDb] section (external DB)." >&2; exit 1
  fi
fi

if [[ "${ENABLE_KEYCLOAK}" == "true" && "${ENABLE_POSTGRES}" == "false" ]]; then
  if [[ -z "${NDEX_CONFIG_FILE}" ]] || ! _toml_has_section "${NDEX_CONFIG_FILE}" keycloakDb; then
    echo "ERROR: --keycloak requires either --postgres (local DB) or --config with [keycloakDb] section (external DB)." >&2; exit 1
  fi
fi

# ── Helper: generate keycloak.conf with provided connection details ────────────
# _prepare_kc_conf <db_host> <db_port> <db_name> <db_user> <db_pass> <kc_hostname>
_prepare_kc_conf() {
  local db_host="$1" db_port="$2" db_name="$3" db_user="$4" db_pass="$5" kc_host="$6"
  cat > /apps/keycloak/config/keycloak.conf <<KC_EOF
# NDEX GENERATED — do not edit manually
db=postgres
db-url=jdbc:postgresql://${db_host}:${db_port}/${db_name}
db-username=${db_user}
db-password=${db_pass}
http-port=8085
http-management-port=9000
hostname-url=http://${kc_host}:8085
hostname-admin-url=http://${kc_host}:8085
health-enabled=true
log-level=io.netty:WARN,io.vertx:WARN
KC_EOF
}

# ── Helper: seed config dir from default if no sentinel present ──────────────
_seed_config() {
  local svc="$1"
  local config_dir="/apps/${svc}/config"
  local default_dir="/opt/defaults/${svc}/config"
  if [[ ! -f "${config_dir}/.initialized" ]]; then
    echo "==> Seeding /apps/${svc}/config/ from defaults..."
    cp -r "${default_dir}/." "${config_dir}/"
    touch "${config_dir}/.initialized"
  fi
}

# ── Phase 2: Seed operational config dirs from defaults ──────────────────────
[[ "${ENABLE_POSTGRES}" == "true" ]] && _seed_config postgres
[[ "${ENABLE_KEYCLOAK}" == "true" ]] && _seed_config keycloak
[[ "${ENABLE_SOLR}" == "true" ]]     && _seed_config solr
[[ "${ENABLE_MAILHOG}" == "true" ]]  && _seed_config mailhog

# ── Phase 3: Setup PostgreSQL ─────────────────────────────────────────────────
if [[ "${ENABLE_POSTGRES}" == "true" ]]; then
  PGDATA=/apps/postgres/data

  if [[ ! -f "${PGDATA}/.initialized" ]]; then
    echo "==> Initializing PostgreSQL..."
    PGDATA="${PGDATA}" PG_PORT=5432 bash /usr/local/bin/init-postgres.sh
    touch "${PGDATA}/.initialized"
  else
    # Restart path: start postgres with corruption detection and recovery.
    # _pg_start_resilient always returns with postgres running (or exits the container
    # if corruption is unrecoverable and reset_data_when_corrupt=false).
    # Starting here (before Phase 4 and 7) means any wipe+reinit fires before
    # Keycloak and NDEx DB setup, so those phases see wiped sentinels and re-run.
    _pg_start_resilient "${PGDATA}" 5432
  fi
fi

# ── Phase 4: Setup Keycloak ───────────────────────────────────────────────────
if [[ "${ENABLE_KEYCLOAK}" == "true" ]]; then
  # ── DB init (first boot only) ─────────────────────────────────────────────
  if [[ ! -f /apps/keycloak/config/.db_initialized ]]; then
    if [[ -n "${NDEX_CONFIG_FILE}" ]] && _toml_has_section "${NDEX_CONFIG_FILE}" keycloakDb; then
      # ── External PG path ───────────────────────────────────────────────────
      KC_HOST=$(_toml_get "${NDEX_CONFIG_FILE}" keycloakDb host)
      KC_PORT=$(_toml_get "${NDEX_CONFIG_FILE}" keycloakDb port)
      KC_DB=$(_toml_get   "${NDEX_CONFIG_FILE}" keycloakDb name)
      KC_USER=$(_toml_get "${NDEX_CONFIG_FILE}" keycloakDb user)
      KC_PASS=$(_toml_get "${NDEX_CONFIG_FILE}" keycloakDb password)
      KC_HOSTNAME=$(_toml_get "${NDEX_CONFIG_FILE}" keycloak hostName 2>/dev/null || true)
      [[ -z "${KC_HOSTNAME}" ]] && KC_HOSTNAME="localhost"

      PGPASSWORD="${KC_PASS}" psql -h "${KC_HOST}" -p "${KC_PORT}" -U "${KC_USER}" \
        -d "${KC_DB}" -c "SELECT 1" > /dev/null \
        || { echo "ERROR: Cannot connect to Keycloak DB at ${KC_HOST}:${KC_PORT}/${KC_DB}" >&2; exit 1; }
      echo "==> Keycloak external DB connection verified: ${KC_HOST}:${KC_PORT}/${KC_DB}"

      _prepare_kc_conf "${KC_HOST}" "${KC_PORT}" "${KC_DB}" "${KC_USER}" "${KC_PASS}" "${KC_HOSTNAME}"
    else
      # ── Local PG path ──────────────────────────────────────────────────────
      [[ ! -f /etc/pg.otp ]] && \
        { echo "ERROR: /etc/pg.otp not found — --postgres must run before --keycloak on same container" >&2; exit 1; }
      PG_SUPER_PASS=$(grep '^password:' /etc/pg.otp | cut -d' ' -f2)

      KC_DB_USER="keycloak"
      KC_DB_PASSWORD=$(_gen_password)

      role_exists=$(PGPASSWORD="${PG_SUPER_PASS}" psql -h 127.0.0.1 -p 5432 -U postgres \
        -tAc "SELECT 1 FROM pg_roles WHERE rolname='${KC_DB_USER}'" 2>/dev/null || true)
      if [[ "${role_exists}" != "1" ]]; then
        PGPASSWORD="${PG_SUPER_PASS}" psql -h 127.0.0.1 -p 5432 -U postgres \
          -c "CREATE ROLE ${KC_DB_USER} WITH LOGIN PASSWORD '${KC_DB_PASSWORD}';"
      fi

      db_exists=$(PGPASSWORD="${PG_SUPER_PASS}" psql -h 127.0.0.1 -p 5432 -U postgres \
        -tAc "SELECT 1 FROM pg_database WHERE datname='keycloak'" 2>/dev/null || true)
      if [[ "${db_exists}" != "1" ]]; then
        PGPASSWORD="${PG_SUPER_PASS}" psql -h 127.0.0.1 -p 5432 -U postgres \
          -c "CREATE DATABASE keycloak WITH OWNER ${KC_DB_USER} ENCODING 'UTF8';"
      fi

      _prepare_kc_conf "127.0.0.1" "5432" "keycloak" "${KC_DB_USER}" "${KC_DB_PASSWORD}" "localhost"
    fi

    touch /apps/keycloak/config/.db_initialized
  fi

  # Symlink must always exist (every boot)
  if [[ -d /opt/keycloak/data && ! -L /opt/keycloak/data ]]; then
    cp -r /opt/keycloak/data/. /apps/keycloak/data/ 2>/dev/null || true
    rm -rf /opt/keycloak/data
  fi
  [[ ! -L /opt/keycloak/data ]] && ln -s /apps/keycloak/data /opt/keycloak/data

  # RSA key pair — idempotent (file existence); writes /apps/keycloak/config/
  KC_KEY_DIR=/apps/keycloak/config
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

  # ── DB credential resolution (every boot) ────────────────────────────────────
  # Credentials must be available before both the .initialized block (first boot)
  # and the every-boot schema_upgrade.sh call.
  if [[ -n "${NDEX_CONFIG_FILE}" ]] && _toml_has_section "${NDEX_CONFIG_FILE}" ndexDb; then
    # External PG: read from config.toml [ndexDb]
    DB_HOST=$(_toml_get "${NDEX_CONFIG_FILE}" ndexDb host)
    DB_PORT=$(_toml_get "${NDEX_CONFIG_FILE}" ndexDb port)
    DB_NAME=$(_toml_get "${NDEX_CONFIG_FILE}" ndexDb name)
    DB_USER=$(_toml_get "${NDEX_CONFIG_FILE}" ndexDb user)
    DB_PASS=$(_toml_get "${NDEX_CONFIG_FILE}" ndexDb password)
  elif [[ -f /apps/ndex/config/.initialized ]]; then
    # Local PG re-boot: parse credentials already substituted into ndex.properties
    DB_URL_RAW=$(grep '^NdexDBURL=' /apps/ndex/config/ndex.properties | cut -d= -f2-)
    DB_HOST=$(echo "${DB_URL_RAW}" | sed 's|.*://\([^:/]*\).*|\1|')
    DB_PORT=$(echo "${DB_URL_RAW}" | sed 's|.*://[^:]*:\([0-9]*\)/.*|\1|')
    DB_NAME=$(echo "${DB_URL_RAW}" | sed 's|.*/\([^/]*\)$|\1|')
    DB_USER=$(grep '^NdexDBUsername='  /apps/ndex/config/ndex.properties | cut -d= -f2-)
    DB_PASS=$(grep '^NdexDBDBPassword=' /apps/ndex/config/ndex.properties | cut -d= -f2-)
  else
    # Local PG first boot: credentials generated inside .initialized block below
    DB_HOST="127.0.0.1"
    DB_PORT="5432"
    DB_NAME="ndex"
    DB_USER="ndexserver"
    DB_PASS=""  # assigned inside .initialized block
  fi

  if [[ ! -f /apps/ndex/config/.initialized ]]; then
    echo "==> Initializing NDEx configuration..."

    mkdir -p /apps/ndex/config
    cp -r /opt/defaults/ndex/config/. /apps/ndex/config/

    if [[ -n "${NDEX_CONFIG_FILE}" ]] && _toml_has_section "${NDEX_CONFIG_FILE}" ndexDb; then
      # External PG: role and DB must already exist; verify connectivity
      PGPASSWORD="${DB_PASS}" psql -h "${DB_HOST}" -p "${DB_PORT}" -U "${DB_USER}" \
        -d "${DB_NAME}" -c "SELECT 1" > /dev/null \
        || { echo "ERROR: Cannot connect to NDEx DB at ${DB_HOST}:${DB_PORT}/${DB_NAME}" >&2; exit 1; }
      echo "==> NDEx external DB connection verified: ${DB_HOST}:${DB_PORT}/${DB_NAME}"
    else
      # Local PG: generate credentials, create role + DB (idempotent existence checks)
      [[ ! -f /etc/pg.otp ]] && \
        { echo "ERROR: /etc/pg.otp not found — --postgres must run before --ndex on same container" >&2; exit 1; }
      PG_SUPER_PASS=$(grep '^password:' /etc/pg.otp | cut -d' ' -f2)
      DB_PASS=$(_gen_password)

      role_exists=$(PGPASSWORD="${PG_SUPER_PASS}" psql -h 127.0.0.1 -p 5432 -U postgres \
        -tAc "SELECT 1 FROM pg_roles WHERE rolname='${DB_USER}'" 2>/dev/null || true)
      if [[ "${role_exists}" != "1" ]]; then
        PGPASSWORD="${PG_SUPER_PASS}" psql -h 127.0.0.1 -p 5432 -U postgres \
          -c "CREATE ROLE ${DB_USER} WITH LOGIN PASSWORD '${DB_PASS}';"
      fi

      db_exists=$(PGPASSWORD="${PG_SUPER_PASS}" psql -h 127.0.0.1 -p 5432 -U postgres \
        -tAc "SELECT 1 FROM pg_database WHERE datname='${DB_NAME}'" 2>/dev/null || true)
      if [[ "${db_exists}" != "1" ]]; then
        PGPASSWORD="${PG_SUPER_PASS}" psql -h 127.0.0.1 -p 5432 -U postgres \
          -c "CREATE DATABASE ${DB_NAME} WITH OWNER ${DB_USER} ENCODING 'UTF8';"
        # Install required extensions as superuser (must be done once on a fresh DB)
        PGPASSWORD="${PG_SUPER_PASS}" psql -h 127.0.0.1 -p 5432 -U postgres \
          --dbname "${DB_NAME}" <<SQL
CREATE SCHEMA IF NOT EXISTS core;
ALTER SCHEMA core OWNER TO ${DB_USER};
CREATE EXTENSION IF NOT EXISTS plpgsql WITH SCHEMA pg_catalog;
COMMENT ON EXTENSION plpgsql IS 'PL/pgSQL procedural language';
CREATE EXTENSION IF NOT EXISTS dblink WITH SCHEMA core;
COMMENT ON EXTENSION dblink IS 'connect to other PostgreSQL databases from within a database';
SQL
      fi
    fi

    # ── Substitute DB placeholders ─────────────────────────────────────────────
    sed -i "s|__NDEX_DB_URL__|jdbc:postgresql://${DB_HOST}:${DB_PORT}/${DB_NAME}|g" \
      /apps/ndex/config/ndex.properties
    sed -i "s|__NDEX_DB_USER__|${DB_USER}|g"      /apps/ndex/config/ndex.properties
    sed -i "s|__NDEX_DB_PASSWORD__|${DB_PASS}|g"  /apps/ndex/config/ndex.properties

    # ── Service endpoint properties ───────────────────────────────────────────
    if [[ -n "${NDEX_CONFIG_FILE}" ]] && _toml_has_section "${NDEX_CONFIG_FILE}" ndex; then
      # Microservices: all 5 values provided in config.toml [ndex] section
      SMTP_HOST=$(_toml_get     "${NDEX_CONFIG_FILE}" ndex smtpHost)
      SOLR_URL=$(_toml_get      "${NDEX_CONFIG_FILE}" ndex solrUrl)
      HOST_URI=$(_toml_get      "${NDEX_CONFIG_FILE}" ndex hostUri)
      KC_ISSUER=$(_toml_get     "${NDEX_CONFIG_FILE}" ndex keycloakIssuer)
      KC_PUBLIC_KEY=$(_toml_get "${NDEX_CONFIG_FILE}" ndex keycloakPublicKey)
      KC_CLIENT_ID=$(_toml_get  "${NDEX_CONFIG_FILE}" ndex keycloakClientId)
    else
      # Monolithic: use localhost defaults; KC cert values applied every boot below
      SMTP_HOST="localhost"
      SOLR_URL="http://localhost:8983/solr"
      HOST_URI="http://localhost:8080"
      KC_ISSUER=""
      KC_PUBLIC_KEY=""
      KC_CLIENT_ID=""
    fi

    sed -i "s|__NDEX_SMTP_HOST__|${SMTP_HOST}|g"               /apps/ndex/config/ndex.properties
    sed -i "s|__NDEX_SOLR_URL__|${SOLR_URL}|g"                 /apps/ndex/config/ndex.properties
    sed -i "s|__NDEX_HOST_URI__|${HOST_URI}|g"                 /apps/ndex/config/ndex.properties
    sed -i "s|__NDEX_KEYCLOAK_ISSUER__|${KC_ISSUER}|g"         /apps/ndex/config/ndex.properties
    sed -i "s|__NDEX_KEYCLOAK_PUBLIC_KEY__|${KC_PUBLIC_KEY}|g" /apps/ndex/config/ndex.properties
    sed -i "s|__NDEX_KEYCLOAK_CLIENT_ID__|${KC_CLIENT_ID}|g"   /apps/ndex/config/ndex.properties

    touch /apps/ndex/config/.initialized
    echo "==> NDEx configuration initialized."
  fi

  # ── Every boot: sync Keycloak auth settings from cert.pem ────────────────────
  # Runs on every boot (not just first) so an admin can provide cert.pem via a
  # volume mount and restart to enable Keycloak auth without resetting state.
  # Skipped for microservices deployments that supply KC settings via --config [ndex].
  if [[ -z "${NDEX_CONFIG_FILE}" ]] || ! _toml_has_section "${NDEX_CONFIG_FILE}" ndex; then
    if [[ "${ENABLE_KEYCLOAK}" == "true" ]]; then
      if [[ -f /apps/keycloak/config/cert.pem ]]; then
        _kc_pub=$(openssl x509 -in /apps/keycloak/config/cert.pem -pubkey -noout \
          | openssl rsa -pubin -pubout -outform DER | base64 -w 0)
        sed -i "s|^KEYCLOAK_PUBLIC_KEY=.*|KEYCLOAK_PUBLIC_KEY=${_kc_pub}|"                /apps/ndex/config/ndex.properties
        sed -i "s|^KEYCLOAK_ISSUER=.*|KEYCLOAK_ISSUER=http://localhost:8085/realms/ndex|"  /apps/ndex/config/ndex.properties
        sed -i "s|^KEYCLOAK_CLIENT_ID=.*|KEYCLOAK_CLIENT_ID=ndex-app|"                    /apps/ndex/config/ndex.properties
        sed -i "s|^USE_KEYCLOAK_AUTHENTICATION=.*|USE_KEYCLOAK_AUTHENTICATION=true|"       /apps/ndex/config/ndex.properties
        echo "==> Keycloak authentication configured from cert.pem."
      else
        echo "WARN: --keycloak enabled but cert.pem not found at /apps/keycloak/config/cert.pem" >&2
        echo "WARN: Keycloak authentication disabled. Mount cert.pem and restart to enable." >&2
        sed -i "s|^USE_KEYCLOAK_AUTHENTICATION=.*|USE_KEYCLOAK_AUTHENTICATION=false|" /apps/ndex/config/ndex.properties
      fi
    else
      sed -i "s|^USE_KEYCLOAK_AUTHENTICATION=.*|USE_KEYCLOAK_AUTHENTICATION=false|"  /apps/ndex/config/ndex.properties
    fi
  fi

  # ── Every boot: apply schema upgrades ─────────────────────────────────────────
  # On first boot: init-postgres.sh left postgres running.
  # On restart: Phase 3 _pg_start_resilient already started postgres (with recovery if needed).
  # Either way postgres must be running here — assert it rather than retry.
  if [[ "${ENABLE_POSTGRES}" == "true" ]] && [[ "${DB_HOST}" == "127.0.0.1" ]]; then
    if ! pg_isready -h 127.0.0.1 -p "${DB_PORT}" -U postgres -q 2>/dev/null; then
      echo "ERROR: PostgreSQL is not running before schema upgrade — Phase 3 should have ensured this" >&2
      exit 1
    fi
  fi
  /usr/local/bin/schema_upgrade.sh "${DB_HOST}" "${DB_PORT}" "${DB_NAME}" "${DB_USER}" "${DB_PASS}"

  # Every-boot: ensure data dirs and support files are present
  mkdir -p \
    /apps/ndex/data/conf \
    /apps/ndex/data/img/background \
    /apps/ndex/data/img/foreground \
    /apps/ndex/data/importer_exporter

  cp /apps/ndex/config/ndex_importer_exporter.json /apps/ndex/data/conf/
  cp /apps/ndex/config/forgot-password.txt         /apps/ndex/data/conf/

  # Wire ndexConfigurationPath into Tomcat's JNDI context so the NDEx webapp
  # can resolve java:comp/env/ndexConfigurationPath.
  NDEX_PROPS_FILE=/apps/ndex/config/ndex.properties
  mkdir -p /usr/local/tomcat/conf/Catalina/localhost
  cat > /usr/local/tomcat/conf/Catalina/localhost/ROOT.xml <<XML
<?xml version="1.0" encoding="UTF-8"?>
<Context>
  <!-- NDEx configuration file path — consumed by org.ndexbio.rest.Configuration
       as java:comp/env/ndexConfigurationPath. Must match the environment variable
       ndexConfigurationPath set in the supervisord ndex.conf for the Tomcat process. -->
  <Environment name="ndexConfigurationPath"
               value="${NDEX_PROPS_FILE}"
               type="java.lang.String"
               override="false"/>
</Context>
XML
  echo "==> Tomcat JNDI context written: ndexConfigurationPath → ${NDEX_PROPS_FILE}"

  # Resolve CATALINA_OPTS: logback flag always prepended; JVM tuning from
  # ndex_catalina_opts in config.toml, falling back to percentage-based defaults.
  _logback_opt="-Dlogback.configurationFile=/apps/ndex/config/logback.xml"
  _default_jvm_opts="-XX:InitialRAMPercentage=25.0 -XX:MaxRAMPercentage=60.0 -XX:+ExitOnOutOfMemoryError"
  _user_jvm_opts=""
  if [[ -n "${NDEX_CONFIG_FILE}" ]]; then
    _user_jvm_opts=$(_toml_get_root "${NDEX_CONFIG_FILE}" ndex_catalina_opts)
  fi
  NDEX_CATALINA_OPTS="${_logback_opt} ${_user_jvm_opts:-${_default_jvm_opts}}"
  echo "==> Tomcat CATALINA_OPTS: ${NDEX_CATALINA_OPTS}"
fi

# ── Stop init-time PostgreSQL before supervisord takes over ───────────────────
# On first boot, init-postgres.sh leaves postgres running so that --keycloak and
# --ndex service methods can create their roles/databases. Stop it here so
# supervisord can manage the process from Phase 9 onward.
if [[ "${ENABLE_POSTGRES}" == "true" ]] && \
   pg_isready -h 127.0.0.1 -p 5432 -U postgres -q 2>/dev/null; then
  echo "==> Stopping init-time PostgreSQL before supervisord takes over..."
  gosu postgres pg_ctl -D /apps/postgres/data stop -m fast -w
fi

# ── Phase 8: Launch OTP cleanup daemon ───────────────────────────────────────
# Scans /etc/*.otp every 5 seconds; deletes files older than 2 hours; exits
# when no OTP files remain. Suppressed when --disable-credential-removal is set
# (e.g. devcontainer — developer may need credentials throughout the session).
if [[ "${DISABLE_CREDENTIAL_REMOVAL}" == "false" ]]; then
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
fi

# ── Phase 9: Assemble supervisord.conf ────────────────────────────────────────
echo "==> Assembling supervisord.conf..."
SUPERVISORD_CONF=/etc/supervisor/supervisord.conf
cat /opt/ndex-supervisord/header.conf > "${SUPERVISORD_CONF}"
[[ "${ENABLE_POSTGRES}" == "true" ]] && cat /opt/ndex-supervisord/postgres.conf >> "${SUPERVISORD_CONF}"
[[ "${ENABLE_KEYCLOAK}" == "true" ]] && cat /opt/ndex-supervisord/keycloak.conf >> "${SUPERVISORD_CONF}"
[[ "${ENABLE_SOLR}" == "true" ]]     && cat /opt/ndex-supervisord/solr.conf     >> "${SUPERVISORD_CONF}"
[[ "${ENABLE_MAILHOG}" == "true" ]]  && cat /opt/ndex-supervisord/mailhog.conf  >> "${SUPERVISORD_CONF}"
[[ "${ENABLE_NDEX}" == "true" ]]     && sed "s|__NDEX_CATALINA_OPTS__|${NDEX_CATALINA_OPTS}|" /opt/ndex-supervisord/ndex.conf >> "${SUPERVISORD_CONF}"

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
    touch /tmp/ndex-services-ready
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
