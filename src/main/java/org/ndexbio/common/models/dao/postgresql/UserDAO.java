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
import java.security.NoSuchAlgorithmException;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.UUID;
import java.util.logging.Logger;

import org.apache.solr.client.solrj.SolrServerException;
import org.apache.solr.common.SolrDocument;
import org.apache.solr.common.SolrDocumentList;
import org.ndexbio.common.NdexClasses;
import org.ndexbio.common.solr.UserIndexManager;
import org.ndexbio.common.util.NdexUUIDFactory;
import org.ndexbio.common.util.Security;
import org.ndexbio.model.exceptions.DuplicateObjectException;
import org.ndexbio.model.exceptions.NdexException;
import org.ndexbio.model.exceptions.ObjectNotFoundException;
import org.ndexbio.model.exceptions.UnauthorizedOperationException;
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

public class UserDAO extends NdexDBDAO {

	private static final Logger logger = Logger.getLogger(UserDAO.class
			.getName());

	/*
	 * User operations can be achieved with Orient Document API methods. The
	 * constructor will need to accept a OrientGraph object if we wish to use
	 * the Graph API.
	 */
	/**************************************************************************
	 * UserDAO
	 * 
	 * @param db
	 *            Database instance from the Connection pool, should be opened
	 * @param graph
	 *            OrientGraph instance for Graph API operations
	 * @throws SQLException 
	 * @throws NdexException 
	 **************************************************************************/
	
	public UserDAO() throws SQLException {
		super();
	}

	/**************************************************************************
	 * Authenticates a user trying to login.
	 * 
	 * @param accountName
	 *            The accountName.
	 * @param password
	 *            The password.
	 * @throws SecurityException
	 *             Invalid accountName or password.
	 * @return The user, from NDEx Object Model.
	 * @throws Exception 
	 **************************************************************************/
	public User authenticateUser(String accountName, String password)
			throws Exception {

		if (Strings.isNullOrEmpty(accountName)
				|| Strings.isNullOrEmpty(password))
			throw new UnauthorizedOperationException("No accountName or password entered.");

		if (!Security.authenticateUser(password, getUserPasswordByAccountName(accountName))) {
				throw new UnauthorizedOperationException("Invalid accountName or password.");
		}
	
		User user = getUserByAccountName(accountName, true);
//		user.setPassword("");
		return user;
		
	}

