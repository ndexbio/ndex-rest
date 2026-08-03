package org.ndexbio.common.models.dao;

import java.sql.SQLException;
import java.util.List;
import java.util.UUID;

import org.ndexbio.model.object.FileItemSummary;
import org.ndexbio.model.object.FileType;
import org.ndexbio.model.object.TrashRestoreRequest;

public interface TrashDAO extends AutoCloseable {
	
	void commit() throws SQLException;
	
	List<FileItemSummary> listTrashedItemsOfUser(UUID ownerId) throws SQLException;
	
	void restoreTrashedItems(UUID userId, TrashRestoreRequest request) throws SQLException;
	
	/**
	 * Physically removes everything in the user's trash.
	 *
	 * @return the ids removed, grouped by type, so the caller can clear their Solr docs
	 */
	DeletedFileIds permanentlyDeleteAllTrashedItemsOfUser(UUID ownerId) throws SQLException;

	/**
	 * Physically removes one trashed item; for a folder this cascades to the descendants that were
	 * trashed along with it.
	 *
	 * @return the ids removed, grouped by type, so the caller can clear their Solr docs
	 */
	DeletedFileIds permanentlyDeleteTrashedItem(UUID itemId, FileType type) throws SQLException;

	FileType getTrashedItemType(UUID itemId) throws SQLException;

}
