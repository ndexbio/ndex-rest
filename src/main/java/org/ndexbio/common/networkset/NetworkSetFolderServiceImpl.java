package org.ndexbio.common.networkset;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.ndexbio.common.models.dao.DAOFactory;
import org.ndexbio.common.models.dao.DeletedFileIds;
import org.ndexbio.common.models.dao.FolderDAO;
import org.ndexbio.common.models.dao.NetworkDAO;
import org.ndexbio.common.models.dao.ShortcutDAO;
import org.ndexbio.common.util.NdexUUIDFactory;
import org.ndexbio.model.exceptions.BadRequestException;
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
 * Folder-backed implementation of the legacy network set API. See {@link NetworkSetFolderService}.
 */
public class NetworkSetFolderServiceImpl implements NetworkSetFolderService {

	/** Shortcut name used when a member network has no name, matching what the v3 migrator did. */
	static final String UNNAMED_NETWORK = "(unnamed network)";

	private final DAOFactory daoFactory;
	private final FileTypeHandlerFactory fileTypeHandlerFactory;

	public NetworkSetFolderServiceImpl(DAOFactory daoFactory) {
		this(daoFactory, new FileTypeHandlerFactory());
	}

	public NetworkSetFolderServiceImpl(DAOFactory daoFactory, FileTypeHandlerFactory fileTypeHandlerFactory) {
		this.daoFactory = daoFactory;
		this.fileTypeHandlerFactory = fileTypeHandlerFactory;
	}

	@Override
	public NetworkSet getSet(UUID folderId, UUID viewerId, String accessKey) throws SQLException, NdexException {
		try (FolderDAO dao = daoFactory.getFolderDAO()) {
			// Existence first. getFolder() applies no is_deleted filter and isFolderKeyValid()'s seed row
			// does not either, so evaluating the key before this would serve trashed sets — and their
			// members — to anyone holding the key.
			NdexFolder folder = readLiveFolder(dao, folderId);

			MemberView view;
			if (accessKey != null && !accessKey.isEmpty() && dao.accessKeyIsValid(folderId, accessKey)) {
				view = MemberView.KEY_VALID;
			} else if (dao.isReadable(folderId, viewerId)) {
				view = MemberView.OWNER_OR_READABLE;
			} else {
				throw new UnauthorizedOperationException("User doesn't have read access to this network set.");
			}

			return toNetworkSet(folder, readMembers(dao, folderId, view, viewerId));
		} catch (SQLException | NdexException e) {
			throw e;
		} catch (Exception e) {
			throw new NdexException("Failed to read network set " + folderId + ": " + e.getMessage(), e);
		}
	}

	@Override
	public List<NetworkSet> listSetsOfUser(UUID ownerId, UUID viewerId, int offset, int limit, boolean summaryOnly)
			throws SQLException, NdexException {
		List<NetworkSet> result = new ArrayList<>();
		try (FolderDAO dao = daoFactory.getFolderDAO()) {
			// FolderDAO has no offset, so fetch enough rows and page in memory. limit <= 0 means unlimited,
			// preserving the legacy contract.
			int fetchLimit = limit > 0 ? (offset > 0 ? offset + limit : limit) : Integer.MAX_VALUE;
			// includeNested=false: a network set is always created at the owner's home root, so nested
			// folders are not sets and scanning them is what made this endpoint take 30+ seconds on
			// accounts with large folder trees.
			List<NdexFolder> folders = dao.listFoldersOfUser(ownerId, fetchLimit, false);

			// The legacy list had no visibility notion, because network_set had no visibility column.
			// Folders do, so a non-self caller is limited to what they may actually read rather than
			// being handed every folder name and description the owner has.
			if (viewerId == null || !viewerId.equals(ownerId)) {
				List<NdexFolder> readable = new ArrayList<>(folders.size());
				for (NdexFolder folder : folders) {
					if (isReadableQuietly(dao, folder.getExternalId(), viewerId)) {
						readable.add(folder);
					}
				}
				folders = readable;
			}

			int startIdx = Math.max(offset, 0);
			if (startIdx >= folders.size()) {
				return result;
			}
			int endIdx = limit > 0 ? Math.min(folders.size(), startIdx + limit) : folders.size();

			for (NdexFolder folder : folders.subList(startIdx, endIdx)) {
				// listFoldersOfUser does not populate owner_id, but its SQL filters on owneruuid=ownerId,
				// so the requested owner is the folder's owner by construction.
				NetworkSet set = toNetworkSet(folder, List.of());
				set.setOwnerId(ownerId);
				if (!summaryOnly) {
					set.setNetworks(readMembers(dao, folder.getExternalId(),
							MemberView.OWNER_OR_READABLE, viewerId));
				}
				result.add(set);
			}
			return result;
		}
	}

