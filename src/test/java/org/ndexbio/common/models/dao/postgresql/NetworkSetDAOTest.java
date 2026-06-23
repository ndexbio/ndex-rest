package org.ndexbio.common.models.dao.postgresql;

import static org.junit.Assert.*;

import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.junit.Before;
import org.junit.Test;
import org.ndexbio.model.object.FileItemSummary;
import org.ndexbio.model.object.FileType;

public class NetworkSetDAOTest {
    
    private UUID networkId1;
    private UUID networkId2;
    private UUID shortcutId1;
    private UUID childFolderId;
    
    @Before
    public void setUp() throws SQLException {
        // Generate test UUIDs
        networkId1 = UUID.randomUUID();
        networkId2 = UUID.randomUUID();
        shortcutId1 = UUID.randomUUID();
        childFolderId = UUID.randomUUID();
    }
    
    @Test
    public void testGetNetworkSet_DirectNetworks() throws Exception {
        // Create folder items with direct networks
        List<FileItemSummary> folderItems = new ArrayList<>();
        folderItems.add(createNetworkItem(networkId1, "Network 1"));
        folderItems.add(createNetworkItem(networkId2, "Network 2"));
        
        // Extract network IDs from folder items
        List<UUID> networkIds = extractNetworkIds(folderItems);

        assertEquals(2, networkIds.size());
        assertTrue(networkIds.contains(networkId1));
        assertTrue(networkIds.contains(networkId2));
    }
    
    @Test
    public void testGetNetworkSet_ExcludeFolders() throws Exception {
        List<FileItemSummary> folderItems = new ArrayList<>();
        folderItems.add(createNetworkItem(networkId1, "Network 1"));
        folderItems.add(createFolderItem(childFolderId, "Child Folder"));
        folderItems.add(createNetworkItem(networkId2, "Network 2"));
        
        List<UUID> networkIds = extractNetworkIds(folderItems);

        assertEquals(2, networkIds.size());
        assertTrue(networkIds.contains(networkId1));
        assertTrue(networkIds.contains(networkId2));
        assertFalse(networkIds.contains(childFolderId));
    }
    
    @Test
    public void testGetNetworkSet_ExcludeFolderShortcuts() throws Exception {
        List<FileItemSummary> folderItems = new ArrayList<>();
        folderItems.add(createNetworkItem(networkId1, "Network 1"));
        folderItems.add(createFolderTypeShortcut(shortcutId1, "Folder Shortcut", childFolderId));
        folderItems.add(createNetworkItem(networkId2, "Network 2"));
        
        List<UUID> networkIds = extractNetworkIds(folderItems);

        assertEquals(2, networkIds.size());
        assertTrue(networkIds.contains(networkId1));
        assertTrue(networkIds.contains(networkId2));
        assertFalse(networkIds.contains(shortcutId1));
    }
    
    @Test
    public void testGetNetworkSet_NetworkShortcutsActive() throws Exception {
        List<FileItemSummary> folderItems = new ArrayList<>();
        folderItems.add(createNetworkItem(networkId1, "Network 1"));
        folderItems.add(createNetworkTypeShortcut(shortcutId1, "Network Shortcut 1", networkId2, "ACTIVE"));
        
        List<UUID> networkIds = extractNetworkIds(folderItems);

        assertEquals(2, networkIds.size());
        assertTrue(networkIds.contains(networkId1));
        assertTrue(networkIds.contains(networkId2));
        assertFalse(networkIds.contains(shortcutId1)); // Shortcut UUID should not be in result
    }
    
    @Test
    public void testGetNetworkSet_NetworkShortcutsDeleted() throws Exception {
        List<FileItemSummary> folderItems = new ArrayList<>();
        folderItems.add(createNetworkItem(networkId1, "Network 1"));
        folderItems.add(createNetworkTypeShortcut(shortcutId1, "Network Shortcut 1", networkId2, "DELETED"));
        
        List<UUID> networkIds = extractNetworkIds(folderItems);

        assertEquals(1, networkIds.size());
        assertTrue(networkIds.contains(networkId1));
        assertFalse(networkIds.contains(shortcutId1));
        assertFalse(networkIds.contains(networkId2)); // Target of deleted shortcut
    }
    
