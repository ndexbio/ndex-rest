# DbMigrationTool CLI Reference

Command-line database maintenance tool for one-off NDEx v3 data migrations. Lives in
`org.ndexbio.common.util.DbMigrationTool` and is compiled into the deployed `ndexbio-rest.war`.
It hosts two orthogonal entrypoints, both introduced alongside the access-key-via-folders change
(issue #133).

## Running the tool

The class is bundled inside the `.war`. Given only the `.war`, explode it to a temp directory and
run against its exploded `WEB-INF/classes` (the app's own compiled classes + resources such as
`logback.xml`) plus `WEB-INF/lib` (all dependency jars):

```bash
# Explode the WAR to a temp location
mkdir -p /tmp/ndex-cli && cd /tmp/ndex-cli
jar xf /path/to/ndexbio-rest.war        # or: unzip -q /path/to/ndexbio-rest.war

# Run a subcommand. Quote the -cp value so the JVM (not the shell) expands the /* glob.
java -cp "WEB-INF/classes:WEB-INF/lib/*" \
  org.ndexbio.common.util.DbMigrationTool <command> [--apply]
```

`WEB-INF/classes` must be on the classpath (not just `WEB-INF/lib`) — it holds both `DbMigrationTool`
itself and resources like `logback.xml`.

### Configuration

On startup the tool calls `Configuration.createInstance()`, which reads `ndex.properties` for the DB
URL/credentials and Solr URL. No DB details are passed on the command line — make sure the config
file is in place and points at the intended server before running.

```bash
# be explicit about the config path
export ndexConfigurationPath=/opt/ndex/conf/ndex.properties
```

`--apply` runs additionally require a reachable Solr and the NFS data root (`ndexRoot/data`), because
after the DB writes it runs a full v3 reindex (see below).

### Dry-run vs apply

A single `--apply` flag controls writes:

- **No flag (default): dry-run.** The tool reports exactly what it *would* change and makes **no**
  writes and runs **no** reindex.
- **`--apply`: perform the changes.** The tool writes to the DB (per-item commits), prints the
  summary, then runs a full v3 reindex.

Both entrypoints are **idempotent** — safe to re-run. A converted+deleted shortcut is no longer a
child on a second run; a folder already `PRIVATE` is skipped.

### Reindex after `--apply`

After a successful `--apply` run, the tool logs completion and then runs a full reindex routine which reindexes
**networks, folders, and shortcuts** and resets the NFS Solr cores. Dry-run doe nnot reindex.

## Commands

| Command | What it does |
|---|---|
| `transform-accesskey-shortcuts` | For every folder that has an access key (directly or inherited from an ancestor), reparents eligible shortcut targets into the folder as real children and deletes the shortcut, so the folder's access key reaches them again. |
| `privatize-folders` | Sets any `PUBLIC` folder whose direct children are all private to `visibility=PRIVATE`. |

### `transform-accesskey-shortcuts`

```bash
java -cp "WEB-INF/classes:WEB-INF/lib/*" \
  org.ndexbio.common.util.DbMigrationTool transform-accesskey-shortcuts           
```

Background: access-key validation now follows the folder hierarchy and **ignores shortcuts**
(issue #133). The original v3 migration converted each network-set into a folder full of shortcuts,
so those networks would no longer be reachable by a folder's access key. This command realigns that
state.

The tool groups the shortcuts that live in keyed folders **by their target**. For each target it
either **converts** it (reparents the real target into the keyed folder as a real child and deletes
that folder's shortcuts to it) or **skips** it with a reason:

- **spans multiple keyed folders** — the target's keyed-folder shortcuts sit in more than one distinct
  keyed folder; a target can have only one parent, so it can't be placed under all of them at once.
  (Shortcuts to the same target that live in *non-keyed* folders don't count here and are left intact —
  they still resolve to the target after it moves.)
- **dangling** — the target is missing or deleted.
- **cross-owner** — the target is owned by a different user than the folder owner; the tool never
  relocates another user's item into someone else's folder.
- **would-create-cycle** — (folder targets only) reparenting would make the folder an ancestor of
  itself.

On a conversion the tool deletes **all** of that keyed folder's shortcuts to the target (there may be
more than one). The report prints the full list of skipped targets with reasons first, then ends with a
count summary — targets converted and shortcuts deleted (or the "would" counts in dry-run) plus the
skipped total — so the tally is always the last thing printed, in both dry-run and `--apply`.

### `privatize-folders`

```bash
java -cp "WEB-INF/classes:WEB-INF/lib/*" \
  org.ndexbio.common.util.DbMigrationTool privatize-folders           
```

For each `PUBLIC` folder with at least one direct child, if **all** direct children are private, the
folder is set to `PRIVATE`. Children considered: direct network and subfolder children, plus the
visibility of each shortcut child's target (a dangling target counts as private). Empty folders are
left unchanged. The report lists the folders patched (or "would patch") first, then ends with a count
summary — folders patched, folders left PUBLIC because they expose a non-private child, and empty
PUBLIC folders skipped — so the tally is the last thing printed, in both dry-run and `--apply`.

## Pre-run checklist

1. **Confirm the target server.** `ndex.properties` decides which DB and Solr the run hits — verify
   it before launching, especially on shared boxes.
2. **Dry-run first.** Run without `--apply` and review the report before applying.
3. **For `--apply`, confirm Solr + NFS reachability** — the post-migration reindex needs both.
4. **Back up / snapshot** the DB before an `--apply` run of `transform-accesskey-shortcuts`, since it
   deletes shortcuts and moves items between folders.

## Troubleshooting

| Symptom | Cause | Fix |
|---|---|---|
| `NoClassDefFoundError` | App classes/deps not on classpath | Use the full quoted `-cp` with both `WEB-INF/classes` and `WEB-INF/lib/*`. |
| `... ndexConfigurationPath is not defined` | Config path not set | `export ndexConfigurationPath=/opt/ndex/conf/ndex.properties`. |
| Reindex step fails after apply | Solr or NFS data root not reachable | Ensure Solr is up and `ndexRoot/data` is accessible; DB changes are already committed, so re-run the reindex via `/v3/admin/reindex-v3` or re-run the command (idempotent). |
| Fewer conversions than expected | Targets skipped as multi-referrer / cross-owner / dangling / cycle | Expected — see the skipped list in the report; these are intentionally not transformed. |
