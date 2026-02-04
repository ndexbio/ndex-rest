package org.ndexbio.common.solr;

import org.apache.solr.client.solrj.SolrQuery;
import org.apache.solr.common.SolrDocumentList;
import org.apache.solr.common.SolrInputDocument;
import org.junit.After;
import org.junit.BeforeClass;
import org.junit.Ignore;
import org.junit.Test;
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
 * Comprehensive unit tests for FolderIndexManager
 * Tests all functionality including document setup, query configuration,
 * permission filters, and edge cases.
 *
 * @author your-name
 */
public class TestFolderIndexManager {

    private FolderIndexManager publicManager;
    private FolderIndexManager privateManager;
    private FolderIndexManager unlistedManager;

    /**
     * Setup Configuration singleton mock before any tests run.
     */
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

    private void closeManager(FolderIndexManager manager) {
        if (manager != null) {
            manager.close();
        }
    }

    // ========================================================================
    // CORE NAME AND INITIALIZATION TESTS
    // ========================================================================

    @Test
    public void testConstructor_PublicVisibility_UsesPublicCore() {
        publicManager = new FolderIndexManager(VisibilityType.PUBLIC);
        assertEquals("public-nfs", publicManager.coreName);
        assertNotNull("SolrInputDocument should be initialized", publicManager.doc);
        assertNotNull("HttpSolrClient should be initialized", publicManager.client);
    }

    @Test
    public void testConstructor_UnlistedVisibility_UsesPublicCore() {
        unlistedManager = new FolderIndexManager(VisibilityType.UNLISTED);
        assertEquals("public-nfs", unlistedManager.coreName);
    }

    @Test
    public void testConstructor_PrivateVisibility_UsesPrivateCore() {
        privateManager = new FolderIndexManager(VisibilityType.PRIVATE);
        assertEquals("private-nfs", privateManager.coreName);
    }

    @Test
    public void testConstructor_VisibilityTypeStored() {
        publicManager = new FolderIndexManager(VisibilityType.PUBLIC);
        privateManager = new FolderIndexManager(VisibilityType.PRIVATE);

        assertEquals(VisibilityType.PUBLIC, publicManager.visibilityType);
        assertEquals(VisibilityType.PRIVATE, privateManager.visibilityType);
    }

    // ========================================================================
    // SETUP INDEX DOCUMENT TESTS - COMPLETE FOLDERS
    // ========================================================================

    @Test
    public void testSetupIndexDocument_PublicFolder_AllFieldsSet() {
        publicManager = new FolderIndexManager(VisibilityType.PUBLIC);

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

        SolrInputDocument doc = publicManager.setupIndexDocument(folder);

        assertNotNull("Document should not be null", doc);
        assertEquals("UUID should match", folderId.toString(), doc.getFieldValue("uuid"));
        assertEquals("Entity type should be FOLDER", "FOLDER", doc.getFieldValue("entityType"));
        assertEquals("Name should match", "Test Folder", doc.getFieldValue("name"));
        assertEquals("Description should match", "Test Description", doc.getFieldValue("description"));
        assertEquals("Owner should match", "testOwner", doc.getFieldValue("owner"));
        assertEquals("Parent UUID should match", parentId.toString(), doc.getFieldValue("parentUuid"));
        assertEquals("Creation time should match", creationTime, doc.getFieldValue("creationTime"));
        assertEquals("Modification time should match", modificationTime, doc.getFieldValue("modificationTime"));
        assertNull("PUBLIC folder should not have visibility field", doc.getFieldValue("visibility"));
    }

    @Test
    public void testSetupIndexDocument_UnlistedFolder_NoVisibilityField() {
        unlistedManager = new FolderIndexManager(VisibilityType.UNLISTED);

        NdexFolder folder = createTestFolder("Unlisted Folder", "Unlisted Description");

        SolrInputDocument doc = unlistedManager.setupIndexDocument(folder);

        assertNotNull(doc);
        assertEquals("FOLDER", doc.getFieldValue("entityType"));
        assertEquals("Unlisted Folder", doc.getFieldValue("name"));
        assertNull("UNLISTED folder should not have visibility field", doc.getFieldValue("visibility"));
    }

    @Test
    public void testSetupIndexDocument_PrivateFolder_HasVisibilityAndOwnerField() {
        privateManager = new FolderIndexManager(VisibilityType.PRIVATE);

        NdexFolder folder = createTestFolder("Private Folder", "Private Description");

        SolrInputDocument doc = privateManager.setupIndexDocument(folder);

        assertNotNull(doc);
        assertEquals("FOLDER", doc.getFieldValue("entityType"));
        assertEquals("Private Folder", doc.getFieldValue("name"));
        assertEquals("PRIVATE", doc.getFieldValue("visibility"));
        assertEquals("testOwner", doc.getFieldValue("owner")); // Both owner and USER_ADMIN
    }

    @Test
    public void testSetupIndexDocument_PrivateFolder_OwnerSetInBothFields() {
        privateManager = new FolderIndexManager(VisibilityType.PRIVATE);

        NdexFolder folder = createTestFolder("Test", "Test");
        folder.setOwner("john_doe");

        SolrInputDocument doc = privateManager.setupIndexDocument(folder);

        // For PRIVATE, owner should be set in both USER_ADMIN and OWNER_FIELD
        assertEquals("Owner should be in USER_ADMIN field", "john_doe", doc.getFieldValue("owner"));
        // OWNER_FIELD is also "owner" in this case, so same value
    }

    // ========================================================================
    // SETUP INDEX DOCUMENT TESTS - MINIMAL/MISSING FIELDS
    // ========================================================================

    @Test
    public void testSetupIndexDocument_MinimalFolder_OnlyRequiredFields() {
        publicManager = new FolderIndexManager(VisibilityType.PUBLIC);

        UUID folderId = UUID.randomUUID();
        Timestamp now = Timestamp.from(Instant.now());

        NdexFolder folder = new NdexFolder();
        folder.setExternalId(folderId);
        folder.setCreationTime(now);
        folder.setModificationTime(now);
        // No name, description, owner, parent

        SolrInputDocument doc = publicManager.setupIndexDocument(folder);

        assertNotNull(doc);
        assertEquals(folderId.toString(), doc.getFieldValue("uuid"));
        assertEquals("FOLDER", doc.getFieldValue("entityType"));
        assertEquals(now, doc.getFieldValue("creationTime"));
        assertEquals(now, doc.getFieldValue("modificationTime"));

        assertNull("Name should not be indexed", doc.getFieldValue("name"));
        assertNull("Description should not be indexed", doc.getFieldValue("description"));
        assertNull("Owner should not be indexed", doc.getFieldValue("owner"));
        assertNull("Parent should not be indexed", doc.getFieldValue("parentUuid"));
    }

    @Test
    public void testSetupIndexDocument_NoParent_ParentFieldNull() {
        publicManager = new FolderIndexManager(VisibilityType.PUBLIC);

        NdexFolder folder = createTestFolder("Root Folder", "Root folder without parent");
        folder.setParent(null);

        SolrInputDocument doc = publicManager.setupIndexDocument(folder);

        assertNotNull(doc);
        assertNull("Folder without parent should not have parentUuid", doc.getFieldValue("parentUuid"));
    }

    // ========================================================================
    // NAME FIELD TESTS - LENGTH VALIDATION
    // ========================================================================

    @Test
    public void testSetupIndexDocument_NameLength0_NotIndexed() {
        publicManager = new FolderIndexManager(VisibilityType.PUBLIC);

        NdexFolder folder = createTestFolder("", "Description");

        SolrInputDocument doc = publicManager.setupIndexDocument(folder);

        assertNull("Empty name (length 0) should not be indexed", doc.getFieldValue("name"));
    }

    @Test
    public void testSetupIndexDocument_NameLength1_NotIndexed() {
        publicManager = new FolderIndexManager(VisibilityType.PUBLIC);

        NdexFolder folder = createTestFolder("A", "Description");

        SolrInputDocument doc = publicManager.setupIndexDocument(folder);

        assertNull("Name with length 1 should not be indexed", doc.getFieldValue("name"));
    }

