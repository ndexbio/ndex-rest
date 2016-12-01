package org.ndexbio.server.migration.v2;

import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;
import java.util.Collection;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;
import java.util.logging.Logger;

import org.apache.solr.client.solrj.SolrServerException;
import org.ndexbio.common.access.NdexDatabase;
import org.ndexbio.common.models.dao.postgresql.GroupDAO;
import org.ndexbio.common.models.dao.postgresql.NetworkDAO;
import org.ndexbio.common.models.dao.postgresql.UserDAO;
import org.ndexbio.common.persistence.CXNetworkLoader;
import org.ndexbio.common.solr.GroupIndexManager;
import org.ndexbio.common.solr.NetworkGlobalIndexManager;
import org.ndexbio.common.solr.UserIndexManager;
import org.ndexbio.model.exceptions.NdexException;
import org.ndexbio.model.exceptions.ObjectNotFoundException;
import org.ndexbio.model.object.Group;
import org.ndexbio.model.object.User;
import org.ndexbio.rest.Configuration;

import com.fasterxml.jackson.core.JsonParseException;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.ObjectMapper;

public class V13DbImporter implements AutoCloseable {

	static final Logger logger = Logger.getLogger(V13DbImporter.class.getName());

	private NdexDatabase ndexDB;

	private Connection db;

	private String importFilesPrefix;

	private TypeReference<Map<String,Object>> typeRef;
	private 			ObjectMapper mapper ;

	
	public V13DbImporter(String srcFilesPath) throws NdexException, SolrServerException, IOException, SQLException {
		Configuration configuration = Configuration.createInstance();

		// create solr core for network indexes if needed.
		NetworkGlobalIndexManager mgr = new NetworkGlobalIndexManager();
		mgr.createCoreIfNotExists();
		UserIndexManager umgr = new UserIndexManager();
		umgr.createCoreIfNotExists();
		GroupIndexManager gmgr = new GroupIndexManager();
		gmgr.createCoreIfNotExists();

		// and initialize the db connections

		ndexDB = NdexDatabase.createNdexDatabase(configuration.getDBURL(), configuration.getDBUser(),
				configuration.getDBPasswd(), 10);

		this.db = ndexDB.getConnection();

		this.importFilesPrefix = srcFilesPath;
		typeRef = new TypeReference<Map<String,Object>>() {   };
		mapper = new ObjectMapper();


	}

	@Override
	public void close() throws Exception {
		db.close();
		NdexDatabase.close();

	}

	private void migrateDBAndNetworks ( ) throws FileNotFoundException, IOException, SQLException, NdexException, IllegalArgumentException, SolrServerException {

			importUsers();
			importGroups();
			importTasks();
			importRequests();
			importNetworks();
			
			try (Statement stmt = db.createStatement() ) {
				stmt.executeUpdate("truncate table working_migrated_uuids");
			} 
			
			populatingUserTable();
			populatingGroupTable();   
			populatingNetworkTable();
			populateTaskNRequestTable();
						
	}
	
	
	
