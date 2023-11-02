package org.ndexbio.rest.services;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.FileAttribute;
import java.nio.file.attribute.PosixFilePermission;
import java.nio.file.attribute.PosixFilePermissions;
import java.sql.SQLException;
import java.util.Set;
import java.util.UUID;

import org.apache.commons.io.IOUtils;
import org.apache.solr.client.solrj.SolrServerException;
import org.ndexbio.common.models.dao.postgresql.NetworkDAO;
import org.ndexbio.common.persistence.CXNetworkLoader;
import org.ndexbio.model.exceptions.NdexException;
import org.ndexbio.model.object.network.VisibilityType;
import org.ndexbio.rest.Configuration;

public class  NetworkStreamSaverThread extends Thread 
{
	UUID networkUUID;
	InputStream input;
	
	public NetworkStreamSaverThread(UUID networkId, InputStream in) {
		this.networkUUID = networkId;
		this.input = in;
	//	this.owner = ownerName;
	}
	
	@Override
	public void run() {
		String pathPrefix = Configuration.getInstance().getNdexRoot() + "/data/" + networkUUID.toString();

		// Create dir
		java.nio.file.Path dir = Paths.get(pathPrefix);
		Set<PosixFilePermission> perms = PosixFilePermissions.fromString("rwxrwxr-x");
		FileAttribute<Set<PosixFilePermission>> attr = PosixFilePermissions.asFileAttribute(perms);

		try {
			Files.createDirectory(dir, attr);

			// write content to file
			File cxFile = new File(pathPrefix + "/network.cx");
			java.nio.file.Files.copy(input, cxFile.toPath(), StandardCopyOption.REPLACE_EXISTING);

			long fileSize = cxFile.length();
			try (NetworkDAO dao = new NetworkDAO()) {
				dao.setNetworkFileSize(networkUUID, fileSize);
				dao.commit();
			} catch (SQLException | NdexException e2) {
				e2.printStackTrace();
				try (NetworkDAO dao = new NetworkDAO()) {
					dao.setErrorMessage(networkUUID, "Failed to set network file size: " + e2.getMessage());
					dao.unlockNetwork(networkUUID);
				} catch (SQLException e3) {
					e3.printStackTrace();
				}
			}
		} catch (IOException e) {
			e.printStackTrace();
			try (NetworkDAO dao = new NetworkDAO()) {
				dao.setErrorMessage(networkUUID, "Failed to create network file on the server: " + e.getMessage());
				dao.unlockNetwork(networkUUID);
			} catch (SQLException e2) {
				e2.printStackTrace();
			}

		}

		IOUtils.closeQuietly(input);
	      
		try (NetworkDAO dao = new NetworkDAO ()) {
			try ( CXNetworkLoader loader = new CXNetworkLoader(networkUUID, false,dao, VisibilityType.PRIVATE, null, 5000) ) {
						loader.persistCXNetwork();
			} catch ( IOException | NdexException | SQLException | RuntimeException | SolrServerException e1) {
				e1.printStackTrace();
				try {
					dao.setErrorMessage(networkUUID, e1.getMessage());
					dao.setFlag(networkUUID, "readonly", false);	 
					try {
						dao.updateNetworkVisibility(networkUUID, VisibilityType.PRIVATE, true);
					} catch (NdexException e) {
						System.out.print("Error when updating network visibility: " + e.getMessage());
						e.printStackTrace();
					}
					dao.unlockNetwork(networkUUID);
				} catch (SQLException e) {
					System.out.println("Failed to set Error for network " + networkUUID);
					e.printStackTrace();
				}
				
			} 
		} catch (SQLException e) {
				e.printStackTrace();
		}			
		
	}
}