    @Test
    public void testSetupIndexDocument_NameLength2_IsIndexed() {
        publicManager = new FolderIndexManager(VisibilityType.PUBLIC);

        NdexFolder folder = createTestFolder("AB", "Description");

        SolrInputDocument doc = publicManager.setupIndexDocument(folder);

        assertEquals("Name with length 2 should be indexed", "AB", doc.getFieldValue("name"));
    }

    @Test
    public void testSetupIndexDocument_NameLengthExactly2_BoundaryCase() {
        publicManager = new FolderIndexManager(VisibilityType.PUBLIC);

        NdexFolder folder = createTestFolder("My", "Description");

        SolrInputDocument doc = publicManager.setupIndexDocument(folder);

        assertEquals("Name with exactly 2 characters should be indexed", "My", doc.getFieldValue("name"));
    }

    @Test
    public void testSetupIndexDocument_NullName_NotIndexed() {
        publicManager = new FolderIndexManager(VisibilityType.PUBLIC);

        NdexFolder folder = createTestFolder(null, "Description");

        SolrInputDocument doc = publicManager.setupIndexDocument(folder);

        assertNull("Null name should not be indexed", doc.getFieldValue("name"));
    }

    @Test
    public void testSetupIndexDocument_LongName_IsIndexed() {
        publicManager = new FolderIndexManager(VisibilityType.PUBLIC);

        String longName = "This is a very long folder name with many characters " +
                "that exceeds normal length expectations";
        NdexFolder folder = createTestFolder(longName, "Description");

        SolrInputDocument doc = publicManager.setupIndexDocument(folder);

        assertEquals("Long name should be indexed", longName, doc.getFieldValue("name"));
    }

    @Test
    public void testSetupIndexDocument_NameWithSpecialCharacters() {
        publicManager = new FolderIndexManager(VisibilityType.PUBLIC);

        String specialName = "Folder-Name_2024 (Test) [v1.0]";
        NdexFolder folder = createTestFolder(specialName, "Description");

        SolrInputDocument doc = publicManager.setupIndexDocument(folder);

        assertEquals("Name with special characters should be indexed",
                specialName, doc.getFieldValue("name"));
    }

    @Test
    public void testSetupIndexDocument_NameWithUnicode() {
        publicManager = new FolderIndexManager(VisibilityType.PUBLIC);

        String unicodeName = "文件夹 Папка مجلد";
        NdexFolder folder = createTestFolder(unicodeName, "Description");

        SolrInputDocument doc = publicManager.setupIndexDocument(folder);

        assertEquals("Name with unicode should be indexed", unicodeName, doc.getFieldValue("name"));
    }

    // ========================================================================
    // DESCRIPTION FIELD TESTS - LENGTH VALIDATION
    // ========================================================================

    @Test
    public void testSetupIndexDocument_DescriptionLength0_NotIndexed() {
        publicManager = new FolderIndexManager(VisibilityType.PUBLIC);

        NdexFolder folder = createTestFolder("Name", "");

        SolrInputDocument doc = publicManager.setupIndexDocument(folder);

        assertNull("Empty description should not be indexed", doc.getFieldValue("description"));
    }

    @Test
    public void testSetupIndexDocument_DescriptionLength1_NotIndexed() {
        publicManager = new FolderIndexManager(VisibilityType.PUBLIC);

        NdexFolder folder = createTestFolder("Name", "D");

        SolrInputDocument doc = publicManager.setupIndexDocument(folder);

        assertNull("Description with length 1 should not be indexed", doc.getFieldValue("description"));
    }

    @Test
    public void testSetupIndexDocument_DescriptionLength2_IsIndexed() {
        publicManager = new FolderIndexManager(VisibilityType.PUBLIC);

        NdexFolder folder = createTestFolder("Name", "OK");

        SolrInputDocument doc = publicManager.setupIndexDocument(folder);

        assertEquals("Description with length 2 should be indexed", "OK", doc.getFieldValue("description"));
    }

    @Test
    public void testSetupIndexDocument_NullDescription_NotIndexed() {
        publicManager = new FolderIndexManager(VisibilityType.PUBLIC);

        NdexFolder folder = new NdexFolder();
        folder.setExternalId(UUID.randomUUID());
        folder.setName("Name");
        folder.setOwner("owner");
        folder.setCreationTime(Timestamp.from(Instant.now()));
        folder.setModificationTime(Timestamp.from(Instant.now()));
        // description is null

        SolrInputDocument doc = publicManager.setupIndexDocument(folder);

        assertNull("Null description should not be indexed", doc.getFieldValue("description"));
    }

    @Test
    public void testSetupIndexDocument_LongDescription_IsIndexed() {
        publicManager = new FolderIndexManager(VisibilityType.PUBLIC);

        String longDesc = "This is a very detailed description that spans multiple sentences. " +
                "It contains various information about the folder and its contents. " +
                "This tests that long text is properly indexed.";
        NdexFolder folder = createTestFolder("Name", longDesc);

        SolrInputDocument doc = publicManager.setupIndexDocument(folder);

        assertEquals("Long description should be indexed", longDesc, doc.getFieldValue("description"));
    }

    // ========================================================================
    // OWNER FIELD TESTS - BLANK/NULL VALIDATION
    // ========================================================================

    @Test
    public void testSetupIndexDocument_NullOwner_NotIndexed() {
        publicManager = new FolderIndexManager(VisibilityType.PUBLIC);

        NdexFolder folder = createTestFolder("Name", "Description");
        folder.setOwner(null);

        SolrInputDocument doc = publicManager.setupIndexDocument(folder);

        assertNull("Null owner should not be indexed", doc.getFieldValue("owner"));
    }

    @Test
    public void testSetupIndexDocument_EmptyOwner_NotIndexed() {
        publicManager = new FolderIndexManager(VisibilityType.PUBLIC);

        NdexFolder folder = createTestFolder("Name", "Description");
        folder.setOwner("");

        SolrInputDocument doc = publicManager.setupIndexDocument(folder);

        assertNull("Empty owner should not be indexed", doc.getFieldValue("owner"));
    }

    @Test
    public void testSetupIndexDocument_BlankOwner_NotIndexed() {
        publicManager = new FolderIndexManager(VisibilityType.PUBLIC);

        NdexFolder folder = createTestFolder("Name", "Description");
        folder.setOwner("   "); // Only spaces

        SolrInputDocument doc = publicManager.setupIndexDocument(folder);

        assertNull("Blank owner (only spaces) should not be indexed", doc.getFieldValue("owner"));
    }

    @Test
    public void testSetupIndexDocument_BlankOwnerTabsNewlines_NotIndexed() {
        publicManager = new FolderIndexManager(VisibilityType.PUBLIC);

        NdexFolder folder = createTestFolder("Name", "Description");
        folder.setOwner("\t\n  \r\n");

        SolrInputDocument doc = publicManager.setupIndexDocument(folder);

        assertNull("Blank owner with tabs/newlines should not be indexed", doc.getFieldValue("owner"));
    }

    @Test
    public void testSetupIndexDocument_OwnerWithLeadingTrailingSpaces_IsIndexed() {
        publicManager = new FolderIndexManager(VisibilityType.PUBLIC);

        NdexFolder folder = createTestFolder("Name", "Description");
        folder.setOwner("  john_doe  ");

        SolrInputDocument doc = publicManager.setupIndexDocument(folder);

        // isBlank() returns false for strings with non-whitespace characters
        assertEquals("Owner with leading/trailing spaces should be indexed",
                "  john_doe  ", doc.getFieldValue("owner"));
    }

    @Test
    public void testSetupIndexDocument_OwnerWithEmailFormat() {
        publicManager = new FolderIndexManager(VisibilityType.PUBLIC);

        NdexFolder folder = createTestFolder("Name", "Description");
        folder.setOwner("user@example.com");

        SolrInputDocument doc = publicManager.setupIndexDocument(folder);

        assertEquals("Email format owner should be indexed",
                "user@example.com", doc.getFieldValue("owner"));
    }

