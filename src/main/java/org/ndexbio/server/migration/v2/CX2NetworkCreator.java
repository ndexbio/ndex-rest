package org.ndexbio.server.migration.v2;

import java.io.File;
import java.io.IOException;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.UUID;

import org.apache.commons.io.FileUtils;
import org.apache.solr.client.solrj.SolrServerException;
import org.ndexbio.common.access.NdexDatabase;
import org.ndexbio.common.models.dao.postgresql.NetworkDAO;
import org.ndexbio.common.persistence.CX2NetworkLoader;
import org.ndexbio.common.persistence.CXNetworkLoader;
import org.ndexbio.common.persistence.CXToCX2ServerSideConverter;
import org.ndexbio.common.solr.NetworkGlobalIndexManager;
import org.ndexbio.common.solr.SingleNetworkSolrIdxManager;
import org.ndexbio.common.util.Util;
import org.ndexbio.cx2.aspect.element.core.CxMetadata;
import org.ndexbio.cxio.metadata.MetaDataCollection;
import org.ndexbio.model.exceptions.NdexException;
import org.ndexbio.rest.Configuration;

public class CX2NetworkCreator {
	
	private static final int edgeCountLimit = 20*1000000; 
	public CX2NetworkCreator() {
		
	}
	
	public static void main(String[] args) throws Exception {		

		Configuration configuration = Configuration.createInstance();

		NdexDatabase.createNdexDatabase(configuration.getDBURL(), configuration.getDBUser(),
				configuration.getDBPasswd(), 10);

		String rootPath = Configuration.getInstance().getNdexRoot() + "/data/";

		try (NetworkGlobalIndexManager globalIdx = new NetworkGlobalIndexManager()) {
			if (args.length == 1) {
				try (NetworkDAO networkdao = new NetworkDAO()) {
					UUID networkUUID = UUID.fromString(args[0]);
					createCX2forNetwork(rootPath, networkUUID, networkdao, globalIdx);
				}
				return;
			}

			try (Connection conn = NdexDatabase.getInstance().getConnection()) {

				String sqlStr = "select \"UUID\" from network where is_deleted=false and iscomplete and error is null";

				int i = 0;
				try (NetworkDAO networkdao = new NetworkDAO()) {
					try (PreparedStatement pst = conn.prepareStatement(sqlStr)) {
						try (ResultSet rs = pst.executeQuery()) {
							while (rs.next()) {
								UUID networkUUID = (UUID) rs.getObject(1);
								
								createCX2forNetwork(rootPath, networkUUID, networkdao,
										globalIdx);

								i++;
								System.out.println(" done (" + i + ").");
							}
						}
					}
				}
			}
		}
	}
	
	
	private static void createCX2forNetwork(String rootPath, UUID networkUUID, NetworkDAO networkdao, 
			 NetworkGlobalIndexManager globalIdx) throws IOException, SQLException, NdexException, SolrServerException {
		
		boolean isSingleNetwork = networkdao.getSubNetworkId(networkUUID).isEmpty();
		
		int edgeCount = networkdao.getNetworkEdgeCount(networkUUID);

		if ( isSingleNetwork && edgeCount <= edgeCountLimit) {
			System.out.print("Recreating cx2 for " + networkUUID.toString() + " ... ");
			// delete cx2 aspect folder if exists
			File f = new File(
					rootPath + networkUUID.toString() + "/" + CX2NetworkLoader.cx2AspectDirName);
			if (f.exists()) {
				FileUtils.deleteDirectory(f);
				System.out.print(" aspect folder deleted ... ");
			}
			
			f = new File (rootPath + networkUUID.toString() + "/" + "net2.cx");
			if ( f.exists())
				f.delete();
			
			MetaDataCollection mc = networkdao.getMetaDataCollection(networkUUID);
			try {
				CXToCX2ServerSideConverter converter = new CXToCX2ServerSideConverter(rootPath, mc,
						networkUUID.toString(), null, true);
				List<CxMetadata> cx2mc = converter.convert();
				networkdao.setCxMetadata(networkUUID, cx2mc);
				if (converter.getWarning().size() > 0) {
					List<String> warnings = new java.util.ArrayList<>(
							networkdao.getWarnings(networkUUID));
					warnings.removeIf(n -> n.startsWith(CXToCX2ServerSideConverter.messagePrefix));
					warnings.addAll(converter.getWarning());
					networkdao.setWarning(networkUUID, warnings);
				}
			} catch (NdexException | RuntimeException e) {
				networkdao.setErrorMessage(networkUUID,
						CXToCX2ServerSideConverter.messagePrefix + e.getMessage());
				System.out.println(networkUUID.toString() + " has error. Error message is: " + e.getMessage());
				globalIdx.deleteNetwork(networkUUID.toString());
				try (SingleNetworkSolrIdxManager networkIdx = new SingleNetworkSolrIdxManager(networkUUID.toString())) {
					networkIdx.dropIndex();
				}
			}
		} else {
			List<String> warnings = new java.util.ArrayList<>(
					networkdao.getWarnings(networkUUID));
			warnings.removeIf(n -> n.startsWith(CXToCX2ServerSideConverter.messagePrefix));
		
			String message = null;
			if ( edgeCount > edgeCountLimit) {
				message = "CX2 network won't be generated on networks that have more than " + edgeCountLimit + " edges.";				
			} else {
				message = "CX2 network won't be generated on Cytoscape network collection." ;
			}
			warnings.add(CXToCX2ServerSideConverter.messagePrefix + message);				
			networkdao.setWarning(networkUUID, warnings);
			System.out.println(networkUUID.toString() + ": " + message);
		}
		
		// gzip the archived cx2 file if it still exists
		String cx1ArchiveFilePath = rootPath + networkUUID.toString() + "/" + CXNetworkLoader.CX1ArchiveFileName;
		File f = new File(cx1ArchiveFilePath);
		if ( f.exists()) {
			System.out.println("CX1 archive is gzipped.");
			Util.aSyncCompressGZIP(cx1ArchiveFilePath);
		}
		
		networkdao.commit();
	}
	
}
