package org.ndexbio.common.solr;

import org.apache.solr.client.solrj.SolrQuery;
import org.apache.solr.client.solrj.SolrServerException;
import org.apache.solr.client.solrj.response.QueryResponse;
import org.apache.solr.common.SolrDocument;
import org.apache.solr.common.SolrDocumentList;
import org.apache.solr.common.SolrInputDocument;
import org.junit.After;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Ignore;
import org.junit.Test;
import org.easymock.Capture;
import org.easymock.CaptureType;
import org.ndexbio.model.object.FileType;
import org.ndexbio.model.object.NdexFolder;
import org.ndexbio.model.object.Permissions;
import org.ndexbio.model.object.network.VisibilityType;
import org.ndexbio.model.tools.SearchUtilities;
import org.ndexbio.rest.Configuration;

import java.io.IOException;
import java.lang.reflect.Field;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.*;

import static org.easymock.EasyMock.*;
import static org.junit.Assert.*;

/**
 * Unit tests for FolderIndexManager using mocked SolrClientWrapper.
 * Integration tests (requiring live Solr) are kept but @Ignore'd.
 */
public class TestFolderIndexManager {

    private static Configuration savedInstance;
    private FolderIndexManager manager;
    private SolrClientWrapper mockWrapper;

    @BeforeClass
    public static void setUpClass() throws Exception {
        Configuration mockConfig = createMock(Configuration.class);
        expect(mockConfig.getSolrURL()).andReturn("http://localhost:8983/solr").anyTimes();
        replay(mockConfig);

        Field instanceField = Configuration.class.getDeclaredField("INSTANCE");
        instanceField.setAccessible(true);
        savedInstance = (Configuration) instanceField.get(null);
        instanceField.set(null, mockConfig);
    }

    @AfterClass
    public static void tearDownClass() {
        Configuration.setInstance(savedInstance);
    }

    @After
    public void tearDown() {
        if (manager != null) {
            manager.close();
        }
    }

    private FolderIndexManager createManagerWithMock() {
        mockWrapper = createNiceMock(SolrClientWrapper.class);
        replay(mockWrapper);
        return new FolderIndexManager(mockWrapper);
    }

    private FolderIndexManager createManagerWithStrictMock() {
        mockWrapper = createMock(SolrClientWrapper.class);
        return new FolderIndexManager(mockWrapper);
    }

    // ========================================================================
    // SETUP INDEX DOCUMENT TESTS - COMPLETE FOLDERS
    // ========================================================================

    @Test
    public void testSetupIndexDocument_AllFieldsSet() {
        manager = createManagerWithMock();

        UUID folderId = UUID.randomUUID();
        UUID parentId = UUID.randomUUID();
        Timestamp creationTime = Timestamp.from(Instant.now().minusSeconds(3600));
        Timestamp modificationTime = Timestamp.from(Instant.now());

        NdexFolder folder = new NdexFolder();
        folder.setExternalId(folderId);
        folder.setName("Test Folder");
        folder.setDescription("Test Description");
        folder.setOwner("testOwner");
        folder.setParent(parentId);
        folder.setCreationTime(creationTime);
        folder.setModificationTime(modificationTime);

        SolrInputDocument doc = manager.setupIndexDocument(folder, VisibilityType.PUBLIC);

        assertNotNull(doc);
        assertEquals(folderId.toString(), doc.getFieldValue("uuid"));
        assertEquals("FOLDER", doc.getFieldValue("entityType"));
        assertEquals("Test Folder", doc.getFieldValue("name"));
        assertEquals("Test Description", doc.getFieldValue("description"));
        assertEquals("testOwner", doc.getFieldValue("owner"));
        assertEquals(parentId.toString(), doc.getFieldValue("parentUuid"));
        assertEquals(creationTime, doc.getFieldValue("creationTime"));
        assertEquals(modificationTime, doc.getFieldValue("modificationTime"));
    }

    @Test
    public void testSetupIndexDocument_MinimalFolder_OnlyRequiredFields() {
        manager = createManagerWithMock();

        UUID folderId = UUID.randomUUID();
        Timestamp now = Timestamp.from(Instant.now());

        NdexFolder folder = new NdexFolder();
        folder.setExternalId(folderId);
        folder.setCreationTime(now);
        folder.setModificationTime(now);

        SolrInputDocument doc = manager.setupIndexDocument(folder, VisibilityType.PUBLIC);

        assertNotNull(doc);
        assertEquals(folderId.toString(), doc.getFieldValue("uuid"));
        assertEquals("FOLDER", doc.getFieldValue("entityType"));
        assertNull(doc.getFieldValue("name"));
        assertNull(doc.getFieldValue("description"));
        assertNull(doc.getFieldValue("owner"));
        assertNull(doc.getFieldValue("parentUuid"));
    }

    @Test
    public void testSetupIndexDocument_NoParent_ParentFieldNull() {
        manager = createManagerWithMock();
        NdexFolder folder = createTestFolder("Root Folder", "Root folder without parent");
        folder.setParent(null);

        SolrInputDocument doc = manager.setupIndexDocument(folder, VisibilityType.PUBLIC);

        assertNull(doc.getFieldValue("parentUuid"));
    }

    // ========================================================================
    // NAME FIELD VALIDATION
    // ========================================================================

    @Test
    public void testSetupIndexDocument_NullName_NotIndexed() {
        manager = createManagerWithMock();
        NdexFolder folder = createTestFolder(null, "Description");
        SolrInputDocument doc = manager.setupIndexDocument(folder, VisibilityType.PUBLIC);
        assertNull(doc.getFieldValue("name"));
    }

    @Test
    public void testSetupIndexDocument_EmptyName_NotIndexed() {
        manager = createManagerWithMock();
        NdexFolder folder = createTestFolder("", "Description");
        SolrInputDocument doc = manager.setupIndexDocument(folder, VisibilityType.PUBLIC);
        assertNull(doc.getFieldValue("name"));
    }

    @Test
    public void testSetupIndexDocument_WhitespaceOnlyName_NotIndexed() {
        manager = createManagerWithMock();
        NdexFolder folder = createTestFolder("   ", "Description");
        SolrInputDocument doc = manager.setupIndexDocument(folder, VisibilityType.PUBLIC);
        assertNull(doc.getFieldValue("name"));
    }

    @Test
    public void testSetupIndexDocument_ValidName_IsIndexed() {
        manager = createManagerWithMock();
        NdexFolder folder = createTestFolder("My Folder", "Description");
        SolrInputDocument doc = manager.setupIndexDocument(folder, VisibilityType.PUBLIC);
        assertEquals("My Folder", doc.getFieldValue("name"));
    }

    @Test
    public void testSetupIndexDocument_NameWithSpecialCharacters() {
        manager = createManagerWithMock();
        String specialName = "Folder-Name_2024 (Test) [v1.0]";
        NdexFolder folder = createTestFolder(specialName, "Description");
        SolrInputDocument doc = manager.setupIndexDocument(folder, VisibilityType.PUBLIC);
        assertEquals(specialName, doc.getFieldValue("name"));
    }

    @Test
    public void testSetupIndexDocument_NameWithUnicode() {
        manager = createManagerWithMock();
        String unicodeName = "文件夹 Папка مجلد";
        NdexFolder folder = createTestFolder(unicodeName, "Description");
        SolrInputDocument doc = manager.setupIndexDocument(folder, VisibilityType.PUBLIC);
        assertEquals(unicodeName, doc.getFieldValue("name"));
    }

    // ========================================================================
    // DESCRIPTION FIELD VALIDATION
    // ========================================================================