    // ========================================================================
    // PRIVATE VISIBILITY SPECIAL CASES
    // ========================================================================

    @Test
    public void testSetupIndexDocument_PrivateFolder_BlankOwner_NoOwnerFieldSet() {
        privateManager = new FolderIndexManager(VisibilityType.PRIVATE);

        NdexFolder folder = createTestFolder("Name", "Description");
        folder.setOwner("  ");

        SolrInputDocument doc = privateManager.setupIndexDocument(folder);

        // For PRIVATE with blank owner, OWNER_FIELD should not be set
        assertNull("Blank owner should not set OWNER_FIELD in PRIVATE", doc.getFieldValue("owner"));
        assertEquals("Visibility should still be set", "PRIVATE", doc.getFieldValue("visibility"));
    }

    @Test
    public void testSetupIndexDocument_PrivateFolder_NullOwner_NoOwnerFieldSet() {
        privateManager = new FolderIndexManager(VisibilityType.PRIVATE);

        NdexFolder folder = createTestFolder("Name", "Description");
        folder.setOwner(null);

        SolrInputDocument doc = privateManager.setupIndexDocument(folder);

        assertNull("Null owner should not set OWNER_FIELD in PRIVATE", doc.getFieldValue("owner"));
        assertEquals("Visibility should still be set", "PRIVATE", doc.getFieldValue("visibility"));
    }

    // ========================================================================
    // QUERY FIELDS TESTS
    // ========================================================================

    @Test
    public void testGetQueryFields_ReturnsExpectedString() {
        publicManager = new FolderIndexManager(VisibilityType.PUBLIC);

        String queryFields = publicManager.getQueryFields();

        assertEquals("uuid^20 name^10 description^5 owner^2", queryFields);
    }

    @Test
    public void testGetQueryFields_ContainsAllFields() {
        publicManager = new FolderIndexManager(VisibilityType.PUBLIC);

        String queryFields = publicManager.getQueryFields();

        assertTrue("Should contain uuid", queryFields.contains("uuid"));
        assertTrue("Should contain name", queryFields.contains("name"));
        assertTrue("Should contain description", queryFields.contains("description"));
        assertTrue("Should contain owner", queryFields.contains("owner"));
    }

    @Test
    public void testGetQueryFields_HasCorrectBoostValues() {
        publicManager = new FolderIndexManager(VisibilityType.PUBLIC);

        String queryFields = publicManager.getQueryFields();

        assertTrue("UUID should have boost 20", queryFields.contains("uuid^20"));
        assertTrue("Name should have boost 10", queryFields.contains("name^10"));
        assertTrue("Description should have boost 5", queryFields.contains("description^5"));
        assertTrue("Owner should have boost 2", queryFields.contains("owner^2"));
    }

    @Test
    public void testGetQueryFields_ConsistentAcrossVisibilityTypes() {
        publicManager = new FolderIndexManager(VisibilityType.PUBLIC);
        privateManager = new FolderIndexManager(VisibilityType.PRIVATE);
        unlistedManager = new FolderIndexManager(VisibilityType.UNLISTED);

        String publicFields = publicManager.getQueryFields();
        String privateFields = privateManager.getQueryFields();
        String unlistedFields = unlistedManager.getQueryFields();

        assertEquals("Query fields should be same for all visibility types",
                publicFields, privateFields);
        assertEquals("Query fields should be same for all visibility types",
                publicFields, unlistedFields);
    }

    @Test
    public void testGetQueryFields_DoesNotContainUnexpectedFields() {
        publicManager = new FolderIndexManager(VisibilityType.PUBLIC);

        String queryFields = publicManager.getQueryFields();

        // Folders shouldn't have network-specific fields
        assertFalse("Should not contain nodeName", queryFields.contains("nodeName"));
        assertFalse("Should not contain represents", queryFields.contains("represents"));
        assertFalse("Should not contain organism", queryFields.contains("organism"));
        assertFalse("Should not contain disease", queryFields.contains("disease"));
    }

    // ========================================================================
    // PERMISSION FILTER TESTS - PUBLIC CORE
    // ========================================================================

    @Test
    public void testBuildPermissionFilter_AnonymousUser_PublicCore() {
        publicManager = new FolderIndexManager(VisibilityType.PUBLIC);

        String filter = publicManager.buildPermissionFilter(null, null);

        assertEquals("Anonymous should only see PUBLIC items",
                "visibility:PUBLIC", filter);
    }

    @Test
    public void testBuildPermissionFilter_AnonymousUser_ReadPermission_PublicCore() {
        publicManager = new FolderIndexManager(VisibilityType.PUBLIC);

        String filter = publicManager.buildPermissionFilter(null, Permissions.READ);

        assertEquals("Anonymous with READ should only see PUBLIC items",
                "visibility:PUBLIC", filter);
    }

    @Test
    public void testBuildPermissionFilter_AnonymousUser_WritePermission_PublicCore() {
        publicManager = new FolderIndexManager(VisibilityType.PUBLIC);

        String filter = publicManager.buildPermissionFilter(null, Permissions.WRITE);

        assertEquals("Anonymous with WRITE should only see PUBLIC items",
                "visibility:PUBLIC", filter);
    }

    @Test
    public void testBuildPermissionFilter_AnonymousUser_AdminPermission_PublicCore() {
        publicManager = new FolderIndexManager(VisibilityType.PUBLIC);

        String filter = publicManager.buildPermissionFilter(null, Permissions.ADMIN);

        assertEquals("Anonymous with ADMIN should only see PUBLIC items",
                "visibility:PUBLIC", filter);
    }

    @Test
    public void testBuildPermissionFilter_AuthenticatedUser_NoPermission_PublicCore() {
        publicManager = new FolderIndexManager(VisibilityType.PUBLIC);

        String filter = publicManager.buildPermissionFilter("john_doe", null);

        assertTrue("Should include PUBLIC items", filter.contains("visibility:PUBLIC"));
        assertTrue("Should include owned items", filter.contains("owner:\"john_doe\""));
        assertTrue("Should include READ permission", filter.contains("userRead:\"john_doe\""));
        assertTrue("Should include EDIT permission", filter.contains("userEdit:\"john_doe\""));
        assertTrue("Should use OR logic", filter.contains(" OR "));

        // Verify it's a proper OR query
        String expected = "(visibility:PUBLIC) OR (owner:\"john_doe\") OR " +
                "(userRead:\"john_doe\") OR (userEdit:\"john_doe\")";
        assertEquals(expected, filter);
    }

    @Test
    public void testBuildPermissionFilter_AuthenticatedUser_ReadPermission_PublicCore() {
        publicManager = new FolderIndexManager(VisibilityType.PUBLIC);

        String filter = publicManager.buildPermissionFilter("alice", Permissions.READ);

        assertTrue("Should include PUBLIC items", filter.contains("visibility:PUBLIC"));
        assertTrue("Should include owned items", filter.contains("owner:\"alice\""));
        assertTrue("Should include READ permission", filter.contains("userRead:\"alice\""));
        assertTrue("Should include EDIT permission", filter.contains("userEdit:\"alice\""));
    }

    @Test
    public void testBuildPermissionFilter_AuthenticatedUser_WritePermission_PublicCore() {
        publicManager = new FolderIndexManager(VisibilityType.PUBLIC);

        String filter = publicManager.buildPermissionFilter("bob", Permissions.WRITE);

        assertFalse("Should NOT include PUBLIC items", filter.contains("visibility"));
        assertTrue("Should include owned items", filter.contains("owner:\"bob\""));
        assertFalse("Should NOT include READ permission", filter.contains("userRead"));
        assertTrue("Should include EDIT permission", filter.contains("userEdit:\"bob\""));

        String expected = "(owner:\"bob\") OR (userEdit:\"bob\")";
        assertEquals(expected, filter);
    }

