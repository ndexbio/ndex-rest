package org.ndexbio.common.persistence;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.SortedMap;
import java.util.stream.Collectors;

import org.ndexbio.cx2.aspect.element.core.Cx2Network;
import org.ndexbio.cx2.aspect.element.core.CxAttributeDeclaration;
import org.ndexbio.cx2.aspect.element.core.CxEdge;
import org.ndexbio.cx2.aspect.element.core.CxEdgeBypass;
import org.ndexbio.cx2.aspect.element.core.CxMetadata;
import org.ndexbio.cx2.aspect.element.core.CxNetworkAttribute;
import org.ndexbio.cx2.aspect.element.core.CxNode;
import org.ndexbio.cx2.aspect.element.core.CxNodeBypass;
import org.ndexbio.cx2.aspect.element.core.CxVisualProperty;
import org.ndexbio.cx2.aspect.element.core.DeclarationEntry;
import org.ndexbio.cx2.aspect.element.core.DefaultVisualProperties;
import org.ndexbio.cx2.aspect.element.core.EdgeControlPoint;
import org.ndexbio.cx2.aspect.element.core.FontFace;
import org.ndexbio.cx2.aspect.element.core.LabelPosition;
import org.ndexbio.cx2.aspect.element.core.ObjectPosition;
import org.ndexbio.cx2.aspect.element.core.VPMappingType;
import org.ndexbio.cx2.aspect.element.core.VisualPropertyMapping;
import org.ndexbio.cx2.aspect.element.core.VisualPropertyTable;
import org.ndexbio.cx2.aspect.element.cytoscape.VisualEditorProperties;
import org.ndexbio.cx2.converter.CX2ToCXVisualPropertyConverter;
import org.ndexbio.cxio.aspects.datamodels.ATTRIBUTE_DATA_TYPE;
import org.ndexbio.cxio.aspects.datamodels.CartesianLayoutElement;
import org.ndexbio.cxio.aspects.datamodels.CyVisualPropertiesElement;
import org.ndexbio.cxio.aspects.datamodels.EdgeAttributesElement;
import org.ndexbio.cxio.aspects.datamodels.EdgesElement;
import org.ndexbio.cxio.aspects.datamodels.NetworkAttributesElement;
import org.ndexbio.cxio.aspects.datamodels.NodeAttributesElement;
import org.ndexbio.cxio.aspects.datamodels.NodesElement;
import org.ndexbio.cxio.core.NdexCXNetworkWriter;
import org.ndexbio.cxio.metadata.MetaDataCollection;
import org.ndexbio.cxio.metadata.MetaDataElement;
import org.ndexbio.model.exceptions.NdexException;

import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Convert a CX2 network on the server to CX1. This converter works on the individual aspects on the 
 * server, not on the full CX2 file.
 * @author jingchen
 *
 */
public class CX2ToCXConverter {
			
	private static final String VM_COL = "COL=";
	private static final String VM_TYPE = "T=";
    private final  static char COMMA = ',';

	
	private String pathPrefix;
	private CxAttributeDeclaration attrDeclarations;
	private	Map<String, CxMetadata> metadataTable;
	private boolean hasLayout;
	private CxNetworkAttribute networkAttributes; 
	
	//private CX2ToCXVisualPropertyConverter vpCvtr;

	private int nodeAttrCount;
	private int edgeAttrCount;
	private int networkAttrCount;
	/**
	 * 
	 * @param rootPath the directory of a CX2 network on the server. 
	 */
	CX2ToCXConverter(String rootPath, CxAttributeDeclaration attributeDeclarations,
			Map<String, CxMetadata> metadata, boolean hasLayout, CxNetworkAttribute networkAttrs) {
		pathPrefix = rootPath;
		this.attrDeclarations = attributeDeclarations;
		this.metadataTable = metadata;
		this.hasLayout = hasLayout;
		this.networkAttributes = networkAttrs;
		//this.vpCvtr = CX2ToCXVisualPropertyConverter.getInstance();

	}
	
