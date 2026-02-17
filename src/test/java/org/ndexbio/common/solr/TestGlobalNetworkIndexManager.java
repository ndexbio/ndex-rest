package org.ndexbio.common.solr;

import org.apache.solr.client.solrj.SolrQuery;
import org.apache.solr.client.solrj.response.QueryResponse;
import org.apache.solr.common.SolrDocumentList;
import org.apache.solr.common.SolrInputDocument;
import org.easymock.Capture;
import org.junit.After;
import org.junit.BeforeClass;
import org.junit.Ignore;
import org.junit.Test;
import org.ndexbio.model.object.NdexFolder;
import org.ndexbio.model.object.Permissions;
import org.ndexbio.model.object.network.NetworkSummary;
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
 * Unit tests for GlobalNetworkIndexManager using mocked SolrClientWrapper.
 * Integration tests (requiring live Solr) are kept but @Ignore'd.
 */
public class TestGlobalNetworkIndexManager {

    private GlobalNetworkIndexManager manager;
    private SolrClientWrapper mockWrapper;

    @BeforeClass
    public static void setUpClass() throws Exception {
        Configuration mockConfig = createMock(Configuration.class);
        expect(mockConfig.getSolrURL()).andReturn("http://localhost:8983/solr").anyTimes();
        replay(mockConfig);

        Field instanceField = Configuration.class.getDeclaredField("INSTANCE");
        instanceField.setAccessible(true);
        instanceField.set(null, mockConfig);
    }

    @After
    public void tearDown() {
        if (manager != null) {
            manager.close();
        }
    }

    private GlobalNetworkIndexManager createManagerWithMock() {
        mockWrapper = createNiceMock(SolrClientWrapper.class);
        replay(mockWrapper);
        return new GlobalNetworkIndexManager(mockWrapper);
    }

    // ========================================================================
    // SETUP INDEX DOCUMENT TESTS - COMPLETE NETWORKS
    // ========================================================================

    @Test
    public void testSetupIndexDocument_AllFieldsSet() {
        manager = createManagerWithMock();

        UUID networkId = UUID.randomUUID();
        Timestamp creationTime = Timestamp.from(Instant.now().minusSeconds(3600));
        Timestamp modificationTime = Timestamp.from(Instant.now());

        NetworkSummary summary = new NetworkSummary();
        summary.setExternalId(networkId);
        summary.setName("Test Network");
        summary.setDescription("Test Description");
        summary.setVersion("1.0.0");
        summary.setOwner("testOwner");
        summary.setNodeCount(100);
        summary.setEdgeCount(500);
        summary.setCreationTime(creationTime);
        summary.setModificationTime(modificationTime);

        SolrInputDocument doc = manager.setupIndexDocument(summary, VisibilityType.PUBLIC);

        assertNotNull(doc);
        assertEquals(networkId.toString(), doc.getFieldValue("uuid"));
        assertEquals("NETWORK", doc.getFieldValue("entityType"));
        assertEquals("Test Network", doc.getFieldValue("name"));
        assertEquals("Test Description", doc.getFieldValue("description"));
        assertEquals("1.0.0", doc.getFieldValue("version"));
        assertEquals(100, doc.getFieldValue("nodeCount"));
        assertEquals(500, doc.getFieldValue("edgeCount"));
        assertEquals("testOwner", doc.getFieldValue("owner"));
        assertEquals(creationTime, doc.getFieldValue("creationTime"));
        assertEquals(modificationTime, doc.getFieldValue("modificationTime"));
        assertNotNull(doc.getFieldValue("ndexScore"));
    }

    @Test
    public void testSetupIndexDocument_MinimalNetwork_OnlyRequiredFields() {
        manager = createManagerWithMock();

        UUID networkId = UUID.randomUUID();
        Timestamp now = Timestamp.from(Instant.now());

        NetworkSummary summary = new NetworkSummary();
        summary.setExternalId(networkId);
        summary.setCreationTime(now);
        summary.setModificationTime(now);

        SolrInputDocument doc = manager.setupIndexDocument(summary, VisibilityType.PUBLIC);

        assertNotNull(doc);
        assertEquals(networkId.toString(), doc.getFieldValue("uuid"));
        assertEquals("NETWORK", doc.getFieldValue("entityType"));
        assertEquals(0, doc.getFieldValue("nodeCount"));
        assertEquals(0, doc.getFieldValue("edgeCount"));

        assertNull(doc.getFieldValue("name"));
        assertNull(doc.getFieldValue("description"));
        assertNull(doc.getFieldValue("version"));
    }