    @Test
    public void testSetupIndexDocument_NullDescription_NotIndexed() {
        manager = createManagerWithMock();
        NdexFolder folder = new NdexFolder();
        folder.setExternalId(UUID.randomUUID());
        folder.setName("Name");
        folder.setOwner("owner");
        folder.setCreationTime(Timestamp.from(Instant.now()));
        folder.setModificationTime(Timestamp.from(Instant.now()));

        SolrInputDocument doc = manager.setupIndexDocument(folder, VisibilityType.PUBLIC);
        assertNull(doc.getFieldValue("description"));
    }

    @Test
    public void testSetupIndexDocument_EmptyDescription_NotIndexed() {
        manager = createManagerWithMock();
        NdexFolder folder = createTestFolder("Name", "");
        SolrInputDocument doc = manager.setupIndexDocument(folder, VisibilityType.PUBLIC);
        assertNull(doc.getFieldValue("description"));
    }

    @Test
    public void testSetupIndexDocument_WhitespaceOnlyDescription_NotIndexed() {
        manager = createManagerWithMock();
        NdexFolder folder = createTestFolder("Name", "   ");
        SolrInputDocument doc = manager.setupIndexDocument(folder, VisibilityType.PUBLIC);
        assertNull(doc.getFieldValue("description"));
    }

    @Test
    public void testSetupIndexDocument_ValidDescription_IsIndexed() {
        manager = createManagerWithMock();
        NdexFolder folder = createTestFolder("Name", "Valid description");
        SolrInputDocument doc = manager.setupIndexDocument(folder, VisibilityType.PUBLIC);
        assertEquals("Valid description", doc.getFieldValue("description"));
    }

    // ========================================================================
    // OWNER FIELD VALIDATION
    // ========================================================================

    @Test
    public void testSetupIndexDocument_NullOwner_NotIndexed() {
        manager = createManagerWithMock();
        NdexFolder folder = createTestFolder("Name", "Desc");
        folder.setOwner(null);
        SolrInputDocument doc = manager.setupIndexDocument(folder, VisibilityType.PUBLIC);
        assertNull(doc.getFieldValue("owner"));
    }

    @Test
    public void testSetupIndexDocument_EmptyOwner_NotIndexed() {
        manager = createManagerWithMock();
        NdexFolder folder = createTestFolder("Name", "Desc");
        folder.setOwner("");
        SolrInputDocument doc = manager.setupIndexDocument(folder, VisibilityType.PUBLIC);
        assertNull(doc.getFieldValue("owner"));
    }

    @Test
    public void testSetupIndexDocument_BlankOwner_NotIndexed() {
        manager = createManagerWithMock();
        NdexFolder folder = createTestFolder("Name", "Desc");
        folder.setOwner("   ");
        SolrInputDocument doc = manager.setupIndexDocument(folder, VisibilityType.PUBLIC);
        assertNull(doc.getFieldValue("owner"));
    }

    @Test
    public void testSetupIndexDocument_BlankOwnerTabsNewlines_NotIndexed() {
        manager = createManagerWithMock();
        NdexFolder folder = createTestFolder("Name", "Desc");
        folder.setOwner("\t\n  \r\n");
        SolrInputDocument doc = manager.setupIndexDocument(folder, VisibilityType.PUBLIC);
        assertNull(doc.getFieldValue("owner"));
    }

    @Test
    public void testSetupIndexDocument_OwnerWithLeadingTrailingSpaces_IsIndexed() {
        manager = createManagerWithMock();
        NdexFolder folder = createTestFolder("Name", "Desc");
        folder.setOwner("  john_doe  ");
        SolrInputDocument doc = manager.setupIndexDocument(folder, VisibilityType.PUBLIC);
        assertEquals("  john_doe  ", doc.getFieldValue("owner"));
    }

    @Test
    public void testSetupIndexDocument_OwnerWithEmailFormat() {
        manager = createManagerWithMock();
        NdexFolder folder = createTestFolder("Name", "Desc");
        folder.setOwner("user@example.com");
        SolrInputDocument doc = manager.setupIndexDocument(folder, VisibilityType.PUBLIC);
        assertEquals("user@example.com", doc.getFieldValue("owner"));
    }

    // ========================================================================
    // PREPARE INDEX DOCUMENT (BASE CLASS) - VISIBILITY + PERMISSIONS
    // ========================================================================

    @Test
    public void testPrepareIndexDocument_PublicVisibility_NoPermissionFields() {
        manager = createManagerWithMock();
        NdexFolder folder = createTestFolder("Public Folder", "Public");

        List<String> readers = Arrays.asList("reader1", "reader2");
        List<String> editors = Arrays.asList("editor1");

        manager.prepareIndexDocument(folder, VisibilityType.PUBLIC, readers, editors);

        SolrInputDocument doc = manager.doc;
        assertEquals("PUBLIC", doc.getFieldValue("visibility"));
        // PUBLIC items should NOT have userRead/userEdit fields
        assertNull(doc.getFieldValue("userRead"));
        assertNull(doc.getFieldValue("userEdit"));
    }

    @Test
    public void testPrepareIndexDocument_PrivateVisibility_HasPermissionFields() {
        manager = createManagerWithMock();
        NdexFolder folder = createTestFolder("Private Folder", "Private");

        List<String> readers = Arrays.asList("reader1", "reader2");
        List<String> editors = Arrays.asList("editor1");

        manager.prepareIndexDocument(folder, VisibilityType.PRIVATE, readers, editors);

        SolrInputDocument doc = manager.doc;
        assertEquals("PRIVATE", doc.getFieldValue("visibility"));
        Collection<Object> readValues = doc.getFieldValues("userRead");
        assertNotNull(readValues);
        assertTrue(readValues.contains("reader1"));
        assertTrue(readValues.contains("reader2"));
        Collection<Object> editValues = doc.getFieldValues("userEdit");
        assertNotNull(editValues);
        assertTrue(editValues.contains("editor1"));
    }

    @Test
    public void testPrepareIndexDocument_PrivateVisibility_NullPermissions() {
        manager = createManagerWithMock();
        NdexFolder folder = createTestFolder("Private Folder", "Private");

        manager.prepareIndexDocument(folder, VisibilityType.PRIVATE, null, null);

        SolrInputDocument doc = manager.doc;
        assertEquals("PRIVATE", doc.getFieldValue("visibility"));
        assertNull(doc.getFieldValue("userRead"));
        assertNull(doc.getFieldValue("userEdit"));
    }

    @Test
    public void testPrepareIndexDocument_PrivateVisibility_BlankValuesSkipped() {
        manager = createManagerWithMock();
        NdexFolder folder = createTestFolder("Private Folder", "Private");

        List<String> readers = Arrays.asList("reader1", "", "  ", null, "reader2");
        List<String> editors = Arrays.asList("", null);

        manager.prepareIndexDocument(folder, VisibilityType.PRIVATE, readers, editors);

        SolrInputDocument doc = manager.doc;
        Collection<Object> readValues = doc.getFieldValues("userRead");
        assertNotNull(readValues);
        assertEquals("Should only have 2 valid readers", 2, readValues.size());
        assertTrue(readValues.contains("reader1"));
        assertTrue(readValues.contains("reader2"));
        // All editors were blank/null
        assertNull(doc.getFieldValue("userEdit"));
    }

    // ========================================================================
    // CREATE INDEX - VERIFIES COMMIT TO CORRECT CORE
    // ========================================================================

