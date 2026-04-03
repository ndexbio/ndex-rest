# Devcontainer — extends shared runtime-base (postgres/keycloak/solr/mailhog pre-installed).
# Requires ndex-runtime-base to be built first (handled by initializeCommand in devcontainer.json):
#   make docker-base
FROM ndex-runtime-base
RUN apt-get update \
    && apt-get install -y --no-install-recommends maven git httpie \
    && rm -rf /var/lib/apt/lists/*
COPY docker/scripts/build-ndex-object-model.sh /usr/local/bin/build-ndex-object-model.sh
COPY .devcontainer/scripts/dev-entrypoint.sh /usr/local/bin/dev-entrypoint.sh
RUN chmod +x /usr/local/bin/build-ndex-object-model.sh /usr/local/bin/dev-entrypoint.sh
EXPOSE 8080
ENTRYPOINT ["/usr/local/bin/dev-entrypoint.sh"]
