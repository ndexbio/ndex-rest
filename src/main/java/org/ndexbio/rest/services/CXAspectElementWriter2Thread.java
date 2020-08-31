package org.ndexbio.rest.services;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.ndexbio.common.persistence.CX2NetworkLoader;
import org.ndexbio.common.persistence.CX2ToCXConverter;
import org.ndexbio.cx2.aspect.element.core.CxAttributeDeclaration;
import org.ndexbio.cx2.aspect.element.core.CxEdge;
import org.ndexbio.cx2.aspect.element.core.CxEdgeBypass;
import org.ndexbio.cx2.aspect.element.core.CxNetworkAttribute;
import org.ndexbio.cx2.aspect.element.core.CxNode;
import org.ndexbio.cx2.aspect.element.core.CxNodeBypass;
import org.ndexbio.cx2.aspect.element.core.CxVisualProperty;
import org.ndexbio.cx2.aspect.element.core.DeclarationEntry;
import org.ndexbio.cx2.aspect.element.cytoscape.VisualEditorProperties;
import org.ndexbio.cxio.aspects.datamodels.ATTRIBUTE_DATA_TYPE;
import org.ndexbio.cxio.aspects.datamodels.CartesianLayoutElement;
import org.ndexbio.cxio.aspects.datamodels.CyVisualPropertiesElement;
import org.ndexbio.cxio.aspects.datamodels.EdgeAttributesElement;
import org.ndexbio.cxio.aspects.datamodels.EdgesElement;
import org.ndexbio.cxio.aspects.datamodels.NetworkAttributesElement;
import org.ndexbio.cxio.aspects.datamodels.NodeAttributesElement;
import org.ndexbio.cxio.aspects.datamodels.NodesElement;
import org.ndexbio.cxio.core.AspectIterator;
import org.ndexbio.cxio.core.CXAspectWriter;
import org.ndexbio.cxio.core.OpaqueAspectIterator;
import org.ndexbio.model.exceptions.ObjectNotFoundException;
import org.ndexbio.rest.Configuration;
import org.slf4j.Logger;

import com.fasterxml.jackson.core.JsonParseException;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.ObjectMapper;


public class CXAspectElementWriter2Thread extends Thread {
		private OutputStream o;
		//private String networkId;
		private String aspect;
		private int limit;
		private String pathPrefix;
		private Logger logger;
		
		public CXAspectElementWriter2Thread (OutputStream out, String networkId, String aspectName, int limit, Logger accLogger) throws ObjectNotFoundException {
			
			
			o = out;
			//this.networkId = networkId;
			aspect = aspectName;
			this.limit = limit;
			checkAspectName();
			pathPrefix = Configuration.getInstance().getNdexRoot() + "/data/" + networkId 
    				+ "/" + CX2NetworkLoader.cx2AspectDirName + "/";
			this.logger = accLogger;
			
		}
		
