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
import java.util.UUID;

import org.junit.Test;
import org.ndexbio.model.exceptions.ObjectNotFoundException;
import org.ndexbio.model.exceptions.UnauthorizedOperationException;

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
}