	private void populatingUserTable() throws SQLException, JsonParseException, JsonMappingException,
			IllegalArgumentException, ObjectNotFoundException, NdexException, IOException, SolrServerException {
		String sql = "insert into ndex_user (\"UUID\",creation_time,modification_time,user_name,first_name,last_name, image_url,website_url,email_addr, " +
							 "password,is_individual,description,is_deleted,is_verified, is_from_13) " + 
							 "select id, creation_time, modification_time, account_name, first_name,last_name,image_url,website_url,email,password,true,description,"+
							 "false,true,true from v1_user on conflict on constraint user_pk do nothing";
			
		
		try (Statement stmt = db.createStatement() ) {
				int cnt = stmt.executeUpdate(
								"insert into working_migrated_uuids select 'user', id "
								+ "from v1_user u1 where not exists (select 1 from ndex_user u where u.\"UUID\" = u1.id)");
				logger.info(cnt + " new users will be imported.");
				
		}
			
		try (PreparedStatement pstUser = db.prepareStatement(sql)) {
				pstUser.executeUpdate();
		}
		
		db.commit();
		
		String sql1 = "select id from working_migrated_uuids where table_name = 'user'";
		try (PreparedStatement pst = db.prepareStatement(sql1)) {
				try (ResultSet rs = pst.executeQuery() ) {
					while (rs.next()) {
						UUID userId = (UUID)rs.getObject(1);
						try (UserDAO dao = new UserDAO()) {
							User user = dao.getUserById(userId, true);
							logger.info("adding user " + user.getUserName() + " to solr index.");
							UserIndexManager mgr = new UserIndexManager();
							mgr.addUser(user.getExternalId().toString(), user.getUserName(), user.getFirstName(), user.getLastName(), user.getDisplayName(), user.getDescription());
						}
					}
				}
		}
		
		db.commit();
		
	}

	
	private void populateTaskNRequestTable() throws SQLException, IllegalArgumentException {
		
		String sql = "insert into task (\"UUID\", creation_time,modification_time, status, start_time,end_time, " + 
					"task_type, owneruuid, is_deleted, other_attributes, description,priority, progress, file_format, message, resource)" + 
					" select id,creation_time,modification_time, status, start_time, end_time, "+
					" task_type,owneruuid,false,attributes,description,'LOW', 0,format, null,resource "+
					" from v1_task t on conflict on constraint task_pk do nothing";
	
		try (PreparedStatement pstUser = db.prepareStatement(sql)) {
			pstUser.executeUpdate();	
		}

		sql = "insert into request (\"UUID\", creation_time,modification_time, is_deleted, sourceuuid,destinationuuid,requestmessage, " +
			  "	response, responsemessage, requestpermission,responsetime, other_attributes, responder, owner_id, request_type) "+
			  " select id, creation_time, modification_time, false, source_uuid, destination_uuid, message, response, response_message, "+
			  " request_permission, response_time, null, responder,  (select id from v1_user where rid = in_request), "+ 
              " case when request_permission = 'GROUPADMIN' or request_permission = 'MEMBER' then 'JoinGroup' "+
              " else case when (select id from v1_user where rid = in_request) = source_uuid then 'UserNetworkAccess' "+
              " else 'GroupNetworkAccess'  "+
              " end "+
              " end "+
              " from v1_request on conflict on constraint request_pk do nothing";
		
		
			try (PreparedStatement pstUser = db.prepareStatement(sql)) {
				pstUser.executeUpdate();	
			}

		db.commit();


}
	
