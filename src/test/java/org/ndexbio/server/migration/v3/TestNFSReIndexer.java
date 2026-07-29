package org.ndexbio.server.migration.v3;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.ndexbio.common.NdexClasses;
import org.ndexbio.common.models.dao.DAOFactory;
import org.ndexbio.common.models.dao.FolderDAO;
import org.ndexbio.common.models.dao.NetworkDAO;
import org.ndexbio.common.models.dao.ShortcutDAO;
import org.ndexbio.common.models.dao.postgresql.UserDAO;
import org.ndexbio.common.solr.GlobalNetworkIndexManager;
import org.ndexbio.common.solr.SolrObjectFactory;

import static org.easymock.EasyMock.*;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;

public class TestNFSReIndexer {

    @Test
    public void testReIndexNetworksSubmitsFilteredSql() throws Exception {
        // Connection: strict — verifies the exact filtered SQL strings are submitted
        Connection mockConn = createMock(Connection.class);

        // Count PreparedStatement: returns 0 networks (loop body never executes)
        PreparedStatement countPst = createNiceMock(PreparedStatement.class);
        ResultSet countRs = createNiceMock(ResultSet.class);
        expect(mockConn.prepareStatement(eq(NFSReIndexer.REINDEX_COUNT_SQL))).andReturn(countPst);
        expect(countPst.executeQuery()).andReturn(countRs);
        expect(countRs.next()).andReturn(false);

        // Select PreparedStatement: returns empty ResultSet
        PreparedStatement selectPst = createNiceMock(PreparedStatement.class);
        ResultSet selectRs = createNiceMock(ResultSet.class);
        expect(mockConn.prepareStatement(eq(NFSReIndexer.REINDEX_SELECT_SQL))).andReturn(selectPst);
        expect(selectPst.executeQuery()).andReturn(selectRs);
        expect(selectRs.next()).andReturn(false);

        // SolrObjectFactory and GlobalNetworkIndexManager: opened in try-with-resources
        SolrObjectFactory mockSolrFactory = createNiceMock(SolrObjectFactory.class);
        GlobalNetworkIndexManager mockGlobalIdx = createNiceMock(GlobalNetworkIndexManager.class);
        expect(mockSolrFactory.getGlobalNetworkIndexManager()).andReturn(mockGlobalIdx);

        DAOFactory mockDaoFactory = createNiceMock(DAOFactory.class);

        replay(mockConn, countPst, countRs, selectPst, selectRs, mockSolrFactory, mockGlobalIdx, mockDaoFactory);

        NFSReIndexer reIndexer = new NFSReIndexer(mockConn, mockSolrFactory, mockDaoFactory);

        // DaoSet constructed with nice mocks for DAO components (not used when count=0)
        UserDAO mockUserDAO = createNiceMock(UserDAO.class);
        FolderDAO mockFolderDAO = createNiceMock(FolderDAO.class);
        ShortcutDAO mockShortcutDAO = createNiceMock(ShortcutDAO.class);
        NetworkDAO mockNetworkDAO = createNiceMock(NetworkDAO.class);
        V3Migrator.DaoSet daoSet = new V3Migrator.DaoSet(mockUserDAO, mockFolderDAO, mockShortcutDAO, mockNetworkDAO);

        reIndexer.reIndexNetworks(daoSet);

        // Verifies prepareStatement was called with the exact filtered SQL strings
        verify(mockConn, countPst, countRs, selectPst, selectRs, mockSolrFactory, mockGlobalIdx);
    }

    @Test
    public void testSqlConstantsContainErrorFilter() {
        // Regression guard: the SQL filter must reference both null-error and index-error networks
        String prefix = NdexClasses.NETWORK_INDEX_FAILED_MSG_PREFIX;
        org.junit.Assert.assertTrue(NFSReIndexer.REINDEX_COUNT_SQL.contains("error IS NULL"));
        org.junit.Assert.assertTrue(NFSReIndexer.REINDEX_COUNT_SQL.contains("error LIKE '" + prefix + "%'"));
        org.junit.Assert.assertTrue(NFSReIndexer.REINDEX_SELECT_SQL.contains("error IS NULL"));
        org.junit.Assert.assertTrue(NFSReIndexer.REINDEX_SELECT_SQL.contains("error LIKE '" + prefix + "%'"));
    }

