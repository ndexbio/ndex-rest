package org.ndexbio.task;

import java.io.IOException;
import java.sql.SQLException;
import java.util.UUID;
import java.util.logging.Logger;

import org.apache.solr.client.solrj.SolrServerException;
import org.ndexbio.common.models.dao.postgresql.NetworkDAO;
import org.ndexbio.common.persistence.CXNetworkLoader;
import org.ndexbio.model.exceptions.NdexException;
import org.ndexbio.model.exceptions.ObjectNotFoundException;
import org.ndexbio.model.object.Task;
import org.ndexbio.model.object.TaskType;


public class CXNetworkLoadingTask extends NdexSystemTask {
	
	private static Logger logger = Logger.getLogger(CXNetworkLoadingTask.class.getName());
	
	private UUID networkId;
	private String ownerUserName;
	private boolean isUpdate;
	
	private static final TaskType taskType = TaskType.SYS_LOAD_NETWORK;
	
	public CXNetworkLoadingTask (UUID networkUUID, String ownerName, boolean isUpdate) {
		super();
		this.networkId = networkUUID;
		this.ownerUserName = ownerName;
		this.isUpdate = isUpdate;
	}
	
	@Override
	public void run()  {
		
	  try (NetworkDAO dao = new NetworkDAO ()) {
		try ( CXNetworkLoader loader = new CXNetworkLoader(networkId, ownerUserName, isUpdate,dao) ) {
				loader.persistCXNetwork();
		} catch ( IOException | NdexException | SQLException | SolrServerException | RuntimeException e1) {
			logger.severe("Error occurred when loading network " + networkId + ": " + e1.getMessage());
			e1.printStackTrace();
			dao.setErrorMessage(networkId, e1.getMessage());
			try {
				dao.unlockNetwork(networkId);
			} catch (ObjectNotFoundException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
				logger.severe("Can't find network " + networkId + " in the server to delete.");
			}
			
			/*} catch (SQLException e) {
				logger.severe("Error occurred when setting error message in network " + networkId + ": " + e1.getMessage());
				e.printStackTrace();
			} */
		
		} 
	  } catch (SQLException e) {
		e.printStackTrace();
		logger.severe("Failed to create NetworkDAO object: " + e.getMessage());
	  }
	}


	@Override
	public Task createTask() {
		Task task = super.createTask();
	    task.setResource(networkId.toString());
			
	    return task;	
	
	}

	@Override
	public TaskType getTaskType() {
		return taskType;
	}

}

