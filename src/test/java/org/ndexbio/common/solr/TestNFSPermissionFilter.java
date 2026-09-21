package org.ndexbio.common.solr;

import static org.easymock.EasyMock.createMock;
import static org.easymock.EasyMock.createNiceMock;
import static org.easymock.EasyMock.expect;
import static org.easymock.EasyMock.replay;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.lang.reflect.Field;
import java.util.Set;
import java.util.UUID;

import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;
import org.ndexbio.common.models.dao.SearchScope;
import org.ndexbio.model.object.Permissions;
import org.ndexbio.model.object.network.VisibilityType;
import org.ndexbio.rest.Configuration;

/**
 * How the search permission filter is built from a {@link SearchScope}.
 *
 * <p>Search stores no permission state, so this filter <em>is</em> the authorization decision — there is
 * no later gate to catch a mistake here. These cases pin the parts that are easy to get wrong: which id a
 * document type is reached by, that each clause is pinned to its entity type, and the two placements that
 * a single core makes load-bearing.</p>
 *
 * <p>Those two placements used to be enforced by the topology rather than by the expression. With a
 * {@code public-nfs}/{@code private-nfs} pair, an UNLISTED file was kept from a folder grantee because the
 * scope clauses only ever ran against a core holding no UNLISTED documents, and an owner found their own
 * UNLISTED file because the public core's filter admitted everything they owned. One core has to say both
 * of those out loud, which is what {@link #aFolderGrantDoesNotListSomeoneElsesUnlistedFile()} and
 * {@link #anOwnerStillFindsTheirOwnUnlistedFiles()} exist to hold.</p>
 */
public class TestNFSPermissionFilter {

	private static final UUID FOLDER_A = UUID.fromString("11111111-1111-1111-1111-111111111111");
	private static final UUID FOLDER_B = UUID.fromString("22222222-2222-2222-2222-222222222222");
	private static final UUID NETWORK_A = UUID.fromString("33333333-3333-3333-3333-333333333333");
	private static final UUID SHORTCUT_A = UUID.fromString("44444444-4444-4444-4444-444444444444");

	private static Configuration savedInstance;

	@BeforeClass
	public static void setUpClass() throws Exception {
		Configuration mockConfig = createMock(Configuration.class);
		expect(mockConfig.getSolrURL()).andReturn("http://localhost:8983/solr").anyTimes();
		replay(mockConfig);

		Field instanceField = Configuration.class.getDeclaredField("INSTANCE");
		instanceField.setAccessible(true);
		savedInstance = (Configuration) instanceField.get(null);
		instanceField.set(null, mockConfig);
	}

	@AfterClass
	public static void tearDownClass() {
		Configuration.setInstance(savedInstance);
	}

	/** Any concrete subclass will do — the builders under test live on the shared base. */
	private GlobalNetworkIndexManager manager() {
		SolrClientWrapper wrapper = createNiceMock(SolrClientWrapper.class);
		replay(wrapper);
		return new GlobalNetworkIndexManager(wrapper);
	}

	private String filter(String user, Permissions permission, SearchScope scope) {
		return manager().buildPermissionFilter(user, permission, scope);
	}

	private String partition(VisibilityType visibilityType) {
		return manager().buildPartitionFilter(visibilityType);
	}

	// ── the shape of the filter, arm by arm ─────────────────────────────────────

	@Test
	public void anonymousReachesPublicDocumentsAndNothingElse() {
		// The positive form matters: the predecessor was "(*:* NOT visibility:UNLISTED)", which was only
		// ever correct against a core holding nothing but PUBLIC and UNLISTED. Against one core it
		// matches every PRIVATE document too.
		assertEquals("(visibility:PUBLIC)", filter(null, Permissions.READ, SearchScope.EMPTY));
		assertEquals("(visibility:PUBLIC)", filter(null, null, SearchScope.EMPTY));
	}

	@Test
	public void anonymousReachesNothingPrivateEvenIfAScopeIsSuppliedByMistake() {
		String f = filter(null, Permissions.READ,
				new SearchScope(Set.of(FOLDER_A), Set.of(NETWORK_A), Set.of(SHORTCUT_A)));
		assertEquals("(visibility:PUBLIC)", f);
		assertFalse(f, f.contains("{!terms"));
	}

