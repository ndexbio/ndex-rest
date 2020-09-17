package org.ndexbio.server.migration.v2;

import java.io.File;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.List;
import java.util.UUID;

import org.apache.commons.io.FileUtils;
import org.ndexbio.common.access.NdexDatabase;
import org.ndexbio.common.models.dao.postgresql.NetworkDAO;
import org.ndexbio.common.persistence.CX2NetworkLoader;
import org.ndexbio.common.persistence.CXToCX2ServerSideConverter;
import org.ndexbio.cx2.aspect.element.core.CxMetadata;
import org.ndexbio.cxio.metadata.MetaDataCollection;
import org.ndexbio.model.exceptions.NdexException;
import org.ndexbio.rest.Configuration;

public class CX2NetworkCreator {
	
	
	public CX2NetworkCreator() {
		
	}
	
	public static void main(String[] args) throws Exception {	
	
		Configuration configuration = Configuration.createInstance();

		NdexDatabase.createNdexDatabase(configuration.getDBURL(), configuration.getDBUser(),
			configuration.getDBPasswd(), 10);
		
		String rootPath = Configuration.getInstance().getNdexRoot() + "/data/";
		
		try (Connection conn = NdexDatabase.getInstance().getConnection()) {
		
		String sqlStr = "select \"UUID\" from network where is_deleted=false and error is null";
		
		int i = 0;
		try (NetworkDAO networkdao = new NetworkDAO() ) {
			try (PreparedStatement pst = conn.prepareStatement(sqlStr)) {
				try (ResultSet rs = pst.executeQuery()) {
					while ( rs.next()) {
						UUID networkUUID = (UUID)rs.getObject(1);
						System.out.print("Recreating cx2 for " + networkUUID.toString() + " ... ");
						// delete cx2 aspect folder if exists
						File f = new File ( rootPath + networkUUID.toString() + "/" + CX2NetworkLoader.cx2AspectDirName);
						if ( f.exists()) {
							FileUtils.deleteDirectory(f);
							System.out.print(" aspect folder deleted ... ");
						}
						MetaDataCollection mc = networkdao.getMetaDataCollection(networkUUID);
						try {
							CXToCX2ServerSideConverter converter = new CXToCX2ServerSideConverter(rootPath,mc,
								networkUUID.toString(),null,true);
							List<CxMetadata> cx2mc = converter.convert();
							networkdao.setCxMetadata(networkUUID, cx2mc);
							if ( converter.getWarning().size() > 0) 
								networkdao.setWarning(networkUUID, converter.getWarning());
						} catch ( NdexException e) {
							networkdao.setErrorMessage(networkUUID, e.getMessage());
						}
						networkdao.commit();
						i++;
						System.out.println( " done (" + i + ").");
					}
				}
			}	
		}
		}
	}
	
}
