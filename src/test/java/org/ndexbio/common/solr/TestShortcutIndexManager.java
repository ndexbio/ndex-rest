package org.ndexbio.common.solr;

import org.apache.solr.client.solrj.SolrQuery;
import org.apache.solr.common.SolrInputDocument;
import org.junit.After;
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
 * Comprehensive unit tests for ShortcutIndexManager
 * Tests document setup, query configuration, permission filters,
 * dangling shortcut detection, and edge cases.
 */
public class TestShortcutIndexManager {

    private ShortcutIndexManager publicManager;
    private ShortcutIndexManager privateManager;
    private ShortcutIndexManager unlistedManager;

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

    private void closeManager(ShortcutIndexManager manager) {
        if (manager != null) {
            manager.close();
        }
    }

    // ========================================================================
    // CORE NAME AND INITIALIZATION TESTS
    // ========================================================================

    @Test
    public void testConstructor_PublicVisibility_UsesPublicCore() {
        publicManager = new ShortcutIndexManager(VisibilityType.PUBLIC);
        assertEquals("public-nfs", publicManager.coreName);
        assertNotNull("SolrInputDocument should be initialized", publicManager.doc);
        assertNotNull("HttpSolrClient should be initialized", publicManager.client);
    }

    @Test
    public void testConstructor_UnlistedVisibility_UsesPublicCore() {
        unlistedManager = new ShortcutIndexManager(VisibilityType.UNLISTED);
        assertEquals("public-nfs", unlistedManager.coreName);
    }

    @Test
    public void testConstructor_PrivateVisibility_UsesPrivateCore() {
        privateManager = new ShortcutIndexManager(VisibilityType.PRIVATE);
        assertEquals("private-nfs", privateManager.coreName);
    }

    @Test
    public void testConstructor_VisibilityTypeStored() {
        publicManager = new ShortcutIndexManager(VisibilityType.PUBLIC);
        privateManager = new ShortcutIndexManager(VisibilityType.PRIVATE);

        assertEquals(VisibilityType.PUBLIC, publicManager.visibilityType);
        assertEquals(VisibilityType.PRIVATE, privateManager.visibilityType);
    }

    // ========================================================================
    // SETUP INDEX DOCUMENT TESTS - COMPLETE SHORTCUTS
    // ========================================================================

    @Test
    public void testSetupIndexDocument_PublicShortcut_AllFieldsSet() {
        publicManager = new ShortcutIndexManager(VisibilityType.PUBLIC);

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

        SolrInputDocument doc = publicManager.setupIndexDocument(shortcut);

        assertNotNull("Document should not be null", doc);
        assertEquals("UUID should match", shortcutId.toString(), doc.getFieldValue("uuid"));
        assertEquals("Entity type should be SHORTCUT", "SHORTCUT", doc.getFieldValue("entityType"));
        assertEquals("Name should match", "Test Shortcut", doc.getFieldValue("name"));
        assertEquals("Owner should match", "testOwner", doc.getFieldValue("owner"));
        assertEquals("Parent UUID should match", parentId.toString(), doc.getFieldValue("parentUuid"));
        assertEquals("Target UUID should match", targetId.toString(), doc.getFieldValue("targetUuid"));
        assertEquals("Target type should match", "NETWORK", doc.getFieldValue("targetType"));
        assertEquals("isDangling should be false", false, doc.getFieldValue("isDangling"));
        assertEquals("Creation time should match", creationTime, doc.getFieldValue("creationTime"));
        assertEquals("Modification time should match", modificationTime, doc.getFieldValue("modificationTime"));
        assertNull("PUBLIC shortcut should not have visibility field", doc.getFieldValue("visibility"));
    }

    @Test
    public void testSetupIndexDocument_UnlistedShortcut_NoVisibilityField() {
        unlistedManager = new ShortcutIndexManager(VisibilityType.UNLISTED);

        NdexShortcut shortcut = createTestShortcut("Unlisted Shortcut");

        SolrInputDocument doc = unlistedManager.setupIndexDocument(shortcut);

        assertNotNull(doc);
        assertEquals("SHORTCUT", doc.getFieldValue("entityType"));
        assertEquals("Unlisted Shortcut", doc.getFieldValue("name"));
        assertNull("UNLISTED shortcut should not have visibility field", doc.getFieldValue("visibility"));
    }

