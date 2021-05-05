package org.ndexbio.common.persistence;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import org.apache.commons.io.FileUtils;
import org.ndexbio.cx2.aspect.element.core.Cx2Network;
import org.ndexbio.cx2.aspect.element.core.CxAttributeDeclaration;
import org.ndexbio.cx2.aspect.element.core.CxEdge;
import org.ndexbio.cx2.aspect.element.core.CxEdgeBypass;
import org.ndexbio.cx2.aspect.element.core.CxMetadata;
import org.ndexbio.cx2.aspect.element.core.CxNetworkAttribute;
import org.ndexbio.cx2.aspect.element.core.CxNode;
import org.ndexbio.cx2.aspect.element.core.CxNodeBypass;
import org.ndexbio.cx2.aspect.element.core.CxOpaqueAspectElement;
import org.ndexbio.cx2.aspect.element.core.CxVisualProperty;
import org.ndexbio.cx2.aspect.element.cytoscape.VisualEditorProperties;
import org.ndexbio.cx2.converter.AspectAttributeStat;
import org.ndexbio.cx2.converter.CX2VPHolder;
import org.ndexbio.cx2.converter.CXToCX2VisualPropertyConverter;
import org.ndexbio.cx2.io.CX2AspectWriter;
import org.ndexbio.cx2.io.CXWriter;
import org.ndexbio.cxio.aspects.datamodels.ATTRIBUTE_DATA_TYPE;
import org.ndexbio.cxio.aspects.datamodels.CartesianLayoutElement;
import org.ndexbio.cxio.aspects.datamodels.CyVisualPropertiesElement;
import org.ndexbio.cxio.aspects.datamodels.EdgeAttributesElement;
import org.ndexbio.cxio.aspects.datamodels.EdgesElement;
import org.ndexbio.cxio.aspects.datamodels.NetworkAttributesElement;
import org.ndexbio.cxio.aspects.datamodels.NodeAttributesElement;
import org.ndexbio.cxio.aspects.datamodels.NodesElement;
import org.ndexbio.cxio.core.AspectIterator;
import org.ndexbio.cxio.core.writers.NiceCXCX2Writer;
import org.ndexbio.cxio.metadata.MetaDataCollection;
import org.ndexbio.model.cx.NamespacesElement;
import org.ndexbio.model.exceptions.NdexException;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

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
	
//	CXToCX2VisualPropertyConverter vpConverter;

	
	private static final int maximumNumberWarningMessages = 20;
	
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
//		vpConverter = CXToCX2VisualPropertyConverter.getInstance();
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
		
		warnings.add(NiceCXCX2Writer.messagePrefix + warningStr);		
	}
        
    /**
     * Adds any warnings found in {@code cRes} to internal {@code warnings} 
     * @param cRes Result from ConverterUtilitiesResult along with any 
     *             issues encountered during conversion
     */
