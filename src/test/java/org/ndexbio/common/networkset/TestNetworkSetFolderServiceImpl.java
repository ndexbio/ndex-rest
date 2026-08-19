package org.ndexbio.common.networkset;

import static org.easymock.EasyMock.anyObject;
import static org.easymock.EasyMock.createMock;
import static org.easymock.EasyMock.createNiceMock;
import static org.easymock.EasyMock.expect;
import static org.easymock.EasyMock.expectLastCall;
import static org.easymock.EasyMock.replay;
import static org.easymock.EasyMock.verify;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.junit.Test;
import org.ndexbio.common.models.dao.DAOFactory;
import org.ndexbio.common.models.dao.DeletedFileIds;
import org.ndexbio.common.models.dao.FolderDAO;
import org.ndexbio.common.models.dao.NetworkDAO;
import org.ndexbio.common.models.dao.ShortcutDAO;
import org.ndexbio.common.networkset.NetworkSetFolderService.RemovedMembers;
import org.ndexbio.common.networkset.NetworkSetFolderService.UpsertOutcome;
import org.ndexbio.model.exceptions.NdexException;
import org.ndexbio.model.exceptions.ObjectNotFoundException;
import org.ndexbio.model.exceptions.UnauthorizedOperationException;
import org.ndexbio.model.object.FileItemSummary;
import org.ndexbio.model.object.FileType;
import org.ndexbio.model.object.NdexFolder;
import org.ndexbio.model.object.NetworkSet;
import org.ndexbio.model.object.ShortcutTargetStatus;
import org.ndexbio.model.object.network.VisibilityType;
import org.ndexbio.rest.services.v3.files.handlers.AbstractFileTypeHandler;
import org.ndexbio.rest.services.v3.files.handlers.FileTypeHandlerFactory;

/**
 * Unit tests for the folder-backed network set service. These cover the behaviors a v2 client can
 * observe but that no folder-level test would catch: how folder children are normalized into the
 * legacy {@code networks} array, and the add/remove member semantics.
 */
public class TestNetworkSetFolderServiceImpl {

	private static final UUID SET_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");
	private static final UUID OWNER_ID = UUID.fromString("22222222-2222-2222-2222-222222222222");
	private static final UUID NET_A = UUID.fromString("aaaaaaaa-0000-0000-0000-000000000001");
	private static final UUID NET_B = UUID.fromString("bbbbbbbb-0000-0000-0000-000000000002");
	private static final UUID SHORTCUT_A = UUID.fromString("cccccccc-0000-0000-0000-000000000003");

	// ── helpers ──────────────────────────────────────────────────────────────

	private static NdexFolder folder(UUID id, UUID parent, boolean deleted) {
		NdexFolder f = new NdexFolder();
		f.setExternalId(id);
		f.setName("My Set");
		f.setDescription("a set");
		f.setParent(parent);
		f.setOwner_id(OWNER_ID.toString());
		f.setIsDeleted(deleted);
		f.setCreationTime(new Timestamp(1000L));
		f.setModificationTime(new Timestamp(2000L));
		return f;
	}

	private static FileItemSummary networkItem(UUID id) {
		return new FileItemSummary(id, FileType.NETWORK, "net", new Timestamp(0L), null, null);
	}

	private static FileItemSummary shortcutItem(UUID shortcutId, UUID target, String targetType,
			ShortcutTargetStatus status) {
		Map<String, Object> attrs = new HashMap<>();
		attrs.put("target_type", targetType);
		attrs.put("target", target);
		attrs.put("target_status", status.toString());
		return new FileItemSummary(shortcutId, FileType.SHORTCUT, "shortcut", new Timestamp(0L), null, attrs);
	}

	/** A factory whose getFolderDAO()/getShortcutDAO()/getNetworkDAO() hand back the given mocks. */
	private static DAOFactory factoryOf(FolderDAO folderDao, ShortcutDAO shortcutDao, NetworkDAO networkDao)
			throws Exception {
		DAOFactory factory = createNiceMock(DAOFactory.class);
		if (folderDao != null) {
			expect(factory.getFolderDAO()).andReturn(folderDao).anyTimes();
		}
		if (shortcutDao != null) {
			expect(factory.getShortcutDAO()).andReturn(shortcutDao).anyTimes();
		}
		if (networkDao != null) {
			expect(factory.getNetworkDAO()).andReturn(networkDao).anyTimes();
		}
		replay(factory);
		return factory;
	}