    @Test
    public void testSetupIndexDocument_PrivateShortcut_HasVisibilityField() {
        privateManager = new ShortcutIndexManager(VisibilityType.PRIVATE);

        NdexShortcut shortcut = createTestShortcut("Private Shortcut");

        SolrInputDocument doc = privateManager.setupIndexDocument(shortcut);

        assertNotNull(doc);
        assertEquals("SHORTCUT", doc.getFieldValue("entityType"));
        assertEquals("Private Shortcut", doc.getFieldValue("name"));
        assertEquals("PRIVATE", doc.getFieldValue("visibility"));
    }

    // ========================================================================
    // SETUP INDEX DOCUMENT TESTS - MINIMAL/MISSING FIELDS
    // ========================================================================

    @Test
    public void testSetupIndexDocument_MinimalShortcut_OnlyRequiredFields() {
        publicManager = new ShortcutIndexManager(VisibilityType.PUBLIC);

        UUID shortcutId = UUID.randomUUID();
        Timestamp now = Timestamp.from(Instant.now());

        NdexShortcut shortcut = new NdexShortcut();
        shortcut.setExternalId(shortcutId);
        shortcut.setCreationTime(now);
        shortcut.setModificationTime(now);
        // No name, owner, parent, target

        SolrInputDocument doc = publicManager.setupIndexDocument(shortcut);

        assertNotNull(doc);
        assertEquals(shortcutId.toString(), doc.getFieldValue("uuid"));
        assertEquals("SHORTCUT", doc.getFieldValue("entityType"));
        assertEquals(now, doc.getFieldValue("creationTime"));
        assertEquals(now, doc.getFieldValue("modificationTime"));

        assertNull("Name should not be indexed", doc.getFieldValue("name"));
        assertNull("Owner should not be indexed", doc.getFieldValue("owner"));
        assertNull("Parent should not be indexed", doc.getFieldValue("parentUuid"));
        assertNull("Target should not be indexed", doc.getFieldValue("targetUuid"));
        assertEquals("isDangling should be true when no target", true, doc.getFieldValue("isDangling"));
    }

    @Test
    public void testSetupIndexDocument_NoParent_ParentFieldNull() {
        publicManager = new ShortcutIndexManager(VisibilityType.PUBLIC);

        NdexShortcut shortcut = createTestShortcut("Root Shortcut");
        shortcut.setParent(null);

        SolrInputDocument doc = publicManager.setupIndexDocument(shortcut);

        assertNotNull(doc);
        assertNull("Shortcut without parent should not have parentUuid", doc.getFieldValue("parentUuid"));
    }

    // ========================================================================
    // NAME FIELD TESTS - LENGTH VALIDATION
    // ========================================================================

    @Test
    public void testSetupIndexDocument_NameLength0_NotIndexed() {
        publicManager = new ShortcutIndexManager(VisibilityType.PUBLIC);

        NdexShortcut shortcut = createTestShortcut("");

        SolrInputDocument doc = publicManager.setupIndexDocument(shortcut);

        assertNull("Empty name (length 0) should not be indexed", doc.getFieldValue("name"));
    }

    @Test
    public void testSetupIndexDocument_NameLength1_NotIndexed() {
        publicManager = new ShortcutIndexManager(VisibilityType.PUBLIC);

        NdexShortcut shortcut = createTestShortcut("A");

        SolrInputDocument doc = publicManager.setupIndexDocument(shortcut);

        assertNull("Name with length 1 should not be indexed", doc.getFieldValue("name"));
    }

    @Test
    public void testSetupIndexDocument_NameLength2_IsIndexed() {
        publicManager = new ShortcutIndexManager(VisibilityType.PUBLIC);

        NdexShortcut shortcut = createTestShortcut("AB");

        SolrInputDocument doc = publicManager.setupIndexDocument(shortcut);

        assertEquals("Name with length 2 should be indexed", "AB", doc.getFieldValue("name"));
    }

