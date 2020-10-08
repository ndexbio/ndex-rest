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
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.UUID;

import org.ndexbio.common.NdexClasses;
import org.ndexbio.common.cx.CXNetworkFileGenerator;
import org.ndexbio.common.models.dao.postgresql.NetworkDAO;
import org.ndexbio.common.solr.SingleNetworkSolrIdxManager;
import org.ndexbio.common.util.Util;
import org.ndexbio.cx2.aspect.element.core.CxNode;
import org.ndexbio.cx2.converter.AspectAttributeStat;
import org.ndexbio.cxio.aspects.datamodels.ATTRIBUTE_DATA_TYPE;
import org.ndexbio.cxio.aspects.datamodels.CartesianLayoutElement;
import org.ndexbio.cxio.aspects.datamodels.CyVisualPropertiesElement;
import org.ndexbio.cxio.aspects.datamodels.EdgeAttributesElement;
import org.ndexbio.cxio.aspects.datamodels.EdgesElement;
import org.ndexbio.cxio.aspects.datamodels.NetworkAttributesElement;
import org.ndexbio.cxio.aspects.datamodels.NodeAttributesElement;
import org.ndexbio.cxio.aspects.datamodels.NodesElement;
import org.ndexbio.cxio.aspects.datamodels.SubNetworkElement;
import org.ndexbio.cxio.aspects.readers.CartesianLayoutFragmentReader;
import org.ndexbio.cxio.aspects.readers.CyVisualPropertiesFragmentReader;
import org.ndexbio.cxio.aspects.readers.EdgeAttributesFragmentReader;
import org.ndexbio.cxio.aspects.readers.EdgesFragmentReader;
import org.ndexbio.cxio.aspects.readers.GeneralAspectFragmentReader;
import org.ndexbio.cxio.aspects.readers.NetworkAttributesFragmentReader;
import org.ndexbio.cxio.aspects.readers.NodeAttributesFragmentReader;
import org.ndexbio.cxio.aspects.readers.NodesFragmentReader;
import org.ndexbio.cxio.core.CXAspectWriter;
import org.ndexbio.cxio.core.CxElementReader2;
import org.ndexbio.cxio.core.interfaces.AspectElement;
import org.ndexbio.cxio.core.interfaces.AspectFragmentReader;
import org.ndexbio.cxio.metadata.MetaDataCollection;
import org.ndexbio.cxio.metadata.MetaDataElement;
import org.ndexbio.cxio.misc.OpaqueElement;
import org.ndexbio.model.cx.CitationElement;
import org.ndexbio.model.cx.EdgeCitationLinksElement;
import org.ndexbio.model.cx.EdgeSupportLinksElement;
import org.ndexbio.model.cx.FunctionTermElement;
import org.ndexbio.model.cx.NdexNetworkStatus;
import org.ndexbio.model.cx.NodeCitationLinksElement;
import org.ndexbio.model.cx.NodeSupportLinksElement;
import org.ndexbio.model.cx.SupportElement;
import org.ndexbio.model.exceptions.DuplicateObjectException;
import org.ndexbio.model.exceptions.NdexException;
import org.ndexbio.model.exceptions.ObjectNotFoundException;
import org.ndexbio.model.object.NdexPropertyValuePair;
import org.ndexbio.model.object.network.NetworkIndexLevel;
import org.ndexbio.model.object.network.NetworkSummary;
import org.ndexbio.model.object.network.VisibilityType;
import org.ndexbio.rest.Configuration;
import org.ndexbio.task.NdexServerQueue;
import org.ndexbio.task.SolrIndexScope;
import org.ndexbio.task.SolrTaskRebuildNetworkIdx;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.core.JsonParseException;
import com.fasterxml.jackson.databind.JsonMappingException;

public class CXNetworkLoader implements AutoCloseable {
	
    protected static Logger logger = LoggerFactory.getLogger(CXNetworkLoader.class);

