package org.ndexbio.common.solr;


import java.util.Collection;
import java.util.Collections;
import java.util.LinkedList;
import org.apache.solr.client.solrj.SolrClient;
import org.apache.solr.client.solrj.SolrQuery;
import org.apache.solr.client.solrj.SolrRequest;
import org.apache.solr.client.solrj.request.CoreAdminRequest;
import org.apache.solr.client.solrj.response.CoreAdminResponse;
import org.apache.solr.client.solrj.response.QueryResponse;
import org.apache.solr.common.SolrDocument;
import org.apache.solr.common.SolrDocumentList;
import org.apache.solr.common.SolrInputDocument;
import org.apache.solr.common.util.NamedList;
import static org.easymock.EasyMock.createMock;
import static org.easymock.EasyMock.expect;
import static org.easymock.EasyMock.expectLastCall;
import static org.easymock.EasyMock.replay;
import static org.easymock.EasyMock.verify;
import org.junit.After;
import org.junit.Test;
import org.junit.Ignore;
import static org.junit.Assert.*;
import org.junit.Before;



/**
 *
 * @author churas
 */
public class TestSolrClientWrapperImpl {


	/*
    @Test
    public void testCreateCoreIfNeeded_CoreDoesNotExist() throws Exception {
        String coreName = "testCore";
		SolrObjectFactory mockFactory = createMock(SolrObjectFactory.class);
        SolrClient baseMockSolrClient = createMock(SolrClient.class);
        CoreAdminResponse mockResponse = createMock(CoreAdminResponse.class);
        NamedList<Object> mockStatusList = new NamedList<>();
        mockStatusList.add("status", new NamedList<>());

        expect(mockFactory.getCoreAdminRequestGetStatus(coreName)).andReturn(mockResponse);
        expect(mockResponse.getStatus()).andReturn(0);
        expect(mockResponse.getResponse()).andReturn(mockStatusList);

        CoreAdminRequest.Create mockCreateRequest = createMock(CoreAdminRequest.Create.class);
        expect(mockFactory.getCoreAdminRequestCreate()).andReturn(mockCreateRequest);
        mockCreateRequest.setCoreName(coreName);
        mockCreateRequest.setConfigSet(coreName);
        expect(mockCreateRequest.process(baseMockSolrClient)).andReturn(mockResponse);

        replay(baseMockSolrClient, mockFactory, mockResponse, mockCreateRequest);
		var solrClientWrapper = new SolrClientWrapperImpl(mockFactory);
        solrClientWrapper.createCoreIfNeeded(coreName);
		verify(mockFactory);
    }
	*/

    @Test
    public void testDropCore() throws Exception {
		String coreName = "testCore";
		
		SolrObjectFactory mockFactory = createMock(SolrObjectFactory.class);
        expect(mockFactory.getSolrClient(null)).andReturn(null);
		expect(mockFactory.getCoreAdminRequestUnloadCore(coreName, true, true)).andReturn(null);
		expectLastCall();
		replay(mockFactory);

        var solrClientWrapper = new SolrClientWrapperImpl(mockFactory);
        solrClientWrapper.dropCore(coreName);
		verify(mockFactory);
    }
/*
    @Test
    public void testCommit_WithDocuments() throws Exception {
        String coreName = "testCore";
        Collection<SolrInputDocument> documents = Collections.singletonList(new SolrInputDocument());

        SolrClient mockCoreClient = createMock(SolrClient.class);

        expect(mockFactory.getSolrClient(coreName)).andReturn(mockCoreClient);
        mockCoreClient.add(documents);
        expectLastCall();
        mockCoreClient.commit(false, true, true);
        expectLastCall();

        replay(mockFactory, mockCoreClient);

        solrClientWrapper.commit(coreName, documents);
    }

    @Test
    public void testCommit_WithoutDocuments() throws Exception {
        String coreName = "testCore";
        Collection<SolrInputDocument> documents = Collections.emptyList();

        SolrClient mockCoreClient = createMock(SolrClient.class);

        expect(mockFactory.getSolrClient(coreName)).andReturn(mockCoreClient);
        mockCoreClient.commit(false, true, true);
        expectLastCall();

        replay(mockFactory, mockCoreClient);

        solrClientWrapper.commit(coreName, documents);
    }
	*/
    @Test
    public void testDelete() throws Exception {
        String coreName = "testCore";
        String documentId = "12345";
        boolean commit = true;
		SolrObjectFactory mockFactory = createMock(SolrObjectFactory.class);
		
		SolrClient baseMockSolrClient = createMock(SolrClient.class);
		
        expect(mockFactory.getSolrClient(null)).andReturn(baseMockSolrClient);
		expectLastCall();
        SolrClient mockCoreClient = createMock(SolrClient.class);

        expect(mockFactory.getSolrClient(coreName)).andReturn(mockCoreClient);
        expect(mockCoreClient.deleteById(documentId)).andReturn(null);
        expectLastCall();
        
		expect(mockCoreClient.commit(false, true, true)).andReturn(null);
        expectLastCall();

        replay(mockFactory, mockCoreClient, baseMockSolrClient);
		var solrClientWrapper = new SolrClientWrapperImpl(mockFactory);
        solrClientWrapper.delete(coreName, documentId, commit);
		verify();
    }
	