	/** A NetworkDAO that reports every network readable — the member filter is not what is under test. */
	private static NetworkDAO allNetworksReadable() throws Exception {
		NetworkDAO dao = createNiceMock(NetworkDAO.class);
		expect(dao.isReadable(anyObject(UUID.class), anyObject())).andReturn(true).anyTimes();
		replay(dao);
		return dao;
	}

	/** A handler factory whose NETWORK handler accepts every target. */
	private static FileTypeHandlerFactory acceptAllTargets() throws Exception {
		AbstractFileTypeHandler handler = createMock(AbstractFileTypeHandler.class);
		handler.validateShortcutTarget(anyObject(UUID.class), anyObject(UUID.class));
		expectLastCall().anyTimes();
		replay(handler);
		FileTypeHandlerFactory factory = createMock(FileTypeHandlerFactory.class);
		expect(factory.getHandler(FileType.NETWORK)).andReturn(handler).anyTimes();
		replay(factory);
		return factory;
	}

	// ── member normalization ─────────────────────────────────────────────────

	@Test
	public void membersAreUnionOfRealChildrenAndActiveNetworkShortcuts() throws Exception {
		FolderDAO folderDao = createNiceMock(FolderDAO.class);
		expect(folderDao.getFolder(SET_ID, null, null)).andReturn(folder(SET_ID, null, false));
		expect(folderDao.accessKeyIsValid(SET_ID, null)).andReturn(false).anyTimes();
		expect(folderDao.isReadable(SET_ID, OWNER_ID)).andReturn(true);
		expect(folderDao.listItemsInFolder(SET_ID, true, FileType.NETWORK))
				.andReturn(List.of(networkItem(NET_A),
						shortcutItem(SHORTCUT_A, NET_B, "NETWORK", ShortcutTargetStatus.ACTIVE)));
		replay(folderDao);

		NetworkSet set = new NetworkSetFolderServiceImpl(factoryOf(folderDao, null, allNetworksReadable()))
				.getSet(SET_ID, OWNER_ID, null);

		// A real child contributes its own id; a shortcut contributes its TARGET, not the shortcut id.
		assertEquals(List.of(NET_A, NET_B), set.getNetworks());
		assertEquals("My Set", set.getName());
		assertEquals(OWNER_ID, set.getOwnerId());
	}

	@Test
	public void membersExcludeFolderShortcutsAndInactiveTargets() throws Exception {
		UUID folderTarget = UUID.randomUUID();
		FolderDAO folderDao = createNiceMock(FolderDAO.class);
		expect(folderDao.getFolder(SET_ID, null, null)).andReturn(folder(SET_ID, null, false));
		expect(folderDao.accessKeyIsValid(SET_ID, null)).andReturn(false).anyTimes();
		expect(folderDao.isReadable(SET_ID, OWNER_ID)).andReturn(true);
		expect(folderDao.listItemsInFolder(SET_ID, true, FileType.NETWORK))
				.andReturn(List.of(
						shortcutItem(UUID.randomUUID(), folderTarget, "FOLDER", ShortcutTargetStatus.ACTIVE),
						shortcutItem(UUID.randomUUID(), NET_A, "NETWORK", ShortcutTargetStatus.IN_TRASH),
						shortcutItem(UUID.randomUUID(), NET_B, "NETWORK", ShortcutTargetStatus.DELETED),
						new FileItemSummary(UUID.randomUUID(), FileType.SHORTCUT, "no attrs",
								new Timestamp(0L), null, null)));
		replay(folderDao);

		NetworkSet set = new NetworkSetFolderServiceImpl(factoryOf(folderDao, null, allNetworksReadable()))
				.getSet(SET_ID, OWNER_ID, null);

		assertTrue("only live network shortcuts are members", set.getNetworks().isEmpty());
	}