	/**************************************************************************
	 * Create a new user
	 * 
	 * @param newUser
	 *            A User object, from the NDEx Object Model
	 
	 * @returns User object, from the NDEx Object Model
	 **************************************************************************/
	public User createNewUser(User newUser, String verificationCode ) throws JsonParseException, JsonMappingException, IllegalArgumentException, NdexException, SQLException, IOException, NoSuchAlgorithmException {

		Preconditions.checkArgument(null != newUser,
				"A user object is required");
		Preconditions.checkArgument(
				!Strings.isNullOrEmpty(newUser.getUserName()),
				"A accountName is required");
		
		Preconditions.checkArgument(
				!Strings.isNullOrEmpty(newUser.getPassword()),
				"A user password is required");
		Preconditions.checkArgument(
				!Strings.isNullOrEmpty(newUser.getEmailAddress()),
				"A user email address is required");

		try {
			User existingAcct = getUserByAccountName(newUser.getUserName(),false);
			throw new DuplicateObjectException ( "User " + newUser.getUserName() + " already exists in NDEx.");
		} catch ( ObjectNotFoundException e ) {}
		
		newUser.setExternalId(NdexUUIDFactory.INSTANCE.createNewNDExUUID());
		newUser.setCreationTime(new Timestamp(Calendar.getInstance().getTimeInMillis()));	
		newUser.setModificationTime(newUser.getCreationTime());
		
		String insertstmt = " insert into " + NdexClasses.User + " (\"" + NdexClasses.ExternalObj_ID + 
				    "\", " + NdexClasses.ExternalObj_cTime     + ", " + NdexClasses.ExternalObj_mTime + 
				    ","  + NdexClasses.ExternalObj_isDeleted + 
				    ","  + NdexClasses.Account_description   + ", " + NdexClasses.Account_imageURL +
				    ","  + NdexClasses.Account_websiteURL    + ", " + NdexClasses.Account_otherAttributes  +
				    ","  +  NdexClasses.User_userName        + ", " +  NdexClasses.User_lastName + 		    
				    ","  + NdexClasses.User_firstName        + ", " + NdexClasses.User_password +
				    ","  + NdexClasses.User_displayName      + ","  + NdexClasses.User_emailAddress + 
				    "," + NdexClasses.User_isIndividual     +","  + NdexClasses.User_isVerified + 
				    ") values ( ?,?,?,false,?,?, ?,? :: jsonb, ?,?, ?, ?,?,?,?,?)";
		try (PreparedStatement st = db.prepareStatement(insertstmt) ) {
				st.setObject(1, newUser.getExternalId());
				st.setTimestamp(2, newUser.getCreationTime());
				st.setTimestamp(3, newUser.getModificationTime());
				st.setString ( 4, newUser.getDescription());
				st.setString(5, newUser.getImage());
				st.setString(6, newUser.getWebsite());
				
				
				if ( verificationCode != null) {
					HashMap<String,String> attr = new HashMap<>();
					attr.put(NdexClasses.User_verification_code, verificationCode);
					ObjectMapper mapper = new ObjectMapper();
			        String s = mapper.writeValueAsString( attr);
					st.setString(7, s);
					st.setBoolean(15, false);
				} else {
					st.setString(7, null);
					st.setBoolean(15, true);
				}
				
				st.setString(8, newUser.getUserName());
				st.setString(9, newUser.getLastName());
				st.setString(10, newUser.getFirstName());
				st.setString(11, Security.hashText(newUser.getPassword()));
				st.setString(12, newUser.getDisplayName());
				st.setString(13, newUser.getEmailAddress());
				st.setBoolean(14, newUser.getIsIndividual());
				
				int rowsInserted = st.executeUpdate();
				if ( rowsInserted != 1)
					throw new NdexException ( "Failed to save user " + newUser.getUserName() + " to database.");
		} catch (SQLException ee) {
			if ( ee.getMessage().startsWith("ERROR: duplicate key value violates unique constraint \"user_emailaddr_constraint\""))
				throw new NdexException ("Email address '" + newUser.getEmailAddress() + "' is being used by another user." );
			
			throw ee;
		}
		return newUser;
		
	}

	
	public String verifyUser ( UUID userUUID, String verificationCode) throws ObjectNotFoundException, NdexException, SQLException, JsonParseException, JsonMappingException, IllegalArgumentException, IOException {
		
		User user = getUserById(userUUID, false);		
		
		if ( user.getIsVerified())
			throw new NdexException ( "User has already been verified.");
		
		String vCode = (String) user.getProperties().get(NdexClasses.User_verification_code) ;
		
		long t2 = Calendar.getInstance().getTimeInMillis();
		boolean within = (t2 - user.getCreationTime().getTime()) < 8 * 3600 * 1000;  // within 8 hours
		
		if ( vCode != null  && verificationCode.equals(vCode) && within) {
			user.getProperties().remove(NdexClasses.User_verification_code);
			String updateStr = "update " + NdexClasses.User + " set " + NdexClasses.Account_otherAttributes + 
					" = ? :: jsonb, " + NdexClasses.User_isVerified + " = true, "  + 
					NdexClasses.ExternalObj_mTime + "= ? where \"UUID\"= '" + user.getExternalId().toString() + "' :: uuid" ;
			
			try (PreparedStatement st = db.prepareStatement(updateStr) ) {
					ObjectMapper mapper = new ObjectMapper();
			        String s = mapper.writeValueAsString( user.getProperties());
					st.setString(1, s);	
					st.setTimestamp(2, new Timestamp (t2));
					int rowsInserted = st.executeUpdate();
					if ( rowsInserted != 1)
						throw new NdexException ( "Failed to verify user " + user.getUserName() + " in database.");
				
			}
			return user.getUserName();			

		}
		
		throw new NdexException ( "Verification information not found");
		
	}

	
	
