package org.ndexbio.common.models.dao.postgresql;

import java.io.IOException;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.logging.Logger;

import org.ndexbio.common.models.dao.ShortcutDAO;
import org.ndexbio.model.exceptions.NdexException;
import org.ndexbio.model.exceptions.ObjectNotFoundException;
import org.ndexbio.model.exceptions.UnauthorizedOperationException;
import org.ndexbio.model.object.FileType;
import org.ndexbio.model.object.NdexObjectUpdateStatus;
import org.ndexbio.model.object.Shortcut;
import org.ndexbio.model.object.network.VisibilityType;

import com.fasterxml.jackson.core.JsonParseException;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonMappingException;

public class PostgresShortcutDAO extends NdexDBDAO implements ShortcutDAO {
	
	private static Logger logger = Logger.getLogger(PostgresShortcutDAO.class.getName());

	public PostgresShortcutDAO() throws SQLException {
		super();
	}
	
	@Override
	public NdexObjectUpdateStatus createShortcut(final UUID shortcutUUID, final UUID ownerId, final UUID parentUUID, final String name, final UUID targetUUID, final FileType targetType) throws SQLException {
		Timestamp t = new Timestamp(System.currentTimeMillis());
		
		String sqlStr = "insert into shortcut (\"UUID\", creation_time, modification_time, is_deleted, name, target_type, target, visibility, owneruuid, parent) values"
				+ "(?, ?, ?, false, ?, ?, ?, 'PRIVATE',?, ?) ";
		try (PreparedStatement pst = db.prepareStatement(sqlStr)) {
			pst.setObject(1, shortcutUUID);
			pst.setTimestamp(2, t);
			pst.setTimestamp(3, t);
			pst.setString(4, name);
			pst.setString(5, targetType.toString());
			pst.setObject(6, targetUUID);
			pst.setObject(7, ownerId);
			pst.setObject(8, parentUUID);
			
			pst.executeUpdate();
		}
		NdexObjectUpdateStatus result = new NdexObjectUpdateStatus();
		result.setModificationTime(t);
		result.setUuid(shortcutUUID);
		return result;
	}
	
	protected static String createIsReadableConditionStr(UUID userId) {
	    if (userId == null) {
	        // Anonymous user => only PUBLIC is allowed
	        return "s.visibility='PUBLIC'";
	    }
	    // Non-anonymous => public or same owner
	    return "( s.visibility='PUBLIC' "
	         + "  OR s.owneruuid = '" + userId + "'::uuid "
	         + ")";
	}
	
	@Override
	public boolean isReadable(UUID shortcutID, UUID userId) throws SQLException, ObjectNotFoundException {
		String sqlStr = "select (" + createIsReadableConditionStr(userId) + ") from shortcut s where s.\"UUID\" = ? and s.is_deleted=false ";		
			
		try (PreparedStatement pst = db.prepareStatement(sqlStr)) {
			pst.setObject(1, shortcutID);

			try ( ResultSet rs = pst.executeQuery()) {
				if ( rs.next()) 
					return rs.getBoolean(1);
				 
				throw new ObjectNotFoundException("Shortcut", shortcutID);
			}
		}
	}
	
	@Override
	public Shortcut getShortcut(UUID shortcutId, UUID userId) throws SQLException, ObjectNotFoundException, UnauthorizedOperationException, JsonParseException, JsonMappingException, IOException {
		
		Shortcut result = new Shortcut();
		String sqlStr = "select creation_time, modification_time, name, target, parent, is_deleted, target_type from shortcut where \"UUID\"=?";
		
		try (PreparedStatement p = db.prepareStatement(sqlStr)) {
			p.setObject(1, shortcutId);
			try ( ResultSet rs = p.executeQuery()) {
				if ( rs.next()) {
					result.setCreationTime(rs.getTimestamp(1));
					result.setModificationTime(rs.getTimestamp(2));
					result.setExternalId(shortcutId);
					result.setName(rs.getString(3));
					result.setTarget((UUID)(rs.getObject(4)));
					result.setParent((UUID)(rs.getObject(5)));
					result.setIsDeleted(rs.getBoolean(6));
					result.setTargetType(FileType.valueOf(rs.getString("target_type").toUpperCase()));
				} else
					throw new ObjectNotFoundException("Shortcut" + shortcutId + " not found in db.");
			}
		}
		
		return result;
	}
	
