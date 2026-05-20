package org.ndexbio.server.migration.v3;

import com.fasterxml.jackson.core.JsonParseException;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.solr.client.solrj.SolrClient;
import org.apache.solr.client.solrj.SolrServerException;
import org.ndexbio.common.access.NdexDatabase;
import org.ndexbio.common.models.dao.DAOFactory;
import org.ndexbio.common.models.dao.FolderDAO;
import org.ndexbio.common.models.dao.NetworkDAO;
import org.ndexbio.common.models.dao.ShortcutDAO;
import org.ndexbio.common.models.dao.postgresql.UserDAO;
import org.ndexbio.common.persistence.CX2NetworkLoader;
import org.ndexbio.common.solr.*;
import org.ndexbio.cx2.aspect.element.core.CxAttributeDeclaration;
import org.ndexbio.cx2.aspect.element.core.CxNetworkAttribute;
import org.ndexbio.cx2.aspect.element.core.CxNode;
import org.ndexbio.cx2.aspect.element.core.DeclarationEntry;
import org.ndexbio.cxio.aspects.datamodels.ATTRIBUTE_DATA_TYPE;
import org.ndexbio.cxio.aspects.datamodels.NetworkAttributesElement;
import org.ndexbio.cxio.aspects.datamodels.NodeAttributesElement;
import org.ndexbio.cxio.aspects.datamodels.NodesElement;
import org.ndexbio.cxio.core.AspectIterator;
import org.ndexbio.model.cx.FunctionTermElement;
import org.ndexbio.model.exceptions.NdexException;
import org.ndexbio.model.object.*;
import org.ndexbio.model.object.network.NetworkSummary;
import org.ndexbio.model.object.network.VisibilityType;
import org.ndexbio.rest.Configuration;
import org.ndexbio.task.SolrIndexScope;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.*;

public class NFSReIndexer implements Runnable,AutoCloseable {
    protected final static Logger logger = LoggerFactory.getLogger(NFSReIndexer.class.getSimpleName());
    private final Connection db;
    private final SolrObjectFactory solrObjectFactory;
    private int networksProcessed = 0;
    private final ObjectMapper mapper;
    private final DAOFactory daoFactory;

    public NFSReIndexer() throws NdexException, SQLException {
        Configuration configuration = Configuration.createInstance();
        NdexDatabase.createNdexDatabase(configuration.getDBURL(), configuration.getDBUser(),
                configuration.getDBPasswd(), 10);
        this.db = NdexDatabase.getInstance().getConnection();
        this.db.setAutoCommit(false);
        this.mapper = new ObjectMapper();
        this.solrObjectFactory = Configuration.getInstance().getSolrObjectFactory();//new CachingSolrObjectFactoryImpl(configuration.getSolrURL());
        this.daoFactory = Configuration.getInstance().getDAOFactory();
    }

    @Override
    public void run() {
        try (UserDAO userDAO = daoFactory.getUserDAO();
             FolderDAO folderDAO = daoFactory.getFolderDAO();
             ShortcutDAO shortcutDAO = daoFactory.getShortcutDAO();
             NetworkDAO networkDAO = daoFactory.getNetworkDAO()) {

            V3Migrator.DaoSet dao = new V3Migrator.DaoSet(userDAO, folderDAO, shortcutDAO, networkDAO);
            resetIndexes(); //should clear public-nfs and private-nfs indexes
            reIndexFolders(dao); // should pull all folders from db and index
            reIndexShortcuts(dao); // should pull all shortcuts from db and index.
            reIndexNetworks(dao); // already implemented


        } catch (Exception e) {
            throw new RuntimeException(e);
        }


    }

    public void resetIndexes() throws Exception {
        logger.info("Resetting NFS Solr indexes...");
        try (FolderIndexManager fim = solrObjectFactory.getFolderIndexManager()) {
            fim.createCoreIfNeeded();

            SolrClient publicClient = solrObjectFactory.getSolrClient(NFSIndexManager.publicCoreName);
            publicClient.deleteByQuery("*:*");
            publicClient.commit();

            SolrClient privateClient = solrObjectFactory.getSolrClient(NFSIndexManager.privateCoreName);
            privateClient.deleteByQuery("*:*");
            privateClient.commit();
        }
        logger.info("NFS Solr indexes reset.");
    }


