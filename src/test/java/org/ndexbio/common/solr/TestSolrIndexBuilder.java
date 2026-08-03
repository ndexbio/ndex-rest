package org.ndexbio.common.solr;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;

/**
 * Tests the per-network failure policy of the index sweeps.
 *
 * <p>A rebuild that fails for one network must not stop the sweep, and must say why at WARN.
 * The migration that emptied every query core across the fleet logged its failures at INFO, and
 * the all-local sweep rethrew, aborting on the first bad network - both made a total failure look
 * like a normal run.
 *
 * <p>These drive the extracted per-network steps rather than the enclosing loops, because the
 * loops open a {@code PostgresNetworkDAO} to stream network ids from the database. The steps are
 * where the policy lives; {@code TestNFSReIndexer} covers loop iteration end to end.
 */
@RunWith(JUnit4.class)
public class TestSolrIndexBuilder {

	private static final UUID NETWORK_A = UUID.fromString("ec25aeeb-fb51-11ef-b81d-005056ae3c32");

	private Logger builderLogger;
	private ListAppender<ILoggingEvent> logged;

	/** A builder whose rebuild always fails, standing in for an unreadable aspect directory. */
	private static final class FailingBuilder extends SolrIndexBuilder {
		private final RuntimeException failure;
		private int attempts;

		FailingBuilder(RuntimeException failure) {
			super((NetworkGlobalIndexManager) null);
			this.failure = failure;
		}

		@Override
		void rebuildNetworkIndex(UUID networkId, boolean ignoreDeletion) {
			attempts++;
			throw failure;
		}

		@Override
		void rebuildLocalNetworkIndex(UUID networkId, boolean ignoreDeletion) {
			attempts++;
			throw failure;
		}
	}

	@Before
	public void captureLogs() {
		builderLogger = (Logger) org.slf4j.LoggerFactory.getLogger(SolrIndexBuilder.class);
		logged = new ListAppender<>();
		logged.start();
		builderLogger.addAppender(logged);
	}

	@After
	public void releaseLogs() {
		builderLogger.detachAppender(logged);
		logged.stop();
	}

	private List<ILoggingEvent> failureEvents() {
		List<ILoggingEvent> events = new ArrayList<>();
		for (ILoggingEvent e : logged.list) {
			if (e.getFormattedMessage().startsWith("Failed to")) {
				events.add(e);
			}
		}
		return events;
	}

	// ------------------------------------------------------------------------

	@Test
	public void networkRebuildFailureIsRecordedAtWarnAndDoesNotPropagate() {
		SolrIndexBuilder builder = new FailingBuilder(
				new RuntimeException("CX2 aspect directory is not readable by user 'someoneelse'"));
		List<String> failed = new ArrayList<>();

		boolean ok = builder.runNetworkIndexStep(NETWORK_A, failed);

		assertFalse("a failed network must not count as indexed", ok);
		assertEquals("the failed network must be recorded for the summary",
				List.of(NETWORK_A.toString()), failed);

		List<ILoggingEvent> events = failureEvents();
		assertEquals("exactly one failure line per network", 1, events.size());
		ILoggingEvent event = events.get(0);
		assertEquals("reindex failures must be WARN, not INFO or ERROR",
				Level.WARN, event.getLevel());
		assertTrue("the message must name the network: " + event.getFormattedMessage(),
				event.getFormattedMessage().contains(NETWORK_A.toString()));
		assertTrue("the message must carry the reason: " + event.getFormattedMessage(),
				event.getFormattedMessage().contains("not readable by user"));
		assertTrue("the log line must stay small - message only, no stack trace",
				event.getThrowableProxy() == null);
	}

	@Test
	public void localIndexFailureIsRecordedAtWarnAndDoesNotPropagate() {
		SolrIndexBuilder builder = new FailingBuilder(new RuntimeException("solr exploded"));
		List<String> failed = new ArrayList<>();

		// The all-local sweep used to rethrow here, killing the run on the first bad network.
		boolean created = builder.runLocalIndexStep(NETWORK_A, failed);

		assertFalse(created);
		assertEquals(List.of(NETWORK_A.toString()), failed);

		List<ILoggingEvent> events = failureEvents();
		assertEquals(1, events.size());
		assertEquals(Level.WARN, events.get(0).getLevel());
		assertTrue(events.get(0).getFormattedMessage().contains("solr exploded"));
	}

	@Test
	public void everyNetworkInABatchIsAttemptedEvenWhenAllFail() {
		FailingBuilder builder = new FailingBuilder(new RuntimeException("boom"));
		List<String> failed = new ArrayList<>();

		List<UUID> batch = List.of(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID());
		int succeeded = 0;
		for (UUID id : batch) {
			if (builder.runNetworkIndexStep(id, failed)) {
				succeeded++;
			}
		}

		assertEquals("no step may abort the batch", 3, builder.attempts);
		assertEquals(0, succeeded);
		assertEquals("all three failures recorded", 3, failed.size());
		assertEquals("one WARN per network", 3, failureEvents().size());
		for (ILoggingEvent e : failureEvents()) {
			assertEquals(Level.WARN, e.getLevel());
		}
	}

	@Test
	public void aPreExistingCoreIsNotCountedAsAFailure() {
		SolrIndexBuilder builder = new SolrIndexBuilder((NetworkGlobalIndexManager) null) {
			@Override
			void rebuildLocalNetworkIndex(UUID networkId, boolean ignoreDeletion) {
				throw new org.apache.solr.client.solrj.impl.HttpSolrClient.RemoteSolrException(
						"http://localhost:8983/solr", 400,
						"Core with name '" + networkId + "' already exists", null);
			}
		};
		List<String> failed = new ArrayList<>();

		boolean created = builder.runLocalIndexStep(NETWORK_A, failed);

		assertFalse("nothing was created", created);
		assertTrue("an already-present core is not a failure", failed.isEmpty());
		assertTrue("and must not be logged as one", failureEvents().isEmpty());
	}

	@Test
	public void noReindexFailureIsLoggedAtInfoOrError() {
		SolrIndexBuilder builder = new FailingBuilder(new RuntimeException("boom"));
		List<String> failed = new ArrayList<>();

		builder.runNetworkIndexStep(NETWORK_A, failed);
		builder.runLocalIndexStep(NETWORK_A, failed);

		for (ILoggingEvent e : logged.list) {
			if (e.getFormattedMessage().startsWith("Failed to")) {
				assertFalse("failures must not be logged at INFO", Level.INFO.equals(e.getLevel()));
				assertFalse("failures must not be logged at ERROR", Level.ERROR.equals(e.getLevel()));
			}
		}
	}
}
