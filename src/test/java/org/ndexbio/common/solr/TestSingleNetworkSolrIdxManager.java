package org.ndexbio.common.solr;

import static org.easymock.EasyMock.anyBoolean;
import static org.easymock.EasyMock.anyObject;
import static org.easymock.EasyMock.capture;
import static org.easymock.EasyMock.createMock;
import static org.easymock.EasyMock.eq;
import static org.easymock.EasyMock.expect;
import static org.easymock.EasyMock.expectLastCall;
import static org.easymock.EasyMock.replay;
import static org.easymock.EasyMock.verify;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.io.IOException;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.apache.solr.client.solrj.SolrQuery;
import org.apache.solr.client.solrj.SolrServerException;
import org.apache.solr.client.solrj.response.QueryResponse;
import org.apache.solr.common.SolrDocument;
import org.apache.solr.common.SolrDocumentList;
import org.apache.solr.common.SolrInputDocument;
import org.easymock.Capture;
import org.easymock.EasyMock;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;
import org.ndexbio.model.exceptions.NdexException;

/**
 * Unit tests for {@link SingleNetworkSolrIdxManager}.
 *
 * <p>The behaviour under test is the reason a fleet-wide outage went unnoticed: a rebuild that
 * committed zero documents used to be reported as success, leaving an empty core that answered
 * every query with no results. A zero-document rebuild for a network the database says has nodes
 * must now fail, and the count reported must be the count Solr actually holds.
 */
@RunWith(JUnit4.class)
public class TestSingleNetworkSolrIdxManager {

	private static final String NETWORK_ID = "ec25aeeb-fb51-11ef-b81d-005056ae3c32";

	// ------------------------------------------------------------------ helpers

	/** A node index entry with a name, which is what makes a node indexable. */
	private static NodeIndexEntry entry(long id) {
		return new NodeIndexEntry(id, "NODE" + id);
	}

	private static Map<Long, NodeIndexEntry> entries(int count) {
		Map<Long, NodeIndexEntry> map = new LinkedHashMap<>();
		for (long i = 0; i < count; i++) {
			map.put(i, entry(i));
		}
		return map;
	}

	/** A QueryResponse whose result list reports numFound, as the doc-count probe reads it. */
	private static QueryResponse responseWithNumFound(long numFound) {
		SolrDocumentList docList = new SolrDocumentList();
		docList.setNumFound(numFound);
		QueryResponse rsp = createMock(QueryResponse.class);
		expect(rsp.getResults()).andReturn(docList).anyTimes();
		replay(rsp);
		return rsp;
	}

	private static Cx2NodeIndexService serviceReturning(Map<Long, NodeIndexEntry> toReturn)
			throws Exception {
		Cx2NodeIndexService svc = createMock(Cx2NodeIndexService.class);
		expect(svc.loadNodeIndexEntries(NETWORK_ID)).andReturn(toReturn);
		replay(svc);
		return svc;
	}

	private static Cx2NodeIndexService serviceThrowing(NdexException toThrow) throws Exception {
		Cx2NodeIndexService svc = createMock(Cx2NodeIndexService.class);
		expect(svc.loadNodeIndexEntries(NETWORK_ID)).andThrow(toThrow);
		replay(svc);
		return svc;
	}

	/** What a commit call looked like at the moment it happened. */
	private static final class CommitRecord {
		private Integer docCount;
		private Boolean hardCommit;
		private int calls;
	}

	/**
	 * Records the commit arguments as they are received. The manager clears its document buffer
	 * straight after committing, so a plain {@code Capture} of the collection would be read back
	 * empty and assert nothing.
	 */
	private static CommitRecord expectCommit(SolrClientWrapper wrapper)
			throws SolrServerException, IOException {

		CommitRecord record = new CommitRecord();
		wrapper.commit(eq(NETWORK_ID), anyObject(), anyBoolean());
		expectLastCall().andAnswer(() -> {
			Object[] args = EasyMock.getCurrentArguments();
			Collection<?> sent = (Collection<?>) args[1];
			record.docCount = (sent == null) ? null : Integer.valueOf(sent.size());
			record.hardCommit = (Boolean) args[2];
			record.calls++;
			return null;
		});
		return record;
	}

	// ------------------------------------------------- doc count verification

