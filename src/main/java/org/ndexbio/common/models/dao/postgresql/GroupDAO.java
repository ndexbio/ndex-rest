/**
 * Copyright (c) 2013, 2016, The Regents of the University of California, The Cytoscape Consortium
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 *
 * 1. Redistributions of source code must retain the above copyright notice, this
 *    list of conditions and the following disclaimer.
 *
 * 2. Redistributions in binary form must reproduce the above copyright notice,
 *    this list of conditions and the following disclaimer in the documentation
 *    and/or other materials provided with the distribution.
 *
 * 3. Neither the name of the copyright holder nor the names of its contributors
 *    may be used to endorse or promote products derived from this software
 *    without specific prior written permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND
 * ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED
 * WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
 * DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT HOLDER OR CONTRIBUTORS BE LIABLE
 * FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL
 * DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR
 * SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER
 * CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY,
 * OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE
 * OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 *
 */
package org.ndexbio.common.models.dao.postgresql;

import java.io.IOException;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;
import java.util.UUID;
import java.util.logging.Logger;

import org.apache.solr.client.solrj.SolrServerException;
import org.apache.solr.common.SolrDocument;
import org.apache.solr.common.SolrDocumentList;
import org.ndexbio.common.NdexClasses;
import org.ndexbio.common.solr.GroupIndexManager;
import org.ndexbio.common.solr.UserIndexManager;
import org.ndexbio.common.util.NdexUUIDFactory;
import org.ndexbio.model.exceptions.ObjectNotFoundException;
import org.ndexbio.model.exceptions.DuplicateObjectException;
import org.ndexbio.model.exceptions.NdexException;

import org.ndexbio.model.object.Group;
import org.ndexbio.model.object.Membership;
import org.ndexbio.model.object.MembershipType;
import org.ndexbio.model.object.Permissions;
import org.ndexbio.model.object.SimpleQuery;
import org.ndexbio.model.object.SolrSearchResult;
import org.ndexbio.model.object.User;

import com.fasterxml.jackson.core.JsonParseException;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.base.Preconditions;
import com.google.common.base.Strings;

public class GroupDAO extends NdexDBDAO {
	
	private static final Logger logger = Logger.getLogger(GroupDAO.class.getName());

	/**************************************************************************
	    * GroupDAO
	    * 
	    * @param db
	    *            Database instance from the Connection pool, should be opened
	    * @param graph
	    * 			OrientBaseGraph layer on top of db instance. 
	 * @throws SQLException 
	    **************************************************************************/
	
	public GroupDAO() throws SQLException {
		super();
	}
	
	
	/**************************************************************************
	    * Get a Group
	    * 
	    * @param id
	    *            UUID for Group
	    * @throws NdexException
	    *            Attempting to access database
	    * @throws IllegalArgumentexception
	    * 			The id is invalid
	    * @throws ObjectNotFoundException
	    * 			The group specified by id does not exist
	 * @throws SQLException 
	 * @throws IOException 
	 * @throws JsonMappingException 
	 * @throws JsonParseException 
	    **************************************************************************/
	public Group getGroupById(UUID id)
			throws IllegalArgumentException, ObjectNotFoundException, NdexException, SQLException, JsonParseException, JsonMappingException, IOException{
		Preconditions.checkArgument(null != id, 
				"UUID required");
		
		String sqlStr = "SELECT * FROM " + NdexClasses.Group + " where \"UUID\" = '" + id + "' :: uuid and is_deleted = false";

		try (Statement st = db.createStatement()) {
			try (ResultSet rs = st.executeQuery(sqlStr) ) {
				if (rs.next()) {
					// populate the user object;
					Group result = new Group();

					populateGroupFromResultSet(result, rs);

					return result;
				} 
				throw new ObjectNotFoundException("Group with UUID: " + id.toString() + " doesn't exist.");

			}
		}
		
	}
	
	
	private static void populateGroupFromResultSet(Group group, ResultSet rs) throws JsonParseException, JsonMappingException, SQLException, IOException {
		Helper.populateAccountFromResultSet (group, rs);

		group.setGroupName(rs.getString("group_name"));
	}
	
