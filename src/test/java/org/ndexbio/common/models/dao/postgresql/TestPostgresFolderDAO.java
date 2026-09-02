package org.ndexbio.common.models.dao.postgresql;

import static org.easymock.EasyMock.*;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import org.easymock.Capture;
import org.junit.Test;
import org.ndexbio.common.models.dao.AccessKeyResolver;
import org.ndexbio.common.models.dao.DeletedFileIds;
import org.ndexbio.model.object.FileCount;
import org.ndexbio.common.models.dao.FilePermissionResolver;
import org.ndexbio.model.exceptions.ObjectNotFoundException;
import org.ndexbio.model.object.FileType;
import org.ndexbio.model.object.Permissions;

public class TestPostgresFolderDAO {

    private static final UUID FOLDER = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID SUBFOLDER = UUID.fromString("22222222-2222-2222-2222-222222222222");
    private static final UUID NETWORK = UUID.fromString("33333333-3333-3333-3333-333333333333");
    private static final UUID SHORTCUT = UUID.fromString("44444444-4444-4444-4444-444444444444");
    private static final UUID USER = UUID.fromString("55555555-5555-5555-5555-555555555555");

    @Test
    public void testAccessKeyIsValidDelegatesToResolver() throws SQLException {
        Connection mockConn = createMock(Connection.class);
        AccessKeyResolver resolver = createMock(AccessKeyResolver.class);
        UUID folder = UUID.randomUUID();
        expect(resolver.isFolderKeyValid(folder, "k")).andReturn(true);
        expect(resolver.isFolderKeyValid(folder, "bad")).andReturn(false);
        replay(mockConn, resolver);

        PostgresFolderDAO dao = new PostgresFolderDAO(mockConn);
        dao.setAccessKeyResolver(resolver);
        assertTrue(dao.accessKeyIsValid(folder, "k"));
        assertFalse(dao.accessKeyIsValid(folder, "bad"));

        verify(resolver);
    }

    @Test
    public void testGetRootChildCountsOfUserQueriesEachRootTable() throws SQLException {
        UUID ownerId = UUID.randomUUID();

        Connection conn = createMock(Connection.class);
        PreparedStatement folderStmt = createMock(PreparedStatement.class);
        PreparedStatement networkStmt = createMock(PreparedStatement.class);
        PreparedStatement shortcutStmt = createMock(PreparedStatement.class);
        ResultSet folderRs = createMock(ResultSet.class);
        ResultSet networkRs = createMock(ResultSet.class);
        ResultSet shortcutRs = createMock(ResultSet.class);

        expect(conn.prepareStatement("SELECT COUNT(*) FROM folder WHERE owneruuid=? AND parent IS NULL AND is_deleted=false"))
                .andReturn(folderStmt);
        folderStmt.setObject(1, ownerId);
        expectLastCall();
        expect(folderStmt.executeQuery()).andReturn(folderRs);
        expect(folderRs.next()).andReturn(true);
        expect(folderRs.getLong(1)).andReturn(2L);
        folderRs.close();
        expectLastCall();
        folderStmt.close();
        expectLastCall();

        expect(conn.prepareStatement("SELECT COUNT(*) FROM network WHERE owneruuid=? AND parent IS NULL AND is_deleted=false"))
                .andReturn(networkStmt);
        networkStmt.setObject(1, ownerId);
        expectLastCall();
        expect(networkStmt.executeQuery()).andReturn(networkRs);
        expect(networkRs.next()).andReturn(true);
        expect(networkRs.getLong(1)).andReturn(3L);
        networkRs.close();
        expectLastCall();
        networkStmt.close();
        expectLastCall();

        expect(conn.prepareStatement("SELECT COUNT(*) FROM shortcut WHERE owneruuid=? AND parent IS NULL AND is_deleted=false"))
                .andReturn(shortcutStmt);
        shortcutStmt.setObject(1, ownerId);
        expectLastCall();
        expect(shortcutStmt.executeQuery()).andReturn(shortcutRs);
        expect(shortcutRs.next()).andReturn(true);
        expect(shortcutRs.getLong(1)).andReturn(4L);
        shortcutRs.close();
        expectLastCall();
        shortcutStmt.close();
        expectLastCall();

        replay(conn, folderStmt, networkStmt, shortcutStmt, folderRs, networkRs, shortcutRs);

        FileCount counts = new PostgresFolderDAO(conn).getRootChildCountsOfUser(ownerId);

        assertEquals(2L, counts.getFolder());
        assertEquals(3L, counts.getNetwork());
        assertEquals(4L, counts.getShortcut());
        verify(conn, folderStmt, networkStmt, shortcutStmt, folderRs, networkRs, shortcutRs);
    }

