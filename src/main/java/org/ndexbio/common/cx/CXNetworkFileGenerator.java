package org.ndexbio.common.cx;

import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;
import java.util.UUID;

import org.cxio.aspects.datamodels.NodesElement;
import org.cxio.core.interfaces.AspectElement;
import org.cxio.metadata.MetaDataCollection;
import org.cxio.metadata.MetaDataElement;
import org.ndexbio.common.models.dao.postgresql.NetworkDAO;
import org.ndexbio.model.cx.NamespacesElement;
import org.ndexbio.model.cx.NdexNetworkStatus;
import org.ndexbio.model.cx.Provenance;
import org.ndexbio.model.exceptions.NdexException;
import org.ndexbio.model.object.ProvenanceEntity;
import org.ndexbio.model.object.network.NetworkSummary;
import org.ndexbio.rest.Configuration;

import com.fasterxml.jackson.core.JsonParseException;
import com.fasterxml.jackson.databind.JsonMappingException;

public class CXNetworkFileGenerator {

	private UUID networkId;
	private NetworkSummary fullSummary;
	private MetaDataCollection metadata;
	private Provenance provenance;
	
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
	public CXNetworkFileGenerator(UUID networkUUID, NetworkDAO networkDao, Provenance provenanceHistory) throws JsonParseException, JsonMappingException, SQLException, IOException, NdexException {
		networkId = networkUUID;
		fullSummary = networkDao.getNetworkSummaryById(networkUUID);
		metadata = networkDao.getMetaDataCollection(networkUUID);
		provenance = provenanceHistory;
	}
	
/*	public CXNetworkFileGenerator(UUID networkUUID, NetworkDAO networkDao, MetaDataCollection metaDataCollection) 
			throws JsonParseException, JsonMappingException, SQLException, IOException, NdexException {
		networkId = networkUUID;
		fullSummary = networkDao.getNetworkSummaryById(networkUUID);
		metadata = metaDataCollection;
	}*/
	
	
	public CXNetworkFileGenerator(UUID networkUUID, NetworkSummary summary, MetaDataCollection metaDataCollection, ProvenanceEntity prov) {
		networkId = networkUUID;
		fullSummary = summary;
		metadata = metaDataCollection;
		provenance = new Provenance(prov);
	}
	
	// create a CX network using a tmp file name and return the temp file name;
	public String createNetworkFile() throws FileNotFoundException, IOException {
		String tmpFileName = Configuration.getInstance().getNdexRoot() + "/data/" + networkId + "/" + 
				 Thread.currentThread().getId() + "-" + Calendar.getInstance().getTimeInMillis();
		try (FileOutputStream out = new FileOutputStream(tmpFileName) ) {
			NdexCXNetworkWriter wtr = new NdexCXNetworkWriter(out);
			wtr.start();
			NdexNetworkStatus status = getNdexNetworkStatusFromSummary(fullSummary);
			
			// process NdexNetworkStatus metadata
			MetaDataElement e = metadata.getMetaDataElement(NdexNetworkStatus.ASPECT_NAME);
			if ( e == null) {
				  e = new MetaDataElement();
				  e.setName(NdexNetworkStatus.ASPECT_NAME);
				  e.setVersion("1.0");
				  if ( metadata.getMetaDataElement(NodesElement.ASPECT_NAME) != null && 
						  metadata.getMetaDataElement(NodesElement.ASPECT_NAME).getConsistencyGroup() !=null)
					  e.setConsistencyGroup(metadata.getMetaDataElement(NodesElement.ASPECT_NAME).getConsistencyGroup());
				  e.setElementCount(1L);
				  metadata.addAt(0, e);
			 }
			 e.setLastUpdate(fullSummary.getModificationTime().getTime());
					 
			 //write metadata first.
			 wtr.writeMetadata(metadata);
			 
			 metadata.remove(NdexNetworkStatus.ASPECT_NAME);
			
			 //write namespace first
			 if ( metadata.getMetaDataElement(NamespacesElement.ASPECT_NAME) != null ) {
				 wtr.startAspectFragment(NamespacesElement.ASPECT_NAME);
				 String aspectFileName = Configuration.getInstance().getNdexRoot() + "/data/" + networkId + "/aspects/" + NamespacesElement.ASPECT_NAME;
			 	 wtr.writeAspectElementsFromNdexAspectFile(aspectFileName);
				 wtr.endAspectFragment(); 
				 metadata.remove(NamespacesElement.ASPECT_NAME);	 
			 }

			 //write the NdexNetworkstatus aspect.
			 List<AspectElement> stat = new ArrayList<> (1);
			 stat.add(status);		 
			 wtr.writeAspectFragment(new CXAspectFragment(NdexNetworkStatus.ASPECT_NAME, stat));
			 
			 //write provenance history separately
			 if ( metadata.getMetaDataElement(Provenance.ASPECT_NAME) != null ) {
				 metadata.remove(Provenance.ASPECT_NAME);
				 if (provenance !=null )  {
					 List<AspectElement> prov = new ArrayList<> (1);
					 prov.add(provenance);		 
					 wtr.writeAspectFragment(new CXAspectFragment(Provenance.ASPECT_NAME, prov));	
				 }
			 }
				 
			 
			 //write all other aspects
			 for ( MetaDataElement metaElmt: metadata) {
				wtr.startAspectFragment(metaElmt.getName());
				String aspectFileName = Configuration.getInstance().getNdexRoot() + "/data/" + networkId + "/aspects/" + metaElmt.getName();
				wtr.writeAspectElementsFromNdexAspectFile(aspectFileName);
				wtr.endAspectFragment(); 
			 }
			 
			 wtr.end();
		}
		
		return tmpFileName;
	}
	
	
	public void reCreateCXFile ( ) throws FileNotFoundException, IOException {
		String tmpFileName = this.createNetworkFile();
			// rename the tmp file
		java.nio.file.Path src = Paths.get(tmpFileName);
		java.nio.file.Path tgt = Paths.get(Configuration.getInstance().getNdexRoot() + "/data/" + networkId + "/network.cx");
		Files.move(src, tgt, StandardCopyOption.REPLACE_EXISTING);  
	}
	
	   private static NdexNetworkStatus getNdexNetworkStatusFromSummary(NetworkSummary summary)  {
		   NdexNetworkStatus nstatus = new NdexNetworkStatus () ;
		  
	        nstatus.setCreationTime(summary.getCreationTime());
	        nstatus.setEdgeCount(summary.getEdgeCount());
	        nstatus.setNodeCount(summary.getNodeCount());
	        nstatus.setExternalId(summary.getExternalId().toString());
	        nstatus.setModificationTime(summary.getModificationTime());
			nstatus.setNdexServerURI(Configuration.getInstance().getHostURI());
	        nstatus.setOwner(summary.getOwner());
	        //nstatus.setPublished(isPublished);
	        
	        nstatus.setReadOnly(summary.getIsReadOnly());
	     
	        nstatus.setVisibility(summary.getVisibility());
	         
		   return nstatus;
	   }
	   
	   
}