    public void reIndexFolders(V3Migrator.DaoSet dao) throws Exception {
        int totalFolders = 0;
        int foldersProcessed = 0;
        try (PreparedStatement countPst = db.prepareStatement(
                "SELECT COUNT(*) FROM folder WHERE is_deleted = false");
             ResultSet countRs = countPst.executeQuery()) {
            if (countRs.next()) totalFolders = countRs.getInt(1);
        }
        logger.info("Found {} folders to reindex.", totalFolders);

        String sql = "SELECT f.\"UUID\", f.name, f.description, f.creation_time, f.modification_time, "
                + "f.visibility, f.owneruuid "
                + "FROM folder f WHERE f.is_deleted = false";

        try (PreparedStatement pst = db.prepareStatement(sql);
             ResultSet rs = pst.executeQuery();
             FolderIndexManager fim = solrObjectFactory.getFolderIndexManager()) {

            while (rs.next()) {
                UUID folderId = (UUID) rs.getObject(1);
                try {
                    NdexFolder folder = new NdexFolder();
                    folder.setExternalId(folderId);
                    folder.setName(rs.getString(2));
                    folder.setDescription(rs.getString(3));
                    folder.setCreationTime(rs.getTimestamp(4));
                    folder.setModificationTime(rs.getTimestamp(5));
                    VisibilityType vis = VisibilityType.valueOf(rs.getString(6));
                    UUID ownerId = (UUID) rs.getObject(7);

                    // Look up owner username
                    String ownerName = getUsernameById(ownerId, dao);
                    folder.setOwner(ownerName);

                    // Load folder permissions
                    Map<String, List<String>> perms = loadFolderPermissions(folderId, dao);

                    fim.createIndex(folder, vis, perms.get("READ"), perms.get("WRITE"));

                    foldersProcessed++;
                    if (foldersProcessed % 100 == 0) {
                        logger.info("[Folders] {}/{} ({}%)",
                                foldersProcessed, totalFolders,
                                (foldersProcessed * 100) / totalFolders);
                    }
                } catch (Exception e) {
                    logger.info("Failed to reindex folder " + folderId, e);
                }
            }
        }
        logger.info("Reindexed {} folders total.", foldersProcessed);
    }

    public void reIndexShortcuts(V3Migrator.DaoSet dao) throws Exception {
        int totalShortcuts = 0;
        int shortcutsProcessed = 0;
        try (PreparedStatement countPst = db.prepareStatement(
                "SELECT COUNT(*) FROM shortcut WHERE is_deleted = false");
             ResultSet countRs = countPst.executeQuery()) {
            if (countRs.next()) totalShortcuts = countRs.getInt(1);
        }
        logger.info("Found {} shortcuts to reindex.", totalShortcuts);

        String sql = "SELECT s.\"UUID\", s.name, s.creation_time, s.modification_time, "
                + "s.visibility, s.owneruuid, s.target, s.target_type, s.parent "
                + "FROM shortcut s WHERE s.is_deleted = false";

        try (PreparedStatement pst = db.prepareStatement(sql);
             ResultSet rs = pst.executeQuery();
             ShortcutIndexManager sim = solrObjectFactory.getShortcutIndexManager()) {

            while (rs.next()) {
                UUID shortcutId = (UUID) rs.getObject(1);
                try {
                    NdexShortcut shortcut = new NdexShortcut();
                    shortcut.setExternalId(shortcutId);
                    shortcut.setName(rs.getString(2));
                    shortcut.setCreationTime(rs.getTimestamp(3));
                    shortcut.setModificationTime(rs.getTimestamp(4));
                    String visStr = rs.getString(5);
                    VisibilityType vis = visStr != null ? VisibilityType.valueOf(visStr) : VisibilityType.PRIVATE;
                    UUID ownerId = (UUID) rs.getObject(6);
                    shortcut.setTarget((UUID) rs.getObject(7));
                    shortcut.setTargetType(FileType.valueOf(rs.getString(8).toUpperCase()));
                    shortcut.setParent((UUID) rs.getObject(9));
                    shortcut.setIsDeleted(false);

                    String ownerName = getUsernameById(ownerId, dao);
                    shortcut.setOwner(ownerName);

                    // Shortcuts have no shareable permissions — only owner + visibility govern access (see PostgresShortcutDAO.isReadable)
                    sim.createIndex(shortcut, vis, null, null);
                    
                    shortcutsProcessed++;
                    if (shortcutsProcessed % 500 == 0) {
                        logger.info("[Shortcuts] {}/{} ({}%)",
                                shortcutsProcessed, totalShortcuts,
                                (shortcutsProcessed * 100) / totalShortcuts);
                    }
                } catch (Exception e) {
                    logger.info("Failed to reindex shortcut " + shortcutId, e);
                }
            }
        }
        logger.info("Reindexed {} shortcuts total.", shortcutsProcessed);
    }

