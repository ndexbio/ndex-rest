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
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;
import java.util.UUID;

import org.ndexbio.common.NdexClasses;
import org.ndexbio.common.util.NdexUUIDFactory;
import org.ndexbio.model.exceptions.*;
import org.ndexbio.model.object.Group;
import org.ndexbio.model.object.Permissions;
import org.ndexbio.model.object.Request;
import org.ndexbio.model.object.RequestType;
import org.ndexbio.model.object.ResponseType;
import org.ndexbio.model.object.User;

import java.util.logging.Logger;

import com.fasterxml.jackson.core.JsonParseException;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.google.common.base.Preconditions;
import com.google.common.base.Strings;

public class RequestDAO extends NdexDBDAO  {
	private static final Logger logger = Logger.getLogger(RequestDAO.class.getName());
	
	/**************************************************************************
	    * RequestDAO
	    * 
	    * @param db
	    *           Database instance from the Connection pool, should be opened
	    * @param graph
	    * 			OrientBaseGraph layer on top of db instance. 
	 * @throws SQLException 
	    **************************************************************************/
	public RequestDAO() throws SQLException {
		super();
		

	}
	/**************************************************************************
	    * Creates a request. 
	    * 
	    * @param newRequest
	    *            The request to create.
	    * @param account
	    * 			Logged in user
	    * @throws IllegalArgumentException
	    *            Bad input.
	    * @throws DuplicateObjectException
	    *            The request is a duplicate.
	    * @throws NdexException
	    *            Duplicate requests or failed to create the request in the
	    *            database.
	    * @return The newly created request.

	    **************************************************************************/
	public Request createRequest(Request newRequest, User account)
			throws IllegalArgumentException, DuplicateObjectException,
			NdexException, SQLException, JsonParseException, JsonMappingException, IOException {
		
		if( newRequest.getRequestType() == RequestType.JoinGroup) {
			try (GroupDAO dao = new GroupDAO()) {				
				 dao.getGroupById(newRequest.getDestinationUUID());
			}
		} 
		
		//TODO: check if the same request exists.
			
		String insertStr = "insert into request (\"UUID\", creation_time, modification_time, is_deleted, sourceuuid,"
				+ "destinationuuid,requestmessage,requestpermission, owner_id, request_type, response)"
				+ "values ( ?,?,?,false,?, ?,?,?,?,?,?)";
		
		Timestamp currentTime = new Timestamp(Calendar.getInstance().getTimeInMillis());
		newRequest.setExternalId(NdexUUIDFactory.INSTANCE.createNewNDExUUID());
		newRequest.setCreationTime(currentTime);
		newRequest.setModificationTime(currentTime);
		
		
		try (PreparedStatement pst = db.prepareStatement(insertStr)) {
			pst.setObject(1, newRequest.getExternalId());
			pst.setTimestamp(2, newRequest.getCreationTime());
			pst.setTimestamp(3, newRequest.getModificationTime());
			pst.setObject(4, newRequest.getSourceUUID());
			pst.setObject(5, newRequest.getDestinationUUID());
			pst.setObject(6, newRequest.getMessage());
			pst.setString(7, newRequest.getPermission().toString());	
			pst.setObject(8, account.getExternalId());
			pst.setString(9, newRequest.getRequestType().name());
			pst.setString(10, newRequest.getResponse().name());
			pst.executeUpdate();
		}
		
		return newRequest;
	}

	/**************************************************************************
	    * Deletes a request.
	    * 
	    * @param requestId
	    *            The request ID.
	    * @param account
	    * 			User object

	 * @throws SQLException 
	    **************************************************************************/
	public void deleteRequest(UUID requestId, UUID ownerId)
			throws IllegalArgumentException, ObjectNotFoundException,
			NdexException, SQLException {
		Preconditions.checkArgument( requestId != null,
				"A request id is required");
		Preconditions.checkArgument( ownerId != null,
				"A user must be logged in");
		
		String updateStr = "update request set is_deleted = true where \"UUID\" = ? and owner_id = ?";
		try ( PreparedStatement pst = db.prepareStatement(updateStr)) {
			pst.setObject(1, requestId);
			pst.setObject(2, ownerId);
			
			int cnt = pst.executeUpdate();
			if ( cnt != 1 ) {
				throw new ObjectNotFoundException ("Request " + requestId + " not found for this user.");
			}
		}
		
	}
	