	@Test
	public void membersAreDeduplicatedInFirstSeenOrder() throws Exception {
		FolderDAO folderDao = createNiceMock(FolderDAO.class);
		expect(folderDao.getFolder(SET_ID, null, null)).andReturn(folder(SET_ID, null, false));
		expect(folderDao.accessKeyIsValid(SET_ID, null)).andReturn(false).anyTimes();
		expect(folderDao.isReadable(SET_ID, OWNER_ID)).andReturn(true);
		// Two shortcuts to the same network, plus that network also parented in the folder.
		expect(folderDao.listItemsInFolder(SET_ID, true, FileType.NETWORK))
				.andReturn(List.of(
						shortcutItem(UUID.randomUUID(), NET_B, "NETWORK", ShortcutTargetStatus.ACTIVE),
						networkItem(NET_A),
						shortcutItem(UUID.randomUUID(), NET_B, "NETWORK", ShortcutTargetStatus.ACTIVE),
						shortcutItem(UUID.randomUUID(), NET_A, "NETWORK", ShortcutTargetStatus.ACTIVE)));
		replay(folderDao);

		NetworkSet set = new NetworkSetFolderServiceImpl(factoryOf(folderDao, null, allNetworksReadable()))
				.getSet(SET_ID, OWNER_ID, null);

		assertEquals(List.of(NET_B, NET_A), set.getNetworks());
	}

	@Test
	public void legacyOnlyFieldsAreNotFabricated() throws Exception {
		FolderDAO folderDao = createNiceMock(FolderDAO.class);
		expect(folderDao.getFolder(SET_ID, null, null)).andReturn(folder(SET_ID, null, false));
		expect(folderDao.accessKeyIsValid(SET_ID, null)).andReturn(false).anyTimes();
		expect(folderDao.isReadable(SET_ID, OWNER_ID)).andReturn(true);
		expect(folderDao.listItemsInFolder(SET_ID, true, FileType.NETWORK))
				.andReturn(List.of());
		replay(folderDao);

		NetworkSet set = new NetworkSetFolderServiceImpl(factoryOf(folderDao, null, allNetworksReadable()))
				.getSet(SET_ID, OWNER_ID, null);

		// No folder equivalent exists for these, so they stay at their defaults rather than being invented.
		assertFalse(set.isShowcased());
		assertNull(set.getDoi());
		assertTrue(set.getProperties().isEmpty());
	}

	@Test
	public void membersAreFilteredByTheTargetNetworksReadabilityNotTheShortcuts() throws Exception {
		// The legacy v2 contract is "members filtered to the networks the caller can read". That cannot be
		// delegated to listReadableItemsInFolder: its shortcut predicate filters on the SHORTCUT's own
		// visibility, and createShortcut always inserts PRIVATE, so a PUBLIC set of PUBLIC networks would
		// return no members to a non-owner — the exact case these endpoints exist to serve. So the set is
		// enumerated unfiltered and each target network's readability is consulted directly.
		UUID viewerId = UUID.randomUUID();
		FolderDAO folderDao = createNiceMock(FolderDAO.class);
		expect(folderDao.getFolder(SET_ID, null, null)).andReturn(folder(SET_ID, null, false));
		expect(folderDao.accessKeyIsValid(SET_ID, null)).andReturn(false).anyTimes();
		expect(folderDao.isReadable(SET_ID, viewerId)).andReturn(true);
		expect(folderDao.listItemsInFolder(SET_ID, true, FileType.NETWORK))
				.andReturn(List.of(
						shortcutItem(SHORTCUT_A, NET_A, "NETWORK", ShortcutTargetStatus.ACTIVE),
						shortcutItem(UUID.randomUUID(), NET_B, "NETWORK", ShortcutTargetStatus.ACTIVE)));
		replay(folderDao);

		NetworkDAO networkDao = createNiceMock(NetworkDAO.class);
		expect(networkDao.isReadable(NET_A, viewerId)).andReturn(true);
		expect(networkDao.isReadable(NET_B, viewerId)).andReturn(false);
		replay(networkDao);

		NetworkSet set = new NetworkSetFolderServiceImpl(factoryOf(folderDao, null, networkDao))
				.getSet(SET_ID, viewerId, null);

		assertEquals("the unreadable member must be filtered out", List.of(NET_A), set.getNetworks());
		verify(networkDao);
	}

