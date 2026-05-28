# Devcontainer — extends shared runtime-base (postgres/keycloak/solr/mailhog pre-installed).
# Requires ndex-runtime-base to be built first (handled by initializeCommand in devcontainer.json):
#   make docker-base
FROM ndex-runtime-base
RUN apt-get update \
    && apt-get install -y --no-install-recommends \
        maven git httpie \
        vim less jq curl wget tree \
    && rm -rf /var/lib/apt/lists/*
COPY .devcontainer/scripts/dev-entrypoint.sh          /usr/local/bin/dev-entrypoint.sh
COPY .devcontainer/scripts/ndex-server.sh              /usr/local/bin/ndex-server.sh
COPY .devcontainer/scripts/logback-dev.xml            /apps/ndex/default/config/logback-dev.xml
RUN chmod +x /usr/local/bin/dev-entrypoint.sh \
             /usr/local/bin/ndex-server.sh
EXPOSE 8080
ENTRYPOINT ["/usr/local/bin/dev-entrypoint.sh"]