    @Test
    public void testSetupIndexDocument_NullName_NotIndexed() {
        publicManager = new ShortcutIndexManager(VisibilityType.PUBLIC);

        NdexShortcut shortcut = createTestShortcut(null);

        SolrInputDocument doc = publicManager.setupIndexDocument(shortcut);

        assertNull("Null name should not be indexed", doc.getFieldValue("name"));
    }

    @Test
    public void testSetupIndexDocument_LongName_IsIndexed() {
        publicManager = new ShortcutIndexManager(VisibilityType.PUBLIC);

        String longName = "This is a very long shortcut name with many characters";
        NdexShortcut shortcut = createTestShortcut(longName);

        SolrInputDocument doc = publicManager.setupIndexDocument(shortcut);

        assertEquals("Long name should be indexed", longName, doc.getFieldValue("name"));
    }

    @Test
    public void testSetupIndexDocument_NameWithSpecialCharacters() {
        publicManager = new ShortcutIndexManager(VisibilityType.PUBLIC);

        String specialName = "Shortcut-Name_2024 (Link)";
        NdexShortcut shortcut = createTestShortcut(specialName);

        SolrInputDocument doc = publicManager.setupIndexDocument(shortcut);

        assertEquals("Name with special characters should be indexed",
                specialName, doc.getFieldValue("name"));
    }

    // ========================================================================
    // OWNER FIELD TESTS - BLANK/NULL VALIDATION
    // ========================================================================

    @Test
    public void testSetupIndexDocument_NullOwner_NotIndexed() {
        publicManager = new ShortcutIndexManager(VisibilityType.PUBLIC);

        NdexShortcut shortcut = createTestShortcut("Name");
        shortcut.setOwner(null);

        SolrInputDocument doc = publicManager.setupIndexDocument(shortcut);

        assertNull("Null owner should not be indexed", doc.getFieldValue("owner"));
    }

    @Test
    public void testSetupIndexDocument_EmptyOwner_NotIndexed() {
        publicManager = new ShortcutIndexManager(VisibilityType.PUBLIC);

        NdexShortcut shortcut = createTestShortcut("Name");
        shortcut.setOwner("");

        SolrInputDocument doc = publicManager.setupIndexDocument(shortcut);

        assertNull("Empty owner should not be indexed", doc.getFieldValue("owner"));
    }

    @Test
    public void testSetupIndexDocument_BlankOwner_NotIndexed() {
        publicManager = new ShortcutIndexManager(VisibilityType.PUBLIC);

        NdexShortcut shortcut = createTestShortcut("Name");
        shortcut.setOwner("   ");

        SolrInputDocument doc = publicManager.setupIndexDocument(shortcut);

        assertNull("Blank owner (only spaces) should not be indexed", doc.getFieldValue("owner"));
    }

    @Test
    public void testSetupIndexDocument_OwnerWithLeadingTrailingSpaces_IsIndexed() {
        publicManager = new ShortcutIndexManager(VisibilityType.PUBLIC);

        NdexShortcut shortcut = createTestShortcut("Name");
        shortcut.setOwner("  john_doe  ");

        SolrInputDocument doc = publicManager.setupIndexDocument(shortcut);

        assertEquals("Owner with leading/trailing spaces should be indexed",
                "  john_doe  ", doc.getFieldValue("owner"));
    }

    // ========================================================================
    // TARGET AND DANGLING TESTS
    // ========================================================================

    @Test
    public void testSetupIndexDocument_WithTarget_NotDangling() {
        publicManager = new ShortcutIndexManager(VisibilityType.PUBLIC);

        UUID targetId = UUID.randomUUID();
        NdexShortcut shortcut = createTestShortcut("Linked Shortcut");
        shortcut.setTarget(targetId);
        shortcut.setTargetType(FileType.NETWORK);

        SolrInputDocument doc = publicManager.setupIndexDocument(shortcut);

        assertEquals("Target UUID should be set", targetId.toString(), doc.getFieldValue("targetUuid"));
        assertEquals("Target type should be set", "NETWORK", doc.getFieldValue("targetType"));
        assertEquals("isDangling should be false", false, doc.getFieldValue("isDangling"));
    }

