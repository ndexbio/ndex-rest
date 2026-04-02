ndex-rest
=========

NDEx Rest Server

## Container Deployments

A production-oriented Docker image (`docker/Dockerfile`) bundles the full NDEx stack — PostgreSQL, Keycloak, Apache Solr, MailHog, and the NDEx REST API (Tomcat) — into a single self-contained image built with `make docker`.

Key features:
- **Monolithic or distributed**: run all services in one container, or split them across containers using command-line flags (`--ndex`, `--postgres`, `--solr`, `--keycloak`, `--mailhog`)
- **Flag-driven startup**: enable only the services you need — no environment variables required
- **Persistent volumes**: mount `/apps/<svc>/config/` and `/apps/<svc>/data/` per-service to retain configuration and data across restarts
- **Auto-provisioned credentials**: random admin passwords are written to `/etc/<svc>.otp` on first boot and deleted after 2 hours
- **Integration tested**: a 24-step test script (`docker/test/integration-test.sh`) validates the full API lifecycle including CX1/CX2 network upload, summary polling, CX2 retrieval, and Solr search

For complete build, run, configuration, and testing instructions, see **[docker/README.md](docker/README.md)**.

---

## Development Environment

A fully self-contained environment is provided as a single Docker image that runs all required services inside one container — no separate PostgreSQL, Keycloak, SMTP, or Solr installation needed. The same image works for local development (devcontainer), standalone `docker run`, and orchestration deployments such as Kubernetes.

### Services

| Service | URL | Credentials |
|---|---|---|
| NDEx API (Jetty) | http://localhost:8080 | — |
| Keycloak | http://localhost:8085 | admin / admin |
| MailHog (SMTP UI) | http://localhost:8025 | — |
| Solr Admin | http://localhost:8983/solr | — |
| PostgreSQL | localhost:5432 | ndexserver / password |

