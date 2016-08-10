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

import org.ndexbio.common.util.NdexUUIDFactory;
import org.ndexbio.model.exceptions.*;
import org.ndexbio.model.object.Permissions;
import org.ndexbio.model.object.Request;
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
	 * @throws SQLException 
	 * @throws IOException 
	 * @throws JsonMappingException 
	 * @throws JsonParseException 
	    **************************************************************************/
	public Request createRequest(Request newRequest, User account)
			throws IllegalArgumentException, DuplicateObjectException,
			NdexException, SQLException, JsonParseException, JsonMappingException, IOException {
		Preconditions.checkArgument(null != newRequest,
				"A Request parameter is required");
		Preconditions.checkArgument( newRequest.getSourceUUID() != null
				&& !Strings.isNullOrEmpty( newRequest.getSourceUUID().toString() ),
				"A source UUID is required");
		Preconditions.checkArgument( newRequest.getDestinationUUID() != null
				&& !Strings.isNullOrEmpty( newRequest.getDestinationUUID().toString() ),
				"A destination UUID is required");
		Preconditions.checkArgument( newRequest.getPermission() != null,
				"A permission is required");
		Preconditions.checkArgument(account != null,
				"Must be logged in to make a request");
		
		if ( newRequest.getSourceUUID() !=null && !newRequest.getSourceUUID().equals(account.getExternalId())) {
			throw new NdexException ("Creating request for other users are not allowed.");
		} 
		
		if ( newRequest.getPermission() == Permissions.GROUPADMIN || 
				newRequest.getPermission() == Permissions.MEMBER) {
			try (GroupDAO gdao = new GroupDAO ()) {
				try {
					 gdao.getGroupById(newRequest.getDestinationUUID());
				} catch ( ObjectNotFoundException e) {
					throw new NdexException ("Destination of group permission request has to be a group");
				}				
			}
		} 
				
		String insertStr = "insert into request (\"UUID\", creation_time, modification_time, is_deleted, sourceuuid,"
				+ "destinationuuid,requestmessage,requestpremission)"
				+ "values ( ?,?,?,false,?, ?,?,?,?,?,?)";
		
		Timestamp currentTime = new Timestamp(Calendar.getInstance().getTimeInMillis());
		newRequest.setExternalId(NdexUUIDFactory.INSTANCE.createNewNDExUUID());
		newRequest.setCreationTime(currentTime);
		newRequest.setModificationTime(currentTime);
		
		
		try (PreparedStatement pst = db.prepareStatement(insertStr)) {
			pst.setObject(1, account.getExternalId());
			pst.setTimestamp(2, newRequest.getCreationTime());
			pst.setTimestamp(3, newRequest.getModificationTime());
			pst.setObject(4, newRequest.getSourceUUID());
			pst.setObject(5, newRequest.getDestinationUUID());
			pst.setObject(6, newRequest.getMessage());
			pst.setString(7, newRequest.getPermission().toString());	
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
	    * @throws IllegalArgumentException
	    *            Bad input.
	    * @throws ObjectNotFoundException
	    *            The request doesn't exist.
	    * @throws NdexException
	    *            Failed to delete the request from the database.
	 * @throws SQLException 
	    **************************************************************************/
	public void deleteRequest(UUID requestId, User account)
			throws IllegalArgumentException, ObjectNotFoundException,
			NdexException, SQLException {
		Preconditions.checkArgument( requestId != null,
				"A request id is required");
		Preconditions.checkArgument( account != null,
				"A user must be logged in");
		
		String updateStr = "update request set is_deleted = true where \"UUID\" = ? and owneruuid = ?";
		try ( PreparedStatement pst = db.prepareStatement(updateStr)) {
			pst.setObject(1, requestId);
			pst.setObject(2, account.getExternalId());
			
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
	    * @throws IllegalArgumentException
	    *           Bad input.
	    * @throws NdexException
	    *           Failed to query the database.
	    * @return The request.
	 * @throws SQLException 
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
		String sqlStr = "select * from request where \"UUID\" = ? and is_deleted=false";
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
		r.setResponse( ResponseType.valueOf(  rs.getString("response") ));
		r.setResponseMessage(rs.getString("responseMessage"));
		r.setResponseTime(rs.getTimestamp("responsetime"));
		
		//TODO: need to populate these 2 fields as well.
		//	result.setSourceName((String) request.field(NdexClasses.Request_P_sourceName));
		//	result.setDestinationName((String) request.field("destinationName"));

		return r;
	}
	
	public List<Request> getPendingRequestByUserId(UUID userId, int skipBlocks,
			int blockSize) throws SQLException {

		final List<Request> requests = new ArrayList<>();

		String queryStr = "select * from request where destinationuuid = ? and is_deleted = false and response = ? order by creation_time desc";
		
		if ( skipBlocks>=0 && blockSize>0) {
			queryStr += " limit " + blockSize + " offset " + skipBlocks * blockSize;
		}
		
		try ( PreparedStatement pst = db.prepareStatement(queryStr)) {
			pst.setObject(1, userId);
			pst.setString(2, ResponseType.PENDING.toString());
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
	 * @throws NdexException
	 *             An error occurred while accessing the database
	 * @throws ObjectNotFoundException
	 *             Invalid userId
	 * @throws SQLException 
	 **************************************************************************/

	public List<Request> getSentRequestByUserId(UUID userId, int skipBlocks,
			int blockSize) throws ObjectNotFoundException, NdexException, SQLException {

		final List<Request> requests = new ArrayList<>();

		String queryStr = "select * from request where owneruuid = ? and is_deleted = false order by creation_time desc";
		
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
	    * Updates a request.
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
	    **************************************************************************/
/*	public void updateRequest(UUID requestId, Request updatedRequest, User account)
			throws IllegalArgumentException, NdexException {
		Preconditions.checkArgument(null != updatedRequest,
				"A Request object is required");
		Preconditions.checkArgument(updatedRequest.getResponse().equals(ResponseType.ACCEPTED)
				|| updatedRequest.getResponse().equals(ResponseType.DECLINED),
				"A proper response type is required");
		Preconditions.checkArgument(account != null,
				"Must be logged in to update a request");

		ODocument request = this.getRecordByUUID(requestId, NdexClasses.Request);
		ODocument responder = this.getRecordByUUID(account.getExternalId(), NdexClasses.User);

		try {
			
			OrientVertex vRequest = this.graph.getVertex(request);
			
			boolean canModify = false;
			for(Vertex v : vRequest.getVertices(Direction.OUT, "requests")) {
				if( ((OrientVertex) v).getIdentity().equals(responder.getIdentity()) )
					canModify = true;
			}
			
			if(canModify) {
				logger.info("User credentials match with request");
				
				request.field("responder", account.getAccountName());
				request.field(NdexClasses.ExternalObj_mTime, new Date());
				if(updatedRequest.getPermission() != null) request.field("requestPermission", updatedRequest.getPermission().name());
				if(!Strings.isNullOrEmpty( updatedRequest.getResponseMessage() )) request.field("responseMessage", updatedRequest.getResponseMessage() );
				if(!Strings.isNullOrEmpty( updatedRequest.getResponse().name() )) request.field("response", updatedRequest.getResponse().name());
				request.field(NdexClasses.Request_P_responseTime, new Date());
				request.save();
				logger.info("Request has been updated. UUID : " + requestId.toString());
				
			} else {
				logger.severe("Account is not a recipient or sender of request");
				throw new NdexException(""); // message will not be saved
			}
			
		} catch (Exception e) {
			logger.severe("Unable to update request. UUID : " +  requestId.toString());
			throw new NdexException("Failed to update the request.");
		} 
	} */
	
/*
	
	private void checkForExistingRequest(ODocument user, ODocument source, ODocument destination, Permissions permission) 
			throws DuplicateObjectException, NdexException {
		
		OrientVertex vUser = this.graph.getVertex(user);
		for(Vertex v : vUser.getVertices(Direction.OUT, "requests") ) {
			if( ((OrientVertex) v).getRecord().field("destinationUUID").equals( destination.field("UUID") )
					&& ((OrientVertex) v).getRecord().field("sourceUUID").equals( source.field("UUID") )
					&& ((OrientVertex) v).getRecord().field("requestPermission").equals( permission.name() ) ) 
				throw new DuplicateObjectException("Request has already been issued");
		}
		
		if(checkPermission(source.getIdentity(), destination.getIdentity(), Direction.OUT, 1, permission))
			throw new NdexException("Access has already been granted");
		
	} */
	
	
}