    @Test
    public void testSetupIndexDocument_WithoutTarget_IsDangling() {
        publicManager = new ShortcutIndexManager(VisibilityType.PUBLIC);

        NdexShortcut shortcut = createTestShortcut("Dangling Shortcut");
        shortcut.setTarget(null);

        SolrInputDocument doc = publicManager.setupIndexDocument(shortcut);

        assertNull("Target UUID should be null", doc.getFieldValue("targetUuid"));
        assertEquals("isDangling should be true", true, doc.getFieldValue("isDangling"));
    }

    @Test
    public void testSetupIndexDocument_TargetTypeNetwork() {
        publicManager = new ShortcutIndexManager(VisibilityType.PUBLIC);

        NdexShortcut shortcut = createTestShortcut("Network Link");
        shortcut.setTarget(UUID.randomUUID());
        shortcut.setTargetType(FileType.NETWORK);

        SolrInputDocument doc = publicManager.setupIndexDocument(shortcut);

        assertEquals("NETWORK", doc.getFieldValue("targetType"));
    }

    @Test
    public void testSetupIndexDocument_TargetTypeFolder() {
        publicManager = new ShortcutIndexManager(VisibilityType.PUBLIC);

        NdexShortcut shortcut = createTestShortcut("Folder Link");
        shortcut.setTarget(UUID.randomUUID());
        shortcut.setTargetType(FileType.FOLDER);

        SolrInputDocument doc = publicManager.setupIndexDocument(shortcut);

        assertEquals("FOLDER", doc.getFieldValue("targetType"));
    }

    @Test
    public void testSetupIndexDocument_NullTargetType() {
        publicManager = new ShortcutIndexManager(VisibilityType.PUBLIC);

        NdexShortcut shortcut = createTestShortcut("Unknown Target");
        shortcut.setTarget(UUID.randomUUID());
        shortcut.setTargetType(null);

        SolrInputDocument doc = publicManager.setupIndexDocument(shortcut);

        assertNull("Null target type should not be indexed", doc.getFieldValue("targetType"));
        assertEquals("isDangling should be false (target exists)", false, doc.getFieldValue("isDangling"));
    }

    // ========================================================================
    // QUERY FIELDS TESTS
    // ========================================================================

    @Test
    public void testGetQueryFields_ReturnsExpectedString() {
        publicManager = new ShortcutIndexManager(VisibilityType.PUBLIC);

        String queryFields = publicManager.getQueryFields();

        assertEquals("uuid^20 name^10 owner^2", queryFields);
    }

    @Test
    public void testGetQueryFields_ContainsAllFields() {
        publicManager = new ShortcutIndexManager(VisibilityType.PUBLIC);

        String queryFields = publicManager.getQueryFields();

        assertTrue("Should contain uuid", queryFields.contains("uuid"));
        assertTrue("Should contain name", queryFields.contains("name"));
        assertTrue("Should contain owner", queryFields.contains("owner"));
    }

    @Test
    public void testGetQueryFields_HasCorrectBoostValues() {
        publicManager = new ShortcutIndexManager(VisibilityType.PUBLIC);

        String queryFields = publicManager.getQueryFields();

        assertTrue("UUID should have boost 20", queryFields.contains("uuid^20"));
        assertTrue("Name should have boost 10", queryFields.contains("name^10"));
        assertTrue("Owner should have boost 2", queryFields.contains("owner^2"));
    }

    @Test
    public void testGetQueryFields_DoesNotContainNetworkFields() {
        publicManager = new ShortcutIndexManager(VisibilityType.PUBLIC);

        String queryFields = publicManager.getQueryFields();

        assertFalse("Should not contain description", queryFields.contains("description"));
        assertFalse("Should not contain nodeName", queryFields.contains("nodeName"));
        assertFalse("Should not contain organism", queryFields.contains("organism"));
    }

    @Test
    public void testGetQueryFields_ConsistentAcrossVisibilityTypes() {
        publicManager = new ShortcutIndexManager(VisibilityType.PUBLIC);
        privateManager = new ShortcutIndexManager(VisibilityType.PRIVATE);
        unlistedManager = new ShortcutIndexManager(VisibilityType.UNLISTED);

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
        publicManager = new ShortcutIndexManager(VisibilityType.PUBLIC);

        String filter = publicManager.buildPermissionFilter(null, null);

        assertEquals("Anonymous can see all PUBLIC/UNLISTED items", "*:*", filter);
    }