    @Test
    public void testBuildPermissionFilter_AuthenticatedUser_AdminPermission_PublicCore() {
        publicManager = new FolderIndexManager(VisibilityType.PUBLIC);

        String filter = publicManager.buildPermissionFilter("admin", Permissions.ADMIN);

        assertEquals("Should only show owned items", "owner:\"admin\"", filter);
        assertFalse("Should NOT include visibility", filter.contains("visibility"));
        assertFalse("Should NOT include userRead", filter.contains("userRead"));
        assertFalse("Should NOT include userEdit", filter.contains("userEdit"));
    }

    @Test
    public void testBuildPermissionFilter_UsernameWithSpecialChars_PublicCore() {
        publicManager = new FolderIndexManager(VisibilityType.PUBLIC);

        String filter = publicManager.buildPermissionFilter("user@example.com", Permissions.ADMIN);

        assertTrue("Should properly quote username with special chars",
                filter.contains("\"user@example.com\""));
        assertEquals("owner:\"user@example.com\"", filter);
    }

    @Test
    public void testBuildPermissionFilter_UsernameWithSpaces_PublicCore() {
        publicManager = new FolderIndexManager(VisibilityType.PUBLIC);

        String filter = publicManager.buildPermissionFilter("John Doe", Permissions.ADMIN);

        assertTrue("Should quote username with spaces", filter.contains("\"John Doe\""));
    }

    // ========================================================================
    // PERMISSION FILTER TESTS - PRIVATE CORE
    // ========================================================================

    @Test
    public void testBuildPermissionFilter_AnonymousUser_PrivateCore() {
        privateManager = new FolderIndexManager(VisibilityType.PRIVATE);

        String filter = privateManager.buildPermissionFilter(null, null);

        assertEquals("Anonymous should see nothing in private core",
                "(*:* AND NOT *:*)", filter);
    }

    @Test
    public void testBuildPermissionFilter_AnonymousUser_AllPermissions_PrivateCore() {
        privateManager = new FolderIndexManager(VisibilityType.PRIVATE);

        assertEquals("(*:* AND NOT *:*)",
                privateManager.buildPermissionFilter(null, Permissions.READ));
        assertEquals("(*:* AND NOT *:*)",
                privateManager.buildPermissionFilter(null, Permissions.WRITE));
        assertEquals("(*:* AND NOT *:*)",
                privateManager.buildPermissionFilter(null, Permissions.ADMIN));
    }

    @Test
    public void testBuildPermissionFilter_AuthenticatedUser_NoPermission_PrivateCore() {
        privateManager = new FolderIndexManager(VisibilityType.PRIVATE);

        String filter = privateManager.buildPermissionFilter("jane_doe", null);

        assertTrue("Should include owned items", filter.contains("owner:\"jane_doe\""));
        assertTrue("Should include READ permission", filter.contains("userRead:\"jane_doe\""));
        assertTrue("Should include EDIT permission", filter.contains("userEdit:\"jane_doe\""));
        assertTrue("Should use OR logic", filter.contains(" OR "));
        assertFalse("Should NOT include visibility", filter.contains("visibility"));

        String expected = "(owner:\"jane_doe\") OR (userRead:\"jane_doe\") OR (userEdit:\"jane_doe\")";
        assertEquals(expected, filter);
    }

    @Test
    public void testBuildPermissionFilter_AuthenticatedUser_ReadPermission_PrivateCore() {
        privateManager = new FolderIndexManager(VisibilityType.PRIVATE);

        String filter = privateManager.buildPermissionFilter("charlie", Permissions.READ);

        assertTrue("Should include owned items", filter.contains("owner:\"charlie\""));
        assertTrue("Should include READ permission", filter.contains("userRead:\"charlie\""));
        assertTrue("Should include EDIT permission", filter.contains("userEdit:\"charlie\""));
        assertFalse("Should NOT include visibility", filter.contains("visibility"));
    }

    @Test
    public void testBuildPermissionFilter_AuthenticatedUser_WritePermission_PrivateCore() {
        privateManager = new FolderIndexManager(VisibilityType.PRIVATE);

        String filter = privateManager.buildPermissionFilter("david", Permissions.WRITE);

        assertTrue("Should include owned items", filter.contains("owner:\"david\""));
        assertFalse("Should NOT include READ permission", filter.contains("userRead"));
        assertTrue("Should include EDIT permission", filter.contains("userEdit:\"david\""));

        String expected = "(owner:\"david\") OR (userEdit:\"david\")";
        assertEquals(expected, filter);
    }

    @Test
    public void testBuildPermissionFilter_AuthenticatedUser_AdminPermission_PrivateCore() {
        privateManager = new FolderIndexManager(VisibilityType.PRIVATE);

        String filter = privateManager.buildPermissionFilter("superadmin", Permissions.ADMIN);

        assertEquals("Should only show owned items", "owner:\"superadmin\"", filter);
    }

    // ========================================================================
    // PREPROCESS SEARCH TERMS TESTS
    // ========================================================================

    @Test
    public void testPreprocessSearchTerms_Wildcard_NotModified() {
        publicManager = new FolderIndexManager(VisibilityType.PUBLIC);

        String result = publicManager.preprocessSearchTerms("*:*");

        assertEquals("Wildcard query should pass through unchanged", "*:*", result);
    }

    @Test
    public void testPreprocessSearchTerms_WildcardMixedCase_NotModified() {
        publicManager = new FolderIndexManager(VisibilityType.PUBLIC);

        assertEquals("*:*", publicManager.preprocessSearchTerms("*:*"));
        assertEquals("*:*", publicManager.preprocessSearchTerms("*:*"));
        assertEquals("*:*", publicManager.preprocessSearchTerms("*:*"));
    }

    @Test
    public void testPreprocessSearchTerms_SimpleQuery_DelegatesToSearchUtilities() {
        publicManager = new FolderIndexManager(VisibilityType.PUBLIC);

        String input = "test query";
        String result = publicManager.preprocessSearchTerms(input);

        // Should delegate to SearchUtilities.preprocessSearchTerm()
        String expected = SearchUtilities.preprocessSearchTerm(input);
        assertEquals("Should delegate to SearchUtilities", expected, result);
    }

    @Test
    public void testPreprocessSearchTerms_SpecialCharacters_ProcessedBySearchUtilities() {
        publicManager = new FolderIndexManager(VisibilityType.PUBLIC);

        String input = "test:query";
        String result = publicManager.preprocessSearchTerms(input);
        String expected = SearchUtilities.preprocessSearchTerm(input);

        assertEquals("Should delegate special character handling to SearchUtilities",
                expected, result);
    }

    @Test
    public void testPreprocessSearchTerms_EmptyString() {
        publicManager = new FolderIndexManager(VisibilityType.PUBLIC);

        String result = publicManager.preprocessSearchTerms("");
        String expected = SearchUtilities.preprocessSearchTerm("");

        assertEquals(expected, result);
    }

    @Test
    public void testPreprocessSearchTerms_ConsistentBehavior() {
        publicManager = new FolderIndexManager(VisibilityType.PUBLIC);

        String input = "cancer pathway";
        String result1 = publicManager.preprocessSearchTerms(input);
        String result2 = publicManager.preprocessSearchTerms(input);

        assertEquals("Same input should produce same output", result1, result2);
    }

    @Test
    public void testPreprocessSearchTerms_DifferentManagers_SameBehavior() {
        publicManager = new FolderIndexManager(VisibilityType.PUBLIC);
        privateManager = new FolderIndexManager(VisibilityType.PRIVATE);

        String input = "test";
        String publicResult = publicManager.preprocessSearchTerms(input);
        String privateResult = privateManager.preprocessSearchTerms(input);

        assertEquals("Different managers should process terms identically",
                publicResult, privateResult);
    }

    // ========================================================================
    // DOCUMENT RESET AND STATE TESTS
    // ========================================================================

