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
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.UUID;

import org.apache.solr.client.solrj.SolrServerException;
import org.ndexbio.common.cx.CX2NetworkFileGenerator;
import org.ndexbio.common.models.dao.postgresql.NetworkDAO;
import org.ndexbio.common.solr.SingleNetworkSolrIdxManager;
import org.ndexbio.cx2.aspect.element.core.AttributeDeclaredAspect;
import org.ndexbio.cx2.aspect.element.core.CxAspectElement;
import org.ndexbio.cx2.aspect.element.core.CxAttributeDeclaration;
import org.ndexbio.cx2.aspect.element.core.CxEdge;
import org.ndexbio.cx2.aspect.element.core.CxEdgeBypass;
import org.ndexbio.cx2.aspect.element.core.CxMetadata;
import org.ndexbio.cx2.aspect.element.core.CxNetworkAttribute;
import org.ndexbio.cx2.aspect.element.core.CxNode;
import org.ndexbio.cx2.aspect.element.core.CxNodeBypass;
import org.ndexbio.cx2.aspect.element.core.CxOpaqueAspectElement;
import org.ndexbio.cx2.aspect.element.core.CxVisualProperty;
import org.ndexbio.cx2.aspect.element.core.DeclarationEntry;
import org.ndexbio.cx2.io.CX2AspectWriter;
import org.ndexbio.cx2.io.CXReader;
import org.ndexbio.cxio.aspects.datamodels.EdgesElement;
import org.ndexbio.cxio.aspects.datamodels.NodesElement;
import org.ndexbio.cxio.aspects.datamodels.SubNetworkElement;
import org.ndexbio.cxio.metadata.MetaDataCollection;
import org.ndexbio.model.exceptions.DuplicateObjectException;
import org.ndexbio.model.exceptions.NdexException;
import org.ndexbio.model.exceptions.ObjectNotFoundException;
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

public class CX2NetworkLoader implements AutoCloseable {
	
    protected static Logger logger = LoggerFactory.getLogger(CXNetworkLoader.class);
	static public final String cx2NetworkFileName = "network.cx2";
	
	//Directory name of CX2 aspects
	static public final String cx2AspectDirName = "aspects_cx2";

    static public final String cx2Format = "cx2";
    
	protected int sampleGenerationThreshold;
	
    private boolean isUpdate;
		
	private UUID networkId;

    private String rootPath;
	
    //mapping tables mapping from element SID to internal ID. 
	private AspectElementIdTracker nodeIdTracker;
	private AspectElementIdTracker edgeIdTracker;
		
	protected Set<Long> subNetworkIds;
		
	private CxAttributeDeclaration attributeDeclarations;
		
	protected CxNetworkAttribute networkAttributes; 
		
	protected Map<String, CxMetadata> metadataTable;
			
	protected Map<String,CX2AspectWriter<? extends CxAspectElement<?>>> aspectTable;
	protected List<String> warnings;
	private NetworkDAO dao;
	private VisibilityType visibility;
	private Set<String> indexedFields;
	private boolean hasLayout;
		
	public CX2NetworkLoader(UUID networkUUID, boolean isUpdate, NetworkDAO networkDao, VisibilityType visibility, Set<String> IndexedFields, int sampleGenerationThreshold) {
		super();
		
		this.isUpdate = isUpdate;
		
		this.networkId = networkUUID;
		this.rootPath = Configuration.getInstance().getNdexRoot() + "/data/" + networkId + "/"+ cx2AspectDirName + "/";
						
		warnings = new ArrayList<> ();
		
		nodeIdTracker = new AspectElementIdTracker(NodesElement.ASPECT_NAME);
		edgeIdTracker = new AspectElementIdTracker(EdgesElement.ASPECT_NAME);
		
		aspectTable = new TreeMap<>();
		this.subNetworkIds = new HashSet<>(10);
			
		networkAttributes = null;
		
		dao = networkDao;
		
		attributeDeclarations = new CxAttributeDeclaration();
		this.hasLayout = false;

		this.visibility = visibility;
		this.indexedFields = IndexedFields;
		if ( sampleGenerationThreshold <= CXNetworkLoader.defaultSampleGenerationThreshhold)
			this.sampleGenerationThreshold = CXNetworkLoader.defaultSampleGenerationThreshhold;
		else
			this.sampleGenerationThreshold = sampleGenerationThreshold;
	}
	