    @Test
    public void testQuery() throws Exception {
		SolrObjectFactory mockFactory = createMock(SolrObjectFactory.class);
		SolrClient baseMockSolrClient = createMock(SolrClient.class);
		
        expect(mockFactory.getSolrClient(null)).andReturn(baseMockSolrClient);
		expectLastCall();
		
		
        String coreName = "testCore";
        SolrQuery query = new SolrQuery("testQuery");
        QueryResponse mockResponse = createMock(QueryResponse.class);

        expect(baseMockSolrClient.query(coreName, query, SolrRequest.METHOD.POST)).andReturn(mockResponse);

        replay(baseMockSolrClient, mockFactory, mockResponse);
		var solrClientWrapper = new SolrClientWrapperImpl(mockFactory);
		
        QueryResponse response = solrClientWrapper.query(coreName, query);

        assertEquals(mockResponse, response);
		verify(mockFactory, baseMockSolrClient);
    }
    
    @Test
    public void testClose() throws Exception {
		SolrObjectFactory mockFactory = createMock(SolrObjectFactory.class);
		SolrClient mockSolrClient = createMock(SolrClient.class);
		
		mockSolrClient.close();
		expectLastCall();
		
        expect(mockFactory.getSolrClient(null)).andReturn(mockSolrClient);
		expectLastCall();
		replay(mockFactory, mockSolrClient);

        var solrClientWrapper = new SolrClientWrapperImpl(mockFactory);
        solrClientWrapper.close();
		verify(mockFactory, mockSolrClient);
    }

	/**
	 * Test of methods on a local solr with private-nfs and public-nfs configsets
	 * @throws Exception 
	 */
	@Ignore
	public void testCreateCoreAndPerformSomeIndexes() throws Exception {
		SolrObjectFactoryImpl factory = new SolrObjectFactoryImpl("http://localhost:8983/solr");
		
		SolrClientWrapperImpl client = new SolrClientWrapperImpl(factory);
		
		client.createCoreIfNeeded("private-nfs");
		client.dropCore("private-nfs");
		client.createCoreIfNeeded("private-nfs");
		
		client.createCoreIfNeeded("public-nfs");
		client.dropCore("public-nfs");
		client.createCoreIfNeeded("public-nfs");
	
		LinkedList<SolrInputDocument> docs = new LinkedList<>();
		for (int i = 0 ;i < 500; i++){
			var doc = new SolrInputDocument();
			doc.addField("uuid", Integer.toString(i));
			doc.addField("entityType", "folder");
			
		
			docs.add(doc);
		}
		client.commit("private-nfs", docs);
		client.commit("public-nfs", docs);
		
		SolrQuery solrQuery = new SolrQuery("*:*");
		solrQuery.setRows(100000); // Set a large number for all documents
		QueryResponse rsp = client.query("private-nfs", solrQuery);
		SolrDocumentList sdl = rsp.getResults();
		for(SolrDocument s : sdl){
			System.out.println(s.toString());
		}
		client.delete("private-nfs","0" , true);
		
		solrQuery = new SolrQuery("*:*");
		solrQuery.setRows(100000); // Set a large number for all documents
		rsp = client.query("private-nfs", solrQuery);
		sdl = rsp.getResults();
		for(SolrDocument s : sdl){
			System.out.println(s.toString());
		}
		client.delete("public-nfs","1" , true);
	}

}
