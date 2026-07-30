# NDEx v3 Migration Guide

This guide helps existing NDEx 2.x (v2 API) clients move to NDEx 3. It first summarizes what is
new in v3, then covers the **migration callouts** — existing v2 endpoints and behaviors that
changed. The two features v2 clients most often depend on, **groups** and **network sets**, are both
replaced by the v3 **folder** model — groups are gone outright (§2), while the `/v2/networkset`
endpoints keep working as a folder-backed compatibility layer (§3).

Two things worth knowing up front, because they shrink the job considerably:

- **Your existing UUIDs are still valid.** A group or network set you created is now a *folder*
  with the **same UUID**, so ids stored in your application keep resolving — against
  `/v3/files/folders/{id}` instead of the old paths (§2, §3).
- **Basic authentication still works everywhere, including on `/v3`.** You do not need to adopt
  OIDC to start calling v3 (§4).

For dated, per-release detail see the
[CHANGELOG](https://github.com/ndexbio/ndex-rest/blob/master/CHANGELOG.md). Live request/response
shapes — including everything marked deprecated below — are browsable in the Swagger UI at
`/swagger/index.html` on the instance you call. The folder / shortcut / visibility model
referenced throughout is specified in
[specifications/FOLDER_SHORTCUT_SPECIFICATION.md](specifications/FOLDER_SHORTCUT_SPECIFICATION.md).

---

## Migration at a glance

1. **Leave your authentication alone.** Basic auth is accepted on `/v3` exactly as on `/v2`; adopt
   OIDC tokens when you want to, not to unblock this migration (§4).
2. **Repoint your stored ids.** Group and network-set UUIDs are now folder UUIDs:
   `GET /v3/files/folders/{id}` for the container, `…/list` for its contents (§2, §3).
3. **Replace the calls that now return 501** using the mapping tables in §2 and §3. A retired
   endpoint answers **501** with a JSON NDEx error body whose `message` explains the removal, so
   you can detect it precisely and surface the server's own wording.
4. **Know which grants cross a shortcut.** Folder **access keys** reach shortcut targets, so your
   link-shared collections keep working as-is. Folder **read/write permissions** do *not*. Either
   way, the recommended end state is the same: move networks into the folder that governs them
   with `POST /v3/batch/networks/move`, and keep shortcuts for views you are not sharing (§3).
5. **Fix your revoke path.** Former group grants were flattened onto individual networks, so
   unsharing a folder no longer removes them (§2).
6. **Scope your regression testing to this guide.** v2 endpoints not mentioned here — network
   CRUD, CX upload/download, search, batch — behave as they did before.

## Key concept: real children vs. shortcuts

Two distinct relationships run through the rest of this guide:

- A **real child** lives *in* the folder — the folder is its parent.
- A **shortcut** is a pointer to a network (or folder) that lives elsewhere.

The two grant mechanisms treat that difference differently, and this is the single most important
thing to internalize:

| Mechanism | Reaches real children | Reaches shortcut targets |
|---|---|---|
| **Read/write permissions** (`/v3/files/sharing/members`) | Yes — down the whole folder tree | **No.** A shortcut inherits its target's permissions; sharing the folder lets someone *see* the shortcut, not read the target |
| **Access keys** (anonymous READ) | Yes — down the whole folder tree | **Yes**, for networks, when the shortcut and network have the same owner |

So a folder full of shortcuts still *shares by link* exactly as its network set did, but it does
not confer per-user read/write on the networks it points at. Both mechanisms are simpler to manage
when the items live in the folder that governs them — see the best-practice note in §3.

Network sets and groups could only ever *reference* networks, so their migrated folders are full
of shortcuts. When you need the folder to genuinely govern per-user access, move the networks in
as real children:

- `POST /v3/batch/networks/move` — bulk reparent (target folder + list of network UUIDs, owner
  only). No content transfer; this is the normal way to fix up a migrated folder.
- `?folderId=` on v3 network create/update — places a network in a folder as you write it.

---

## 1. What's new in v3

NDEx 3 is a major architectural upgrade from the 2.x line. The v2 API remains available for
backwards compatibility; v3 is the primary interface going forward.

- **New `/v3` REST API** — a complete rewrite of the major endpoints under `/v3`
  (networks, users, search, batch, admin status, and CyWeb workspaces).
- **Folders & Shortcuts (NDEx file system)** — a hierarchical file system for organizing
  networks and other content, under `/v3/files`. Folders and shortcuts support full CRUD,
  visibility controls, permission sharing, access-key sharing, transfer-ownership, and copy.
- **Visibility tiers (PUBLIC / UNLISTED / PRIVATE)** — a unified visibility model that applies
  to networks, folders, and shortcuts (details in §2).
- **Trash lifecycle** — move-to-trash, restore, permanent-delete, and clear-all-trash for all
  file types.
- **Embedded MCP server at `/mcp`** — a Model Context Protocol server exposing tools for
  LLM-driven network and folder management.
- **Token authentication** — v3 accepts Keycloak OIDC bearer tokens in addition to the basic
  auth v2 clients already use (§4).

> **Note:** This section introduces *new* v3 capabilities. The sections below are migration
> callouts — they cover how *existing v2 endpoints/behavior* changed, and do not repeat the
> new features above.

---

## 2. Migrating from Groups to Folders

The NDEx **group** feature is **removed**. Every group endpoint returns
**HTTP 501 Not Implemented**, and group-based network permissions no longer exist — a user
reaches a network only by **ownership** or a **direct user permission**. If your application
still calls group endpoints, migrate to folders + visibility + folder permission sharing.

### Why folders replace groups

A group did exactly two things: it collected **users** (members) and granted those users
shared access to a set of **networks**. Both are now expressed more generally by the v3 file
system:

- A **Folder** is an ownable container for networks/folders that can be **shared** with
  specific users (permission sharing) and given a **visibility**.
- **Folder permissions propagate downward to real children** — the folder's subfolders and the
  networks parented to them — so granting access once on a folder shares everything actually
  inside it.
- **Shortcuts do not carry folder permissions to their targets.** A shortcut inherits its
  target's permissions, so a folder full of shortcuts is a curated *view*, not a per-user grant.
  To reproduce a group's shared network list, either move those networks into the folder
  (`POST /v3/batch/networks/move`) or grant each user a direct permission per network. (Access
  keys behave differently — they *do* reach shortcut targets; see §3.)

### Visibility model (replaces group-mediated access)

Visibility applies to **networks**, **folders**, and **shortcuts**:

| Visibility | Who can view | Searchable |
|---|---|---|
| **PUBLIC** | Any user | Yes |
| **UNLISTED** | Anyone with the link/UUID | Only by the owner (others need the exact UUID) |
| **PRIVATE** | Only the owner, plus users explicitly granted `read`/`write`, or anyone holding an **access key** (read) | No |

Together, **visibility + per-user folder/network permissions + access keys** cover every
sharing scenario that groups previously handled.

### How former group capabilities map to v3

| Old (groups) | New (v3 folders) |
|---|---|
| Create a group | `POST /v3/files/folders` |
| Add users to a group (group membership) | Share the folder with those users (`POST /v3/files/sharing/members`) |
| Grant a group access to networks | Move those networks into the shared folder (`POST /v3/batch/networks/move`) so the folder's permissions cascade to them |
| A member gains access to the group's networks | The user the folder is shared with gains the same permission on the folder's real children |
| Group-scoped READ/WRITE on a network | A user's **direct** READ/WRITE permission on the network/folder, or folder-inherited permission |
| Public exposure of a group's networks | Set network/folder **visibility** to PUBLIC (or UNLISTED for link-only) |
| Group join requests (`JoinGroup`) | Not applicable — share the folder directly with the user |

### Replacement API surface

- Folders: `/v3/files/folders`
- Shortcuts: `/v3/files/shortcuts`
- Generic file operations (visibility, permissions, access keys, transfer, copy, trash):
  `/v3/files/...` (sharing lives under `/v3/files/sharing`)

### Your existing group data

Each group became a **Folder** with the **same UUID**, owned by the former group owner, holding a
**Shortcut** to each network the group could access. A group id your application stored is
therefore a valid folder id: `GET /v3/files/folders/{oldGroupUUID}`.

Because those folders hold shortcuts rather than real children, the group's member permissions
were **flattened onto the individual networks** for each member — that is what preserves their
access. Two consequences for your code:

- **Revoking is no longer one call.** Unsharing the folder does not remove those per-network
  grants, so a revoke means removing the user's permission on each affected network (or on the
  folder plus each network that carries a direct grant).