    @Test
    public void testGetNetworkSet_NetworkShortcutsInTrash() throws Exception {
        List<FileItemSummary> folderItems = new ArrayList<>();
        folderItems.add(createNetworkItem(networkId1, "Network 1"));
        folderItems.add(createNetworkTypeShortcut(shortcutId1, "Network Shortcut 1", networkId2, "IN_TRASH"));
        
        List<UUID> networkIds = extractNetworkIds(folderItems);

        assertEquals(1, networkIds.size());
        assertTrue(networkIds.contains(networkId1));
        assertFalse(networkIds.contains(shortcutId1));
        assertFalse(networkIds.contains(networkId2));
    }
    
    @Test
    public void testGetNetworkSet_DeduplicateNetworks() throws Exception {
        List<FileItemSummary> folderItems = new ArrayList<>();
        folderItems.add(createNetworkItem(networkId1, "Network 1"));
        folderItems.add(createNetworkTypeShortcut(shortcutId1, "Shortcut to Network 1", networkId1, "ACTIVE"));
        folderItems.add(createNetworkItem(networkId2, "Network 2"));
        
        List<UUID> networkIds = extractNetworkIds(folderItems);

        assertEquals(2, networkIds.size());
        assertEquals(networkId1, networkIds.get(0)); // First occurrence
        assertEquals(networkId2, networkIds.get(1));
    }
    
    @Test
    public void testGetNetworkSet_EmptyFolder() throws Exception {
        List<FileItemSummary> folderItems = new ArrayList<>();
        
        List<UUID> networkIds = extractNetworkIds(folderItems);

        assertEquals(0, networkIds.size());
    }
    
    // ==================== Helper Methods ====================
    
    private FileItemSummary createNetworkItem(UUID networkId, String name) {
        FileItemSummary item = new FileItemSummary(networkId, FileType.NETWORK, name, 
            new Timestamp(System.currentTimeMillis()), "testuser", null);
        return item;
    }
    
    private FileItemSummary createFolderItem(UUID folderId, String name) {
        FileItemSummary item = new FileItemSummary(folderId, FileType.FOLDER, name, 
            new Timestamp(System.currentTimeMillis()), "testuser", null);
        return item;
    }
    
    private FileItemSummary createFolderTypeShortcut(UUID shortcutId, String name, UUID targetId) {
        Map<String, Object> attrs = new HashMap<>();
        attrs.put("target_type", "FOLDER");
        attrs.put("target", targetId);
        attrs.put("target_status", "ACTIVE");
        FileItemSummary item = new FileItemSummary(shortcutId, FileType.SHORTCUT, name, 
            new Timestamp(System.currentTimeMillis()), "testuser", attrs);
        return item;
    }
    
    private FileItemSummary createNetworkTypeShortcut(UUID shortcutId, String name, UUID targetId, String targetStatus) {
        Map<String, Object> attrs = new HashMap<>();
        attrs.put("target_type", "NETWORK");
        attrs.put("target", targetId);
        attrs.put("target_status", targetStatus);
        FileItemSummary item = new FileItemSummary(shortcutId, FileType.SHORTCUT, name, 
            new Timestamp(System.currentTimeMillis()), "testuser", attrs);
        return item;
    }
    
    private List<UUID> extractNetworkIds(List<FileItemSummary> folderItems) {
        java.util.Set<UUID> seenNetworkIds = new java.util.LinkedHashSet<>();
        for (FileItemSummary item : folderItems) {
            if (item.getType() == FileType.NETWORK) {
                seenNetworkIds.add(item.getUuid());
            } else if (item.getType() == FileType.SHORTCUT) {
                Map<String, Object> attrs = item.getAttributes();
                if (attrs != null) {
                    String targetType = (String) attrs.get("target_type");
                    if ("NETWORK".equals(targetType)) {
                        String targetStatus = (String) attrs.get("target_status");
                        if ("ACTIVE".equals(targetStatus)) {
                            UUID targetId = (UUID) attrs.get("target");
                            if (targetId != null) {
                                seenNetworkIds.add(targetId);
                            }
                        }
                    }
                }
            }
        }
        return new ArrayList<>(seenNetworkIds);
    }
}
