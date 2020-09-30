package org.ndexbio.common.persistence;

import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.SortedMap;
import java.util.TreeMap;

import org.ndexbio.cx2.aspect.element.core.CxAttributeDeclaration;
import org.ndexbio.cx2.aspect.element.core.CxEdge;
import org.ndexbio.cx2.aspect.element.core.CxEdgeBypass;
import org.ndexbio.cx2.aspect.element.core.CxMetadata;
import org.ndexbio.cx2.aspect.element.core.CxNetworkAttribute;
import org.ndexbio.cx2.aspect.element.core.CxNode;
import org.ndexbio.cx2.aspect.element.core.CxNodeBypass;
import org.ndexbio.cx2.aspect.element.core.CxOpaqueAspectElement;
import org.ndexbio.cx2.aspect.element.core.CxVisualProperty;
import org.ndexbio.cx2.aspect.element.core.MappingDefinition;
import org.ndexbio.cx2.aspect.element.core.VPMappingType;
import org.ndexbio.cx2.aspect.element.core.VisualPropertyMapping;
import org.ndexbio.cx2.aspect.element.cytoscape.VisualEditorProperties;
import org.ndexbio.cx2.converter.AspectAttributeStat;
import org.ndexbio.cx2.converter.CX2VPHolder;
import org.ndexbio.cx2.converter.CXToCX2Converter;
import org.ndexbio.cx2.converter.CXToCX2VisualPropertyConverter;
import org.ndexbio.cx2.converter.ConverterUtilities;
import org.ndexbio.cx2.converter.MappingValueStringParser;
import org.ndexbio.cx2.io.CX2AspectWriter;
import org.ndexbio.cx2.io.CXWriter;
import org.ndexbio.cxio.aspects.datamodels.ATTRIBUTE_DATA_TYPE;
import org.ndexbio.cxio.aspects.datamodels.CartesianLayoutElement;
import org.ndexbio.cxio.aspects.datamodels.CyVisualPropertiesElement;
import org.ndexbio.cxio.aspects.datamodels.EdgeAttributesElement;
import org.ndexbio.cxio.aspects.datamodels.EdgesElement;
import org.ndexbio.cxio.aspects.datamodels.Mapping;
import org.ndexbio.cxio.aspects.datamodels.NetworkAttributesElement;
import org.ndexbio.cxio.aspects.datamodels.NodeAttributesElement;
import org.ndexbio.cxio.aspects.datamodels.NodesElement;
import org.ndexbio.cxio.core.AspectIterator;
import org.ndexbio.cxio.metadata.MetaDataCollection;
import org.ndexbio.cxio.metadata.MetaDataElement;
import org.ndexbio.model.exceptions.NdexException;

import com.fasterxml.jackson.core.JsonProcessingException;
import java.io.File;
import org.ndexbio.cx2.converter.ConverterUtilitiesResult;

/**
 * Convert a CX2 network on the server to CX1. This converter works on the individual aspects on the 
 * server, not on the full CX2 file.
 * @author jingchen
 *
 */
public class CXToCX2ServerSideConverter {
	
	private String pathPrefix;
	private String networkId;
	private CxAttributeDeclaration attrDeclarations;
	private	MetaDataCollection metaDataCollection;
	private VisualEditorProperties visualDependencies;
	
	//private boolean isCollection; // cytoscape collection.
	
	//when this value is true, the converter will skip data errors and try to make a guess.
	private boolean alwaysCreate;
	
	CXToCX2VisualPropertyConverter vpConverter;

	public static final String messagePrefix = "CX2-CONVERTER: ";
	
	public static final int maximumNumberWarningMessages = 20;
	
	AspectAttributeStat attrStats;
	
	public List<String> getWarning() {
		return warnings;
	}

	private List<String> warnings;
		
	/**
	 * 
	 * @param rootPath the directory of a CX2 network on the server, this path
	 *                 must end with /
	 */
	public CXToCX2ServerSideConverter(String rootPath, 
			MetaDataCollection metadataCollection, String networkIdStr, AspectAttributeStat cx1AttributeStats ,
			boolean alwaysCreate /*, boolean isCytoscapeCollection*/) {
		pathPrefix = rootPath;
		this.metaDataCollection = metadataCollection;
		
		this.attrDeclarations = new CxAttributeDeclaration();
		this.networkId = networkIdStr;
		this.attrStats = cx1AttributeStats;
		this.visualDependencies = new VisualEditorProperties();
		vpConverter = CXToCX2VisualPropertyConverter.getInstance();
		this.alwaysCreate = alwaysCreate;
		warnings = new ArrayList<>(20);
	//	this.isCollection = isCytoscapeCollection;
	}
	
