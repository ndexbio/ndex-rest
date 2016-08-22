package org.ndexbio.task;

import java.io.FileInputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.sql.SQLException;
import java.util.UUID;

import org.ndexbio.common.models.dao.postgresql.NetworkDocDAO;
import org.ndexbio.model.exceptions.NdexException;
import org.ndexbio.rest.Configuration;

public class CXNetworkProcessor implements NdexSystemTask {
	
	
	private UUID networkId;
	
	public CXNetworkProcessor (UUID networkUUID) {
		this.networkId = networkUUID;
	}
	
	@Override
	public void run() throws SQLException, NdexException, IOException {
		// TODO Auto-generated method stub
		try (NetworkDocDAO networkDao = new NetworkDocDAO() ) {
			UUID ownerId = networkDao.getNetworkOwner(networkId);
			String pathPrefix = Configuration.getInstance().getNdexRoot() + "/data/" + networkId;
			   
			//Create dir for aspects
			String aspectsRoot =  pathPrefix + "/" + networkId + "/aspects";
			Path dir = Paths.get(aspectsRoot);
			Files.createDirectory(dir);
			
			 //start parsing the CX document 
			String cxFilePath = pathPrefix + "/" + networkId + ".cx";   
			try (FileInputStream in = new FileInputStream(cxFilePath) ){
				
			}
			
		}
		
	}

}
