package org.ndexbio.common.solr;

import java.io.IOException;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.apache.solr.client.solrj.SolrServerException;
import org.apache.solr.client.solrj.impl.HttpSolrClient;
import org.apache.solr.client.solrj.request.CoreAdminRequest;
import org.apache.solr.client.solrj.response.CoreAdminResponse;
import org.ndexbio.common.access.NdexDatabase;
import org.ndexbio.cxio.core.AspectIterator;
import org.ndexbio.common.models.dao.postgresql.GroupDAO;
import org.ndexbio.common.models.dao.postgresql.PostgresNetworkDAO;
import org.ndexbio.common.models.dao.postgresql.UserDAO;
import org.ndexbio.cxio.aspects.datamodels.NetworkAttributesElement;
import org.ndexbio.cxio.aspects.datamodels.NodeAttributesElement;
import org.ndexbio.cxio.aspects.datamodels.NodesElement;
import org.ndexbio.model.cx.FunctionTermElement;
import org.ndexbio.model.exceptions.NdexException;
import org.ndexbio.model.object.FileType;
import org.ndexbio.model.object.Group;
import org.ndexbio.model.object.Permissions;
import org.ndexbio.model.object.User;
import org.ndexbio.model.object.network.NetworkIndexLevel;
import org.ndexbio.model.object.network.NetworkSummary;
import org.ndexbio.model.object.network.VisibilityType;
import org.ndexbio.rest.Configuration;
import org.ndexbio.task.SolrTaskDeleteFile;
import org.ndexbio.task.SolrTaskRebuildFileIdx;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.core.JsonParseException;
import com.fasterxml.jackson.databind.JsonMappingException;

public class SolrIndexBuilder implements AutoCloseable {

	protected static Logger logger = LoggerFactory.getLogger(SolrIndexBuilder.class);

	NetworkGlobalIndexManager globalIdx ;

	public SolrIndexBuilder () throws NdexException, SolrServerException, IOException {
		globalIdx = new NetworkGlobalIndexManager();
		globalIdx.createCoreIfNotExists();

	}


	private  void rebuildNetworkIndex (UUID networkid, boolean ignoreDeletion ) throws SQLException, JsonParseException, JsonMappingException, IOException, NdexException, SolrServerException {
		try (PostgresNetworkDAO dao = new PostgresNetworkDAO()) {
			logger.info("Rebuild solr index of network " + networkid);
			NetworkSummary summary = dao.getNetworkSummaryById(networkid);
			if (summary == null)
				throw new NdexException ("Network "+ networkid + " not found in the server." );

			dao.lockNetwork(networkid);
			try (SingleNetworkSolrIdxManager idx2 = new SingleNetworkSolrIdxManager(networkid.toString())) {

				if (!ignoreDeletion) {
					try {
						globalIdx.deleteNetwork(networkid.toString());
						globalIdx.commit();
						idx2.dropIndex();
					} catch (IOException | SolrServerException | NdexException e) {
						e.printStackTrace();
						logger.warn("Warning: Failed to delete node Index for network " + networkid.toString());
					}

				}
				if (summary.getNodeCount() >= SingleNetworkSolrIdxManager.AUTOCREATE_THRESHHOLD ) {

					idx2.createIndex(null);
					idx2.close();

					logger.info("Solr index for query created.");
				}

				if ( summary.getIndexLevel() != NetworkIndexLevel.NONE) {
					// build the solr document obj
					List<Map<Permissions, Collection<String>>> permissionTable =  dao.getAllMembershipsOnNetwork(networkid);
					Map<Permissions,Collection<String>> userMemberships = permissionTable.get(0);
					Map<Permissions,Collection<String>> grpMemberships = permissionTable.get(1);
					globalIdx.createIndexDocFromSummary(summary,summary.getOwner(),
							userMemberships.get(Permissions.READ),
							userMemberships.get(Permissions.WRITE),
							grpMemberships.get(Permissions.READ),
							grpMemberships.get(Permissions.WRITE));

					//process node attribute aspect and add to solr doc
					String pathPrefix = Configuration.getInstance().getNdexRoot() + "/data/" ;

					try (AspectIterator<NetworkAttributesElement> it = new AspectIterator<>(networkid.toString(),
							NetworkAttributesElement.ASPECT_NAME, NetworkAttributesElement.class, pathPrefix)) {
						while (it.hasNext()) {
							NetworkAttributesElement e = it.next();

							List<String> indexWarnings = globalIdx.addCXNetworkAttrToIndex(e);
							if ( !indexWarnings.isEmpty())
								for (String warning : indexWarnings)
									System.err.println("Warning: " + warning);

						}
					}


					if (summary.getIndexLevel() == NetworkIndexLevel.ALL) {
						try (AspectIterator<FunctionTermElement> it = new AspectIterator<>(networkid.toString(),
								FunctionTermElement.ASPECT_NAME, FunctionTermElement.class, pathPrefix)) {
							while (it.hasNext()) {
								FunctionTermElement fun = it.next();

								globalIdx.addFunctionTermToIndex(fun);

							}
						}

						try (AspectIterator<NodeAttributesElement> it = new AspectIterator<>(networkid.toString(),
								NodeAttributesElement.ASPECT_NAME, NodeAttributesElement.class, pathPrefix)) {
							while (it.hasNext()) {
								NodeAttributesElement e = it.next();
								globalIdx.addCXNodeAttrToIndex(e);
							}
						}

						try (AspectIterator<NodesElement> it = new AspectIterator<>(networkid.toString(),
								NodesElement.ASPECT_NAME, NodesElement.class, pathPrefix)) {
							while (it.hasNext()) {
								NodesElement e = it.next();
								globalIdx.addCXNodeToIndex(e);
							}
						}

					}
					globalIdx.commit();

					logger.info("Solr index of network " + networkid + " created.");
				}
			} finally {
				dao.unlockNetwork(networkid);
			}
		}
	}