		private void checkAspectName() throws ObjectNotFoundException {
			if (aspect.equals(CxAttributeDeclaration.ASPECT_NAME) ||
					aspect.equals(CxVisualProperty.ASPECT_NAME) ||
					aspect.equals(CxNodeBypass.ASPECT_NAME) ||
					aspect.equals(CxEdgeBypass.ASPECT_NAME) ||
					aspect.equals(VisualEditorProperties.ASPECT_NAME)) {
				throw new ObjectNotFoundException ("Aspect " + aspect + " is not found. It is only available in CX2.");
			}
		}
		
		
		@Override
		public void run() {

			try {
	
			    if ( aspect.equals(NodesElement.ASPECT_NAME)) {
				  writeNodes();
				  return;
			    }
			    
			    if ( aspect.equals(NodeAttributesElement.ASPECT_NAME)) {
			    	writeNodeAttributes();
			    	return ;
			    }
			    
			    if ( aspect.equals(EdgesElement.ASPECT_NAME)) {
			    	writeEdges();
			    	return;
			    }
			    
			    if ( aspect.equals(EdgeAttributesElement.ASPECT_NAME)) {
			    	writeEdgeAttributes();
			    	return;
			    }
			    
			    if ( aspect.equals(NetworkAttributesElement.ASPECT_NAME)) {
			    	writeNetworkAttributes();
			    	return;
			    }
			    
			    if ( aspect.equals(CyVisualPropertiesElement.ASPECT_NAME)) {
			    	writeCyVisualProperties();
			    	return;
			    }
			    
			    if (aspect.equals(CartesianLayoutElement.ASPECT_NAME)) {
			    	writeNodeCoordinates();
			    	return;
			    }
		
			    File aspF = new File ( pathPrefix + aspect);
				if ( !aspF.exists() ) {
					o.write("[]".getBytes());
					return;
				}
				try(FileInputStream in = new FileInputStream (aspF))	 {
				OpaqueAspectIterator asi = new OpaqueAspectIterator(in);
				try (CXAspectWriter wtr = new CXAspectWriter (o)) {
					for ( int i = 0 ; (limit <=0 ||i < limit) && asi.hasNext() ; i++) {
						wtr.writeCXElement(asi.next());
					}
				}
				}
			} catch (IOException e) {
					logger.error("IOException in CXAspectElementWriterThread: " + e.getMessage());
			} catch (Exception e1) {
				logger.error("Ndex exception: " + e1.getMessage());
			} finally {
				try {
					o.flush();
					o.close();
				} catch (IOException e) {
					logger.error("Failed to close outputstream in CXElementWriterWriterThread");
					e.printStackTrace();
				}
			} 
		}
		
		private Map<String,DeclarationEntry> getDeclarations(String cx2AspectName) throws JsonParseException, JsonMappingException, IOException {			
			File attrDeclF = new File ( pathPrefix + CxAttributeDeclaration.ASPECT_NAME);
			CxAttributeDeclaration[] declarations = null;
			if ( attrDeclF.exists() ) {
				ObjectMapper om = new ObjectMapper();
				declarations = om.readValue(attrDeclF, CxAttributeDeclaration[].class);
			}

			Map<String,DeclarationEntry> aspAttrDecls = null;
			if ( declarations != null)
				aspAttrDecls = declarations[0].getAttributesInAspect(cx2AspectName);
			
			return aspAttrDecls;
		}
		
