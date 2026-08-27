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
 * document type is reached by, that each clause is pinned to its entity type, and that the public core
 * stays scope-free so UNLISTED items cannot be surfaced by a folder grant.</p>
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

	private String privateFilter(String user, Permissions permission, SearchScope scope) {
		return manager().buildPermissionFilter(user, VisibilityType.PRIVATE, permission, scope);
	}

	@Test
	public void ownershipIsTheOnlyReasonWhenNothingIsGranted() {
		assertEquals("(owner:\"alice\")",
				privateFilter("alice", Permissions.READ, SearchScope.EMPTY));
	}

	@Test
	public void emptySetsEmitNoTermsClauseAtAll() {
		// An empty terms list is a wasted clause on every query by a user with no grants.
		String filter = privateFilter("alice", Permissions.READ,
				new SearchScope(Set.of(), Set.of(), Set.of()));
		assertFalse(filter, filter.contains("{!terms"));
	}

	@Test
	public void aNetworkIsReachedThroughItsParentAndAFolderThroughItsOwnId() {
		String filter = privateFilter("alice", Permissions.READ,
				new SearchScope(Set.of(FOLDER_A), Set.of(), Set.of()));

		// The same granted id appears twice, against two different fields — that asymmetry is the whole
		// point: a network inherits from the folder above it, a folder is granted directly.
		assertTrue(filter,
				filter.contains("(entityType:\"NETWORK\" AND {!terms f=parentUuid v='" + FOLDER_A + "'})"));
		assertTrue(filter,
				filter.contains("(entityType:\"FOLDER\" AND {!terms f=uuid v='" + FOLDER_A + "'})"));
	}

	@Test
	public void everyClauseIsPinnedToAnEntityType() {
		// Folder and shortcut documents also carry parentUuid. An unpinned parent clause would admit any
		// shortcut sitting in a granted folder, bypassing the target half of the shortcut conjunction.
		String filter = privateFilter("alice", Permissions.READ,
				new SearchScope(Set.of(FOLDER_A), Set.of(NETWORK_A), Set.of(SHORTCUT_A)));
		for (String clause : filter.split(" OR ")) {
			if (clause.contains("{!terms")) {
				assertTrue("unpinned terms clause: " + clause, clause.contains("entityType:"));
			}
		}
	}

	@Test
	public void directlyReachableNetworksAreMatchedByTheirOwnId() {
		String filter = privateFilter("alice", Permissions.READ,
				new SearchScope(Set.of(), Set.of(NETWORK_A), Set.of()));
		assertTrue(filter,
				filter.contains("(entityType:\"NETWORK\" AND {!terms f=uuid v='" + NETWORK_A + "'})"));
	}

	@Test
	public void readableShortcutsAreMatchedByTheirOwnId() {
		String filter = privateFilter("alice", Permissions.READ,
				new SearchScope(Set.of(), Set.of(), Set.of(SHORTCUT_A)));
		assertTrue(filter,
				filter.contains("(entityType:\"SHORTCUT\" AND {!terms f=uuid v='" + SHORTCUT_A + "'})"));
	}

	@Test
	public void everyGrantedIdReachesTheFilter() {
		String filter = privateFilter("alice", Permissions.READ,
				new SearchScope(Set.of(FOLDER_A, FOLDER_B), Set.of(), Set.of()));
		assertTrue(filter, filter.contains(FOLDER_A.toString()));
		assertTrue(filter, filter.contains(FOLDER_B.toString()));
	}

	@Test
	public void shortcutsTakeNoPartInAWriteSearch() {
		// Permission cannot be set on a shortcut, and the set is a read-level conjunction.
		String filter = privateFilter("alice", Permissions.WRITE,
				new SearchScope(Set.of(FOLDER_A), Set.of(), Set.of(SHORTCUT_A)));
		assertFalse(filter, filter.contains(SHORTCUT_A.toString()));
		assertTrue(filter, filter.contains(FOLDER_A.toString()));
	}

	@Test
	public void adminIsOwnershipAloneBecauseAFolderGrantNeverConfersIt() {
		assertEquals("owner:\"alice\"",
				privateFilter("alice", Permissions.ADMIN,
						new SearchScope(Set.of(FOLDER_A), Set.of(NETWORK_A), Set.of(SHORTCUT_A))));
	}

	@Test
	public void anonymousReachesNothingOnThePrivateCoreEvenIfAScopeIsSuppliedByMistake() {
		assertEquals("(*:* AND NOT *:*)",
				privateFilter(null, Permissions.READ,
						new SearchScope(Set.of(FOLDER_A), Set.of(NETWORK_A), Set.of(SHORTCUT_A))));
	}

	@Test
	public void aNullScopeIsTreatedAsNoGrantsRatherThanFailing() {
		assertEquals("(owner:\"alice\")", privateFilter("alice", Permissions.READ, null));
	}

	@Test
	public void thePublicCoreIgnoresTheScopeSoAnUnlistedFileStaysUnlisted() {
		// A grant changes who can open an item, never whether it is listed. If a folder clause ever leaks
		// into the public filter, an UNLISTED file becomes searchable by every grantee on its ancestors.
		SearchScope wide = new SearchScope(Set.of(FOLDER_A), Set.of(NETWORK_A), Set.of(SHORTCUT_A));
		String withScope = manager().buildPermissionFilter("alice", VisibilityType.PUBLIC,
				Permissions.READ, wide);
		String withoutScope = manager().buildPermissionFilter("alice", VisibilityType.PUBLIC,
				Permissions.READ, SearchScope.EMPTY);

		assertEquals(withoutScope, withScope);
		assertFalse(withScope, withScope.contains("{!terms"));
		assertTrue(withScope, withScope.contains("NOT visibility:UNLISTED"));
	}
}