	public void deleteMembershipRequest(UUID requestId, UUID ownerId)
			throws IllegalArgumentException, ObjectNotFoundException,
			NdexException, SQLException {
		Preconditions.checkArgument( requestId != null,
				"A request id is required");
		Preconditions.checkArgument( ownerId != null,
				"A user must be logged in");
		
		String updateStr = "update request set is_deleted = true where \"UUID\" = ? and owner_id = ? and request_type='" +
					RequestType.JoinGroup.name() + "'";
		try ( PreparedStatement pst = db.prepareStatement(updateStr)) {
			pst.setObject(1, requestId);
			pst.setObject(2, ownerId);
			
			int cnt = pst.executeUpdate();
			if ( cnt != 1 ) {
				throw new ObjectNotFoundException ("Request " + requestId + " not found for this user.");
			}
		}
		
	}
	
	public void deletePermissionRequest(UUID requestId, UUID ownerId)
			throws IllegalArgumentException, ObjectNotFoundException,
			NdexException, SQLException {
		Preconditions.checkArgument( requestId != null,
				"A request id is required");
		Preconditions.checkArgument( ownerId != null,
				"A user must be logged in");
		
		String updateStr = "update request set is_deleted = true where \"UUID\" = ? and owner_id = ? and is_deleted= false"
			+ " and request_type <> '" + RequestType.JoinGroup.name() + "'";
		
		try ( PreparedStatement pst = db.prepareStatement(updateStr)) {
			pst.setObject(1, requestId);
			pst.setObject(2, ownerId);
			
			int cnt = pst.executeUpdate();
			if ( cnt != 1 ) {
				throw new ObjectNotFoundException ("Request " + requestId + " not found for this user.");
			}
		}
		
	}

	/**************************************************************************
	    * Gets a request by ID.
	    * 
	    * @param requestId
	    *           The request ID.
	    * @param account
	    * 			User object     

	    * @return The request.
	    **************************************************************************/
	public Request getRequest(UUID requestId, User account)
			throws IllegalArgumentException, NdexException, SQLException {
		Preconditions.checkArgument(requestId != null,
				"A request id is required");
		Preconditions.checkArgument(account != null,
				"Must be logged in to get a request");
		
		
		// TODO check if source UUID and account UUID match up
		Request r = getRequestById(requestId);
		
		return r;
		
	}
	
	
	private Request getRequestById (UUID requestId) throws SQLException, ObjectNotFoundException {
		String sqlStr = "select r.*, " +
				"case when r.request_type ='JoinGroup' then (select g.group_name from ndex_group g where g.\"UUID\" = r.destinationuuid) " +
				"when  r.request_type ='UserNetworkAccess' or r.request_type = 'GroupNetworkAccess' then " +
				"(select n.name from network n where n.\"UUID\"= r.destinationuuid) " +
				" end as destination_name, " +
				" case when r.request_type ='JoinGroup' or r.request_type ='UserNetworkAccess' " +
				" then (select u.user_name from ndex_user u where u.\"UUID\" = r.sourceuuid) " +
				" when r.request_type = 'GroupNetworkAccess' then " +
				" (select g.group_name from ndex_group g where g.\"UUID\"= r.sourceuuid) " +
				" end as source_name "		
				+ "from request r where r.\"UUID\" = ? and r.is_deleted=false";
		try (PreparedStatement pst = db.prepareStatement(sqlStr)) {
			pst.setObject(1, requestId);
			try (ResultSet rs = pst.executeQuery()) {
				if ( rs.next()) {
					Request r = getRequestFromResultSet(rs);
					return r;
				}
				
				throw new ObjectNotFoundException("Request " + requestId + " not found in Ndex db.");
			}
		}
	}
	
	private static Request getRequestFromResultSet(ResultSet rs) throws SQLException {
		if (rs == null) return null;
		
		Request r = new Request();
		Helper.populateExternalObjectFromResultSet(r, rs);
		r.setSourceUUID( (UUID) rs.getObject("sourceuuid") ); 
		
		r.setDestinationUUID( (UUID)rs.getObject("destinationuuid")  );
		r.setMessage(rs.getString("requestmessage"));
		r.setPermission( Permissions.valueOf( rs.getString("requestpermission") ) );
		r.setResponder(rs.getString("responder"));
		String responseStr = rs.getString("response") ;
		if ( responseStr != null)
			r.setResponse( ResponseType.valueOf( responseStr));
		r.setResponseMessage(rs.getString("responseMessage"));
		r.setResponseTime(rs.getTimestamp("responsetime"));
		
		String s = rs.getString("request_type");
		if ( s!=null) 
		   r.setRequestType(RequestType.valueOf(s));
		
		r.setSourceName(rs.getString("source_name"));
		r.setDestinationName(rs.getString("destination_name"));

		return r;
	}
	