		private void writeNodes() throws IOException {
			String fileName = pathPrefix + CxNode.ASPECT_NAME;
			
			Map<String,DeclarationEntry> nodeAttrDecls = getDeclarations(CxNode.ASPECT_NAME);
			
			File f = new File (fileName);
			if ( f.exists()) {
				try (CXAspectWriter wtr = new CXAspectWriter (o)) {
					try(FileInputStream in = new FileInputStream(f)) {
					AspectIterator<CxNode> it = new AspectIterator<>(in, CxNode.class);
					for ( int i = 0 ; (limit <=0 || i < limit) && it.hasNext() ; i++) {
						CxNode n = it.next();
						NodesElement node = new NodesElement(n.getId(),n.getNodeName(nodeAttrDecls),
								n.getNodeRepresents(nodeAttrDecls));
						wtr.writeCXElement(node);
					}	
					}
				}
			} else 
				o.write("[]".getBytes());
		}
		
		
		private void writeNodeCoordinates() throws IOException {
			String fileName = pathPrefix + CxNode.ASPECT_NAME;

			File f = new File(fileName);
			if (f.exists()) {
				try (CXAspectWriter wtr = new CXAspectWriter(o)) {
					try (FileInputStream in = new FileInputStream(f)) {
						try (AspectIterator<CxNode> it = new AspectIterator<>(in, CxNode.class)) {
							for (int i = 0; (limit <= 0 || i < limit) && it.hasNext(); i++) {
								CxNode n = it.next();
								if (n.getX() == null && n.getY() == null && n.getZ() == null)
									break;

								CartesianLayoutElement node = new CartesianLayoutElement(n.getId(), n.getX(), n.getY(),
										n.getZ());
								wtr.writeCXElement(node);
							}
						}
					}
				}
			} else
				o.write("[]".getBytes());
		}
		
		
		private void writeNodeAttributes() throws IOException {
			String fileName = pathPrefix + CxNode.ASPECT_NAME;
			
			Map<String,DeclarationEntry> nodeAttrDecls = getDeclarations(CxNode.ASPECT_NAME);
			
			File f = new File (fileName);
			if ( f.exists()) {
				try (CXAspectWriter wtr = new CXAspectWriter (o)) {
					try(FileInputStream in = new FileInputStream(f)) {
					AspectIterator<CxNode> it = new AspectIterator<>(in, CxNode.class);
					for ( int i = 0 ; (limit <=0 || i < limit) && it.hasNext() ; i++) {
						CxNode n = it.next();
						n.extendToFullNode(nodeAttrDecls);
						for ( Map.Entry<String,Object> attr : n.getAttributes().entrySet() ) {
							String attrName = attr.getKey();
							if ( !attrName.equals(CxNode.NAME) && ! attrName.equals(CxNode.REPRESENTS) ) {
								ATTRIBUTE_DATA_TYPE t = nodeAttrDecls.get(attrName).getDataType();
								NodeAttributesElement nodeAttr;
								if (t.isSingleValueType()) 
										nodeAttr = new NodeAttributesElement(null, n.getId(),
												attrName, attr.getValue().toString(),t);
								else {
									List<Object> v = (List<Object>)attr.getValue();
									List<String> vs = v.stream().map((Object vn )-> vn.toString()).collect(Collectors.toList());
									nodeAttr = new NodeAttributesElement(null, n.getId(), attrName, vs,t);
								}
								wtr.writeCXElement(nodeAttr);	
							}
						}
						
					}	
					}
				}
			} else 
				o.write("[]".getBytes());
		}
		
		private void writeNetworkAttributes() throws IOException {
			String fileName = pathPrefix + CxNetworkAttribute.ASPECT_NAME;
			
			Map<String,DeclarationEntry> netAttrDecls = getDeclarations(CxNetworkAttribute.ASPECT_NAME);
			
			File f = new File (fileName);
			if ( f.exists()) {
				try (CXAspectWriter wtr = new CXAspectWriter (o)) {
					try(FileInputStream in = new FileInputStream(f)) {
					AspectIterator<CxNetworkAttribute> it = new AspectIterator<>(in, CxNetworkAttribute.class);
					for ( int i = 0 ; (limit <=0 || i < limit) && it.hasNext() ; i++) {
						CxNetworkAttribute n = it.next();
						n.extendToFullNode(netAttrDecls);
						for ( Map.Entry<String,Object> attr : n.getAttributes().entrySet() ) {
							String attrName = attr.getKey();
							ATTRIBUTE_DATA_TYPE t = netAttrDecls.get(attrName).getDataType();
							NetworkAttributesElement nodeAttr;
							if (t.isSingleValueType()) 
								nodeAttr = new NetworkAttributesElement(null, attrName, attr.getValue().toString(),t);
							else {
								List<Object> v = (List<Object>)attr.getValue();
								List<String> vs = v.stream().map((Object vn )-> vn.toString()).collect(Collectors.toList());
								nodeAttr = new NetworkAttributesElement(null, attrName, vs,t);
							}
							wtr.writeCXElement(nodeAttr);	
							
						}
						
					}	
					}
				}
			} else 
				o.write("[]".getBytes());
		}
		
		
		private void writeEdgeAttributes() throws IOException {
			String fileName = pathPrefix + CxEdge.ASPECT_NAME;
			
			Map<String,DeclarationEntry> edgeAttrDecls = getDeclarations(CxEdge.ASPECT_NAME);
			
			File f = new File (fileName);
			if ( f.exists()) {
				try (CXAspectWriter wtr = new CXAspectWriter (o)) {
					try(FileInputStream in = new FileInputStream(f)) {
					AspectIterator<CxEdge> it = new AspectIterator<>(in, CxEdge.class);
					for ( int i = 0 ; (limit <=0 || i < limit) && it.hasNext() ; i++) {
						CxEdge n = it.next();
						n.extendToFullNode(edgeAttrDecls);
						for ( Map.Entry<String,Object> attr : n.getAttributes().entrySet() ) {
							String attrName = attr.getKey();
							if ( !attrName.equals(CxEdge.INTERACTION) ) {
								ATTRIBUTE_DATA_TYPE t = edgeAttrDecls.get(attrName).getDataType();
								EdgeAttributesElement edgeAttr;
								if (t.isSingleValueType()) 
										edgeAttr = new EdgeAttributesElement(null, n.getId(),
												attrName, attr.getValue().toString(),t);
								else {
									List<Object> v = (List<Object>)attr.getValue();
									List<String> vs = v.stream().map((Object vn )-> vn.toString()).collect(Collectors.toList());
									edgeAttr = new EdgeAttributesElement(null, n.getId(), attrName, vs,t);
								}
								wtr.writeCXElement(edgeAttr);	
							}
						}
						
					}	
					}
				}
			} else 
				o.write("[]".getBytes());
		}