    /**
     * Adds {@code warningStr} to internal {@code warnings} variable 
     * unless there are more then 20 entries in list in which case
     * method just returns
     * @param warningStr warning message to add to warnings list
     */
	private void addWarning(String warningStr) {
		if (warnings.size() >= maximumNumberWarningMessages)
			return;
		
		warnings.add(messagePrefix + warningStr);		
	}
        
    /**
     * Adds any warnings found in {@code cRes} to internal {@code warnings} 
     * @param cRes Result from ConverterUtilitiesResult along with any 
     *             issues encountered during conversion
     */
	private void addWarning(final ConverterUtilitiesResult cRes) {
	    if (cRes == null) {
            return;
        }
        List<String> warnList = cRes.getWarnings();
        if (warnList == null) {
            return;
        }
        for (String warning : warnList) {
            addWarning(warning);
        }
	}
	
	public List<CxMetadata> convert() throws FileNotFoundException, IOException, NdexException {
				
		//create the aspect dir
        String cx2AspectDir  = pathPrefix + File.separator + networkId + File.separator + CX2NetworkLoader.cx2AspectDirName + File.separator;
		Files.createDirectory(Paths.get(cx2AspectDir));
		
		boolean attrStatsAlreadyCreated = true;
		
		if ( attrStats == null){
			attrStatsAlreadyCreated = false;
			attrStats = analyzeAttributes();
		}
		
		attrDeclarations = attrStats.createCxDeclaration();
		
		List<CxMetadata> cx2Metadata = getCX2Metadata();

		try (FileOutputStream out = new FileOutputStream(pathPrefix + File.separator + networkId + File.separator + CX2NetworkLoader.cx2NetworkFileName) ) {
			CXWriter wtr = new CXWriter(out, false);
			
			boolean hasAttributes = !attrDeclarations.getDeclarations().isEmpty();
		
			//write metadata first.
			wtr.writeMetadata(cx2Metadata);
			
			// write attributes declarations
			if (hasAttributes) {
				List<CxAttributeDeclaration> attrDecls = new ArrayList<>(1);
				attrDecls.add(this.attrDeclarations);
				wtr.writeFullAspectFragment(attrDecls);
				
				try (CX2AspectWriter<CxAttributeDeclaration> aspWtr = new CX2AspectWriter<>(cx2AspectDir + CxAttributeDeclaration.ASPECT_NAME)) {
					aspWtr.writeCXElement(attrDeclarations);
				}
			 
			} 
			
			//write network attributes
			CxNetworkAttribute cx2NetAttr = new CxNetworkAttribute();
			try (AspectIterator<NetworkAttributesElement> a = new AspectIterator<>(networkId, NetworkAttributesElement.ASPECT_NAME, NetworkAttributesElement.class, pathPrefix) ) {
				while (a.hasNext()) {
					NetworkAttributesElement netAttr = a.next();
					try {
						Object attrValue = CXToCX2Converter.convertAttributeValue(netAttr);
						Object oldV = cx2NetAttr.getAttributes().put(netAttr.getName(), attrValue);

						// if attrStats had to be created by this method
						// skip the duplicate network attribute name check because
						// analyzeAttributes() performs the check
						if (attrStatsAlreadyCreated == true && oldV !=null) {
						   String msg = "Duplicated network attribute name found: " + netAttr.getName();	
						   if (alwaysCreate) {
							 addWarning(msg);  
						   } else 
							   throw new NdexException(msg);
						}
					} catch(NumberFormatException nfe){
						String errMsg = "For network attribute '"
								+ netAttr.getName() + "' unable to convert value  to '" 
								+ netAttr.getDataType() + "' : " + nfe.getMessage();
						if (alwaysCreate){
							addWarning(errMsg);
						} else
							throw new NdexException(errMsg);
					}
				}
			}		
			if ( !cx2NetAttr.getAttributes().isEmpty()) {
				List<CxNetworkAttribute> netAttrs = new ArrayList<>(1);
				netAttrs.add(cx2NetAttr);
				wtr.writeFullAspectFragment(netAttrs);
				
				try (CX2AspectWriter<CxNetworkAttribute> aspWtr = new CX2AspectWriter<>(cx2AspectDir + CxNetworkAttribute.ASPECT_NAME)) {
					aspWtr.writeCXElement(cx2NetAttr);
				}
				
			}
			 
			//write nodes
			if( needToWriteAspect(CxNode.ASPECT_NAME, cx2Metadata)) {
				Map<Long, CxNode> nodeTable = createCX2NodeTable();
				wtr.startAspectFragment(CxNode.ASPECT_NAME);
				try (CX2AspectWriter<CxNode> aspWtr = new CX2AspectWriter<>(cx2AspectDir + CxNode.ASPECT_NAME)) {
					for (CxNode n : nodeTable.values()) {
					
						wtr.writeElementInFragment(n);
						aspWtr.writeCXElement(n);
					}
				}
				wtr.endAspectFragment();
			}
			
			//write edges
			if ( needToWriteAspect(CxEdge.ASPECT_NAME, cx2Metadata)) {
				Map<Long, CxEdge> edgeAttrTable = createEdgeAttrTable();
				wtr.startAspectFragment(CxEdge.ASPECT_NAME);
				try (AspectIterator<EdgesElement> a = new AspectIterator<>(networkId, EdgesElement.ASPECT_NAME, EdgesElement.class, pathPrefix) ) {
					try(CX2AspectWriter<CxEdge> aspWtr = new CX2AspectWriter<>(cx2AspectDir + CxEdge.ASPECT_NAME)) {
						while (a.hasNext()) {
							EdgesElement cx1Edge = a.next();
							CxEdge cx2Edge = edgeAttrTable.remove(cx1Edge.getId());
							if ( cx2Edge == null) 
								cx2Edge = new CxEdge(cx1Edge.getId());
							cx2Edge.setSource(cx1Edge.getSource());
							cx2Edge.setTarget(cx1Edge.getTarget());
							if ( cx1Edge.getInteraction() != null) {
								EdgeAttributesElement attr = new EdgeAttributesElement(cx1Edge.getId(), CxEdge.INTERACTION, cx1Edge.getInteraction(), ATTRIBUTE_DATA_TYPE.STRING);	
								try {
									cx2Edge.addCX1EdgeAttribute(attr, this.attrDeclarations);   
								} catch ( NdexException e) {
									if ( !alwaysCreate) 
										throw e;
									addWarning(e.getMessage());
									System.err.println("Network " + networkId + " Ignoring error: " + e.getMessage());
								} catch (NumberFormatException e2) {
									System.err.println("Network " + networkId + "has error: " + e2.getMessage());
									throw new NdexException (e2.getMessage());
								}
							}
							wtr.writeElementInFragment(cx2Edge);
							aspWtr.writeCXElement(cx2Edge);
						}
						
					}
				}		
					
				
				wtr.endAspectFragment();
				
			}
			
			
			// write visualProperites
			CX2VPHolder vp = readVisualProperties();
						
			if ( !vp.getStyle().isEmpty()) {
				wtr.startAspectFragment(CxVisualProperty.ASPECT_NAME);
				wtr.writeElementInFragment(vp.getStyle());
				wtr.endAspectFragment();
				
				try (CX2AspectWriter<CxVisualProperty> aspWtr = new CX2AspectWriter<>(cx2AspectDir + CxVisualProperty.ASPECT_NAME)) {
					aspWtr.writeCXElement(vp.getStyle());
				}
			}
						
			if ( !vp.getNodeBypasses().isEmpty()) {
				wtr.startAspectFragment(CxNodeBypass.ASPECT_NAME);
				
				try (CX2AspectWriter<CxNodeBypass> aspWtr = new CX2AspectWriter<>(cx2AspectDir + CxNodeBypass.ASPECT_NAME)) {

					for ( CxNodeBypass e : vp.getNodeBypasses()) {
						wtr.writeElementInFragment(e);
						aspWtr.writeCXElement(e);
					}
				}
				wtr.endAspectFragment();
			}
			
			if ( !vp.getEdgeBypasses().isEmpty()) {
				wtr.startAspectFragment(CxEdgeBypass.ASPECT_NAME);
				try (CX2AspectWriter<CxEdgeBypass> aspWtr = new CX2AspectWriter<>(cx2AspectDir + CxEdgeBypass.ASPECT_NAME)) {

					for ( CxEdgeBypass e : vp.getEdgeBypasses()) {
						wtr.writeElementInFragment(e);
						aspWtr.writeCXElement(e);
					}
				}
				wtr.endAspectFragment();
			}
			
			// write visualDependencies
			if ( !this.visualDependencies.getProperties().isEmpty()) {
				wtr.startAspectFragment(VisualEditorProperties.ASPECT_NAME);
				wtr.writeElementInFragment(this.visualDependencies);
				wtr.endAspectFragment();
				
				try (CX2AspectWriter<VisualEditorProperties> aspWtr = new CX2AspectWriter<>(cx2AspectDir + VisualEditorProperties.ASPECT_NAME)) {
					aspWtr.writeCXElement(this.visualDependencies);
				}
			}

			//write possible opaque aspects
	        String cx1AspectDir  = pathPrefix + File.separator + networkId + File.separator + CXNetworkLoader.CX1AspectDir ;

			for ( CxMetadata m : cx2Metadata) {
				String aspectName = m.getName();
			    if (! CX2ToCXConverter.cx2SpecialAspects.contains(aspectName) && 
					   !aspectName.equals(CxNode.ASPECT_NAME) && !aspectName.equals(CxEdge.ASPECT_NAME)
					   && !aspectName.equals(CxVisualProperty.ASPECT_NAME)) {
					wtr.startAspectFragment(aspectName);
					try (AspectIterator<CxOpaqueAspectElement> a = new AspectIterator<>(networkId, aspectName, CxOpaqueAspectElement.class, pathPrefix) ) {
						while ( a.hasNext())
							wtr.writeElementInFragment(a.next());
					}
					wtr.endAspectFragment();
					
					Path tgt = Paths.get(cx1AspectDir, aspectName);
					Path link = Paths.get(pathPrefix + File.separator + networkId + File.separator + CX2NetworkLoader.cx2AspectDirName, aspectName);
					Files.createSymbolicLink(link, tgt);
			   }
			}
			
			wtr.finish();
		}
		
		return cx2Metadata;
		
	}
	
