package org.ndexbio.common.solr;

import org.apache.solr.client.solrj.SolrQuery;
import org.apache.solr.client.solrj.SolrServerException;
import org.apache.solr.client.solrj.response.QueryResponse;
import org.apache.solr.common.SolrDocumentList;
import org.apache.solr.common.SolrInputDocument;
import org.easymock.Capture;
import org.junit.After;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Ignore;
import org.junit.Test;
import org.ndexbio.model.object.FileType;
import org.ndexbio.model.object.NdexFolder;
import org.ndexbio.model.object.NdexShortcut;
import org.ndexbio.model.object.Permissions;
import org.ndexbio.model.object.network.VisibilityType;
import org.ndexbio.model.tools.SearchUtilities;
import org.ndexbio.rest.Configuration;

import java.lang.reflect.Field;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.*;

import static org.easymock.EasyMock.*;
import static org.junit.Assert.*;

/**
 * Unit tests for ShortcutIndexManager using mocked SolrClientWrapper.
 * Integration tests (requiring live Solr) are kept but @Ignore'd.
 */
public class TestShortcutIndexManager {

    private static Configuration savedInstance;
    private ShortcutIndexManager manager;
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

    private ShortcutIndexManager createManagerWithMock() {
        mockWrapper = createNiceMock(SolrClientWrapper.class);
        replay(mockWrapper);
        return new ShortcutIndexManager(mockWrapper);
    }

    // ========================================================================
    // SETUP INDEX DOCUMENT TESTS - COMPLETE SHORTCUTS
    // ========================================================================

    @Test
    public void testSetupIndexDocument_AllFieldsSet() {
        manager = createManagerWithMock();

        UUID shortcutId = UUID.randomUUID();
        UUID parentId = UUID.randomUUID();
        UUID targetId = UUID.randomUUID();
        Timestamp creationTime = Timestamp.from(Instant.now().minusSeconds(3600));
        Timestamp modificationTime = Timestamp.from(Instant.now());

        NdexShortcut shortcut = new NdexShortcut();
        shortcut.setExternalId(shortcutId);
        shortcut.setName("Test Shortcut");
        shortcut.setOwner("testOwner");
        shortcut.setParent(parentId);
        shortcut.setTarget(targetId);
        shortcut.setTargetType(FileType.NETWORK);
        shortcut.setCreationTime(creationTime);
        shortcut.setModificationTime(modificationTime);

        SolrInputDocument doc = manager.setupIndexDocument(shortcut, VisibilityType.PUBLIC);

        assertNotNull(doc);
        assertEquals(shortcutId.toString(), doc.getFieldValue("uuid"));
        assertEquals("SHORTCUT", doc.getFieldValue("entityType"));
        assertEquals("Test Shortcut", doc.getFieldValue("name"));
        assertEquals("testOwner", doc.getFieldValue("owner"));
        assertEquals(parentId.toString(), doc.getFieldValue("parentUuid"));
        assertEquals(targetId.toString(), doc.getFieldValue("targetUuid"));
        assertEquals("NETWORK", doc.getFieldValue("targetType"));
        assertEquals(creationTime, doc.getFieldValue("creationTime"));
        assertEquals(modificationTime, doc.getFieldValue("modificationTime"));
    }

    @Test
    public void testSetupIndexDocument_MinimalShortcut_OnlyRequiredFields() {
        manager = createManagerWithMock();

        UUID shortcutId = UUID.randomUUID();
        Timestamp now = Timestamp.from(Instant.now());

        NdexShortcut shortcut = new NdexShortcut();
        shortcut.setExternalId(shortcutId);
        shortcut.setCreationTime(now);
        shortcut.setModificationTime(now);

        SolrInputDocument doc = manager.setupIndexDocument(shortcut, VisibilityType.PUBLIC);

        assertNotNull(doc);
        assertEquals(shortcutId.toString(), doc.getFieldValue("uuid"));
        assertEquals("SHORTCUT", doc.getFieldValue("entityType"));
        assertNull(doc.getFieldValue("name"));
        assertNull(doc.getFieldValue("owner"));
        assertNull(doc.getFieldValue("parentUuid"));
        assertNull(doc.getFieldValue("targetUuid"));
    }

