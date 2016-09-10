package org.ndexbio.model.internal;

import java.io.IOException;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.Map;
import java.util.Set;

import org.cxio.aspects.datamodels.EdgeAttributesElement;
import org.cxio.aspects.datamodels.EdgesElement;
import org.cxio.aspects.datamodels.NetworkAttributesElement;
import org.cxio.aspects.datamodels.NodeAttributesElement;
import org.cxio.aspects.datamodels.NodesElement;
import org.cxio.aspects.writers.EdgeAttributesFragmentWriter;
import org.cxio.aspects.writers.EdgesFragmentWriter;
import org.cxio.aspects.writers.NetworkAttributesFragmentWriter;
import org.cxio.aspects.writers.NodeAttributesFragmentWriter;
import org.cxio.aspects.writers.NodesFragmentWriter;
import org.cxio.core.CxWriter;
import org.cxio.core.interfaces.AspectElement;
import org.cxio.core.interfaces.AspectFragmentWriter;
import org.cxio.metadata.MetaDataCollection;
import org.cxio.util.CxioUtil;
import org.ndexbio.common.cx.aspect.GeneralAspectFragmentWriter;
import org.ndexbio.model.cx.BELNamespaceElement;
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


/**
 * Note: the node and edge attribute tables are indexed on node and edge id respectively. 
 * However, the po field is still in the compact format ( a list) in the attributeElement object. So don't use that field in this model.
 * @author chenjing
 *
 */
public class CXNetwork {
	private MetaDataCollection metadata;
	
	private NamespacesElement namespaces;
	private Map<Long, NodesElement> nodes;
	private Map<Long, EdgesElement> edges;
	private Map<Long, CitationElement> citations;
	private Map<Long, SupportElement> supports;
	
	private Map<Long, Collection<NodeAttributesElement>> nodeAttributes;
	private Map<Long, Collection<EdgeAttributesElement>> edgeAttributes;
	
	private Collection <NetworkAttributesElement> networkAttributes;
	
	private Map<String,Map<Long,Collection<AspectElement>>> nodeAssociatedAspects;
	private Map<String,Map<Long, Collection<AspectElement>>> edgeAssociatedAspects;
	
	private Map<String, Collection<AspectElement>> opaqueAspects;
	
	public CXNetwork() {
		setMetadata(new MetaDataCollection());
		namespaces = new NamespacesElement();
		nodes = new HashMap<>();
		edges = new HashMap<>();
		citations = new HashMap<>();
		supports = new HashMap<>();
		
		nodeAttributes = new HashMap<> ();
		edgeAttributes = new HashMap<> ();
		
		networkAttributes = new ArrayList<> ();
		
		nodeAssociatedAspects = new HashMap<>();
		edgeAssociatedAspects = new HashMap<>();
		
		opaqueAspects = new HashMap<>();
		
	}
	
	public void addNode(NodesElement node) {
		nodes.put(node.getId(), node);
	}
	
	public void addEdge(EdgesElement edge) {
		edges.put(edge.getId(), edge);
	}
	
	public void addNetworkAttribute ( NetworkAttributesElement networkAttribute) {
		networkAttributes.add(networkAttribute);
	}
	
	public void addNodeAttribute( NodeAttributesElement nodeAttribute) {
		
		for(Long i : nodeAttribute.getPropertyOf()) {
			addNodeAttribute(i,nodeAttribute);
		}
	}
	
	public void addNodeAttribute( Long i, NodeAttributesElement nodeAttribute) {
		
		Collection<NodeAttributesElement> nodeAttrs = nodeAttributes.get(i);
		if ( nodeAttrs == null) {
				nodeAttrs = new LinkedList<>();
				nodeAttributes.put(i,nodeAttrs);
		}
		nodeAttrs.add(nodeAttribute);
	}
	
	public void addEdgeAttribute(EdgeAttributesElement edgeAttribute) {
		for ( Long i : edgeAttribute.getPropertyOf()) {
			addEdgeAttribute(i,edgeAttribute);
		}
	}
	
	public void addEdgeAttribute(Long i , EdgeAttributesElement edgeAttribute) {
			Collection<EdgeAttributesElement> edgeAttrs = edgeAttributes.get(i);
			if ( edgeAttrs == null) {
				edgeAttrs = new LinkedList<>();
				edgeAttributes.put(i, edgeAttrs);
			}
			edgeAttrs.add(edgeAttribute);
	}
	
	public void addSupport(SupportElement e) {
		supports.put(e.getId(), e);
	}
	
	public void addCitation(CitationElement e) {
		citations.put(e.getId(),e);
	}
	
	public void addOpapqueAspect(AspectElement e) {
		Collection<AspectElement> aspectElmts = opaqueAspects.get(e.getAspectName());
		if ( aspectElmts == null) {
			aspectElmts = new LinkedList<> ();
			opaqueAspects.put(e.getAspectName(), aspectElmts);
		}
		aspectElmts.add(e);
		
	}

	public MetaDataCollection getMetadata() {
		return metadata;
	}

	public void setMetadata(MetaDataCollection metadata) {
		this.metadata = metadata;
	}
	
	public void addNameSpace(String prefix, String uri) {
		namespaces.put(prefix, uri);
	}
	
	public void setNamespaces(NamespacesElement ns ) {
		this.namespaces = ns;
	}
	
	public Map<Long, EdgesElement> getEdges () {
		return this.edges;
	}
	
