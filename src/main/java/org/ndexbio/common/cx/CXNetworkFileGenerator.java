package org.ndexbio.common.cx;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.sql.SQLException;
import java.util.Calendar;
import java.util.UUID;

import org.ndexbio.common.models.dao.postgresql.NetworkDAO;
import org.ndexbio.cxio.core.NdexCXNetworkWriter;
import org.ndexbio.cxio.metadata.MetaDataCollection;
import org.ndexbio.cxio.metadata.MetaDataElement;
import org.ndexbio.model.cx.NamespacesElement;
import org.ndexbio.model.cx.Provenance;
import org.ndexbio.model.exceptions.NdexException;
import org.ndexbio.rest.Configuration;

import com.fasterxml.jackson.core.JsonParseException;
import com.fasterxml.jackson.databind.JsonMappingException;

public class CXNetworkFileGenerator {

	private UUID networkId;
//	private NetworkSummary fullSummary;
	private MetaDataCollection metadata;
//	private Provenance provenance;
	
	/**
	 * We need the caller to pass in the NetworkDAO object so that the generator can see the changes have been
	 * made in the current transaction.
	 * @param netowrkUUID
	 * @param networkDao
	 * @throws IOException 
	 * @throws SQLException 
	 * @throws JsonMappingException 
	 * @throws JsonParseException 
	 * @throws NdexException 
	 */
	public CXNetworkFileGenerator(UUID networkUUID, NetworkDAO networkDao /*, Provenance provenanceHistory*/) throws JsonParseException, JsonMappingException, SQLException, IOException, NdexException {
		networkId = networkUUID;
//		fullSummary = networkDao.getNetworkSummaryById(networkUUID);
		metadata = networkDao.getMetaDataCollection(networkUUID);
//		provenance = provenanceHistory;
	}	
	
	public CXNetworkFileGenerator(UUID networkUUID, /*NetworkSummary summary,*/ MetaDataCollection metaDataCollection /*, ProvenanceEntity prov*/) {
		networkId = networkUUID;
//		fullSummary = summary;
		metadata = metaDataCollection;
//		provenance = new Provenance(prov);
	}
	
	public MetaDataCollection getMetaData() { return metadata;}
	
	// create a CX network using a tmp file name and return the temp file name;
	public static String createNetworkFile(String uuidStr, MetaDataCollection metadataCollection) throws FileNotFoundException, IOException {
		String tmpFileName = Configuration.getInstance().getNdexRoot() + "/data/" + uuidStr + "/" + 
				 Thread.currentThread().getId() + "-" + Calendar.getInstance().getTimeInMillis();
		try (FileOutputStream out = new FileOutputStream(tmpFileName) ) {
			NdexCXNetworkWriter wtr = new NdexCXNetworkWriter(out);
			wtr.start();

			 //	NdexNetworkStatus status = getNdexNetworkStatusFromSummary(fullSummary);
			
	/*		// process NdexNetworkStatus metadata
			MetaDataElement e = metadata.getMetaDataElement(NdexNetworkStatus.ASPECT_NAME);
			if ( e == null) {
				  e = new MetaDataElement();
				  e.setName(NdexNetworkStatus.ASPECT_NAME);
				  e.setVersion("1.0");
				  if ( metadata.getMetaDataElement(NodesElement.ASPECT_NAME) != null && 
						  metadata.getMetaDataElement(NodesElement.ASPECT_NAME).getConsistencyGroup() !=null)
					  e.setConsistencyGroup(metadata.getMetaDataElement(NodesElement.ASPECT_NAME).getConsistencyGroup());
				  e.setElementCount(Long.valueOf(1L));
				  metadata.add(e);
			 }
//			 e.setLastUpdate(fullSummary.getModificationTime().getTime()); */
		
			 // for back compatibility reason. System used to generate provenance on the fly.
			 String provenanceAspectFileName = Configuration.getInstance().getNdexRoot() + "/data/" + uuidStr + "/aspects/" + Provenance.ASPECT_NAME;
			 File f = new File(provenanceAspectFileName);
			 if(!f.exists() ) { 
				 metadataCollection.remove(Provenance.ASPECT_NAME);
			 }
			
			 //write metadata first.
			 wtr.writeMetadata(metadataCollection);
			 
			
			 //write namespace first
			 if ( metadataCollection.getMetaDataElement(NamespacesElement.ASPECT_NAME) != null ) {
				 wtr.startAspectFragment(NamespacesElement.ASPECT_NAME);
				 String aspectFileName = Configuration.getInstance().getNdexRoot() + "/data/" + uuidStr + "/aspects/" + NamespacesElement.ASPECT_NAME;
			 	 wtr.writeAspectElementsFromNdexAspectFile(aspectFileName);
				 wtr.endAspectFragment(); 
				 metadataCollection.remove(NamespacesElement.ASPECT_NAME);	 
			 }
		 
			 //write provenance history separately
		/*	 if ( metadata.getMetaDataElement(Provenance.ASPECT_NAME) != null ) {
				 metadata.remove(Provenance.ASPECT_NAME);
				 if (provenance !=null )  {
					 List<AspectElement> prov = new ArrayList<> (1);
					 prov.add(provenance);		 
					 wtr.writeAspectFragment(new CXAspectFragment(Provenance.ASPECT_NAME, prov));	
				 }
			 }
		*/		 
			 
			 //write all other aspects
			 for ( MetaDataElement metaElmt: metadataCollection) {
				wtr.startAspectFragment(metaElmt.getName());
				String aspectFileName = Configuration.getInstance().getNdexRoot() + "/data/" + uuidStr + "/aspects/" + metaElmt.getName();
				wtr.writeAspectElementsFromNdexAspectFile(aspectFileName);
				wtr.endAspectFragment(); 
			 }
			 
			 wtr.end();
		}
		
		return tmpFileName;
	}
	
	
	public void reCreateCXFile ( ) throws FileNotFoundException, IOException {
		String tmpFileName = createNetworkFile(networkId.toString(), metadata);
			// rename the tmp file
		java.nio.file.Path src = Paths.get(tmpFileName);
		java.nio.file.Path tgt = Paths.get(Configuration.getInstance().getNdexRoot() + "/data/" + networkId + "/network.cx");
		Files.move(src, tgt, StandardCopyOption.REPLACE_EXISTING);  
	}
	
	//Asyncronous version of CX file recreation. 
	//TODO:how to catch errors in the runner and put it into db.
	public static void reCreateCXFileAsync ( String uuidStr, MetaDataCollection metadataCollection) {
		
		Thread t = new Thread() {
		    @Override
			public void run() {
		    	try {
		    		String tmpFileName = createNetworkFile(uuidStr, metadataCollection);
		    		// rename the tmp file
		    		java.nio.file.Path src = Paths.get(tmpFileName);
		    		java.nio.file.Path tgt = Paths.get(Configuration.getInstance().getNdexRoot() + "/data/" + uuidStr + "/network.cx");
				
					Files.move(src, tgt, StandardCopyOption.REPLACE_EXISTING);
				} catch (IOException e) {
					System.out.println("Error occurred when re-creating cx file for network " + uuidStr.toString());
					e.printStackTrace();
				}  
				
		    }
		};
		t.start();
	}
	
	   
}
