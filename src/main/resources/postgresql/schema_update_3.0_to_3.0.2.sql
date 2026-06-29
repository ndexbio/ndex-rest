-- NDEx schema migration: 3.0 → 3.0.2
-- Removes the legacy group feature: group_network_membership, ndex_group_user,
-- ndex_group, and their archive tables. Groups are fully retired.

-- ── drop dependents first (CASCADE also clears FKs/indexes) ──────────────────

DROP TABLE IF EXISTS core.group_network_membership_arc;
DROP TABLE IF EXISTS core.group_network_membership CASCADE;
DROP TABLE IF EXISTS core.ndex_group_user_arc;
DROP TABLE IF EXISTS core.ndex_group_user CASCADE;
DROP TABLE IF EXISTS core.ndex_group CASCADE;

-- ── purge now-orphaned group request rows ───────────────────────────────────
-- request_type stays an enum value, but no group rows should remain.

DELETE FROM core.request WHERE request_type IN ('JoinGroup','GroupNetworkAccess');