	private void populatingGroupTable() throws SQLException, JsonParseException, JsonMappingException,
		IllegalArgumentException, ObjectNotFoundException, NdexException, IOException, SolrServerException {
		
		String sql = "insert into ndex_group (\"UUID\",creation_time,modification_time,group_name,image_url,description,is_deleted,website_url,is_from_13) " +
				 "select id, creation_time, modification_time, group_name,image_url,description, false,website_url, true"+
				 " from v1_group on conflict on constraint group_pk do nothing";
	

		try (Statement stmt = db.createStatement() ) {
			int cnt = stmt.executeUpdate(
						"insert into working_migrated_uuids select 'group', id "
						+ "from v1_group u1 where not exists (select 1 from ndex_group u where u.\"UUID\" = u1.id)");
			logger.info(cnt + " new groups will be imported.");
		
		}
	
		try (PreparedStatement pstUser = db.prepareStatement(sql)) {
			pstUser.executeUpdate();
		}

		db.commit();
		
		String sql1 = "select id from working_migrated_uuids where table_name = 'group'";
		try (PreparedStatement pst = db.prepareStatement(sql1)) {
			try (ResultSet rs = pst.executeQuery() ) {
				while (rs.next()) {
					UUID groupId = (UUID)rs.getObject(1);
					try (GroupDAO dao = new GroupDAO()) {
						Group group = dao.getGroupById(groupId);
						logger.info("adding group " + group.getGroupName() + " to solr index.");
						GroupIndexManager m = new GroupIndexManager();
						m.addGroup(group.getExternalId().toString(), group.getGroupName(), group.getDescription());
						
					}
				}
			}
		}
		

		sql = "insert into ndex_group_user (group_id,user_id,is_admin)" +
			  "	select g.id,ug.user_id, ug.type='groupadmin' "+
			  "	from v1_user_group ug, v1_group g " + 
			  " 	where ug.group_rid = g.rid on conflict on constraint \"ndexGroupUser_pkey\" do nothing";
		try (PreparedStatement pstUser = db.prepareStatement(sql)) {
			pstUser.executeUpdate();
		}

		db.commit();

	}	
	
	
	private void populatingNetworkTable() throws SQLException, JsonParseException, JsonMappingException,
			IllegalArgumentException, ObjectNotFoundException, NdexException, IOException, SolrServerException {
	
		String sql = "insert into network (\"UUID\",creation_time,modification_time,is_deleted,name,description,edgecount,nodecount,islocked,visibility,owneruuid,"+
			 "sourceformat,properties,provenance,version,readonly,is_from_13, show_in_homepage) " +
			 "select id, creation_time, modification_time, false,name,description, edge_count,node_count, false,visibility, "+
				"     (select user_id from v1_user_network un where type='admin' and un.network_rid = n.rid limit 1)," +
				"   source_format," + "case when source_format is null " + 
				 " then props :: jsonb " + 
				 " else " + 
			  	 " case when props is null " + 
			     " then ('[{\"subNetworkId\":null,\"value\":\"' || source_format || '\",\"dataType\":\"string\",\"predicateString\":\"ndex:sourceFormat\"}]') ::jsonb " + 
			     "  else ('[{\"subNetworkId\":null,\"value\":\"' || source_format || '\",\"dataType\":\"string\",\"predicateString\":\"ndex:sourceFormat\"}]') ::jsonb " + 
			     "       || props :: jsonb " + 
			   " end " + 
			   "  end " 
				+ 
				", provenance,version,readonly , true, visibility = 'PUBLIC' from v1_network n on conflict on constraint network_pk do nothing";


		try (Statement stmt = db.createStatement() ) {
			int cnt = stmt.executeUpdate(
					"insert into working_migrated_uuids select 'network', id "
					+ "from v1_network u1 where not exists (select 1 from network u where u.\"UUID\" = u1.id)");
			logger.info(cnt + " new networks will be imported.");
	
		}

		try (PreparedStatement pstUser = db.prepareStatement(sql)) {
			pstUser.executeUpdate();
		}

		sql = "update network n set owner= (select user_name from ndex_user u where u.\"UUID\" = n.owneruuid) where  n.owner is null"  ;
		try (PreparedStatement pstUser = db.prepareStatement(sql)) {
			pstUser.executeUpdate();
		}

		
		// populate permission tables.
		sql = "insert into user_network_membership ( user_id,network_id, permission_type) " + 
			" select s.* from  (select user_id, (select id from v1_network vn where vn.rid = un.network_rid), "+
			" upper(un.type) :: ndex_permission_type from v1_user_network un where un.type <> 'admin' ) s where s.id is not null " +
			"on conflict on constraint \"userNetworkMembership_pkey\" do nothing";
		try (PreparedStatement pstUser = db.prepareStatement(sql)) {
				pstUser.executeUpdate();
		}
		
		sql = "insert into group_network_membership (group_id,network_id,permission_type) "+
			" select s.* from (select group_id, (select id from v1_network vn where vn.rid = gn.network_rid), "+
		    "  upper(gn.type) :: ndex_permission_type from v1_group_network gn ) s where s.id is not null " + 
		    " on conflict on constraint \"groupNetworkMembership_pkey\" do nothing";
		try (PreparedStatement pstUser = db.prepareStatement(sql)) {
					pstUser.executeUpdate();
		}	

		db.commit();
		
		try (NetworkDAO dao = new NetworkDAO()) {
			String sql1 = "select id, (select owner from network n where n.\"UUID\" = id) from working_migrated_uuids where table_name = 'network'" + 
							" and exists ( select 1 from network n where n.\"UUID\"= id and n.iscomplete is null)" ;
			
			try (PreparedStatement pst = db.prepareStatement(sql1)) {
					try (ResultSet rs = pst.executeQuery() ) {
						while (rs.next()) {
							UUID uuid = (UUID)rs.getObject(1);
							String uuidStr = uuid.toString();
							logger.info("Loading network "+ uuidStr );
							String pathPrefix = Configuration.getInstance().getNdexRoot() + "/data/" + uuidStr;
							   
							//Create dir
							java.nio.file.Path dir = Paths.get(pathPrefix);
							Files.createDirectory(dir);
							
							java.nio.file.Path src = Paths.get(importFilesPrefix  + "/" +  uuidStr + ".cx");
							java.nio.file.Path tgt = Paths.get(Configuration.getInstance().getNdexRoot() + "/data/" + uuidStr + "/network.cx");
							Files.copy(src, tgt,StandardCopyOption.REPLACE_EXISTING);  
							
							try (CXNetworkLoader loader = new CXNetworkLoader(uuid,rs.getString(2), false,dao)) {
								loader.importNetwork();		
							}
							try {
								Thread.sleep(2000);
							} catch (InterruptedException e) {
								// TODO Auto-generated catch block
								e.printStackTrace();
							}
							
						}
					}
			}
			dao.commit();	
		}
		

		db.commit();

	}	
	
