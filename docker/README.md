# NDEx Deploy Image

## Overview

The deploy image (`docker/Dockerfile`) is a self-contained, production-oriented image for running NDEx and its supporting services. 

- **Monolithic Mode**: THe whole stack of services runs in one container - Keycloak, Solr, MailHog, and PostgreSQL and NDEX Web.
- **Distributed Microservices Mode**: Optoinally, each service can be run in separate container - Keycloak, Solr, MailHog, and PostgreSQL and then configure NDEX Web to use their external endpoints. Or some services don't have to be run from this image, could use your own existing Postgres server.
- **Multi-stage build**: Maven compiles the NDEx WAR in a builder stage; the runtime stage is based on Tomcat 10.1 with JDK 17.
- **All services bundled**: PostgreSQL, Keycloak, Solr, MailHog, and NDEx (Tomcat) are all installed in a single image.
- **Flag-driven service selection**: You choose which services to start at container runtime via command-line flags (`--ndex`, `--postgres`, etc.). Run all flags together for monolithic mode; split across containers for distributed deployment.
- **Per Service Config at `/apps/<svc>/config/`**: Every service reads its configuration from `/apps/<svc>/config/`. On first boot, defaults are seeded from `/apps/<svc>/default/config/` (baked into the image). Mount volumes at `/apps/<svc>/config/` and `/apps/<svc>/data/` independently — you can persist only config, only data, or both.
- **No environment variable configuration**: All service settings live in config files under `/apps/<svc>/config/`. There are no required `docker run -e` variables.
- **Auto provisioned service credentials **: First boot of enabled dependent services will trigger randomly generated admin passwords for Keycloak, Solr, MailHog, and PostgreSQL and ndex are written to `/etc/<svc>.otp`. Each file is automatically deleted after 2 hours.
- **Persistence or Ephemeral**: Image supports external volume mounts for servcies in container to persist data outside of ephemeral container storage, durability across restarts.

---

## Services

| Service    | Port(s)        | Config dir                    | Data dir                |
|------------|----------------|-------------------------------|-------------------------|
| NDEx       | 8080           | `/apps/ndex/config/`          | `/apps/ndex/data/`      |
| Keycloak   | 8085 (UI/API), 9000 (internal management — not exposed in run examples) | `/apps/keycloak/config/`      | `/apps/keycloak/data/`  |
| Solr       | 8983           | `/apps/solr/config/`          | `/apps/solr/data/`      |
| MailHog    | 1025, 8025     | `/apps/mailhog/config/`       | —                       |
| PostgreSQL | 5432           | `/apps/postgres/config/`      | `/apps/postgres/data/`  |

---

## Building

Build with default component versions:

```bash
make docker
```

Override one or more component versions:

```bash
make docker KEYCLOAK_VERSION=26.2.0 SOLR_VERSION=9.7.0
```

Available version variables (with defaults):

| Variable           | Default  |
|--------------------|----------|
| `KEYCLOAK_VERSION` | `26.1.0` |
| `SOLR_VERSION`     | `9.6.1`  |
| `POSTGRES_VERSION` | `14`     |
| `MAILHOG_VERSION`  | `1.0.1`  |

---

## Running — Monolithic (all services in one container)

### Ephemeral (data lost on container removal)

```bash
docker run --rm -it \
  --name ndex \ 
  -p 8080:8080 \
  -p 8085:8085 \
  -p 8025:8025 \
  -p 8983:8983 \
  -p 5432:5432 \
  ndexbio/ndex-rest \
  --ndex --postgres --keycloak --solr --mailhog
```

> **Startup feedback**: while services initialize, the container prints `NDEx Container initializing...  (Xs)` to the console every 3 seconds. The `NDEx Deploy Container Ready!` banner (with service URLs) only appears once all enabled services have reached the `RUNNING` state. Wait for that banner before hitting API endpoints.

### Persistent (data survives container restarts)

```bash
docker run --rm -it \
  --name ndex \
  -p 8080:8080 \
  -p 8085:8085 \
  -p 8025:8025 \
  -p 8983:8983 \
  -p 5432:5432 \
  -v ndex-ndex-config:/apps/ndex/config \
  -v ndex-ndex-data:/apps/ndex/data \
  -v ndex-postgres-config:/apps/postgres/config \
  -v ndex-postgres-data:/apps/postgres/data \
  -v ndex-keycloak-config:/apps/keycloak/config \
  -v ndex-keycloak-data:/apps/keycloak/data \
  -v ndex-solr-config:/apps/solr/config \
  -v ndex-solr-data:/apps/solr/data \
  -v ndex-mailhog-config:/apps/mailhog/config \
  ndexbio/ndex-rest \
  --ndex --postgres --keycloak --solr --mailhog
```

