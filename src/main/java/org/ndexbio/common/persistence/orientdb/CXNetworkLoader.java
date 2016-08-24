/**
 * Copyright (c) 2013, 2016, The Regents of the University of California, The Cytoscape Consortium
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 *
 * 1. Redistributions of source code must retain the above copyright notice, this
 *    list of conditions and the following disclaimer.
 *
 * 2. Redistributions in binary form must reproduce the above copyright notice,
 *    this list of conditions and the following disclaimer in the documentation
 *    and/or other materials provided with the distribution.
 *
 * 3. Neither the name of the copyright holder nor the names of its contributors
 *    may be used to endorse or promote products derived from this software
 *    without specific prior written permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND
 * ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED
 * WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
 * DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT HOLDER OR CONTRIBUTORS BE LIABLE
 * FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL
 * DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR
 * SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER
 * CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY,
 * OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE
 * OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 *
 */
package org.ndexbio.common.persistence.orientdb;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.UUID;
import java.util.concurrent.ExecutionException;

import org.apache.solr.client.solrj.SolrServerException;
import org.cxio.aspects.datamodels.EdgesElement;
import org.cxio.aspects.datamodels.NetworkAttributesElement;
import org.cxio.aspects.datamodels.NodeAttributesElement;
import org.cxio.aspects.datamodels.NodesElement;
import org.cxio.aspects.readers.EdgeAttributesFragmentReader;
import org.cxio.aspects.readers.EdgesFragmentReader;
import org.cxio.aspects.readers.NetworkAttributesFragmentReader;
import org.cxio.aspects.readers.NodeAttributesFragmentReader;
import org.cxio.aspects.readers.NodesFragmentReader;
import org.cxio.core.CxElementReader;
import org.cxio.core.interfaces.AspectElement;
import org.cxio.core.interfaces.AspectFragmentReader;
import org.cxio.metadata.MetaDataCollection;
import org.cxio.util.CxioUtil;
import org.ndexbio.common.NdexClasses;
import org.ndexbio.common.cx.aspect.GeneralAspectFragmentReader;
import org.ndexbio.common.models.dao.postgresql.NetworkDocDAO;
import org.ndexbio.common.solr.NetworkGlobalIndexManager;
import org.ndexbio.model.cx.CitationElement;
import org.ndexbio.model.cx.EdgeCitationLinksElement;
import org.ndexbio.model.cx.EdgeSupportLinksElement;
import org.ndexbio.model.cx.NamespacesElement;
import org.ndexbio.model.cx.NdexNetworkStatus;
import org.ndexbio.model.cx.NodeCitationLinksElement;
import org.ndexbio.model.cx.NodeSupportLinksElement;
import org.ndexbio.model.cx.Provenance;
import org.ndexbio.model.cx.SupportElement;
import org.ndexbio.model.exceptions.DuplicateObjectException;
import org.ndexbio.model.exceptions.NdexException;
import org.ndexbio.model.exceptions.ObjectNotFoundException;
import org.ndexbio.model.object.NdexPropertyValuePair;
import org.ndexbio.model.object.ProvenanceEntity;
import org.ndexbio.model.object.network.NetworkSummary;
import org.ndexbio.model.object.network.VisibilityType;
import org.ndexbio.rest.Configuration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.FileInputStream;
import java.io.FileNotFoundException;


public class CXNetworkLoader implements AutoCloseable {
	
    protected static Logger logger = LoggerFactory.getLogger(CXNetworkLoader.class);

	//private static final String nodeName = "name";
	
	private long counter;
	
	private InputStream inputStream;
//	private NdexDatabase ndexdb;
	private String ownerName;
	private UUID networkId;

    private String rootPath;
	
    //mapping tables mapping from element SID to internal ID. 
	private Set<Long> nodeIds;
	private Set<Long> edgeIds;
	private Set<Long> citationIds;
	private Set<Long> supportIds;
	private Map<String, Long> namespaceMap;   // prefix to nsID mapping.
	
