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
import java.util.Collection;
import java.util.Date;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.UUID;

import org.ndexbio.common.NdexClasses;
import org.ndexbio.common.access.NdexDatabase;
import org.ndexbio.common.util.NdexUUIDFactory;
import org.ndexbio.model.exceptions.NdexException;
import org.ndexbio.model.exceptions.ObjectNotFoundException;
import org.ndexbio.model.object.NdexPropertyValuePair;
import org.ndexbio.model.object.Permissions;
import org.ndexbio.model.object.Priority;
import org.ndexbio.model.object.Status;
import org.ndexbio.model.object.Task;
import org.ndexbio.model.object.TaskAttribute;
import org.ndexbio.model.object.TaskType;
import org.ndexbio.model.object.User;
import org.ndexbio.model.object.network.FileFormat;
import org.ndexbio.task.NdexServerQueue;

import com.fasterxml.jackson.core.JsonParseException;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.base.Preconditions;
import com.google.common.collect.Lists;

public class TaskDAO extends NdexDBDAO {


//	private static final Logger logger = Logger.getLogger(TaskDAO.class.getName());
	
	public TaskDAO () throws SQLException {
		super();
	}

	public Task getTaskByUUID(UUID taskId) throws ObjectNotFoundException, NdexException, SQLException, JsonParseException, JsonMappingException, IOException {
        
		String sqlStr = "SELECT * FROM " + NdexClasses.Task + " where \"UUID\" = ? and not is_deleted ";
		
		try (PreparedStatement st = db.prepareStatement(sqlStr)) {
			try (ResultSet rs = st.executeQuery(sqlStr) ) {
				if (rs.next()) {
					// populate the user object;
					Task result = new Task();

					populateTaskFromResultSet(result, rs);

					return result;
				} 
				throw new ObjectNotFoundException("Task with UUID: " + taskId.toString() + " doesn't exist.");

			}
		}		
	}
	
	
	private static void populateTaskFromResultSet(Task task, ResultSet rs) throws SQLException, JsonParseException, JsonMappingException, IOException {
		
		Helper.populateExternalObjectFromResultSet(task, rs);
				
		task.setDescription(rs.getString("description"));
		task.setPriority(Priority.valueOf(rs.getString("priority")));
		task.setProgress(rs.getInt("progress"));
		task.setResource(rs.getString("resource"));
		task.setStatus(Status.valueOf(rs.getString("status")));
		task.setTaskType(TaskType.valueOf(rs.getString("task_type")));
		task.setStartTime(rs.getTimestamp("start_time"));
		task.setFinishTime(rs.getTimestamp("end_time"));
		task.setTaskOwnerId((UUID)rs.getObject("owneruuid"));
		task.setMessage(rs.getString("message"));
        
		String str = rs.getString("file_format");		
		if ( str != null) task.setFormat(FileFormat.valueOf(str));

		String o = rs.getString("other_attributes");
		if ( o != null) {
			ObjectMapper mapper = new ObjectMapper(); 
			Map<String, Object> attr = mapper.readValue(o, new TypeReference<Map<String,Object>>() {}); 					
			task.setAttributes(attr);
		}
	}
	

    
	public UUID createTask(Task newTask) throws ObjectNotFoundException, NdexException, SQLException, JsonProcessingException {
		
		if (newTask.getExternalId() == null)
			newTask.setExternalId(NdexUUIDFactory.INSTANCE.createNewNDExUUID());
		
		String insertStr = "insert into task (\"UUID\", creation_time, modification_time," + 
					"status, start_time,end_time,task_type,owneruuid,is_deleted,other_attributes,"+
				   "description,priority, progress,file_format, message, resource) values ( ?,?,?,?,?,?,?,?,false,? ::jsonb,?,?,?,?,?,?)";
		
		try (PreparedStatement st = db.prepareStatement(insertStr) ) {
			st.setObject(1, newTask.getExternalId());		
			st.setTimestamp(2, newTask.getCreationTime());
			st.setTimestamp(3, newTask.getModificationTime());
			st.setString(4,newTask.getStatus().toString());
			st.setTimestamp(5, newTask.getStartTime());
			st.setTimestamp(6, newTask.getFinishTime());
			st.setString(7, newTask.getTaskType().toString());
			st.setObject(8, newTask.getTaskOwnerId());
			
			if ( newTask.getAttributes() !=null && newTask.getAttributes().size() >0 ) {
				ObjectMapper mapper = new ObjectMapper();
			    String s = mapper.writeValueAsString( newTask.getAttributes());
		    	st.setString(9, s);
			} else 
				st.setString(9, null);
			
			st.setString(10, newTask.getDescription());
			st.setString(11, newTask.getPriority().toString());
			st.setInt(12, newTask.getProgress());
			st.setString(13, (newTask.getFormat() != null ? newTask.getFormat().toString(): null));
			st.setString(14, newTask.getMessage());
			st.setString(15, newTask.getResource());
			
			int rowsInserted = st.executeUpdate();
			if ( rowsInserted != 1)
				throw new NdexException ( "Failed to create Task in database.");
		}
	
		return newTask.getExternalId();
		
	}