    @Test
    public void testSetupIndexDocument_NoParent_ParentFieldNull() {
        manager = createManagerWithMock();
        NdexShortcut shortcut = createTestShortcut("Root Shortcut");
        shortcut.setParent(null);

        SolrInputDocument doc = manager.setupIndexDocument(shortcut, VisibilityType.PUBLIC);

        assertNull(doc.getFieldValue("parentUuid"));
    }

    // ========================================================================
    // NAME FIELD VALIDATION
    // ========================================================================

    @Test
    public void testSetupIndexDocument_NullName_NotIndexed() {
        manager = createManagerWithMock();
        NdexShortcut shortcut = createTestShortcut(null);
        SolrInputDocument doc = manager.setupIndexDocument(shortcut, VisibilityType.PUBLIC);
        assertNull(doc.getFieldValue("name"));
    }

    @Test
    public void testSetupIndexDocument_EmptyName_NotIndexed() {
        manager = createManagerWithMock();
        NdexShortcut shortcut = createTestShortcut("");
        SolrInputDocument doc = manager.setupIndexDocument(shortcut, VisibilityType.PUBLIC);
        assertNull(doc.getFieldValue("name"));
    }

    @Test
    public void testSetupIndexDocument_WhitespaceOnlyName_NotIndexed() {
        manager = createManagerWithMock();
        NdexShortcut shortcut = createTestShortcut("   ");
        SolrInputDocument doc = manager.setupIndexDocument(shortcut, VisibilityType.PUBLIC);
        assertNull(doc.getFieldValue("name"));
    }

    @Test
    public void testSetupIndexDocument_ValidName_IsIndexed() {
        manager = createManagerWithMock();
        NdexShortcut shortcut = createTestShortcut("My Shortcut");
        SolrInputDocument doc = manager.setupIndexDocument(shortcut, VisibilityType.PUBLIC);
        assertEquals("My Shortcut", doc.getFieldValue("name"));
    }

    @Test
    public void testSetupIndexDocument_NameWithSpecialCharacters() {
        manager = createManagerWithMock();
        String specialName = "Shortcut-Name_2024 (Link)";
        NdexShortcut shortcut = createTestShortcut(specialName);
        SolrInputDocument doc = manager.setupIndexDocument(shortcut, VisibilityType.PUBLIC);
        assertEquals(specialName, doc.getFieldValue("name"));
    }

    @Test
    public void testSetupIndexDocument_LongName_IsIndexed() {
        manager = createManagerWithMock();
        String longName = "This is a very long shortcut name with many characters";
        NdexShortcut shortcut = createTestShortcut(longName);
        SolrInputDocument doc = manager.setupIndexDocument(shortcut, VisibilityType.PUBLIC);
        assertEquals(longName, doc.getFieldValue("name"));
    }

    // ========================================================================
    // OWNER FIELD VALIDATION
    // ========================================================================

    @Test
    public void testSetupIndexDocument_NullOwner_NotIndexed() {
        manager = createManagerWithMock();
        NdexShortcut shortcut = createTestShortcut("Name");
        shortcut.setOwner(null);
        SolrInputDocument doc = manager.setupIndexDocument(shortcut, VisibilityType.PUBLIC);
        assertNull(doc.getFieldValue("owner"));
    }

    @Test
    public void testSetupIndexDocument_EmptyOwner_NotIndexed() {
        manager = createManagerWithMock();
        NdexShortcut shortcut = createTestShortcut("Name");
        shortcut.setOwner("");
        SolrInputDocument doc = manager.setupIndexDocument(shortcut, VisibilityType.PUBLIC);
        assertNull(doc.getFieldValue("owner"));
    }