    // ── read authorization goes through the injected resolver (issue #165) ────
    //
    // The predicate is built by the resolver and embedded in the query, so these tests assert both that
    // the resolver is consulted with the right arguments and that its output actually reaches the SQL.
    // A sentinel fragment is used rather than matching real SQL keywords, so the assertion cannot pass
    // by coincidence if the DAO were to fall back to a hand-built predicate.

    private static final String FRAGMENT = "__RESOLVER_FRAGMENT__";

    /** Drives isReadable with a mock resolver; returns the SQL the DAO actually executed. */
    private String runIsReadable(boolean dbVerdict, boolean expectedResult, UUID viewer) throws Exception {
        Connection conn = createMock(Connection.class);
        PreparedStatement pst = createMock(PreparedStatement.class);
        ResultSet rs = createMock(ResultSet.class);
        FilePermissionResolver resolver = createMock(FilePermissionResolver.class);

        Set<UUID> granted = Collections.singleton(FOLDER);
        // READ, not WRITE: a read check must not narrow to write-granted folders only.
        expect(resolver.grantedFolderIds(viewer, Permissions.READ)).andReturn(granted);
        expect(resolver.readableConditionSql(FileType.FOLDER, "f", viewer, granted)).andReturn(FRAGMENT);

        Capture<String> sql = newCapture();
        expect(conn.prepareStatement(capture(sql))).andReturn(pst);
        pst.setObject(1, FOLDER);
        expectLastCall();
        expect(pst.executeQuery()).andReturn(rs);
        expect(rs.next()).andReturn(true);
        expect(rs.getBoolean(1)).andReturn(dbVerdict);
        rs.close();
        expectLastCall();
        pst.close();
        expectLastCall();
        replay(conn, pst, rs, resolver);

        PostgresFolderDAO dao = new PostgresFolderDAO(conn);
        dao.setPermissionResolver(resolver);
        assertEquals(expectedResult, dao.isReadable(FOLDER, viewer));

        verify(conn, pst, rs, resolver);
        return sql.getValue();
    }

    /**
     * The all-folders scope must keep emitting exactly the statement it always has. The predicate is
     * assembled from fragments, so a stray space would change the SQL text with nothing else to catch it.
     */
    @Test
    public void listFoldersOfUserIncludesNestedFoldersByDefault() throws SQLException {
        UUID ownerId = UUID.randomUUID();

        Connection conn = createMock(Connection.class);
        PreparedStatement stmt = createMock(PreparedStatement.class);
        ResultSet rs = createMock(ResultSet.class);

        expect(conn.prepareStatement("SELECT \"UUID\", name, parent, creation_time, modification_time, is_deleted, description, visibility "
                + " FROM folder "
                + " WHERE owneruuid=? AND is_deleted=false "
                + " ORDER BY name "
                + " LIMIT ?")).andReturn(stmt);
        stmt.setObject(1, ownerId);
        expectLastCall();
        stmt.setInt(2, 25);
        expectLastCall();
        expect(stmt.executeQuery()).andReturn(rs);
        expect(rs.next()).andReturn(false);
        rs.close();
        expectLastCall();
        stmt.close();
        expectLastCall();

        replay(conn, stmt, rs);

        // The 2-arg form is what /v3/files/folders/ and the MCP get_folder list mode call; it must stay
        // on the all-folders scope.
        assertTrue(new PostgresFolderDAO(conn).listFoldersOfUser(ownerId, 25).isEmpty());

        verify(conn, stmt, rs);
    }

