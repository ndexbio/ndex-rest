package org.ndexbio.common.models.search;

import org.apache.solr.client.solrj.SolrQuery;
import org.apache.solr.client.solrj.response.QueryResponse;
import org.apache.solr.common.SolrDocumentList;
import org.easymock.Capture;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;
import org.ndexbio.common.solr.SolrClientWrapper;
import org.ndexbio.model.object.SimpleFileQuery;
import org.ndexbio.model.object.network.VisibilityType;
import org.ndexbio.rest.Configuration;

import java.lang.reflect.Field;

import static org.easymock.EasyMock.*;
import static org.junit.Assert.assertEquals;

/**
 * Unit tests for NFSSearchProvider's Solr query configuration.
 * Verifies the edgeless-network demotion boost (issue #116) is applied to the
 * unified file-search path via the mocked SolrClientWrapper. No live Solr required.
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
}