	public List<Request> getPendingRequestByUserId(UUID userId, int skipBlocks,
			int blockSize) throws SQLException {

		final List<Request> requests = new ArrayList<>();

		String queryStr = "select a.*, " +
				"case when r.request_type ='JoinGroup' then (select g.group_name from ndex_group g where g.\"UUID\" = r.destinationuuid) " +
				 "when  a.request_type ='UserNetworkAccess' or a.request_type = 'GroupNetworkAccess' then " +
				"(select n.name from network n where n.\"UUID\"= a.destinationuuid) " +
				" end as destination_name, " +
				" case when a.request_type ='JoinGroup' or a.request_type ='UserNetworkAccess' " +
				" then (select u.user_name from ndex_user u where u.\"UUID\" = a.sourceuuid) " +
				" when a.request_type = 'GroupNetworkAccess' then " +
				" (select g.group_name from ndex_group g where g.\"UUID\"= a.sourceuuid) " +
				" end as source_name "		
				+ "from ( select r.* from request r, network n "
				+ "where n.\"UUID\" = r.destinationuuid and n.owneruuid = ? and "
				+ "(r.response is null or r.response = '"+ ResponseType.PENDING.name()+ "') and r.is_deleted =false "
				+ "union select r2.* from request r2, ndex_group_user gu "
				+ "where gu.group_id = r2.destinationuuid and gu.user_id = ? and gu.is_admin and "
				+ "( r2.response is null or r2.response = '"+ ResponseType.PENDING.name()+ "') and r2.is_deleted=false) a ";
						
		if ( skipBlocks>=0 && blockSize>0) {
			queryStr += " limit " + blockSize + " offset " + skipBlocks * blockSize;
		}
		
		try ( PreparedStatement pst = db.prepareStatement(queryStr)) {
			pst.setObject(1, userId);
			pst.setObject(2, userId);
			try ( ResultSet rs = pst.executeQuery()) {
				while ( rs.next()) {
					Request r = getRequestFromResultSet(rs);
					requests.add(r);
				}
			}
		}
		
		return requests;
	
	}
	
	public List<Request> getPendingNetworkAccessRequestByUserId(UUID userId, int skipBlocks,
			int blockSize) throws SQLException {

		final List<Request> requests = new ArrayList<>();

		String queryStr = " select r.*, " +
				"case when r.request_type ='JoinGroup' then (select g.group_name from ndex_group g where g.\"UUID\" = r.destinationuuid) " +
				"when  r.request_type ='UserNetworkAccess' or r.request_type = 'GroupNetworkAccess' then " +
				"(select n.name from network n where n.\"UUID\"= r.destinationuuid) " +
				" end as destination_name, " +
				" case when r.request_type ='JoinGroup' or r.request_type ='UserNetworkAccess' " +
				" then (select u.user_name from ndex_user u where u.\"UUID\" = r.sourceuuid) " +
				" when r.request_type = 'GroupNetworkAccess' then " +
				" (select g.group_name from ndex_group g where g.\"UUID\"= r.sourceuuid) " +
				" end as source_name "		
				+ "from request r, network n "
				+ "where r.request_type <> 'JoinGroup' and n.\"UUID\" = r.destinationuuid and n.owneruuid = ? and "
				+ "(r.response is null or r.response = '"+ ResponseType.PENDING.name()+ "') and r.is_deleted =false ";
						
		if ( skipBlocks>=0 && blockSize>0) {
			queryStr += " limit " + blockSize + " offset " + skipBlocks * blockSize;
		}
		
		try ( PreparedStatement pst = db.prepareStatement(queryStr)) {
			pst.setObject(1, userId);
			try ( ResultSet rs = pst.executeQuery()) {
				while ( rs.next()) {
					Request r = getRequestFromResultSet(rs);
					requests.add(r);
				}
			}
		}
		
		return requests;
	
	}
	
	
	public List<Request> getPendingGroupMembershipRequestByUserId(UUID userId, int skipBlocks,
			int blockSize) throws SQLException {

		final List<Request> requests = new ArrayList<>();

		String queryStr = "select r.*, " +
				"case when r.request_type ='JoinGroup' then (select g.group_name from ndex_group g where g.\"UUID\" = r.destinationuuid) " +
				"when  r.request_type ='UserNetworkAccess' or r.request_type = 'GroupNetworkAccess' then " +
				"(select n.name from network n where n.\"UUID\"= r.destinationuuid) " +
				" end as destination_name, " +
				" case when r.request_type ='JoinGroup' or r.request_type ='UserNetworkAccess' " +
				" then (select u.user_name from ndex_user u where u.\"UUID\" = r.sourceuuid) " +
				" when r.request_type = 'GroupNetworkAccess' then " +
				" (select g.group_name from ndex_group g where g.\"UUID\"= r.sourceuuid) " +
				" end as source_name "		
				+ "from request r, ndex_group_user gu "
				+ "where gu.group_id = r.destinationuuid and gu.user_id = ? and gu.is_admin and "
				+ "( r.response is null or r.response = '"+ ResponseType.PENDING.name()+ "') and r.is_deleted=false";
						
		if ( skipBlocks>=0 && blockSize>0) {
			queryStr += " limit " + blockSize + " offset " + skipBlocks * blockSize;
		}
		
		try ( PreparedStatement pst = db.prepareStatement(queryStr)) {
			pst.setObject(1, userId);
			try ( ResultSet rs = pst.executeQuery()) {
				while ( rs.next()) {
					Request r = getRequestFromResultSet(rs);
					requests.add(r);
				}
			}
		}
		
		return requests;
	
	}
	
	
	/**************************************************************************
	 * getSentRequest
	 * 
	 * @param account
	 *            User object
	 * @param skipBlocks
	 *            amount of blocks to skip
	 * @param blockSize
	 *            The size of blocks to be skipped and retrieved

	 **************************************************************************/

