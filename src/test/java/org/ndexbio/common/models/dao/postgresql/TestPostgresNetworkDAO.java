package org.ndexbio.common.models.dao.postgresql;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;
import java.util.List;
import java.util.UUID;

import org.junit.Test;
import static org.easymock.EasyMock.*;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.ndexbio.common.models.dao.AccessKeyResolver;
import org.ndexbio.model.exceptions.BadRequestException;
import org.ndexbio.model.object.network.NetworkIndexLevel;
import org.ndexbio.model.object.network.VisibilityType;

public class TestPostgresNetworkDAO {

    @Test
    public void testAccessKeyIsValidDelegatesToResolver() throws SQLException {
        Connection mockConn = createMock(Connection.class);
        AccessKeyResolver resolver = createMock(AccessKeyResolver.class);
        UUID net = UUID.randomUUID();
        expect(resolver.isNetworkKeyValid(net, "k")).andReturn(true);
        replay(mockConn, resolver);

        PostgresNetworkDAO dao = new PostgresNetworkDAO(mockConn);
        dao.setAccessKeyResolver(resolver);
        assertTrue(dao.accessKeyIsValid(net, "k"));

        verify(resolver);
    }

    @Test
    public void testAccessKeyIsValidEmptyKeySkipsResolver() throws SQLException {
        Connection mockConn = createMock(Connection.class);
        AccessKeyResolver resolver = createMock(AccessKeyResolver.class);
        replay(mockConn, resolver); // resolver must NOT be consulted for an empty key

        PostgresNetworkDAO dao = new PostgresNetworkDAO(mockConn);
        dao.setAccessKeyResolver(resolver);
        assertFalse(dao.accessKeyIsValid(UUID.randomUUID(), ""));
        assertFalse(dao.accessKeyIsValid(UUID.randomUUID(), null));

        verify(resolver);
    }

    @Test
    public void testSetErrorMessageNull_usesJdbcNull() throws SQLException {
        Connection mockConn = createMock(Connection.class);
        PreparedStatement mockPst = createMock(PreparedStatement.class);

        expect(mockConn.prepareStatement(anyString())).andReturn(mockPst);
        mockPst.setNull(1, Types.VARCHAR);
        expectLastCall();
        mockPst.setObject(eq(2), anyObject());
        expectLastCall();
        expect(mockPst.executeUpdate()).andReturn(1);
        mockConn.commit();
        expectLastCall();
        mockPst.close();
        expectLastCall();
        replay(mockConn, mockPst);

        PostgresNetworkDAO dao = new PostgresNetworkDAO(mockConn);
        dao.setErrorMessage(UUID.randomUUID(), null);

        verify(mockConn, mockPst);
    }

    @Test
    public void testSetErrorMessageValue_usesSetString() throws SQLException {
        Connection mockConn = createMock(Connection.class);
        PreparedStatement mockPst = createMock(PreparedStatement.class);

        expect(mockConn.prepareStatement(anyString())).andReturn(mockPst);
        mockPst.setString(eq(1), eq("some error"));
        expectLastCall();
        mockPst.setObject(eq(2), anyObject());
        expectLastCall();
        expect(mockPst.executeUpdate()).andReturn(1);
        mockConn.commit();
        expectLastCall();
        mockPst.close();
        expectLastCall();
        replay(mockConn, mockPst);

        PostgresNetworkDAO dao = new PostgresNetworkDAO(mockConn);
        dao.setErrorMessage(UUID.randomUUID(), "some error");

        verify(mockConn, mockPst);
    }

    @Test
    public void testSetErrorMessageTruncation() throws SQLException {
        String longMsg = "x".repeat(2001);
        String expectedTruncated = "x".repeat(1996) + "...";

        Connection mockConn = createMock(Connection.class);
        PreparedStatement mockPst = createMock(PreparedStatement.class);

        expect(mockConn.prepareStatement(anyString())).andReturn(mockPst);
        mockPst.setString(eq(1), eq(expectedTruncated));
        expectLastCall();
        mockPst.setObject(eq(2), anyObject());
        expectLastCall();
        expect(mockPst.executeUpdate()).andReturn(1);
        mockConn.commit();
        expectLastCall();
        mockPst.close();
        expectLastCall();
        replay(mockConn, mockPst);

        PostgresNetworkDAO dao = new PostgresNetworkDAO(mockConn);
        dao.setErrorMessage(UUID.randomUUID(), longMsg);

        verify(mockConn, mockPst);
    }

    // ---- requestDOI: final-visibility -> access-key-provisioning behavior --------------------------
    // Partial mock of PostgresNetworkDAO so the real requestDOI orchestration runs against mocked
    // collaborators; whether enableNetworkAccessKey / updateNetworkVisibility are invoked is the assertion
    // (an unexpected call to a mocked-but-not-expected method fails the mock).

    private static PostgresNetworkDAO requestDoiPartialMock(Connection conn) {
        return createMockBuilder(PostgresNetworkDAO.class)
                .withConstructor(Connection.class).withArgs(conn)
                .addMockedMethod("setFlag")
                .addMockedMethod("setDOI")
                .addMockedMethod("getNetworkVisibility")
                .addMockedMethod("updateNetworkVisibility")
                .addMockedMethod("setIndexLevel")
                .addMockedMethod("enableNetworkAccessKey")
                .createMock();
    }

