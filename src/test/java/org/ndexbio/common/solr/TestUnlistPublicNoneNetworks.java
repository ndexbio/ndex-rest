package org.ndexbio.common.solr;

import org.easymock.IAnswer;
import org.junit.Test;
import org.ndexbio.common.models.dao.postgresql.PostgresNetworkDAO;
import org.ndexbio.model.exceptions.NdexException;

import java.lang.reflect.Constructor;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import static org.easymock.EasyMock.*;
import static org.junit.Assert.*;

public class TestUnlistPublicNoneNetworks {

    private static final UUID NETWORK_ID = UUID.randomUUID();
    private static final UUID OWNER_ID   = UUID.randomUUID();
    private static final String OWNER_NAME = "testowner";

    /**
     * Dispatches prepareStatement() calls to mock PSTs by SQL content so all
     * SQL variants in the routine can be intercepted with a single expect.
     */
    private static IAnswer<PreparedStatement> pstDispatcher(
            PreparedStatement selectPst,
            PreparedStatement updatePst,
            PreparedStatement revertPst,
            PreparedStatement lockPst,
            PreparedStatement unlockPst) {
        return () -> {
            String sql = (String) getCurrentArguments()[0];
            if (sql.contains("solr_idx_lvl"))            return selectPst;
            if (sql.contains("'UNLISTED'"))               return updatePst;
            if (sql.contains("SET visibility = 'PUBLIC'")) return revertPst;
            if (sql.contains("islocked= true"))           return lockPst;
            if (sql.contains("islocked=?"))               return unlockPst;
            throw new IllegalArgumentException("Unexpected SQL in test: " + sql);
        };
    }

    private static PostgresNetworkDAO daoWithConnection(Connection conn) throws Exception {
        Constructor<PostgresNetworkDAO> ctor = PostgresNetworkDAO.class.getDeclaredConstructor(Connection.class);
        ctor.setAccessible(true);
        return ctor.newInstance(conn);
    }

    private static Connection setupMockConnection(
            PreparedStatement selectPst,
            PreparedStatement updatePst,
            PreparedStatement revertPst,
            PreparedStatement lockPst,
            PreparedStatement unlockPst) throws SQLException {
        Connection mockConn = createNiceMock(Connection.class);
        expect(mockConn.prepareStatement(anyString()))
                .andAnswer(pstDispatcher(selectPst, updatePst, revertPst, lockPst, unlockPst))
                .anyTimes();
        return mockConn;
    }

    // ── Tests ──────────────────────────────────────────────────────────────────

    @Test
    public void testEmptyResultSet_noWorkDone() throws Exception {
        PreparedStatement mockSelectPst = createNiceMock(PreparedStatement.class);
        PreparedStatement mockUpdatePst = createNiceMock(PreparedStatement.class);
        PreparedStatement mockRevertPst = createNiceMock(PreparedStatement.class);
        ResultSet mockRs = createNiceMock(ResultSet.class);

        expect(mockSelectPst.executeQuery()).andReturn(mockRs);
        expect(mockRs.next()).andReturn(false);

        Connection mockConn = setupMockConnection(
                mockSelectPst, mockUpdatePst, mockRevertPst,
                createNiceMock(PreparedStatement.class),
                createNiceMock(PreparedStatement.class));

        AtomicBoolean solrCalled = new AtomicBoolean(false);
        SolrIndexBuilder.SolrProcessor solrProcessor = (id, oId, oName) -> solrCalled.set(true);

        replay(mockConn, mockSelectPst, mockUpdatePst, mockRevertPst, mockRs);

        PostgresNetworkDAO dao = daoWithConnection(mockConn);
        SolrIndexBuilder.unlistPublicNoneNetworks(dao, solrProcessor);

        assertFalse("SolrProcessor must not be called for empty result set", solrCalled.get());
        verify(mockConn, mockSelectPst, mockUpdatePst, mockRevertPst, mockRs);
    }

    @Test
    public void testHappyPath_visibilityFlippedAndSolrCalled() throws Exception {
        PreparedStatement mockSelectPst = createNiceMock(PreparedStatement.class);
        PreparedStatement mockUpdatePst = createNiceMock(PreparedStatement.class);
        PreparedStatement mockRevertPst = createNiceMock(PreparedStatement.class);
        PreparedStatement mockLockPst   = createNiceMock(PreparedStatement.class);
        PreparedStatement mockUnlockPst = createNiceMock(PreparedStatement.class);
        ResultSet mockRs = createNiceMock(ResultSet.class);

        expect(mockSelectPst.executeQuery()).andReturn(mockRs);
        expect(mockRs.next()).andReturn(true).andReturn(false);
        expect(mockRs.getObject(1)).andReturn(NETWORK_ID);
        expect(mockRs.getObject(2)).andReturn(OWNER_ID);
        expect(mockRs.getString(3)).andReturn(OWNER_NAME);

        expect(mockLockPst.executeUpdate()).andReturn(1);
        expect(mockUpdatePst.executeUpdate()).andReturn(1);

        Connection mockConn = setupMockConnection(
                mockSelectPst, mockUpdatePst, mockRevertPst, mockLockPst, mockUnlockPst);

        AtomicReference<UUID>   capturedId        = new AtomicReference<>();
        AtomicReference<UUID>   capturedOwnerId   = new AtomicReference<>();
        AtomicReference<String> capturedOwnerName = new AtomicReference<>();
        SolrIndexBuilder.SolrProcessor solrProcessor = (id, oId, oName) -> {
            capturedId.set(id);
            capturedOwnerId.set(oId);
            capturedOwnerName.set(oName);
        };

        replay(mockConn, mockSelectPst, mockUpdatePst, mockRevertPst, mockLockPst, mockUnlockPst, mockRs);

        PostgresNetworkDAO dao = daoWithConnection(mockConn);
        SolrIndexBuilder.unlistPublicNoneNetworks(dao, solrProcessor);

        assertEquals(NETWORK_ID,  capturedId.get());
        assertEquals(OWNER_ID,    capturedOwnerId.get());
        assertEquals(OWNER_NAME,  capturedOwnerName.get());
        verify(mockConn, mockSelectPst, mockUpdatePst, mockRevertPst, mockLockPst, mockUnlockPst, mockRs);
    }

