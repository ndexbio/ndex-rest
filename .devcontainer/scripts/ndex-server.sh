#!/usr/bin/env bash
# ndex-server.sh — Start or stop the NDEx API server in the devcontainer.
#
# Usage:
#   ndex-server.sh start         — Start NDEx in the background (no-op if already running)
#   ndex-server.sh stop          — Stop NDEx and block until the process exits
#   ndex-server.sh wait [secs]   — Block until NDEx HTTP is responding (default timeout: 120s)
#
# All NDEx output (application logs + Maven console output) is written to
# /apps/ndex/data/ndex.log. Follow with: tail -f /apps/ndex/data/ndex.log

set -euo pipefail

NDEX_LOG=/apps/ndex/data/ndex.log
NDEX_PORT="${NDEX_PORT:-8080}"

_detect_ndex() {
  pgrep -f "jetty:run" > /dev/null 2>&1
}

_wait_http() {
  local timeout="${1:-120}"
  local elapsed=0
  echo -n "Waiting for NDEx HTTP on port ${NDEX_PORT}"
  while ! curl -s -X POST -H 'Content-Type: application/json' -d '{}' \
      "http://localhost:${NDEX_PORT}/v3/search/files" \
      -o /dev/null 2>/dev/null; do
    if (( elapsed >= timeout )); then
      echo ""
      echo "ERROR: NDEx did not respond within ${timeout}s" >&2
      exit 1
    fi
    echo -n "."
    sleep 3
    (( elapsed += 3 )) || true
  done
  echo " ready."
}

case "${1:-}" in
  start)
    # Block until dev-entrypoint.sh Phase 6 writes the ready sentinel.
    if [[ ! -f /tmp/ndex-dev-ready ]]; then
      echo "Waiting for devcontainer initialization to complete..."
      until [[ -f /tmp/ndex-dev-ready ]]; do
        sleep 2
      done
      echo "Devcontainer ready — starting NDEx server."
    fi
    if _detect_ndex; then
      echo "NDEx server is already running. Logs: ${NDEX_LOG}"
      exit 0
    fi
    export ndexConfigurationPath=/apps/ndex/config/ndex.properties
    cd /workspaces/ndex-rest
    nohup mvn jetty:run \
      -Dlogback.configurationFile=/apps/ndex/default/config/logback-dev.xml \
      >> "${NDEX_LOG}" 2>&1 &
    echo "NDEx server started. Logs: ${NDEX_LOG}"
    ;;
  wait)
    _wait_http "${2:-120}"
    ;;
  stop)
    if ! _detect_ndex; then
      echo "No NDEx server process found."
      exit 0
    fi
    pkill -f "jetty:run"
    echo -n "Stopping NDEx server"
    while _detect_ndex; do
      echo -n "."
      sleep 1
    done
    echo " done."
    ;;
  *)
    echo "Usage: ndex-server.sh {start|stop|wait [timeout_secs]}" >&2
    exit 1
    ;;
esac