	@Test
	public void aMemberWhoseNetworkIsGoneIsNotAMember() throws Exception {
		FolderDAO folderDao = createNiceMock(FolderDAO.class);
		expect(folderDao.getFolder(SET_ID, null, null)).andReturn(folder(SET_ID, null, false));
		expect(folderDao.accessKeyIsValid(SET_ID, null)).andReturn(false).anyTimes();
		expect(folderDao.isReadable(SET_ID, OWNER_ID)).andReturn(true);
		expect(folderDao.listItemsInFolder(SET_ID, true, FileType.NETWORK))
				.andReturn(List.of(shortcutItem(SHORTCUT_A, NET_A, "NETWORK", ShortcutTargetStatus.ACTIVE)));
		replay(folderDao);

		NetworkDAO networkDao = createNiceMock(NetworkDAO.class);
		// isReadable throws when the row is gone; for a member list that just means "not a member".
		expect(networkDao.isReadable(NET_A, OWNER_ID)).andThrow(new ObjectNotFoundException("Network", NET_A));
		replay(networkDao);

		NetworkSet set = new NetworkSetFolderServiceImpl(factoryOf(folderDao, null, networkDao))
				.getSet(SET_ID, OWNER_ID, null);

		assertTrue(set.getNetworks().isEmpty());
	}

	// ── read authorization ───────────────────────────────────────────────────

	@Test
	public void aValidAccessKeySelectsTheKeyFilteredMemberView() throws Exception {
		FolderDAO folderDao = createNiceMock(FolderDAO.class);
		expect(folderDao.getFolder(SET_ID, null, null)).andReturn(folder(SET_ID, null, false));
		expect(folderDao.accessKeyIsValid(SET_ID, "goodkey")).andReturn(true);
		// The key-filtered listing is the one that must be used: it reduces to the members the key
		// actually unlocks rather than returning every member.
		expect(folderDao.listItemsInFolderKeyFiltered(SET_ID, true, FileType.NETWORK))
				.andReturn(List.of(networkItem(NET_A)));
		replay(folderDao);

		NetworkSet set = new NetworkSetFolderServiceImpl(factoryOf(folderDao, null, null))
				.getSet(SET_ID, null, "goodkey");

		assertEquals(List.of(NET_A), set.getNetworks());
		verify(folderDao);
	}

	@Test
	public void aTrashedSetIsNotFoundEvenWithAValidAccessKey() throws Exception {
		// Regression guard: getFolder applies no is_deleted filter and isFolderKeyValid's seed row does
		// not either, so checking the key before existence would serve trashed sets and their members.
		FolderDAO folderDao = createNiceMock(FolderDAO.class);
		expect(folderDao.getFolder(SET_ID, null, null)).andReturn(folder(SET_ID, null, true));
		replay(folderDao);

		NetworkSetFolderServiceImpl service = new NetworkSetFolderServiceImpl(factoryOf(folderDao, null, null));

		assertThrows(ObjectNotFoundException.class, () -> service.getSet(SET_ID, null, "goodkey"));
	}

	@Test
	public void anUnreadableSetIsUnauthorized() throws Exception {
		FolderDAO folderDao = createNiceMock(FolderDAO.class);
		expect(folderDao.getFolder(SET_ID, null, null)).andReturn(folder(SET_ID, null, false));
		expect(folderDao.accessKeyIsValid(SET_ID, null)).andReturn(false).anyTimes();
		expect(folderDao.isReadable(SET_ID, null)).andReturn(false);
		replay(folderDao);

		NetworkSetFolderServiceImpl service = new NetworkSetFolderServiceImpl(factoryOf(folderDao, null, null));

		assertThrows(UnauthorizedOperationException.class, () -> service.getSet(SET_ID, null, null));
	}

	// ── update preserves parent ──────────────────────────────────────────────

	@Test
	public void updatePreservesTheCurrentParent() throws Exception {
		UUID parentId = UUID.randomUUID();
		FolderDAO folderDao = createNiceMock(FolderDAO.class);
		expect(folderDao.getFolder(SET_ID, null, null)).andReturn(folder(SET_ID, parentId, false));
		// updateFolder always writes parent, so a nested set would silently move to the root if the
		// current value were not read back and passed through.
		folderDao.updateFolder(SET_ID, "new name", parentId, OWNER_ID, "new desc");
		expectLastCall().once();
		folderDao.commit();
		expectLastCall().once();
		expect(folderDao.getFolderVisibility(SET_ID)).andReturn(VisibilityType.PRIVATE);
		replay(folderDao);

		UpsertOutcome outcome = new NetworkSetFolderServiceImpl(factoryOf(folderDao, null, null))
				.upsertSet(SET_ID, OWNER_ID, "new name", "new desc");

		assertEquals(VisibilityType.PRIVATE, outcome.visibility());
		assertFalse("an existing set is updated, not created", outcome.created());
		verify(folderDao);
	}

