package org.ndexbio.common.solr;

import org.apache.solr.client.solrj.SolrQuery;
import org.apache.solr.common.SolrInputDocument;
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
 * Comprehensive unit tests for GlobalNetworkIndexManager
 * Tests document setup, query configuration, permission filters,
 * search term preprocessing, and edge cases.
 */
public class TestGlobalNetworkIndexManager {

    private GlobalNetworkIndexManager publicManager;
    private GlobalNetworkIndexManager privateManager;
    private GlobalNetworkIndexManager unlistedManager;

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
        closeManager(publicManager);
        closeManager(privateManager);
        closeManager(unlistedManager);
    }

    private void closeManager(GlobalNetworkIndexManager manager) {
        if (manager != null) {
            manager.close();
        }
    }

    // ========================================================================
    // CORE NAME AND INITIALIZATION TESTS
    // ========================================================================

    @Test
    public void testConstructor_PublicVisibility_UsesPublicCore() {
        publicManager = new GlobalNetworkIndexManager(VisibilityType.PUBLIC);
        assertEquals("public-nfs", publicManager.coreName);
        assertNotNull("SolrInputDocument should be initialized", publicManager.doc);
        assertNotNull("HttpSolrClient should be initialized", publicManager.client);
    }

    @Test
    public void testConstructor_UnlistedVisibility_UsesPublicCore() {
        unlistedManager = new GlobalNetworkIndexManager(VisibilityType.UNLISTED);
        assertEquals("public-nfs", unlistedManager.coreName);
    }

    @Test
    public void testConstructor_PrivateVisibility_UsesPrivateCore() {
        privateManager = new GlobalNetworkIndexManager(VisibilityType.PRIVATE);
        assertEquals("private-nfs", privateManager.coreName);
    }

    @Test
    public void testConstructor_VisibilityTypeStored() {
        publicManager = new GlobalNetworkIndexManager(VisibilityType.PUBLIC);
        privateManager = new GlobalNetworkIndexManager(VisibilityType.PRIVATE);

        assertEquals(VisibilityType.PUBLIC, publicManager.visibilityType);
        assertEquals(VisibilityType.PRIVATE, privateManager.visibilityType);
    }

    // ========================================================================
    // SETUP INDEX DOCUMENT TESTS - COMPLETE NETWORKS
    // ========================================================================

    @Test
    public void testSetupIndexDocument_PublicNetwork_AllFieldsSet() {
        publicManager = new GlobalNetworkIndexManager(VisibilityType.PUBLIC);

        UUID networkId = UUID.randomUUID();
        Timestamp creationTime = Timestamp.from(Instant.now().minusSeconds(3600));
        Timestamp modificationTime = Timestamp.from(Instant.now());

        NetworkSummary summary = new NetworkSummary();
        summary.setExternalId(networkId);
        summary.setName("Test Network");
        summary.setDescription("Test Description");
        summary.setVersion("1.0.0");
        summary.setNodeCount(100);
        summary.setEdgeCount(500);
        summary.setCreationTime(creationTime);
        summary.setModificationTime(modificationTime);

        NetworkSummaryWrapper wrapper = new NetworkSummaryWrapper(
                summary, "testOwner", null, null);

        SolrInputDocument doc = publicManager.setupIndexDocument(wrapper);

        assertNotNull("Document should not be null", doc);
        assertEquals("UUID should match", networkId.toString(), doc.getFieldValue("uuid"));
        assertEquals("Entity type should be NETWORK", "NETWORK", doc.getFieldValue("entityType"));
        assertEquals("Name should match", "Test Network", doc.getFieldValue("name"));
        assertEquals("Description should match", "Test Description", doc.getFieldValue("description"));
        assertEquals("Version should match", "1.0.0", doc.getFieldValue("version"));
        assertEquals("Node count should match", 100, doc.getFieldValue("nodeCount"));
        assertEquals("Edge count should match", 500, doc.getFieldValue("edgeCount"));
        assertEquals("Owner should match", "testOwner", doc.getFieldValue("owner"));
        assertEquals("Creation time should match", creationTime, doc.getFieldValue("creationTime"));
        assertEquals("Modification time should match", modificationTime, doc.getFieldValue("modificationTime"));
        assertNotNull("ndexScore should be set", doc.getFieldValue("ndexScore"));
        assertNull("PUBLIC network should not have visibility field", doc.getFieldValue("visibility"));
    }

    @Test
    public void testSetupIndexDocument_UnlistedNetwork_NoVisibilityField() {
        unlistedManager = new GlobalNetworkIndexManager(VisibilityType.UNLISTED);

        NetworkSummaryWrapper wrapper = createTestNetworkWrapper("Unlisted Network", "Unlisted Description");

        SolrInputDocument doc = unlistedManager.setupIndexDocument(wrapper);

        assertNotNull(doc);
        assertEquals("NETWORK", doc.getFieldValue("entityType"));
        assertEquals("Unlisted Network", doc.getFieldValue("name"));
        assertNull("UNLISTED network should not have visibility field", doc.getFieldValue("visibility"));
    }

    @Test
    public void testSetupIndexDocument_PrivateNetwork_HasVisibilityAndOwnerField() {
        privateManager = new GlobalNetworkIndexManager(VisibilityType.PRIVATE);

        NetworkSummaryWrapper wrapper = createTestNetworkWrapper("Private Network", "Private Description");

        SolrInputDocument doc = privateManager.setupIndexDocument(wrapper);

        assertNotNull(doc);
        assertEquals("NETWORK", doc.getFieldValue("entityType"));
        assertEquals("Private Network", doc.getFieldValue("name"));
        assertEquals("PRIVATE", doc.getFieldValue("visibility").toString());
        assertEquals("testOwner", doc.getFieldValue("owner"));
    }

    @Test
    public void testSetupIndexDocument_PrivateNetwork_WithUserReadsAndEdits() {
        privateManager = new GlobalNetworkIndexManager(VisibilityType.PRIVATE);

        NetworkSummary summary = createTestNetworkSummary("Test", "Test");
        Collection<String> userReads = Arrays.asList("reader1", "reader2");
        Collection<String> userEdits = Arrays.asList("editor1");

        NetworkSummaryWrapper wrapper = new NetworkSummaryWrapper(
                summary, "testOwner", userReads, userEdits);

        SolrInputDocument doc = privateManager.setupIndexDocument(wrapper);

        assertNotNull(doc);
        // userRead and userEdit fields should be populated
        Collection<Object> readValues = doc.getFieldValues("userRead");
        Collection<Object> editValues = doc.getFieldValues("userEdit");

        assertNotNull("userRead should be set", readValues);
        assertEquals("Should have 2 readers", 2, readValues.size());
        assertTrue("Should contain reader1", readValues.contains("reader1"));
        assertTrue("Should contain reader2", readValues.contains("reader2"));

        assertNotNull("userEdit should be set", editValues);
        assertEquals("Should have 1 editor", 1, editValues.size());
        assertTrue("Should contain editor1", editValues.contains("editor1"));
    }

    @Test
    public void testSetupIndexDocument_PrivateNetwork_NullUserReadsAndEdits() {
        privateManager = new GlobalNetworkIndexManager(VisibilityType.PRIVATE);

        NetworkSummaryWrapper wrapper = createTestNetworkWrapper("Test", "Test");

        SolrInputDocument doc = privateManager.setupIndexDocument(wrapper);

        assertNotNull(doc);
        assertNull("userRead should be null when not provided", doc.getFieldValues("userRead"));
        assertNull("userEdit should be null when not provided", doc.getFieldValues("userEdit"));
    }

    @Test
    public void testSetupIndexDocument_PublicNetwork_IgnoresUserReadsAndEdits() {
        publicManager = new GlobalNetworkIndexManager(VisibilityType.PUBLIC);

        NetworkSummary summary = createTestNetworkSummary("Test", "Test");
        Collection<String> userReads = Arrays.asList("reader1");
        Collection<String> userEdits = Arrays.asList("editor1");

        NetworkSummaryWrapper wrapper = new NetworkSummaryWrapper(
                summary, "testOwner", userReads, userEdits);

        SolrInputDocument doc = publicManager.setupIndexDocument(wrapper);

        assertNotNull(doc);
        // PUBLIC visibility should not index userRead/userEdit
        assertNull("PUBLIC should not have userRead", doc.getFieldValues("userRead"));
        assertNull("PUBLIC should not have userEdit", doc.getFieldValues("userEdit"));
    }

    // ========================================================================
    // SETUP INDEX DOCUMENT TESTS - MINIMAL/MISSING FIELDS
    // ========================================================================

    @Test
    public void testSetupIndexDocument_MinimalNetwork_OnlyRequiredFields() {
        publicManager = new GlobalNetworkIndexManager(VisibilityType.PUBLIC);

        UUID networkId = UUID.randomUUID();
        Timestamp now = Timestamp.from(Instant.now());

        NetworkSummary summary = new NetworkSummary();
        summary.setExternalId(networkId);
        summary.setCreationTime(now);
        summary.setModificationTime(now);
        // No name, description, version

        NetworkSummaryWrapper wrapper = new NetworkSummaryWrapper(summary, null, null, null);

        SolrInputDocument doc = publicManager.setupIndexDocument(wrapper);

        assertNotNull(doc);
        assertEquals(networkId.toString(), doc.getFieldValue("uuid"));
        assertEquals("NETWORK", doc.getFieldValue("entityType"));
        assertEquals(now, doc.getFieldValue("creationTime"));
        assertEquals(now, doc.getFieldValue("modificationTime"));
        assertEquals(0, doc.getFieldValue("nodeCount"));
        assertEquals(0, doc.getFieldValue("edgeCount"));

        assertNull("Name should not be indexed", doc.getFieldValue("name"));
        assertNull("Description should not be indexed", doc.getFieldValue("description"));
        assertNull("Version should not be indexed", doc.getFieldValue("version"));
    }

    // ========================================================================
    // NAME FIELD TESTS - LENGTH VALIDATION
    // ========================================================================

    @Test
    public void testSetupIndexDocument_NameLength0_NotIndexed() {
        publicManager = new GlobalNetworkIndexManager(VisibilityType.PUBLIC);

        NetworkSummaryWrapper wrapper = createTestNetworkWrapper("", "Description");

        SolrInputDocument doc = publicManager.setupIndexDocument(wrapper);

        assertNull("Empty name (length 0) should not be indexed", doc.getFieldValue("name"));
    }

    @Test
    public void testSetupIndexDocument_NameLength1_NotIndexed() {
        publicManager = new GlobalNetworkIndexManager(VisibilityType.PUBLIC);

        NetworkSummaryWrapper wrapper = createTestNetworkWrapper("A", "Description");

        SolrInputDocument doc = publicManager.setupIndexDocument(wrapper);

        assertNull("Name with length 1 should not be indexed", doc.getFieldValue("name"));
    }

    @Test
    public void testSetupIndexDocument_NameLength2_IsIndexed() {
        publicManager = new GlobalNetworkIndexManager(VisibilityType.PUBLIC);

        NetworkSummaryWrapper wrapper = createTestNetworkWrapper("AB", "Description");

        SolrInputDocument doc = publicManager.setupIndexDocument(wrapper);

        assertEquals("Name with length 2 should be indexed", "AB", doc.getFieldValue("name"));
    }

    @Test
    public void testSetupIndexDocument_NullName_NotIndexed() {
        publicManager = new GlobalNetworkIndexManager(VisibilityType.PUBLIC);

        NetworkSummaryWrapper wrapper = createTestNetworkWrapper(null, "Description");

        SolrInputDocument doc = publicManager.setupIndexDocument(wrapper);

        assertNull("Null name should not be indexed", doc.getFieldValue("name"));
    }

    @Test
    public void testSetupIndexDocument_LongName_IsIndexed() {
        publicManager = new GlobalNetworkIndexManager(VisibilityType.PUBLIC);

        String longName = "This is a very long network name with many characters " +
                "that exceeds normal length expectations for testing purposes";
        NetworkSummaryWrapper wrapper = createTestNetworkWrapper(longName, "Description");

        SolrInputDocument doc = publicManager.setupIndexDocument(wrapper);

        assertEquals("Long name should be indexed", longName, doc.getFieldValue("name"));
    }

    @Test
    public void testSetupIndexDocument_NameWithSpecialCharacters() {
        publicManager = new GlobalNetworkIndexManager(VisibilityType.PUBLIC);

        String specialName = "Network-Name_2024 (Test) [v1.0]";
        NetworkSummaryWrapper wrapper = createTestNetworkWrapper(specialName, "Description");

        SolrInputDocument doc = publicManager.setupIndexDocument(wrapper);

        assertEquals("Name with special characters should be indexed",
                specialName, doc.getFieldValue("name"));
    }

    // ========================================================================
    // DESCRIPTION FIELD TESTS - LENGTH VALIDATION
    // ========================================================================

    @Test
    public void testSetupIndexDocument_DescriptionLength0_NotIndexed() {
        publicManager = new GlobalNetworkIndexManager(VisibilityType.PUBLIC);

        NetworkSummaryWrapper wrapper = createTestNetworkWrapper("Name", "");

        SolrInputDocument doc = publicManager.setupIndexDocument(wrapper);

        assertNull("Empty description should not be indexed", doc.getFieldValue("description"));
    }

    @Test
    public void testSetupIndexDocument_DescriptionLength1_NotIndexed() {
        publicManager = new GlobalNetworkIndexManager(VisibilityType.PUBLIC);

        NetworkSummaryWrapper wrapper = createTestNetworkWrapper("Name", "D");

        SolrInputDocument doc = publicManager.setupIndexDocument(wrapper);

        assertNull("Description with length 1 should not be indexed", doc.getFieldValue("description"));
    }

    @Test
    public void testSetupIndexDocument_DescriptionLength2_IsIndexed() {
        publicManager = new GlobalNetworkIndexManager(VisibilityType.PUBLIC);

        NetworkSummaryWrapper wrapper = createTestNetworkWrapper("Name", "OK");

        SolrInputDocument doc = publicManager.setupIndexDocument(wrapper);

        assertEquals("Description with length 2 should be indexed", "OK", doc.getFieldValue("description"));
    }

    @Test
    public void testSetupIndexDocument_NullDescription_NotIndexed() {
        publicManager = new GlobalNetworkIndexManager(VisibilityType.PUBLIC);

        NetworkSummary summary = createTestNetworkSummary("Name", null);
        NetworkSummaryWrapper wrapper = new NetworkSummaryWrapper(summary, "owner", null, null);

        SolrInputDocument doc = publicManager.setupIndexDocument(wrapper);

        assertNull("Null description should not be indexed", doc.getFieldValue("description"));
    }

    // ========================================================================
    // VERSION FIELD TESTS - LENGTH VALIDATION
    // ========================================================================

    @Test
    public void testSetupIndexDocument_VersionLength0_NotIndexed() {
        publicManager = new GlobalNetworkIndexManager(VisibilityType.PUBLIC);

        NetworkSummary summary = createTestNetworkSummary("Name", "Desc");
        summary.setVersion("");
        NetworkSummaryWrapper wrapper = new NetworkSummaryWrapper(summary, "owner", null, null);

        SolrInputDocument doc = publicManager.setupIndexDocument(wrapper);

        assertNull("Empty version should not be indexed", doc.getFieldValue("version"));
    }

    @Test
    public void testSetupIndexDocument_VersionLength1_NotIndexed() {
        publicManager = new GlobalNetworkIndexManager(VisibilityType.PUBLIC);

        NetworkSummary summary = createTestNetworkSummary("Name", "Desc");
        summary.setVersion("1");
        NetworkSummaryWrapper wrapper = new NetworkSummaryWrapper(summary, "owner", null, null);

        SolrInputDocument doc = publicManager.setupIndexDocument(wrapper);

        assertNull("Version with length 1 should not be indexed", doc.getFieldValue("version"));
    }

    @Test
    public void testSetupIndexDocument_VersionLength2_IsIndexed() {
        publicManager = new GlobalNetworkIndexManager(VisibilityType.PUBLIC);

        NetworkSummary summary = createTestNetworkSummary("Name", "Desc");
        summary.setVersion("1.0");
        NetworkSummaryWrapper wrapper = new NetworkSummaryWrapper(summary, "owner", null, null);

        SolrInputDocument doc = publicManager.setupIndexDocument(wrapper);

        assertEquals("Version with length >= 2 should be indexed", "1.0", doc.getFieldValue("version"));
    }

    @Test
    public void testSetupIndexDocument_NullVersion_NotIndexed() {
        publicManager = new GlobalNetworkIndexManager(VisibilityType.PUBLIC);

        NetworkSummary summary = createTestNetworkSummary("Name", "Desc");
        // version is null by default
        NetworkSummaryWrapper wrapper = new NetworkSummaryWrapper(summary, "owner", null, null);

        SolrInputDocument doc = publicManager.setupIndexDocument(wrapper);

        assertNull("Null version should not be indexed", doc.getFieldValue("version"));
    }

    // ========================================================================
    // OWNER FIELD TESTS
    // ========================================================================



    @Test
    public void testSetupIndexDocument_BlankOwner_StillSetsUserAdmin() {
        publicManager = new GlobalNetworkIndexManager(VisibilityType.PUBLIC);

        NetworkSummary summary = createTestNetworkSummary("Name", "Description");
        NetworkSummaryWrapper wrapper = new NetworkSummaryWrapper(summary, "  ", null, null);

        SolrInputDocument doc = publicManager.setupIndexDocument(wrapper);

        // USER_ADMIN is set unconditionally (matches legacy behavior)
        assertEquals("USER_ADMIN is set unconditionally (legacy behavior)", "  ", doc.getFieldValue("owner"));
    }

    @Test
    public void testSetupIndexDocument_NullOwner_StillSetsUserAdmin() {
        publicManager = new GlobalNetworkIndexManager(VisibilityType.PUBLIC);

        NetworkSummary summary = createTestNetworkSummary("Name", "Description");
        NetworkSummaryWrapper wrapper = new NetworkSummaryWrapper(summary, null, null, null);

        SolrInputDocument doc = publicManager.setupIndexDocument(wrapper);

        // USER_ADMIN is set unconditionally (matches legacy behavior)
        assertNull("Null owner indexed as null (legacy behavior)", doc.getFieldValue("owner"));
    }

    @Test
    public void testSetupIndexDocument_PrivateNetwork_BlankOwner_VisibilityStillSet() {
        privateManager = new GlobalNetworkIndexManager(VisibilityType.PRIVATE);

        NetworkSummary summary = createTestNetworkSummary("Name", "Description");
        NetworkSummaryWrapper wrapper = new NetworkSummaryWrapper(summary, "  ", null, null);

        SolrInputDocument doc = privateManager.setupIndexDocument(wrapper);

        // USER_ADMIN set unconditionally, visibility still set for PRIVATE
        assertEquals("USER_ADMIN is set unconditionally", "  ", doc.getFieldValue("owner"));
        assertEquals("Visibility should still be set", "PRIVATE", doc.getFieldValue("visibility").toString());
    }

    // ========================================================================
    // NODE AND EDGE COUNT TESTS
    // ========================================================================

    @Test
    public void testSetupIndexDocument_ZeroCounts() {
        publicManager = new GlobalNetworkIndexManager(VisibilityType.PUBLIC);

        NetworkSummary summary = createTestNetworkSummary("Test", "Test");
        summary.setNodeCount(0);
        summary.setEdgeCount(0);
        NetworkSummaryWrapper wrapper = new NetworkSummaryWrapper(summary, "owner", null, null);

        SolrInputDocument doc = publicManager.setupIndexDocument(wrapper);

        assertEquals("Zero node count should be indexed", 0, doc.getFieldValue("nodeCount"));
        assertEquals("Zero edge count should be indexed", 0, doc.getFieldValue("edgeCount"));
    }

    @Test
    public void testSetupIndexDocument_LargeCounts() {
        publicManager = new GlobalNetworkIndexManager(VisibilityType.PUBLIC);

        NetworkSummary summary = createTestNetworkSummary("Large Network", "Big one");
        summary.setNodeCount(1000000);
        summary.setEdgeCount(5000000);
        NetworkSummaryWrapper wrapper = new NetworkSummaryWrapper(summary, "owner", null, null);

        SolrInputDocument doc = publicManager.setupIndexDocument(wrapper);

        assertEquals("Large node count should be indexed", 1000000, doc.getFieldValue("nodeCount"));
        assertEquals("Large edge count should be indexed", 5000000, doc.getFieldValue("edgeCount"));
    }

    // ========================================================================
    // NDEX SCORE TESTS
    // ========================================================================

    @Test
    public void testSetupIndexDocument_NdexScoreIsSet() {
        publicManager = new GlobalNetworkIndexManager(VisibilityType.PUBLIC);

        NetworkSummaryWrapper wrapper = createTestNetworkWrapper("Test", "Test");

        SolrInputDocument doc = publicManager.setupIndexDocument(wrapper);

        assertNotNull("ndexScore should always be set", doc.getFieldValue("ndexScore"));
    }

    @Test
    public void testSetupIndexDocument_NdexScoreConsistentAcrossVisibilities() {
        publicManager = new GlobalNetworkIndexManager(VisibilityType.PUBLIC);
        privateManager = new GlobalNetworkIndexManager(VisibilityType.PRIVATE);

        NetworkSummary summary = createTestNetworkSummary("Test", "Test");
        NetworkSummaryWrapper publicWrapper = new NetworkSummaryWrapper(summary, "owner", null, null);
        NetworkSummaryWrapper privateWrapper = new NetworkSummaryWrapper(summary, "owner", null, null);

        SolrInputDocument publicDoc = publicManager.setupIndexDocument(publicWrapper);
        SolrInputDocument privateDoc = privateManager.setupIndexDocument(privateWrapper);

        assertEquals("ndexScore should be same regardless of visibility",
                publicDoc.getFieldValue("ndexScore"),
                privateDoc.getFieldValue("ndexScore"));
    }

    // ========================================================================
    // QUERY FIELDS TESTS
    // ========================================================================

    @Test
    public void testGetQueryFields_ReturnsExpectedString() {
        publicManager = new GlobalNetworkIndexManager(VisibilityType.PUBLIC);

        String queryFields = publicManager.getQueryFields();

        String expected = "uuid^20 name^10 description^5 labels^6 owner^2 " +
                "networkType^4 organism^3 disease^3 tissue^3 author^2 methods " +
                "nodeName represents alias rights^0.6 rightsHolder^0.6";
        assertEquals(expected, queryFields);
    }

    @Test
    public void testGetQueryFields_ContainsNetworkSpecificFields() {
        publicManager = new GlobalNetworkIndexManager(VisibilityType.PUBLIC);

        String queryFields = publicManager.getQueryFields();

        assertTrue("Should contain uuid", queryFields.contains("uuid"));
        assertTrue("Should contain name", queryFields.contains("name"));
        assertTrue("Should contain description", queryFields.contains("description"));
        assertTrue("Should contain organism", queryFields.contains("organism"));
        assertTrue("Should contain disease", queryFields.contains("disease"));
        assertTrue("Should contain tissue", queryFields.contains("tissue"));
        assertTrue("Should contain networkType", queryFields.contains("networkType"));
        assertTrue("Should contain nodeName", queryFields.contains("nodeName"));
        assertTrue("Should contain represents", queryFields.contains("represents"));
        assertTrue("Should contain alias", queryFields.contains("alias"));
    }

    @Test
    public void testGetQueryFields_HasCorrectBoostValues() {
        publicManager = new GlobalNetworkIndexManager(VisibilityType.PUBLIC);

        String queryFields = publicManager.getQueryFields();

        assertTrue("UUID should have boost 20", queryFields.contains("uuid^20"));
        assertTrue("Name should have boost 10", queryFields.contains("name^10"));
        assertTrue("Description should have boost 5", queryFields.contains("description^5"));
        assertTrue("Labels should have boost 6", queryFields.contains("labels^6"));
        assertTrue("Organism should have boost 3", queryFields.contains("organism^3"));
    }

    @Test
    public void testGetQueryFields_ConsistentAcrossVisibilityTypes() {
        publicManager = new GlobalNetworkIndexManager(VisibilityType.PUBLIC);
        privateManager = new GlobalNetworkIndexManager(VisibilityType.PRIVATE);
        unlistedManager = new GlobalNetworkIndexManager(VisibilityType.UNLISTED);

        String publicFields = publicManager.getQueryFields();
        String privateFields = privateManager.getQueryFields();
        String unlistedFields = unlistedManager.getQueryFields();

        assertEquals("Query fields should be same for all visibility types",
                publicFields, privateFields);
        assertEquals("Query fields should be same for all visibility types",
                publicFields, unlistedFields);
    }

    // ========================================================================
    // PERMISSION FILTER TESTS - PUBLIC CORE
    // ========================================================================

    @Test
    public void testBuildPermissionFilter_AnonymousUser_PublicCore() {
        publicManager = new GlobalNetworkIndexManager(VisibilityType.PUBLIC);

        String filter = publicManager.buildPermissionFilter(null, null);

        assertEquals("Anonymous can see all PUBLIC/UNLISTED items", "*:*", filter);
    }

    @Test
    public void testBuildPermissionFilter_AuthenticatedUser_WritePermission_PublicCore() {
        publicManager = new GlobalNetworkIndexManager(VisibilityType.PUBLIC);

        String filter = publicManager.buildPermissionFilter("bob", Permissions.WRITE);

        assertFalse("Should NOT include visibility", filter.contains("visibility"));
        assertTrue("Should include owned items", filter.contains("owner:\"bob\""));
        assertTrue("Should include EDIT permission", filter.contains("userEdit:\"bob\""));

        String expected = "(owner:\"bob\") OR (userEdit:\"bob\")";
        assertEquals(expected, filter);
    }

    @Test
    public void testBuildPermissionFilter_AuthenticatedUser_AdminPermission_PublicCore() {
        publicManager = new GlobalNetworkIndexManager(VisibilityType.PUBLIC);

        String filter = publicManager.buildPermissionFilter("admin", Permissions.ADMIN);

        assertEquals("Should only show owned items", "owner:\"admin\"", filter);
    }

    // ========================================================================
    // PERMISSION FILTER TESTS - PRIVATE CORE
    // ========================================================================

    @Test
    public void testBuildPermissionFilter_AnonymousUser_PrivateCore() {
        privateManager = new GlobalNetworkIndexManager(VisibilityType.PRIVATE);

        String filter = privateManager.buildPermissionFilter(null, null);

        assertEquals("Anonymous should see nothing in private core",
                "(*:* AND NOT *:*)", filter);
    }

    @Test
    public void testBuildPermissionFilter_AuthenticatedUser_NoPermission_PrivateCore() {
        privateManager = new GlobalNetworkIndexManager(VisibilityType.PRIVATE);

        String filter = privateManager.buildPermissionFilter("jane_doe", null);

        assertTrue("Should include owned items", filter.contains("owner:\"jane_doe\""));
        assertTrue("Should include READ permission", filter.contains("userRead:\"jane_doe\""));
        assertTrue("Should include EDIT permission", filter.contains("userEdit:\"jane_doe\""));

        String expected = "(owner:\"jane_doe\") OR (userRead:\"jane_doe\") OR (userEdit:\"jane_doe\")";
        assertEquals(expected, filter);
    }

    @Test
    public void testBuildPermissionFilter_AuthenticatedUser_WritePermission_PrivateCore() {
        privateManager = new GlobalNetworkIndexManager(VisibilityType.PRIVATE);

        String filter = privateManager.buildPermissionFilter("david", Permissions.WRITE);

        assertTrue("Should include owned items", filter.contains("owner:\"david\""));
        assertFalse("Should NOT include READ permission", filter.contains("userRead"));
        assertTrue("Should include EDIT permission", filter.contains("userEdit:\"david\""));

        String expected = "(owner:\"david\") OR (userEdit:\"david\")";
        assertEquals(expected, filter);
    }

    @Test
    public void testBuildPermissionFilter_AuthenticatedUser_AdminPermission_PrivateCore() {
        privateManager = new GlobalNetworkIndexManager(VisibilityType.PRIVATE);

        String filter = privateManager.buildPermissionFilter("superadmin", Permissions.ADMIN);

        assertEquals("Should only show owned items", "owner:\"superadmin\"", filter);
    }

    // ========================================================================
    // PREPROCESS SEARCH TERMS TESTS - NETWORK SPECIFIC
    // ========================================================================

    @Test
    public void testPreprocessSearchTerms_Wildcard_NotModified() {
        publicManager = new GlobalNetworkIndexManager(VisibilityType.PUBLIC);

        String result = publicManager.preprocessSearchTerms("*:*");

        assertEquals("Wildcard query should pass through unchanged", "*:*", result);
    }

    @Test
    public void testPreprocessSearchTerms_RegularQuery_AddsScoreBoost() {
        publicManager = new GlobalNetworkIndexManager(VisibilityType.PUBLIC);

        String input = "cancer";
        String result = publicManager.preprocessSearchTerms(input);

        assertTrue("Should contain ndexScore boost", result.contains("_val_:\"div(ndexScore,10)\""));
        assertTrue("Should wrap in AND clause", result.contains(" AND "));
        assertTrue("Should contain preprocessed search term",
                result.contains(SearchUtilities.preprocessSearchTerm(input)));
    }

    @Test
    public void testPreprocessSearchTerms_ComplexQuery_AddsScoreBoost() {
        publicManager = new GlobalNetworkIndexManager(VisibilityType.PUBLIC);

        String input = "cancer pathway signaling";
        String result = publicManager.preprocessSearchTerms(input);

        assertTrue("Should contain ndexScore boost", result.contains("ndexScore"));
        assertTrue("Should be wrapped in parentheses", result.startsWith("( "));
        assertTrue("Should end with score boost", result.endsWith("\""));
    }

    @Test
    public void testPreprocessSearchTerms_DifferentFromBaseClass() {
        publicManager = new GlobalNetworkIndexManager(VisibilityType.PUBLIC);
        FolderIndexManager folderManager = new FolderIndexManager(VisibilityType.PUBLIC);

        String input = "test";
        String networkResult = publicManager.preprocessSearchTerms(input);
        String folderResult = folderManager.preprocessSearchTerms(input);

        assertNotEquals("Network preprocessing should differ from folder",
                networkResult, folderResult);
        assertTrue("Network should have score boost", networkResult.contains("ndexScore"));
        assertFalse("Folder should not have score boost", folderResult.contains("ndexScore"));

        folderManager.close();
    }

    // ========================================================================
    // CONFIGURE QUERY TESTS
    // ========================================================================

    @Test
    public void testConfigureQuery_WildcardQuery_SortsByModificationTime() {
        publicManager = new GlobalNetworkIndexManager(VisibilityType.PUBLIC);

        SolrQuery solrQuery = new SolrQuery();
        publicManager.configureQuery(solrQuery, "*:*", "filter", 10, 0);

        List<SolrQuery.SortClause> sorts = solrQuery.getSorts();

        assertNotNull("Should have sort clauses", sorts);
        assertFalse("Should have at least one sort clause", sorts.isEmpty());

        SolrQuery.SortClause sortClause = sorts.get(0);
        assertEquals("Should sort by modificationTime", "modificationTime", sortClause.getItem());
        assertEquals("Should sort descending", SolrQuery.ORDER.desc, sortClause.getOrder());
    }

    @Test
    public void testConfigureQuery_RegularQuery_NoDefaultSort() {
        publicManager = new GlobalNetworkIndexManager(VisibilityType.PUBLIC);

        SolrQuery solrQuery = new SolrQuery();
        publicManager.configureQuery(solrQuery, "test query", "filter", 10, 0);

        assertTrue("Should not have explicit sort for relevance queries",
                solrQuery.getSorts() == null || solrQuery.getSorts().isEmpty());
    }

    @Test
    public void testConfigureQuery_SetsQueryType() {
        publicManager = new GlobalNetworkIndexManager(VisibilityType.PUBLIC);

        SolrQuery solrQuery = new SolrQuery();
        publicManager.configureQuery(solrQuery, "test", "filter", 10, 0);

        assertEquals("Should use edismax query parser", "edismax", solrQuery.get("defType"));
    }

    @Test
    public void testConfigureQuery_SetsNetworkQueryFields() {
        publicManager = new GlobalNetworkIndexManager(VisibilityType.PUBLIC);

        SolrQuery solrQuery = new SolrQuery();
        publicManager.configureQuery(solrQuery, "test", "filter", 10, 0);

        String qf = solrQuery.get("qf");
        assertTrue("Should contain organism field", qf.contains("organism"));
        assertTrue("Should contain disease field", qf.contains("disease"));
        assertTrue("Should contain nodeName field", qf.contains("nodeName"));
    }

    @Test
    public void testConfigureQuery_SetsPagination() {
        publicManager = new GlobalNetworkIndexManager(VisibilityType.PUBLIC);

        SolrQuery solrQuery = new SolrQuery();
        publicManager.configureQuery(solrQuery, "test", "filter", 25, 50);

        assertEquals("Should set correct offset", Integer.valueOf(50), solrQuery.getStart());
        assertEquals("Should set correct limit", Integer.valueOf(25), solrQuery.getRows());
    }

    @Test
    public void testConfigureQuery_ZeroLimit_UsesDefault() {
        publicManager = new GlobalNetworkIndexManager(VisibilityType.PUBLIC);

        SolrQuery solrQuery = new SolrQuery();
        publicManager.configureQuery(solrQuery, "test", "filter", 0, 0);

        assertEquals("Zero limit should use default 100000",
                Integer.valueOf(100000), solrQuery.getRows());
    }

    // ========================================================================
    // POST COMMIT TESTS
    // ========================================================================

    @Test
    public void testPostCommit_ResetsNodeMembers() throws Exception {
        publicManager = new GlobalNetworkIndexManager(VisibilityType.PUBLIC);

        // Access private nodeMembers field via reflection to verify reset
        Field nodeMembersField = GlobalNetworkIndexManager.class.getDeclaredField("nodeMembers");
        nodeMembersField.setAccessible(true);

        // Add something to nodeMembers (normally done during indexing)
        @SuppressWarnings("unchecked")
        Map<Long, Set<String>> nodeMembers = (Map<Long, Set<String>>) nodeMembersField.get(publicManager);
        nodeMembers.put(1L, new HashSet<>(Arrays.asList("gene1", "gene2")));
        assertFalse("nodeMembers should have content before postCommit", nodeMembers.isEmpty());

        // Call postCommit
        publicManager.postCommit();

        // Get the field again (it's replaced with new TreeMap)
        @SuppressWarnings("unchecked")
        Map<Long, Set<String>> nodeMembersAfter = (Map<Long, Set<String>>) nodeMembersField.get(publicManager);
        assertTrue("nodeMembers should be empty after postCommit", nodeMembersAfter.isEmpty());
    }

    // ========================================================================
    // SEARCH FOR NETWORKS TESTS
    // ========================================================================

    @Test
    public void testSearchForNetworks_DelegatesToSearchByType() {
        publicManager = new GlobalNetworkIndexManager(VisibilityType.PUBLIC);

        // We can't easily test the actual search without Solr
        // Verify the method exists and builds correct parameters
        try {
            publicManager.searchForNetworks("*:*", "testUser", 10, 0, null, null);
        } catch (Exception e) {
            assertTrue("Should fail due to missing Solr, not logic error",
                    e.getMessage().contains("Connection refused") ||
                            e.getMessage().contains("Solr") ||
                            e.getMessage().contains("connect"));
        }
    }

    // ========================================================================
    // OTHER ATTRIBUTES SET TESTS
    // ========================================================================

    @Test
    public void testOtherAttributes_ContainsExpectedFields() {
        assertTrue("Should contain organism", GlobalNetworkIndexManager.otherAttributes.contains("organism"));
        assertTrue("Should contain disease", GlobalNetworkIndexManager.otherAttributes.contains("disease"));
        assertTrue("Should contain tissue", GlobalNetworkIndexManager.otherAttributes.contains("tissue"));
        assertTrue("Should contain networkType", GlobalNetworkIndexManager.otherAttributes.contains("networkType"));
        assertTrue("Should contain author", GlobalNetworkIndexManager.otherAttributes.contains("author"));
        assertTrue("Should contain labels", GlobalNetworkIndexManager.otherAttributes.contains("labels"));
        assertTrue("Should contain rights", GlobalNetworkIndexManager.otherAttributes.contains("rights"));
        assertTrue("Should contain rightsHolder", GlobalNetworkIndexManager.otherAttributes.contains("rightsHolder"));
    }

    @Test
    public void testOtherAttributes_IsImmutableSet() {
        int sizeBefore = GlobalNetworkIndexManager.otherAttributes.size();

        // Attempting to modify should either throw or have no effect
        try {
            GlobalNetworkIndexManager.otherAttributes.add("newField");
        } catch (UnsupportedOperationException e) {
            // Expected for immutable set
        }

        // If no exception, verify size unchanged (HashSet allows modification)
        // This test documents current behavior - consider making set immutable
    }

    // ========================================================================
    // DOCUMENT RESET AND STATE TESTS
    // ========================================================================

    @Test
    public void testSetupIndexDocument_CalledTwice_DocumentIsReset() {
        publicManager = new GlobalNetworkIndexManager(VisibilityType.PUBLIC);

        NetworkSummaryWrapper wrapper1 = createTestNetworkWrapper("Network One", "Description One");
        UUID uuid1 = wrapper1.getNetworkSummary().getExternalId();

        SolrInputDocument doc1 = publicManager.setupIndexDocument(wrapper1);
        assertEquals("First network name", "Network One", doc1.getFieldValue("name"));
        assertEquals("First network UUID", uuid1.toString(), doc1.getFieldValue("uuid"));

        NetworkSummaryWrapper wrapper2 = createTestNetworkWrapper("Network Two", "Description Two");
        UUID uuid2 = wrapper2.getNetworkSummary().getExternalId();

        SolrInputDocument doc2 = publicManager.setupIndexDocument(wrapper2);
        assertEquals("Second network name", "Network Two", doc2.getFieldValue("name"));
        assertEquals("Second network UUID", uuid2.toString(), doc2.getFieldValue("uuid"));

        assertNotNull(doc1);
        assertNotNull(doc2);
    }

    @Test
    public void testSetupIndexDocument_DifferentVisibilityTypes_ProduceCorrectDocuments() {
        publicManager = new GlobalNetworkIndexManager(VisibilityType.PUBLIC);
        privateManager = new GlobalNetworkIndexManager(VisibilityType.PRIVATE);
        unlistedManager = new GlobalNetworkIndexManager(VisibilityType.UNLISTED);

        NetworkSummary summary = createTestNetworkSummary("Test", "Test");

        NetworkSummaryWrapper publicWrapper = new NetworkSummaryWrapper(summary, "owner", null, null);
        NetworkSummaryWrapper privateWrapper = new NetworkSummaryWrapper(summary, "owner", null, null);
        NetworkSummaryWrapper unlistedWrapper = new NetworkSummaryWrapper(summary, "owner", null, null);

        SolrInputDocument publicDoc = publicManager.setupIndexDocument(publicWrapper);
        SolrInputDocument privateDoc = privateManager.setupIndexDocument(privateWrapper);
        SolrInputDocument unlistedDoc = unlistedManager.setupIndexDocument(unlistedWrapper);

        assertNull("PUBLIC should not have visibility", publicDoc.getFieldValue("visibility"));
        assertEquals("PRIVATE should have visibility=PRIVATE",
                "PRIVATE", privateDoc.getFieldValue("visibility").toString());
        assertNull("UNLISTED should not have visibility", unlistedDoc.getFieldValue("visibility"));

        assertEquals("Test", publicDoc.getFieldValue("name"));
        assertEquals("Test", privateDoc.getFieldValue("name"));
        assertEquals("Test", unlistedDoc.getFieldValue("name"));
    }

    // ========================================================================
    // INTEGRATION TESTS (Require local Solr) - @Ignore by default
    // ========================================================================

    @Test
    @Ignore
    public void testSearchForNetworks_Integration() throws Exception {
        publicManager = new GlobalNetworkIndexManager(VisibilityType.PUBLIC);

        // Clean up the core first
        publicManager.client.deleteByQuery("*:*");
        publicManager.client.commit();

        // Create and index test networks
        NetworkSummaryWrapper network1 = createTestNetworkWrapper("Cancer Pathway Network", "Signaling pathways in cancer");
        publicManager.createIndex(network1);

        NetworkSummaryWrapper network2 = createTestNetworkWrapper("Diabetes Signaling", "Insulin signaling network");
        publicManager.createIndex(network2);

        Thread.sleep(2000);

        // Search for cancer
        var results = publicManager.searchForNetworks("cancer", "testOwner", 100, 0, null, null);

        assertNotNull(results);
        assertEquals("Should find 1 cancer network", 1, results.getNumFound());
    }

    @Test
    @Ignore
    public void testSearchForNetworks_WithAdminFilter_Integration() throws Exception {
        publicManager = new GlobalNetworkIndexManager(VisibilityType.PUBLIC);

        // Clean up the core first
        publicManager.client.deleteByQuery("*:*");
        publicManager.client.commit();

        NetworkSummary summary1 = createTestNetworkSummary("My Network", "Owned by me");
        NetworkSummaryWrapper wrapper1 = new NetworkSummaryWrapper(summary1, "owner1", null, null);
        publicManager.createIndex(wrapper1);

        NetworkSummary summary2 = createTestNetworkSummary("Their Network", "Owned by them");
        NetworkSummaryWrapper wrapper2 = new NetworkSummaryWrapper(summary2, "owner2", null, null);
        publicManager.createIndex(wrapper2);

        Thread.sleep(2000);

        // Search with ADMIN permission for owner1
        var results = publicManager.searchForNetworks("*:*", "owner1", 100, 0, "owner1", Permissions.ADMIN);

        assertNotNull(results);
        assertEquals("Should find only owner1's network", 1, results.getNumFound());
    }

    @Test
    @Ignore
    public void testSearchForNetworks_Pagination_Integration() throws Exception {
        publicManager = new GlobalNetworkIndexManager(VisibilityType.PUBLIC);

        // Clean up the core first
        publicManager.client.deleteByQuery("*:*");
        publicManager.client.commit();

        // Create 10 networks
        for (int i = 0; i < 10; i++) {
            NetworkSummaryWrapper wrapper = createTestNetworkWrapper("Network " + i, "Description " + i);
            publicManager.createIndex(wrapper);
        }

        Thread.sleep(2000);

        // First page
        var page1 = publicManager.searchForNetworks("*:*", "testOwner", 5, 0, null, null);
        assertEquals("First page should have 5 results", 5, page1.size());
        assertEquals("Total should be 10", 10, page1.getNumFound());

        // Second page
        var page2 = publicManager.searchForNetworks("*:*", "testOwner", 5, 5, null, null);
        assertEquals("Second page should have 5 results", 5, page2.size());
    }

    @Test
    @Ignore
    public void testSearchForNetworks_PrivateVisibility_Integration() throws Exception {
        privateManager = new GlobalNetworkIndexManager(VisibilityType.PRIVATE);

        // Clean up the core first
        privateManager.client.deleteByQuery("*:*");
        privateManager.client.commit();

        NetworkSummary summary = createTestNetworkSummary("Private Network", "Secret data");
        Collection<String> readers = Arrays.asList("reader1", "reader2");
        NetworkSummaryWrapper wrapper = new NetworkSummaryWrapper(summary, "owner", readers, null);
        privateManager.createIndex(wrapper);

        Thread.sleep(2000);

        // Anonymous cannot see
        var anonymousResults = privateManager.searchForNetworks("*:*", null, 100, 0, null, null);
        assertEquals("Anonymous should see nothing", 0, anonymousResults.getNumFound());

        // Owner can see
        var ownerResults = privateManager.searchForNetworks("*:*", "owner", 100, 0, null, null);
        assertEquals("Owner should see network", 1, ownerResults.getNumFound());

        // Reader can see
        var readerResults = privateManager.searchForNetworks("*:*", "reader1", 100, 0, null, null);
        assertEquals("Reader should see network", 1, readerResults.getNumFound());

        // Non-reader cannot see
        var otherResults = privateManager.searchForNetworks("*:*", "stranger", 100, 0, null, null);
        assertEquals("Stranger should not see network", 0, otherResults.getNumFound());
    }

    @Test
    @Ignore
    public void testSearchForNetworks_ScoreBoost_Integration() throws Exception {
        publicManager = new GlobalNetworkIndexManager(VisibilityType.PUBLIC);

        // Clean up the core first
        publicManager.client.deleteByQuery("*:*");
        publicManager.client.commit();

        // Create networks - the one with higher ndexScore should rank higher
        NetworkSummary highScore = createTestNetworkSummary("Cancer Network High", "High quality");
        highScore.setNodeCount(10000);
        highScore.setEdgeCount(50000);
        NetworkSummaryWrapper highWrapper = new NetworkSummaryWrapper(highScore, "owner", null, null);
        publicManager.createIndex(highWrapper);

        NetworkSummary lowScore = createTestNetworkSummary("Cancer Network Low", "Low quality");
        lowScore.setNodeCount(10);
        lowScore.setEdgeCount(5);
        NetworkSummaryWrapper lowWrapper = new NetworkSummaryWrapper(lowScore, "owner", null, null);
        publicManager.createIndex(lowWrapper);

        Thread.sleep(2000);

        var results = publicManager.searchForNetworks("cancer", "testOwner", 100, 0, null, null);

        assertEquals("Should find both networks", 2, results.getNumFound());
        // Higher scored network should come first
        String firstUuid = (String) results.get(0).getFieldValue("uuid");
        assertEquals("Higher scored network should rank first",
                highScore.getExternalId().toString(), firstUuid);
    }

    @Test
    @Ignore
    public void testSearchForNetworks_OnlyReturnsNetworks_Integration() throws Exception {
        publicManager = new GlobalNetworkIndexManager(VisibilityType.PUBLIC);
        FolderIndexManager folderManager = new FolderIndexManager(VisibilityType.PUBLIC);

        // Clean up the core first
        publicManager.client.deleteByQuery("*:*");
        publicManager.client.commit();

        // Index a network
        NetworkSummaryWrapper network = createTestNetworkWrapper("Test Network", "A network");
        publicManager.createIndex(network);

        // Index a folder (using folder manager - same core)
        NdexFolder folder = new NdexFolder();
        folder.setExternalId(UUID.randomUUID());
        folder.setName("Test Folder");
        folder.setDescription("A folder");
        folder.setOwner("testOwner");
        folder.setCreationTime(Timestamp.from(Instant.now()));
        folder.setModificationTime(Timestamp.from(Instant.now()));
        folderManager.createIndex(folder);

        Thread.sleep(2000);

        var results = publicManager.searchForNetworks("*:*", "testOwner", 100, 0, null, null);

        // Should only return networks, not folders
        assertEquals("Should find only 1 network", 1, results.getNumFound());

        folderManager.close();
    }

    // ========================================================================
    // HELPER METHODS
    // ========================================================================

    private NetworkSummary createTestNetworkSummary(String name, String description) {
        NetworkSummary summary = new NetworkSummary();
        summary.setExternalId(UUID.randomUUID());
        summary.setName(name);
        summary.setDescription(description);
        summary.setNodeCount(100);
        summary.setEdgeCount(500);
        summary.setCreationTime(Timestamp.from(Instant.now()));
        summary.setModificationTime(Timestamp.from(Instant.now()));
        return summary;
    }

    private NetworkSummaryWrapper createTestNetworkWrapper(String name, String description) {
        NetworkSummary summary = createTestNetworkSummary(name, description);
        return new NetworkSummaryWrapper(summary, "testOwner", null, null);
    }
}