    // ========================================================================
    // Helpers
    // ========================================================================

    private String getUsernameById(UUID userId, V3Migrator.DaoSet dao) {
        try {
            User u = dao.userDAO.getUserById(userId, false, false);
            return u != null ? u.getUserName() : null;
        } catch (Exception e) {
            logger.info("Could not look up username for " + userId);
            return null;
        }
    }

    /**
     * Load folder permissions and resolve user IDs to usernames.
     * Returns map with "READ" and "WRITE" keys, each containing a list of usernames.
     */
    private Map<String, List<String>> loadFolderPermissions(UUID folderId, V3Migrator.DaoSet dao) throws SQLException {
        Map<String, List<String>> result = new HashMap<>();
        result.put("READ", new ArrayList<>());
        result.put("WRITE", new ArrayList<>());

        String sql = "SELECT fp.user_id, fp.permission FROM folder_permission fp WHERE fp.folder_id = ?";
        try (PreparedStatement pst = db.prepareStatement(sql)) {
            pst.setObject(1, folderId);
            try (ResultSet rs = pst.executeQuery()) {
                while (rs.next()) {
                    UUID userId = (UUID) rs.getObject(1);
                    String perm = rs.getString(2);
                    String username = getUsernameById(userId, dao);
                    if (username != null && result.containsKey(perm)) {
                        result.get(perm).add(username);
                    }
                }
            }
        }
        return result;
    }
    public void reIndexNetworks(V3Migrator.DaoSet dao) throws Exception {
        int totalNetworks = 0;
        try (PreparedStatement countPst = db.prepareStatement("SELECT COUNT(*) FROM network WHERE is_deleted = false");
             ResultSet countRs = countPst.executeQuery()) {
            if (countRs.next()) totalNetworks = countRs.getInt(1);
        }
        logger.info("Found {} networks to reindex.", totalNetworks);

        String sql = "SELECT \"UUID\", owneruuid, \"owner\", visibility "
                + "FROM network WHERE is_deleted = false";

        try (PreparedStatement pst = db.prepareStatement(sql);
             ResultSet rs = pst.executeQuery();
             GlobalNetworkIndexManager globalNetworkIndexManager = solrObjectFactory.getGlobalNetworkIndexManager()) {

            while (rs.next()) {
                UUID networkId = (UUID) rs.getObject(1);
                logger.info("Starting for {}", networkId);
                try {
                    UUID ownerId = (UUID) rs.getObject(2);
                    String ownerName = rs.getString(3);
                    String visibility = rs.getString(4);

                    reindexNetwork(networkId, ownerId, ownerName, visibility, dao,globalNetworkIndexManager);

                    networksProcessed++;
                    if (networksProcessed % 500 == 0) {
                        logger.info("[Phase 3] {}/{} ({}%)",
                                networksProcessed, totalNetworks,
                                (networksProcessed * 100) / totalNetworks);
                    }
                } catch (Exception e) {
                    logger.info("Failed to reindex network " + networkId, e);
                }
            }
        }
        logger.info("Reindexed " + networksProcessed + " networks total.");
    }
    /**
     * Rebuild the Solr index for a single network using the new permission model.
     * Reads user_network_membership to build the permission map for the index.
     */
    private void reindexNetwork(UUID networkId, UUID ownerId, String ownerName,
                                String visibility, V3Migrator.DaoSet dao, GlobalNetworkIndexManager globalNetworkIndexManager) throws Exception {
        
        rebuildNetworkIndex(networkId, false, false, globalNetworkIndexManager,
                dao.networkDAO);

    }

    @Override
    public void close() throws Exception {

    }

