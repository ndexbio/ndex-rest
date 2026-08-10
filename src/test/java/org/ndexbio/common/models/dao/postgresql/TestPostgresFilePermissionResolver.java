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
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

import org.easymock.Capture;
import org.junit.Test;
import org.ndexbio.model.object.FileType;
import org.ndexbio.model.object.Permissions;

/**
 * Unit coverage for {@link PostgresFilePermissionResolver} (issue #165).
 *
 * <p>Two kinds of assertion live here, and the distinction matters. With a mocked {@link Connection}
 * the database never runs, so these tests verify the <em>Java</em> behavior — short-circuits, rank
 * mapping, shortcut delegation, set handling — plus, via {@link Capture}, that the generated SQL
 * actually contains the guards the design depends on (the same-owner shortcut clause, the
 * {@code is_deleted} filters, the fail-closed grant filter). Whether those guards produce the right
 * rows against real data is proven by the integration suite, not here.</p>
 */
public class TestPostgresFilePermissionResolver {

	private static final UUID FOLDER   = UUID.fromString("11111111-1111-1111-1111-111111111111");
	private static final UUID NETWORK  = UUID.fromString("22222222-2222-2222-2222-222222222222");
	private static final UUID SHORTCUT = UUID.fromString("33333333-3333-3333-3333-333333333333");
	private static final UUID USER     = UUID.fromString("44444444-4444-4444-4444-444444444444");

	// ── helpers ─────────────────────────────────────────────────────────────────

	/** A connection whose single query yields one row holding {@code rank} in column 1. */
	private static Connection connYieldingRank(int rank, Capture<String> sqlCapture) throws SQLException {
		Connection conn = createMock(Connection.class);
		PreparedStatement pst = createMock(PreparedStatement.class);
		ResultSet rs = createMock(ResultSet.class);

		if (sqlCapture != null)
			expect(conn.prepareStatement(capture(sqlCapture))).andReturn(pst);
		else
			expect(conn.prepareStatement(anyString())).andReturn(pst);

		pst.setObject(anyInt(), anyObject());
		expectLastCall().anyTimes();
		expect(pst.executeQuery()).andReturn(rs);
		expect(rs.next()).andReturn(true);
		expect(rs.getInt(1)).andReturn(rank);
		rs.close();
		expectLastCall();
		pst.close();
		expectLastCall();
		replay(conn, pst, rs);
		return conn;
	}

	private static Permissions folderPermForRank(int rank) throws SQLException {
		Connection conn = connYieldingRank(rank, null);
		return new PostgresFilePermissionResolver(conn).effectiveFolderPermission(FOLDER, USER);
	}

	// ── short-circuits: no database work at all ─────────────────────────────────

	@Test
	public void testNullIdsShortCircuitWithoutQuerying() throws SQLException {
		Connection conn = createMock(Connection.class);
		replay(conn); // no prepareStatement expected

		PostgresFilePermissionResolver r = new PostgresFilePermissionResolver(conn);
		assertNull(r.effectiveFolderPermission(null, USER));
		assertNull(r.effectiveNetworkPermission(null, USER));
		assertNull(r.effectiveShortcutPermission(null, USER));
		assertTrue(r.grantedFolderIds(null, Permissions.READ).isEmpty());

		verify(conn);
	}

	// ── rank mapping (owner / write / read / none) ──────────────────────────────

	@Test
	public void testOwnerRankResolvesToWrite() throws SQLException {
		assertEquals(Permissions.WRITE, folderPermForRank(3));
	}

	@Test
	public void testWriteGrantResolvesToWrite() throws SQLException {
		assertEquals(Permissions.WRITE, folderPermForRank(2));
	}

	@Test
	public void testReadGrantResolvesToRead() throws SQLException {
		assertEquals(Permissions.READ, folderPermForRank(1));
	}

	/** No grant anywhere for an authenticated user — denied, not defaulted. */
	@Test
	public void testNoGrantResolvesToNull() throws SQLException {
		assertNull(folderPermForRank(0));
	}

	/** READ must never satisfy a write check. */
	@Test
	public void testReadDoesNotImplyWrite() throws SQLException {
		assertFalse(Permissions.WRITE.equals(folderPermForRank(1)));
	}