	@Test
	public void anAuthenticatedReadReachesPublicDocumentsAndTheirOwn() {
		assertEquals("(visibility:PUBLIC) OR (owner:\"alice\")",
				filter("alice", Permissions.READ, SearchScope.EMPTY));
	}

	@Test
	public void aWriteSearchDropsThePublicArmBecausePublicVisibilityGrantsNoEdit() {
		assertEquals("(owner:\"bob\")", filter("bob", Permissions.WRITE, SearchScope.EMPTY));
	}

	@Test
	public void adminIsOwnershipAloneBecauseAFolderGrantNeverConfersIt() {
		assertEquals("(owner:\"alice\")",
				filter("alice", Permissions.ADMIN,
						new SearchScope(Set.of(FOLDER_A), Set.of(NETWORK_A), Set.of(SHORTCUT_A))));
	}

	@Test
	public void aPermissionThatIsNeitherReadWriteNorAdminReachesOnlyPublicDocuments() {
		assertEquals("(visibility:PUBLIC)", filter("alice", Permissions.MEMBER, SearchScope.EMPTY));
		assertEquals("(visibility:PUBLIC)", filter("alice", Permissions.GROUPADMIN, SearchScope.EMPTY));
	}

	// ── the two placements a single core makes load-bearing ─────────────────────

	@Test
	public void anOwnerStillFindsTheirOwnUnlistedFiles() {
		// The owner clause is deliberately NOT pinned to a visibility. It has to cover owned PUBLIC,
		// owned UNLISTED and owned PRIVATE, which is what the old public core's filter did. Pinning it
		// inside the PRIVATE arm would silently drop the owner's own unlisted files.
		String f = filter("alice", Permissions.READ, new SearchScope(Set.of(FOLDER_A), Set.of(), Set.of()));
		int owner = f.indexOf("(owner:\"alice\")");
		int privateArm = f.indexOf("visibility:PRIVATE");
		assertTrue(f, owner >= 0);
		assertTrue("the owner clause must sit outside the PRIVATE arm: " + f, owner < privateArm);
	}

	@Test
	public void aFolderGrantDoesNotListSomeoneElsesUnlistedFile() {
		// A grant changes who can OPEN an item, never whether it is LISTED. Pinning the terms group to
		// visibility:PRIVATE is the whole of that rule: unpinned, every grantee on an ancestor folder
		// would see an UNLISTED file sitting in it.
		String f = filter("alice", Permissions.READ,
				new SearchScope(Set.of(FOLDER_A), Set.of(NETWORK_A), Set.of(SHORTCUT_A)));

		int pin = f.indexOf("visibility:PRIVATE AND (");
		assertTrue("the terms group must be pinned to PRIVATE: " + f, pin >= 0);
		// Every terms clause has to sit after the pin. Checking the first occurrence is enough: they are
		// emitted contiguously, so one escaping the pin would be the earliest.
		assertTrue("a terms clause escaped the PRIVATE pin: " + f, f.indexOf("{!terms") > pin);
	}

	// ── what each id set reaches ────────────────────────────────────────────────

	@Test
	public void ownershipIsTheOnlyReasonWhenNothingIsGranted() {
		assertEquals("(visibility:PUBLIC) OR (owner:\"alice\")",
				filter("alice", Permissions.READ, SearchScope.EMPTY));
	}

	@Test
	public void emptySetsEmitNoTermsClauseAtAll() {
		// An empty terms list is a wasted clause on every query by a user with no grants, and
		// "visibility:PRIVATE AND ()" is a parse error.
		String f = filter("alice", Permissions.READ, new SearchScope(Set.of(), Set.of(), Set.of()));
		assertFalse(f, f.contains("{!terms"));
		assertFalse(f, f.contains("visibility:PRIVATE"));
	}