---

## Stopping the Container

If started with `--rm -it`, press `Ctrl-C` in the terminal or run from another terminal:
```bash
docker stop ndex
```
The container is automatically removed on exit.

If started with `-d` for background mode
```bash
# Graceful stop (waits for supervisord to shut down services)
docker stop ndex

# Remove the container (volumes are preserved)
docker rm ndex
```

To stop and remove container in one step:
```bash
docker rm -f ndex
```
Named volumes are **not** removed by `docker rm`. Use `docker volume rm` explicitly if you want to discard data (see Re-initializing below).

---

## Running — Distributed (NDEx only, external services)

When running NDEx alongside externally managed PostgreSQL, Keycloak, and Solr instances, start only the NDEx service and point it at those external endpoints via `/apps/ndex/config/ndex.properties`:

```bash
docker run -d \
  --name ndex-app \
  -p 8080:8080 \
  -v ndex-ndex-config:/apps/ndex/config \
  ndexbio/ndex-rest \
  --ndex
```

Before starting, edit `/apps/ndex/config/ndex.properties` on the host volume to set `NdexDBURL`, `SolrURL`, `KEYCLOAK_ISSUER`, and `KEYCLOAK_PUBLIC_KEY` to point at the external services.

---

## Accessing Services from the Host

Once running, services are available at:

| Service         | URL / Address                        |
|-----------------|--------------------------------------|
| NDEx REST API   | http://localhost:8080/v3             |
| Keycloak Admin  | http://localhost:8085/admin          |
| MailHog UI      | http://localhost:8025                |
| Solr Admin UI   | http://localhost:8983/solr           |
| PostgreSQL      | localhost:5432                       |

---

## Credentials

### Keycloak

When enabled with `--keycloak` On first boot, `start.sh` generates a random admin password and writes it to `/etc/keycloak.otp`:

```
user: admin
password: <random>
url: http://localhost:8085/admin
```

Retrieve it before it expires:

```bash
docker exec ndex cat /etc/keycloak.otp
```

The file is automatically deleted 2 hours after container start. After deletion, use the Keycloak admin UI or CLI to manage admin credentials.

**RSA key pair**: `start.sh` generates a 2048-bit RSA key pair on first boot and stores it in `/apps/ndex/config/`:

- `priv.key` — PEM private key (used by Keycloak to sign JWTs)
- `cert.pem` — self-signed X.509 certificate; contains the public key embedded in its X.509 structure along with a self-signed signature

The public key is extracted from `cert.pem` at every boot (as base64 DER) and written into `ndex.properties` as `KEYCLOAK_PUBLIC_KEY`. Do not delete these files — they must persist across reboots for NDEx to verify Keycloak-issued JWTs.

### PostgreSQL

When enabled with `--postgres` On first boot, the `postgres` superuser is assigned a random password written to `/etc/pg.otp`:

```
user: postgres
password: <random>
```

Retrieve it:

```bash
docker exec ndex cat /etc/pg.otp
```

The file is automatically deleted 2 hours after container start. The `ndexserver` application user password is randomly generated on first boot and stored as `NdexDBDBPassword` in `/apps/ndex/config/ndex.properties`. Retrieve it from that file after initialization.

### Solr

Solr runs with **no authentication** — it is bound to localhost inside the container and is not intended to be exposed to external clients. No OTP file is generated. The Solr Admin UI at `http://localhost:8983/solr` is accessible from the host without credentials.

### MailHog

When enabled with `--mailhog` On first boot, a random admin password is generated and written to `/etc/mailhog.otp`:

```
user: admin
password: <random>
url: http://localhost:8025
```

Retrieve it:

```bash
docker exec ndex cat /etc/mailhog.otp
```

The file is automatically deleted 2 hours after container start. After deletion, the bcrypt hash is stored in `/apps/mailhog/config/auth`.

---

## Configuration

`/apps/<svc>/config/` is the sole source of truth for each service's configuration in the container. On first boot, `start.sh` seeds each service's config directory from the image defaults at `/apps/<svc>/default/config/`. Subsequent boots skip seeding (guarded by a `.initialized` sentinel file in each config directory).