	private  void rebuildNetworkIndexInGlobalIdx (UUID networkid , boolean ignoreDeletion ) throws SQLException, JsonParseException, JsonMappingException, IOException, NdexException, SolrServerException {
		try (PostgresNetworkDAO dao = new PostgresNetworkDAO()) {
			logger.info("Rebuild global index of network " +networkid);

			NetworkSummary summary = dao.getNetworkSummaryById(networkid);
			if (summary == null)
				throw new NdexException ("Network "+ networkid + " not found in the server." );

			//dao.lockNetwork(networkid);

			if (!ignoreDeletion) {
				try {
					globalIdx.deleteNetwork(networkid.toString());
					globalIdx.commit();
				} catch (IOException | SolrServerException e) {
					e.printStackTrace();
					logger.warn("Warning: Failed to delete node Index for network " + networkid.toString());
				}

			}

			if ( summary.getIndexLevel() != NetworkIndexLevel.NONE) {
				// build the solr document obj
				List<Map<Permissions, Collection<String>>> permissionTable =  dao.getAllMembershipsOnNetwork(networkid);
				Map<Permissions,Collection<String>> userMemberships = permissionTable.get(0);
				Map<Permissions,Collection<String>> grpMemberships = permissionTable.get(1);
				globalIdx.createIndexDocFromSummary(summary,summary.getOwner(),
						userMemberships.get(Permissions.READ),
						userMemberships.get(Permissions.WRITE),
						grpMemberships.get(Permissions.READ),
						grpMemberships.get(Permissions.WRITE));

				//process node attribute aspect and add to solr doc
				String pathPrefix = Configuration.getInstance().getNdexRoot() + "/data/" ;

				try (AspectIterator<NetworkAttributesElement> it = new AspectIterator<>(networkid.toString(),
						NetworkAttributesElement.ASPECT_NAME, NetworkAttributesElement.class, pathPrefix)) {
					while (it.hasNext()) {
						NetworkAttributesElement e = it.next();

						List<String> indexWarnings = globalIdx.addCXNetworkAttrToIndex(e);
						if ( !indexWarnings.isEmpty())
							for (String warning : indexWarnings)
								System.err.println("Warning: " + warning);

					}
				}

				if (summary.getIndexLevel() == NetworkIndexLevel.ALL) {
					try (AspectIterator<FunctionTermElement> it = new AspectIterator<>(networkid.toString(),
							FunctionTermElement.ASPECT_NAME, FunctionTermElement.class, pathPrefix)) {
						while (it.hasNext()) {
							FunctionTermElement fun = it.next();

							globalIdx.addFunctionTermToIndex(fun);

						}
					}

					try (AspectIterator<NodeAttributesElement> it = new AspectIterator<>(networkid.toString(),
							NodeAttributesElement.ASPECT_NAME, NodeAttributesElement.class, pathPrefix)) {
						while (it.hasNext()) {
							NodeAttributesElement e = it.next();
							globalIdx.addCXNodeAttrToIndex(e);
						}
					}

					try (AspectIterator<NodesElement> it = new AspectIterator<>(networkid.toString(),
							NodesElement.ASPECT_NAME, NodesElement.class, pathPrefix)) {
						while (it.hasNext()) {
							NodesElement e = it.next();
							globalIdx.addCXNodeToIndex(e);
						}
					}

				}
				globalIdx.commit();

				// dao.unlockNetwork(networkid);
				logger.info("Solr index of network " + networkid + " created.");
			}

		}
	}


