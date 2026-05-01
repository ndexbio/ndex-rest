package org.ndexbio.common.models.dao;

import java.io.IOException;
import java.sql.SQLException;
import java.util.List;
import java.util.UUID;

import org.ndexbio.model.exceptions.NdexException;
import org.ndexbio.model.exceptions.ObjectNotFoundException;
import org.ndexbio.model.exceptions.UnauthorizedOperationException;
import org.ndexbio.model.object.FileItemSummary;
import org.ndexbio.model.object.FileType;
import org.ndexbio.model.object.NdexObjectUpdateStatus;
import org.ndexbio.model.object.NdexShortcut;
import org.ndexbio.model.object.network.VisibilityType;

import com.fasterxml.jackson.core.JsonParseException;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonMappingException;

public interface ShortcutDAO extends AutoCloseable {
	
	void commit() throws SQLException;
	
	NdexObjectUpdateStatus createShortcut(final UUID shortcutUUID, final UUID ownerId, final UUID parentUUID, final String name, final UUID targetUUID, final FileType targetType) throws SQLException;
	
	boolean isReadable(UUID shortcutID, UUID userId) throws SQLException, ObjectNotFoundException;
		
	NdexShortcut getShortcut(UUID shortcutId, UUID userId) throws SQLException, ObjectNotFoundException, UnauthorizedOperationException, JsonParseException, JsonMappingException, IOException;
	
	boolean isShortcutOwner(UUID shortcutId, UUID ownerId) throws SQLException;
	
	void deleteShortcut(UUID shortcutId, boolean permanent) throws SQLException;
	
	void updateShortcut(UUID shortcutId, String name, UUID parentId) throws SQLException, JsonProcessingException, NdexException;
	
	List<NdexShortcut> listShortcutsOfUser(UUID ownerId, int limit) throws SQLException;
	
	void setShortcutVisibility(UUID shortcutId, VisibilityType visibility) throws SQLException, NdexException;
	VisibilityType getShortcutVisibility(UUID shortcutId) throws SQLException, NdexException;
	List<NdexShortcut> getShortcutsByIds(List<UUID> shortcutIds) throws SQLException;
	List<FileItemSummary> getShortcutSummariesByIds(List<UUID> shortcutIds) throws SQLException;

}