    @Test
    public void testCreateIndex_PublicFolder_CommitsToPublicCore() throws Exception {
        mockWrapper = createMock(SolrClientWrapper.class);
        Capture<String> coreCapture = Capture.newInstance();
        Capture<Collection<SolrInputDocument>> docsCapture = Capture.newInstance();

        mockWrapper.commit(capture(coreCapture), capture(docsCapture));
        expectLastCall().once();
        mockWrapper.close();
        expectLastCall().anyTimes();
        replay(mockWrapper);

        manager = new FolderIndexManager(mockWrapper);
        NdexFolder folder = createTestFolder("Test", "Test");

        manager.createIndex(folder, VisibilityType.PUBLIC, null, null);

        verify(mockWrapper);
        assertEquals("public-nfs", coreCapture.getValue());
        assertNotNull(docsCapture.getValue());
        assertEquals(1, docsCapture.getValue().size());

        SolrInputDocument committed = docsCapture.getValue().iterator().next();
        assertEquals("FOLDER", committed.getFieldValue("entityType"));
        assertEquals("Test", committed.getFieldValue("name"));
    }

    @Test
    public void testCreateIndex_PrivateFolder_CommitsToPrivateCore() throws Exception {
        mockWrapper = createMock(SolrClientWrapper.class);
        Capture<String> coreCapture = Capture.newInstance();
        Capture<Collection<SolrInputDocument>> docsCapture = Capture.newInstance();

        mockWrapper.commit(capture(coreCapture), capture(docsCapture));
        expectLastCall().once();
        mockWrapper.close();
        expectLastCall().anyTimes();
        replay(mockWrapper);

        manager = new FolderIndexManager(mockWrapper);
        NdexFolder folder = createTestFolder("Private", "Private folder");
        List<String> readers = Arrays.asList("user1");

        manager.createIndex(folder, VisibilityType.PRIVATE, readers, null);

        verify(mockWrapper);
        assertEquals("private-nfs", coreCapture.getValue());

        SolrInputDocument committed = docsCapture.getValue().iterator().next();
        assertEquals("PRIVATE", committed.getFieldValue("visibility"));
        Collection<Object> readValues = committed.getFieldValues("userRead");
        assertNotNull(readValues);
        assertTrue(readValues.contains("user1"));
    }

    // ========================================================================
    // DELETE - VERIFIES CORRECT CORE
    // ========================================================================

    @Test
    public void testDelete_PublicFolder_DeletesFromPublicCore() throws Exception {
        mockWrapper = createMock(SolrClientWrapper.class);
        Capture<String> coreCapture = Capture.newInstance();
        Capture<String> uuidCapture = Capture.newInstance();

        mockWrapper.delete(capture(coreCapture), capture(uuidCapture), eq(false));
        expectLastCall().once();
        mockWrapper.close();
        expectLastCall().anyTimes();
        replay(mockWrapper);

        manager = new FolderIndexManager(mockWrapper);
        manager.delete("test-uuid-123", VisibilityType.PUBLIC);

        verify(mockWrapper);
        assertEquals("public-nfs", coreCapture.getValue());
        assertEquals("test-uuid-123", uuidCapture.getValue());
    }

    @Test
    public void testDelete_PrivateFolder_DeletesFromPrivateCore() throws Exception {
        mockWrapper = createMock(SolrClientWrapper.class);
        Capture<String> coreCapture = Capture.newInstance();

        mockWrapper.delete(capture(coreCapture), anyString(), eq(false));
        expectLastCall().once();
        mockWrapper.close();
        expectLastCall().anyTimes();
        replay(mockWrapper);

        manager = new FolderIndexManager(mockWrapper);
        manager.delete("test-uuid-456", VisibilityType.PRIVATE);

        verify(mockWrapper);
        assertEquals("private-nfs", coreCapture.getValue());
    }

    // ========================================================================
    // SEARCH - VERIFIES QUERY SENT TO SOLR
    // ========================================================================

    @Test
    public void testSearch_PublicCore_AnonymousUser_WildcardQuery() throws Exception {
        SolrDocumentList mockResults = new SolrDocumentList();
        mockResults.setNumFound(0);

        QueryResponse mockResponse = createMock(QueryResponse.class);
        expect(mockResponse.getResults()).andReturn(mockResults);
        replay(mockResponse);

        mockWrapper = createMock(SolrClientWrapper.class);
        Capture<String> coreCapture = Capture.newInstance();
        Capture<SolrQuery> queryCapture = Capture.newInstance();

        expect(mockWrapper.query(capture(coreCapture), capture(queryCapture)))
                .andReturn(mockResponse);
        mockWrapper.close();
        expectLastCall().anyTimes();
        replay(mockWrapper);

        manager = new FolderIndexManager(mockWrapper);
        SolrDocumentList results = manager.search(
                "*:*", null, VisibilityType.PUBLIC, 10, 0, null, null);

        verify(mockWrapper);
        assertEquals("public-nfs", coreCapture.getValue());

        SolrQuery captured = queryCapture.getValue();
        assertEquals("*:*", captured.getQuery());
        assertEquals("edismax", captured.get("defType"));

        // Anonymous public: filter should be (*:*)
        String[] fq = captured.getFilterQueries();
        assertNotNull(fq);
        assertTrue(fq[0].contains("(*:* NOT visibility:UNLISTED)"));
        // Wildcard should sort by modificationTime desc
        assertFalse(captured.getSorts().isEmpty());
        assertEquals("modificationTime", captured.getSorts().get(0).getItem());
    }

    @Test
    public void testSearch_PrivateCore_AnonymousUser_MatchesNothing() throws Exception {
        SolrDocumentList mockResults = new SolrDocumentList();
        mockResults.setNumFound(0);

        QueryResponse mockResponse = createMock(QueryResponse.class);
        expect(mockResponse.getResults()).andReturn(mockResults);
        replay(mockResponse);

        mockWrapper = createMock(SolrClientWrapper.class);
        Capture<SolrQuery> queryCapture = Capture.newInstance();
        expect(mockWrapper.query(eq("private-nfs"), capture(queryCapture)))
                .andReturn(mockResponse);
        mockWrapper.close();
        expectLastCall().anyTimes();
        replay(mockWrapper);

        manager = new FolderIndexManager(mockWrapper);
        SolrDocumentList results = manager.search(
                "*:*", null, VisibilityType.PRIVATE, 10, 0, null, null);

        String[] fq = queryCapture.getValue().getFilterQueries();
        assertTrue("Anonymous private filter should match nothing",
                fq[0].contains("(*:* AND NOT *:*)"));
    }

    @Test
    public void testSearch_WithOwnerFilter() throws Exception {
        SolrDocumentList mockResults = new SolrDocumentList();
        mockResults.setNumFound(0);

        QueryResponse mockResponse = createMock(QueryResponse.class);
        expect(mockResponse.getResults()).andReturn(mockResults);
        replay(mockResponse);

        mockWrapper = createMock(SolrClientWrapper.class);
        Capture<SolrQuery> queryCapture = Capture.newInstance();
        expect(mockWrapper.query(anyString(), capture(queryCapture)))
                .andReturn(mockResponse);
        mockWrapper.close();
        expectLastCall().anyTimes();
        replay(mockWrapper);

        manager = new FolderIndexManager(mockWrapper);
        manager.search("test", "user1", VisibilityType.PUBLIC, 10, 0, "specificOwner", null);

        String[] fq = queryCapture.getValue().getFilterQueries();
        assertTrue("Should contain owner filter",
                fq[0].contains("owner:\"specificOwner\""));
    }