	void convert() throws FileNotFoundException, IOException, NdexException {
		
		nodeAttrCount = 0;
		edgeAttrCount = 0;
		networkAttrCount = 0;
		
		String aspectPath = pathPrefix + CX2NetworkLoader.cx2AspectDirName + "/";
		String cx1AspectPath = pathPrefix + CXNetworkLoader.CX1AspectDir + "/";
		
		try (FileOutputStream out = new FileOutputStream(pathPrefix + CXNetworkLoader.CX1FileName)) {
			NdexCXNetworkWriter writer = new NdexCXNetworkWriter(out,true);
			writer.start();
			
			writer.writeMetadata(createCX1PreMetadata());
			
			//write networkAttribute
			Map<String,DeclarationEntry> netAttrDecls = attrDeclarations.getAttributesInAspect(CxNetworkAttribute.ASPECT_NAME);
			if ( netAttrDecls!= null && !netAttrDecls.isEmpty()) {
				writer.startAspectFragment(CxNetworkAttribute.ASPECT_NAME);
				writer.openFragment();
				networkAttributes.extendToFullNode(netAttrDecls);
				for (Map.Entry<String,Object> e : this.networkAttributes.getAttributes().entrySet()) {
					ATTRIBUTE_DATA_TYPE attrType = netAttrDecls.get(e.getKey()).getDataType();
					NetworkAttributesElement na;
					if ( attrType.isSingleValueType()) {
					  na = new NetworkAttributesElement(null, e.getKey(), e.getValue().toString(), attrType);
					} else {
						List<Object> listV = (List<Object>)e.getValue();
						na = new NetworkAttributesElement(null, e.getKey(),
								listV.stream().map(s -> s.toString()).collect(Collectors.toList()), attrType);
					}
					writer.writeElement(na);
					networkAttrCount ++;
				}
				writer.closeFragment();
				writer.endAspectFragment();
			}
			
			ObjectMapper om = new ObjectMapper();
			
			if ( metadataTable.get(CxNode.ASPECT_NAME) != null) {
				
				//write nodes 
				Map<String,DeclarationEntry> nodeAttrDecls = attrDeclarations.getAttributesInAspect(CxNode.ASPECT_NAME); 
				List<CartesianLayoutElement> coordinates = new ArrayList<>(
						hasLayout? metadataTable.get(CxNode.ASPECT_NAME).getElementCount().intValue() : 10);
				writer.startAspectFragment(CxNode.ASPECT_NAME);
				writer.openFragment();
				try (FileInputStream inputStream = new FileInputStream(aspectPath + "nodes")) {

					Iterator<CxNode> it = om.readerFor(CxNode.class).readValues(inputStream);
					
					while (it.hasNext()) {
						CxNode n = it.next();
						n.extendToFullNode(nodeAttrDecls);
						NodesElement node = new NodesElement(n.getId(), (String)n.getAttributes().get(CxNode.NAME),
								(String)n.getAttributes().get(CxNode.REPRESENTS));
						writer.writeElement(node);
						
						if ( hasLayout) {
							coordinates.add(new CartesianLayoutElement(n.getId(), n.getX(), n.getY(), n.getZ()));
						}
					}	
				}
				writer.closeFragment();
				writer.endAspectFragment();

				//write coordinates
				if(hasLayout) {
					writer.startAspectFragment(CartesianLayoutElement.ASPECT_NAME);
					writer.openFragment();
					for (CartesianLayoutElement e : coordinates) 
						writer.writeElement(e);
					writer.closeFragment();
					writer.endAspectFragment();
				}
				
				//write nodeAttributes
				writer.startAspectFragment(NodeAttributesElement.ASPECT_NAME);
				writer.openFragment();
				try (FileInputStream inputStream = new FileInputStream(aspectPath + CxNode.ASPECT_NAME)) {

					Iterator<CxNode> it = om.readerFor(CxNode.class).readValues(inputStream);
					
					while (it.hasNext()) {
						CxNode n = it.next();
						n.extendToFullNode(nodeAttrDecls);
						Map<String,Object> attrs = n.getAttributes();
						for (Map.Entry<String,Object> e : attrs.entrySet()) {
							String attrName = e.getKey();
							if ( !attrName.equals(CxNode.NAME) && !attrName.equals(CxNode.REPRESENTS)) {
								ATTRIBUTE_DATA_TYPE attrType = nodeAttrDecls.get(attrName).getDataType();
								NodeAttributesElement na;
								if ( attrType.isSingleValueType()) {
									na = new NodeAttributesElement(n.getId(), e.getKey(), e.getValue().toString(), attrType);
								} else {
									List<Object> listV = (List<Object>)e.getValue();
									na = new NodeAttributesElement(n.getId(), e.getKey(),
										listV.stream().map(s -> s.toString()).collect(Collectors.toList()), attrType);
								}
								writer.writeElement(na);
								nodeAttrCount ++;
							}
						}
					}	
				}
				writer.closeFragment();
				writer.endAspectFragment();

			}
			
			if ( metadataTable.get(CxEdge.ASPECT_NAME) != null) {
				Map<String,DeclarationEntry> edgeAttrDecls = attrDeclarations.getAttributesInAspect(CxEdge.ASPECT_NAME); 

				//write edges
				writer.startAspectFragment(CxEdge.ASPECT_NAME);
				writer.openFragment();
				try (FileInputStream inputStream = new FileInputStream(aspectPath + CxEdge.ASPECT_NAME)) {

					Iterator<CxEdge> it = om.readerFor(CxEdge.class).readValues(inputStream);
					
					while (it.hasNext()) {
						CxEdge edge = it.next();
						EdgesElement e;
						if (edgeAttrDecls.get(CxEdge.INTERACTION) !=null ) {
							edge.extendToFullNode(edgeAttrDecls);
						    e = new EdgesElement(edge.getId(), edge.getSource(),
								edge.getTarget(), (String)edge.getAttributes().get(CxEdge.INTERACTION));
						} else {
							e =  new EdgesElement(edge.getId(), edge.getSource(),
									edge.getTarget(), null);
						}
						writer.writeElement(e);
					}	
				}
				writer.closeFragment();
				writer.endAspectFragment();

				
				//write edge attributes
				writer.startAspectFragment(EdgeAttributesElement.ASPECT_NAME);
				writer.openFragment();
				try (FileInputStream inputStream = new FileInputStream(aspectPath + CxEdge.ASPECT_NAME)) {

					Iterator<CxEdge> it = om.readerFor(CxEdge.class).readValues(inputStream);
					
					while (it.hasNext()) {
						CxEdge edge = it.next();
						edge.extendToFullNode(edgeAttrDecls);
						Map<String,Object> attrs = edge.getAttributes();
						for (Map.Entry<String,Object> e : attrs.entrySet()) {
							String attrName = e.getKey();
							if ( !attrName.equals(CxEdge.INTERACTION)) {
								ATTRIBUTE_DATA_TYPE attrType = edgeAttrDecls.get(attrName).getDataType();
								EdgeAttributesElement ea;
								if ( attrType.isSingleValueType()) {
									ea = new EdgeAttributesElement(edge.getId(), e.getKey(), e.getValue().toString(), attrType);
								} else {
									List<Object> listV = (List<Object>)e.getValue();
									ea = new EdgeAttributesElement(edge.getId(), e.getKey(),
										listV.stream().map(s -> s.toString()).collect(Collectors.toList()), attrType);
								}
								writer.writeElement(ea);
								edgeAttrCount ++;
							}
						}
					}	
				}
				writer.closeFragment();
				writer.endAspectFragment();
				
			}	
			
			//write visual aspects
			if ( this.metadataTable.get(CxVisualProperty.ASPECT_NAME)!=null) {
				
				writer.startAspectFragment(CyVisualPropertiesElement.ASPECT_NAME);
				writer.openFragment();

				File vsFile = new File(aspectPath + CxVisualProperty.ASPECT_NAME);
				
				CxVisualProperty[] vPs = om.readValue(vsFile, CxVisualProperty[].class); 
				
				DefaultVisualProperties defaultVPs = vPs[0].getDefaultProps();
				
				// get the dependency table
				VisualEditorProperties vep = null;
				File vsEditorPropsFile = new File ( aspectPath + VisualEditorProperties.ASPECT_NAME);
				if (vsEditorPropsFile.exists()) {
					VisualEditorProperties[] vepr = om.readValue(vsEditorPropsFile, VisualEditorProperties[].class);
					vep = vepr[0];
				}
				
				//convert network default VPs
				writer.writeElement(getDefaultNetworkVP(defaultVPs, vep));

				// convert node default and node mappings
				CyVisualPropertiesElement vp = getDefaultNodeVP(vPs[0], vep, this.attrDeclarations );
				
				writer.writeElement(vp);
				
				// convert edge default and edge mappings
				vp = getDefaultEdgeVP(vPs[0], vep, this.attrDeclarations);
				
				writer.writeElement(vp);
				
				// add node bypasses
				if (this.metadataTable.get(CxNodeBypass.ASPECT_NAME) != null) {
					try (FileInputStream inputStream = new FileInputStream(aspectPath + CxNodeBypass.ASPECT_NAME)) {
						Iterator<CxNodeBypass> it = om.readerFor(CxNodeBypass.class).readValues(inputStream);

						while (it.hasNext()) {
							CxNodeBypass bypass = it.next();
							CyVisualPropertiesElement e = new CyVisualPropertiesElement(NodesElement.ASPECT_NAME,
									Long.valueOf(bypass.getId()), null);

							boolean nodeSizeLocked = (vep == null) ? false
									: ((Boolean) vep.getProperties().get("nodeSizeLocked")).booleanValue();
							VisualPropertyTable bypassProps = bypass.getVisualProperties();
							if (nodeSizeLocked) {
								if (bypassProps.get("NODE_WIDTH") != null) {
									bypassProps.getVisualProperties().put("NODE_SIZE", bypassProps.get("NODE_WIDTH"));
								}
							}

							e.setProperties(
									CX2ToCXVisualPropertyConverter.getInstance().convertEdgeOrNodeVPs(bypassProps));

							writer.writeElement(e);
						}
					}
				}
				
				// add edge bypasses
				if (this.metadataTable.get(CxEdgeBypass.ASPECT_NAME) != null) {
					try (FileInputStream inputStream = new FileInputStream(aspectPath + CxEdgeBypass.ASPECT_NAME)) {
						Iterator<CxEdgeBypass> it = om.readerFor(CxEdgeBypass.class).readValues(inputStream);

						while (it.hasNext()) {
							CxEdgeBypass bypass = it.next();
							CyVisualPropertiesElement e = new CyVisualPropertiesElement(EdgesElement.ASPECT_NAME,
									Long.valueOf(bypass.getId()), null);

							VisualPropertyTable bypassProps = bypass.getVisualProperties();

							e.setProperties(
									CX2ToCXVisualPropertyConverter.getInstance().convertEdgeOrNodeVPs(bypassProps));

							writer.writeElement(e);
						}
					}
				}
				writer.closeFragment();
				writer.endAspectFragment();
				
			}
			
			//write other opaque aspects.
			for (String aspectName: this.metadataTable.keySet()) {
				if ( !Cx2Network.cx2SpecialAspects.contains(aspectName) && !aspectName.equals(CxNode.ASPECT_NAME)
						&& ! aspectName.equals(CxEdge.ASPECT_NAME) && !aspectName.equals(CxVisualProperty.ASPECT_NAME)) {
					writer.startAspectFragment(aspectName);
					writer.writeAspectElementsFromNdexAspectFile(aspectPath + aspectName);
					writer.endAspectFragment();
				}
			}
			
			//write post meatadata 
			if ( (nodeAttrCount + edgeAttrCount + networkAttrCount) > 0) {
				MetaDataCollection c = new MetaDataCollection ();
				if ( networkAttrCount > 0) {
					MetaDataElement me = new MetaDataElement(NetworkAttributesElement.ASPECT_NAME, "1.0");
					me.setElementCount(Long.valueOf(networkAttrCount));
					c.add(me);
				}			
				
				if ( nodeAttrCount > 0 ) {
					MetaDataElement me = new MetaDataElement(NodeAttributesElement.ASPECT_NAME, "1.0");
					me.setElementCount(Long.valueOf(nodeAttrCount));
					c.add(me);
				}
				if ( edgeAttrCount > 0 ) {
					MetaDataElement me = new MetaDataElement(EdgeAttributesElement.ASPECT_NAME, "1.0");
					me.setElementCount(Long.valueOf(edgeAttrCount));
					c.add(me);
				}
				writer.writeMetadata(c);
			}
			
			//finish up.
			writer.end();
		}
	}
	