	protected UUID getNetworkId() {return this.networkId;}
	protected NetworkDAO getDAO () {return dao;}
	
	
	public void persistCXNetwork() throws IOException, DuplicateObjectException, ObjectNotFoundException, NdexException, SQLException, SolrServerException {
		        	    
		  java.nio.file.Path dir = Paths.get(rootPath);
		  Files.createDirectory(dir);
		  
		  try (	InputStream inputStream = new FileInputStream(Configuration.getInstance().getNdexRoot() + "/data/" + networkId + "/"+ cx2NetworkFileName) ) {
	
			  persistNetworkData(inputStream, false); 
		  
		  
			  NetworkSummary summary = new NetworkSummary();

				//handle the network properties 
				summary.setExternalId(this.networkId);
				summary.setCxFormat(cx2Format);
				if ( isUpdate) {
					summary.setVisibility(dao.getNetworkVisibility(networkId));
				} else 
					summary.setVisibility(VisibilityType.PRIVATE);
				summary.setEdgeCount(this.edgeIdTracker.getDefinedElementSize());
				summary.setNodeCount(this.nodeIdTracker.getDefinedElementSize());
				
				Timestamp t = dao.getNetworkCreationTime(this.networkId)	;
				summary.setCreationTime(t);
				summary.setModificationTime(t);
				
				if (networkAttributes!=null ) {
					summary.setProperties(networkAttributes.toV1PropertyList(attributeDeclarations.getAttributesInAspect(CxNetworkAttribute.ASPECT_NAME)));
					summary.setName(networkAttributes.getNetworkName());
					summary.setDescription(networkAttributes.getNetworkDescription());
					summary.setVersion(networkAttributes.getNetworkVersion());
				}
				summary.setWarnings(warnings);
				summary.setSubnetworkIds(subNetworkIds);
				try {
					dao.saveCX2NetworkEntry(summary, metadataTable, false);
						
					dao.commit();
				} catch (SQLException e) {
					dao.rollback();	
					throw new NdexException ("DB error when saving network summary: " + e.getMessage(), e);
				}
		  
				// commenting this out because we don't generate samples for CX2
				// create the network sample if the network has more than 500 edges
			/*	boolean sampleCreated = false;
				if (summary.getEdgeCount() > sampleGenerationThreshold)  {
			  
					Long subNetworkId = null;
					if (subNetworkIds.size()>0 )  {
						for ( Long i : subNetworkIds) {
							subNetworkId = i;
							break;
						}
					}
					CXNetworkSampleGenerator g = new CXNetworkSampleGenerator(this.networkId, subNetworkId, metadata, CXNetworkLoader.defaultSampleSize);
					g.createSampleNetwork();
					sampleCreated = true;
			  
				} */
			  				
				//recreate CX and CX2 files
				MetaDataCollection cx1Metadata = reCreateCXFiles();//networkId,dao);
				
				try {
					dao.updateMetadataColleciton(networkId, cx1Metadata);
					if ( warnings.size()>0)
						dao.setWarning(networkId, warnings);
					
					if ( !isUpdate) {
						dao.setFlag(this.networkId, "iscomplete", true);
					} 
					if (visibility != null) {
						dao.updateNetworkVisibility(networkId, visibility, true);
					}
					
					dao.setFlag(this.networkId, "has_sample", false );//sampleCreated);
					dao.setFlag(this.networkId, "has_layout", this.hasLayout);
					dao.unlockNetwork(this.networkId);

				} catch (SQLException e) {
					dao.rollback();
					throw new NdexException ("DB error when setting unlock flag: " + e.getMessage(), e);
				}

				NetworkIndexLevel indexLevel = dao.getIndexLevel(networkId);
				boolean needIndividualIndex = this.nodeIdTracker.getDefinedElementSize() >= SingleNetworkSolrIdxManager.AUTOCREATE_THRESHHOLD;
				
				// clear individual index
				try (SingleNetworkSolrIdxManager idx2 = new SingleNetworkSolrIdxManager(networkId.toString())) {
					idx2.dropIndex();
				}
				
				if ( isUpdate && indexLevel != NetworkIndexLevel.NONE)  {
				   if ( needIndividualIndex)
					  NdexServerQueue.INSTANCE.addSystemTask(new SolrTaskRebuildNetworkIdx(networkId,SolrIndexScope.both,false,indexedFields, indexLevel , true ));
				   else 
				      NdexServerQueue.INSTANCE.addSystemTask(new SolrTaskRebuildNetworkIdx(networkId,SolrIndexScope.global,false,indexedFields, indexLevel, true ));
				} else {
					if (needIndividualIndex)
						NdexServerQueue.INSTANCE.addSystemTask(new SolrTaskRebuildNetworkIdx(networkId,SolrIndexScope.individual,!isUpdate, indexedFields, NetworkIndexLevel.NONE, true));
					else {
						dao.setFlag(this.networkId, "iscomplete", true);
						dao.commit();
					}	
				}
		  }

	}

