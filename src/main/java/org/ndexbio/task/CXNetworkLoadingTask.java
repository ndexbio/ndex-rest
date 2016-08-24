package org.ndexbio.task;

import java.util.UUID;

import org.ndexbio.common.persistence.orientdb.CXNetworkLoader;

public class CXNetworkLoadingTask implements NdexSystemTask {
	
	
	private UUID networkId;
	private String ownerUserName;
	
	public CXNetworkLoadingTask (UUID networkUUID, String ownerName) {
		this.networkId = networkUUID;
		this.ownerUserName = ownerName;
	}
	
	@Override
	public void run() throws Exception {
		
		try ( CXNetworkLoader loader = new CXNetworkLoader(networkId, ownerUserName) ) {
			loader.persistCXNetwork();
		}	
	}

}