	@Override
	public int countSetsOfUser(UUID ownerId) throws SQLException, NdexException {
		// getRootChildCountsOfUser counts with owneruuid=? AND parent IS NULL AND is_deleted=false, the
		// same predicate listSetsOfUser now pages over, so this count always equals the unpaged length of
		// that list for a self-caller. getOwnedFileCounts counts folders at every depth and would report
		// more sets than the list contains.
		try (FolderDAO dao = daoFactory.getFolderDAO()) {
			return (int) dao.getRootChildCountsOfUser(ownerId).getFolder();
		} catch (SQLException e) {
			throw e;
		} catch (Exception e) {
			throw new NdexException("Failed to count network sets of user " + ownerId + ": " + e.getMessage(), e);
		}
	}

	@Override
	public boolean isSetOwner(UUID folderId, UUID userId) throws SQLException, NdexException {
		try (FolderDAO dao = daoFactory.getFolderDAO()) {
			return dao.isFolderOwner(folderId, userId);
		}
	}

	@Override
	public void createSet(UUID folderId, UUID ownerId, String name, String description)
			throws SQLException, NdexException {
		try (FolderDAO dao = daoFactory.getFolderDAO()) {
			// parent=null puts the set at the owner's home root, where the v3 migrator placed every
			// migrated set and where GET /v3/users/{id}/home surfaces it.
			dao.createFolder(folderId, ownerId, null, name, description);
			dao.commit();
		}
	}

	@Override
	public UpsertOutcome upsertSet(UUID folderId, UUID ownerId, String name, String description)
			throws SQLException, NdexException {
		try (FolderDAO dao = daoFactory.getFolderDAO()) {
			// Resolve the id once, with no is_deleted filter, so a trashed folder counts as "in use".
			// Branching on ownership alone would let two occupied-key states — someone else's live set,
			// and the caller's own trashed set — fall through to a create and fail on the primary key.
			NdexFolder existing;
			try {
				existing = dao.getFolder(folderId, null, null);
			} catch (ObjectNotFoundException e) {
				existing = null;
			} catch (SQLException | NdexException e) {
				throw e;
			} catch (Exception e) {
				throw new NdexException("Failed to resolve network set " + folderId + ": " + e.getMessage(), e);
			}

			if (existing == null) {
				// Legacy upsert: a PUT to an id that holds no set creates one there.
				dao.createFolder(folderId, ownerId, null, name, description);
				dao.commit();
				// createFolder always inserts PRIVATE.
				return new UpsertOutcome(VisibilityType.PRIVATE, true);
			}

			if (existing.getIsDeleted()) {
				// Report it the same way getSet does, rather than as an ownership failure.
				throw new ObjectNotFoundException("Network set", folderId);
			}

			// folder.owneruuid is NOT NULL, and this endpoint is not @PermitAll so the auth filter has
			// already rejected an anonymous caller — both sides of this comparison are non-null.
			if (!existing.getOwner_id().equals(ownerId.toString())) {
				throw new UnauthorizedOperationException("Signed in user is not the owner of this network set.");
			}

			// updateFolder always writes parent, so pass the current value through; otherwise a set
			// nested inside another folder would silently move to the root.
			try {
				dao.updateFolder(folderId, name, existing.getParent(), ownerId, description);
				dao.commit();
				return new UpsertOutcome(dao.getFolderVisibility(folderId), false);
			} catch (SQLException | NdexException e) {
				throw e;
			} catch (Exception e) {
				throw new NdexException("Failed to update network set " + folderId + ": " + e.getMessage(), e);
			}
		}
	}