	/**************************************************************************
	    * Get a Group
	    * 
	    * @param accountName
	    *            Group's accountName
	    * @throws NdexException
	    *            Attempting to access database
	    * @throws IllegalArgumentexception
	    * 
	    * 			The id is invalid
	    * @throws ObjectNotFoundException
	    * 			The group specified by id does not exist
	 * @throws SQLException 
	 * @throws IOException 
	 * @throws JsonMappingException 
	 * @throws JsonParseException 
	    **************************************************************************/
	public Group getGroupByGroupName(String accountName)
			throws IllegalArgumentException, ObjectNotFoundException, NdexException, SQLException, JsonParseException, JsonMappingException, IOException{
		Preconditions.checkArgument(!Strings.isNullOrEmpty(accountName), 
				"UUID required");
		
		String sqlStr = "SELECT * FROM " + NdexClasses.Group + " where group_name = ? and is_deleted = false";

		try (PreparedStatement st = db.prepareStatement(sqlStr)) {
			st.setString(1, accountName);
			try (ResultSet rs = st.executeQuery() ) {
				if (rs.next()) {
					// populate the user object;
					Group result = new Group();

					populateGroupFromResultSet(result, rs);

					return result;
				} 
				throw new ObjectNotFoundException("Group " + accountName + " doesn't exist.");

			}
		}

	}
	
	
	/**************************************************************************
	    * Find groups
	    * 
	    * @param query
	    * 			SimpleUserQuery object. The search string filters by 
	    * 			group account name and organization name. The accountName
	    * 			filters to groups owned by the account specified.
	    * @param skipBlocks
	    *            amount of blocks to skip
	    * @param blockSize
	    * 			the size of a block
	    * @throws NdexException
	    *            Attempting to access and delete an ODocument from the database
	    * @throws IllegalArgumentException
	    * 			Group object cannot be null
	 * @throws IOException 
	 * @throws SolrServerException 
	 * @throws SQLException 
	    **************************************************************************/
	public SolrSearchResult<Group> findGroups(SimpleQuery simpleQuery, int skipBlocks, int blockSize) 
			throws NdexException, IllegalArgumentException, SolrServerException, IOException, SQLException {
		
		Preconditions.checkArgument(null != simpleQuery, "Search parameters are required");

		 if ( simpleQuery.getSearchString().length()==0)
		    	simpleQuery.setSearchString("*:*");
		 
		GroupIndexManager indexManager = new GroupIndexManager();
		SolrDocumentList l = indexManager.searchGroups(simpleQuery.getSearchString(), blockSize, skipBlocks*blockSize);	
		List<Group> results = new ArrayList<>(l.size());
		for (SolrDocument d : l) {
			results.add(getGroupById(UUID.fromString((String)d.get(GroupIndexManager.UUID))));
		}
		
		return new SolrSearchResult<> (l.getNumFound(),l.getStart(), results);
		
	}
	
	
	
