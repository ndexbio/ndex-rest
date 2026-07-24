# Changelog

All notable changes to this project will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.0.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).


## Unreleased

### Breaking Changes

- **The NDEx network set feature has been removed**, except for read-only endpoints re-enabled for backward-compatible reads of legacy data. All `/v2/networkset` *write* endpoints return **HTTP 501 Not Implemented**. New network sets can no longer be created; use folders + shortcuts + folder access keys instead — see the [V3 Migration Guide](docs/V3-Migration-Guide.md). Endpoint status:
  - **Re-enabled (read-only):** `GET /v2/networkset/{id}`, `GET /v2/networkset/{id}/accesskey`, and `GET /v2/user/{userid}/networksets` now serve the frozen, archived `network_set` / `network_set_member` tables directly — `GET /v2/networkset/{id}` returns the archived set (members filtered to networks the caller can read, a valid access key returning all); `GET /v2/networkset/{id}/accesskey` returns the archived key to the set owner; `GET /v2/user/{userid}/networksets` returns the archived sets owned by the user (members filtered to the networks the caller can read; honors `offset`/`limit`/`summary`/`showcase`). All are marked deprecated/archived in Swagger.
  - **Retired → 501:** `POST /v2/networkset`, `PUT|DELETE /v2/networkset/{id}`, `POST|DELETE /v2/networkset/{id}/members`, `PUT /v2/networkset/{id}/accesskey`, `PUT /v2/networkset/{id}/systemproperty`.
  - The `networkSetCount` field is removed from `GET /v2/user/{userid}/networkcount` response model.
  - The legacy `network_set` / `network_set_member` tables are retained as frozen/read-only; the `network_set_member → network` foreign key is dropped so the tables no longer couple to network deletion.
- **Swagger / OpenAPI** — every removed network-set endpoint is marked `deprecated` with a documented `501` response.
- **`GET /v3/networks/{networkid}/DOI` removed.** The v3 DOI-mint endpoint is deleted; it duplicated the v2 admin DOI flow (`POST /v2/admin/request` with `type=DOI`), which remains the single DOI mechanism. The endpoint existed only to serve a two-step, asynchronous DOI workflow (a DOI request emailed an admin a link — `…/DOI?key=<encrypted-network-id>&email=…` — which the admin clicked to mint). That workflow was retired in Feb 2024 ("Mint DOI immediately when user requests", UD-2788): the request handler was changed to mint synchronously in-process, and the email/link generation was commented out. Since then nothing has generated that link and nothing consumed the endpoint's `key` parameter, so the endpoint had been dead code — its removal breaks no live flow.
### Changed