    @Test
    public void listFoldersOfUserRestrictsToHomeRootWhenNestedExcluded() throws SQLException {
        UUID ownerId = UUID.randomUUID();

        Connection conn = createMock(Connection.class);
        PreparedStatement stmt = createMock(PreparedStatement.class);
        ResultSet rs = createMock(ResultSet.class);

        // parent IS NULL is the same home-root definition getRootChildCountsOfUser counts with, so
        // /v2/user/{id}/networksets and its networkSetCount cannot disagree.
        expect(conn.prepareStatement("SELECT \"UUID\", name, parent, creation_time, modification_time, is_deleted, description, visibility "
                + " FROM folder "
                + " WHERE owneruuid=? AND parent IS NULL AND is_deleted=false "
                + " ORDER BY name "
                + " LIMIT ?")).andReturn(stmt);
        stmt.setObject(1, ownerId);
        expectLastCall();
        stmt.setInt(2, 25);
        expectLastCall();
        expect(stmt.executeQuery()).andReturn(rs);
        expect(rs.next()).andReturn(false);
        rs.close();
        expectLastCall();
        stmt.close();
        expectLastCall();

        replay(conn, stmt, rs);

        assertTrue(new PostgresFolderDAO(conn).listFoldersOfUser(ownerId, 25, false).isEmpty());

        verify(conn, stmt, rs);
    }

    @Test
    public void testIsReadableEmbedsResolverPredicateAndReturnsGranted() throws Exception {
        String sql = runIsReadable(true, true, USER);
        assertTrue("the resolver's predicate must be the one evaluated", sql.contains(FRAGMENT));
    }

    @Test
    public void testIsReadableReturnsDeniedWhenPredicateEvaluatesFalse() throws Exception {
        runIsReadable(false, false, USER);
    }

    /** Anonymous callers are resolved the same way; the DAO does not branch on a null user itself. */
    @Test
    public void testIsReadableDelegatesForAnonymousCaller() throws Exception {
        String sql = runIsReadable(false, false, null);
        assertTrue(sql.contains(FRAGMENT));
    }

    @Test
    public void testIsReadableThrowsNotFoundWhenFolderRowAbsent() throws Exception {
        Connection conn = createMock(Connection.class);
        PreparedStatement pst = createMock(PreparedStatement.class);
        ResultSet rs = createMock(ResultSet.class);
        FilePermissionResolver resolver = createMock(FilePermissionResolver.class);

        expect(resolver.grantedFolderIds(USER, Permissions.READ)).andReturn(Collections.<UUID>emptySet());
        expect(resolver.readableConditionSql(eq(FileType.FOLDER), eq("f"), eq(USER), anyObject()))
                .andReturn(FRAGMENT);
        expect(conn.prepareStatement(anyString())).andReturn(pst);
        pst.setObject(anyInt(), anyObject());
        expectLastCall().anyTimes();
        expect(pst.executeQuery()).andReturn(rs);
        expect(rs.next()).andReturn(false);   // folder row missing
        rs.close();
        expectLastCall();
        pst.close();
        expectLastCall();
        replay(conn, pst, rs, resolver);

        PostgresFolderDAO dao = new PostgresFolderDAO(conn);
        dao.setPermissionResolver(resolver);
        assertThrows(ObjectNotFoundException.class, () -> dao.isReadable(FOLDER, USER));
    }

    // ── getEffectivePermission is a passthrough, including the null case ──────

    @Test
    public void testGetEffectivePermissionPassesResolverResultThrough() throws Exception {
        Connection conn = createMock(Connection.class);
        FilePermissionResolver resolver = createMock(FilePermissionResolver.class);
        expect(resolver.effectiveFolderPermission(FOLDER, USER)).andReturn(Permissions.WRITE);
        expect(resolver.effectiveFolderPermission(SUBFOLDER, USER)).andReturn(Permissions.READ);
        expect(resolver.effectiveFolderPermission(NETWORK, USER)).andReturn(null);
        replay(conn, resolver);

        PostgresFolderDAO dao = new PostgresFolderDAO(conn);
        dao.setPermissionResolver(resolver);

        assertEquals(Permissions.WRITE, dao.getEffectivePermission(FOLDER, USER));
        assertEquals(Permissions.READ, dao.getEffectivePermission(SUBFOLDER, USER));
        assertEquals(null, dao.getEffectivePermission(NETWORK, USER));
        verify(resolver);
    }

    // ── permission writes touch this folder only (issue #165) ─────────────────
    //
    // Permissions used to be copied down: a grant enumerated every descendant folder and wrote a
    // folder_permission row for each, plus a user_network_membership row for every network beneath.
    // Inheritance is now resolved at read time, so exactly one row is written.
    //
    // The Connection is a strict mock allowing exactly ONE prepareStatement. That is what proves the
    // copy-down is gone: a descendant enumeration or a membership write would be a second statement and
    // would fail the mock as an unexpected call.