	private static  void rebuildLocalNetworkIndex (UUID networkid, boolean ignoreDeletion ) throws SQLException, JsonParseException, JsonMappingException, IOException, NdexException, SolrServerException {
		try (PostgresNetworkDAO dao = new PostgresNetworkDAO()) {
			logger.info("Rebuild local index of " + networkid);
			NetworkSummary summary = dao.getNetworkSummaryById(networkid);
			if (summary == null)
				throw new NdexException ("Network "+ networkid + " not found in the server." );

			//dao.lockNetwork(networkid);
			try (SingleNetworkSolrIdxManager idx2 = new SingleNetworkSolrIdxManager(networkid.toString())) {

				if (!ignoreDeletion) {
					try {
						idx2.dropIndex();
					} catch (IOException | SolrServerException | NdexException e) {
						e.printStackTrace();
						logger.warn("Warning: Failed to delete node Index for network " + networkid.toString());
					}

				}
				if (summary.getNodeCount() >= SingleNetworkSolrIdxManager.AUTOCREATE_THRESHHOLD ) {

					idx2.createIndex(null);
					idx2.close();

					logger.info("Solr index for query created.");
				}

			}
			//dao.unlockNetwork(networkid);

		}
	}



	private  void rebuildAll() throws SQLException, JsonParseException, JsonMappingException, IOException, NdexException, SolrServerException {
		try (PostgresNetworkDAO dao = new PostgresNetworkDAO ()) {
			@SuppressWarnings("resource")
			Connection db = dao.getDBConnection();
			String sqlStr = "select \"UUID\" from network n where n.iscomplete and n.is_deleted=false and n.is_validated and n.islocked=false and n.error is null";

			int i = 0;
			try (PreparedStatement pst = db.prepareStatement(sqlStr)) {
				try ( ResultSet rs = pst.executeQuery()) {
					while (rs.next()) {
						//int nodeCount = rs.getInt(2);
						rebuildNetworkIndex((UUID)rs.getObject(1), true);
						i ++;
						if ( i % 500 == 0 ) {
							System.err.println("Loaded " + i + " records to solr. sleep 2 seconds");
							//   globalIdx.commit();
							try {
								Thread.sleep(2000);
							} catch (InterruptedException e) {
								// TODO Auto-generated catch block
								e.printStackTrace();
							}
						}
					}
				}
			}
		}
		//	globalIdx.commit();
		logger.info("Indexes of all networks have been rebuilt.");
	}


	private  void rebuildGlobalIdx() throws SQLException, JsonParseException, JsonMappingException, IOException, NdexException, SolrServerException {
		try (PostgresNetworkDAO dao = new PostgresNetworkDAO ()) {
			@SuppressWarnings("resource")
			Connection db = dao.getDBConnection();
			String sqlStr = "select \"UUID\" from network n where n.iscomplete and n.is_deleted=false and n.is_validated and n.islocked=false and n.error is null";

			int i = 0;
			try (PreparedStatement pst = db.prepareStatement(sqlStr)) {
				try ( ResultSet rs = pst.executeQuery()) {
					while (rs.next()) {
						//int nodeCount = rs.getInt(2);
						rebuildNetworkIndexInGlobalIdx((UUID)rs.getObject(1), true);
						i ++;
						if ( i % 1000 == 0 ) {
							System.err.println("Loaded " + i + " records to solr. sleep 2 seconds");
							//   globalIdx.commit();
							try {
								Thread.sleep(2000);
							} catch (InterruptedException e) {
								// TODO Auto-generated catch block
								e.printStackTrace();
							}
						}
					}
				}
			}
		}
		//	globalIdx.commit();
		logger.info("Indexes of all networks have been rebuilt.");
	}