	private void importUsers() throws IOException, JsonProcessingException, SQLException, FileNotFoundException {
		//import users.
		try (FileInputStream i = new FileInputStream(importFilesPrefix+"/user.json")) {
			
			String sqlStr1 = "insert into v1_user (rid, id, creation_time, modification_time, account_name, password,description,email,first_name,last_name,"
					+ "image_url,website_url) values (?,?,?,?,?, ?,?,?,?,?, ?,?) on conflict (id) do nothing";
			String sqlStr2 = "insert into v1_user_group (user_id,group_rid,type) values (?,?,?) on conflict on constraint v1_user_group_pkey do nothing";
			String sqlStr3 = "insert into v1_user_network (user_id, network_rid,type) values (?,?,?) on conflict on constraint v1_user_network_pkey do nothing";

			try (PreparedStatement pstUser = db.prepareStatement(sqlStr1)) {
				try (PreparedStatement pstUserGroup = db.prepareStatement(sqlStr2)) {
					try (PreparedStatement pstUserNetwork = db.prepareStatement(sqlStr3)) {
						Iterator<Map<String,Object>> it = new ObjectMapper().readerFor(typeRef).readValues(i);
		        	
						while (it.hasNext()) {
							Map<String,Object> map = it.next();
							String accName = (String)map.get("accountName");
							logger.info("processing user " + accName );
							UUID userId = UUID.fromString((String) map.get("UUID"));
							String imageURL = (String)map.get("imageURL");
							

							// insert user rec
							pstUser.setString(1, (String)map.get("@rid"));
							pstUser.setObject(2, userId);
							pstUser.setTimestamp(3,Timestamp.valueOf((String)map.get("createdTime")));
							pstUser.setTimestamp(4,Timestamp.valueOf((String)map.get("modificationTime")));
							pstUser.setString(5, accName);
							pstUser.setString(6, (String)map.get("password"));
							pstUser.setString(7, (String)map.get("description"));
							pstUser.setString(8, (String)map.get("emailAddress"));
							pstUser.setString(9, (String)map.get("firstName"));
							pstUser.setString(10, (String)map.get("lastName"));
							if ( imageURL !=null ) {
								if ( imageURL.length() < 500 )
									pstUser.setString(11, imageURL);
								else  {
									pstUser.setString(11, null);
									logger.warning("image url length over limit, ignoring it.");
								}
							} else 
								pstUser.setString(11, null);

							pstUser.setString(12, (String)map.get("websiteURL"));
							pstUser.executeUpdate();
		    			
							// insert userGroup records
							Object o = map.get("out_groupadmin");
							if ( o!=null) {
								if (o instanceof String) {
									pstUserGroup.setObject(1, userId);
									pstUserGroup.setString(2, (String)o);
									pstUserGroup.setString(3, "groupadmin");
									pstUserGroup.executeUpdate();
								} else if ( o instanceof Collection<?>) {
									for (String oe : (Collection<String>)o) {
										pstUserGroup.setObject(1, userId);
										pstUserGroup.setString(2, (String)oe);
										pstUserGroup.setString(3, "groupadmin");
										pstUserGroup.executeUpdate();
									}
								}
									
							}
							
							o = map.get("out_member");
							if ( o!=null) {
								if (o instanceof String) {
									pstUserGroup.setObject(1, userId);
									pstUserGroup.setString(2, (String)o);
									pstUserGroup.setString(3, "member");
									pstUserGroup.executeUpdate();
								} else if ( o instanceof Collection<?>) {
									for (String oe : (Collection<String>)o) {
										pstUserGroup.setObject(1, userId);
										pstUserGroup.setString(2, (String)oe);
										pstUserGroup.setString(3, "member");
										pstUserGroup.executeUpdate();
									}
								}
									
							}
							
							// insert user network records
							
							o = map.get("out_admin");
							if ( o!=null) {
								if (o instanceof String) {
									pstUserNetwork.setObject(1, userId);
									pstUserNetwork.setString(2, (String)o);
									pstUserNetwork.setString(3, "admin");
									pstUserNetwork.executeUpdate();
								} else if ( o instanceof Collection<?>) {
									for (String oe : (Collection<String>)o) {
										pstUserNetwork.setObject(1, userId);
										pstUserNetwork.setString(2, (String)oe);
										pstUserNetwork.setString(3, "admin");
										pstUserNetwork.executeUpdate();
									}
								}
									
							}
		    			
							o = map.get("out_admin");
							if ( o!=null) {
								if (o instanceof String) {
									pstUserNetwork.setObject(1, userId);
									pstUserNetwork.setString(2, (String)o);
									pstUserNetwork.setString(3, "admin");
									pstUserNetwork.executeUpdate();
								} else if ( o instanceof Collection<?>) {
									for (String oe : (Collection<String>)o) {
										pstUserNetwork.setObject(1, userId);
										pstUserNetwork.setString(2, (String)oe);
										pstUserNetwork.setString(3, "admin");
										pstUserNetwork.executeUpdate();
									}
								}
									
							}
							
							o = map.get("out_write");
							if ( o!=null) {
								if (o instanceof String) {
									pstUserNetwork.setObject(1, userId);
									pstUserNetwork.setString(2, (String)o);
									pstUserNetwork.setString(3, "write");
									pstUserNetwork.executeUpdate();
								} else if ( o instanceof Collection<?>) {
									for (String oe : (Collection<String>)o) {
										pstUserNetwork.setObject(1, userId);
										pstUserNetwork.setString(2, (String)oe);
										pstUserNetwork.setString(3, "write");
										pstUserNetwork.executeUpdate();
									}
								}
									
							}
							
							o = map.get("out_read");
							if ( o!=null) {
								if (o instanceof String) {
									pstUserNetwork.setObject(1, userId);
									pstUserNetwork.setString(2, (String)o);
									pstUserNetwork.setString(3, "read");
									pstUserNetwork.executeUpdate();
								} else if ( o instanceof Collection<?>) {
									for (String oe : (Collection<String>)o) {
										pstUserNetwork.setObject(1, userId);
										pstUserNetwork.setString(2, (String)oe);
										pstUserNetwork.setString(3, "read");
										pstUserNetwork.executeUpdate();
									}
								}
									
							}
						}
					}
				}
			}
			
			db.commit();
			
		}
	}
	