- **To make the folder itself govern access again**, move its networks in as real children with
  `POST /v3/batch/networks/move`; from then on folder sharing cascades normally.

### Group endpoints (now HTTP 501)

- Entire `/v2/group` resource and the v1 `/group` resource.
- `GET /v2/user/{userid}/membership` and `…/membershiprequest[/{requestid}]`
  (the join-group flow), and v1 `GET /user/{userid}/group/...` and
  `GET /user/membership/group/{groupid}`.
- `POST /v2/batch/group` and `POST /v2/search/group`.
- On `GET /v2/network/{networkid}/permission`, the `type=group` branch; on
  `DELETE` / `PUT /v2/network/{networkid}/permission`, the `groupid` parameter. The
  `type=user` / `userid` paths are unchanged.

Related request/response changes: the `directOnly` query parameter on
`GET /v2/user/{userid}/permission` was removed, the v1
`GET /user/membership/network/{networkid}/{directonly}` path lost its `{directonly}` segment,
`GET /v2/network/{networkid}/permission?type=user` never returns group entries, and
`groupCount` in admin status is always `0`.

---

## 3. Migrating from Network Sets to Folders

The NDEx **network set** feature is removed **as storage**, but preserved **as an API**. Every
`/v2/networkset` endpoint still works: a network set id **is** a folder id, so each endpoint performs
folder and shortcut operations internally and maps the result back onto the legacy `NetworkSet` shape.
Your existing v2 code keeps working, and — unlike a frozen archive — it now reads and writes the same
live data your v3 clients see. The legacy `network_set` tables are never touched.

