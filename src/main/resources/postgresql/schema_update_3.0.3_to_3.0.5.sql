-- Folder permissions are resolved live up the ancestor chain from 3.0.5 onward (issue #165).
--
-- Previously a grant was copied down onto every descendant folder and network at grant time. Those
-- copied rows are indistinguishable from direct per-network grants — same table, same shape, no
-- provenance column — so once the copy-down stops they linger as permanent grants that revoking the
-- folder share can no longer remove.
--
-- This migration archives the table, then removes only the rows an inherited grant already accounts
-- for. It is idempotent and safe to re-run.

-- ── 1. Index supporting the ancestor walk ────────────────────────────────────────────────────────
-- The chain seeds from network.parent and listing queries filter on it, but the table carried only
-- primary-key and owner indexes.

CREATE INDEX IF NOT EXISTS network_parent_idx
    ON core.network (parent)
    WHERE is_deleted = false;

-- ── 2. Archive before deleting ───────────────────────────────────────────────────────────────────
-- Snapshot of user_network_membership as it stood before cleanup, so the delete below is recoverable.
-- CREATE TABLE IF NOT EXISTS makes a re-run a no-op rather than overwriting the original snapshot
-- with post-cleanup contents.

CREATE TABLE IF NOT EXISTS core.user_network_membership_archive AS
    SELECT m.*, now() AS archived_at
      FROM core.user_network_membership m;

ALTER TABLE IF EXISTS core.user_network_membership_archive OWNER TO ndexserver;

-- ── 3. Remove rows an inherited folder grant already explains ────────────────────────────────────
-- Only where the inherited grant is AT LEAST AS PERMISSIVE as the row being removed. A direct WRITE
-- sitting under a folder that grants only READ must survive, or the migration would quietly reduce
-- someone's access. Rows for users with no inherited access at all are likewise untouched — those are
-- genuine direct grants.

DELETE FROM core.user_network_membership m
 WHERE EXISTS (
     WITH RECURSIVE chain AS (
         SELECT f."UUID" AS fid, f.parent
           FROM core.folder f
           JOIN core.network n ON n.parent = f."UUID"
          WHERE n."UUID" = m.network_id
            AND f.is_deleted = false
         UNION
         SELECT pf."UUID", pf.parent
           FROM core.folder pf
           JOIN chain c ON pf."UUID" = c.parent
          WHERE pf.is_deleted = false
     )
     SELECT 1
       FROM chain
       JOIN core.folder_permission fp ON fp.folder_id = chain.fid
      WHERE fp.user_id = m.user_id
        AND ( upper(fp.permission) = 'WRITE'
              OR m.permission_type::text = 'READ' )
      LIMIT 1
 );
