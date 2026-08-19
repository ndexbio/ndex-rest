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

import java.util.Collections;
import java.util.Set;

import org.easymock.Capture;
import org.ndexbio.common.models.dao.AccessKeyResolver;
import org.ndexbio.common.models.dao.FilePermissionResolver;
import org.ndexbio.model.exceptions.BadRequestException;
import org.ndexbio.model.object.FileType;
import org.ndexbio.model.object.Permissions;
import org.ndexbio.model.object.network.NetworkIndexLevel;
import org.ndexbio.model.object.network.VisibilityType;

public class TestPostgresNetworkDAO {

    private static final UUID NET = UUID.fromString("33333333-3333-3333-3333-333333333333");
    private static final UUID VIEWER = UUID.fromString("55555555-5555-5555-5555-555555555555");
    private static final String FRAGMENT = "__RESOLVER_FRAGMENT__";

    // ── read/write authorization goes through the injected resolver (issue #165) ──
    //
    // The predicate is built by the resolver and embedded in the query, so these assert both that the
    // resolver is consulted with the right arguments and that its output reaches the executed SQL. A
    // sentinel fragment is used so the assertion cannot pass by coincidence against real SQL keywords.

    /**
     * Read checks must ask for {@code READ}-granted folders. Asking for {@code WRITE} here would hide
     * content a user can legitimately read.
     */
    @Test
    public void testIsReadableUsesReadableFragmentAndReadGrantedFolders() throws Exception {
        Connection conn = createMock(Connection.class);
        PreparedStatement pst = createMock(PreparedStatement.class);
        ResultSet rs = createMock(ResultSet.class);
        FilePermissionResolver resolver = createMock(FilePermissionResolver.class);

        Set<UUID> granted = Collections.singleton(UUID.randomUUID());
        expect(resolver.grantedFolderIds(VIEWER, Permissions.READ)).andReturn(granted);
        expect(resolver.readableConditionSql(FileType.NETWORK, "n", VIEWER, granted)).andReturn(FRAGMENT);

        Capture<String> sql = newCapture();
        expect(conn.prepareStatement(capture(sql))).andReturn(pst);
        pst.setObject(anyInt(), anyObject());
        expectLastCall().anyTimes();
        expect(pst.executeQuery()).andReturn(rs);
        expect(rs.next()).andReturn(true);
        expect(rs.getBoolean(1)).andReturn(true);
        rs.close();
        expectLastCall();
        pst.close();
        expectLastCall();
        replay(conn, pst, rs, resolver);

        PostgresNetworkDAO dao = new PostgresNetworkDAO(conn);
        dao.setPermissionResolver(resolver);

        assertTrue(dao.isReadable(NET, VIEWER));
        assertTrue(sql.getValue().contains(FRAGMENT));
        verify(conn, pst, rs, resolver);
    }

    /**
     * Write checks must use the <em>writable</em> fragment and ask for {@code WRITE}-granted folders.
     *
     * <p>Both halves matter. Using the readable fragment would let public visibility confer write, and
     * asking for {@code READ}-granted folders would let a read-only grantee edit everything in a shared
     * folder. The strict mock fails the test if either argument is wrong.</p>
     */
    @Test
    public void testIsWriteableUsesWritableFragmentAndWriteGrantedFolders() throws Exception {
        Connection conn = createMock(Connection.class);
        PreparedStatement pst = createMock(PreparedStatement.class);
        ResultSet rs = createMock(ResultSet.class);
        FilePermissionResolver resolver = createMock(FilePermissionResolver.class);

        Set<UUID> granted = Collections.singleton(UUID.randomUUID());
        expect(resolver.grantedFolderIds(VIEWER, Permissions.WRITE)).andReturn(granted);
        expect(resolver.writableConditionSql(FileType.NETWORK, "n", VIEWER, granted)).andReturn(FRAGMENT);

        Capture<String> sql = newCapture();
        expect(conn.prepareStatement(capture(sql))).andReturn(pst);
        pst.setObject(anyInt(), anyObject());
        expectLastCall().anyTimes();
        expect(pst.executeQuery()).andReturn(rs);
        expect(rs.next()).andReturn(true);   // a row means the predicate matched
        rs.close();
        expectLastCall();
        pst.close();
        expectLastCall();
        replay(conn, pst, rs, resolver);

        PostgresNetworkDAO dao = new PostgresNetworkDAO(conn);
        dao.setPermissionResolver(resolver);

        assertTrue(dao.isWriteable(NET, VIEWER));
        assertTrue(sql.getValue().contains(FRAGMENT));
        verify(conn, pst, rs, resolver);
    }

    /** No row means the predicate did not match, so write access is denied. */
    @Test
    public void testIsWriteableDeniedWhenPredicateMatchesNoRow() throws Exception {
        Connection conn = createMock(Connection.class);
        PreparedStatement pst = createMock(PreparedStatement.class);
        ResultSet rs = createMock(ResultSet.class);
        FilePermissionResolver resolver = createMock(FilePermissionResolver.class);

        expect(resolver.grantedFolderIds(VIEWER, Permissions.WRITE))
                .andReturn(Collections.<UUID>emptySet());
        expect(resolver.writableConditionSql(eq(FileType.NETWORK), eq("n"), eq(VIEWER), anyObject()))
                .andReturn(FRAGMENT);
        expect(conn.prepareStatement(anyString())).andReturn(pst);
        pst.setObject(anyInt(), anyObject());
        expectLastCall().anyTimes();
        expect(pst.executeQuery()).andReturn(rs);
        expect(rs.next()).andReturn(false);
        rs.close();
        expectLastCall();
        pst.close();
        expectLastCall();
        replay(conn, pst, rs, resolver);

        PostgresNetworkDAO dao = new PostgresNetworkDAO(conn);
        dao.setPermissionResolver(resolver);

        assertFalse(dao.isWriteable(NET, VIEWER));
        verify(resolver);
    }

    /** The Solr audience lookup must come from the resolver, not a direct-row query. */
    @Test
    public void testGetAllMembershipsOnNetworkDelegatesToEffectiveMembers() throws Exception {
        Connection conn = createMock(Connection.class);
        FilePermissionResolver resolver = createMock(FilePermissionResolver.class);

        java.util.Map<Permissions, java.util.Collection<String>> expected = new java.util.HashMap<>();
        expected.put(Permissions.ADMIN, java.util.Arrays.asList("owner"));
        expected.put(Permissions.WRITE, java.util.Arrays.asList("writer"));
        expected.put(Permissions.READ, java.util.Arrays.asList("reader"));
        expect(resolver.effectiveMembers(NET, FileType.NETWORK)).andReturn(expected);
        replay(conn, resolver);   // conn strict: no direct-row query may be issued

        PostgresNetworkDAO dao = new PostgresNetworkDAO(conn);
        dao.setPermissionResolver(resolver);

        assertEquals(expected, dao.getAllMembershipsOnNetwork(NET));
        verify(conn, resolver);
    }

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
