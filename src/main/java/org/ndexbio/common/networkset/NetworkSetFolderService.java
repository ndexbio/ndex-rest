package org.ndexbio.common.networkset;

import java.sql.SQLException;
import java.util.List;
import java.util.UUID;

import org.ndexbio.common.models.dao.DeletedFileIds;
import org.ndexbio.model.exceptions.NdexException;
import org.ndexbio.model.object.NetworkSet;
import org.ndexbio.model.object.network.VisibilityType;

/**
 * Backs the legacy {@code /v2/networkset} API with the v3 folder model.
 *
 * <p>The NDEx network set feature is retired: a network set id <em>is</em> a folder id
 * ({@code FOLDER_SHORTCUT_SPECIFICATION.md} — "the networksetid is equivalent to the folderid and will
 * still continue to work"). The v3 migration converted every set into a folder with the same UUID,
 * holding a shortcut per member network. This service performs the folder and shortcut operations and
 * maps the result back onto the legacy {@link NetworkSet} representation, so v2 clients keep working
 * unchanged while the storage underneath is the live v3 model. The frozen {@code network_set} tables
 * are never read or written.
 *
 * <p>Three legacy fields have no folder equivalent and are therefore not round-tripped:
 * {@code showcased}, {@code doi} and {@code properties}. See the {@code /v2/networkset} Swagger
 * descriptions.
 *
 * <p><b>DAO lifecycle:</b> every method opens the DAOs it needs, commits each one it wrote to, and
 * closes them before returning. Because {@code getXDAO()} hands back a fresh connection at
 * {@code autoCommit=false}, a method touching more than one DAO is <em>not</em> atomic across them —
 * the same trade-off {@code FileServiceV3.transferNetworksOwnership} already makes. Solr index
 * bookkeeping stays with the caller, since {@code createFileIndex}/{@code deleteFileIndex} are
 * {@code protected} on {@code NdexService}.
 */
public interface NetworkSetFolderService {

	/** How much of a set's membership a given caller may see. */
	enum MemberView {
		/** Owner or a reader: members restricted to the networks the viewer may read. */
		OWNER_OR_READABLE,
		/** A valid access key was presented: the members that key actually unlocks. */
		KEY_VALID,
		/** No filtering — every member. */
		UNFILTERED
	}

	/**
	 * What {@link #removeMembers} did, so the caller can do the matching index bookkeeping. Not
	 * surfaced to clients: {@code DELETE /v2/networkset/{id}/members} stays a 204 with no body.
	 *
	 * @param deletedShortcutIds shortcuts physically removed — their Solr docs must be deleted
	 * @param movedNetworkIds    networks reparented to the owner's home root. These need <em>no</em>
	 *                           re-index: only folder and shortcut docs carry {@code parentUuid}
	 *                           ({@code FolderIndexManager} / {@code ShortcutIndexManager}), so a
	 *                           network's document is unaffected by a move. Reported for the caller's
	 *                           logging and tests.
	 */
	record RemovedMembers(List<UUID> deletedShortcutIds, List<UUID> movedNetworkIds) {}

	/**
	 * Reads a set as a {@link NetworkSet}.
	 *
	 * <p>Resolves existence before authorization, so a trashed set is a 404 rather than being served
	 * to an access-key holder: {@code isFolderKeyValid}'s seed row and {@code getFolder} both ignore
	 * {@code is_deleted}, so checking the key first would hand out soft-deleted sets.
	 *
	 * @throws org.ndexbio.model.exceptions.ObjectNotFoundException      no such live set
	 * @throws org.ndexbio.model.exceptions.UnauthorizedOperationException caller may not read it
	 */
	NetworkSet getSet(UUID folderId, UUID viewerId, String accessKey) throws SQLException, NdexException;

	/**
	 * The sets owned by {@code ownerId} — every folder they own, at any depth.
	 *
	 * @param viewerId    the caller; when it differs from {@code ownerId} the result is limited to the
	 *                    folders that caller may read
	 * @param limit       {@code <= 0} means unlimited
	 * @param summaryOnly when true, set headers are returned without loading members
	 */
	List<NetworkSet> listSetsOfUser(UUID ownerId, UUID viewerId, int offset, int limit, boolean summaryOnly)
			throws SQLException, NdexException;

