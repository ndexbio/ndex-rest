package org.ndexbio.common.models.dao.postgresql;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.sql.Timestamp;

import org.ndexbio.common.models.dao.TrashDAO;
import org.ndexbio.model.object.FileItemSummary;
import org.ndexbio.model.object.FileType;
import org.ndexbio.model.object.Permissions;
import org.ndexbio.model.object.TrashRestoreRequest;


public class PostgresTrashDAO extends NdexDBDAO implements TrashDAO {

    public PostgresTrashDAO() throws SQLException {
        super();
    }

    /**
     * List all "trashed" (is_deleted=true) items owned by a specific user.
     * Returns a FileItemSummary list with type = folder|network|shortcut.
     */
    public List<FileItemSummary> listTrashedItemsOfUser(UUID ownerId) throws SQLException {
        List<FileItemSummary> results = new ArrayList<>();

        // 1) Folders
        String folderSql = 
            "SELECT \"UUID\", name, modification_time, updated_by, description FROM folder " +
            "WHERE owneruuid=? AND is_deleted=true AND show_in_trash = true";
        try (PreparedStatement pst = db.prepareStatement(folderSql)) {
            pst.setObject(1, ownerId);
            try (ResultSet rs = pst.executeQuery()) {
                while (rs.next()) {
					Map<String, Object> attr = null;
                    attr = new HashMap<>();
                    attr.put("description", rs.getString(5));
                    results.add(new FileItemSummary(
                        (UUID) rs.getObject(1), FileType.FOLDER,
                        rs.getString(2), rs.getTimestamp(3), rs.getString(4),
                        attr));
                }
            }
        }

        // 2) Networks
        String networkSql =
            "SELECT \"UUID\", name, modification_time, updated_by, description, edgecount, visibility FROM network " +
            "WHERE owneruuid=? AND is_deleted=true AND show_in_trash = true";
        try (PreparedStatement pst = db.prepareStatement(networkSql)) {
            pst.setObject(1, ownerId);
            try (ResultSet rs = pst.executeQuery()) {
                while (rs.next()) {
                    Map<String, Object> attr = null;
                    attr = new HashMap<>();
                    attr.put("description", rs.getString(5));
                    FileItemSummary summary = new FileItemSummary(
                        (UUID) rs.getObject(1), FileType.NETWORK,
                        rs.getString(2), rs.getTimestamp(3), rs.getString(4),
                        attr);
                    summary.setEdges((Integer) rs.getObject(6));
                    summary.setVisibility(rs.getString(7));
                    results.add(summary);
                }
            }
        }

        // 3) Shortcuts
        String shortcutSql =
            "SELECT \"UUID\", name, modification_time, updated_by, target_type, target FROM shortcut " +
            "WHERE owneruuid=? AND is_deleted=true AND show_in_trash = true";
        try (PreparedStatement pst = db.prepareStatement(shortcutSql)) {
            pst.setObject(1, ownerId);
            try (ResultSet rs = pst.executeQuery()) {
                while (rs.next()) {
                	Map<String, Object> attr = null;
                    attr = new HashMap<>();
					String targetType = rs.getString(5);
					UUID targetId = (UUID) rs.getObject(6);
                    attr.put("target_type", targetType);
					attr.put("target", targetId);
                    results.add(
                        new FileItemSummary(
        	                    (UUID) rs.getObject(1), FileType.SHORTCUT,
        	                    rs.getString(2), rs.getTimestamp(3), rs.getString(4), attr
                        )
                    );
                }
            }
        }

        return results;
    }
    