	//private static final String nodeName = "name";
    
    public static final String CX1FileName = "network.cx";
    public static final String CX1AspectDir = "aspects";
    public static final String CX1ArchiveFileName = "network.arc";
    
	public static final int defaultSampleSize = 300;
	public static final int defaultSampleGenerationThreshhold = 1000;
	private static final int maximumNumberWarningMessages = 20;
    
	protected int sampleGenerationThreshold;
	
    private boolean isUpdate;
		
//	private InputStream inputStream;
//	private String ownerName;
	private UUID networkId;

    private String rootPath;
	
    //mapping tables mapping from element SID to internal ID. 
	private AspectElementIdTracker nodeIdTracker;
	private AspectElementIdTracker edgeIdTracker;
	private AspectElementIdTracker citationIdTracker;
	private AspectElementIdTracker supportIdTracker;
		
	protected Set<Long> subNetworkIds;
		
	long opaqueCounter ;

	long serverElementLimit; 
	
	protected String networkName ;
	protected String description;
	protected String version;
	
	private boolean networkNameIsAssigned;
	
	protected MetaDataCollection metadata;
	
	protected List<NdexPropertyValuePair> properties;
		
	protected Map<String,CXAspectWriter> aspectTable;
	private List<String> warnings;
	private NetworkDAO dao;
	private VisibilityType visibility;
	private Set<String> indexedFields;
	
	private boolean foundEdgeInteractionAttr;
	private boolean foundNodeNameAttr;
	private boolean foundNodeRepresentAttr;
	
	
	protected AspectAttributeStat attributeStats;

		
//	protected String updatedBy;
	public CXNetworkLoader(UUID networkUUID, boolean isUpdate, NetworkDAO networkDao, VisibilityType visibility, Set<String> IndexedFields, int sampleGenerationThreshold) {
		super();
		
		this.isUpdate = isUpdate;
		
//		this.ownerName = ownerUserName;
		this.networkId = networkUUID;
		this.rootPath = Configuration.getInstance().getNdexRoot() + "/data/" + networkId + "/" + CX1AspectDir+ "/";
		this.networkName = null;

	//	this.inputStream = new FileInputStream(Configuration.getInstance().getNdexRoot() + "/data/" + networkId + "/network.cx");
				
		serverElementLimit = Configuration.getInstance().getServerElementLimit();
		
		warnings = new ArrayList<> ();
		networkNameIsAssigned = false;

		opaqueCounter = 0;
		
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
		attributeStats = new AspectAttributeStat();
		properties = new ArrayList<>();
		dao = networkDao;
	//	updatedBy = updaterUserName;
		this.visibility = visibility;
		this.indexedFields = IndexedFields;
		if ( sampleGenerationThreshold <= defaultSampleGenerationThreshhold)
			this.sampleGenerationThreshold = defaultSampleGenerationThreshhold;
		else
			this.sampleGenerationThreshold = sampleGenerationThreshold;
		
		this.foundEdgeInteractionAttr = false;
		this.foundNodeNameAttr = false;
		this.foundNodeRepresentAttr = false;
		
	}
	
	protected UUID getNetworkId() {return this.networkId;}
	protected NetworkDAO getDAO () {return dao;}
	protected List<String> getWarnings() {return warnings;}
	