	private static  void rebuildAllLocalIdx() throws SQLException, JsonParseException, JsonMappingException, IOException, NdexException, SolrServerException {
		try (PostgresNetworkDAO dao = new PostgresNetworkDAO ()) {
			@SuppressWarnings("resource")
			Connection db = dao.getDBConnection();
			String sqlStr = "select \"UUID\" from network n where n.iscomplete and n.is_deleted=false and n.is_validated and n.islocked=false and n.error is null and cx2_file_size is not null and nodecount >= "
					+ SingleNetworkSolrIdxManager.AUTOCREATE_THRESHHOLD ;

			int i = 0;
			int j = 0;
			try (PreparedStatement pst = db.prepareStatement(sqlStr)) {
				try ( ResultSet rs = pst.executeQuery()) {
					while (rs.next()) {
						//int nodeCount = rs.getInt(2);
						UUID netid = (UUID)rs.getObject(1);
						try {
							rebuildLocalNetworkIndex(netid, true);
							j++;
						} catch (HttpSolrClient.RemoteSolrException e4) {
							if ( e4.getMessage().indexOf("Core with name '"+
									netid.toString() +  "' already exists") == -1) {
								e4.printStackTrace();
								throw new NdexException("Unexpected Solr Exception: " + e4.getMessage());
							}
							logger.info("index exists. Ignore creating it.");
						}
						i ++;
						if ( i % 500 == 0 ) {
							System.err.println("Loaded " + i + " records to solr. sleep 2 seconds");
							//   globalIdx.commit();
							try {
								Thread.sleep(2000);
							} catch (InterruptedException e) {
								// TODO Auto-generated catch block
								e.printStackTrace();
							}
						}
					}
				}
			}
			logger.info("Local index of " + i + " networks have been checked. " + j + " are created." );
		}
		//	globalIdx.commit();
	}


	private  void rebuildAllNetworksOnline() throws SQLException, JsonParseException, JsonMappingException, IOException, NdexException, SolrServerException {
		try (PostgresNetworkDAO dao = new PostgresNetworkDAO ()) {
			@SuppressWarnings("resource")
			Connection db = dao.getDBConnection();
			String sqlStr = "select \"UUID\" from network n where n.iscomplete and n.is_deleted=false and n.is_validated and n.error is null";

			int i = 0;
			try (PreparedStatement pst = db.prepareStatement(sqlStr)) {
				try ( ResultSet rs = pst.executeQuery()) {
					while (rs.next()) {
						//int nodeCount = rs.getInt(2);
						UUID networkId = (UUID)rs.getObject(1);
						try {

							rebuildNetworkIndex(networkId, false);
						} catch(Exception ex){
							logger.error("Failed to rebuild index for network: " + networkId.toString());
						}
						i ++;
						if ( i % 500 == 0 ) {
							System.err.println("Loaded " + i + " records to solr. sleep 2 seconds");
							//   globalIdx.commit();
							try {
								Thread.sleep(2000);
							} catch (InterruptedException e) {
								// TODO Auto-generated catch block
								e.printStackTrace();
							}
						}
					}
				}
			}
		}
		//	globalIdx.commit();
		logger.info("Indexes of all networks have been rebuilt.");
	}


	private  void rebuildSingleNetworkIndex (UUID networkid) throws SQLException, JsonParseException, JsonMappingException, IOException, NdexException, SolrServerException {
		try (PostgresNetworkDAO dao = new PostgresNetworkDAO()) {
			logger.info("Rebuild solr index of network " + networkid);
			NetworkSummary summary = dao.getNetworkSummaryById(networkid);
			if (summary == null)
				throw new NdexException ("Network "+ networkid + " not found in the server." );

			rebuildNetworkIndex(networkid, false);
		}
	}

	private static void rebuildUserIndex() throws Exception {
		logger.info("Start rebuild user index.");
		try (UserIndexManager umgr = new UserIndexManager()) {
			umgr.createCoreIfNotExists();

			try (UserDAO dao = new UserDAO()) {
				@SuppressWarnings("resource")
				Connection db = dao.getDBConnection();
				String sqlStr = "select \"UUID\" from ndex_user n where n.is_deleted=false and n.is_verified=true";

				try (PreparedStatement pst = db.prepareStatement(sqlStr)) {
					try (ResultSet rs = pst.executeQuery()) {
						while (rs.next()) {
							UUID userId = (UUID) rs.getObject(1);
							try (UserDAO dao2 = new UserDAO()) {
								User user = dao2.getUserById(userId, true, false);
								if (user == null)
									throw new NdexException("User " + userId
											+ " can't be indexed because this account is not verified.");
								logger.info("Adding user " + user.getUserName() + " to index.");
								umgr.addUser(user.getExternalId().toString(), user.getUserName(), user.getFirstName(),
										user.getLastName(), user.getDisplayName(), user.getDescription());
								logger.info("User " + user.getUserName() + " added to index.");
							}

						}
					}
				}
			}
		}
		logger.info("User index has been rebuilt.");
	}