	/**************************************************************************
	 * Get a user
	 * 
	 * @param id
	 *            UUID for User
	 *        currentUserOnly 
	 *        	  If set to true, this function only search for current users. Deleted users wont be returned. Otherwise 
	 *        	  this function search for both deleted and undeleted users.
	 *            
	 * @throws NdexException
	 *             Attempting to query the database
	 * @throws SQLException 
	 * @throws IOException 
	 * @throws JsonMappingException 
	 * @throws JsonParseException 
	 * @returns User object, from the NDEx Object Model
	 **************************************************************************/

	public User getUserById(UUID id, boolean currentUserOnly) throws NdexException,
			IllegalArgumentException, ObjectNotFoundException, SQLException, JsonParseException, JsonMappingException, IOException {

		Preconditions.checkArgument(null != id, "UUID required");
		
		String sqlStr = "SELECT * FROM " + NdexClasses.User + " where \"UUID\" = '" + id + "' :: uuid";
		if ( currentUserOnly) {
			sqlStr += " and is_deleted = false";
		}
		try (Statement st = db.createStatement()) {
			try (ResultSet rs = st.executeQuery(sqlStr) ) {
				if (rs.next()) {
					// populate the user object;
					User result = new User();

					populateUserFromResultSet(result, rs);

					return result;
				} 
				throw new ObjectNotFoundException("User with UUID: " + id.toString() + " doesn't exist.");

			}
		}

	}
	
	public UUID getUUIDByEmail(String email) throws NdexException,
		IllegalArgumentException, ObjectNotFoundException, SQLException {


		String sqlStr = "SELECT \"UUID\" FROM " + NdexClasses.User + " where  email_addr = ? and is_deleted=false";

		try (PreparedStatement st = db.prepareStatement(sqlStr)) {
			st.setString(1, email);
			try (ResultSet rs = st.executeQuery(sqlStr) ) {
				if (rs.next()) 
					return (UUID)rs.getObject(1);
			}
		} 
		throw new ObjectNotFoundException("User with email " + email.toString() + " doesn't exist.");

	}


	public boolean isNetworkAdmin(UUID userId, UUID networkId) throws IllegalArgumentException, SQLException {

		String sqlStr = "SELECT 1 FROM network where owneruuid = ? and \"UUID\" = ? and is_deleted=false";

		try (PreparedStatement st = db.prepareStatement(sqlStr)) {
			st.setObject(1, userId);
			st.setObject(2, networkId);
			try (ResultSet rs = st.executeQuery(sqlStr) ) {
				return rs.next();
			}
		} 

	}


	private static void populateUserFromResultSet(User user, ResultSet rs) throws JsonParseException, JsonMappingException, SQLException, IOException {
		Helper.populateAccountFromResultSet (user, rs);

		user.setFirstName(rs.getString(NdexClasses.User_firstName));
		user.setLastName(rs.getString(NdexClasses.User_lastName));
		user.setDisplayName(rs.getString(NdexClasses.User_displayName));
		user.setIsIndividual(rs.getBoolean(NdexClasses.User_isIndividual));
		user.setEmailAddress(rs.getString(NdexClasses.User_emailAddress));
	//	user.setPassword(rs.getString(NdexClasses.User_password));
		user.setIsVerified(rs.getBoolean(NdexClasses.User_isVerified));
		user.setUserName(rs.getString("user_name"));
	}
	
	/**************************************************************************
	 * Get a user
	 * 
	 * @param accountName
	 *            accountName for User
	
	 * @returns User object, from the NDEx Object Model
	 **************************************************************************/
	public User getUserByAccountName(String accountName, boolean verifiedOnly) throws NdexException,
			IllegalArgumentException, ObjectNotFoundException, SQLException, JsonParseException, JsonMappingException, IOException {

		String queryStr = "SELECT * FROM " + NdexClasses.User + " where " + NdexClasses.User_userName + " = ? and is_deleted=false";
		
		if ( verifiedOnly ) {
			queryStr += " and " + NdexClasses.User_isVerified ;
		}
		
		try (PreparedStatement st = db.prepareStatement(queryStr))  {
			st.setString(1, accountName);
		
			try (ResultSet rs = st.executeQuery() ) {
				if (rs.next()) {
					// populate the user object;
					
					User user = new User();
					populateUserFromResultSet(user, rs);
					if ( !user.getIsVerified())
						user.setExternalId(null);
					return user;
					
				} 
				throw new ObjectNotFoundException("User " + accountName + " doesn't exist.");

			}
		}

	}

