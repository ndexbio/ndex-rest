ndex-rest
=========

NDEx Rest Server

## Container Deployments

A production-oriented [Docker image](docker/README.md]) bundles the full NDEx stack — PostgreSQL, Keycloak, Apache Solr, MailHog, and the NDEx REST API (Tomcat) — into a single self-contained image.


Key features:
- **Monolithic or distributed**: run all services in one container, or split them across containers using command-line flags (`--ndex`, `--postgres`, `--solr`, `--keycloak`, `--mailhog`)
- **Flag-driven startup**: enable only the services you need — no environment variables required
- **Ephemeral by default**: all state lives in the container layer; bind-mount host paths to persist config or data across restarts
- **Auto-provisioned credentials**: in monolithic mode, randomized credentials are written to `/etc/<svc>.otp` on first boot and deleted after 2 hours
- **Integration tested**: a live integration test script (`docker/test/integration-test.sh`) validates the ndex API is working in container through full API lifecycle including CX1/CX2 network upload, summary polling, CX2 retrieval, and Solr search

For complete build, run, configuration, and testing instructions, see **[docker/README.md](docker/README.md)**.

---

## Development Environment

A fully self-contained devcontainer bundle is provided under [.devcontainer](.devcontainer). It provides PostgreSQL, Keycloak, Solr, MailHog, and the NDEx API (Jetty) built from current source code of ndex-rest into a single container — no external service installation needed.

For complete setup, configuration, testing, and persistence instructions, see **[.devcontainer/README.md](.devcontainer/README.md)**.

---

## Admin: Reindex Repair (`/v3/admin/reindex-v3`)

The reindex endpoint rebuilds Solr search indexes for networks that are either unindexed or stuck in an index-failure error state. It is intended as an operational repair tool — run it after a Solr outage, a partial migration, or when networks appear unsearchable due to a prior indexing failure.

### When to use it

- Networks are missing from search results and show an `errorMessage` like `"Failed to create Index on network."` in their summary
- After restoring Solr from a backup or recreating Solr cores
- After a server restart that interrupted an in-progress reindex

### Which networks are reindexed

The endpoint processes a targeted subset of non-deleted networks — not every network indiscriminately:

| Network error state | Included? |
|---|---|
| `errorMessage` is `null` (no error) | Yes |
| `errorMessage` starts with `"Failed to create Index on network."` | Yes — prior index failure, safe to retry |
| `errorMessage` set by upload or CX validation failure | **No** — skipped to avoid masking data integrity issues |

After a network is successfully reindexed, its `errorMessage` is cleared to `null` automatically.

The operation is **synchronous** — the HTTP response is not returned until all eligible networks have been processed. For large deployments with many networks, this call may take several minutes.

### How to invoke it

```
GET /v3/admin/reindex-v3?password=<MigrationPassword>
```

The `MigrationPassword` is set in `ndex.properties` (key: `MigrationPassword`). In the default Docker image it is `changeme` — change it before production use.

**curl example:**
```bash
curl "http://localhost:8080/v3/admin/reindex-v3?password=changeme"
```

Returns `200 OK` with body `Reindexing complete` on success. Returns `401` if the password is wrong, or `500` with an error message if reindexing fails partway through.

### Verifying the result

After the call completes, fetch a network summary to confirm the `errorMessage` field is cleared:

```bash
curl -u username:password \
  "http://localhost:8080/v3/networks/<uuid>/summary" | python3 -m json.tool | grep errorMessage
```

A missing or `null` `errorMessage` field confirms the network was successfully reindexed.

---

## MCP Server

