package org.ndexbio.common.persistence;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;
import java.util.UUID;

import org.cxio.aspects.datamodels.EdgesElement;
import org.cxio.aspects.datamodels.NetworkAttributesElement;
import org.cxio.aspects.datamodels.NodesElement;
import org.cxio.aspects.datamodels.SubNetworkElement;
import org.cxio.metadata.MetaDataCollection;
import org.cxio.metadata.MetaDataElement;
import org.ndexbio.common.cx.CXNetworkFileGenerator;
import org.ndexbio.common.models.dao.postgresql.NetworkDAO;
import org.ndexbio.model.cx.Provenance;
import org.ndexbio.model.exceptions.DuplicateObjectException;
import org.ndexbio.model.exceptions.NdexException;
import org.ndexbio.model.exceptions.ObjectNotFoundException;
import org.ndexbio.model.object.ProvenanceEntity;
import org.ndexbio.model.object.SimplePropertyValuePair;
import org.ndexbio.model.object.network.NetworkIndexLevel;
import org.ndexbio.model.object.network.NetworkSummary;
import org.ndexbio.rest.Configuration;
import org.ndexbio.task.NdexServerQueue;
import org.ndexbio.task.SolrIndexScope;
import org.ndexbio.task.SolrTaskRebuildNetworkIdx;

public class CXNetworkAspectsUpdater extends CXNetworkLoader {

	private UUID aspectsCXNetworkID;
	
	public CXNetworkAspectsUpdater(UUID networkUUID, /*String ownerUserName,*/ NetworkDAO networkDao, UUID aspectsCXUUID) {
		super(networkUUID,/* ownerUserName,*/ true, networkDao, null,null);
		
		this.aspectsCXNetworkID = aspectsCXUUID;
	}

	public void update() throws FileNotFoundException, IOException, DuplicateObjectException, ObjectNotFoundException, NdexException, SQLException {
	
		try (	InputStream inputStream = new FileInputStream(Configuration.getInstance().getNdexRoot() + "/data/" + aspectsCXNetworkID.toString() + "/network.cx") ) {
			  persistNetworkData(inputStream); 
			  

			  UUID networkUUID = getNetworkId();
			  NetworkDAO dao = getDAO();
			  //handle the network properties 
			  NetworkSummary summary = dao.getNetworkSummaryById(networkUUID);
			  MetaDataCollection fullMetaData = dao.getMetaDataCollection(networkUUID);
			 
			  for ( MetaDataElement newMetaElement: this.metadata) {
				  fullMetaData.remove(newMetaElement.getName());
				  fullMetaData.add(newMetaElement);
			  }
			  
			  if (aspectTable.containsKey(EdgesElement.ASPECT_NAME) )
				  summary.setEdgeCount((int)aspectTable.get(EdgesElement.ASPECT_NAME).getElementCount());
			  if ( aspectTable.containsKey(NodesElement.ASPECT_NAME))
				  summary.setNodeCount((int) aspectTable.get(NodesElement.ASPECT_NAME).getElementCount());
				
				summary.setModificationTime(new Timestamp(Calendar.getInstance().getTimeInMillis()));
				
				if ( aspectTable.containsKey(NetworkAttributesElement.ASPECT_NAME)) {
					summary.setProperties(properties);
					summary.setName(this.networkName);
					summary.setDescription(this.description);
					summary.setVersion(this.version);
					summary.setWarnings(warnings);
				}
				if ( aspectTable.containsKey(SubNetworkElement.ASPECT_NAME)) {
					summary.setSubnetworkIds(subNetworkIds);
				}
				try {
				//	dao.saveNetworkEntry(summary, (this.provenanceHistory == null? null: provenanceHistory.getEntity()), metadata);
					dao.saveNetworkEntry(summary, fullMetaData);
						
					dao.commit();
				} catch (SQLException e) {
					dao.rollback();	
					dao.close();
					throw new NdexException ("DB error when saving network summary: " + e.getMessage(), e);
				}
		  
				
				// create the network sample if the network has more than 500 edges
				if (summary.getEdgeCount() > CXNetworkSampleGenerator.sampleSize)  {
			  
					Long subNetworkId = null;
					if (subNetworkIds.size()>0 )  {
						for ( Long i : subNetworkIds) {
							subNetworkId = i;
							break;
						}
					}
					CXNetworkSampleGenerator g = new CXNetworkSampleGenerator(networkUUID, subNetworkId, metadata);
					g.createSampleNetwork();
			  
				}
			  				
				// update provenance
				
				ProvenanceEntity provenanceEntity = dao.getProvenance(networkUUID);
				
				List<SimplePropertyValuePair> pProps =provenanceEntity.getProperties();
				
				    if ( summary.getName() != null)
				       pProps.add( new SimplePropertyValuePair("dc:title", summary.getName()) );

				    provenanceEntity.setProperties(pProps); 
				    
				if (this.provenanceHistory != null) {
					ProvenanceEntity oldEntity = this.provenanceHistory.getEntity();
					provenanceEntity.getCreationEvent().setInputs(new ArrayList<ProvenanceEntity>(1));
					provenanceEntity.getCreationEvent().addInput(oldEntity);
				}
					    
				dao.setProvenance(networkUUID, provenanceEntity);
				
				//recreate CX file
				CXNetworkFileGenerator g = new CXNetworkFileGenerator ( networkUUID, dao, new Provenance(provenanceEntity));
				String tmpFileName = g.createNetworkFile();
				
				long fileSize = new File(tmpFileName).length();
                dao.setNetworkFileSize(networkUUID, fileSize);
				
				java.nio.file.Path src = Paths.get(tmpFileName);
				java.nio.file.Path tgt = Paths.get(Configuration.getInstance().getNdexRoot() + "/data/" + networkUUID + "/network.cx");
				java.nio.file.Path tgt2 = Paths.get(Configuration.getInstance().getNdexRoot() + "/data/" + networkUUID + "/network.arc");
				
				Files.move(tgt, tgt2, StandardCopyOption.ATOMIC_MOVE); 				
				Files.move(src, tgt, StandardCopyOption.ATOMIC_MOVE,StandardCopyOption.REPLACE_EXISTING);  
				

			try {
				dao.unlockNetwork(networkUUID);
				// dao.commit();
			} catch (SQLException e) {
				dao.rollback();
				dao.close();
				throw new NdexException("DB error when setting unlock flag: " + e.getMessage(), e);
			}

			NetworkIndexLevel indexLevel = dao.getIndexLevel(networkUUID);
			if (indexLevel != NetworkIndexLevel.NONE)
				NdexServerQueue.INSTANCE.addSystemTask(
						new SolrTaskRebuildNetworkIdx(networkUUID, SolrIndexScope.both, false, null, indexLevel));
			else
				NdexServerQueue.INSTANCE.addSystemTask(new SolrTaskRebuildNetworkIdx(networkUUID,
						SolrIndexScope.individual, false, null, NetworkIndexLevel.NONE));
			dao.setFlag(networkUUID, "iscomplete", false);

			dao.commit();
		  }
		
	}
	
}