	@Override
	public boolean isShortcutOwner(UUID shortcutId, UUID ownerId) throws SQLException {
		String sqlStr = "select 1 from shortcut where \"UUID\" = ? and owneruuid = ? and is_deleted=false";
		try (PreparedStatement p = db.prepareStatement(sqlStr)) {
			p.setObject(1, shortcutId);
			p.setObject(2, ownerId);
			try ( ResultSet rs = p.executeQuery()) {
				return rs.next(); 
			}
		}
	}
	
	@Override
	public void deleteShortcut(UUID shortcutId, boolean permanent) throws SQLException {
		Timestamp t = new Timestamp(System.currentTimeMillis());
		
		if (permanent) {
			String sqlStr = "DELETE FROM shortcut WHERE \"UUID\"=?";
			try (PreparedStatement pst = db.prepareStatement(sqlStr)) {
				pst.setObject(1, shortcutId);
				pst.executeUpdate();
			}
		} else {
			String sqlStr = "UPDATE shortcut SET modification_time = ?, is_deleted = true WHERE \"UUID\"=?";
			try (PreparedStatement pst = db.prepareStatement(sqlStr)) {
				pst.setTimestamp(1, t);
				pst.setObject(2, shortcutId);
				pst.executeUpdate();
			}
		}
	}
	
	@Override
	public void updateShortcut(UUID shortcutId, String name, UUID parentId) throws SQLException, JsonProcessingException, NdexException {
	    if (name == null && parentId == null) {
	        throw new NdexException("No updates requested (both name and parent are null).");
	    }
		
		Timestamp t = new Timestamp(System.currentTimeMillis());
	    
	    StringBuilder sb = new StringBuilder("update shortcut set modification_time=?");
	    if (name != null) {
	        sb.append(", name=?");
	    }
	    if (parentId != null) {
	        sb.append(", parent=?");
	    }
	    sb.append(" WHERE \"UUID\"=? AND is_deleted=false");
				
	    try (PreparedStatement pst = db.prepareStatement(sb.toString())) {
	        int idx = 1;
	        pst.setTimestamp(idx++, t);
	        if (name != null) {
	            pst.setString(idx++, name);
	        }
	        if (parentId != null) {
	            pst.setObject(idx++, parentId);
	        }
	        pst.setObject(idx++, shortcutId);
	        
	        int updated = pst.executeUpdate();
	        if (updated == 0) {
	            throw new NdexException(
	                "Failed to update folder. Shortcut " + shortcutId + " may not exist or is deleted."
	            );
	        }
	    }
	}
	
	@Override
	public List<Shortcut> listShortcutsOfUser(UUID ownerId, int limit) throws SQLException {
	    List<Shortcut> result = new ArrayList<>();

	    String sql = "SELECT \"UUID\", name, creation_time, modification_time, is_deleted, target, parent, target_type " +
	                 " FROM shortcut " +
	                 " WHERE owneruuid=? AND is_deleted=false " +
	                 " ORDER BY name " +
	                 " LIMIT ?";

	    try (PreparedStatement pst = db.prepareStatement(sql)) {
	        pst.setObject(1, ownerId);
	        pst.setInt(2, limit);

	        try (ResultSet rs = pst.executeQuery()) {
	            while (rs.next()) {
	                Shortcut s = new Shortcut();
	                s.setExternalId((UUID) rs.getObject("UUID"));
	                s.setName(rs.getString("name"));
	                s.setCreationTime(rs.getTimestamp("creation_time"));
	                s.setModificationTime(rs.getTimestamp("modification_time"));
	                s.setIsDeleted(rs.getBoolean("is_deleted"));
	                s.setTarget((UUID) rs.getObject("target"));
	                s.setParent((UUID) rs.getObject("parent"));
	                s.setTargetType(FileType.valueOf(rs.getString("target_type").toUpperCase()));

	                result.add(s);
	            }
	        }
	    }

	    return result;
	}
	
	public void setShortcutVisibility(UUID shortcutId, VisibilityType visibility) throws SQLException, NdexException {
	    String sql = "UPDATE shortcut SET visibility = ? WHERE \"UUID\" = ? AND is_deleted = false";
	    try (PreparedStatement pst = db.prepareStatement(sql)) {
	        pst.setString(1, visibility.toString());
	        pst.setObject(2, shortcutId);
	        int updated = pst.executeUpdate();
	        if (updated != 1) {
	            throw new NdexException("Failed to update visibility for shortcut " + shortcutId);
	        }
	    }
	}


}