	// if request type is null, return all types of request sent by this user.

	public List<Request> getSentRequestByUserId(UUID userId, RequestType requestType, int skipBlocks,
			int blockSize) throws SQLException {

		final List<Request> requests = new ArrayList<>();

		String queryStr = "select r.*, " +
				"case when r.request_type ='JoinGroup' then (select g.group_name from ndex_group g where g.\"UUID\" = r.destinationuuid) " +
				"when  r.request_type ='UserNetworkAccess' or r.request_type = 'GroupNetworkAccess' then " +
				"(select n.name from network n where n.\"UUID\"= r.destinationuuid) " +
				" end as destination_name, " +
				" case when r.request_type ='JoinGroup' or r.request_type ='UserNetworkAccess' " +
				" then (select u.user_name from ndex_user u where u.\"UUID\" = r.sourceuuid) " +
				" when r.request_type = 'GroupNetworkAccess' then " +
				" (select g.group_name from ndex_group g where g.\"UUID\"= r.sourceuuid) " +
				" end as source_name "		
				+ " from request r where r.owner_id = ? and r.is_deleted = false ";
		if ( requestType !=null)
			queryStr += " and request_type = '" + requestType.name() + "'";
		
		queryStr += " order by creation_time desc";
		
		if ( skipBlocks>=0 && blockSize>0) {
			queryStr += " limit " + blockSize + " offset " + skipBlocks * blockSize;
		}
		
		try ( PreparedStatement pst = db.prepareStatement(queryStr)) {
			pst.setObject(1, userId);
			try ( ResultSet rs = pst.executeQuery()) {
				while ( rs.next()) {
					Request r = getRequestFromResultSet(rs);
					requests.add(r);
				}
			}
		}
		
		return requests;		
		
	}
	
	
	public List<Request> getGroupPermissionRequest(UUID groupId, UUID networkId, Permissions permission)
			throws SQLException {

		final List<Request> requests = new ArrayList<>();
		
		String queryStr = "select r.*, " +
				"case when r.request_type ='JoinGroup' then (select g.group_name from ndex_group g where g.\"UUID\" = r.destinationuuid) " +
				"when  r.request_type ='UserNetworkAccess' or r.request_type = 'GroupNetworkAccess' then " +
				"(select n.name from network n where n.\"UUID\"= r.destinationuuid) " +
				" end as destination_name, " +
				" case when r.request_type ='JoinGroup' or r.request_type ='UserNetworkAccess' " +
				" then (select u.user_name from ndex_user u where u.\"UUID\" = r.sourceuuid) " +
				" when r.request_type = 'GroupNetworkAccess' then " +
				" (select g.group_name from ndex_group g where g.\"UUID\"= r.sourceuuid) " +
				" end as source_name "		
				+ "from request r where r.is_deleted = false and r.permission_type = 'GroupNetworkAccess' and r.sourceuuid = '"
				   + groupId.toString() + "' ";
		if ( networkId != null)
			queryStr += " and destinationuuid = '" + networkId.toString() + "' ";
		if ( permission != null)
			queryStr += " and requestpermission = '" + permission.name() + "'";
		
		try ( PreparedStatement pst = db.prepareStatement(queryStr)) {
			try ( ResultSet rs = pst.executeQuery()) {
				while ( rs.next()) {
					Request r = getRequestFromResultSet(rs);
					requests.add(r);
				}
			}
		}
		
		return requests;		
		
	}
	