	// return cx1 metadata created from the cx2 to cx converter.
	private MetaDataCollection reCreateCXFiles( ) throws JsonParseException, JsonMappingException, SQLException, IOException,
			NdexException, FileNotFoundException {
		
		
		CX2NetworkFileGenerator g = new CX2NetworkFileGenerator ( networkId, dao);
		String tmpFileName = g.createCX2File();
		
		String pathPrefix = Configuration.getInstance().getNdexRoot() + "/data/" + networkId + "/";
		
		java.nio.file.Path src = Paths.get(tmpFileName);
		java.nio.file.Path tgt = Paths.get( pathPrefix + cx2NetworkFileName);
		java.nio.file.Path tgt2 = Paths.get( pathPrefix + cx2NetworkFileName + ".arc");
		
		Files.move(tgt, tgt2, StandardCopyOption.ATOMIC_MOVE); 				
		Files.move(src, tgt, StandardCopyOption.ATOMIC_MOVE,StandardCopyOption.REPLACE_EXISTING);  
		
		// TODO: zip the archive and convert to CX file
		CX2ToCXConverter cvtr = new CX2ToCXConverter(pathPrefix, this.attributeDeclarations, 
				this.metadataTable, this.hasLayout, this.networkAttributes, warnings);
		
		return cvtr.convert();
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
				
		CXReader cxreader = new CXReader(in);
		  
		for ( CxAspectElement elmt : cxreader ) {
			switch ( elmt.getAspectName() ) {
				case CxAttributeDeclaration.ASPECT_NAME:
					CxAttributeDeclaration decl = (CxAttributeDeclaration)elmt;
					if ( !decl.getDeclarations().isEmpty())
						attributeDeclarations.addNewDeclarations(decl);
					break;
				case CxNode.ASPECT_NAME :       //Node
					createCXNode((CxNode) elmt);
					break;
				case CxEdge.ASPECT_NAME:       // Edge
					CxEdge ee = (CxEdge) elmt;
					createCXEdge(ee);
					break;
				case CxNetworkAttribute.ASPECT_NAME: //network attributes
					createNetworkAttribute(( CxNetworkAttribute) elmt);
					break;
				case CxVisualProperty.ASPECT_NAME: 
					createCyVisualProperitiesElement((CxVisualProperty) elmt);
					break;
				case CxNodeBypass.ASPECT_NAME: 
					createNodeBypass((CxNodeBypass) elmt );
					break;
				case CxEdgeBypass.ASPECT_NAME:
					createEdgeBypass((CxEdgeBypass) elmt);
					break;
				default:    // opaque aspect
					createAspectElement(elmt);
					if ( elmt.getAspectName().equals(SubNetworkElement.ASPECT_NAME)) {
						CxOpaqueAspectElement e = (CxOpaqueAspectElement) elmt;
						Long subNetID = (Long)e.getElementObject().get("@id");
						subNetworkIds.add(subNetID ) ;
					} 
			}

		} 
		
		writeCXElement(attributeDeclarations);
		
		closeAspectStreams();

		//save the attribute Declaration
		
		//save the metadata
		metadataTable = cxreader.getMetadata();
		
		// add warnings from the status aspect.
		if ( cxreader.getWarning()!=null)
			warnings.add("Message from status aspect: "+cxreader.getWarning());
		
		  
		if(metadataTable !=null) {
			  			  			  
			  Set<String> tobeRemovedMetaData = new HashSet<>();
			  for ( Map.Entry<String, CxMetadata> e: metadataTable.entrySet()) {

				  //check if elementCount is missing in metadata
				  Long cntObj = e.getValue().getElementCount();
				  long actualCnt = cxreader.getAspectElementCount(e.getKey());
				  if ( cntObj == null) {
					  if (actualCnt > 0 )
						  e.getValue().setElementCount(Long.valueOf(actualCnt));
					  else {
						  tobeRemovedMetaData.add(e.getKey());
					  }
				  } else {
				      //check if elementCount matches between metadata and data
					  long declaredCnt = cntObj.longValue();
					  
					  if ( declaredCnt != actualCnt) 
						  throw new NdexException ("Element count mismatch in aspect " + e.getKey() + ". Metadata declared element count " + declaredCnt +
								  ", but " + actualCnt + " was received in CX.");
					  if ( declaredCnt == 0) {
						 tobeRemovedMetaData.add(e.getKey()); 
					  } 
				  }
			  }
			  
			  for (String name : tobeRemovedMetaData) {
				  metadataTable.remove(name);
			  }
			  
			  // check if all the aspects has metadata
			  addMissingMetadata();
			  
			  // check data integrity.
			  String errMsg = metadataTable.get(NodesElement.ASPECT_NAME) !=null? nodeIdTracker.checkUndefinedIds() : null;
			  if ( errMsg !=null) 
				  throw new NdexException(errMsg);
			  
			  errMsg = metadataTable.get(EdgesElement.ASPECT_NAME) !=null? edgeIdTracker.checkUndefinedIds() : null;
			  if ( errMsg !=null )
				  throw new NdexException(errMsg);
			  			  
		  } else {
			  // generate metadata from actual data
			  metadataTable = new HashMap<>();
			  addMissingMetadata();
		  }
		
	}
	
