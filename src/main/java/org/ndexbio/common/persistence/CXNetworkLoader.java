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
package org.ndexbio.common.persistence;

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
import java.util.Arrays;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.UUID;

import org.apache.solr.client.solrj.SolrServerException;
import org.cxio.aspects.datamodels.ATTRIBUTE_DATA_TYPE;
import org.cxio.aspects.datamodels.EdgesElement;
import org.cxio.aspects.datamodels.NetworkAttributesElement;
import org.cxio.aspects.datamodels.NodeAttributesElement;
import org.cxio.aspects.datamodels.NodesElement;
import org.cxio.aspects.datamodels.SubNetworkElement;
import org.cxio.aspects.readers.EdgeAttributesFragmentReader;
import org.cxio.aspects.readers.EdgesFragmentReader;
import org.cxio.aspects.readers.NetworkAttributesFragmentReader;
import org.cxio.aspects.readers.NodeAttributesFragmentReader;
import org.cxio.aspects.readers.NodesFragmentReader;
import org.cxio.core.CxElementReader2;
import org.cxio.core.interfaces.AspectElement;
import org.cxio.core.interfaces.AspectFragmentReader;
import org.cxio.metadata.MetaDataCollection;
import org.cxio.metadata.MetaDataElement;
import org.cxio.misc.OpaqueElement;
import org.ndexbio.common.NdexClasses;
import org.ndexbio.common.cx.CXAspectWriter;
import org.ndexbio.common.cx.CXNetworkFileGenerator;
import org.ndexbio.common.cx.GeneralAspectFragmentReader;
import org.ndexbio.common.models.dao.postgresql.NetworkDAO;
import org.ndexbio.common.solr.NetworkGlobalIndexManager;
import org.ndexbio.common.solr.SingleNetworkSolrIdxManager;
import org.ndexbio.model.cx.CitationElement;
import org.ndexbio.model.cx.EdgeCitationLinksElement;
import org.ndexbio.model.cx.EdgeSupportLinksElement;
import org.ndexbio.model.cx.FunctionTermElement;
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
import org.ndexbio.model.object.Permissions;
import org.ndexbio.model.object.ProvenanceEntity;
import org.ndexbio.model.object.network.NetworkSummary;
import org.ndexbio.model.object.network.VisibilityType;
import org.ndexbio.rest.Configuration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class CXNetworkLoader implements AutoCloseable {
	
    protected static Logger logger = LoggerFactory.getLogger(CXNetworkLoader.class);

	//private static final String nodeName = "name";
    
    private boolean isUpdate;
	
	private long counter;
	
	private InputStream inputStream;
	private String ownerName;
	private UUID networkId;

    private String rootPath;
	
    //mapping tables mapping from element SID to internal ID. 
	private AspectElementIdTracker nodeIdTracker;
	private AspectElementIdTracker edgeIdTracker;
	private AspectElementIdTracker citationIdTracker;
	private AspectElementIdTracker supportIdTracker;
	
//	private Map<String, Long> namespaceMap;   // prefix to nsID mapping.
	
//	private Provenance provenanceHistory;    // comment out for now.
	private Set<Long> subNetworkIds;
		
	long opaqueCounter ;

	long serverElementLimit; 
	
	private String networkName ;
	private String description;
	private String version;
	
	private boolean networkNameIsAssigned;
	
	private MetaDataCollection metadata;
	
	private List<NdexPropertyValuePair> properties;
		
	private Map<String,CXAspectWriter> aspectTable;
	private NetworkGlobalIndexManager globalIdx ;
	private List<String> warnings;
	private NetworkDAO dao;
		
	public CXNetworkLoader(UUID networkUUID,String ownerUserName, boolean isUpdate, NetworkDAO networkDao)  throws NdexException, FileNotFoundException {
		super();
		
		this.isUpdate = isUpdate;
		
		this.ownerName = ownerUserName;
		this.networkId = networkUUID;
		this.rootPath = Configuration.getInstance().getNdexRoot() + "/data/" + networkId + "/aspects/";
		this.networkName = null;

		this.inputStream = new FileInputStream(Configuration.getInstance().getNdexRoot() + "/data/" + networkId + "/network.cx");
				
		serverElementLimit = Configuration.getInstance().getServerElementLimit();
		
		globalIdx = new NetworkGlobalIndexManager();
		
		warnings = new ArrayList<> ();
		networkNameIsAssigned = false;

		opaqueCounter = 0;
		counter =0; 
		
		nodeIdTracker = new AspectElementIdTracker(NodesElement.ASPECT_NAME);
		edgeIdTracker = new AspectElementIdTracker(EdgesElement.ASPECT_NAME);
		citationIdTracker = new AspectElementIdTracker(CitationElement.ASPECT_NAME);
		supportIdTracker = new AspectElementIdTracker(SupportElement.ASPECT_NAME);
		
	//	this.namespaceMap = new TreeMap<>();

		aspectTable = new TreeMap<>();
		this.subNetworkIds = new HashSet<>(10);
	
//		provenanceHistory = null;
		
		networkName = null;
		description = null;
		version = null;
		properties = new ArrayList<>();
		dao = networkDao;
		
	}
	
	private CxElementReader2 createCXReader () throws IOException {
		HashSet<AspectFragmentReader> readers = new HashSet<>(20);
		
		  readers.add(EdgesFragmentReader.createInstance());
		  readers.add(EdgeAttributesFragmentReader.createInstance());
		  readers.add(NetworkAttributesFragmentReader.createInstance());
		  readers.add(NodesFragmentReader.createInstance());
		  readers.add(NodeAttributesFragmentReader.createInstance());
		  
		  readers.add(new GeneralAspectFragmentReader (NdexNetworkStatus.ASPECT_NAME,
				NdexNetworkStatus.class));
		  readers.add(new GeneralAspectFragmentReader (NamespacesElement.ASPECT_NAME,NamespacesElement.class));
		  readers.add(new GeneralAspectFragmentReader (FunctionTermElement.ASPECT_NAME,FunctionTermElement.class));
		  readers.add(new GeneralAspectFragmentReader (CitationElement.ASPECT_NAME,CitationElement.class));
		  readers.add(new GeneralAspectFragmentReader (SupportElement.ASPECT_NAME,SupportElement.class));
//		  readers.add(new GeneralAspectFragmentReader (ReifiedEdgeElement.ASPECT_NAME,ReifiedEdgeElement.class));
		  readers.add(new GeneralAspectFragmentReader (EdgeCitationLinksElement.ASPECT_NAME,EdgeCitationLinksElement.class));
		  readers.add(new GeneralAspectFragmentReader (EdgeSupportLinksElement.ASPECT_NAME,EdgeSupportLinksElement.class));
		  readers.add(new GeneralAspectFragmentReader (NodeCitationLinksElement.ASPECT_NAME,NodeCitationLinksElement.class));
		  readers.add(new GeneralAspectFragmentReader (NodeSupportLinksElement.ASPECT_NAME,NodeSupportLinksElement.class));
		  readers.add(new GeneralAspectFragmentReader (Provenance.ASPECT_NAME,Provenance.class));
		  return  new CxElementReader2(inputStream, readers);
	}
	
	public void persistCXNetwork() throws IOException, DuplicateObjectException, ObjectNotFoundException, NdexException, SQLException, SolrServerException {
		        	    
	 //   try {
	    	
	      //Create dir
		  java.nio.file.Path dir = Paths.get(rootPath);
		  Files.createDirectory(dir);
			    	
		  persistNetworkData(); 
		  
		  logger.info("aspects have been stored.");
		  
		  NetworkSummary summary = new NetworkSummary();

				//handle the network properties 
				summary.setExternalId(this.networkId);
				summary.setVisibility(VisibilityType.PRIVATE);
				summary.setEdgeCount(this.edgeIdTracker.getDefinedElementSize());
				summary.setNodeCount(this.nodeIdTracker.getDefinedElementSize());
				
				Timestamp t = dao.getNetworkCreationTime(this.networkId)	;
				summary.setCreationTime(t);
				summary.setModificationTime(t);
				summary.setProperties(properties);
				summary.setName(this.networkName);
				summary.setDescription(this.description);
				summary.setVersion(this.version);
				summary.setWarnings(warnings);
				try {
				//	dao.saveNetworkEntry(summary, (this.provenanceHistory == null? null: provenanceHistory.getEntity()), metadata);
					dao.saveNetworkEntry(summary, metadata);
						
					dao.commit();
				} catch (SQLException e) {
					dao.rollback();	
					throw new NdexException ("DB error when saving network summary: " + e.getMessage(), e);
				}
		  
				SingleNetworkSolrIdxManager idx2 = new SingleNetworkSolrIdxManager(networkId.toString());
				if ( isUpdate ) {
					this.globalIdx.deleteNetwork(networkId.toString());
					idx2.dropIndex();		
				}	
				
				createSolrIndex(summary);
				idx2.createIndex();
  
				// create the network sample if the network has more than 500 edges
				if (summary.getEdgeCount() > CXNetworkSampleGenerator.sampleSize)  {
			  
					Long subNetworkId = null;
					if (subNetworkIds.size()>1 )  {
						for ( Long i : subNetworkIds) {
							subNetworkId = i;
							break;
						}
					}
					CXNetworkSampleGenerator g = new CXNetworkSampleGenerator(this.networkId, subNetworkId, metadata);
					g.createSampleNetwork();
			  
				}
			  				
				//recreate CX file
				ProvenanceEntity provenanceHistory = dao.getProvenance(networkId);
				CXNetworkFileGenerator g = new CXNetworkFileGenerator ( networkId, dao, new Provenance(provenanceHistory));
				String tmpFileName = g.createNetworkFile();
				
				java.nio.file.Path src = Paths.get(tmpFileName);
				java.nio.file.Path tgt = Paths.get(Configuration.getInstance().getNdexRoot() + "/data/" + networkId + "/network.cx");
				java.nio.file.Path tgt2 = Paths.get(Configuration.getInstance().getNdexRoot() + "/data/" + networkId + "/network.arc");
				
				Files.move(tgt, tgt2, StandardCopyOption.ATOMIC_MOVE); 				
				Files.move(src, tgt, StandardCopyOption.ATOMIC_MOVE,StandardCopyOption.REPLACE_EXISTING);  
				
				try {
					dao.setFlag(this.networkId, "iscomplete", true);
					dao.commit();
				} catch (SQLException e) {
					dao.rollback();
					throw new NdexException ("DB error when setting iscomplete flag: " + e.getMessage(), e);
				}
       
	}
	
	/**
	 * This function is only for migrating db. It validates and creates network files in NDEx file store, but doesn't update the main network table record. Solr indexes
	 * are not created either.
	 * @throws SolrServerException 
	 * 

	 */

	public void importNetwork() throws IOException, DuplicateObjectException, ObjectNotFoundException, NdexException, SQLException, SolrServerException {
	    
		 //   try {
		    	
		      //Create dir
			  java.nio.file.Path dir = Paths.get(rootPath);
			  Files.createDirectory(dir);
				    	
			  persistNetworkData(); 
			  
			  logger.info("aspects have been stored.");
			  
			  NetworkSummary summary = dao.getNetworkSummaryById(networkId);
			  SingleNetworkSolrIdxManager idx2 = new SingleNetworkSolrIdxManager(networkId.toString());
				if ( isUpdate ) {
					this.globalIdx.deleteNetwork(networkId.toString());
					idx2.dropIndex();		
				}	
				
				createSolrIndex(summary);
				idx2.createIndex();
			  			  
			 // create the network sample if the network has more than 500 edges
			 if (this.edgeIdTracker.getDefinedElementSize() > CXNetworkSampleGenerator.sampleSize)  {
				  
						Long subNetworkId = null;
						if (subNetworkIds.size()>1 )  {
							for ( Long i : subNetworkIds) {
								subNetworkId = i;
								break;
							}
						}
						CXNetworkSampleGenerator g = new CXNetworkSampleGenerator(this.networkId, subNetworkId, metadata);
						g.createSampleNetwork();
				  
				}
				  				
			 

				try {
					dao.saveNetworkMetaData(this.networkId,metadata);
					dao.commit();
				} catch (SQLException e) {
					dao.rollback();
					throw new NdexException ("DB error when setting iscomplete flag: " + e.getMessage(), e);
				}
					//recreate CX file
					ProvenanceEntity provenanceHistory = dao.getProvenance(networkId);
					CXNetworkFileGenerator g = new CXNetworkFileGenerator ( networkId, dao, new Provenance(provenanceHistory));
					String tmpFileName = g.createNetworkFile();
					
					java.nio.file.Path src = Paths.get(tmpFileName);
					java.nio.file.Path tgt = Paths.get(Configuration.getInstance().getNdexRoot() + "/data/" + networkId + "/network.cx");
					java.nio.file.Path tgt2 = Paths.get(Configuration.getInstance().getNdexRoot() + "/data/" + networkId + "/network.arc");
					
					Files.move(tgt, tgt2, StandardCopyOption.ATOMIC_MOVE); 				
					Files.move(src, tgt, StandardCopyOption.ATOMIC_MOVE,StandardCopyOption.REPLACE_EXISTING);  
					
					try {
						dao.setFlag(this.networkId, "iscomplete", true);
						dao.setFlag(this.networkId, "is_validated", true);
						dao.commit();
					} catch (SQLException e) {
						dao.rollback();
						throw new NdexException ("DB error when setting iscomplete flag: " + e.getMessage(), e);
					}
	       
		}

	
	private void persistNetworkData()
			throws IOException, DuplicateObjectException, NdexException, ObjectNotFoundException {
				
		CxElementReader2 cxreader = createCXReader();
		  
	    metadata = cxreader.getPreMetaData();
		
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
				case CitationElement.ASPECT_NAME: 
					createCXCitation((CitationElement)elmt);
					break;
				case SupportElement.ASPECT_NAME:
					createCXSupport((SupportElement)elmt);
					break;
				case EdgeCitationLinksElement.ASPECT_NAME:
					createEdgeCitation((EdgeCitationLinksElement) elmt);
					break;
				case EdgeSupportLinksElement.ASPECT_NAME:
					createEdgeSupport((EdgeSupportLinksElement) elmt);
					break;
				case NodeSupportLinksElement.ASPECT_NAME:
					createNodeSupport((NodeSupportLinksElement) elmt);
					break;
				case NodeCitationLinksElement.ASPECT_NAME:
					createNodeCitation((NodeCitationLinksElement) elmt);
					break;
				case FunctionTermElement.ASPECT_NAME:
					createFunctionTerm((FunctionTermElement)elmt);
					break;
					
				case Provenance.ASPECT_NAME:   // we ignore the provenance aspect in the uploaded networ for now.
												// uncomment the following line to save it.
		//			this.provenanceHistory = (Provenance)elmt;
					break;
				default:    // opaque aspect
					createAspectElement(elmt);
					if ( elmt.getAspectName().equals("subNetworks") 
							|| elmt.getAspectName().equals(SubNetworkElement.ASPECT_NAME)) {
						 OpaqueElement e = (OpaqueElement) elmt;
						 subNetworkIds.add(e.getData().get("@id").asLong())  ;
					} 
			}

		} 
		  // check data integrity.
		  String errMsg = nodeIdTracker.checkUndefinedIds() ;
		  if ( errMsg !=null) 
			  throw new NdexException(errMsg);
		  
		  errMsg = edgeIdTracker.checkUndefinedIds();
		  if ( errMsg !=null )
			  throw new NdexException(errMsg);
		  
		  errMsg = supportIdTracker.checkUndefinedIds();
		  if ( errMsg != null)
			  warnings.add(errMsg);
		  
		  errMsg = citationIdTracker.checkUndefinedIds();
		  if ( errMsg !=null)
			  warnings.add(errMsg);
		  
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
		  
		//  Timestamp modificationTime = new Timestamp(Calendar.getInstance().getTimeInMillis());

		  if(metadata !=null) {
			  
			  if (networkNameIsAssigned) {
				 Long l = metadata.getMetaDataElement(NetworkAttributesElement.ASPECT_NAME).getElementCount();
				 metadata.getMetaDataElement(NetworkAttributesElement.ASPECT_NAME).setElementCount(l + 1);
				 writeCXElement(new NetworkAttributesElement(null, this.networkName, ATTRIBUTE_DATA_TYPE.STRING));
			  }
			  
			  //Remove the NdexNetworkStatus metadata if it exists
			  metadata.remove(NdexNetworkStatus.ASPECT_NAME);
			  
			  Set<Long> consistencyGrpIds = new TreeSet<>();
			  
			  for ( MetaDataElement e: metadata.toCollection()) {

				  // check if idCounter is defined in certain espects.
				  if (  (e.getName().equals(NodesElement.ASPECT_NAME) || e.getName().equals(EdgesElement.ASPECT_NAME) || 
								  e.getName().equals(CitationElement.ASPECT_NAME) || 
								  e.getName().equals(SupportElement.ASPECT_NAME) ) 
						  &&  e.getIdCounter() == null ) 
 						   throw new NdexException ( "Idcounter value is not found in metadata of aspect " + e.getName());
				  
				  //check if elementCount matches between metadata and data
				  Long cntObj = e.getElementCount();
				  if ( cntObj == null) {
					  warnings.add("ElementCount missing in Metadata of aspect " + e.getName());
					  CXAspectWriter w = this.aspectTable.get(e.getName());
					  if ( w == null)
						  e.setElementCount(0L);
					  else {
						  warnings.add("ElementCount in Metadata of aspect " + e.getName() + " is set to " + w.getElementCount() +" by NDEx server.");
						  e.setElementCount(w.getElementCount());
					  }
				  } 
					  
				  long declaredCnt = e.getElementCount().longValue() ;
				  if ( declaredCnt == 0) {
						  CXAspectWriter w = this.aspectTable.get(e.getName());
						  if (w == null)  { // no element found, remove the metadatEntry
							  metadata.remove(e.getName());
							  warnings.add("Metadata element of aspect " + e.getName() + " is removed by NDEx because the element count is 0.");
						  } else  // maybe this should be raised as an error?
							  warnings.add ("Element count mismatch in aspect " + e.getName() + ". Metadata declared element count " + e.getElementCount()+
								  ", but only " + w.getElementCount() + " was received in CX.");
				  } else {
						  if ( this.aspectTable.get(e.getName()) == null || declaredCnt != this.aspectTable.get(e.getName()).getElementCount()) {
							  warnings.add ("Element count mismatch in aspect " + e.getName() + ". Metadate declared element count " + e.getElementCount()+
							  ", but only " + (this.aspectTable.get(e.getName()) == null ? 0:this.aspectTable.get(e.getName()).getElementCount()) + " was received in CX.");
						  }
				  }
				  
				  //check consistencyGrp 
				  Long cGrpIds = e.getConsistencyGroup();
				  if ( cGrpIds == null) 
					  warnings.add("Aspect " + e.getName() + " doesn't have consistencyGroupId defined in metadata.");
				  else
				     consistencyGrpIds.add(cGrpIds);
			  }
			  
			  // check if all the aspects has metadata
			  for ( String aspectName : aspectTable.keySet() ){
				  if ( metadata.getMetaDataElement(aspectName) == null)
					  warnings.add ("Aspect " + aspectName + " is not defined in MetaData section.");
			  }
			  
			  if (consistencyGrpIds.size()!=1) {
				  warnings.add("Unmatching consisencyGroupIds found in Metadata: " + Arrays.toString(consistencyGrpIds.toArray()));
			  }
			  			  
		  } else 
			  throw new NdexException ("No CX metadata found in this CX stream.");
  
		  closeAspectStreams();
	}
	
	
	private void createSolrIndex(NetworkSummary summary) throws SolrServerException, IOException, ObjectNotFoundException, NdexException, SQLException {

		
		List<Map<Permissions, Collection<String>>> permissionTable =  dao.getAllMembershipsOnNetwork(networkId);
		Map<Permissions,Collection<String>> userMemberships = permissionTable.get(0);
		Map<Permissions,Collection<String>> grpMemberships = permissionTable.get(1);
		globalIdx.createIndexDocFromSummary(summary,ownerName,
				userMemberships.get(Permissions.READ),
				userMemberships.get(Permissions.WRITE),
				grpMemberships.get(Permissions.READ),
				grpMemberships.get(Permissions.WRITE));
		
		globalIdx.commit();
   	
	}


    
	private void createNetworkAttribute(NetworkAttributesElement e) throws NdexException, IOException {
		
		if ( e.getName().equals(NdexClasses.Network_P_name) && ( networkName == null || e.getSubnetwork() == null)) {
				this.networkName = e.getValue();
				if ( e.getSubnetwork() != null ) {
					properties.add(new NdexPropertyValuePair(e.getSubnetwork(), e.getName(), e.getValue(), ATTRIBUTE_DATA_TYPE.STRING.toString()));
					this.networkNameIsAssigned = true;
				} else 
					this.networkNameIsAssigned = false;					
		} else if ( e.getName().equals(NdexClasses.Network_P_desc) &&  e.getSubnetwork() == null) {
			this.description = e.getValue();
		} else if ( e.getName().equals(NdexClasses.Network_P_version) &&  e.getSubnetwork() == null) {
			this.version = e.getValue();
		} else 
			properties.add(new NdexPropertyValuePair(e.getSubnetwork(),e.getName(), 
					(e.isSingleValue() ? e.getValue(): e.getValueAsJsonString()), e.getDataType().toString()));
		
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

		nodeIdTracker.addDefinedElementId(node.getId());
		writeCXElement(node);
		this.globalIdx.addCXNodeToIndex(node);	
		   
		tick();   
	}	 

	private void createCXCitation(CitationElement citation) throws NdexException, IOException {

		citationIdTracker.addDefinedElementId(citation.getId());
		writeCXElement(citation);		   
		tick();   
	}	 

	private void createFunctionTerm(FunctionTermElement funcTerm) throws NdexException, IOException {

		nodeIdTracker.addReferenceId(funcTerm.getNodeID());
		writeCXElement(funcTerm);		   
		
		globalIdx.addFunctionTermToIndex(funcTerm);
		tick();   
	}	 

	
	private void createCXSupport(SupportElement support) throws NdexException, IOException {
		supportIdTracker.addDefinedElementId(support.getId());
		writeCXElement(support);		   
		tick();   
	}	 
	
	
	private void createCXEdge(EdgesElement ee) throws NdexException, IOException {
		
		edgeIdTracker.addDefinedElementId(ee.getId());
		
		nodeIdTracker.addReferenceId(Long.valueOf(ee.getSource()));
		nodeIdTracker.addReferenceId(Long.valueOf(ee.getTarget()));
		
		writeCXElement(ee);

		tick();	
	   
	}
	
	private void createEdgeCitation(EdgeCitationLinksElement elmt) throws IOException {
		  for ( Long sourceId : elmt.getSourceIds()) {
			  edgeIdTracker.addReferenceId(sourceId);
		  }
		  
		  for ( Long citationSID : elmt.getCitationIds()) {
			  citationIdTracker.addReferenceId(citationSID);
		  }
	  	  writeCXElement(elmt);

	}

	private void createEdgeSupport(EdgeSupportLinksElement elmt) throws IOException {
		  for ( Long sourceId : elmt.getSourceIds()) {
			edgeIdTracker.addReferenceId(sourceId);
		  }
		  
		  for ( Long supportId : elmt.getSupportIds()) {
			  supportIdTracker.addReferenceId(supportId);
		  }
	  	  writeCXElement(elmt);

	}

	private void createNodeCitation(NodeCitationLinksElement elmt) throws IOException {
		  for ( Long sourceId : elmt.getSourceIds()) {
			  nodeIdTracker.addReferenceId(sourceId);
		  }
		  
		  for ( Long citationSID : elmt.getCitationIds()) {
			  citationIdTracker.addReferenceId(citationSID);
		  }
	  	  writeCXElement(elmt);
	}

	private void createNodeSupport(NodeSupportLinksElement elmt) throws IOException {
		  for ( Long sourceId : elmt.getSourceIds()) {
			nodeIdTracker.addReferenceId(sourceId);
		  }
		  
		  for ( Long supportId : elmt.getSupportIds()) {
			  supportIdTracker.addReferenceId(supportId);
		  }
		  
	  	  writeCXElement(elmt);

	}

	
	private void createAspectElement(AspectElement element) throws NdexException, IOException {
		writeCXElement(element);
		tick();			
	}
	

	private void addNodeAttribute(NodeAttributesElement e) throws NdexException, IOException{
		
			nodeIdTracker.addReferenceId(e.getPropertyOf());
		
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
	

	private void closeAspectStreams() {
		for ( Map.Entry<String, CXAspectWriter> entry : aspectTable.entrySet() ){
			try {
				entry.getValue().close();
			} catch (IOException e) {
				logger.error("Failed to close output stream when closing CXNetworkLoader: " + e.getMessage());
			}
		}
	}
	
	@Override
	public void close() {
		closeAspectStreams();
		
		try {
			this.inputStream.close();
		} catch (IOException e) {
			logger.error("Failed to close input stream when closing CXNetworkLoader: " + e.getMessage());
		}
	}


	
	
	
}