    @Test
    public void testSearch_Pagination() throws Exception {
        SolrDocumentList mockResults = new SolrDocumentList();
        mockResults.setNumFound(0);

        QueryResponse mockResponse = createMock(QueryResponse.class);
        expect(mockResponse.getResults()).andReturn(mockResults);
        replay(mockResponse);

        mockWrapper = createMock(SolrClientWrapper.class);
        Capture<SolrQuery> queryCapture = Capture.newInstance();
        expect(mockWrapper.query(anyString(), capture(queryCapture)))
                .andReturn(mockResponse);
        mockWrapper.close();
        expectLastCall().anyTimes();
        replay(mockWrapper);

        manager = new FolderIndexManager(mockWrapper);
        manager.search("test", null, VisibilityType.PUBLIC, 25, 50, null, null);

        SolrQuery captured = queryCapture.getValue();
        assertEquals(Integer.valueOf(50), captured.getStart());
        assertEquals(Integer.valueOf(25), captured.getRows());
    }

    @Test
    public void testSearch_PrivateCore_AuthenticatedUser_ReadPermission() throws Exception {
        SolrDocumentList mockResults = new SolrDocumentList();
        mockResults.setNumFound(0);

        QueryResponse mockResponse = createMock(QueryResponse.class);
        expect(mockResponse.getResults()).andReturn(mockResults);
        replay(mockResponse);

        mockWrapper = createMock(SolrClientWrapper.class);
        Capture<SolrQuery> queryCapture = Capture.newInstance();
        expect(mockWrapper.query(eq("private-nfs"), capture(queryCapture)))
                .andReturn(mockResponse);
        mockWrapper.close();
        expectLastCall().anyTimes();
        replay(mockWrapper);

        manager = new FolderIndexManager(mockWrapper);
        manager.search("*:*", "charlie", VisibilityType.PRIVATE, 10, 0, null, Permissions.READ);

        String[] fq = queryCapture.getValue().getFilterQueries();
        assertTrue(fq[0].contains("owner:\"charlie\""));
        assertTrue(fq[0].contains("userRead:\"charlie\""));
        assertTrue(fq[0].contains("userEdit:\"charlie\""));
    }

    @Test
    public void testSearch_PrivateCore_AuthenticatedUser_WritePermission() throws Exception {
        SolrDocumentList mockResults = new SolrDocumentList();
        mockResults.setNumFound(0);

        QueryResponse mockResponse = createMock(QueryResponse.class);
        expect(mockResponse.getResults()).andReturn(mockResults);
        replay(mockResponse);

        mockWrapper = createMock(SolrClientWrapper.class);
        Capture<SolrQuery> queryCapture = Capture.newInstance();
        expect(mockWrapper.query(eq("private-nfs"), capture(queryCapture)))
                .andReturn(mockResponse);
        mockWrapper.close();
        expectLastCall().anyTimes();
        replay(mockWrapper);

        manager = new FolderIndexManager(mockWrapper);
        manager.search("*:*", "david", VisibilityType.PRIVATE, 10, 0, null, Permissions.WRITE);

        String[] fq = queryCapture.getValue().getFilterQueries();
        assertTrue(fq[0].contains("owner:\"david\""));
        assertTrue(fq[0].contains("userEdit:\"david\""));
        assertFalse("WRITE should not include userRead", fq[0].contains("userRead"));
    }

    @Test
    public void testSearch_PrivateCore_AuthenticatedUser_AdminPermission() throws Exception {
        SolrDocumentList mockResults = new SolrDocumentList();
        mockResults.setNumFound(0);

        QueryResponse mockResponse = createMock(QueryResponse.class);
        expect(mockResponse.getResults()).andReturn(mockResults);
        replay(mockResponse);

        mockWrapper = createMock(SolrClientWrapper.class);
        Capture<SolrQuery> queryCapture = Capture.newInstance();
        expect(mockWrapper.query(eq("private-nfs"), capture(queryCapture)))
                .andReturn(mockResponse);
        mockWrapper.close();
        expectLastCall().anyTimes();
        replay(mockWrapper);

        manager = new FolderIndexManager(mockWrapper);
        manager.search("*:*", "admin", VisibilityType.PRIVATE, 10, 0, null, Permissions.ADMIN);

        String[] fq = queryCapture.getValue().getFilterQueries();
        assertTrue(fq[0].contains("owner:\"admin\""));
        assertFalse(fq[0].contains("userRead"));
        assertFalse(fq[0].contains("userEdit"));
    }

    @Test
    public void testSearch_PublicCore_WritePermission_FiltersToOwnedAndEditable() throws Exception {
        SolrDocumentList mockResults = new SolrDocumentList();
        mockResults.setNumFound(0);

        QueryResponse mockResponse = createMock(QueryResponse.class);
        expect(mockResponse.getResults()).andReturn(mockResults);
        replay(mockResponse);

        mockWrapper = createMock(SolrClientWrapper.class);
        Capture<SolrQuery> queryCapture = Capture.newInstance();
        expect(mockWrapper.query(eq("public-nfs"), capture(queryCapture)))
                .andReturn(mockResponse);
        mockWrapper.close();
        expectLastCall().anyTimes();
        replay(mockWrapper);

        manager = new FolderIndexManager(mockWrapper);
        manager.search("*:*", "bob", VisibilityType.PUBLIC, 10, 0, null, Permissions.WRITE);

        String[] fq = queryCapture.getValue().getFilterQueries();
        assertTrue(fq[0].contains("owner:\"bob\""));
        assertTrue(fq[0].contains("userEdit:\"bob\""));
        assertFalse(fq[0].contains("userRead"));
    }

    // ========================================================================
    // SEARCH BY TYPE
    // ========================================================================

    @Test
    public void testSearchByType_AddsEntityTypeFilter() throws Exception {
        SolrDocumentList mockResults = new SolrDocumentList();
        mockResults.setNumFound(0);

        QueryResponse mockResponse = createMock(QueryResponse.class);
        expect(mockResponse.getResults()).andReturn(mockResults);
        replay(mockResponse);

        mockWrapper = createMock(SolrClientWrapper.class);
        Capture<SolrQuery> queryCapture = Capture.newInstance();
        expect(mockWrapper.query(anyString(), capture(queryCapture)))
                .andReturn(mockResponse);
        mockWrapper.close();
        expectLastCall().anyTimes();
        replay(mockWrapper);

        manager = new FolderIndexManager(mockWrapper);
        manager.searchByType("test", "user", VisibilityType.PUBLIC, 10, 0,
                null, null, "FOLDER");

        String[] fq = queryCapture.getValue().getFilterQueries();
        assertTrue(fq[0].contains("entityType:\"FOLDER\""));
    }

    // ========================================================================
    // SEARCH IN FOLDER
    // ========================================================================

    @Test
    public void testSearchInFolder_WithParentFilter() throws Exception {
        UUID parentId = UUID.randomUUID();
        SolrDocumentList mockResults = new SolrDocumentList();
        mockResults.setNumFound(2);

        QueryResponse mockResponse = createMock(QueryResponse.class);
        expect(mockResponse.getResults()).andReturn(mockResults);
        replay(mockResponse);

        mockWrapper = createMock(SolrClientWrapper.class);
        Capture<SolrQuery> queryCapture = Capture.newInstance();
        expect(mockWrapper.query(eq("public-nfs"), capture(queryCapture)))
                .andReturn(mockResponse);
        mockWrapper.close();
        expectLastCall().anyTimes();
        replay(mockWrapper);

        manager = new FolderIndexManager(mockWrapper);
        SolrDocumentList results = manager.searchInFolder(
                "*:*", "testUser", 10, 0, parentId.toString(), null, VisibilityType.PUBLIC);

        verify(mockWrapper);
        String[] fq = queryCapture.getValue().getFilterQueries();
        assertTrue("Should contain entity type filter", fq[0].contains("entityType:FOLDER"));
        assertTrue("Should contain parent filter",
                fq[0].contains("parentUuid:\"" + parentId + "\""));
    }

