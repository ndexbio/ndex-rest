#!/usr/bin/env bash
# generate-ndex-config.sh — Writes /data/ndex/conf/ndex.properties.
# Called by entrypoint.sh on every container start (not guarded by sentinel)
# so config.toml changes take effect on restart without wiping data.
#
# Required env vars (exported by entrypoint.sh):
#   KEYCLOAK_PUBLIC_KEY, KEYCLOAK_PORT, KC_ISSUER_URL
#   NDEX_API_PORT, PG_PORT, PG_NDEX_PASSWORD
#   SOLR_PORT, SMTP_PORT, SMTP_FROM, DATA_ROOT
#   NDEX_LOG_LEVEL, NDEX_SYSTEM_USER, NDEX_SYSTEM_USER_PASSWORD, NDEX_SYSTEM_USER_EMAIL
#   NDEX_STORAGE_LIMIT, NEIGHBORHOOD_QUERY_URL, ADVANCED_QUERY_URL
#   NDEX_FEEDBACK_EMAIL, NDEX_FORGOT_PASSWORD_EMAIL
set -euo pipefail

CONF_FILE="${DATA_ROOT}/ndex/conf/ndex.properties"

if [[ -z "${KEYCLOAK_PUBLIC_KEY:-}" ]]; then
  echo "ERROR: KEYCLOAK_PUBLIC_KEY is not set." >&2
  exit 1
fi

# Conditional graph query service lines.
# Commented out when empty so getProperty() returns null (not ""), avoiding
# a runtime URL construction error inside the Java server.
if [[ -n "${NEIGHBORHOOD_QUERY_URL:-}" ]]; then
  _neighborhood_line="NeighborhoodQueryURL=${NEIGHBORHOOD_QUERY_URL}"
else
  _neighborhood_line="#NeighborhoodQueryURL="
fi

if [[ -n "${ADVANCED_QUERY_URL:-}" ]]; then
  _advanced_line="AdvancedQueryURL=${ADVANCED_QUERY_URL}"
else
  _advanced_line="#AdvancedQueryURL="
fi

echo "==> Writing ${CONF_FILE}..."
echo "    KEYCLOAK_PORT=${KEYCLOAK_PORT}, NDEX_API_PORT=${NDEX_API_PORT}"
[[ -n "${NEIGHBORHOOD_QUERY_URL:-}" ]] && echo "    NEIGHBORHOOD_QUERY_URL=${NEIGHBORHOOD_QUERY_URL}"
[[ -n "${ADVANCED_QUERY_URL:-}" ]]     && echo "    ADVANCED_QUERY_URL=${ADVANCED_QUERY_URL}"

cat > "${CONF_FILE}" <<EOF
## NDEx database — all services communicate via localhost in the single container
NdexDBURL=jdbc:postgresql://127.0.0.1:${PG_PORT}/ndex
NdexDBUsername=ndexserver
NdexDBDBPassword=${PG_NDEX_PASSWORD}

## System user — auto-created by NDEx server on first startup
NdexSystemUser=${NDEX_SYSTEM_USER}
NdexSystemUserPassword=${NDEX_SYSTEM_USER_PASSWORD}
NdexSystemUserEmail=${NDEX_SYSTEM_USER_EMAIL}

OrientDB-Use-Transactions=false

Feedback-Email=${NDEX_FEEDBACK_EMAIL}
Forgot-Password-Email=${NDEX_FORGOT_PASSWORD_EMAIL}
Forgot-Password-File=${DATA_ROOT}/ndex/conf/forgot-password.txt

Profile-Background-Width=670
Profile-Background-Height=200
Profile-Background-Path=${DATA_ROOT}/ndex/img/background/
Profile-Image-Width=100
Profile-Image-Height=125
Profile-Image-Path=${DATA_ROOT}/ndex/img/foreground/

## SMTP — MailHog (no auth) running on localhost inside the container
SMTP-Auth=false
SMTP-Host=localhost
SMTP-Port=${SMTP_PORT}
SMTP-From=${SMTP_FROM}

## NDEx server root and API config
NdexRoot=${DATA_ROOT}/ndex
Log-Level=${NDEX_LOG_LEVEL}
HostURI=http://localhost:${NDEX_API_PORT}
RESTAPIPrefix=/v3

## External graph query microservices. Commented out = endpoint disabled.
${_neighborhood_line}
${_advanced_line}

## Solr — running on localhost inside the container
SolrURL=http://localhost:${SOLR_PORT}/solr

BACKUP_DATABASE=FALSE

USE_GOOGLE_AUTHENTICATION=false

## Keycloak — issuer URL must match KC_HOSTNAME_URL set on the Keycloak service.
## Both are derived from [keycloak] issuer_url in config.toml.
USE_KEYCLOAK_AUTHENTICATION=true
KEYCLOAK_ISSUER=${KC_ISSUER_URL}/realms/ndex
ALLOWED_ADMIN_HOSTS=localhost
## RSA public key extracted from Keycloak realm at container startup (base64 DER, no PEM headers).
KEYCLOAK_PUBLIC_KEY=${KEYCLOAK_PUBLIC_KEY}

USER_STORAGE_LIMIT=${NDEX_STORAGE_LIMIT}

NDEX_KEY=haRwav-4macbe-dyqkev
DOI_PREFIX=10.5072/FK2
DOI_CREATOR=2Uq3hRFHXGUAPznseQ+7dA==
EOF

echo "==> ndex.properties written successfully."