    @Override
    public void restoreTrashedItems(UUID userId, TrashRestoreRequest request) throws SQLException {
        Timestamp t = new Timestamp(System.currentTimeMillis());
        
        // Restore folders first
        if (request.getFolders() != null) {
            for (UUID folderId : request.getFolders()) {
                // Check if parent folder exists and is not deleted
                String checkParentSql = "SELECT parent FROM folder WHERE \"UUID\"=? AND is_deleted=true";
                UUID parentId = null;
                try (PreparedStatement pst = db.prepareStatement(checkParentSql)) {
                    pst.setObject(1, folderId);
                    try (ResultSet rs = pst.executeQuery()) {
                        if (rs.next()) {
                            parentId = (UUID) rs.getObject(1);
                        }
                    }
                }
                
                // If parent exists and is not deleted, check if user has write access
                boolean restoreToParent = false;
                if (parentId != null) {
                    String checkParentAccessSql = "SELECT 1 FROM folder WHERE \"UUID\"=? AND is_deleted=false AND " +
                        "(owneruuid=? OR EXISTS (SELECT 1 FROM folder_permission WHERE folder_id=? AND user_id=? AND permission=?))";
                    try (PreparedStatement pst = db.prepareStatement(checkParentAccessSql)) {
                        pst.setObject(1, parentId);
                        pst.setObject(2, userId);
                        pst.setObject(3, parentId);
                        pst.setObject(4, userId);
                        pst.setObject(5, Permissions.WRITE.toString());
                        try (ResultSet rs = pst.executeQuery()) {
                            restoreToParent = rs.next();
                        }
                    }
                }
                
                // Restore folder
                String restoreFolderSql = "UPDATE folder SET is_deleted=false, show_in_trash=false, modification_time=? WHERE \"UUID\"=?";
                try (PreparedStatement pst = db.prepareStatement(restoreFolderSql)) {
                    pst.setTimestamp(1, t);
                    pst.setObject(2, folderId);
                    pst.executeUpdate();
                }
                
                // If parent doesn't exist or user doesn't have access, move to home
                if (!restoreToParent) {
                    String moveToHomeSql = "UPDATE folder SET parent=NULL WHERE \"UUID\"=?";
                    try (PreparedStatement pst = db.prepareStatement(moveToHomeSql)) {
                        pst.setObject(1, folderId);
                        pst.executeUpdate();
                    }
                }
                
                // Recursively restore contents of this folder
                restoreFolderContents(folderId, userId, t);
            }
        }
        
        // Restore networks
        if (request.getNetworks() != null) {
            for (UUID networkId : request.getNetworks()) {
                // Check if parent folder exists and is not deleted
                String checkParentSql = "SELECT parent FROM network WHERE \"UUID\"=? AND is_deleted=true";
                UUID parentId = null;
                try (PreparedStatement pst = db.prepareStatement(checkParentSql)) {
                    pst.setObject(1, networkId);
                    try (ResultSet rs = pst.executeQuery()) {
                        if (rs.next()) {
                            parentId = (UUID) rs.getObject(1);
                        }
                    }
                }
                
                // If parent exists and is not deleted, check if user has access
                boolean restoreToParent = false;
                if (parentId != null) {
                    String checkParentAccessSql = "SELECT 1 FROM folder WHERE \"UUID\"=? AND is_deleted=false AND " +
                        "(owneruuid=? OR EXISTS (SELECT 1 FROM folder_permission WHERE folder_id=? AND user_id=? AND permission=?))";
                    try (PreparedStatement pst = db.prepareStatement(checkParentAccessSql)) {
                        pst.setObject(1, parentId);
                        pst.setObject(2, userId);
                        pst.setObject(3, parentId);
                        pst.setObject(4, userId);
                        pst.setObject(5, Permissions.WRITE.toString());
                        try (ResultSet rs = pst.executeQuery()) {
                            restoreToParent = rs.next();
                        }
                    }
                }
                
                // Restore network
                String restoreNetworkSql = "UPDATE network SET is_deleted=false, show_in_trash=false, modification_time=? WHERE \"UUID\"=?";
                try (PreparedStatement pst = db.prepareStatement(restoreNetworkSql)) {
                    pst.setTimestamp(1, t);
                    pst.setObject(2, networkId);
                    pst.executeUpdate();
                }
                
                // If parent doesn't exist or user doesn't have access, move to home
                if (!restoreToParent) {
                    String moveToHomeSql = "UPDATE network SET parent=NULL WHERE \"UUID\"=?";
                    try (PreparedStatement pst = db.prepareStatement(moveToHomeSql)) {
                        pst.setObject(1, networkId);
                        pst.executeUpdate();
                    }
                }
            }
        }
        
        // Restore shortcuts
        if (request.getShortcuts() != null) {
            for (UUID shortcutId : request.getShortcuts()) {
                // Check if parent folder exists and is not deleted
                String checkParentSql = "SELECT parent FROM shortcut WHERE \"UUID\"=? AND is_deleted=true";
                UUID parentId = null;
                try (PreparedStatement pst = db.prepareStatement(checkParentSql)) {
                    pst.setObject(1, shortcutId);
                    try (ResultSet rs = pst.executeQuery()) {
                        if (rs.next()) {
                            parentId = (UUID) rs.getObject(1);
                        }
                    }
                }
                
                // If parent exists and is not deleted, check if user has access
                boolean restoreToParent = false;
                if (parentId != null) {
                    String checkParentAccessSql = "SELECT 1 FROM folder WHERE \"UUID\"=? AND is_deleted=false AND " +
                        "(owneruuid=? OR EXISTS (SELECT 1 FROM folder_permission WHERE folder_id=? AND user_id=? AND permission=?))";
                    try (PreparedStatement pst = db.prepareStatement(checkParentAccessSql)) {
                        pst.setObject(1, parentId);
                        pst.setObject(2, userId);
                        pst.setObject(3, parentId);
                        pst.setObject(4, userId);
                        try (ResultSet rs = pst.executeQuery()) {
                            restoreToParent = rs.next();
                        }
                    }
                }
                
                // Restore shortcut
                String restoreShortcutSql = "UPDATE shortcut SET is_deleted=false, show_in_trash=false, modification_time=? WHERE \"UUID\"=?";
                try (PreparedStatement pst = db.prepareStatement(restoreShortcutSql)) {
                    pst.setTimestamp(1, t);
                    pst.setObject(2, shortcutId);
                    pst.executeUpdate();
                }
                
                // If parent doesn't exist or user doesn't have access, move to home
                if (!restoreToParent) {
                    String moveToHomeSql = "UPDATE shortcut SET parent=NULL WHERE \"UUID\"=?";
                    try (PreparedStatement pst = db.prepareStatement(moveToHomeSql)) {
                        pst.setObject(1, shortcutId);
                        pst.executeUpdate();
                    }
                }
            }
        }
    }