    @Test
    public void testSearchInFolder_NullParent_NoParentFilter() throws Exception {
        SolrDocumentList mockResults = new SolrDocumentList();
        mockResults.setNumFound(0);

        QueryResponse mockResponse = createMock(QueryResponse.class);
        expect(mockResponse.getResults()).andReturn(mockResults);
        replay(mockResponse);

        mockWrapper = createMock(SolrClientWrapper.class);
        Capture<SolrQuery> queryCapture = Capture.newInstance();
        expect(mockWrapper.query(anyString(), capture(queryCapture)))
                .andReturn(mockResponse);
        mockWrapper.close();
        expectLastCall().anyTimes();
        replay(mockWrapper);

        manager = new FolderIndexManager(mockWrapper);
        manager.searchInFolder("*:*", "user", 10, 0, null, null, VisibilityType.PUBLIC);

        String[] fq = queryCapture.getValue().getFilterQueries();
        assertTrue("Should contain entity type filter", fq[0].contains("entityType:FOLDER"));
        assertFalse("Should NOT contain parent filter", fq[0].contains("parentUuid"));
    }

    @Test
    public void testSearchInFolder_PrivateCore_RespectsPermissions() throws Exception {
        UUID parentId = UUID.randomUUID();
        SolrDocumentList mockResults = new SolrDocumentList();
        mockResults.setNumFound(0);

        QueryResponse mockResponse = createMock(QueryResponse.class);
        expect(mockResponse.getResults()).andReturn(mockResults);
        replay(mockResponse);

        mockWrapper = createMock(SolrClientWrapper.class);
        Capture<SolrQuery> queryCapture = Capture.newInstance();
        expect(mockWrapper.query(eq("private-nfs"), capture(queryCapture)))
                .andReturn(mockResponse);
        mockWrapper.close();
        expectLastCall().anyTimes();
        replay(mockWrapper);

        manager = new FolderIndexManager(mockWrapper);
        manager.searchInFolder("*:*", "alice", 10, 0, parentId.toString(),
                Permissions.ADMIN, VisibilityType.PRIVATE);

        String[] fq = queryCapture.getValue().getFilterQueries();
        assertTrue("Should contain admin permission filter", fq[0].contains("owner:\"alice\""));
        assertFalse("ADMIN should not include userRead", fq[0].contains("userRead"));
        assertTrue("Should contain entity type", fq[0].contains("entityType:FOLDER"));
        assertTrue("Should contain parent filter", fq[0].contains("parentUuid:\"" + parentId + "\""));
    }

    @Test
    public void testSearchInFolder_AnonymousPrivate_MatchesNothing() throws Exception {
        SolrDocumentList mockResults = new SolrDocumentList();
        mockResults.setNumFound(0);

        QueryResponse mockResponse = createMock(QueryResponse.class);
        expect(mockResponse.getResults()).andReturn(mockResults);
        replay(mockResponse);

        mockWrapper = createMock(SolrClientWrapper.class);
        Capture<SolrQuery> queryCapture = Capture.newInstance();
        expect(mockWrapper.query(eq("private-nfs"), capture(queryCapture)))
                .andReturn(mockResponse);
        mockWrapper.close();
        expectLastCall().anyTimes();
        replay(mockWrapper);

        manager = new FolderIndexManager(mockWrapper);
        manager.searchInFolder("*:*", null, 10, 0, UUID.randomUUID().toString(),
                null, VisibilityType.PRIVATE);

        String[] fq = queryCapture.getValue().getFilterQueries();
        assertTrue("Anonymous private should match nothing",
                fq[0].contains("(*:* AND NOT *:*)"));
    }

    // ========================================================================
    // QUERY FIELDS
    // ========================================================================

    @Test
    public void testGetQueryFields_ReturnsExpectedString() {
        manager = createManagerWithMock();
        assertEquals("uuid^20 name^10 description^5 owner^2", manager.getQueryFields());
    }

    @Test
    public void testGetQueryFields_DoesNotContainNetworkSpecificFields() {
        manager = createManagerWithMock();
        String qf = manager.getQueryFields();
        assertFalse(qf.contains("nodeName"));
        assertFalse(qf.contains("represents"));
        assertFalse(qf.contains("organism"));
    }

    // ========================================================================
    // CONFIGURE QUERY TESTS
    // ========================================================================

    @Test
    public void testConfigureQuery_WildcardQuery_SortsByModificationTime() {
        manager = createManagerWithMock();
        SolrQuery q = new SolrQuery();
        manager.configureQuery(q, "*:*", "filter", 10, 0);

        assertFalse(q.getSorts().isEmpty());
        assertEquals("modificationTime", q.getSorts().get(0).getItem());
        assertEquals(SolrQuery.ORDER.desc, q.getSorts().get(0).getOrder());
    }

    @Test
    public void testConfigureQuery_RegularQuery_NoDefaultSort() {
        manager = createManagerWithMock();
        SolrQuery q = new SolrQuery();
        manager.configureQuery(q, "test query", "filter", 10, 0);

        assertTrue(q.getSorts() == null || q.getSorts().isEmpty());
    }

    @Test
    public void testConfigureQuery_SetsEdismaxAndQueryFields() {
        manager = createManagerWithMock();
        SolrQuery q = new SolrQuery();
        manager.configureQuery(q, "test", "filter", 10, 0);

        assertEquals("edismax", q.get("defType"));
        assertEquals("uuid^20 name^10 description^5 owner^2", q.get("qf"));
    }

    @Test
    public void testConfigureQuery_ZeroOrNegativeLimit_UsesDefault() {
        manager = createManagerWithMock();

        SolrQuery q1 = new SolrQuery();
        manager.configureQuery(q1, "test", "filter", 0, 0);
        assertEquals(Integer.valueOf(100000), q1.getRows());

        SolrQuery q2 = new SolrQuery();
        manager.configureQuery(q2, "test", "filter", -5, 0);
        assertEquals(Integer.valueOf(100000), q2.getRows());
    }

    @Test
    public void testConfigureQuery_NegativeOffset_NotSet() {
        manager = createManagerWithMock();
        SolrQuery q = new SolrQuery();
        manager.configureQuery(q, "test", "filter", 10, -1);
        assertNull(q.getStart());
    }

    @Test
    public void testConfigureQuery_AppliesFilterQuery() {
        manager = createManagerWithMock();
        String filter = "visibility:PUBLIC AND entityType:\"FOLDER\"";
        SolrQuery q = new SolrQuery();
        manager.configureQuery(q, "test", filter, 10, 0);

        String[] fqs = q.getFilterQueries();
        assertNotNull(fqs);
        assertEquals(1, fqs.length);
        assertEquals(filter, fqs[0]);
    }

    @Test
    public void testConfigureQuery_PreprocessesSearchTerms() {
        manager = createManagerWithMock();
        SolrQuery q = new SolrQuery();
        manager.configureQuery(q, "test query", "filter", 10, 0);

        String expected = SearchUtilities.preprocessSearchTerm("test query");
        assertEquals(expected, q.getQuery());
    }

