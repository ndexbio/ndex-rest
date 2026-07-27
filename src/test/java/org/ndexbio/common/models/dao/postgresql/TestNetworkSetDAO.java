package org.ndexbio.common.models.dao.postgresql;

import static org.easymock.EasyMock.*;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.UUID;

import org.easymock.Capture;
import org.junit.Test;
import org.ndexbio.model.exceptions.ObjectNotFoundException;
import org.ndexbio.model.exceptions.UnauthorizedOperationException;
import org.ndexbio.model.object.NetworkSet;

/**
 * Unit tests for the archive-only {@link NetworkSetDAO}. These verify the DAO reads exclusively from
 * the frozen {@code network_set} / {@code network_set_member} tables (JDBC is mocked; there is no
 * {@code FolderDAO} involved). Coverage focuses on the simple single-query methods and the cheap
 * branch behavior of {@code getNetworkSet}; the full two-query member read is covered by the
 * integration test against seeded rows.
 */
public class TestNetworkSetDAO {

	// ---- isNetworkSetOwner ----

	@Test
	public void testIsNetworkSetOwnerTrueWhenRowPresent() throws SQLException {
		assertTrue(runOwnerQuery(true));
	}

	@Test
	public void testIsNetworkSetOwnerFalseWhenNoRow() throws SQLException {
		assertFalse(runOwnerQuery(false));
	}

	private boolean runOwnerQuery(boolean rowPresent) throws SQLException {
		Connection conn = createMock(Connection.class);
		PreparedStatement pst = createMock(PreparedStatement.class);
		ResultSet rs = createNiceMock(ResultSet.class);

		expect(conn.prepareStatement(anyString())).andReturn(pst);
		pst.setObject(anyInt(), anyObject());
		expectLastCall().anyTimes();
		expect(pst.executeQuery()).andReturn(rs);
		expect(rs.next()).andReturn(rowPresent);
		pst.close();
		expectLastCall();
		replay(conn, pst, rs);

		NetworkSetDAO dao = new NetworkSetDAO(conn);
		boolean result = dao.isNetworkSetOwner(UUID.randomUUID(), UUID.randomUUID());
		verify(conn, pst);
		return result;
	}

	// ---- getNetworkSetAccessKey ----

	@Test
	public void testGetNetworkSetAccessKeyReturnsKeyWhenEnabled() throws Exception {
		Connection conn = createMock(Connection.class);
		PreparedStatement pst = createMock(PreparedStatement.class);
		ResultSet rs = createNiceMock(ResultSet.class);

		expect(conn.prepareStatement(anyString())).andReturn(pst);
		pst.setObject(anyInt(), anyObject());
		expectLastCall().anyTimes();
		expect(pst.executeQuery()).andReturn(rs);
		expect(rs.next()).andReturn(true);
		expect(rs.getString(1)).andReturn("secret-key");
		expect(rs.getBoolean(2)).andReturn(true);
		pst.close();
		expectLastCall();
		replay(conn, pst, rs);

		NetworkSetDAO dao = new NetworkSetDAO(conn);
		assertEquals("secret-key", dao.getNetworkSetAccessKey(UUID.randomUUID()));
		verify(conn, pst);
	}

	@Test
	public void testGetNetworkSetAccessKeyReturnsNullWhenDisabled() throws Exception {
		Connection conn = createMock(Connection.class);
		PreparedStatement pst = createMock(PreparedStatement.class);
		ResultSet rs = createNiceMock(ResultSet.class);

		expect(conn.prepareStatement(anyString())).andReturn(pst);
		pst.setObject(anyInt(), anyObject());
		expectLastCall().anyTimes();
		expect(pst.executeQuery()).andReturn(rs);
		expect(rs.next()).andReturn(true);
		expect(rs.getString(1)).andReturn("secret-key");
		expect(rs.getBoolean(2)).andReturn(false);
		pst.close();
		expectLastCall();
		replay(conn, pst, rs);

		NetworkSetDAO dao = new NetworkSetDAO(conn);
		assertNull(dao.getNetworkSetAccessKey(UUID.randomUUID()));
		verify(conn, pst);
	}