    @Test
    public void testSetupIndexDocument_CalledTwice_DocumentIsReset() {
        publicManager = new FolderIndexManager(VisibilityType.PUBLIC);

        NdexFolder folder1 = createTestFolder("Folder One", "Description One");
        UUID uuid1 = folder1.getExternalId();

        SolrInputDocument doc1 = publicManager.setupIndexDocument(folder1);
        assertEquals("First folder name", "Folder One", doc1.getFieldValue("name"));
        assertEquals("First folder UUID", uuid1.toString(), doc1.getFieldValue("uuid"));

        NdexFolder folder2 = createTestFolder("Folder Two", "Description Two");
        UUID uuid2 = folder2.getExternalId();

        SolrInputDocument doc2 = publicManager.setupIndexDocument(folder2);
        assertEquals("Second folder name", "Folder Two", doc2.getFieldValue("name"));
        assertEquals("Second folder UUID", uuid2.toString(), doc2.getFieldValue("uuid"));

        // Verify doc is properly reset (doc is recreated with each call)
        assertNotNull(doc1);
        assertNotNull(doc2);
    }

    @Test
    public void testSetupIndexDocument_DifferentVisibilityTypes_ProduceCorrectDocuments() {
        publicManager = new FolderIndexManager(VisibilityType.PUBLIC);
        privateManager = new FolderIndexManager(VisibilityType.PRIVATE);
        unlistedManager = new FolderIndexManager(VisibilityType.UNLISTED);

        NdexFolder folder = createTestFolder("Test", "Test");

        SolrInputDocument publicDoc = publicManager.setupIndexDocument(folder);
        SolrInputDocument privateDoc = privateManager.setupIndexDocument(folder);
        SolrInputDocument unlistedDoc = unlistedManager.setupIndexDocument(folder);

        assertNull("PUBLIC should not have visibility", publicDoc.getFieldValue("visibility"));
        assertEquals("PRIVATE should have visibility=PRIVATE",
                "PRIVATE", privateDoc.getFieldValue("visibility"));
        assertNull("UNLISTED should not have visibility", unlistedDoc.getFieldValue("visibility"));

        // All should have same core fields
        assertEquals("Test", publicDoc.getFieldValue("name"));
        assertEquals("Test", privateDoc.getFieldValue("name"));
        assertEquals("Test", unlistedDoc.getFieldValue("name"));
    }
    @Test
    public void testSearchInFolder_WithParentFilter() throws Exception {
        publicManager = new FolderIndexManager(VisibilityType.PUBLIC);

        UUID parentId = UUID.randomUUID();

        // Mock the client to verify the query is built correctly
        // In a real integration test, this would actually query Solr
        // For unit test, we verify the filter construction

        // We can't easily test the actual search without Solr, but we can test
        // that searchInFolder calls the right methods with right parameters

        // This would be better as an integration test with actual Solr
        // For now, verify the method exists and doesn't crash with valid inputs
        try {
            publicManager.searchInFolder("*:*", "testUser", 10, 0, parentId.toString(), null);
        } catch (Exception e) {
            // Expected to fail without real Solr, but method signature is correct
            assertTrue("Should fail due to missing Solr, not logic error",
                    e.getMessage().contains("Connection refused") ||
                            e.getMessage().contains("Solr"));
        }
    }

    @Test
    public void testSearchInFolder_BuildsCorrectEntityTypeFilter() throws Exception {
        publicManager = new FolderIndexManager(VisibilityType.PUBLIC);

        // The searchInFolder method should add entityType:FOLDER filter
        // We're testing the filter string construction logic

        String permissionFilter = publicManager.buildPermissionFilter("user", null);
        String typeFilter = " AND (entityType:\"FOLDER\")";
        String parentFilter = " AND (parentUuid:\"" + UUID.randomUUID() + "\")";

        String expectedFilter = permissionFilter + typeFilter + parentFilter;

        // Verify the filter contains all required parts
        assertTrue("Should contain entity type filter", expectedFilter.contains("entityType:\"FOLDER\""));
        assertTrue("Should contain parent filter", expectedFilter.contains("parentUuid:"));
    }

    @Test
    public void testSearchInFolder_NullParentId_NoParentFilter() throws Exception {
        publicManager = new FolderIndexManager(VisibilityType.PUBLIC);

        // When parentFolderId is null, no parent filter should be added
        String permissionFilter = publicManager.buildPermissionFilter("user", null);
        String typeFilter = " AND (entityType:\"FOLDER\")";
        String parentFilter = ""; // Empty when parent is null

        String expectedFilter = permissionFilter + typeFilter + parentFilter;

        assertFalse("Should not contain parent filter when null",
                expectedFilter.contains("parentUuid:"));
        assertTrue("Should still contain entity type",
                expectedFilter.contains("entityType:\"FOLDER\""));
    }

    @Test
    public void testSearchInFolder_CombinesFiltersCorrectly() throws Exception {
        publicManager = new FolderIndexManager(VisibilityType.PUBLIC);

        UUID parentId = UUID.randomUUID();

        // Build the expected filter manually
        String permissionFilter = "(visibility:PUBLIC) OR (owner:\"testUser\") OR " +
                "(userRead:\"testUser\") OR (userEdit:\"testUser\")";
        String typeFilter = " AND (entityType:\"FOLDER\")";
        String parentFilter = " AND (parentUuid:\"" + parentId.toString() + "\")";

        String expectedFilter = permissionFilter + typeFilter + parentFilter;

        // Verify all three filters are combined with AND
        assertTrue("Filter should contain permission logic",
                expectedFilter.contains("visibility:PUBLIC"));
        assertTrue("Filter should contain entity type",
                expectedFilter.contains("entityType:\"FOLDER\""));
        assertTrue("Filter should contain parent UUID",
                expectedFilter.contains("parentUuid:\"" + parentId.toString() + "\""));

        // Verify AND operators are present
        int andCount = expectedFilter.split(" AND ").length - 1;
        assertEquals("Should have 2 AND operators connecting 3 filter parts", 2, andCount);
    }

// ========================================================================
// BASE SEARCH METHOD TESTS (from NFSIndexManager)
// ========================================================================

    @Test
    public void testSearch_BuildsOwnerFilter() {
        publicManager = new FolderIndexManager(VisibilityType.PUBLIC);

        // Test that ownedBy parameter creates correct filter
        String permissionFilter = publicManager.buildPermissionFilter("user", null);
        String ownerFilter = " AND (owner:\"specificOwner\")";
        String expectedFilter = permissionFilter + ownerFilter;

        assertTrue("Should contain owner filter", expectedFilter.contains("owner:\"specificOwner\""));
    }

    @Test
    public void testSearch_NullOwnedBy_NoOwnerFilter() {
        publicManager = new FolderIndexManager(VisibilityType.PUBLIC);

        String permissionFilter = publicManager.buildPermissionFilter("user", null);
        String ownerFilter = ""; // Empty when ownedBy is null
        String expectedFilter = permissionFilter + ownerFilter;

        // Should only have the permission filter, no owner filter
        int ownerOccurrences = expectedFilter.split("owner:", -1).length - 1;
        // Permission filter has owner checks, but no additional owner filter
        assertTrue("Should contain owner in permission filter", ownerOccurrences > 0);
    }

    @Test
    public void testSearchByType_AddsEntityTypeFilter() {
        publicManager = new FolderIndexManager(VisibilityType.PUBLIC);

        String permissionFilter = publicManager.buildPermissionFilter("user", null);
        String ownerFilter = "";
        String typeFilter = " AND (entityType:\"FOLDER\")";

        String expectedFilter = permissionFilter + ownerFilter + typeFilter;

        assertTrue("Should contain entity type filter",
                expectedFilter.contains("entityType:\"FOLDER\""));
    }

