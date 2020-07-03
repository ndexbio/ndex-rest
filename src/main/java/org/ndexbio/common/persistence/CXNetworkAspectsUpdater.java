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
import java.util.Calendar;
import java.util.UUID;

import org.ndexbio.common.cx.CXNetworkFileGenerator;
import org.ndexbio.common.models.dao.postgresql.NetworkDAO;
import org.ndexbio.cx2.converter.CXToCX2Converter;
import org.ndexbio.cxio.aspects.datamodels.CartesianLayoutElement;
import org.ndexbio.cxio.aspects.datamodels.EdgesElement;
import org.ndexbio.cxio.aspects.datamodels.NetworkAttributesElement;
import org.ndexbio.cxio.aspects.datamodels.NodesElement;
import org.ndexbio.cxio.aspects.datamodels.SubNetworkElement;
import org.ndexbio.cxio.metadata.MetaDataCollection;
import org.ndexbio.cxio.metadata.MetaDataElement;
import org.ndexbio.model.exceptions.DuplicateObjectException;
import org.ndexbio.model.exceptions.NdexException;
import org.ndexbio.model.exceptions.ObjectNotFoundException;
import org.ndexbio.model.object.network.NetworkIndexLevel;
import org.ndexbio.model.object.network.NetworkSummary;
import org.ndexbio.rest.Configuration;
import org.ndexbio.task.NdexServerQueue;
import org.ndexbio.task.SolrIndexScope;
import org.ndexbio.task.SolrTaskRebuildNetworkIdx;

public class CXNetworkAspectsUpdater extends CXNetworkLoader {
	

	private UUID aspectsCXNetworkID;
	
	public CXNetworkAspectsUpdater(UUID networkUUID, /*String ownerUserName,*/ NetworkDAO networkDao, UUID aspectsCXUUID) {
		super(networkUUID,/* ownerUserName,*/ true, networkDao, null,null, 0);
		
		this.aspectsCXNetworkID = aspectsCXUUID;
	}

	public void update() throws FileNotFoundException, IOException, DuplicateObjectException, ObjectNotFoundException, NdexException, SQLException {
	
		try (	InputStream inputStream = new FileInputStream(Configuration.getInstance().getNdexRoot() + "/data/" + aspectsCXNetworkID.toString() + "/network.cx") ) {
			  persistNetworkData(inputStream, true); 
			  

			  UUID networkUUID = getNetworkId();
			  @SuppressWarnings("resource")
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
				
			  //summary.setModificationTime(new Timestamp(Calendar.getInstance().getTimeInMillis()));
				
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
					dao.saveNetworkEntry(summary, fullMetaData ,true);
					dao.setFlag(getNetworkId(), "has_layout", fullMetaData.getMetaDataElement(CartesianLayoutElement.ASPECT_NAME)!=null);	
					dao.commit();
				} catch (SQLException e) {
					dao.rollback();	
					dao.close();
					throw new NdexException ("DB error when saving network summary: " + e.getMessage(), e);
				}
		  
				
				// create the network sample if the network has more than 500 edges
				if (summary.getEdgeCount() > this.sampleGenerationThreshold)  {
			  
					Long subNetworkId = null;
					if (subNetworkIds.size()>0 )  {
						for ( Long i : subNetworkIds) {
							subNetworkId = i;
							break;
						}
					}
					CXNetworkSampleGenerator g = new CXNetworkSampleGenerator(networkUUID, subNetworkId, metadata, defaultSampleSize);
					g.createSampleNetwork();
			  
				}
			  				
				//recreate CX file
				CXNetworkLoader.reCreateCXFiles(networkUUID, dao);
				/*CXNetworkFileGenerator g = new CXNetworkFileGenerator ( networkUUID, dao );
				String tmpFileName = CXNetworkFileGenerator.createNetworkFile(networkUUID.toString(), g.getMetaData());
				
				long fileSize = new File(tmpFileName).length();
                dao.setNetworkFileSize(networkUUID, fileSize);
				
				java.nio.file.Path src = Paths.get(tmpFileName);
				java.nio.file.Path tgt = Paths.get(Configuration.getInstance().getNdexRoot() + "/data/" + networkUUID + "/network.cx");
				java.nio.file.Path tgt2 = Paths.get(Configuration.getInstance().getNdexRoot() + "/data/" + networkUUID + "/network.arc");
				
				Files.move(tgt, tgt2, StandardCopyOption.ATOMIC_MOVE); 				
				Files.move(src, tgt, StandardCopyOption.ATOMIC_MOVE,StandardCopyOption.REPLACE_EXISTING);  
				
				 */

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
						new SolrTaskRebuildNetworkIdx(networkUUID, SolrIndexScope.both, false, null, indexLevel,false));
			else
				NdexServerQueue.INSTANCE.addSystemTask(new SolrTaskRebuildNetworkIdx(networkUUID,
						SolrIndexScope.individual, false, null, NetworkIndexLevel.NONE,false));
			dao.setFlag(networkUUID, "iscomplete", false);

			dao.commit();
		  }
		
	}
	
}