    /**
     * Recursively restores all contents of a folder that were deleted with it.
     * This includes subfolders, networks, and shortcuts.
     */
    private void restoreFolderContents(UUID folderId, UUID userId, Timestamp t) throws SQLException {
        // Restore subfolders
        String getSubfoldersSql = "SELECT \"UUID\" FROM folder WHERE parent=? AND is_deleted=true AND show_in_trash = false";
        List<UUID> subfolderIds = new ArrayList<>();
        try (PreparedStatement pst = db.prepareStatement(getSubfoldersSql)) {
            pst.setObject(1, folderId);
            try (ResultSet rs = pst.executeQuery()) {
                while (rs.next()) {
                    subfolderIds.add((UUID) rs.getObject(1));
                }
            }
        }
        
        // Restore each subfolder and its contents
        for (UUID subfolderId : subfolderIds) {
            String restoreFolderSql = "UPDATE folder SET is_deleted=false, modification_time=? WHERE \"UUID\"=? AND show_in_trash = false";
            try (PreparedStatement pst = db.prepareStatement(restoreFolderSql)) {
                pst.setTimestamp(1, t);
                pst.setObject(2, subfolderId);
                pst.executeUpdate();
            }
            restoreFolderContents(subfolderId, userId, t);
        }
        
        // Restore networks in this folder
        String restoreNetworksSql = "UPDATE network SET is_deleted=false, modification_time=? WHERE parent=? AND is_deleted=true AND show_in_trash = false";
        try (PreparedStatement pst = db.prepareStatement(restoreNetworksSql)) {
            pst.setTimestamp(1, t);
            pst.setObject(2, folderId);
            pst.executeUpdate();
        }
        
        // Restore shortcuts in this folder
        String restoreShortcutsSql = "UPDATE shortcut SET is_deleted=false, modification_time=? WHERE parent=? AND is_deleted=true AND show_in_trash = false";
        try (PreparedStatement pst = db.prepareStatement(restoreShortcutsSql)) {
            pst.setTimestamp(1, t);
            pst.setObject(2, folderId);
            pst.executeUpdate();
        }
    }