    // ------------------------------------------------------------------------
    // Failure policy: one network's failure must not stop the sweep, and must be
    // reported at WARN with the reason. This reindexer emptied every per-network
    // query core across the fleet while logging its failures at INFO, which is why
    // a total failure looked like a normal run.
    // ------------------------------------------------------------------------

    /** NFSReIndexer names its logger with getSimpleName(), not the fully qualified class. */
    private static final String REINDEXER_LOGGER = "NFSReIndexer";

    private ch.qos.logback.classic.Logger reindexerLogger;
    private ListAppender<ILoggingEvent> logged;

    @Before
    public void captureLogs() {
        reindexerLogger = (ch.qos.logback.classic.Logger)
                org.slf4j.LoggerFactory.getLogger(REINDEXER_LOGGER);
        logged = new ListAppender<>();
        logged.start();
        reindexerLogger.addAppender(logged);
    }

    @After
    public void releaseLogs() {
        if (reindexerLogger != null) {
            reindexerLogger.detachAppender(logged);
        }
        if (logged != null) {
            logged.stop();
        }
    }

    private List<ILoggingEvent> eventsStartingWith(String prefix) {
        List<ILoggingEvent> matched = new ArrayList<>();
        for (ILoggingEvent e : logged.list) {
            if (e.getFormattedMessage().startsWith(prefix)) {
                matched.add(e);
            }
        }
        return matched;
    }

    /**
     * Builds a reindexer whose select query yields the given network ids.
     *
     * @param failFor ids whose summary lookup throws, standing in for a network whose aspect
     *                files cannot be read
     */
    private NFSReIndexer reIndexerOver(List<UUID> ids, Set<UUID> failFor,
            NetworkDAO networkDAO) throws Exception {

        Connection mockConn = createNiceMock(Connection.class);

        PreparedStatement countPst = createNiceMock(PreparedStatement.class);
        ResultSet countRs = createNiceMock(ResultSet.class);
        expect(mockConn.prepareStatement(eq(NFSReIndexer.REINDEX_COUNT_SQL))).andReturn(countPst);
        expect(countPst.executeQuery()).andReturn(countRs);
        expect(countRs.next()).andReturn(true);
        expect(countRs.getInt(1)).andReturn(ids.size());

        PreparedStatement selectPst = createNiceMock(PreparedStatement.class);
        ResultSet selectRs = createNiceMock(ResultSet.class);
        expect(mockConn.prepareStatement(eq(NFSReIndexer.REINDEX_SELECT_SQL))).andReturn(selectPst);
        expect(selectPst.executeQuery()).andReturn(selectRs);
        for (UUID id : ids) {
            expect(selectRs.next()).andReturn(true);
            expect(selectRs.getObject(1)).andReturn(id);
            expect(selectRs.getObject(2)).andReturn(UUID.randomUUID());
            expect(selectRs.getString(3)).andReturn("owner");
            expect(selectRs.getString(4)).andReturn("PUBLIC");
        }
        expect(selectRs.next()).andReturn(false);

        SolrObjectFactory mockSolrFactory = createNiceMock(SolrObjectFactory.class);
        GlobalNetworkIndexManager mockGlobalIdx = createNiceMock(GlobalNetworkIndexManager.class);
        expect(mockSolrFactory.getGlobalNetworkIndexManager()).andReturn(mockGlobalIdx);

        for (UUID id : ids) {
            if (failFor.contains(id)) {
                expect(networkDAO.getNetworkSummaryById(id))
                        .andThrow(new RuntimeException(
                                "CX2 aspect directory is not readable by user 'someoneelse'"));
            } else {
                // returning null makes rebuildNetworkIndex fail its own way; either path is a
                // per-network failure, which is what this test is about
                expect(networkDAO.getNetworkSummaryById(id)).andReturn(null);
            }
        }

        DAOFactory mockDaoFactory = createNiceMock(DAOFactory.class);

        replay(mockConn, countPst, countRs, selectPst, selectRs, mockSolrFactory, mockGlobalIdx,
                mockDaoFactory, networkDAO);

        return new NFSReIndexer(mockConn, mockSolrFactory, mockDaoFactory);
    }