    /**
     * Update the status of task.status to newStatus. Update will be applied to database and the task object. 
     * @param newStatus
     * @param task
     * @return
     * @throws ObjectNotFoundException
     * @throws NdexException
     * @throws SQLException 
     * @throws IOException 
     * @throws JsonMappingException 
     * @throws JsonParseException 
     */
    public void updateTaskStatus(UUID taskID,Status newStatus) throws ObjectNotFoundException, NdexException, SQLException, JsonParseException, JsonMappingException, IOException {
    	
    	updateTaskStatus (taskID,newStatus, null,null);
    }


	public void updateTaskStatus (UUID taskID, Status status, String message, String stackTrace) throws NdexException, SQLException, JsonParseException, JsonMappingException, IOException {
		
		String updateStr = " update " + NdexClasses.Task + " set status= ?, message = ? " ;	
		String updateStr2 =  " where \"UUID\" = ? and is_deleted = false";
			
		if ( stackTrace == null) {
			try (PreparedStatement st = db.prepareStatement(updateStr + updateStr2 ) ) {
				st.setString ( 1, status.toString());
				st.setString(2, message);
				st.setObject(3, taskID);			
						
				int rowsInserted = st.executeUpdate();
				if ( rowsInserted != 1)
					throw new NdexException ( "Failed to update task status for " + taskID + " in database.");
			}
		} else {
			Task t = getTaskByUUID(taskID);
			Map<String, Object> attrs = t.getAttributes();
			if ( attrs == null)
				attrs = new HashMap<>();
			attrs.put(TaskAttribute.NdexServerStackTrace, stackTrace);
			ObjectMapper mapper = new ObjectMapper();
		    String s = mapper.writeValueAsString( t.getAttributes());
		    
			try (PreparedStatement st = db.prepareStatement(updateStr + ", other_attributes=? "+ updateStr2 ) ) {
				st.setString ( 1, status.toString());
				st.setString(2, message);
				st.setString(3, s);
				st.setObject(4, taskID);			
						
				int rowsInserted = st.executeUpdate();
				if ( rowsInserted != 1)
					throw new NdexException ( "Failed to update task status for " + taskID + " in database.");
			}

		}

	}

 

    public int deleteTask (UUID taskID) throws ObjectNotFoundException, NdexException, SQLException {
    	
    	String updateStr = "update " + NdexClasses.Task + " set is_deleted= true where \"UUID\" = ? and isDeleted = false";
		
    	try (PreparedStatement st = db.prepareStatement(updateStr) ) {
    		st.setObject(1, taskID);			
    			
    		int rowsInserted = st.executeUpdate();
    		if ( rowsInserted != 1)
    			throw new NdexException ( "Failed to delete task " + taskID + " in database.");
        	return 1;		           
    	}   		
    }
    
    public void saveTaskAttributes(UUID taskID, Map<String, Object> attributes ) throws ObjectNotFoundException, NdexException, JsonProcessingException, SQLException {

    	String updateStr = "update " + NdexClasses.Task + " set other_attributes= ? ::jsonb where \"UUID\" = ? and isDeleted = false";
		
		ObjectMapper mapper = new ObjectMapper();
	    String s = mapper.writeValueAsString( attributes);
    	try (PreparedStatement st = db.prepareStatement(updateStr) ) {
    		st.setString(1, s);
    		st.setObject(2, taskID);			
    			
    		int rowsInserted = st.executeUpdate();
    		if ( rowsInserted != 1)
    			throw new NdexException ( "Failed to delete task " + taskID + " in database.");
    	}   		
    	
    }

    
	public List<Task> getTasksByUserId(UUID userID, Status status, int skipBlocks, int blockSize) throws SQLException, JsonParseException, JsonMappingException, IOException {

		Preconditions.checkArgument(userID != null, "UserID can't be null");

		final List<Task> tasks = new ArrayList<>();

		String limitStr = "";
		if ( skipBlocks >=0 && blockSize > 0) {
			limitStr = " limit " + blockSize + " offset " + skipBlocks * blockSize;
		}
		
		String statusFilter = "";
		if (status != Status.ALL){
			statusFilter = " and status = '" + status + "'";
		}

		String queryStr = "select * from  task where owneruuid = ? and not is_deleted" + statusFilter + limitStr;
		
		try (PreparedStatement st = db.prepareStatement(queryStr))  {
			st.setObject(1, userID);
			try (ResultSet rs = st.executeQuery() ) {
				while (rs.next()) {
					Task t = new Task();
					populateTaskFromResultSet(t,rs);
					tasks.add(t);
				} 
			}
		}
		
		return tasks;
		
	}


}
