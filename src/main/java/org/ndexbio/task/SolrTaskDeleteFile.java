package org.ndexbio.task;

import org.apache.solr.client.solrj.SolrServerException;
import org.ndexbio.common.solr.FolderIndexManager;
import org.ndexbio.common.solr.GlobalNetworkIndexManager;
import org.ndexbio.common.solr.SingleNetworkSolrIdxManager;
import org.ndexbio.common.solr.SolrObjectFactory;
import org.ndexbio.model.exceptions.NdexException;
import org.ndexbio.model.object.FileType;
import org.ndexbio.model.object.Task;
import org.ndexbio.model.object.TaskType;
import org.ndexbio.model.object.network.VisibilityType;
import org.ndexbio.rest.Configuration;

import java.io.IOException;
import java.util.UUID;

public class SolrTaskDeleteFile extends NdexSystemTask {

//private static Logger logger = Logger.getLogger(CXNetworkLoadingTask.class.getName());

	private UUID fileId;
    private static final TaskType taskType = TaskType.SYS_SOLR_DELETE_NETWORK;
	private final VisibilityType visibilityType;
	private final boolean globalIdxOnly ;
	private final FileType fileType;

	public SolrTaskDeleteFile(UUID fileId, VisibilityType visibilityType) {
		this(fileId, visibilityType, true, FileType.NETWORK);
	}
	public SolrTaskDeleteFile(UUID fileId, VisibilityType visibilityType, boolean globalIdxOnly,
			FileType fileType) {
		super();
		this.fileId = fileId;
		this.visibilityType = visibilityType;
		this.globalIdxOnly = globalIdxOnly;
		this.fileType = fileType;
	}


	@Override
	public void run() throws NdexException, SolrServerException, IOException  {
		String id = fileId.toString();
		
		try(FolderIndexManager globalIdx = Configuration.getInstance().getSolrObjectFactory().getFolderIndexManager()) {
			globalIdx.delete(id, visibilityType);
			// Only networks have a per-network query core. Asking Solr to unload one for a
			// folder or shortcut can never succeed - it just costs a round trip and an error.
			if (!globalIdxOnly && fileType == FileType.NETWORK) {
				try (SingleNetworkSolrIdxManager idxManager = Configuration.getInstance().getSolrObjectFactory().getSingleNetworkSolrIdxManager(id)) {
					idxManager.dropIndex();
				}
			}
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