	@Test(expected = ObjectNotFoundException.class)
	public void testGetNetworkSetAccessKeyThrowsWhenMissing() throws Exception {
		Connection conn = createMock(Connection.class);
		PreparedStatement pst = createMock(PreparedStatement.class);
		ResultSet rs = createNiceMock(ResultSet.class);

		expect(conn.prepareStatement(anyString())).andReturn(pst);
		pst.setObject(anyInt(), anyObject());
		expectLastCall().anyTimes();
		expect(pst.executeQuery()).andReturn(rs);
		expect(rs.next()).andReturn(false);
		pst.close();
		expectLastCall();
		replay(conn, pst, rs);

		NetworkSetDAO dao = new NetworkSetDAO(conn);
		dao.getNetworkSetAccessKey(UUID.randomUUID());
	}

	// ---- getNetworkSet: cheap guard branches (single-query, no member read) ----

	@Test(expected = ObjectNotFoundException.class)
	public void testGetNetworkSetThrowsWhenSetMissing() throws Exception {
		Connection conn = createMock(Connection.class);
		PreparedStatement pst = createMock(PreparedStatement.class);
		ResultSet rs = createNiceMock(ResultSet.class);

		// Only the first (metadata) query runs; missing row -> ObjectNotFoundException before members.
		expect(conn.prepareStatement(anyString())).andReturn(pst);
		pst.setObject(anyInt(), anyObject());
		expectLastCall().anyTimes();
		expect(pst.executeQuery()).andReturn(rs);
		expect(rs.next()).andReturn(false);
		pst.close();
		expectLastCall();
		replay(conn, pst, rs);

		NetworkSetDAO dao = new NetworkSetDAO(conn);
		dao.getNetworkSet(UUID.randomUUID(), UUID.randomUUID(), null);
	}

	@Test(expected = UnauthorizedOperationException.class)
	public void testGetNetworkSetThrowsOnInvalidAccessKey() throws Exception {
		Connection conn = createMock(Connection.class);
		PreparedStatement pst = createMock(PreparedStatement.class);
		ResultSet rs = createNiceMock(ResultSet.class);

		// Row present, key enabled with a different stored key -> supplied non-null key is invalid,
		// so it throws before the member query (only the metadata query runs).
		expect(conn.prepareStatement(anyString())).andReturn(pst);
		pst.setObject(anyInt(), anyObject());
		expectLastCall().anyTimes();
		expect(pst.executeQuery()).andReturn(rs);
		expect(rs.next()).andReturn(true);
		expect(rs.getString(6)).andReturn("stored-key"); // access_key
		expect(rs.getBoolean(7)).andReturn(true);         // access_key_is_on
		// remaining getters (timestamps, name, description, other_attributes, showcased, doi) default
		// via the nice mock; other_attributes (getString(8)) -> null so no JSON parsing.
		pst.close();
		expectLastCall();
		replay(conn, pst, rs);

		NetworkSetDAO dao = new NetworkSetDAO(conn);
		dao.getNetworkSet(UUID.randomUUID(), UUID.randomUUID(), "wrong-key");
	}

	@Test(expected = UnauthorizedOperationException.class)
	public void testGetNetworkSetNullStoredKeyDoesNotNPE() throws Exception {
		Connection conn = createMock(Connection.class);
		PreparedStatement pst = createMock(PreparedStatement.class);
		ResultSet rs = createNiceMock(ResultSet.class);

		// access_key column is nullable: key flagged on but stored key is NULL. A supplied non-null
		// key must NOT NullPointerException on the compare — it is simply invalid, so the method
		// throws UnauthorizedOperationException (guards the #2 regression).
		expect(conn.prepareStatement(anyString())).andReturn(pst);
		pst.setObject(anyInt(), anyObject());
		expectLastCall().anyTimes();
		expect(pst.executeQuery()).andReturn(rs);
		expect(rs.next()).andReturn(true);
		expect(rs.getString(6)).andReturn(null); // access_key IS NULL
		expect(rs.getBoolean(7)).andReturn(true); // access_key_is_on = true
		pst.close();
		expectLastCall();
		replay(conn, pst, rs);

		NetworkSetDAO dao = new NetworkSetDAO(conn);
		dao.getNetworkSet(UUID.randomUUID(), UUID.randomUUID(), "any-key");
	}

	// ---- getNetworkSetsByUserId (owner-scoped archive list) ----

