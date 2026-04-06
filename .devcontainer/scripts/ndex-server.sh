#!/usr/bin/env bash
# ndex-server.sh — Start or stop the NDEx API server in the devcontainer.
#
# Usage:
#   ndex-server.sh start   — Start NDEx in the background (no-op if already running)
#   ndex-server.sh stop    — Stop NDEx and block until the process exits
#
# All NDEx output (application logs + Maven console output) is written to
# /apps/ndex/data/ndex.log. Follow with: tail -f /apps/ndex/data/ndex.log

set -euo pipefail

NDEX_LOG=/apps/ndex/data/ndex.log

_detect_ndex() {
  pgrep -f "jetty:run" > /dev/null 2>&1
}

case "${1:-}" in
  start)
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
    echo "Usage: ndex-server.sh {start|stop}" >&2
    exit 1
    ;;
esac