    // ========================================================================
    // NAME FIELD VALIDATION
    // ========================================================================

    @Test
    public void testSetupIndexDocument_NullName_NotIndexed() {
        manager = createManagerWithMock();
        NetworkSummary s = createTestSummary(null, "Description");
        SolrInputDocument doc = manager.setupIndexDocument(s, VisibilityType.PUBLIC);
        assertNull(doc.getFieldValue("name"));
    }

    @Test
    public void testSetupIndexDocument_EmptyName_NotIndexed() {
        manager = createManagerWithMock();
        NetworkSummary s = createTestSummary("", "Description");
        SolrInputDocument doc = manager.setupIndexDocument(s, VisibilityType.PUBLIC);
        assertNull(doc.getFieldValue("name"));
    }

    @Test
    public void testSetupIndexDocument_WhitespaceOnlyName_NotIndexed() {
        manager = createManagerWithMock();
        NetworkSummary s = createTestSummary("   ", "Description");
        SolrInputDocument doc = manager.setupIndexDocument(s, VisibilityType.PUBLIC);
        assertNull(doc.getFieldValue("name"));
    }

    @Test
    public void testSetupIndexDocument_ValidName_IsIndexed() {
        manager = createManagerWithMock();
        NetworkSummary s = createTestSummary("Cancer Pathway", "Description");
        SolrInputDocument doc = manager.setupIndexDocument(s, VisibilityType.PUBLIC);
        assertEquals("Cancer Pathway", doc.getFieldValue("name"));
    }

    @Test
    public void testSetupIndexDocument_LongName_IsIndexed() {
        manager = createManagerWithMock();
        String longName = "This is a very long network name with many characters " +
                "that exceeds normal length expectations for testing purposes";
        NetworkSummary s = createTestSummary(longName, "Description");
        SolrInputDocument doc = manager.setupIndexDocument(s, VisibilityType.PUBLIC);
        assertEquals(longName, doc.getFieldValue("name"));
    }

    @Test
    public void testSetupIndexDocument_NameWithSpecialCharacters() {
        manager = createManagerWithMock();
        String specialName = "Network-Name_2024 (Test) [v1.0]";
        NetworkSummary s = createTestSummary(specialName, "Description");
        SolrInputDocument doc = manager.setupIndexDocument(s, VisibilityType.PUBLIC);
        assertEquals(specialName, doc.getFieldValue("name"));
    }

    // ========================================================================
    // DESCRIPTION FIELD VALIDATION
    // ========================================================================

    @Test
    public void testSetupIndexDocument_NullDescription_NotIndexed() {
        manager = createManagerWithMock();
        NetworkSummary s = createTestSummary("Name", null);
        SolrInputDocument doc = manager.setupIndexDocument(s, VisibilityType.PUBLIC);
        assertNull(doc.getFieldValue("description"));
    }

    @Test
    public void testSetupIndexDocument_EmptyDescription_NotIndexed() {
        manager = createManagerWithMock();
        NetworkSummary s = createTestSummary("Name", "");
        SolrInputDocument doc = manager.setupIndexDocument(s, VisibilityType.PUBLIC);
        assertNull(doc.getFieldValue("description"));
    }

    @Test
    public void testSetupIndexDocument_WhitespaceOnlyDescription_NotIndexed() {
        manager = createManagerWithMock();
        NetworkSummary s = createTestSummary("Name", "   ");
        SolrInputDocument doc = manager.setupIndexDocument(s, VisibilityType.PUBLIC);
        assertNull(doc.getFieldValue("description"));
    }

    @Test
    public void testSetupIndexDocument_ValidDescription_IsIndexed() {
        manager = createManagerWithMock();
        NetworkSummary s = createTestSummary("Name", "Valid description");
        SolrInputDocument doc = manager.setupIndexDocument(s, VisibilityType.PUBLIC);
        assertEquals("Valid description", doc.getFieldValue("description"));
    }

    // ========================================================================
    // VERSION FIELD VALIDATION
    // ========================================================================

    @Test
    public void testSetupIndexDocument_NullVersion_NotIndexed() {
        manager = createManagerWithMock();
        NetworkSummary s = createTestSummary("Name", "Desc");
        SolrInputDocument doc = manager.setupIndexDocument(s, VisibilityType.PUBLIC);
        assertNull(doc.getFieldValue("version"));
    }