	@Test
	public void testGetNetworkSetsByUserIdEmptyWhenNoRows() throws Exception {
		Connection conn = createMock(Connection.class);
		PreparedStatement pst = createMock(PreparedStatement.class);
		ResultSet rs = createNiceMock(ResultSet.class);

		// Exactly ONE prepareStatement: header query only (no rows -> no per-set member query).
		Capture<String> sqlCap = newCapture();
		expect(conn.prepareStatement(capture(sqlCap))).andReturn(pst);
		pst.setObject(anyInt(), anyObject());
		expectLastCall().anyTimes();
		expect(pst.executeQuery()).andReturn(rs);
		expect(rs.next()).andReturn(false);
		pst.close();
		expectLastCall();
		replay(conn, pst, rs);

		NetworkSetDAO dao = new NetworkSetDAO(conn);
		List<NetworkSet> sets = dao.getNetworkSetsByUserId(UUID.randomUUID(), UUID.randomUUID(), 0, 0, false, false);
		verify(conn, pst);

		assertTrue(sets.isEmpty());
		String sql = sqlCap.getValue();
		assertTrue("owner-scoped header query", sql.contains("owner_id=?"));
		assertTrue("frozen non-deleted rows only", sql.contains("is_deleted=false"));
		assertTrue("reads the frozen network_set table", sql.contains("from network_set"));
		assertFalse("no showcase filter by default", sql.contains("showcased=true"));
	}

	@Test
	public void testGetNetworkSetsByUserIdShowcasedOnlyAddsFilter() throws Exception {
		Connection conn = createMock(Connection.class);
		PreparedStatement pst = createMock(PreparedStatement.class);
		ResultSet rs = createNiceMock(ResultSet.class);

		Capture<String> sqlCap = newCapture();
		expect(conn.prepareStatement(capture(sqlCap))).andReturn(pst);
		pst.setObject(anyInt(), anyObject());
		expectLastCall().anyTimes();
		expect(pst.executeQuery()).andReturn(rs);
		expect(rs.next()).andReturn(false);
		pst.close();
		expectLastCall();
		replay(conn, pst, rs);

		NetworkSetDAO dao = new NetworkSetDAO(conn);
		dao.getNetworkSetsByUserId(UUID.randomUUID(), UUID.randomUUID(), 0, 0, false, true);
		verify(conn, pst);

		assertTrue("showcasedOnly adds the showcased filter", sqlCap.getValue().contains("showcased=true"));
	}

	@Test
	public void testGetNetworkSetsByUserIdSummaryOnlySkipsMemberQuery() throws Exception {
		Connection conn = createMock(Connection.class);
		PreparedStatement pst = createMock(PreparedStatement.class);
		ResultSet rs = createNiceMock(ResultSet.class);

		// One header row present. With summaryOnly=true the member query must NOT run, so the strict
		// conn mock expects prepareStatement exactly once — a second call (member read) fails verify().
		expect(conn.prepareStatement(anyString())).andReturn(pst);
		pst.setObject(anyInt(), anyObject());
		expectLastCall().anyTimes();
		expect(pst.executeQuery()).andReturn(rs);
		expect(rs.next()).andReturn(true).andReturn(false);
		pst.close();
		expectLastCall();
		replay(conn, pst, rs);

		NetworkSetDAO dao = new NetworkSetDAO(conn);
		List<NetworkSet> sets = dao.getNetworkSetsByUserId(UUID.randomUUID(), UUID.randomUUID(), 0, 0, true, false);
		verify(conn, pst);

		assertEquals(1, sets.size());
	}

	@Test
	public void testMemberQueryExcludesDeletedNetworks() throws Exception {
		Connection conn = createMock(Connection.class);
		PreparedStatement pst = createNiceMock(PreparedStatement.class);
		ResultSet rs = createNiceMock(ResultSet.class);

		// One header row, summaryOnly=false -> the member query runs. Capture every SQL statement so we
		// can assert the member read filters out soft-deleted networks (dangling members guard, #145).
		Capture<String> sqlCap = newCapture(org.easymock.CaptureType.ALL);
		expect(conn.prepareStatement(capture(sqlCap))).andReturn(pst).anyTimes();
		expect(pst.executeQuery()).andReturn(rs).anyTimes();
		// header query: one row then done; member query: no rows.
		expect(rs.next()).andReturn(true).andReturn(false).andReturn(false);
		replay(conn, pst, rs);

		NetworkSetDAO dao = new NetworkSetDAO(conn);
		dao.getNetworkSetsByUserId(UUID.randomUUID(), UUID.randomUUID(), 0, 0, false, false);
		verify(conn);

		String memberSql = sqlCap.getValues().stream()
				.filter(s -> s.contains("network_set_member"))
				.findFirst()
				.orElseThrow(() -> new AssertionError("member query was not issued"));
		assertTrue("member query must exclude soft-deleted networks: " + memberSql,
				memberSql.contains("n.is_deleted=false"));
	}
}
