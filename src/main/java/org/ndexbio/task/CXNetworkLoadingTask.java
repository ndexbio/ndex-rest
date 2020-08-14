package org.ndexbio.task;

import java.io.IOException;
import java.sql.SQLException;
import java.util.Set;
import java.util.UUID;
import java.util.logging.Logger;

import org.ndexbio.common.models.dao.postgresql.NetworkDAO;
import org.ndexbio.common.persistence.CXNetworkLoader;
import org.ndexbio.model.exceptions.NdexException;
import org.ndexbio.model.object.Task;
import org.ndexbio.model.object.TaskType;
import org.ndexbio.model.object.network.VisibilityType;


public class CXNetworkLoadingTask extends NdexSystemTask {
	
	private static Logger logger = Logger.getLogger(CXNetworkLoadingTask.class.getName());
	
	protected UUID networkId;
//	private String ownerUserName;
	protected boolean isUpdate;
	protected VisibilityType visibility;
	protected Set<String> nodeAttributeIndexList; 
	private static final TaskType taskType = TaskType.SYS_LOAD_NETWORK;
	
	public CXNetworkLoadingTask (UUID networkUUID, /*String ownerName,*/ boolean isUpdate, 
			VisibilityType visibility, Set<String> nodeAttributeIndexList) {
		super();
		this.networkId = networkUUID;
	//	this.ownerUserName = ownerName;
		this.isUpdate = isUpdate;
		this.visibility = visibility;
		this.nodeAttributeIndexList = nodeAttributeIndexList;
	}
	
	@Override
	public void run()  {
		
	  try (NetworkDAO dao = new NetworkDAO ()) {
		try ( CXNetworkLoader loader = new CXNetworkLoader(networkId, /*ownerUserName,*/ isUpdate,dao, visibility, nodeAttributeIndexList, 0) ) {
				loader.persistCXNetwork();
		} catch ( IOException | NdexException | SQLException | RuntimeException e1) {
			logger.severe("Error occurred when loading network " + networkId + ": " + e1.getMessage());
			e1.printStackTrace();
			dao.setFlag(networkId, "is_validated", true);
			dao.setFlag(networkId, "iscomplete", true);
			dao.setErrorMessage(networkId, e1.getMessage());
			dao.unlockNetwork(networkId);
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
		task.setAttribute("visibility", visibility);
		task.setAttribute("nodeIndexes", this.nodeAttributeIndexList);
	//	task.setAttribute("owner", ownerUserName);
		task.setAttribute("isUpdate", Boolean.valueOf(isUpdate));
	    return task;	
	
	}

	@Override
	public TaskType getTaskType() {
		return taskType;
	}
	
	public UUID getNetworkId () { return networkId;}

}

