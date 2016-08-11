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
import java.net.URI;
import java.net.URISyntaxException;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collection;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.UUID;
import java.util.concurrent.ExecutionException;

import org.apache.solr.client.solrj.SolrServerException;
import org.apache.solr.client.solrj.impl.HttpSolrClient;
import org.cxio.aspects.datamodels.EdgeAttributesElement;
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
import org.cxio.misc.OpaqueElement;
import org.cxio.core.interfaces.AspectElement;
import org.cxio.core.interfaces.AspectFragmentReader;
import org.cxio.metadata.MetaDataCollection;
import org.cxio.metadata.MetaDataElement;
import org.cxio.util.CxioUtil;
import org.ndexbio.common.NdexClasses;
import org.ndexbio.common.access.NdexDatabase;
import org.ndexbio.common.cx.aspect.GeneralAspectFragmentReader;
import org.ndexbio.common.models.dao.postgresql.Helper;
import org.ndexbio.common.models.dao.postgresql.UserDAO;
import org.ndexbio.common.solr.NetworkGlobalIndexManager;
import org.ndexbio.common.solr.SingleNetworkSolrIdxManager;
import org.ndexbio.common.util.NdexUUIDFactory;
import org.ndexbio.common.util.TermUtilities;
import org.ndexbio.model.cx.CXSimpleAttribute;
import org.ndexbio.model.cx.CitationElement;
import org.ndexbio.model.cx.EdgeCitationLinksElement;
import org.ndexbio.model.cx.EdgeSupportLinksElement;
import org.ndexbio.model.cx.FunctionTermElement;
import org.ndexbio.model.cx.NamespacesElement;
import org.ndexbio.model.cx.NdexNetworkStatus;
import org.ndexbio.model.cx.NodeCitationLinksElement;
import org.ndexbio.model.cx.NodeSupportLinksElement;
import org.ndexbio.model.cx.Provenance;
import org.ndexbio.model.cx.ReifiedEdgeElement;
import org.ndexbio.model.cx.SupportElement;
import org.ndexbio.model.exceptions.DuplicateObjectException;
import org.ndexbio.model.exceptions.NdexException;
import org.ndexbio.model.exceptions.ObjectNotFoundException;
import org.ndexbio.model.object.NdexPropertyValuePair;
import org.ndexbio.model.object.ProvenanceEntity;
import org.ndexbio.model.object.Task;
import org.ndexbio.model.object.TaskType;
import org.ndexbio.model.object.network.NetworkSourceFormat;
import org.ndexbio.model.object.network.VisibilityType;
import org.ndexbio.rest.Configuration;
import org.ndexbio.task.NdexServerQueue;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;


public class CXNetworkLoader  {
	
    protected static Logger logger = LoggerFactory.getLogger(CXNetworkLoader.class);

	//private static final String nodeName = "name";
	
	private long counter;
	
	private InputStream inputStream;
	private NdexDatabase ndexdb;
	private UUID ownerUUID;

    
    //mapping tables mapping from element SID to internal ID. 
	private Map<Long, Long> nodeSIDMap;
	private Map<Long, Long> edgeSIDMap;
	private Map<Long, Long> citationSIDMap;
	private Map<Long, Long> supportSIDMap;
	private Map<String, Long> namespaceMap;   // prefix to nsID mapping.
	private Map<String, Long> baseTermMap;    // map a baseterm string to bastermId;
	
	// tables to track undefined Elements. Stores element SIDs
	private Set<Long> undefinedNodeId;
	private Set<Long> undefinedEdgeId;
	private Set<Long> undefinedCitationId;
	private Set<Long> undefinedSupportId;
	
	private Provenance provenanceHistory;
		
	long opaqueCounter ;

	long serverElementLimit; 
	
	private UUID uuid;
	
	private Map<String, String> opaqueAspectEdgeTable;
		
	public CXNetworkLoader(InputStream iStream,UUID ownerUUID)  throws NdexException {
		super();
		this.inputStream = iStream;
		
		ndexdb = NdexDatabase.getInstance();
		
		ownerUUID = ownerUUID;
	
				
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
		
	}
	