	@Test
	public void returnsCountSolrReportsAfterHardCommit() throws Exception {
		SolrClientWrapper wrapper = createMock(SolrClientWrapper.class);
		wrapper.createCore(NETWORK_ID, "ndex-nodes");
		expectLastCall();
		CommitRecord commit = expectCommit(wrapper);
		expect(wrapper.query(eq(NETWORK_ID), anyObject(SolrQuery.class)))
				.andReturn(responseWithNumFound(3));
		replay(wrapper);

		SingleNetworkSolrIdxManager mgr = new SingleNetworkSolrIdxManager(NETWORK_ID, wrapper,
				serviceReturning(entries(3)));

		assertEquals(3, mgr.createIndexFromCx2(null, 3));
		assertTrue("index creation must use a hard commit so the documents are durable",
				commit.hardCommit);
		verify(wrapper);
	}

	@Test
	public void zeroDocumentsForANetworkWithNodesThrows() throws Exception {
		SolrClientWrapper wrapper = createMock(SolrClientWrapper.class);
		wrapper.createCore(NETWORK_ID, "ndex-nodes");
		expectLastCall();
		expectCommit(wrapper);
		expect(wrapper.query(eq(NETWORK_ID), anyObject(SolrQuery.class)))
				.andReturn(responseWithNumFound(0));
		replay(wrapper);

		SingleNetworkSolrIdxManager mgr = new SingleNetworkSolrIdxManager(NETWORK_ID, wrapper,
				serviceReturning(entries(0)));

		try {
			mgr.createIndexFromCx2(null, 226);
			fail("a rebuild that commits nothing for a 226 node network must not report success");
		} catch (NdexException e) {
			String msg = e.getMessage();
			assertTrue("should name the network, got: " + msg, msg.contains(NETWORK_ID));
			assertTrue("should report the expected node count, got: " + msg, msg.contains("226"));
			assertTrue("should say the index holds no documents, got: " + msg,
					msg.contains("no documents"));
		}
	}

	@Test
	public void zeroDocumentsForANetworkWithNoNodesIsAccepted() throws Exception {
		SolrClientWrapper wrapper = createMock(SolrClientWrapper.class);
		wrapper.createCore(NETWORK_ID, "ndex-nodes");
		expectLastCall();
		expectCommit(wrapper);
		expect(wrapper.query(eq(NETWORK_ID), anyObject(SolrQuery.class)))
				.andReturn(responseWithNumFound(0));
		replay(wrapper);

		SingleNetworkSolrIdxManager mgr = new SingleNetworkSolrIdxManager(NETWORK_ID, wrapper,
				serviceReturning(entries(0)));

		assertEquals(0, mgr.createIndexFromCx2(null, 0));
	}

	@Test
	public void partialIndexIsAcceptedBecauseUnnamedNodesAreNotIndexed() throws Exception {
		SolrClientWrapper wrapper = createMock(SolrClientWrapper.class);
		wrapper.createCore(NETWORK_ID, "ndex-nodes");
		expectLastCall();
		expectCommit(wrapper);
		expect(wrapper.query(eq(NETWORK_ID), anyObject(SolrQuery.class)))
				.andReturn(responseWithNumFound(713));
		replay(wrapper);

		SingleNetworkSolrIdxManager mgr = new SingleNetworkSolrIdxManager(NETWORK_ID, wrapper,
				serviceReturning(entries(713)));

		// 713 of 867 is the real shape of a WikiPathways network: layout-only nodes carry no name
		assertEquals(713, mgr.createIndexFromCx2(null, 867));
	}

	// ------------------------------------------------------- batch boundaries

	@Test
	public void commitsEvenWhenEntryCountIsAnExactMultipleOfBatchSize() throws Exception {
		int batchSize = NodeIndexDocumentBuilder.DEFAULT_BATCH_SIZE;

		SolrClientWrapper wrapper = createMock(SolrClientWrapper.class);
		wrapper.createCore(NETWORK_ID, "ndex-nodes");
		expectLastCall();
		// the full batch is flushed by add(), leaving nothing for the final commit to send
		wrapper.add(eq(NETWORK_ID), anyObject());
		expectLastCall();
		CommitRecord commit = expectCommit(wrapper);
		expect(wrapper.query(eq(NETWORK_ID), anyObject(SolrQuery.class)))
				.andReturn(responseWithNumFound(batchSize));
		replay(wrapper);

		SingleNetworkSolrIdxManager mgr = new SingleNetworkSolrIdxManager(NETWORK_ID, wrapper,
				serviceReturning(entries(batchSize)));

		assertEquals(batchSize, mgr.createIndexFromCx2(null, batchSize));
		assertEquals("commit must still be issued when the trailing batch is empty",
				1, commit.calls);
		assertEquals("the trailing batch really was empty",
				Integer.valueOf(0), commit.docCount);
		assertTrue(commit.hardCommit);
		verify(wrapper);
	}

