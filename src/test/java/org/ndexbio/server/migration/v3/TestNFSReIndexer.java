package org.ndexbio.server.migration.v3;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;

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
        assert NFSReIndexer.REINDEX_COUNT_SQL.contains("error IS NULL");
        assert NFSReIndexer.REINDEX_COUNT_SQL.contains("error LIKE '" + prefix + "%'");
        assert NFSReIndexer.REINDEX_SELECT_SQL.contains("error IS NULL");
        assert NFSReIndexer.REINDEX_SELECT_SQL.contains("error LIKE '" + prefix + "%'");
    }
}