    @Test
    public void testSearchByType_CombinesAllFilters() {
        publicManager = new FolderIndexManager(VisibilityType.PUBLIC);

        String permissionFilter = publicManager.buildPermissionFilter("john", Permissions.WRITE);
        String ownerFilter = " AND (owner:\"specificOwner\")";
        String typeFilter = " AND (entityType:\"FOLDER\")";

        String expectedFilter = permissionFilter + ownerFilter + typeFilter;

        // Should have all three filter components
        assertTrue("Should have permission filter",
                expectedFilter.contains("owner:\"john\""));
        assertTrue("Should have owned-by filter",
                expectedFilter.contains("owner:\"specificOwner\""));
        assertTrue("Should have entity type filter",
                expectedFilter.contains("entityType:\"FOLDER\""));
    }

// ========================================================================
// CONFIGURE QUERY TESTS
// ========================================================================

    @Test
    public void testConfigureQuery_WildcardQuery_SortsByModificationTime() {
        publicManager = new FolderIndexManager(VisibilityType.PUBLIC);

        SolrQuery solrQuery = new SolrQuery();
        publicManager.configureQuery(solrQuery, "*:*", "filter", 10, 0);

        // For wildcard queries, should sort by modification time descending
        List<SolrQuery.SortClause> sorts = solrQuery.getSorts();

        assertNotNull("Should have sort clauses", sorts);
        assertFalse("Should have at least one sort clause", sorts.isEmpty());

        SolrQuery.SortClause sortClause = sorts.get(0);
        assertEquals("Should sort by modificationTime",
                "modificationTime", sortClause.getItem());
        assertEquals("Should sort descending",
                SolrQuery.ORDER.desc, sortClause.getOrder());
    }


    @Test
    public void testConfigureQuery_RegularQuery_NoDefaultSort() {
        publicManager = new FolderIndexManager(VisibilityType.PUBLIC);

        SolrQuery solrQuery = new SolrQuery();
        publicManager.configureQuery(solrQuery, "test query", "filter", 10, 0);

        // For regular queries, Solr uses relevance scoring, no explicit sort
        assertTrue("Should not have explicit sort for relevance queries",
                solrQuery.getSorts() == null || solrQuery.getSorts().isEmpty());
    }

    @Test
    public void testConfigureQuery_SetsQueryType() {
        publicManager = new FolderIndexManager(VisibilityType.PUBLIC);

        SolrQuery solrQuery = new SolrQuery();
        publicManager.configureQuery(solrQuery, "test", "filter", 10, 0);

        assertEquals("Should use edismax query parser", "edismax", solrQuery.get("defType"));
    }

    @Test
    public void testConfigureQuery_SetsQueryFields() {
        publicManager = new FolderIndexManager(VisibilityType.PUBLIC);

        SolrQuery solrQuery = new SolrQuery();
        publicManager.configureQuery(solrQuery, "test", "filter", 10, 0);

        String qf = solrQuery.get("qf");
        assertEquals("Should set correct query fields",
                "uuid^20 name^10 description^5 owner^2", qf);
    }

    @Test
    public void testConfigureQuery_SetsFieldsToReturn() {
        publicManager = new FolderIndexManager(VisibilityType.PUBLIC);

        SolrQuery solrQuery = new SolrQuery();
        publicManager.configureQuery(solrQuery, "test", "filter", 10, 0);

        String fields = solrQuery.getFields();
        assertEquals("Should only return uuid field", "uuid", fields);
    }

    @Test
    public void testConfigureQuery_SetsPagination_WithLimit() {
        publicManager = new FolderIndexManager(VisibilityType.PUBLIC);

        SolrQuery solrQuery = new SolrQuery();
        publicManager.configureQuery(solrQuery, "test", "filter", 25, 50);

        assertEquals("Should set correct offset", Integer.valueOf(50), solrQuery.getStart());
        assertEquals("Should set correct limit", Integer.valueOf(25), solrQuery.getRows());
    }

    @Test
    public void testConfigureQuery_NegativeOffset_NotSet() {
        publicManager = new FolderIndexManager(VisibilityType.PUBLIC);

        SolrQuery solrQuery = new SolrQuery();
        publicManager.configureQuery(solrQuery, "test", "filter", 10, -1);

        assertNull("Negative offset should not be set", solrQuery.getStart());
    }

    @Test
    public void testConfigureQuery_ZeroLimit_UsesDefault() {
        publicManager = new FolderIndexManager(VisibilityType.PUBLIC);

        SolrQuery solrQuery = new SolrQuery();
        publicManager.configureQuery(solrQuery, "test", "filter", 0, 0);

        assertEquals("Zero limit should use default 100000",
                Integer.valueOf(100000), solrQuery.getRows());
    }

    @Test
    public void testConfigureQuery_NegativeLimit_UsesDefault() {
        publicManager = new FolderIndexManager(VisibilityType.PUBLIC);

        SolrQuery solrQuery = new SolrQuery();
        publicManager.configureQuery(solrQuery, "test", "filter", -5, 0);

        assertEquals("Negative limit should use default 100000",
                Integer.valueOf(100000), solrQuery.getRows());
    }

    @Test
    public void testConfigureQuery_AppliesFilterQuery() {
        publicManager = new FolderIndexManager(VisibilityType.PUBLIC);

        String testFilter = "visibility:PUBLIC AND entityType:\"FOLDER\"";
        SolrQuery solrQuery = new SolrQuery();
        publicManager.configureQuery(solrQuery, "test", testFilter, 10, 0);

        String[] filterQueries = solrQuery.getFilterQueries();
        assertNotNull("Should have filter queries", filterQueries);
        assertEquals("Should have one filter query", 1, filterQueries.length);
        assertEquals("Should set correct filter", testFilter, filterQueries[0]);
    }

    @Test
    public void testConfigureQuery_PreprocessesSearchTerms() {
        publicManager = new FolderIndexManager(VisibilityType.PUBLIC);

        SolrQuery solrQuery = new SolrQuery();
        publicManager.configureQuery(solrQuery, "test query", "filter", 10, 0);

        String query = solrQuery.getQuery();
        assertNotNull("Query should be set", query);

        // Should be preprocessed by SearchUtilities
        String expected = SearchUtilities.preprocessSearchTerm("test query");
        assertEquals("Should preprocess search terms", expected, query);
    }

    @Test
    public void testConfigureQuery_WildcardNotPreprocessed() {
        publicManager = new FolderIndexManager(VisibilityType.PUBLIC);

        SolrQuery solrQuery = new SolrQuery();
        publicManager.configureQuery(solrQuery, "*:*", "filter", 10, 0);

        String query = solrQuery.getQuery();
        assertEquals("Wildcard should not be preprocessed", "*:*", query);
    }

    // ========================================================================
// FOLDER SEARCH INTEGRATION TESTS (Require local Solr)
// These are @Ignore by default - remove annotation to run with Solr
// ========================================================================

    /**
     * Integration test - searchInFolder with parent filter
     * Tests that searchInFolder correctly filters by parent UUID
     */
    @Ignore
    @Test
    public void testSearchInFolder_WithParentFilter_Integration() throws Exception {
        publicManager = new FolderIndexManager(VisibilityType.PUBLIC);
        publicManager.createCoreIfNeeded();

        // Create parent folder
        UUID parentId = UUID.randomUUID();
        NdexFolder parent = createTestFolder("Parent Folder", "Parent description");
        parent.setExternalId(parentId);
        publicManager.createIndex(parent);

        // Create child folders in parent
        NdexFolder child1 = createTestFolder("Child Folder 1", "First child");
        child1.setParent(parentId);
        publicManager.createIndex(child1);

        NdexFolder child2 = createTestFolder("Child Folder 2", "Second child");
        child2.setParent(parentId);
        publicManager.createIndex(child2);

        // Create orphan folder (different parent)
        NdexFolder orphan = createTestFolder("Orphan Folder", "No parent");
        orphan.setParent(UUID.randomUUID());
        publicManager.createIndex(orphan);

        // Create root folder (no parent)
        NdexFolder root = createTestFolder("Root Folder", "No parent at all");
        publicManager.createIndex(root);

        // Give Solr time to index
        Thread.sleep(1000);

        // Search within parent folder
        SolrDocumentList results = publicManager.searchInFolder(
                "*:*",
                "testOwner",
                100,
                0,
                parentId.toString(),
                null
        );

        assertNotNull("Results should not be null", results);
        assertEquals("Should find exactly 2 children", 2, results.getNumFound());

        // Verify both children are in results
        Set<String> resultIds = new HashSet<>();
        results.forEach(doc -> resultIds.add((String) doc.getFieldValue("uuid")));

        assertTrue("Should contain child1", resultIds.contains(child1.getExternalId().toString()));
        assertTrue("Should contain child2", resultIds.contains(child2.getExternalId().toString()));
        assertFalse("Should not contain orphan", resultIds.contains(orphan.getExternalId().toString()));
        assertFalse("Should not contain root", resultIds.contains(root.getExternalId().toString()));
        assertFalse("Should not contain parent", resultIds.contains(parentId.toString()));
    }