	private void importGroups() throws IOException, JsonProcessingException, SQLException, FileNotFoundException {
		//import users.
		try (FileInputStream i = new FileInputStream(importFilesPrefix+"/group.json")) {
			
			String sqlStr1 = "insert into v1_group (rid, id, creation_time, modification_time, account_name, group_name,description,"
					+ "image_url,website_url) values (?,?,?,?,?, ?,?,?,?) on conflict (id) do nothing";
			String sqlStr2 = "insert into v1_group_network (group_id, network_rid,type) values (?,?,?) on conflict on constraint v1_group_network_pkey do nothing";

			try (PreparedStatement pstUser = db.prepareStatement(sqlStr1)) {
				try (PreparedStatement pstUserGroup = db.prepareStatement(sqlStr2)) {
					try (PreparedStatement pstUserNetwork = db.prepareStatement(sqlStr2)) {
						Iterator<Map<String,Object>> it = new ObjectMapper().readerFor(typeRef).readValues(i);
		        	
						while (it.hasNext()) {
							Map<String,Object> map = it.next();
							String accName = (String)map.get("accountName");
							logger.info("processing group " + accName );
							UUID groupId = UUID.fromString((String) map.get("UUID"));
							String imageURL = (String)map.get("imageURL");
							

							// insert user rec
							pstUser.setString(1, (String)map.get("@rid"));
							pstUser.setObject(2, groupId);
							pstUser.setTimestamp(3,Timestamp.valueOf((String)map.get("createdTime")));
							pstUser.setTimestamp(4,Timestamp.valueOf((String)map.get("modificationTime")));
							pstUser.setString(5, accName);
							pstUser.setString(6, (String)map.get("groupName"));
							pstUser.setString(7, (String)map.get("description"));

							if ( imageURL !=null ) {
								if ( imageURL.length() < 500 )
									pstUser.setString(8, imageURL);
								else  {
									pstUser.setString(8, null);
									logger.warning("image url length over limit, ignoring it.");
								}
							} else 
								pstUser.setString(8, null);

							pstUser.setString(9, (String)map.get("websiteURL"));
							pstUser.executeUpdate();
							
							// insert group network records
							
							Object o = map.get("out_write");
							if ( o!=null) {
								if (o instanceof String) {
									pstUserNetwork.setObject(1, groupId);
									pstUserNetwork.setString(2, (String)o);
									pstUserNetwork.setString(3, "write");
									pstUserNetwork.executeUpdate();
								} else if ( o instanceof Collection<?>) {
									for (String oe : (Collection<String>)o) {
										pstUserNetwork.setObject(1, groupId);
										pstUserNetwork.setString(2, (String)oe);
										pstUserNetwork.setString(3, "write");
										pstUserNetwork.executeUpdate();
									}
								}
									
							}
							
							o = map.get("out_read");
							if ( o!=null) {
								if (o instanceof String) {
									pstUserNetwork.setObject(1, groupId);
									pstUserNetwork.setString(2, (String)o);
									pstUserNetwork.setString(3, "read");
									pstUserNetwork.executeUpdate();
								} else if ( o instanceof Collection<?>) {
									for (String oe : (Collection<String>)o) {
										pstUserNetwork.setObject(1, groupId);
										pstUserNetwork.setString(2, (String)oe);
										pstUserNetwork.setString(3, "read");
										pstUserNetwork.executeUpdate();
									}
								}
									
							}
						}
					}
				}
			}
			
			db.commit();
			
		}
	}
	
	
	private void importNetworks() throws IOException, JsonProcessingException, SQLException, FileNotFoundException {
		//import users.
		try (FileInputStream i = new FileInputStream(importFilesPrefix+"/network.json")) {
			
			String sqlStr = "insert into v1_network (rid, id, creation_time, modification_time, visibility,  node_count,edge_count, description,"
					+ "version,props, provenance,readonly,name,source_format) values (?,?,?,?,?, ?,?,?,?,? ::json, ? :: json,?,?,?) on conflict (id) do nothing";

			try (PreparedStatement pstUser = db.prepareStatement(sqlStr)) {
						Iterator<Map<String,Object>> it = new ObjectMapper().readerFor(typeRef).readValues(i);
		        	
						while (it.hasNext()) {
							Map<String,Object> map = it.next();
							UUID networkId = UUID.fromString((String) map.get("uuid"));
							logger.info("processing network " + networkId );							

							// insert network rec
							pstUser.setString(1, (String)map.get("rid"));
							pstUser.setObject(2, networkId);
							pstUser.setTimestamp(3, new Timestamp((long)map.get("createdTime")));
							pstUser.setTimestamp(4,new Timestamp((long)map.get("modificationTime")));
							pstUser.setString(5, (String) map.get("visibility"));
							pstUser.setLong(6, (Integer)map.get("nodeCount"));
							pstUser.setLong(7, (Integer)map.get("edgeCount"));
							pstUser.setString(8, (String) map.get("description"));
							pstUser.setString(9, (String) map.get("version"));
							
							Object propObj = map.get("props");
							if (propObj !=null)
								pstUser.setString(10, mapper.writeValueAsString(map.get("props")));
							else 
								pstUser.setString(10, null);
							pstUser.setString(11, (String)map.get("provenance"));
							pstUser.setBoolean(12,(Boolean)map.get("readonly"));
							pstUser.setString(13,(String)map.get("name"));
							pstUser.setString(14,(String)map.get("sourceFormat"));
						
							pstUser.executeUpdate();
						}
			}
			
			db.commit();
			
		}
	}
	
