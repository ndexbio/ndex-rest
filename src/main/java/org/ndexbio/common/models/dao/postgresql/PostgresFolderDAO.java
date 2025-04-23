package org.ndexbio.common.models.dao.postgresql;

import java.io.IOException;
import java.security.SecureRandom;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.logging.Logger;

import org.ndexbio.common.models.dao.FolderDAO;
import org.ndexbio.model.exceptions.NdexException;
import org.ndexbio.model.exceptions.ObjectNotFoundException;
import org.ndexbio.model.exceptions.UnauthorizedOperationException;
import org.ndexbio.model.object.FileCount;
import org.ndexbio.model.object.FileItemSummary;
import org.ndexbio.model.object.FileType;
import org.ndexbio.model.object.Folder;
import org.ndexbio.model.object.NdexObjectUpdateStatus;
import org.ndexbio.model.object.Permissions;
import org.ndexbio.model.object.network.VisibilityType;

import com.fasterxml.jackson.core.JsonParseException;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonMappingException;

import jakarta.xml.bind.DatatypeConverter;

public class PostgresFolderDAO extends NdexDBDAO implements FolderDAO {
	
	private static Logger logger = Logger.getLogger(PostgresFolderDAO.class.getName());

	public PostgresFolderDAO() throws SQLException {
		super();
	}
	
	@Override
	public NdexObjectUpdateStatus createFolder(final UUID folderUUID, final UUID ownerId, final UUID parentUUID, final String name) throws SQLException {
		Timestamp t = new Timestamp(System.currentTimeMillis());
		
		String sqlStr = "insert into folder (\"UUID\", creation_time, modification_time, is_deleted, name, visibility, owneruuid, access_key_is_on, parent) values"
				+ "(?, ?, ?, false, ?, 'PRIVATE',?, false, ?) ";
		try (PreparedStatement pst = db.prepareStatement(sqlStr)) {
			pst.setObject(1, folderUUID);
			pst.setTimestamp(2, t);
			pst.setTimestamp(3, t);
			pst.setString(4, name);
			pst.setObject(5, ownerId);
			pst.setObject(6, parentUUID);
			
			pst.executeUpdate();
		}
		NdexObjectUpdateStatus result = new NdexObjectUpdateStatus();
		result.setModificationTime(t);
		result.setUuid(folderUUID);
		return result;
	}
	
	protected static String createIsReadableConditionStr(UUID userId) {
	    if (userId == null) {
	        // Anonymous user => only PUBLIC is allowed
	        return "f.visibility='PUBLIC'";
	    }
	    // Non-anonymous => public or same owner or has permission
	    return "( f.visibility='PUBLIC' "
	         + "  OR f.owneruuid = '" + userId + "'::uuid "
	         + "  OR EXISTS ( "
	         + "       SELECT 1 "
	         + "       FROM folder_permission fp "
	         + "       WHERE fp.folder_id = f.\"UUID\" "
	         + "         AND fp.user_id = '" + userId + "'::uuid "
	         + "       LIMIT 1 "
	         + "     ) "
	         + ")";
	}
	
	@Override
	public boolean isReadable(UUID folderID, UUID userId) throws SQLException, ObjectNotFoundException {
		String sqlStr = "SELECT (" 
		        + createIsReadableConditionStr(userId) 
		        + ") "
		        + "FROM folder f "
		        + "WHERE f.\"UUID\" = ? "
		        + "  AND f.is_deleted = false";
		
		try (PreparedStatement pst = db.prepareStatement(sqlStr)) {
			pst.setObject(1, folderID);
		
			try (ResultSet rs = pst.executeQuery()) {
				if (rs.next())
					return rs.getBoolean(1);
				throw new ObjectNotFoundException("Folder", folderID);
			}
		}
	}
	