To customize or override configuration:

1. Mount a named volume at `/apps/<svc>/config/` so config persists across restarts.
2. After first boot, edit files under `/apps/<svc>/config/` directly (e.g., `docker exec -it ndex vi /apps/ndex/config/ndex.properties`).
3. Restart the container — `start.sh` will not overwrite existing config.

To customize data storage:

Mount a named volume at `/apps/<svc>/data/` to control where each service writes its runtime data. Each service writes different things:

| Service    | Data path               | What is stored                                                                 |
|------------|-------------------------|--------------------------------------------------------------------------------|
| PostgreSQL | `/apps/postgres/data/`  | Full PostgreSQL data cluster — all databases, WAL, indexes. **Most critical volume to persist.** Without it, all NDEx network data is lost when the container is removed. Mount externally to enable backups, snapshots, and point-in-time recovery. |
| NDEx       | `/apps/ndex/data/`      | CX2 network files, importer/exporter working directory, user-uploaded images. Grows with usage — mount externally to control disk allocation and to back up network data independently of the database. |
| Solr       | `/apps/solr/data/`      | Solr home: configsets, core data, and all full-text search indexes. Grows with the number of indexed networks. Mount externally for persistence and to allow offline index inspection or rebuilding. |
| Keycloak   | `/apps/keycloak/data/`  | Keycloak provider caches and the pre-processed realm import. Small but should persist to avoid slow cache rebuilds on restart and to retain the RSA-patched realm JSON across boots. |
| MailHog    | —                       | No data path. MailHog holds messages in memory only; nothing is written to disk. |

Key config files per service:

| Service    | Primary config file                              |
|------------|--------------------------------------------------|
| NDEx       | `/apps/ndex/config/ndex.properties`             |
| Keycloak   | `/apps/keycloak/config/ndex-realm.json`         |
| Solr       | `/apps/solr/config/solr.xml`, configsets/       |
| MailHog    | `/apps/mailhog/config/outgoing-smtp.json`       |
| PostgreSQL | `/apps/postgres/config/pg_hba.conf` | `/apps/postgres/data/` |

> **PostgreSQL `pg_hba.conf`**: The seeded default at `/apps/postgres/config/pg_hba.conf` contains a single sentinel line (`# NDEX DEFAULT - INTENTIONALLY BLANK`). On first boot, `start.sh` detects this sentinel and generates a hardened config (marked `# NDEX GENERATED - DO NOT EDIT`) requiring `scram-sha-256` for all connections. To supply a custom access-control policy, replace the file content with your own `pg_hba.conf` rules (e.g. by editing it after first boot or by mounting a volume with a pre-populated file) — any content other than the sentinel causes `start.sh` to apply your file as-is.

---

## Enable/Disable Services with entrypoint command line flags

| Flag          | Service enabled          |
|---------------|--------------------------|
| `--ndex`      | NDEx REST API (Tomcat)   |
| `--postgres`  | PostgreSQL 14            |
| `--keycloak`  | Keycloak 26.x            |
| `--solr`      | Apache Solr 9.x          |
| `--mailhog`   | MailHog SMTP/UI          |

At least one flag must be specified. Flags can be combined in any order.

---

## Re-initializing

Config and data are governed by independent sentinels, so they can be reset separately.

### Reset data only

Removes the data initialization sentinel; `start.sh` re-runs data setup on the next boot while leaving config unchanged. Example for Solr:

```bash
docker exec ndex rm -f /apps/solr/data/.initialized
docker restart ndex
```

### Reset config only

Removes the config seeding sentinel; `start.sh` re-seeds from image defaults on the next boot while leaving data unchanged. Example for NDEx:

```bash
docker exec ndex rm -f /apps/ndex/config/.initialized
docker restart ndex
```
### Full reset of one service

Stop the container, drop both volumes, and restart:

```bash
docker stop ndex
docker volume rm ndex-solr-config ndex-solr-data
docker start ndex
```

### Full reset of all services

```bash
docker rm -f ndex
docker volume rm \
  ndex-ndex-config ndex-ndex-data \
  ndex-postgres-config ndex-postgres-data \
  ndex-keycloak-config ndex-keycloak-data \
  ndex-solr-config ndex-solr-data \
  ndex-mailhog-config
docker run -d --name ndex ... ndexbio/ndex-rest --ndex --postgres --keycloak --solr --mailhog
```