	private static boolean needToWriteAspect(String aspectName, List<CxMetadata> metaDataList) {
		for ( CxMetadata m : metaDataList) {
			if ( m.getName().equals(aspectName) ) {
				Long c = m.getElementCount();
				return c !=null && c.longValue() > 0; 
					
			}
		}
		return false;
	}
	
	private Map<Long, CxNode> createCX2NodeTable () throws JsonProcessingException, IOException, NdexException {
		Map<Long, CxNode> nodeTable = new TreeMap<>();
		
		// go through node aspects.
		try (AspectIterator<NodesElement> nodes = new AspectIterator<>(networkId, NodesElement.ASPECT_NAME, NodesElement.class, pathPrefix) ) {
			while (nodes.hasNext()) {
				NodesElement cx1node = nodes.next();
				Long nodeId = Long.valueOf(cx1node.getId());
				CxNode newNode = new CxNode(nodeId);
										
				if ( cx1node.getNodeName() != null) {
					NodeAttributesElement attr = new NodeAttributesElement(nodeId, CxNode.NAME, cx1node.getNodeName(), ATTRIBUTE_DATA_TYPE.STRING);	
					newNode.addCX1NodeAttribute(attr, this.attrDeclarations);
				}   
				if (cx1node.getNodeRepresents() != null) {
					NodeAttributesElement attr = new NodeAttributesElement(nodeId, CxNode.REPRESENTS, cx1node.getNodeName(), ATTRIBUTE_DATA_TYPE.STRING);	
					newNode.addCX1NodeAttribute(attr, this.attrDeclarations);
				}    
				
				nodeTable.put(nodeId, newNode);
			}
		}
		
		// then node attribute aspect
		try (AspectIterator<NodeAttributesElement> nAttrs = new AspectIterator<>(networkId, NodeAttributesElement.ASPECT_NAME, NodeAttributesElement.class, pathPrefix) ) {
			while (nAttrs.hasNext()) {
				NodeAttributesElement cx1nodeAttr = nAttrs.next();
				Long nodeId = cx1nodeAttr.getPropertyOf();
				CxNode newNode = nodeTable.get(nodeId);
				try {
					newNode.addCX1NodeAttribute(cx1nodeAttr, this.attrDeclarations);
				} catch( NdexException e) {
					if (!alwaysCreate)
						throw e;
					addWarning (e.getMessage());
					System.err.println("Network " + networkId + " Ignoring error: " + e.getMessage());

				} catch (NumberFormatException e2) {
					// @TODO Check if this scenario should be considered
					//       fatal if alwaysCreate is true
					String errMsg = "For node attribute id: "
							+ cx1nodeAttr.getPropertyOf()
							+ " with name '" + cx1nodeAttr.getName()
							+ "' received fatal parsing error: " + e2.getMessage();
					System.err.println("Network " + networkId + "has error: " + errMsg);
					throw new NdexException (errMsg);
				}
			}
		}
		
		// then coordinate aspect
		try (AspectIterator<CartesianLayoutElement> coordinates = new AspectIterator<>(networkId, CartesianLayoutElement.ASPECT_NAME, CartesianLayoutElement.class, pathPrefix) ) {
			while (coordinates.hasNext()) {
				CartesianLayoutElement coord = coordinates.next();
				Long nodeId = coord.getNode();
				CxNode newNode = nodeTable.get(nodeId);
				if ( newNode == null)
					throw new  NdexException ("Node " + nodeId + " is referenced in " + CartesianLayoutElement.ASPECT_NAME
							+ " but not defined in the nodes aspect.");
				newNode.setCoordinates(coord.getX(), coord.getY(), coord.getZ());
			}
		}
		
		return nodeTable;
	}
	