	public String getUserPasswordByAccountName(String userName) throws NdexException,
		IllegalArgumentException, ObjectNotFoundException, SQLException {

		String queryStr = "SELECT password FROM " + NdexClasses.User + " where " + NdexClasses.User_userName + " = ? and is_deleted=false";

		try (PreparedStatement st = db.prepareStatement(queryStr))  {
			st.setString(1, userName);

			try (ResultSet rs = st.executeQuery() ) {
				if (rs.next()) {
					// populate the user object;
					return rs.getString(1);
		
			
				} 
				throw new ObjectNotFoundException("User " + userName + " doesn't exist.");
			}
		}

	}
	/**************************************************************************
	 * Find users
	 * 
	 * @param id
	 *            UUID for User
	 * @param skip
	 *            amount of blocks to skip
	 * @param top
	 *            block size
	 * @throws NdexException
	 *             Attempting to query the database
	 * @throws IOException 
	 * @throws SolrServerException 
	 * @throws SQLException 
	 * @returns User object, from the NDEx Object Model
	 **************************************************************************/
	public SolrSearchResult<User> findUsers(SimpleQuery simpleQuery, int skipBlock, int top)
			throws IllegalArgumentException, NdexException, SolrServerException, IOException, SQLException {
		Preconditions.checkArgument(simpleQuery != null,
				"Search parameters are required");

		    if ( simpleQuery.getSearchString().length()==0)
		    	simpleQuery.setSearchString("*:*");
			UserIndexManager indexManager = new UserIndexManager();
			SolrDocumentList l = indexManager.searchUsers(simpleQuery.getSearchString(), top, skipBlock*top);
			
			List<User> results = new ArrayList<>(l.size());
			for (SolrDocument d : l) {
				results.add(getUserById(UUID.fromString((String)d.get(UserIndexManager.UUID)), true));
			}
			return new SolrSearchResult<> (l.getNumFound(),l.getStart(), results);

			//return results;
			
	}

	/**************************************************************************
	 * Change given user's password, if password is null, generate a new one for this user.
	 * 
	 * @param accountName
	 *            accountName for the User
	 * @throws NdexException
	 *             Attempting to query the database
	 * @throws IllegalArgumentException
	 *             accountName is required
	 * @throws ObjectNotFoundException
	 *             user with account name does not exist
	 * @throws SQLException 
	 * @throws NoSuchAlgorithmException 
	 * @returns response
	 **************************************************************************/
	public String setNewPassword(String accountName, String password)
			throws IllegalArgumentException, ObjectNotFoundException,
			NdexException, SQLException, NoSuchAlgorithmException {

		Preconditions.checkArgument(!Strings.isNullOrEmpty(accountName),
				"An accountName is required");
		
		String newPassword = password;
		if ( password == null) { 
			newPassword = Security.generatePassword();
		} 
		
		newPassword = newPassword.trim();
		if (newPassword.startsWith("\"") && newPassword.endsWith("\"") )
			newPassword = newPassword.substring(1, newPassword.length() - 2);

		String updateStr = "update " + NdexClasses.User + " set password = ? where user_name = ? and is_deleted = false";
		try (PreparedStatement st = db.prepareStatement(updateStr))  {
			st.setString(1, Security.hashText(newPassword));
			st.setString(2, accountName);
			st.executeUpdate();
		} 
		return newPassword;		
	}

	


