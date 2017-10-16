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
import java.sql.Timestamp;
import java.util.Calendar;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.UUID;
import java.util.stream.Collectors;

import javax.annotation.security.PermitAll;
import javax.servlet.http.HttpServletRequest;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.Context;

import org.ndexbio.common.importexport.ImporterExporterEntry;
import org.ndexbio.common.models.dao.postgresql.GroupDAO;
import org.ndexbio.common.models.dao.postgresql.NetworkDAO;
import org.ndexbio.common.models.dao.postgresql.TaskDAO;
import org.ndexbio.common.models.dao.postgresql.UserDAO;
import org.ndexbio.model.exceptions.ForbiddenOperationException;
import org.ndexbio.model.exceptions.NdexException;
import org.ndexbio.model.exceptions.ObjectNotFoundException;
import org.ndexbio.model.object.Group;
import org.ndexbio.model.object.NetworkExportRequestV2;
import org.ndexbio.model.object.Status;
import org.ndexbio.model.object.Task;
import org.ndexbio.model.object.TaskType;
import org.ndexbio.model.object.User;
import org.ndexbio.model.object.network.NetworkSummary;
import org.ndexbio.rest.Configuration;
import org.ndexbio.rest.annotations.ApiDoc;
import org.ndexbio.rest.filters.BasicAuthenticationFilter;
import org.ndexbio.task.NdexServerQueue;
import org.ndexbio.task.NetworkExportTask;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.core.JsonParseException;
import com.fasterxml.jackson.databind.JsonMappingException;

@Path("/v2/batch")
public class BatchServiceV2 extends NdexService {
	
	
//	static Logger logger = LoggerFactory.getLogger(BatchServiceV2.class);
	static Logger accLogger = LoggerFactory.getLogger(BasicAuthenticationFilter.accessLoggerName);

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
	@AuthenticationNotRequired
	@Path("/user")
	@Produces("application/json")
	@ApiDoc("Return the user corresponding to user's UUID. Error if no such user is found.")
	public List<User> getUsersByUUIDs(
			List<String> userIdStrs)
			throws IllegalArgumentException, NdexException, JsonParseException, JsonMappingException, SQLException, IOException {

	//	logger.info("[start: Getting users from UUIDs]");
		accLogger.info("[data]\t[uuidcounts:" +userIdStrs.size() + "]" );

		try (UserDAO dao = new UserDAO() ){
			List<User> users = new LinkedList<>();
			for ( String uuidStr : userIdStrs) {
				User user = dao.getUserById(UUID.fromString(uuidStr),true, false);
				users.add(user);
			}
//		    logger.info("[end: User object returned for user uuids]");
			return users;	
		} 
		
	}	
	
	
	
	@SuppressWarnings("static-method")
	@POST
	@PermitAll
	@AuthenticationNotRequired
	@Path("/group")
	@Produces("application/json")
	@ApiDoc("Returns a list of groups for the groups specified by the groupid list. Errors if any of the group id is not found. ")
	public List<Group> getGroupsByUUIDs(List<String> groupIdStrs)
			throws IllegalArgumentException,ObjectNotFoundException, NdexException, JsonParseException, JsonMappingException, SQLException, IOException {
		
//		logger.info("[start: Getting groups by uuids]");
		accLogger.info("[data]\t[uuidcounts:" +groupIdStrs.size() + "]" );

		try (GroupDAO dao = new GroupDAO()) {
			List<Group> groups = new LinkedList<>();
			for ( String groupId : groupIdStrs) {
			  final Group group = dao.getGroupById(UUID.fromString(groupId));
			  groups.add(group);
			}
//			logger.info("[end: Getting groups by uuids]");
			return groups;
		} 
	}
		