	// ── upsertSet resolves the state of the target id ─────────────────────────
	//
	// "Not the owner" covers two states that already occupy the primary key: someone else's live set, and
	// the caller's own set sitting in the trash. Creating in either case raises a constraint violation that
	// surfaces as a 500, so upsertSet has to tell all three states apart.

	@Test
	public void upsertCreatesWhenTheIdIsUnused() throws Exception {
		FolderDAO folderDao = createMock(FolderDAO.class);
		expect(folderDao.getFolder(SET_ID, null, null))
				.andThrow(new ObjectNotFoundException("Folder", SET_ID));
		expect(folderDao.createFolder(SET_ID, OWNER_ID, null, "a name", "a desc")).andReturn(null);
		folderDao.commit();
		expectLastCall().once();
		folderDao.close();
		expectLastCall().anyTimes();
		replay(folderDao);

		UpsertOutcome outcome = new NetworkSetFolderServiceImpl(factoryOf(folderDao, null, null))
				.upsertSet(SET_ID, OWNER_ID, "a name", "a desc");

		assertTrue("an unused id is created at, preserving the legacy upsert", outcome.created());
		// createFolder always inserts PRIVATE, so that is what the caller must index with.
		assertEquals(VisibilityType.PRIVATE, outcome.visibility());
		verify(folderDao);
	}

	@Test
	public void upsertRefusesASetOwnedBySomeoneElse() throws Exception {
		UUID otherOwner = UUID.randomUUID();
		NdexFolder theirs = folder(SET_ID, null, false);
		theirs.setOwner_id(otherOwner.toString());

		// A strict mock with no create/update expectation: attempting either would fail the test, which is
		// the point — the old code created here and got a primary-key violation surfaced as a 500.
		FolderDAO folderDao = createMock(FolderDAO.class);
		expect(folderDao.getFolder(SET_ID, null, null)).andReturn(theirs);
		folderDao.close();
		expectLastCall().anyTimes();
		replay(folderDao);

		NetworkSetFolderServiceImpl service = new NetworkSetFolderServiceImpl(factoryOf(folderDao, null, null));

		assertThrows(UnauthorizedOperationException.class,
				() -> service.upsertSet(SET_ID, OWNER_ID, "a name", null));
		verify(folderDao);
	}

	@Test
	public void upsertReportsATrashedSetAsNotFound() throws Exception {
		// isFolderOwner filters is_deleted=false, so a trashed set the caller owns used to look "not mine"
		// and fall through to a create. Report it the way getSet does instead of claiming 401.
		FolderDAO folderDao = createMock(FolderDAO.class);
		expect(folderDao.getFolder(SET_ID, null, null)).andReturn(folder(SET_ID, null, true));
		folderDao.close();
		expectLastCall().anyTimes();
		replay(folderDao);

		NetworkSetFolderServiceImpl service = new NetworkSetFolderServiceImpl(factoryOf(folderDao, null, null));

		assertThrows(ObjectNotFoundException.class,
				() -> service.upsertSet(SET_ID, OWNER_ID, "a name", null));
		verify(folderDao);
	}

	@Test
	public void upsertLeavesAnOmittedDescriptionUnchanged() throws Exception {
		FolderDAO folderDao = createNiceMock(FolderDAO.class);
		expect(folderDao.getFolder(SET_ID, null, null)).andReturn(folder(SET_ID, null, false));
		// A null description is passed straight through; the DAO omits the column, so the stored value
		// survives. An empty string would be written and would clear it — that asymmetry is documented on
		// the endpoint because the legacy endpoint cleared on null instead.
		folderDao.updateFolder(SET_ID, "new name", null, OWNER_ID, null);
		expectLastCall().once();
		expect(folderDao.getFolderVisibility(SET_ID)).andReturn(VisibilityType.PUBLIC);
		replay(folderDao);

		UpsertOutcome outcome = new NetworkSetFolderServiceImpl(factoryOf(folderDao, null, null))
				.upsertSet(SET_ID, OWNER_ID, "new name", null);

		assertEquals(VisibilityType.PUBLIC, outcome.visibility());
		verify(folderDao);
	}