	/**************************************************************************
	 * Update a user
	 * 
	 * @param updatedUser
	 *            User with new information
	 * @param id
	 *            UUID for user
	 * @throws NdexException
	 *             Attempting to access the database
	 * @throws IllegalArgumentException
	 *             new password and user id are required
	 * @throws ObjectNotFoundException
	 *             user does not exist
	 * @return User object
	 * @throws SQLException 
	 * @throws JsonProcessingException 
	 **************************************************************************/
	public User updateUser(User updatedUser, UUID id)
			throws IllegalArgumentException, NdexException,
			ObjectNotFoundException, SQLException, JsonProcessingException {

		Preconditions.checkArgument(id != null, "A user id is required");
		Preconditions.checkArgument(updatedUser != null,
				"An updated user is required");

		String updateStr = " update " + NdexClasses.User + " set \"" + NdexClasses.ExternalObj_mTime + "\"= localtimestamp, " + 
			     NdexClasses.Account_description  + " =?, " +
				 NdexClasses.Account_imageURL     + " =?, " +
			     NdexClasses.Account_websiteURL   + " =?, " +
				 NdexClasses.Account_otherAttributes  + " =? ::jsonb, " +
			     NdexClasses.User_firstName        + " =?, " +
			     NdexClasses.User_lastName        + " =?, " +
			     NdexClasses.User_displayName      + " =?, " +
			     NdexClasses.User_emailAddress     + " =?, " +
			     NdexClasses.User_isIndividual     + " =? where \"UUID\" = \'" + id.toString() + "\' :: uuid and is_deleted = false";
		
		try (PreparedStatement st = db.prepareStatement(updateStr) ) {
			st.setString ( 1, updatedUser.getDescription());
			st.setString(2, updatedUser.getImage());
			st.setString(3, updatedUser.getWebsite());
			
			
			if ( updatedUser.getProperties()!=null && updatedUser.getProperties().size() >0 ) {
				ObjectMapper mapper = new ObjectMapper();
		        String s = mapper.writeValueAsString( updatedUser.getProperties());
				st.setString(4, s);
			} else {
				st.setString(4, null);
			}
			
			st.setString(5, updatedUser.getFirstName());
			st.setString(6, updatedUser.getLastName());
			st.setString(7, updatedUser.getDisplayName());
			st.setString(8, updatedUser.getEmailAddress());
			st.setBoolean(9, updatedUser.getIsIndividual());
			
			
			int rowsInserted = st.executeUpdate();
			if ( rowsInserted != 1)
				throw new NdexException ( "Failed to save user " + updatedUser.getUserName() + " to database when update.");
		}
			return updatedUser;		

	}

	/**************************************************************************
	 * getUserNetworkMemberships
	 * 
	 * @param userId
	 *            UUID for associated user
	 * @param permission
	 *            Type of memberships to retrieve, ADMIN, WRITE, or READ
	 * @param skipBlocks
	 *            amount of blocks to skip
	 * @param blockSize
	 *            The size of blocks to be skipped and retrieved
	 * @throws NdexException
	 *             Invalid parameters or an error occurred while accessing the
	 *             database
	 * @throws ObjectNotFoundException
	 *             Invalid userId
	 * @throws SQLException 
	 * @throws IOException 
	 * @throws IllegalArgumentException 
	 * @throws JsonMappingException 
	 * @throws JsonParseException 
	 **************************************************************************/