	/**************************************************************************
	    * getGroupNetworkMemberships
	    *
	    * @param groupId
	    *            UUID for associated group
	    * @param permission
	    * 			Type of memberships to retrieve, GROUPADMIN or MEMBER
	    * @param skipBlocks
	    * 			amount of blocks to skip
	    * @param blockSize
	    * 			The size of blocks to be skipped and retrieved
	    * @throws NdexException
	    *            Invalid parameters or an error occurred while accessing the database
	    * @throws ObjectNotFoundException
	    * 			Invalid groupId
	 * @throws IOException 
	 * @throws SQLException 
	 * @throws IllegalArgumentException 
	 * @throws JsonMappingException 
	 * @throws JsonParseException 
	    **************************************************************************/
	public List<Membership> getGroupNetworkMemberships(UUID groupId, Permissions permission, int skipBlocks, int blockSize, UUID userId, boolean inclusive) 
			throws ObjectNotFoundException, NdexException, JsonParseException, JsonMappingException, IllegalArgumentException, SQLException, IOException {
		
		Preconditions.checkArgument(!Strings.isNullOrEmpty(groupId.toString()),
				"A group UUID is required");
		Preconditions.checkArgument( (permission.equals( Permissions.READ ))
				|| (permission.equals( Permissions.WRITE )),
				"Valid permissions required");
				
	
		String permissionStr = "";		
		if ((permission.equals( Permissions.WRITE ) )) {
			permissionStr = " and permission_type=\'" + Permissions.WRITE.toString() + "\'";
		} else if ( permission != Permissions.READ ) {
			throw new IllegalArgumentException("Valid permissions required.");
		} else if ( !inclusive)
			permissionStr = " and permission_type=\'" + Permissions.READ.toString() + "\'";
				
		String sqlStr = "SELECT n.\"UUID\", n.name, gn.permission_type FROM group_network_membership gn, network n where gn.group_id = ? "+ permissionStr +
				" and gn.network_id = n.\"UUID\" and " + NetworkDAO.createIsReadableConditionStr(userId) + " order by n.modification_time desc";
		if ( skipBlocks>=0 && blockSize>0) {
			sqlStr += " limit " + blockSize + " offset " + skipBlocks * blockSize;
		}
		
		Group group = getGroupById(groupId);
		
		List<Membership> memberships = new ArrayList<>();
		try (PreparedStatement st = db.prepareStatement(sqlStr)) {
			st.setObject(1, groupId);
			
			try (ResultSet rs = st.executeQuery() ) {
				while (rs.next()) {
					
					Membership membership = new Membership();
					membership.setMembershipType( MembershipType.NETWORK );
					membership.setMemberAccountName( group.getGroupName() ); 
					membership.setMemberUUID( groupId );
					membership.setPermissions( Permissions.valueOf(rs.getString(3)) );
					membership.setResourceName( rs.getString(2) );
					membership.setResourceUUID( (UUID) rs.getObject(1) );
					
					memberships.add(membership);
				} 
			}
		}	
		
		return memberships;
		
	}
	
	/**
	 * 
	 * 
	 *  
	 * @param groupId
	 * @param userId when userId is null, only public networks are returned. Otherwise the result is filtered by the userId.
	 * @return
	 * @throws ObjectNotFoundException
	 * @throws NdexException
	 */
	
/*	public List<NetworkSummary> getGroupNetworks (UUID groupId, UUID userId) 
			throws ObjectNotFoundException, NdexException {
		
		Preconditions.checkArgument(!Strings.isNullOrEmpty(groupId.toString()),
				"A group UUID is required");
		
		ODocument group = this.getRecordByUUIDStr(groupId, NdexClasses.Group);
	
		List<NetworkSummary> results = new ArrayList<>(20);
		
		try {
			String groupRID = group.getIdentity().toString();
			OSQLSynchQuery<ODocument> query = new OSQLSynchQuery<>(
		  			"SELECT FROM"
		  			+ " (TRAVERSE "+ NdexClasses.Group +".out_"+ Permissions.READ.toString().toLowerCase()  + 
		  			     "," + NdexClasses.Group + ".out_"+ Permissions.WRITE.toString().toLowerCase() +" FROM"
		  				+ " " + groupRID
		  				+ "  WHILE $depth <=1)"
		  			+ " WHERE @class = '" + NdexClasses.Network + "' and ( " + NdexClasses.ExternalObj_isDeleted + " = false)"
		 			);
			
			List<ODocument> records = this.db.command(query).execute(); 
			for(ODocument netDoc: records) {
				NetworkSummary summary = NetworkDocDAO.getNetworkSummary(netDoc);
				
				if ( summary.getVisibility() == VisibilityType.PUBLIC) {
					results.add(summary );
				} else if ( userAccountName !=null ){
					 NetworkDocDAO dao = new NetworkDocDAO(this.getDBConnection());
					 if ( dao.networkSummaryIsReadable(userAccountName, summary.getExternalId().toString()) )
						results.add(summary);
				}
			}
			
			logger.info("Successfuly retrieved group-networks");
			return results;
			
		} catch(Exception e) {
			logger.severe("An unexpected error occured while retrieving group-networks " + e.getMessage());
			throw new NdexException("Unable to get networks for group with UUID "+groupId);
		}
	}	 */
	
