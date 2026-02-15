package org.ndexbio.task;

import org.apache.solr.client.solrj.SolrServerException;
import org.ndexbio.common.solr.FolderIndexManager;
import org.ndexbio.common.solr.GlobalNetworkIndexManager;
import org.ndexbio.common.solr.SingleNetworkSolrIdxManager;
import org.ndexbio.model.exceptions.NdexException;
import org.ndexbio.model.object.FileType;
import org.ndexbio.model.object.Task;
import org.ndexbio.model.object.TaskType;
import org.ndexbio.model.object.network.VisibilityType;

import java.io.IOException;
import java.util.UUID;

public class SolrTaskDeleteFile extends NdexSystemTask {

//private static Logger logger = Logger.getLogger(CXNetworkLoadingTask.class.getName());

	private UUID fileId;
    private static final TaskType taskType = TaskType.SYS_SOLR_DELETE_NETWORK;
	private final VisibilityType visibilityType;

	public SolrTaskDeleteFile(UUID fileId, VisibilityType visibilityType) {
		super();
		this.fileId = fileId;
		this.visibilityType = visibilityType;
	}

	@Override
	public void run() throws NdexException, SolrServerException, IOException  {
		String id = fileId.toString();
		
		try(FolderIndexManager globalIdx = new FolderIndexManager(visibilityType)) {
			globalIdx.delete(id);
		}
		
	}


	@Override
	public Task createTask() {
		Task t = super.createTask();
		t.setResource(fileId.toString());
		return t;
	}

	@Override
	public TaskType getTaskType() {
		return taskType;
	}
}
