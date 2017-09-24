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
import java.security.GeneralSecurityException;
import java.sql.SQLException;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import javax.annotation.security.PermitAll;
import javax.servlet.http.HttpServletRequest;
import javax.ws.rs.DELETE;
import javax.ws.rs.DefaultValue;
import javax.ws.rs.GET;
import javax.ws.rs.PUT;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.Response.ResponseBuilder;

import org.ndexbio.common.models.dao.postgresql.TaskDAO;
import org.ndexbio.model.exceptions.ForbiddenOperationException;
import org.ndexbio.model.exceptions.NdexException;
import org.ndexbio.model.exceptions.ObjectNotFoundException;
import org.ndexbio.model.exceptions.UnauthorizedOperationException;
import org.ndexbio.model.object.Status;
import org.ndexbio.model.object.Task;
import org.ndexbio.model.object.TaskType;
import org.ndexbio.model.object.User;
import org.ndexbio.rest.Configuration;
import org.ndexbio.rest.annotations.ApiDoc;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.core.JsonParseException;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.google.common.base.Preconditions;
import com.google.common.base.Strings;

@Path("/v2/task")
public class TaskServiceV2 extends NdexService
{
	static Logger logger = LoggerFactory.getLogger(TaskService.class);
    
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
    @Produces("application/json")
	@ApiDoc("Delete the task specified by taskId. Errors if no task found or if authenticated user does not own task.")
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
            if ( taskToDelete.getIsDeleted()) {
            	logger.info("[end: Task {} is already deleted by user {}]", 
            			taskUUID,this.getLoggedInUser().getUserName());            
            } else {
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
    @Produces("application/json")
	@ApiDoc("Return a JSON task object for the task specified by taskId. Errors if no task found or if authenticated user does not own task.")
    public Task getTask(@PathParam("taskid")final String taskIdStr) throws  UnauthorizedOperationException, NdexException, SQLException, JsonParseException, JsonMappingException, IOException
    {
    	logger.info("[start: get task {}] ", taskIdStr);
    	
    	Preconditions.checkArgument(!Strings.isNullOrEmpty(taskIdStr), "A task id is required");
    	
    	UUID taskId = UUID.fromString(taskIdStr);

    	try (TaskDAO tdao= new TaskDAO()) {
            
            final Task task = tdao.getTaskByUUID(taskId);
            
            if (task == null || task.getIsDeleted()) {
        		logger.info("[end: Task {} not found]", taskIdStr);
                throw new ObjectNotFoundException("Task", taskIdStr);
            }    
            
            else if (!task.getTaskOwnerId().equals(this.getLoggedInUser().getExternalId())) {
        		logger.info("[end: User {} is unauthorized to query task {}]", 
        				getLoggedInUser().getExternalId(), taskId);            
                throw new UnauthorizedOperationException("Can't query task " + taskId + 
                		" for user " + this.getLoggedInUser().getUserName());
            }

        	logger.info("[end: Return task {} to user] ", taskIdStr);
        	return task;
        }
    }

    
	@GET
	@Produces("application/json")
	@ApiDoc("Returns an array of Task objects with the specified status")
	public List<Task> getTasks(
		    @QueryParam("status") String status,
			@DefaultValue("0") @QueryParam("start") int skipBlocks,
			@DefaultValue("100") @QueryParam("size") int blockSize) 
					throws SQLException, JsonParseException, JsonMappingException, IOException {

		logger.info("[start: Getting tasks for user {}]", getLoggedInUser().getUserName());

		Status taskStatus = Status.ALL;
		if ( status != null)
			taskStatus = Status.valueOf(status);

		try (TaskDAO dao = new TaskDAO ()){
			List<Task> tasks= dao.getTasksByUserId(this.getLoggedInUser().getExternalId(),taskStatus, skipBlocks, blockSize);
			logger.info("[end: Returned {} tasks under user {}]", tasks.size(), getLoggedInUser().getUserName());
			return tasks;
		} 
	}
	
	
	   	@PUT
	    @Path("/{taskid}/ownerProperties")
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
	@ApiDoc("")
	//download the file generated by this export task. If this task is not an export task, a 500 error will be return.
	public Response downloadExportedFile(	@PathParam("taskid") final String taskIdStr,
			@QueryParam("download") boolean isDownload,
			@QueryParam("id_token") String id_token)
			throws NdexException, SQLException, JsonParseException, JsonMappingException, IOException, IllegalArgumentException, GeneralSecurityException {
    	
    	String title = null;
		UUID taskId = UUID.fromString(taskIdStr);
		String extension = null;
		
		UUID userId = getLoggedInUserId();
		if ( userId == null && id_token !=null) {
			if ( getGoogleAuthenticator() == null)
				throw new UnauthorizedOperationException("Google OAuth is not enabled on this server.");
			userId = getGoogleAuthenticator().getUserUUIDByIdToken(id_token);
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
			if ( isDownload)
				r.header("Content-Disposition",  "attachment; filename=\"" + title + (extension != null ? "." + extension : "") +"\"");
			return 	r.type(MediaType.APPLICATION_JSON_TYPE).entity(in).build();
		} catch (IOException e) {
			logger.error("[end: Ndex server can't find file: {}]", e.getMessage());
			throw new NdexException ("Ndex server can't find file: " + e.getMessage());
		}
		
	}  
}