	/**************************************************************************
	    * getGroupUserMemberships
	    *
	    * @param groupId
	    *            UUID for associated group
	    * @param permission
	    * 			Type of memberships to retrieve, ADMIN, WRITE, or READ
	    * @param skipBlocks
	    * 			amount of blocks to skip
	    * @param blockSize
	    * 			The size of blocks to be skipped and retrieved
	    * @throws NdexException
	    *            Invalid parameters or an error occurred while accessing the database
	    * @throws ObjectNotFoundException
	    * 			Invalid groupId
	 * @throws IOException 
	 * @throws SQLException 
	 * @throws IllegalArgumentException 
	 * @throws JsonMappingException 
	 * @throws JsonParseException 
	    **************************************************************************/
	
	public List<Membership> getGroupUserMemberships(UUID groupId, Permissions permission, int skipBlocks, int blockSize, boolean inclusive) 
			throws ObjectNotFoundException, NdexException, JsonParseException, JsonMappingException, IllegalArgumentException, SQLException, IOException {
		
		Preconditions.checkArgument( (permission.equals( Permissions.GROUPADMIN) )
				|| (permission.equals( Permissions.MEMBER )),
				"Valid permissions required");
		
		String queryStr = "select user_id, is_admin, u.user_name from ndex_group_user gu, ndex_user u where u.\"UUID\" = gu.user_id and group_id = ?";
		
		if ( permission == Permissions.GROUPADMIN) 
			queryStr += " and is_admin";
		else {
			if ( !inclusive) {
				queryStr += " and is_admin = false";
			}
		}
			
		
		if ( skipBlocks>=0 && blockSize>0) {
			queryStr += " limit " + blockSize + " offset " + skipBlocks * blockSize;
		}
		
		Group group = getGroupById(groupId);
		
		List<Membership> memberships = new ArrayList<>();
		try (PreparedStatement st = db.prepareStatement(queryStr)) {
			st.setObject(1, groupId);
			
			try (ResultSet rs = st.executeQuery() ) {
				while (rs.next()) {
					
					Membership membership = new Membership();
					membership.setMembershipType( MembershipType.GROUP );
					membership.setMemberAccountName( rs.getString(3) ); 
					membership.setMemberUUID( (UUID)rs.getObject(1) );
					if (rs.getBoolean(2)) 
						membership.setPermissions( Permissions.GROUPADMIN );
					else 
						membership.setPermissions( Permissions.MEMBER );

					membership.setResourceName( group.getGroupName() );
					membership.setResourceUUID( groupId );
					
					memberships.add(membership);
				} 
			}
		}
		
		return memberships;
		
	}

	/*
	
	private  List<String> getGroupUserAccount(String groupAccount) 
			throws ObjectNotFoundException, NdexException {
		
		Preconditions.checkArgument(!Strings.isNullOrEmpty(groupAccount.toString()),
				"A group UUID is required");
	
		ODocument group = this.getRecordByAccountName(groupAccount, NdexClasses.Group);
		
		List<String> result = new ArrayList<>();
		try {
			List<Membership> memberships = new ArrayList<>();
			
			String groupRID = group.getIdentity().toString();
			OSQLSynchQuery<ODocument> query = new OSQLSynchQuery<>(
		  			"SELECT FROM"
		  			+ " (TRAVERSE "+ NdexClasses.Group +".in_"+ Permissions.GROUPADMIN.name().toString().toLowerCase() +
		  					",in_" + NdexClasses.Group +".in_"+ Permissions.MEMBER.name().toString().toLowerCase() 
		  					+ " FROM " + groupRID + "  WHILE $depth <=1) WHERE @class = '" + NdexClasses.User + 
		  					"' AND ( " + NdexClasses.ExternalObj_isDeleted + " = false) ");
			
			List<ODocument> records = this.db.command(query).execute(); 
			for(ODocument member: records) {
				result.add((String)member.field(NdexClasses.account_P_accountName));		
			}
			
			return result;
			
		} catch(Exception e) {
			logger.severe("An unexpected error occured while retrieving group-user memberships "+e.getMessage());
			throw new NdexException("Unable to get user memberships for group "+groupAccount);
		} 
	} */
	