	// tables to track undefined Elements. Stores element SIDs
	private Set<Long> undefinedNodeId;
	private Set<Long> undefinedEdgeId;
	private Set<Long> undefinedCitationId;
	private Set<Long> undefinedSupportId;
	
	private Provenance provenanceHistory;
		
	long opaqueCounter ;

	long serverElementLimit; 
	
	private String networkName ;
	private String description;
	private String version;
	
	private List<NdexPropertyValuePair> properties;
	
		
	private Map<String,CXAspectWriter> aspectTable;
	private NetworkGlobalIndexManager globalIdx ;

		
	public CXNetworkLoader(UUID networkUUID,String ownerUserName)  throws NdexException, FileNotFoundException {
		super();
		
		this.ownerName = ownerUserName;
		this.networkId = networkUUID;
		this.rootPath = Configuration.getInstance().getNdexRoot() + "/data/" + networkId + "/aspects/";
		this.networkName = null;

		this.inputStream = new FileInputStream(Configuration.getInstance().getNdexRoot() + "/data/" + networkId + "/" + networkId + ".cx");
				
		String edgeLimit = Configuration.getInstance().getProperty(Configuration.networkPostEdgeLimit);
		if ( edgeLimit != null ) {
			try {
				serverElementLimit = Long.parseLong(edgeLimit);
			} catch( NumberFormatException e) {
				logger.error("[Invalid value in server property {}]", Configuration.networkPostEdgeLimit);
		//		props.put("ServerPostEdgeLimit", "-1");  //defaultPostEdgeLimit);
			}
		} else 
			serverElementLimit = -1;
		
		globalIdx = new NetworkGlobalIndexManager();
		
	}
	
	private void init () {
		opaqueCounter = 0;
		counter =0; 
		
		nodeIds = new TreeSet<>();
		edgeIds = new TreeSet<> ();
		citationIds = new TreeSet<> ();
		supportIds = new TreeSet<> ();
		this.namespaceMap = new TreeMap<>();
		
		undefinedNodeId = new TreeSet<>();
		undefinedEdgeId = new TreeSet<>();
		undefinedSupportId = new TreeSet<>();
		undefinedCitationId = new TreeSet<>();

		aspectTable = new TreeMap<>();
		
		provenanceHistory = null;
		
		networkName = null;
		description = null;
		version = null;
		properties = new ArrayList<>();
		
	//	declaredNodeCount = -1 ;
	//	declaredEdgeCount = -1;
		
	}
	
	private CxElementReader createCXReader () throws IOException {
		HashSet<AspectFragmentReader> readers = new HashSet<>(20);
		
		  readers.add(EdgesFragmentReader.createInstance());
		  readers.add(EdgeAttributesFragmentReader.createInstance());
		  readers.add(NetworkAttributesFragmentReader.createInstance());
		  readers.add(NodesFragmentReader.createInstance());
		  readers.add(NodeAttributesFragmentReader.createInstance());
		  
		  readers.add(new GeneralAspectFragmentReader (NdexNetworkStatus.ASPECT_NAME,
				NdexNetworkStatus.class));
		  readers.add(new GeneralAspectFragmentReader (NamespacesElement.ASPECT_NAME,NamespacesElement.class));
		//  readers.add(new GeneralAspectFragmentReader (FunctionTermElement.ASPECT_NAME,FunctionTermElement.class));
		  readers.add(new GeneralAspectFragmentReader (CitationElement.ASPECT_NAME,CitationElement.class));
		  readers.add(new GeneralAspectFragmentReader (SupportElement.ASPECT_NAME,SupportElement.class));
//		  readers.add(new GeneralAspectFragmentReader (ReifiedEdgeElement.ASPECT_NAME,ReifiedEdgeElement.class));
		  readers.add(new GeneralAspectFragmentReader (EdgeCitationLinksElement.ASPECT_NAME,EdgeCitationLinksElement.class));
		  readers.add(new GeneralAspectFragmentReader (EdgeSupportLinksElement.ASPECT_NAME,EdgeSupportLinksElement.class));
		  readers.add(new GeneralAspectFragmentReader (NodeCitationLinksElement.ASPECT_NAME,NodeCitationLinksElement.class));
		  readers.add(new GeneralAspectFragmentReader (NodeSupportLinksElement.ASPECT_NAME,NodeSupportLinksElement.class));
		  readers.add(new GeneralAspectFragmentReader (Provenance.ASPECT_NAME,Provenance.class));
		  		  
		  return  CxElementReader.createInstance(inputStream, true,
				   readers);
	}
	
