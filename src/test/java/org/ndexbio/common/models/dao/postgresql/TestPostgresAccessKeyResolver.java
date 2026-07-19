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

    @Test
    public void testFilterNetworksByKeyEmptyInputsNoQuery() throws SQLException {
        Connection conn = createMock(Connection.class);
        replay(conn); // no query for empty id list / empty key

        PostgresAccessKeyResolver r = new PostgresAccessKeyResolver(conn);
        assertTrue(r.filterNetworksByKey(java.util.Collections.emptyList(), "k").isEmpty());
        assertTrue(r.filterNetworksByKey(Arrays.asList(UUID.randomUUID()), "").isEmpty());
        verify(conn);
    }

    /** The chain query returns a row -> that key (own or nearest ancestor folder). */
    @Test
    public void testResolveNetworkAccessKeyReturnsNearestKey() throws SQLException {
        Connection conn = createMock(Connection.class);
        PreparedStatement pst = createMock(PreparedStatement.class);
        ResultSet rs = createMock(ResultSet.class);

        expect(conn.prepareStatement(anyString())).andReturn(pst);
        pst.setObject(anyInt(), anyObject());
        expectLastCall().anyTimes();
        expect(pst.executeQuery()).andReturn(rs);
        expect(rs.next()).andReturn(true);
        expect(rs.getString(1)).andReturn("inheritedKey");
        rs.close();
        expectLastCall();
        pst.close();
        expectLastCall();
        replay(conn, pst, rs);

        PostgresAccessKeyResolver r = new PostgresAccessKeyResolver(conn);
        assertEquals("inheritedKey", r.resolveNetworkAccessKey(UUID.randomUUID()));
        verify(conn, pst, rs);
    }

    /** No enabled key anywhere in the chain -> null (empty result set). */
    @Test
    public void testResolveNetworkAccessKeyReturnsNullWhenNoKey() throws SQLException {
        Connection conn = createMock(Connection.class);
        PreparedStatement pst = createMock(PreparedStatement.class);
        ResultSet rs = createMock(ResultSet.class);

        expect(conn.prepareStatement(anyString())).andReturn(pst);
        pst.setObject(anyInt(), anyObject());
        expectLastCall().anyTimes();
        expect(pst.executeQuery()).andReturn(rs);
        expect(rs.next()).andReturn(false);
        rs.close();
        expectLastCall();
        pst.close();
        expectLastCall();
        replay(conn, pst, rs);

        PostgresAccessKeyResolver r = new PostgresAccessKeyResolver(conn);
        assertEquals(null, r.resolveNetworkAccessKey(UUID.randomUUID()));
        verify(conn, pst, rs);
    }
}