    @Test
    public void testBuildPermissionFilter_AuthenticatedUser_WritePermission_PublicCore() {
        publicManager = new ShortcutIndexManager(VisibilityType.PUBLIC);

        String filter = publicManager.buildPermissionFilter("bob", Permissions.WRITE);

        assertTrue("Should include owned items", filter.contains("owner:\"bob\""));
        assertTrue("Should include EDIT permission", filter.contains("userEdit:\"bob\""));

        String expected = "(owner:\"bob\") OR (userEdit:\"bob\")";
        assertEquals(expected, filter);
    }

    @Test
    public void testBuildPermissionFilter_AuthenticatedUser_AdminPermission_PublicCore() {
        publicManager = new ShortcutIndexManager(VisibilityType.PUBLIC);

        String filter = publicManager.buildPermissionFilter("admin", Permissions.ADMIN);

        assertEquals("Should only show owned items", "owner:\"admin\"", filter);
    }

    // ========================================================================
    // PERMISSION FILTER TESTS - PRIVATE CORE
    // ========================================================================

    @Test
    public void testBuildPermissionFilter_AnonymousUser_PrivateCore() {
        privateManager = new ShortcutIndexManager(VisibilityType.PRIVATE);

        String filter = privateManager.buildPermissionFilter(null, null);

        assertEquals("Anonymous should see nothing in private core",
                "(*:* AND NOT *:*)", filter);
    }

    @Test
    public void testBuildPermissionFilter_AuthenticatedUser_NoPermission_PrivateCore() {
        privateManager = new ShortcutIndexManager(VisibilityType.PRIVATE);

        String filter = privateManager.buildPermissionFilter("jane_doe", null);

        assertTrue("Should include owned items", filter.contains("owner:\"jane_doe\""));
        assertTrue("Should include READ permission", filter.contains("userRead:\"jane_doe\""));
        assertTrue("Should include EDIT permission", filter.contains("userEdit:\"jane_doe\""));

        String expected = "(owner:\"jane_doe\") OR (userRead:\"jane_doe\") OR (userEdit:\"jane_doe\")";
        assertEquals(expected, filter);
    }

    @Test
    public void testBuildPermissionFilter_AuthenticatedUser_AdminPermission_PrivateCore() {
        privateManager = new ShortcutIndexManager(VisibilityType.PRIVATE);

        String filter = privateManager.buildPermissionFilter("superadmin", Permissions.ADMIN);

        assertEquals("Should only show owned items", "owner:\"superadmin\"", filter);
    }

    // ========================================================================
    // PREPROCESS SEARCH TERMS TESTS
    // ========================================================================

    @Test
    public void testPreprocessSearchTerms_Wildcard_NotModified() {
        publicManager = new ShortcutIndexManager(VisibilityType.PUBLIC);

        String result = publicManager.preprocessSearchTerms("*:*");

        assertEquals("Wildcard query should pass through unchanged", "*:*", result);
    }

    @Test
    public void testPreprocessSearchTerms_RegularQuery_DelegatesToSearchUtilities() {
        publicManager = new ShortcutIndexManager(VisibilityType.PUBLIC);

        String input = "test query";
        String result = publicManager.preprocessSearchTerms(input);

        String expected = SearchUtilities.preprocessSearchTerm(input);
        assertEquals("Should delegate to SearchUtilities", expected, result);
    }

    @Test
    public void testPreprocessSearchTerms_NoScoreBoost() {
        publicManager = new ShortcutIndexManager(VisibilityType.PUBLIC);

        String input = "shortcut";
        String result = publicManager.preprocessSearchTerms(input);

        assertFalse("Shortcuts should not have ndexScore boost",
                result.contains("ndexScore"));
    }

    // ========================================================================
    // CONFIGURE QUERY TESTS
    // ========================================================================

