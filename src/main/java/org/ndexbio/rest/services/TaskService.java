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


import java.sql.SQLException;
import java.util.UUID;

import javax.servlet.http.HttpServletRequest;
import javax.ws.rs.DELETE;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.core.Context;

import org.ndexbio.common.models.dao.postgresql.TaskDAO;
import org.ndexbio.model.exceptions.NdexException;
import org.ndexbio.model.exceptions.ObjectNotFoundException;
import org.ndexbio.model.exceptions.UnauthorizedOperationException;
import org.ndexbio.model.object.Task;
import org.ndexbio.rest.annotations.ApiDoc;
import org.slf4j.LoggerFactory;

import com.google.common.base.Preconditions;
import com.google.common.base.Strings;

import org.slf4j.Logger;

@Path("/task")
public class TaskService extends NdexService
{
	static Logger logger = LoggerFactory.getLogger(TaskService.class);
    
    /**************************************************************************
    * Injects the HTTP request into the base class to be used by
    * getLoggedInUser(). 
    * 
    * @param httpRequest
    *            The HTTP request injected by RESTEasy's context.
    **************************************************************************/
    public TaskService(@Context HttpServletRequest httpRequest)
    {
        super(httpRequest);
    }
    
    /**************************************************************************
    * Creates a task. 
    * 
    * @param newTask
    *            The task to create.
    * @throws IllegalArgumentException
    *            Bad input.
    * @throws NdexException
    *            Failed to create the task in the database.
    * @return The newly created task.
    **************************************************************************/
    /*
     * refactored for non-transactional database operation
     */

    @POST
    @Produces("application/json")
	@ApiDoc("Create a new task owned by the authenticated user based on the supplied JSON task object.")
    public UUID createTask(final Task newTask) throws IllegalArgumentException, NdexException
    {
        final String userAccount = this.getLoggedInUser().getUserName();

		logger.info("[start: Creating {} task for user {}]", newTask.getTaskType(), userAccount);
		
        Preconditions.checkArgument(null != newTask, 
    			" A task object is required");
        newTask.setTaskOwnerId(getLoggedInUser().getExternalId());
        
        try (TaskDAO dao = new TaskDAO())    {
        	
        	UUID taskId = dao.createTask( newTask);
            
            dao.commit();

            logger.info("[end: task {} with type {} created for {}]", 
            		taskId, newTask.getTaskType(), userAccount);
            
            return taskId;
        }
        catch (Exception e)
        {
        	logger.error("[end: Unable to create a task. Exception caught:]{}", e);        	
            throw new NdexException("Error creating a task: " + e.getMessage());
        }
    }
    


    /**************************************************************************
    * Deletes a task. 
    * 
    * @param taskId
    *            The task ID.
    * @throws IllegalArgumentException
    *            Bad input.
    * @throws ObjectNotFoundException
    *            The task doesn't exist.
    * @throws SecurityException
    *            The user doesn't own the task.
    * @throws NdexException
    *            Failed to delete the task from the database.
    **************************************************************************/
    /*
     * refactored for non-transactional database operations
     */
    @DELETE
    @Path("/{taskId}")
    @Produces("application/json")
	@ApiDoc("Delete the task specified by taskId. Errors if no task found or if authenticated user does not own task.")
    public void deleteTask(@PathParam("taskId")final String taskUUID) throws IllegalArgumentException, ObjectNotFoundException, UnauthorizedOperationException, NdexException
    {
		logger.info("[start: Start deleting task {}]", taskUUID);

    	Preconditions.checkArgument(!Strings.isNullOrEmpty(taskUUID), 
    			"A task id is required");
  
    	UUID taskId = UUID.fromString(taskUUID);
    	try (TaskDAO tdao= new TaskDAO()) {
            
            final Task taskToDelete = tdao.getTaskByUUID(taskId);
            
            if (taskToDelete == null) {
        		logger.info("[end: Task {} not found. Throwing ObjectNotFoundException.]", 
        				taskUUID);
                throw new ObjectNotFoundException("Task with ID: " + taskUUID + " doesn't exist.");
            }    
            else if (!taskToDelete.getTaskOwnerId().equals(this.getLoggedInUser().getExternalId())) {
        		logger.info(
        			"[end: You cannot delete task {} because you don't own it. Throwing UnauthorizedOperationException...]", 
        			taskUUID);         		
                throw new UnauthorizedOperationException("You cannot delete a task you don't own.");
            }
            if ( taskToDelete.getIsDeleted()) {
            	logger.info("[end: Task {} is already deleted by user {}]", 
            			taskUUID,this.getLoggedInUser().getUserName());            
            } else {
            	tdao.deleteTask(taskToDelete.getExternalId());
            
            	tdao.commit();
            	logger.info("[end: Task {} is deleted by user {}]", 
            			taskUUID,this.getLoggedInUser().getUserName());
            }
        }
        catch (UnauthorizedOperationException | ObjectNotFoundException onfe)
        {
        	logger.error("[end: Failed to delete task {}. Exception caught:]{}", 
        			taskUUID , onfe);
            throw onfe;
        }
        catch (Exception e)
        {
        	logger.error("[end: Failed to delete task {}. Exception caught:]{}", 
        			taskUUID , e);
        	
        	if (e.getMessage().indexOf("cluster: null") > -1) {	
        		throw new ObjectNotFoundException("Task with ID: " + taskUUID + " doesn't exist.");
            }
	        
            throw new NdexException("Failed to delete task " + taskUUID);
        }
    }

    /**************************************************************************
    * Gets a task by ID.
    * 
    * @param taskId
    *            The task ID.
    * @throws IllegalArgumentException
    *            Bad input.
    * @throws SecurityException
    *            The user doesn't own the task.
    * @throws NdexException
    *            Failed to query the database.
     * @throws SQLException 
    **************************************************************************/
    @GET
    @Path("/{taskId}")
    @Produces("application/json")
	@ApiDoc("Return a JSON task object for the task specified by taskId. Errors if no task found or if authenticated user does not own task.")
    public Task getTask(@PathParam("taskId")final String taskIdStr) throws  UnauthorizedOperationException, NdexException, SQLException
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

    
}