	public Permissions getMembershipToNetwork(UUID groupId, UUID networkId) 
		throws IllegalArgumentException, ObjectNotFoundException, NdexException, SQLException {
		
		Preconditions.checkArgument(groupId != null, "UUID for group required");
		Preconditions.checkArgument(networkId != null, "UUID for network required");
	
		String queryStr = "select permission_type from group_network_membership where group_id = ? and network_id = ?";
		
		try (PreparedStatement pst = db.prepareStatement(queryStr)) {
			pst.setObject(1, groupId);
			pst.setObject(2, networkId);
			try (ResultSet rs = pst.executeQuery()) {
				if ( rs.next()) {
					return Permissions.valueOf(rs.getString(1));
				}
				throw new ObjectNotFoundException("Network " + networkId + "not found in function getMembershipToNetwork(...)");
			}
		}
	}
	
	private void checkForExistingGroup(final Group group) 
			throws IllegalArgumentException, NdexException, JsonParseException, JsonMappingException, SQLException, IOException {
		
		Preconditions.checkArgument(null != group, 
				"UUID required");

		try {
			getGroupByGroupName(group.getGroupName());
			String msg = "Group with name " + group.getGroupName() + " already exists.";
			logger.info(msg);
			throw new DuplicateObjectException(msg);
		} catch ( ObjectNotFoundException e) {
			// when account doesn't exists return as normal.
		}
		
	}

	/**************************************************************************
	    * Create a new group
	    * 
	    * @param newGroup
	    *            A Group object, from the NDEx Object Model
	    * @param adminId
	    * 			UUID for logged in user
	    * @throws NdexException
	    *            Attempting to save an ODocument to the database
	    * @throws IllegalArgumentException
	    * 			 The newUser does not contain proper fields
	    * @throws DuplicateObjectException
	    * 			 The account name and/or email already exist
	 * @throws IOException 
	 * @throws SQLException 
	 * @throws JsonMappingException 
	 * @throws JsonParseException 
	    * @returns Group object, from the NDEx Object Model
	    **************************************************************************/
	public Group createNewGroup(Group newGroup, UUID adminId)
			throws NdexException, IllegalArgumentException, DuplicateObjectException, JsonParseException, JsonMappingException, SQLException, IOException {

			Preconditions.checkArgument(null != newGroup, 
					"A group is required");
//			Preconditions.checkArgument(!Strings.isNullOrEmpty(newGroup.getOrganizationName()),
	//				"An organizationName is required");
			Preconditions.checkArgument(!Strings.isNullOrEmpty( newGroup.getGroupName()),
					"A group name is required" );
			Preconditions.checkArgument(!Strings.isNullOrEmpty(adminId.toString()),
					"An admin id is required" );
			
			this.checkForExistingGroup(newGroup);

			String insertStr = "insert into ndex_group (\"UUID\", creation_time, modification_time,is_deleted,"+
					"description, image_url,website_url, group_name) values (?,?,?,false,?,?,?,?)";
			
			try (PreparedStatement st = db.prepareStatement(insertStr) ) {
				newGroup.setExternalId(NdexUUIDFactory.INSTANCE.createNewNDExUUID());
				Timestamp current = new Timestamp(Calendar.getInstance().getTimeInMillis());
				newGroup.setCreationTime(current);
				newGroup.setModificationTime(current);
				
				st.setObject(1, newGroup.getExternalId());
				st.setTimestamp(2, current);
				st.setTimestamp(3, current);
				st.setString ( 4, newGroup.getDescription());
				st.setString(5, newGroup.getImage());
				st.setString(6, newGroup.getWebsite());
				
				st.setString(7, newGroup.getGroupName());
				
				int rowsInserted = st.executeUpdate();
				if ( rowsInserted != 1)
					throw new NdexException ( "Failed to save group " + newGroup.getGroupName() + " to database.");
			}
			
			insertStr = "insert into ndex_group_user (group_id,user_id,is_admin) values(?,?,true)";
			try (PreparedStatement st = db.prepareStatement(insertStr) ) {
				st.setObject(1, newGroup.getExternalId());
				st.setObject(2, adminId);
				
				int rowsInserted = st.executeUpdate();
				if ( rowsInserted != 1)
					throw new NdexException ( "Failed to save group-user relationship" + newGroup.getGroupName() + " to database.");
			}
			
			return newGroup;

		}