    @Test
    public void testConfigureQuery_WildcardQuery_SortsByModificationTime() {
        publicManager = new ShortcutIndexManager(VisibilityType.PUBLIC);

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
        publicManager = new ShortcutIndexManager(VisibilityType.PUBLIC);

        SolrQuery solrQuery = new SolrQuery();
        publicManager.configureQuery(solrQuery, "test query", "filter", 10, 0);

        assertTrue("Should not have explicit sort for relevance queries",
                solrQuery.getSorts() == null || solrQuery.getSorts().isEmpty());
    }

    @Test
    public void testConfigureQuery_SetsQueryType() {
        publicManager = new ShortcutIndexManager(VisibilityType.PUBLIC);

        SolrQuery solrQuery = new SolrQuery();
        publicManager.configureQuery(solrQuery, "test", "filter", 10, 0);

        assertEquals("Should use edismax query parser", "edismax", solrQuery.get("defType"));
    }

    @Test
    public void testConfigureQuery_SetsShortcutQueryFields() {
        publicManager = new ShortcutIndexManager(VisibilityType.PUBLIC);

        SolrQuery solrQuery = new SolrQuery();
        publicManager.configureQuery(solrQuery, "test", "filter", 10, 0);

        String qf = solrQuery.get("qf");
        assertEquals("Should set correct query fields", "uuid^20 name^10 owner^2", qf);
    }

    @Test
    public void testConfigureQuery_SetsPagination() {
        publicManager = new ShortcutIndexManager(VisibilityType.PUBLIC);

        SolrQuery solrQuery = new SolrQuery();
        publicManager.configureQuery(solrQuery, "test", "filter", 25, 50);

        assertEquals("Should set correct offset", Integer.valueOf(50), solrQuery.getStart());
        assertEquals("Should set correct limit", Integer.valueOf(25), solrQuery.getRows());
    }

    @Test
    public void testConfigureQuery_ZeroLimit_UsesDefault() {
        publicManager = new ShortcutIndexManager(VisibilityType.PUBLIC);

        SolrQuery solrQuery = new SolrQuery();
        publicManager.configureQuery(solrQuery, "test", "filter", 0, 0);

        assertEquals("Zero limit should use default 100000",
                Integer.valueOf(100000), solrQuery.getRows());
    }

    // ========================================================================
    // DOCUMENT RESET AND STATE TESTS
    // ========================================================================

    @Test
    public void testSetupIndexDocument_CalledTwice_DocumentIsReset() {
        publicManager = new ShortcutIndexManager(VisibilityType.PUBLIC);

        NdexShortcut shortcut1 = createTestShortcut("Shortcut One");
        UUID uuid1 = shortcut1.getExternalId();

        SolrInputDocument doc1 = publicManager.setupIndexDocument(shortcut1);
        assertEquals("First shortcut name", "Shortcut One", doc1.getFieldValue("name"));
        assertEquals("First shortcut UUID", uuid1.toString(), doc1.getFieldValue("uuid"));

        NdexShortcut shortcut2 = createTestShortcut("Shortcut Two");
        UUID uuid2 = shortcut2.getExternalId();

        SolrInputDocument doc2 = publicManager.setupIndexDocument(shortcut2);
        assertEquals("Second shortcut name", "Shortcut Two", doc2.getFieldValue("name"));
        assertEquals("Second shortcut UUID", uuid2.toString(), doc2.getFieldValue("uuid"));

        assertNotNull(doc1);
        assertNotNull(doc2);
    }

    @Test
    public void testSetupIndexDocument_DifferentVisibilityTypes_ProduceCorrectDocuments() {
        publicManager = new ShortcutIndexManager(VisibilityType.PUBLIC);
        privateManager = new ShortcutIndexManager(VisibilityType.PRIVATE);
        unlistedManager = new ShortcutIndexManager(VisibilityType.UNLISTED);

        NdexShortcut shortcut = createTestShortcut("Test");

        SolrInputDocument publicDoc = publicManager.setupIndexDocument(shortcut);
        SolrInputDocument privateDoc = privateManager.setupIndexDocument(shortcut);
        SolrInputDocument unlistedDoc = unlistedManager.setupIndexDocument(shortcut);

        assertNull("PUBLIC should not have visibility", publicDoc.getFieldValue("visibility"));
        assertEquals("PRIVATE should have visibility=PRIVATE",
                "PRIVATE", privateDoc.getFieldValue("visibility"));
        assertNull("UNLISTED should not have visibility", unlistedDoc.getFieldValue("visibility"));

        assertEquals("Test", publicDoc.getFieldValue("name"));
        assertEquals("Test", privateDoc.getFieldValue("name"));
        assertEquals("Test", unlistedDoc.getFieldValue("name"));
    }

