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


import java.io.FileInputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.sql.SQLException;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import jakarta.annotation.security.PermitAll;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.ws.rs.DELETE;
import jakarta.ws.rs.DefaultValue;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.PUT;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.Response.ResponseBuilder;

import org.ndexbio.common.models.dao.postgresql.TaskDAO;
import org.ndexbio.model.exceptions.ForbiddenOperationException;
import org.ndexbio.model.exceptions.NdexException;
import org.ndexbio.model.exceptions.ObjectNotFoundException;
import org.ndexbio.model.exceptions.UnauthorizedOperationException;
import org.ndexbio.model.object.Status;
import org.ndexbio.model.object.Task;
import org.ndexbio.model.object.TaskType;
import org.ndexbio.rest.Configuration;

import com.fasterxml.jackson.core.JsonParseException;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.google.common.base.Preconditions;
import com.google.common.base.Strings;

import io.swagger.v3.oas.annotations.Operation;

@Path("/v2/task")
public class TaskServiceV2 extends NdexService
{
    
    /**************************************************************************
    * Injects the HTTP request into the base class to be used by
    * getLoggedInUser(). 
    * 
    * @param httpRequest
    *            The HTTP request injected by RESTEasy's context.
    **************************************************************************/
    public TaskServiceV2(@Context HttpServletRequest httpRequest)
    {
        super(httpRequest);
    }
    
   
    /**************************************************************************
    * Deletes a task. 
    * 
    * @param taskId
    *            The task ID.
     * @throws SQLException 
     * @throws IOException 
     * @throws JsonMappingException 
     * @throws JsonParseException 

    **************************************************************************/
    /*
     * refactored for non-transactional database operations
     */
    @DELETE
    @Path("/{taskid}")
	@Operation(summary = "Delete a Task", description = "Deletes the task specified by taskId.")
    @Produces("application/json")
    public void deleteTask(@PathParam("taskid")final String taskUUID) throws IllegalArgumentException, ObjectNotFoundException, UnauthorizedOperationException, NdexException, SQLException, JsonParseException, JsonMappingException, IOException
    {

    	UUID taskId = UUID.fromString(taskUUID);
    	try (TaskDAO tdao= new TaskDAO()) {
            
            final Task taskToDelete = tdao.getTaskByUUID(taskId);
            
            if (taskToDelete == null) {
                throw new ObjectNotFoundException("Task with ID: " + taskUUID + " doesn't exist.");
            }    
            else if (!taskToDelete.getTaskOwnerId().equals(this.getLoggedInUser().getExternalId())) {
                throw new UnauthorizedOperationException("You cannot delete a task you don't own.");
            }
            if ( ! taskToDelete.getIsDeleted()) {
            	       
            	tdao.deleteTask(taskToDelete.getExternalId());
            	tdao.commit();
            	
            	if (taskToDelete.getTaskType() == TaskType.EXPORT_NETWORK_TO_FILE) { //delete the exported file assume all exported files started with the taskId
            		            		
            		Files.list(Paths.get(Configuration.getInstance().getNdexRoot() + "/workspace/" +taskToDelete.getTaskOwnerId()))
            		.filter(p -> p.toString().contains("/" + taskToDelete.getExternalId().toString() + ".")).forEach((p) -> {
            		    try {
            		        Files.deleteIfExists(p);
            		    } catch (Exception e) {
            		        e.printStackTrace();
            		    }
            		});

            	}
            }
        }

    }