| Endpoint | Status | What it actually does |
|---|---|---|
| `POST /v2/networkset` | **Live** | Creates a **folder** at your home root. The returned id resolves at `GET /v3/files/folders/{id}` |
| `PUT /v2/networkset/{id}` | **Live** | Renames/redescribes the folder, preserving its parent. Cannot *clear* a description |
| `DELETE /v2/networkset/{id}` | **Live** | Trashes the folder **and everything in it**; recoverable via `POST /v3/files/trash/restore` |
| `GET /v2/networkset/{id}` | **Live** | Reads the folder; `networks` is the union of its network-shortcut targets and any networks parented in it |
| `POST /v2/networkset/{id}/members` | **Live** | Adds each network as a **shortcut**. Every id must be readable by you, or the whole request is rejected |
| `DELETE /v2/networkset/{id}/members` | **Live** | Permanently deletes the member **shortcut**; a network parented *in* the folder is **moved to your home root** instead |
| `GET /v2/networkset/{id}/accesskey` | **Live** | Returns the **folder's** key. Read-only — it will not create or enable one |
| `PUT /v2/networkset/{id}/accesskey` | **Live** | Enables/disables the **folder's** key, the same one `POST /v3/files/sharing/share` manages |
| `PUT /v2/networkset/{id}/systemproperty` | **Live** | Accepts `showcase` and **does nothing** with it — folders have no showcase flag |
| `GET /v2/user/{userid}/networksets` | **Live** | Lists your **folders**. `showcase=true` is a **no-op filter** |
| `GET /v2/user/{userid}/networkcount` | **Live** | `networkSetCount` counts your folders, so it always matches the list above |

### What differs, and why

These endpoints are a faithful shim, not a perfect one. Three legacy fields have no folder
equivalent and are therefore never stored or returned — `showcased` (always `false`), `doi`
(absent), and `properties` (empty). If you relied on any of them, that state did not survive the
migration and there is nowhere to put it back.

Three behaviors are narrower than they were:

- **Folder visibility governs reading a set.** This is the one change most likely to affect you. A set
  *is* a folder, and folders are **PRIVATE** by default — including every set the migration converted —
  so `GET /v2/networkset/{id}` now returns **401** to an anonymous or non-permitted caller. The legacy
  endpoint returned the set header to everyone, because `network_set` had no visibility column at all.
  The fix is one call per set: `PUT /v3/files/folders/{folderid}` with `"visibility":"PUBLIC"`, or hand
  out the set's access key. We enforce this because a set and a folder are the same row: serving it
  under weaker rules on `/v2` than on `/v3` would let anyone who knows a folder id read its name and
  description without authenticating. Member filtering is unchanged — a set you *can* read still shows
  only the member networks *you* can read.