	private void importTasks() throws IOException, JsonProcessingException, SQLException, FileNotFoundException {
		//import users.
		try (FileInputStream i = new FileInputStream(importFilesPrefix+"/task.json")) {
			
			String sqlStr1 = "insert into v1_task (rid, id, creation_time, modification_time,description,"
					+ "status,task_type,resource,start_time,end_time, owneruuid,format, attributes) values "
					+ "(?,?,?,?,?, ?,?,?,?,?, ?,?,? :: json) on conflict (id) do nothing";

			try (PreparedStatement pstUser = db.prepareStatement(sqlStr1)) {

						Iterator<Map<String,Object>> it = new ObjectMapper().readerFor(typeRef).readValues(i);
		        	
						while (it.hasNext()) {
							Map<String,Object> map = it.next();
							UUID taskId = UUID.fromString((String) map.get("UUID"));
							logger.info("processing task " +  taskId);


							// insert user rec
							pstUser.setString(1, (String)map.get("@rid"));
							pstUser.setObject(2, taskId);
							pstUser.setTimestamp(3,Timestamp.valueOf((String)map.get("createdTime")));
							pstUser.setTimestamp(4,Timestamp.valueOf((String)map.get("modificationTime")));
							pstUser.setString(5, (String)map.get("description"));
							pstUser.setString(6, (String)map.get("status"));
							pstUser.setString(7, (String)map.get("taskType"));

							pstUser.setObject(8, UUID.fromString((String)map.get("resource")));
						
							pstUser.setTimestamp(9, (map.get("startTime") !=null ? Timestamp.valueOf((String)map.get("startTime")): null));
							pstUser.setTimestamp(10,(map.get("endTime") != null ?  Timestamp.valueOf((String)map.get("endTime")): null));
							pstUser.setObject(11, UUID.fromString((String)map.get("ownerUUID")));
							pstUser.setString(12, (String)map.get("format"));
							pstUser.setString(13, mapper.writeValueAsString(map.get("attributes")));

							pstUser.executeUpdate();

						}
			
			}
			
			db.commit();
			
		}
	}

