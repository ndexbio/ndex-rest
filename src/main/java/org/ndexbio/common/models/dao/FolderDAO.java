package org.ndexbio.common.models.dao;

import java.io.IOException;
import java.sql.SQLException;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.ndexbio.model.exceptions.NdexException;
import org.ndexbio.model.exceptions.ObjectNotFoundException;
import org.ndexbio.model.exceptions.UnauthorizedOperationException;
import org.ndexbio.model.object.FileCount;
import org.ndexbio.model.object.FileItemSummary;
import org.ndexbio.model.object.FileType;
import org.ndexbio.model.object.NdexFolder;
import org.ndexbio.model.object.NdexObjectUpdateStatus;
import org.ndexbio.model.object.Permissions;
import org.ndexbio.model.object.network.VisibilityType;

import com.fasterxml.jackson.core.JsonParseException;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonMappingException;

public interface FolderDAO extends AutoCloseable {

	@Override
	void close() throws SQLException;
	
	void commit() throws SQLException;
	
	NdexObjectUpdateStatus createFolder(final UUID folderUUID, final UUID ownerId, final UUID parentUUID, final String name, final String description) throws SQLException;
	
	boolean isReadable(UUID folderID, UUID userId) throws SQLException, ObjectNotFoundException;
	
	boolean accessKeyIsValid(UUID folderId, String accessKey) throws SQLException;
	
	NdexFolder getFolder(UUID folderId, UUID userId, String accessKey) throws SQLException, ObjectNotFoundException, UnauthorizedOperationException, JsonParseException, JsonMappingException, IOException;
	
	boolean isFolderOwner(UUID folderId, UUID ownerId) throws SQLException;
	
	/**
	 * Deletes a folder, optionally cascading over its whole subtree.
	 *
	 * @param force     when true, delete the folder even if it has children, cascading to every
	 *                  descendant folder, network and shortcut at any depth; when false a non-empty
	 *                  folder is rejected with {@link SQLException}
	 * @param permanent when true the rows are physically removed; when false they are marked
	 *                  {@code is_deleted=true} and the named folder also gets {@code show_in_trash=true}
	 * @return the ids actually affected, grouped by type, so the caller can clear their Solr docs.
	 *         The named folder is always included in {@code folders()}; descendants appear only when
	 *         {@code force} is true.
	 */
	DeletedFileIds deleteFolder(UUID folderId, boolean force, boolean permanent) throws SQLException;
	
	void updateFolder(UUID folderId, String name, UUID parentId, UUID ownerId, String description) throws SQLException, JsonProcessingException, NdexException;
	
	FileCount getFolderChildCounts(UUID folderId) throws SQLException;

	/**
	 * Like {@link #getFolderChildCounts(UUID)} but counts only children the given viewer may read.
	 * @param viewerUserId the accessing user, or {@code null} for an anonymous caller
	 */
	FileCount getReadableFolderChildCounts(UUID folderId, UUID viewerUserId) throws SQLException;

	/**
	 * Like {@link #getFolderChildCounts(UUID)} but for a request that provided a valid access key for
	 * this folder: counts the children the key validates (folders + networks, plus same-owner NETWORK
	 * shortcuts whose target networks the key now unlocks — issue #133/#137).
	 */
	FileCount getFolderChildCountsKeyFiltered(UUID folderId) throws SQLException;

	List<FileItemSummary> listItemsInFolder(UUID folderId, boolean compact, FileType type) throws SQLException;

	/**
	 * Like {@link #listItemsInFolder(UUID, boolean, FileType)} but returns only the immediate children
	 * the given viewer may read (PUBLIC/UNLISTED, plus items they own or are shared on).
	 * @param viewerUserId the accessing user, or {@code null} for an anonymous caller
	 */
	List<FileItemSummary> listReadableItemsInFolder(UUID folderId, boolean compact, FileType type, UUID viewerUserId) throws SQLException;

	/**
	 * Like {@link #listItemsInFolder(UUID, boolean, FileType)} but for a request that provided a valid
	 * access key for this folder: returns the immediate children the key validates (folders + networks,
	 * plus same-owner NETWORK shortcuts whose target networks the key now unlocks — issue #133/#137).
	 */
	List<FileItemSummary> listItemsInFolderKeyFiltered(UUID folderId, boolean compact, FileType type) throws SQLException;

	List<FileItemSummary> listRootItemsOfUser(UUID ownerId, boolean compact, FileType type) throws SQLException;

	/**
	 * Counts the root-level (home directory) items owned by the given user — folders, networks,
	 * and shortcuts whose parent is NULL. Mirrors {@link #listRootItemsOfUser}.
	 */
	FileCount getRootChildCountsOfUser(UUID ownerId) throws SQLException;
	
	/** Every folder this user owns, at any depth. Equivalent to {@code listFoldersOfUser(ownerId, limit, true)}. */
	List<NdexFolder> listFoldersOfUser(UUID ownerId, int limit) throws SQLException;

	/**
	 * The folders this user owns, ordered by name.
	 *
	 * @param includeNested when false, only home-root folders (parent IS NULL) are returned, matching
	 *        the "home" scope {@link #listRootItemsOfUser} and {@link #getRootChildCountsOfUser} use.
	 */
	List<NdexFolder> listFoldersOfUser(UUID ownerId, int limit, boolean includeNested) throws SQLException;
	
	NdexObjectUpdateStatus setFolderPermission(UUID folderId, UUID userId, Permissions permission) throws SQLException, NdexException;
	
	void removeFolderPermission(UUID folderId, UUID userId) throws SQLException;
	
	Map<String, String> getFolderPermissions(UUID folderId) throws SQLException;

	/**
	 * The permission {@code userId} effectively holds on {@code folderId}, or null for none.
	 *
	 * <p>Unlike {@link #getFolderPermissions(UUID)}, which returns only rows stored directly on the
	 * folder, this accounts for ownership and for permissions inherited from ancestor folders. Callers
	 * deciding whether a user may write into a folder must use this — a direct-row check reports "no
	 * access" for a user whose write permission comes from a parent.</p>
	 */
	Permissions getEffectivePermission(UUID folderId, UUID userId) throws SQLException;

	String getFolderAccessKey(UUID folderId) throws SQLException, ObjectNotFoundException;

	String enableFolderAccessKey(UUID folderId) throws SQLException, NdexException;
	
	void disableFolderAccessKey(UUID folderId) throws SQLException, NdexException;
	
	void transferFolder(UUID folderId, UUID newOwnerId) throws SQLException, NdexException;
	
	List<FileItemSummary> listSharedFolders(UUID userId) throws SQLException;
	
	List<FileItemSummary> listFoldersSharedBySpecificUser(UUID userId, UUID ownerId, boolean compact) throws SQLException;
	
	void setFolderVisibility(UUID folderId, VisibilityType visibility) throws SQLException, NdexException;
	VisibilityType getFolderVisibility(UUID folderId) throws SQLException, NdexException;


	boolean isDescendantOf(UUID folderId, UUID potentialDescendantId) throws SQLException;
	
	List<FileItemSummary> listPublicRootItemsOfUser(UUID ownerId, boolean compact) throws SQLException;
	
	List<FileItemSummary> listPublicRootItemsOfUser(UUID ownerId, boolean compact, FileType fileType) throws SQLException;
	List<NdexFolder> getFoldersByIds(List<UUID> folderIds) throws SQLException;
}