    private void rebuildNetworkIndex(UUID fileId, boolean createOnly, boolean ignoreCxFiles,
                                     GlobalNetworkIndexManager globalNetworkIndexManager,
                                     NetworkDAO dao) throws Exception {

        try {
            String id = fileId.toString();
            NetworkSummary summary = dao.getNetworkSummaryById(fileId);
            VisibilityType visibilityType = dao.getNetworkVisibility(fileId);
            SolrIndexScope idxScope;

            if (summary.getNodeCount() >= SingleNetworkSolrIdxManager.AUTOCREATE_THRESHHOLD)
                idxScope = SolrIndexScope.both;
            else
                idxScope = SolrIndexScope.global;

            // drop the old ones.
            if (!createOnly) {
                globalNetworkIndexManager.delete(id, visibilityType);
                
                if ( idxScope == SolrIndexScope.both)
					try (SingleNetworkSolrIdxManager idx2 = new SingleNetworkSolrIdxManager(fileId.toString())) {
						idx2.dropIndex();
					}
            }
            
            //build the individual index for queries
			if (idxScope == SolrIndexScope.both) {
				long t1 = Calendar.getInstance().getTimeInMillis();
				try (SingleNetworkSolrIdxManager idx2 = new SingleNetworkSolrIdxManager(fileId.toString())) {
						idx2.createIndexFromCx2(null);
				}
				long t = Calendar.getInstance().getTimeInMillis() -t1;
				System.out.println("Takes " + t/1000 +" secs to create index for network " + fileId.toString());
			}
            
            

            // build the solr document obj
            List<Map<Permissions, Collection<String>>> permissionTable = dao
                    .getAllMembershipsOnNetwork(fileId);
            Map<Permissions, Collection<String>> userMemberships = permissionTable.get(0);
            globalNetworkIndexManager.prepareIndexDocument(summary, visibilityType,
                    userMemberships.get(Permissions.READ), userMemberships.get(Permissions.WRITE));

            String pathPrefix = Configuration.getInstance().getNdexRoot() + "/data/";
            String cx2AspectPath = pathPrefix + id + "/" + CX2NetworkLoader.cx2AspectDirName + "/";
            File attrFile = new File(cx2AspectPath + CxNetworkAttribute.ASPECT_NAME);
            File functionAspectFile = new File(cx2AspectPath + FunctionTermElement.ASPECT_NAME);

            // Always index network attributes (META + ALL behavior)
            if (attrFile.exists() && !ignoreCxFiles) {

                File declFile = new File(cx2AspectPath + CxAttributeDeclaration.ASPECT_NAME);
                ObjectMapper om = new ObjectMapper();

                CxAttributeDeclaration[] declarations = om.readValue(declFile, CxAttributeDeclaration[].class);

                CxNetworkAttribute[] attrs = om.readValue(attrFile, CxNetworkAttribute[].class);
                attrs[0].extendToFullNode(declarations[0].getAttributesInAspect(CxNetworkAttribute.ASPECT_NAME));

                List<String> indexWarnings = globalNetworkIndexManager.addCX2NetworkAttrToIndex(attrs[0]);
                if (!indexWarnings.isEmpty())
                    for (String warning : indexWarnings)
                        System.err.println("Warning: " + warning);
            }
            else {
                try (AspectIterator<NetworkAttributesElement> it = new AspectIterator<>(id,
                        NetworkAttributesElement.ASPECT_NAME, NetworkAttributesElement.class, pathPrefix)) {
                    while (it.hasNext()) {
                        NetworkAttributesElement e = it.next();
                        if (!e.getName().equals(NFSIndexManager.NAME)){
                            List<String> indexWarnings = globalNetworkIndexManager.addCXNetworkAttrToIndex(e);
                            if (!indexWarnings.isEmpty())
                                for (String warning : indexWarnings)
                                    System.err.println("Warning: " + warning);
                        }

                    }
                }
            }

            // Always index node attributes and nodes (ALL behavior)
            if (functionAspectFile.exists() && !ignoreCxFiles) {
                ObjectMapper om = new ObjectMapper();

                try (FileInputStream inputStream = new FileInputStream(cx2AspectPath + FunctionTermElement.ASPECT_NAME)) {

                    Iterator<FunctionTermElement> it = om.readerFor(FunctionTermElement.class).readValues(inputStream);

                    while (it.hasNext()) {
                        FunctionTermElement fun = it.next();
                        globalNetworkIndexManager.addFunctionTermToIndex(fun);
                    }
                }

                processCx2Nodes(cx2AspectPath, om, globalNetworkIndexManager);

            } else {
                try (AspectIterator<FunctionTermElement> it = new AspectIterator<>(fileId.toString(),
                        FunctionTermElement.ASPECT_NAME, FunctionTermElement.class, pathPrefix)) {
                    while (it.hasNext()) {
                        FunctionTermElement fun = it.next();
                        globalNetworkIndexManager.addFunctionTermToIndex(fun);
                    }
                }

                try (AspectIterator<NodeAttributesElement> it = new AspectIterator<>(fileId.toString(),
                        NodeAttributesElement.ASPECT_NAME, NodeAttributesElement.class, pathPrefix)) {
                    while (it.hasNext()) {
                        NodeAttributesElement e = it.next();
                        globalNetworkIndexManager.addCXNodeAttrToIndex(e);
                    }
                }

                try (AspectIterator<NodesElement> it = new AspectIterator<>(fileId.toString(), NodesElement.ASPECT_NAME,
                        NodesElement.class, pathPrefix)) {
                    while (it.hasNext()) {
                        NodesElement e = it.next();
                        globalNetworkIndexManager.addCXNodeToIndex(e);
                    }
                }
            }

            globalNetworkIndexManager.commit(visibilityType);


            try {
                dao.setFlag(fileId, "iscomplete", true);
                dao.commit();
            } catch (SQLException e) {
                throw new NdexException("DB error when setting iscomplete flag: " + e.getMessage(), e);
            }

        } catch (SQLException | IOException | NdexException | SolrServerException e1) {
            e1.printStackTrace();
            try {
                dao.setErrorMessage(fileId, "Failed to create Index on network."
                        + " Cause: " + e1.getMessage());
                dao.commit();
            } catch (Exception e2){
                e2.printStackTrace();
            }
            throw e1;
        }

    }

