package org.ndexbio.common.models.dao;

import java.io.IOException;
import java.sql.SQLException;
import java.util.List;
import java.util.UUID;

import org.ndexbio.model.exceptions.NdexException;
import org.ndexbio.model.exceptions.ObjectNotFoundException;
import org.ndexbio.model.exceptions.UnauthorizedOperationException;
import org.ndexbio.model.object.FileType;
import org.ndexbio.model.object.NdexObjectUpdateStatus;
import org.ndexbio.model.object.Shortcut;

import com.fasterxml.jackson.core.JsonParseException;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonMappingException;

public interface ShortcutDAO extends AutoCloseable {
	
	void commit() throws SQLException;
	
	NdexObjectUpdateStatus createShortcut(final UUID shortcutUUID, final UUID ownerId, final UUID parentUUID, final String name, final UUID targetUUID, final FileType targetType) throws SQLException;
	
	boolean isReadable(UUID shortcutID, UUID userId) throws SQLException, ObjectNotFoundException;
		
	Shortcut getShortcut(UUID shortcutId, UUID userId) throws SQLException, ObjectNotFoundException, UnauthorizedOperationException, JsonParseException, JsonMappingException, IOException;
	
	boolean isShortcutOwner(UUID shortcutId, UUID ownerId) throws SQLException;
	
	void deleteShortcut(UUID shortcutId) throws SQLException;
	
	void updateShortcut(UUID shortcutId, String name, UUID parentId) throws SQLException, JsonProcessingException, NdexException;
	
	List<Shortcut> listShortcutsOfUser(UUID ownerId, int limit) throws SQLException;

}
