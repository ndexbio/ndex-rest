FROM eclipse-temurin:17-jdk-jammy

ARG KEYCLOAK_VERSION=26.1.0
ARG SOLR_VERSION=9.6.1
ARG MAILHOG_VERSION=1.0.1
ARG TARGETARCH

# ── System packages ───────────────────────────────────────────────────────────
# postgresql-14            — Postgres server (creates the postgres OS user)
# supervisor               — process manager for all services
# gosu                     — drop privileges (run postgres process as postgres user)
# curl jq unzip            — used in entrypoint.sh
# maven                    — build tool for NDEx Java project (devcontainer + docker run)
# httpie                   — HTTP client useful for development/debugging
# python3 python3-pip      — used to parse config.toml in entrypoint.sh
RUN apt-get update \
    && apt-get install -y --no-install-recommends \
       postgresql-14 postgresql-client-14 \
       supervisor gosu \
       curl jq unzip \
       maven \
       httpie \
       python3 python3-pip \
    && pip3 install --no-cache-dir toml \
    && rm -rf /var/lib/apt/lists/*

# ── Keycloak ──────────────────────────────────────────────────────────────────
# Pure JVM distribution — identical binary on amd64 and arm64
RUN curl -fsSL \
      "https://github.com/keycloak/keycloak/releases/download/${KEYCLOAK_VERSION}/keycloak-${KEYCLOAK_VERSION}.zip" \
      -o /tmp/keycloak.zip \
    && unzip -q /tmp/keycloak.zip -d /opt \
    && mv "/opt/keycloak-${KEYCLOAK_VERSION}" /opt/keycloak \
    && rm /tmp/keycloak.zip

# ── Solr ──────────────────────────────────────────────────────────────────────
# Pure JVM distribution — identical binary on amd64 and arm64
# Create a dedicated solr user so Solr doesn't refuse to start as root
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
# Static Go binary — different download per architecture
RUN ARCH="${TARGETARCH:-amd64}"; \
    [ "${ARCH}" = "arm64" ] && ARCH="arm"; \
    curl -fsSL \
      "https://github.com/mailhog/MailHog/releases/download/v${MAILHOG_VERSION}/MailHog_linux_${ARCH}" \
      -o /usr/local/bin/mailhog \
    && chmod +x /usr/local/bin/mailhog

# ── Bake in static assets ─────────────────────────────────────────────────────
# SQL schema and migration files applied at first boot by init-postgres.sh
COPY src/main/resources/postgresql/  /opt/ndex-install/sql/

# Solr configsets copied into /data/solr/data/configsets/ at first boot
COPY src/main/resources/solr/        /opt/ndex-install/solr-configsets/

# Keycloak realm definition imported on first Keycloak start
COPY .devcontainer/keycloak/ndex-realm.json          /opt/ndex-install/ndex-realm.json

# Keycloak auto-discovers JARs from /opt/keycloak/providers/ on every start
COPY .devcontainer/keycloak/providers/               /opt/keycloak/providers/

# NDEx runtime support files
COPY .devcontainer/config/ndex_importer_exporter.json /opt/ndex-install/config/
COPY .devcontainer/config/forgot-password.txt         /opt/ndex-install/config/

# ── Scripts ───────────────────────────────────────────────────────────────────
COPY .devcontainer/scripts/entrypoint.sh           /usr/local/bin/entrypoint.sh
# init-postgres.sh lives in docker/ (shared with deploy image)
COPY docker/scripts/init-postgres.sh        /usr/local/bin/init-postgres.sh
COPY .devcontainer/scripts/generate-ndex-config.sh /usr/local/bin/generate-ndex-config.sh
RUN chmod +x /usr/local/bin/entrypoint.sh \
             /usr/local/bin/init-postgres.sh \
             /usr/local/bin/generate-ndex-config.sh

# ── Supervisord config ────────────────────────────────────────────────────────
COPY .devcontainer/supervisord.conf /etc/supervisord.conf

# ── Default / example config ──────────────────────────────────────────────────
COPY .devcontainer/config.toml.example /etc/ndex/config.toml.example

# ── Permissions ───────────────────────────────────────────────────────────────
RUN mkdir -p /etc/ndex \
    && chown postgres:postgres /var/lib/postgresql

# ── Image metadata ────────────────────────────────────────────────────────────
VOLUME ["/data"]
EXPOSE 8080 8085 1025 8025 5432 8983 9000
ENV NDEX_CONFIG=/etc/ndex/config.toml

ENTRYPOINT ["/usr/local/bin/entrypoint.sh"]