	@Test
	public void aNetworkIsReachedThroughItsParentAndAFolderThroughItsOwnId() {
		// The same granted id appears twice, against two different fields — that asymmetry is the whole
		// point: a network inherits from the folder above it, a folder is granted directly.
		String f = filter("alice", Permissions.READ, new SearchScope(Set.of(FOLDER_A), Set.of(), Set.of()));

		assertTrue(f, f.contains("(entityType:\"NETWORK\" AND {!terms f=parentUuid v='" + FOLDER_A + "'})"));
		assertTrue(f, f.contains("(entityType:\"FOLDER\" AND {!terms f=uuid v='" + FOLDER_A + "'})"));
	}

	@Test
	public void everyClauseIsPinnedToAnEntityType() {
		// Folder and shortcut documents also carry parentUuid. An unpinned parent clause would admit any
		// shortcut sitting in a granted folder, bypassing the target half of the shortcut conjunction.
		String f = filter("alice", Permissions.READ,
				new SearchScope(Set.of(FOLDER_A), Set.of(NETWORK_A), Set.of(SHORTCUT_A)));
		for (String clause : f.split(" OR ")) {
			if (clause.contains("{!terms")) {
				assertTrue("unpinned terms clause: " + clause, clause.contains("entityType:"));
			}
		}
	}

	@Test
	public void directlyReachableNetworksAreMatchedByTheirOwnId() {
		String f = filter("alice", Permissions.READ, new SearchScope(Set.of(), Set.of(NETWORK_A), Set.of()));
		assertTrue(f, f.contains("(entityType:\"NETWORK\" AND {!terms f=uuid v='" + NETWORK_A + "'})"));
	}

	@Test
	public void readableShortcutsAreMatchedByTheirOwnId() {
		String f = filter("alice", Permissions.READ, new SearchScope(Set.of(), Set.of(), Set.of(SHORTCUT_A)));
		assertTrue(f, f.contains("(entityType:\"SHORTCUT\" AND {!terms f=uuid v='" + SHORTCUT_A + "'})"));
	}

	@Test
	public void everyGrantedIdReachesTheFilter() {
		String f = filter("alice", Permissions.READ,
				new SearchScope(Set.of(FOLDER_A, FOLDER_B), Set.of(), Set.of()));
		assertTrue(f, f.contains(FOLDER_A.toString()));
		assertTrue(f, f.contains(FOLDER_B.toString()));
	}

	@Test
	public void shortcutsTakeNoPartInAWriteSearch() {
		// Permission cannot be set on a shortcut, and the set is a read-level conjunction.
		String f = filter("alice", Permissions.WRITE,
				new SearchScope(Set.of(FOLDER_A), Set.of(), Set.of(SHORTCUT_A)));
		assertFalse(f, f.contains(SHORTCUT_A.toString()));
		assertTrue(f, f.contains(FOLDER_A.toString()));
	}

	@Test
	public void aNullScopeIsTreatedAsNoGrantsRatherThanFailing() {
		assertEquals("(visibility:PUBLIC) OR (owner:\"alice\")",
				filter("alice", Permissions.READ, null));
	}

	@Test
	public void theFirstTermsClauseCarriesNoLeadingOperator() {
		// The group is assembled standalone and then embedded, so a clause separator that was
		// unconditional would leave "visibility:PRIVATE AND ( OR (entityType:...))".
		String f = filter("alice", Permissions.READ, new SearchScope(Set.of(), Set.of(NETWORK_A), Set.of()));
		assertFalse(f, f.contains("AND ( OR "));
		assertTrue(f, f.contains("AND ((entityType:"));
	}

	// ── the narrowing filter, which is a separate expression ────────────────────

	@Test
	public void omittingVisibilityNarrowsNothing() {
		assertEquals("", partition(null));
	}

	@Test
	public void publicNarrowsOnTheOldPublicCorePartitionRatherThanTheLiteralValue() {
		// visibility=PUBLIC selected the public-nfs CORE, which physically held PUBLIC and UNLISTED
		// documents alike. Narrowing on the literal field value instead drops the caller's own unlisted
		// files, which that core always returned to them.
		assertEquals(" AND (visibility:(PUBLIC OR UNLISTED))", partition(VisibilityType.PUBLIC));
	}

	@Test
	public void privateNarrowsOnPrivateWherePartitionAndFieldValueCoincide() {
		assertEquals(" AND (visibility:PRIVATE)", partition(VisibilityType.PRIVATE));
	}
}