- **DOI requests auto-manage network access.** `POST /v2/admin/request` with `type=DOI` no longer requires the caller to allocate an access key. A network that stays **PRIVATE** gets a network-scoped access key embedded in the minted DOI viewer URL — an access key already on the network is **reused** (enabled if it was off), otherwise one is **generated**. A **certified** request makes the network **PUBLIC** with **no** access key (and no longer leaves a stray access key enabled on it). Edge case: a PRIVATE network with no enabled access key at mint time is rejected with **400 Bad Request** (a data-integrity guard, to avoid minting an `accesskey=null` URL) — recover by issuing a `type=Cancel_DOI` request, then retry.
- **Access-key validation now follows the v3 folder hierarchy (issue #133).** A key is valid for a network when it matches the network's own access key or an enabled key on any ancestor folder (full-chain accrual); the legacy `network_set`-based validation path was removed. Access-key-authorized `GET /v3/files/folders/{id}/list` and `…/count` now return only the key-accessible children (folders + networks; shortcuts are excluded, since access keys do not traverse shortcuts). A paired one-off CLI migration (`DbMigrationTool`) reparents single-folder shortcut targets into their access-key folder.


## [3.0.2] - 2026-07-07

### Added

- **OAuth register & reset URLs in admin status** — `/admin/status` (v1 & v2) now returns the OAuth register URL, reset URL, and client id so client apps (e.g. cy-ndex-2) can surface real register/reset links to users. Swagger docs added for the new fields; the register redirect points at `/swagger/index.html`. See [#97](https://github.com/ndexbio/ndex-rest/pull/97), [#99](https://github.com/ndexbio/ndex-rest/pull/99).
- **`unlist_none` reindex option** — New option to unlist public networks whose Solr index level is `NONE`. Network locking is scoped to the DB-update window (not held across the reindex op) with rollback on error. See [#96](https://github.com/ndexbio/ndex-rest/pull/96).
- **Folder/shortcut `visibility`** — `visibility` (`PUBLIC`/`PRIVATE`/`UNLISTED`) can now be set on folder and shortcut create/update requests (`POST`/`PUT /v3/files/folders` and `…/shortcuts`, and the MCP `manage_folder` tool), defaulting to `PRIVATE` when omitted, and is reported on folder/shortcut read responses. Requires `ndex-object-model` 3.0.2-SNAPSHOT. See [#129](https://github.com/ndexbio/ndex-rest/pull/129) (closes #127).

### Breaking Changes

- **The NDEx group feature has been removed.** All group endpoints now return **HTTP 501 Not Implemented**, and group-based network permissions no longer exist (a user reaches a network only by ownership or a direct user permission). Use folders + visibility + folder permission sharing instead — see the [V3 Migration Guide](docs/V3-Migration-Guide.md). Affected endpoints:
  - `POST|GET|PUT|DELETE /v2/group/{id}` and its `…/membership`, `…/permission`, and `…/permissionrequest[/{requestid}]` sub-paths → 501 (the entire `/v2/group` resource is retired).
  - The v1 `/group` resource (create/get/update/delete, `/search`, `/groups`, `/{id}/member/*`, `/{id}/network/*`, `/{id}/user/*`, `/{id}/membership/*`) → 501.
  - `GET /v2/user/{id}/membership` and `…/membershiprequest[/{requestid}]` (POST/GET/PUT/DELETE — the JoinGroup flow) → 501.
  - v1 `GET /user/{id}/group/{permission}/{start}/{size}` and `GET /user/membership/group/{groupid}` → 501.
  - `POST /v2/batch/group` (get groups by UUIDs) → 501.
  - `POST /v2/search/group` (search groups) → 501.
  - `GET /v2/network/{id}/permission` — `type=group` now returns 501; `type=user` is unchanged.
  - `DELETE /v2/network/{id}/permission` and `PUT /v2/network/{id}/permission` — supplying the `groupid` parameter now returns 501; the `userid` path is unchanged.
  - The membership/permission request accept flow no longer grants group permissions; only `UserNetworkAccess` requests are actionable (group requests can no longer be created).
- **Request parameter changes**
  - Removed the `directOnly` query parameter from `GET /v2/user/{id}/permission` (group-derived permissions no longer exist, so it had become a no-op).
  - v1 `GET /user/membership/network/{networkid}/{directonly}` → path shortened to `…/{networkid}` (the `{directonly}` path segment was removed).
- **Response / content changes**
  - `GET /v2/network/{id}/permission?type=user` no longer includes any group entries (the response schema is unchanged; group rows simply never appear).
  - Admin status (`/admin/status`, v1 & v2) `groupCount` is now always `0`.
- **Swagger / OpenAPI** — all removed group endpoints are marked `deprecated`; the mixed network-permission endpoints have updated descriptions noting that group access is no longer supported.
- [#112](https://github.com/ndexbio/ndex-rest/pull/112)

### Fixed

- **Edgeless-network search ranking** — Networks with zero edges are now pushed to the bottom of Solr search results instead of ranking equally with content-bearing networks. See [#123](https://github.com/ndexbio/ndex-rest/pull/123) (closes #116/#114).
- **Invalid UUID handling on v3 search** — v3 search endpoints that take a UUID path parameter now return **400 Bad Request** for a malformed UUID instead of a generic 500. See [#106](https://github.com/ndexbio/ndex-rest/pull/106).
- **Shortcuts in network search** — Added an explicit `includeShortcuts` flag and removed redundant logging and network lookups that treated shortcuts as networks. See [#105](https://github.com/ndexbio/ndex-rest/pull/105) (relates to #101).
- **Reindex healing of networks stuck in error state** — `SolrIndexBuilder` now clears a network's `error` flag after a successful reindex, so batch reindex can heal networks that were stamped with an error while Solr was unreachable. See [#93](https://github.com/ndexbio/ndex-rest/pull/93).

## [3.0.1] - 2026-06-18

### Fixed

- **Out-of-memory (OOM) fix** — Addressed a memory issue that could lead to OOM conditions in production workloads. See [#103](https://github.com/ndexbio/ndex-rest/pull/103).


## [3.0.0] - 2026-05-27

First GA release of NDEx REST 3.0 — a major architectural upgrade from the 2.x release line, introducing a new `/v3` API surface, a hierarchical file system model, containerized deployment, and an embedded MCP server. For the full commit history of this release, refer to the [`ndex3develop`](https://github.com/ndexbio/ndex-rest/tree/ndex3develop) branch.

### Added

- **New `/v3` REST API** — Complete rewrite of all major endpoints under the `/v3` path, including `NetworkServiceV3`, `UserServicesV3`, `SearchServiceV3`, `AdminServiceV3`, `BatchService`, and `CyWebWorkspaceServices`. The v2 API remains available for backwards compatibility while v3 becomes the primary interface.

- **Folders & Shortcuts (NDEx File System)** — Hierarchical file system for organizing networks and other content. Introduces `NdexFolder` and `NdexShortcut` entities with full CRUD, visibility controls (public/unlisted/private), permission sharing, access-key-based sharing, transfer ownership, and copy operations via `FolderServiceV3`, `ShortcutServiceV3`, and `FileServiceV3`.

- **Docker Containerization** — Production-ready containerization with multi-stage Dockerfile, CI/CD release pipeline, and multi-architecture manifest publishing supporting both `arm64` and `amd64` platforms.

- **Embedded MCP Server at `/mcp`** — Model Context Protocol server embedded directly in the REST service, exposing tools (`GetFolderTool`, `ManageFolderTool`, `UpdateNetworkProfileTool`) for LLM-driven network and folder management. Includes per-request transaction logging on all MCP invocation paths.

- **Solr Search Overhaul** — Fully reworked Solr-backed full-text search with a new abstract index-manager framework providing per-entity-type indexing for networks, folders, and shortcuts. Supports visibility tiers (public, unlisted, private), inherited permissions through folder hierarchy, and a utility class for full Solr re-index from Postgres state.

- **v2-to-v3 Data Migration Framework** — New migrator service for converting existing v2 data (network sets, groups, networks, shortcuts) to the v3 schema, with configurable migration credentials, per-type batch commit frequency, and local Solr re-indexing after migration.

- **Trash Lifecycle Management** — Full trash workflow for all file types: move to trash, restore from trash, permanent delete, and clear-all-trash operations. Trash state is tracked via `show_in_trash` flag; Solr index is updated on each trash/restore event.

- **Keycloak-Gated Auth & Revised User/Group Model** — User creation is now gated on authenticated requests. Group permission APIs are deprecated in favor of folder-based sharing. `WWW-Authenticate: Basic` headers are added to all v2 API 401 responses. Multi-admin group logic is improved with priority-user selection.