    private static V3Migrator.DaoSet daoSetWith(NetworkDAO networkDAO) {
        return new V3Migrator.DaoSet(createNiceMock(UserDAO.class), createNiceMock(FolderDAO.class),
                createNiceMock(ShortcutDAO.class), networkDAO);
    }

    @Test
    public void aFailingNetworkDoesNotStopTheSweep() throws Exception {
        UUID first = UUID.randomUUID();
        UUID failing = UUID.fromString("ec25aeeb-fb51-11ef-b81d-005056ae3c32");
        UUID third = UUID.randomUUID();
        List<UUID> ids = List.of(first, failing, third);

        NetworkDAO networkDAO = createNiceMock(NetworkDAO.class);
        NFSReIndexer reIndexer = reIndexerOver(ids, Set.of(failing), networkDAO);

        // must not throw
        reIndexer.reIndexNetworks(daoSetWith(networkDAO));

        // every network was attempted, so the loop advanced past the failure
        verify(networkDAO);

        List<ILoggingEvent> failures = eventsStartingWith("Failed to reindex network");
        org.junit.Assert.assertEquals("one failure line per failing network", 3, failures.size());

        boolean sawUnreadable = false;
        for (ILoggingEvent e : failures) {
            org.junit.Assert.assertEquals("reindex failures must be WARN, not INFO",
                    Level.WARN, e.getLevel());
            org.junit.Assert.assertNotNull("the exception must be logged", e.getThrowableProxy());
            if (e.getFormattedMessage().contains(failing.toString())) {
                sawUnreadable = true;
                org.junit.Assert.assertTrue("the reason must be in the message: "
                        + e.getFormattedMessage(),
                        e.getFormattedMessage().contains("not readable by user"));
            }
        }
        org.junit.Assert.assertTrue("the failing network must be named in a WARN", sawUnreadable);
    }

    @Test
    public void everyNetworkFailingStillCompletesTheSweep() throws Exception {
        List<UUID> ids = List.of(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID());

        NetworkDAO networkDAO = createNiceMock(NetworkDAO.class);
        NFSReIndexer reIndexer = reIndexerOver(ids, Set.copyOf(ids), networkDAO);

        // This is the shape of the incident: every network fails. The run must still finish.
        reIndexer.reIndexNetworks(daoSetWith(networkDAO));

        verify(networkDAO);
        org.junit.Assert.assertEquals("all three logged as failures",
                3, eventsStartingWith("Failed to reindex network").size());
        for (ILoggingEvent e : eventsStartingWith("Failed to reindex network")) {
            org.junit.Assert.assertEquals(Level.WARN, e.getLevel());
        }
    }

    @Test
    public void noReindexFailureIsLoggedAtInfo() throws Exception {
        List<UUID> ids = List.of(UUID.randomUUID());

        NetworkDAO networkDAO = createNiceMock(NetworkDAO.class);
        NFSReIndexer reIndexer = reIndexerOver(ids, Set.copyOf(ids), networkDAO);

        reIndexer.reIndexNetworks(daoSetWith(networkDAO));

        for (ILoggingEvent e : logged.list) {
            if (e.getFormattedMessage().startsWith("Failed to reindex")) {
                org.junit.Assert.assertNotEquals("failures logged at INFO are how this outage hid",
                        Level.INFO, e.getLevel());
            }
        }
    }
}
