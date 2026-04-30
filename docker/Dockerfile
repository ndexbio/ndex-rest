# ── Runtime base ──────────────
FROM tomcat:10.1-jdk17-temurin-jammy AS runtime-base

ARG KEYCLOAK_VERSION=26.1.0
ARG SOLR_VERSION=9.6.1
ARG POSTGRES_VERSION=16
ARG MAILHOG_VERSION=1.0.1
ENV PATH="/usr/lib/postgresql/${POSTGRES_VERSION}/bin:${PATH}"

# ── System packages ───────────────────────────────────────────────────────────
# postgresql-${POSTGRES_VERSION}  — Postgres server
# supervisor gosu                 — process manager, privilege drop
# curl jq openssl unzip           — used by start.sh and Dockerfile RUN steps
# apache2-utils                   — htpasswd for bcrypt hashes (MailHog, Solr auth)
RUN apt-get update \
    && apt-get install -y --no-install-recommends gnupg curl \
    && curl -fsSL https://www.postgresql.org/media/keys/ACCC4CF8.asc \
        | gpg --dearmor -o /usr/share/keyrings/postgresql-keyring.gpg \
    && echo "deb [signed-by=/usr/share/keyrings/postgresql-keyring.gpg] https://apt.postgresql.org/pub/repos/apt/ jammy-pgdg main" \
        > /etc/apt/sources.list.d/pgdg.list \
    && apt-get update \
    && apt-get install -y --no-install-recommends \
       postgresql-${POSTGRES_VERSION} postgresql-client-${POSTGRES_VERSION} \
       supervisor gosu \
       jq openssl unzip \
       apache2-utils \
    && rm -rf /var/lib/apt/lists/*

# ── Keycloak ──────────────────────────────────────────────────────────────────
RUN curl -fsSL \
      "https://github.com/keycloak/keycloak/releases/download/${KEYCLOAK_VERSION}/keycloak-${KEYCLOAK_VERSION}.zip" \
      -o /tmp/keycloak.zip \
    && unzip -q /tmp/keycloak.zip -d /opt \
    && mv "/opt/keycloak-${KEYCLOAK_VERSION}" /opt/keycloak \
    && rm /tmp/keycloak.zip

# ── Solr ──────────────────────────────────────────────────────────────────────
RUN curl -fsSL \
      "https://archive.apache.org/dist/solr/solr/${SOLR_VERSION}/solr-${SOLR_VERSION}.tgz" \
      -o /tmp/solr.tgz \
    && tar -xzf /tmp/solr.tgz -C /opt \
    && mv "/opt/solr-${SOLR_VERSION}" /opt/solr \
    && rm /tmp/solr.tgz \
    && groupadd -r solr \
    && useradd -r -g solr -d /opt/solr -s /sbin/nologin solr \
    && chown -R solr:solr /opt/solr

# ── MailHog ───────────────────────────────────────────────────────────────────
RUN curl -fsSL \
      "https://github.com/mailhog/MailHog/releases/download/v${MAILHOG_VERSION}/MailHog_linux_amd64" \
      -o /usr/local/bin/mailhog \
    && chmod +x /usr/local/bin/mailhog

# ── Remove Tomcat default webapps (security hygiene) ─────────────────────────
RUN rm -rf /usr/local/tomcat/webapps/*

# ── Tomcat JULI logging → stdout/stderr (visible via docker logs) ─────────────
COPY docker/conf/tomcat-logging.properties /usr/local/tomcat/conf/logging.properties

# ── Solr configsets (canonical source: src/main/resources/solr/) ─────────────
# NDEx passes configset names to the remote Solr HTTP API; Solr reads them from its own
# filesystem at $SOLR_HOME/configsets/. These files are not bundled in the WAR at runtime —
# COPY from source directly keeps src/main/resources/solr/ as the single source of truth.
COPY src/main/resources/solr/ /opt/ndex-install/solr-configsets/

# ── SQL schema files (init-postgres.sh applies these at first boot) ───────────
COPY src/main/resources/postgresql/ /opt/ndex-install/sql/

COPY docker/scripts/init-postgres.sh /usr/local/bin/init-postgres.sh
RUN chmod +x /usr/local/bin/init-postgres.sh

# ── Build-time defaults (/opt/defaults/<svc>/config/) ───────────────────────
# Stored under /opt/defaults/ (not /apps/) so a volume mount to /apps/ does not
# wipe out these baked-in defaults. Operational /apps/<svc>/config/ and
# /apps/<svc>/data/ start empty; start.sh seeds them on first run.
COPY docker/apps/ndex/default/config/      /opt/defaults/ndex/config/
COPY docker/apps/postgres/default/config/  /opt/defaults/postgres/config/
COPY docker/apps/keycloak/default/config/  /opt/defaults/keycloak/config/
COPY docker/apps/solr/default/config/      /opt/defaults/solr/config/
COPY docker/apps/mailhog/default/config/   /opt/defaults/mailhog/config/

# ── Capture KC distribution keycloak.conf as the clean default ────────────────
# Copied verbatim — no NDEx settings. start.sh appends NDEx deployment settings
# at container runtime. Tied to the installed KC version; rebuild to pick up changes.
RUN cp /opt/keycloak/conf/keycloak.conf /opt/defaults/keycloak/config/keycloak.conf

# ── Operational config and data directories (empty; populated at runtime) ─────
RUN mkdir -p \
        /apps/ndex/config \
        /apps/ndex/data \
        /apps/postgres/config \
        /apps/postgres/data \
        /apps/keycloak/config \
        /apps/keycloak/data \
        /apps/solr/config \
        /apps/solr/data \
        /apps/mailhog/config \
    && chown postgres:postgres /apps/postgres/data /apps/postgres/config \
    && chown -R solr:solr /apps/solr/data /apps/solr/config /opt/defaults/solr

# ── Per-service supervisord snippets ─────────────────────────────────────────
COPY docker/supervisord/ /opt/ndex-supervisord/

# ── Startup script ────────────────────────────────────────────────────────────
COPY docker/scripts/start.sh /usr/local/bin/start.sh
RUN chmod +x /usr/local/bin/start.sh

COPY docker/scripts/schema_upgrade.sh /usr/local/bin/schema_upgrade.sh
RUN chmod +x /usr/local/bin/schema_upgrade.sh

# ── Image metadata ────────────────────────────────────────────────────────────
EXPOSE 8085 1025 8025 5432 8983 9000

# ── Stage 2: Build NDEx WAR ───────────────────────────────────────────────────
FROM eclipse-temurin:17-jdk-jammy AS builder
RUN apt-get update && apt-get install -y --no-install-recommends maven git \
    && rm -rf /var/lib/apt/lists/*
WORKDIR /build
COPY pom.xml .
COPY src/ src/
ARG NDEX_COMMIT_HASH=docker
RUN --mount=type=cache,target=/root/.m2 \
    mvn install -DskipTests -DbuildNumber=${NDEX_COMMIT_HASH}

# ── Stage 3: Deploy image ─────────────────────────────────────────────────────
FROM runtime-base AS deploy
COPY --from=builder /build/target/ndexbio-rest.war /usr/local/tomcat/webapps/ROOT.war
EXPOSE 8080
ENTRYPOINT ["/usr/local/bin/start.sh"]