	@Override
	public DeletedFileIds deleteSet(UUID folderId) throws SQLException, NdexException {
		try (FolderDAO dao = daoFactory.getFolderDAO()) {
			// force=true because a set normally holds shortcuts and would otherwise be rejected as
			// non-empty; permanent=false so the delete is recoverable from the trash.
			DeletedFileIds deleted = dao.deleteFolder(folderId, true, false);
			dao.commit();
			return deleted;
		}
	}

	@Override
	public List<UUID> addMembers(UUID folderId, UUID ownerId, List<UUID> networkIds)
			throws SQLException, NdexException {
		List<UUID> created = new ArrayList<>();
		if (networkIds == null || networkIds.isEmpty()) {
			return created;
		}

		// Validate every posted target before writing anything, so a list containing an id the caller
		// cannot read creates no shortcuts at all. This is the rule the legacy v2 Swagger stated but no
		// v2 code enforced. Shared with ShortcutServiceV3 through the handler so "valid target" has one
		// definition.
		try {
			AbstractFileTypeHandler handler = fileTypeHandlerFactory.getHandler(FileType.NETWORK);
			for (UUID networkId : networkIds) {
				handler.validateShortcutTarget(networkId, ownerId);
			}
		} catch (ObjectNotFoundException e) {
			// No such network: 404 is the more precise answer than "bad request".
			throw e;
		} catch (Exception e) {
			// The network exists but cannot be a member (not readable by this caller). That is a client
			// error, so surface 400 — a bare NdexException would map to 500.
			throw new BadRequestException("Cannot add network to network set " + folderId + ": " + e.getMessage());
		}

		try (FolderDAO folderDao = daoFactory.getFolderDAO()) {
			Set<UUID> existing = new LinkedHashSet<>(readMembers(folderDao, folderId,
					MemberView.UNFILTERED, ownerId));

			try (ShortcutDAO shortcutDao = daoFactory.getShortcutDAO();
					NetworkDAO networkDao = daoFactory.getNetworkDAO()) {
				for (UUID networkId : new LinkedHashSet<>(networkIds)) {
					// createShortcut has no uniqueness guard, so skipping ids already in the set is what
					// keeps a repeated POST from stacking duplicate shortcuts.
					if (existing.contains(networkId)) {
						continue;
					}
					UUID shortcutId = NdexUUIDFactory.INSTANCE.createNewNDExUUID();
					shortcutDao.createShortcut(shortcutId, ownerId, folderId,
							shortcutNameFor(networkDao, networkId), networkId, FileType.NETWORK);
					created.add(shortcutId);
					existing.add(networkId);
				}
				if (!created.isEmpty()) {
					shortcutDao.commit();
				}
			}
			return created;
		} catch (SQLException | NdexException e) {
			throw e;
		} catch (Exception e) {
			throw new NdexException("Failed to add members to network set " + folderId + ": " + e.getMessage(), e);
		}
	}