	// ── addMembers ───────────────────────────────────────────────────────────

	@Test
	public void addMembersCreatesNothingWhenAnyTargetFailsValidation() throws Exception {
		AbstractFileTypeHandler handler = createMock(AbstractFileTypeHandler.class);
		handler.validateShortcutTarget(NET_A, OWNER_ID);
		expectLastCall().once();
		handler.validateShortcutTarget(NET_B, OWNER_ID);
		expectLastCall().andThrow(new NdexException("Target network does not exist or is not accessible."));
		replay(handler);
		FileTypeHandlerFactory handlerFactory = createMock(FileTypeHandlerFactory.class);
		expect(handlerFactory.getHandler(FileType.NETWORK)).andReturn(handler).anyTimes();
		replay(handlerFactory);

		// A strict mock with no expectations: any shortcut write would fail the test.
		ShortcutDAO shortcutDao = createMock(ShortcutDAO.class);
		replay(shortcutDao);
		FolderDAO folderDao = createNiceMock(FolderDAO.class);
		replay(folderDao);

		NetworkSetFolderServiceImpl service = new NetworkSetFolderServiceImpl(
				factoryOf(folderDao, shortcutDao, null), handlerFactory);

		assertThrows(NdexException.class, () -> service.addMembers(SET_ID, OWNER_ID, List.of(NET_A, NET_B)));
		verify(shortcutDao);
	}

	@Test
	public void addMembersSkipsNetworksAlreadyInTheSet() throws Exception {
		FolderDAO folderDao = createNiceMock(FolderDAO.class);
		// NET_A is already referenced by a shortcut; NET_B is new.
		expect(folderDao.listItemsInFolder(SET_ID, true, FileType.NETWORK))
				.andReturn(List.of(shortcutItem(SHORTCUT_A, NET_A, "NETWORK", ShortcutTargetStatus.ACTIVE)));
		replay(folderDao);

		NetworkDAO networkDao = createNiceMock(NetworkDAO.class);
		expect(networkDao.getNetworkName(NET_B)).andReturn("Network B").anyTimes();
		replay(networkDao);

		ShortcutDAO shortcutDao = createMock(ShortcutDAO.class);
		// createShortcut has no uniqueness guard, so only the genuinely new member may be created.
		expect(shortcutDao.createShortcut(anyObject(UUID.class), anyObject(UUID.class), anyObject(UUID.class),
				anyObject(String.class), anyObject(UUID.class), anyObject(FileType.class))).andReturn(null).once();
		shortcutDao.commit();
		expectLastCall().once();
		shortcutDao.close();
		expectLastCall().anyTimes();
		replay(shortcutDao);

		List<UUID> created = new NetworkSetFolderServiceImpl(
				factoryOf(folderDao, shortcutDao, networkDao), acceptAllTargets())
				.addMembers(SET_ID, OWNER_ID, List.of(NET_A, NET_B));

		assertEquals(1, created.size());
		verify(shortcutDao);
	}

	// ── removeMembers ────────────────────────────────────────────────────────

	@Test
	public void removeMembersPhysicallyDeletesShortcuts() throws Exception {
		FolderDAO folderDao = createNiceMock(FolderDAO.class);
		expect(folderDao.listItemsInFolder(SET_ID, true, FileType.NETWORK))
				.andReturn(List.of(shortcutItem(SHORTCUT_A, NET_A, "NETWORK", ShortcutTargetStatus.ACTIVE)));
		replay(folderDao);

		ShortcutDAO shortcutDao = createMock(ShortcutDAO.class);
		// permanent=true: a soft delete would leave a trash entry for every network removed from a set,
		// and the legacy network_set_member row deletion had no recovery path either.
		shortcutDao.deleteShortcut(SHORTCUT_A, true);
		expectLastCall().once();
		shortcutDao.commit();
		expectLastCall().once();
		shortcutDao.close();
		expectLastCall().anyTimes();
		replay(shortcutDao);

		NetworkDAO networkDao = createNiceMock(NetworkDAO.class);
		replay(networkDao);

		RemovedMembers removed = new NetworkSetFolderServiceImpl(factoryOf(folderDao, shortcutDao, networkDao))
				.removeMembers(SET_ID, OWNER_ID, List.of(NET_A));

		assertEquals(List.of(SHORTCUT_A), removed.deletedShortcutIds());
		assertTrue(removed.movedNetworkIds().isEmpty());
		verify(shortcutDao);
	}

