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

import org.ndexbio.model.exceptions.NdexException;
import org.ndexbio.model.exceptions.ObjectNotFoundException;
import org.ndexbio.model.exceptions.UnauthorizedOperationException;
import org.ndexbio.model.object.NdexObjectUpdateStatus;
import org.ndexbio.model.object.Shortcut;

import com.fasterxml.jackson.core.JsonParseException;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonMappingException;

public class PostgresShortcutDAO extends NdexDBDAO {
	
	private static Logger logger = Logger.getLogger(PostgresShortcutDAO.class.getName());

	public PostgresShortcutDAO() throws SQLException {
		super();
	}
	
	public NdexObjectUpdateStatus createShortcut(final UUID shortcutUUID, final UUID ownerId, final UUID parentUUID, final String name, final UUID targetUUID) throws SQLException {
		Timestamp t = new Timestamp(System.currentTimeMillis());
		
		String sqlStr = "insert into shortcut (\"UUID\", creation_time, modification_time, is_deleted, name, target_type, target, visibility, owneruuid, parent) values"
				+ "(?, ?, ?, false, ?, ?, ?, 'PRIVATE',?, ?) ";
		try (PreparedStatement pst = db.prepareStatement(sqlStr)) {
			pst.setObject(1, shortcutUUID);
			pst.setTimestamp(2, t);
			pst.setTimestamp(3, t);
			pst.setString(4, name);
			pst.setString(5, "folder"); //TODO: add a method to check type of target
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
	
	public boolean accessKeyIsValid(UUID shortcutId, String accessKey) throws SQLException {
		if ( accessKey ==null || accessKey.length() == 0)
			return false;
		
		String sqlStr = "select 1 from shortcut where (\"UUID\"=? and access_key_is_on and access_key = ?)" ;
		try (PreparedStatement p = db.prepareStatement(sqlStr)) {
			p.setObject(1, shortcutId);
			p.setString(2, accessKey);
			try ( ResultSet rs = p.executeQuery()) {
				 if (rs.next())
					 return true;
			}		
		}
		return true;

	}
	
	public Shortcut getShortcut(UUID shortcutId, UUID userId, String accessKey) throws SQLException, ObjectNotFoundException, UnauthorizedOperationException, JsonParseException, JsonMappingException, IOException {
		
		Shortcut result = new Shortcut();
		String sqlStr = "select creation_time, modification_time, name, target, parent, is_deleted from shortcut where \"UUID\"=?";
		
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
				} else
					throw new ObjectNotFoundException("Shortcut" + shortcutId + " not found in db.");
			}
		}
		
		return result;
	}
	
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
	
	public void deleteShortcut(UUID shortcutId) throws SQLException {
		Timestamp t = new Timestamp(System.currentTimeMillis());
		
		String sqlStr = "update shortcut set modification_time = ?, is_deleted = true  where \"UUID\"=?";
				
		try (PreparedStatement pst = db.prepareStatement(sqlStr)) {
			pst.setTimestamp(1, t);
			pst.setObject(2, shortcutId);
			pst.executeUpdate();
		}
		
	}
	
	public void updateShortcut(UUID shortcutId, String name, UUID ownerId) throws SQLException, JsonProcessingException, NdexException {
		Timestamp t = new Timestamp(System.currentTimeMillis());
		String sqlStr = "update shortcut set modification_time = ?, name = ? where \"UUID\"=? and is_deleted=false";
		
		try (PreparedStatement pst = db.prepareStatement(sqlStr)) {
			pst.setTimestamp(1, t);
			pst.setString(2, name);
			pst.setObject(3, shortcutId);
			pst.executeUpdate();
		}
	}
	
	public List<Shortcut> listShortcutsOfUser(UUID ownerId, int limit) throws SQLException {
	    List<Shortcut> result = new ArrayList<>();

	    String sql = "SELECT \"UUID\", name, creation_time, modification_time, is_deleted, target, parent " +
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

	                result.add(s);
	            }
	        }
	    }

	    return result;
	}


}