	public static CyVisualPropertiesElement getDefaultNetworkVP (DefaultVisualProperties dvps, 
			VisualEditorProperties vep) {
		CyVisualPropertiesElement vp = new CyVisualPropertiesElement ("network");
		vp.setProperties(CX2ToCXVisualPropertyConverter.getInstance().convertNetworkVPs(dvps.getNetworkProperties()));

		if ( vep != null ) {
			for ( Map.Entry<String,Object> entry: vep.getProperties().entrySet()) {
				String vpName = entry.getKey();
				if ( vpName.equals("NETWORK_CENTER_X_LOCATION") || 
					vpName.equals("NETWORK_CENTER_Y_LOCATION") || 
					vpName.equals("NETWORK_SCALE_FACTOR"))
					vp.getProperties().put(vpName, entry.getValue().toString());
			}
		}
		return vp;
	}
	
	
	public static CyVisualPropertiesElement getDefaultNodeVP (CxVisualProperty vps, VisualEditorProperties vep,
			CxAttributeDeclaration attrDecls) throws NdexException {
		DefaultVisualProperties defaultVPs = vps.getDefaultProps();
		VisualPropertyTable nodeDefaultVPs =defaultVPs.getNodeProperties();
		CyVisualPropertiesElement vp = new CyVisualPropertiesElement ("nodes:default");
		vp.setProperties(CX2ToCXVisualPropertyConverter.getInstance().convertEdgeOrNodeVPs(nodeDefaultVPs));
		
		// set node dependency
		boolean nodeSizeLocked = false;
		if ( vep != null ) {
			Map<String,Object> deps = vep.getProperties();
			SortedMap<String, String> nodeVPDependencies = vp.getDependencies();
			if ( deps.get("nodeSizeLocked") != null ) {
				nodeSizeLocked = ((Boolean)deps.get("nodeSizeLocked")).booleanValue();
				nodeVPDependencies.put("nodeSizeLocked", Boolean.toString(nodeSizeLocked));
				vp.getProperties().put("NODE_SIZE", vp.getProperties().get("NODE_WIDTH"));
			}
			if ( deps.get("nodeCustomGraphicsSizeSync") !=null ) {
				nodeVPDependencies.put("nodeCustomGraphicsSizeSync", 
						deps.get("nodeCustomGraphicsSizeSync").toString()); 
			}
		}
		
		//set node mapping
		Map<String,VisualPropertyMapping> nodeMappings = vps.getNodeMappings();
		if ( nodeSizeLocked) {
			VisualPropertyMapping m = nodeMappings.get("NODE_WIDTH");
			if ( m != null)
				nodeMappings.put("NODE_SIZE", m);
		}
		
		convertMapping(vp, nodeMappings, CxNode.ASPECT_NAME, attrDecls);

		return vp;
	}
	
	
	public static CyVisualPropertiesElement getDefaultEdgeVP (CxVisualProperty vps, VisualEditorProperties vep,
			CxAttributeDeclaration attrDecls) throws NdexException {
		DefaultVisualProperties defaultVPs = vps.getDefaultProps();
		VisualPropertyTable edgeDefaultVPs =defaultVPs.getEdgeProperties();
		CyVisualPropertiesElement vp = new CyVisualPropertiesElement ("edges:default");
		vp.setProperties(CX2ToCXVisualPropertyConverter.getInstance().convertEdgeOrNodeVPs(edgeDefaultVPs));
		
		// set edge dependency
		
		boolean arrowColorMatchesEdge = false;
		if ( vep != null ) {
			Map<String,Object> deps = vep.getProperties();
			SortedMap<String, String> edgeVPDependencies = vp.getDependencies();
			if ( deps.get("arrowColorMatchesEdge") != null ) {
				arrowColorMatchesEdge = ((Boolean)deps.get("arrowColorMatchesEdge")).booleanValue();
				edgeVPDependencies.put("arrowColorMatchesEdge", Boolean.toString(arrowColorMatchesEdge));
				vp.getProperties().put("EDGE_PAINT", vp.getProperties().get("EDGE_STROKE_UNSELECTED_PAINT"));	
			}
		}
		
		//set edge mapping
		Map<String,VisualPropertyMapping> edgeMappings = vps.getEdgeMappings();
		
		// add edge_paint mapping if dependency flag exists.
		if ( arrowColorMatchesEdge) {
			VisualPropertyMapping m = edgeMappings.get("EDGE_STROKE_UNSELECTED_PAINT");
			if ( m != null)
				edgeMappings.put("EDGE_PAINT", m);
		}
		
		convertMapping(vp, edgeMappings, CxEdge.ASPECT_NAME, attrDecls);
		
		return vp;
	}
	