	@Test
	public void removeMembersMovesARealChildNetworkToHome() throws Exception {
		FolderDAO folderDao = createNiceMock(FolderDAO.class);
		expect(folderDao.listItemsInFolder(SET_ID, true, FileType.NETWORK))
				.andReturn(List.of(networkItem(NET_A)));
		replay(folderDao);

		NetworkDAO networkDao = createMock(NetworkDAO.class);
		expect(networkDao.isAdmin(NET_A, OWNER_ID)).andReturn(true);
		// A network living in the folder is data, not a reference, so "remove" moves it out rather than
		// deleting it. parent=null lands it at the owner's home root.
		networkDao.setNetworkFolder(NET_A, null);
		expectLastCall().once();
		networkDao.commit();
		expectLastCall().once();
		networkDao.close();
		expectLastCall().anyTimes();
		replay(networkDao);

		ShortcutDAO shortcutDao = createNiceMock(ShortcutDAO.class);
		replay(shortcutDao);

		RemovedMembers removed = new NetworkSetFolderServiceImpl(factoryOf(folderDao, shortcutDao, networkDao))
				.removeMembers(SET_ID, OWNER_ID, List.of(NET_A));

		assertEquals(List.of(NET_A), removed.movedNetworkIds());
		assertTrue(removed.deletedShortcutIds().isEmpty());
		verify(networkDao);
	}

	@Test
	public void removeMembersLeavesARealChildTheCallerDoesNotOwn() throws Exception {
		FolderDAO folderDao = createNiceMock(FolderDAO.class);
		expect(folderDao.listItemsInFolder(SET_ID, true, FileType.NETWORK))
				.andReturn(List.of(networkItem(NET_A)));
		replay(folderDao);

		NetworkDAO networkDao = createMock(NetworkDAO.class);
		expect(networkDao.isAdmin(NET_A, OWNER_ID)).andReturn(false);
		networkDao.close();
		expectLastCall().anyTimes();
		// No setNetworkFolder and no commit expected: a set owner must not relocate someone else's network.
		replay(networkDao);

		ShortcutDAO shortcutDao = createNiceMock(ShortcutDAO.class);
		replay(shortcutDao);

		RemovedMembers removed = new NetworkSetFolderServiceImpl(factoryOf(folderDao, shortcutDao, networkDao))
				.removeMembers(SET_ID, OWNER_ID, List.of(NET_A));

		assertTrue(removed.movedNetworkIds().isEmpty());
		assertTrue(removed.deletedShortcutIds().isEmpty());
		verify(networkDao);
	}

	// ── listing and paging ───────────────────────────────────────────────────

	@Test
	public void listAppliesOffsetAndLimitInMemory() throws Exception {
		List<NdexFolder> folders = new ArrayList<>();
		for (int i = 0; i < 5; i++) {
			folders.add(folder(UUID.randomUUID(), null, false));
		}
		FolderDAO folderDao = createNiceMock(FolderDAO.class);
		// FolderDAO has no offset, so the service fetches offset+limit rows and pages in memory.
		expect(folderDao.listFoldersOfUser(OWNER_ID, 4)).andReturn(folders);
		replay(folderDao);

		List<NetworkSet> sets = new NetworkSetFolderServiceImpl(factoryOf(folderDao, null, null))
				.listSetsOfUser(OWNER_ID, OWNER_ID, 2, 2, true);

		assertEquals(2, sets.size());
		assertEquals(folders.get(2).getExternalId(), sets.get(0).getExternalId());
		assertEquals(folders.get(3).getExternalId(), sets.get(1).getExternalId());
	}

	@Test
	public void listReturnsEmptyWhenOffsetIsPastTheEnd() throws Exception {
		FolderDAO folderDao = createNiceMock(FolderDAO.class);
		expect(folderDao.listFoldersOfUser(OWNER_ID, 12)).andReturn(List.of(folder(SET_ID, null, false)));
		replay(folderDao);

		List<NetworkSet> sets = new NetworkSetFolderServiceImpl(factoryOf(folderDao, null, null))
				.listSetsOfUser(OWNER_ID, OWNER_ID, 10, 2, true);

		assertTrue(sets.isEmpty());
	}

