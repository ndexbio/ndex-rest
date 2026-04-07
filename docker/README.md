# NDEx Deploy Image

## Overview

The deploy image is a self-contained, production-oriented image for running NDEx and its supporting services. It is built from two Dockerfiles:

- `docker/Dockerfile` — defines the shared `runtime-base` stage: installs PostgreSQL, Keycloak, Solr, MailHog, and all scripts/configs (no WAR, no entrypoint). 
- `docker/Dockerfile_deploy` — self-contained deploy pipeline: `builder` stage compiles the NDEx WAR from source using Maven, then layers it onto `runtime-base` with the production entrypoint (`start.sh`)

The `runtime-base` stage is the single source of truth for service installation. the deploy image builds FROM it, ensuring identical service versions and configurations in development and production.

- **Monolithic Mode**: The whole stack of services runs in one container — Keycloak, Solr, MailHog, PostgreSQL, and NDEx web.
- **Distributed Microservices Mode**: Optionally, each service can run in a separate container. The `--ndex` container can point at external endpoints via `/apps/ndex/config/ndex.properties`.
- **Flag-driven service selection**: You choose which services to start at container runtime via command-line flags (`--ndex`, `--postgres`, etc.). Run all flags together for monolithic mode; split across containers for distributed deployment.
- **Per Service Config at `/apps/<svc>/config/`**: Every service reads its configuration from `/apps/<svc>/config/`. On first boot, defaults are seeded from `/apps/<svc>/default/config/` (baked into the image). Mount volumes at `/apps/<svc>/config/` and `/apps/<svc>/data/` independently — you can persist only config, only data, or both.
- **No environment variable configuration**: All service settings live in config files under `/apps/<svc>/config/`. There are no required `docker run -e` variables.
- **Auto-provisioned credentials**: First boot generates random admin passwords for Keycloak, MailHog, and PostgreSQL, written to `/etc/<svc>.otp`. Each file is automatically deleted after 2 hours.
- **Ephemeral by default**: All service state lives in the container layer. No Docker volumes are created automatically. To persist data across container removals, bind-mount host directories at the relevant `/apps/<svc>/config/` or `/apps/<svc>/data/` paths using `-v` flags — see [Persistence](#persistence) below.

---

## Services

| Service    | Internal Port(s) | Config dir                    | Data dir                |
|------------|------------------|-------------------------------|-------------------------|
| NDEx       | 8080             | `/apps/ndex/config/`          | `/apps/ndex/data/`      |
| Keycloak   | 8085 (UI/API), 9000 (mgmt) | `/apps/keycloak/config/` | `/apps/keycloak/data/` |
| Solr       | 8983             | `/apps/solr/config/`          | `/apps/solr/data/`      |
| MailHog    | 1025 (SMTP), 8025 (UI) | `/apps/mailhog/config/` | —                  |
| PostgreSQL | 5432             | `/apps/postgres/config/`      | `/apps/postgres/data/`  |

All internal service ports are fixed — they cannot be changed via environment variables. Use `-p host:container` flags in `docker run` to publish any service port to your host machine. Only publish what you need.

---

## Building

The deploy image is built in two steps — `docker/Dockerfile` produces the shared `runtime-base`, then `docker/Dockerfile_deploy` compiles the WAR and assembles the final image. `make docker` handles the full sequence:

```bash
make docker
```

`docker/Dockerfile` defines only `runtime-base` — installs PostgreSQL, Keycloak, Solr, MailHog, all scripts and configs. No NDEX, no build tooling.

`docker/Dockerfile_deploy` is self-contained: it compiles the NDEx WAR in a `builder` stage (eclipse-temurin + Maven), then layers the WAR onto `ndex-runtime-base`.

Override one or more component versions:

```bash
make docker KEYCLOAK_VERSION=26.2.0 SOLR_VERSION=9.7.0
```

Available version variables (with defaults):

| Variable           | Default  |
|--------------------|----------|
| `KEYCLOAK_VERSION` | `26.1.0` |
| `SOLR_VERSION`     | `9.6.1`  |
| `POSTGRES_VERSION` | `16`     |
| `MAILHOG_VERSION`  | `1.0.1`  |

---

## Testing

An integration test script lives at `docker/test/integration-test.sh`. It builds the image, starts an ephemeral container, and validates the full API lifecycle across both v2 and v3 endpoints:

- User creation and Basic Auth (v2)
- CX1 network upload via v2, summary poll until `completed:true`, CX2 retrieval via v3
- CX2 network upload via v3, summary poll until `completed:true`, CX2 retrieval via v3
- Solr keyword search via v2 (step 15) and v3 (step 16)

The test **fails fast** — on the first failure it stops, prints the reason, how many calls passed, and how many were left unrun, then exits 1.

**Fixtures required** (pre-built, committed in `docker/test/fixtures/`):
- 3 × `.cx` files (CX1) — uploaded via `POST /v2/network`
- 3 × `.cx2` files (CX2) — uploaded via `POST /v3/networks`

### Full run (build + test)

```bash
cd docker/test
./integration-test.sh
```

### Skip the image build (re-use existing image)

```bash
cd docker/test
./integration-test.sh --skip-build
```

### What success looks like

```
=== STEP 1: Building Docker image ===
  Image built successfully

... rest of steps logged

================================================
  ✓ ALL 24 API CALLS PASSED — TEST PASSED
================================================
```

Exit code 0 means all calls passed.

### What failure looks like

```
 ... steps logged as running 

TEST FAILED
  Passed : 5 / 24
  Remaining unrun: 19
  Reason : POST /v2/search/network (WP1984) → UUID not found in results
```

Exit code 1 means the test failed. The container is always stopped and removed on exit (pass or fail).

---

## Running deployment modes
Refer to [RUNBOOK.md](./RUNBOOK.md) for how to run the deployment container in monolithic or distributed modes.

---

## Accessing Services from the Container Host

Once running, services are available at:

| Service                  | URL / Address                                         | Description                                                                                                                                                       |
|--------------------------|-------------------------------------------------------|-------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| NDEx REST API            | http://localhost:8080/v3                              | REST API base path for NDEx network operations.                                                                                                                   |
| Keycloak Admin Console   | http://localhost:8085/admin                           | Browser UI for realm and user administration. Requires the admin credentials from `/etc/keycloak.otp`.                                                           |
| Keycloak Account Console | http://localhost:8085/realms/ndex/account             | Browser UI for user login and self-registration. Unauthenticated visitors are redirected to the login form, which includes a Register link to enroll a new account. |
| Solr Admin UI            | http://localhost:8983/solr                            | Browser UI for core management, index inspection, and query testing.                                                                                              |
| Solr REST API            | http://localhost:8983/solr/\<core\>/select, .../update | REST API used internally by NDEx for indexing and search. Primary cores: `public-nfs`, `private-nfs`, `ndex-networks`, `ndex-users`.                             |
| MailHog SMTP             | localhost:1025                                        | Internal SMTP endpoint that captures outbound email from NDEx. Not published to the host — only accessible to processes running inside the container.             |
| MailHog UI               | http://localhost:8025                                 | Browser UI for inspecting email captured by MailHog.                                                                                                              |
| PostgreSQL               | localhost:5432                                        | TCP endpoint for direct database client connections (e.g. psql, pgAdmin).                                                                                         |

---

## Logging

All service output is consolidated to the container's **stdout and stderr** — there is a single stream to observe, accessible without execing into the container.

Sources that contribute to this stream:
- `start.sh` init phase messages (service seeding, PostgreSQL init, key generation, etc.)
- PostgreSQL startup and runtime logs
- Keycloak startup and runtime logs
- Solr startup and core initialization logs
- MailHog startup and access logs
- supervisord lifecycle events (service spawning, RUNNING state transitions)
- Tomcat (JULI) — server startup, WAR deployment, unhandled exception stack traces
- NDEx application logs (logback) — API request processing, auth events, errors, and debug output at the level configured by `Log-Level` in `ndex.properties` (default: `INFO`)

```bash
# Snapshot of all output so far
docker logs ndex

# Follow live (Ctrl-C to stop)
docker logs -f ndex
```

NDEx application logs are written directly to the container's stdout by logback — no log files are created inside the container.

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

Enable `--mailhog` alongside `--ndex` when you want to capture and inspect email sent by NDEx during local development. NDEx is pre-configured to deliver to `localhost:1025` inside the container — no changes to `ndex.properties` are needed.

If you have an external SMTP relay, set `SMTP-Host` and `SMTP-Port` (and optionally `SMTP-Auth`, `SMTP-Username`, `SMTP-Password`) in `/apps/ndex/config/ndex.properties` and omit `--mailhog` entirely — no capture service is needed when NDEx delivers directly to a real mail server.

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

1. Bind-mount a host directory at `/apps/<svc>/config/` (e.g., `-v /host/path/ndex-config:/apps/ndex/config`) so config persists across container removals and is editable directly from the host.
2. After first boot, edit files under `/apps/<svc>/config/` directly (e.g., `docker exec -it ndex vi /apps/ndex/config/ndex.properties`, or edit the bind-mounted host path if using persistence).
3. Restart the container — `start.sh` will not overwrite existing config.

### Log Level

NDEx application log verbosity is controlled by `Log-Level` in `/apps/ndex/config/ndex.properties`. Default: `INFO`. Valid values: `trace`, `debug`, `info`, `warn`, `error`.

To enable DEBUG logging:

```bash
docker exec ndex vi /apps/ndex/config/ndex.properties
# Set: Log-Level=DEBUG
docker restart ndex
```

Or with a bind-mounted config directory, edit the host-side file and restart. Note: even at `Log-Level=DEBUG`, Tomcat server internals are capped at INFO to avoid flooding the log stream with server-level noise.

### Keycloak Log Level

Keycloak logging is split across two controls:

- **Root log level** — set via `--log-level=info` on the `kc.sh` startup command (in the container's supervisord config). This overrides Quarkus dev-mode defaults, which otherwise emit DEBUG output during startup before runtime configuration is applied.
- **Category overrides** — `io.netty` and `io.vertx` are pinned to `WARN` in `/apps/keycloak/config/keycloak.conf` (generated on first boot) to suppress networking-internal noise from Keycloak's embedded Vert.x server.

To change Keycloak's root log level, edit the supervisord config inside the running container and restart Keycloak:

```bash
docker exec ndex vi /opt/ndex-supervisord/keycloak.conf
# Change --log-level=info to e.g. --log-level=debug
docker restart ndex
```

Category-specific overrides (`log-level=io.netty:WARN,io.vertx:WARN`) can be edited in keycloak.conf inside the container without touching the startup command:

```bash
docker exec ndex vi /apps/keycloak/config/keycloak.conf
docker restart ndex
```

To persist data across container removals:

Bind-mount a host directory at `/apps/<svc>/data/` to route each service's runtime data to your host filesystem. Each service writes different things:

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

## Enable/Disable Services 
Use command line flags

| Flag          | Service enabled          |
|---------------|--------------------------|
| `--ndex`      | NDEx REST API (Tomcat)   |
| `--postgres`  | PostgreSQL 16            |
| `--keycloak`  | Keycloak 26.x            |
| `--solr`      | Apache Solr 9.x          |
| `--mailhog`   | MailHog email capture (enable alongside `--ndex` for local dev) |

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

### Full reset of one service (ephemeral mode)

Remove and recreate the container. All state resets on the next boot:

```bash
docker rm -f ndex
docker run --platform linux/amd64 ... ndexbio/ndex-rest --ndex --postgres --keycloak --solr --mailhog
```

### Full reset of one service (persistent mode — bind mounts)

Delete the host-side directory for the service(s) you want to reset, then restart:

```bash
docker rm -f ndex
rm -rf /host/path/solr-config /host/path/solr-data
docker run --platform linux/amd64 -v /host/path/solr-config:/apps/solr/config -v /host/path/solr-data:/apps/solr/data ... ndexbio/ndex-rest --ndex --postgres --keycloak --solr --mailhog
```

`start.sh` detects the absent sentinel and re-initializes only the cleared service(s) on the next boot.

### Full reset of all services

**Ephemeral mode**: remove and recreate the container — no other cleanup needed:

```bash
docker rm -f ndex
docker run --platform linux/amd64 ... ndexbio/ndex-rest --ndex --postgres --keycloak --solr --mailhog
```

**Persistent mode**: delete all bind-mounted host directories, then recreate:

```bash
docker rm -f ndex
rm -rf /host/path/ndex-config /host/path/ndex-data \
        /host/path/postgres-config /host/path/postgres-data \
        /host/path/keycloak-config /host/path/keycloak-data \
        /host/path/solr-config /host/path/solr-data \
        /host/path/mailhog-config
docker run --platform linux/amd64 -v /host/path/ndex-config:/apps/ndex/config ... ndexbio/ndex-rest --ndex --postgres --keycloak --solr --mailhog
```