    private static void convertMapping(CyVisualPropertiesElement vp, Map<String,VisualPropertyMapping> mappings, 
    		String aspectName, CxAttributeDeclaration attrDecls) throws NdexException {
    	CX2ToCXVisualPropertyConverter vpCvtr = CX2ToCXVisualPropertyConverter.getInstance();
    	
    	for ( Map.Entry<String, VisualPropertyMapping> cx2Mapping: mappings.entrySet()) {
			String vpName = cx2Mapping.getKey();
			String cx1VPName = vpCvtr.getCx1EdgeOrNodeProperty(vpName);
			VisualPropertyMapping mapping = cx2Mapping.getValue();
			String colName = mapping.getMappingDef().getAttributeName();
			DeclarationEntry decl = attrDecls.getAttributesInAspect(aspectName).get(colName);
			if ( decl == null) {
				throw new NdexException("Mapping on visual property " + vpName + " uses a non-exist attribute '"+
			     colName + "' in aspect " + aspectName+ ".");
			}
			ATTRIBUTE_DATA_TYPE type = decl.getDataType();
			// workaround the cyndex list type handling issue
			if ( !type.isSingleValueType())
				type = type.elementType();
			final StringBuilder sb = new StringBuilder();
	        sb.append(VM_COL);
	        sb.append(escapeString(colName));
	        sb.append(",");
	        sb.append(VM_TYPE);
	        sb.append(type.toString());
            int counter = 0;
			switch ( mapping.getType()) {
			case PASSTHROUGH: {
		        vp.putMapping(cx1VPName, VPMappingType.PASSTHROUGH.toString(), sb.toString());
				break;
			}
			case DISCRETE:
				for ( Map<String,Object> m : mapping.getMappingDef().getMapppingList() ) {
					  Object vpValue = cvtVPfromRaw(vpName,m.get("vp"));
					  sb.append(",K=");
	                  sb.append(counter);
	                  sb.append("=");
	                  sb.append(escapeString(m.get("v").toString()));
	                  sb.append(",V=");
	                  sb.append(counter);
	                  sb.append("=");
	                  sb.append(escapeString(vpCvtr.getCx1EdgeOrNodePropertyValue(vpName,vpValue)));
	                  counter++;
				}
		        vp.putMapping(cx1VPName, VPMappingType.DISCRETE.toString(), sb.toString());
				break;
			case CONTINUOUS: {
				//int total = mapping.getMappingDef().getMapppingList().size(); 
				int cyCounter = 0;
				String L = null;
				String E = null;
				String G = null;
				String ov = null;
				for ( Map<String,Object> m : mapping.getMappingDef().getMapppingList() ) {
					
					Object minV = m.get("min");
					Object maxV = m.get("max");
					Boolean includeMin = (Boolean)m.get("includeMin");
					Boolean includeMax = (Boolean)m.get("includeMax");
					Object minVP = m.get("minVPValue");
					Object maxVP = m.get("maxVPValue");
					
					if ( minVP == null && maxVP == null)
						throw new NdexException ("minVPValue and maxVPValue are both missing in CONTINUOUS mapping of " + vpName + " on column " + colName);
					
					
					
					if ( counter == 0) { // first range
						
						if ( minV != null) {  // no out of range definition
							ov = minV.toString();
							L = vpCvtr.getCx1EdgeOrNodePropertyValue(vpName,minVP);
							E = L;
							G = L;
							sb.append(createMappingStr(cyCounter,L,E,G,ov));
			                cyCounter++;
			                
			                ov = maxV.toString();
		                	L = vpCvtr.getCx1EdgeOrNodePropertyValue(vpName,maxVP);
		                	E=L;
		                	G=L;
							sb.append(createMappingStr(cyCounter,L,E,G,ov));
		                	cyCounter++;
						}
					    L = vpCvtr.getCx1EdgeOrNodePropertyValue(vpName,maxVP);
					    ov = maxV.toString();
					    if ( includeMax.booleanValue()) 
					    	E = L;
					} else {  // middle ranges and the last range
						G = vpCvtr.getCx1EdgeOrNodePropertyValue(vpName,minVP);
						if (includeMin.booleanValue())
							E=G;
						
						// create the mapping point
						sb.append(createMappingStr(cyCounter,L,E,G,ov));
		                cyCounter++;
		                
		                // prepare for the next point
		                if ( maxV != null) {
		                	ov = maxV.toString();
		                	L = vpCvtr.getCx1EdgeOrNodePropertyValue(vpName,maxVP);
		                	if (includeMax.booleanValue())
		                		E = L;
		                	else 
		                		E = null;
		                }	
		                
					}
					counter++;
				}	
		        vp.putMapping(cx1VPName, VPMappingType.CONTINUOUS.toString(), sb.toString());

				break;
			}
			default:
				break;		
			}		
			
		}
    }
    