	private void importRequests() throws IOException, JsonProcessingException, SQLException, FileNotFoundException {
		//import users.
		try (FileInputStream i = new FileInputStream(importFilesPrefix+"/request.json")) {
			
			String sqlStr1 = "insert into v1_request (rid, id, creation_time, modification_time,source_uuid,"
					+ "destination_uuid,message,responder,response_message,response, "
					+ "in_request,out_request,request_permission,response_time) values "
					+ "(?,?,?,?,?, ?,?,?,?,?, ?,?,?,?) on conflict (id) do nothing";

			try (PreparedStatement pstUser = db.prepareStatement(sqlStr1)) {

						Iterator<Map<String,Object>> it = new ObjectMapper().readerFor(typeRef).readValues(i);
		        	
						while (it.hasNext()) {
							Map<String,Object> map = it.next();
							UUID requestId = UUID.fromString((String) map.get("UUID"));
							logger.info("processing request " +  requestId);


							// insert request rec
							pstUser.setString(1, (String)map.get("@rid"));
							pstUser.setObject(2, requestId);
							pstUser.setTimestamp(3,Timestamp.valueOf((String)map.get("createdTime")));
							pstUser.setTimestamp(4,Timestamp.valueOf((String)map.get("modificationTime")));
							pstUser.setObject(5, UUID.fromString((String)map.get("sourceUUID")));

							pstUser.setObject(6, UUID.fromString((String)map.get("destinationUUID")));
							
							pstUser.setString(7, (String)map.get("message"));
							pstUser.setString(8, (String)map.get("responder"));
							pstUser.setString(9, (String)map.get("responseMessage"));
							pstUser.setString(10, (String)map.get("response"));
							
							pstUser.setString(11, (String)map.get("in_requests"));
							pstUser.setString(12,  mapper.writeValueAsString(map.get("out_requests")));
							pstUser.setString(13,(String)map.get("requestPermission"));
							pstUser.setTimestamp(14,(map.get("responseTime") !=null ? Timestamp.valueOf((String)map.get("responseTime")) : null));
							pstUser.executeUpdate();

						}
			
			}
			
			db.commit();
			
		}
	}
	
	public static void main(String[] args) throws Exception {

		if (args.length != 1) {
			System.out.println(
					"Usage: V13DbImporter <Ndex 1.3 db export path>\n\n example: \n\n V13DbImporter /opt/ndex/migration\n");
			return;
		}

		try (V13DbImporter importer = new V13DbImporter(args[0])) {

			importer.migrateDBAndNetworks();
		}
		logger.info("1.3 DB migration to 2.0 completed.");
	}

}