/*	private void addWarning(final ConverterUtilitiesResult cRes) {
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
	} */
	
	public List<CxMetadata> convert() throws FileNotFoundException, IOException, NdexException {
				
		//create the aspect dir
        String cx2AspectDir  = pathPrefix + File.separator + networkId + File.separator + CX2NetworkLoader.cx2AspectDirName + File.separator;
		
    	File f = new File(cx2AspectDir);
    	if (f.exists()) {
    		FileUtils.deleteDirectory(f);
    	}
    	
        Files.createDirectory(Paths.get(cx2AspectDir));
		
		boolean attrStatsAlreadyCreated = true;
		
		if ( attrStats == null){
			attrStatsAlreadyCreated = false;
			attrStats = analyzeAttributes();
		}
		
		attrDeclarations = attrStats.createCxDeclaration();
		
		List<CxMetadata> cx2Metadata = attrStats.getCX2Metadata( metaDataCollection ,attrDeclarations);

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
						Object attrValue = AspectAttributeStat.convertAttributeValue(netAttr);
						Object oldV = cx2NetAttr.getAttributes().put(netAttr.getName(), attrValue);

						// if attrStats had to be created by this method
						// skip the duplicate network attribute name check because
						// analyzeAttributes() performs the check
						if (attrStatsAlreadyCreated == true && oldV !=null && 
								!attrValue.equals(oldV)) {
						   String msg = "Inconsistent network attribute value found on attribute '" + netAttr.getName()
						      + "'. It has value (" + oldV.toString() + ") and (" + netAttr.getValueAsJsonString()+")" ;	
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
			
			// add @context as network attribute if it is a separate aspect
			if ( attrStats.hasNamespacesAspect()) {
				ObjectMapper om = new ObjectMapper();
				NamespacesElement namespaces = null;
				try (AspectIterator<NamespacesElement> a = new AspectIterator<>(networkId, NamespacesElement.ASPECT_NAME, NamespacesElement.class, pathPrefix) ) {
					while (a.hasNext()) {
						namespaces = a.next();
					}
				}	
				if ( namespaces!=null)
					cx2NetAttr.add(NamespacesElement.ASPECT_NAME, om.writeValueAsString(namespaces));
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
									String warning = cx2Edge.addCX1EdgeAttribute(attr, this.attrDeclarations);
									if (warning != null)
										addWarning(warning);
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
			    if (! Cx2Network.cx2SpecialAspects.contains(aspectName) && 
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
					String warning = newNode.addCX1NodeAttribute(attr, this.attrDeclarations);
					if ( warning != null)
						addWarning(warning);
				}   
				if (cx1node.getNodeRepresents() != null) {
					NodeAttributesElement attr = new NodeAttributesElement(nodeId, CxNode.REPRESENTS, cx1node.getNodeRepresents(), ATTRIBUTE_DATA_TYPE.STRING);	
					String warning = newNode.addCX1NodeAttribute(attr, this.attrDeclarations);
					if ( warning != null)
						addWarning(warning);
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
					String warning = newNode.addCX1NodeAttribute(cx1nodeAttr, this.attrDeclarations);
					if ( warning != null) {
						addWarning(warning);
					}
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
					String warning = newEdge.addCX1EdgeAttribute(cx1EdgeAttr, this.attrDeclarations);
					if ( warning != null )
						addWarning(warning);
				} catch (NdexException e) {
					if ( !alwaysCreate)
						throw e;
					addWarning (e.getMessage());
					System.err.println("Network " + networkId + " Ignoring error: " + e.getMessage());
				} catch (NumberFormatException e2) {
					// special case to ignore nulls
					
					if ( cx1EdgeAttr.isSingleValue() && cx1EdgeAttr.getValue().toLowerCase().equals("null")) {
						addWarning("Edge attribute '" + cx1EdgeAttr.getName() + "' on edge " +
								cx1EdgeAttr.getPropertyOf() + " is " + cx1EdgeAttr.getValue() + ". It will be ignored." );
						continue;
					}	
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
		
	
	private AspectAttributeStat analyzeAttributes() throws NdexException, IOException {
		
		AspectAttributeStat attributeStats = new AspectAttributeStat();
		
		boolean foundEdgeInteractionAttr = false;
		boolean foundNodeNameAttr = false;
		boolean foundNodeRepresentAttr = false;
		
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
					String warning = attributeStats.addNetworkAttribute(a.next());
					if ( warning != null)
						addWarning(warning);
				} catch ( NdexException e) {
					if ( !alwaysCreate)
						throw e;
					addWarning(e.getMessage());
					System.err.println("Network " + networkId + " Ignoring error: " + e.getMessage());
				} 
			}
		}
				
		// check if @context exists.
		String fname = pathPrefix + networkId + "/aspects/"+ NamespacesElement.ASPECT_NAME;
		java.nio.file.Path contextAspectFile = Paths.get(fname);
		if ( Files.exists(contextAspectFile))
			attributeStats.setHasNamespacesAspect();
		
		
		//check node attributes
		try (AspectIterator<NodeAttributesElement> a = new AspectIterator<>(networkId, NodeAttributesElement.ASPECT_NAME, NodeAttributesElement.class, pathPrefix) ) {
			while (a.hasNext()) {
				NodeAttributesElement attr = a.next();
				if (attr.getName().equals(CxNode.NAME) && (!foundNodeNameAttr) ){
					String errMsg = "Attribute '" + attr.getName() + "' on node "
							+ attr.getPropertyOf() + " is not allowed in CX specification. Please upgrade your cyNDEx-2 and Cytoscape to the latest version and reload this network.";				
					addWarning ( errMsg);
					foundNodeNameAttr = true;
				} else if ( attr.getName().equals(CxNode.REPRESENTS) && !foundNodeRepresentAttr) {
					String errMsg = "Attribute '" + attr.getName() + "' on node "
							+ attr.getPropertyOf() + " is not allowed in CX specification. Please upgrade your cyNDEx-2 and Cytoscape to the latest version and reload this network.";				
					addWarning ( errMsg);
					foundNodeRepresentAttr = true;
				}
				attributeStats.addNodeAttribute(attr);
			}
		}
		
		//check edge attributes
		try (AspectIterator<EdgeAttributesElement> a = new AspectIterator<>(networkId, EdgeAttributesElement.ASPECT_NAME, EdgeAttributesElement.class, pathPrefix) ) {
			while (a.hasNext()) {
				EdgeAttributesElement e = a.next();
				if (  (e.getName().equals(CxEdge.INTERACTION) && (!foundEdgeInteractionAttr))) {
					String errMsg = "Attribute '" + e.getName() + "' on edge "
							+ e.getPropertyOf() + "' is not allowed in CX specification. Please upgrade your cyNDEx-2 and Cytoscape to the latest version and reload this network.";	
					
					addWarning (errMsg);
					foundEdgeInteractionAttr = true;
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
				holder.addVisuaProperty(e, visualDependencies, warnings);
			}
		}
		
		return holder;

	} 
   
	   
}