	private static void rebuildGroupIndex() throws Exception {
		logger.info("Start rebuild group index.");
		try (GroupIndexManager umgr = new GroupIndexManager()) {

			umgr.createCoreIfNotExists();
		/*	String coreName = GroupIndexManager.coreName;
			CoreAdminRequest.Create creator = new CoreAdminRequest.Create();
			creator.setCoreName(coreName);
			creator.setConfigSet(coreName);
			CoreAdminResponse foo = creator.process(umgr.client);

			if (foo.getStatus() != 0) {
				throw new NdexException("Failed to create solrIndex for " + coreName + ". Error: "
						+ foo.getResponseHeader().toString());
			}
			logger.info("Solr core " + coreName + " created.");*/


			try (GroupDAO dao = new GroupDAO()) {
				@SuppressWarnings("resource")
				Connection db = dao.getDBConnection();
				String sqlStr = "select \"UUID\" from ndex_group n where n.is_deleted=false";

				try (PreparedStatement pst = db.prepareStatement(sqlStr)) {
					try (ResultSet rs = pst.executeQuery()) {
						while (rs.next()) {
							UUID groupId = (UUID) rs.getObject(1);
							try (GroupDAO dao2 = new GroupDAO()) {
								Group group = dao2.getGroupById(groupId);
								if (group == null)
									throw new NdexException("Group " + groupId
											+ " can't be indexed because this account is not verified.");
								logger.info("Adding Group " + group.getGroupName() + " to index.");
								umgr.addGroup(group.getExternalId().toString(), group.getGroupName(),
										group.getDescription());

								logger.info("Group " + group.getGroupName() + " added to index.");
							}

						}
					}
				}
			}

			logger.info("Group index has been rebuilt.");
		}
	}
	/** Executes the Solr delete+rebuild tasks for one network during unlist. */
	@FunctionalInterface
	interface SolrProcessor {
		void process(UUID networkId, UUID ownerId, String ownerName) throws Exception;
	}

	/**
	 * Find all networks where visibility='PUBLIC' and solr_idx_lvl='NONE',
	 * flip them to UNLISTED in Postgres, then synchronously run Solr delete+rebuild
	 * tasks so the file index is updated in public-nfs (both PUBLIC and UNLISTED
	 * networks use the public-nfs core; visibility controls query-time filtering).
	 *
	 * Per-network: (1) acquire lock, UPDATE visibility, commit, release lock;
	 * (2) run Solr tasks with no lock held. On any failure, attempts a compensating
	 * UPDATE back to PUBLIC (also under lock) then rethrows immediately — already-
	 * processed networks remain UNLISTED.
	 */
	private static void unlistPublicNoneNetworks() throws Exception {
		try (PostgresNetworkDAO dao = new PostgresNetworkDAO()) {
			unlistPublicNoneNetworks(dao, (networkId, ownerId, ownerName) -> {
				// ignoreCxFiles=true: these networks have solr_idx_lvl=NONE
				// and may not have CX aspect files on disk to index from.
				new SolrTaskDeleteFile(networkId, VisibilityType.PUBLIC).run();
				new SolrTaskRebuildFileIdx(networkId, ownerId, ownerName,
						VisibilityType.UNLISTED, FileType.NETWORK, false, true).run();
			});
		}
	}

