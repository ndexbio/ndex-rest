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
import org.ndexbio.common.models.dao.SearchScope;
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

        manager.prepareIndexDocument(folder, VisibilityType.PUBLIC);

        SolrInputDocument doc = manager.doc;
        assertEquals("PUBLIC", doc.getFieldValue("visibility"));
        // PUBLIC items should NOT have userRead/userEdit fields
        assertNull(doc.getFieldValue("userRead"));
        assertNull(doc.getFieldValue("userEdit"));
    }

    @Test
    public void testPrepareIndexDocument_PrivateVisibility_WritesNoAccessList() {
        manager = createManagerWithMock();
        NdexFolder folder = createTestFolder("Private Folder", "Private");

        manager.prepareIndexDocument(folder, VisibilityType.PRIVATE);

        SolrInputDocument doc = manager.doc;
        assertEquals("PRIVATE", doc.getFieldValue("visibility"));

        // The index holds structure only. Permissions used to be copied onto the document at index time
        // and went stale on every share, move or revoke; they are resolved per query from the database
        // instead, so a document must carry no access list at all.
        assertNull(doc.getFieldValue("userRead"));
        assertNull(doc.getFieldValue("userEdit"));
    }

    @Test
    public void testPrepareIndexDocument_PrivateVisibility_NullPermissions() {
        manager = createManagerWithMock();
        NdexFolder folder = createTestFolder("Private Folder", "Private");

        manager.prepareIndexDocument(folder, VisibilityType.PRIVATE);

        SolrInputDocument doc = manager.doc;
        assertEquals("PRIVATE", doc.getFieldValue("visibility"));
        assertNull(doc.getFieldValue("userRead"));
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

        manager.createIndex(folder, VisibilityType.PUBLIC);

        verify(mockWrapper);
        assertEquals(NFSIndexManager.nfsCoreName, coreCapture.getValue());
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

        manager.createIndex(folder, VisibilityType.PRIVATE);

        verify(mockWrapper);
        assertEquals(NFSIndexManager.nfsCoreName, coreCapture.getValue());

        SolrInputDocument committed = docsCapture.getValue().iterator().next();
        assertEquals("PRIVATE", committed.getFieldValue("visibility"));
        assertNull(committed.getFieldValue("userRead"));
    }

    // ========================================================================
    // DELETE - VERIFIES CORRECT CORE
    // ========================================================================

    @Test
    public void testDelete_RemovesFromTheSingleCore() throws Exception {
        mockWrapper = createMock(SolrClientWrapper.class);
        Capture<String> coreCapture = Capture.newInstance();
        Capture<String> uuidCapture = Capture.newInstance();

        mockWrapper.delete(capture(coreCapture), capture(uuidCapture), eq(false));
        expectLastCall().once();
        mockWrapper.close();
        expectLastCall().anyTimes();
        replay(mockWrapper);

        manager = new FolderIndexManager(mockWrapper);
        manager.delete("test-uuid-123");

        verify(mockWrapper);
        assertEquals(NFSIndexManager.nfsCoreName, coreCapture.getValue());
        assertEquals("test-uuid-123", uuidCapture.getValue());
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
                "*:*", null, VisibilityType.PUBLIC, 10, 0, null, null, SearchScope.EMPTY);

        verify(mockWrapper);
        assertEquals(NFSIndexManager.nfsCoreName, coreCapture.getValue());

        SolrQuery captured = queryCapture.getValue();
        assertEquals("*:*", captured.getQuery());
        assertEquals("edismax", captured.get("defType"));

        // Anonymous public: filter should be (*:*)
        String[] fq = captured.getFilterQueries();
        assertNotNull(fq);
        assertTrue(fq[0].contains("(visibility:PUBLIC)"));
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
        expect(mockWrapper.query(eq(NFSIndexManager.nfsCoreName), capture(queryCapture)))
                .andReturn(mockResponse);
        mockWrapper.close();
        expectLastCall().anyTimes();
        replay(mockWrapper);

        manager = new FolderIndexManager(mockWrapper);
        SolrDocumentList results = manager.search(
                "*:*", null, VisibilityType.PRIVATE, 10, 0, null, null, SearchScope.EMPTY);

        String[] fq = queryCapture.getValue().getFilterQueries();
        assertTrue("Anonymous private filter should match nothing",
                fq[0].contains("(visibility:PUBLIC)"));
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
        manager.search("test", "user1", VisibilityType.PUBLIC, 10, 0, "specificOwner", null, SearchScope.EMPTY);

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
        manager.search("test", null, VisibilityType.PUBLIC, 25, 50, null, null, SearchScope.EMPTY);

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
        expect(mockWrapper.query(eq(NFSIndexManager.nfsCoreName), capture(queryCapture)))
                .andReturn(mockResponse);
        mockWrapper.close();
        expectLastCall().anyTimes();
        replay(mockWrapper);

        manager = new FolderIndexManager(mockWrapper);
        manager.search("*:*", "charlie", VisibilityType.PRIVATE, 10, 0, null, Permissions.READ, SearchScope.EMPTY);

        String[] fq = queryCapture.getValue().getFilterQueries();
        assertTrue(fq[0].contains("owner:\"charlie\""));
        // No permission state is read from the index: with no folder scope resolved, ownership is the
        // only reason a private document can match.
        assertFalse(fq[0].contains("userRead"));
        assertFalse(fq[0].contains("userEdit"));
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
        expect(mockWrapper.query(eq(NFSIndexManager.nfsCoreName), capture(queryCapture)))
                .andReturn(mockResponse);
        mockWrapper.close();
        expectLastCall().anyTimes();
        replay(mockWrapper);

        manager = new FolderIndexManager(mockWrapper);
        manager.search("*:*", "david", VisibilityType.PRIVATE, 10, 0, null, Permissions.WRITE, SearchScope.EMPTY);

        String[] fq = queryCapture.getValue().getFilterQueries();
        assertTrue(fq[0].contains("owner:\"david\""));
        assertFalse(fq[0].contains("userEdit"));
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
        expect(mockWrapper.query(eq(NFSIndexManager.nfsCoreName), capture(queryCapture)))
                .andReturn(mockResponse);
        mockWrapper.close();
        expectLastCall().anyTimes();
        replay(mockWrapper);

        manager = new FolderIndexManager(mockWrapper);
        manager.search("*:*", "admin", VisibilityType.PRIVATE, 10, 0, null, Permissions.ADMIN, SearchScope.EMPTY);

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
        expect(mockWrapper.query(eq(NFSIndexManager.nfsCoreName), capture(queryCapture)))
                .andReturn(mockResponse);
        mockWrapper.close();
        expectLastCall().anyTimes();
        replay(mockWrapper);

        manager = new FolderIndexManager(mockWrapper);
        manager.search("*:*", "bob", VisibilityType.PUBLIC, 10, 0, null, Permissions.WRITE, SearchScope.EMPTY);

        String[] fq = queryCapture.getValue().getFilterQueries();
        // userEdit was only ever written onto PRIVATE/UNLISTED documents, so on the public core it
        // served solely to surface UNLISTED files to non-owner editors.
        assertTrue(fq[0].contains("owner:\"bob\""));
        assertFalse(fq[0].contains("userEdit"));
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
                null, null, "FOLDER", true, SearchScope.EMPTY);

        String[] fq = queryCapture.getValue().getFilterQueries();
        assertTrue(fq[0].contains("entityType:\"FOLDER\""));
    }

    // ========================================================================
    // SEARCH IN FOLDER
    // ========================================================================






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


    // ========================================================================
    // BOOST FUNCTION - NON-NETWORK MANAGERS ARE NOT PENALIZED (issue #116)
    // ========================================================================

    @Test
    public void testGetBoostFunction_DefaultsToNull() {
        manager = createManagerWithMock();
        // Folders (and other non-network types) inherit the base no-op boost.
        assertNull(manager.getBoostFunction());
    }

    @Test
    public void testConfigureQuery_NoBoostForFolders() {
        manager = createManagerWithMock();
        SolrQuery q = new SolrQuery();
        manager.configureQuery(q, "test", "filter", 10, 0);
        assertNull(q.get("boost"));
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