	public List<Membership> getUserNetworkMemberships(UUID userId,
			Permissions permission, int skipBlocks, int blockSize, boolean inclusive)
			throws ObjectNotFoundException, NdexException, SQLException, JsonParseException, JsonMappingException, IllegalArgumentException, IOException {

		Preconditions.checkArgument(!Strings.isNullOrEmpty(userId.toString()),
				"A user UUID is required");
		
		String queryStr = "select \"UUID\" as network_id, 'ADMIN' :: ndex_permission_type as permission_type " + 
						"from network n where owneruuid = '" + userId.toString() + "' :: uuid ";
		
		if ( permission == Permissions.READ || permission == Permissions.WRITE) {
			
			String permissionClause =  (inclusive? " >= '" : " = '" ) + permission + "' ";
			queryStr = " select a.network_id, max(a.permission_type) as permission_type from (" + (inclusive ? (queryStr + " union ") : "" ) +
					" select un.network_id, un.permission_type " + 
					"from user_network_membership un where un.user_id = '"+ userId.toString() + "' :: uuid and un.permission_type " + permissionClause +
					" union select gn.network_id, gn.permission_type from ndex_group_user ug, group_network_membership gn " + 
					" where ug.group_id = gn.group_id and ug.user_id = '" + userId + "' :: uuid and gn.permission_type " + permissionClause +" ) a group by a.network_id ";
					
		}  else if ( permission == null || permission !=Permissions.ADMIN) {
			throw new IllegalArgumentException("Valid permissions required.");
		}
		
		queryStr = "select b.network_id, b.permission_type, n2.name from (" + queryStr + ") b, network n2 where n2.\"UUID\"= b.network_id and n2.is_deleted =false";
		
	/*	if ( loggedInUserId == null) {
			queryStr =  queryStr + " and n2.visibility='PUBLIC'";
		} else if ( !loggedInUserId.equals(userId)) {
			queryStr = " and (n2.visibility='PUBLIC' or " +
					" exists ( select 1 from network n1 where n1.owneruuid = '" + loggedInUserId + "' :: uuid limit 1)	or " + 
					" exists ( select 1 from user_network_membership un1 where un1.network_id = n2.\"UUID\" and un1.user_id = '" + loggedInUserId + "' ::uuid limit 1) or " +
					" exists ( select 1 from group_network_membership gn1, ndex_group_user gu where gn1.group_id = gu.group_id and gn1.network_id = n2.\"UUID\" and gu.user_id = '" +
					loggedInUserId + "' ::uuid limit 1) )" ;
		} */
		
		if ( skipBlocks>=0 && blockSize>0) {
			queryStr += " limit " + blockSize + " offset " + skipBlocks * blockSize;
		}

		List<Membership> memberships = new ArrayList<>();

		try (PreparedStatement st = db.prepareStatement(queryStr))  {		
			try (ResultSet rs = st.executeQuery() ) {
				User user = getUserById(userId, true );
				while (rs.next()) {
					// populate the user object;
					Membership membership = new Membership();
					membership.setMembershipType(MembershipType.NETWORK);
					membership.setMemberAccountName(user.getUserName());
					membership.setMemberUUID(userId);
					String pp = rs.getString(2);
					membership.setPermissions(Permissions.valueOf(pp));
					membership.setResourceName(rs.getString(3));
					membership.setResourceUUID(UUID.fromString( rs.getString(1)));

					memberships.add(membership);
										
				} 

			}
		}
		
		return memberships;
	}
	
	
	public Map<String,String> getUserNetworkPermissionMap(UUID userId,
			Permissions permission, int skipBlocks, int blockSize, boolean inclusive, boolean directOnly)
			throws SQLException {
	
		String queryStr = "select \"UUID\" as network_id, 'ADMIN' :: ndex_permission_type as permission_type " + 
						"from network n where owneruuid = '" + userId.toString() + "' :: uuid ";
		
		if ( permission == Permissions.READ || permission == Permissions.WRITE) {
			
			String permissionClause =  (inclusive? " >= '" : " = '" ) + permission + "' ";
			queryStr = " select a.network_id, max(a.permission_type) as permission_type from (" + (inclusive ? (queryStr + " union ") : "" ) +
					" select un.network_id, un.permission_type " + 
					"from user_network_membership un where un.user_id = '"+ userId.toString() + "' :: uuid and un.permission_type " + permissionClause +
					( directOnly ? "" :
					(" union select gn.network_id, gn.permission_type from ndex_group_user ug, group_network_membership gn " + 
					" where ug.group_id = gn.group_id and ug.user_id = '" + userId + "' :: uuid and gn.permission_type " + permissionClause) ) +" ) a group by a.network_id ";
					
		}  else if ( permission == null || permission !=Permissions.ADMIN) {
			throw new IllegalArgumentException("Valid permissions required.");
		}
		
		if ( skipBlocks>=0 && blockSize>0) {
			queryStr += " limit " + blockSize + " offset " + skipBlocks * blockSize;
		}

		Map<String,String> result = new TreeMap<>();

		try (PreparedStatement st = db.prepareStatement(queryStr))  {		
			try (ResultSet rs = st.executeQuery() ) {
				while (rs.next()) {
					result.put(rs.getObject(1).toString(), rs.getString(2));
				} 
			}
		}
		
		return result;
	}