	// ── anonymous callers take the visibility-only path ─────────────────────────

	@Test
	public void testAnonymousFolderUsesVisibilityOnlyQuery() throws SQLException {
		Capture<String> sql = newCapture();
		Connection conn = createMock(Connection.class);
		PreparedStatement pst = createMock(PreparedStatement.class);
		ResultSet rs = createMock(ResultSet.class);
		expect(conn.prepareStatement(capture(sql))).andReturn(pst);
		pst.setObject(anyInt(), anyObject());
		expectLastCall().anyTimes();
		expect(pst.executeQuery()).andReturn(rs);
		expect(rs.next()).andReturn(true);
		rs.close();
		expectLastCall();
		pst.close();
		expectLastCall();
		replay(conn, pst, rs);

		assertEquals(Permissions.READ,
				new PostgresFilePermissionResolver(conn).effectiveFolderPermission(FOLDER, null));

		// An anonymous caller must never cause a permission-table lookup.
		assertFalse("anonymous path must not touch folder_permission",
				sql.getValue().contains("folder_permission"));
		assertTrue(sql.getValue().contains("visibility IN ('PUBLIC','UNLISTED')"));
		verify(conn, pst, rs);
	}

	// ── shortcuts delegate to their target, never to their own parent ───────────

	@Test
	public void testShortcutDelegatesToNetworkTarget() throws SQLException {
		Connection conn = createMock(Connection.class);
		PreparedStatement lookup = createMock(PreparedStatement.class);
		ResultSet lookupRs = createMock(ResultSet.class);
		PreparedStatement delegate = createMock(PreparedStatement.class);
		ResultSet delegateRs = createMock(ResultSet.class);

		Capture<String> firstSql = newCapture();
		expect(conn.prepareStatement(capture(firstSql))).andReturn(lookup);
		lookup.setObject(anyInt(), anyObject());
		expectLastCall().anyTimes();
		expect(lookup.executeQuery()).andReturn(lookupRs);
		expect(lookupRs.next()).andReturn(true);
		expect(lookupRs.getObject(1)).andReturn(NETWORK);
		expect(lookupRs.getString(2)).andReturn("NETWORK");
		lookupRs.close();
		expectLastCall();
		lookup.close();
		expectLastCall();

		expect(conn.prepareStatement(anyString())).andReturn(delegate);
		delegate.setObject(anyInt(), anyObject());
		expectLastCall().anyTimes();
		expect(delegate.executeQuery()).andReturn(delegateRs);
		expect(delegateRs.next()).andReturn(true);
		expect(delegateRs.getInt(1)).andReturn(2);
		delegateRs.close();
		expectLastCall();
		delegate.close();
		expectLastCall();
		replay(conn, lookup, lookupRs, delegate, delegateRs);

		assertEquals(Permissions.WRITE,
				new PostgresFilePermissionResolver(conn).effectiveShortcutPermission(SHORTCUT, USER));

		// The shortcut lookup reads only target/target_type — the shortcut's own parent is irrelevant.
		assertFalse("shortcut resolution must not consult s.parent",
				firstSql.getValue().contains("parent"));
		verify(conn, lookup, lookupRs, delegate, delegateRs);
	}

	/** A shortcut whose target was removed resolves to no access rather than throwing. */
	@Test
	public void testDanglingShortcutResolvesToNullNotError() throws SQLException {
		Connection conn = createMock(Connection.class);
		PreparedStatement pst = createMock(PreparedStatement.class);
		ResultSet rs = createMock(ResultSet.class);
		expect(conn.prepareStatement(anyString())).andReturn(pst);
		pst.setObject(anyInt(), anyObject());
		expectLastCall().anyTimes();
		expect(pst.executeQuery()).andReturn(rs);
		expect(rs.next()).andReturn(false); // target row gone
		rs.close();
		expectLastCall();
		pst.close();
		expectLastCall();
		replay(conn, pst, rs);

		assertNull(new PostgresFilePermissionResolver(conn).effectiveShortcutPermission(SHORTCUT, USER));
		verify(conn, pst, rs);
	}

	// ── generated SQL carries the guards the design depends on ──────────────────

