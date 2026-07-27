-- NDEx schema migration: 3.0 → 3.0.3
-- Retires the network set feature at the data layer (issue #133 follow-up).
--
-- The network_set / network_set_member tables are intentionally KEPT as frozen, read-only legacy
-- data (mirroring the group-feature retirement, which preserved its tables). We only drop the
-- foreign key that couples network_set_member to the live network table, so that deleting a network
-- no longer requires touching (or is blocked by) the frozen network_set_member rows. All network-set
-- application code has been removed; nothing writes to these tables anymore.

ALTER TABLE IF EXISTS core.network_set_member
    DROP CONSTRAINT IF EXISTS network_set_member_network_id_fkey;