	/**************************************************************************
	 * getUsergroupMemberships
	 * 
	 * @param userId
	 *            UUID for associated user
	 * @param permission
	 *            Type of memberships to retrieve, ADMIN, WRITE, or READ
	 * @param skipBlocks
	 *            amount of blocks to skip
	 * @param blockSize
	 *            The size of blocks to be skipped and retrieved
	 * @throws NdexException
	 *             Invalid parameters or an error occurred while accessing the
	 *             database
	 * @throws ObjectNotFoundException
	 *             Invalid userId
	 * @throws IOException 
	 * @throws SQLException 
	 * @throws IllegalArgumentException 
	 * @throws JsonMappingException 
	 * @throws JsonParseException 
	 **************************************************************************/

	public List<Membership> getUserGroupMemberships(UUID userId,
			Permissions permission, int skipBlocks, int blockSize, boolean inclusive)
			throws ObjectNotFoundException, NdexException, JsonParseException, JsonMappingException, IllegalArgumentException, SQLException, IOException {

		String queryStr = "select gu.group_id, g.group_name, gu.is_admin from  ndex_group_user gu, ndex_group g " + 
		     " where gu.group_id = g.\"UUID\" and gu.user_id = ? ";
		
		if ( permission == Permissions.GROUPADMIN) {
			queryStr += " and gu.is_admin";
		} else if ( permission == null || permission != Permissions.MEMBER) 
			throw new NdexException ("Valid permissions required in getUserGroupMembership function.");
		else {
			if ( !inclusive)
				queryStr += " and gu.is_admin = false";
		}
			
		if ( skipBlocks>=0 && blockSize>0) {
			queryStr += " limit " + blockSize + " offset " + skipBlocks * blockSize;
		}
		
		User user = getUserById(userId, true);
		List<Membership> memberships = new ArrayList<>();

		try (PreparedStatement st = db.prepareStatement(queryStr))  {
			st.setObject(1, userId);
		
			try (ResultSet rs = st.executeQuery() ) {
				while (rs.next()) {
					Membership membership = new Membership();
					membership.setMembershipType(MembershipType.GROUP);
					membership.setMemberAccountName( user.getUserName());
					membership.setMemberUUID(userId);
					membership.setPermissions(rs.getBoolean(3)? Permissions.GROUPADMIN : Permissions.MEMBER);
					membership.setResourceName(rs.getString(2));
					membership.setResourceUUID((java.util.UUID)rs.getObject(1));

					memberships.add(membership);
					
				} 
			}
		}

		return memberships;
	}


	public Map<String,String> getUserGroupMembershipMap(UUID userId,
			Permissions permission, int skipBlocks, int blockSize)
			throws ObjectNotFoundException, NdexException, IllegalArgumentException, SQLException {

		Map <String,String> result = new TreeMap<>();
		String queryStr = "select gu.group_id, gu.is_admin from ndex_group_user gu where gu.user_id = ? ";
		
		if ( permission !=null) {
			if ( permission == Permissions.GROUPADMIN) {
				queryStr += " and gu.is_admin";
			} else if ( permission != Permissions.MEMBER) 
				throw new NdexException ("Valid permissions required in getUserGroupMembership function.");
			else {
				queryStr += " and not gu.is_admin ";
			}
		}
			
		if ( skipBlocks>=0 && blockSize>0) {
			queryStr += " limit " + blockSize + " offset " + skipBlocks * blockSize;
		}
		
		try (PreparedStatement st = db.prepareStatement(queryStr))  {
			st.setObject(1, userId);
		
			try (ResultSet rs = st.executeQuery() ) {
				while (rs.next()) {
					result.put(rs.getObject(1).toString(), 
							(rs.getBoolean(2)? Permissions.GROUPADMIN.toString() : Permissions.MEMBER.toString()));					
				} 
			}
		}

		return result;
	}
	
	
	
	/**************************************************************************
	 * getMembership
	 * 
	 * @param account
	 *            UUID for user
	 * @param resource
	 *            UUID for resource
	 * @throws NdexException
	 *             Invalid parameters or an error occurred while accessing the
	 *             database
	 * @throws ObjectNotFoundException
	 *             Invalid userId
	 * @throws SQLException 
	 **************************************************************************/