    @Test
    public void testConfigureQuery_WildcardNotPreprocessed() {
        manager = createManagerWithMock();
        SolrQuery q = new SolrQuery();
        manager.configureQuery(q, "*:*", "filter", 10, 0);
        assertEquals("*:*", q.getQuery());
    }

    // ========================================================================
    // PERMISSION FILTER TESTS (direct method calls)
    // ========================================================================

    @Test
    public void testBuildPermissionFilter_PublicCore_Anonymous() {
        manager = createManagerWithMock();
        String noUnlisted = "(*:* NOT visibility:UNLISTED)";
        assertEquals(noUnlisted, manager.buildPermissionFilter(null, VisibilityType.PUBLIC, null));
        assertEquals(noUnlisted, manager.buildPermissionFilter(null, VisibilityType.PUBLIC, Permissions.READ));
    }

    @Test
    public void testBuildPermissionFilter_PublicCore_AuthenticatedAdmin() {
        manager = createManagerWithMock();
        assertEquals("owner:\"admin\"",
                manager.buildPermissionFilter("admin", VisibilityType.PUBLIC, Permissions.ADMIN));
    }
    @Test
    public void testBuildPermissionFilter_PublicCore_AuthenticatedWrite() {
        manager = createManagerWithMock();
        String filter = manager.buildPermissionFilter("bob", VisibilityType.PUBLIC, Permissions.WRITE);
        assertEquals("(owner:\"bob\") OR (userEdit:\"bob\")", filter);
    }

    @Test
    public void testBuildPermissionFilter_PrivateCore_Anonymous_MatchesNothing() {
        manager = createManagerWithMock();
        assertEquals("(*:* AND NOT *:*)",
                manager.buildPermissionFilter(null, VisibilityType.PRIVATE, null));
        assertEquals("(*:* AND NOT *:*)",
                manager.buildPermissionFilter(null, VisibilityType.PRIVATE, Permissions.READ));
        assertEquals("(*:* AND NOT *:*)",
                manager.buildPermissionFilter(null, VisibilityType.PRIVATE, Permissions.WRITE));
        assertEquals("(*:* AND NOT *:*)",
                manager.buildPermissionFilter(null, VisibilityType.PRIVATE, Permissions.ADMIN));
    }

    @Test
    public void testBuildPermissionFilter_PrivateCore_AuthenticatedRead() {
        manager = createManagerWithMock();
        String filter = manager.buildPermissionFilter("jane", VisibilityType.PRIVATE, Permissions.READ);
        assertEquals("(owner:\"jane\") OR (userRead:\"jane\") OR (userEdit:\"jane\")", filter);
    }

    @Test
    public void testBuildPermissionFilter_PrivateCore_AuthenticatedWrite() {
        manager = createManagerWithMock();
        String filter = manager.buildPermissionFilter("david", VisibilityType.PRIVATE, Permissions.WRITE);
        assertEquals("(owner:\"david\") OR (userEdit:\"david\")", filter);
    }

    @Test
    public void testBuildPermissionFilter_PrivateCore_AuthenticatedAdmin() {
        manager = createManagerWithMock();
        assertEquals("owner:\"superadmin\"",
                manager.buildPermissionFilter("superadmin", VisibilityType.PRIVATE, Permissions.ADMIN));
    }

    @Test
    public void testBuildPermissionFilter_SpecialCharsInUsername() {
        manager = createManagerWithMock();
        String filter = manager.buildPermissionFilter("user@example.com", VisibilityType.PUBLIC, Permissions.ADMIN);
        assertEquals("owner:\"user@example.com\"", filter);
    }

    // ========================================================================
    // DOCUMENT RESET STATE
    // ========================================================================

    @Test
    public void testSetupIndexDocument_CalledTwice_DocumentIsReset() {
        manager = createManagerWithMock();

        NdexFolder folder1 = createTestFolder("Folder One", "Description One");
        SolrInputDocument doc1 = manager.setupIndexDocument(folder1, VisibilityType.PUBLIC);
        assertEquals("Folder One", doc1.getFieldValue("name"));

        NdexFolder folder2 = createTestFolder("Folder Two", "Description Two");
        SolrInputDocument doc2 = manager.setupIndexDocument(folder2, VisibilityType.PUBLIC);
        assertEquals("Folder Two", doc2.getFieldValue("name"));
        // doc should be fresh, not contain fields from folder1
        assertFalse(doc2.getFieldValue("uuid").equals(doc1.getFieldValue("uuid")));
    }

    // ========================================================================
    // CORE NAME MAPPING
    // ========================================================================

    @Test
    public void testGetCoreNameFromVisibility() {
        assertEquals("private-nfs", NFSIndexManager.getCoreNameFromVisibility(VisibilityType.PRIVATE));
        assertEquals("public-nfs", NFSIndexManager.getCoreNameFromVisibility(VisibilityType.PUBLIC));
        assertEquals("public-nfs", NFSIndexManager.getCoreNameFromVisibility(VisibilityType.UNLISTED));
    }

    // ========================================================================
    // INTEGRATION TESTS (Require local Solr - @Ignore by default)
    // Single manager instance since visibility is now per-operation
    // ========================================================================

    private FolderIndexManager createIntegrationManager() {
        SolrClientWrapper wrapper = new SolrClientWrapperImpl(
                Configuration.getInstance().getSolrObjectFactory());
        return new FolderIndexManager(wrapper);
    }

    @Test @Ignore
    public void testSearchInFolder_WithParentFilter_Integration() throws Exception {
        manager = createIntegrationManager();

        UUID parentId = UUID.randomUUID();
        NdexFolder parent = createTestFolder("Parent Folder", "Parent description");
        parent.setExternalId(parentId);
        manager.createIndex(parent, VisibilityType.PUBLIC, null, null);

        NdexFolder child1 = createTestFolder("Child Folder 1", "First child");
        child1.setParent(parentId);
        manager.createIndex(child1, VisibilityType.PUBLIC, null, null);

        NdexFolder child2 = createTestFolder("Child Folder 2", "Second child");
        child2.setParent(parentId);
        manager.createIndex(child2, VisibilityType.PUBLIC, null, null);

        NdexFolder orphan = createTestFolder("Orphan Folder", "No parent");
        orphan.setParent(UUID.randomUUID());
        manager.createIndex(orphan, VisibilityType.PUBLIC, null, null);

        NdexFolder root = createTestFolder("Root Folder", "No parent at all");
        manager.createIndex(root, VisibilityType.PUBLIC, null, null);

        Thread.sleep(2000);

        SolrDocumentList results = manager.searchInFolder(
                "*:*", "testOwner", 100, 0,
                parentId.toString(), null, VisibilityType.PUBLIC);

        assertNotNull(results);
        assertEquals("Should find exactly 2 children", 2, results.getNumFound());

        Set<String> resultIds = new HashSet<>();
        results.forEach(doc -> resultIds.add((String) doc.getFieldValue("uuid")));

        assertTrue("Should contain child1", resultIds.contains(child1.getExternalId().toString()));
        assertTrue("Should contain child2", resultIds.contains(child2.getExternalId().toString()));
        assertFalse("Should not contain orphan", resultIds.contains(orphan.getExternalId().toString()));
        assertFalse("Should not contain root", resultIds.contains(root.getExternalId().toString()));
        assertFalse("Should not contain parent", resultIds.contains(parentId.toString()));
    }