    @Test
    public void testSetFolderPermissionWritesOneRowForThisFolderOnly() throws Exception {
        Connection conn = createMock(Connection.class);
        PreparedStatement pst = createMock(PreparedStatement.class);

        Capture<String> sql = newCapture();
        expect(conn.prepareStatement(capture(sql))).andReturn(pst);   // exactly one statement
        pst.setObject(1, FOLDER);
        expectLastCall();
        pst.setObject(2, USER);
        expectLastCall();
        pst.setString(3, Permissions.WRITE.toString());
        expectLastCall();
        expect(pst.executeUpdate()).andReturn(1);
        pst.close();
        expectLastCall();
        replay(conn, pst);

        PostgresFolderDAO dao = new PostgresFolderDAO(conn);
        dao.setFolderPermission(FOLDER, USER, Permissions.WRITE);

        String s = sql.getValue();
        assertTrue("writes the folder's own grant", s.contains("INSERT INTO folder_permission"));
        assertFalse("must not write per-network membership rows", s.contains("user_network_membership"));
        assertFalse("must not enumerate descendants", s.toLowerCase().contains("recursive"));
        verify(conn, pst);
    }

    /**
     * Revocation deletes this folder's row and nothing else.
     *
     * <p>The old cascade also deleted {@code user_network_membership} rows for every network beneath the
     * folder — including grants a user held directly, issued through the network sharing endpoints and
     * unrelated to the folder. Un-sharing a folder therefore silently un-shared those networks, with no
     * record of which rows were collateral.</p>
     */
    @Test
    public void testRemoveFolderPermissionDeletesOneRowAndSparesDirectNetworkGrants() throws Exception {
        Connection conn = createMock(Connection.class);
        PreparedStatement pst = createMock(PreparedStatement.class);

        Capture<String> sql = newCapture();
        expect(conn.prepareStatement(capture(sql))).andReturn(pst);   // exactly one statement
        pst.setObject(1, FOLDER);
        expectLastCall();
        pst.setObject(2, USER);
        expectLastCall();
        expect(pst.executeUpdate()).andReturn(1);
        pst.close();
        expectLastCall();
        replay(conn, pst);

        PostgresFolderDAO dao = new PostgresFolderDAO(conn);
        dao.removeFolderPermission(FOLDER, USER);

        String s = sql.getValue();
        assertTrue(s.contains("DELETE FROM folder_permission"));
        assertTrue("scoped to one folder and one user",
                s.contains("folder_id = ?") && s.contains("user_id = ?"));
        assertFalse("must not touch direct per-network grants", s.contains("user_network_membership"));
        verify(conn, pst);
    }

    // ── is_shared reflects effective sharing, not just direct rows ────────────

    /**
     * An object shared only by inheritance from an ancestor folder must still report as shared, otherwise
     * its owner gets no signal that it is exposed while collaborators can read and edit it.
     *
     * <p>Drives a real listing call with empty result sets and inspects the three generated queries.</p>
     */
    @Test
    public void testListingSqlComputesIsSharedFromAncestorChain() throws Exception {
        Connection conn = createMock(Connection.class);
        FilePermissionResolver resolver = createMock(FilePermissionResolver.class);

        Set<UUID> granted = Collections.singleton(FOLDER);
        expect(resolver.grantedFolderIds(USER, Permissions.READ)).andReturn(granted);
        expect(resolver.readableConditionSql(FileType.FOLDER, "f", USER, granted)).andReturn("TRUE");
        expect(resolver.readableConditionSql(FileType.NETWORK, "n", USER, granted)).andReturn("TRUE");
        expect(resolver.readableConditionSql(FileType.SHORTCUT, "s", USER, granted)).andReturn("TRUE");

        // three child queries: folders, networks, shortcuts — all returning nothing
        Capture<String> sql = newCapture(org.easymock.CaptureType.ALL);
        for (int i = 0; i < 3; i++) {
            PreparedStatement pst = createNiceMock(PreparedStatement.class);
            ResultSet rs = createNiceMock(ResultSet.class);
            expect(rs.next()).andReturn(false).anyTimes();
            expect(pst.executeQuery()).andReturn(rs).anyTimes();
            replay(pst, rs);
            expect(conn.prepareStatement(capture(sql))).andReturn(pst);
        }
        replay(conn, resolver);

        PostgresFolderDAO dao = new PostgresFolderDAO(conn);
        dao.setPermissionResolver(resolver);
        dao.listReadableItemsInFolder(FOLDER, false, null, USER);

        String folderSql = sql.getValues().get(0);
        String networkSql = sql.getValues().get(1);

        assertTrue("folder is_shared must walk the ancestor chain",
                folderSql.contains("is_shared") && folderSql.contains("RECURSIVE chain"));
        // A network can be shared either directly or by inheritance, so both arms must be present —
        // neither alone covers the other's case.
        assertTrue("network is_shared must keep the direct-membership arm",
                networkSql.contains("user_network_membership"));
        assertTrue("network is_shared must add the ancestor-chain arm",
                networkSql.contains("RECURSIVE chain"));
        verify(resolver);
    }