	@Override
	public RemovedMembers removeMembers(UUID folderId, UUID ownerId, List<UUID> networkIds)
			throws SQLException, NdexException {
		List<UUID> deletedShortcuts = new ArrayList<>();
		List<UUID> movedNetworks = new ArrayList<>();
		if (networkIds == null || networkIds.isEmpty()) {
			return new RemovedMembers(deletedShortcuts, movedNetworks);
		}

		try (FolderDAO folderDao = daoFactory.getFolderDAO()) {
			// One listing resolves both shapes a member can take: shortcuts pointing at a network, and
			// networks parented directly in the folder.
			Map<UUID, List<UUID>> shortcutsByTarget = new HashMap<>();
			Set<UUID> realChildren = new LinkedHashSet<>();
			for (FileItemSummary item : folderDao.listItemsInFolder(folderId, true, FileType.NETWORK)) {
				if (item.getType() == FileType.NETWORK) {
					realChildren.add(item.getUuid());
				} else if (item.getType() == FileType.SHORTCUT) {
					UUID target = networkShortcutTarget(item);
					if (target != null) {
						shortcutsByTarget.computeIfAbsent(target, k -> new ArrayList<>()).add(item.getUuid());
					}
				}
			}

			try (ShortcutDAO shortcutDao = daoFactory.getShortcutDAO();
					NetworkDAO networkDao = daoFactory.getNetworkDAO()) {
				for (UUID networkId : new LinkedHashSet<>(networkIds)) {
					List<UUID> shortcutIds = shortcutsByTarget.get(networkId);
					if (shortcutIds != null) {
						// Physical delete: a soft delete would put a trash entry in the owner's way for
						// every network they removed from a set, and the legacy network_set_member row
						// deletion had no recovery path either.
						for (UUID shortcutId : shortcutIds) {
							shortcutDao.deleteShortcut(shortcutId, true);
							deletedShortcuts.add(shortcutId);
						}
					} else if (realChildren.contains(networkId) && networkDao.isAdmin(networkId, ownerId)) {
						// A network living in the folder is data, not a reference, so "remove" means move
						// it out — to home root — rather than delete it. One the caller does not own is
						// left alone; a set owner cannot relocate someone else's network.
						networkDao.setNetworkFolder(networkId, null);
						movedNetworks.add(networkId);
					}
				}
				if (!deletedShortcuts.isEmpty()) {
					shortcutDao.commit();
				}
				if (!movedNetworks.isEmpty()) {
					networkDao.commit();
				}
			}
			return new RemovedMembers(deletedShortcuts, movedNetworks);
		} catch (SQLException | NdexException e) {
			throw e;
		} catch (Exception e) {
			throw new NdexException("Failed to remove members from network set " + folderId + ": " + e.getMessage(), e);
		}
	}

	@Override
	public String getAccessKey(UUID folderId) throws SQLException, NdexException {
		try (FolderDAO dao = daoFactory.getFolderDAO()) {
			// Deliberately getFolderAccessKey, never enableFolderAccessKey: this is a read, and the
			// pre-3.0.3 version minted a key as a side effect of a GET.
			return dao.getFolderAccessKey(folderId);
		} catch (SQLException | NdexException e) {
			throw e;
		} catch (Exception e) {
			throw new NdexException("Failed to read access key of network set " + folderId, e);
		}
	}

	@Override
	public String enableAccessKey(UUID folderId, UUID ownerId) throws Exception {
		// Through the folder handler so /v2/networkset/{id}/accesskey and POST /v3/files/sharing/share
		// share one implementation, including its owner check and commit.
		return fileTypeHandlerFactory.getHandler(FileType.FOLDER).enableAccessKey(folderId, ownerId);
	}

	@Override
	public void disableAccessKey(UUID folderId, UUID ownerId) throws Exception {
		fileTypeHandlerFactory.getHandler(FileType.FOLDER).disableAccessKey(folderId, ownerId);
	}

	/**
	 * Reads a folder and rejects one that is only soft-deleted. {@code getFolder} applies no
	 * {@code is_deleted} filter, so without this a trashed set would read as live.
	 */
	private static NdexFolder readLiveFolder(FolderDAO dao, UUID folderId) throws Exception {
		NdexFolder folder = dao.getFolder(folderId, null, null);
		if (folder == null || folder.getIsDeleted()) {
			throw new ObjectNotFoundException("Network set", folderId);
		}
		return folder;
	}

	/** {@code isReadable} throws when the row is missing; for filtering a list that just means "no". */
	private static boolean isReadableQuietly(FolderDAO dao, UUID folderId, UUID viewerId) throws SQLException {
		try {
			return dao.isReadable(folderId, viewerId);
		} catch (ObjectNotFoundException e) {
			return false;
		}
	}