    /**************************************************************************
    * Gets a task by ID.
    * 
    * @param taskId
    *            The task ID.

    **************************************************************************/
    @GET
    @Path("/{taskid}")
	@Operation(summary = "Get a Task by Task UUID", description = "Returns a JSON task object for the task specified by taskId.")
    @Produces("application/json")
    public Task getTask(@PathParam("taskid")final String taskIdStr) throws  UnauthorizedOperationException, NdexException, SQLException, JsonParseException, JsonMappingException, IOException
    {    	
    	Preconditions.checkArgument(!Strings.isNullOrEmpty(taskIdStr), "A task id is required");
    	
    	UUID taskId = UUID.fromString(taskIdStr);

    	try (TaskDAO tdao= new TaskDAO()) {
            
            final Task task = tdao.getTaskByUUID(taskId);
            
            if (task == null || task.getIsDeleted()) {
                throw new ObjectNotFoundException("Task", taskIdStr);
            }    
            
            else if (!task.getTaskOwnerId().equals(this.getLoggedInUser().getExternalId())) {
        	      
                throw new UnauthorizedOperationException("Can't query task " + taskId + 
                		" for user " + this.getLoggedInUser().getUserName());
            }
        	return task;
        }
    }

    
	@GET
	@Produces("application/json")
	@Operation(summary = "Get User's Tasks", description = "Returns a JSON array of Task objects owned by the authenticated user with the specified status.")
	public List<Task> getTasks(
		    @QueryParam("status") String status,
			@DefaultValue("0") @QueryParam("start") int skipBlocks,
			@DefaultValue("100") @QueryParam("size") int blockSize) 
					throws SQLException, JsonParseException, JsonMappingException, IOException {

		Status taskStatus = Status.ALL;
		if ( status != null)
			taskStatus = Status.valueOf(status);

		try (TaskDAO dao = new TaskDAO ()){
			List<Task> tasks= dao.getTasksByUserId(this.getLoggedInUser().getExternalId(),taskStatus, skipBlocks, blockSize);
			return tasks;
		} 
	}
	
	
	   	@PUT
	    @Path("/{taskid}/ownerProperties")
		@Operation(summary = "Update task Properties", description = "User can use this method to set arbitrary attributes on a task they own.")
	    @Produces("application/json")
	    public void updateTaskOwnerProperties(
	    		@PathParam("taskid")final String taskUUID,
	    		Map<String,Object> props) throws IllegalArgumentException, ObjectNotFoundException, UnauthorizedOperationException, NdexException, SQLException, JsonParseException, JsonMappingException, IOException
	    {

	    	UUID taskId = UUID.fromString(taskUUID);
	    	try (TaskDAO tdao= new TaskDAO()) {
	            
	            UUID ownerId = tdao.getTaskOwnerId(taskId);
	            
	            if ( !ownerId.equals(this.getLoggedInUserId()))
	            	throw new ForbiddenOperationException("You are not the owner of the task.");
	            
	            tdao.updateTaskOwnerProperties(taskId,props);
	            tdao.commit();
	        }

	    }

	@PermitAll
	@GET
	@Path("/{taskid}/file")
	@Operation(summary = "Download exported file by Task UUID", description = "There are 2 additional parameters for this function. These parameters are added to allow web applications to dynamically create download links without using http headers.")
	//download the file generated by this export task. If this task is not an export task, a 500 error will be return.
	public Response downloadExportedFile(	@PathParam("taskid") final String taskIdStr,
			@QueryParam("download") boolean isDownload,
			@QueryParam("id_token") String id_token,
			@QueryParam("auth_token") String auth_token)
			throws Exception {
    	
    	String title = null;
		UUID taskId = UUID.fromString(taskIdStr);
		String extension = null;
		
		UUID userId = getLoggedInUserId();
		if ( userId == null ) {	
			if ( auth_token != null) {
				userId = getUserIdFromBasicAuthString(auth_token);
			} else if ( id_token !=null) {
				if ( getOAuthAuthenticator() == null)
					throw new UnauthorizedOperationException("Google OAuth is not enabled on this server.");
				userId = getOAuthAuthenticator().getUserUUIDByIdToken(id_token);
			}
		}	
    	try (TaskDAO dao = new TaskDAO()) {
    		Task t = dao.getTaskByUUID(taskId);
    		if (t == null)
    			throw new ObjectNotFoundException("Task " + taskIdStr + " is not found in this server.");
    		if(t.getTaskOwnerId() == null || !t.getTaskOwnerId().equals(userId) )
                throw new UnauthorizedOperationException("This task is not owned by this user.");
    		if ( t.getTaskType() != TaskType.EXPORT_NETWORK_TO_FILE)
    			throw new ObjectNotFoundException("File not found. This task is not a network export task.");
    		if ( t.getStatus() != Status.COMPLETED)
    			throw new ObjectNotFoundException("File not found. This task is not completed yet.");
    		title = (String)t.getAttribute("downloadFileName");
    		extension = (String)t.getAttribute("downloadFileExtension");
    	}

    	if ( title == null || title.length() < 1) {
    		title = taskIdStr;
    	}
    	title.replace('"', '_');
    	
		String cxFilePath = Configuration.getInstance().getNdexRoot() + "/workspace/" +userId + 
			"/"+ taskId +	(extension != null ? "." + extension : "");

    	try {
			FileInputStream in = new FileInputStream(cxFilePath)  ;
		
		//	setZipFlag();
//			logger.info("[end: Return network {}]", networkId);
			ResponseBuilder r = Response.ok();
			if ( isDownload) {
				r.header("Content-Disposition",  "attachment; filename=\"" + title + (extension != null ? "." + extension : "") +"\"");
				r.header("Access-Control-Expose-Headers", "Content-Disposition");
			}	
			return r.type(MediaType.APPLICATION_OCTET_STREAM_TYPE).entity(in).build();
		} catch (IOException e) {
			throw new NdexException ("Ndex server can't find file: " + e.getMessage());
		}
		
	}  
}
