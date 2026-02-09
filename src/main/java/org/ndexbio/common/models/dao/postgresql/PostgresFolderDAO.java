package org.ndexbio.common.models.dao.postgresql;

import java.io.IOException;
import java.security.SecureRandom;
import java.sql.Array;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.sql.Types;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.logging.Logger;
import java.util.Arrays;

import org.ndexbio.common.models.dao.FolderDAO;
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
import org.ndexbio.model.object.ShortcutTargetStatus;

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
	public NdexObjectUpdateStatus createFolder(final UUID folderUUID, final UUID ownerId, final UUID parentUUID, final String name, final String description) throws SQLException {
		Timestamp t = new Timestamp(System.currentTimeMillis());
		
		String sqlStr = "insert into folder (\"UUID\", creation_time, modification_time, is_deleted, name, visibility, owneruuid, access_key_is_on, parent, description) values"
				+ "(?, ?, ?, false, ?, 'PRIVATE',?, false, ?, ?) ";
		try (PreparedStatement pst = db.prepareStatement(sqlStr)) {
			pst.setObject(1, folderUUID);
			pst.setTimestamp(2, t);
			pst.setTimestamp(3, t);
			pst.setString(4, name);
			pst.setObject(5, ownerId);
			pst.setObject(6, parentUUID);
			pst.setString(7, description);

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
	public NdexFolder getFolder(UUID folderId, UUID userId, String accessKey) throws SQLException, ObjectNotFoundException, UnauthorizedOperationException, JsonParseException, JsonMappingException, IOException {
		
		NdexFolder result = new NdexFolder();
		String sqlStr = "select creation_time, modification_time, name, parent, is_deleted, description from folder where \"UUID\"=?";

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
					result.setDescription(rs.getString(6));
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
	
	private List<UUID> getDescendantFolders(UUID folderId) throws SQLException {
	    List<UUID> descendants = new ArrayList<>();
	    getDescendantFoldersRecursive(folderId, descendants);
	    return descendants;
	}

	private void getDescendantFoldersRecursive(UUID parentId, List<UUID> descendants) throws SQLException {
	    String sql = "SELECT \"UUID\" FROM folder WHERE parent = ? AND is_deleted = false";
	    try (PreparedStatement pst = db.prepareStatement(sql)) {
	        pst.setObject(1, parentId);
	        try (ResultSet rs = pst.executeQuery()) {
	            while (rs.next()) {
	                UUID childId = (UUID) rs.getObject(1);
	                descendants.add(childId);
	                getDescendantFoldersRecursive(childId, descendants);
	            }
	        }
	    }
	}

	@Override
	public void deleteFolder(UUID folderId, boolean force, boolean permanent) throws SQLException {
	    Timestamp t = new Timestamp(System.currentTimeMillis());
	    
	    if (!force) {
	        // Check if folder is empty
	        FileCount counts = getFolderChildCounts(folderId);
	        if (counts.getFolder() > 0 || counts.getNetwork() > 0 || counts.getShortcut() > 0) {
	            throw new SQLException("Folder is not empty. Use force=true to delete non-empty folders.");
	        }
	    }
	    
	    if (permanent) {
	        if (force) {
	        	
	            // Get all descendant folders recursively
	            List<UUID> descendantFolders = getDescendantFolders(folderId);
	            int totalPlaceholders = 1 + descendantFolders.size(); // 1 for folderId + rest

	            String placeholders = String.join(",", Collections.nCopies(totalPlaceholders, "?"));
	            
	            // Get all networks in the folder tree for permission cleanup
	            List<UUID> networkIds = new ArrayList<>();
	            String getNetworksSql = "SELECT \"UUID\" FROM network WHERE parent IN (" + placeholders + ")";
	            try (PreparedStatement pst = db.prepareStatement(getNetworksSql)) {
	                pst.setObject(1, folderId);
	                for (int i = 0; i < descendantFolders.size(); i++) {
	                    pst.setObject(i + 2, descendantFolders.get(i));
	                }
	                try (ResultSet rs = pst.executeQuery()) {
	                    while (rs.next()) {
	                        networkIds.add((UUID) rs.getObject(1));
	                    }
	                }
	            }
	            
	            // Delete network permissions for all networks in the folder tree
	            if (!networkIds.isEmpty()) {
	                String networkPlaceholders = String.join(",", Collections.nCopies(networkIds.size(), "?"));
	                String deleteNetworkPermissionsSql = "DELETE FROM user_network_membership WHERE network_id IN (" + networkPlaceholders + ")";
	                try (PreparedStatement pst = db.prepareStatement(deleteNetworkPermissionsSql)) {
	                    for (int i = 0; i < networkIds.size(); i++) {
	                        pst.setObject(i + 1, networkIds.get(i));
	                    }
	                    pst.executeUpdate();
	                }
	            }
	            
	            // Delete folder permissions for all folders in the tree
	            String deleteFolderPermissionsSql = "DELETE FROM folder_permission WHERE folder_id IN (" + placeholders + ")";
	            try (PreparedStatement pst = db.prepareStatement(deleteFolderPermissionsSql)) {
	                pst.setObject(1, folderId);
	                for (int i = 0; i < descendantFolders.size(); i++) {
	                    pst.setObject(i + 2, descendantFolders.get(i));
	                }
	                pst.executeUpdate();
	            }
	            
	            // Delete all networks in the folder tree
	            String deleteNetworksSql = "DELETE FROM network WHERE parent IN (" + placeholders + ")";
	            try (PreparedStatement pst = db.prepareStatement(deleteNetworksSql)) {
	                pst.setObject(1, folderId);
	                for (int i = 0; i < descendantFolders.size(); i++) {
	                    pst.setObject(i + 2, descendantFolders.get(i));
	                }
	                pst.executeUpdate();
	            }
	            
	            // Delete all shortcuts in the folder tree
	            String deleteShortcutsSql = "DELETE FROM shortcut WHERE parent IN (" + placeholders + ")";
	            try (PreparedStatement pst = db.prepareStatement(deleteShortcutsSql)) {
	                pst.setObject(1, folderId);
	                for (int i = 0; i < descendantFolders.size(); i++) {
	                    pst.setObject(i + 2, descendantFolders.get(i));
	                }
	                pst.executeUpdate();
	            }
	            
	            // Delete all subfolders
	            String deleteSubfoldersSql = "DELETE FROM folder WHERE \"UUID\" IN (" + placeholders + ")";
	            try (PreparedStatement pst = db.prepareStatement(deleteSubfoldersSql)) {
	                pst.setObject(1, folderId);
	                for (int i = 0; i < descendantFolders.size(); i++) {
	                    pst.setObject(i + 2, descendantFolders.get(i));
	                }
	                pst.executeUpdate();
	            }
	        } else {
	            // Delete folder permissions for just this folder
	            String deleteFolderPermissionsSql = "DELETE FROM folder_permission WHERE folder_id = ?";
	            try (PreparedStatement pst = db.prepareStatement(deleteFolderPermissionsSql)) {
	                pst.setObject(1, folderId);
	                pst.executeUpdate();
	            }
	            
	            // Just delete the folder itself
	            String deleteFolderSql = "DELETE FROM folder WHERE \"UUID\"=?";
	            try (PreparedStatement pst = db.prepareStatement(deleteFolderSql)) {
	                pst.setObject(1, folderId);
	                pst.executeUpdate();
	            }
	        }
	    } else {
	        if (force) {
	            // Get all descendant folders recursively
	            List<UUID> descendantFolders = getDescendantFolders(folderId);
	            int totalPlaceholders = 1 + descendantFolders.size(); // 1 for folderId + rest

	            String placeholders = String.join(",", Collections.nCopies(totalPlaceholders, "?"));
	            
	            // Mark all networks as deleted
	            String markNetworksSql = "UPDATE network SET modification_time = ?, is_deleted = true " +
	                "WHERE parent IN (" + placeholders + ")";
	            try (PreparedStatement pst = db.prepareStatement(markNetworksSql)) {
	                pst.setTimestamp(1, t);
	                pst.setObject(2, folderId);
	                for (int i = 0; i < descendantFolders.size(); i++) {
	                    pst.setObject(i + 3, descendantFolders.get(i));
	                }
	                pst.executeUpdate();
	            }
	            
	            // Mark all shortcuts as deleted
	            String markShortcutsSql = "UPDATE shortcut SET modification_time = ?, is_deleted = true " +
	            	"WHERE parent IN (" + placeholders + ")";
	            try (PreparedStatement pst = db.prepareStatement(markShortcutsSql)) {
	                pst.setTimestamp(1, t);
	                pst.setObject(2, folderId);
	                for (int i = 0; i < descendantFolders.size(); i++) {
	                    pst.setObject(i + 3, descendantFolders.get(i));
	                }
	                pst.executeUpdate();
	            }
	            
	            // Mark all child folders as deleted
	            String markFoldersSql = "UPDATE folder SET modification_time = ?, is_deleted = true " +
	            	"WHERE parent IN (" + placeholders + ")";
	            try (PreparedStatement pst = db.prepareStatement(markFoldersSql)) {
	                pst.setTimestamp(1, t);
	                pst.setObject(2, folderId);
	                for (int i = 0; i < descendantFolders.size(); i++) {
	                    pst.setObject(i + 3, descendantFolders.get(i));
	                }
	                pst.executeUpdate();
	            }
	        }
            // Mark the folder as deleted and show in Trash
            String sqlStr = "UPDATE folder SET modification_time = ?, is_deleted = true, show_in_trash = true WHERE \"UUID\"=?";
            try (PreparedStatement pst = db.prepareStatement(sqlStr)) {
                pst.setTimestamp(1, t);
                pst.setObject(2, folderId);
                pst.executeUpdate();
	        }
	    }
	}
	
	@Override
	public void updateFolder(UUID folderId, String name, UUID parentId, UUID ownerId, String description) throws SQLException, JsonProcessingException, NdexException {
	    
		Timestamp t = new Timestamp(System.currentTimeMillis());
	    
	    StringBuilder sb = new StringBuilder("update folder set modification_time=?");
	    if (name != null) {
	        sb.append(", name=?");
	    }
	    sb.append(", parent=?");
	    if (description != null) {
	        sb.append(", description=?");
	    }
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
	        if (description != null) {
	            pst.setString(idx++, description);
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
	
	@Override
	public List<FileItemSummary> listItemsInFolder(UUID folderId, boolean compact, FileType type) throws SQLException {
	    return listItemsInFolderOrHome(folderId, compact, false, type);
	}
	
	@Override
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
	    /* ────────────── 1) Folders ─────────────── */
	    if (type == null || type == FileType.FOLDER) {
	        StringBuilder folderSql = new StringBuilder();
        folderSql.append("SELECT f.\"UUID\", f.name, f.modification_time, f.updated_by");
        if (compact) {
            folderSql.append(", f.description, f.visibility");
        }
        folderSql.append(", f.owneruuid AS owner_id, u.user_name AS owner_name");
        folderSql.append(", EXISTS (SELECT 1 FROM folder_permission fp WHERE fp.folder_id = f.\"UUID\" AND fp.user_id <> f.owneruuid LIMIT 1) AS is_shared ");
        folderSql.append("FROM folder f JOIN ndex_user u ON f.owneruuid = u.\"UUID\" WHERE ");
	        folderSql.append(home ? "f.owneruuid=? AND f.parent IS NULL" : "f.parent=?");
	        folderSql.append(" AND f.is_deleted=false");
	        try (PreparedStatement pst = db.prepareStatement(folderSql.toString())) {
	            pst.setObject(1, contextId);
	            try (ResultSet rs = pst.executeQuery()) {
	                while (rs.next()) {
	                    Map<String, Object> attr = null;
	                    if (compact) {
                        attr = new HashMap<>();
                        attr.put("description", rs.getString("description"));
                    }
                    FileItemSummary summary = new FileItemSummary(
                        (UUID) rs.getObject("UUID"), FileType.FOLDER,
                        rs.getString("name"), rs.getTimestamp("modification_time"), rs.getString("updated_by"),
                        attr);
                    summary.setOwnerId((UUID) rs.getObject("owner_id"));
                    summary.setOwner(rs.getString("owner_name"));
                    if (compact) {
                        summary.setVisibility(rs.getString("visibility"));
                    }
                    summary.setIsShared(rs.getBoolean("is_shared"));
                    results.add(summary);
	                }
	            }
	        }
	    }

	    /* ────────────── 2) Networks ─────────────── */
	    if (type == null || type == FileType.NETWORK) {
	        StringBuilder networkSql = new StringBuilder();
        networkSql.append("SELECT n.\"UUID\", n.name, n.modification_time, n.updated_by, ");
        networkSql.append("n.readonly, n.error, n.warnings, n.iscomplete, n.is_validated, n.ndexdoi");
        if (compact) {
            networkSql.append(", n.description, n.edgecount, n.visibility");
        }
        networkSql.append(", n.owneruuid AS owner_id, u.user_name AS owner_name");
        networkSql.append(", EXISTS (SELECT 1 FROM user_network_membership nm WHERE nm.network_id = n.\"UUID\" AND nm.user_id <> n.owneruuid LIMIT 1) AS is_shared ");
        networkSql.append("FROM network n JOIN ndex_user u ON n.owneruuid = u.\"UUID\" WHERE ");
	        networkSql.append(home ? "n.owneruuid=? AND n.parent IS NULL" : "n.parent=?");
	        networkSql.append(" AND n.is_deleted=false");
	        try (PreparedStatement pst = db.prepareStatement(networkSql.toString())) {
	            pst.setObject(1, contextId);
	            try (ResultSet rs = pst.executeQuery()) {
	                while (rs.next()) {
	                    Map<String, Object> attr = null;
	                    if (compact) {
	                        attr = new HashMap<>();
	                        attr.put("description", rs.getString("description"));
	                    }

	                    Boolean isReadOnly = null;
	                    boolean readOnlyValue = rs.getBoolean("readonly");
	                    if (!rs.wasNull()) {
	                        isReadOnly = readOnlyValue;
	                    }

	                    String errorMessage = rs.getString("error");

	                    List<String> warnings = null;
	                    Array warningsArray = rs.getArray("warnings");
	                    if (warningsArray != null) {
	                        try {
	                            warnings = Arrays.asList((String[]) warningsArray.getArray());
	                        } finally {
	                            warningsArray.free();
	                        }
	                    }

	                    Boolean isCompleted = null;
	                    boolean completedValue = rs.getBoolean("iscomplete");
	                    if (!rs.wasNull()) {
	                        isCompleted = completedValue;
	                    }

                    FileItemSummary summary = new FileItemSummary(
                        (UUID) rs.getObject("UUID"), FileType.NETWORK,
                        rs.getString("name"), rs.getTimestamp("modification_time"), rs.getString("updated_by"), attr,
                        isReadOnly, errorMessage, warnings, isCompleted);
                    summary.setOwnerId((UUID) rs.getObject("owner_id"));
                    summary.setOwner(rs.getString("owner_name"));
                    if (compact) {
                        summary.setEdges((Integer) rs.getObject("edgecount"));
                        summary.setVisibility(rs.getString("visibility"));
                    }
	                    summary.setIsShared(rs.getBoolean("is_shared"));
	                    boolean isValidValue = rs.getBoolean("is_validated");
	                    if (!rs.wasNull()) {
	                        summary.setIsValid(isValidValue);
	                    } else {
	                        summary.setIsValid(null);
	                    }
	                    summary.setDoi(rs.getString("ndexdoi"));
	                    results.add(summary);
	                }
	            }
	        }
	    }

	    /* ────────────── 3) Shortcuts ─────────────── */
    String sql = "SELECT s.\"UUID\", s.name, s.modification_time, s.updated_by, s.visibility, s.owneruuid AS owner_id, u.user_name AS owner_name, "
        + "s.target_type, s.target, f.is_deleted AS target_folder_deleted, n.is_deleted AS target_network_deleted, "
        + "n.edgecount AS network_edgecount "
        + "FROM shortcut s "
        + "JOIN ndex_user u ON s.owneruuid = u.\"UUID\" "
        + "LEFT JOIN folder f ON s.target_type = 'FOLDER' AND s.target = f.\"UUID\" "
        + "LEFT JOIN network n ON s.target_type = 'NETWORK' AND s.target = n.\"UUID\" "
        + "WHERE "
	        + (home ? "s.owneruuid=? AND s.parent IS NULL" : "s.parent=?")
	        + " AND s.is_deleted=false";
	    if (type != null) {
	        sql += " AND s.target_type=?";
	    }
	    try (PreparedStatement pst = db.prepareStatement(sql)) {
	        pst.setObject(1, contextId);
	        if (type != null) {
	            pst.setString(2, type.toString());
	        }
	        try (ResultSet rs = pst.executeQuery()) {
	            while (rs.next()) {
	                Map<String, Object> attr = null;
	                Integer edgecount = null;
	                if (compact) {
	                    attr = new HashMap<>();
	                    String targetType = rs.getString("target_type");
	                    UUID targetId = (UUID) rs.getObject("target");
	                    attr.put("target_type", targetType);
	                    attr.put("target", targetId);

	                    ShortcutTargetStatus targetStatus = ShortcutTargetStatus.DELETED;
	                    if (targetId != null && targetType != null) {
	                        if ("FOLDER".equals(targetType)) {
	                            Boolean deleted = (Boolean) rs.getObject("target_folder_deleted");
	                            if (deleted != null) {
	                                targetStatus = deleted ? ShortcutTargetStatus.IN_TRASH : ShortcutTargetStatus.ACTIVE;
	                            }
	                        } else if ("NETWORK".equals(targetType)) {
	                            Boolean deleted = (Boolean) rs.getObject("target_network_deleted");
	                            if (deleted != null) {
	                                targetStatus = deleted ? ShortcutTargetStatus.IN_TRASH : ShortcutTargetStatus.ACTIVE;
	                            }
	                            edgecount = (Integer) rs.getObject("network_edgecount");
	                        }
	                    }
	                    attr.put("target_status", targetStatus.toString());
	                }
                FileItemSummary summary = new FileItemSummary(
                    (UUID) rs.getObject("UUID"), FileType.SHORTCUT,
                    rs.getString("name"), rs.getTimestamp("modification_time"), rs.getString("updated_by"),
                    attr);
                summary.setVisibility(rs.getString("visibility"));
                summary.setOwnerId((UUID) rs.getObject("owner_id"));
                summary.setOwner(rs.getString("owner_name"));
                if (edgecount != null) {
                    summary.setEdges(edgecount);
                }
	                results.add(summary);
	            }
	        }
	    }

	    return results;
	}
	
	@Override
	public List<NdexFolder> listFoldersOfUser(UUID ownerId, int limit) throws SQLException {
	    List<NdexFolder> result = new ArrayList<>();

	    String sql = "SELECT \"UUID\", name, parent, creation_time, modification_time, is_deleted, description " +
	                 " FROM folder " +
	                 " WHERE owneruuid=? AND is_deleted=false " +
	                 " ORDER BY name " +
	                 " LIMIT ?";

	    try (PreparedStatement pst = db.prepareStatement(sql)) {
	        pst.setObject(1, ownerId);
	        pst.setInt(2, limit);

	        try (ResultSet rs = pst.executeQuery()) {
	            while (rs.next()) {
	                NdexFolder f = new NdexFolder();
	                f.setExternalId((UUID) rs.getObject("UUID"));
	                f.setName(rs.getString("name"));
	                f.setParent((UUID) rs.getObject("parent"));
	                f.setCreationTime(rs.getTimestamp("creation_time"));
	                f.setModificationTime(rs.getTimestamp("modification_time"));
	                f.setIsDeleted(rs.getBoolean("is_deleted"));
					f.setDescription(rs.getString("description"));

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
	@Override
	public NdexObjectUpdateStatus setFolderPermission(UUID folderId, UUID userId, Permissions permission) throws SQLException, NdexException {
	    // Get all descendant folders
	    List<UUID> descendantFolders = getDescendantFolders(folderId);
	    
	    // Create placeholders for the SQL query
	    String placeholders = String.join(",", Collections.nCopies(1 + descendantFolders.size(), "(?, ?, ?)"));
	    
	    // Insert/update permissions for the parent folder and all descendants
	    String sql = 
	        "INSERT INTO folder_permission (folder_id, user_id, permission) " +
	        "VALUES " + placeholders + " " +
	        "ON CONFLICT (folder_id, user_id) DO UPDATE SET permission = EXCLUDED.permission";

	    try (PreparedStatement pst = db.prepareStatement(sql)) {
	        int paramIndex = 1;
	        // Add parent folder
	        pst.setObject(paramIndex++, folderId);
	        pst.setObject(paramIndex++, userId);
	        pst.setString(paramIndex++, permission.toString());
	        
	        // Add all descendant folders
	        for (UUID descendantId : descendantFolders) {
	            pst.setObject(paramIndex++, descendantId);
	            pst.setObject(paramIndex++, userId);
	            pst.setString(paramIndex++, permission.toString());
	        }
	        pst.executeUpdate();
	    }

	    // Get all networks in the folder tree
	    String networkSql = "SELECT n.\"UUID\" FROM network n " +
	                       "WHERE n.parent IN (" + String.join(",", Collections.nCopies(1 + descendantFolders.size(), "?")) + ")";
	    try (PreparedStatement pst = db.prepareStatement(networkSql)) {
	        int paramIndex = 1;
	        // Add parent folder
	        pst.setObject(paramIndex++, folderId);
	        
	        // Add all descendant folders
	        for (UUID descendantId : descendantFolders) {
	            pst.setObject(paramIndex++, descendantId);
	        }
	        
	        try (ResultSet rs = pst.executeQuery()) {
	            while (rs.next()) {
	                UUID networkId = (UUID) rs.getObject(1);
	                String networkPermissionSql =  "insert into user_network_membership ( user_id,network_id, permission_type) values (?,?,'"+ permission.toString() + "') "
    				+ "ON CONFLICT (user_id,network_id) DO UPDATE set permission_type = EXCLUDED.permission_type";
	                try (PreparedStatement pst2 = db.prepareStatement(networkPermissionSql)) {
	                    pst2.setObject(1, userId);
	                    pst2.setObject(2, networkId);
	                    pst2.executeUpdate();
	                }
	            }
	        }
	    }
	    
	    Timestamp t = new Timestamp(System.currentTimeMillis());
	    NdexObjectUpdateStatus result = new NdexObjectUpdateStatus();
	    result.setModificationTime(t);
	    return result;
	}

	@Override
	public void removeFolderPermission(UUID folderId, UUID userId) throws SQLException {
	    // Get all descendant folders
	    List<UUID> descendantFolders = getDescendantFolders(folderId);
	    
	    // Create placeholders for the SQL query
	    String placeholders = String.join(",", Collections.nCopies(1 + descendantFolders.size(), "?"));
	    
	    // Delete permissions for the parent folder and all descendants
	    String sql = "DELETE FROM folder_permission WHERE folder_id IN (" + placeholders + ") AND user_id = ?";
	    try (PreparedStatement pst = db.prepareStatement(sql)) {
	        int paramIndex = 1;
	        // Add parent folder
	        pst.setObject(paramIndex++, folderId);
	        
	        // Add all descendant folders
	        for (UUID descendantId : descendantFolders) {
	            pst.setObject(paramIndex++, descendantId);
	        }
	        
	        // Add user ID
	        pst.setObject(paramIndex, userId);
	        pst.executeUpdate();
	    }

	    // Remove permissions from all networks in the folder tree
	    String networkSql = "SELECT n.\"UUID\" FROM network n " +
	                       "WHERE n.parent IN (" + placeholders + ")";
	    try (PreparedStatement pst = db.prepareStatement(networkSql)) {
	        int paramIndex = 1;
	        // Add parent folder
	        pst.setObject(paramIndex++, folderId);
	        
	        // Add all descendant folders
	        for (UUID descendantId : descendantFolders) {
	            pst.setObject(paramIndex++, descendantId);
	        }
	        
	        try (ResultSet rs = pst.executeQuery()) {
	            while (rs.next()) {
	                UUID networkId = (UUID) rs.getObject(1);
	                String networkPermissionSql = "DELETE FROM user_network_membership WHERE user_id = ? AND network_id = ?";
	                try (PreparedStatement pst2 = db.prepareStatement(networkPermissionSql)) {
	                    pst2.setObject(1, userId);
	                    pst2.setObject(2, networkId);
	                    pst2.executeUpdate();
	                }
	            }
	        }
	    }
	}
	
	@Override
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

	@Override
	public String getFolderAccessKey(UUID folderId) throws SQLException, ObjectNotFoundException {
        String sql = "SELECT access_key, access_key_is_on FROM folder WHERE \"UUID\"=? AND is_deleted=false";
        try (PreparedStatement pst = db.prepareStatement(sql)) {
            pst.setObject(1, folderId);
            try (ResultSet rs = pst.executeQuery()) {
                if (!rs.next()) {
                    throw new ObjectNotFoundException("Folder", folderId);
                }
                String key = rs.getString("access_key");
                boolean keyIsOn = rs.getBoolean("access_key_is_on");
                return keyIsOn ? key : null;
            }
        }
	}
	
	@Override
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

	@Override
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

	@Override
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
    
    @Override
    public List<FileItemSummary> listSharedFolders(UUID userId) throws SQLException {
        String sql = "SELECT f.\"UUID\" AS folder_id, f.name, f.modification_time, f.updated_by, f.description, " +
                    "       f.owneruuid AS owner_id, u.user_name AS owner_name, fp.permission AS permission " +
                    "FROM folder_permission fp " +
                    "JOIN folder f ON f.\"UUID\" = fp.folder_id " +
                    "JOIN ndex_user u ON f.owneruuid = u.\"UUID\" " +
                    "WHERE fp.user_id=? " +
                    "  AND f.owneruuid<>? " +
                    "  AND f.is_deleted=false";
        
        List<FileItemSummary> result = new ArrayList<>();
        try (PreparedStatement pst = db.prepareStatement(sql)) {
            pst.setObject(1, userId);
            pst.setObject(2, userId);
            try (ResultSet rs = pst.executeQuery()) {
                while (rs.next()) {
                    FileItemSummary folderInfo = new FileItemSummary();
					Map<String, Object> attr = new HashMap<>();
					attr.put("description", rs.getString("description"));

                    folderInfo.setUuid((UUID) rs.getObject("folder_id"));
                    folderInfo.setType(FileType.FOLDER);
					folderInfo.setName(rs.getString("name"));
					folderInfo.setModificationTime(rs.getTimestamp("modification_time"));
					folderInfo.setUpdatedBy(rs.getString("updated_by"));
                    folderInfo.setOwnerId((UUID) rs.getObject("owner_id"));
                    folderInfo.setOwner(rs.getString("owner_name"));
                    folderInfo.setPermission(rs.getString("permission"));
					folderInfo.setAttributes(attr);
                    result.add(folderInfo);
                }
            }
        }
        return result;
    }
    
    public List<FileItemSummary> listFoldersSharedBySpecificUser(UUID userId, UUID ownerId, boolean compact) throws SQLException {
        String sql = "SELECT DISTINCT f.\"UUID\", f.name, f.modification_time, f.updated_by, f.description " +
                "FROM folder f " +
                "LEFT JOIN folder_permission fp ON f.\"UUID\" = fp.folder_id AND fp.user_id = ? " +
                "WHERE f.owneruuid = ? " +
                "  AND f.parent IS NULL " +
                "  AND f.is_deleted = false " +
                "  AND (f.visibility = 'PUBLIC' OR fp.user_id IS NOT NULL)";

	   List<FileItemSummary> result = new ArrayList<>();
	   try (PreparedStatement pst = db.prepareStatement(sql)) {
	       pst.setObject(1, userId);
	       pst.setObject(2, ownerId);
	       try (ResultSet rs = pst.executeQuery()) {
	           while (rs.next()) {
					Map<String, Object> attr = null;
					if (compact) {
						attr = new HashMap<>();
						attr.put("description", rs.getString(5));
					}
	               result.add(new FileItemSummary(
	                   (UUID) rs.getObject(1), FileType.FOLDER,
	                   rs.getString(2), rs.getTimestamp(3), rs.getString(4),
	                   attr
	               ));
	           }
	       }
	   }
	   return result;
	}
    
    @Override
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
	@Override
	public VisibilityType getFolderVisibility(UUID folderId) throws SQLException, NdexException {
		String sql = "SELECT visibility FROM folder WHERE \"UUID\" = ? AND is_deleted = false";
		try (PreparedStatement pst = db.prepareStatement(sql)) {
			pst.setObject(1, folderId);
			try (ResultSet rs = pst.executeQuery()) {
				if (rs.next()) {
					return VisibilityType.valueOf(rs.getString("visibility"));
				}
				throw new NdexException("Folder not found: " + folderId);
			}
		}
	}

	@Override
	public boolean isDescendantOf(UUID folderId, UUID potentialDescendantId) throws SQLException {
		// Use a recursive CTE to check if potentialDescendantId is a descendant of folderId
		String sql = "WITH RECURSIVE folder_tree AS (" +
			"  SELECT \"UUID\", parent FROM folder WHERE \"UUID\" = ? AND is_deleted = false " +
			"  UNION ALL " +
			"  SELECT f.\"UUID\", f.parent FROM folder f " +
			"  JOIN folder_tree ft ON f.parent = ft.\"UUID\" " +
			"  WHERE f.is_deleted = false" +
			") SELECT 1 FROM folder_tree WHERE \"UUID\" = ? LIMIT 1";
		
		try (PreparedStatement pst = db.prepareStatement(sql)) {
			pst.setObject(1, folderId);
			pst.setObject(2, potentialDescendantId);
			try (ResultSet rs = pst.executeQuery()) {
				return rs.next(); // Returns true if potentialDescendantId is found in the tree
			}
		}
	}
	
	public List<FileItemSummary> listPublicRootItemsOfUser(UUID ownerId, boolean compact) throws SQLException {
		return listPublicRootItemsOfUser(ownerId, compact, null);
	}
	
	public List<FileItemSummary> listPublicRootItemsOfUser(UUID ownerId, boolean compact, FileType fileType) throws SQLException {
	    List<FileItemSummary> result = new ArrayList<>();

	    final String baseCols = "\"UUID\", name, modification_time, updated_by";

	    // Folders
		if (fileType == null || fileType == FileType.FOLDER) {
    String sqlFolders = "SELECT " + baseCols + (compact ? ", description, visibility" : "") + " FROM folder " +
                        "WHERE owneruuid = ? AND parent IS NULL AND visibility = 'PUBLIC' AND is_deleted = false";
    try (PreparedStatement pst = db.prepareStatement(sqlFolders)) {
        pst.setObject(1, ownerId);
        try (ResultSet rs = pst.executeQuery()) {
            while (rs.next()) {
                Map<String, Object> attr = null;
                if (compact) {
                    attr = new HashMap<>();
                    attr.put("description", rs.getString("description"));
                }
                FileItemSummary summary = new FileItemSummary(
                    (UUID) rs.getObject("UUID"), FileType.FOLDER,
                    rs.getString("name"), rs.getTimestamp("modification_time"), rs.getString("updated_by"),
                    attr);
                if (compact) {
                    summary.setVisibility(rs.getString("visibility"));
                }
                result.add(summary);
            }
        }
    }
		}

	    // Networks
		if (fileType == null || fileType == FileType.NETWORK) {
	    String sqlNetworks = "SELECT " + baseCols
	            + ", readonly, error, warnings, iscomplete, is_validated, ndexdoi"
	            + (compact ? ", description, edgecount, visibility" : "")
	            + " FROM network WHERE owneruuid = ? AND parent IS NULL AND visibility = 'PUBLIC' AND is_deleted = false";
	    try (PreparedStatement pst = db.prepareStatement(sqlNetworks)) {
	        pst.setObject(1, ownerId);
	        try (ResultSet rs = pst.executeQuery()) {
	            while (rs.next()) {
	                Map<String, Object> attr = null;
	                if (compact) {
	                    attr = new HashMap<>();
	                    attr.put("description", rs.getString("description"));
	                }

	                boolean isReadOnly = rs.getBoolean("readonly");

	                String errorMessage = rs.getString("error");

	                List<String> warnings = null;
	                Array warningsArray = rs.getArray("warnings");
	                if (warningsArray != null) {
	                    try {
	                        warnings = Arrays.asList((String[]) warningsArray.getArray());
	                    } finally {
	                        warningsArray.free();
	                    }
	                }

	                boolean isCompleted = rs.getBoolean("iscomplete");
	                boolean isValidValue = rs.getBoolean("is_validated");
	                boolean isValidNull = rs.wasNull();
	                String doi = rs.getString("ndexdoi");

	                FileItemSummary summary = new FileItemSummary(
	                    (UUID) rs.getObject("UUID"), FileType.NETWORK,
	                    rs.getString("name"), rs.getTimestamp("modification_time"), rs.getString("updated_by"), attr,
	                    isReadOnly, errorMessage, warnings, isCompleted);
	                if (compact) {
	                    summary.setEdges((Integer) rs.getObject("edgecount"));
	                    summary.setVisibility(rs.getString("visibility"));
	                }
	                if (!isValidNull) {
	                    summary.setIsValid(isValidValue);
	                }
	                summary.setDoi(doi);
	                result.add(summary);
	            }
	        }
	    }
		}

	    // Shortcuts
		if (fileType == null || fileType == FileType.SHORTCUT) {
    String sqlShortcuts = "SELECT " + baseCols + ", target_type, target, visibility FROM shortcut " +
                          "WHERE owneruuid = ? AND parent IS NULL AND is_deleted = false";
    try (PreparedStatement pst = db.prepareStatement(sqlShortcuts)) {
        pst.setObject(1, ownerId);
        try (ResultSet rs = pst.executeQuery()) {
            while (rs.next()) {
                Map<String, Object> attr = null;
                if (compact) {
                    attr = new HashMap<>();
                    String targetType = rs.getString("target_type");
                    UUID targetId = (UUID) rs.getObject("target");
                    attr.put("target_type", targetType);
                    attr.put("target", targetId);

                    // check deletion status
                    ShortcutTargetStatus targetStatus = ShortcutTargetStatus.DELETED;
                    if (targetId != null && targetType != null) {
                        String checkTargetSql = "SELECT is_deleted FROM " +
                                                ("FOLDER".equals(targetType) ? "folder" : "network") +
                                                " WHERE \"UUID\"=?";
                        try (PreparedStatement checkPst = db.prepareStatement(checkTargetSql)) {
                            checkPst.setObject(1, targetId);
                            try (ResultSet checkRs = checkPst.executeQuery()) {
                                if (checkRs.next()) {
                                    targetStatus = checkRs.getBoolean(1) ? ShortcutTargetStatus.IN_TRASH : ShortcutTargetStatus.ACTIVE;
                                }
                            }
                        }
                    }
                    attr.put("target_status", targetStatus.toString());
                }
                FileItemSummary summary = new FileItemSummary(
                    (UUID) rs.getObject(1), FileType.SHORTCUT,
                    rs.getString(2), rs.getTimestamp(3), rs.getString(4),
                    attr);
                summary.setVisibility(rs.getString("visibility"));
                result.add(summary);
            }
        }
    }	
		}

	    return result;
	}

}