	@Test
	public void testNetworkQueryCarriesSameOwnerShortcutGuard() throws SQLException {
		Capture<String> sql = newCapture();
		Connection conn = connYieldingRank(1, sql);
		new PostgresFilePermissionResolver(conn).effectiveNetworkPermission(NETWORK, USER);

		String s = sql.getValue();
		assertTrue("network chain must seed from same-owner NETWORK shortcuts",
				s.contains("s.target_type = 'NETWORK'"));
		assertTrue("same-owner guard blocks cross-owner escalation",
				s.contains("s.owneruuid = (SELECT owneruuid FROM network"));
		assertTrue("deleted shortcuts must not seed the chain", s.contains("s.is_deleted = false"));
		assertTrue("deleted ancestors must break the chain", s.contains("pf.is_deleted = false"));
	}

	/** Only READ and WRITE are honoured; anything else in the unvalidated column is ignored. */
	@Test
	public void testGrantFilterFailsClosed() throws SQLException {
		Capture<String> sql = newCapture();
		Connection conn = connYieldingRank(0, sql);
		new PostgresFilePermissionResolver(conn).effectiveFolderPermission(FOLDER, USER);
		assertTrue(sql.getValue().contains("upper(fp.permission) IN ('READ','WRITE')"));
	}

	@Test
	public void testGrantedFolderIdsWriteVariantExcludesReadGrants() throws SQLException {
		Capture<String> sql = newCapture();
		Connection conn = createMock(Connection.class);
		PreparedStatement pst = createMock(PreparedStatement.class);
		ResultSet rs = createMock(ResultSet.class);
		expect(conn.prepareStatement(capture(sql))).andReturn(pst);
		pst.setObject(anyInt(), anyObject());
		expectLastCall().anyTimes();
		expect(pst.executeQuery()).andReturn(rs);
		expect(rs.next()).andReturn(false);
		rs.close();
		expectLastCall();
		pst.close();
		expectLastCall();
		replay(conn, pst, rs);

		new PostgresFilePermissionResolver(conn).grantedFolderIds(USER, Permissions.WRITE);
		assertTrue("write variant must require WRITE specifically",
				sql.getValue().contains("upper(fp.permission) = 'WRITE'"));
		assertTrue("granted folders expand downward to descendants",
				sql.getValue().contains("JOIN sub s ON f.parent = s.fid"));
	}

	/**
	 * A folder's owner holds no folder_permission row for it, so seeding the set from grants alone
	 * leaves an owner with nothing and they cannot see what collaborators placed in their own folder —
	 * the reciprocal half of #165. The ownership arm is what prevents that.
	 */
	@Test
	public void testGrantedFolderIdsSeedsFromOwnedFoldersToo() throws SQLException {
		Capture<String> sql = newCapture();
		Connection conn = createMock(Connection.class);
		PreparedStatement pst = createMock(PreparedStatement.class);
		ResultSet rs = createMock(ResultSet.class);
		expect(conn.prepareStatement(capture(sql))).andReturn(pst);
		pst.setObject(anyInt(), anyObject());
		expectLastCall().anyTimes();
		expect(pst.executeQuery()).andReturn(rs);
		expect(rs.next()).andReturn(false);
		rs.close();
		expectLastCall();
		pst.close();
		expectLastCall();
		replay(conn, pst, rs);

		new PostgresFilePermissionResolver(conn).grantedFolderIds(USER, Permissions.READ);
		assertTrue("owned folders must seed the set alongside granted ones",
				sql.getValue().contains("f.owneruuid = ?"));
	}

	// ── SQL fragment builders (no database access) ──────────────────────────────

	private PostgresFilePermissionResolver fragmentResolver() {
		return new PostgresFilePermissionResolver(createMock(Connection.class));
	}

	@Test
	public void testAnonymousFragmentIsVisibilityOnlyAndNeverWritable() {
		PostgresFilePermissionResolver r = fragmentResolver();
		String read = r.readableConditionSql(FileType.NETWORK, "n", null, Collections.<UUID>emptySet());
		assertTrue(read.contains("visibility IN ('PUBLIC','UNLISTED')"));
		assertFalse(read.contains("user_network_membership"));
		assertEquals("(false)", r.writableConditionSql(FileType.NETWORK, "n", null, Collections.<UUID>emptySet()));
	}