    private static String createMappingStr(int cyCounter, String L, String E, String G, String ov) {
    	return ",L=" + cyCounter
        +"="
        +escapeString(L)
        +",E="
        +cyCounter
        +"="
        +escapeString(E)
        +",G="
        +cyCounter
        +"="
        +escapeString(G)
        +",OV="
        +cyCounter
        +"="
        +escapeString(ov);
    }
	
	private MetaDataCollection createCX1PreMetadata() {
		MetaDataCollection result = new MetaDataCollection ();
		if ( networkAttributes !=null && ! networkAttributes.getAttributes().isEmpty()) {
			MetaDataElement cl = new MetaDataElement(NetworkAttributesElement.ASPECT_NAME, "1.0");
			cl.setElementCount(Long.valueOf(networkAttributes.getAttributes().size()));
			result.add(cl);
		}
		Long nodeCount = null;
		for (CxMetadata m : metadataTable.values() ) {
			String aspectName = m.getName();
			// ignore some aspects 
			if ( !Cx2Network.cx2SpecialAspects.contains(aspectName)) {
				MetaDataElement e ;
				if (aspectName.equals(CxVisualProperty.ASPECT_NAME)) {
					e = new MetaDataElement(CyVisualPropertiesElement.ASPECT_NAME, "1.0");
					long cnt = 3;
					if ( metadataTable.get(CxNodeBypass.ASPECT_NAME) != null) {
						cnt += metadataTable.get(CxNodeBypass.ASPECT_NAME).getElementCount().longValue();
					}
					if (metadataTable.get(CxEdgeBypass.ASPECT_NAME) != null) {
						cnt += metadataTable.get(CxEdgeBypass.ASPECT_NAME).getElementCount().longValue();
					}
					e.setElementCount(Long.valueOf(cnt));
				} else 
					e = m.toMetaDataElement();
				result.add(e);
				if ( aspectName.contentEquals(CxNode.ASPECT_NAME))
					nodeCount = m.getElementCount(); 
			}
		}
		
		if ( hasLayout && nodeCount != null ) {
			MetaDataElement cl = new MetaDataElement(CartesianLayoutElement.ASPECT_NAME, "1.0");
			cl.setElementCount(nodeCount);
			result.add(cl);
		}	
		return result;
	}

	
	// escape ',' with double ',' 
    private static String escapeString(String str) {
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

	private static Object cvtVPfromRaw(String vpName, Object e) {
		if (vpName.equals("EDGE_LABEL_FONT_FACE") ||
					vpName.equals("NODE_LABEL_FONT_FACE")) 
				return FontFace.createFromMap((Map<String,String>)e);
		
		if ( vpName.equals("NODE_LABEL_POSITION")) 
				return LabelPosition.createFromMap((Map<String,Object>)e);
		
		if ( vpName.matches(VisualPropertyTable.imagePositionPattern)) 
				return ObjectPosition.createFromMap((Map<String,Object>)e);
	
		if ( vpName.equals("EDGE_CONTROL_POINTS")) {
				List<Map<String,Object>> rawPoints = (List<Map<String,Object>>)e;
				return  rawPoints.stream()
						.map(p -> { return EdgeControlPoint.createFromMap(p);})
						.collect(Collectors.toList());
		} 
		return e;
	}

    
}
