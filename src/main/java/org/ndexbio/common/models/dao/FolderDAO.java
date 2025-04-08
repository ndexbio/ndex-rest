package org.ndexbio.common.models.dao;

import java.io.IOException;
import java.sql.SQLException;
import java.util.List;
import java.util.UUID;

import org.ndexbio.model.exceptions.NdexException;
import org.ndexbio.model.exceptions.ObjectNotFoundException;
import org.ndexbio.model.exceptions.UnauthorizedOperationException;
import org.ndexbio.model.object.FileCount;
import org.ndexbio.model.object.FileItemSummary;
import org.ndexbio.model.object.Folder;
import org.ndexbio.model.object.NdexObjectUpdateStatus;

import com.fasterxml.jackson.core.JsonParseException;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonMappingException;

public interface FolderDAO extends AutoCloseable {
	
	void commit() throws SQLException;
	
	NdexObjectUpdateStatus createFolder(final UUID folderUUID, final UUID ownerId, final UUID parentUUID, final String name) throws SQLException;
	
	boolean isReadable(UUID folderID, UUID userId) throws SQLException, ObjectNotFoundException;
	
	boolean accessKeyIsValid(UUID folderId, String accessKey) throws SQLException;
	
	Folder getFolder(UUID folderId, UUID userId, String accessKey) throws SQLException, ObjectNotFoundException, UnauthorizedOperationException, JsonParseException, JsonMappingException, IOException;
	
	boolean isFolderOwner(UUID folderId, UUID ownerId) throws SQLException;
	
	void deleteFolder(UUID folderId) throws SQLException;
	
	void updateFolder(UUID folderId, String name, UUID parentId, UUID ownerId) throws SQLException, JsonProcessingException, NdexException;
	
	FileCount getFolderChildCounts(UUID folderId) throws SQLException;
	
	List<FileItemSummary> listItemsInFolder(UUID folderId) throws SQLException;
	
	List<Folder> listFoldersOfUser(UUID ownerId, int limit) throws SQLException;
	
	NdexObjectUpdateStatus addFolderPermission(UUID folderId, UUID userId, String permission) throws SQLException, NdexException;
	
	void updateFolderPermission(UUID folderId, UUID userId, String permission) throws SQLException, NdexException;
	
	void removeFolderPermission(UUID folderId, UUID userId) throws SQLException;
	
	String enableFolderAccessKey(UUID folderId) throws SQLException, NdexException;
	
	void disableFolderAccessKey(UUID folderId) throws SQLException, NdexException;
	
	void transferFolder(UUID folderId, UUID newOwnerId) throws SQLException, NdexException;
	
	List<UUID> listSharedFolderIds(UUID userId) throws SQLException;

}