    @Test
    public void testSetupIndexDocument_EmptyVersion_NotIndexed() {
        manager = createManagerWithMock();
        NetworkSummary s = createTestSummary("Name", "Desc");
        s.setVersion("");
        SolrInputDocument doc = manager.setupIndexDocument(s, VisibilityType.PUBLIC);
        assertNull(doc.getFieldValue("version"));
    }

    @Test
    public void testSetupIndexDocument_WhitespaceOnlyVersion_NotIndexed() {
        manager = createManagerWithMock();
        NetworkSummary s = createTestSummary("Name", "Desc");
        s.setVersion("   ");
        SolrInputDocument doc = manager.setupIndexDocument(s, VisibilityType.PUBLIC);
        assertNull(doc.getFieldValue("version"));
    }

    @Test
    public void testSetupIndexDocument_ValidVersion_IsIndexed() {
        manager = createManagerWithMock();
        NetworkSummary s = createTestSummary("Name", "Desc");
        s.setVersion("1.0");
        SolrInputDocument doc = manager.setupIndexDocument(s, VisibilityType.PUBLIC);
        assertEquals("1.0", doc.getFieldValue("version"));
    }

    // ========================================================================
    // OWNER FIELD VALIDATION
    // ========================================================================

    @Test
    public void testSetupIndexDocument_NullOwner() {
        manager = createManagerWithMock();
        NetworkSummary s = createTestSummary("Name", "Desc");
        s.setOwner(null);
        SolrInputDocument doc = manager.setupIndexDocument(s, VisibilityType.PUBLIC);
        assertNull(doc.getFieldValue("owner"));
    }

    @Test
    public void testSetupIndexDocument_BlankOwner() {
        manager = createManagerWithMock();
        NetworkSummary s = createTestSummary("Name", "Desc");
        s.setOwner("  ");
        SolrInputDocument doc = manager.setupIndexDocument(s, VisibilityType.PUBLIC);
        // Document current behavior — owner set unconditionally or blank-checked?
        // Adjust assertion based on your implementation
        assertEquals("  ", doc.getFieldValue("owner"));
    }

    @Test
    public void testSetupIndexDocument_ValidOwner() {
        manager = createManagerWithMock();
        NetworkSummary s = createTestSummary("Name", "Desc");
        s.setOwner("testOwner");
        SolrInputDocument doc = manager.setupIndexDocument(s, VisibilityType.PUBLIC);
        assertEquals("testOwner", doc.getFieldValue("owner"));
    }

    // ========================================================================
    // NODE AND EDGE COUNT TESTS
    // ========================================================================

    @Test
    public void testSetupIndexDocument_ZeroCounts() {
        manager = createManagerWithMock();
        NetworkSummary s = createTestSummary("Test", "Test");
        s.setNodeCount(0);
        s.setEdgeCount(0);
        SolrInputDocument doc = manager.setupIndexDocument(s, VisibilityType.PUBLIC);
        assertEquals(0, doc.getFieldValue("nodeCount"));
        assertEquals(0, doc.getFieldValue("edgeCount"));
    }

    @Test
    public void testSetupIndexDocument_LargeCounts() {
        manager = createManagerWithMock();
        NetworkSummary s = createTestSummary("Large Network", "Big one");
        s.setNodeCount(1000000);
        s.setEdgeCount(5000000);
        SolrInputDocument doc = manager.setupIndexDocument(s, VisibilityType.PUBLIC);
        assertEquals(1000000, doc.getFieldValue("nodeCount"));
        assertEquals(5000000, doc.getFieldValue("edgeCount"));
    }

    // ========================================================================
    // NDEX SCORE TESTS
    // ========================================================================

    @Test
    public void testSetupIndexDocument_NdexScoreIsSet() {
        manager = createManagerWithMock();
        NetworkSummary s = createTestSummary("Test", "Test");
        SolrInputDocument doc = manager.setupIndexDocument(s, VisibilityType.PUBLIC);
        assertNotNull(doc.getFieldValue("ndexScore"));
    }

    @Test
    public void testSetupIndexDocument_NdexScoreConsistentAcrossVisibilities() {
        manager = createManagerWithMock();
        NetworkSummary s = createTestSummary("Test", "Test");

        SolrInputDocument publicDoc = manager.setupIndexDocument(s, VisibilityType.PUBLIC);
        Object publicScore = publicDoc.getFieldValue("ndexScore");

        SolrInputDocument privateDoc = manager.setupIndexDocument(s, VisibilityType.PRIVATE);
        Object privateScore = privateDoc.getFieldValue("ndexScore");

        assertEquals(publicScore, privateScore);
    }

