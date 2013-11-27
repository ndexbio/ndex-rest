package org.ndexbio.rest.models;

import java.util.HashMap;
import java.util.Map;

import org.ndexbio.rest.domain.IBaseTerm;
import org.ndexbio.rest.domain.ICitation;
import org.ndexbio.rest.domain.IEdge;
import org.ndexbio.rest.domain.IFunctionTerm;
import org.ndexbio.rest.domain.INamespace;
import org.ndexbio.rest.domain.INetwork;
import org.ndexbio.rest.domain.INode;
import org.ndexbio.rest.domain.ISupport;
import org.ndexbio.rest.domain.ITerm;

public class Network extends NdexObject
{
	/*
	 * mod 25Nov2013
	 * modify collection fields to be Map<String,model_type> using the JdexId as the map key
	 * this will support Jackson object mapping
	 */
    private Map<String, Citation> citations;
    private Map<String, Edge> edges;
    private String format;
    private Map<String,Namespace> namespaces;
    private Map<String,Node> nodes;
    private Map<String, String> properties;
    private Map<String, Support> supports;
    private Map<String, Term> terms;
    private Map<String,NodeType> nodeTypes;
    private Integer edgeCount;
    private Integer nodeCount;

    /**************************************************************************
    * Default constructor.
    **************************************************************************/
    public Network()
    {
        super();
        this.initMaps();
        
        edgeCount = 0;
        nodeCount = 0;
    }
    
    private void initMaps() {
    	citations = new HashMap<String, Citation>();
        edges = new HashMap<String,Edge>();
        namespaces = new HashMap<String,Namespace>();
        nodes = new HashMap<String,Node>();
        properties = new HashMap<String, String>();
        supports = new HashMap<String, Support>();
        terms = new HashMap<String,Term>();
    }
    
    /**************************************************************************
    * Populates the class (from the database) and removes circular references.
    * Doesn't load Edges, Nodes, or Terms.
    * 
    * @param network The Network with source data.
    **************************************************************************/
    public Network(INetwork network)
    {
        this(network, false);
    }
    
    /**************************************************************************
    * Populates the class (from the database) and removes circular references.
    * 
    * @param network        The Network with source data.
    * @param loadEverything True to load Edges, Nodes, and Terms, false to
    *                       exclude them.
    **************************************************************************/
    public Network(INetwork network, boolean loadEverything)
    {
    	
        super(network);
       
        this.initMaps();
        format = network.getFormat();
        edgeCount = network.getNdexEdgeCount();
        nodeCount = network.getNdexNodeCount();
        properties.putAll(network.getProperties());
        
        if (loadEverything)
        {
            for (IEdge edge : network.getNdexEdges())
                edges.put(edge.getJdexId(), new Edge(edge));
            
            for (INode node : network.getNdexNodes())
                nodes.put(node.getJdexId(), new Node(node));
            
            for (ITerm term : network.getTerms())
            	if( term instanceof IBaseTerm) {
            		terms.put(term.getJdexId(), new BaseTerm((IBaseTerm)term));
            	} else if ( term instanceof IFunctionTerm) {
            		terms.put(term.getJdexId(), new FunctionTerm((IFunctionTerm) term));
            	}
            
            for (ICitation citation : network.getCitations())
                citations.put(citation.getJdexId(), new Citation(citation));
            
            for (INamespace namespace : network.getNamespaces())
                namespaces.put(namespace.getJdexId(), new Namespace(namespace));
            
            for (ISupport support : network.getSupports())
                supports.put(support.getJdexId(), new Support(support));
        }
    }
      
    public Map<String, NodeType> getNodeTypes() {
		return nodeTypes;
	}

	public void setNodeTypes(Map<String, NodeType> nodeTypes) {
		this.nodeTypes = nodeTypes;
	}

	public Map<String,Citation> getCitations()
    {
        return citations;
    }

    public void setCitations(Map<String,Citation> citations)
    {
        this.citations = citations;
    }
    
    public String getFormat()
    {
        return format;
    }
    
    public void setFormat(String format)
    {
        this.format = format;
    }
    
    public Map<String,Namespace> getNamespaces()
    {
        return namespaces;
    }
    
    public void setNamespaces(Map<String,Namespace> namespaces)
    {
        this.namespaces = namespaces;
    }
    
    public Map<String,Edge> getEdges()
    {
        return edges;
    }
    
    public void setEdges(Map<String,Edge> edges)
    {
        this.edges = edges;
    }

    public Map<String,Node> getNodes()
    {
        return nodes;
    }
    
    public void setNodes(Map<String,Node> nodes)
    {
        this.nodes = nodes;
    }
    
    public Map<String, String> getProperties()
    {
        return properties;
    }

    public void setProperties(Map<String, String> properties)
    {
        this.properties = properties;
    }

    public Map<String,Support> getSupports()
    {
        return supports;
    }

    public void setSupports(Map<String,Support> supports)
    {
        this.supports = supports;
    }

    public Map<String,Term> getTerms()
    {
        return terms;
    }

    public void setTerms(Map<String,Term> terms)
    {
        this.terms = terms;
    }

	public Integer getEdgeCount() {
		return edgeCount;
	}

	public void setEdgeCount(Integer _edgeCount) {
		this.edgeCount = _edgeCount;
	}

	public Integer getNodeCount() {
		return nodeCount;
	}

	public void setNodeCount(Integer _nodeCount) {
		this.nodeCount = _nodeCount;
	}
    
}