	@Override
	public boolean accessKeyIsValid(UUID folderId, String accessKey) throws SQLException {
		if ( accessKey == null || accessKey.isEmpty())
			return false;
		
		String sqlStr = "select 1 from folder f where (\"UUID\"=? and access_key_is_on and access_key = ?)" ;
		try (PreparedStatement p = db.prepareStatement(sqlStr)) {
			p.setObject(1, folderId);
			p.setString(2, accessKey);
			try ( ResultSet rs = p.executeQuery()) {
				 if (rs.next())
					 return true;
			}		
		}
	    return false;

	}
	
	@Override
	public Folder getFolder(UUID folderId, UUID userId, String accessKey) throws SQLException, ObjectNotFoundException, UnauthorizedOperationException, JsonParseException, JsonMappingException, IOException {
		
		Folder result = new Folder();
		String sqlStr = "select creation_time, modification_time, name, parent, is_deleted from folder where \"UUID\"=?";
		
		try (PreparedStatement p = db.prepareStatement(sqlStr)) {
			p.setObject(1, folderId);
			try ( ResultSet rs = p.executeQuery()) {
				if ( rs.next()) {
					result.setCreationTime(rs.getTimestamp(1));
					result.setModificationTime(rs.getTimestamp(2));
					result.setExternalId(folderId);
					result.setName(rs.getString(3));
					result.setParent((UUID)(rs.getObject(4)));
					result.setIsDeleted(rs.getBoolean(5));
				} else
					throw new ObjectNotFoundException("Folder" + folderId + " not found in db.");
			}
		}
		
		return result;
	}
	
	@Override
	public boolean isFolderOwner(UUID folderId, UUID ownerId) throws SQLException {
		String sqlStr = "select 1 from folder where \"UUID\" = ? and owneruuid = ? and is_deleted=false";
		try (PreparedStatement p = db.prepareStatement(sqlStr)) {
			p.setObject(1, folderId);
			p.setObject(2, ownerId);
			try ( ResultSet rs = p.executeQuery()) {
				return rs.next(); 
			}
		}
	}
	
	@Override
	public void deleteFolder(UUID folderId) throws SQLException {
		Timestamp t = new Timestamp(System.currentTimeMillis());
		
		String sqlStr = "update folder set modification_time = ?, is_deleted = true  where \"UUID\"=?";
				
		try (PreparedStatement pst = db.prepareStatement(sqlStr)) {
			pst.setTimestamp(1, t);
			pst.setObject(2, folderId);
			pst.executeUpdate();
		}
		
	}
	
