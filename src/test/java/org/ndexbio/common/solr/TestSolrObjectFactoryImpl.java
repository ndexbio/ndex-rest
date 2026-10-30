package org.ndexbio.common.solr;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;

import org.apache.solr.client.solrj.SolrQuery;
import org.apache.solr.client.solrj.SolrServerException;
import org.apache.solr.client.solrj.request.ConfigSetAdminRequest;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/**
 * Wiring tests for {@link SolrObjectFactoryImpl}.
 *
 * <p>The factory owns the one Solr client the JVM uses; every manager it hands out shares it.
 */
@RunWith(JUnit4.class)
public class TestSolrObjectFactoryImpl {

	private static final String SOLR_URL = "http://localhost:8983/solr";
	private static final String NDEX_ROOT = "/opt/ndex";
	private static final String NETWORK_ID = "ec25aeeb-fb51-11ef-b81d-005056ae3c32";
	private static final long REQUEST_TIMEOUT_SECONDS = 15;

	private SolrObjectFactoryImpl factory;

	@Before
	public void setUp() {
		factory = new SolrObjectFactoryImpl(SOLR_URL, NDEX_ROOT, REQUEST_TIMEOUT_SECONDS);
	}

	@After
	public void tearDown() throws Exception {
		factory.close();
	}

	@Test
	public void buildsAFullyWiredNodeIndexManager() {
		assertNotNull(factory.getSingleNetworkSolrIdxManager(NETWORK_ID));
	}

	@Test
	public void returnsTheSameClientOnEveryCall() {
		assertSame(factory.getSolrClient(), factory.getSolrClient());
	}

	@Test
	public void returnsTheSameClientToConcurrentCallers() throws Exception {
		Object[] seen = new Object[8];
		Thread[] threads = new Thread[seen.length];
		for (int i = 0; i < threads.length; i++) {
			int slot = i;
			threads[i] = new Thread(() -> seen[slot] = factory.getSolrClient());
			threads[i].start();
		}
		for (Thread t : threads) {
			t.join();
		}
		for (Object client : seen) {
			assertSame(factory.getSolrClient(), client);
		}
	}

	/**
	 * A Solr that accepts the connection but never answers must not hold the caller: the request
	 * timeout ends the call, and it surfaces as unavailability rather than a generic failure.
	 */
	@Test(timeout = 20000)
	public void aSilentSolrIsCutOffByTheRequestTimeout() throws Exception {
		List<Socket> held = new CopyOnWriteArrayList<>();
		try (ServerSocket silentSolr = new ServerSocket(0)) {
			Thread acceptor = new Thread(() -> {
				try {
					// Accept and hold connections open without ever replying. Each socket is kept
					// referenced: one left for the collector can be closed early, and the client then
					// sees the connection drop instead of a silent server.
					while (!silentSolr.isClosed()) {
						held.add(silentSolr.accept());
					}
				} catch (IOException closed) {
					// test is over
				}
			});
			acceptor.setDaemon(true);
			acceptor.start();

			SolrObjectFactoryImpl silent = new SolrObjectFactoryImpl(
					"http://127.0.0.1:" + silentSolr.getLocalPort() + "/solr", NDEX_ROOT, 1);
			try {
				long start = System.nanoTime();
				try {
					new SolrClientWrapperImpl(silent).query("ndex-nfs", new SolrQuery("*:*"));
					fail("expected SolrUnavailableException");
				} catch (SolrUnavailableException expected) {
					long elapsedSeconds = TimeUnit.NANOSECONDS.toSeconds(System.nanoTime() - start);
					assertTrue("cut off by the 1 s request timeout, took " + elapsedSeconds + " s",
							elapsedSeconds < 10);
				}
			} finally {
				silent.close();
				for (Socket socket : held) {
					socket.close();
				}
			}
		}
	}

	/** Nothing listening is the "Solr is down" case and must also surface as unavailability. */
	@Test(timeout = 20000)
	public void aRefusedConnectionIsReportedAsUnavailable() throws Exception {
		int closedPort;
		try (ServerSocket probe = new ServerSocket(0)) {
			closedPort = probe.getLocalPort();
		}
		SolrObjectFactoryImpl down = new SolrObjectFactoryImpl(
				"http://127.0.0.1:" + closedPort + "/solr", NDEX_ROOT, REQUEST_TIMEOUT_SECONDS);
		try {
			new SolrClientWrapperImpl(down).query("ndex-nfs", new SolrQuery("*:*"));
			fail("expected SolrUnavailableException");
		} catch (SolrUnavailableException expected) {
			assertEquals(SolrUnavailableException.MESSAGE, expected.getMessage());
		} finally {
			down.close();
		}
	}

	@Test
	public void handsOutAManagerForEveryIndex() {
		assertNotNull(factory.getGlobalNetworkIndexManager());
		assertNotNull(factory.getFolderIndexManager());
		assertNotNull(factory.getShortcutIndexManager());
		assertNotNull(factory.getUserIndexManager());
		assertNotNull(factory.getCoreAdminRequestCreate());
	}

	/** Core-admin calls go through the shared client too, so a down Solr fails them the same way. */
	@Test(timeout = 20000)
	public void coreAdminCallsUseTheSharedClient() throws Exception {
		int closedPort;
		try (ServerSocket probe = new ServerSocket(0)) {
			closedPort = probe.getLocalPort();
		}
		SolrObjectFactoryImpl down = new SolrObjectFactoryImpl(
				"http://127.0.0.1:" + closedPort + "/solr", NDEX_ROOT, REQUEST_TIMEOUT_SECONDS);
		try {
			try {
				down.getCoreAdminRequestGetStatus("ndex-nfs");
				fail("expected SolrServerException");
			} catch (SolrServerException expected) {
				assertTrue(expected.getCause() instanceof java.net.ConnectException);
			}
			try {
				down.getCoreAdminRequestUnloadCore("ndex-nfs", true, true);
				fail("expected SolrServerException");
			} catch (SolrServerException expected) {
				assertTrue(expected.getCause() instanceof java.net.ConnectException);
			}
		} finally {
			down.close();
		}
	}

	@Test
	public void exposesAConfigSetCreateRequest() {
		ConfigSetAdminRequest.Create request = factory.getConfigSetAdminRequestCreate();

		assertNotNull("needed so the extra-fields index path can be exercised in tests", request);
	}
}