	/**
	 * How many sets {@code ownerId} owns. Uses the same predicate as
	 * {@link #listSetsOfUser}, so the count always agrees with that list's unpaged length.
	 */
	int countSetsOfUser(UUID ownerId) throws SQLException, NdexException;

	/** Whether {@code userId} owns this set. */
	boolean isSetOwner(UUID folderId, UUID userId) throws SQLException, NdexException;

	/**
	 * Creates a set as a folder at {@code ownerId}'s home root.
	 *
	 * @param folderId the id to create it under — minted by the caller for {@code POST}, or supplied by
	 *                 the client for the {@code PUT} upsert
	 */
	void createSet(UUID folderId, UUID ownerId, String name, String description)
			throws SQLException, NdexException;

	/** What {@link #upsertSet} did, so the caller can index with the right visibility and mode. */
	record UpsertOutcome(VisibilityType visibility, boolean created) {}

	/**
	 * Updates the set at {@code folderId}, or creates one there if the id is unused — the legacy
	 * {@code PUT /v2/networkset/{id}} upsert.
	 *
	 * <p>Resolving the id is the whole point of this method existing rather than the caller branching on
	 * ownership: "not the owner" covers two states that must not be treated as "free to create at". A
	 * live set belonging to someone else, and a set of the caller's own sitting in the trash, both already
	 * occupy the primary key, so creating would raise a constraint violation and surface as a 500.
	 *
	 * <ul>
	 * <li>no folder row → create, {@code created = true}</li>
	 * <li>live folder owned by {@code ownerId} → update, {@code created = false}</li>
	 * <li>live folder owned by someone else → {@link UnauthorizedOperationException}</li>
	 * <li>trashed folder → {@link org.ndexbio.model.exceptions.ObjectNotFoundException}, matching what
	 *     {@link #getSet} reports for a trashed set rather than claiming an ownership problem</li>
	 * </ul>
	 */
	UpsertOutcome upsertSet(UUID folderId, UUID ownerId, String name, String description)
			throws SQLException, NdexException;

	/**
	 * Deletes a set, trashing the folder and everything in it.
	 *
	 * @return every id affected, so the caller can clear their Solr docs
	 */
	DeletedFileIds deleteSet(UUID folderId) throws SQLException, NdexException;

	/**
	 * Adds networks to a set as shortcuts; the networks themselves are not moved.
	 *
	 * <p>Every target is validated before anything is written, so a list containing an unreadable id
	 * creates nothing. Ids already in the set are skipped rather than duplicated.
	 *
	 * @return the ids of the shortcuts created
	 * @throws NdexException a target does not exist or is not readable by {@code ownerId}
	 */
	List<UUID> addMembers(UUID folderId, UUID ownerId, List<UUID> networkIds) throws SQLException, NdexException;

	/**
	 * Removes networks from a set.
	 *
	 * <p>A network referenced by a shortcut has that shortcut physically deleted — the analogue of
	 * dropping a legacy {@code network_set_member} row — leaving the network untouched. A network
	 * parented directly in the folder is instead moved to the owner's home root, v3's remove-from-folder
	 * semantic; one the caller does not own is left in place.
	 */
	RemovedMembers removeMembers(UUID folderId, UUID ownerId, List<UUID> networkIds)
			throws SQLException, NdexException;

	/** The set's access key, or null when absent or disabled. Never creates or enables one. */
	String getAccessKey(UUID folderId) throws SQLException, NdexException;

	/** Enables the set's access key, returning it. Idempotent: an enabled key is returned as-is. */
	String enableAccessKey(UUID folderId, UUID ownerId) throws Exception;

	/** Disables the set's access key, preserving its value so re-enabling returns the same string. */
	void disableAccessKey(UUID folderId, UUID ownerId) throws Exception;
}