    // ── certified must stay in the listing projection ─────────────────────────
    //
    // isCertified is read with a plain rs.getBoolean, so a SQL NULL — or a column dropped from the
    // select list — both surface as false rather than as an error. Dropping it would therefore make
    // every network in every folder listing silently report "not certified", including networks that
    // really are certified and locked. No mapper test can catch that, because the mapper would still
    // be doing exactly what it was told; only the query shape reveals it.
    @Test
    public void testListingSqlSelectsCertified() throws Exception {
        Connection conn = createMock(Connection.class);
        FilePermissionResolver resolver = createMock(FilePermissionResolver.class);

        Set<UUID> granted = Collections.singleton(FOLDER);
        expect(resolver.grantedFolderIds(USER, Permissions.READ)).andReturn(granted);
        expect(resolver.readableConditionSql(FileType.FOLDER, "f", USER, granted)).andReturn("TRUE");
        expect(resolver.readableConditionSql(FileType.NETWORK, "n", USER, granted)).andReturn("TRUE");
        expect(resolver.readableConditionSql(FileType.SHORTCUT, "s", USER, granted)).andReturn("TRUE");

        Capture<String> sql = newCapture(org.easymock.CaptureType.ALL);
        for (int i = 0; i < 3; i++) {
            PreparedStatement pst = createNiceMock(PreparedStatement.class);
            ResultSet rs = createNiceMock(ResultSet.class);
            expect(rs.next()).andReturn(false).anyTimes();
            expect(pst.executeQuery()).andReturn(rs).anyTimes();
            replay(pst, rs);
            expect(conn.prepareStatement(capture(sql))).andReturn(pst);
        }
        replay(conn, resolver);

        PostgresFolderDAO dao = new PostgresFolderDAO(conn);
        dao.setPermissionResolver(resolver);
        dao.listReadableItemsInFolder(FOLDER, false, null, USER);

        // Order matches testListingSqlComputesIsSharedFromAncestorChain: folders, networks, shortcuts.
        String networkSql = sql.getValues().get(1);
        assertTrue("network listing must select certified, or every entry reports isCertified=false",
                networkSql.contains("certified"));
        assertTrue("network listing must keep selecting ndexdoi — isCertified is meaningless without it",
                networkSql.contains("ndexdoi"));
        verify(resolver);
    }

    // Note: there is no folder-audience test here. Solr moved from index-time access lists to
    // query-time scope filtering, which removed getFolderPermissionsWithUsernames along with its only
    // caller. The surviving network audience path (getAllMembershipsOnNetwork -> effectiveMembers, still
    // used by SolrIndexBuilder and CXNetworkLoader) is asserted in TestPostgresNetworkDAO.

    // ── deleteFolder reports the ids it affected ─────────────────────────────
    //
    // A force delete cascades over the whole subtree, but Solr docs live outside the transaction, so the
    // caller has to be told every id that was touched in order to clear them. These tests pin down that
    // reporting; without it a cascaded delete silently orphans its descendants' index entries.

    /** A ResultSet yielding one UUID per row from column 1. */
    private static ResultSet uuidRows(UUID... ids) throws SQLException {
        ResultSet rs = createNiceMock(ResultSet.class);
        for (UUID id : ids) {
            expect(rs.next()).andReturn(true);
            expect(rs.getObject(1)).andReturn(id);
        }
        expect(rs.next()).andReturn(false);
        replay(rs);
        return rs;
    }

    /** A ResultSet yielding a single COUNT(*) value. */
    private static ResultSet countRow(long count) throws SQLException {
        ResultSet rs = createNiceMock(ResultSet.class);
        expect(rs.next()).andReturn(true);
        expect(rs.getLong(1)).andReturn(count);
        replay(rs);
        return rs;
    }

