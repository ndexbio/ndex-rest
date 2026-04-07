# NDEX Monolithic deployment (all services in one container)

## Ephemeral (data lost on container removal)

```bash
docker run --platform linux/amd64 -d \
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

All services state is ephemeral by default since the services store data on intenral container file system, which means all service level data is gone when container is deleted. Unless bind-mounts to host directories were used, then those paths are unaffected by container removal since they reside outside of container.

## Persistent (data survives container removal)

Bind-mount host directories at the paths you want to persist. Mount only what you need — each path is independent:

```bash
docker run --platform linux/amd64 -d \
  --name ndex \
  -p 8080:8080 \
  -p 8085:8085 \
  -p 8025:8025 \
  -p 8983:8983 \
  -p 5432:5432 \
  -v /host/path/ndex-config:/apps/ndex/config \
  -v /host/path/ndex-data:/apps/ndex/data \
  -v /host/path/postgres-config:/apps/postgres/config \
  -v /host/path/postgres-data:/apps/postgres/data \
  -v /host/path/keycloak-config:/apps/keycloak/config \
  -v /host/path/keycloak-data:/apps/keycloak/data \
  -v /host/path/solr-config:/apps/solr/config \
  -v /host/path/solr-data:/apps/solr/data \
  -v /host/path/mailhog-config:/apps/mailhog/config \
  ndexbio/ndex-rest \
  --ndex --postgres --keycloak --solr --mailhog
```

No Docker-managed volumes are created. Each `-v` flag routes directly to your host filesystem. Persistence is entirely at your discretion — omit any `-v` flag to let that service's state remain ephemeral.

---

## Stopping the Container

**Graceful stop (container preserved):**
```bash
docker stop ndex
```

**Force Stop and remove (container is deleted):**
```bash
docker rm -f ndex
```

# NDEx Microservices Deployment

This is example of a three-container deployment of `ndexbio/ndex-rest` with persistence. It uses an externally provided Postgres DB. Each container runs on a dedicated host and services communicate via published ports and hostnames.

> **Docker example note**: If you are running all three containers with docker on the same host then substitute `host.docker.internal` for the `<SERVICES_HOST>`, `<KEYCLOAK_HOST>`, `<NDEX_HOST>`, and `<PG_HOST>` placeholders instead of `localhost`. Inside a container, `localhost` refers to the container's own loopback interface — not the host machine — so using `localhost` would fail to reach services published on the host's ports. `host.docker.internal` is Docker's built-in DNS name that always resolves to the host machine from within any container and this is only applicable for docker on single host.

| Host variable | Container | Flags | Exposes |
|---|---|---|---|
| `SERVICES_HOST` | `ndex-services` | `--solr --mailhog` | Solr `:8983`, MailHog SMTP `:1025` |
| `KEYCLOAK_HOST` | `ndex-keycloak` | `--keycloak --config /etc/ndex-config.toml` | Keycloak `:8085` |
| `NDEX_HOST` | `ndex-app` | `--ndex --config /etc/ndex-config.toml` | NDEx API `:8080` |

This example presumes an external PostgreSQL server exists and is user-managed. Another option could be to run the Postgres server as another container instance from `ndexbio/ndex-rest` with the `--postgres` flag only and persistent volume mounts for /apps/postgres/data and /apps/postgres/config and expose port 5432. In either case, the role and database for both ndex and keycloak services must be pre-created first (Steps 1–2).

## Prerequisites

- Docker 24+ on each host
- `ndexbio/ndex-rest` image available on each host — see [Building](./README.md#building)
- External PostgreSQL 14+ reachable from `KEYCLOAK_HOST` and `NDEX_HOST`
- `openssl` available on `KEYCLOAK_HOST`

---

## Phase 1 — PostgreSQL Setup

Run these SQL commands as a PostgreSQL superuser **before starting any container**.

### Step 1 — Create NDEx database and role

Replace `<NDEX_DB_PASSWORD>` with a strong password.

```sql
CREATE ROLE ndexserver WITH LOGIN PASSWORD '<NDEX_DB_PASSWORD>';
CREATE DATABASE ndex WITH OWNER ndexserver ENCODING 'UTF8';
\c ndex
GRANT CONNECT ON DATABASE ndex TO ndexserver;
GRANT USAGE ON SCHEMA public TO ndexserver;
-- NDEx creates and owns the 'core' schema on first start; pre-grant defaults so all
-- future tables in both schemas are accessible to ndexserver.
CREATE SCHEMA core
CREATE EXTENSION IF NOT EXISTS plpgsql WITH SCHEMA pg_catalog;
COMMENT ON EXTENSION plpgsql IS 'PL/pgSQL procedural language';
CREATE EXTENSION IF NOT EXISTS dblink WITH SCHEMA core;
COMMENT ON EXTENSION dblink IS 'connect to other PostgreSQL databases from within a database';

