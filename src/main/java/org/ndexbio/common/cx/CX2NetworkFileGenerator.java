package org.ndexbio.common.cx;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;
import java.util.UUID;

import org.ndexbio.common.models.dao.postgresql.NetworkDAO;
import org.ndexbio.common.persistence.CX2NetworkLoader;
import org.ndexbio.cx2.aspect.element.core.CxAttributeDeclaration;
import org.ndexbio.cx2.aspect.element.core.CxEdge;
import org.ndexbio.cx2.aspect.element.core.CxMetadata;
import org.ndexbio.cx2.aspect.element.core.CxNetworkAttribute;
import org.ndexbio.cx2.aspect.element.core.CxNode;
import org.ndexbio.cx2.aspect.element.core.CxVisualProperty;
import org.ndexbio.cx2.io.CXWriter;
import org.ndexbio.cxio.core.NdexCXNetworkWriter;
import org.ndexbio.cxio.metadata.MetaDataCollection;
import org.ndexbio.cxio.metadata.MetaDataElement;
import org.ndexbio.model.exceptions.NdexException;
import org.ndexbio.rest.Configuration;

import com.fasterxml.jackson.core.JsonParseException;
import com.fasterxml.jackson.databind.JsonMappingException;

/**
 * Regenerate CX2 files from all aspects stored in the file system.
 * @author jingchen
 *
 */
public class CX2NetworkFileGenerator {

	private String networkId;
	private List<CxMetadata> metadata;
	
	//predefined aspect order in CX2
	private static final String[] predefinedOrder = {
			CxAttributeDeclaration.ASPECT_NAME, CxNetworkAttribute.ASPECT_NAME,
			CxNode.ASPECT_NAME, CxEdge.ASPECT_NAME, CxVisualProperty.ASPECT_NAME};	
	
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
	public CX2NetworkFileGenerator(UUID networkUUID, NetworkDAO networkDao) throws JsonParseException, JsonMappingException, SQLException, IOException, NdexException {
		this(networkUUID, networkDao.getCx2MetaDataList(networkUUID));
		
	}	
	
	public CX2NetworkFileGenerator(UUID networkUUID,  List<CxMetadata> metaDataList) {
		networkId = networkUUID.toString();
		metadata = metaDataList;
	}

	private static List<CxMetadata> getRemainingAspects(List<CxMetadata> metadataList) {
		List<CxMetadata> remainingAspect = new ArrayList<> ();
		for ( CxMetadata m: metadataList) {
			if ( !isPreorderedAspect (m)) {
				remainingAspect.add(m);
			}
		}
		return remainingAspect;
	}
	
	private static boolean isPreorderedAspect(CxMetadata e) {
		for ( String s : predefinedOrder) {
			if ( s.equals(e.getName()))
				return true;
		}
		return false;
	}
	
	public List<CxMetadata> getMetaData() { return metadata;}
	
	
	// create a CX2 network using a tmp file name and return the temp file name;
	public String createCX2File() throws FileNotFoundException, IOException, NdexException {
		String tmpFileName = Configuration.getInstance().getNdexRoot() + "/data/" + networkId + "/" + 
				 Thread.currentThread().getId() + "-" + Calendar.getInstance().getTimeInMillis();
		
		List<CxMetadata> remainingAspects = getRemainingAspects(metadata);
		try (FileOutputStream out = new FileOutputStream(tmpFileName) ) {
			CXWriter wtr = new CXWriter(out, false);
	
			 //write metadata first.
			 wtr.writeMetadata(metadata);
			 
			 
			 //write aspects that needs to be in order
			 for (String aspectName : predefinedOrder) {
				 writeAspect(aspectName, wtr);
			 }
			 
			 for (CxMetadata md : remainingAspects) {
				 writeAspect(md.getName(), wtr);
			 }
			 
			 wtr.finish();
		}
		
		return tmpFileName;
	}
	
	private void writeAspect(String aspectName, CXWriter wtr) throws NdexException, IOException {
		 String aspectFileName = Configuration.getInstance().getNdexRoot() + "/data/" + networkId + "/"+
				 CX2NetworkLoader.cx2AspectDirName+ "/" + aspectName;
		 File f = new File (aspectFileName);
		 if (f.exists()) {
			 wtr.writeAspectFromAspectFile(aspectName, aspectFileName);
		 }
	}
	
	/*
	public void reCreateCX2File ( ) throws FileNotFoundException, IOException, NdexException {
		String tmpFileName = createCX2File();
			// rename the tmp file
		java.nio.file.Path src = Paths.get(tmpFileName);
		java.nio.file.Path tgt = Paths.get(Configuration.getInstance().getNdexRoot() + "/data/" + networkId + "/network.cx");
		Files.move(src, tgt, StandardCopyOption.REPLACE_EXISTING);  
	} */
	
	//Asyncronous version of CX file recreation. 
	//TODO:how to catch errors in the runner and put it into db.
	/*private static void reCreateCXFileAsync ( String uuidStr, MetaDataCollection metadataCollection) {
		
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
	} */
	
}