	public Permissions getLoggedInUserPermissionOnNetwork(UUID userId, UUID networkId, boolean directOnly)
			throws IllegalArgumentException, ObjectNotFoundException,
			NdexException, SQLException {

		Preconditions.checkArgument(userId != null, "User UUID required");
		Preconditions.checkArgument(networkId != null, "Network UUID required");

		String queryStr = "select 1 from network where owneruuid = ? and \"UUID\" = ?";
			
		try (PreparedStatement st = db.prepareStatement(queryStr))  {
			st.setObject(1, userId);
			st.setObject(2, networkId);
			try (ResultSet rs = st.executeQuery() ) {
				if (rs.next()) {
					return Permissions.ADMIN;				
				} 
			}
		}

		queryStr = "select permission_type from  user_network_membership where user_id = ? and network_id = ?";
		Permissions result = null;
		
		try (PreparedStatement st = db.prepareStatement(queryStr))  {
			st.setObject(1, userId);
			st.setObject(2, networkId);
			try (ResultSet rs = st.executeQuery() ) {
				while (rs.next()) {
					result = Permissions.valueOf(rs.getString(1))	;			
				} 
			}
		}
		
		if ( result == Permissions.WRITE)
			return result;
		
		if ( !directOnly) {
			queryStr = "select gn.permission_type from ndex_group_user gu, group_network_membership gn where "+
		     " gu.group_id = gn.group_id and gu.user_id = ? and gn.network_id = ?";
			try (PreparedStatement st = db.prepareStatement(queryStr))  {
				st.setObject(1, userId);
				st.setObject(2, networkId);
				try (ResultSet rs = st.executeQuery() ) {
					while (rs.next()) {
						Permissions p = Permissions.valueOf(rs.getString(1));
						if ( p == Permissions.WRITE)
							return p;					
						result = p	;			
					} 
				}
			}
		}

		return result;
	}

	public Permissions getUserMembershipTypeOnGroup(UUID userId, UUID groupId)
			throws IllegalArgumentException, SQLException {

		Preconditions.checkArgument(userId != null, "User UUID required");
		Preconditions.checkArgument(groupId != null, "Group UUID required");

		String queryStr = "select is_admin from ndex_group_user where user_id = ? and group_id = ?";
		
		try (PreparedStatement st = db.prepareStatement(queryStr))  {
			st.setObject(1, userId);
			st.setObject(2, groupId);
			try (ResultSet rs = st.executeQuery() ) {
				if (rs.next()) {
					if ( rs.getBoolean(1)) 
						return Permissions.GROUPADMIN;		
					
					return Permissions.MEMBER;
				} 
			}
		}
		
		return null;
		
	}

	
	public void deleteUserById(UUID id) throws NdexException, ObjectNotFoundException, SQLException {
		Preconditions.checkArgument(null != id, "UUID required");

		try (PreparedStatement st = db.prepareStatement("select 1 from network where owneruuid = ? and is_deleted = false limit 1 ")) {
			st.setObject(1, id);
			try (ResultSet rs = st.executeQuery()) {
				if ( rs.next()) {
					throw new NdexException("Cannot orphan networks");
				}	
			}
		}	
		
		try (PreparedStatement st = db.prepareStatement("select 1 from ndex_group_user where user_id = ? and is_admin limit 1")) {
			st.setObject(1, id);
			try (ResultSet rs = st.executeQuery()) {
				if ( rs.next()) {
					throw new NdexException("This user is still an admin of group.");
				}	
			}
		}	
	    
		String[] sqlCmds = {
				"insert into user_network_membership_arc (user_id, network_id, permission_type) " + 
						" select user_id, network_id, permission_type from user_network_membership where user_id = ?",
				"delete from user_network_membership where user_id = ?",
				"insert into ndex_group_user_arc (user_id, group_id, is_admin) " + 
						" select user_id, group_id, is_admin from ndex_group_user where user_id = ?",
				"delete from ndex_group_user where user_id = ?",
				"update task set is_deleted = true where owneruuid = ?",
				"update request set is_deleted = true where owner_id =? ",
				"update ndex_user set is_deleted = true where \"UUID\" = ? and not is_deleted"
			};

		for (String cmd : sqlCmds) {
			try (PreparedStatement st = db.prepareStatement(cmd) ) {
				st.setObject(1, id);
				st.executeUpdate();
			}		
		}
		

	}

}
