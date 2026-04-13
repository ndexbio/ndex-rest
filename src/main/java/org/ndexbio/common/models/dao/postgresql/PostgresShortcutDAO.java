package org.ndexbio.common.models.dao.postgresql;

import java.io.IOException;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.sql.Types;
import java.util.*;
import java.util.logging.Logger;

import org.ndexbio.common.models.dao.ShortcutDAO;
import org.ndexbio.model.exceptions.NdexException;
import org.ndexbio.model.exceptions.ObjectNotFoundException;
import org.ndexbio.model.exceptions.UnauthorizedOperationException;
import org.ndexbio.model.object.FileType;
import org.ndexbio.model.object.NdexObjectUpdateStatus;
import org.ndexbio.model.object.NdexShortcut;
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

	private NdexShortcut mapResultSetToNdexShortcut(ResultSet rs) throws SQLException {
		NdexShortcut s = new NdexShortcut();
		s.setExternalId((UUID) rs.getObject("UUID"));
		s.setName(rs.getString("name"));
		s.setCreationTime(rs.getTimestamp("creation_time"));
		s.setModificationTime(rs.getTimestamp("modification_time"));
		s.setIsDeleted(rs.getBoolean("is_deleted"));
		s.setTarget((UUID) rs.getObject("target"));
		s.setParent((UUID) rs.getObject("parent"));
		s.setTargetType(FileType.valueOf(rs.getString("target_type").toUpperCase()));
		s.setOwner_id(rs.getString("owner_id"));
		s.setOwner(rs.getString("owner_name"));
		return s;
	}

	@Override
	public NdexShortcut getShortcut(UUID shortcutId, UUID userId) throws SQLException, ObjectNotFoundException, UnauthorizedOperationException, JsonParseException, JsonMappingException, IOException {
		String sqlStr = "SELECT s.\"UUID\", s.name, s.creation_time, s.modification_time, s.target, s.parent, s.is_deleted, s.target_type, " +
				"s.owneruuid AS owner_id, u.user_name AS owner_name " +
				"FROM shortcut s JOIN ndex_user u ON s.owneruuid = u.\"UUID\" " +
				"WHERE s.\"UUID\"=?";
		try (PreparedStatement p = db.prepareStatement(sqlStr)) {
			p.setObject(1, shortcutId);
			try (ResultSet rs = p.executeQuery()) {
				if (rs.next()) {
					return mapResultSetToNdexShortcut(rs);
				} else
					throw new ObjectNotFoundException("Shortcut" + shortcutId + " not found in db.");
			}
		}
	}

	public List<NdexShortcut> getShortcutsByIds(List<UUID> shortcutIds) throws SQLException {
		if (shortcutIds == null || shortcutIds.isEmpty())
			return new ArrayList<>();

		String placeholders = String.join(",", Collections.nCopies(shortcutIds.size(), "?"));
		String sql = "SELECT s.\"UUID\", s.name, s.creation_time, s.modification_time, s.is_deleted, s.target, s.parent, s.target_type, " +
				"s.owneruuid AS owner_id, u.user_name AS owner_name " +
				"FROM shortcut s JOIN ndex_user u ON s.owneruuid = u.\"UUID\" " +
				"WHERE s.\"UUID\" IN (" + placeholders + ")";

		Map<UUID, NdexShortcut> shortcutMap = new HashMap<>(shortcutIds.size());
		try (PreparedStatement pst = db.prepareStatement(sql)) {
			for (int i = 0; i < shortcutIds.size(); i++) {
				pst.setObject(i + 1, shortcutIds.get(i));
			}
			try (ResultSet rs = pst.executeQuery()) {
				while (rs.next()) {
					NdexShortcut s = mapResultSetToNdexShortcut(rs);
					shortcutMap.put(s.getExternalId(), s);
				}
			}
		}

		List<NdexShortcut> result = new ArrayList<>(shortcutIds.size());
		for (UUID id : shortcutIds) {
			NdexShortcut s = shortcutMap.get(id);
			if (s != null) {
				result.add(s);
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
			String sqlStr = "UPDATE shortcut SET modification_time = ?, is_deleted = true, show_in_trash = true WHERE \"UUID\"=?";
			try (PreparedStatement pst = db.prepareStatement(sqlStr)) {
				pst.setTimestamp(1, t);
				pst.setObject(2, shortcutId);
				pst.executeUpdate();
			}
		}
	}
	
	@Override
	public void updateShortcut(UUID shortcutId, String name, UUID parentId) throws SQLException, JsonProcessingException, NdexException {
		Timestamp t = new Timestamp(System.currentTimeMillis());
	    
	    StringBuilder sb = new StringBuilder("update shortcut set modification_time=?");
	    if (name != null) {
	        sb.append(", name=?");
	    }
	    sb.append(", parent=?");
	    sb.append(" WHERE \"UUID\"=? AND is_deleted=false");
				
	    try (PreparedStatement pst = db.prepareStatement(sb.toString())) {
	        int idx = 1;
	        pst.setTimestamp(idx++, t);
	        if (name != null) {
	            pst.setString(idx++, name);
	        }
	        if (parentId == null) {
	        	pst.setNull(idx++, Types.OTHER);
	        } else {
	        	pst.setObject(idx++, parentId);
	        }
	        pst.setObject(idx++, shortcutId);
	        
	        int updated = pst.executeUpdate();
	        if (updated == 0) {
	            throw new NdexException(
	                "Failed to update shortcut. Shortcut " + shortcutId + " may not exist or is deleted."
	            );
	        }
	    }
	}
	
	@Override
	public List<NdexShortcut> listShortcutsOfUser(UUID ownerId, int limit) throws SQLException {
	    List<NdexShortcut> result = new ArrayList<>();

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
	                NdexShortcut s = new NdexShortcut();
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
	@Override
	public VisibilityType getShortcutVisibility(UUID shortcutId) throws SQLException, NdexException {
		String sql = "SELECT visibility FROM shortcut WHERE \"UUID\" = ? AND is_deleted = false";
		try (PreparedStatement pst = db.prepareStatement(sql)) {
			pst.setObject(1, shortcutId);
			try (ResultSet rs = pst.executeQuery()) {
				if (rs.next()) {
					return VisibilityType.valueOf(rs.getString("visibility"));
				}
				throw new NdexException("Shortcut not found: " + shortcutId);
			}
		}
	}

    /**
     * Returns all root-level (parent is null) shortcuts for a user that are not deleted.
     */
    public List<NdexShortcut> listRootShortcutsOfUser(UUID ownerId) throws SQLException {
        List<NdexShortcut> result = new ArrayList<>();
        String sql = "SELECT \"UUID\", name, creation_time, modification_time, is_deleted, target, parent, target_type " +
                     " FROM shortcut " +
                     " WHERE owneruuid=? AND parent IS NULL AND is_deleted=false " +
                     " ORDER BY name";
        try (PreparedStatement pst = db.prepareStatement(sql)) {
            pst.setObject(1, ownerId);
            try (ResultSet rs = pst.executeQuery()) {
                while (rs.next()) {
                    NdexShortcut s = new NdexShortcut();
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

}
