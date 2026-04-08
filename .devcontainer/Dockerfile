# Devcontainer — extends shared runtime-base (postgres/keycloak/solr/mailhog pre-installed).
# Requires ndex-runtime-base to be built first (handled by initializeCommand in devcontainer.json):
#   make docker-base
FROM ndex-runtime-base
RUN apt-get update \
    && apt-get install -y --no-install-recommends maven git httpie \
    && rm -rf /var/lib/apt/lists/*
# TODO - remove this once the object model is published to Maven Central and can be pulled as a dependency in pom.xml
COPY docker/scripts/build-ndex-object-model.sh        /usr/local/bin/build-ndex-object-model.sh
COPY .devcontainer/scripts/dev-entrypoint.sh          /usr/local/bin/dev-entrypoint.sh
COPY .devcontainer/scripts/ndex-server.sh              /usr/local/bin/ndex-server.sh
COPY .devcontainer/scripts/logback-dev.xml            /apps/ndex/default/config/logback-dev.xml
RUN chmod +x /usr/local/bin/build-ndex-object-model.sh \
             /usr/local/bin/dev-entrypoint.sh \
             /usr/local/bin/ndex-server.sh
EXPOSE 8080
ENTRYPOINT ["/usr/local/bin/dev-entrypoint.sh"]