	@Override
	public void updateFolder(UUID folderId, String name, UUID parentId, UUID ownerId) throws SQLException, JsonProcessingException, NdexException {
		
	    if (name == null && parentId == null) {
	        throw new NdexException("No updates requested (both name and parent are null).");
	    }
	    
		Timestamp t = new Timestamp(System.currentTimeMillis());
	    
	    StringBuilder sb = new StringBuilder("update folder set modification_time=?");
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
	        pst.setObject(idx++, folderId);
	        
	        int updated = pst.executeUpdate();
	        if (updated == 0) {
	            throw new NdexException(
	                "Failed to update folder. Folder " + folderId + " may not exist or is deleted."
	            );
	        }
	    }
	}
	
	@Override
	public FileCount getFolderChildCounts(UUID folderId) throws SQLException {
	    FileCount fc = new FileCount();

	    // Count sub-folders
	    String subfoldersSql = 
	        "SELECT COUNT(*) FROM folder WHERE parent=? AND is_deleted=false";
	    try (PreparedStatement pst = db.prepareStatement(subfoldersSql)) {
	        pst.setObject(1, folderId);
	        try (ResultSet rs = pst.executeQuery()) {
	            if (rs.next()) {
	                fc.setFolder(rs.getLong(1));
	            }
	        }
	    }

	    // Count networks
	    String subNetworksSql = 
	        "SELECT COUNT(*) FROM network WHERE parent=? AND is_deleted=false";
	    try (PreparedStatement pst = db.prepareStatement(subNetworksSql)) {
	        pst.setObject(1, folderId);
	        try (ResultSet rs = pst.executeQuery()) {
	            if (rs.next()) {
	                fc.setNetwork(rs.getLong(1));
	            }
	        }
	    }

	    // Count shortcuts
	    String subShortcutsSql = 
	        "SELECT COUNT(*) FROM shortcut WHERE parent=? AND is_deleted=false";
	    try (PreparedStatement pst = db.prepareStatement(subShortcutsSql)) {
	        pst.setObject(1, folderId);
	        try (ResultSet rs = pst.executeQuery()) {
	            if (rs.next()) {
	                fc.setShortcut(rs.getLong(1));
	            }
	        }
	    }

	    return fc;
	}
	
	public List<FileItemSummary> listItemsInFolder(UUID folderId, boolean compact, FileType type) throws SQLException {
	    return listItemsInFolderOrHome(folderId, compact, false, type);
	}
	
	public List<FileItemSummary> listRootItemsOfUser(UUID ownerId, boolean compact, FileType type) throws SQLException {
	    return listItemsInFolderOrHome(ownerId, compact, true, type);
	}

	/**
	 * Lists items in a folder or in the user's root directory.
	 *
	 * @param contextId UUID to use as the query anchor:
	 *                  - if {@code home} is {@code false}, this is a folder UUID.
	 *                  - if {@code home} is {@code true}, this is a user (owner) UUID.
	 * @param compact if true, return {@code compact}, if false {@code update} form of metadata
	 * @param home if true, return root-level (home) items for the given user
	 * @param type if not null, filter by type (network or folder)
	 * @return list of folders, networks, and shortcuts
	 * @throws SQLException if database access fails
	 */
	private List<FileItemSummary> listItemsInFolderOrHome(UUID contextId, boolean compact, boolean home, FileType type) throws SQLException {
	    List<FileItemSummary> results = new ArrayList<>();
	    final String baseCols = "\"UUID\", name, modification_time, updated_by";

	    /* ────────────── 1) Folders ─────────────── */
	    if (type == null || type == FileType.FOLDER) {
	        String sql = "SELECT " + baseCols + " FROM folder WHERE "
	                   + (home ? "owneruuid=? AND parent IS NULL" : "parent=?") + " AND is_deleted=false";
	        try (PreparedStatement pst = db.prepareStatement(sql)) {
	            pst.setObject(1, contextId);
	            try (ResultSet rs = pst.executeQuery()) {
	                while (rs.next()) {
	                    results.add(new FileItemSummary(
	                        (UUID) rs.getObject(1), FileType.FOLDER,
	                        rs.getString(2), rs.getTimestamp(3), rs.getString(4),
	                        compact ? Map.of() : null));
	                }
	            }
	        }
	    }

	    /* ────────────── 2) Networks ─────────────── */
	    if (type == null || type == FileType.NETWORK) {
	        String sql = "SELECT " + baseCols
	            + (compact ? ", description, edgecount, visibility" : "")
	            + " FROM network WHERE "
	            + (home ? "owneruuid=? AND parent IS NULL" : "parent=?") + " AND is_deleted=false";
	        try (PreparedStatement pst = db.prepareStatement(sql)) {
	            pst.setObject(1, contextId);
	            try (ResultSet rs = pst.executeQuery()) {
	                while (rs.next()) {
	                    Map<String, Object> attr = null;
	                    if (compact) {
	                        attr = new HashMap<>();
	                        attr.put("description", rs.getString(5));
	                        attr.put("edges", rs.getInt(6));
	                        attr.put("visibility", rs.getString(7));
	                    }
	                    results.add(new FileItemSummary(
	                        (UUID) rs.getObject(1), FileType.NETWORK,
	                        rs.getString(2), rs.getTimestamp(3), rs.getString(4),
	                        attr));
	                }
	            }
	        }
	    }

	    /* ────────────── 3) Shortcuts ─────────────── */
	    String sql = "SELECT " + baseCols
	        + ", target_type"
	        + " FROM shortcut WHERE "
	        + (home ? "owneruuid=? AND parent IS NULL" : "parent=?") 
	        + " AND is_deleted=false";
	    if (type != null) {
	        sql += " AND target_type=?";
	    }
	    try (PreparedStatement pst = db.prepareStatement(sql)) {
	        pst.setObject(1, contextId);
	        if (type != null) {
	            pst.setString(2, type.toString());
	        }
	        try (ResultSet rs = pst.executeQuery()) {
	            while (rs.next()) {
	                Map<String, Object> attr = null;
	                if (compact) {
	                    attr = new HashMap<>();
	                    attr.put("target_type", rs.getString(5));
	                }
	                results.add(new FileItemSummary(
	                    (UUID) rs.getObject(1), FileType.SHORTCUT,
	                    rs.getString(2), rs.getTimestamp(3), rs.getString(4),
	                    attr));
	            }
	        }
	    }

	    return results;
	}
	
	@Override
	public List<Folder> listFoldersOfUser(UUID ownerId, int limit) throws SQLException {
	    List<Folder> result = new ArrayList<>();

	    String sql = "SELECT \"UUID\", name, parent, creation_time, modification_time, is_deleted " +
	                 " FROM folder " +
	                 " WHERE owneruuid=? AND is_deleted=false " +
	                 " ORDER BY name " +
	                 " LIMIT ?";

	    try (PreparedStatement pst = db.prepareStatement(sql)) {
	        pst.setObject(1, ownerId);
	        pst.setInt(2, limit);

	        try (ResultSet rs = pst.executeQuery()) {
	            while (rs.next()) {
	                Folder f = new Folder();
	                f.setExternalId((UUID) rs.getObject("UUID"));
	                f.setName(rs.getString("name"));
	                f.setParent((UUID) rs.getObject("parent"));
	                f.setCreationTime(rs.getTimestamp("creation_time"));
	                f.setModificationTime(rs.getTimestamp("modification_time"));
	                f.setIsDeleted(rs.getBoolean("is_deleted"));

	                result.add(f);
	            }
	        }
	    }

	    return result;
	}
	
	/**
	 * Adds new or updates a permission row. 
	 * @return 
	 */
	public NdexObjectUpdateStatus setFolderPermission(UUID folderId, UUID userId, Permissions permission) throws SQLException, NdexException {
	    String sql = 
	        "INSERT INTO folder_permission (folder_id, user_id, permission) " +
	        "VALUES (?, ?, ?) " +
	        "ON CONFLICT (folder_id, user_id) DO UPDATE SET permission = EXCLUDED.permission";

	    try (PreparedStatement pst = db.prepareStatement(sql)) {
	        pst.setObject(1, folderId);
	        pst.setObject(2, userId);
	        pst.setString(3, permission.toString());
	        pst.executeUpdate();
	    }
	    
	    Timestamp t = new Timestamp(System.currentTimeMillis());
		NdexObjectUpdateStatus result = new NdexObjectUpdateStatus();
		result.setModificationTime(t);
		return result;
	}

	public void removeFolderPermission(UUID folderId, UUID userId) throws SQLException {
	    String sql = "DELETE FROM folder_permission WHERE folder_id = ? AND user_id = ?";
	    try (PreparedStatement pst = db.prepareStatement(sql)) {
	        pst.setObject(1, folderId);
	        pst.setObject(2, userId);
	        pst.executeUpdate();
	    }
	}
	
	public Map<String, String> getFolderPermissions(UUID folderId) throws SQLException {
	    Map<String, String> permissionsMap = new HashMap<>();
	    String sql = "SELECT user_id, permission FROM folder_permission WHERE folder_id=?";
	    try (PreparedStatement pst = db.prepareStatement(sql)) {
	        pst.setObject(1, folderId);
	        try (ResultSet rs = pst.executeQuery()) {
	            while (rs.next()) {
	                permissionsMap.put(
	                    rs.getObject("user_id").toString(),
	                    rs.getString("permission")
	                );
	            }
	        }
	    }
	    return permissionsMap;
	}
	
	public String enableFolderAccessKey(UUID folderId) throws SQLException, NdexException {
	    String oldKey = null;
	    boolean keyIsOn = false;

	    String selectSql = "SELECT access_key, access_key_is_on FROM folder WHERE \"UUID\"=? AND is_deleted=false";
	    try (PreparedStatement pst = db.prepareStatement(selectSql)) {
	        pst.setObject(1, folderId);
	        try (ResultSet rs = pst.executeQuery()) {
	            if (!rs.next()) {
	                throw new NdexException("Folder " + folderId + " not found or is deleted.");
	            }
	            oldKey = rs.getString("access_key");
	            keyIsOn = rs.getBoolean("access_key_is_on");
	        }
	    }

	    if (keyIsOn && oldKey != null && !oldKey.isEmpty()) {
	        // Already shared, just return existing key
	        return oldKey;
	    }

	    // If we need to generate a new key:
	    if (oldKey == null || oldKey.isEmpty()) {
	        oldKey = generateRandomKey();
	    }

	    String updateSql = "UPDATE folder SET access_key=?, access_key_is_on=true WHERE \"UUID\"=?";
	    try (PreparedStatement pst = db.prepareStatement(updateSql)) {
	        pst.setString(1, oldKey);
	        pst.setObject(2, folderId);
	        pst.executeUpdate();
	    }

	    return oldKey;
	}

	public void disableFolderAccessKey(UUID folderId) throws SQLException, NdexException {
	    String sql = "UPDATE folder SET access_key_is_on=false WHERE \"UUID\"=? AND is_deleted=false";
	    try (PreparedStatement pst = db.prepareStatement(sql)) {
	        pst.setObject(1, folderId);
	        int updated = pst.executeUpdate();
	        if (updated == 0) {
	            throw new NdexException("Folder " + folderId + " not found or is deleted.");
	        }
	    }
	}

	public void transferFolder(UUID folderId, UUID newOwnerId) throws SQLException, NdexException {
	    String sql = "UPDATE folder SET owneruuid=? WHERE \"UUID\"=? AND is_deleted=false";
	    try (PreparedStatement pst = db.prepareStatement(sql)) {
	        pst.setObject(1, newOwnerId);
	        pst.setObject(2, folderId);
	        int updated = pst.executeUpdate();
	        if (updated == 0) {
	            throw new NdexException("Folder " + folderId + " not found, or is deleted.");
	        }
	    }
	}

	private String generateRandomKey() {
	    // TODO: decide how the key should look, now it is 16 random bytes
	    byte[] randomBytes = new byte[16];
	    new SecureRandom().nextBytes(randomBytes);
	    return DatatypeConverter.printHexBinary(randomBytes).toLowerCase();
	}
	
    public List<UUID> listSharedFolderIds(UUID userId) throws SQLException {
        String sql = 
            "SELECT f.\"UUID\" " +
            "FROM folder_permission fp " +
            "JOIN folder f ON f.\"UUID\" = fp.folder_id " +
            "WHERE fp.user_id=? " +
            "  AND f.owneruuid<>? " +
            "  AND f.is_deleted=false " +
            "  AND (fp.permission='read' OR fp.permission='edit')";
        
        List<UUID> folderIds = new ArrayList<>();
        try (PreparedStatement pst = db.prepareStatement(sql)) {
            pst.setObject(1, userId);
            pst.setObject(2, userId);
            try (ResultSet rs = pst.executeQuery()) {
                while (rs.next()) {
                    folderIds.add((UUID) rs.getObject(1));
                }
            }
        }
        return folderIds;
    }
    
    public void setFolderVisibility(UUID folderId, VisibilityType visibility) throws SQLException, NdexException {
        String sql = "UPDATE folder SET visibility = ? WHERE \"UUID\" = ? AND is_deleted = false";
        try (PreparedStatement pst = db.prepareStatement(sql)) {
            pst.setString(1, visibility.toString());
            pst.setObject(2, folderId);
            int updated = pst.executeUpdate();
            if (updated != 1) {
                throw new NdexException("Failed to update visibility for folder " + folderId);
            }
        }
    }


}
