package org.ndexbio.common.persistence;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.SortedMap;
import java.util.stream.Collectors;

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
import org.ndexbio.cx2.aspect.element.core.VPMappingType;
import org.ndexbio.cx2.aspect.element.core.VisualPropertyMapping;
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
	
	public static final List<String> cx2SpecialAspects = Arrays.asList(
			CxAttributeDeclaration.ASPECT_NAME,CxEdgeBypass.ASPECT_NAME,
			CxNodeBypass.ASPECT_NAME, VisualEditorProperties.ASPECT_NAME,
			CxNetworkAttribute.ASPECT_NAME);
		
	private static final String VM_COL = "COL=";
	private static final String VM_TYPE = "T=";
    private final  static char COMMA = ',';

	
	private String pathPrefix;
	private CxAttributeDeclaration attrDeclarations;
	private	Map<String, CxMetadata> metadataTable;
	private boolean hasLayout;
	private CxNetworkAttribute networkAttributes; 
	
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
	}
	
	void convert() throws FileNotFoundException, IOException, NdexException {
		
		nodeAttrCount = 0;
		edgeAttrCount = 0;
		networkAttrCount = 0;
		
		String aspectPath = pathPrefix + CX2NetworkLoader.cx2AspectDirName + "/";
		
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
				
				CX2ToCXVisualPropertyConverter vpCvtr = new CX2ToCXVisualPropertyConverter();
				writer.startAspectFragment(CyVisualPropertiesElement.ASPECT_NAME);
				writer.openFragment();

				File vsFile = new File(aspectPath + CxVisualProperty.ASPECT_NAME);
				
				CxVisualProperty[] vPs = om.readValue(vsFile, CxVisualProperty[].class); 
				
				DefaultVisualProperties defaultVPs = vPs[0].getDefaultProps();
				
				//convert network default VPs
				Map<String, Object> networkDFVPs = defaultVPs.getNetworkProperties();
				CyVisualPropertiesElement vp = new CyVisualPropertiesElement ("network");
				vp.setProperties(vpCvtr.convertNetworkVPs(networkDFVPs));
				writer.writeElement(vp);
				
				// get the dependency table
				Map<String,Object> vep = null;
				File vsEditorPropsFile = new File ( aspectPath + VisualEditorProperties.ASPECT_NAME);
				if (vsEditorPropsFile.exists()) {
					VisualEditorProperties[] vepr = om.readValue(vsEditorPropsFile, VisualEditorProperties[].class);
					vep = vepr[0].getProperties();
				}
				
				// convert node default
				Map<String,Object> nodeDefaultVPs =defaultVPs.getNodeProperties();
				vp = new CyVisualPropertiesElement ("nodes:default");
				vp.setProperties(vpCvtr.convertEdgeOrNodeVPs(nodeDefaultVPs));
				
				// set node dependency
				boolean nodeSizeLocked = false;
				if ( vep != null ) {
					SortedMap<String, String> nodeVPDependencies = vp.getDependencies();
					if ( vep.get("nodeSizeLocked") != null ) {
						nodeSizeLocked = ((Boolean)vep.get("nodeSizeLocked")).booleanValue();
						nodeVPDependencies.put("nodeSizeLocked", Boolean.toString(nodeSizeLocked));
						vp.getProperties().put("NODE_SIZE", vp.getProperties().get("NODE_WIDTH"));
					}
					if ( vep.get("nodeCustomGraphicsSizeSync") !=null ) {
						nodeVPDependencies.put("nodeCustomGraphicsSizeSync", 
								vep.get("nodeCustomGraphicsSizeSync").toString()); 
					}
				}
				
				//set node mapping
				Map<String,VisualPropertyMapping> nodeMappings = vPs[0].getNodeMappings();
				if ( nodeSizeLocked) {
					VisualPropertyMapping m = nodeMappings.get("NODE_WIDTH");
					if ( m != null)
						nodeMappings.put("NODE_SIZE", m);
				}
				convertMapping(vp, nodeMappings, CxNode.ASPECT_NAME);
				
				
				writer.writeElement(vp);
				
				
				// convert edge default
				Map<String,Object> edgeDefaultVPs =defaultVPs.getEdgeProperties();
				vp = new CyVisualPropertiesElement ("edge:default");
				vp.setProperties(vpCvtr.convertEdgeOrNodeVPs(edgeDefaultVPs));
				
				// set edge dependency
				
				boolean arrowColorMatchesEdge = false;
				if ( vep != null ) {
					SortedMap<String, String> edgeVPDependencies = vp.getDependencies();
					if ( vep.get("arrowColorMatchesEdge") != null ) {
						arrowColorMatchesEdge = ((Boolean)vep.get("arrowColorMatchesEdge")).booleanValue();
						edgeVPDependencies.put("arrowColorMatchesEdge", Boolean.toString(arrowColorMatchesEdge));
						vp.getProperties().put("EDGE_PAINT", vp.getProperties().get("EDGE_STROKE_UNSELECTED_PAINT"));	
					}
				}
				
				//set edge mapping
				Map<String,VisualPropertyMapping> edgeMappings = vPs[0].getEdgeMappings();
				
				// add edge_paint mapping if dependency flag exists.
				if ( arrowColorMatchesEdge) {
					VisualPropertyMapping m = edgeMappings.get("EDGE_STROKE_UNSELECTED_PAINT");
					if ( m != null)
						edgeMappings.put("EDGE_PAINT", m);
				}
				
				convertMapping(vp, edgeMappings, CxEdge.ASPECT_NAME);
				
				
				writer.writeElement(vp);
				
				
				writer.closeFragment();
				writer.endAspectFragment();
				
			}
			
			//write other opaque aspects.
			for (String aspectName: this.metadataTable.keySet()) {
				if ( !cx2SpecialAspects.contains(aspectName) && !aspectName.equals(CxNode.ASPECT_NAME)
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
	
	
    private void convertMapping(CyVisualPropertiesElement vp, Map<String,VisualPropertyMapping> mappings, String aspectName) throws NdexException {
		for ( Map.Entry<String, VisualPropertyMapping> cx2Mapping: mappings.entrySet()) {
			String vpName = cx2Mapping.getKey();
			VisualPropertyMapping mapping = cx2Mapping.getValue();
			String colName = mapping.getMappingDef().getAttributeName();
			ATTRIBUTE_DATA_TYPE type = this.attrDeclarations.getAttributesInAspect(aspectName)
						.get(colName).getDataType();
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
		        vp.putMapping(vpName, VPMappingType.PASSTHROUGH.toString(), sb.toString());
				break;
			}
			case DISCRETE:
				for ( Map<String,Object> m : mapping.getMappingDef().getMapppingList() ) {
					  sb.append(",K=");
	                  sb.append(counter);
	                  sb.append("=");
	                  sb.append(escapeString(m.get("v").toString()));
	                  sb.append(",V=");
	                  sb.append(counter);
	                  sb.append("=");
	                  sb.append(escapeString(m.get("vp").toString()));
	                  counter++;
				}
		        vp.putMapping(vpName, VPMappingType.DISCRETE.toString(), sb.toString());
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
					    L = maxVP.toString();
					    ov = maxV.toString();
					    if ( includeMax.booleanValue()) 
					    	E = L;
					} else {  // middle ranges and the last range
						G = minVP.toString();
						if (includeMin.booleanValue())
							E=G;
						
						// create the mapping point
						sb.append(",L=");
		                sb.append(cyCounter);
		                sb.append("=");
		                sb.append(escapeString(L));
		                sb.append(",E=");
		                sb.append(cyCounter);
		                sb.append("=");
		                sb.append(escapeString(E));
		                sb.append(",G=");
		                sb.append(cyCounter);
		                sb.append("=");
		                sb.append(escapeString(G));
		                sb.append(",OV=");
		                sb.append(cyCounter);
		                sb.append("=");
		                sb.append(escapeString(ov));
		                cyCounter++;
		                
		                // prepare for the next point
		                if ( maxV != null) {
		                	ov = maxV.toString();
		                	L = maxVP.toString();
		                	if (includeMax.booleanValue())
		                		E = L;
		                	else 
		                		E = null;
		                }	
		                
					}
					counter++;
				}	
		        vp.putMapping(vpName, VPMappingType.CONTINUOUS.toString(), sb.toString());

				break;
			}
			default:
				break;		
			}		
			
		}
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
			if ( !cx2SpecialAspects.contains(aspectName)) {
				MetaDataElement e = 
						aspectName.equals(CxVisualProperty.ASPECT_NAME) ?
								new MetaDataElement(CyVisualPropertiesElement.ASPECT_NAME, "1.0") :
								m.toMetaDataElement();
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

}