	/**
	 * The member network ids of a set, normalized to the legacy v2 shape: networks parented in the
	 * folder plus the targets of its live network shortcuts, deduplicated in first-seen order. Folders
	 * and folder-target shortcuts are not members.
	 *
	 * <p>{@code compact=true} is required — the DAO only populates a shortcut's {@code target} and
	 * {@code target_status} attributes in compact form.
	 */
	private List<UUID> readMembers(FolderDAO dao, UUID folderId, MemberView view, UUID viewerId)
			throws SQLException, NdexException {
		// KEY_VALID uses the DAO's key-accessible listing, which reduces shortcut children to the
		// same-owner NETWORK shortcuts a validated key unlocks. The other two views enumerate everything
		// and filter afterwards — see below for why the DAO's readable listing is not usable here.
		List<FileItemSummary> items = view == MemberView.KEY_VALID
				? dao.listItemsInFolderKeyFiltered(folderId, true, FileType.NETWORK)
				: dao.listItemsInFolder(folderId, true, FileType.NETWORK);

		Set<UUID> memberIds = new LinkedHashSet<>();
		for (FileItemSummary item : items) {
			if (item.getType() == FileType.NETWORK) {
				memberIds.add(item.getUuid());
			} else if (item.getType() == FileType.SHORTCUT) {
				UUID target = networkShortcutTarget(item);
				if (target != null) {
					memberIds.add(target);
				}
			}
		}

		if (view != MemberView.OWNER_OR_READABLE || memberIds.isEmpty()) {
			return new ArrayList<>(memberIds);
		}

		// Filter by what the viewer may read of each MEMBER NETWORK, which is the legacy v2 contract:
		// "members filtered to the networks the caller can read".
		//
		// listReadableItemsInFolder cannot express this. Its shortcut predicate filters on the
		// SHORTCUT's own visibility, and createShortcut always inserts PRIVATE — so a PUBLIC set of
		// PUBLIC networks would return an empty member list to any non-owner, which is exactly the
		// "display a public network set" case these endpoints exist to serve. Shortcut visibility is
		// independent of its target's, so the target has to be consulted directly.
		List<UUID> readable = new ArrayList<>(memberIds.size());
		try (NetworkDAO networkDao = daoFactory.getNetworkDAO()) {
			for (UUID networkId : memberIds) {
				if (isNetworkReadableQuietly(networkDao, networkId, viewerId)) {
					readable.add(networkId);
				}
			}
		} catch (SQLException | NdexException e) {
			throw e;
		} catch (Exception e) {
			throw new NdexException("Failed to filter members of network set " + folderId, e);
		}
		return readable;
	}

	/** {@code isReadable} throws when the network is gone; for a member list that just means "not a member". */
	private static boolean isNetworkReadableQuietly(NetworkDAO dao, UUID networkId, UUID viewerId)
			throws SQLException {
		try {
			return dao.isReadable(networkId, viewerId);
		} catch (ObjectNotFoundException e) {
			return false;
		}
	}

	/**
	 * The target network of a shortcut item, or null when it is not a live network shortcut — a
	 * folder-target shortcut, or one whose target is trashed or gone, is not a member.
	 */
	private static UUID networkShortcutTarget(FileItemSummary item) {
		Map<String, Object> attrs = item.getAttributes();
		if (attrs == null) {
			return null;
		}
		if (!FileType.NETWORK.toString().equals(attrs.get("target_type"))) {
			return null;
		}
		if (!ShortcutTargetStatus.ACTIVE.toString().equals(attrs.get("target_status"))) {
			return null;
		}
		Object target = attrs.get("target");
		if (target instanceof UUID uuid) {
			return uuid;
		}
		return target == null ? null : UUID.fromString(target.toString());
	}

	/** Names a member shortcut after its target network, as the v3 migrator did. */
	private static String shortcutNameFor(NetworkDAO networkDao, UUID networkId)
			throws SQLException, NdexException {
		// network.name is nullable, hence the fallback. Errors are not swallowed: every target was already
		// validated as readable, so a failure here is a real fault, not a missing network.
		String name = networkDao.getNetworkName(networkId);
		return name == null || name.isBlank() ? UNNAMED_NETWORK : name;
	}

	/** Maps a folder onto the legacy NetworkSet shape. */
	static NetworkSet toNetworkSet(NdexFolder folder, List<UUID> members) {
		NetworkSet set = new NetworkSet();
		set.setExternalId(folder.getExternalId());
		set.setCreationTime(folder.getCreationTime());
		set.setModificationTime(folder.getModificationTime());
		set.setName(folder.getName());
		set.setDescription(folder.getDescription());
		if (folder.getOwner_id() != null) {
			set.setOwnerId(UUID.fromString(folder.getOwner_id()));
		}
		// showcased, doi and properties have no folder equivalent and are left at their defaults.
		set.setNetworks(new ArrayList<>(members));
		return set;
	}
}