NDEx exposes an [MCP (Model Context Protocol)](https://modelcontextprotocol.io) server at `/mcp/*`, enabling AI agents and LLM clients to interact with NDEx networks and folders through a standardized tool interface.

**Servlet architecture (separate from the REST API):**
- The MCP servlet is registered programmatically at startup by `McpServletContextListener` using the MCP SDK's `HttpServletStreamableServerTransportProvider` — it cannot be declared in `web.xml` because the provider uses a builder-only construction pattern
- It runs as an independent async servlet alongside the RESTEasy JAX-RS servlet, sharing the same Tomcat/Jetty container but with its own request pipeline which is necessary to support the streaming transport over http that mcp protocol requires and JAX-RS servlet doesn't support.
- `/mcp/manifest` is served by a dedicated `McpManifestServlet` and is always publicly accessible — no credentials required
- `/mcp/upload` and `/mcp/download` are served by `UploadPreSignedServlet` and `DownloadPreSignedServlet` — both bypass auth filtering because they are guarded by single-use pre-signed tokens based an authenticated users(120-second TTL) issued by `request_network_upload` and `request_network_download`; authorization is enforced at token-issuance time (the calling user must be authenticated), not at transfer time

**Authentication (handled by `McpAuthFilter` before tools are invoked):**

All `/mcp` requests (except `/mcp/manifest`, `/mcp/upload`, and `/mcp/download`) pass through
`McpAuthFilter`. On success the resolved `User` object is attached to the servlet request and
propagated into every tool call via MCP transport context.

Auth is attempted in this order:

1. **Basic auth (primary)** — if `Authorization: Basic <base64>` is present it is validated
   against the NDEx user database. On success the request proceeds. On failure a plain 401 is
   returned with no `WWW-Authenticate` header (no OAuth fallback is triggered).

2. **Bearer token** — if `Authorization: Bearer <token>` is present it is validated using the
   configured OAuth authenticator (Keycloak or Google — see configuration below). On failure a
   plain 401 is returned.

3. **No credentials** — anonymous is allowed on mcp, it will allow an agent to handshake with mcp server and excehange manifests. However, each specific tool additionally checks authentication vs. anon during invocation as as a pass-through to the underlying api auth requirements that each tool wraps. some tools will allow anon invocation and others wont.

**Auth and no-auth tool pathways:**
- **Auth-required tools** (`request_network_upload`, `delete_network`, `manage_folder`, `get_folder`, `share_network`, `get_user_networks`, `get_user_info`, `set_network_properties`, `set_network_systemproperties`, `update_network_profile`) perform an explicit auth check — requests with no authenticated user are rejected and get a mcp response with reason of authentication error.
- **Always-public tools** (`get_connection_status`, `search_network`, `get_network_summary`) never require authentication.
- **Conditionally-auth tools** (`request_network_download`) perform a visibility pre-check on requested file, if the target network is PUBLIC or UNLISTED, anonymous callers are allowed if the network is PRIVATE and the caller has no authenticated session, the tool is denied.


**OAuth discovery endpoints (`/.well-known/oauth-protected-resource`):**

As of current, the NDEx MCP server will not initiate OAuth 2.1 + PKCE flows for unauthenticated clients. It will accept an existing OAuth Bearer token but that is the extent of support for now. 
`OAuthProtectedResourceServlet` serves RFC 9728 Protected Resource Metadata at both
`/.well-known/oauth-protected-resource/mcp` and `/.well-known/oauth-protected-resource`.
The response always includes an `authorization_servers` field whose value depends on what is
configured in `ndex.properties`:

| `ndex.properties` setting | `authorization_servers` value in response |
|---------------------------|-------------------------------------------|
| `USE_KEYCLOAK_AUTHENTICATION=true` + `KEYCLOAK_ISSUER=<url>` | `["<KEYCLOAK_ISSUER value>"]` |
| `USE_GOOGLE_AUTHENTICATION=true` | `["https://accounts.google.com"]` |
| Neither (default) | `[]` — empty array |

An empty `authorization_servers` array explicitly tells MCP clients that no OAuth authorization
server is configured, preventing them from probing `/.well-known/oauth-authorization-server/*`.
When a Keycloak or Google issuer is present, MCP clients can use that server to obtain Bearer
tokens that the server will then validate via `McpAuthFilter`.

---

### Connecting an AI Agent

If your agent is not listed below, consult its documentation for configuring an MCP server
with Streamable HTTP transport. The NDEx MCP server URL is:

```
http://<host>:8080/mcp
```

For a local Docker container that is `http://localhost:8080/mcp`. Replace the host and port
for any remote NDEx server.

---

#### Claude Desktop

**Prerequisite:** In Claude Desktop go to **Settings > Extensions > Advanced** and enable
**Use Built-in Node.js for MCP**. This is required for the extension to function.

Download `ndex-mcp.mcpb` from the
[releases page](https://github.com/ndexbio/ndex-rest/releases). In Claude Desktop go to
**Settings > Extensions**, click **Install Extension**, and select the downloaded file.

The **NDEx MCP** connector will appear in **Customize > Connectors**. Fill in the host,
port, username, and password fields. Leave username/password blank for anonymous read-only
access to public networks.

---

#### Claude Code

```bash
claude mcp add --transport http ndex http://localhost:8080/mcp \
  --header "Authorization: Basic $(echo -n 'username:password' | base64)"
```

Or add to `~/.claude.json` (user-wide) or `.mcp.json` (project root):

```json
{
  "mcpServers": {
    "ndex": {
      "type": "http",
      "url": "http://localhost:8080/mcp",
      "headers": {
        "Authorization": "Basic dXNlcm5hbWU6cGFzc3dvcmQ="
      }
    }
  }
}
```

`dXNlcm5hbWU6cGFzc3dvcmQ=` is the base64 encoding of `username:password`.
Generate yours: `echo -n 'username:password' | base64` (macOS/Linux) or
`[Convert]::ToBase64String([Text.Encoding]::ASCII.GetBytes("username:password"))`
(Windows PowerShell). Omit the `headers` block for anonymous access.

---

#### GitHub Copilot CLI

Add to `~/.copilot/mcp-config.json` (user-wide, default location — override with `COPILOT_HOME`):

```json
{
  "mcpServers": {
    "ndex": {
      "type": "http",
      "url": "http://localhost:8080/mcp",
      "headers": {
        "Authorization": "Basic dXNlcm5hbWU6cGFzc3dvcmQ="
      }
    }
  }
}
```

`dXNlcm5hbWU6cGFzc3dvcmQ=` is the base64 encoding of `username:password`.
Generate yours: `echo -n 'username:password' | base64` (macOS/Linux) or
`[Convert]::ToBase64String([Text.Encoding]::ASCII.GetBytes("username:password"))`
(Windows PowerShell). Omit the `headers` block for anonymous access.

Alternatively, add the server interactively from within a running `copilot` session using the `/mcp add` slash command.

---

#### VS Code GitHub Copilot

Add to `.vscode/mcp.json` in the workspace root. The `${input:ndex-token}` reference
prompts once and is stored securely by VS Code:

```json
{
  "inputs": [
    {
      "type": "promptString",
      "id": "ndex-token",
      "description": "NDEx Basic Auth token — base64 of username:password",
      "password": true
    }
  ],
  "servers": {
    "ndex": {
      "type": "http",
      "url": "http://localhost:8080/mcp",
      "headers": {
        "Authorization": "Basic ${input:ndex-token}"
      }
    }
  }
}
```

Omit the `headers` block and `inputs` array for anonymous access.
