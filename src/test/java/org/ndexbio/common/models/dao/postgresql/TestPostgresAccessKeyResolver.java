package org.ndexbio.common.models.dao.postgresql;

import static org.easymock.EasyMock.*;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Arrays;
import java.util.Set;
import java.util.UUID;

import org.easymock.Capture;
import org.junit.Test;

public class TestPostgresAccessKeyResolver {

    /** Empty/null keys short-circuit to false without touching the database. */
    @Test
    public void testEmptyKeyShortCircuits() throws SQLException {
        Connection conn = createMock(Connection.class);
        replay(conn); // no prepareStatement expected

        PostgresAccessKeyResolver r = new PostgresAccessKeyResolver(conn);
        assertFalse(r.isFolderKeyValid(UUID.randomUUID(), ""));
        assertFalse(r.isFolderKeyValid(UUID.randomUUID(), null));
        assertFalse(r.isNetworkKeyValid(UUID.randomUUID(), ""));
        assertFalse(r.isNetworkKeyValid(UUID.randomUUID(), null));

        verify(conn);
    }

    @Test
    public void testFolderKeyValidWhenChainHasMatch() throws SQLException {
        assertEquals(true, runSingleRowQuery(true));
    }

    @Test
    public void testFolderKeyInvalidWhenNoMatch() throws SQLException {
        assertEquals(false, runSingleRowQuery(false));
    }

    private boolean runSingleRowQuery(boolean rowPresent) throws SQLException {
        Connection conn = createMock(Connection.class);
        PreparedStatement pst = createMock(PreparedStatement.class);
        ResultSet rs = createMock(ResultSet.class);

        expect(conn.prepareStatement(anyString())).andReturn(pst);
        pst.setObject(anyInt(), anyObject());
        expectLastCall().anyTimes();
        pst.setString(anyInt(), anyString());
        expectLastCall().anyTimes();
        expect(pst.executeQuery()).andReturn(rs);
        expect(rs.next()).andReturn(rowPresent);
        rs.close();
        expectLastCall();
        pst.close();
        expectLastCall();
        replay(conn, pst, rs);

        PostgresAccessKeyResolver r = new PostgresAccessKeyResolver(conn);
        boolean result = r.isFolderKeyValid(UUID.randomUUID(), "k");
        verify(conn, pst, rs);
        return result;
    }

    @Test
    public void testNetworkKeyValidWhenRowPresent() throws SQLException {
        Connection conn = createMock(Connection.class);
        PreparedStatement pst = createMock(PreparedStatement.class);
        ResultSet rs = createMock(ResultSet.class);

        expect(conn.prepareStatement(anyString())).andReturn(pst);
        pst.setObject(anyInt(), anyObject());
        expectLastCall().anyTimes();
        pst.setString(anyInt(), anyString());
        expectLastCall().anyTimes();
        expect(pst.executeQuery()).andReturn(rs);
        expect(rs.next()).andReturn(true);
        rs.close();
        expectLastCall();
        pst.close();
        expectLastCall();
        replay(conn, pst, rs);

        PostgresAccessKeyResolver r = new PostgresAccessKeyResolver(conn);
        assertTrue(r.isNetworkKeyValid(UUID.randomUUID(), "k"));
        verify(conn, pst, rs);
    }

    @Test
    public void testFilterNetworksByKeyAssemblesReturnedIds() throws SQLException {
        Connection conn = createMock(Connection.class);
        PreparedStatement pst = createMock(PreparedStatement.class);
        ResultSet rs = createMock(ResultSet.class);

        UUID a = UUID.randomUUID();
        UUID b = UUID.randomUUID();
        UUID c = UUID.randomUUID();

        expect(conn.prepareStatement(anyString())).andReturn(pst);
        pst.setString(anyInt(), anyString());
        expectLastCall().anyTimes();
        expect(pst.executeQuery()).andReturn(rs);
        // two granted rows (a and b); c not returned
        expect(rs.next()).andReturn(true).andReturn(true).andReturn(false);
        expect(rs.getObject(1)).andReturn(a).andReturn(b);
        rs.close();
        expectLastCall();
        pst.close();
        expectLastCall();
        replay(conn, pst, rs);

        PostgresAccessKeyResolver r = new PostgresAccessKeyResolver(conn);
        Set<UUID> granted = r.filterNetworksByKey(Arrays.asList(a, b, c), "k");

        assertEquals(2, granted.size());
        assertTrue(granted.contains(a));
        assertTrue(granted.contains(b));
        assertFalse(granted.contains(c));
        verify(conn, pst, rs);
    }