	private void addMissingMetadata() {
		for ( Map.Entry<String,CX2AspectWriter<? extends CxAspectElement<?>>> aw : aspectTable.entrySet() ){
			 String aspectName = aw.getKey();
			 if ( metadataTable.get(aspectName) == null) {
				  CxMetadata mElmt = new CxMetadata();
				  mElmt.setName(aspectName);
				  mElmt.setElementCount(Long.valueOf(aw.getValue().getElementCount()));
				  metadataTable.put(aspectName, mElmt);
			 }	  
		 }
	}

	//No need to expend the attributes because default value and aliases.
	private void createNetworkAttribute(CxNetworkAttribute e) throws IOException, NdexException {
		
		if ( this.networkAttributes == null)
			this.networkAttributes = e;
		else 
			throw new NdexException ("Only one networkAttributes element is allowed in CX.");
		
		if ( !e.getAttributes().isEmpty()) {
			writeCXElement(e);		
			validateElementAttributes(e);
		}
	}
	
	@SuppressWarnings("resource")
	private void writeCXElement(CxAspectElement element) throws IOException {
		String aspectName = element.getAspectName();
		CX2AspectWriter writer = aspectTable.get(aspectName);
		if ( writer == null) {
			//logger.info("creating new file for aspect " + aspectName);
			writer = new CX2AspectWriter<>(rootPath + aspectName);
			aspectTable.put(aspectName, writer);
		}
		writer.writeCXElement(element);
		//writer.flush();
	}
	
	private void validateElementAttributes(AttributeDeclaredAspect e) throws NdexException {
		Map<String,DeclarationEntry> declarations = this.attributeDeclarations.getAttributesInAspect(e.getAspectName());
		e.replaceShortenedName(declarations);
		e.validateAttribute(declarations, false);	
	}
	
	
	private void createCXNode(CxNode node) throws NdexException, IOException {

		node.validate();
		writeCXElement(node);
		
		validateElementAttributes(node);
		
		if ( this.hasLayout) {
			if ( node.getX() == null ) 
				throw new NdexException ("Coordinates missing in node " + node.getId());
		} else {
			if (node.getX() != null) {
				this.hasLayout = true;		
			}
		}
		
		
		nodeIdTracker.addDefinedElementId(node.getId());
	}	 


	private void createCyVisualProperitiesElement(CxVisualProperty visualProperty) throws IOException {
		
		writeCXElement(visualProperty);		   
	}	 
	
	
	private void createNodeBypass(CxNodeBypass e) throws IOException {
		nodeIdTracker.addReferenceId(e.getId(), e.getAspectName());
		writeCXElement(e);
	}

	private void createEdgeBypass(CxEdgeBypass e) throws IOException {
		edgeIdTracker.addReferenceId(Long.valueOf(e.getId()), e.getAspectName());
		writeCXElement(e);
	}
	
	private void createCXEdge(CxEdge ee) throws NdexException, IOException {
		
		writeCXElement(ee);	   
		validateElementAttributes(ee);
		
		edgeIdTracker.addDefinedElementId(ee.getId());
		
		nodeIdTracker.addReferenceId(ee.getSource(), EdgesElement.ASPECT_NAME);
		nodeIdTracker.addReferenceId(ee.getTarget(), EdgesElement.ASPECT_NAME);
		
	}
	
	
	private void createAspectElement(CxAspectElement<?> element) throws IOException {
		writeCXElement(element);
	}
	

	private void closeAspectStreams() {
		for ( Map.Entry<String, CX2AspectWriter<? extends CxAspectElement<?>>> entry : aspectTable.entrySet() ){
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