    @Test
    public void testSetupIndexDocument_BlankOwner_NotIndexed() {
        manager = createManagerWithMock();
        NdexShortcut shortcut = createTestShortcut("Name");
        shortcut.setOwner("   ");
        SolrInputDocument doc = manager.setupIndexDocument(shortcut, VisibilityType.PUBLIC);
        assertNull(doc.getFieldValue("owner"));
    }

    @Test
    public void testSetupIndexDocument_OwnerWithLeadingTrailingSpaces_IsIndexed() {
        manager = createManagerWithMock();
        NdexShortcut shortcut = createTestShortcut("Name");
        shortcut.setOwner("  john_doe  ");
        SolrInputDocument doc = manager.setupIndexDocument(shortcut, VisibilityType.PUBLIC);
        assertEquals("  john_doe  ", doc.getFieldValue("owner"));
    }

    // ========================================================================
    // TARGET AND DANGLING TESTS
    // ========================================================================

    @Test
    public void testSetupIndexDocument_WithTarget_NotDangling() {
        manager = createManagerWithMock();

        UUID targetId = UUID.randomUUID();
        NdexShortcut shortcut = createTestShortcut("Linked Shortcut");
        shortcut.setTarget(targetId);
        shortcut.setTargetType(FileType.NETWORK);

        SolrInputDocument doc = manager.setupIndexDocument(shortcut, VisibilityType.PUBLIC);

        assertEquals(targetId.toString(), doc.getFieldValue("targetUuid"));
        assertEquals("NETWORK", doc.getFieldValue("targetType"));
    }

    @Test
    public void testSetupIndexDocument_WithoutTarget_IsDangling() {
        manager = createManagerWithMock();

        NdexShortcut shortcut = createTestShortcut("Dangling Shortcut");
        shortcut.setTarget(null);

        SolrInputDocument doc = manager.setupIndexDocument(shortcut, VisibilityType.PUBLIC);

        assertNull(doc.getFieldValue("targetUuid"));
    }

    @Test
    public void testSetupIndexDocument_TargetTypeNetwork() {
        manager = createManagerWithMock();
        NdexShortcut shortcut = createTestShortcut("Network Link");
        shortcut.setTarget(UUID.randomUUID());
        shortcut.setTargetType(FileType.NETWORK);
        SolrInputDocument doc = manager.setupIndexDocument(shortcut, VisibilityType.PUBLIC);
        assertEquals("NETWORK", doc.getFieldValue("targetType"));
    }

    @Test
    public void testSetupIndexDocument_TargetTypeFolder() {
        manager = createManagerWithMock();
        NdexShortcut shortcut = createTestShortcut("Folder Link");
        shortcut.setTarget(UUID.randomUUID());
        shortcut.setTargetType(FileType.FOLDER);
        SolrInputDocument doc = manager.setupIndexDocument(shortcut, VisibilityType.PUBLIC);
        assertEquals("FOLDER", doc.getFieldValue("targetType"));
    }

    @Test
    public void testSetupIndexDocument_NullTargetType() {
        manager = createManagerWithMock();
        NdexShortcut shortcut = createTestShortcut("Unknown Target");
        shortcut.setTarget(UUID.randomUUID());
        shortcut.setTargetType(null);
        SolrInputDocument doc = manager.setupIndexDocument(shortcut, VisibilityType.PUBLIC);
        assertNull(doc.getFieldValue("targetType"));
    }

    // ========================================================================
    // PREPARE INDEX DOCUMENT - VISIBILITY + PERMISSIONS
    // ========================================================================