    /** Certified -> network made PUBLIC, NO access key provisioned (returns null). */
    @Test
    public void testRequestDOICertifiedMakesPublicNoKey() throws Exception {
        Connection conn = createMock(Connection.class);
        PostgresNetworkDAO dao = requestDoiPartialMock(conn);
        UUID net = UUID.randomUUID();

        dao.setFlag(net, "readonly", true); expectLastCall();
        dao.setDOI(net, PostgresNetworkDAO.PENDING); expectLastCall();
        dao.setFlag(net, "certified", true); expectLastCall();
        dao.updateNetworkVisibility(net, VisibilityType.PUBLIC, true); expectLastCall();
        dao.setIndexLevel(net, NetworkIndexLevel.ALL); expectLastCall();
        // enableNetworkAccessKey / getNetworkVisibility must NOT be called.
        replay(dao);

        assertNull(dao.requestDOI(net, true));
        verify(dao);
    }

    /** Non-certified PRIVATE -> a network-scoped access key is provisioned (returned). */
    @Test
    public void testRequestDOIPrivateProvisionsOwnKey() throws Exception {
        Connection conn = createMock(Connection.class);
        PostgresNetworkDAO dao = requestDoiPartialMock(conn);
        UUID net = UUID.randomUUID();

        dao.setFlag(net, "readonly", true); expectLastCall();
        dao.setDOI(net, PostgresNetworkDAO.PENDING); expectLastCall();
        dao.setFlag(net, "certified", false); expectLastCall();
        expect(dao.getNetworkVisibility(net)).andReturn(VisibilityType.PRIVATE);
        expect(dao.enableNetworkAccessKey(net)).andReturn("thekey");
        // updateNetworkVisibility / setIndexLevel must NOT be called.
        replay(dao);

        assertEquals("thekey", dao.requestDOI(net, false));
        verify(dao);
    }

    /** Non-certified PUBLIC -> no access key provisioned (returns null). */
    @Test
    public void testRequestDOIPublicNoKey() throws Exception {
        Connection conn = createMock(Connection.class);
        PostgresNetworkDAO dao = requestDoiPartialMock(conn);
        UUID net = UUID.randomUUID();

        dao.setFlag(net, "readonly", true); expectLastCall();
        dao.setDOI(net, PostgresNetworkDAO.PENDING); expectLastCall();
        dao.setFlag(net, "certified", false); expectLastCall();
        expect(dao.getNetworkVisibility(net)).andReturn(VisibilityType.PUBLIC);
        // enableNetworkAccessKey must NOT be called.
        replay(dao);

        assertNull(dao.requestDOI(net, false));
        verify(dao);
    }

    // ---- getNetworkAccessKey: null edge case (the condition that trips the mint-time 400 guard) -----

    /** access_key_is_on = false -> returns null even though a key string is stored. */
    @Test
    public void testGetNetworkAccessKeyNullWhenKeyOff() throws Exception {
        Connection conn = createMock(Connection.class);
        PreparedStatement pst = createMock(PreparedStatement.class);
        ResultSet rs = createMock(ResultSet.class);

        expect(conn.prepareStatement(anyString())).andReturn(pst);
        pst.setObject(anyInt(), anyObject()); expectLastCall();
        expect(pst.executeQuery()).andReturn(rs);
        expect(rs.next()).andReturn(true);
        expect(rs.getString(1)).andReturn("somekey");
        expect(rs.getBoolean(2)).andReturn(false);
        rs.close(); expectLastCall();
        pst.close(); expectLastCall();
        replay(conn, pst, rs);

        PostgresNetworkDAO dao = new PostgresNetworkDAO(conn);
        assertNull(dao.getNetworkAccessKey(UUID.randomUUID()));
        verify(conn, pst, rs);
    }

    /** Positive control: access_key_is_on = true -> returns the stored key. */
    @Test
    public void testGetNetworkAccessKeyReturnsKeyWhenOn() throws Exception {
        Connection conn = createMock(Connection.class);
        PreparedStatement pst = createMock(PreparedStatement.class);
        ResultSet rs = createMock(ResultSet.class);

        expect(conn.prepareStatement(anyString())).andReturn(pst);
        pst.setObject(anyInt(), anyObject()); expectLastCall();
        expect(pst.executeQuery()).andReturn(rs);
        expect(rs.next()).andReturn(true);
        expect(rs.getString(1)).andReturn("somekey");
        expect(rs.getBoolean(2)).andReturn(true);
        rs.close(); expectLastCall();
        pst.close(); expectLastCall();
        replay(conn, pst, rs);

        PostgresNetworkDAO dao = new PostgresNetworkDAO(conn);
        assertEquals("somekey", dao.getNetworkAccessKey(UUID.randomUUID()));
        verify(conn, pst, rs);
    }

    // ---- PR #139: batch-summary id lists must be validated before any SQL is built (no injection) ----
    // The Connection mock is put in replay mode with NO prepareStatement expectation, so if a malformed
    // id string ever reached SQL construction the call would fail; instead it must be rejected with 400.

    @Test(expected = BadRequestException.class)
    public void testGetNetworkSummariesRejectsMalformedIdBeforeSql() throws Exception {
        Connection conn = createMock(Connection.class);
        replay(conn); // no SQL expected
        PostgresNetworkDAO dao = new PostgresNetworkDAO(conn);
        dao.getNetworkSummariesByIdStrList(List.of("x') OR 1=1 --"), UUID.randomUUID(), null);
    }

    @Test(expected = BadRequestException.class)
    public void testGetNetworkV3SummariesRejectsMalformedIdBeforeSql() throws Exception {
        Connection conn = createMock(Connection.class);
        replay(conn); // no SQL expected
        PostgresNetworkDAO dao = new PostgresNetworkDAO(conn);
        dao.getNetworkV3SummariesByIdStrList(List.of("not-a-uuid"), UUID.randomUUID(), null, null);
    }
}
