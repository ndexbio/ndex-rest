package org.ndexbio.task;

import java.sql.Timestamp;
import java.util.UUID;

import org.ndexbio.common.util.NdexUUIDFactory;
import org.ndexbio.model.object.Status;
import org.ndexbio.model.object.Task;
import org.ndexbio.model.object.TaskType;


public abstract class NdexSystemTask {
	
	private UUID taskId ;
	
	public NdexSystemTask() {
	  taskId = 	NdexUUIDFactory.INSTANCE.createNewNDExUUID();
	}
	
	public final UUID getTaskId() {return taskId;}
	
	public abstract void run () throws Exception;
	public abstract TaskType getTaskType();
	
	public Task createTask() {
		Timestamp t = new Timestamp(System.currentTimeMillis());
		Task task = new Task();
		task.setExternalId(taskId);
		task.setCreationTime(t);
		task.setTaskType(getTaskType());
		
	    task.setStatus(Status.QUEUED);			
	    return task;	
	}
}
