package org.ndexbio.common.models.dao.postgresql;

import static org.junit.Assert.*;
import static org.easymock.EasyMock.*;

import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.junit.Before;
import org.junit.Test;
import org.ndexbio.common.models.dao.FolderDAO;
import org.ndexbio.model.exceptions.ObjectNotFoundException;
import org.ndexbio.model.object.FileItemSummary;
import org.ndexbio.model.object.FileType;
import org.ndexbio.model.object.NetworkSet;
import org.ndexbio.model.object.NdexFolder;

public class NetworkSetDAOTest {

    private Connection mockConnection;
    private FolderDAO mockFolderDAO;
    private NetworkSetDAO dao;

    private UUID setId;
    private UUID ownerId;
    private UUID networkId1;
    private UUID networkId2;
    private UUID networkId3;
    private UUID shortcutId;
    private UUID childFolderId;

    @Before
    public void setUp() throws SQLException {
        mockConnection = createMock(Connection.class);
        mockFolderDAO = createMock(FolderDAO.class);
        dao = new NetworkSetDAO(mockConnection, mockFolderDAO);

        setId = UUID.randomUUID();
        ownerId = UUID.randomUUID();
        networkId1 = UUID.randomUUID();
        networkId2 = UUID.randomUUID();
        networkId3 = UUID.randomUUID();
        shortcutId = UUID.randomUUID();
        childFolderId = UUID.randomUUID();
    }

    @Test
    public void testGetNetworkSet_IncludesDirectNetworksAndActiveNetworkShortcutTarget() throws Exception {
        NdexFolder folder = createFolder(setId, ownerId, "set-name");
        List<FileItemSummary> folderItems = new ArrayList<>();
        folderItems.add(createNetworkItem(networkId1, "Network 1"));
        folderItems.add(createNetworkTypeShortcut(shortcutId, "Network Shortcut", networkId2, "ACTIVE"));
        folderItems.add(createNetworkItem(networkId2, "Network 2"));

        expect(mockFolderDAO.getFolder(setId, ownerId, "key")).andReturn(folder);
        expect(mockFolderDAO.listItemsInFolder(setId, true, FileType.NETWORK)).andReturn(folderItems);
        mockFolderDAO.close();
        expectLastCall().once();

        replay(mockFolderDAO, mockConnection);

        NetworkSet result = dao.getNetworkSet(setId, ownerId, "key");

        assertEquals(setId, result.getExternalId());
        assertEquals(ownerId, result.getOwnerId());
        assertEquals("set-name", result.getName());
        assertEquals("set-description", result.getDescription());
        assertEquals(2, result.getNetworks().size());
        assertEquals(networkId1, result.getNetworks().get(0));
        assertEquals(networkId2, result.getNetworks().get(1));

        verify(mockFolderDAO, mockConnection);
    }

    @Test
    public void testGetNetworkSet_ExcludesFolderAndFolderShortcut() throws Exception {
        NdexFolder folder = createFolder(setId, ownerId, "set-name");
        List<FileItemSummary> folderItems = new ArrayList<>();
        folderItems.add(createNetworkItem(networkId1, "Network 1"));
        folderItems.add(createFolderItem(childFolderId, "Child Folder"));
        folderItems.add(createFolderTypeShortcut(shortcutId, "Folder Shortcut", childFolderId));
        folderItems.add(createNetworkItem(networkId2, "Network 2"));

        expect(mockFolderDAO.getFolder(setId, ownerId, null)).andReturn(folder);
        expect(mockFolderDAO.listItemsInFolder(setId, true, FileType.NETWORK)).andReturn(folderItems);
        mockFolderDAO.close();
        expectLastCall().once();

        replay(mockFolderDAO, mockConnection);

        NetworkSet result = dao.getNetworkSet(setId, ownerId, null);

        assertEquals(2, result.getNetworks().size());
        assertTrue(result.getNetworks().contains(networkId1));
        assertTrue(result.getNetworks().contains(networkId2));
        assertFalse(result.getNetworks().contains(childFolderId));
        assertFalse(result.getNetworks().contains(shortcutId));

        verify(mockFolderDAO, mockConnection);
    }

    @Test
    public void testGetNetworkSet_ExcludesDeletedAndInTrashNetworkShortcuts() throws Exception {
        NdexFolder folder = createFolder(setId, ownerId, "set-name");
        List<FileItemSummary> folderItems = new ArrayList<>();
        folderItems.add(createNetworkItem(networkId1, "Network 1"));
        folderItems.add(createNetworkTypeShortcut(shortcutId, "Deleted shortcut", networkId2, "DELETED"));
        folderItems.add(createNetworkTypeShortcut(UUID.randomUUID(), "In trash shortcut", networkId3, "IN_TRASH"));

        expect(mockFolderDAO.getFolder(setId, ownerId, null)).andReturn(folder);
        expect(mockFolderDAO.listItemsInFolder(setId, true, FileType.NETWORK)).andReturn(folderItems);
        mockFolderDAO.close();
        expectLastCall().once();

        replay(mockFolderDAO, mockConnection);

        NetworkSet result = dao.getNetworkSet(setId, ownerId, null);

        assertEquals(1, result.getNetworks().size());
        assertEquals(networkId1, result.getNetworks().get(0));
        assertFalse(result.getNetworks().contains(networkId2));
        assertFalse(result.getNetworks().contains(networkId3));

        verify(mockFolderDAO, mockConnection);
    }

