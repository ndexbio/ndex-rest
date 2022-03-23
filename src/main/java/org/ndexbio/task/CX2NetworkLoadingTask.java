package org.ndexbio.task;

import java.io.IOException;
import java.sql.SQLException;
import java.util.Set;
import java.util.UUID;
import java.util.logging.Logger;

import org.apache.solr.client.solrj.SolrServerException;
import org.ndexbio.common.models.dao.postgresql.NetworkDAO;
import org.ndexbio.common.persistence.CX2NetworkLoader;
import org.ndexbio.model.exceptions.NdexException;
import org.ndexbio.model.object.TaskType;
import org.ndexbio.model.object.network.VisibilityType;


public class CX2NetworkLoadingTask extends CXNetworkLoadingTask {
	
	private static Logger logger = Logger.getLogger(CX2NetworkLoadingTask.class.getName());
	
	private static final TaskType taskType = TaskType.SYS_LOAD_CX2_NETWORK;
	
	public CX2NetworkLoadingTask (UUID networkUUID,  boolean isUpdate, 
			VisibilityType visibility, Set<String> nodeAttributeIndexList) {
		super(networkUUID, isUpdate, visibility, nodeAttributeIndexList);
	}
	
	@Override
	public void run()  {
		
	  try (NetworkDAO dao = new NetworkDAO ()) {
		try ( CX2NetworkLoader loader = new CX2NetworkLoader(getNetworkId(), isUpdate,dao, visibility, nodeAttributeIndexList, 0) ) {
				loader.persistCXNetwork();
		} catch ( IOException | NdexException | SQLException | RuntimeException | SolrServerException e1) {
			logger.severe("Error occurred when loading network " + networkId + ": " + e1.getMessage());
			e1.printStackTrace();
			dao.setFlag(networkId, "is_validated", true);
			dao.setFlag(networkId, "iscomplete", true);
			dao.setFlag(networkId,"readonly", false);
			try {
				dao.updateNetworkVisibility(networkId, VisibilityType.PRIVATE, true);
			} catch (NdexException e) {
				System.out.print("Error when updating network visibility: " + e.getMessage());
				e.printStackTrace();
			}
			dao.setErrorMessage(networkId, e1.getMessage() == null? "Unknow server error.": e1.getMessage() );
			dao.unlockNetwork(networkId);
		} 
	  } catch (SQLException e) {
		e.printStackTrace();
		logger.severe("Failed to create NetworkDAO object: " + e.getMessage());
	  }
	}


	@Override
	public TaskType getTaskType() {
		return taskType;
	}

}