    // ========================================================================
    // PREPARE INDEX DOCUMENT - VISIBILITY + PERMISSIONS
    // ========================================================================

    @Test
    public void testPrepareIndexDocument_PublicVisibility_NoPermissionFields() {
        manager = createManagerWithMock();
        NetworkSummary s = createTestSummary("Public Network", "Public");

        List<String> readers = Arrays.asList("reader1", "reader2");
        List<String> editors = Arrays.asList("editor1");

        manager.prepareIndexDocument(s, VisibilityType.PUBLIC, readers, editors);

        SolrInputDocument doc = manager.doc;
        assertEquals("PUBLIC", doc.getFieldValue("visibility"));
        assertNull(doc.getFieldValue("userRead"));
        assertNull(doc.getFieldValue("userEdit"));
    }

    @Test
    public void testPrepareIndexDocument_PrivateVisibility_HasPermissionFields() {
        manager = createManagerWithMock();
        NetworkSummary s = createTestSummary("Private Network", "Private");

        List<String> readers = Arrays.asList("reader1", "reader2");
        List<String> editors = Arrays.asList("editor1");

        manager.prepareIndexDocument(s, VisibilityType.PRIVATE, readers, editors);

        SolrInputDocument doc = manager.doc;
        assertEquals("PRIVATE", doc.getFieldValue("visibility"));

        Collection<Object> readValues = doc.getFieldValues("userRead");
        assertNotNull(readValues);
        assertEquals(2, readValues.size());
        assertTrue(readValues.contains("reader1"));
        assertTrue(readValues.contains("reader2"));

        Collection<Object> editValues = doc.getFieldValues("userEdit");
        assertNotNull(editValues);
        assertEquals(1, editValues.size());
        assertTrue(editValues.contains("editor1"));
    }

    @Test
    public void testPrepareIndexDocument_PrivateVisibility_NullPermissions() {
        manager = createManagerWithMock();
        NetworkSummary s = createTestSummary("Private Network", "Private");

        manager.prepareIndexDocument(s, VisibilityType.PRIVATE, null, null);

        SolrInputDocument doc = manager.doc;
        assertEquals("PRIVATE", doc.getFieldValue("visibility"));
        assertNull(doc.getFieldValue("userRead"));
        assertNull(doc.getFieldValue("userEdit"));
    }