    /**
     * The isNetworkKeyValid query must seed the folder chain from same-owner NETWORK shortcuts pointing
     * at the network (issue #133/#137), so a key stranded on a migrated networkset folder still resolves.
     * The mock can't exercise real rows, so assert the emitted SQL carries the shortcut seed and the
     * same-owner guard, and that all four UUID binds are set (index shifted from 2 to 4).
     */
    @Test
    public void testNetworkKeyQuerySeedsFromSameOwnerShortcut() throws SQLException {
        Connection conn = createMock(Connection.class);
        PreparedStatement pst = createMock(PreparedStatement.class);
        ResultSet rs = createMock(ResultSet.class);

        Capture<String> sqlCap = newCapture();
        expect(conn.prepareStatement(capture(sqlCap))).andReturn(pst);
        Capture<Integer> objIdx = newCapture(org.easymock.CaptureType.ALL);
        pst.setObject(captureInt(objIdx), anyObject());
        expectLastCall().anyTimes();
        pst.setString(anyInt(), anyString());
        expectLastCall().anyTimes();
        expect(pst.executeQuery()).andReturn(rs);
        expect(rs.next()).andReturn(false);
        rs.close();
        expectLastCall();
        pst.close();
        expectLastCall();
        replay(conn, pst, rs);

        PostgresAccessKeyResolver r = new PostgresAccessKeyResolver(conn);
        r.isNetworkKeyValid(UUID.randomUUID(), "k");
        verify(conn, pst, rs);

        String sql = sqlCap.getValue();
        assertTrue("query must join shortcut table", sql.contains("shortcut s"));
        assertTrue("query must restrict to NETWORK shortcuts", sql.contains("s.target_type = 'NETWORK'"));
        assertTrue("query must apply the same-owner guard",
                sql.contains("s.owneruuid = (SELECT owneruuid FROM network"));
        assertEquals("four UUID binds expected (network parent, shortcut target, owner, own-key)",
                4, objIdx.getValues().size());
    }

    /**
     * filterNetworksByKey is the batch twin: its seed_folders CTE must union each network's own parent
     * folder with the parent folders of its same-owner NETWORK shortcuts.
     */
    @Test
    public void testFilterNetworksByKeyQuerySeedsFromSameOwnerShortcut() throws SQLException {
        Connection conn = createMock(Connection.class);
        PreparedStatement pst = createMock(PreparedStatement.class);
        ResultSet rs = createMock(ResultSet.class);

        Capture<String> sqlCap = newCapture();
        expect(conn.prepareStatement(capture(sqlCap))).andReturn(pst);
        pst.setString(anyInt(), anyString());
        expectLastCall().anyTimes();
        expect(pst.executeQuery()).andReturn(rs);
        expect(rs.next()).andReturn(false);
        rs.close();
        expectLastCall();
        pst.close();
        expectLastCall();
        replay(conn, pst, rs);

        PostgresAccessKeyResolver r = new PostgresAccessKeyResolver(conn);
        r.filterNetworksByKey(Arrays.asList(UUID.randomUUID()), "k");
        verify(conn, pst, rs);

        String sql = sqlCap.getValue();
        assertTrue("query must define seed_folders CTE", sql.contains("seed_folders"));
        assertTrue("query must join shortcut table", sql.contains("shortcut sc"));
        assertTrue("query must restrict to NETWORK shortcuts", sql.contains("sc.target_type = 'NETWORK'"));
        assertTrue("query must apply the same-owner guard", sql.contains("sc.owneruuid = sd.net_owner"));
    }

    @Test
    public void testFilterNetworksByKeyEmptyInputsNoQuery() throws SQLException {
        Connection conn = createMock(Connection.class);
        replay(conn); // no query for empty id list / empty key

        PostgresAccessKeyResolver r = new PostgresAccessKeyResolver(conn);
        assertTrue(r.filterNetworksByKey(java.util.Collections.emptyList(), "k").isEmpty());
        assertTrue(r.filterNetworksByKey(Arrays.asList(UUID.randomUUID()), "").isEmpty());
        verify(conn);
    }

}