	/**************************************************************************
	    * Delete a group
	    * 
	    * @param groupId
	    *            UUID for Group
	    * @param adminId
	    * 			UUID for admin of group
	    * @throws NdexException
	    *            Attempting to access and delete an ODocument from the database
	    * @throws ObjectNotFoundException
	    * 			Specified group does not exist
	 * @throws SQLException 
	    **************************************************************************/
	public void deleteGroupById(UUID groupId, UUID adminId) 
		throws NdexException, ObjectNotFoundException, SQLException{
			
			//TODO cannot orphan networks, has not been tested
		
		Preconditions.checkArgument(null != groupId, 
				"group UUID required");
		Preconditions.checkArgument(null != adminId, 
				"admin UUID required");
		
		// get records and validate admin permissions
		if (!isGroupAdmin(groupId, adminId) )
			throw new NdexException ("User " + adminId + " doesn't have permission to delete group "
		            + groupId);
		
		//TODO: check if there are pending requests.

		
		String[] sqlCmds = {
				"insert into ndex_group_user_arc (group_id,user_id, is_admin) " + 
						" select group_id,user_id,is_admin from ndex_group_user where group_id = ?",
				"delete from ndex_group_user where group_id = ?",
				"update ndex_group set is_deleted = true where \"UUID\" = ? and not is_deleted"
			};
		
		for (String cmd : sqlCmds) {
			try (PreparedStatement st = db.prepareStatement(cmd) ) {
				st.setObject(1, groupId);
				st.executeUpdate();
			}		
		}
		
	}
	

	/**
	 *  Returns true if the given user is an admin of the given group.
	 * @param groupId
	 * @param userId
	 * @return
	 * @throws SQLException
	 */
	public boolean isGroupAdmin(UUID groupId, UUID userId) throws SQLException  {
		
		String queryStr = "SELECT * FROM ndex_group_user where group_id = ? and user_id = ? and is_admin";
		
		try (PreparedStatement st = db.prepareStatement(queryStr))  {
			st.setObject(1, groupId);
			st.setObject(2, userId);
		
			try (ResultSet rs = st.executeQuery() ) {
				return rs.next();
			}
		}
		
	}
	
	/**
	 * Returns true if the given user is a member or admin of the given group.
	 * @param groupId
	 * @param userId
	 * @return
	 * @throws SQLException
	 */
	public boolean isInGroup(UUID groupId, UUID userId) throws SQLException  {
		
		String queryStr = "SELECT * FROM ndex_group_user where group_id = ? and user_id = ?";
		
		try (PreparedStatement st = db.prepareStatement(queryStr))  {
			st.setObject(1, groupId);
			st.setObject(2, userId);
		
			try (ResultSet rs = st.executeQuery() ) {
				return rs.next();
			}
		}
		
	}
	
	
	public Group updateGroup(Group updatedGroup, UUID groupId) 
			throws IllegalArgumentException, NdexException, ObjectNotFoundException, SQLException, JsonProcessingException {
				
			Preconditions.checkArgument(groupId != null, 
						"A group id is required");
			Preconditions.checkArgument(updatedGroup != null, 
						"An updated group is required");
			
				
			String updateStr = "update ndex_group set modification_time = localtimestamp, "
					+ "group_name = ?, image_url = ?, description =?, website_url = ?, other_attributes = ? :: json "
					+ " where \"UUID\" = ? and is_deleted = false";
			
			try (PreparedStatement st = db.prepareStatement(updateStr) ) {
				st.setString(1,  updatedGroup.getGroupName());
				st.setString ( 2, updatedGroup.getImage());
				st.setString(3, updatedGroup.getDescription());
				st.setString(4, updatedGroup.getWebsite());
							
				if ( updatedGroup.getProperties()!=null && updatedGroup.getProperties().size() >0 ) {
					ObjectMapper mapper = new ObjectMapper();
			        String s = mapper.writeValueAsString( updatedGroup.getProperties());
					st.setString(5, s);
				} else {
					st.setString(5, null);
				}
				
				st.setObject(6, groupId);			
				
				int rowsInserted = st.executeUpdate();
				if ( rowsInserted != 1)
					throw new NdexException ( "Failed to save user " + updatedGroup.getGroupName() + " to database when update.");
			}
			
			return updatedGroup;
	
	}
		

