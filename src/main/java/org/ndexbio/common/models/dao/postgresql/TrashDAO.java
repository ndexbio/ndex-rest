package org.ndexbio.common.models.dao.postgresql;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.sql.Timestamp;

import org.ndexbio.model.object.FileItemSummary;
import org.ndexbio.model.object.TrashRestoreRequest;

/**
 * DAO for listing and permanently removing trashed items (folders/networks/shortcuts).
 */
public class TrashDAO extends NdexDBDAO {

    public TrashDAO() throws SQLException {
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
            "SELECT \"UUID\", name FROM folder " +
            "WHERE owneruuid=? AND is_deleted=true";
        try (PreparedStatement pst = db.prepareStatement(folderSql)) {
            pst.setObject(1, ownerId);
            try (ResultSet rs = pst.executeQuery()) {
                while (rs.next()) {
                    results.add(
                        new FileItemSummary(
                            (UUID) rs.getObject("UUID"),
                            "folder",
                            rs.getString("name")
                        )
                    );
                }
            }
        }

        // 2) Networks
        String networkSql =
            "SELECT \"UUID\", name FROM network " +
            "WHERE owneruuid=? AND is_deleted=true";
        try (PreparedStatement pst = db.prepareStatement(networkSql)) {
            pst.setObject(1, ownerId);
            try (ResultSet rs = pst.executeQuery()) {
                while (rs.next()) {
                    results.add(
                        new FileItemSummary(
                            (UUID) rs.getObject("UUID"),
                            "network",
                            rs.getString("name")
                        )
                    );
                }
            }
        }

        // 3) Shortcuts
        String shortcutSql =
            "SELECT \"UUID\", name FROM shortcut " +
            "WHERE owneruuid=? AND is_deleted=true";
        try (PreparedStatement pst = db.prepareStatement(shortcutSql)) {
            pst.setObject(1, ownerId);
            try (ResultSet rs = pst.executeQuery()) {
                while (rs.next()) {
                    results.add(
                        new FileItemSummary(
                            (UUID) rs.getObject("UUID"),
                            "shortcut",
                            rs.getString("name")
                        )
                    );
                }
            }
        }

        return results;
    }
    
    public void restoreTrashedItems(UUID userId, TrashRestoreRequest request) throws SQLException {
        Timestamp now = new Timestamp(System.currentTimeMillis());

        // 1) Restore networks
        List<UUID> networkList = request.getNetworks();
        if (networkList != null && !networkList.isEmpty()) {
            restoreItems("network", networkList, userId, now);
        }

        // 2) Restore folders
        List<UUID> folderList = request.getFolders();
        if (folderList != null && !folderList.isEmpty()) {
            restoreItems("folder", folderList, userId, now);
        }

        // 3) Restore shortcuts
        List<UUID> shortcutList = request.getShortcuts();
        if (shortcutList != null && !shortcutList.isEmpty()) {
            restoreItems("shortcut", shortcutList, userId, now);
        }
    }

    /**
     * Sets is_deleted=false and modification_time=? 
     * for a batch of items in the given table, if they belong to userId.
     *
     * @param tableName   "network", "folder", or "shortcut"
     * @param uuids       list of UUIDs to restore
     * @param userId      the owner
     * @param restoreTime the timestamp for modification_time
     */
    private void restoreItems(String tableName, List<UUID> uuids, UUID userId, Timestamp restoreTime) 
            throws SQLException {

        // Build an IN clause (?,?,...) based on the size of uuids
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < uuids.size(); i++) {
            if (i > 0) sb.append(", ");
            sb.append("?");
        }
        
        // Build clause and add table name to clause
        String sql = String.format(
            "UPDATE %s SET is_deleted=false, modification_time=? " +
            " WHERE owneruuid=? AND is_deleted=true AND \"UUID\" IN (%s)",
            tableName,
            sb.toString()
        );

        try (PreparedStatement pst = db.prepareStatement(sql)) {
            pst.setTimestamp(1, restoreTime);
            pst.setObject(2, userId);

            // Fill the UUID placeholders
            int index = 3;
            for (UUID u : uuids) {
                pst.setObject(index++, u);
            }
            pst.executeUpdate();
        }
    }


    /**
     * Permanently deletes all trashed (is_deleted=true) items owned by this user.
     * This means physically removing them from the DB (and related permission/membership rows).
     */
    
    public void permanentlyDeleteAllTrashedItemsOfUser(UUID ownerId) throws SQLException {
    	/**
        // 1) Remove membership for networks (remove group_network_membership?)
        String deleteNetMembership = 
            "DELETE FROM user_network_membership " +
            " WHERE networkid IN ( " +
            "   SELECT \"UUID\" FROM network WHERE owneruuid=? AND is_deleted=true " +
            " )";
        try (PreparedStatement pst = db.prepareStatement(deleteNetMembership)) {
            pst.setObject(1, ownerId);
            pst.executeUpdate();
        }

        String deleteGroupNetMembership =
            "DELETE FROM group_network_membership " +
            " WHERE network_id IN ( " +
            "   SELECT \"UUID\" FROM network WHERE owneruuid=? AND is_deleted=true " +
            " )";
        try (PreparedStatement pst = db.prepareStatement(deleteGroupNetMembership)) {
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
        */

    }

}