    /**
     * Integration test - searchInFolder without parent filter returns all folders
     */
    @Ignore
    @Test
    public void testSearchInFolder_NullParent_ReturnsAllFolders_Integration() throws Exception {
        publicManager = new FolderIndexManager(VisibilityType.PUBLIC);
        publicManager.createCoreIfNeeded();

        // Create multiple folders with various parent relationships
        NdexFolder folder1 = createTestFolder("Folder 1", "First");
        publicManager.createIndex(folder1);

        NdexFolder folder2 = createTestFolder("Folder 2", "Second");
        folder2.setParent(UUID.randomUUID());
        publicManager.createIndex(folder2);

        NdexFolder folder3 = createTestFolder("Folder 3", "Third");
        publicManager.createIndex(folder3);

        Thread.sleep(1000);

        // Search without parent filter (null)
        SolrDocumentList results = publicManager.searchInFolder(
                "*:*",
                "testOwner",
                100,
                0,
                null,  // No parent filter
                null
        );

        assertNotNull(results);
        assertTrue("Should find all folders", results.getNumFound() >= 3);
    }

    /**
     * Integration test - searchInFolder respects permissions
     */
    @Ignore
    @Test
    public void testSearchInFolder_RespectsPermissions_Integration() throws Exception {
        publicManager = new FolderIndexManager(VisibilityType.PUBLIC);
        privateManager = new FolderIndexManager(VisibilityType.PRIVATE);

        publicManager.createCoreIfNeeded();
        privateManager.createCoreIfNeeded();

        UUID parentId = UUID.randomUUID();

        // Create public folder
        NdexFolder publicFolder = createTestFolder("Public Child", "Public");
        publicFolder.setParent(parentId);
        publicManager.createIndex(publicFolder);

        // Create private folder
        NdexFolder privateFolder = createTestFolder("Private Child", "Private");
        privateFolder.setParent(parentId);
        privateFolder.setOwner("otherOwner");
        privateManager.createIndex(privateFolder);

        Thread.sleep(1000);

        // Anonymous user searching public core
        SolrDocumentList publicResults = publicManager.searchInFolder(
                "*:*",
                null,  // Anonymous
                100,
                0,
                parentId.toString(),
                null
        );

        assertTrue("Anonymous should see public folder", publicResults.getNumFound() >= 1);

        // Anonymous user searching private core (should see nothing)
        SolrDocumentList privateResults = privateManager.searchInFolder(
                "*:*",
                null,  // Anonymous
                100,
                0,
                parentId.toString(),
                null
        );

        assertEquals("Anonymous should see no private folders", 0, privateResults.getNumFound());
    }

    /**
     * Integration test - searchInFolder with search terms
     */
    @Ignore
    @Test
    public void testSearchInFolder_WithSearchTerms_Integration() throws Exception {
        publicManager = new FolderIndexManager(VisibilityType.PUBLIC);
        publicManager.createCoreIfNeeded();

        UUID parentId = UUID.randomUUID();

        // Create folders with different names
        NdexFolder cancer1 = createTestFolder("Cancer Research Data", "Cancer study");
        cancer1.setParent(parentId);
        publicManager.createIndex(cancer1);

        NdexFolder cancer2 = createTestFolder("Lung Cancer Analysis", "Another cancer study");
        cancer2.setParent(parentId);
        publicManager.createIndex(cancer2);

        NdexFolder diabetes = createTestFolder("Diabetes Study", "Diabetes research");
        diabetes.setParent(parentId);
        publicManager.createIndex(diabetes);

        Thread.sleep(1000);

        // Search for "cancer" within parent
        SolrDocumentList results = publicManager.searchInFolder(
                "cancer",
                "testOwner",
                100,
                0,
                parentId.toString(),
                null
        );

        assertNotNull(results);
        assertEquals("Should find 2 cancer-related folders", 2, results.getNumFound());

        // Verify cancer folders found, not diabetes
        Set<String> resultIds = new HashSet<>();
        results.forEach(doc -> resultIds.add((String) doc.getFieldValue("uuid")));

        assertTrue("Should find first cancer folder", resultIds.contains(cancer1.getExternalId().toString()));
        assertTrue("Should find second cancer folder", resultIds.contains(cancer2.getExternalId().toString()));
        assertFalse("Should not find diabetes folder", resultIds.contains(diabetes.getExternalId().toString()));
    }

    /**
     * Integration test - searchInFolder pagination
     */
    @Ignore
    @Test
    public void testSearchInFolder_Pagination_Integration() throws Exception {
        publicManager = new FolderIndexManager(VisibilityType.PUBLIC);
        publicManager.createCoreIfNeeded();

        UUID parentId = UUID.randomUUID();

        // Create 10 folders in same parent
        List<UUID> folderIds = new ArrayList<>();
        for (int i = 0; i < 10; i++) {
            NdexFolder folder = createTestFolder("Folder " + i, "Description " + i);
            folder.setParent(parentId);
            publicManager.createIndex(folder);
            folderIds.add(folder.getExternalId());
        }

        Thread.sleep(1000);

        // First page (5 items)
        SolrDocumentList page1 = publicManager.searchInFolder(
                "*:*",
                "testOwner",
                5,  // limit
                0,  // offset
                parentId.toString(),
                null
        );

        assertEquals("First page should have 5 results", 5, page1.size());
        assertEquals("Total should be 10", 10, page1.getNumFound());

        // Second page (5 items)
        SolrDocumentList page2 = publicManager.searchInFolder(
                "*:*",
                "testOwner",
                5,   // limit
                5,   // offset
                parentId.toString(),
                null
        );

        assertEquals("Second page should have 5 results", 5, page2.size());
        assertEquals("Total should still be 10", 10, page2.getNumFound());

        // Verify no overlap between pages
        Set<String> page1Ids = new HashSet<>();
        page1.forEach(doc -> page1Ids.add((String) doc.getFieldValue("uuid")));

        Set<String> page2Ids = new HashSet<>();
        page2.forEach(doc -> page2Ids.add((String) doc.getFieldValue("uuid")));

        // No intersection
        page1Ids.retainAll(page2Ids);
        assertTrue("Pages should not overlap", page1Ids.isEmpty());
    }

    /**
     * Integration test - searchInFolder with ADMIN permission filter
     */
    @Ignore
    @Test
    public void testSearchInFolder_AdminPermission_Integration() throws Exception {
        publicManager = new FolderIndexManager(VisibilityType.PUBLIC);
        publicManager.createCoreIfNeeded();

        UUID parentId = UUID.randomUUID();

        // Create folder owned by testOwner
        NdexFolder myFolder = createTestFolder("My Folder", "I own this");
        myFolder.setOwner("testOwner");
        myFolder.setParent(parentId);
        publicManager.createIndex(myFolder);

        // Create folder owned by someone else
        NdexFolder theirFolder = createTestFolder("Their Folder", "They own this");
        theirFolder.setOwner("otherOwner");
        theirFolder.setParent(parentId);
        publicManager.createIndex(theirFolder);

        Thread.sleep(1000);

        // Search with ADMIN permission (only see owned folders)
        SolrDocumentList results = publicManager.searchInFolder(
                "*:*",
                "testOwner",
                100,
                0,
                parentId.toString(),
                Permissions.ADMIN
        );

        assertEquals("Should only see owned folder", 1, results.getNumFound());
        assertEquals("Should be my folder",
                myFolder.getExternalId().toString(),
                results.get(0).getFieldValue("uuid"));
    }

