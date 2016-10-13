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
package org.ndexbio.rest.services;

import java.io.IOException;
import java.sql.SQLException;
import java.util.LinkedList;
import java.util.List;
import java.util.UUID;

import javax.annotation.security.PermitAll;
import javax.servlet.http.HttpServletRequest;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.core.Context;

import org.ndexbio.common.models.dao.postgresql.GroupDAO;
import org.ndexbio.common.models.dao.postgresql.NetworkDAO;
import org.ndexbio.common.models.dao.postgresql.UserDAO;
import org.ndexbio.model.exceptions.NdexException;
import org.ndexbio.model.exceptions.ObjectNotFoundException;
import org.ndexbio.model.object.Group;
import org.ndexbio.model.object.User;
import org.ndexbio.model.object.network.NetworkSummary;
import org.ndexbio.rest.annotations.ApiDoc;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.core.JsonParseException;
import com.fasterxml.jackson.databind.JsonMappingException;

@Path("/v2/batch")
public class BatchServiceV2 extends NdexService {
	
//	private static final String GOOGLE_OAUTH_FLAG = "USE_GOOGLE_AUTHENTICATION";
//	private static final String GOOGLE_OATH_KEY = "GOOGLE_OATH_KEY";
	
	
	static Logger logger = LoggerFactory.getLogger(BatchServiceV2.class);

	/**************************************************************************
	 * Injects the HTTP request into the base class to be used by
	 * getLoggedInUser().
	 * 
	 * @param httpRequest
	 *            The HTTP request injected by RESTEasy's context.
	 **************************************************************************/
	public BatchServiceV2(@Context HttpServletRequest httpRequest) {
		super(httpRequest);
	}
	
	
	@SuppressWarnings("static-method")
	@POST
	@PermitAll
	@Path("/user")
	@Produces("application/json")
	@ApiDoc("Return the user corresponding to user's UUID. Error if no such user is found.")
	public List<User> getUsersByUUIDs(
			List<String> userIdStrs)
			throws IllegalArgumentException, NdexException, JsonParseException, JsonMappingException, SQLException, IOException {

		logger.info("[start: Getting users from UUIDs]");
		
		try (UserDAO dao = new UserDAO() ){
			List<User> users = new LinkedList<>();
			for ( String uuidStr : userIdStrs) {
				User user = dao.getUserById(UUID.fromString(uuidStr),true);
				users.add(user);
			}
		    logger.info("[end: User object returned for user uuids]");
			return users;	
		} 
		
	}	
	
	
	
	@SuppressWarnings("static-method")
	@POST
	@PermitAll
	@Path("/group")
	@Produces("application/json")
	@ApiDoc("Returns a list of groups for the groups specified by the groupid list. Errors if any of the group id is not found. ")
	public List<Group> getGroupsByUUIDs(List<String> groupIdStrs)
			throws IllegalArgumentException,ObjectNotFoundException, NdexException, JsonParseException, JsonMappingException, SQLException, IOException {
		
		logger.info("[start: Getting groups by uuids]");

		try (GroupDAO dao = new GroupDAO()) {
			List<Group> groups = new LinkedList<>();
			for ( String groupId : groupIdStrs) {
			  final Group group = dao.getGroupById(UUID.fromString(groupId));
			  groups.add(group);
			}
			logger.info("[end: Getting groups by uuids]");
			return groups;
		} 
	}
		

	@PermitAll
	@POST
	@Path("/network/summary")
	@Produces("application/json")
	@ApiDoc("Retrieves a list of NetworkSummary objects based on the network uuids POSTed. This " +
            "method only returns network summaries that the user is allowed to read. User can only post up to 300 uuids in this function.")
	public List<NetworkSummary> getNetworkSummaries(
			List<String> networkIdStrs)
			throws IllegalArgumentException, NdexException, SQLException, JsonParseException, JsonMappingException, IOException {

		if (networkIdStrs.size() > 300) 
			throw new NdexException ("You can only send up to 300 network ids in this function.");
		
    	logger.info("[start: Getting networkSummary of networks {}]", networkIdStrs);
		
		try (NetworkDAO dao = new NetworkDAO())  {
			UUID userId = getLoggedInUserId();
			return dao.getNetworkSummariesByIdStrList(networkIdStrs, userId);				
		}  finally {
	    	logger.info("[end: Getting networkSummary of networks {}]", networkIdStrs);
		}						
	}
	
	

	

}