    /**
     * Permanently deletes all trashed (is_deleted=true) items owned by this user.
     */
    
    public void permanentlyDeleteAllTrashedItemsOfUser(UUID ownerId) throws SQLException {
    	
        // 1) Remove membership for networks
        String deleteNetMembership =
            "DELETE FROM user_network_membership " +
            " WHERE network_id IN ( " +
            "   SELECT \"UUID\" FROM network WHERE owneruuid=? AND is_deleted=true " +
            " )";
        try (PreparedStatement pst = db.prepareStatement(deleteNetMembership)) {
            pst.setObject(1, ownerId);
            pst.executeUpdate();
        }

        // 2) Remove networks
        String deleteNetworks = 
            "DELETE FROM network WHERE owneruuid=? AND is_deleted=true";
        try (PreparedStatement pst = db.prepareStatement(deleteNetworks)) {
            pst.setObject(1, ownerId);
            pst.executeUpdate();
        }
        

        // 3) remove folder_permissions for trashed folders
        String deleteFolderPermissions =
            "DELETE FROM folder_permission " +
            " WHERE folder_id IN (" +
            "   SELECT \"UUID\" FROM folder WHERE owneruuid=? AND is_deleted=true" +
            " )";
        try (PreparedStatement pst = db.prepareStatement(deleteFolderPermissions)) {
            pst.setObject(1, ownerId);
            pst.executeUpdate();
        }

        // 4) remove shortcuts
        String deleteShortcuts = 
            "DELETE FROM shortcut WHERE owneruuid=? AND is_deleted=true";
        try (PreparedStatement pst = db.prepareStatement(deleteShortcuts)) {
            pst.setObject(1, ownerId);
            pst.executeUpdate();
        }

        // 5) remove folders
        String deleteFolders = 
            "DELETE FROM folder WHERE owneruuid=? AND is_deleted=true";
        try (PreparedStatement pst = db.prepareStatement(deleteFolders)) {
            pst.setObject(1, ownerId);
            pst.executeUpdate();
        }

    }

