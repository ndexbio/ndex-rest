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

	/**
	 * Discriminates a persisted row of this task from the other classes sharing
	 * SYS_SOLR_DELETE_NETWORK. Its presence is what routes a row back to this class on the
	 * startup queue replay.
	 */
	public final static String fileTypeAttr = "fileType";
	public final static String visibilityAttr = "visibility";

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
		// Everything run() branches on has to be persisted, or a row replayed after a restart comes
		// back as a different task with different behaviour - in particular losing the fileType
		// guard above and dropping a per-network core for a folder.
		t.setAttribute(fileTypeAttr, fileType.toString());
		t.setAttribute(SolrTaskDeleteNetwork.globalIdxAttr, Boolean.valueOf(globalIdxOnly));
		if (visibilityType != null) {
			t.setAttribute(visibilityAttr, visibilityType.toString());
		}
		return t;
	}

	@Override
	public TaskType getTaskType() {
		return taskType;
	}

	/**
	 * Reconstructs a single-file delete from its persisted row, for the startup queue replay.
	 *
	 * <p>Every attribute is read defensively: rows written before these attributes existed must
	 * reconstruct rather than fail, because a task that cannot be rebuilt would otherwise stop the
	 * server from starting.
	 */
	static SolrTaskDeleteFile fromTask(Task t) {
		return new SolrTaskDeleteFile(UUID.fromString(t.getResource()),
				NdexSystemTask.visibilityFromAttribute(t.getAttribute(visibilityAttr)),
				!Boolean.FALSE.equals(t.getAttribute(SolrTaskDeleteNetwork.globalIdxAttr)),
				fileTypeFromAttribute(t.getAttribute(fileTypeAttr)));
	}

	/**
	 * Unknown or absent values fall back to NETWORK, which is what the two-arg constructor has
	 * always meant, so legacy rows keep their original behaviour.
	 */
	static FileType fileTypeFromAttribute(Object attribute) {
		if (attribute == null) {
			return FileType.NETWORK;
		}
		try {
			return FileType.valueOf(attribute.toString());
		} catch (IllegalArgumentException e) {
			return FileType.NETWORK;
		}
	}
}