	/*
	 * This function returns the table that only holds edge attributes. Edge is not populated yet.
	 */
	private Map<Long, CxEdge> createEdgeAttrTable() throws NdexException, JsonProcessingException, IOException {
		Map<Long, CxEdge> edgeTable = new TreeMap<> ();
		
		try (AspectIterator<EdgeAttributesElement> eAttrs = new AspectIterator<>(networkId, EdgeAttributesElement.ASPECT_NAME, EdgeAttributesElement.class, pathPrefix) ) {
			while (eAttrs.hasNext()) {
				EdgeAttributesElement cx1EdgeAttr = eAttrs.next();
				Long edgeId = cx1EdgeAttr.getPropertyOf();
				CxEdge newEdge = edgeTable.get(edgeId);
				if( newEdge == null) {
					newEdge = new CxEdge(edgeId);
					edgeTable.put(edgeId, newEdge);
				}
				try {
					newEdge.addCX1EdgeAttribute(cx1EdgeAttr, this.attrDeclarations);
				} catch (NdexException e) {
					if ( !alwaysCreate)
						throw e;
					addWarning (e.getMessage());
					System.err.println("Network " + networkId + " Ignoring error: " + e.getMessage());
				} catch (NumberFormatException e2) {
					// @TODO Check if this scenario should be considered
					//       fatal if alwaysCreate is true
					String errMsg = "For edge attribute id: "
							+ cx1EdgeAttr.getPropertyOf()
							+ " with name '" + cx1EdgeAttr.getName()
							+ "' received fatal parsing error: " + e2.getMessage();
					System.err.println("Network " + networkId + "has error: " + errMsg);
					throw new NdexException (errMsg);
				}
			}
		}
		
		return edgeTable;
	}
	
	
	/* warning: this function can only be called after attrStats is initialized */
	private List<CxMetadata> getCX2Metadata() {
		List<CxMetadata> result = new ArrayList<>(metaDataCollection.size());
				
		MetaDataElement networkAttribute = metaDataCollection.getMetaDataElement(NetworkAttributesElement.ASPECT_NAME);
		if ( networkAttribute != null ) {
			result.add(new CxMetadata (CxNetworkAttribute.ASPECT_NAME, 1L));
		}	

		MetaDataElement vpM = metaDataCollection.getMetaDataElement( CyVisualPropertiesElement.ASPECT_NAME);
		if ( vpM != null) {
			result.add(new CxMetadata(CxVisualProperty.ASPECT_NAME, 1L));
			
			//addCx2 extra aspects
			result.add(new CxMetadata(VisualEditorProperties.ASPECT_NAME, 1L));
			
			if (attrStats.getNodeBypassCount() > 0) {
				result.add(new CxMetadata(CxNodeBypass.ASPECT_NAME,
						attrStats.getNodeBypassCount()));
			}
			
			if (attrStats.getEdgeBypassCount() > 0) {
				result.add(new CxMetadata(CxEdgeBypass.ASPECT_NAME, attrStats.getEdgeBypassCount()));
			}
		}
		
		if (!attrDeclarations.getDeclarations().isEmpty()) {
			result.add(new CxMetadata(CxAttributeDeclaration.ASPECT_NAME,1L));
		}
		
		for ( MetaDataElement e: metaDataCollection) {
			String aspectName = e.getName();
			if ( !aspectName.equals(NetworkAttributesElement.ASPECT_NAME) && 
					!aspectName.equals(CyVisualPropertiesElement.ASPECT_NAME) &&
					!aspectName.equals(NodeAttributesElement.ASPECT_NAME) &&
					!aspectName.equals(EdgeAttributesElement.ASPECT_NAME) &&
					!aspectName.equals(CartesianLayoutElement.ASPECT_NAME) ) {
				result.add(new CxMetadata(e.getName(), e.getElementCount().longValue()));
			}
	
		}
		return result;

	}
	
	
	private AspectAttributeStat analyzeAttributes() throws NdexException, IOException {
		
		AspectAttributeStat attributeStats = new AspectAttributeStat();
		
		// check nodes aspect
		try (AspectIterator<NodesElement> nodes = new AspectIterator<>(networkId, NodesElement.ASPECT_NAME, NodesElement.class, pathPrefix) ) {
			while (nodes.hasNext()) {
				attributeStats.addNode(nodes.next());
				if ( attributeStats.hasBothReservedNodeAttr())
					break;
			}
		}
		
		//check edges aspect
		try (AspectIterator<EdgesElement> edges = new AspectIterator<>(networkId, EdgesElement.ASPECT_NAME, EdgesElement.class, pathPrefix) ) {
			while (edges.hasNext()) {
				attributeStats.addEdge(edges.next());
				if ( attributeStats.hasEdgeInteractionAttr())
					break;
			}
		}
		
		//check network attribute
		try (AspectIterator<NetworkAttributesElement> a = new AspectIterator<>(networkId, NetworkAttributesElement.ASPECT_NAME, NetworkAttributesElement.class, pathPrefix) ) {
			while (a.hasNext()) {
				try {
					attributeStats.addNetworkAttribute(a.next());	
				} catch ( NdexException e) {
					if ( !alwaysCreate)
						throw e;
					addWarning(e.getMessage());
					System.err.println("Network " + networkId + " Ignoring error: " + e.getMessage());
				} 
			}
		}
		
		//check node attributes
		try (AspectIterator<NodeAttributesElement> a = new AspectIterator<>(networkId, NodeAttributesElement.ASPECT_NAME, NodeAttributesElement.class, pathPrefix) ) {
			while (a.hasNext()) {
				NodeAttributesElement attr = a.next();
				if (attr.getName().equals("name") || attr.getName().equals("represents")){
					String errMsg = "Node attribute id: "
							+ attr.getPropertyOf() + " is named '"
							+ attr.getName() + "' which is not allowed in CX spec.";				
					if (!alwaysCreate){
						throw new NdexException (errMsg);
					}
					addWarning(errMsg);
				}
				attributeStats.addNodeAttribute(attr);
			}
		}
		
		//check edge attributes
		try (AspectIterator<EdgeAttributesElement> a = new AspectIterator<>(networkId, EdgeAttributesElement.ASPECT_NAME, EdgeAttributesElement.class, pathPrefix) ) {
			while (a.hasNext()) {
				EdgeAttributesElement e = a.next();
				if (  (e.getName().equals("interaction"))) {
					String errMsg = "Edge attribute id: "
							+ e.getPropertyOf() + " is named '"
							+ e.getName() + "' which is not allowed in CX spec.";	
					if (!alwaysCreate)
						throw new NdexException (errMsg);
					addWarning (errMsg);
				}	
				attributeStats.addEdgeAttribute(e);
			}
		}
		
		//check node and edge bypass count
		try (AspectIterator<CyVisualPropertiesElement> a = new AspectIterator<>(networkId, CyVisualPropertiesElement.ASPECT_NAME, CyVisualPropertiesElement.class, pathPrefix) ) {
			while (a.hasNext()) {
				attributeStats.addCyVisualPropertiesElement(a.next());
			}
		}
		return attributeStats;
	}
	
