package org.ndexbio.common.models.dao.postgresql;

import static org.easymock.EasyMock.*;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
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
import org.easymock.CaptureType;
import org.junit.Test;
import org.ndexbio.common.models.dao.SearchScope;
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
	private static final UUID USER       = UUID.fromString("44444444-4444-4444-4444-444444444444");
	private static final UUID OTHER_USER = UUID.fromString("55555555-5555-5555-5555-555555555555");

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
	public void testShortcutOwnedByCallerDelegatesToNetworkTarget() throws SQLException {
		Connection conn = createNiceMock(Connection.class);
		PreparedStatement lookup = createNiceMock(PreparedStatement.class);
		ResultSet lookupRs = createNiceMock(ResultSet.class);
		PreparedStatement delegate = createNiceMock(PreparedStatement.class);
		ResultSet delegateRs = createNiceMock(ResultSet.class);

		Capture<String> firstSql = newCapture();
		expect(conn.prepareStatement(capture(firstSql))).andReturn(lookup);
		expect(lookup.executeQuery()).andReturn(lookupRs);
		expect(lookupRs.next()).andReturn(true);
		expect(lookupRs.getObject(1)).andReturn(NETWORK);   // target
		expect(lookupRs.getString(2)).andReturn("NETWORK"); // target_type
		expect(lookupRs.getString(3)).andReturn("PRIVATE"); // visibility
		expect(lookupRs.getObject(4)).andReturn(USER);      // owneruuid — caller owns it
		expect(lookupRs.getObject(5)).andReturn(FOLDER);    // parent

		// containment satisfied by ownership, so the only further query is the target delegation
		expect(conn.prepareStatement(anyString())).andReturn(delegate);
		expect(delegate.executeQuery()).andReturn(delegateRs);
		expect(delegateRs.next()).andReturn(true);
		expect(delegateRs.getInt(1)).andReturn(2);
		replay(conn, lookup, lookupRs, delegate, delegateRs);

		assertEquals(Permissions.WRITE,
				new PostgresFilePermissionResolver(conn).effectiveShortcutPermission(SHORTCUT, USER));

		// The lookup must now read the shortcut's own visibility/owner/parent: readability is a
		// conjunction of the shortcut being reachable AND its target being reachable, so that search,
		// fetch-by-id and listing all give the same answer.
		assertTrue("shortcut resolution must read its own parent for the containment arm",
				firstSql.getValue().contains("parent"));
		assertTrue(firstSql.getValue().contains("visibility"));
		assertTrue(firstSql.getValue().contains("owneruuid"));
		verify(conn, lookup, lookupRs, delegate, delegateRs);
	}

	/**
	 * The containment arm: a shortcut the caller cannot reach is denied even when its target is
	 * readable. Without this, knowing an id would reveal an entry inside a folder they cannot open.
	 */
	@Test
	public void testUnreachableShortcutDeniedEvenWhenTargetReadable() throws SQLException {
		Connection conn = createNiceMock(Connection.class);
		PreparedStatement lookup = createNiceMock(PreparedStatement.class);
		ResultSet lookupRs = createNiceMock(ResultSet.class);
		PreparedStatement parentQ = createNiceMock(PreparedStatement.class);
		ResultSet parentRs = createNiceMock(ResultSet.class);

		expect(conn.prepareStatement(anyString())).andReturn(lookup);
		expect(lookup.executeQuery()).andReturn(lookupRs);
		expect(lookupRs.next()).andReturn(true);
		expect(lookupRs.getObject(1)).andReturn(NETWORK);
		expect(lookupRs.getString(2)).andReturn("NETWORK");
		expect(lookupRs.getString(3)).andReturn("PRIVATE"); // not public
		expect(lookupRs.getObject(4)).andReturn(OTHER_USER); // owned by someone else
		expect(lookupRs.getObject(5)).andReturn(FOLDER);     // parent the caller cannot read

		// the parent-folder check resolves to no access -> denied without ever querying the target
		expect(conn.prepareStatement(anyString())).andReturn(parentQ);
		expect(parentQ.executeQuery()).andReturn(parentRs);
		expect(parentRs.next()).andReturn(true);
		expect(parentRs.getInt(1)).andReturn(0); // RANK_NONE
		replay(conn, lookup, lookupRs, parentQ, parentRs);

		assertNull(new PostgresFilePermissionResolver(conn).effectiveShortcutPermission(SHORTCUT, USER));
		verify(conn, lookup, lookupRs, parentQ, parentRs);
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
	public void testShortcutFragmentIsAConjunctionOfContainmentAndTarget() {
		Set<UUID> granted = new HashSet<>();
		granted.add(FOLDER);
		String sql = fragmentResolver().readableConditionSql(FileType.SHORTCUT, "s", USER, granted);

		// Target reachability — a shortcut yields whatever its target yields.
		assertTrue(sql.contains("s.target_type = 'NETWORK'"));
		assertTrue(sql.contains("s.target_type = 'FOLDER'"));

		// Containment — reaching the target is not enough; the shortcut itself must be reachable, or a
		// caller holding the id could read an entry inside a folder they cannot open and learn its name,
		// target and parent. Tested as "the parent folder is readable" rather than "the parent is in the
		// granted set", so a shortcut in a PUBLIC folder stays visible to an anonymous caller, whose
		// granted set is empty.
		assertTrue("shortcut reachability must gate on its parent folder",
				sql.contains("pf.\"UUID\" = s.parent"));
		assertTrue("the two arms must be ANDed, not ORed", sql.contains(" AND ( EXISTS"));
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

	// ── search scope resolution ─────────────────────────────────────────────────

	/** A connection whose single query returns no rows, capturing the SQL it was handed. */
	private static Connection connYieldingNoRows(Capture<String> sqlCapture) throws SQLException {
		Connection conn = createMock(Connection.class);
		PreparedStatement pst = createMock(PreparedStatement.class);
		ResultSet rs = createMock(ResultSet.class);
		expect(conn.prepareStatement(capture(sqlCapture))).andReturn(pst).anyTimes();
		pst.setObject(anyInt(), anyObject());
		expectLastCall().anyTimes();
		expect(pst.executeQuery()).andReturn(rs).anyTimes();
		expect(rs.next()).andReturn(false).anyTimes();
		rs.close();
		expectLastCall().anyTimes();
		pst.close();
		expectLastCall().anyTimes();
		replay(conn, pst, rs);
		return conn;
	}

	/**
	 * The two arms a folder-containment test cannot express: a direct per-network grant, and a network
	 * referenced from a granted folder by a same-owner shortcut whose target lives elsewhere.
	 */
	@Test
	public void testReachableNetworkIdsCoversDirectGrantsAndTheSameOwnerShortcutSeed() throws SQLException {
		Capture<String> sql = newCapture();
		Set<UUID> granted = new HashSet<>();
		granted.add(FOLDER);

		new PostgresFilePermissionResolver(connYieldingNoRows(sql)).reachableNetworkIds(USER, granted, Permissions.READ);

		String q = sql.getValue();
		assertTrue("direct per-network grants must be one arm", q.contains("user_network_membership"));
		assertTrue("the same-owner shortcut seed must be the other arm", q.contains("shortcut"));
		assertTrue("the seed must not let a folder owner widen access to a network they do not own",
				q.contains("owneruuid"));
	}

	/**
	 * A WRITE search must narrow the direct-grant arm too. The folder arm is already narrowed, so
	 * leaving this one open returned networks the caller could only read — the filter claimed WRITE and
	 * delivered READ.
	 */
	@Test
	public void testReachableNetworkIdsNarrowsToWriteGrantsForAWriteSearch() throws SQLException {
		Capture<String> sql = newCapture();
		Set<UUID> granted = new HashSet<>();
		granted.add(FOLDER);

		new PostgresFilePermissionResolver(connYieldingNoRows(sql))
				.reachableNetworkIds(USER, granted, Permissions.WRITE);

		String q = sql.getValue();
		assertTrue("a WRITE search must match write grants only",
				q.contains("m.permission_type::text = 'WRITE'"));
		assertFalse("a WRITE search must not admit read grants",
				q.contains("m.permission_type::text IN ('READ','WRITE')"));
	}

	/** ...and a READ search still admits both, since WRITE implies READ. */
	@Test
	public void testReachableNetworkIdsAdmitsBothLevelsForAReadSearch() throws SQLException {
		Capture<String> sql = newCapture();

		new PostgresFilePermissionResolver(connYieldingNoRows(sql))
				.reachableNetworkIds(USER, Collections.<UUID>emptySet(), Permissions.READ);

		assertTrue("a READ search admits read and write grants alike",
				sql.getValue().contains("m.permission_type::text IN ('READ','WRITE')"));
	}

	/** The whole scope must be resolved at one level — the two arms cannot disagree. */
	@Test
	public void testSearchScopeNarrowsBothArmsTogetherForWrite() throws SQLException {
		Capture<String> sql = Capture.newInstance(CaptureType.ALL);

		new PostgresFilePermissionResolver(connYieldingNoRows(sql))
				.searchScope(USER, Permissions.WRITE);

		String folderArm = sql.getValues().stream()
				.filter(q -> q.contains("WITH RECURSIVE sub")).findFirst().orElseThrow();
		String networkArm = sql.getValues().stream()
				.filter(q -> q.contains("user_network_membership m")).findFirst().orElseThrow();

		assertTrue("folder arm narrowed", folderArm.contains("upper(fp.permission) = 'WRITE'"));
		assertTrue("direct-grant arm narrowed", networkArm.contains("m.permission_type::text = 'WRITE'"));
	}

	/** Anonymous callers reach nothing on the private core, so no query should be issued at all. */
	@Test
	public void testSearchResolversShortCircuitForAnonymous() throws SQLException {
		Connection conn = createMock(Connection.class);
		replay(conn); // no query expected

		PostgresFilePermissionResolver r = new PostgresFilePermissionResolver(conn);
		assertTrue(r.reachableNetworkIds(null, Collections.<UUID>emptySet(), Permissions.READ).isEmpty());
		assertTrue(r.readableShortcutIds(null, Collections.<UUID>emptySet()).isEmpty());
		assertEquals(SearchScope.EMPTY, r.searchScope(null, Permissions.READ));
		verify(conn);
	}

	/**
	 * The shortcut id query must apply the same conjunction the fetch and listing paths do, and its
	 * target arm must recognise the same-owner seed — otherwise a shortcut is judged unreadable while
	 * {@code reachableNetworkIds} says its target is readable, and search contradicts itself.
	 */
	@Test
	public void testReadableShortcutIdsIsAConjunctionWhoseTargetArmKnowsTheSeed() throws SQLException {
		Capture<String> sql = newCapture();
		Set<UUID> granted = new HashSet<>();
		granted.add(FOLDER);

		new PostgresFilePermissionResolver(connYieldingNoRows(sql)).readableShortcutIds(USER, granted);

		String q = sql.getValue();
		assertTrue("the shortcut itself must be reachable", q.contains("s.parent IN"));
		assertTrue("its target must be reachable too", q.contains("s.target_type = 'NETWORK'"));
		assertTrue("the target arm must include the same-owner shortcut seed",
				q.contains("sc2.owneruuid = n.owneruuid"));
	}

	/** All three sets come back together, resolved at the level the caller is about to search for. */
	@Test
	public void testSearchScopeResolvesAllThreeSetsAtTheRequestedLevel() throws SQLException {
		Capture<String> sql = Capture.newInstance(CaptureType.ALL);

		SearchScope scope = new PostgresFilePermissionResolver(connYieldingNoRows(sql))
				.searchScope(USER, Permissions.WRITE);

		assertNotNull(scope);
		assertTrue(scope.grantedFolderIds().isEmpty());
		assertTrue(scope.reachableNetworkIds().isEmpty());
		assertTrue(scope.readableShortcutIds().isEmpty());

		// A WRITE search must not be built from a READ-level granted-folder set.
		String hierarchyQuery = sql.getValues().stream()
				.filter(q -> q.contains("WITH RECURSIVE sub"))
				.findFirst().orElseThrow();
		assertTrue("a WRITE scope must filter grants to WRITE",
				hierarchyQuery.contains("upper(fp.permission) = 'WRITE'"));
		assertFalse("a WRITE scope must not admit READ grants",
				hierarchyQuery.contains("('READ','WRITE')"));
	}
}
