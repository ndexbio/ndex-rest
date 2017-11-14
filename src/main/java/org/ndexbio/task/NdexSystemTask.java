package org.ndexbio.task;

import java.sql.Timestamp;
import java.util.Set;
import java.util.UUID;

import org.ndexbio.common.util.NdexUUIDFactory;
import org.ndexbio.model.exceptions.NdexException;
import org.ndexbio.model.object.Status;
import org.ndexbio.model.object.Task;
import org.ndexbio.model.object.TaskType;
import org.ndexbio.model.object.network.VisibilityType;


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
	
	
	public static NdexSystemTask createSystemTask(Task t) throws NdexException {
		switch (t.getTaskType()) {
			case SYS_SOLR_DELETE_NETWORK:
				return new SolrTaskDeleteNetwork(UUID.fromString(t.getResource()),
						Boolean.getBoolean((String)t.getAttribute(SolrTaskDeleteNetwork.globalIdxAttr)));
			case SYS_SOLR_REBUILD_NETWORK_INDEX:
				return new SolrTaskRebuildNetworkIdx(UUID.fromString(t.getResource()), SolrIndexScope.valueOf((String)t.getAttribute(SolrTaskRebuildNetworkIdx.AttrScope)), 
						  ((Boolean)t.getAttribute(SolrTaskRebuildNetworkIdx.AttrCreateOnly)).booleanValue(),
						  (Set<String>)t.getAttribute("fields"));
			case SYS_LOAD_NETWORK:
				return new CXNetworkLoadingTask (UUID.fromString(t.getResource()),(String)t.getAttribute("owner"),
						(Boolean)t.getAttribute("isUpdate"), VisibilityType.valueOf((String)t.getAttribute("visibility")),
						(Set<String>)t.getAttribute("nodeIndexes"));
			default:
				throw new NdexException("Unknow system task: " + t.getExternalId() + " - " + t.getTaskType());
			
		}
	}
}