> **Note:** Three graph query endpoints — `POST /network/{id}/query`, `POST /network/{id}/advancedquery`, and `POST /v3/networks/{id}/query` — require external microservices. They are disabled by default but can be enabled via `neighborhood_query_url` and `advanced_query_url` in `config.toml`. See [Configuration](#configuration) below.

### Prerequisites

- [Docker Desktop](https://www.docker.com/products/docker-desktop/) (Mac/Windows) or Docker Engine (Linux)
- For devcontainer mode, one of:
  - **VS Code** with the [Dev Containers extension](https://marketplace.visualstudio.com/items?itemName=ms-vscode-remote.remote-containers)
  - **DevContainer CLI** (`npm install -g @devcontainers/cli`)

---

### Running as a Devcontainer (VS Code)

1. Open the repository folder in VS Code.
2. When prompted *"Reopen in Container"*, click it. Or open the Command Palette (`Cmd+Shift+P`) and run **Dev Containers: Reopen in Container**.
3. VS Code builds the image and starts the container. On first launch this takes several minutes — all services (PostgreSQL, Keycloak, Solr, MailHog) start automatically inside the container and the database schema is initialized.
4. Once the terminal prompt appears, all supporting services are ready. The NDEx API is **not** started automatically — build and run it from local copy of source code in the terminal:
   ```bash
   cd /workspaces/ndex-rest && mvn jetty:run
   ```

### Running as a Devcontainer (CLI)

```bash
# Build the image and start the container (first time is slow; subsequent starts are fast)
devcontainer up --workspace-folder /path/to/ndex-rest

# Open a shell inside the running container
devcontainer exec --workspace-folder /path/to/ndex-rest bash

# Rebuild the image (e.g. after changing the Dockerfile)
devcontainer up --workspace-folder /path/to/ndex-rest --build-no-cache
```

All supporting services (PostgreSQL, Keycloak, Solr, MailHog) start automatically. The NDEx API is **not** started automatically — once the container is up, open a shell and build and run ndex from your local copy of source code:

```bash
cd /workspaces/ndex-rest && mvn jetty:run
```

### Stopping the Devcontainer

**From VS Code:** Open the Command Palette (`Cmd+Shift+P`) and run **Dev Containers: Stop Container**. Closing VS Code also stops the container automatically.

**From the CLI:** The devcontainer CLI has no `down` command — stop the container directly:

```bash
# Find the container ID
docker ps --filter name=ndex-rest

# Stop and remove it
docker stop <container-id> && docker rm <container-id>
```

Data is preserved in named Docker volumes (`ndex-rest-data`, `ndex-rest-maven`) and survives container restarts.

> For production/server deployment, see the **[deploy image documentation](docker/README.md)**.

### Development Cycles

All supporting services (PostgreSQL, Keycloak, Solr, MailHog) start automatically via `supervisord` when the container starts. You can verify their status at any time:

```bash
supervisorctl status   # postgres, keycloak, solr, mailhog should all be RUNNING
```

**Starting NDEx:** Run it directly in a terminal — this gives you live log output and easy restart:

```bash
cd /workspaces/ndex-rest
mvn jetty:run
```

The NDEx API is available at `http://localhost:8080` once Jetty finishes starting (look for `Started Jetty Server` in the output).

**Stopping NDEx:** Press `Ctrl+C` in the terminal where `mvn jetty:run` is running.

**After changing Java source code:** Press `Ctrl+C` and re-run `mvn jetty:run` — Jetty recompiles automatically before restarting. There is no hot reload.

The `ndexConfigurationPath` environment variable points to `/data/ndex/conf/ndex.properties`, which is generated at container startup with relevant values referencing container locality and includes the Keycloak RSA public key extracted at startup.

### Accessing Services

Once the container is running, the following services are accessible from your host:

| Service | URL | Credentials |
|---------|-----|-------------|
| NDEx API | http://localhost:8080/v2 | — |
| Keycloak admin | http://localhost:8085/admin | admin / admin |
| Keycloak NDEx realm | http://localhost:8085/realms/ndex/account/ | your account |
| MailHog UI | http://localhost:8025 | open (no auth) |
| Solr Admin | http://localhost:8983/solr | open (no auth) |
| PostgreSQL | localhost:5432 | ndexserver / password |

> **Keycloak RSA key pair:** Generated at first boot using `openssl`. Private key stored at `/data/ndex/conf/priv.key`. Public key injected into `ndex.properties` on every boot.

**Connecting to PostgreSQL from your host:**
```bash
psql -h 127.0.0.1 -p 5432 -U ndexserver -d ndex
# Password: password (from .devcontainer/config.toml)
```
> The `postgres` superuser allows trusted local connections (Unix socket) for development convenience; TCP connections use md5.

---

### Configuration

All configuration lives in a single TOML file. The full reference with all fields and their defaults is in **`docker/config.toml.example`**.

**For devcontainer:** edit **`.devcontainer/config.toml`** — this file is mounted into the container automatically.

**For `docker run`:** mount your own config file and point `NDEX_CONFIG` at it:
```bash
docker run ... \
  -v /path/to/my-config.toml:/etc/ndex/config.toml \
  -e NDEX_CONFIG=/etc/ndex/config.toml \
  ndexbio/ndex-rest
```

#### Key settings

| Section / Key | Default | Description |
|---|---|---|
| `NDEX_AUTOSTART` (env var) | `false` | `true` = supervisord auto-starts and manages the NDEx API process (docker run / k8s). `false` = devcontainer mode; start NDEx manually with `mvn jetty:run`. |
| `data.root` | `/data` | Mount a volume or PVC here to persist all data. |
| `postgres.ndexserver_password` | `password` | **Change for production.** Password for the NDEx database user. |
| `keycloak.issuer_url` | `http://localhost:8085` | **Must match the URL clients use to reach Keycloak.** Change this for Kubernetes or remote deployments. |
| `keycloak.admin_password` | `admin` | **Change for production.** |
| `ndex.system_user_password` | `admin` | **Change for production.** |
| `ndex.neighborhood_query_url` | *(empty)* | Set to enable `POST /network/{id}/query` and `POST /v3/networks/{id}/query`. |
| `ndex.advanced_query_url` | *(empty)* | Set to enable `POST /network/{id}/advancedquery`. |

#### How configuration flows through the system

All settings are read from `config.toml` by `entrypoint.sh` at container startup. Port values are passed to each service process. The Keycloak issuer URL is written into both Keycloak's own configuration (so it embeds the correct `iss` claim in JWTs) and into `ndex.properties` as `KEYCLOAK_ISSUER` (so NDEx validates those JWTs correctly).

`ndex.properties` is regenerated on every container start, so changes to `config.toml` take effect on the next restart — no need to wipe data.

#### Port changes

All ports are configured in `config.toml` under their respective service sections (`[postgres]`, `[keycloak]`, `[solr]`, `[smtp]`). For devcontainer use, if you change a port in `.devcontainer/config.toml`, also update the matching entry in `forwardPorts` / `portsAttributes` in `.devcontainer/devcontainer.json` so VS Code forwards the correct port.

---

### Re-initializing the Environment

The first-boot setup (database schema, Solr configsets) is guarded by a sentinel file at `/data/.initialized` and runs only once per data volume. `ndex.properties` is always regenerated on startup.

To force a full re-initialization, remove the data volume:

```bash
# For devcontainer
docker volume rm ndex-rest-data

# For docker run
docker volume rm ndex-data   # or whatever name you used with -v

# Then restart — full init runs again
```
