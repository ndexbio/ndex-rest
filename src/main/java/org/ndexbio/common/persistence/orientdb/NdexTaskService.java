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
package org.ndexbio.common.persistence.orientdb;

import java.util.List;

import org.ndexbio.common.models.dao.postgresql.TaskDAO;
import org.ndexbio.common.models.dao.postgresql.TaskDAO;
import org.ndexbio.common.models.dao.postgresql.UserDAO;
import org.ndexbio.model.exceptions.NdexException;
import org.ndexbio.model.object.Status;
import org.ndexbio.model.object.Task;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.orientechnologies.orient.core.record.impl.ODocument;
import com.orientechnologies.orient.core.sql.query.OSQLSynchQuery;

/*
 * Represents a collection of methods for interacting with Tasks in the orientdb database
 * Retained in the common ndex-common project to facilitate availability to multiple ndex
 * projects using Tasks
 * 
 * mod 13Jan2014 - use domain objects instead of model objects 
 * mod 01Apr2014 - add public method to delete task entities
 */

public class NdexTaskService 
{
    private static final Logger logger = LoggerFactory.getLogger(NdexTaskService.class);
    private OrientDBNoTxConnectionService ndexService;
    
    public NdexTaskService()
    {
    	ndexService = new OrientDBNoTxConnectionService();  
    }
    
    
    /*
     * public method to delete Task entities that have a status of
     * QUEUED_FOR_DELETION
     */
    public void deleteTasksQueuedForDeletion() throws NdexException {
    	String query = "select from task "
	            + " where status = '" +Status.QUEUED_FOR_DELETION.toString() +"'";
    	
    	try {
    		
			this.ndexService.setupDatabase();
			
			final List<ODocument> taskDocumentList = this.ndexService._ndexDatabase.
					query(new OSQLSynchQuery<ODocument>(query));
			for (final ODocument document : taskDocumentList) {
				this.ndexService.getGraph().getVertex(document).remove();
			}
			
		} catch (Exception e) {
			logger.error("Failed to search tasks", e);
            throw new NdexException("Failed to search tasks.");
			
		}finally {
			this.ndexService.teardownDatabase();
		}
    	
    }
  
    /*
    public Task getTask(String taskUUID) throws ObjectNotFoundException, NdexException {
    	TaskDAO dao = new TaskDAO(NdexAOrientDBConnectionPool.getInstance().acquire());
    	return dao.getTaskByUUID(taskUUID);
    }
*/
    
    
    private List<Task> stageQueuedTasks() throws NdexException
    {
    	try {
    		
			this.ndexService.setupDatabase();
		//	TaskDAO dao = new TaskDAO(this.ndexService._ndexDatabase);
		//	List<Task> taskList = dao.stageQueuedTasks();
	//		this.ndexService._ndexDatabase.commit();
			return null ; //taskList;
			
		} catch (Exception e) {
			logger.error("Failed to search tasks", e);
            throw new NdexException("Failed to search tasks.");
			
		}finally {
			this.ndexService.teardownDatabase();
		}
   	 
    }

    public Task updateTaskStatus(Status status, Task task) throws NdexException {
    	
    	try {
    		
			this.ndexService.setupDatabase();
			TaskDAO dao = new TaskDAO(this.ndexService._ndexDatabase);
			logger.info("Updating status of tasks " + task.getExternalId() + " from " +
			   task.getStatus() + " to " + status);
			dao.saveTaskStatus(task.getExternalId().toString(), status, null, null);
			task.setStatus(status);
			return task;
		} catch (Exception e) {
			logger.error("Failed to update task satus : " + e.getMessage(), e);
			throw new NdexException("Failed to update status of task: " + task.getExternalId() + " to " + status);
			
		}finally {
			this.ndexService.teardownDatabase();
		}
    	
    }

    public void addTaskAttribute(UUID taskId, String name, Object value) throws NdexException {
    	try {
    		
			this.ndexService.setupDatabase();
			TaskDAO dao = new TaskDAO(this.ndexService._ndexDatabase);
			Task t = dao.getTaskByUUID(uuidStr);
			t.getAttributes().put(name,value);
			dao.saveTaskAttributes(taskId,t.getAttributes());
			this.ndexService._ndexDatabase.commit();
			return;
		} finally {
			this.ndexService.teardownDatabase();
		}
    	
    	
    }
    
    public String getTaskOwnerAccount(Task task) throws NdexException {
    	try {
    		
			this.ndexService.setupDatabase();
			UserDAO dao = new UserDAO(this.ndexService._ndexDatabase);
			return dao.getUserById(task.getTaskOwnerId()).getAccountName();
		} catch (Exception e) {
			logger.error("Failed to search tasks", e);
			throw new NdexException("Failed to search tasks." + e.getMessage());
			
		}finally {
			this.ndexService.teardownDatabase();
		}
    	
    }
   
}