	private void init () {
		opaqueCounter = 0;
		counter =0; 
		
		nodeSIDMap = new TreeMap<>();
		edgeSIDMap = new TreeMap<> ();
		citationSIDMap = new TreeMap<> ();
		supportSIDMap = new TreeMap<> ();
		this.namespaceMap = new TreeMap<>();
		this.baseTermMap = new TreeMap<>();
		
		undefinedNodeId = new TreeSet<>();
		undefinedEdgeId = new TreeSet<>();
		undefinedSupportId = new TreeSet<>();
		undefinedCitationId = new TreeSet<>();

		opaqueAspectEdgeTable = new HashMap<>();
		
		provenanceHistory = null;
		
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
		  readers.add(new GeneralAspectFragmentReader (FunctionTermElement.ASPECT_NAME,FunctionTermElement.class));
		  readers.add(new GeneralAspectFragmentReader (CitationElement.ASPECT_NAME,CitationElement.class));
		  readers.add(new GeneralAspectFragmentReader (SupportElement.ASPECT_NAME,SupportElement.class));
		  readers.add(new GeneralAspectFragmentReader (ReifiedEdgeElement.ASPECT_NAME,ReifiedEdgeElement.class));
		  readers.add(new GeneralAspectFragmentReader (EdgeCitationLinksElement.ASPECT_NAME,EdgeCitationLinksElement.class));
		  readers.add(new GeneralAspectFragmentReader (EdgeSupportLinksElement.ASPECT_NAME,EdgeSupportLinksElement.class));
		  readers.add(new GeneralAspectFragmentReader (NodeCitationLinksElement.ASPECT_NAME,NodeCitationLinksElement.class));
		  readers.add(new GeneralAspectFragmentReader (NodeSupportLinksElement.ASPECT_NAME,NodeSupportLinksElement.class));
		  readers.add(new GeneralAspectFragmentReader (Provenance.ASPECT_NAME,Provenance.class));
		  		  
		  return  CxElementReader.createInstance(inputStream, true,
				   readers);
	}
	
	//TODO: will modify this function to return a CX version of NetworkSummary object.
	public UUID persistCXNetwork() throws IOException, ObjectNotFoundException, NdexException {
		        
	    uuid = NdexUUIDFactory.INSTANCE.createNewNDExUUID();
	    
	    try {
		  persistNetworkData(); 
		
		  // set the admin
		
	/*	  UserDAO userdao = new UserDAO(localConnection);
		  ODocument ownerDoc = userdao.getRecordByAccountName(ownerAcctName, null) ;
		  OrientVertex ownerV = graph.getVertex(ownerDoc);
		  
		  for	(int retry = 0;	retry <	NdexDatabase.maxRetries;	++retry)	{
				try	{
					ownerV.reload();
					ownerV.addEdge(NdexClasses.E_admin, this.networkVertex);
					break;
				} catch(ONeedRetryException	e)	{
					logger.warn("Retry - " + e.getMessage());
					
				}
			}		
		graph.commit(); */
	//	createSolrIndex(networkDoc);
		return uuid;
		
		} catch (Exception e) {
			// delete network and close the database connection
			e.printStackTrace();
	//		this.abortTransaction();
			throw new NdexException("Error occurred when loading CX stream. " + e.getMessage());
		} 
       
	}