		private void writeEdges() throws IOException {
			String fileName = pathPrefix + CxEdge.ASPECT_NAME;
			
			Map<String,DeclarationEntry> edgeAttrDecls = getDeclarations(CxEdge.ASPECT_NAME);
			
			File f = new File (fileName);
			if ( f.exists()) {
				try (CXAspectWriter wtr = new CXAspectWriter (o)) {
					try(FileInputStream in = new FileInputStream(f)) {
					AspectIterator<CxEdge> it = new AspectIterator<>(in, CxEdge.class);
					for ( int i = 0 ; (limit <=0 || i < limit) && it.hasNext() ; i++) {
						CxEdge n = it.next();
						EdgesElement node = new EdgesElement(n.getId(),n.getSource(),n.getTarget(),
								n.getInteraction(edgeAttrDecls));
						wtr.writeCXElement(node);
					}	
					}
				}
			} else 
				o.write("[]".getBytes());
		}
		
		private void writeCyVisualProperties() throws JsonParseException, JsonMappingException, IOException {
			String fileName = pathPrefix + CxVisualProperty.ASPECT_NAME;
			
			CxVisualProperty[] vp = null;
			File f = new File ( fileName);
			if ( f.exists() ) {
				ObjectMapper om = new ObjectMapper();
				vp = om.readValue(f, CxVisualProperty[].class);
			} else 
				o.write("[]".getBytes());
			
			fileName = pathPrefix + VisualEditorProperties.ASPECT_NAME;
			VisualEditorProperties[] evp = null;
			if ( f.exists() ) {
				ObjectMapper om = new ObjectMapper();
				evp = om.readValue(f, VisualEditorProperties[].class);
			}

			try (CXAspectWriter wtr = new CXAspectWriter (o)) {
				CyVisualPropertiesElement netDefault = CX2ToCXConverter.getDefaultNetworkVP(vp[0].getDefaultProps());
				wtr.writeCXElement(netDefault);

				
				
			/*	try(FileInputStream in = new FileInputStream(f)) {
					AspectIterator<CxEdge> it = new AspectIterator<>(in, CxEdge.class);
					for ( int i = 0 ; (limit <=0 || i < limit) && it.hasNext() ; i++) {
						CxEdge n = it.next();
						EdgesElement node = new EdgesElement(n.getId(),n.getSource(),n.getTarget(),
								n.getInteraction(edgeAttrDecls));
						wtr.writeCXElement(node);
					}	
					}
				} */
			} 
		}
		
}