	private static CxWriter getNdexCXWriter(OutputStream out, boolean use_default_pretty_printer) throws IOException {
        CxWriter cxwtr = CxWriter.createInstance(out, use_default_pretty_printer);
        
        cxwtr.addAspectFragmentWriter(NodesFragmentWriter.createInstance());
        cxwtr.addAspectFragmentWriter(EdgesFragmentWriter.createInstance());
        cxwtr.addAspectFragmentWriter(NetworkAttributesFragmentWriter.createInstance());
        cxwtr.addAspectFragmentWriter(EdgeAttributesFragmentWriter.createInstance());
        cxwtr.addAspectFragmentWriter(NodeAttributesFragmentWriter.createInstance());
        
        GeneralAspectFragmentWriter cfw = new GeneralAspectFragmentWriter(CitationElement.ASPECT_NAME);
        cxwtr.addAspectFragmentWriter(cfw);
        cxwtr.addAspectFragmentWriter(new GeneralAspectFragmentWriter(SupportElement.ASPECT_NAME));
        cxwtr.addAspectFragmentWriter(new GeneralAspectFragmentWriter(NodeCitationLinksElement.ASPECT_NAME));
        cxwtr.addAspectFragmentWriter(new GeneralAspectFragmentWriter(EdgeCitationLinksElement.ASPECT_NAME));
        cxwtr.addAspectFragmentWriter(new GeneralAspectFragmentWriter(EdgeSupportLinksElement.ASPECT_NAME));
        cxwtr.addAspectFragmentWriter(new GeneralAspectFragmentWriter(NodeSupportLinksElement.ASPECT_NAME));
        cxwtr.addAspectFragmentWriter(new GeneralAspectFragmentWriter(NamespacesElement.ASPECT_NAME));
      
        return cxwtr;
	}
	
    private static void writeNdexAspectElementAsAspectFragment (CxWriter cxwtr, AspectElement element ) throws IOException {
    	cxwtr.startAspectFragment(element.getAspectName());
		cxwtr.writeAspectElement(element);
		cxwtr.endAspectFragment();
    }

	public void write(OutputStream out) throws IOException {
		
		CxWriter cxwtr = getNdexCXWriter(out, true);     
		
        cxwtr.addPreMetaData(metadata);
      try {
        cxwtr.start();
        
        //always export the context first.
        if ( namespaces.size() > 0 ) {
        	writeNdexAspectElementAsAspectFragment(cxwtr, namespaces);
        }
                
        // write name, desc and other properties;
        if ( networkAttributes.size()>0) {
        	cxwtr.startAspectFragment(NetworkAttributesElement.ASPECT_NAME);
        	for ( AspectElement e: this.networkAttributes)
        		cxwtr.writeAspectElement(e);
        	cxwtr.endAspectFragment();
        }
        
        cxwtr.startAspectFragment(NodesElement.ASPECT_NAME);
        for ( AspectElement e: this.nodes.values())
        	cxwtr.writeAspectElement(e);
        cxwtr.endAspectFragment();

        cxwtr.startAspectFragment(EdgesElement.ASPECT_NAME);
        for ( AspectElement e: this.edges.values())
        	cxwtr.writeAspectElement(e);
        cxwtr.endAspectFragment();
        
        if ( citations.size()>0) {
        	cxwtr.startAspectFragment(CitationElement.ASPECT_NAME);
        	for ( AspectElement e: this.citations.values())
        		cxwtr.writeAspectElement(e);
        	cxwtr.endAspectFragment();
        }	
        
        if ( supports.size() > 0 ) {
        	cxwtr.startAspectFragment(SupportElement.ASPECT_NAME);
        
        	for ( AspectElement e: this.supports.values())
        		cxwtr.writeAspectElement(e);
        	cxwtr.endAspectFragment();
        }
        
        if ( nodeAttributes.size() > 0 ) {
        	cxwtr.startAspectFragment(NodeAttributesElement.ASPECT_NAME);
        	for ( Map.Entry<Long, Collection<NodeAttributesElement>> entry: this.nodeAttributes.entrySet()) {
        		Long nodeId = entry.getKey();
        		for ( NodeAttributesElement e : entry.getValue()) {
        			ArrayList<Long> ids = new ArrayList<>(1);
        			ids.add(nodeId);
        			e.setPropertyOf(ids);
        			cxwtr.writeAspectElement(e);
        		}		
        	}
        	cxwtr.endAspectFragment();
        }
        
        if ( edgeAttributes.size() > 0) {
        	cxwtr.startAspectFragment(EdgeAttributesElement.ASPECT_NAME);
        	for ( Map.Entry<Long, Collection<EdgeAttributesElement>> entry: this.edgeAttributes.entrySet()) {
        		Long edgeId = entry.getKey();
        		for ( EdgeAttributesElement e : entry.getValue()) {
        			ArrayList<Long> ids = new ArrayList<>(1);
        			ids.add(edgeId);
        			e.setPropertyOf(ids);
        			cxwtr.writeAspectElement(e);
        		}		
        	}
        	cxwtr.endAspectFragment();
        }

        for (Map.Entry<String, Collection<AspectElement>> entry : this.opaqueAspects.entrySet()) {
        	 cxwtr.startAspectFragment(entry.getKey());
        	 for (AspectElement e : entry.getValue() ) {
              	cxwtr.writeAspectElement(e);
        	 }
             cxwtr.endAspectFragment();      	
        }
          
        
        cxwtr.end(true,"");
      } catch (Exception e ) {
    	  cxwtr.end(false, "Error: " + e.getMessage() );
    	  throw e;
      }

	}
}