	public void persistCXNetwork() throws ObjectNotFoundException, NdexException {
		        	    
	    try {
	    	
	      //Create dir
		  java.nio.file.Path dir = Paths.get(rootPath);
		  Files.createDirectory(dir);
			    	
		  persistNetworkData(); 
		  
		  NetworkSummary summary = new NetworkSummary();

		  try (NetworkDocDAO dao = new NetworkDocDAO ()) {
				//handle the network properties 
				summary.setExternalId(this.networkId);
				summary.setVisibility(VisibilityType.PRIVATE);
				summary.setEdgeCount(this.edgeIds.size());
				summary.setNodeCount(this.nodeIds.size());
				
				Timestamp t = dao.getNetworkCreationTime(this.networkId)	;
				summary.setCreationTime(t);
				summary.setModificationTime(t);
				summary.setProperties(properties);
				summary.setName(this.networkName);
				summary.setDescription(this.description);
				summary.setVersion(this.version);
				dao.populateNetworkEntry(summary);
				dao.commit();
		  }
		
		  createSolrIndex(summary);
		  try ( NetworkDocDAO dao = new NetworkDocDAO()) {
			  dao.setFlag(this.networkId, "iscomplete", true);
			  dao.commit();
		  }
		
		} catch (Exception e) {
			// delete network and close the database connection
			e.printStackTrace();
	//		this.abortTransaction();
			throw new NdexException("Error occurred when loading CX stream. " + e.getMessage());
		} 
       
	}