    @Test
    public void testGetNetworkSet_DeduplicatesNetworkFromDirectAndShortcut() throws Exception {
        NdexFolder folder = createFolder(setId, ownerId, "set-name");
        List<FileItemSummary> folderItems = new ArrayList<>();
        folderItems.add(createNetworkItem(networkId1, "Network 1"));
        folderItems.add(createNetworkTypeShortcut(shortcutId, "Shortcut to Network 1", networkId1, "ACTIVE"));
        folderItems.add(createNetworkItem(networkId2, "Network 2"));

        expect(mockFolderDAO.getFolder(setId, ownerId, null)).andReturn(folder);
        expect(mockFolderDAO.listItemsInFolder(setId, true, FileType.NETWORK)).andReturn(folderItems);
        mockFolderDAO.close();
        expectLastCall().once();

        replay(mockFolderDAO, mockConnection);

        NetworkSet result = dao.getNetworkSet(setId, ownerId, null);

        assertEquals(2, result.getNetworks().size());
        assertEquals(networkId1, result.getNetworks().get(0));
        assertEquals(networkId2, result.getNetworks().get(1));

        verify(mockFolderDAO, mockConnection);
    }

    @Test
    public void testGetNetworkSet_EmptyFolder() throws Exception {
        NdexFolder folder = createFolder(setId, ownerId, "set-name");

        expect(mockFolderDAO.getFolder(setId, ownerId, null)).andReturn(folder);
        expect(mockFolderDAO.listItemsInFolder(setId, true, FileType.NETWORK)).andReturn(Collections.emptyList());
        mockFolderDAO.close();
        expectLastCall().once();

        replay(mockFolderDAO, mockConnection);

        NetworkSet result = dao.getNetworkSet(setId, ownerId, null);

        assertNotNull(result.getNetworks());
        assertEquals(0, result.getNetworks().size());

        verify(mockFolderDAO, mockConnection);
    }

    @Test(expected = ObjectNotFoundException.class)
    public void testGetNetworkSet_PropagatesObjectNotFound() throws Exception {
        expect(mockFolderDAO.getFolder(setId, ownerId, null)).andThrow(new ObjectNotFoundException("Folder", setId));
        mockFolderDAO.close();
        expectLastCall().once();

        replay(mockFolderDAO, mockConnection);

        try {
            dao.getNetworkSet(setId, ownerId, null);
        } finally {
            verify(mockFolderDAO, mockConnection);
        }
    }

    @Test
    public void testGetNetworkSetsByUserId_SummaryOnlySkipsMembers() throws Exception {
        UUID userId = ownerId;
        NdexFolder folder1 = createFolder(UUID.randomUUID(), userId, "set-1");
        NdexFolder folder2 = createFolder(UUID.randomUUID(), userId, "set-2");

        expect(mockFolderDAO.listFoldersOfUser(userId, 2)).andReturn(List.of(folder1, folder2));
        mockFolderDAO.close();
        expectLastCall().once();

        replay(mockFolderDAO, mockConnection);

        List<NetworkSet> result = dao.getNetworkSetsByUserId(userId, userId, 0, 2, true, false);

        assertEquals(2, result.size());
        assertNotNull(result.get(0).getNetworks());
        assertNotNull(result.get(1).getNetworks());
        assertTrue(result.get(0).getNetworks().isEmpty());
        assertTrue(result.get(1).getNetworks().isEmpty());

        verify(mockFolderDAO, mockConnection);
    }

    @Test
    public void testGetNetworkSetsByUserId_AppliesOffsetLimitAndNormalizesMembers() throws Exception {
        UUID userId = ownerId;
        NdexFolder folder1 = createFolder(UUID.randomUUID(), userId, "set-1");
        NdexFolder folder2 = createFolder(UUID.randomUUID(), userId, "set-2");
        NdexFolder folder3 = createFolder(UUID.randomUUID(), userId, "set-3");

        List<FileItemSummary> folder2Items = new ArrayList<>();
        folder2Items.add(createNetworkItem(networkId1, "Network 1"));
        folder2Items.add(createNetworkTypeShortcut(shortcutId, "Shortcut to Network 2", networkId2, "ACTIVE"));
        folder2Items.add(createNetworkTypeShortcut(UUID.randomUUID(), "Deleted shortcut", networkId3, "DELETED"));

        expect(mockFolderDAO.listFoldersOfUser(userId, 2)).andReturn(List.of(folder1, folder2, folder3));
        expect(mockFolderDAO.listItemsInFolder(folder2.getExternalId(), true, FileType.NETWORK)).andReturn(folder2Items);
        mockFolderDAO.close();
        expectLastCall().once();

        replay(mockFolderDAO, mockConnection);

        List<NetworkSet> result = dao.getNetworkSetsByUserId(userId, userId, 1, 1, false, false);

        assertEquals(1, result.size());
        NetworkSet only = result.get(0);
        assertEquals(folder2.getExternalId(), only.getExternalId());
        assertEquals(2, only.getNetworks().size());
        assertEquals(networkId1, only.getNetworks().get(0));
        assertEquals(networkId2, only.getNetworks().get(1));

        verify(mockFolderDAO, mockConnection);
    }