- **An access key reaches only member networks owned by the set's owner.** Legacy validation had no
  such condition, so a set containing *another user's* network used to unlock it by key and no longer
  will. That guard is deliberate — see [Access keys](#access-keys) below — because without it anyone
  could grant anonymous read to someone else's private network just by shortcutting it into a keyed
  folder.
- **`POST /{id}/members` now enforces read access** on every posted network, rejecting the whole
  request if one fails. The legacy documentation always claimed this rule; nothing enforced it.

And one is broader than you may expect: `DELETE /v2/networkset/{id}` deletes the folder's *contents*,
not just the set header. Networks you moved into the folder go to the trash with it.

### Why still migrate to the v3 endpoints

Nothing forces you off `/v2/networkset` — but it can only express what a network set could. Folders
nest, carry per-user read/write permissions, hold real networks rather than only references, and have
a visibility independent of sharing. None of that is reachable through the legacy shape, so new work
belongs on `/v3/files/folders`. Use the mapping table below.

### Your existing network-set data

Each network set became a **Folder** with the **same UUID**, owned by the set owner, holding a
**Shortcut** to each member network, and carrying the set's access key. A network set id your
application stored is therefore a valid folder id:
`GET /v3/files/folders/{oldNetworkSetUUID}` returns the folder, and
`GET /v3/files/folders/{oldNetworkSetUUID}/list` returns its contents. Switching to the folder
endpoints is a path change, not a data-reconciliation project.

### How former network-set capabilities map to v3

| Old (network sets) | New (v3 folders) |
|---|---|
| Create a network set | `POST /v3/files/folders` |
| Rename / delete a set | `PUT` / `DELETE /v3/files/folders/{folderid}` |
| Add networks to a set | Move them in: `POST /v3/batch/networks/move` (real children), or create **Shortcuts** (`/v3/files/shortcuts`) — an access key still reaches shortcut targets, per-user permissions do not |
| Remove networks from a set | Move them elsewhere, or delete the shortcut |
| List a set's contents | `GET /v3/files/folders/{folderid}/list` (and `…/count`) |
| List a user's network sets | `GET /v3/users/{userid}/home` |
| Share a whole set by link (set access key) | Enable a **folder** access key: `POST /v3/files/sharing/share`, read it with `GET /v3/files/folders/{folderid}/accesskey`, revoke with `POST /v3/files/sharing/unshare` |
| `showcase` system property on a set | **No direct equivalent.** The user's **Home folder**, returned by `GET /v3/users/{userid}/home`, is the account-page surface; **visibility** separately controls who can read. Do not use PUBLIC as a stand-in for "showcased" — it changes who can read the data |

### Access keys

An access key is a binary anonymous-**READ** grant that propagates **downward** through the
folder tree — to subfolders, to the networks parented in that tree, and to the targets of the
network shortcuts it contains. A key is valid for a network when it matches:

- the network's **own** enabled key;
- an enabled key on **any ancestor folder** — the full chain, not just the immediate parent; or
- an enabled key on the ancestry of a folder holding a **shortcut** to that network, where the
  shortcut and the network have the same owner.

That third rule is deliberate backwards compatibility: migrated network sets became folders full
of shortcuts, so **the keys you handed out for your network sets keep working unchanged** — the
same key string is live on the folder, and it still reaches the member networks through their
shortcuts. The only limit is the same-owner condition, which stops a keyed folder from granting
anonymous read to a network someone else owns; a set that referenced another user's network is
where you may see a difference.

Note this is *broader* than read/write permission propagation, which stops at real children (§2).
Enable the key on the **folder** rather than per network — one key then covers everything below
it, including future additions. When a request supplies an access key,
`GET /v3/files/folders/{folderid}/list` and `…/count` return only the key-accessible children.

### Best practice: keep keyed folders filled with real networks, not shortcuts

Keying a folder of shortcuts is supported — it is exactly what keeps migrated network sets
working — but understand the complexity you are taking on. Because a key reaches shortcut targets,
a network's anonymous-read exposure becomes a function of **every place it is referenced**, not
just where it lives: any same-owner shortcut pointing at it, sitting in any keyed folder anywhere
in your account, grants read. Answering "who can read this network?" — or shutting that access
off — then means finding and auditing every shortcut to it, and that surface grows every time
someone adds one.

When the network is a **real child** of the keyed folder, its exposure is defined by a single
ancestor chain: the folder it lives in. That is far easier to reason about, audit, and revoke. So
for anything you intend to key-share:

- put the networks **in** the folder you key — `POST /v3/batch/networks/move` for existing
  networks, or `?folderId=` on v3 network create/update for new ones;
- **reuse that folder and its key** to share those networks again, instead of creating another
  keyed folder of shortcuts to them. A network lives in exactly one folder, so that folder is its
  natural share point; handing out the same key to a second audience costs nothing and keeps one
  thing to rotate or revoke. Minting a new keyed folder per audience recreates the sprawl this
  practice avoids — the same network ends up exposed by several keys, each with its own lifetime;
- when audiences need **different subsets**, express that with the folder tree — nest subfolders
  and key at the level each audience should see — rather than parallel keyed folders pointing at
  the same content;
- reserve shortcuts for organizing *views* of content you are not sharing by key.

This is a recommendation, not a restriction — if you keep shortcuts in keyed folders, or several
keys over the same networks, just track where they are.

---

## 4. Other v2 endpoint changes

These are changes to **pre-existing v2 endpoints** (beyond the group and network-set removals)
that v2 clients should be aware of when upgrading. **v2 endpoints not listed in this guide are
unchanged** — network CRUD, CX upload/download, search, and batch behave as they did in 2.x. New
`/v3` endpoints are out of scope here (see §1).

### Authentication

- **Basic auth still works, on every path.** The `Authorization` header is accepted as either
  `Basic <credentials>` or `Bearer <token>` on v1, v2, *and* v3 endpoints alike. An existing
  basic-auth client can call `/v3` immediately, with no auth changes.
- **Keycloak OIDC bearer tokens are also supported** and are the preferred scheme for new
  integrations, on both v2 and v3. `/admin/status` (v1 & v2) exposes the OAuth register URL,
  reset URL, and client id so your client can point users at the real sign-up / password-reset
  flows.
- **What actually changed is the 401 *challenge*, not what is accepted.** A 401 from a `/v2`
  (or v1) endpoint still carries the `WWW-Authenticate: Basic` header, so challenge-response
  clients such as Cytoscape keep working; v3 endpoints deliberately omit that header. If your
  client relies on being *challenged* rather than sending credentials pre-emptively, that is the
  one thing to adjust. The legacy `setAuthHeader=false` suppression parameter is gone.
- **`POST /v2/user` (create user) is no longer an "open" function.** It lost its
  anonymous-access exemption, so where the server requires authenticated requests, anonymous
  user creation returns **401** — direct new users to the hosted sign-up flow instead.

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

### DOI requests (`POST /v2/admin/request` with `type=DOI`)

This is the **only** DOI mechanism, and the caller **no longer allocates an access key**:

- A network that stays **PRIVATE** gets a network-scoped access key embedded in the minted DOI
  viewer URL — an existing key on the network is reused (re-enabled if it was off), otherwise one
  is generated. A **certified** request makes the network **PUBLIC** with no access key.
- Edge case: a PRIVATE network with no enabled access key at mint time is rejected with
  **400 Bad Request**; recover with a `type=Cancel_DOI` request, then retry.

### Deprecations (still functional, marked deprecated in Swagger)

- `GET /v2/user/{userid}/permission` is deprecated.
- `GET /v2/request/{requestid}` and `PUT /v2/request/{requestid}/properties` are deprecated.
- The `showcase` system property on `PUT /v2/network/{networkid}/systemproperty` is deprecated.

> **Note:** Deprecated v2 endpoints remain available for backwards compatibility but will be
> removed in a future release; prefer the v3 equivalents. Treat a **501** as permanent — that
> feature is gone, not paused, and no future release will restore it.