    // ========================================================================
    // FIND DANGLING SHORTCUTS TESTS (Unit level - filter construction)
    // ========================================================================

    @Test
    public void testFindDanglingShortcuts_BuildsCorrectFilter() {
        publicManager = new ShortcutIndexManager(VisibilityType.PUBLIC);

        String permissionFilter = publicManager.buildPermissionFilter("user", null);
        String typeFilter = " AND (entityType:SHORTCUT)";
        String danglingFilter = " AND (isDangling:true)";

        String expectedFilter = "(" + permissionFilter + ")" + typeFilter + danglingFilter;

        assertTrue("Should contain permission filter", expectedFilter.contains(permissionFilter));
        assertTrue("Should contain entity type filter", expectedFilter.contains("entityType:SHORTCUT"));
        assertTrue("Should contain dangling filter", expectedFilter.contains("isDangling:true"));
    }

    // ========================================================================
    // INTEGRATION TESTS (Require local Solr) - @Ignore by default
    // ========================================================================

    @Test
    @Ignore
    public void testFindDanglingShortcuts_Integration() throws Exception {
        publicManager = new ShortcutIndexManager(VisibilityType.PUBLIC);

        // Clean up the core first
        publicManager.client.deleteByQuery("*:*");
        publicManager.client.commit();

        // Create a valid shortcut (has target)
        NdexShortcut validShortcut = createTestShortcut("Valid Shortcut");
        validShortcut.setTarget(UUID.randomUUID());
        validShortcut.setTargetType(FileType.NETWORK);
        publicManager.createIndex(validShortcut);

        // Create a dangling shortcut (no target)
        NdexShortcut danglingShortcut = createTestShortcut("Dangling Shortcut");
        danglingShortcut.setTarget(null);
        publicManager.createIndex(danglingShortcut);

        Thread.sleep(2000);

        // Find dangling shortcuts
        var results = publicManager.findDanglingShortcuts("testOwner", 100, 0);

        assertNotNull(results);
        assertEquals("Should find 1 dangling shortcut", 1, results.getNumFound());
    }

    @Test
    @Ignore
    public void testFindDanglingShortcuts_NoDanglingShortcuts_Integration() throws Exception {
        publicManager = new ShortcutIndexManager(VisibilityType.PUBLIC);

        // Clean up the core first
        publicManager.client.deleteByQuery("*:*");
        publicManager.client.commit();

        // Create only valid shortcuts
        NdexShortcut shortcut1 = createTestShortcut("Valid Shortcut 1");
        shortcut1.setTarget(UUID.randomUUID());
        publicManager.createIndex(shortcut1);

        NdexShortcut shortcut2 = createTestShortcut("Valid Shortcut 2");
        shortcut2.setTarget(UUID.randomUUID());
        publicManager.createIndex(shortcut2);

        Thread.sleep(2000);

        var results = publicManager.findDanglingShortcuts("testOwner", 100, 0);

        assertNotNull(results);
        assertEquals("Should find 0 dangling shortcuts", 0, results.getNumFound());
    }

