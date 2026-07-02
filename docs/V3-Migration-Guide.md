# NDEx v3 Migration Guide

This guide helps existing NDEx 2.x (v2 API) clients move to NDEx 3.0. It first summarizes
what is new in v3, then focuses on **migration callouts** — existing v2 endpoints and
behaviors that changed as part of the 3.0 release line, including the removal of the legacy
**group** feature.

For the authoritative, dated change log see the
[CHANGELOG](https://github.com/ndexbio/ndex-rest/blob/master/CHANGELOG.md). The folder /
shortcut / visibility model referenced throughout is specified in
[specifications/FOLDER_SHORTCUT_SPECIFICATION.md](specifications/FOLDER_SHORTCUT_SPECIFICATION.md).

---

## 1. What's new in v3 (3.0.0)

NDEx 3.0 is a major architectural upgrade from the 2.x line. The v2 API remains available for
backwards compatibility; v3 is the primary interface going forward.

- **New `/v3` REST API** — a complete rewrite of the major endpoints under `/v3`
  (`NetworkServiceV3`, `UserServicesV3`, `SearchServiceV3`, `AdminServiceV3`, `BatchService`,
  `CyWebWorkspaceServices`).
- **Folders & Shortcuts (NDEx file system)** — a hierarchical file system for organizing
  networks and other content. `NdexFolder` and `NdexShortcut` entities with full CRUD,
  visibility controls, permission sharing, access-key sharing, transfer-ownership, and copy
  operations via `FolderServiceV3`, `ShortcutServiceV3`, and `FileServiceV3`.
- **Visibility tiers (PUBLIC / UNLISTED / PRIVATE)** — a unified visibility model that applies
  to both networks and folders (details in §2).
- **Trash lifecycle** — move-to-trash, restore, permanent-delete, and clear-all-trash for all
  file types.
- **Embedded MCP server at `/mcp`** — a Model Context Protocol server exposing tools for
  LLM-driven network and folder management.
- **Keycloak-gated authentication & a revised user/group model** — user creation is gated on
  authenticated requests; group permission APIs are deprecated in favor of folder-based
  sharing (and removed entirely in 3.0.2 — see §2).
- **Containerized deployment** — production Docker image with multi-architecture
  (`amd64` + `arm64`) publishing.

> **Note:** This section introduces *new* v3 capabilities. The sections below are migration
> callouts — they cover how *existing v2 endpoints/behavior* changed, and do not repeat the
> new features above.

---

## 2. Migrating from Groups to Folders

### Status

- **3.0.0** — the NDEx **group** feature was **deprecated** in favor of folder-based sharing.
- **3.0.2** — the group feature is **removed**. Every group endpoint now returns
  **HTTP 501 Not Implemented**, and group-based network permissions no longer exist. A user
  reaches a network only by **ownership** or a **direct user permission**.

If your application still calls group endpoints, you must migrate to folders + visibility +
folder permission sharing.

### Why folders replace groups

A group did exactly two things: it collected **users** (members) and granted those users
shared access to a set of **networks**. Both are now expressed more generally by the v3 file
system:

- A **Folder** is an ownable container for networks/folders that can be **shared** with
  specific users (permission sharing) and given a **visibility**.
- A **Shortcut** points at a network (or folder) and inherits the target's permissions, so a
  folder full of shortcuts shares a curated set of networks with whoever the folder is shared
  with — exactly what a group's network list did.
- **Folder permissions propagate downward** to nested folders/networks, so granting access
  once on a folder shares everything inside it.

### Visibility model (replaces group-mediated access)

Visibility applies to both **networks** and **folders**:

| Visibility | Who can view | Searchable |
|---|---|---|
| **PUBLIC** | Any user | Yes |
| **UNLISTED** | Anyone with the link/UUID | Only by the owner (others need the exact UUID) |
| **PRIVATE** | Only the owner, plus users explicitly granted `read`/`write`, or anyone holding a network **access key** (read) | No |

Together, **visibility + per-user folder/network permissions + access keys** cover every
sharing scenario that groups previously handled.

### How former group capabilities map to v3

| Old (groups) | New (v3 folders) |
|---|---|
| Create a group | Create a **Folder** (`FolderServiceV3`) |
| Add users to a group (group membership) | Share the folder with those users (**folder permission**) |
| Grant a group access to networks | Put **Shortcuts** to those networks in the shared folder (permissions propagate) |
| A member gains access to the group's networks | The user the folder is shared with sees the folder's networks/shortcuts |
| Group-scoped READ/WRITE on a network | A user's **direct** READ/WRITE permission on the network/folder, or folder-inherited permission |
| Public exposure of a group's networks | Set network/folder **visibility** to PUBLIC (or UNLISTED for link-only) |
| Group join requests (`JoinGroup`) | Not applicable — share the folder directly with the user |

### Replacement API surface

- Folders: `FolderServiceV3` (`/v3/...` files endpoints)
- Shortcuts: `ShortcutServiceV3`
- Generic file operations (visibility, permissions, access keys, transfer, copy, trash):
  `FileServiceV3`

### Migrating existing group data

The 3.0.0 release performed a v2→v3 data migrator that converted existing group data:

- Each **group** becomes a **Folder** owned by the former group owner; the networks the group
  could access become **Shortcuts** in that folder; the group's permissions are flattened onto
  the individual networks for each member.
- **Network Sets** become Folders (with a shortcut per network); **Networks** stay in the
  owner's Home folder with permissions and access keys mapped across.

### Endpoints removed in 3.0.2 (now HTTP 501)

- Entire `/v2/group` resource and the v1 `/group` resource.
- `GET /v2/user/{userid}/membership` and `…/membershiprequest[/{requestid}]`
  (the join-group flow), and v1 `GET /user/{userid}/group/...` and
  `GET /user/membership/group/{groupid}`.
- `POST /v2/batch/group` and `POST /v2/search/group`.
- On `GET /v2/network/{networkid}/permission`, the `type=group` branch; on
  `DELETE` / `PUT /v2/network/{networkid}/permission`, the `groupid` parameter. The
  `type=user` / `userid` paths are unchanged.
- The `directOnly` query parameter on `GET /v2/user/{userid}/permission` was removed; the v1
  `GET /user/membership/network/{networkid}/{directonly}` path lost its `{directonly}` segment.

---

## 3. Other v2 endpoint changes in the 3.0 release

These are changes to **pre-existing v2 endpoints** (beyond the group removal) that v2 clients
should be aware of when upgrading. New `/v3` endpoints are out of scope here (see §1).

### Authentication & 401 responses

- **`WWW-Authenticate: Basic` is now scoped to v2 paths.** A 401 from a `/v2` (or v1) endpoint
  still includes the `WWW-Authenticate: Basic` header (so challenge-response clients such as
  Cytoscape keep working); v3 endpoints intentionally do **not** send it (they use OIDC bearer
  tokens). The legacy `setAuthHeader=false` query-parameter suppression switch is gone.
- **`POST /v2/user` (create user) is no longer an "open" function.** On servers configured with
  `AUTHENTICATED_USER_ONLY=true`, anonymous user creation via `POST /v2/user` is now blocked
  (the endpoint lost its open-function exemption). Default servers
  (`AUTHENTICATED_USER_ONLY=false`) are unaffected.

### Request content-type requirements (new `@Consumes("application/json")`)

These POST endpoints now require an explicit `Content-Type: application/json` on the request
body (previously unspecified):

- `POST /v2/search/network` and `POST /v2/search/network/genes`
- `POST /v2/batch/network/summary` and `POST /v2/batch/network/permission`

### Response / model changes

- **`POST /v2/user`** now returns the created `User` object as the response entity (in addition
  to the existing `Location` header).
- **`GET /v2/user/{userid}/showcase`** is **deprecated** (use `/v3/users/{userid}/home`) and its
  response model changed from `List<NetworkSummary>` to **`List<FileItemSummary>`** — it now
  returns networks, folders, and shortcuts. This is a breaking response-shape change for v2
  clients of this endpoint.
- **`GET /v2/user/{userid}/networksummary`** (account-page networks) is deprecated and may now
  return additional entries (networks referenced by the user's root shortcuts are folded in).

### Deprecations (still functional, marked deprecated in Swagger)

- **Network Sets → Folders:** the entire `/v2/networkset` resource is deprecated. Endpoints
  still work and now transparently resolve ids against both the legacy network-set store and
  the new folder store (`createNetworkSet` creates a folder under the hood). Migrate to the v3
  folder endpoints.
- `GET /v2/user/{userid}/permission` is deprecated.
- `GET /v2/request/{requestid}` and `PUT /v2/request/{requestid}/properties` are deprecated.
- The `showcase` system property on `PUT /v2/network/{networkid}/systemproperty` is deprecated.

> **Note:** Deprecated v2 endpoints remain available for backwards compatibility but will be
> removed in a future release; prefer the v3 equivalents.
