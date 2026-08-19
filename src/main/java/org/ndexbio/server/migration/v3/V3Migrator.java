package org.ndexbio.server.migration.v3;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.*;

import com.fasterxml.jackson.core.JsonParseException;
import com.fasterxml.jackson.databind.JsonMappingException;
import org.apache.solr.client.solrj.SolrServerException;
import org.ndexbio.common.NdexClasses;
import org.ndexbio.common.access.NdexDatabase;
import org.ndexbio.common.models.dao.DAOFactory;
import org.ndexbio.common.models.dao.FolderDAO;
import org.ndexbio.common.models.dao.NetworkDAO;
import org.ndexbio.common.models.dao.ShortcutDAO;
import org.ndexbio.common.models.dao.postgresql.*;
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

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.ndexbio.task.NdexServerQueue;
import org.ndexbio.task.SolrIndexScope;
import org.ndexbio.task.SolrTaskDeleteFile;
import org.ndexbio.task.SolrTaskRebuildFileIdx;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class V3Migrator implements AutoCloseable {


	protected final static Logger logger = LoggerFactory.getLogger(V3Migrator.class.getSimpleName());

	private final Connection db;
	private final ObjectMapper mapper;
	private final TypeReference<Map<String, Object>> mapTypeRef;
	private final SolrObjectFactory solrObjectFactory;

	// --- Migration stats ---
	private int networksProcessed = 0;
	private int usersProcessed = 0;

	//todo v2 endpoints should correspond network sets with folders
	//todo
	private List<String> preferredUsers;
	private List<UUID> preferredUserIds;
	private final DAOFactory daoFactory;
	private final Path priorityUserFilePath;

	public V3Migrator(String priorityUserFilePath) throws Exception {
		Configuration configuration = Configuration.createInstance();
		NdexDatabase.createNdexDatabase(configuration.getDBURL(), configuration.getDBUser(),
				configuration.getDBPasswd(), 10);
		this.db = NdexDatabase.getInstance().getConnection();
		this.db.setAutoCommit(false);
		this.mapper = new ObjectMapper();
		this.solrObjectFactory = Configuration.getInstance().getSolrObjectFactory();//new CachingSolrObjectFactoryImpl(configuration.getSolrURL());
		this.daoFactory = Configuration.getInstance().getDAOFactory();

		this.mapTypeRef = new TypeReference<>() {};
		this.priorityUserFilePath = Path.of(priorityUserFilePath);
	}

	/**
	 * Run the full v3 migration in order.
	 */
	public void run() throws Exception {
		try (UserDAO userDAO = daoFactory.getUserDAO();
			 FolderDAO folderDAO = daoFactory.getFolderDAO();
			 ShortcutDAO shortcutDAO = daoFactory.getShortcutDAO();
			 NetworkDAO networkDAO = daoFactory.getNetworkDAO()) {

			DaoSet dao = new DaoSet(userDAO, folderDAO, shortcutDAO, networkDAO);

			logger.info("=== V3 Migration Starting ===");

			logger.info("--- Setup: Loading preferred Owners");
			setupCoresAndPreferredUsers(userDAO);

			logger.info("--- Phase 3: Networks -> Verify in Home Folder + Migrate Access Keys ---");
			processNetworks(dao);

			//logger.info("--- Phase 4: Users -> Set search opt-in default ---");
			//processUsers();

			logger.info("=== V3 Migration Complete ===");
			logger.info(String.format(
					"Stats: networks=%d, users=%d",
					networksProcessed, usersProcessed));
		}
	}

	public void setupCoresAndPreferredUsers(UserDAO userDAO) throws NdexException {
		logger.info("Reading priority user file...");
		preferredUsers = readPriorityUsers(priorityUserFilePath);
		logger.info("Fetching accounts for {} preferred users. ({})", preferredUsers.size(), String.join(",", preferredUsers));
		preferredUserIds = new ArrayList<>();
		for (String username: preferredUsers){
            try {
                User user = userDAO.getUserByAccountName(username, false, false);
				preferredUserIds.add(user.getExternalId());
            } catch (NdexException | IOException | SQLException e) {
				logger.info("Failed to fetch priority user with username {}", username);
                throw new NdexException("Failed to fetch user with username " + username);
            }
        }
		try (FolderIndexManager indexManager = solrObjectFactory.getFolderIndexManager()){
			indexManager.createCoreIfNeeded();
		} catch (SolrServerException | IOException | NdexException e) {
			logger.info("Failed to create core!");
            throw new NdexException("Failed to create core!");
        }

    }


	// ========================================================================
	// Phase 3: Networks -> Home Folder + Access Keys
	// ========================================================================

	public void processNetworks(DaoSet dao) throws Exception {
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
								String visibility, DaoSet dao, GlobalNetworkIndexManager globalNetworkIndexManager) throws Exception {
		NetworkSummary ns = dao.networkDAO.getNetworkSummaryById(networkId);
		if (ns == null) return;

		VisibilityType vis = VisibilityType.valueOf(visibility);
		rebuildNetworkIndex(networkId, false, false, globalNetworkIndexManager,
				dao.networkDAO);

	}

	private String getUsernameById(UUID userId, DaoSet dao) {
		try {
			User u = dao.userDAO.getUserById(userId, false, false);
			return u != null ? u.getUserName() : null;
		} catch (Exception e) {
			logger.info("Could not look up username for " + userId);
			return null;
		}
	}

	/**
	 * Load all user permissions for a network from user_network_membership.
	 * Returns map of userId.toString() -> permission string.
	 */
	private Map<String, String> loadNetworkUserPermissions(UUID networkId) throws SQLException {
		Map<String, String> perms = new HashMap<>();
		String sql = "SELECT user_id, permission_type FROM user_network_membership WHERE network_id = ?";
		try (PreparedStatement pst = db.prepareStatement(sql)) {
			pst.setObject(1, networkId);
			try (ResultSet rs = pst.executeQuery()) {
				while (rs.next()) {
					perms.put(rs.getObject(1).toString(), rs.getString(2));
				}
			}
		}
		return perms;
	}

	// ========================================================================
	// Phase 4: Users -> Search Opt-in
	// ========================================================================


	public void processUsers() throws Exception {
		// User search opt-in: not yet implemented, ndex_user schema changes pending.
		// TODO: add is_searchable column to ndex_user and set default to false
		logger.info("User search opt-in migration skipped (schema not ready).");
	}

	// ========================================================================
	// Shared helpers
	// ============================================loadNetworkUserPermissions============================

	private List<String> readPriorityUsers(Path priorityUsersPath) throws NdexException {
		if (!Files.exists(priorityUsersPath)){
			logger.info("Priority user file not found at {}!", priorityUsersPath);
			throw new NdexException("Priority user file path not found!");
		}
		try {
			return Files.readAllLines(priorityUsersPath).stream()
					.map(String::trim)
					.filter(line -> !line.isEmpty() && !line.startsWith("#"))
					.toList();
		} catch (IOException e) {
			logger.info("Could not read priority users file: {}", priorityUsersPath, e);
			throw new NdexException("Could not read priority user file");
		}
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

			}

			// build the solr document obj
			globalNetworkIndexManager.prepareIndexDocument(summary, visibilityType,
					dao.getNetworkFolder(fileId));

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
				dao.setErrorMessage(fileId, NdexClasses.NETWORK_INDEX_FAILED_MSG_PREFIX
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
			} else if ( attrName.equalsIgnoreCase(NodeIndexFields.ALIAS) ) {
				if ( entry.getValue().getDataType() == ATTRIBUTE_DATA_TYPE.LIST_OF_STRING) {
					attributeNameMapping.put (NodeIndexFields.ALIAS, entry);
				}
			} else if ( attrName.equalsIgnoreCase(NodeIndexFields.TYPE)) {
				if ( entry.getValue().getDataType() == null ||
						entry.getValue().getDataType() == ATTRIBUTE_DATA_TYPE.STRING)
					attributeNameMapping.put (NodeIndexFields.TYPE, entry);
			} else if ( attrName.equalsIgnoreCase(NodeIndexFields.MEMBER)) {
				if ( entry.getValue().getDataType() == null ||
						entry.getValue().getDataType() == ATTRIBUTE_DATA_TYPE.STRING)
					attributeNameMapping.put (NodeIndexFields.MEMBER, entry);
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
	// ========================================================================
	// Inner helper classes
	// ========================================================================

	static class DaoSet {
		final UserDAO userDAO;
		final FolderDAO folderDAO;
		final ShortcutDAO shortcutDAO;
		final NetworkDAO networkDAO;

		DaoSet(UserDAO u, FolderDAO f, ShortcutDAO s, NetworkDAO n) {
			this.userDAO = u;
			this.folderDAO = f;
			this.shortcutDAO = s;
			this.networkDAO = n;
		}
	}

}