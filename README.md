ndex-rest
=========

NDEx Rest Server

## Container Deployments

A production-oriented Docker image bundles the full NDEx stack — PostgreSQL, Keycloak, Apache Solr, MailHog, and the NDEx REST API (Tomcat) — into a single self-contained image.

The `runtime-base` stage is also used by the devcontainer image, ensuring both environments run identical service installations.

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

## MCP Server

NDEx exposes an [MCP (Model Context Protocol)](https://modelcontextprotocol.io) server at `/mcp/*`, enabling AI agents and LLM clients to interact with NDEx networks and folders through a standardized tool interface.

**Servlet architecture (separate from the REST API):**
- The MCP servlet is registered programmatically at startup by `McpServletContextListener` using the MCP SDK's `HttpServletStreamableServerTransportProvider` — it cannot be declared in `web.xml` because the provider uses a builder-only construction pattern
- It runs as an independent async servlet alongside the RESTEasy JAX-RS servlet, sharing the same Tomcat/Jetty container but with its own request pipeline
- `/mcp/manifest` is served by a dedicated `McpManifestServlet` and is always publicly accessible — no credentials required

**Authentication (handled by `McpAuthFilter` before tools are invoked):**
- All `/mcp/*` requests (except `/mcp/manifest`) pass through `McpAuthFilter`, which supports Basic auth and Bearer token (KeyCloak / Google OAuth)
- Invalid or missing credentials return HTTP 401 with a `WWW-Authenticate: Bearer` OAuth challenge header
- On success the resolved `User` object is attached to the servlet request and propagated into every tool call via MCP transport context

**Auth and no-auth tool pathways:**
- **Write/mutate tools** (`create_network`, `update_network`, `delete_network`, `manage_folder`, `set_network_properties`, `set_network_system_properties`, `update_network_profile`) perform an explicit auth check — requests with no authenticated user are rejected immediately with a structured 401 result
- **Read tools** (`search_network`, `get_network_summary`, `download_network`, `get_folder`) delegate to the underlying NDEx service layer, which enforces per-resource visibility: public networks and folders are accessible without credentials; private resources require either an authenticated user or a valid `accessKey` query parameter
- All tools invoke NDEx services **in-process** (no outbound HTTP) — the same `HttpServletRequest` carrying the authenticated user is passed directly to `NetworkServiceV3`, `SearchServiceV2`, `FolderServiceV3`, etc., so permission checks work transparently without any re-authentication
