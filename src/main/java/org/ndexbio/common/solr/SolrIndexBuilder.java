package org.ndexbio.common.solr;

import java.io.IOException;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.apache.solr.client.solrj.SolrServerException;
import org.apache.solr.client.solrj.impl.HttpSolrClient;
import org.apache.solr.client.solrj.request.CoreAdminRequest;
import org.apache.solr.client.solrj.response.CoreAdminResponse;
import org.ndexbio.common.access.NdexDatabase;
import org.ndexbio.server.migration.v3.NFSReIndexer;
import org.ndexbio.cxio.core.AspectIterator;
import org.ndexbio.common.models.dao.postgresql.PostgresNetworkDAO;
import org.ndexbio.common.models.dao.postgresql.UserDAO;
import org.ndexbio.cxio.aspects.datamodels.NetworkAttributesElement;
import org.ndexbio.cxio.aspects.datamodels.NodeAttributesElement;
import org.ndexbio.cxio.aspects.datamodels.NodesElement;
import org.ndexbio.model.cx.FunctionTermElement;
import org.ndexbio.model.exceptions.NdexException;
import org.ndexbio.model.object.FileType;
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

	/**
	 * Test constructor. The public constructor reaches Solr while building its global index
	 * manager, so tests supply one instead of having it created for them.
	 */
	SolrIndexBuilder (NetworkGlobalIndexManager globalIdx) {
		this.globalIdx = globalIdx;
	}
	
	
	void rebuildNetworkIndex (UUID networkid, boolean ignoreDeletion ) throws SQLException, JsonParseException, JsonMappingException, IOException, NdexException, SolrServerException {
		try (PostgresNetworkDAO dao = new PostgresNetworkDAO()) {
		  logger.info("Rebuild solr index of network " + networkid);
		  NetworkSummary summary = dao.getNetworkSummaryById(networkid);
		  if (summary == null)
			  throw new NdexException ("Network "+ networkid + " not found in the server." );
		  
		  dao.lockNetwork(networkid);
		  try (SingleNetworkSolrIdxManager idx2 = Configuration.getInstance().getSolrObjectFactory().getSingleNetworkSolrIdxManager(networkid.toString())) {
		  
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
					
					int committed = idx2.createIndex(null, summary.getNodeCount());
					idx2.close();

				    logger.info("Solr index for query created for network {} with {} documents.",
				    		networkid, committed);
			  } 
		  
			  if ( summary.getIndexLevel() != NetworkIndexLevel.NONE) {
				  // build the solr document obj
				  Map<Permissions,Collection<String>> userMemberships =  dao.getAllMembershipsOnNetwork(networkid);
				  globalIdx.createIndexDocFromSummary(summary,summary.getOwner(),
					userMemberships.get(Permissions.READ),
					userMemberships.get(Permissions.WRITE));

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
				  Map<Permissions,Collection<String>> userMemberships =  dao.getAllMembershipsOnNetwork(networkid);
				  globalIdx.createIndexDocFromSummary(summary,summary.getOwner(),
					userMemberships.get(Permissions.READ),
					userMemberships.get(Permissions.WRITE));

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
	
	
	void rebuildLocalNetworkIndex (UUID networkid, boolean ignoreDeletion ) throws SQLException, JsonParseException, JsonMappingException, IOException, NdexException, SolrServerException {
		try (PostgresNetworkDAO dao = new PostgresNetworkDAO()) {
		  logger.info("Rebuild local index of " + networkid);
		  NetworkSummary summary = dao.getNetworkSummaryById(networkid);
		  if (summary == null)
			  throw new NdexException ("Network "+ networkid + " not found in the server." );
		  
		  try (SingleNetworkSolrIdxManager idx2 = Configuration.getInstance().getSolrObjectFactory().getSingleNetworkSolrIdxManager(networkid.toString())) {
		  
			  if (!ignoreDeletion) {
				try {
					idx2.dropIndex();
				} catch (IOException | SolrServerException | NdexException e) {
					e.printStackTrace();
					logger.warn("Warning: Failed to delete node Index for network " + networkid.toString());
				}
				
			  }		
			  if (summary.getNodeCount() >= SingleNetworkSolrIdxManager.AUTOCREATE_THRESHHOLD ) {
					
					int committed = idx2.createIndex(null, summary.getNodeCount());
					idx2.close();

				    logger.info("Solr index for query created for network {} with {} documents.",
				    		networkid, committed);
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
						rebuildNetworkIndexInGlobalIdx((UUID)rs.getObject(1), true);
					   i ++;
					   if ( i % 1000 == 0 ) {
						   System.err.println("Loaded " + i + " records to solr. sleep 2 seconds");
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
		logger.info("Indexes of all networks have been rebuilt.");
	}
	

	private  void rebuildAllLocalIdx() throws SQLException, JsonParseException, JsonMappingException, IOException, NdexException, SolrServerException {
		try (PostgresNetworkDAO dao = new PostgresNetworkDAO ()) {
			@SuppressWarnings("resource")
			Connection db = dao.getDBConnection();
			String sqlStr = "select \"UUID\" from network n where n.iscomplete and n.is_deleted=false and n.is_validated and n.islocked=false and n.error is null and cx2_file_size is not null and nodecount >= " 
					+ SingleNetworkSolrIdxManager.AUTOCREATE_THRESHHOLD ;
			
			int i = 0;
			int j = 0;
			List<String> failedNetworks = new ArrayList<>();
			try (PreparedStatement pst = db.prepareStatement(sqlStr)) {
				try ( ResultSet rs = pst.executeQuery()) {
					while (rs.next()) {
						UUID netid = (UUID)rs.getObject(1);
						if (runLocalIndexStep(netid, failedNetworks)) {
							j++;
						}
					   i ++;
					   if ( i % 500 == 0 ) {
						   System.err.println("Loaded " + i + " records to solr. sleep 2 seconds");
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
			logSweepSummary("all-local", j, failedNetworks);
		}
	}

	
	private  void rebuildAllNetworksOnline() throws SQLException, JsonParseException, JsonMappingException, IOException, NdexException, SolrServerException {
		try (PostgresNetworkDAO dao = new PostgresNetworkDAO ()) {
			@SuppressWarnings("resource")
			Connection db = dao.getDBConnection();
			String sqlStr = "select \"UUID\" from network n where n.iscomplete and n.is_deleted=false and n.is_validated and n.error is null";
			
			int i = 0;
			int succeeded = 0;
			List<String> failedNetworks = new ArrayList<>();
			try (PreparedStatement pst = db.prepareStatement(sqlStr)) {
				try ( ResultSet rs = pst.executeQuery()) {
					while (rs.next()) {
					   UUID networkId = (UUID)rs.getObject(1);
					   if (runNetworkIndexStep(networkId, failedNetworks)) {
						   succeeded++;
					   }
					   i ++;
					   if ( i % 500 == 0 ) {
						   System.err.println("Loaded " + i + " records to solr. sleep 2 seconds");
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
			logSweepSummary("all-networks-online", succeeded, failedNetworks);
		}
		logger.info("Indexes of all networks have been rebuilt.");
	}

	/**
	 * Rebuilds one network's index as part of a sweep.
	 *
	 * <p>A failure here concerns only this network: it is logged once at WARN with the reason
	 * carried by the exception, recorded in {@code failedNetworks}, and never rethrown, so the
	 * caller's loop continues to the next network.
	 *
	 * @return true when the network was indexed
	 */
	boolean runNetworkIndexStep(UUID networkId, List<String> failedNetworks) {
		try {
			rebuildNetworkIndex(networkId, false);
			return true;
		} catch (Exception ex) {
			failedNetworks.add(networkId.toString());
			logger.warn("Failed to rebuild index for network {}: {}", networkId, ex.getMessage());
			return false;
		}
	}

	/**
	 * Creates one network's local query index as part of a sweep.
	 *
	 * <p>A core that already exists is not a failure - this command only fills in missing ones.
	 * Anything else is this network's problem alone: logged once at WARN and never rethrown.
	 * This previously rethrew, which aborted the whole sweep on the first bad network.
	 *
	 * @return true when an index was created
	 */
	boolean runLocalIndexStep(UUID networkId, List<String> failedNetworks) {
		try {
			rebuildLocalNetworkIndex(networkId, true);
			return true;
		} catch (HttpSolrClient.RemoteSolrException e4) {
			if (e4.getMessage() != null && e4.getMessage().indexOf("Core with name '"
					+ networkId.toString() + "' already exists") != -1) {
				logger.info("index exists. Ignore creating it.");
				return false;
			}
			failedNetworks.add(networkId.toString());
			logger.warn("Failed to create local index for network {}: {}",
					networkId, e4.getMessage());
			return false;
		} catch (Exception e) {
			failedNetworks.add(networkId.toString());
			logger.warn("Failed to create local index for network {}: {}",
					networkId, e.getMessage());
			return false;
		}
	}

	/**
	 * Reports the outcome of a sweep so a run that quietly failed on every network is visible
	 * without grepping the whole log. Never changes the process exit status - the loops are
	 * designed to always run to completion.
	 */
	private static void logSweepSummary(String command, int succeeded, List<String> failed) {
		if (failed.isEmpty()) {
			logger.info("{}: {} networks indexed, 0 failed.", command, succeeded);
			return;
		}
		logger.warn("{}: {} networks indexed, {} failed. Failed network ids: {}",
				command, succeeded, failed.size(), String.join(", ", failed));
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
	
	/**
	 * Offline equivalent of {@code GET /v3/admin/reindex-v3}: clears {@code public-nfs} and
	 * {@code private-nfs}, then rebuilds every folder, shortcut and network document from PostgreSQL.
	 *
	 * <p>Preferred over the endpoint on a large instance, because the endpoint runs the whole rebuild
	 * synchronously inside a single HTTP request.</p>
	 *
	 * <p><b>{@code all-networks-online} is not a substitute.</b> It rebuilds network documents only, so
	 * folder and shortcut documents would keep whatever they were last written with — a half-migrated
	 * index, and a silent one.</p>
	 *
	 * <p>Both cores are emptied before the rebuild starts, so search returns nothing until it finishes.
	 * Run it in a maintenance window.</p>
	 */
	private static void rebuildNFSIdx() throws Exception {
		logger.info("Clearing public-nfs and private-nfs, then rebuilding folders, shortcuts and networks.");
		// try-with-resources: NFSReIndexer holds Solr clients whose non-daemon threads would otherwise
		// keep this CLI process alive after the rebuild finishes.
		try (NFSReIndexer reIndexer = new NFSReIndexer()) {
			reIndexer.run();
		}
		logger.info("NFS indexes have been rebuilt.");
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
				// ignoreCxFiles=true: skips CX2 aspect reads (these networks may have no CX2
				// files on disk). The CX1 AspectIterator fallback still runs but returns nothing
				// for NONE-indexed networks, so only metadata is indexed.
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
						try { db.rollback(); } catch (SQLException rbe) { /* best-effort: clear aborted txn before unlock */ }
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
							int revertRows = revertPst.executeUpdate();
							if (revertRows != 1) {
								throw new NdexException("Expected 1 row reverted for " + networkId + " but got " + revertRows);
							}
							db.commit();
							logger.info("Reverted visibility to PUBLIC for network {}", networkId);
						} finally {
							if (revertLocked) {
								try { db.rollback(); } catch (SQLException rbe) { /* best-effort: clear aborted txn before unlock */ }
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
				builder.rebuildAll();
				break;
			case "user":
				SolrIndexBuilder.rebuildUserIndex();
				break;
			case "all-networks-online":
				builder.rebuildAllNetworksOnline();
				break;
			case "global-networks":
				builder.rebuildGlobalIdx();
				break;
			case "all-local":
				builder.rebuildAllLocalIdx();
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