	/** An empty granted set must not produce {@code IN ()}, which is a syntax error. */
	@Test
	public void testEmptyGrantedSetOmitsInClause() {
		String sql = fragmentResolver()
				.readableConditionSql(FileType.NETWORK, "n", USER, Collections.<UUID>emptySet());
		assertFalse("empty set must not emit an IN () clause", sql.contains("IN ()"));
		assertTrue("owner check still present", sql.contains("n.owneruuid"));
	}

	@Test
	public void testNetworkFragmentUsesGrantedSetAndShortcutSeed() {
		Set<UUID> granted = new HashSet<>();
		granted.add(FOLDER);
		String sql = fragmentResolver().readableConditionSql(FileType.NETWORK, "n", USER, granted);

		assertTrue("network inherits when its parent is a granted folder", sql.contains("n.parent IN ("));
		assertTrue("same-owner shortcut seed present", sql.contains("sc.owneruuid = n.owneruuid"));
		assertTrue(sql.contains(FOLDER.toString()));
	}

	/** Public visibility confers read, never write. */
	@Test
	public void testWritableFragmentIgnoresVisibility() {
		Set<UUID> granted = new HashSet<>();
		granted.add(FOLDER);
		String sql = fragmentResolver().writableConditionSql(FileType.NETWORK, "n", USER, granted);
		assertFalse("public visibility must not confer write", sql.contains("visibility"));
		assertTrue("direct membership must be write-typed", sql.contains("permission_type::text = 'WRITE'"));
	}

	/** The shortcut fragment resolves through the target and never references the shortcut's parent. */
	@Test
	public void testShortcutFragmentDelegatesToTargetOnly() {
		Set<UUID> granted = new HashSet<>();
		granted.add(FOLDER);
		String sql = fragmentResolver().readableConditionSql(FileType.SHORTCUT, "s", USER, granted);

		assertTrue(sql.contains("s.target_type = 'NETWORK'"));
		assertTrue(sql.contains("s.target_type = 'FOLDER'"));
		assertFalse("a shortcut must not inherit from its own parent folder", sql.contains("s.parent IN"));
	}

	// ── Solr audience ───────────────────────────────────────────────────────────

	@Test
	public void testEffectiveMembersBucketsByRank() throws SQLException {
		Connection conn = createMock(Connection.class);
		PreparedStatement pst = createMock(PreparedStatement.class);
		ResultSet rs = createMock(ResultSet.class);
		expect(conn.prepareStatement(anyString())).andReturn(pst);
		pst.setObject(anyInt(), anyObject());
		expectLastCall().anyTimes();
		expect(pst.executeQuery()).andReturn(rs);
		expect(rs.next()).andReturn(true);
		expect(rs.getString(1)).andReturn("owner");
		expect(rs.getInt(2)).andReturn(3);
		expect(rs.next()).andReturn(true);
		expect(rs.getString(1)).andReturn("writer");
		expect(rs.getInt(2)).andReturn(2);
		expect(rs.next()).andReturn(true);
		expect(rs.getString(1)).andReturn("reader");
		expect(rs.getInt(2)).andReturn(1);
		expect(rs.next()).andReturn(false);
		rs.close();
		expectLastCall();
		pst.close();
		expectLastCall();
		replay(conn, pst, rs);

		var members = new PostgresFilePermissionResolver(conn).effectiveMembers(NETWORK, FileType.NETWORK);
		assertTrue(members.get(Permissions.ADMIN).contains("owner"));
		assertTrue(members.get(Permissions.WRITE).contains("writer"));
		assertTrue(members.get(Permissions.READ).contains("reader"));
		verify(conn, pst, rs);
	}

	/** Shortcuts have no audience of their own; the target's document carries the access list. */
	@Test
	public void testEffectiveMembersForShortcutIsEmptyWithoutQuerying() throws SQLException {
		Connection conn = createMock(Connection.class);
		replay(conn); // no query expected

		var members = new PostgresFilePermissionResolver(conn).effectiveMembers(SHORTCUT, FileType.SHORTCUT);
		assertTrue(members.get(Permissions.ADMIN).isEmpty());
		assertTrue(members.get(Permissions.WRITE).isEmpty());
		assertTrue(members.get(Permissions.READ).isEmpty());
		verify(conn);
	}
}