    @Test @Ignore
    public void testSearchInFolder_NullParent_ReturnsAllFolders_Integration() throws Exception {
        manager = createIntegrationManager();

        NdexFolder folder1 = createTestFolder("Folder 1", "First");
        manager.createIndex(folder1, VisibilityType.PUBLIC, null, null);

        NdexFolder folder2 = createTestFolder("Folder 2", "Second");
        folder2.setParent(UUID.randomUUID());
        manager.createIndex(folder2, VisibilityType.PUBLIC, null, null);

        NdexFolder folder3 = createTestFolder("Folder 3", "Third");
        manager.createIndex(folder3, VisibilityType.PUBLIC, null, null);

        Thread.sleep(1000);

        SolrDocumentList results = manager.searchInFolder(
                "*:*", "testOwner", 100, 0,
                null, null, VisibilityType.PUBLIC);

        assertNotNull(results);
        assertTrue("Should find all folders", results.getNumFound() >= 3);
    }

    @Test @Ignore
    public void testSearchInFolder_RespectsPermissions_Integration() throws Exception {
        manager = createIntegrationManager();

        UUID parentId = UUID.randomUUID();

        NdexFolder publicFolder = createTestFolder("Public Child", "Public");
        publicFolder.setParent(parentId);
        manager.createIndex(publicFolder, VisibilityType.PUBLIC, null, null);

        NdexFolder privateFolder = createTestFolder("Private Child", "Private");
        privateFolder.setParent(parentId);
        privateFolder.setOwner("otherOwner");
        manager.createIndex(privateFolder, VisibilityType.PRIVATE,
                Arrays.asList("otherOwner"), null);

        Thread.sleep(1000);

        // Anonymous user searching public core
        SolrDocumentList publicResults = manager.searchInFolder(
                "*:*", null, 100, 0,
                parentId.toString(), null, VisibilityType.PUBLIC);

        assertTrue("Anonymous should see public folder", publicResults.getNumFound() >= 1);

        // Anonymous user searching private core (should see nothing)
        SolrDocumentList privateResults = manager.searchInFolder(
                "*:*", null, 100, 0,
                parentId.toString(), null, VisibilityType.PRIVATE);

        assertEquals("Anonymous should see no private folders", 0, privateResults.getNumFound());
    }

    @Test @Ignore
    public void testSearchInFolder_WithSearchTerms_Integration() throws Exception {
        manager = createIntegrationManager();

        UUID parentId = UUID.randomUUID();

        NdexFolder cancer1 = createTestFolder("Cancer Research Data", "Cancer study");
        cancer1.setParent(parentId);
        manager.createIndex(cancer1, VisibilityType.PUBLIC, null, null);

        NdexFolder cancer2 = createTestFolder("Lung Cancer Analysis", "Another cancer study");
        cancer2.setParent(parentId);
        manager.createIndex(cancer2, VisibilityType.PUBLIC, null, null);

        NdexFolder diabetes = createTestFolder("Diabetes Study", "Diabetes research");
        diabetes.setParent(parentId);
        manager.createIndex(diabetes, VisibilityType.PUBLIC, null, null);

        Thread.sleep(1000);

        SolrDocumentList results = manager.searchInFolder(
                "cancer", "testOwner", 100, 0,
                parentId.toString(), null, VisibilityType.PUBLIC);

        assertNotNull(results);
        assertEquals("Should find 2 cancer-related folders", 2, results.getNumFound());

        Set<String> resultIds = new HashSet<>();
        results.forEach(doc -> resultIds.add((String) doc.getFieldValue("uuid")));

        assertTrue(resultIds.contains(cancer1.getExternalId().toString()));
        assertTrue(resultIds.contains(cancer2.getExternalId().toString()));
        assertFalse(resultIds.contains(diabetes.getExternalId().toString()));
    }

    @Test @Ignore
    public void testSearchInFolder_Pagination_Integration() throws Exception {
        manager = createIntegrationManager();

        UUID parentId = UUID.randomUUID();

        List<UUID> folderIds = new ArrayList<>();
        for (int i = 0; i < 10; i++) {
            NdexFolder folder = createTestFolder("Folder " + i, "Description " + i);
            folder.setParent(parentId);
            manager.createIndex(folder, VisibilityType.PUBLIC, null, null);
            folderIds.add(folder.getExternalId());
        }

        Thread.sleep(1000);

        SolrDocumentList page1 = manager.searchInFolder(
                "*:*", "testOwner", 5, 0,
                parentId.toString(), null, VisibilityType.PUBLIC);

        assertEquals("First page should have 5 results", 5, page1.size());
        assertEquals("Total should be 10", 10, page1.getNumFound());

        SolrDocumentList page2 = manager.searchInFolder(
                "*:*", "testOwner", 5, 5,
                parentId.toString(), null, VisibilityType.PUBLIC);

        assertEquals("Second page should have 5 results", 5, page2.size());
        assertEquals("Total should still be 10", 10, page2.getNumFound());

        Set<String> page1Ids = new HashSet<>();
        page1.forEach(doc -> page1Ids.add((String) doc.getFieldValue("uuid")));

        Set<String> page2Ids = new HashSet<>();
        page2.forEach(doc -> page2Ids.add((String) doc.getFieldValue("uuid")));

        page1Ids.retainAll(page2Ids);
        assertTrue("Pages should not overlap", page1Ids.isEmpty());
    }

    @Test @Ignore
    public void testSearchInFolder_AdminPermission_Integration() throws Exception {
        manager = createIntegrationManager();

        UUID parentId = UUID.randomUUID();

        NdexFolder myFolder = createTestFolder("My Folder", "I own this");
        myFolder.setOwner("testOwner");
        myFolder.setParent(parentId);
        manager.createIndex(myFolder, VisibilityType.PUBLIC, null, null);

        NdexFolder theirFolder = createTestFolder("Their Folder", "They own this");
        theirFolder.setOwner("otherOwner");
        theirFolder.setParent(parentId);
        manager.createIndex(theirFolder, VisibilityType.PUBLIC, null, null);

        Thread.sleep(1000);

        SolrDocumentList results = manager.searchInFolder(
                "*:*", "testOwner", 100, 0,
                parentId.toString(), Permissions.ADMIN, VisibilityType.PUBLIC);

        assertEquals("Should only see owned folder", 1, results.getNumFound());
        assertEquals(myFolder.getExternalId().toString(),
                results.get(0).getFieldValue("uuid"));
    }

    @Test @Ignore
    public void testSearchInFolder_WildcardSortsByModificationTime_Integration() throws Exception {
        manager = createIntegrationManager();

        UUID parentId = UUID.randomUUID();
        Instant now = Instant.now();

        NdexFolder oldest = createTestFolder("Oldest", "First created");
        oldest.setParent(parentId);
        oldest.setModificationTime(Timestamp.from(now.minusSeconds(3600)));
        manager.createIndex(oldest, VisibilityType.PUBLIC, null, null);
        Thread.sleep(100);

        NdexFolder middle = createTestFolder("Middle", "Second created");
        middle.setParent(parentId);
        middle.setModificationTime(Timestamp.from(now.minusSeconds(1800)));
        manager.createIndex(middle, VisibilityType.PUBLIC, null, null);
        Thread.sleep(100);

        NdexFolder newest = createTestFolder("Newest", "Last created");
        newest.setParent(parentId);
        newest.setModificationTime(Timestamp.from(now));
        manager.createIndex(newest, VisibilityType.PUBLIC, null, null);

        Thread.sleep(1000);

        SolrDocumentList results = manager.searchInFolder(
                "*:*", "testOwner", 100, 0,
                parentId.toString(), null, VisibilityType.PUBLIC);

        assertEquals("Should find all 3 folders", 3, results.getNumFound());
        assertEquals("First should be newest",
                newest.getExternalId().toString(),
                results.get(0).getFieldValue("uuid"));
        assertEquals("Last should be oldest",
                oldest.getExternalId().toString(),
                results.get(2).getFieldValue("uuid"));
    }

