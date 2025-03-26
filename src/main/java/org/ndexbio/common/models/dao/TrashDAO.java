package org.ndexbio.common.models.dao;

import java.sql.SQLException;
import java.util.List;
import java.util.UUID;

import org.ndexbio.model.object.FileItemSummary;
import org.ndexbio.model.object.TrashRestoreRequest;

public interface TrashDAO extends AutoCloseable {
	
	void commit() throws SQLException;
	
	List<FileItemSummary> listTrashedItemsOfUser(UUID ownerId) throws SQLException;
	
	void restoreTrashedItems(UUID userId, TrashRestoreRequest request) throws SQLException;
	
	void permanentlyDeleteAllTrashedItemsOfUser(UUID ownerId) throws SQLException;

}