	private void persistNetworkData()
			throws IOException, DuplicateObjectException, NdexException, ObjectNotFoundException {
		
		init();
		
		CxElementReader cxreader = createCXReader();
		  
		MetaDataCollection metadata = cxreader.getPreMetaData();
		
		for ( AspectElement elmt : cxreader ) {
			switch ( elmt.getAspectName() ) {
				case NodesElement.ASPECT_NAME :       //Node
					createCXNode((NodesElement) elmt);
					break;
				case NdexNetworkStatus.ASPECT_NAME:   //ndexStatus we ignore this in CX
				//	netStatus = (NdexNetworkStatus) elmt;
				//	saveNetworkStatus(netStatus);
					break; 
				case EdgesElement.ASPECT_NAME:       // Edge
					EdgesElement ee = (EdgesElement) elmt;
					createCXEdge(ee);
					break;
				case NodeAttributesElement.ASPECT_NAME:  // node attributes
					addNodeAttribute((NodeAttributesElement) elmt );
					break;
				case NetworkAttributesElement.ASPECT_NAME: //network attributes
					createNetworkAttribute(( NetworkAttributesElement) elmt);
					break;
				default:    // opaque aspect
					createAspectElement(elmt);
			}

		} 
/*		  // check data integrity.
		  if ( !undefinedNodeId.isEmpty()) {
			  String errorMessage = undefinedNodeId.size() + "undefined nodes found in CX stream: [";
			  for( Long sid : undefinedNodeId)
				  errorMessage += sid + " ";		  
			  logger.error(errorMessage);
			  throw new NdexException(errorMessage );
		  } 
		  
		  if ( !undefinedEdgeId.isEmpty()) {
			  String errorMessage = undefinedEdgeId.size() + "undefined edges found in CX stream: [";
			  for( Long sid : undefinedEdgeId)
				  errorMessage += sid + " ";		  
			  logger.error(errorMessage);
			  throw new NdexException(errorMessage );
		  }
		  //TODO: check citation and supports
		  
		  //save the metadata
		  MetaDataCollection postmetadata = cxreader.getPostMetaData();
		  if ( postmetadata !=null) {
			  if( metadata == null) {
				  metadata = postmetadata;
			  } else {
				  for (MetaDataElement e : postmetadata.toCollection()) {
					  Long cnt = e.getIdCounter();
					  if ( cnt !=null) {
						 metadata.setIdCounter(e.getName(),cnt);
					  }
					  cnt = e.getElementCount() ;
					  if ( cnt !=null) {
							 metadata.setElementCount(e.getName(),cnt);
					  }
				  }
			  }
		  }
		  
		  Timestamp modificationTime = new Timestamp(Calendar.getInstance().getTimeInMillis());

		  if(metadata !=null) {
			  // check if idCounter is defined in certain espects, and elementCount matches between metadata and data.
			  for ( MetaDataElement e: metadata.toCollection()) {
				  if (  (e.getName().equals(NodesElement.ASPECT_NAME) || e.getName().equals(EdgesElement.ASPECT_NAME) || 
								  e.getName().equals(CitationElement.ASPECT_NAME) || 
								  e.getName().equals(SupportElement.ASPECT_NAME))) {  
					   if ( e.getIdCounter() == null )
						   throw new NdexException ( "Idcounter value is not found in metadata of aspect " + e.getName());
					   if ( e.getName().equals(NodesElement.ASPECT_NAME) && e.getElementCount() !=null && nodeSIDMap.size()!=e.getElementCount())
						   throw new NdexException("ActualNodeCount in CX stream is " + nodeSIDMap.size() + ", but metadata says it's " + e.getElementCount());
					   //TODO: check other 3 aspects too.
				  }
			  }
			  Long consistencyGrp = metadata.getMetaDataElement(NodesElement.ASPECT_NAME).getConsistencyGroup();

			  // process NdexNetworkStatus metadata
			  MetaDataElement e = metadata.getMetaDataElement(NdexNetworkStatus.ASPECT_NAME);
			  if ( e == null) {
				  e = new MetaDataElement();
				  e.setName(NdexNetworkStatus.ASPECT_NAME);
				  e.setVersion("1.0");
				  e.setConsistencyGroup(consistencyGrp);
				  e.setElementCount(1l);
				  metadata.add(e);
			  }
			  e.setLastUpdate(modificationTime.getTime());
			  
			  // process Provenance metadata
			  e = metadata.getMetaDataElement(Provenance.ASPECT_NAME);
			  if ( e == null) {
				  e = new MetaDataElement();
				  e.setName(Provenance.ASPECT_NAME);
				  e.setVersion("1.0");
				  e.setConsistencyGroup(consistencyGrp);
				  e.setElementCount(1l);
				  metadata.add(e);
			  }
			  e.setLastUpdate(modificationTime.getTime());
			  
		      networkDoc.field(NdexClasses.Network_P_metadata,metadata);
		  } else 
			  throw new NdexException ("No CX metadata found in this CX stream.");
  
		  // finalize the headnode
		  networkDoc.fields(NdexClasses.ExternalObj_mTime, modificationTime,
				  NdexClasses.Network_P_nodeCount, this.nodeSIDMap.size(),
				  NdexClasses.Network_P_edgeCount,this.edgeSIDMap.size(),
				   NdexClasses.Network_P_isComplete,true,
				   NdexClasses.Network_P_opaquEdgeTable, this.opaqueAspectEdgeTable);
		  networkDoc.save(); */
	}
	
	
	private void createSolrIndex(NetworkSummary summary) throws SolrServerException, IOException {

		globalIdx.createIndexDocFromSummary(summary,ownerName);
		
		globalIdx.commit();
   	
	}



	private void createNetworkAttribute(NetworkAttributesElement e) throws NdexException, IOException {
		
		if ( e.getName().equals(NdexClasses.Network_P_name) && ( networkName == null || e.getSubnetwork() == null)) {
				this.networkName = e.getValue();
		} else if ( e.getName().equals(NdexClasses.Network_P_desc) && ( description == null || e.getSubnetwork() == null)) {
			this.description = e.getValue();
		} else if ( e.getName().equals(NdexClasses.Network_P_version) && ( version == null || e.getSubnetwork() == null)) {
			this.version = e.getValue();
		} else 
			properties.add(new NdexPropertyValuePair(e.getSubnetwork(),e.getName(), 
					 (e.isSingleValue() ? e.getValue(): CxioUtil.getAttributeValuesAsString(e)), e.getDataType().toString()));
		
		writeCXElement(e);
		
		this.globalIdx.addCXNetworkAttrToIndex(e);	
		tick();

	}
	