	@PermitAll
	@POST
	@Path("/network/summary")
	@Produces("application/json")
	@ApiDoc("Retrieves a list of NetworkSummary objects based on the network uuids POSTed. This " +
            "method only returns network summaries that the user is allowed to read. User can only post up to 2000 uuids in this function.")
	public List<NetworkSummary> getNetworkSummaries(
			@QueryParam("accesskey") String accessKey,
			List<String> networkIdStrs)
			throws IllegalArgumentException, NdexException, SQLException, JsonParseException, JsonMappingException, IOException {

		if (networkIdStrs.size() > 2000) 
			throw new NdexException ("You can only send up to 2000 network ids in this function.");
		
		accLogger.info("[data]\t[uuidcounts:" +networkIdStrs.size() + "]" );

		try (NetworkDAO dao = new NetworkDAO())  {
			UUID userId = getLoggedInUserId();
			return dao.getNetworkSummariesByIdStrList(networkIdStrs, userId, accessKey);				
		}  finally {
//	    	logger.info("[end: Getting networkSummary of networks {}]", networkIdStrs);
		}						
	}
	
	
	@POST
	@Path("/network/permission")
	@Produces("application/json")
	public Map<String,String> getNetworkPermissions(
			List<String> networkIdStrs)
			throws IllegalArgumentException, NdexException, SQLException {

		if ( networkIdStrs == null || networkIdStrs.isEmpty())
			throw new ForbiddenOperationException("An non-empty network UUID list is required");
		
		if (networkIdStrs.size() > 500) 
			throw new ForbiddenOperationException ("You can only send up to 500 network ids in this function.");
		
		accLogger.info("[data]\t[uuidcounts:" +networkIdStrs.size() + "]" );

		try (NetworkDAO dao = new NetworkDAO())  {
			UUID userId = getLoggedInUserId();
			return dao.getNetworkPermissionMapByNetworkIds(userId, networkIdStrs.stream().map( UUID::fromString).collect(Collectors.toList()));				
		}  					
	}
	
	@POST
	@Path("/network/export")
	@Produces("application/json")
    @ApiDoc("")
	public Map<UUID,UUID> exportNetworks(NetworkExportRequestV2 exportRequest)

			throws IllegalArgumentException, NdexException, SQLException, IOException {
		
		    logger.info("exporting networks");
		    ImporterExporterEntry entry = Configuration.getInstance().getImpExpEntry(exportRequest.getExportFormat());
		    if ( entry == null || entry.getExporterCmd() == null || 
		    		entry.getExporterCmd().isEmpty())
		    	throw new NdexException("No exporter was registered in this server for network format " + exportRequest.getExportFormat());
		    
		    Map<UUID,UUID> result = new TreeMap<>();
			try (NetworkDAO networkDao = new NetworkDAO()) {
				try (TaskDAO taskdao = new TaskDAO()) {

					for ( UUID networkID : exportRequest.getNetworkIds()) {
						Task t = new Task();
						Timestamp currentTime = new Timestamp(Calendar.getInstance().getTimeInMillis());
						t.setCreationTime(currentTime);
				//		t.setModificationTime(currentTime);
				//		t.setStartTime(currentTime);
				//		t.setFinishTime(currentTime);
						t.setDescription("network export");
						t.setTaskType(TaskType.EXPORT_NETWORK_TO_FILE);
						//t.setFormat(FileFormat.CX);
						t.setTaskOwnerId(getLoggedInUserId());
						t.setResource(networkID.toString());
						if (! networkDao.isReadable(networkID, getLoggedInUserId())) {
							t.setStatus(Status.FAILED);
							t.setMessage("User doesn't have read access to network " + networkID + ".");
							//throw new NdexException ("Network " + networkID + " is not found.");
						}	else {
							t.setStatus(Status.QUEUED);
							
							NetworkSummary s = networkDao.getNetworkSummaryById(networkID);
							t.setAttribute("downloadFileName", s.getName());
							t.setAttribute("name", entry.getName());
							t.setAttribute("downloadFileExtension", entry.getFileExtension());
						}
						UUID taskId = taskdao.createTask(t);
						taskdao.commit();
						
						result.put(networkID, taskId);
						
						if ( t.getStatus() == Status.QUEUED)
						NdexServerQueue.INSTANCE.addUserTask(new NetworkExportTask(t));

					}
				}
			}
			
			return result;
		    
	}
	
	

}