	private void persistNetworkData()
			throws IOException, DuplicateObjectException, NdexException, ObjectNotFoundException {
		
	/*	init();
		
		networkDoc = this.createNetworkHeadNode();
		networkVertex = graph.getVertex(networkDoc);
		

		  CxElementReader cxreader = createCXReader();
		  
		  MetaDataCollection metadata = cxreader.getPreMetaData();
		
		  for ( AspectElement elmt : cxreader ) {
			//String aspectName = ;
			switch ( elmt.getAspectName() ) {
				case NodesElement.ASPECT_NAME :       //Node
					createCXNode((NodesElement) elmt);
					break;
				case NdexNetworkStatus.ASPECT_NAME:   //ndexStatus
				//	netStatus = (NdexNetworkStatus) elmt;
				//	saveNetworkStatus(netStatus);
					break; 
				case EdgesElement.ASPECT_NAME:       // Edge
					EdgesElement ee = (EdgesElement) elmt;
					createCXEdge(ee);
					break;
				case NamespacesElement.ASPECT_NAME:    // namespace
					createCXContext((NamespacesElement) elmt);
					break;
				case NodeAttributesElement.ASPECT_NAME:  // node attributes
					addNodeAttribute((NodeAttributesElement) elmt );
					break;
				case FunctionTermElement.ASPECT_NAME:   // function term
					createFunctionTerm((FunctionTermElement) elmt);
					break;
				case NetworkAttributesElement.ASPECT_NAME: //network attributes
					createNetworkAttribute(( NetworkAttributesElement) elmt);
					break;
				case EdgeAttributesElement.ASPECT_NAME:     // edge attibutes
					addEdgeAttribute((EdgeAttributesElement) elmt );
					break;
				case ReifiedEdgeElement.ASPECT_NAME:   // reified edge
					createReifiedEdgeTerm((ReifiedEdgeElement) elmt);
					break;
				case CitationElement.ASPECT_NAME:     // citation
					createCitation((CitationElement) elmt);
					break;
				case SupportElement.ASPECT_NAME:      //
					createSupport((SupportElement) elmt);
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
				case Provenance.ASPECT_NAME:
					if ( provenanceHistory !=null)
						throw new NdexException ("More than one provenanceHistory aspect element found in the CX stream.");
					provenanceHistory = (Provenance) elmt;
					break;
				default:    // opaque aspect
					addOpaqueAspectElement((OpaqueElement) elmt);
			}

		}
		  // check data integrity.
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
	
	private void addOpaqueAspectElement(OpaqueElement elmt) throws IOException, NdexException {
		
		String aspectName = elmt.getAspectName();

		String edgeName = this.opaqueAspectEdgeTable.get(aspectName);
		if ( edgeName == null) {
			edgeName = NdexClasses.Network_E_opaque_asp_prefix + this.opaqueCounter;
			opaqueCounter ++;
			opaqueAspectEdgeTable.put(aspectName, edgeName);
		}
/*		ODocument doc = new ODocument (NdexClasses.OpaqueElement).
				field(edgeName, elmt.toJsonString() ).save();
		networkVertex.addEdge(edgeName, graph.getVertex(doc)); */
		//tick();
	}


