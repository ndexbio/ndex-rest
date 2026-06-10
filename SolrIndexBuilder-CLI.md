# SolrIndexBuilder CLI Reference

Command-line tool for rebuilding and repairing NDEx Solr indexes from the
PostgreSQL source of truth. Lives in `org.ndexbio.common.solr.SolrIndexBuilder`
and is run directly from the deployed `ndexbio-rest` jar plus its `WEB-INF/lib`
dependencies.

## Running the tool

The tool needs the app jar, all dependency jars in `WEB-INF/lib`, and the
exploded `WEB-INF/classes` (for `logback.xml` and other resources) on the
classpath. The app jar alone is not enough — it omits SolrJ and friends, which
fails with `NoClassDefFoundError: org/apache/solr/client/solrj/SolrServerException`.

```bash
java -cp "/home/ndex/ndexbio-rest.jar:/home/ndex/ndexbio-rest/WEB-INF/lib/*:/home/ndex/ndexbio-rest/WEB-INF/classes" \
  org.ndexbio.common.solr.SolrIndexBuilder <command>
```

Keep the entire `-cp` value inside quotes so the JVM expands the `/*` glob
rather than the shell. The tool takes exactly one argument; with zero or more
than one it prints the usage line and exits.

### Configuration

On startup the tool calls `Configuration.createInstance()`, which reads
`/opt/ndex/conf/ndex.properties` for DB URL/credentials and Solr URL. No DB or
Solr connection details are passed on the command line. Make sure the config
file is in place and points at the intended server before running.

```bash
# optional: be explicit about the config path
export ndexConfigurationPath=/opt/ndex/conf/ndex.properties
```

### A note on logging output

The bundled `logback.xml` writes to `../logs/ndex.log` relative to the current
working directory. When the tool is launched from a directory where `../logs`
does not resolve to a real, writable path (e.g. from `/home/ndex`, where it
resolves to `/logs`), file logging silently fails and the console stays mostly
quiet during the run. The work still proceeds.

To see progress, either run from a directory where `../logs` points at an
existing writable log directory, or monitor the database directly in a second
terminal (see the `unlist-public-none` section for an example).

## Commands

| Command | Scope | What it does |
|---|---|---|
| `all` | User + group + all networks | Full rebuild: user index, group index, then every eligible network's global index. |
| `user` | User index | Rebuilds only the user search index. |
| `group` | Group index | Rebuilds only the group search index. |
| `all-networks-online` | All networks | Rebuilds network indexes without the `islocked=false` precondition; tolerates per-network failures and keeps going. |
| `global-networks` | Global network index | Rebuilds the global network index only (no per-network query cores). |
| `all-local` | Per-network query cores | Creates the per-network Solr query cores for networks at or above the autocreate node-count threshold. |
| `nfs` | NFS indexes | Placeholder; currently a no-op. |
| `unlist-public-none` | Maintenance | Flips `PUBLIC` + `solr_idx_lvl=NONE` networks to `UNLISTED` and moves them from `public-nfs` to `private-nfs`. |
| `<networkUUID>` | Single network | Rebuilds the index for one network by UUID. |

### `all`

```bash
java -cp "..." org.ndexbio.common.solr.SolrIndexBuilder all
```

Runs three phases in order: rebuild the user index, rebuild the group index,
then rebuild the global index for every network that is complete, not deleted,
validated, not locked, and has no error. Sleeps 2 seconds every 500 networks to
ease load on Solr. This is the heaviest command and the most likely to stress
Solr heap on a large server.

### `user`

```bash
java -cp "..." org.ndexbio.common.solr.SolrIndexBuilder user
```

Creates the user core if needed and reindexes every verified, non-deleted user.

### `group`

```bash
java -cp "..." org.ndexbio.common.solr.SolrIndexBuilder group
```

Creates the group core if needed and reindexes every non-deleted group.

### `all-networks-online`

```bash
java -cp "..." org.ndexbio.common.solr.SolrIndexBuilder all-networks-online
```

Rebuilds network indexes for all complete, non-deleted, validated networks with
no error — note this command does **not** require `islocked=false`, so it can
run against networks that are currently locked. Each network is wrapped in its
own try/catch, so a single failure is logged and the loop continues. Sleeps 2
seconds every 500 networks.

### `global-networks`

```bash
java -cp "..." org.ndexbio.common.solr.SolrIndexBuilder global-networks
```

Rebuilds only the global network index (the cross-network search doc), skipping
the per-network query cores. Same eligibility filter as `all` (complete, not
deleted, validated, not locked, no error). Sleeps 2 seconds every 1000 networks.