    @Test
    public void testPrepareIndexDocument_PrivateVisibility_BlankValuesSkipped() {
        manager = createManagerWithMock();
        NetworkSummary s = createTestSummary("Private Network", "Private");

        List<String> readers = Arrays.asList("reader1", "", "  ", null, "reader2");
        List<String> editors = Arrays.asList("", null);

        manager.prepareIndexDocument(s, VisibilityType.PRIVATE, readers, editors);

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
    public void testCreateIndex_PublicNetwork_CommitsToPublicCore() throws Exception {
        mockWrapper = createMock(SolrClientWrapper.class);
        Capture<String> coreCapture = Capture.newInstance();

        mockWrapper.commit(capture(coreCapture), anyObject(Collection.class));
        expectLastCall().once();
        mockWrapper.close();
        expectLastCall().anyTimes();
        replay(mockWrapper);

        manager = new GlobalNetworkIndexManager(mockWrapper);
        NetworkSummary s = createTestSummary("Test", "Test");

        // Verify document via prepareIndexDocument
        manager.prepareIndexDocument(s, VisibilityType.PUBLIC, null, null);
        assertEquals("NETWORK", manager.doc.getFieldValue("entityType"));
        assertEquals("Test", manager.doc.getFieldValue("name"));

        // Test commit routing
        reset(mockWrapper);
        mockWrapper.commit(capture(coreCapture), anyObject(Collection.class));
        expectLastCall().once();
        mockWrapper.close();
        expectLastCall().anyTimes();
        replay(mockWrapper);

        manager = new GlobalNetworkIndexManager(mockWrapper);
        manager.createIndex(s, VisibilityType.PUBLIC, null, null);

        verify(mockWrapper);
        assertEquals("public-nfs", coreCapture.getValue());
    }

    @Test
    public void testCreateIndex_PrivateNetwork_CommitsToPrivateCore() throws Exception {
        mockWrapper = createMock(SolrClientWrapper.class);
        Capture<String> coreCapture = Capture.newInstance();

        mockWrapper.commit(capture(coreCapture), anyObject(Collection.class));
        expectLastCall().once();
        mockWrapper.close();
        expectLastCall().anyTimes();
        replay(mockWrapper);

        manager = new GlobalNetworkIndexManager(mockWrapper);
        NetworkSummary s = createTestSummary("Private", "Private network");
        List<String> readers = Arrays.asList("user1");

        // Verify document
        manager.prepareIndexDocument(s, VisibilityType.PRIVATE, readers, null);
        assertEquals("PRIVATE", manager.doc.getFieldValue("visibility"));
        assertNotNull(manager.doc.getFieldValues("userRead"));

        // Test commit routing
        reset(mockWrapper);
        mockWrapper.commit(capture(coreCapture), anyObject(Collection.class));
        expectLastCall().once();
        mockWrapper.close();
        expectLastCall().anyTimes();
        replay(mockWrapper);

        manager = new GlobalNetworkIndexManager(mockWrapper);
        manager.createIndex(s, VisibilityType.PRIVATE, readers, null);

        verify(mockWrapper);
        assertEquals("private-nfs", coreCapture.getValue());
    }

    // ========================================================================
    // DELETE
    // ========================================================================

    @Test
    public void testDelete_PublicNetwork_DeletesFromPublicCore() throws Exception {
        mockWrapper = createMock(SolrClientWrapper.class);
        Capture<String> coreCapture = Capture.newInstance();
        Capture<String> uuidCapture = Capture.newInstance();

        mockWrapper.delete(capture(coreCapture), capture(uuidCapture), eq(false));
        expectLastCall().once();
        mockWrapper.close();
        expectLastCall().anyTimes();
        replay(mockWrapper);

        manager = new GlobalNetworkIndexManager(mockWrapper);
        manager.delete("network-uuid-123", VisibilityType.PUBLIC);

        verify(mockWrapper);
        assertEquals("public-nfs", coreCapture.getValue());
        assertEquals("network-uuid-123", uuidCapture.getValue());
    }

    @Test
    public void testDelete_PrivateNetwork_DeletesFromPrivateCore() throws Exception {
        mockWrapper = createMock(SolrClientWrapper.class);
        Capture<String> coreCapture = Capture.newInstance();

        mockWrapper.delete(capture(coreCapture), anyString(), eq(false));
        expectLastCall().once();
        mockWrapper.close();
        expectLastCall().anyTimes();
        replay(mockWrapper);

        manager = new GlobalNetworkIndexManager(mockWrapper);
        manager.delete("network-uuid-456", VisibilityType.PRIVATE);

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

        manager = new GlobalNetworkIndexManager(mockWrapper);
        manager.search("*:*", null, VisibilityType.PUBLIC, 10, 0, null, null);

        verify(mockWrapper);
        assertEquals("public-nfs", coreCapture.getValue());

        SolrQuery captured = queryCapture.getValue();
        assertEquals("*:*", captured.getQuery());
        assertEquals("edismax", captured.get("defType"));

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

        manager = new GlobalNetworkIndexManager(mockWrapper);
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

        manager = new GlobalNetworkIndexManager(mockWrapper);
        manager.search("test", "user1", VisibilityType.PUBLIC, 10, 0, "specificOwner", null);

        String[] fq = queryCapture.getValue().getFilterQueries();
        assertTrue(fq[0].contains("owner:\"specificOwner\""));
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

        manager = new GlobalNetworkIndexManager(mockWrapper);
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

        manager = new GlobalNetworkIndexManager(mockWrapper);
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

        manager = new GlobalNetworkIndexManager(mockWrapper);
        manager.search("*:*", "david", VisibilityType.PRIVATE, 10, 0, null, Permissions.WRITE);

        String[] fq = queryCapture.getValue().getFilterQueries();
        assertTrue(fq[0].contains("owner:\"david\""));
        assertTrue(fq[0].contains("userEdit:\"david\""));
        assertFalse(fq[0].contains("userRead"));
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

        manager = new GlobalNetworkIndexManager(mockWrapper);
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

        manager = new GlobalNetworkIndexManager(mockWrapper);
        manager.search("*:*", "bob", VisibilityType.PUBLIC, 10, 0, null, Permissions.WRITE);

        String[] fq = queryCapture.getValue().getFilterQueries();
        assertTrue(fq[0].contains("owner:\"bob\""));
        assertTrue(fq[0].contains("userEdit:\"bob\""));
        assertFalse(fq[0].contains("userRead"));
        assertFalse(fq[0].contains("UNLISTED"));
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

        manager = new GlobalNetworkIndexManager(mockWrapper);
        manager.searchByType("test", "user", VisibilityType.PUBLIC, 10, 0,
                null, null, "NETWORK");

        String[] fq = queryCapture.getValue().getFilterQueries();
        assertTrue(fq[0].contains("entityType:\"NETWORK\""));
    }

    // ========================================================================
    // QUERY FIELDS
    // ========================================================================

    @Test
    public void testGetQueryFields_ReturnsExpectedString() {
        manager = createManagerWithMock();
        String expected = "uuid^20 name^10 description^5 labels^6 owner^2 " +
                "networkType^4 organism^3 disease^3 tissue^3 author^2 methods " +
                "nodeName represents alias rights^0.6 rightsHolder^0.6";
        assertEquals(expected, manager.getQueryFields());
    }

    @Test
    public void testGetQueryFields_ContainsNetworkSpecificFields() {
        manager = createManagerWithMock();
        String qf = manager.getQueryFields();
        assertTrue(qf.contains("organism"));
        assertTrue(qf.contains("disease"));
        assertTrue(qf.contains("tissue"));
        assertTrue(qf.contains("networkType"));
        assertTrue(qf.contains("nodeName"));
        assertTrue(qf.contains("represents"));
        assertTrue(qf.contains("alias"));
    }

    @Test
    public void testGetQueryFields_HasCorrectBoostValues() {
        manager = createManagerWithMock();
        String qf = manager.getQueryFields();
        assertTrue(qf.contains("uuid^20"));
        assertTrue(qf.contains("name^10"));
        assertTrue(qf.contains("description^5"));
        assertTrue(qf.contains("labels^6"));
        assertTrue(qf.contains("organism^3"));
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
        assertTrue(q.get("qf").contains("organism"));
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

    // ========================================================================
    // PREPROCESS SEARCH TERMS - NETWORK SPECIFIC (ndexScore boost)
    // ========================================================================

    @Test
    public void testPreprocessSearchTerms_Wildcard_NotModified() {
        manager = createManagerWithMock();
        assertEquals("*:*", manager.preprocessSearchTerms("*:*"));
    }

    @Test
    public void testPreprocessSearchTerms_RegularQuery_AddsScoreBoost() {
        manager = createManagerWithMock();
        String result = manager.preprocessSearchTerms("cancer");

        assertTrue(result.contains("_val_:\"div(ndexScore,10)\""));
        assertTrue(result.contains(" AND "));
        assertTrue(result.contains(SearchUtilities.preprocessSearchTerm("cancer")));
    }

    @Test
    public void testPreprocessSearchTerms_ComplexQuery_AddsScoreBoost() {
        manager = createManagerWithMock();
        String result = manager.preprocessSearchTerms("cancer pathway signaling");

        assertTrue(result.contains("ndexScore"));
        assertTrue(result.startsWith("( "));
    }

    @Test
    public void testPreprocessSearchTerms_DifferentFromFolderManager() {
        manager = createManagerWithMock();

        SolrClientWrapper folderMock = createNiceMock(SolrClientWrapper.class);
        replay(folderMock);
        FolderIndexManager folderMgr = new FolderIndexManager(folderMock);

        String networkResult = manager.preprocessSearchTerms("test");
        String folderResult = folderMgr.preprocessSearchTerms("test");

        assertNotEquals(networkResult, folderResult);
        assertTrue(networkResult.contains("ndexScore"));
        assertFalse(folderResult.contains("ndexScore"));

        folderMgr.close();
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
    public void testBuildPermissionFilter_PublicCore_AuthenticatedNullPermission() {
        manager = createManagerWithMock();
        assertEquals("(*:* NOT visibility:UNLISTED) OR (owner:\"alice\")",
                manager.buildPermissionFilter("alice", VisibilityType.PUBLIC, null));
    }

    @Test
    public void testBuildPermissionFilter_PublicCore_AuthenticatedRead() {
        manager = createManagerWithMock();
        assertEquals("(*:* NOT visibility:UNLISTED) OR (owner:\"alice\")",
                manager.buildPermissionFilter("alice", VisibilityType.PUBLIC, Permissions.READ));
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
    // POST COMMIT TESTS
    // ========================================================================

    @Test
    public void testPostCommit_ResetsNodeMembers() throws Exception {
        manager = createManagerWithMock();

        Field nodeMembersField = GlobalNetworkIndexManager.class.getDeclaredField("nodeMembers");
        nodeMembersField.setAccessible(true);

        @SuppressWarnings("unchecked")
        Map<Long, Set<String>> nodeMembers = (Map<Long, Set<String>>) nodeMembersField.get(manager);
        nodeMembers.put(1L, new HashSet<>(Arrays.asList("gene1", "gene2")));
        assertFalse(nodeMembers.isEmpty());

        manager.postCommit();

        @SuppressWarnings("unchecked")
        Map<Long, Set<String>> after = (Map<Long, Set<String>>) nodeMembersField.get(manager);
        assertTrue(after.isEmpty());
    }

    // ========================================================================
    // OTHER ATTRIBUTES SET
    // ========================================================================

    @Test
    public void testOtherAttributes_ContainsExpectedFields() {
        assertTrue(GlobalNetworkIndexManager.otherAttributes.contains("organism"));
        assertTrue(GlobalNetworkIndexManager.otherAttributes.contains("disease"));
        assertTrue(GlobalNetworkIndexManager.otherAttributes.contains("tissue"));
        assertTrue(GlobalNetworkIndexManager.otherAttributes.contains("networkType"));
        assertTrue(GlobalNetworkIndexManager.otherAttributes.contains("author"));
        assertTrue(GlobalNetworkIndexManager.otherAttributes.contains("labels"));
        assertTrue(GlobalNetworkIndexManager.otherAttributes.contains("rights"));
        assertTrue(GlobalNetworkIndexManager.otherAttributes.contains("rightsHolder"));
    }

    // ========================================================================
    // DOCUMENT RESET STATE
    // ========================================================================

    @Test
    public void testSetupIndexDocument_CalledTwice_DocumentIsReset() {
        manager = createManagerWithMock();

        NetworkSummary s1 = createTestSummary("Network One", "Description One");
        SolrInputDocument doc1 = manager.setupIndexDocument(s1, VisibilityType.PUBLIC);
        assertEquals("Network One", doc1.getFieldValue("name"));

        NetworkSummary s2 = createTestSummary("Network Two", "Description Two");
        SolrInputDocument doc2 = manager.setupIndexDocument(s2, VisibilityType.PUBLIC);
        assertEquals("Network Two", doc2.getFieldValue("name"));
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

    private GlobalNetworkIndexManager createIntegrationManager() {
        SolrClientWrapper wrapper = new SolrClientWrapperImpl(
                Configuration.getInstance().getSolrObjectFactory());
        return new GlobalNetworkIndexManager(wrapper);
    }

    @Test @Ignore
    public void testSearch_Integration() throws Exception {
        manager = createIntegrationManager();

        NetworkSummary cancer = createTestSummary("Cancer Pathway Network", "Signaling pathways in cancer");
        manager.createIndex(cancer, VisibilityType.PUBLIC, null, null);

        NetworkSummary diabetes = createTestSummary("Diabetes Signaling", "Insulin signaling network");
        manager.createIndex(diabetes, VisibilityType.PUBLIC, null, null);

        Thread.sleep(2000);

        SolrDocumentList results = manager.searchByType("cancer", "testOwner", VisibilityType.PUBLIC,
                100, 0, null, null, "NETWORK");

        assertNotNull(results);
        assertEquals(1, results.getNumFound());
    }

    @Test @Ignore
    public void testSearch_WithAdminFilter_Integration() throws Exception {
        manager = createIntegrationManager();

        NetworkSummary s1 = createTestSummary("My Network", "Owned by me");
        s1.setOwner("owner1");
        manager.createIndex(s1, VisibilityType.PUBLIC, null, null);

        NetworkSummary s2 = createTestSummary("Their Network", "Owned by them");
        s2.setOwner("owner2");
        manager.createIndex(s2, VisibilityType.PUBLIC, null, null);

        Thread.sleep(2000);

        SolrDocumentList results = manager.search("*:*", "owner1", VisibilityType.PUBLIC,
                100, 0, "owner1", Permissions.ADMIN);

        assertNotNull(results);
        assertEquals(1, results.getNumFound());
    }

    @Test @Ignore
    public void testSearch_Pagination_Integration() throws Exception {
        manager = createIntegrationManager();

        for (int i = 0; i < 10; i++) {
            NetworkSummary s = createTestSummary("Network " + i, "Description " + i);
            manager.createIndex(s, VisibilityType.PUBLIC, null, null);
        }

        Thread.sleep(2000);

        SolrDocumentList page1 = manager.search("*:*", "testOwner", VisibilityType.PUBLIC,
                5, 0, null, null);
        assertEquals(5, page1.size());
        assertEquals(10, page1.getNumFound());

        SolrDocumentList page2 = manager.search("*:*", "testOwner", VisibilityType.PUBLIC,
                5, 5, null, null);
        assertEquals(5, page2.size());
    }

    @Test @Ignore
    public void testSearch_PrivateVisibility_Integration() throws Exception {
        manager = createIntegrationManager();

        NetworkSummary s = createTestSummary("Private Network", "Secret data");
        s.setOwner("owner");
        List<String> readers = Arrays.asList("reader1", "reader2");
        manager.createIndex(s, VisibilityType.PRIVATE, readers, null);

        Thread.sleep(2000);

        SolrDocumentList anonResults = manager.search("*:*", null, VisibilityType.PRIVATE,
                100, 0, null, null);
        assertEquals(0, anonResults.getNumFound());

        SolrDocumentList ownerResults = manager.search("*:*", "owner", VisibilityType.PRIVATE,
                100, 0, null, null);
        assertEquals(1, ownerResults.getNumFound());

        SolrDocumentList readerResults = manager.search("*:*", "reader1", VisibilityType.PRIVATE,
                100, 0, null, null);
        assertEquals(1, readerResults.getNumFound());

        SolrDocumentList strangerResults = manager.search("*:*", "stranger", VisibilityType.PRIVATE,
                100, 0, null, null);
        assertEquals(0, strangerResults.getNumFound());
    }

    @Test @Ignore
    public void testSearch_ScoreBoost_Integration() throws Exception {
        manager = createIntegrationManager();

        NetworkSummary highScore = createTestSummary("Cancer Network High", "High quality");
        highScore.setNodeCount(10000);
        highScore.setEdgeCount(50000);
        manager.createIndex(highScore, VisibilityType.PUBLIC, null, null);

        NetworkSummary lowScore = createTestSummary("Cancer Network Low", "Low quality");
        lowScore.setNodeCount(10);
        lowScore.setEdgeCount(5);
        manager.createIndex(lowScore, VisibilityType.PUBLIC, null, null);

        Thread.sleep(2000);

        SolrDocumentList results = manager.search("cancer", "testOwner", VisibilityType.PUBLIC,
                100, 0, null, null);

        assertEquals(2, results.getNumFound());
        assertEquals(highScore.getExternalId().toString(),
                results.get(0).getFieldValue("uuid"));
    }

    @Test @Ignore
    public void testSearch_OnlyReturnsNetworks_Integration() throws Exception {
        manager = createIntegrationManager();
        FolderIndexManager folderMgr = new FolderIndexManager(
                new SolrClientWrapperImpl(Configuration.getInstance().getSolrObjectFactory()));

        NetworkSummary network = createTestSummary("Test Network", "A network");
        manager.createIndex(network, VisibilityType.PUBLIC, null, null);

        NdexFolder folder = new NdexFolder();
        folder.setExternalId(UUID.randomUUID());
        folder.setName("Test Folder");
        folder.setDescription("A folder");
        folder.setOwner("testOwner");
        folder.setCreationTime(Timestamp.from(Instant.now()));
        folder.setModificationTime(Timestamp.from(Instant.now()));
        folderMgr.createIndex(folder, VisibilityType.PUBLIC, null, null);

        Thread.sleep(2000);

        SolrDocumentList results = manager.searchByType("*:*", "testOwner", VisibilityType.PUBLIC,
                100, 0, null, null, "NETWORK");

        assertEquals(1, results.getNumFound());

        folderMgr.close();
    }

    // ========================================================================
    // HELPERS
    // ========================================================================

    private NetworkSummary createTestSummary(String name, String description) {
        NetworkSummary s = new NetworkSummary();
        s.setExternalId(UUID.randomUUID());
        s.setName(name);
        s.setDescription(description);
        s.setOwner("testOwner");
        s.setNodeCount(100);
        s.setEdgeCount(500);
        s.setCreationTime(Timestamp.from(Instant.now()));
        s.setModificationTime(Timestamp.from(Instant.now()));
        return s;
    }
}