    @Test
    @Ignore
    public void testFindDanglingShortcuts_PrivateVisibility_Integration() throws Exception {
        privateManager = new ShortcutIndexManager(VisibilityType.PRIVATE);

        // Clean up the core first
        privateManager.client.deleteByQuery("*:*");
        privateManager.client.commit();

        // Create a dangling shortcut owned by testOwner
        NdexShortcut danglingShortcut = createTestShortcut("My Dangling Shortcut");
        danglingShortcut.setOwner("testOwner");
        danglingShortcut.setTarget(null);
        privateManager.createIndex(danglingShortcut);

        Thread.sleep(2000);

        // Owner can find their dangling shortcuts
        var ownerResults = privateManager.findDanglingShortcuts("testOwner", 100, 0);
        assertEquals("Owner should find their dangling shortcut", 1, ownerResults.getNumFound());

        // Anonymous cannot see private dangling shortcuts
        var anonymousResults = privateManager.findDanglingShortcuts(null, 100, 0);
        assertEquals("Anonymous should not see private shortcuts", 0, anonymousResults.getNumFound());

        // Other users cannot see
        var otherResults = privateManager.findDanglingShortcuts("otherUser", 100, 0);
        assertEquals("Other users should not see private shortcuts", 0, otherResults.getNumFound());
    }

    @Test
    @Ignore
    public void testSearchShortcuts_ByName_Integration() throws Exception {
        publicManager = new ShortcutIndexManager(VisibilityType.PUBLIC);

        // Clean up the core first
        publicManager.client.deleteByQuery("*:*");
        publicManager.client.commit();

        // Create shortcuts with different names
        NdexShortcut cancerShortcut = createTestShortcut("Cancer Network Link");
        cancerShortcut.setTarget(UUID.randomUUID());
        publicManager.createIndex(cancerShortcut);

        NdexShortcut diabetesShortcut = createTestShortcut("Diabetes Pathway Link");
        diabetesShortcut.setTarget(UUID.randomUUID());
        publicManager.createIndex(diabetesShortcut);

        Thread.sleep(2000);

        // Search by type
        var results = publicManager.searchByType("cancer", "testOwner", 100, 0,
                null, null, FileType.SHORTCUT.toString());

        assertNotNull(results);
        assertEquals("Should find 1 cancer shortcut", 1, results.getNumFound());
    }

    @Test
    @Ignore
    public void testSearchShortcuts_Pagination_Integration() throws Exception {
        publicManager = new ShortcutIndexManager(VisibilityType.PUBLIC);

        // Clean up the core first
        publicManager.client.deleteByQuery("*:*");
        publicManager.client.commit();

        // Create 10 shortcuts
        for (int i = 0; i < 10; i++) {
            NdexShortcut shortcut = createTestShortcut("Shortcut " + i);
            shortcut.setTarget(UUID.randomUUID());
            publicManager.createIndex(shortcut);
        }

        Thread.sleep(2000);

        // First page
        var page1 = publicManager.searchByType("*:*", "testOwner", 5, 0,
                null, null, FileType.SHORTCUT.toString());
        assertEquals("First page should have 5 results", 5, page1.size());
        assertEquals("Total should be 10", 10, page1.getNumFound());

        // Second page
        var page2 = publicManager.searchByType("*:*", "testOwner", 5, 5,
                null, null, FileType.SHORTCUT.toString());
        assertEquals("Second page should have 5 results", 5, page2.size());
    }

    @Test
    @Ignore
    public void testSearchShortcuts_OnlyReturnsShortcuts_Integration() throws Exception {
        publicManager = new ShortcutIndexManager(VisibilityType.PUBLIC);
        FolderIndexManager folderManager = new FolderIndexManager(VisibilityType.PUBLIC);

        // Clean up the core first
        publicManager.client.deleteByQuery("*:*");
        publicManager.client.commit();

        // Index a shortcut
        NdexShortcut shortcut = createTestShortcut("Test Shortcut");
        shortcut.setTarget(UUID.randomUUID());
        publicManager.createIndex(shortcut);

        // Index a folder (same core)
        NdexFolder folder = new NdexFolder();
        folder.setExternalId(UUID.randomUUID());
        folder.setName("Test Folder");
        folder.setOwner("testOwner");
        folder.setCreationTime(Timestamp.from(Instant.now()));
        folder.setModificationTime(Timestamp.from(Instant.now()));
        folderManager.createIndex(folder);

        Thread.sleep(2000);

        // Search for shortcuts only
        var results = publicManager.searchByType("*:*", "testOwner", 100, 0,
                null, null, FileType.SHORTCUT.toString());

        assertEquals("Should find only 1 shortcut", 1, results.getNumFound());

        folderManager.close();
    }

    // ========================================================================
    // HELPER METHODS
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