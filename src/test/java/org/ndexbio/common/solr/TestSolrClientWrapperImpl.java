package org.ndexbio.common.solr;


import java.io.IOException;
import java.net.ConnectException;
import java.net.SocketTimeoutException;
import java.util.Collection;
import java.util.Collections;
import java.util.concurrent.TimeoutException;
import org.apache.solr.client.solrj.impl.BaseHttpSolrClient;
import org.ndexbio.model.exceptions.BadRequestException;
import org.ndexbio.model.exceptions.NdexException;
import org.apache.solr.client.solrj.SolrClient;
import org.apache.solr.client.solrj.SolrQuery;
import org.apache.solr.client.solrj.SolrRequest;
import org.apache.solr.client.solrj.SolrServerException;
import org.apache.solr.client.solrj.request.ConfigSetAdminRequest;
import org.apache.solr.client.solrj.request.CoreAdminRequest;
import org.apache.solr.client.solrj.response.CoreAdminResponse;
import org.apache.solr.client.solrj.response.QueryResponse;
import org.apache.solr.common.SolrInputDocument;
import org.apache.solr.common.util.NamedList;
import static org.easymock.EasyMock.anyObject;
import static org.easymock.EasyMock.createMock;
import static org.easymock.EasyMock.expect;
import static org.easymock.EasyMock.expectLastCall;
import static org.easymock.EasyMock.isNull;
import static org.easymock.EasyMock.same;
import static org.easymock.EasyMock.replay;
import static org.easymock.EasyMock.verify;
import org.junit.After;
import org.junit.Test;
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
		expect(mockFactory.getCoreAdminRequestUnloadCore(coreName, true, true)).andReturn(null);
		expectLastCall();
		replay(mockFactory);

        var solrClientWrapper = new SolrClientWrapperImpl(mockFactory);
        solrClientWrapper.dropCore(coreName);
		verify(mockFactory);
    }

	/**
	 * A core that is not there is the normal drop-before-rebuild case, so it must be swallowed
	 * silently rather than failing the caller's rebuild.
	 */
	@Test
	public void dropCoreSwallowsAnAbsentCore() throws Exception {
		String coreName = "testCore";

		SolrObjectFactory mockFactory = createMock(SolrObjectFactory.class);
		expect(mockFactory.getCoreAdminRequestUnloadCore(coreName, true, true))
				.andThrow(new BaseHttpSolrClient.RemoteSolrException(
						"http://localhost:8983/solr", 400,
						"Cannot unload non-existent core [" + coreName + "]", null));
		replay(mockFactory);

		var solrClientWrapper = new SolrClientWrapperImpl(mockFactory);
		solrClientWrapper.dropCore(coreName);

		verify(mockFactory);
	}

	/**
	 * A null message cannot be classified as the benign case, so it must surface as an NdexException
	 * rather than NPE while trying to inspect the message.
	 */
	@Test
	public void dropCoreRethrowsWhenTheMessageIsNull() throws Exception {
		String coreName = "testCore";

		SolrObjectFactory mockFactory = createMock(SolrObjectFactory.class);
		expect(mockFactory.getCoreAdminRequestUnloadCore(coreName, true, true))
				.andThrow(new BaseHttpSolrClient.RemoteSolrException(
						"http://localhost:8983/solr", 500, null, null));
		replay(mockFactory);

		var solrClientWrapper = new SolrClientWrapperImpl(mockFactory);
		try {
			solrClientWrapper.dropCore(coreName);
			fail("an unclassifiable Solr failure must not be swallowed");
		} catch (NdexException expected) {
			assertNotNull(expected.getMessage());
		}
		verify(mockFactory);
	}

	/** Anything other than the absent-core case is the caller's problem to log and handle. */
	@Test
	public void dropCoreRethrowsUnexpectedSolrErrors() throws Exception {
		String coreName = "testCore";

		SolrObjectFactory mockFactory = createMock(SolrObjectFactory.class);
		expect(mockFactory.getCoreAdminRequestUnloadCore(coreName, true, true))
				.andThrow(new BaseHttpSolrClient.RemoteSolrException(
						"http://localhost:8983/solr", 500, "something else went wrong", null));
		replay(mockFactory);

		var solrClientWrapper = new SolrClientWrapperImpl(mockFactory);
		try {
			solrClientWrapper.dropCore(coreName);
			fail("an unexpected Solr failure must be rethrown");
		} catch (NdexException expected) {
			assertTrue(expected.getMessage().contains("something else went wrong"));
		}
		verify(mockFactory);
	}
    @Test
    public void testCommit_WithDocuments() throws Exception {
        String coreName = "testCore";
        Collection<SolrInputDocument> documents = Collections.singletonList(new SolrInputDocument());

		SolrObjectFactory mockFactory = createMock(SolrObjectFactory.class);
        SolrClient mockSolrClient = createMock(SolrClient.class);

        expect(mockFactory.getSolrClient()).andReturn(mockSolrClient).anyTimes();
        expect(mockSolrClient.add(coreName, documents)).andReturn(null);
        expect(mockSolrClient.commit(coreName, false, true, true)).andReturn(null);

        replay(mockFactory, mockSolrClient);
		var solrClientWrapper = new SolrClientWrapperImpl(mockFactory);
        solrClientWrapper.commit(coreName, documents);
		verify(mockSolrClient);
    }

    @Test
    public void testDelete() throws Exception {
        String coreName = "testCore";
        String documentId = "12345";
        boolean commit = true;
		SolrObjectFactory mockFactory = createMock(SolrObjectFactory.class);
        SolrClient mockSolrClient = createMock(SolrClient.class);

        expect(mockFactory.getSolrClient()).andReturn(mockSolrClient).anyTimes();
        expect(mockSolrClient.deleteById(coreName, documentId)).andReturn(null);
		expect(mockSolrClient.commit(coreName, false, true, true)).andReturn(null);

        replay(mockFactory, mockSolrClient);
		var solrClientWrapper = new SolrClientWrapperImpl(mockFactory);
        solrClientWrapper.delete(coreName, documentId, commit);
		verify(mockSolrClient);
    }
	
    @Test
    public void testQuery() throws Exception {
		SolrObjectFactory mockFactory = createMock(SolrObjectFactory.class);
		SolrClient mockSolrClient = createMock(SolrClient.class);
		
        expect(mockFactory.getSolrClient()).andReturn(mockSolrClient);

        String coreName = "testCore";
        SolrQuery query = new SolrQuery("testQuery");
        QueryResponse mockResponse = createMock(QueryResponse.class);

        expect(mockSolrClient.query(coreName, query, SolrRequest.METHOD.POST)).andReturn(mockResponse);

        replay(mockSolrClient, mockFactory, mockResponse);
		var solrClientWrapper = new SolrClientWrapperImpl(mockFactory);
		
        QueryResponse response = solrClientWrapper.query(coreName, query);

        assertEquals(mockResponse, response);
		verify(mockFactory, mockSolrClient);
    }

	/** A refused connection means Solr is down: callers get the 503-mapped exception. */
	@Test
	public void queryReportsARefusedConnectionAsUnavailable() throws Exception {
		assertQueryFailureIsUnavailable(new ConnectException("Connection refused"));
	}

	/** No answer within the request timeout means Solr is unresponsive. */
	@Test
	public void queryReportsATimeoutAsUnavailable() throws Exception {
		assertQueryFailureIsUnavailable(new TimeoutException("Total timeout 15000 ms elapsed"));
	}

	/** Jetty reports a connect timeout as a SocketTimeoutException. */
	@Test
	public void queryReportsAConnectTimeoutAsUnavailable() throws Exception {
		assertQueryFailureIsUnavailable(new SocketTimeoutException("Connect Timeout"));
	}

	/** Any other client failure is not a sign Solr is down, so it is rethrown unchanged. */
	@Test
	public void queryRethrowsOtherFailuresUnchanged() throws Exception {
		SolrServerException failure = new SolrServerException("IOException occurred when talking to server",
				new IOException("stream closed"));
		SolrObjectFactory mockFactory = createMock(SolrObjectFactory.class);
		SolrClient mockSolrClient = createMock(SolrClient.class);
		expect(mockFactory.getSolrClient()).andReturn(mockSolrClient);
		SolrQuery query = new SolrQuery("q");
		expect(mockSolrClient.query("testCore", query, SolrRequest.METHOD.POST))
				.andThrow(failure);
		replay(mockFactory, mockSolrClient);

		try {
			new SolrClientWrapperImpl(mockFactory).query("testCore", query);
			fail("expected the original SolrServerException");
		} catch (SolrServerException e) {
			assertSame(failure, e);
		}
	}

	/** Writes - the user index on the request path - report unavailability the same way. */
	@Test
	public void addReportsARefusedConnectionAsUnavailable() throws Exception {
		Collection<SolrInputDocument> documents = Collections.singletonList(new SolrInputDocument());
		SolrObjectFactory mockFactory = createMock(SolrObjectFactory.class);
		SolrClient mockSolrClient = createMock(SolrClient.class);
		expect(mockFactory.getSolrClient()).andReturn(mockSolrClient);
		expect(mockSolrClient.add("testCore", documents)).andThrow(
				new SolrServerException("Server refused connection", new ConnectException("Connection refused")));
		replay(mockFactory, mockSolrClient);

		try {
			new SolrClientWrapperImpl(mockFactory).add("testCore", documents);
			fail("expected SolrUnavailableException");
		} catch (SolrUnavailableException expected) {
			assertEquals(SolrUnavailableException.MESSAGE, expected.getMessage());
		}
	}

	/** Core status checks run on the node-search path, so they report unavailability too. */
	@Test
	public void coreExistsReportsATimeoutAsUnavailable() throws Exception {
		SolrObjectFactory mockFactory = createMock(SolrObjectFactory.class);
		expect(mockFactory.getCoreAdminRequestGetStatus("testCore")).andThrow(
				new SolrServerException("Timeout occurred while waiting response from server",
						new TimeoutException()));
		replay(mockFactory);

		try {
			new SolrClientWrapperImpl(mockFactory).coreExists("testCore");
			fail("expected SolrUnavailableException");
		} catch (SolrUnavailableException expected) {
			assertEquals(SolrUnavailableException.MESSAGE, expected.getMessage());
		}
	}

	/** A STATUS listing the core means it exists. */
	@Test
	public void coreExistsReadsTheStatusListing() throws Exception {
		SolrObjectFactory mockFactory = createMock(SolrObjectFactory.class);
		expect(mockFactory.getCoreAdminRequestGetStatus("present")).andReturn(statusResponse("present", true));
		expect(mockFactory.getCoreAdminRequestGetStatus("absent")).andReturn(statusResponse("absent", false));
		replay(mockFactory);

		var wrapper = new SolrClientWrapperImpl(mockFactory);
		assertTrue(wrapper.coreExists("present"));
		assertFalse(wrapper.coreExists("absent"));
	}

	/** A failed STATUS call cannot answer the question, so it surfaces rather than reads as "absent". */
	@Test(expected = NdexException.class)
	public void coreExistsRejectsAFailedStatus() throws Exception {
		SolrObjectFactory mockFactory = createMock(SolrObjectFactory.class);
		CoreAdminResponse failed = new CoreAdminResponse();
		failed.setResponse(responseWithStatus(1));
		expect(mockFactory.getCoreAdminRequestGetStatus("core")).andReturn(failed);
		replay(mockFactory);

		new SolrClientWrapperImpl(mockFactory).coreExists("core");
	}

	@Test
	public void createCoreIfNeededDoesNothingWhenTheCoreExists() throws Exception {
		SolrObjectFactory mockFactory = createMock(SolrObjectFactory.class);
		expect(mockFactory.getCoreAdminRequestGetStatus("core")).andReturn(statusResponse("core", true));
		replay(mockFactory);

		new SolrClientWrapperImpl(mockFactory).createCoreIfNeeded("core");

		verify(mockFactory);
	}

	/** An absent core is created on the shared client, with the core name as its configSet. */
	@Test
	public void createCoreIfNeededCreatesAnAbsentCoreOnTheSharedClient() throws Exception {
		SolrObjectFactory mockFactory = createMock(SolrObjectFactory.class);
		SolrClient mockSolrClient = createMock(SolrClient.class);
		CoreAdminRequest.Create create = new CoreAdminRequest.Create();
		expect(mockFactory.getCoreAdminRequestGetStatus("core")).andReturn(statusResponse("core", false));
		expect(mockFactory.getCoreAdminRequestCreate()).andReturn(create);
		expect(mockFactory.getSolrClient()).andReturn(mockSolrClient);
		expect(mockSolrClient.request(same(create), isNull())).andReturn(responseWithStatus(0));
		replay(mockFactory, mockSolrClient);

		new SolrClientWrapperImpl(mockFactory).createCoreIfNeeded("core");

		verify(mockFactory, mockSolrClient);
		assertEquals("core", create.getParams().get("name"));
		assertEquals("core", create.getConfigSet());
	}

	@Test
	public void createCoreMakesATransientCoreThatIsNotLoadedOnStartup() throws Exception {
		SolrObjectFactory mockFactory = createMock(SolrObjectFactory.class);
		SolrClient mockSolrClient = createMock(SolrClient.class);
		CoreAdminRequest.Create create = new CoreAdminRequest.Create();
		expect(mockFactory.getCoreAdminRequestCreate()).andReturn(create);
		expect(mockFactory.getSolrClient()).andReturn(mockSolrClient);
		expect(mockSolrClient.request(same(create), isNull())).andReturn(responseWithStatus(0));
		replay(mockFactory, mockSolrClient);

		new SolrClientWrapperImpl(mockFactory).createCore("network-core", "ndex-nodes");

		assertEquals("network-core", create.getParams().get("name"));
		assertEquals("ndex-nodes", create.getConfigSet());
		assertEquals(Boolean.FALSE, create.getIsLoadOnStartup());
		assertEquals(Boolean.TRUE, create.getIsTransient());
	}

	@Test(expected = NdexException.class)
	public void createCoreRejectsAFailedCreate() throws Exception {
		SolrObjectFactory mockFactory = createMock(SolrObjectFactory.class);
		SolrClient mockSolrClient = createMock(SolrClient.class);
		CoreAdminRequest.Create create = new CoreAdminRequest.Create();
		expect(mockFactory.getCoreAdminRequestCreate()).andReturn(create);
		expect(mockFactory.getSolrClient()).andReturn(mockSolrClient);
		expect(mockSolrClient.request(same(create), isNull())).andReturn(responseWithStatus(1));
		replay(mockFactory, mockSolrClient);

		new SolrClientWrapperImpl(mockFactory).createCore("network-core", "ndex-nodes");
	}

	@Test
	public void createConfigSetClonesTheTemplateOnTheSharedClient() throws Exception {
		SolrObjectFactory mockFactory = createMock(SolrObjectFactory.class);
		SolrClient mockSolrClient = createMock(SolrClient.class);
		ConfigSetAdminRequest.Create create = new ConfigSetAdminRequest.Create();
		expect(mockFactory.getConfigSetAdminRequestCreate()).andReturn(create);
		expect(mockFactory.getSolrClient()).andReturn(mockSolrClient);
		expect(mockSolrClient.request(same(create), isNull())).andReturn(responseWithStatus(0));
		replay(mockFactory, mockSolrClient);

		new SolrClientWrapperImpl(mockFactory).createConfigSet("network-core", "ndex-nodes-template");

		verify(mockSolrClient);
		assertEquals("network-core", create.getConfigSetName());
		assertEquals("ndex-nodes-template", create.getBaseConfigSetName());
	}

	/** A hard commit is durable: waitFlush, waitSearcher, no soft commit. */
	@Test
	public void hardCommitFlushesToDisk() throws Exception {
		SolrObjectFactory mockFactory = createMock(SolrObjectFactory.class);
		SolrClient mockSolrClient = createMock(SolrClient.class);
		expect(mockFactory.getSolrClient()).andReturn(mockSolrClient).anyTimes();
		expect(mockSolrClient.commit("core", true, true, false)).andReturn(null);
		replay(mockFactory, mockSolrClient);

		new SolrClientWrapperImpl(mockFactory).commit("core", null, true);

		verify(mockSolrClient);
	}

	@Test
	public void deleteWithoutCommitOnlyDeletes() throws Exception {
		SolrObjectFactory mockFactory = createMock(SolrObjectFactory.class);
		SolrClient mockSolrClient = createMock(SolrClient.class);
		expect(mockFactory.getSolrClient()).andReturn(mockSolrClient);
		expect(mockSolrClient.deleteById("core", "id1")).andReturn(null);
		replay(mockFactory, mockSolrClient);

		new SolrClientWrapperImpl(mockFactory).delete("core", "id1", false);

		verify(mockSolrClient);
	}

	/** Dropping a core while Solr is down is reported as unavailability, not swallowed. */
	@Test(expected = SolrUnavailableException.class)
	public void dropCoreReportsARefusedConnectionAsUnavailable() throws Exception {
		SolrObjectFactory mockFactory = createMock(SolrObjectFactory.class);
		expect(mockFactory.getCoreAdminRequestUnloadCore("core", true, true)).andThrow(
				new SolrServerException("Server refused connection", new ConnectException()));
		replay(mockFactory);

		new SolrClientWrapperImpl(mockFactory).dropCore("core");
	}

	/** A 400 from Solr is the caller's bad query, reported without Solr's URL prefix. */
	@Test
	public void queryReportsABadQueryAsBadRequest() throws Exception {
		SolrObjectFactory mockFactory = createMock(SolrObjectFactory.class);
		SolrClient mockSolrClient = createMock(SolrClient.class);
		SolrQuery query = new SolrQuery("q");
		expect(mockFactory.getSolrClient()).andReturn(mockSolrClient);
		expect(mockSolrClient.query("testCore", query, SolrRequest.METHOD.POST)).andThrow(
				new BaseHttpSolrClient.RemoteSolrException("http://localhost:8983/solr", 400,
						"Error from server at http://localhost:8983/solr/testCore: undefined field foo", null));
		replay(mockFactory, mockSolrClient);

		try {
			new SolrClientWrapperImpl(mockFactory).query("testCore", query);
			fail("expected BadRequestException");
		} catch (BadRequestException expected) {
			assertEquals("undefined field foo", expected.getMessage());
		}
	}

	/** Any other Solr error status is a server-side failure. */
	@Test
	public void queryReportsAServerErrorAsNdexException() throws Exception {
		SolrObjectFactory mockFactory = createMock(SolrObjectFactory.class);
		SolrClient mockSolrClient = createMock(SolrClient.class);
		SolrQuery query = new SolrQuery("q");
		expect(mockFactory.getSolrClient()).andReturn(mockSolrClient);
		expect(mockSolrClient.query("testCore", query, SolrRequest.METHOD.POST)).andThrow(
				new BaseHttpSolrClient.RemoteSolrException("http://localhost:8983/solr", 500, "boom", null));
		replay(mockFactory, mockSolrClient);

		try {
			new SolrClientWrapperImpl(mockFactory).query("testCore", query);
			fail("expected NdexException");
		} catch (NdexException expected) {
			assertFalse(expected instanceof BadRequestException);
			assertTrue(expected.getMessage().contains("boom"));
		}
	}

	/** Every operation, writes and core admin included, reports a refused connection the same way. */
	@Test
	public void everyOperationReportsARefusedConnectionAsUnavailable() throws Exception {
		Collection<SolrInputDocument> documents = Collections.singletonList(new SolrInputDocument());
		SolrServerException refused = new SolrServerException("Server refused connection",
				new ConnectException("Connection refused"));
		SolrObjectFactory mockFactory = createMock(SolrObjectFactory.class);
		SolrClient mockSolrClient = createMock(SolrClient.class);
		expect(mockFactory.getSolrClient()).andReturn(mockSolrClient).anyTimes();
		expect(mockFactory.getCoreAdminRequestCreate()).andReturn(new CoreAdminRequest.Create()).anyTimes();
		expect(mockFactory.getConfigSetAdminRequestCreate()).andReturn(new ConfigSetAdminRequest.Create()).anyTimes();
		expect(mockFactory.getCoreAdminRequestGetStatus("core")).andReturn(statusResponse("core", false));
		expect(mockSolrClient.request(anyObject(), isNull())).andThrow(refused).anyTimes();
		expect(mockSolrClient.add("core", documents)).andReturn(null);
		expect(mockSolrClient.commit("core", false, true, true)).andThrow(refused);
		expect(mockSolrClient.add("core2", documents)).andThrow(refused);
		expect(mockSolrClient.deleteById("core", "id1")).andThrow(refused);
		replay(mockFactory, mockSolrClient);
		var wrapper = new SolrClientWrapperImpl(mockFactory);

		assertUnavailable(() -> wrapper.createCoreIfNeeded("core"));
		assertUnavailable(() -> wrapper.createCore("core", "ndex-nodes"));
		assertUnavailable(() -> wrapper.createConfigSet("core", "ndex-nodes-template"));
		assertUnavailable(() -> wrapper.commit("core", documents));
		assertUnavailable(() -> wrapper.add("core2", documents));
		assertUnavailable(() -> wrapper.delete("core", "id1", true));
	}

	@FunctionalInterface
	private interface SolrCall {
		void run() throws Exception;
	}

	private static void assertUnavailable(SolrCall call) throws Exception {
		try {
			call.run();
			fail("expected SolrUnavailableException");
		} catch (SolrUnavailableException expected) {
			assertEquals(SolrUnavailableException.MESSAGE, expected.getMessage());
		}
	}

	private static NamedList<Object> responseWithStatus(int status) {
		NamedList<Object> header = new NamedList<>();
		header.add("status", status);
		NamedList<Object> response = new NamedList<>();
		response.add("responseHeader", header);
		return response;
	}

	private static CoreAdminResponse statusResponse(String coreName, boolean present) {
		NamedList<Object> core = new NamedList<>();
		if (present) {
			core.add("name", coreName);
		}
		NamedList<Object> statusList = new NamedList<>();
		statusList.add(coreName, core);
		NamedList<Object> response = responseWithStatus(0);
		response.add("status", statusList);
		CoreAdminResponse coreAdminResponse = new CoreAdminResponse();
		coreAdminResponse.setResponse(response);
		return coreAdminResponse;
	}

	private static void assertQueryFailureIsUnavailable(Exception cause) throws Exception {
		SolrObjectFactory mockFactory = createMock(SolrObjectFactory.class);
		SolrClient mockSolrClient = createMock(SolrClient.class);
		expect(mockFactory.getSolrClient()).andReturn(mockSolrClient);
		SolrQuery query = new SolrQuery("q");
		expect(mockSolrClient.query("testCore", query, SolrRequest.METHOD.POST))
				.andThrow(new SolrServerException(cause.getMessage(), cause));
		replay(mockFactory, mockSolrClient);

		try {
			new SolrClientWrapperImpl(mockFactory).query("testCore", query);
			fail("expected SolrUnavailableException");
		} catch (SolrUnavailableException expected) {
			assertSame(cause, expected.getCause().getCause());
		}
	}

}