	private void writeCXElement(AspectElement element) throws IOException {
		String aspectName = element.getAspectName();
		CXAspectWriter writer = aspectTable.get(aspectName);
		if ( writer == null) {
			logger.info("creating new file for aspect " + aspectName);
			writer = new CXAspectWriter(rootPath + aspectName);
			aspectTable.put(aspectName, writer);
		}
		writer.writeCXElement(element);
		writer.flush();
	}
	
	
	private void createCXNode(NodesElement node) throws NdexException, IOException {
		if ( !this.nodeIds.add(Long.valueOf(node.getId()))) {
			throw new NdexException ("Duplicate Node Id " + node.getId() + " found.");
		}
		
		undefinedNodeId.remove(node.getId());
		
		writeCXElement(node);
		this.globalIdx.addCXNodeToIndex(node);	
		   
		tick();   
	}	 

	
	private void createCXEdge(EdgesElement ee) throws NdexException, IOException {
		
		if ( !this.edgeIds.add(ee.getId())) {
			throw new NdexException ("Duplicate Edge found in CX stream. @id=" + ee.getId());
		}
		
		undefinedEdgeId.remove(ee.getId());
		
		if( !nodeIds.contains(ee.getSource()))
			undefinedNodeId.add(ee.getSource());
		if ( !nodeIds.contains(ee.getTarget()))
			undefinedNodeId.add(ee.getTarget());
		
		writeCXElement(ee);

		tick();	
	   
	}
	
	private void createAspectElement(AspectElement element) throws NdexException, IOException {
		writeCXElement(element);
		tick();			
	}
	

