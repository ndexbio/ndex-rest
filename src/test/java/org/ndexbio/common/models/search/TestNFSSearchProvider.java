package org.ndexbio.common.models.search;

import org.apache.solr.client.solrj.SolrQuery;
import org.apache.solr.client.solrj.response.QueryResponse;
import org.apache.solr.common.SolrDocumentList;
import org.easymock.Capture;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;
import org.ndexbio.common.solr.SolrClientWrapper;
import org.ndexbio.model.object.FileItemSummary;
import org.ndexbio.model.object.FileSearchResult;
import org.ndexbio.model.object.NdexFolder;
import org.ndexbio.model.object.SimpleFileQuery;
import org.ndexbio.model.object.network.NetworkSummary;
import org.ndexbio.model.object.network.VisibilityType;
import org.ndexbio.rest.Configuration;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.sql.Timestamp;
import java.util.UUID;

import static org.easymock.EasyMock.*;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertNotNull;

/**
 * Unit tests for NFSSearchProvider's Solr query configuration and result mapping.
 * Verifies the edgeless-network demotion boost (issue #116) is applied to the
 * unified file-search path via the mocked SolrClientWrapper, and that each
 * per-type mapper populates the fields the FileItemSummary contract promises
 * (issue #162). No live Solr required.
 */
public class TestNFSSearchProvider {

    private static Configuration savedInstance;

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

    @Test
    public void testSearchFiles_AppliesEdgelessBoost() throws Exception {
        // Empty result set so no DAO post-processing is triggered.
        SolrDocumentList emptyResults = new SolrDocumentList();
        emptyResults.setNumFound(0);

        QueryResponse mockResponse = createMock(QueryResponse.class);
        expect(mockResponse.getResults()).andReturn(emptyResults);
        replay(mockResponse);

        SolrClientWrapper mockWrapper = createMock(SolrClientWrapper.class);
        Capture<SolrQuery> queryCapture = Capture.newInstance();
        expect(mockWrapper.query(anyString(), capture(queryCapture))).andReturn(mockResponse);
        replay(mockWrapper);

        NFSSearchProvider provider = new NFSSearchProvider(mockWrapper, 100);

        SimpleFileQuery query = new SimpleFileQuery();
        query.setSearchString("cancer");

        provider.searchFiles(query, VisibilityType.PUBLIC, null, 0, 10);

        verify(mockWrapper);
        assertEquals("map(def(edgeCount,1),0,0,0.01,1)", queryCapture.getValue().get("boost"));
    }

    @Test
    public void testSearchFiles_ReportsSolrNumFoundNotPageSize() throws Exception {
        // Solr reports a large total but returns no docs on this page (empty page),
        // so no DAO hydration is needed. The result total must reflect Solr's
        // numFound, not the number of summaries on this page.
        SolrDocumentList results = new SolrDocumentList();
        results.setNumFound(4269);

        QueryResponse mockResponse = createMock(QueryResponse.class);
        expect(mockResponse.getResults()).andReturn(results);
        replay(mockResponse);

        SolrClientWrapper mockWrapper = createMock(SolrClientWrapper.class);
        expect(mockWrapper.query(anyString(), anyObject(SolrQuery.class))).andReturn(mockResponse);
        replay(mockWrapper);

        NFSSearchProvider provider = new NFSSearchProvider(mockWrapper, 100);
        SimpleFileQuery query = new SimpleFileQuery();
        query.setSearchString("cancer");

        FileSearchResult result = provider.searchFiles(query, VisibilityType.PUBLIC, null, 0, 10);

        verify(mockWrapper);
        assertEquals(4269L, result.getNumFound());
    }

    @Test
    public void testSearchFiles_ReportsRequestedStartOffset() throws Exception {
        // The reported start must be the absolute offset actually applied to Solr (skipBlocks),
        // NOT skipBlocks * blockSize. Empty page so no DAO hydration is needed.
        SolrDocumentList results = new SolrDocumentList();
        results.setNumFound(4269);

        QueryResponse mockResponse = createMock(QueryResponse.class);
        expect(mockResponse.getResults()).andReturn(results);
        replay(mockResponse);

        SolrClientWrapper mockWrapper = createMock(SolrClientWrapper.class);
        expect(mockWrapper.query(anyString(), anyObject(SolrQuery.class))).andReturn(mockResponse);
        replay(mockWrapper);

        NFSSearchProvider provider = new NFSSearchProvider(mockWrapper, 100);
        SimpleFileQuery query = new SimpleFileQuery();
        query.setSearchString("cancer");

        FileSearchResult result = provider.searchFiles(query, VisibilityType.PUBLIC, null, 50, 25);

        verify(mockWrapper);
        assertEquals(50L, result.getStart());
    }