ALTER DEFAULT PRIVILEGES IN SCHEMA core   GRANT SELECT, INSERT, UPDATE, DELETE ON TABLES TO ndexserver;
ALTER DEFAULT PRIVILEGES IN SCHEMA public GRANT SELECT, INSERT, UPDATE, DELETE ON TABLES TO ndexserver;
```

### Step 2 — Create Keycloak database and role

Replace `<KC_DB_PASSWORD>` with a strong password. Record it — needed in Step 5.

```sql
CREATE ROLE keycloak WITH LOGIN PASSWORD '<KC_DB_PASSWORD>';
CREATE DATABASE keycloak WITH OWNER keycloak ENCODING 'UTF8';
\c keycloak
GRANT CONNECT ON DATABASE keycloak TO keycloak;
ALTER DEFAULT PRIVILEGES IN SCHEMA public GRANT SELECT, INSERT, UPDATE, DELETE ON TABLES TO keycloak;
```

---

## Phase 2 — Services Container (SERVICES_HOST)  with volume persistence

### Step 3 — Host directories

```bash
BASE=~/ndex-deploy
mkdir -p $BASE/solr-data
```

### Step 4 — Start services container with persistence

```bash
docker run -d \
  --name ndex-services \
  -p 8983:8983 \
  -p 1025:1025 \
  -e SOLR_JETTY_HOST=0.0.0.0 \
  -v $BASE/solr-data:/apps/solr/data \
  ndexbio/ndex-rest \
  --solr --mailhog
```

> `SOLR_JETTY_HOST=0.0.0.0` is required — Solr 9 defaults to `127.0.0.1`; without it the NDEx
> container on `NDEX_HOST` cannot reach Solr.

```bash
docker logs -f ndex-services   # wait for "NDEx Deploy Container Ready"
```

---

## Phase 3 — Keycloak Container (KEYCLOAK_HOST) with volume persistence.

### Step 5 — Host directories and config file

```bash
BASE=~/ndex-deploy
mkdir -p $BASE/{keycloak-config,keycloak-data}
```

Create `ndex-config.toml` with the Keycloak hostname and DB credentials from Step 2.
`<KEYCLOAK_HOST>` is the publicly reachable hostname or IP of this host — it is embedded in
JWT `iss` claims and OAuth redirect URLs; it must match `keycloakIssuer` in Step 8.

```bash
cat > $BASE/ndex-config.toml <<'EOF'
[keycloak]
hostName = "<KEYCLOAK_HOST>"

[keycloakDb]
host     = "<PG_HOST>"
port     = 5432
name     = "keycloak"
user     = "keycloak"
password = "<KC_DB_PASSWORD>"
EOF
chmod 600 $BASE/ndex-config.toml
```

### Step 6 — Start Keycloak container

On first boot, `start.sh` verifies Keycloak DB connectivity, generates `keycloak.conf` with the
DB connection details and hostname from `ndex-config.toml`, and generates an RSA key pair into
`/apps/keycloak/config/` inside the container.

```bash
docker run -d \
  --name ndex-keycloak \
  -p 8085:8085 \
  -v $BASE/ndex-config.toml:/etc/ndex-config.toml:ro \
  -v $BASE/keycloak-config:/apps/keycloak/config \
  -v $BASE/keycloak-data:/apps/keycloak/data \
  ndexbio/ndex-rest \
  --keycloak --config /etc/ndex-config.toml