	private void addNodeAttribute(NodeAttributesElement e) throws NdexException, IOException{
		
		for ( Long nodeId : e.getPropertyOf()) { 
			if ( !nodeIds.contains(nodeId))
				undefinedNodeId.add(nodeId);
		}
		
		writeCXElement(e);
		
		this.globalIdx.addCXNodeAttrToIndex(e);	
		tick();
		
	}

	
	private void tick() throws NdexException {
		counter ++;
		if ( serverElementLimit>=0 && counter >serverElementLimit ) 
			throw new NdexException("Element count in the CX input stream exceeded server limit " + serverElementLimit);
		if ( counter %10000 == 0 )
			System.out.println("Loaded " + counter + " element in CX");
		
	} 
	
	
/*	private void abortTransaction() throws ObjectNotFoundException, NdexException {
		logger.warn("AbortTransaction has been invoked from CX loader.");

		logger.info("Deleting partial network "+ uuid + " in order to rollback in response to error");
		networkDoc.field(NdexClasses.ExternalObj_isDeleted, true).save();
		graph.commit();
		
		Task task = new Task();
		task.setTaskType(TaskType.SYSTEM_DELETE_NETWORK);
		task.setResource(uuid.toString());
		NdexServerQueue.INSTANCE.addSystemTask(task);
		logger.info("Partial network "+ uuid + " is deleted.");
	} */
	
	
	public UUID updateNetwork(String networkUUID, ProvenanceEntity provenanceEntity) throws NdexException, ExecutionException, SolrServerException, IOException {

		// get the old network head node
	/*	ODocument srcNetworkDoc = this.getRecordByUUIDStr(networkUUID);
		if (srcNetworkDoc == null)
				throw new NdexException("Network with UUID " + networkUUID + " is not found in this server");

//		srcNetworkDoc.field(NdexClasses.Network_P_isComplete, false).save();
//		graph.commit();
		try {


			// create new network and set the isComplete flag to false 
			uuid = NdexUUIDFactory.INSTANCE.createNewNDExUUID();

			persistNetworkData();
			
			// copy the permission from source to target.
			copyNetworkPermissions(srcNetworkDoc, networkVertex);
			
			graph.commit();
		} catch ( Exception e) {
			e.printStackTrace();
			this.abortTransaction();
			srcNetworkDoc.field(NdexClasses.Network_P_isLocked, false).save();
//			srcNetworkDoc.field(NdexClasses.Network_P_isComplete, true).save();
			graph.commit();
			throw new NdexException("Error occurred when updating network using CX. " + e.getMessage());
		}
			
		// remove the old solr Index and add the new one.
		SingleNetworkSolrIdxManager idxManager = new SingleNetworkSolrIdxManager(networkUUID);
		NetworkGlobalIndexManager globalIdx = new NetworkGlobalIndexManager();
		try {
			idxManager.dropIndex();
			globalIdx.deleteNetwork(networkUUID);
		} catch (SolrServerException | HttpSolrClient.RemoteSolrException | IOException se ) {
			logger.warn("Failed to delete Solr Index for network " + networkUUID + ". Please clean it up manually from solr. Error message: " + se.getMessage());
		}
		
		graph.begin();

		UUID newUUID = NdexUUIDFactory.INSTANCE.createNewNDExUUID();

		srcNetworkDoc.fields(NdexClasses.ExternalObj_ID, newUUID.toString(),
					  NdexClasses.ExternalObj_isDeleted,true).save();
			
		this.networkDoc.reload();
		
		// copy the creationTime and visibility
		networkDoc.fields( NdexClasses.ExternalObj_ID, networkUUID,
					NdexClasses.ExternalObj_cTime, srcNetworkDoc.field(NdexClasses.ExternalObj_cTime),
					NdexClasses.Network_P_visibility, srcNetworkDoc.field(NdexClasses.Network_P_visibility),
					NdexClasses.Network_P_isLocked,false,
					NdexClasses.ExternalObj_mTime, new Date() ,
					          NdexClasses.Network_P_isComplete,true)
			.save();
		setNetworkProvenance(provenanceEntity);
		graph.commit();
		
		//create Solr index on the new network.	
		createSolrIndex(networkDoc);
			
		// added a delete old network task.
		// comment this block out because OrientDB will be locked up during delete and update. 
	/*	Task task = new Task();
		task.setTaskType(TaskType.SYSTEM_DELETE_NETWORK);
		task.setResource(newUUID.toString());
		NdexServerQueue.INSTANCE.addSystemTask(task); */
			
		return UUID.fromString(networkUUID);
		 	
	}

/*	private void copyNetworkPermissions(ODocument srcNetworkDoc, OrientVertex targetNetworkVertex) {
		
		copyNetworkPermissionAux(srcNetworkDoc, targetNetworkVertex, NdexClasses.E_admin);
		copyNetworkPermissionAux(srcNetworkDoc, targetNetworkVertex, NdexClasses.account_E_canEdit);
		copyNetworkPermissionAux(srcNetworkDoc, targetNetworkVertex, NdexClasses.account_E_canRead);

	}
	
	private void copyNetworkPermissionAux(ODocument srcNetworkDoc, OrientVertex targetNetworkVertex, String permissionEdgeType) {
		
		for ( ODocument rec : Helper.getDocumentLinks(srcNetworkDoc, "in_", permissionEdgeType)) {
			OrientVertex userV = graph.getVertex(rec);
			targetNetworkVertex.reload();
			userV.addEdge(permissionEdgeType, targetNetworkVertex);
		}
		
	} */
	
    public void setNetworkProvenance(ProvenanceEntity e) throws JsonProcessingException
    {

        ObjectMapper mapper = new ObjectMapper();
        String provenanceString = mapper.writeValueAsString(e);
        // store provenance string
  /*      this.networkDoc = this.networkDoc.field(NdexClasses.Network_P_provenance, provenanceString)
                .save(); */
    }

	@Override
	public void close() throws Exception {
		for ( Map.Entry<String, CXAspectWriter> entry : aspectTable.entrySet() ){
			entry.getValue().close();
		}
		
		this.inputStream.close();
	}


}
