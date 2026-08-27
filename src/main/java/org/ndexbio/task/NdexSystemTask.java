package org.ndexbio.task;

import java.sql.Timestamp;
import java.util.Set;
import java.util.UUID;

import org.ndexbio.common.util.NdexUUIDFactory;
import org.ndexbio.model.exceptions.NdexException;
import org.ndexbio.model.object.Status;
import org.ndexbio.model.object.Task;
import org.ndexbio.model.object.TaskType;
import org.ndexbio.model.object.network.NetworkIndexLevel;
import org.ndexbio.model.object.network.VisibilityType;


public abstract class NdexSystemTask  {
	
	private UUID taskId ;
	
	public NdexSystemTask() {
	  taskId = 	NdexUUIDFactory.INSTANCE.createNewNDExUUID();
	}
	
	public final UUID getTaskId() {return taskId;}
	public void setTaskId(UUID taskID) {taskId = taskID;}
	
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
				// SolrTaskDeleteFiles and SolrTaskDeleteFile both share this task type (TaskType lives
				// in ndex-object-model, so a new value would force a dependency bump). Each is
				// discriminated by an attribute only it writes; rows written before those attributes
				// existed lack them and fall through to the SolrTaskDeleteNetwork path below, so this
				// stays backward compatible.
				if (t.getAttribute(SolrTaskDeleteFiles.folderIdsAttr) != null) {
					return SolrTaskDeleteFiles.fromTask(t);
				}
				if (t.getAttribute(SolrTaskDeleteFile.fileTypeAttr) != null) {
					return SolrTaskDeleteFile.fromTask(t);
				}
				// Read defensively: a legacy row can be missing globalIdxOnly entirely, and unboxing
				// a null Boolean into the primitive parameter would throw during startup replay.
				return new SolrTaskDeleteNetwork(UUID.fromString(t.getResource()),
						!Boolean.FALSE.equals(t.getAttribute(SolrTaskDeleteNetwork.globalIdxAttr)),
						visibilityFromAttribute(t.getAttribute("visibility")));
			case SYS_SOLR_REBUILD_NETWORK_INDEX:
				return new SolrTaskRebuildNetworkIdx(UUID.fromString(t.getResource()), SolrIndexScope.valueOf((String)t.getAttribute(SolrTaskRebuildNetworkIdx.AttrScope)), 
						  ((Boolean)t.getAttribute(SolrTaskRebuildNetworkIdx.AttrCreateOnly)).booleanValue(),
						  (Set<String>)t.getAttribute("fields"), 
						  NetworkIndexLevel.valueOf((String)t.getAttribute("indexLevel")),
								  ((Boolean)t.getAttribute(SolrTaskRebuildNetworkIdx.FORMCX2FILE)).booleanValue());
			case SYS_LOAD_NETWORK:
				return new CXNetworkLoadingTask (UUID.fromString(t.getResource()),
						(Boolean)t.getAttribute("isUpdate"), 
						(t.getAttribute("visibility") != null ? VisibilityType.valueOf((String)t.getAttribute("visibility")): null),
						(Set<String>)t.getAttribute("nodeIndexes"));
			case SYS_LOAD_CX2_NETWORK:
				return new CX2NetworkLoadingTask(UUID.fromString(t.getResource()),
						(Boolean)t.getAttribute("isUpdate"), 
						(t.getAttribute("visibility") != null ? VisibilityType.valueOf((String)t.getAttribute("visibility")): null),
						(Set<String>)t.getAttribute("nodeIndexes"));
			default:
				throw new NdexException("Unknow system task: " + t.getExternalId() + " - " + t.getTaskType());

		}
	}

	/**
	 * Reads a persisted visibility attribute, tolerating absent and unrecognised values.
	 *
	 * <p>Reconstruction runs during the startup queue replay, so a row carrying an unexpected value
	 * must not throw - the pre-attribute behaviour was a null visibility, and that is what an
	 * unusable value falls back to.
	 */
	static VisibilityType visibilityFromAttribute(Object attribute) {
		if (attribute == null) {
			return null;
		}
		try {
			return VisibilityType.valueOf(attribute.toString());
		} catch (IllegalArgumentException e) {
			return null;
		}
	}
}