	@Test
	public void flushesEachFullBatchThenCommitsTheRemainder() throws Exception {
		int batchSize = NodeIndexDocumentBuilder.DEFAULT_BATCH_SIZE;
		int total = batchSize * 2 + 5;

		SolrClientWrapper wrapper = createMock(SolrClientWrapper.class);
		wrapper.createCore(NETWORK_ID, "ndex-nodes");
		expectLastCall();
		wrapper.add(eq(NETWORK_ID), anyObject());
		expectLastCall().times(2);
		CommitRecord commit = expectCommit(wrapper);
		expect(wrapper.query(eq(NETWORK_ID), anyObject(SolrQuery.class)))
				.andReturn(responseWithNumFound(total));
		replay(wrapper);

		SingleNetworkSolrIdxManager mgr = new SingleNetworkSolrIdxManager(NETWORK_ID, wrapper,
				serviceReturning(entries(total)));

		assertEquals(total, mgr.createIndexFromCx2(null, total));
		assertEquals("the trailing partial batch goes out with the commit",
				Integer.valueOf(5), commit.docCount);
		verify(wrapper);
	}

	// --------------------------------------------- source failure propagation

	@Test
	public void aspectReadFailurePropagatesWithItsOwnMessage() throws Exception {
		SolrClientWrapper wrapper = createMock(SolrClientWrapper.class);
		wrapper.createCore(NETWORK_ID, "ndex-nodes");
		expectLastCall();
		replay(wrapper);

		NdexException cause = new NdexException(
				"Cannot index network " + NETWORK_ID + ": CX2 aspect directory is not readable "
						+ "by user 'someoneelse' - /opt/ndex/data/" + NETWORK_ID + "/aspects_cx2");

		SingleNetworkSolrIdxManager mgr = new SingleNetworkSolrIdxManager(NETWORK_ID, wrapper,
				serviceThrowing(cause));

		try {
			mgr.createIndexFromCx2(null, 226);
			fail("an unreadable aspect directory must not be reported as a successful rebuild");
		} catch (NdexException e) {
			assertEquals("the diagnostic must survive unchanged", cause.getMessage(), e.getMessage());
		}
		// no commit and no doc-count probe should have happened
		verify(wrapper);
	}

	// ---------------------------------------------------------- ensureReady

	@Test
	public void ensureReadyDoesNothingWhenCoreExists() throws Exception {
		SolrClientWrapper wrapper = createMock(SolrClientWrapper.class);
		expect(wrapper.coreExists(NETWORK_ID)).andReturn(true);
		replay(wrapper);

		Cx2NodeIndexService svc = createMock(Cx2NodeIndexService.class);
		replay(svc); // must not be touched

		new SingleNetworkSolrIdxManager(NETWORK_ID, wrapper, svc).ensureReady(226);

		verify(wrapper);
		verify(svc);
	}

	@Test
	public void ensureReadyBuildsIndexForSmallNetworkWhenCoreAbsent() throws Exception {
		SolrClientWrapper wrapper = createMock(SolrClientWrapper.class);
		expect(wrapper.coreExists(NETWORK_ID)).andReturn(false).times(2);
		wrapper.createCore(NETWORK_ID, "ndex-nodes");
		expectLastCall();
		expectCommit(wrapper);
		expect(wrapper.query(eq(NETWORK_ID), anyObject(SolrQuery.class)))
				.andReturn(responseWithNumFound(20));
		replay(wrapper);

		SingleNetworkSolrIdxManager mgr = new SingleNetworkSolrIdxManager(NETWORK_ID, wrapper,
				serviceReturning(entries(20)));

		mgr.ensureReady(20);

		verify(wrapper);
	}