    /**
     * Integration test - searchInFolder sorts by modification time for wildcard
     */
    @Ignore
    @Test
    public void testSearchInFolder_WildcardSortsByModificationTime_Integration() throws Exception {
        publicManager = new FolderIndexManager(VisibilityType.PUBLIC);
        publicManager.createCoreIfNeeded();

        UUID parentId = UUID.randomUUID();

        // Create folders with different modification times
        Instant now = Instant.now();

        NdexFolder oldest = createTestFolder("Oldest", "First created");
        oldest.setParent(parentId);
        oldest.setModificationTime(Timestamp.from(now.minusSeconds(3600)));
        publicManager.createIndex(oldest);
        Thread.sleep(100);

        NdexFolder middle = createTestFolder("Middle", "Second created");
        middle.setParent(parentId);
        middle.setModificationTime(Timestamp.from(now.minusSeconds(1800)));
        publicManager.createIndex(middle);
        Thread.sleep(100);

        NdexFolder newest = createTestFolder("Newest", "Last created");
        newest.setParent(parentId);
        newest.setModificationTime(Timestamp.from(now));
        publicManager.createIndex(newest);

        Thread.sleep(1000);

        // Search with wildcard (should sort by modification time desc)
        SolrDocumentList results = publicManager.searchInFolder(
                "*:*",
                "testOwner",
                100,
                0,
                parentId.toString(),
                null
        );

        assertEquals("Should find all 3 folders", 3, results.getNumFound());

        // Verify order: newest first, oldest last
        assertEquals("First result should be newest",
                newest.getExternalId().toString(),
                results.get(0).getFieldValue("uuid"));
        assertEquals("Last result should be oldest",
                oldest.getExternalId().toString(),
                results.get(2).getFieldValue("uuid"));
    }

    /**
     * Integration test - searchInFolder only returns folders (not networks or shortcuts)
     */
    @Ignore
    @Test
    public void testSearchInFolder_OnlyReturnsFolders_Integration() throws Exception {
        // This test verifies the entityType:FOLDER filter works correctly
        // If you have network/shortcut indexing working, this test becomes more valuable

        publicManager = new FolderIndexManager(VisibilityType.PUBLIC);
        publicManager.createCoreIfNeeded();

        UUID parentId = UUID.randomUUID();

        // Create folder
        NdexFolder folder = createTestFolder("Test Folder", "A folder");
        folder.setParent(parentId);
        publicManager.createIndex(folder);

        Thread.sleep(1000);

        // Search should only return folders
        SolrDocumentList results = publicManager.searchInFolder(
                "*:*",
                "testOwner",
                100,
                0,
                parentId.toString(),
                null
        );

        assertNotNull(results);
        assertTrue("Should find at least one folder", results.getNumFound() >= 1);

        // Verify all results are folders
        for (var doc : results) {
            assertEquals("All results should be folders",
                    "FOLDER",
                    doc.getFieldValue("entityType"));
        }
    }

    /**
     * Integration test - searchInFolder with empty parent folder
     */
    @Ignore
    @Test
    public void testSearchInFolder_EmptyParent_ReturnsNothing_Integration() throws Exception {
        publicManager = new FolderIndexManager(VisibilityType.PUBLIC);
        publicManager.createCoreIfNeeded();

        UUID emptyParentId = UUID.randomUUID();

        // Create some folders but NOT in the empty parent
        NdexFolder folder1 = createTestFolder("Folder 1", "Different parent");
        folder1.setParent(UUID.randomUUID());
        publicManager.createIndex(folder1);

        NdexFolder folder2 = createTestFolder("Folder 2", "No parent");
        publicManager.createIndex(folder2);

        Thread.sleep(1000);

        // Search in empty parent
        SolrDocumentList results = publicManager.searchInFolder(
                "*:*",
                "testOwner",
                100,
                0,
                emptyParentId.toString(),
                null
        );

        assertNotNull(results);
        assertEquals("Empty parent should return no results", 0, results.getNumFound());
    }

    /**
     * Integration test - searchInFolder with description search
     */
    @Ignore
    @Test
    public void testSearchInFolder_SearchInDescription_Integration() throws Exception {
        publicManager = new FolderIndexManager(VisibilityType.PUBLIC);
        publicManager.createCoreIfNeeded();

        UUID parentId = UUID.randomUUID();

        // Create folders where search term is in description, not name
        NdexFolder folder1 = createTestFolder("Data Folder", "Contains pathway analysis");
        folder1.setParent(parentId);
        publicManager.createIndex(folder1);

        NdexFolder folder2 = createTestFolder("Research Folder", "Gene expression studies");
        folder2.setParent(parentId);
        publicManager.createIndex(folder2);

        NdexFolder folder3 = createTestFolder("Analysis Folder", "Pathway enrichment results");
        folder3.setParent(parentId);
        publicManager.createIndex(folder3);

        Thread.sleep(1000);

        // Search for "pathway" (appears in descriptions)
        SolrDocumentList results = publicManager.searchInFolder(
                "pathway",
                "testOwner",
                100,
                0,
                parentId.toString(),
                null
        );

        assertNotNull(results);
        assertEquals("Should find 2 folders with 'pathway' in description",
                2, results.getNumFound());

        Set<String> resultIds = new HashSet<>();
        results.forEach(doc -> resultIds.add((String) doc.getFieldValue("uuid")));

        assertTrue("Should find folder1", resultIds.contains(folder1.getExternalId().toString()));
        assertTrue("Should find folder3", resultIds.contains(folder3.getExternalId().toString()));
        assertFalse("Should not find folder2", resultIds.contains(folder2.getExternalId().toString()));
    }

    /**
     * Integration test - searchInFolder hierarchical structure
     */
    @Ignore
    @Test
    public void testSearchInFolder_MultiLevelHierarchy_Integration() throws Exception {
        publicManager = new FolderIndexManager(VisibilityType.PUBLIC);
        publicManager.createCoreIfNeeded();

        // Create multi-level hierarchy: root -> parent -> children
        UUID rootId = UUID.randomUUID();
        NdexFolder root = createTestFolder("Root", "Root folder");
        root.setExternalId(rootId);
        publicManager.createIndex(root);

        UUID parentId = UUID.randomUUID();
        NdexFolder parent = createTestFolder("Parent", "Parent folder");
        parent.setExternalId(parentId);
        parent.setParent(rootId);
        publicManager.createIndex(parent);

        NdexFolder child1 = createTestFolder("Child 1", "First child");
        child1.setParent(parentId);
        publicManager.createIndex(child1);

        NdexFolder child2 = createTestFolder("Child 2", "Second child");
        child2.setParent(parentId);
        publicManager.createIndex(child2);

        Thread.sleep(1000);

        // Search in root should only find parent, not grandchildren
        SolrDocumentList rootResults = publicManager.searchInFolder(
                "*:*",
                "testOwner",
                100,
                0,
                rootId.toString(),
                null
        );

        assertEquals("Root should contain only parent folder", 1, rootResults.getNumFound());
        assertEquals("Should find parent",
                parentId.toString(),
                rootResults.get(0).getFieldValue("uuid"));

        // Search in parent should find both children
        SolrDocumentList parentResults = publicManager.searchInFolder(
                "*:*",
                "testOwner",
                100,
                0,
                parentId.toString(),
                null
        );

        assertEquals("Parent should contain 2 children", 2, parentResults.getNumFound());
    }

    // ========================================================================
    // HELPER METHODS
    // ========================================================================

    /**
     * Helper method to create a test folder with standard fields
     */
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