	private static CxElementReader2 createCXReader (InputStream in) throws IOException {
		HashSet<AspectFragmentReader> readers = new HashSet<>(20);
		
		  readers.add(EdgesFragmentReader.createInstance());
		  readers.add(EdgeAttributesFragmentReader.createInstance());
		  readers.add(NetworkAttributesFragmentReader.createInstance());
		  readers.add(NodesFragmentReader.createInstance());
		  readers.add(NodeAttributesFragmentReader.createInstance());
		  readers.add(CartesianLayoutFragmentReader.createInstance());
		  readers.add(CyVisualPropertiesFragmentReader.createInstance());
		  
		  readers.add(new GeneralAspectFragmentReader<> (NdexNetworkStatus.ASPECT_NAME,
				NdexNetworkStatus.class));
//		  readers.add(new GeneralAspectFragmentReader<> (NamespacesElement.ASPECT_NAME,NamespacesElement.class));
		  readers.add(new GeneralAspectFragmentReader<> (FunctionTermElement.ASPECT_NAME,FunctionTermElement.class));
		  readers.add(new GeneralAspectFragmentReader<> (CitationElement.ASPECT_NAME,CitationElement.class));
		  readers.add(new GeneralAspectFragmentReader<> (SupportElement.ASPECT_NAME,SupportElement.class));
//		  readers.add(new GeneralAspectFragmentReader (ReifiedEdgeElement.ASPECT_NAME,ReifiedEdgeElement.class));
		  readers.add(new GeneralAspectFragmentReader<> (EdgeCitationLinksElement.ASPECT_NAME,EdgeCitationLinksElement.class));
		  readers.add(new GeneralAspectFragmentReader<> (EdgeSupportLinksElement.ASPECT_NAME,EdgeSupportLinksElement.class));
		  readers.add(new GeneralAspectFragmentReader<> (NodeCitationLinksElement.ASPECT_NAME,NodeCitationLinksElement.class));
		  readers.add(new GeneralAspectFragmentReader<> (NodeSupportLinksElement.ASPECT_NAME,NodeSupportLinksElement.class));
//		  readers.add(new GeneralAspectFragmentReader<> (Provenance.ASPECT_NAME,Provenance.class));
		  return  new CxElementReader2(in, readers,true);
	}
	
	
	private void addWarning(String warningStr) {
		if (warnings.size() >= maximumNumberWarningMessages)
			return;
		
		warnings.add( warningStr);		
	}
	
	public void persistCXNetwork() throws IOException, DuplicateObjectException, ObjectNotFoundException, NdexException, SQLException {
		        	    
	 //   try {
	    	
	      //Create dir
		  java.nio.file.Path dir = Paths.get(rootPath);
		  Files.createDirectory(dir);
		  
		  try (	InputStream inputStream = new FileInputStream(Configuration.getInstance().getNdexRoot() + "/data/" + networkId + "/" + CX1FileName) ) {
	
			  persistNetworkData(inputStream, false); 
		  
		//	  logger.info("aspects have been stored.");
		  
			  NetworkSummary summary = new NetworkSummary();

				//handle the network properties 
				summary.setExternalId(this.networkId);
				if ( isUpdate) {
					summary.setVisibility(dao.getNetworkVisibility(networkId));
				} else 
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
				summary.setSubnetworkIds(subNetworkIds);
				try {
				//	dao.saveNetworkEntry(summary, (this.provenanceHistory == null? null: provenanceHistory.getEntity()), metadata);
					dao.saveNetworkEntry(summary, metadata, false);
						
					dao.commit();
				} catch (SQLException e) {
					dao.rollback();	
					throw new NdexException ("DB error when saving network summary: " + e.getMessage(), e);
				}
		  
				
				// create the network sample if the network has more than 500 edges
				boolean sampleCreated = false;
				if (summary.getEdgeCount() > sampleGenerationThreshold)  {
			  
					Long subNetworkId = null;
					if (subNetworkIds.size()>0 )  {
						for ( Long i : subNetworkIds) {
							subNetworkId = i;
							break;
						}
					}
					CXNetworkSampleGenerator g = new CXNetworkSampleGenerator(this.networkId, subNetworkId, metadata, defaultSampleSize);
					g.createSampleNetwork();
					sampleCreated = true;
			  
				}
			  				
				//recreate CX and CX2 files
				reCreateCXFiles(networkId,metadata, dao, this.attributeStats, this.subNetworkIds.isEmpty());
				
				try {
					if ( !isUpdate) {
						dao.setFlag(this.networkId, "iscomplete", true);
					} 
					if (visibility != null) {
						dao.updateNetworkVisibility(networkId, visibility, true);
					}
					
					dao.setFlag(this.networkId, "has_sample", sampleCreated);
					dao.setFlag(this.networkId, "has_layout", metadata.getMetaDataElement(CartesianLayoutElement.ASPECT_NAME)!=null);
					dao.unlockNetwork(this.networkId);

					
					//gzip the archived file.
					
					
				//	dao.commit();
				} catch (SQLException e) {
					dao.rollback();
					throw new NdexException ("DB error when setting unlock flag: " + e.getMessage(), e);
				}

				NetworkIndexLevel indexLevel = dao.getIndexLevel(networkId);
				boolean needIndividualIndex = this.nodeIdTracker.getDefinedElementSize() >= SingleNetworkSolrIdxManager.AUTOCREATE_THRESHHOLD;
				if ( isUpdate && indexLevel != NetworkIndexLevel.NONE)  {
				   if ( needIndividualIndex)
					  NdexServerQueue.INSTANCE.addSystemTask(new SolrTaskRebuildNetworkIdx(networkId,SolrIndexScope.both,false,indexedFields, indexLevel,false ));
				   else 
				      NdexServerQueue.INSTANCE.addSystemTask(new SolrTaskRebuildNetworkIdx(networkId,SolrIndexScope.global,false,indexedFields, indexLevel,false ));
				} else {
					if (needIndividualIndex)
						NdexServerQueue.INSTANCE.addSystemTask(new SolrTaskRebuildNetworkIdx(networkId,SolrIndexScope.individual,!isUpdate, indexedFields, NetworkIndexLevel.NONE,false));
					else {
						dao.setFlag(this.networkId, "iscomplete", true);
						dao.commit();
					}	
				}
		  }

	}