    @Test
    public void testSolrFailure_compensatesAndFastFails() throws Exception {
        PreparedStatement mockSelectPst = createNiceMock(PreparedStatement.class);
        PreparedStatement mockUpdatePst = createNiceMock(PreparedStatement.class);
        PreparedStatement mockRevertPst = createNiceMock(PreparedStatement.class);
        PreparedStatement mockLockPst   = createNiceMock(PreparedStatement.class);
        PreparedStatement mockUnlockPst = createNiceMock(PreparedStatement.class);
        ResultSet mockRs = createNiceMock(ResultSet.class);

        expect(mockSelectPst.executeQuery()).andReturn(mockRs);
        expect(mockRs.next()).andReturn(true).andReturn(false);
        expect(mockRs.getObject(1)).andReturn(NETWORK_ID);
        expect(mockRs.getObject(2)).andReturn(OWNER_ID);
        expect(mockRs.getString(3)).andReturn(OWNER_NAME);

        // Phase 1 lock + compensation lock (2 total)
        expect(mockLockPst.executeUpdate()).andReturn(1).times(2);
        expect(mockUpdatePst.executeUpdate()).andReturn(1);
        // Compensating revert must happen
        expect(mockRevertPst.executeUpdate()).andReturn(1);

        Connection mockConn = setupMockConnection(
                mockSelectPst, mockUpdatePst, mockRevertPst, mockLockPst, mockUnlockPst);

        RuntimeException solrEx = new RuntimeException("Solr unavailable");
        SolrIndexBuilder.SolrProcessor solrProcessor = (id, oId, oName) -> { throw solrEx; };

        replay(mockConn, mockSelectPst, mockUpdatePst, mockRevertPst, mockLockPst, mockUnlockPst, mockRs);

        PostgresNetworkDAO dao = daoWithConnection(mockConn);
        try {
            SolrIndexBuilder.unlistPublicNoneNetworks(dao, solrProcessor);
            fail("Expected exception to propagate");
        } catch (RuntimeException e) {
            assertSame("Original Solr exception must be rethrown", solrEx, e);
        }

        // verify() confirms mockRevertPst.executeUpdate() was called (compensating update ran)
        verify(mockConn, mockSelectPst, mockUpdatePst, mockRevertPst, mockLockPst, mockUnlockPst, mockRs);
    }

    @Test
    public void testDbUpdateFailure_noSolrCallAndFastFails() throws Exception {
        PreparedStatement mockSelectPst = createNiceMock(PreparedStatement.class);
        PreparedStatement mockUpdatePst = createNiceMock(PreparedStatement.class);
        PreparedStatement mockRevertPst = createNiceMock(PreparedStatement.class);
        PreparedStatement mockLockPst   = createNiceMock(PreparedStatement.class);
        PreparedStatement mockUnlockPst = createNiceMock(PreparedStatement.class);
        ResultSet mockRs = createNiceMock(ResultSet.class);

        expect(mockSelectPst.executeQuery()).andReturn(mockRs);
        expect(mockRs.next()).andReturn(true).andReturn(false);
        expect(mockRs.getObject(1)).andReturn(NETWORK_ID);
        expect(mockRs.getObject(2)).andReturn(OWNER_ID);
        expect(mockRs.getString(3)).andReturn(OWNER_NAME);

        expect(mockLockPst.executeUpdate()).andReturn(1);
        // Update returns 0 rows — triggers NdexException from phase 1
        expect(mockUpdatePst.executeUpdate()).andReturn(0);

        Connection mockConn = setupMockConnection(
                mockSelectPst, mockUpdatePst, mockRevertPst, mockLockPst, mockUnlockPst);

        AtomicBoolean solrCalled = new AtomicBoolean(false);
        SolrIndexBuilder.SolrProcessor solrProcessor = (id, oId, oName) -> solrCalled.set(true);

        replay(mockConn, mockSelectPst, mockUpdatePst, mockRevertPst, mockLockPst, mockUnlockPst, mockRs);

        PostgresNetworkDAO dao = daoWithConnection(mockConn);
        try {
            SolrIndexBuilder.unlistPublicNoneNetworks(dao, solrProcessor);
            fail("Expected NdexException to propagate");
        } catch (NdexException e) {
            assertTrue("Exception message must include network UUID",
                    e.getMessage().contains(NETWORK_ID.toString()));
        }

        assertFalse("SolrProcessor must not be called when DB update fails", solrCalled.get());
        verify(mockConn, mockSelectPst, mockUpdatePst, mockRevertPst, mockLockPst, mockUnlockPst, mockRs);
    }
}
