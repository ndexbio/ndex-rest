package org.ndexbio.task;

import java.io.FileInputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.sql.SQLException;
import java.util.UUID;

import org.ndexbio.common.models.dao.postgresql.NetworkDocDAO;
import org.ndexbio.common.persistence.orientdb.CXNetworkLoader;
import org.ndexbio.model.exceptions.NdexException;
import org.ndexbio.rest.Configuration;

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
