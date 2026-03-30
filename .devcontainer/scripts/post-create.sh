#!/usr/bin/env bash
# post-create.sh — runs after the devcontainer starts.
# All service initialization (PostgreSQL schema, Keycloak setup, ndex.properties
# generation) is handled by entrypoint.sh running as PID 1 inside the container.
# This script only needs to:
#   1. Wait for entrypoint.sh to finish generating ndex.properties
#   2. Register ndexConfigurationPath in the shell environment
#   3. Pre-warm the Maven dependency cache
set -euo pipefail

NDEX_PROPS="/data/ndex/conf/ndex.properties"

# ── 1. Wait for container initialization ─────────────────────────────────────
# entrypoint.sh runs in parallel — it takes 2-4 minutes on first boot
# (Keycloak startup + RSA key extraction). Poll until ndex.properties appears.
echo "==> Waiting for container initialization to complete..."
MAX_WAIT=300
ELAPSED=0
until [[ -f "${NDEX_PROPS}" ]]; do
  if [[ ${ELAPSED} -ge ${MAX_WAIT} ]]; then
    echo "ERROR: Container initialization did not complete within ${MAX_WAIT}s." >&2
    echo "       Check 'docker logs <container>' for details." >&2
    exit 1
  fi
  sleep 5
  ELAPSED=$((ELAPSED + 5))
  echo "   ... waiting (${ELAPSED}s elapsed)"
done
echo "==> Container initialization complete."

# ── 2. Register ndexConfigurationPath in shell environment ────────────────────
echo "==> Registering ndexConfigurationPath in shell environment..."
echo "export ndexConfigurationPath=${NDEX_PROPS}" > /etc/profile.d/ndex.sh
export ndexConfigurationPath="${NDEX_PROPS}"

# ── 3. Pre-warm the Maven dependency cache ────────────────────────────────────
echo "==> Pre-warming Maven dependency cache (resolve only, no compile)..."
cd /workspaces/ndex-rest
mvn dependency:resolve -T 4 || echo "WARN: Maven dependency resolution had warnings (non-fatal)"

echo ""
echo "========================================================"
echo "  NDEx DevContainer Ready!"
echo ""
echo "  Services:"
echo "    NDEx API (Jetty):  http://localhost:8080   (start manually below)"
echo "    Keycloak:          http://localhost:8085   (admin/admin)"
echo "    MailHog UI:        http://localhost:8025"
echo "    Solr Admin:        http://localhost:8983/solr"
echo "    PostgreSQL:        localhost:5432  (ndexserver/password)"
echo ""
echo "  To start the NDEx server:"
echo "    cd /workspaces/ndex-rest && mvn jetty:run"
echo ""
echo "  ndexConfigurationPath is pre-set in your environment."
echo "========================================================"
echo ""
