package org.ndexbio.model.internal;

import java.io.IOException;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.Map;
import java.util.Map.Entry;
import java.util.TreeMap;

import org.ndexbio.cxio.aspects.datamodels.EdgeAttributesElement;
import org.ndexbio.cxio.aspects.datamodels.EdgesElement;
import org.ndexbio.cxio.aspects.datamodels.NetworkAttributesElement;
import org.ndexbio.cxio.aspects.datamodels.NodeAttributesElement;
import org.ndexbio.cxio.aspects.datamodels.NodesElement;
import org.ndexbio.cxio.aspects.writers.EdgeAttributesFragmentWriter;
import org.ndexbio.cxio.aspects.writers.EdgesFragmentWriter;
import org.ndexbio.cxio.aspects.writers.GeneralAspectFragmentWriter;
import org.ndexbio.cxio.aspects.writers.NetworkAttributesFragmentWriter;
import org.ndexbio.cxio.aspects.writers.NodeAttributesFragmentWriter;
import org.ndexbio.cxio.aspects.writers.NodesFragmentWriter;
import org.ndexbio.cxio.aspects.writers.VisualPropertiesFragmentWriter;
import org.ndexbio.cxio.core.CxWriter;
import org.ndexbio.cxio.core.interfaces.AspectElement;
import org.ndexbio.cxio.metadata.MetaDataCollection;
import org.ndexbio.model.cx.CitationElement;
import org.ndexbio.model.cx.EdgeCitationLinksElement;
import org.ndexbio.model.cx.EdgeSupportLinksElement;
import org.ndexbio.model.cx.FunctionTermElement;
import org.ndexbio.model.cx.NamespacesElement;
import org.ndexbio.model.cx.NodeCitationLinksElement;
import org.ndexbio.model.cx.NodeSupportLinksElement;
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
	
	//function term, node/edge citation and supports might use these 2 tables.
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
			addNodeAttribute(nodeAttribute.getPropertyOf(),nodeAttribute);
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
			addEdgeAttribute(edgeAttribute.getPropertyOf(),edgeAttribute);
		
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
	
	public void addNodeAssociatedAspectElement(Long nodeId, AspectElement elmt) {
		addAssciatatedAspectElement(nodeAssociatedAspects, nodeId, elmt);
	}

	public void addEdgeAssociatedAspectElement(Long edgeId, AspectElement elmt) {
		addAssciatatedAspectElement(edgeAssociatedAspects, edgeId, elmt);	
	}
	
	private static void addAssciatatedAspectElement(Map<String,Map<Long,Collection<AspectElement>>> table, Long id, AspectElement elmt) {
		Map<Long,Collection<AspectElement>> aspectElements = table.get(elmt.getAspectName());
		if ( aspectElements == null) {
			aspectElements = new TreeMap<> ();
			table.put(elmt.getAspectName(), aspectElements);
		}
		Collection<AspectElement> elmts = aspectElements.get(id);
		
		if (elmts == null) {
			elmts = new ArrayList<>();
			aspectElements.put(id, elmts);
		}
		elmts.add(elmt);
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
        cxwtr.addAspectFragmentWriter(VisualPropertiesFragmentWriter.createInstance());
        
        GeneralAspectFragmentWriter cfw = new GeneralAspectFragmentWriter(CitationElement.ASPECT_NAME);
        cxwtr.addAspectFragmentWriter(cfw);
        cxwtr.addAspectFragmentWriter(new GeneralAspectFragmentWriter(SupportElement.ASPECT_NAME));
        cxwtr.addAspectFragmentWriter(new GeneralAspectFragmentWriter(FunctionTermElement.ASPECT_NAME));
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
    
   /* 
    private MetaDataCollection computeMetadatqCollection() {
    	MetaDataCollection mdc = new MetaDataCollection();
    	
    	
    	return mdc;
    } */

	public void write(OutputStream out) throws IOException {
		
		CxWriter cxwtr = getNdexCXWriter(out, false);     
		
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
        			e.setPropertyOf(nodeId);
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
        			e.setPropertyOf(edgeId);
        			cxwtr.writeAspectElement(e);
        		}		
        	}
        	cxwtr.endAspectFragment();
        }

        for (Entry<String,Map<Long,Collection<AspectElement>>>  aspect : nodeAssociatedAspects.entrySet()) {
        	Map<Long, Collection<AspectElement>> elementMap = aspect.getValue();
        	if ( ! elementMap.isEmpty()) {
        		cxwtr.startAspectFragment(aspect.getKey());
        		for (Collection<AspectElement> eCollection : elementMap.values() ) {
        			for ( AspectElement e : eCollection)
        				cxwtr.writeAspectElement(e);
        		}
        		cxwtr.endAspectFragment();    
        	}
        }
        
        for (Entry<String,Map<Long,Collection<AspectElement>>>  aspect : edgeAssociatedAspects.entrySet()) {
        	Map<Long, Collection<AspectElement>> elementMap = aspect.getValue();
        	if ( ! elementMap.isEmpty()) {
        		cxwtr.startAspectFragment(aspect.getKey());
        		for (Collection<AspectElement> eCollection : elementMap.values() ) {
        			for ( AspectElement e : eCollection)
        				cxwtr.writeAspectElement(e);
        		}
        		cxwtr.endAspectFragment();    
        	}
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
    	  e.printStackTrace();
    	  cxwtr.end(false, "Error: " + e.getMessage() );
    	  throw e;
      }

	}
}