	private CX2VPHolder readVisualProperties() throws JsonProcessingException, IOException, NdexException {
		CX2VPHolder holder = new CX2VPHolder ();
		
		try (AspectIterator<CyVisualPropertiesElement> a = new AspectIterator<>(networkId, CyVisualPropertiesElement.ASPECT_NAME, CyVisualPropertiesElement.class, pathPrefix) ) {
			while (a.hasNext()) {
				CyVisualPropertiesElement e = a.next();
				addVisuaProperty(e, holder);
			}
		}
		
		return holder;

	}
   
	
	   private void addVisuaProperty(CyVisualPropertiesElement elmt,CX2VPHolder holder) throws NdexException, IOException {
	    	
		    CxVisualProperty style = holder.getStyle();
		    String po = elmt.getProperties_of();
	    	if ( po.equals("network")) {

	    		style.getDefaultProps().setNetworkProperties(vpConverter.convertNetworkVPs(elmt.getProperties()));
	    	} else if( po.equals("nodes:default")) {

	       		// get dependencies
	    		String nodeSizeLockedStr = elmt.getDependencies().get("nodeSizeLocked");
	    	    		
	    		boolean nodeSizeLocked = ( nodeSizeLockedStr != null && nodeSizeLockedStr.equals("true"));   	
	    		
	    		this.visualDependencies.getProperties().put("nodeSizeLocked", Boolean.valueOf(nodeSizeLockedStr) );
	    		this.visualDependencies.getProperties().put("nodeCustomGraphicsSizeSync", 
	    				Boolean.valueOf(elmt.getDependencies().get("nodeCustomGraphicsSizeSync")) );

	    		Map<String,String> cx1Properties = elmt.getProperties();
	    		if ( nodeSizeLocked) {
	    			String size = cx1Properties.get("NODE_SIZE");
	    			cx1Properties.put("NODE_WIDTH", size);
	    			cx1Properties.put("NODE_HEIGHT", size);
	    		}
	    		style.getDefaultProps().setNodeProperties(vpConverter.convertEdgeOrNodeVPs(cx1Properties));
	    		
	    		// add mapping
	    		SortedMap<String,Mapping> nodeMappings = elmt.getMappings();
	    		
	    		if ( nodeSizeLocked ) {
	       			Mapping sizeMapping = nodeMappings.remove("NODE_SIZE");
	       			if ( sizeMapping != null ) {
	       				nodeMappings.put("NODE_WIDTH", sizeMapping);
	       				nodeMappings.put("NODE_HEIGHT", sizeMapping);
	       			}
	    		}
	    		
	    		if ( nodeMappings != null && !nodeMappings.isEmpty()) {
	    			processMappingEntry(nodeMappings, style.getNodeMappings());
	    		}
	    		
	    	} else if ( po.equals("edges:default")) {
	      		//Map<String, Object> defaultVp = getDefaultVP (style);
	    		      		
	    		SortedMap<String,String> cx1Properties = elmt.getProperties();
	    		
	    		this.visualDependencies.getProperties().putAll(elmt.getDependencies());
	    		
	    		// add dependencies
	    		String arrowColorMatchesEdgeStr = elmt.getDependencies().get("arrowColorMatchesEdge");

	    		this.visualDependencies.getProperties().put("arrowColorMatchesEdge", Boolean.valueOf(arrowColorMatchesEdgeStr) );
	    		
	    		boolean arrowColorMatchesEdge = (arrowColorMatchesEdgeStr != null && arrowColorMatchesEdgeStr.equals("true"));

	    		if ( arrowColorMatchesEdge ) {
	    			String ep = cx1Properties.get("EDGE_PAINT");
	    			cx1Properties.put("EDGE_SOURCE_ARROW_UNSELECTED_PAINT", ep);
	    			cx1Properties.put("EDGE_STROKE_UNSELECTED_PAINT", ep);
	    			cx1Properties.put("EDGE_TARGET_ARROW_UNSELECTED_PAINT", ep);
	    		}

	    		style.getDefaultProps().setEdgeProperties(vpConverter.convertEdgeOrNodeVPs(cx1Properties));
	    		
	    		// add mapping
	    		SortedMap<String,Mapping> edgeMappings = elmt.getMappings();
	    		
	    		if ( arrowColorMatchesEdge) {
	    			Mapping m = edgeMappings.remove("EDGE_PAINT");
	    			if ( m !=null) {
	    				edgeMappings.put("EDGE_SOURCE_ARROW_UNSELECTED_PAINT", m);
	    				edgeMappings.put("EDGE_STROKE_UNSELECTED_PAINT", m);
	    				edgeMappings.put("EDGE_TARGET_ARROW_UNSELECTED_PAINT", m);
	    			}
	    		}	
	    		
	    		if ( edgeMappings != null && !edgeMappings.isEmpty()) {
	    			processMappingEntry(edgeMappings, style.getEdgeMappings());
	    		}
	    		
	    	} else if ( po.equals("nodes")) {  //node bypasses
	    		
	    		SortedMap<String,String> vps = elmt.getProperties();
	    		
	    		Boolean nodeSizeLocked = (Boolean)this.visualDependencies.getProperties().get("nodeSizeLocked");
	    		if( nodeSizeLocked.booleanValue()) {
	    			String nsize = vps.remove("NODE_SIZE");
	    			if ( nsize != null) {
	    				vps.put("NODE_WIDTH", nsize);
	    				vps.put("NODE_HEIGHT",nsize);
	    			}
	    		}
	    		
	    		CxNodeBypass nodebp = new CxNodeBypass(elmt.getApplies_to().longValue(), 
	    				vpConverter.convertEdgeOrNodeVPs(elmt.getProperties()));
	    		
	    		if ( !nodebp.getVisualProperties().isEmpty())
	    			holder.getNodeBypasses().add(nodebp);
	    	} else {  // edge bypasses
	    		Map<String,Object> v = vpConverter.convertEdgeOrNodeVPs(elmt.getProperties());
	    		if ( !v.isEmpty()) {
	    			CxEdgeBypass edgebp = new CxEdgeBypass();
	    			edgebp.setId(elmt.getApplies_to().longValue());
	    			edgebp.setVisualProperties(v);
	    			holder.getEdgeBypasses().add(edgebp);
	    		}
	    	}
	    }
	   