    /**
     * Wires a Connection whose statements return the given result sets from executeQuery in order.
     * Every statement is the same nice mock, so parameter binding and executeUpdate are no-ops.
     */
    private static Connection connectionYielding(ResultSet... queryResults) throws SQLException {
        PreparedStatement pst = createNiceMock(PreparedStatement.class);
        for (ResultSet rs : queryResults) {
            expect(pst.executeQuery()).andReturn(rs);
        }
        expect(pst.executeUpdate()).andReturn(1).anyTimes();
        replay(pst);

        Connection conn = createNiceMock(Connection.class);
        expect(conn.prepareStatement(anyString())).andReturn(pst).anyTimes();
        replay(conn);
        return conn;
    }

    @Test
    public void softForceDeleteReportsTheWholeSubtree() throws SQLException {
        // Query order: descendants(FOLDER) -> [SUBFOLDER], descendants(SUBFOLDER) -> [], then the
        // network ids parented in the tree, then the shortcut ids.
        Connection conn = connectionYielding(
                uuidRows(SUBFOLDER),
                uuidRows(),
                uuidRows(NETWORK),
                uuidRows(SHORTCUT));

        DeletedFileIds deleted = new PostgresFolderDAO(conn).deleteFolder(FOLDER, true, false);

        // The named folder is always reported, and descendants at any depth come with it.
        assertEquals(List.of(FOLDER, SUBFOLDER), deleted.folders());
        assertEquals(List.of(NETWORK), deleted.networks());
        assertEquals(List.of(SHORTCUT), deleted.shortcuts());
        assertEquals(4, deleted.size());
    }

    @Test
    public void permanentForceDeleteReportsTheWholeSubtree() throws SQLException {
        Connection conn = connectionYielding(
                uuidRows(SUBFOLDER),
                uuidRows(),
                uuidRows(NETWORK),
                uuidRows(SHORTCUT));

        DeletedFileIds deleted = new PostgresFolderDAO(conn).deleteFolder(FOLDER, true, true);

        assertEquals(List.of(FOLDER, SUBFOLDER), deleted.folders());
        assertEquals(List.of(NETWORK), deleted.networks());
        assertEquals(List.of(SHORTCUT), deleted.shortcuts());
    }

    @Test
    public void deleteReportsNestedDescendantsAtEveryDepth() throws SQLException {
        UUID grandchild = UUID.fromString("55555555-5555-5555-5555-555555555555");
        // descendants(FOLDER) -> [SUBFOLDER]; descendants(SUBFOLDER) -> [grandchild];
        // descendants(grandchild) -> []; then the network and shortcut queries.
        Connection conn = connectionYielding(
                uuidRows(SUBFOLDER),
                uuidRows(grandchild),
                uuidRows(),
                uuidRows(),
                uuidRows());

        DeletedFileIds deleted = new PostgresFolderDAO(conn).deleteFolder(FOLDER, true, false);

        assertEquals(List.of(FOLDER, SUBFOLDER, grandchild), deleted.folders());
    }

    @Test
    public void nonForceDeleteOfAnEmptyFolderReportsOnlyThatFolder() throws SQLException {
        // getFolderChildCounts runs three COUNT queries; all zero means empty. Without force there is no
        // traversal, so no descendant ids are collected.
        Connection conn = connectionYielding(countRow(0), countRow(0), countRow(0));

        DeletedFileIds deleted = new PostgresFolderDAO(conn).deleteFolder(FOLDER, false, false);

        assertEquals(List.of(FOLDER), deleted.folders());
        assertTrue(deleted.networks().isEmpty());
        assertTrue(deleted.shortcuts().isEmpty());
    }

    @Test
    public void nonForceDeleteOfANonEmptyFolderIsRejected() throws SQLException {
        // One shortcut child is enough to reject the delete, so an unforced delete can never destroy a
        // network parented in the folder.
        Connection conn = connectionYielding(countRow(0), countRow(0), countRow(1));

        PostgresFolderDAO dao = new PostgresFolderDAO(conn);
        SQLException e = assertThrows(SQLException.class, () -> dao.deleteFolder(FOLDER, false, false));
        assertTrue(e.getMessage().contains("Folder is not empty"));
    }
}
