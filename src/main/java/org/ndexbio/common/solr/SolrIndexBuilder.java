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
import org.apache.solr.client.solrj.request.CoreAdminRequest;
import org.apache.solr.client.solrj.response.CoreAdminResponse;
import org.cxio.aspects.datamodels.NetworkAttributesElement;
import org.cxio.aspects.datamodels.NodeAttributesElement;
import org.cxio.aspects.datamodels.NodesElement;
import org.ndexbio.common.access.NdexDatabase;
import org.ndexbio.common.cx.AspectIterator;
import org.ndexbio.common.models.dao.postgresql.GroupDAO;
import org.ndexbio.common.models.dao.postgresql.NetworkDAO;
import org.ndexbio.common.models.dao.postgresql.UserDAO;
import org.ndexbio.model.cx.FunctionTermElement;
import org.ndexbio.model.exceptions.NdexException;
import org.ndexbio.model.object.Group;
import org.ndexbio.model.object.Permissions;
import org.ndexbio.model.object.User;
import org.ndexbio.model.object.network.NetworkIndexLevel;
import org.ndexbio.model.object.network.NetworkSummary;
import org.ndexbio.rest.Configuration;
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
	
	
	private  void rebuildNetworkIndex (UUID networkid, NetworkIndexLevel lvl ) throws SQLException, JsonParseException, JsonMappingException, IOException, NdexException, SolrServerException {
		try (NetworkDAO dao = new NetworkDAO()) {
		  logger.info("Rebuild solr index of network " + networkid);
		  NetworkSummary summary = dao.getNetworkSummaryById(networkid);
		  if (summary == null)
			  throw new NdexException ("Network "+ networkid + " not found in the server." );
		  try (SingleNetworkSolrIdxManager idx2 = new SingleNetworkSolrIdxManager(networkid.toString())) {

			  //drop the old ones.
			  globalIdx.deleteNetwork(networkid.toString());
  
			  try {
					idx2.dropIndex();
			  } catch (IOException | SolrServerException | NdexException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
					logger.warn("Warning: Failed to delete node Index for network " + networkid.toString());
			  }		
			
			  logger.info("Existing indexes deleted.");
		  	
			  idx2.createIndex(null);
			  idx2.close();
		  }
		
		  logger.info("Solr index for query created.");
		  
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
			
		  try (AspectIterator<NetworkAttributesElement> it = new AspectIterator<>(networkid, NetworkAttributesElement.ASPECT_NAME, NetworkAttributesElement.class)) {
				while (it.hasNext()) {
					NetworkAttributesElement e = it.next();
					
					List<String> indexWarnings = globalIdx.addCXNetworkAttrToIndex(e);	
					if ( !indexWarnings.isEmpty())
						for (String warning : indexWarnings) 
							System.err.println("Warning: " + warning);
					
				}
		    }

		  
		if (lvl == NetworkIndexLevel.ALL) {	
			try (AspectIterator<FunctionTermElement> it = new AspectIterator<>(networkid, FunctionTermElement.ASPECT_NAME, FunctionTermElement.class)) {
				while (it.hasNext()) {
					FunctionTermElement fun = it.next();
					
					globalIdx.addFunctionTermToIndex(fun);

				}
			}

			try (AspectIterator<NodeAttributesElement> it = new AspectIterator<>(networkid, NodeAttributesElement.ASPECT_NAME, NodeAttributesElement.class)) {
				while (it.hasNext()) {
					NodeAttributesElement e = it.next();					
					globalIdx.addCXNodeAttrToIndex(e);	
				}
			}

			try (AspectIterator<NodesElement> it = new AspectIterator<>(networkid, NodesElement.ASPECT_NAME, NodesElement.class)) {
				while (it.hasNext()) {
					NodesElement e = it.next();					
					globalIdx.addCXNodeToIndex(e);	
				}
			}
						
		}	
			globalIdx.commit();
			
			logger.info("Solr index of network " + networkid + " created.");
		} 
	}
	
	private  void rebuildAll() throws SQLException, JsonParseException, JsonMappingException, IOException, NdexException, SolrServerException {
		try (NetworkDAO dao = new NetworkDAO ()) {
			@SuppressWarnings("resource")
			Connection db = dao.getDBConnection();
			String sqlStr = "select \"UUID\", solr_idx_lvl from network n where n.iscomplete and n.is_deleted=false and n.is_validated and n.islocked=false and solr_idx_lvl <> 'NONE'";
			
			int i = 0;
			try (PreparedStatement pst = db.prepareStatement(sqlStr)) {
				try ( ResultSet rs = pst.executeQuery()) {
					while (rs.next()) {
					   rebuildNetworkIndex((UUID)rs.getObject(1), NetworkIndexLevel.valueOf(rs.getString(2)));
					   i ++;
					   if ( i % 100 == 0 ) {
						   System.err.println("Loaded " + i + " records to solr. sleep 10 seconds");
						//   globalIdx.commit();
						   try {
							  Thread.sleep(10000);
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
	
	private static void rebuildUserIndex() throws Exception {
		logger.info("Start rebuild user index.");
		try(UserIndexManager umgr = new UserIndexManager()) {
		String coreName = UserIndexManager.coreName; 
		CoreAdminRequest.Create creator = new CoreAdminRequest.Create(); 
		creator.setCoreName(coreName);
		creator.setConfigSet( coreName); 
		CoreAdminResponse foo = creator.process(umgr.client);		
		
		if ( foo.getStatus() != 0 ) {
			throw new NdexException ("Failed to create solrIndex for " + coreName + ". Error: " + foo.getResponseHeader().toString());
		}
		logger.info("Solr core " + coreName + " created.");		

		try (UserDAO dao = new UserDAO ()) {
			@SuppressWarnings("resource")
			Connection db = dao.getDBConnection();
			String sqlStr = "select \"UUID\" from ndex_user n where n.is_deleted=false and n.is_verified=true";
			
			try (PreparedStatement pst = db.prepareStatement(sqlStr)) {
				try ( ResultSet rs = pst.executeQuery()) {
					while (rs.next()) {
					    UUID userId = (UUID)rs.getObject(1);
					    try (UserDAO dao2 = new UserDAO()) {
					    	User user = dao2.getUserById(userId, true, false);
					    	if (user == null)
					    		throw new NdexException ("User " + userId+ " can't be indexed because this account is not verified.");
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
			String coreName = GroupIndexManager.coreName;
			CoreAdminRequest.Create creator = new CoreAdminRequest.Create();
			creator.setCoreName(coreName);
			creator.setConfigSet(coreName);
			CoreAdminResponse foo = creator.process(umgr.client);

			if (foo.getStatus() != 0) {
				throw new NdexException("Failed to create solrIndex for " + coreName + ". Error: "
						+ foo.getResponseHeader().toString());
			}
			logger.info("Solr core " + coreName + " created.");

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
	
	public static void main(String[] args) throws Exception {
	//	SolrIndexBuilder i = new SolrIndexBuider();
		Configuration configuration = Configuration.createInstance();

		NdexDatabase.createNdexDatabase(configuration.getDBURL(), configuration.getDBUser(),
				configuration.getDBPasswd(), 10);
	
		try (SolrIndexBuilder builder = new SolrIndexBuilder()) {
		if ( args.length == 1) {
			switch ( args[0]) {
			case "all":
				builder.rebuildAll();
				SolrIndexBuilder.rebuildUserIndex();
				SolrIndexBuilder.rebuildGroupIndex();
				break;
			case "user":
				SolrIndexBuilder.rebuildUserIndex();
				break;
			case "group":
				SolrIndexBuilder.rebuildGroupIndex();
				break;
			default:	
				builder.rebuildNetworkIndex(UUID.fromString(args[0]), NetworkIndexLevel.valueOf(args[1]));
				builder.globalIdx.commit();
				
			}
			logger.info("Index rebuid process finished.");
		} else {
			System.out.println("Supported argument: all/user/group/networkid");
		}
		
		}
		
	}


	@Override
	public void close() throws Exception {
		this.globalIdx.close();
	}

}