### `all-local`

```bash
java -cp "..." org.ndexbio.common.solr.SolrIndexBuilder all-local
```

Creates the per-network Solr query core for each network that is complete, not
deleted, validated, not locked, has no error, has a CX2 file, and meets or
exceeds the autocreate node-count threshold. If a core already exists for a
network, that case is detected and skipped rather than treated as an error.
Logs how many were checked vs. created.

### `nfs`

```bash
java -cp "..." org.ndexbio.common.solr.SolrIndexBuilder nfs
```

Currently a placeholder with no implementation. Running it does nothing.

### `unlist-public-none`

```bash
java -cp "..." org.ndexbio.common.solr.SolrIndexBuilder unlist-public-none
```

Maintenance command for the `public-nfs` cleanup. It finds every network with
`visibility = 'PUBLIC'` and `solr_idx_lvl = 'NONE'` that is not deleted, flips
each to `UNLISTED` in Postgres, and runs the Solr delete + rebuild so the file
index entry moves from `public-nfs` to `private-nfs`.

Behavior details:

- **Per-network locking.** Each network is locked for the duration of its
  conversion and unlocked in a `finally`, with a commit so the unlock persists
  on the shared `autoCommit=false` connection.
- **Commit-then-compensate.** The visibility flip is committed before the Solr
  work runs. If a Solr step fails, a compensating `UPDATE` restores the network
  to `PUBLIC` so Postgres and Solr don't drift apart.
- **Continues on failure.** A failed network is logged and counted; the loop
  moves on. Already-converted networks remain `UNLISTED`.
- **`ignoreCxFiles=true`** on the rebuild, because these `solr_idx_lvl=NONE`
  networks may not have CX aspect files on disk to index from.

**Before running:**

Clear any stale row locks left by a previously crashed run, or the lock step
will throw `NetworkConcurrentModificationException` on those rows:

```sql
SELECT COUNT(*) FROM network WHERE islocked = true;
UPDATE network SET islocked = false WHERE islocked = true;
```

Sanity-check the target count so you know how much work is queued:

```sql
SELECT COUNT(*) FROM network
WHERE visibility='PUBLIC' AND solr_idx_lvl='NONE' AND is_deleted=false;
```

**Monitor progress** (file logging is usually silent — see the logging note
above) by watching the target count tick down in a second terminal:

```bash
watch -n 5 "psql -d ndex -c \"SELECT COUNT(*) FROM network WHERE visibility='PUBLIC' AND solr_idx_lvl='NONE' AND is_deleted=false;\""
```

### Single network by UUID

```bash
java -cp "..." org.ndexbio.common.solr.SolrIndexBuilder 6d19dd59-0e56-11eb-9eee-0ac135e8bacf
```

Any argument that isn't one of the named commands is treated as a network UUID.
The network's index is rebuilt (global plus per-network query core if it meets
the threshold) and the global index is committed at the end. An invalid UUID
will throw an `IllegalArgumentException` from `UUID.fromString`.

## Pre-run checklist

1. **Confirm the target server.** `ndex.properties` decides which DB and Solr
   the run hits — verify it before launching, especially on shared boxes.
2. **Confirm Solr heap.** Large rebuilds (`all`, `global-networks`,
   `all-networks-online`) can OOM Solr if `SOLR_JAVA_MEM` is left at the 512MB
   default. Check `solr.in.sh` has an explicit heap (e.g.
   `SOLR_JAVA_MEM="-Xms2g -Xmx4g"`) and that Solr was restarted after any change.
3. **Clear stale locks** if a prior run crashed (see `unlist-public-none`).
4. **Have a progress monitor ready** (DB query in a second terminal), since
   console output is often empty.

## Troubleshooting

| Symptom | Cause | Fix |
|---|---|---|
| `NoClassDefFoundError: .../SolrServerException` | App jar on classpath without `WEB-INF/lib` | Use the full quoted `-cp` with `WEB-INF/lib/*` and `WEB-INF/classes`. |
| `NetworkConcurrentModificationException` on first/most networks | Stale `islocked=true` rows from a crashed run | `UPDATE network SET islocked=false WHERE islocked=true;` then rerun. |
| No console output, looks hung | `../logs` doesn't resolve from CWD, file logging failed | Run from a dir where `../logs` is writable, or monitor the DB count. |
| Solr goes down mid-run | Solr heap too small (512MB default) | Set `SOLR_JAVA_MEM` in `solr.in.sh`, restart Solr, rerun. |