	private void createEdgeSupport(EdgeSupportLinksElement elmt) throws ObjectNotFoundException, DuplicateObjectException {
		for ( Long sourceId : elmt.getSourceIds()) {
/*		   ODocument edgeDoc = getOrCreateEdgeDocBySID(sourceId);
		   Set<Long> supportIds = edgeDoc.field(NdexClasses.Support);
		
		  if(supportIds == null)
			  supportIds = new HashSet<>(elmt.getSupportIds().size());
		
		  for ( Long supportSID : elmt.getSupportIds()) {
			Long supportId = supportSIDMap.get(supportSID);
			if ( supportId == null) {
				supportId = createSupportBySID(supportSID);
			}
			supportIds.add(supportId);
		  }
		
		  edgeDoc.field(NdexClasses.Support, supportIds).save(); */
		}
	}
/*
	private void createNodeSupport(NodeSupportLinksElement elmt) throws ObjectNotFoundException, DuplicateObjectException {
	  for (Long sourceId : elmt.getSourceIds())	 {
		ODocument nodeDoc = getOrCreateNodeDocBySID(sourceId);
		
		Set<Long> supportIds = nodeDoc.field(NdexClasses.Support);
		
		if(supportIds == null)
			supportIds = new HashSet<>(elmt.getSupportIds().size());
		
		for ( Long supportSID : elmt.getSupportIds()) {
			Long supportId = supportSIDMap.get(supportSID);
			if ( supportId == null) {
				supportId = createSupportBySID(supportSID);
			}
			supportIds.add(supportId);
		}
		
		nodeDoc.field(NdexClasses.Support, supportIds).save();
	  }	
	}
	
	private void createEdgeCitation(EdgeCitationLinksElement elmt) throws DuplicateObjectException, ObjectNotFoundException {
	  for ( Long sourceId : elmt.getSourceIds())	 {
		
		ODocument edgeDoc = getOrCreateEdgeDocBySID(sourceId);
		Set<Long> citationIds = edgeDoc.field(NdexClasses.Citation);
		
		if(citationIds == null)
			citationIds = new HashSet<>(elmt.getCitationIds().size());
		
		for ( Long citationSID : elmt.getCitationIds()) {
			Long citationId = citationSIDMap.get(citationSID);
			if ( citationId == null) {
				citationId = createCitationBySID(citationSID);
			}
			citationIds.add(citationId);
		}
		
		edgeDoc.field(NdexClasses.Citation, citationIds).save();
	  }
	}

	
	private void createNodeCitation(NodeCitationLinksElement elmt) throws DuplicateObjectException, ObjectNotFoundException {
	  for ( Long sourceId : elmt.getSourceIds())	{
		ODocument nodeDoc = getOrCreateNodeDocBySID(sourceId);
		
		Set<Long> citationIds = nodeDoc.field(NdexClasses.Citation);
		
		if(citationIds == null)
			citationIds = new HashSet<>(elmt.getCitationIds().size());
		
		for ( Long citationSID : elmt.getCitationIds()) {
			Long citationId = citationSIDMap.get(citationSID);
			if ( citationId == null) {
				citationId = createCitationBySID(citationSID);
			}
			citationIds.add(citationId);
		}
		
		nodeDoc.field(NdexClasses.Citation, citationIds).save();
	  }	
	}
	
	private Long createSupportBySID(Long sid) {
		Long supportId =ndexdb.getNextId(localConnection) ;

		new ODocument(NdexClasses.Support)
		   .fields(NdexClasses.Element_ID, supportId,
				   NdexClasses.Element_SID, sid).save()	;

		this.supportSIDMap.put(sid, supportId);
		undefinedSupportId.add(sid);
		return supportId;
	}
	
	
	private Long createSupport(SupportElement elmt) throws NdexException {
		Long supportId = supportSIDMap.get(elmt.getId()) ;
		
		ODocument supportDoc;
		
		if ( supportId == null ) {
			supportId = ndexdb.getNextId(localConnection) ;
			supportDoc = new ODocument(NdexClasses.Support)
					.fields(NdexClasses.Element_ID, supportId,
							NdexClasses.Element_SID, elmt.getId(),
							NdexClasses.Support_P_text, elmt.getText())	;
			this.supportSIDMap.put(elmt.getId(), supportId);
		} else {
			supportDoc = getSupportDocById(supportId);
			supportDoc.fields(NdexClasses.Support_P_text, elmt.getText());
		}
		
		Long citationSID = elmt.getCitationId();
		if ( citationSID !=null ) {
			Long citationId = citationSIDMap.get(citationSID);
			if (citationId == null) {
				citationId = createCitationBySID(citationSID);
			}
		
			supportDoc.field(NdexClasses.Citation, citationId);
		}
		
		//TODO: this will be removed after we modify the xbel loader to remove properties on Support.
		if(elmt.getProps()!=null && elmt.getProps().size()>0) {
			Collection<NdexPropertyValuePair> properties = new ArrayList<>(elmt.getProps().size());
			for ( CXSimpleAttribute s : elmt.getProps())	{	
				properties.add(new NdexPropertyValuePair(s));
			}
			supportDoc.field(NdexClasses.ndexProperties, properties);
		}

		supportDoc.save();

		OrientVertex supportV = graph.getVertex(supportDoc);

		networkVertex.addEdge(NdexClasses.Network_E_Supports, supportV);
		this.undefinedSupportId.remove(elmt.getId());
		tick();
		return supportId;
	}
	
	private void createReifiedEdgeTerm(ReifiedEdgeElement e) 
						throws NdexException {		
		 Long edgeSID = e.getEdge();
		 ODocument edgeDoc = getOrCreateEdgeDocBySID(edgeSID); 
		 
		 Long termId = ndexdb.getNextId(localConnection);
		 ODocument reifiedEdgeTermDoc = new ODocument(NdexClasses.ReifiedEdgeTerm)
				 	.fields(NdexClasses.Element_ID, termId).save();
				 			
		 OrientVertex retV = graph.getVertex(reifiedEdgeTermDoc);
		 retV.addEdge(NdexClasses.ReifiedEdge_E_edge, graph.getVertex(edgeDoc));
		 
		 Long nodeSID = e.getNode();
		 ODocument nodeDoc = getOrCreateNodeDocBySID(nodeSID);
		 
		 nodeDoc.fields(NdexClasses.Node_P_represents, termId,
				    NdexClasses.Node_P_representTermType, NdexClasses.ReifiedEdgeTerm)
		   .save();
		 
		networkVertex.addEdge(NdexClasses.Network_E_ReifiedEdgeTerms, retV);
		tick();
	}

	private ODocument getOrCreateEdgeDocBySID(Long edgeSID) throws DuplicateObjectException, ObjectNotFoundException {
		Long edgeId = edgeSIDMap.get(edgeSID);
		 if (edgeId == null ) {
				edgeId = ndexdb.getNextId(localConnection);
				
				ODocument nodeDoc =
					new ODocument(NdexClasses.Edge)
					   .fields(NdexClasses.Element_ID, edgeId,
							   NdexClasses.Element_SID, edgeSID);
			    edgeSIDMap.put(edgeSID, edgeId);
				undefinedEdgeId.add(edgeSID);
				return nodeDoc;
		 }
		return this.getEdgeDocById(edgeId);
	}

	private ODocument getOrCreateNodeDocBySID(Long nodeSID) throws ObjectNotFoundException {
		Long nodeId = nodeSIDMap.get(nodeSID);
		if(nodeId == null) {
			nodeId = ndexdb.getNextId(localConnection);
			
		    ODocument nodeDoc = new ODocument(NdexClasses.Node)
			   .fields(NdexClasses.Element_ID, nodeId,
					   NdexClasses.Element_SID, nodeSID);
			nodeSIDMap.put(nodeSID, nodeId);
			undefinedNodeId.add(nodeSID);		   
			return nodeDoc;
		}
		return getNodeDocById(nodeId);
	}
	
	private void createNetworkAttribute(NetworkAttributesElement e) throws NdexException, JsonProcessingException {
		if ( e.getName().equals(NdexClasses.Network_P_name) && e.getSubnetwork() == null) {
			networkDoc.field(NdexClasses.Network_P_name,
					  e.getValue()).save();
		} else if ( e.getName().equals(NdexClasses.Network_P_desc)) {
			networkDoc.field(NdexClasses.Network_P_desc, e.getValue()).save();
		} else if ( e.getName().equals(NdexClasses.Network_P_version)) {
			networkDoc.field(NdexClasses.Network_P_version, e.getValue()).save();
		} else if ( e.getName().equals(SingleNetworkDAO.CXsrcFormatAttrName)) {
			try {
				NetworkSourceFormat.valueOf(e.getValue());
			} catch (IllegalArgumentException ex) {
				throw new NdexException("Unsupported source format " + 
						e.getValue() + 
						" received in network attribute " + 
						SingleNetworkDAO.CXsrcFormatAttrName);
			}
			networkDoc.field(NdexClasses.Network_P_source_format, e.getValue()).save();
		} else {
			
			NdexPropertyValuePair newProps= new NdexPropertyValuePair(e.getSubnetwork(),
					 e.getName(),
					 (e.isSingleValue() ? e.getValue(): CxioUtil.getAttributeValuesAsString(e))
					 , e.getDataType().toString());
			List<NdexPropertyValuePair> props =networkDoc.field(NdexClasses.ndexProperties);
			if ( props == null) {
				props = new ArrayList<>(1);
			} 
			
			props.add(newProps);
			networkDoc.field(NdexClasses.ndexProperties,props).save();
		}
	}
	
	private void createCXContext(NamespacesElement context) throws NdexException {
		for ( Map.Entry<String, String> e : context.entrySet()) {
			Long nsId = ndexdb.getNextId(localConnection);

			ODocument nsDoc = new ODocument(NdexClasses.Namespace);
			nsDoc = nsDoc.fields(NdexClasses.Element_ID,nsId,
								 NdexClasses.ns_P_prefix, e.getKey(),
			                     NdexClasses.ns_P_uri, e.getValue())
			  .save();
			
	        
			OrientVertex nsV = graph.getVertex(nsDoc);
			networkVertex.addEdge(NdexClasses.Network_E_Namespace, nsV);
			Long oldv = this.namespaceMap.put(e.getKey(), nsId);
			if ( oldv !=null)
				throw new DuplicateObjectException("Duplicate @context prefix " + e.getKey());			
			   tick();
		}
		
	}
	
	private ODocument createNetworkHeadNode( ) {
	
		ODocument doc = new ODocument (NdexClasses.Network)
				  .fields(NdexClasses.Network_P_UUID,uuid,
						  NdexClasses.Network_P_name, "",
						  NdexClasses.Network_P_desc, "",
						  NdexClasses.Network_P_owner, ownerAcctName,
				          NdexClasses.ExternalObj_cTime, new Timestamp(Calendar.getInstance().getTimeInMillis()),
				          NdexClasses.ExternalObj_isDeleted, false,
				          NdexClasses.Network_P_isLocked, false,
				          NdexClasses.Network_P_isComplete, false,
				          NdexClasses.Network_P_cacheId, Long.valueOf(-1),
				          NdexClasses.Network_P_readOnlyCommitId, Long.valueOf(-1),
				          NdexClasses.Network_P_visibility,
				        		  VisibilityType.PRIVATE.toString() );
		return doc.save();
	}

	private Long createCXNode(NodesElement node) throws NdexException {
		Long nodeId = nodeSIDMap.get(node.getId());
		ODocument nodeDoc;
		
		if ( nodeId !=null) {
			if ( !undefinedNodeId.remove(node.getId()))  // it has been defined more than once
			   throw new DuplicateObjectException(NodesElement.ASPECT_NAME, node.getId());
			nodeDoc = this.getNodeDocById(nodeId);
		} else { 
			nodeId = ndexdb.getNextId(localConnection);
			nodeDoc = new ODocument(NdexClasses.Node)
					   .fields(NdexClasses.Element_ID, nodeId,
							   NdexClasses.Element_SID, node.getId());
			nodeSIDMap.put(node.getId(), nodeId);
		}			
		
		if ( node.getNodeName()!=null)
			   nodeDoc.field(NdexClasses.Node_P_name,node.getNodeName());

		if ( node.getNodeRepresents() !=null) {
			   Long btId = getBaseTermId ( node.getNodeRepresents());
			   nodeDoc.fields(NdexClasses.Node_P_represents, btId,
					          NdexClasses.Node_P_representTermType,NdexClasses.BaseTerm).save();
		}
		
		nodeDoc.save();
		networkVertex.addEdge(NdexClasses.Network_E_Nodes,graph.getVertex(nodeDoc));
		tick();
		return nodeId;
	}	
	
	private Long createCXEdge(EdgesElement ee) throws NdexException {
		
		String relation = ee.getInteraction();
	
		Long btId = null;
		if ( relation != null && relation.length() >0 )
			 btId = getBaseTermId(relation);

		Long edgeId = edgeSIDMap.get(ee.getId());
		ODocument edgeDoc; 

		if ( edgeId == null ) { 
		  edgeId = ndexdb.getNextId(localConnection);

	      edgeDoc = new ODocument(NdexClasses.Edge)
		   .fields(NdexClasses.Element_ID, edgeId,
				   NdexClasses.Element_SID, ee.getId());
		   edgeSIDMap.put(ee.getId(), edgeId);
		} else {
			if ( ! undefinedEdgeId.remove(ee.getId())) 
				throw new NdexException ("Duplicate Edge found in CX stream. @id=" + ee.getId());
			edgeDoc = this.getEdgeDocById(edgeId);
		}
		
	    if ( btId !=null)
				edgeDoc.field(NdexClasses.Edge_P_predicateId, btId );
	      
		edgeDoc.save();
		
		OrientVertex edgeV = graph.getVertex(edgeDoc);
		
		ODocument subjectDoc = this.getOrCreateNodeDocBySID(ee.getSource());
	           
	   graph.getVertex(subjectDoc).addEdge(NdexClasses.Edge_E_subject, edgeV);
		
	   ODocument objectDoc = getOrCreateNodeDocBySID(ee.getTarget());
       
       edgeV.addEdge(NdexClasses.Edge_E_object, graph.getVertex(objectDoc)); 
	   
	   networkVertex.addEdge(NdexClasses.Network_E_Edges,edgeV);
	   tick();
	   
	   return edgeId;
	}
	
	private Long createCitationBySID(Long sid) throws DuplicateObjectException {
		Long citationId = ndexdb.getNextId(localConnection);
		
	//	ODocument citationDoc = 
		new ODocument(NdexClasses.Citation)
		   .fields(NdexClasses.Element_ID, citationId,
				   NdexClasses.Element_SID, sid)
		   .save();
		
		Long oldId = citationSIDMap.put(sid, citationId);
		if ( oldId !=null)
			throw new DuplicateObjectException(CitationElement.ASPECT_NAME, sid);
		
		undefinedCitationId.add(sid); 
		return citationId;
	}
	
	private Long createCitation(CitationElement c) throws NdexException {

		//TODO: add description to citation.
		
		Long citationId = citationSIDMap.get(c.getId());
		ODocument citationDoc ;
		if ( citationId == null) {
			citationId = ndexdb.getNextId(localConnection);
			citationDoc = new ODocument(NdexClasses.Citation)
					  .fields(
							NdexClasses.Element_ID, citationId,
							NdexClasses.Element_SID, c.getId());
			citationSIDMap.put(c.getId(), citationId);
		} else {
			citationDoc = this.getCitationDocById(citationId);
		}
		
		citationDoc.fields(NdexClasses.Citation_P_title, c.getTitle(),
				        NdexClasses.Citation_p_idType, c.getCitationType(),
				        NdexClasses.Citation_P_identifier, c.getIdentifier())
				   .field( NdexClasses.Citation_P_contributors,c.getContributor(), OType.EMBEDDEDLIST);			   		
		
		//TODO: remove this after we modify the xbel parser.
		if(c.getProps()!=null && c.getProps().size()>0) {
			Collection<NdexPropertyValuePair> properties = new ArrayList<>(c.getProps().size());
			for ( CXSimpleAttribute s : c.getProps())	{	
				properties.add(new NdexPropertyValuePair(s));
			}
			citationDoc.field(NdexClasses.ndexProperties, properties);
		}
		
		citationDoc.save();
		        
		OrientVertex citationV = graph.getVertex(citationDoc);
		networkVertex.addEdge(NdexClasses.Network_E_Citations, citationV);
		
		undefinedCitationId.remove(c.getId());
		tick();
		return citationId;
	}
	
	private Long createFunctionTerm(FunctionTermElement func) throws NdexException  {
		Long funcId = ndexdb.getNextId(localConnection);
		
		Long baseTermId = getBaseTermId(func.getFunctionName());
		
		ODocument funcDoc = new ODocument(NdexClasses.FunctionTerm)
				.fields(NdexClasses.Element_ID, funcId,
						NdexClasses.BaseTerm, baseTermId).save();
		
		OrientVertex functionTermV = graph.getVertex(funcDoc);
					 
		for ( Object arg : func.getArgs()) {
			ODocument argumentDoc ;
			
			if ( arg instanceof String) {
				Long bId = getBaseTermId ((String)arg);
				argumentDoc = this.getBasetermDocById(bId);
			} else if ( arg instanceof FunctionTermElement ) {
				Long fId = createFunctionTerm((FunctionTermElement) arg);
				argumentDoc = this.getFunctionDocById(fId);
			} else
				throw new NdexException("Invalid function term argument type " + arg.getClass().getName() + " found." );
		    functionTermV.addEdge(NdexClasses.FunctionTerm_E_paramter, graph.getVertex(argumentDoc));
		}
			
		Long nodeSID = func.getNodeID() ;
		if ( nodeSID != null) {
			ODocument nodeDoc = getOrCreateNodeDocBySID(nodeSID);
			nodeDoc.fields(NdexClasses.Node_P_represents, funcId,
					NdexClasses.Node_P_representTermType,NdexClasses.FunctionTerm).save();
		}
		
		networkVertex.addEdge(NdexClasses.Network_E_FunctionTerms, functionTermV);
		tick();
		return funcId;
	}
	
	
	
	private Long createBaseTerm(String termString) throws NdexException {
		
		// case 1 : termString is a URI
		// example: http://identifiers.org/uniprot/P19838
		// treat the last element in the URI as the identifier and the rest as
		// prefix string. Just to help the future indexing.
		//
		String prefix = null;
		String identifier = null;
		if ( termString.length() > 8 && termString.substring(0, 7).equalsIgnoreCase("http://") &&
				(!termString.endsWith("/"))) {
  		  try {
			URI termStringURI = new URI(termString);
				identifier = termStringURI.getFragment();
			
			    if ( identifier == null ) {
				    String path = termStringURI.getPath();
				    if (path != null && path.indexOf("/") != -1) {
				       int pos = termString.lastIndexOf('/');
					   identifier = termString.substring(pos + 1);
					   prefix = termString.substring(0, pos + 1);
				    } else
				       throw new NdexException ("Unsupported URI format in term: " + termString);
			    } else {
				    prefix = termStringURI.getScheme()+":"+termStringURI.getSchemeSpecificPart()+"#";
			    }
                 
			    Long btId = createBaseTerm(prefix,identifier, null);
			    baseTermMap.put(termString, btId);
				tick();
			    return btId;
			  
		  } catch (URISyntaxException e) {
			// ignore and move on to next case
		  }
		}
		
		Long btId = null;
		String[] termStringComponents = TermUtilities.getNdexQName(termString);
		if (termStringComponents != null && termStringComponents.length == 2) {
			// case 2: termString is of the form (NamespacePrefix:)*Identifier
			identifier = termStringComponents[1];
			prefix = termStringComponents[0];
			Long nsId = namespaceMap.get(prefix);

			if ( nsId !=null) {
			  btId = createBaseTerm(null, identifier, nsId);
			} else 
				btId = createBaseTerm(prefix + ":",identifier, null);
			baseTermMap.put(termString, btId);
			tick();
			return btId;
		} 
		
			// case 3: termString cannot be parsed, use it as the identifier.
			// so leave the prefix as null and create the baseterm
			identifier = termString;
	
		
		// create baseTerm in db
		Long id= createBaseTerm(null,identifier,null);
        this.baseTermMap.put(termString, id);
		   tick();
        return id;

	}
	
	private Long createBaseTerm(String prefix, String identifier, Long nsId) {
		Long termId = ndexdb.getNextId(localConnection);
		
		ODocument btDoc = new ODocument(NdexClasses.BaseTerm)
		  .fields(NdexClasses.BTerm_P_name, identifier,
				  NdexClasses.Element_ID, termId,
		  		  NdexClasses.BTerm_P_prefix, prefix); 
		
		if ( nsId !=null)
			  btDoc.field(NdexClasses.BTerm_NS_ID, nsId);
 
		btDoc.save();
		OrientVertex basetermV = graph.getVertex(btDoc);
	//	networkVertex.getRecord().reload();
        networkVertex.addEdge(NdexClasses.Network_E_BaseTerms, basetermV);
		return termId;
	}

	private void addEdgeAttribute(EdgeAttributesElement e) throws NdexException, JsonProcessingException{
		for ( Long edgeSID : e.getPropertyOf()) {
		
		   ODocument edgeDoc = getOrCreateEdgeDocBySID(edgeSID); 

		   NdexPropertyValuePair newProps= new NdexPropertyValuePair(e.getSubnetwork(),
					 e.getName(),
					 (e.isSingleValue() ? e.getValue(): CxioUtil.getAttributeValuesAsString(e)),
					 e.getDataType().toString());
		   List<NdexPropertyValuePair> props =edgeDoc.field(NdexClasses.ndexProperties);
			if ( props == null)
				props = new ArrayList<>(1);
			 
			props.add(newProps);
			edgeDoc.field(NdexClasses.ndexProperties,props).save();

			tick();
		}
	}
	
	
	private void addNodeAttribute(NodeAttributesElement e) throws NdexException, JsonProcessingException{
		for ( Long nodeSID : e.getPropertyOf()) {
			ODocument nodeDoc = getOrCreateNodeDocBySID(nodeSID);
		   
		   String propName = e.getName();

		   if ( propName.equals(NdexClasses.Node_P_alias)) {       // aliases
			   if (!e.getValues().isEmpty()) {
				   Set<Long> aliases = new TreeSet<>();
				   for ( String v : e.getValues()) {
					   aliases.add(getBaseTermId(v));
				   }
				   
				   nodeDoc.field(NdexClasses.Node_P_alias, aliases).save();
			   } 
		   } else if ( propName.equals(NdexClasses.Node_P_relatedTo)) {       // relateTo
			   if (!e.getValues().isEmpty()) {
				   Set<Long> relateTo = new TreeSet<>();
				   for ( String v : e.getValues()) {
					   relateTo.add(getBaseTermId(v));
				   }
				   
				   nodeDoc.field(NdexClasses.Node_P_relatedTo, relateTo).save();
			   } 
		   }  else {
			   NdexPropertyValuePair newProps= new NdexPropertyValuePair(e.getSubnetwork(),
						 e.getName(),
						 (e.isSingleValue() ? e.getValue(): CxioUtil.getAttributeValuesAsString(e)),
						 e.getDataType().toString());
			   List<NdexPropertyValuePair> props =nodeDoc.field(NdexClasses.ndexProperties);
				if ( props == null)
					props = new ArrayList<>(1);
					
				props.add(newProps);
				nodeDoc.field(NdexClasses.ndexProperties,props).save();
		   }
		   
		   tick();
		}
	}

	/**
	 * Get the id of the base term if it was already created. Othewise creates it and return its id.
	 * @param termString
	 * @return
	 * @throws NdexException
	 */
/*	private Long getBaseTermId(String termString) throws NdexException {
		Long btId = baseTermMap.get(termString);
		if ( btId == null) {
			btId = createBaseTerm(termString);
		}
		return btId;
	}

    
	
	@Override
	public void close() throws Exception {
		graph.shutdown();
	}
	
	private void tick() throws NdexException {
		counter ++;
		if ( serverElementLimit>=0 && counter >serverElementLimit ) 
			throw new NdexException("Element count in the CX input stream exceeded server limit " + serverElementLimit);
	//	if ( counter % 5000 == 0 )  graph.commit();
		if ( counter %10000 == 0 )
			System.out.println("Loaded " + counter + " element in CX");
		
	} 
	
	
	private void abortTransaction() throws ObjectNotFoundException, NdexException {
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

   // public void commit () { graph.commit(); } 

}