    @Test
    public void testGetNetworkSetsByUserId_OffsetBeyondRangeReturnsEmpty() throws Exception {
        UUID userId = ownerId;
        NdexFolder folder1 = createFolder(UUID.randomUUID(), userId, "set-1");

        expect(mockFolderDAO.listFoldersOfUser(userId, 4)).andReturn(List.of(folder1));
        mockFolderDAO.close();
        expectLastCall().once();

        replay(mockFolderDAO, mockConnection);

        List<NetworkSet> result = dao.getNetworkSetsByUserId(userId, userId, 3, 1, false, false);

        assertNotNull(result);
        assertTrue(result.isEmpty());

        verify(mockFolderDAO, mockConnection);
    }

    @Test
    public void testGetNetworkSetsByUserId_NonPositiveLimitUsesMaxFetch() throws Exception {
        UUID userId = ownerId;
        NdexFolder folder1 = createFolder(UUID.randomUUID(), userId, "set-1");
        List<FileItemSummary> folderItems = List.of(createNetworkItem(networkId1, "Network 1"));

        expect(mockFolderDAO.listFoldersOfUser(userId, Integer.MAX_VALUE)).andReturn(List.of(folder1));
        expect(mockFolderDAO.listItemsInFolder(folder1.getExternalId(), true, FileType.NETWORK)).andReturn(folderItems);
        mockFolderDAO.close();
        expectLastCall().once();

        replay(mockFolderDAO, mockConnection);

        List<NetworkSet> result = dao.getNetworkSetsByUserId(userId, userId, 0, 0, false, true);

        assertEquals(1, result.size());
        assertEquals(1, result.get(0).getNetworks().size());
        assertEquals(networkId1, result.get(0).getNetworks().get(0));

        verify(mockFolderDAO, mockConnection);
    }

    @Test(expected = SQLException.class)
    public void testGetNetworkSetsByUserId_PropagatesSQLExceptionFromListFolders() throws Exception {
        UUID userId = ownerId;

        expect(mockFolderDAO.listFoldersOfUser(userId, 1)).andThrow(new SQLException("listFolders failed"));
        mockFolderDAO.close();
        expectLastCall().once();

        replay(mockFolderDAO, mockConnection);

        try {
            dao.getNetworkSetsByUserId(userId, userId, 0, 1, false, false);
        } finally {
            verify(mockFolderDAO, mockConnection);
        }
    }

    private NdexFolder createFolder(UUID folderId, UUID folderOwnerId, String name) {
        NdexFolder folder = new NdexFolder();
        folder.setExternalId(folderId);
        folder.setOwner_id(folderOwnerId.toString());
        folder.setName(name);
        folder.setDescription("set-description");
        folder.setCreationTime(new Timestamp(System.currentTimeMillis()));
        folder.setModificationTime(new Timestamp(System.currentTimeMillis()));
        return folder;
    }

    private FileItemSummary createNetworkItem(UUID networkId, String name) {
        return new FileItemSummary(networkId, FileType.NETWORK, name,
            new Timestamp(System.currentTimeMillis()), "testuser", null);
    }

    private FileItemSummary createFolderItem(UUID folderId, String name) {
        return new FileItemSummary(folderId, FileType.FOLDER, name,
            new Timestamp(System.currentTimeMillis()), "testuser", null);
    }

    private FileItemSummary createFolderTypeShortcut(UUID shortcutId, String name, UUID targetId) {
        Map<String, Object> attrs = new HashMap<>();
        attrs.put("target_type", "FOLDER");
        attrs.put("target", targetId);
        attrs.put("target_status", "ACTIVE");
        return new FileItemSummary(shortcutId, FileType.SHORTCUT, name,
            new Timestamp(System.currentTimeMillis()), "testuser", attrs);
    }

    private FileItemSummary createNetworkTypeShortcut(UUID shortcutId, String name, UUID targetId, String targetStatus) {
        Map<String, Object> attrs = new HashMap<>();
        attrs.put("target_type", "NETWORK");
        attrs.put("target", targetId);
        attrs.put("target_status", targetStatus);
        return new FileItemSummary(shortcutId, FileType.SHORTCUT, name,
            new Timestamp(System.currentTimeMillis()), "testuser", attrs);
    }
}