```

```bash
docker logs -f ndex-keycloak   # wait for "NDEx Deploy Container Ready"
```

```bash
# Verify Keycloak health (management port — internal only):
docker exec ndex-keycloak curl -s -o /dev/null -w "%{http_code}" http://localhost:9000/health/ready
# Expect: 200
```

See [Credentials](./README.md#credentials) for retrieving the Keycloak admin password.

### Step 7 — Extract RSA public key

After Keycloak has started, exec into the container to convert `cert.pem` to the base64-DER
string that NDEx requires:

```bash
KEYCLOAK_PUBLIC_KEY=$(docker exec ndex-keycloak bash -c \
  "openssl x509 -in /apps/keycloak/config/cert.pem -pubkey -noout \
   | openssl rsa -pubin -pubout -outform DER | base64 -w 0")
echo "$KEYCLOAK_PUBLIC_KEY"
```

Copy this value — it is needed in Step 8.

---

## Phase 4 — NDEx Container (NDEX_HOST)

### Step 8 — Host directories and config file

```bash
BASE=~/ndex-deploy
mkdir -p $BASE/{ndex-config,ndex-data}
```

Create `ndex-config.toml` with the NDEx DB credentials (Step 1) and all five service endpoint
values. On first boot `start.sh` copies the default config files and substitutes all placeholders
automatically — **no manual `ndex.properties` editing required**.

```bash
cat > $BASE/ndex-config.toml <<'EOF'
[ndexDb]
host          = "<PG_HOST>"
port          = 5432
name          = "ndex"
user          = "ndexserver"
password      = "<NDEX_DB_PASSWORD>"

[ndex]
smtpHost          = "<SERVICES_HOST>"
solrUrl           = "http://<SERVICES_HOST>:8983/solr"
hostUri           = "http://<NDEX_HOST>:8080"
keycloakIssuer    = "http://<KEYCLOAK_HOST>:8085/realms/ndex"
keycloakPublicKey = "<value from Step 7>"
EOF
chmod 600 $BASE/ndex-config.toml
```

### Step 9 — Start NDEx container

On first boot, `start.sh` connects to the external NDEx DB, loads the schema if not already
present, and writes all connection details and service endpoints into `ndex.properties`.

```bash
docker run -d \
  --name ndex-app \
  -p 8080:8080 \
  -v $BASE/ndex-config.toml:/etc/ndex-config.toml:ro \
  -v $BASE/ndex-config:/apps/ndex/config \
  -v $BASE/ndex-data:/apps/ndex/data \
  ndexbio/ndex-rest \
  --ndex --config /etc/ndex-config.toml
```

```bash
docker logs -f ndex-app   # wait for "NDEx Deploy Container Ready"
```

---

## Phase 5 — Verify

### Logs

```bash
docker logs -n 200 ndex-app  
```

### API
```bash
curl -s -o /dev/null -w "%{http_code}" \
  -X POST -H "Content-Type: application/json" -d '{}' \
  "http://<NDEX_HOST>:8080/v2/search/network"
# Expect: 200 (empty result set is fine — no data needs to exist)
```

### Keycloak realm

```bash
curl -s -o /dev/null -w "%{http_code}" http://<KEYCLOAK_HOST>:8085/realms/ndex
# Expect: 200
```

---

## Stopping

```bash
docker stop ndex-app        # on NDEX_HOST
docker stop ndex-keycloak   # on KEYCLOAK_HOST
docker stop ndex-services   # on SERVICES_HOST
```

## Restarting

Start in order: services → keycloak → ndex-app. 
See [Re-initializing](./README.md#re-initializing) to wipe any persistence volumes and start fresh.

```bash
docker start ndex-services   # on SERVICES_HOST — wait for "NDEx Deploy Container Ready"
docker start ndex-keycloak   # on KEYCLOAK_HOST — wait for "NDEx Deploy Container Ready"
docker start ndex-app        # on NDEX_HOST
```
