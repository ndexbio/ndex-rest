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
import java.util.List;
import java.util.UUID;

import org.junit.Test;
import org.ndexbio.common.models.dao.AccessKeyResolver;
import org.ndexbio.common.models.dao.DeletedFileIds;
import org.ndexbio.model.object.FileCount;

public class TestPostgresFolderDAO {

    private static final UUID FOLDER = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID SUBFOLDER = UUID.fromString("22222222-2222-2222-2222-222222222222");
    private static final UUID NETWORK = UUID.fromString("33333333-3333-3333-3333-333333333333");
    private static final UUID SHORTCUT = UUID.fromString("44444444-4444-4444-4444-444444444444");

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