    @Test @Ignore
    public void testSearchInFolder_OnlyReturnsFolders_Integration() throws Exception {
        manager = createIntegrationManager();

        UUID parentId = UUID.randomUUID();

        NdexFolder folder = createTestFolder("Test Folder", "A folder");
        folder.setParent(parentId);
        manager.createIndex(folder, VisibilityType.PUBLIC, null, null);

        Thread.sleep(1000);

        SolrDocumentList results = manager.searchInFolder(
                "*:*", "testOwner", 100, 0,
                parentId.toString(), null, VisibilityType.PUBLIC);

        assertNotNull(results);
        assertTrue(results.getNumFound() >= 1);

        for (var doc : results) {
            assertEquals("All results should be folders",
                    "FOLDER", doc.getFieldValue("entityType"));
        }
    }

    @Test @Ignore
    public void testSearchInFolder_EmptyParent_ReturnsNothing_Integration() throws Exception {
        manager = createIntegrationManager();

        UUID emptyParentId = UUID.randomUUID();

        NdexFolder folder1 = createTestFolder("Folder 1", "Different parent");
        folder1.setParent(UUID.randomUUID());
        manager.createIndex(folder1, VisibilityType.PUBLIC, null, null);

        NdexFolder folder2 = createTestFolder("Folder 2", "No parent");
        manager.createIndex(folder2, VisibilityType.PUBLIC, null, null);

        Thread.sleep(1000);

        SolrDocumentList results = manager.searchInFolder(
                "*:*", "testOwner", 100, 0,
                emptyParentId.toString(), null, VisibilityType.PUBLIC);

        assertNotNull(results);
        assertEquals("Empty parent should return no results", 0, results.getNumFound());
    }

    @Test @Ignore
    public void testSearchInFolder_SearchInDescription_Integration() throws Exception {
        manager = createIntegrationManager();

        UUID parentId = UUID.randomUUID();

        NdexFolder folder1 = createTestFolder("Data Folder", "Contains pathway analysis");
        folder1.setParent(parentId);
        manager.createIndex(folder1, VisibilityType.PUBLIC, null, null);

        NdexFolder folder2 = createTestFolder("Research Folder", "Gene expression studies");
        folder2.setParent(parentId);
        manager.createIndex(folder2, VisibilityType.PUBLIC, null, null);

        NdexFolder folder3 = createTestFolder("Analysis Folder", "Pathway enrichment results");
        folder3.setParent(parentId);
        manager.createIndex(folder3, VisibilityType.PUBLIC, null, null);

        Thread.sleep(1000);

        SolrDocumentList results = manager.searchInFolder(
                "pathway", "testOwner", 100, 0,
                parentId.toString(), null, VisibilityType.PUBLIC);

        assertNotNull(results);
        assertEquals("Should find 2 folders with 'pathway' in description",
                2, results.getNumFound());

        Set<String> resultIds = new HashSet<>();
        results.forEach(doc -> resultIds.add((String) doc.getFieldValue("uuid")));

        assertTrue(resultIds.contains(folder1.getExternalId().toString()));
        assertTrue(resultIds.contains(folder3.getExternalId().toString()));
        assertFalse(resultIds.contains(folder2.getExternalId().toString()));
    }

    @Test @Ignore
    public void testSearchInFolder_MultiLevelHierarchy_Integration() throws Exception {
        manager = createIntegrationManager();

        UUID rootId = UUID.randomUUID();
        NdexFolder root = createTestFolder("Root", "Root folder");
        root.setExternalId(rootId);
        manager.createIndex(root, VisibilityType.PUBLIC, null, null);

        UUID parentId = UUID.randomUUID();
        NdexFolder parent = createTestFolder("Parent", "Parent folder");
        parent.setExternalId(parentId);
        parent.setParent(rootId);
        manager.createIndex(parent, VisibilityType.PUBLIC, null, null);

        NdexFolder child1 = createTestFolder("Child 1", "First child");
        child1.setParent(parentId);
        manager.createIndex(child1, VisibilityType.PUBLIC, null, null);

        NdexFolder child2 = createTestFolder("Child 2", "Second child");
        child2.setParent(parentId);
        manager.createIndex(child2, VisibilityType.PUBLIC, null, null);

        Thread.sleep(1000);

        SolrDocumentList rootResults = manager.searchInFolder(
                "*:*", "testOwner", 100, 0,
                rootId.toString(), null, VisibilityType.PUBLIC);

        assertEquals("Root should contain only parent folder", 1, rootResults.getNumFound());
        assertEquals(parentId.toString(), rootResults.get(0).getFieldValue("uuid"));

        SolrDocumentList parentResults = manager.searchInFolder(
                "*:*", "testOwner", 100, 0,
                parentId.toString(), null, VisibilityType.PUBLIC);

        assertEquals("Parent should contain 2 children", 2, parentResults.getNumFound());
    }

    @Test @Ignore
    public void testSearchInFolder_PrivateFolderWithPermissions_Integration() throws Exception {
        manager = createIntegrationManager();

        UUID parentId = UUID.randomUUID();

        // Private folder where "alice" has read access
        NdexFolder privateFolder = createTestFolder("Secret Folder", "Private data");
        privateFolder.setParent(parentId);
        privateFolder.setOwner("bob");
        manager.createIndex(privateFolder, VisibilityType.PRIVATE,
                Arrays.asList("alice", "bob"), Arrays.asList("bob"));

        // Another private folder alice can't see
        NdexFolder hiddenFolder = createTestFolder("Hidden Folder", "No access for alice");
        hiddenFolder.setParent(parentId);
        hiddenFolder.setOwner("charlie");
        manager.createIndex(hiddenFolder, VisibilityType.PRIVATE,
                Arrays.asList("charlie"), Arrays.asList("charlie"));

        Thread.sleep(1000);

        // Alice should see the first folder (she's in userRead)
        SolrDocumentList aliceResults = manager.searchInFolder(
                "*:*", "alice", 100, 0,
                parentId.toString(), null, VisibilityType.PRIVATE);

        assertEquals("Alice should see 1 private folder", 1, aliceResults.getNumFound());
        assertEquals(privateFolder.getExternalId().toString(),
                aliceResults.get(0).getFieldValue("uuid"));

        // Bob should see his folder
        SolrDocumentList bobResults = manager.searchInFolder(
                "*:*", "bob", 100, 0,
                parentId.toString(), null, VisibilityType.PRIVATE);

        assertEquals("Bob should see 1 private folder", 1, bobResults.getNumFound());

        // Charlie should see his folder
        SolrDocumentList charlieResults = manager.searchInFolder(
                "*:*", "charlie", 100, 0,
                parentId.toString(), null, VisibilityType.PRIVATE);

        assertEquals("Charlie should see 1 private folder", 1, charlieResults.getNumFound());
    }


    // ========================================================================
    // HELPERS
    // ========================================================================

    private NdexFolder createTestFolder(String name, String description) {
        NdexFolder folder = new NdexFolder();
        folder.setExternalId(UUID.randomUUID());
        folder.setName(name);
        folder.setDescription(description);
        folder.setOwner("testOwner");
        folder.setCreationTime(Timestamp.from(Instant.now()));
        folder.setModificationTime(Timestamp.from(Instant.now()));
        return folder;
    }
}