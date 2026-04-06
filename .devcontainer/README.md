# NDEx Devcontainer

A fully self-contained devcontainer provides a complete NDEx development environment — no separate PostgreSQL, Keycloak, SMTP, or Solr installation needed.

On first launch, all supporting services (PostgreSQL, Keycloak, Solr, MailHog) initialize automatically. Once the **"NDEx Devcontainer Ready!"** banner appears, start the NDEx API server manually from inside the container — giving you full control over when to build and run it.

> **First boot is slower**: supporting services initialize, and `ndex-object-model:3.0.0-SNAPSHOT` is cloned and built from source (not available in any public Maven repo). Subsequent container starts are fast — the Maven cache is preserved in a Docker volume across container removals. When the **"NDEx Devcontainer Ready!"** banner appears, start NDEx manually (see [Development Cycles](#development-cycles)).

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

Credentials for Keycloak, PostgreSQL, and MailHog are auto-generated on first boot. In the devcontainer, `.otp` files are never auto-deleted — they remain available for the duration of your session. Read them from inside the container:
- Keycloak admin: `cat /etc/keycloak.otp`
- MailHog admin: `cat /etc/mailhog.otp`
- PostgreSQL ndexserver password: `grep NdexDBDBPassword /apps/ndex/config/ndex.properties`

For full context on what `.otp` files are and how credential management works, see [Credentials](../docker/README.md#credentials) in docker/README.md.

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
4. Once the container starts, all supporting services (PostgreSQL, Keycloak, Solr, MailHog) initialize automatically. When the **"NDEx Devcontainer Ready!"** banner appears in the terminal, open a new integrated terminal (**Terminal → New Terminal**) and start the NDEx API server:
   ```bash
   ndex-server.sh start
   ```
   This returns immediately — NDEx starts in the background. All output goes to `/apps/ndex/data/ndex.log`. Follow startup with `tail -f /apps/ndex/data/ndex.log` and wait for a Jetty "Started" message before hitting API endpoints.

---

## Running (CLI)

`devcontainer up` returns to the shell prompt once the container is running, but its stdout/stderr remain attached — container output continues to appear in the terminal while services initialize. The CLI has no built-in output suppression. Choose one of the following approaches:

**Option A — two terminals (simplest):**

```bash
# Terminal 1 — start; output appears here while container initializes
devcontainer up --workspace-folder /path/to/ndex-rest

# Terminal 2 — open a shell in the running container
devcontainer exec --workspace-folder /path/to/ndex-rest bash

# Once inside the container — start NDEx in the background:
ndex-server.sh start

# Follow startup output:
tail -f /apps/ndex/data/ndex.log
```

**Option B — background, single terminal:**

```bash
# Start in background, capture all output to a log file
devcontainer up --workspace-folder /path/to/ndex-rest > /tmp/devcontainer-up.log 2>&1 &

# Block until the ready banner appears, then return to prompt
until grep -q "NDEx Devcontainer Ready!" /tmp/devcontainer-up.log 2>/dev/null; do
  sleep 5
done
echo "Core services ready"

# Start NDEx inside the container (async — returns immediately):
devcontainer exec --workspace-folder /path/to/ndex-rest bash -c "ndex-server.sh start"
# Logs at /apps/ndex/data/ndex.log inside the container

# Open a shell in the same terminal
devcontainer exec --workspace-folder /path/to/ndex-rest bash
```

**Rebuilding the image** (e.g. after changing the Dockerfile):
```bash
devcontainer up --workspace-folder /path/to/ndex-rest --build-no-cache
```

In both cases, wait for the **"NDEx Devcontainer Ready!"** banner (core services ready) before starting NDEx with `ndex-server.sh start`.

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

**NDEx API (Jetty)** is started manually after the container is ready. Open a terminal inside the container and run:

```bash
ndex-server.sh start
```

`ndex-server.sh start` starts Jetty in the background and returns immediately. If NDEx is already running it is a no-op. All NDEx output (application logs + Maven console output) is written to `/apps/ndex/data/ndex.log`. Follow it with:

```bash
tail -f /apps/ndex/data/ndex.log
```

**Stopping NDEx**: Run `ndex-server.sh stop` — this blocks until the Jetty process has fully exited. Supporting services (postgres, keycloak, solr, mailhog) keep running.

**Hot reload**: Java source changes are picked up automatically within ~5 seconds via Jetty's file scanner. Changes compiled by your IDE (or by `mvn compile` in a terminal) are reflected live — no restart required.

**Restarting NDEx**: `ndex-server.sh stop` then `ndex-server.sh start`. The log file appends across restarts. All services are already initialized and the Maven cache is warm, so restart is fast.

---

## Logging

The devcontainer has two separate log streams:

**Core services** (postgres, keycloak, solr, mailhog) log to the container's stdout/stderr alongside `dev-entrypoint.sh` init messages. View them from the host:

```bash
docker logs -f <container-id>
```

**NDEx API** logs to `/apps/ndex/data/ndex.log` inside the container (not stdout). Follow it from a container shell:

```bash
tail -f /apps/ndex/data/ndex.log
```

For log level configuration (NDEx `Log-Level`, Keycloak log level controls), the same settings and procedures apply as in the deploy image — see [Log Level](../docker/README.md#log-level) and [Keycloak Log Level](../docker/README.md#keycloak-log-level) in docker/README.md. In the devcontainer, use `supervisorctl restart keycloak` instead of `docker restart ndex` when restarting Keycloak.

---

## Accessing Services

NDEx and Keycloak ports are published directly to the host by Docker (`runArgs` in `devcontainer.json`) — no VS Code port forwarding required. Keycloak is available as soon as the container starts. The NDEx API is available on port 8080 only after starting it manually (see [Development Cycles](#development-cycles)).

| Service | Host URL | Auth |
|---|---|---|
| NDEx API | http://localhost:8080/v3 | — |
| Keycloak admin | http://localhost:8085/admin | admin / get credentials from `/etc/keycloak.otp` |
| Keycloak account | http://localhost:8085/realms/ndex/account/ | your account |

Other services (PostgreSQL, Solr, MailHog) run inside the container but are not exposed to the host by default. See [Exposed Ports](#exposed-ports) to change this.

---

## Testing the Devcontainer

NDEx must be running before executing integration tests. Start it first and confirm startup is complete:

```bash
ndex-server.sh start
tail -f /apps/ndex/data/ndex.log   # wait until you see Jetty "Started" message, then Ctrl-C
```

Then run the integration test targeting localhost:

```bash
# From inside the container:
docker/test/integration-test.sh --remote-ndex-url http://localhost:8080
```

Or as a one-liner from the host:
```bash
devcontainer exec --workspace-folder /path/to/ndex-rest bash -c \
  "cd /workspaces/ndex-rest && docker/test/integration-test.sh --remote-ndex-url http://localhost:8080"
```

`--remote-ndex-url` skips all Docker build and container management steps — it runs only the API calls against a running NDEx instance at the given URL. See `docker/README.md` for full integration test documentation including deploy image testing.

---

## Configuration

### Exposed Ports

All services run on fixed internal ports. NDEx (8080) and Keycloak (8085) are published to the host via `runArgs` in `devcontainer.json` — no VS Code port forwarding involved. All other services are reachable inside the container but not exposed to the host by default.

**`NDEX_PORT`** is the only env var that changes actual service behavior — it sets Jetty's listen port and the `HostURI` value written into `ndex.properties` on first boot. Default: `8080`.

To expose an additional service port to the host, add a `-p host:container` entry to `runArgs` in `.devcontainer/devcontainer.json` and rebuild:

```jsonc
"runArgs": ["-p", "8080:8080", "-p", "8085:8085", "-p", "8983:8983"]
```

### NDEx Properties

The NDEx application reads `/apps/ndex/config/ndex.properties`, generated on first boot. Edit it inside the container and restart NDEx (`ndex-server.sh stop && ndex-server.sh start`) to apply changes. For full configuration reference, see [Configuration](../docker/README.md#configuration) in docker/README.md.

### Persistence

All service state is ephemeral by default — removed with the container. To persist service data across container removals, add bind mounts to `devcontainer.json`:

```jsonc
"mounts": [
    "source=/host/path/ndex-data,target=/apps/ndex,type=bind,consistency=cached",
    "source=/host/path/postgres-data,target=/apps/postgres/data,type=bind,consistency=cached"
]
```

One Docker-managed volume (`ndex-rest-m2`) is created automatically for the Maven cache — see [Maven Cache](#maven-cache) below. For details on what each service stores and how config/data paths are structured, see [Configuration](../docker/README.md#configuration) in docker/README.md.

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

Removing and recreating the container resets all service state. The Maven cache is **not** reset by container removal — it persists in the `ndex-rest-m2` Docker volume.

```bash
# Stop and remove (resets service state)
docker stop <container-id> && docker rm <container-id>

# Restart:
devcontainer up --workspace-folder /path/to/ndex-rest
```

To reset the Maven cache: `docker volume rm ndex-rest-m2`

For selective per-service resets (config only, data only, or individual services), see [Re-initializing](../docker/README.md#re-initializing) in docker/README.md — the sentinel-based reset mechanism is identical in the devcontainer.
