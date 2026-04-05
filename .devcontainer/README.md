# NDEx Devcontainer

A fully self-contained devcontainer provides a complete NDEx development environment — no separate PostgreSQL, Keycloak, SMTP, or Solr installation needed.

On first launch, all supporting services initialize and the NDEx API starts automatically on port 8080, running directly from your working copy source code with hot reload active.

> **First boot is slower**: supporting services initialize, and `ndex-object-model:3.0.0-SNAPSHOT` is cloned and built from source (not available in any public Maven repo). Subsequent container starts are fast — the Maven cache is preserved in a Docker volume across container removals. Wait for the **"NDEx Devcontainer Ready!"** banner before hitting API endpoints.

> **Hot reload**: Java source changes are picked up automatically within ~5 seconds via Jetty's file scanner. No restart required.

---

## Services

| Service | Internal Port | Exposed to Host |
|---|---|---|
| NDEx API (Jetty) | 8080 | Yes (default) |
| Keycloak | 8085 | No (default) |
| MailHog UI | 8025 | No (default) |
| MailHog SMTP | 1025 | No (default) |
| Solr Admin | 8983 | No (default) |
| PostgreSQL | 5432 | No (default) |
| Keycloak Mgmt | 9000 | No (default) |

All services run inside the container and are reachable from within it regardless of host exposure settings. See [Exposed Ports](#exposed-ports) below to expose additional services to the host.

Credentials for Keycloak, PostgreSQL, and MailHog are auto-generated on first boot. Read them from inside the container:
- Keycloak admin: `cat /etc/keycloak.otp`
- MailHog admin: `cat /etc/mailhog.otp`
- PostgreSQL ndexserver password: `grep NdexDBDBPassword /apps/ndex/config/ndex.properties`

---

## Prerequisites

- [Docker Desktop](https://www.docker.com/products/docker-desktop/) (Mac/Windows) or Docker Engine (Linux)
- One of:
  - **VS Code** with the [Dev Containers extension](https://marketplace.visualstudio.com/items?itemName=ms-vscode-remote.remote-containers)
  - **DevContainer CLI** (`npm install -g @devcontainers/cli`)

---

## Running (VS Code)

1. Open the repository folder in VS Code.
2. When prompted *"Reopen in Container"*, click it. Or open the Command Palette (`Cmd+Shift+P`) and run **Dev Containers: Reopen in Container**.
3. VS Code builds the `runtime-base` image (installs all services) then builds the devcontainer image on top of it. On first launch this takes several minutes.
4. Once the container starts, all supporting services (PostgreSQL, Keycloak, Solr, MailHog) initialize automatically, then the NDEx API starts on port 8080. Wait for the "NDEx Devcontainer Ready!" banner in the terminal before hitting API endpoints.

---

## Running (CLI)

`devcontainer up` returns to the shell prompt once the container is running, but its stdout/stderr remain attached — container output continues to appear in the terminal while services initialize and NDEx starts. The CLI has no built-in output suppression. Choose one of the following approaches:

**Option A — two terminals (simplest):**

```bash
# Terminal 1 — start; output appears here while container initializes
devcontainer up --workspace-folder /path/to/ndex-rest

# Terminal 2 — open a shell in the running container
devcontainer exec --workspace-folder /path/to/ndex-rest bash
```

**Option B — background, single terminal:**

```bash
# Start in background, capture all output to a log file
devcontainer up --workspace-folder /path/to/ndex-rest > /tmp/devcontainer-up.log 2>&1 &

# Block until the ready banner appears, then return to prompt
until grep -q "NDEx Devcontainer Ready!" /tmp/devcontainer-up.log 2>/dev/null; do
  sleep 5
done
echo "NDEx is ready"

# Open a shell in the same terminal
devcontainer exec --workspace-folder /path/to/ndex-rest bash
```

**Rebuilding the image** (e.g. after changing the Dockerfile):
```bash
devcontainer up --workspace-folder /path/to/ndex-rest --build-no-cache
```

In both cases, wait for the **"NDEx Devcontainer Ready!"** banner before hitting API endpoints.

---

## Stopping the Devcontainer

**When started by VS Code:** Open the Command Palette (`Cmd+Shift+P`) and run **Dev Containers: Stop Container**. Closing VS Code also stops the container automatically.

**When started by Devcontainer CLI:**

```bash
# Find the container ID
docker ps --filter name=ndex-rest

# Stop and remove it
docker stop <container-id> && docker rm <container-id>
```

The devcontainer image (`ndexbio/ndex-rest-dev`) is never deleted by container removal. The next `devcontainer up` reuses cached image layers — no full rebuild unless you pass `--build-no-cache`.

Most container state is ephemeral — removing the container resets service data (NDEx networks, PostgreSQL, Keycloak, Solr). The Maven cache (`ndex-rest-m2` Docker volume) is an exception: it survives container removal automatically. To persist additional state across container removals, bind-mount host directories (see [Persistence](#persistence) below).

---

## Development Cycles

All supporting services (PostgreSQL, Keycloak, Solr, MailHog) are managed by supervisord inside the container. Check their status at any time from a container shell:

```bash
supervisorctl status   # postgres, keycloak, solr, mailhog should all be RUNNING
```

**NDEx API (Jetty)** starts automatically as the container's main process. It runs as PID 1 and the container's lifetime is tied to it — it is not managed by supervisord.

**Hot reload**: Edit Java source files directly in VS Code while connected to the devcontainer. Jetty scans for class changes every 5 seconds. Changes compiled by your IDE (or by `mvn compile` in a terminal) are reflected live without restarting.

**Restarting NDEx**: Stop the container (VS Code or `docker stop`) and start it again. All services are already initialized and the Maven cache is warm (persisted in the `ndex-rest-m2` Docker volume), so startup is significantly faster than the first boot — even after container removal and recreation.

---

## Logging

All service output is consolidated to the container's **stdout and stderr** — there is a single stream to observe.

Sources that contribute to this stream:
- `dev-entrypoint.sh` init phase messages (service wait, ndex-object-model build, Jetty launch)
- PostgreSQL, Keycloak, Solr, MailHog startup and runtime logs (via supervisord)
- supervisord lifecycle events (service spawning, RUNNING state transitions)
- Jetty server output
- NDEx application logs (logback) — API request processing, auth events, errors, and debug output at the level configured by `Log-Level` in `ndex.properties` (default: `INFO`)

**In VS Code**: the integrated terminal shows this output live while connected to the devcontainer.

**From the host**:
```bash
# Find the container ID
docker ps --filter name=ndex-rest

# Snapshot of all output so far
docker logs <container-id>

# Follow live (Ctrl-C to stop)
docker logs -f <container-id>
```

NDEx application logs are written directly to the container's stdout by logback — no log files are created inside the container.

### Log Level

NDEx application log verbosity is controlled by `Log-Level` in `/apps/ndex/config/ndex.properties` (default: `INFO`). Valid values: `trace`, `debug`, `info`, `warn`, `error`.

To enable DEBUG logging, edit the file inside the container and restart:

```bash
vi /apps/ndex/config/ndex.properties
# Set: Log-Level=DEBUG
# Then restart the container to apply
```

Note: even at `Log-Level=DEBUG`, Jetty server internals (`org.eclipse.jetty.*`) are capped at INFO to avoid flooding the log stream.

### Keycloak Log Level

Keycloak logging is split across two controls:

- **Root log level** — set via `--log-level=info` on the `kc.sh` startup command (in the container's supervisord config). This overrides Quarkus dev-mode defaults, which otherwise emit DEBUG output during startup before runtime configuration is applied.
- **Category overrides** — `io.netty` and `io.vertx` are pinned to `WARN` in `/apps/keycloak/config/keycloak.conf` to suppress networking-internal noise from Keycloak's embedded Vert.x server.

To change the root log level, edit the supervisord config inside the running container and restart Keycloak:

```bash
# From a container shell:
vi /opt/ndex-supervisord/keycloak.conf
# Change --log-level=info to e.g. --log-level=debug
supervisorctl restart keycloak
```

Category-specific overrides (`log-level=io.netty:WARN,io.vertx:WARN`) can be edited in `/apps/keycloak/config/keycloak.conf` independently.

---

## Accessing Services

NDEx and Keycloak are published directly to the host by Docker (`runArgs` in `devcontainer.json`) — no VS Code port forwarding required. Both are available from the host as soon as the container starts.

| Service | Host URL | Auth |
|---|---|---|
| NDEx API | http://localhost:8080/v3 | — |
| Keycloak admin | http://localhost:8085/admin | admin / see `/etc/keycloak.otp` |
| Keycloak account | http://localhost:8085/realms/ndex/account/ | your account |

Other services (PostgreSQL, Solr, MailHog) run inside the container but are not exposed to the host by default. See [Exposed Ports](#exposed-ports) to change this.

---

## Testing the Devcontainer

The devcontainer CLI (`devcontainer up`) does not publish ports to the host — that is handled by VS Code's port forwarding. To run the integration test against the running devcontainer, exec into the container and run it targeting localhost:

```bash
# Open a shell inside the running devcontainer:
devcontainer exec --workspace-folder /path/to/ndex-rest bash

# From inside the container:
docker/test/integration-test.sh --remote-ndex-url http://localhost:8080
```

Or as a one-liner:
```bash
devcontainer exec --workspace-folder /path/to/ndex-rest bash -c \
  "cd /workspaces/ndex-rest && docker/test/integration-test.sh --remote-ndex-url http://localhost:8080"
```

`--remote-ndex-url` skips all Docker build and container management steps — it runs only the 24 API calls against the given URL. See `docker/README.md` for full integration test documentation including deploy image testing.

---

## Configuration

### Exposed Ports

All services run on fixed internal ports. By default, only the NDEx API (port 8080) is forwarded to the host via VS Code port forwarding. All other service ports are reachable inside the container but not exposed to the host.

There are two distinct mechanisms:

**`NDEX_PORT`** is the only env var that changes actual service behavior. It sets Jetty's listen port inside the container *and* writes the `HostURI` value in `ndex.properties` on first boot. Default: `8080`. Change this value to make Jetty listen on a different port (and keep `forwardPorts` in sync).

**All other port env vars** (`KEYCLOAK_PORT`, `POSTGRES_PORT`, etc.) do **not** change where any service listens. Those services always bind to their fixed internal ports. These env vars only tell VS Code which container port to forward to your host machine. When absent, the service still runs — only host access is gated.

| Env Var | Internal port | What it controls | Default |
|---|---|---|---|
| `NDEX_PORT` | configurable (default 8080) | Jetty listen port + `HostURI` in `ndex.properties` | **8080** (set — forwarded) |
| `KEYCLOAK_PORT` | 8085 (fixed) | VS Code port forwarding to host only | unset (internal only) |
| `POSTGRES_PORT` | 5432 (fixed) | VS Code port forwarding to host only | unset (internal only) |
| `SOLR_PORT` | 8983 (fixed) | VS Code port forwarding to host only | unset (internal only) |
| `MAILHOG_UI_PORT` | 8025 (fixed) | VS Code port forwarding to host only | unset (internal only) |
| `MAILHOG_SMTP_PORT` | 1025 (fixed) | VS Code port forwarding to host only | unset (internal only) |
| `KEYCLOAK_MGMT_PORT` | 9000 (fixed) | VS Code port forwarding to host only | unset (internal only) |

To expose an additional service port to the host:
1. Open `.devcontainer/devcontainer.json`
2. Uncomment the desired env var in `containerEnv` (e.g. `"KEYCLOAK_PORT": "8085"`)
3. Add that same port number to the `forwardPorts` array
4. Rebuild: VS Code Command Palette → **Dev Containers: Rebuild Container**

### NDEx Properties

The NDEx application reads `/apps/ndex/config/ndex.properties`, generated on first boot from the image template with auto-generated credentials. To apply advanced configuration (e.g. enable graph query microservices via `NeighborhoodQueryURL` or `AdvancedQueryURL`), edit this file inside the running container and restart:

```bash
# From a container shell:
vi /apps/ndex/config/ndex.properties
# Then restart the container to pick up changes
```

For production/server deployments, see **[docker/README.md](../docker/README.md)**.

### Persistence

All service state lives inside the container by default and is lost when the container is removed. To persist specific state across container removals, bind-mount a host directory at the relevant path. For example in `.devcontainer/devcontainer.json`:

```jsonc
"mounts": [
    "source=/host/path/ndex-data,target=/apps/ndex,type=bind,consistency=cached",
    "source=/host/path/postgres-data,target=/apps/postgres/data,type=bind,consistency=cached"
]
```

Or via the devcontainer CLI with `--mount` flags. One Docker-managed volume (`ndex-rest-m2`) is created automatically for the Maven cache — see [Maven Cache](#maven-cache) below. All other persistence is at your discretion.

### Maven Cache

Maven dependencies are stored in a Docker-managed named volume (`ndex-rest-m2`). Docker creates and manages this volume automatically — no host path configuration needed, and it works identically on macOS, Linux, and Windows.

**First container start on a new host**: `ndex-object-model` is built from source and Maven downloads all project dependencies. This is a one-time cost that takes several minutes.

**All subsequent container starts**: the `ndex-rest-m2` volume is reused automatically. No build or downloads occur — the container is ready in under a minute. This holds even after the container is stopped, removed, and recreated.

**Resetting the cache**: to force a fresh download (e.g. after a dependency conflict or to free disk space):

```bash
# Remove the volume — it will be recreated on the next container start
docker volume rm ndex-rest-m2
```

Then restart the devcontainer. The next start incurs the one-time cost again.

---

## Re-initializing the Environment

Removing and recreating the container resets all service state (NDEx networks, PostgreSQL, Keycloak, Solr). The Maven cache is **not** reset by container removal — it persists in the `ndex-rest-m2` Docker volume.

```bash
# Stop and remove the container (resets service state)
docker stop <container-id> && docker rm <container-id>

# Then restart via VS Code or:
devcontainer up --workspace-folder /path/to/ndex-rest
```

To also reset the Maven cache (forces a full re-download on next start):
```bash
docker volume rm ndex-rest-m2
```

In persistent mode (bind mounts active for service data), delete the host-side directories for the services you want to reset, then restart. Service initialization sentinels live under `/apps/<svc>/config/.initialized` and `/apps/<svc>/data/.initialized` — deleting the relevant host directory causes that service to re-initialize on next boot.