	/**
	 * 
	 * @param networkId
	 * @param m
	 * @param dao
	 * @param attrStats This object will help the cx2 converter to generate metadata for some cx2 aspect. When this 
	 *        parameter is null, the cx2converter will run analyze attributes in the converter.
	 * @throws JsonParseException
	 * @throws JsonMappingException
	 * @throws SQLException
	 * @throws IOException
	 * @throws NdexException
	 * @throws FileNotFoundException
	 * @throws  
	 */
	public static void reCreateCXFiles(UUID networkId, MetaDataCollection m, NetworkDAO dao, AspectAttributeStat attrStats, boolean isSingleNetwork) throws JsonParseException, JsonMappingException, SQLException, IOException,
			NdexException, FileNotFoundException {
		CXNetworkFileGenerator g = new CXNetworkFileGenerator ( networkId, dao);
		String tmpFileName = CXNetworkFileGenerator.createNetworkFile(networkId.toString(),g.getMetaData());
		
		String pathPrefix = Configuration.getInstance().getNdexRoot() + "/data/";
		
		java.nio.file.Path src = Paths.get(tmpFileName);
		java.nio.file.Path tgt = Paths.get( pathPrefix + networkId + "/" + CX1FileName);
		
		String archivedFileName = pathPrefix + networkId + "/" + CX1ArchiveFileName;
		java.nio.file.Path tgt2 = Paths.get(archivedFileName);
		
		Files.move(tgt, tgt2, StandardCopyOption.ATOMIC_MOVE); 				
		Files.move(src, tgt, StandardCopyOption.ATOMIC_MOVE,StandardCopyOption.REPLACE_EXISTING);  
		
		Util.aSyncCompressGZIP(archivedFileName);
		
		// create the CX2 file
		if (isSingleNetwork) {
			CXToCX2ServerSideConverter cvtr = new CXToCX2ServerSideConverter( pathPrefix,
				m, networkId.toString(), attrStats ,false);
			dao.setCxMetadata(networkId, cvtr.convert()); 
			if ( !cvtr.getWarning().isEmpty()) {
				List<String> w = cvtr.getWarning(); 
				w.addAll(0, dao.getWarnings(networkId));
				dao.setWarning(networkId, w);
			}
		}
	}
	

	
	/** 
	 * If it is called from a network import function ( db migrator), we don't remove the provenance entry from the metadata.
	 * @param  isAspectUpdate set this flag if updating aspects in network. We are not check if node aspect is missing when 
	 *          this parameter is set to true.
	 * @throws IOException
	 * @throws DuplicateObjectException
	 * @throws NdexException
	 * @throws ObjectNotFoundException
	 */
	protected void persistNetworkData(InputStream in, boolean isAspectUpdate)
			throws IOException, DuplicateObjectException, NdexException, ObjectNotFoundException {
				
		CxElementReader2 cxreader = createCXReader(in);
		  
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
				case EdgeAttributesElement.ASPECT_NAME:
					addEdgeAttribute((EdgeAttributesElement) elmt);
					break;
				case NetworkAttributesElement.ASPECT_NAME: //network attributes
					createNetworkAttribute(( NetworkAttributesElement) elmt);
					break;
				case CartesianLayoutElement.ASPECT_NAME:
					addCartesianLayoutElement((CartesianLayoutElement) elmt);
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
				case CyVisualPropertiesElement.ASPECT_NAME: 
					createCyVisualProperitiesElement((CyVisualPropertiesElement) elmt);
					break;
/*				case Provenance.ASPECT_NAME:   // provenance is treated as an opaque aspect now.
					this.provenanceHistory = (Provenance)elmt;
					break; */
				default:    // opaque aspect
					createAspectElement(elmt);
					if ( elmt.getAspectName().equals("subNetworks") 
							|| elmt.getAspectName().equals(SubNetworkElement.ASPECT_NAME)) {
						 OpaqueElement e = (OpaqueElement) elmt;
						 subNetworkIds.add(Long.valueOf(e.getData().get("@id").asLong()) ) ;
					} 
			}

		} 
		  //save the metadata
		  MetaDataCollection postmetadata = cxreader.getPostMetaData();
		  if ( postmetadata !=null) {
			  if( metadata == null) {
				  metadata = postmetadata;
			  } else {
				  for (MetaDataElement e : postmetadata) {
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
		  
		  if(metadata !=null) {
			  
			  if ( !isAspectUpdate && metadata.getMetaDataElement(NodesElement.ASPECT_NAME) == null ) {
				  throw new NdexException ("Nodes aspect is missing.");
			  }
			  
			  if (networkNameIsAssigned) {
				 MetaDataElement ee =   metadata.getMetaDataElement(NetworkAttributesElement.ASPECT_NAME);
				 if ( ee != null ) {
					 Long l = ee.getElementCount();
					 metadata.getMetaDataElement(NetworkAttributesElement.ASPECT_NAME).setElementCount(l + 1);
				 } else {
					MetaDataElement ne = new MetaDataElement(); 
					ne.setElementCount(1L);
					ne.setConsistencyGroup(1L);
					ne.setName(NetworkAttributesElement.ASPECT_NAME);
					ne.setVersion("1.0");
					metadata.add(ne);
					
				 }
				 writeCXElement(new NetworkAttributesElement(null, NdexClasses.Network_P_name, this.networkName));
			  }
			  
			  //Remove the NdexNetworkStatus metadata if it exists
			  metadata.remove(NdexNetworkStatus.ASPECT_NAME);
			  			  
			  Set<String> tobeRemovedMetaData = new HashSet<>();
			  for ( MetaDataElement e: metadata) {

				  // check if idCounter is defined in certain espects.
				  /* this is no longer required so that a CX2 network can be converted back to cx1
				  if (  (e.getName().equals(NodesElement.ASPECT_NAME) || e.getName().equals(EdgesElement.ASPECT_NAME) || 
								  e.getName().equals(CitationElement.ASPECT_NAME) || 
								  e.getName().equals(SupportElement.ASPECT_NAME) ) 
						  &&  e.getIdCounter() == null ) 
 						   throw new NdexException ( "Idcounter value is not found in metadata of aspect " + e.getName());
				  */
				  
				  //check if elementCount is missing in metadata
				  Long cntObj = e.getElementCount();
				  if ( cntObj == null) {
//					  warnings.add("ElementCount missing in Metadata of aspect " + e.getName());
					  CXAspectWriter w = this.aspectTable.get(e.getName());
					  if ( w == null)
						  e.setElementCount(0L);
					  else {
//						  warnings.add("ElementCount in Metadata of aspect " + e.getName() + " is set to " + w.getElementCount() +" by NDEx server.");
						  e.setElementCount(Long.valueOf(w.getElementCount()));
					  }
				  } 
					  
				  //check if elementCount matches between metadata and data
		//		  if ( !e.getName().equals(Provenance.ASPECT_NAME)) {
				  
					  long declaredCnt = e.getElementCount().longValue() ;
					  if ( declaredCnt == 0) {
						  CXAspectWriter w = this.aspectTable.get(e.getName());
						  if (w == null)  { // no element found, remove the metadatEntry
							    //metadata.remove(e.getName());
							    tobeRemovedMetaData.add(e.getName());
						  } else  // maybe this should be raised as an error?
							  warnings.add ("Element count mismatch in aspect " + e.getName() + ". Metadata declared element count " + e.getElementCount()+
								  ", but " + w.getElementCount() + " was received in CX.");
					  } else {
						  long actualCount = this.aspectTable.get(e.getName()) == null ? 0:this.aspectTable.get(e.getName()).getElementCount();
						  if ( this.aspectTable.get(e.getName()) == null || declaredCnt != this.aspectTable.get(e.getName()).getElementCount()) {
							  warnings.add ("Element count mismatch in aspect " + e.getName() + ". Metadata declared element count " + e.getElementCount()+
							  ", but " + actualCount + " was received in CX.");
						  }
						  if (actualCount == 0) {
							  //metadata.remove(e.getName());
						    tobeRemovedMetaData.add(e.getName());
					 	    warnings.add("Metadata of aspect " + e.getName() + " is removed by NDEx server because this aspect has no data in it.");
						  }
					  }
		//		  }
				  
				  //check consistencyGrp 
				/*  Long cGrpIds = e.getConsistencyGroup();
				  if ( cGrpIds == null) 
					  warnings.add("Aspect " + e.getName() + " doesn't have consistencyGroupId defined in metadata.");
				  else
				     consistencyGrpIds.add(cGrpIds); */
			  }
			  
			  for (String name : tobeRemovedMetaData) {
				  metadata.remove(name);
			  }
			  // check if all the aspects has metadata
			  for ( String aspectName : aspectTable.keySet() ){
				  if ( metadata.getMetaDataElement(aspectName) == null) {
					  addWarning ("Aspect " + aspectName + " is not defined in MetaData section. NDEx is adding one without a version in it.");
					  MetaDataElement mElmt = new MetaDataElement();
					  mElmt.setName(aspectName);
					  mElmt.setElementCount(this.aspectTable.get(aspectName).getElementCount());
					  metadata.add(mElmt);
				  }	  
			  }
			  
			  // check data integrity.
			  String errMsg = metadata.getMetaDataElement(NodesElement.ASPECT_NAME) !=null? nodeIdTracker.checkUndefinedIds() : null;
			  if ( errMsg !=null) 
				  throw new NdexException(errMsg);
			  
			  errMsg = metadata.getMetaDataElement(EdgesElement.ASPECT_NAME) !=null? edgeIdTracker.checkUndefinedIds() : null;
			  if ( errMsg !=null )
				  throw new NdexException(errMsg);
			  
			  errMsg = metadata.getMetaDataElement(SupportElement.ASPECT_NAME) !=null ? supportIdTracker.checkUndefinedIds() : null;
			  if ( errMsg != null)
				  warnings.add(errMsg);
			  
			  errMsg = metadata.getMetaDataElement(CitationElement.ASPECT_NAME) !=null? citationIdTracker.checkUndefinedIds() : null;
			  if ( errMsg !=null)
				  warnings.add(errMsg);
			  
			/*  if (consistencyGrpIds.size()!=1) {
				  warnings.add("Unmatching consisencyGroupIds found in Metadata: " + Arrays.toString(consistencyGrpIds.toArray()));
			  } */
			  			  
		  } else 
			  throw new NdexException ("No CX metadata found in this CX stream.");
  
		  closeAspectStreams();
	}
	
	/*
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
   	
	} */


    
	private void createNetworkAttribute(NetworkAttributesElement e) throws IOException, NdexException {
		
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
		attributeStats.addNetworkAttribute(e);		

	}
	
	private void writeCXElement(AspectElement element) throws IOException {
		String aspectName = element.getAspectName();
//		if ( aspectName.equals("visualProperties"))
//			aspectName =CyVisualPropertiesElement.ASPECT_NAME;
		CXAspectWriter writer = aspectTable.get(aspectName);
		if ( writer == null) {
			//logger.info("creating new file for aspect " + aspectName);
			writer = new CXAspectWriter(rootPath + aspectName);
			aspectTable.put(aspectName, writer);
		}
		writer.writeCXElement(element);
		writer.flush();
	}
	
	
	private void createCXNode(NodesElement node) throws NdexException, IOException {

		nodeIdTracker.addDefinedElementId(node.getId());
		writeCXElement(node);
		attributeStats.addNode(node);
	}	 

	private void createCXCitation(CitationElement citation) throws NdexException, IOException {

		citationIdTracker.addDefinedElementId(citation.getId());
		writeCXElement(citation);		   
	}	 

	private void createFunctionTerm(FunctionTermElement funcTerm) throws IOException {

		nodeIdTracker.addReferenceId(funcTerm.getNodeID(), FunctionTermElement.ASPECT_NAME);
		writeCXElement(funcTerm);		   
	}	 

	private void createCyVisualProperitiesElement(CyVisualPropertiesElement visualProperty) throws IOException {
		
		String po = visualProperty.getProperties_of();
		if(po.equals("edges")) {
			edgeIdTracker.addReferenceId(visualProperty.getApplies_to(), CyVisualPropertiesElement.ASPECT_NAME);
		} else if ( po.equals("nodes")) { 
			nodeIdTracker.addReferenceId(visualProperty.getApplies_to(), CyVisualPropertiesElement.ASPECT_NAME);
		}	
		writeCXElement(visualProperty);		  
		attributeStats.addCyVisualPropertiesElement(visualProperty);

	}	 
	
	private void createCXSupport(SupportElement support) throws NdexException, IOException {
		supportIdTracker.addDefinedElementId(support.getId());
		writeCXElement(support);		   
	}	 
	
	
	private void createCXEdge(EdgesElement ee) throws NdexException, IOException {
		
		edgeIdTracker.addDefinedElementId(ee.getId());
		
		nodeIdTracker.addReferenceId(ee.getSource(), EdgesElement.ASPECT_NAME);
		nodeIdTracker.addReferenceId(ee.getTarget(), EdgesElement.ASPECT_NAME);
		
		writeCXElement(ee);	   
		attributeStats.addEdge(ee);

	}
	
	private void createEdgeCitation(EdgeCitationLinksElement elmt) throws IOException {
		  for ( Long sourceId : elmt.getSourceIds()) {
			  edgeIdTracker.addReferenceId(sourceId, EdgeCitationLinksElement.ASPECT_NAME);
		  }
		  
		  for ( Long citationSID : elmt.getCitationIds()) {
			  citationIdTracker.addReferenceId(citationSID, EdgeCitationLinksElement.ASPECT_NAME);
		  }
	  	  writeCXElement(elmt);

	}

	private void createEdgeSupport(EdgeSupportLinksElement elmt) throws IOException {
		  for ( Long sourceId : elmt.getSourceIds()) {
			edgeIdTracker.addReferenceId(sourceId, EdgeSupportLinksElement.ASPECT_NAME);
		  }
		  
		  for ( Long supportId : elmt.getSupportIds()) {
			  supportIdTracker.addReferenceId(supportId, EdgeSupportLinksElement.ASPECT_NAME);
		  }
	  	  writeCXElement(elmt);

	}

	private void createNodeCitation(NodeCitationLinksElement elmt) throws IOException {
		  for ( Long sourceId : elmt.getSourceIds()) {
			  nodeIdTracker.addReferenceId(sourceId, NodeCitationLinksElement.ASPECT_NAME);
		  }
		  
		  for ( Long citationSID : elmt.getCitationIds()) {
			  citationIdTracker.addReferenceId(citationSID, NodeCitationLinksElement.ASPECT_NAME);
		  }
	  	  writeCXElement(elmt);
	}

	private void createNodeSupport(NodeSupportLinksElement elmt) throws IOException {
		  for ( Long sourceId : elmt.getSourceIds()) {
			nodeIdTracker.addReferenceId(sourceId, NodeSupportLinksElement.ASPECT_NAME);
		  }
		  
		  for ( Long supportId : elmt.getSupportIds()) {
			  supportIdTracker.addReferenceId(supportId, NodeSupportLinksElement.ASPECT_NAME);
		  }
		  
	  	  writeCXElement(elmt);

	}

	
	private void createAspectElement(AspectElement element) throws IOException {
		writeCXElement(element);
	}
	

	private void addNodeAttribute(NodeAttributesElement e) throws IOException, NdexException{
		if ( (!this.foundNodeNameAttr) &&e.getName().equals(CxNode.NAME)) { 
			addWarning ( "Attribute 'name' on node " + e.getPropertyOf() + 
					" is not allowed in CX specification. Please consider recreate this network in the latest version of CyNDEx2 and Cytoscape.");
			this.foundNodeNameAttr = true;	
		} else if ((!this.foundNodeRepresentAttr) &&e.getName().equals(CxNode.REPRESENTS)) {		
			addWarning ( "Attribute 'represents' on node " + e.getPropertyOf() + 
					" is not allowed in CX specification. Please consider recreate this network in the latest version of CyNDEx2 and Cytoscape.");		
			this.foundNodeRepresentAttr = true;
		}
		nodeIdTracker.addReferenceId(e.getPropertyOf(), NodeAttributesElement.ASPECT_NAME);
		writeCXElement(e);
		attributeStats.addNodeAttribute(e);
	}
	
	private void addEdgeAttribute(EdgeAttributesElement e) throws IOException, NdexException{
		if ( e.getName().equals("interaction")) {
			if ( !this.foundEdgeInteractionAttr) {
				addWarning ( "Attribute 'interaction' on id " + e.getPropertyOf() + 
						" is not allowed in CX specification. Please consider recreate this network in the latest version of CyNDEx2 and Cytoscape.");
				this.foundEdgeInteractionAttr = true;
			}
		}	
		edgeIdTracker.addReferenceId(e.getPropertyOf(), EdgeAttributesElement.ASPECT_NAME);
		writeCXElement(e);
		attributeStats.addEdgeAttribute(e);

	}

	private void addCartesianLayoutElement(CartesianLayoutElement e) throws IOException{
		nodeIdTracker.addReferenceId(e.getNode(), CartesianLayoutElement.ASPECT_NAME);
		writeCXElement(e);
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
		
	}


	
	
	
}
