package org.ndexbio.task;

import java.io.IOException;
import java.util.UUID;

import org.apache.solr.client.solrj.SolrServerException;
import org.ndexbio.common.solr.GlobalNetworkIndexManager;
import org.ndexbio.common.solr.NetworkGlobalIndexManager;
import org.ndexbio.common.solr.SingleNetworkSolrIdxManager;
import org.ndexbio.model.exceptions.NdexException;
import org.ndexbio.model.object.Task;
import org.ndexbio.model.object.TaskType;
import org.ndexbio.model.object.network.VisibilityType;
import org.ndexbio.rest.Configuration;

public class SolrTaskDeleteNetwork extends NdexSystemTask {

//private static Logger logger = Logger.getLogger(CXNetworkLoadingTask.class.getName());
	
	private UUID networkId;
    private static final TaskType taskType = TaskType.SYS_SOLR_DELETE_NETWORK;
    private boolean globalIdxOnly;
    public final static String globalIdxAttr = "globalIdxOnly";
	private final VisibilityType visibilityType;
	
	public SolrTaskDeleteNetwork (UUID networkUUID) {
		this(networkUUID, false, VisibilityType.PRIVATE);
	}
	
	public SolrTaskDeleteNetwork (UUID networkUUID, boolean globalOnly, VisibilityType visibilityType) {
		super();
		this.networkId = networkUUID;
		this.globalIdxOnly = globalOnly;
		this.visibilityType = visibilityType;

	}
	
	@Override
	public void run() throws NdexException, SolrServerException, IOException  {
		String id = networkId.toString();
		
		try(GlobalNetworkIndexManager globalIdx = Configuration.getInstance().getSolrObjectFactory().getGlobalNetworkIndexManager()) {
			globalIdx.delete(id, visibilityType);
			if (!globalIdxOnly) {
				try (SingleNetworkSolrIdxManager idxManager = new SingleNetworkSolrIdxManager(id)) {
					idxManager.dropIndex();
				}		
			}
		}	
		
	}


	@Override
	public Task createTask() {
		Task t = super.createTask();
		t.setResource(networkId.toString());
		t.setAttribute(globalIdxAttr, Boolean.valueOf(globalIdxOnly));
		return t;
	}

	@Override
	public TaskType getTaskType() {
		return taskType;
	}
}