    private static void processCx2Nodes(String cx2AspectPath, ObjectMapper om, GlobalNetworkIndexManager globalIdx) throws JsonParseException, JsonMappingException, IOException {
        File declFile = new File(cx2AspectPath + CxAttributeDeclaration.ASPECT_NAME);
        if (!declFile.exists())
            return;

        CxAttributeDeclaration[] declarations = om.readValue(declFile,
                CxAttributeDeclaration[].class);

        if ( declarations.length == 0 || ! declarations[0].getDeclarations().containsKey(CxNode.ASPECT_NAME))
            return ;

        Map<String, DeclarationEntry> nodeAttributeDecls = declarations[0].getAttributesInAspect(CxNode.ASPECT_NAME);
        if ( nodeAttributeDecls.size() == 0 )
            return ;

        Map<String, Map.Entry<String,DeclarationEntry>> attributeNameMapping = new HashMap<> ();
        for ( Map.Entry<String,DeclarationEntry> entry: nodeAttributeDecls.entrySet()) {
            String attrName = entry.getKey();
            if (attrName.equals(CxNode.NAME)) {
                if ( entry.getValue().getDataType() == null ||
                        entry.getValue().getDataType() == ATTRIBUTE_DATA_TYPE.STRING)
                    attributeNameMapping.put (CxNode.NAME, entry);
            } else if (attrName.equals(CxNode.REPRESENTS) ) {
                if ( entry.getValue().getDataType() == null ||
                        entry.getValue().getDataType() == ATTRIBUTE_DATA_TYPE.STRING)
                    attributeNameMapping.put (CxNode.REPRESENTS, entry);
            } else if ( attrName.equalsIgnoreCase(SingleNetworkSolrIdxManager.ALIAS) ) {
                if ( entry.getValue().getDataType() == ATTRIBUTE_DATA_TYPE.LIST_OF_STRING) {
                    attributeNameMapping.put (SingleNetworkSolrIdxManager.ALIAS, entry);
                }
            } else if ( attrName.equalsIgnoreCase(SingleNetworkSolrIdxManager.TYPE)) {
                if ( entry.getValue().getDataType() == null ||
                        entry.getValue().getDataType() == ATTRIBUTE_DATA_TYPE.STRING)
                    attributeNameMapping.put (SingleNetworkSolrIdxManager.TYPE, entry);
            } else if ( attrName.equalsIgnoreCase(SingleNetworkSolrIdxManager.MEMBER)) {
                if ( entry.getValue().getDataType() == null ||
                        entry.getValue().getDataType() == ATTRIBUTE_DATA_TYPE.STRING)
                    attributeNameMapping.put (SingleNetworkSolrIdxManager.MEMBER, entry);
            }

        }

        File nodeAspectFile = new File (cx2AspectPath + CxNode.ASPECT_NAME);
        if ( nodeAspectFile.exists()) {
            //go through node aspect
            try (FileInputStream inputStream = new FileInputStream(cx2AspectPath + "nodes")) {

                Iterator<CxNode> it = om.readerFor(CxNode.class).readValues(inputStream);

                while (it.hasNext()) {
                    CxNode node = it.next();
                    node.extendToFullNode(nodeAttributeDecls);

                    globalIdx.addCX2NodeToIndex(node, attributeNameMapping);
                }
            }
        }

    }

}