	@Test
	public void ensureReadyRefusesToBuildLargeNetworkOnDemand() throws Exception {
		SolrClientWrapper wrapper = createMock(SolrClientWrapper.class);
		expect(wrapper.coreExists(NETWORK_ID)).andReturn(false);
		replay(wrapper);

		Cx2NodeIndexService svc = createMock(Cx2NodeIndexService.class);
		replay(svc); // must not attempt a build

		SingleNetworkSolrIdxManager mgr = new SingleNetworkSolrIdxManager(NETWORK_ID, wrapper, svc);

		try {
			mgr.ensureReady(SingleNetworkSolrIdxManager.AUTOCREATE_THRESHHOLD);
			fail("a large network should be left to the background indexer");
		} catch (NdexException e) {
			assertTrue("got: " + e.getMessage(),
					e.getMessage().contains("hasn't finished creating index"));
		}
		verify(svc);
	}

	@Test
	public void ensureReadyPreservesTheAspectReadDiagnostic() throws Exception {
		SolrClientWrapper wrapper = createMock(SolrClientWrapper.class);
		expect(wrapper.coreExists(NETWORK_ID)).andReturn(false).times(2);
		wrapper.createCore(NETWORK_ID, "ndex-nodes");
		expectLastCall();
		replay(wrapper);

		NdexException cause = new NdexException("Cannot index network " + NETWORK_ID
				+ ": CX2 aspect directory is not readable by user 'someoneelse'");

		SingleNetworkSolrIdxManager mgr = new SingleNetworkSolrIdxManager(NETWORK_ID, wrapper,
				serviceThrowing(cause));

		try {
			mgr.ensureReady(20);
			fail("expected the read failure to surface");
		} catch (NdexException e) {
			assertEquals("must not be flattened to \"Failed to create Solr Index on this network.\"",
					cause.getMessage(), e.getMessage());
			assertTrue(e.getMessage().contains("not readable by user"));
		}
	}

	/**
	 * Two threads racing to make the same network ready must not both create the core.
	 *
	 * <p>Hand written stubs rather than EasyMock: EasyMock mocks are not thread safe, so a
	 * concurrent test built on them fails nondeterministically.
	 */
	@Test
	public void concurrentEnsureReadyCreatesTheCoreOnce() throws Exception {
		final AtomicInteger createCalls = new AtomicInteger();
		final CountDownLatch bothArrived = new CountDownLatch(2);

		final CountingWrapper wrapper = new CountingWrapper(createCalls, bothArrived);
		Cx2NodeIndexService svc = networkId -> entries(5);

		final SingleNetworkSolrIdxManager mgr =
				new SingleNetworkSolrIdxManager(NETWORK_ID, wrapper, svc);

		Runnable task = () -> {
			try {
				mgr.ensureReady(5);
			} catch (Exception e) {
				throw new RuntimeException(e);
			}
		};

		Thread t1 = new Thread(task, "ensureReady-1");
		Thread t2 = new Thread(task, "ensureReady-2");
		t1.start();
		t2.start();
		t1.join(10_000);
		t2.join(10_000);

		assertEquals("core creation must happen exactly once", 1, createCalls.get());
	}

	// -------------------------------------------------------- query delegation

	@Test
	public void getNodeIdsByQueryDelegatesWithEdismaxAndNodeFields() throws Exception {
		SolrDocumentList results = new SolrDocumentList();
		SolrDocument doc = new SolrDocument();
		doc.addField("id", "0");
		results.add(doc);
		results.setNumFound(1);

		QueryResponse rsp = createMock(QueryResponse.class);
		expect(rsp.getResults()).andReturn(results).anyTimes();
		replay(rsp);

		SolrClientWrapper wrapper = createMock(SolrClientWrapper.class);
		Capture<SolrQuery> query = Capture.newInstance();
		expect(wrapper.query(eq(NETWORK_ID), capture(query))).andReturn(rsp);
		replay(wrapper);

		Cx2NodeIndexService svc = createMock(Cx2NodeIndexService.class);
		replay(svc);

		SolrDocumentList out = new SingleNetworkSolrIdxManager(NETWORK_ID, wrapper, svc)
				.getNodeIdsByQuery("fbxl13", 5);

		assertEquals(1, out.size());
		SolrQuery sent = query.getValue();
		assertEquals("edismax", sent.get("defType"));
		assertEquals("nodeName represents alias text", sent.get("qf"));
		assertEquals(Integer.valueOf(5), sent.getRows());
		verify(wrapper);
	}