    @Override
    public void permanentlyDeleteTrashedItem(UUID itemId, FileType type) throws SQLException {
        switch (type) {
            case FOLDER:
                // First get all descendant folders recursively that have show_in_trash=false
                String getDescendantsSql = 
                    "WITH RECURSIVE folder_tree AS (" +
                    "  SELECT \"UUID\" FROM folder WHERE \"UUID\"=? AND is_deleted=true " +
                    "  UNION ALL " +
                    "  SELECT f.\"UUID\" FROM folder f " +
                    "  JOIN folder_tree ft ON f.parent = ft.\"UUID\" " +
                    "  WHERE f.is_deleted=true AND f.show_in_trash=false" +
                    ") SELECT \"UUID\" FROM folder_tree";
                
                List<UUID> descendantFolders = new ArrayList<>();
                try (PreparedStatement pst = db.prepareStatement(getDescendantsSql)) {
                    pst.setObject(1, itemId);
                    try (ResultSet rs = pst.executeQuery()) {
                        while (rs.next()) {
                            descendantFolders.add((UUID) rs.getObject(1));
                        }
                    }
                }
                
                int totalPlaceholders = 1 + descendantFolders.size(); // 1 for folderId + rest
                String placeholders = String.join(",", Collections.nCopies(totalPlaceholders, "?"));
                
                // Delete all networks in the folder tree that have show_in_trash=false
                String deleteNetworksSql = "DELETE FROM network WHERE parent IN (" + placeholders + ") AND show_in_trash=false";
                try (PreparedStatement pst = db.prepareStatement(deleteNetworksSql)) {
                    pst.setObject(1, itemId);
                    for (int i = 0; i < descendantFolders.size(); i++) {
                        pst.setObject(i + 2, descendantFolders.get(i));
                    }
                    pst.executeUpdate();
                }
                
                // Delete all shortcuts in the folder tree that have show_in_trash=false
                String deleteShortcutsSql = "DELETE FROM shortcut WHERE parent IN (" + placeholders + ") AND show_in_trash=false";
                try (PreparedStatement pst = db.prepareStatement(deleteShortcutsSql)) {
                    pst.setObject(1, itemId);
                    for (int i = 0; i < descendantFolders.size(); i++) {
                        pst.setObject(i + 2, descendantFolders.get(i));
                    }
                    pst.executeUpdate();
                }
                
                // Delete all folder permissions in the folder tree
                String deleteFolderPermissionsSql = "DELETE FROM folder_permission WHERE folder_id IN (" + placeholders + ")";
                try (PreparedStatement pst = db.prepareStatement(deleteFolderPermissionsSql)) {
                    pst.setObject(1, itemId);
                    for (int i = 0; i < descendantFolders.size(); i++) {
                        pst.setObject(i + 2, descendantFolders.get(i));
                    }
                    pst.executeUpdate();
                }
                
                // Delete all folders in the tree that have show_in_trash=false
                String deleteFoldersSql = "DELETE FROM folder WHERE \"UUID\" IN (" + placeholders + ") AND show_in_trash=false";
                try (PreparedStatement pst = db.prepareStatement(deleteFoldersSql)) {
                    pst.setObject(1, itemId);
                    for (int i = 0; i < descendantFolders.size(); i++) {
                        pst.setObject(i + 2, descendantFolders.get(i));
                    }
                    pst.executeUpdate();
                }

                // Finally delete the main folder (itemId) which has show_in_trash=true
                String deleteMainFolderSql = "DELETE FROM folder WHERE \"UUID\"=? AND is_deleted=true AND show_in_trash=true";
                try (PreparedStatement pst = db.prepareStatement(deleteMainFolderSql)) {
                    pst.setObject(1, itemId);
                    int updated = pst.executeUpdate();
                    if (updated == 0) {
                        throw new SQLException("Folder not found in trash or not deleted.");
                    }
                }
                break;
                
            case NETWORK:
                // First delete network memberships
                String deleteNetMembership = "DELETE FROM user_network_membership WHERE network_id=?";
                try (PreparedStatement pst = db.prepareStatement(deleteNetMembership)) {
                    pst.setObject(1, itemId);
                    pst.executeUpdate();
                }
                
                // Then delete the network
                String deleteNetwork = "DELETE FROM network WHERE \"UUID\"=? AND is_deleted=true";
                try (PreparedStatement pst = db.prepareStatement(deleteNetwork)) {
                    pst.setObject(1, itemId);
                    int updated = pst.executeUpdate();
                    if (updated == 0) {
                        throw new SQLException("Network not found in trash or not deleted.");
                    }
                }
                break;
                
            case SHORTCUT:
                String deleteShortcut = "DELETE FROM shortcut WHERE \"UUID\"=? AND is_deleted=true";
                try (PreparedStatement pst = db.prepareStatement(deleteShortcut)) {
                    pst.setObject(1, itemId);
                    int updated = pst.executeUpdate();
                    if (updated == 0) {
                        throw new SQLException("Shortcut not found in trash or not deleted.");
                    }
                }
                break;
                
            default:
                throw new SQLException("Unsupported file type: " + type);
        }
    }

    @Override
    public FileType getTrashedItemType(UUID itemId) throws SQLException {
        String sql = "SELECT '" + FileType.FOLDER.toString() + "'::text FROM folder WHERE \"UUID\"=? AND is_deleted=true " +
                    "UNION ALL " +
                    "SELECT '" + FileType.NETWORK.toString() + "'::text FROM network WHERE \"UUID\"=? AND is_deleted=true " +
                    "UNION ALL " +
                    "SELECT '" + FileType.SHORTCUT.toString() + "'::text FROM shortcut WHERE \"UUID\"=? AND is_deleted=true";
        
        try (PreparedStatement pst = db.prepareStatement(sql)) {
            pst.setObject(1, itemId);
            pst.setObject(2, itemId);
            pst.setObject(3, itemId);
            try (ResultSet rs = pst.executeQuery()) {
                if (rs.next()) {
                    return FileType.valueOf(rs.getString(1));
                }
                return null;
            }
        }
    }

}