	static void unlistPublicNoneNetworks(PostgresNetworkDAO dao, SolrProcessor solrProcessor) throws Exception {
		logger.info("Finding PUBLIC networks with solr_idx_lvl='NONE'...");

		@SuppressWarnings("resource")
		Connection db = dao.getDBConnection();
		db.setAutoCommit(false);

		int total = 0;
		int processed = 0;

		// Collect targets up front so the ResultSet isn't held open across writes/commits.
		List<Object[]> targets = new java.util.ArrayList<>();
		String selectSql = "SELECT \"UUID\", owneruuid, \"owner\" FROM network "
				+ "WHERE visibility = 'PUBLIC' AND solr_idx_lvl = 'NONE' AND is_deleted = false";
		try (PreparedStatement pst = db.prepareStatement(selectSql);
			 ResultSet rs = pst.executeQuery()) {
			while (rs.next()) {
				targets.add(new Object[] {
						(UUID) rs.getObject(1),
						(UUID) rs.getObject(2),
						rs.getString(3)
				});
			}
		}
		total = targets.size();
		logger.info("Found {} networks to convert from PUBLIC to UNLISTED.", total);

		String updateSql = "UPDATE network SET visibility = 'UNLISTED' WHERE \"UUID\" = ?";
		String revertSql = "UPDATE network SET visibility = 'PUBLIC'   WHERE \"UUID\" = ?";
		try (PreparedStatement updatePst = db.prepareStatement(updateSql);
			 PreparedStatement revertPst = db.prepareStatement(revertSql)) {
			for (Object[] row : targets) {
				UUID networkId = (UUID) row[0];
				UUID ownerId   = (UUID) row[1];
				String ownerName = (String) row[2];

				// Phase 1: DB update under lock.
				boolean locked = false;
				try {
					dao.lockNetwork(networkId);
					locked = true;
					updatePst.setObject(1, networkId);
					int rows = updatePst.executeUpdate();
					if (rows != 1) {
						throw new NdexException("Expected 1 row updated for " + networkId
								+ " but got " + rows);
					}
					db.commit();
				} finally {
					if (locked) {
						try { dao.unlockNetwork(networkId); }
						catch (SQLException unlockEx) {
							logger.error("CRITICAL: Network {} is stuck locked — manual intervention required: {}",
									networkId, unlockEx.getMessage(), unlockEx);
						}
					}
				}

				// Phase 2: Solr work — no lock held.
				try {
					solrProcessor.process(networkId, ownerId, ownerName);
					processed++;
					if (processed % 500 == 0) {
						logger.info("Processed {}/{} ({}%)",
								processed, total, (processed * 100) / total);
					}
				} catch (Exception e) {
					logger.error("Failed Solr tasks for network {}: {}", networkId, e.getMessage(), e);
					// Visibility was committed; compensate back to PUBLIC under a fresh lock.
					try {
						boolean revertLocked = false;
						try {
							dao.lockNetwork(networkId);
							revertLocked = true;
							revertPst.setObject(1, networkId);
							revertPst.executeUpdate();
							db.commit();
							logger.info("Reverted visibility to PUBLIC for network {}", networkId);
						} finally {
							if (revertLocked) {
								try { dao.unlockNetwork(networkId); }
								catch (SQLException unlockEx) {
									logger.error("CRITICAL: Network {} is stuck locked — manual intervention required: {}",
											networkId, unlockEx.getMessage(), unlockEx);
								}
							}
						}
					} catch (Exception revertEx) {
						logger.error("CRITICAL: Failed to revert visibility to PUBLIC for network {}: {}",
								networkId, revertEx.getMessage(), revertEx);
					}
					throw e;
				}
			}
		}
		logger.info("Done. processed={}, total={}", processed, total);
	}

	private static void rebuildNFSIdx(){

	}

	public static void main(String[] args) throws Exception {
		//	SolrIndexBuilder i = new SolrIndexBuider();
		Configuration configuration = Configuration.createInstance();

		NdexDatabase.createNdexDatabase(configuration.getDBURL(), configuration.getDBUser(),
				configuration.getDBPasswd(), 10);

		try (SolrIndexBuilder builder = new SolrIndexBuilder()) {
			if ( args.length == 1) {
				switch ( args[0]) {
					case "all":
						SolrIndexBuilder.rebuildUserIndex();
						SolrIndexBuilder.rebuildGroupIndex();
						builder.rebuildAll();
						break;
					case "user":
						SolrIndexBuilder.rebuildUserIndex();
						break;
					case "group":
						SolrIndexBuilder.rebuildGroupIndex();
						break;
					case "all-networks-online":
						builder.rebuildAllNetworksOnline();
						break;
					case "global-networks":
						builder.rebuildGlobalIdx();
						break;
					case "all-local":
						SolrIndexBuilder.rebuildAllLocalIdx();
						break;
					case "nfs":
						SolrIndexBuilder.rebuildNFSIdx();
						break;
					case "unlist-public-none":
						SolrIndexBuilder.unlistPublicNoneNetworks();
						break;
					default:
						builder.rebuildSingleNetworkIndex(UUID.fromString(args[0]));
						builder.globalIdx.commit();

				}
				logger.info("Index rebuild process finished.");
			} else {
				System.err.println("Supported argument: all/nfs/user/group/all-networks-online/global-networks/all-local/unlist-public-none/<networkUUID>");
				//System.out.println("For the boolean argument after network ID, true means rebuild the Single Network index.");
			}

		}

	}


	@Override
	public void close() throws Exception {
		this.globalIdx.close();
	}

}