	@Test
	public void dropIndexDelegatesToDropCore() throws Exception {
		SolrClientWrapper wrapper = createMock(SolrClientWrapper.class);
		wrapper.dropCore(NETWORK_ID);
		expectLastCall();
		replay(wrapper);

		Cx2NodeIndexService svc = createMock(Cx2NodeIndexService.class);
		replay(svc);

		new SingleNetworkSolrIdxManager(NETWORK_ID, wrapper, svc).dropIndex();

		verify(wrapper);
	}

	@Test
	public void closeClosesTheWrapperItOwns() throws Exception {
		SolrClientWrapper wrapper = createMock(SolrClientWrapper.class);
		wrapper.close();
		expectLastCall();
		replay(wrapper);

		Cx2NodeIndexService svc = createMock(Cx2NodeIndexService.class);
		replay(svc);

		new SingleNetworkSolrIdxManager(NETWORK_ID, wrapper, svc).close();

		verify(wrapper);
	}

	// ----------------------------------------------------- configSet variant

	@Test
	public void createIndexWithExtraFieldsClonesAConfigSetFirst() throws Exception {
		SolrClientWrapper wrapper = createMock(SolrClientWrapper.class);
		wrapper.createConfigSet(NETWORK_ID, "ndex-nodes-template");
		expectLastCall();
		wrapper.createCore(NETWORK_ID, NETWORK_ID);
		expectLastCall();
		expectCommit(wrapper);
		expect(wrapper.query(eq(NETWORK_ID), anyObject(SolrQuery.class)))
				.andReturn(responseWithNumFound(2));
		replay(wrapper);

		SingleNetworkSolrIdxManager mgr = new SingleNetworkSolrIdxManager(NETWORK_ID, wrapper,
				serviceReturning(entries(2)));

		assertEquals(2, mgr.createIndex(Set.of("customField"), 2));
		verify(wrapper);
	}

	@Test
	public void createIndexFromCx2RejectsExtraFields() throws Exception {
		SolrClientWrapper wrapper = createMock(SolrClientWrapper.class);
		replay(wrapper);
		Cx2NodeIndexService svc = createMock(Cx2NodeIndexService.class);
		replay(svc);

		try {
			new SingleNetworkSolrIdxManager(NETWORK_ID, wrapper, svc)
					.createIndexFromCx2(Set.of("customField"), 5);
			fail("extra attribute indexing is not implemented for the CX2 path");
		} catch (NdexException e) {
			assertNotNull(e.getMessage());
		}
	}

	/**
	 * Minimal thread-safe {@link SolrClientWrapper} that counts core creations and makes both
	 * threads arrive before either proceeds, so the race is real rather than theoretical.
	 */
	private static final class CountingWrapper implements SolrClientWrapper {

		private final AtomicInteger createCalls;
		private final CountDownLatch bothArrived;
		private volatile boolean created;

		CountingWrapper(AtomicInteger createCalls, CountDownLatch bothArrived) {
			this.createCalls = createCalls;
			this.bothArrived = bothArrived;
		}

		@Override
		public boolean coreExists(String coreName) {
			bothArrived.countDown();
			try {
				bothArrived.await(5, TimeUnit.SECONDS);
			} catch (InterruptedException e) {
				Thread.currentThread().interrupt();
			}
			return created;
		}

		@Override
		public void createCore(String coreName, String configSetName) {
			createCalls.incrementAndGet();
			created = true;
		}

		@Override
		public QueryResponse query(String coreName, SolrQuery query) {
			return responseWithNumFound(5);
		}

		@Override
		public void createCoreIfNeeded(String coreName) { /* unused */ }

		@Override
		public void createCoreIfNeeded(String coreName, String configSetName) { /* unused */ }

		@Override
		public void createConfigSet(String configSetName, String baseConfigSetName) { /* unused */ }

		@Override
		public void dropCore(String coreName) { /* unused */ }

		@Override
		public void add(String coreName, Collection<SolrInputDocument> documents) { /* unused */ }

		@Override
		public void commit(String coreName, Collection<SolrInputDocument> documents) { /* unused */ }

		@Override
		public void commit(String coreName, Collection<SolrInputDocument> documents,
				boolean hardCommit) { /* unused */ }

		@Override
		public void delete(String coreName, String id, boolean commit) { /* unused */ }

		@Override
		public void close() { /* unused */ }
	}
}
