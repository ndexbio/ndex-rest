package org.ndexbio.task;

import java.io.IOException;
import java.sql.SQLException;
import java.util.UUID;

import org.apache.solr.client.solrj.SolrServerException;
import org.ndexbio.common.models.dao.postgresql.NetworkDAO;
import org.ndexbio.common.persistence.CXNetworkLoader;
import org.ndexbio.model.exceptions.NdexException;

public class CXNetworkLoadingTask implements NdexSystemTask {
	
	
	private UUID networkId;
	private String ownerUserName;
	
	public CXNetworkLoadingTask (UUID networkUUID, String ownerName) {
		this.networkId = networkUUID;
		this.ownerUserName = ownerName;
	}
	
	@Override
	public void run()  {
		
		try ( CXNetworkLoader loader = new CXNetworkLoader(networkId, ownerUserName) ) {
				loader.persistCXNetwork();
			/*} catch (IOException | NdexException | SQLException | SolrServerException e) {
				
			} */
		} catch ( IOException | NdexException | SQLException | SolrServerException e1) {
			//TODO: put something in the log.
			
			try (NetworkDAO dao= new NetworkDAO()) {
				dao.setErrorMessage(networkId, e1.getMessage());
			} catch (SQLException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
		
		} 
	}

}