	public void updateMember( UUID groupId, UUID userId, Permissions permission, UUID adminId)
			throws ObjectNotFoundException, NdexException, SQLException {

		Preconditions.checkArgument( permission == Permissions.GROUPADMIN || 
				 permission == Permissions.MEMBER ,
				"Valid permissions required");
		
		
		if ( adminId.equals(userId) && permission == Permissions.MEMBER && isTheOnlyAdmin(groupId,adminId)) {
				throw new NdexException ("Cannot orphan group to have no admin.");
		}
		
		String insertStr = "insert into ndex_group_user (group_id,user_id, is_admin) values ( ?,?,?) on conflict do update set is_admin = EXCLUDED.is_admin";
		
		try ( PreparedStatement pst = db.prepareStatement(insertStr)) {
			pst.setObject(1, groupId);
			pst.setObject(2, userId);
			pst.setBoolean(3, permission == Permissions.GROUPADMIN);
			
			pst.executeUpdate();
		}
		
	}
	
	/**
	 * Check if the given admin is the only Admin of the given group.
	 * @param groupID
	 * @param adminId
	 * @return
	 * @throws SQLException 
	 * @throws NdexException 
	 */
	private boolean isTheOnlyAdmin(UUID groupId, UUID adminId) throws SQLException, NdexException {
		
		String chkStr = "select count(*) from ndex_group_user where group_id = ? and is_admin and user_id <> ?";
		try ( PreparedStatement pst = db.prepareStatement(chkStr)) {
			pst.setObject(1, adminId);
			pst.setObject(2, adminId);
			try (ResultSet rs = pst.executeQuery()) {
				if ( rs.next()) {
					return rs.getInt(1) <1;
				}
				throw new NdexException ("Ndex internal error. Can't find admins on group "+ groupId.toString());
				}
		}
	}

	
	public void removeMember(UUID memberId, UUID groupId, UUID adminId) 
			throws ObjectNotFoundException, IllegalArgumentException, NdexException, SQLException {
		Preconditions.checkArgument( memberId != null ,
				"member UUID required");
		Preconditions.checkArgument( groupId != null ,
				"group UUID required");
		Preconditions.checkArgument(adminId !=null ,
				"admin UUID required");
		
		if ( adminId.equals(memberId) && isTheOnlyAdmin(groupId,adminId)) {
			throw new NdexException ("Cannot orphan group to have no admin.");	
		}
		
		String[] sqlCmds = {
				"insert into ndex_group_user_arc (group_id,user_id, is_admin) " + 
						" select group_id,user_id,is_admin from ndex_group_user where group_id = ? and user_id = ?",
				"delete from ndex_group_user where group_id = ? and user_id = ?",
			};
		
		for (String cmd : sqlCmds) {
			try (PreparedStatement st = db.prepareStatement(cmd) ) {
				st.setObject(1, groupId);
				st.setObject(2, memberId);
				st.executeUpdate();
			}		
		}
		
	}
	
	

	
}