    @Test
    public void testPrepareIndexDocument_PublicVisibility_NoPermissionFields() {
        manager = createManagerWithMock();
        NdexShortcut shortcut = createTestShortcut("Public Shortcut");

        List<String> readers = Arrays.asList("reader1");
        List<String> editors = Arrays.asList("editor1");

        manager.prepareIndexDocument(shortcut, VisibilityType.PUBLIC, readers, editors);

        SolrInputDocument doc = manager.doc;
        assertEquals("PUBLIC", doc.getFieldValue("visibility"));
        assertNull(doc.getFieldValue("userRead"));
        assertNull(doc.getFieldValue("userEdit"));
    }

    @Test
    public void testPrepareIndexDocument_PrivateVisibility_HasPermissionFields() {
        manager = createManagerWithMock();
        NdexShortcut shortcut = createTestShortcut("Private Shortcut");

        List<String> readers = Arrays.asList("reader1", "reader2");
        List<String> editors = Arrays.asList("editor1");

        manager.prepareIndexDocument(shortcut, VisibilityType.PRIVATE, readers, editors);

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
    public void testPrepareIndexDocument_PrivateVisibility_BlankValuesSkipped() {
        manager = createManagerWithMock();
        NdexShortcut shortcut = createTestShortcut("Private Shortcut");

        List<String> readers = Arrays.asList("reader1", "", "  ", null, "reader2");

        manager.prepareIndexDocument(shortcut, VisibilityType.PRIVATE, readers, null);

        SolrInputDocument doc = manager.doc;
        Collection<Object> readValues = doc.getFieldValues("userRead");
        assertNotNull(readValues);
        assertEquals(2, readValues.size());
        assertTrue(readValues.contains("reader1"));
        assertTrue(readValues.contains("reader2"));
        assertNull(doc.getFieldValue("userEdit"));
    }

    // ========================================================================
    // CREATE INDEX - VERIFIES COMMIT TO CORRECT CORE
    // ========================================================================

    @Test
    public void testCreateIndex_PublicShortcut_CommitsToPublicCore() throws Exception {
        mockWrapper = createMock(SolrClientWrapper.class);
        Capture<String> coreCapture = Capture.newInstance();

        mockWrapper.commit(capture(coreCapture), anyObject(Collection.class));
        expectLastCall().once();
        mockWrapper.close();
        expectLastCall().anyTimes();
        replay(mockWrapper);

        manager = new ShortcutIndexManager(mockWrapper);
        NdexShortcut shortcut = createTestShortcut("Test");

        // Verify document contents via prepareIndexDocument
        manager.prepareIndexDocument(shortcut, VisibilityType.PUBLIC, null, null);
        SolrInputDocument preparedDoc = manager.doc;
        assertEquals("SHORTCUT", preparedDoc.getFieldValue("entityType"));
        assertEquals("Test", preparedDoc.getFieldValue("name"));

        // Test commit routing
        reset(mockWrapper);
        mockWrapper.commit(capture(coreCapture), anyObject(Collection.class));
        expectLastCall().once();
        mockWrapper.close();
        expectLastCall().anyTimes();
        replay(mockWrapper);

        manager = new ShortcutIndexManager(mockWrapper);
        manager.createIndex(shortcut, VisibilityType.PUBLIC, null, null);

        verify(mockWrapper);
        assertEquals("public-nfs", coreCapture.getValue());
    }

    @Test
    public void testCreateIndex_PrivateShortcut_CommitsToPrivateCore() throws Exception {
        mockWrapper = createMock(SolrClientWrapper.class);
        Capture<String> coreCapture = Capture.newInstance();

        mockWrapper.commit(capture(coreCapture), anyObject(Collection.class));
        expectLastCall().once();
        mockWrapper.close();
        expectLastCall().anyTimes();
        replay(mockWrapper);

        manager = new ShortcutIndexManager(mockWrapper);
        NdexShortcut shortcut = createTestShortcut("Private");
        List<String> readers = Arrays.asList("user1");

        // Verify document
        manager.prepareIndexDocument(shortcut, VisibilityType.PRIVATE, readers, null);
        assertEquals("PRIVATE", manager.doc.getFieldValue("visibility"));
        assertNotNull(manager.doc.getFieldValues("userRead"));

        // Test commit routing
        reset(mockWrapper);
        mockWrapper.commit(capture(coreCapture), anyObject(Collection.class));
        expectLastCall().once();
        mockWrapper.close();
        expectLastCall().anyTimes();
        replay(mockWrapper);

        manager = new ShortcutIndexManager(mockWrapper);
        manager.createIndex(shortcut, VisibilityType.PRIVATE, readers, null);

        verify(mockWrapper);
        assertEquals("private-nfs", coreCapture.getValue());
    }

    // ========================================================================
    // DELETE
    // ========================================================================

    @Test
    public void testDelete_PublicShortcut_DeletesFromPublicCore() throws Exception {
        mockWrapper = createMock(SolrClientWrapper.class);
        Capture<String> coreCapture = Capture.newInstance();
        Capture<String> uuidCapture = Capture.newInstance();

        mockWrapper.delete(capture(coreCapture), capture(uuidCapture), eq(false));
        expectLastCall().once();
        mockWrapper.close();
        expectLastCall().anyTimes();
        replay(mockWrapper);

        manager = new ShortcutIndexManager(mockWrapper);
        manager.delete("shortcut-uuid-123", VisibilityType.PUBLIC);

        verify(mockWrapper);
        assertEquals("public-nfs", coreCapture.getValue());
        assertEquals("shortcut-uuid-123", uuidCapture.getValue());
    }

    @Test
    public void testDelete_PrivateShortcut_DeletesFromPrivateCore() throws Exception {
        mockWrapper = createMock(SolrClientWrapper.class);
        Capture<String> coreCapture = Capture.newInstance();

        mockWrapper.delete(capture(coreCapture), anyString(), eq(false));
        expectLastCall().once();
        mockWrapper.close();
        expectLastCall().anyTimes();
        replay(mockWrapper);

        manager = new ShortcutIndexManager(mockWrapper);
        manager.delete("shortcut-uuid-456", VisibilityType.PRIVATE);

        verify(mockWrapper);
        assertEquals("private-nfs", coreCapture.getValue());
    }

    // ========================================================================
    // SEARCH - VERIFIES QUERY SENT TO SOLR
    // ========================================================================

    @Test
    public void testSearch_PublicCore_AnonymousUser() throws Exception {
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

        manager = new ShortcutIndexManager(mockWrapper);
        manager.search("*:*", null, VisibilityType.PUBLIC, 10, 0, null, null);

        verify(mockWrapper);
        assertEquals("public-nfs", coreCapture.getValue());

        SolrQuery captured = queryCapture.getValue();
        assertEquals("*:*", captured.getQuery());
        assertEquals("edismax", captured.get("defType"));
        assertEquals("uuid^20 name^10 owner^2", captured.get("qf"));

        String[] fq = captured.getFilterQueries();
        assertNotNull(fq);
        assertTrue(fq[0].contains("(*:* NOT visibility:UNLISTED)"));
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

        manager = new ShortcutIndexManager(mockWrapper);
        manager.search("*:*", null, VisibilityType.PRIVATE, 10, 0, null, null);

        String[] fq = queryCapture.getValue().getFilterQueries();
        assertTrue(fq[0].contains("(*:* AND NOT *:*)"));
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

        manager = new ShortcutIndexManager(mockWrapper);
        manager.search("test", "user1", VisibilityType.PUBLIC, 10, 0, "specificOwner", null);

        String[] fq = queryCapture.getValue().getFilterQueries();
        assertTrue(fq[0].contains("owner:\"specificOwner\""));
    }

    @Test
    public void testSearch_PrivateCore_WritePermission() throws Exception {
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

        manager = new ShortcutIndexManager(mockWrapper);
        manager.search("*:*", "david", VisibilityType.PRIVATE, 10, 0, null, Permissions.WRITE);

        String[] fq = queryCapture.getValue().getFilterQueries();
        assertTrue(fq[0].contains("owner:\"david\""));
        assertTrue(fq[0].contains("userEdit:\"david\""));
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

        manager = new ShortcutIndexManager(mockWrapper);
        manager.searchByType("test", "user", VisibilityType.PUBLIC, 10, 0,
                null, null, "SHORTCUT", true);

        String[] fq = queryCapture.getValue().getFilterQueries();
        assertTrue(fq[0].contains("entityType:\"SHORTCUT\""));
    }

    // ========================================================================
    // QUERY FIELDS
    // ========================================================================

    @Test
    public void testGetQueryFields_ReturnsExpectedString() {
        manager = createManagerWithMock();
        assertEquals("uuid^20 name^10 owner^2", manager.getQueryFields());
    }

    @Test
    public void testGetQueryFields_DoesNotContainNetworkFields() {
        manager = createManagerWithMock();
        String qf = manager.getQueryFields();
        assertFalse(qf.contains("description"));
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
        assertEquals("uuid^20 name^10 owner^2", q.get("qf"));
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
    public void testConfigureQuery_SetsPagination() {
        manager = createManagerWithMock();
        SolrQuery q = new SolrQuery();
        manager.configureQuery(q, "test", "filter", 25, 50);
        assertEquals(Integer.valueOf(50), q.getStart());
        assertEquals(Integer.valueOf(25), q.getRows());
    }

    @Test
    public void testConfigureQuery_NegativeOffset_NotSet() {
        manager = createManagerWithMock();
        SolrQuery q = new SolrQuery();
        manager.configureQuery(q, "test", "filter", 10, -1);
        assertNull(q.getStart());
    }

    // ========================================================================
    // PERMISSION FILTER TESTS (direct method calls)
    // ========================================================================

    @Test
    public void testBuildPermissionFilter_PublicCore_Anonymous() {
        manager = createManagerWithMock();
        assertEquals("(*:* NOT visibility:UNLISTED)", manager.buildPermissionFilter(null, VisibilityType.PUBLIC, null));
        assertEquals("(*:* NOT visibility:UNLISTED)", manager.buildPermissionFilter(null, VisibilityType.PUBLIC, Permissions.READ));
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

    // ========================================================================
    // PREPROCESS SEARCH TERMS
    // ========================================================================

    @Test
    public void testPreprocessSearchTerms_Wildcard_NotModified() {
        manager = createManagerWithMock();
        assertEquals("*:*", manager.preprocessSearchTerms("*:*"));
    }

    @Test
    public void testPreprocessSearchTerms_RegularQuery_DelegatesToSearchUtilities() {
        manager = createManagerWithMock();
        String input = "test query";
        assertEquals(SearchUtilities.preprocessSearchTerm(input),
                manager.preprocessSearchTerms(input));
    }

    @Test
    public void testPreprocessSearchTerms_ConsistentBehavior() {
        manager = createManagerWithMock();
        String input = "shortcut link";
        assertEquals(manager.preprocessSearchTerms(input),
                manager.preprocessSearchTerms(input));
    }

    // ========================================================================
    // DOCUMENT RESET STATE
    // ========================================================================

    @Test
    public void testSetupIndexDocument_CalledTwice_DocumentIsReset() {
        manager = createManagerWithMock();

        NdexShortcut s1 = createTestShortcut("Shortcut One");
        SolrInputDocument doc1 = manager.setupIndexDocument(s1, VisibilityType.PUBLIC);
        assertEquals("Shortcut One", doc1.getFieldValue("name"));

        NdexShortcut s2 = createTestShortcut("Shortcut Two");
        SolrInputDocument doc2 = manager.setupIndexDocument(s2, VisibilityType.PUBLIC);
        assertEquals("Shortcut Two", doc2.getFieldValue("name"));
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
    // ========================================================================

    private ShortcutIndexManager createIntegrationManager() {
        SolrClientWrapper wrapper = new SolrClientWrapperImpl(
                Configuration.getInstance().getSolrObjectFactory());
        return new ShortcutIndexManager(wrapper);
    }


    @Test @Ignore
    public void testSearchShortcuts_ByName_Integration() throws Exception {
        manager = createIntegrationManager();

        NdexShortcut cancerShortcut = createTestShortcut("Cancer Network Link");
        cancerShortcut.setTarget(UUID.randomUUID());
        manager.createIndex(cancerShortcut, VisibilityType.PUBLIC, null, null);

        NdexShortcut diabetesShortcut = createTestShortcut("Diabetes Pathway Link");
        diabetesShortcut.setTarget(UUID.randomUUID());
        manager.createIndex(diabetesShortcut, VisibilityType.PUBLIC, null, null);

        Thread.sleep(2000);

        var results = manager.searchByType("cancer", "testOwner", VisibilityType.PUBLIC,
                100, 0, null, null, FileType.SHORTCUT.toString(), true);

        assertNotNull(results);
        assertEquals(1, results.getNumFound());
    }

    @Test @Ignore
    public void testSearchShortcuts_Pagination_Integration() throws Exception {
        manager = createIntegrationManager();

        for (int i = 0; i < 10; i++) {
            NdexShortcut shortcut = createTestShortcut("Shortcut " + i);
            shortcut.setTarget(UUID.randomUUID());
            manager.createIndex(shortcut, VisibilityType.PUBLIC, null, null);
        }

        Thread.sleep(2000);

        var page1 = manager.searchByType("*:*", "testOwner", VisibilityType.PUBLIC,
                5, 0, null, null, FileType.SHORTCUT.toString(), true);
        assertEquals(5, page1.size());
        assertEquals(10, page1.getNumFound());

        var page2 = manager.searchByType("*:*", "testOwner", VisibilityType.PUBLIC,
                5, 5, null, null, FileType.SHORTCUT.toString(), true);
        assertEquals(5, page2.size());
    }

    @Test @Ignore
    public void testSearchShortcuts_OnlyReturnsShortcuts_Integration() throws Exception {
        manager = createIntegrationManager();
        FolderIndexManager folderMgr = new FolderIndexManager(
                new SolrClientWrapperImpl(Configuration.getInstance().getSolrObjectFactory()));

        NdexShortcut shortcut = createTestShortcut("Test Shortcut");
        shortcut.setTarget(UUID.randomUUID());
        manager.createIndex(shortcut, VisibilityType.PUBLIC, null, null);

        NdexFolder folder = new NdexFolder();
        folder.setExternalId(UUID.randomUUID());
        folder.setName("Test Folder");
        folder.setOwner("testOwner");
        folder.setCreationTime(Timestamp.from(Instant.now()));
        folder.setModificationTime(Timestamp.from(Instant.now()));
        folderMgr.createIndex(folder, VisibilityType.PUBLIC, null, null);

        Thread.sleep(2000);

        var results = manager.searchByType("*:*", "testOwner", VisibilityType.PUBLIC,
                100, 0, null, null, FileType.SHORTCUT.toString(), true);

        assertEquals(1, results.getNumFound());

        folderMgr.close();
    }

    // ========================================================================
    // HELPERS
    // ========================================================================

    private NdexShortcut createTestShortcut(String name) {
        NdexShortcut shortcut = new NdexShortcut();
        shortcut.setExternalId(UUID.randomUUID());
        shortcut.setName(name);
        shortcut.setOwner("testOwner");
        shortcut.setTarget(UUID.randomUUID());
        shortcut.setTargetType(FileType.NETWORK);
        shortcut.setCreationTime(Timestamp.from(Instant.now()));
        shortcut.setModificationTime(Timestamp.from(Instant.now()));
        return shortcut;
    }
}