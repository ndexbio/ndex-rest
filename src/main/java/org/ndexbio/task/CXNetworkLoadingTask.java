package org.ndexbio.task;

import java.io.IOException;
import java.sql.SQLException;
import java.util.UUID;
import java.util.logging.Logger;

import org.apache.solr.client.solrj.SolrServerException;
import org.ndexbio.common.models.dao.postgresql.NetworkDAO;
import org.ndexbio.common.persistence.CXNetworkLoader;
import org.ndexbio.model.exceptions.NdexException;

public class CXNetworkLoadingTask implements NdexSystemTask {
	
	private static Logger logger = Logger.getLogger(CXNetworkLoadingTask.class.getName());
	
	private UUID networkId;
	private String ownerUserName;
	private boolean isUpdate;
	
	public CXNetworkLoadingTask (UUID networkUUID, String ownerName, boolean isUpdate) {
		this.networkId = networkUUID;
		this.ownerUserName = ownerName;
		this.isUpdate = isUpdate;
	}
	
	@Override
	public void run()  {
		
		try ( CXNetworkLoader loader = new CXNetworkLoader(networkId, ownerUserName, isUpdate) ) {
				loader.persistCXNetwork();
		} catch ( IOException | NdexException | SQLException | SolrServerException e1) {
			logger.severe("Error occured when loading network " + networkId + ": " + e1.getMessage());
			e1.printStackTrace();
			try (NetworkDAO dao= new NetworkDAO()) {
				dao.setErrorMessage(networkId, e1.getMessage());
			} catch (SQLException e) {
				logger.severe("Error occured when setting error message in network " + networkId + ": " + e1.getMessage());
				e.printStackTrace();
			}
		
		} 
	}

}