    // ── Issue #162: FOLDER search results must carry visibility ──────────────
    // searchFiles hydrates Solr hits from Postgres and maps them per type. The
    // NETWORK mapper sets visibility (mapNetworkToSummary) and the SHORTCUT path
    // sets it in PostgresShortcutDAO, but mapFolderToSummary omits it — and since
    // FileItemSummary is @JsonInclude(NON_NULL), the key disappears from the JSON
    // entirely rather than serializing as null. These tests exercise the mapper
    // directly: the tests above deliberately use empty Solr result sets so DAO
    // hydration never runs, because hydration needs a live DAOFactory, so there is
    // no way to reach the mapper through searchFiles here. Reflection is used
    // rather than widening the mapper's visibility (this class already reflects on
    // Configuration.INSTANCE above).

    private static FileItemSummary mapFolder(NdexFolder folder) throws Exception {
        NFSSearchProvider provider = new NFSSearchProvider(createMock(SolrClientWrapper.class), 100);
        Method mapper = NFSSearchProvider.class.getDeclaredMethod("mapFolderToSummary", NdexFolder.class);
        mapper.setAccessible(true);
        return (FileItemSummary) mapper.invoke(provider, folder);
    }

    /** owner_id must be a parseable UUID string: the mapper calls UUID.fromString on it unguarded. */
    private static NdexFolder folderFixture(VisibilityType visibility) {
        NdexFolder folder = new NdexFolder();
        folder.setExternalId(UUID.randomUUID());
        folder.setName("NCI PID");
        folder.setDescription("curated collection");
        folder.setOwner("nci-pid");
        folder.setOwner_id(UUID.randomUUID().toString());
        folder.setCreationTime(new Timestamp(1_500_000_000_000L));
        folder.setModificationTime(new Timestamp(1_600_000_000_000L));
        folder.setVisibility(visibility);
        return folder;
    }

    @Test
    public void testMapFolderToSummary_ReportsPublicVisibility() throws Exception {
        FileItemSummary summary = mapFolder(folderFixture(VisibilityType.PUBLIC));

        assertEquals("PUBLIC", summary.getVisibility());
    }

    @Test
    public void testMapFolderToSummary_ReportsPrivateVisibility() throws Exception {
        // Pins that the value comes from the folder row rather than a hardcoded constant.
        FileItemSummary summary = mapFolder(folderFixture(VisibilityType.PRIVATE));

        assertEquals("PRIVATE", summary.getVisibility());
    }

    @Test
    public void testMapFolderToSummary_ToleratesNullVisibility() throws Exception {
        // Legacy rows can have a null visibility column; mapNetworkToSummary already
        // guards for this, so the folder mapper must not NPE either.
        FileItemSummary summary = mapFolder(folderFixture(null));

        assertNull(summary.getVisibility());
    }

    // ── isCertified must reach BOTH the top level and the legacy attributes key ────
    // mapNetworkToSummary sets isCertified twice: once on the FileItemSummary itself
    // (matching the folder/home/shared listings) and once inside attributes, where it
    // predates the top-level field and is kept so existing consumers keep working.
    // The two are asserted together on purpose — a change that drops or diverges one
    // of them is exactly the regression this guards, and neither assertion alone
    // would catch it. Reflection is used for the same reason as mapFolder above:
    // hydration through searchFiles needs a live DAOFactory.

    private static FileItemSummary mapNetwork(NetworkSummary network) throws Exception {
        NFSSearchProvider provider = new NFSSearchProvider(createMock(SolrClientWrapper.class), 100);
        Method mapper = NFSSearchProvider.class.getDeclaredMethod("mapNetworkToSummary", NetworkSummary.class);
        mapper.setAccessible(true);
        return (FileItemSummary) mapper.invoke(provider, network);
    }

    /** visibility and indexLevel are left null deliberately: the mapper guards both. */
    private static NetworkSummary networkFixture(boolean certified) {
        NetworkSummary network = new NetworkSummary();
        network.setExternalId(UUID.randomUUID());
        network.setName("BindingDB");
        network.setIsCertified(certified);
        return network;
    }

    @Test
    public void testMapNetworkToSummary_ReportsCertified() throws Exception {
        FileItemSummary summary = mapNetwork(networkFixture(true));

        assertEquals(Boolean.TRUE, summary.getIsCertified());
        assertEquals(Boolean.TRUE, summary.getAttributes().get("isCertified"));
    }

    @Test
    public void testMapNetworkToSummary_ReportsNotCertified() throws Exception {
        // Pins that the value is read from the network row rather than hardcoded, and that
        // false is carried through as a value instead of being dropped.
        FileItemSummary summary = mapNetwork(networkFixture(false));

        assertEquals(Boolean.FALSE, summary.getIsCertified());
        assertEquals(Boolean.FALSE, summary.getAttributes().get("isCertified"));
    }

    /**
     * A network entry must always carry the key. NetworkSummary.isCertified is a primitive
     * boolean, so search results can never omit it; the DAO listings now match that. Absence
     * is reserved for folders and shortcuts, which have no certification state.
     */
    @Test
    public void testMapNetworkToSummary_AlwaysEmitsCertifiedForNetworks() throws Exception {
        assertNotNull(mapNetwork(new NetworkSummary()).getIsCertified());
    }
}