	@Test
	public void listTreatsNonPositiveLimitAsUnlimited() throws Exception {
		FolderDAO folderDao = createNiceMock(FolderDAO.class);
		// limit <= 0 means "no limit", the legacy contract.
		expect(folderDao.listFoldersOfUser(OWNER_ID, Integer.MAX_VALUE))
				.andReturn(List.of(folder(SET_ID, null, false)));
		replay(folderDao);

		List<NetworkSet> sets = new NetworkSetFolderServiceImpl(factoryOf(folderDao, null, null))
				.listSetsOfUser(OWNER_ID, OWNER_ID, 0, 0, true);

		assertEquals(1, sets.size());
		verify(folderDao);
	}

	@Test
	public void summaryOnlyListDoesNotLoadMembers() throws Exception {
		FolderDAO folderDao = createMock(FolderDAO.class);
		expect(folderDao.listFoldersOfUser(OWNER_ID, Integer.MAX_VALUE))
				.andReturn(List.of(folder(SET_ID, null, false)));
		folderDao.close();
		expectLastCall().anyTimes();
		// No listItemsInFolder expectation: loading members under summary=true would fail this test.
		replay(folderDao);

		List<NetworkSet> sets = new NetworkSetFolderServiceImpl(factoryOf(folderDao, null, null))
				.listSetsOfUser(OWNER_ID, OWNER_ID, 0, 0, true);

		// The field is present-but-empty rather than absent: NetworkSet's constructor pre-allocates it.
		assertTrue(sets.get(0).getNetworks().isEmpty());
		verify(folderDao);
	}

	@Test
	public void listLimitsANonSelfCallerToReadableFolders() throws Exception {
		UUID viewerId = UUID.randomUUID();
		NdexFolder readable = folder(SET_ID, null, false);
		NdexFolder hidden = folder(UUID.randomUUID(), null, false);

		FolderDAO folderDao = createNiceMock(FolderDAO.class);
		expect(folderDao.listFoldersOfUser(OWNER_ID, Integer.MAX_VALUE))
				.andReturn(List.of(readable, hidden));
		expect(folderDao.isReadable(readable.getExternalId(), viewerId)).andReturn(true);
		// Folders have a visibility that network_set never had, so a non-self caller must not be handed
		// the names and descriptions of the owner's PRIVATE folders.
		expect(folderDao.isReadable(hidden.getExternalId(), viewerId)).andReturn(false);
		replay(folderDao);

		List<NetworkSet> sets = new NetworkSetFolderServiceImpl(factoryOf(folderDao, null, null))
				.listSetsOfUser(OWNER_ID, viewerId, 0, 0, true);

		assertEquals(1, sets.size());
		assertEquals(SET_ID, sets.get(0).getExternalId());
	}

	@Test
	public void countSetsOfUserDelegatesToCountRootFoldersOfUser() throws Exception {
		FolderDAO folderDao = createMock(FolderDAO.class);
		expect(folderDao.countRootFoldersOfUser(OWNER_ID)).andReturn(7);
		folderDao.close();
		expectLastCall().anyTimes();
		replay(folderDao);

		int count = new NetworkSetFolderServiceImpl(factoryOf(folderDao, null, null))
				.countSetsOfUser(OWNER_ID);

		assertEquals(7, count);
		verify(folderDao);
	}

	// ── delete ───────────────────────────────────────────────────────────────

	@Test
	public void deleteForcesAndTrashesTheWholeSubtree() throws Exception {
		DeletedFileIds expected = new DeletedFileIds(List.of(SET_ID), List.of(), List.of(SHORTCUT_A));
		FolderDAO folderDao = createMock(FolderDAO.class);
		// force=true because a set normally holds shortcuts and would be rejected as non-empty;
		// permanent=false so the delete stays recoverable from the trash.
		expect(folderDao.deleteFolder(SET_ID, true, false)).andReturn(expected);
		folderDao.commit();
		expectLastCall().once();
		folderDao.close();
		expectLastCall().anyTimes();
		replay(folderDao);

		DeletedFileIds deleted = new NetworkSetFolderServiceImpl(factoryOf(folderDao, null, null))
				.deleteSet(SET_ID);

		assertEquals(List.of(SHORTCUT_A), deleted.shortcuts());
		verify(folderDao);
	}
}