	    /**
	     * Get the default style object from a cx2 style object
	     * @param cx2Style
	     * @return
	     */
	  /*  private static Map<String, Object> getDefaultVP(DefaultVisualProperties cx2Style) {
	    	Map<String, Object> defaultVp = (Map<String, Object>)cx2Style.get("default");
			if ( defaultVp == null) {
				defaultVp = new HashMap<> ();
				cx2Style.put("default", defaultVp);
			}
			return defaultVp;
	    } */
	    
		private void processMappingEntry(SortedMap<String, Mapping> nodeMappings,
				Map<String, VisualPropertyMapping> v2NodeMappings) throws NdexException, IOException {
			for (Map.Entry<String, Mapping> entry : nodeMappings.entrySet()) {
				String vpName = entry.getKey();
				String newVPName = vpConverter.getNewEdgeOrNodeProperty(vpName);
				if (newVPName == null)
					continue;

				VisualPropertyMapping mappingObj = new VisualPropertyMapping();
				String mappingType = entry.getValue().getType();
				mappingObj.setType(VPMappingType.valueOf(mappingType));
				MappingDefinition defObj = new MappingDefinition();
				mappingObj.setMappingDef(defObj);
				String defString = entry.getValue().getDefinition();
				int len = defString.length();
				if ( len > 30000)
					throw new NdexException ("Mapping value on " + vpName + " is too long. The maximium length allowed is 30k.");
				try {
					if (mappingType.equals("PASSTHROUGH")) {
						String mappingAttrName = ConverterUtilities.getPassThroughMappingAttribute(defString);
						defObj.setAttributeName(mappingAttrName);
					} else if (mappingType.equals("DISCRETE")) {
						List<Map<String, Object>> m = new ArrayList<>();
						try {
							MappingValueStringParser sp = new MappingValueStringParser(defString);

							String col = sp.get("COL");
							String t = sp.get("T");
							int counter = 0;
							while (true) {
								final String k = sp.get("K=" + counter);
								if (k == null) {
									break;
								}
								final String v = sp.get("V=" + counter);

								if (v == null) {
									throw new NdexException(
											"error: discrete mapping string is corruptted for " + defString);
								}

								Map<String, Object> mapEntry = new HashMap<>(2);
								ConverterUtilitiesResult cRes = ConverterUtilities.cvtStringValueToObj(t, k);
								addWarning(cRes);
								mapEntry.put("v", cRes);
								mapEntry.put("vp", vpConverter.getNewEdgeOrNodePropertyValue(vpName, v));
								m.add(mapEntry);
								counter++;
							}

							defObj.setAttributeName(col);
							defObj.setMapppingList(m);
						} catch (IOException e) {
							if (alwaysCreate) {
								addWarning(e.getMessage());
								System.err.println(e.getMessage());
								continue;
							}
							// otherwise throw the exception.
							throw e;
						}

					} else { // continuous mapping
						List<Map<String, Object>> m = new ArrayList<>();
						MappingValueStringParser sp = new MappingValueStringParser(defString);
						String col = sp.get("COL");
						String t = sp.get("T");

						Object min = null;
						Boolean includeMin = null;
						// Object max = null;
						Object minVP = null;
						// Object maxVP = null;

						int counter = 0;
						Map<String, Object> currentMapping = new HashMap<>();

						while (true) {
							final String L = sp.get("L=" + counter);
							if (L == null) {
								break;
							}
							Object LO = vpConverter.getNewEdgeOrNodePropertyValue(vpName, L);

							final String E = sp.get("E=" + counter);
							if (E == null) {
								break;
							}
							// Object EO = vpConverter.getNewEdgeOrNodePropertyValue(vpName,E);

							final String G = sp.get("G=" + counter);
							if (G == null) {
								break;
							}
							Object GO = vpConverter.getNewEdgeOrNodePropertyValue(vpName, G);

							final String OV = sp.get("OV=" + counter);
							if (OV == null) {
								throw new NdexException(
										"error: continuous mapping string is corruptted for " + defString);
							}
							ConverterUtilitiesResult cRes = ConverterUtilities.cvtStringValueToObj(t, OV);
							addWarning(cRes);
							
							Object OVO = cRes.getResult();

							if (counter == 0) { // min side
								currentMapping.put("includeMin", Boolean.FALSE);
								currentMapping.put("includeMax", Boolean.valueOf(E.equals(L)));
								currentMapping.put("maxVPValue", LO);
								currentMapping.put("max", OVO);
								m.add(currentMapping);

							} else {
								currentMapping.put("includeMin", includeMin);
								currentMapping.put("includeMax", Boolean.valueOf(E.equals(L)));
								currentMapping.put("minVPValue", minVP);
								currentMapping.put("min", min);
								currentMapping.put("maxVPValue", LO);
								currentMapping.put("max", OVO);
								m.add(currentMapping);
							}

							// store the max values as min for the next segment
							includeMin = Boolean.valueOf(E.equals(G));

							min = OVO;
							minVP = GO;

							currentMapping = new HashMap<>();
							counter++;
						}

						// add the last entry
						currentMapping.put("includeMin", includeMin);
						currentMapping.put("includeMax", Boolean.FALSE);
						currentMapping.put("minVPValue", minVP);
						currentMapping.put("min", min);
						m.add(currentMapping);

						// add the list
						defObj.setAttributeName(col);
						defObj.setMapppingList(m);
					}
				} catch (NdexException e) {
					throw new NdexException(
							"Can't converter " + mappingType + " mapping on " + vpName + ". Cause: " + e.getMessage());
				}
				v2NodeMappings.put(newVPName, mappingObj);
			}
		}
	   

	
	// escape ',' with double ',' 
   /* private static String escapeString(String str) {
        if (str == null) {
          return null;
        }
        StringBuilder result = new StringBuilder();
        for (int i=0; i<str.length(); i++) {
          char curChar = str.charAt(i);
          if (curChar == COMMA) {
            // special char
            result.append(COMMA);
          }
          result.append(curChar);
        }
        return result.toString();
      }
    */
}