	/**************************************************************************
	    * Updates a request. Should only be called by the users who are targets of this request.
	    * This function is used as a response as a request, so only the reponsepart will be updated.
	    * 
	    * @param requestId
	    * 			UUID for request
	    * @param updatedRequest
	    *            The updated request information.
	    * @param account
	    * 		user associated request
	    * @throws IllegalArgumentException
	    *            Bad input.
	    * @throws NdexException
	    *            Failed to update the request in the database.
	 * @throws SQLException 
	    **************************************************************************/
	public void updateRequest(UUID requestId, Request updatedRequest, User account)
			throws IllegalArgumentException, NdexException, SQLException {
		Preconditions.checkArgument(null != updatedRequest,
				"A Request object is required");
		Preconditions.checkArgument(updatedRequest.getResponse().equals(ResponseType.ACCEPTED)
				|| updatedRequest.getResponse().equals(ResponseType.DECLINED),
				"A proper response type is required");
		Preconditions.checkArgument(account != null,
				"Must be logged in to update a request");

					
		if (userIsRequestDestination(requestId,account.getExternalId())) {
			String sql = "update request set responder = ? , response = ?, responsemessage = ?, responsetime = localtimestamp "
					+ "where \"UUID\" = ? and is_deleted=false ";
			try ( PreparedStatement p = db.prepareStatement(sql)) {
				p.setString(1, account.getUserName() );
				p.setString(2, updatedRequest.getResponse().name());
				p.setString(3, updatedRequest.getResponseMessage());
				p.setObject(4, requestId);
				p.executeUpdate();
			}
			
			logger.info("Request has been updated. UUID : " + requestId.toString());
		
			
		} else {
			logger.severe("Unable to update request. Account is not a recipient of request.");
			throw new NdexException("Failed to update the request.");
		} 
	} 
	
	/**
	 * Returns true if the given user is the destination of the given request.
	 * @param requestId
	 * @param userId
	 * @return
	 * @throws SQLException 
	 */
	
	private boolean userIsRequestDestination (UUID requestId, UUID userId) throws SQLException {
		String sql = "select 1 from request r, network n where r.\"UUID\" = ? and "
				+ "n.\"UUID\" = r.destinationuuid and n.owneruuid = ? and r.is_deleted =false "
				+ "union select 1 from request r2, ndex_group_user gu "
				+ "where r2.\"UUID\" = ? and gu.group_id = r2.destinationuuid and gu.user_id = ? and gu.is_admin and r2.is_deleted=false";
		try (PreparedStatement p = db.prepareStatement(sql)) {
			p.setObject(1, requestId);
			p.setObject(2, userId);
			p.setObject(3, requestId);
			p.setObject(4, userId);
			try ( ResultSet rs = p.executeQuery()) {
					return rs.next();
			}
		}
	}
	
	

/*	
	private void checkForExistingRequest(UUID sourceUUID, UUID destinationUUID, Permissions permission) 
			throws DuplicateObjectException, NdexException {
		
		String sql = "select count(*) from request where destinationuuid = ? and  sourceuuid = ? " 
				+ "and requestpermission =? and is_deleted =false";
		try (PreparedStatement p = db.prepareStatement(sql)) {
			p.setObject(1, destinationUUID);
			p.setObject(2, sourceUUID);
			p.setString(3, permission.toString());
			try ( ResultSet rs = p.executeQuery()) {
					if ( rs.next()) {
						Integer i = rs.getInt(1);
						return i.intValue() > 0;
						return rs.getInt(1)
					}
			}
		}
		OrientVertex vUser = this.graph.getVertex(user);
		for(Vertex v : vUser.getVertices(Direction.OUT, "requests") ) {
			if( ((OrientVertex) v).getRecord().field("destinationUUID").equals( destination.field("UUID") )
					&& ((OrientVertex) v).getRecord().field("sourceUUID").equals( source.field("UUID") )
					&& ((OrientVertex) v).getRecord().field("requestPermission").equals( permission.name() ) ) 
				throw new DuplicateObjectException("Request has already been issued");
		}
		
		if(checkPermission(source.getIdentity(), destination.getIdentity(), Direction.OUT, 1, permission))
			throw new NdexException("Access has already been granted");
		
	} 
	*/
	
}
