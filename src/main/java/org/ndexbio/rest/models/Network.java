package org.ndexbio.rest.models;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.ndexbio.rest.domain.ICitation;
import org.ndexbio.rest.domain.IEdge;
import org.ndexbio.rest.domain.INamespace;
import org.ndexbio.rest.domain.INetwork;
import org.ndexbio.rest.domain.INode;
import org.ndexbio.rest.domain.ISupport;
import org.ndexbio.rest.domain.ITerm;

public class Network extends NdexObject
{
    private List<Citation> _citations;
    private List<Edge> _edges;
    private String _format;
    private List<Namespace> _namespaces;
    private List<Node> _nodes;
    private Map<String, String> _properties;
    private List<Support> _supports;
    private List<Term> _terms;
    private Integer _edgeCount;
    private Integer _nodeCount;

    /**************************************************************************
    * Default constructor.
    **************************************************************************/
    public Network()
    {
        super();
        
        _citations = new ArrayList<Citation>();
        _edges = new ArrayList<Edge>();
        _namespaces = new ArrayList<Namespace>();
        _nodes = new ArrayList<Node>();
        _properties = new HashMap<String, String>();
        _supports = new ArrayList<Support>();
        _terms = new ArrayList<Term>();
        _edgeCount = 0;
        _nodeCount = 0;
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
        
        _citations = new ArrayList<Citation>();
        _edges = new ArrayList<Edge>();
        _namespaces = new ArrayList<Namespace>();
        _nodes = new ArrayList<Node>();
        _properties = new HashMap<String, String>();
        _supports = new ArrayList<Support>();
        _terms = new ArrayList<Term>();

        _format = network.getFormat();
        _edgeCount = network.getNdexEdgeCount();
        _nodeCount = network.getNdexNodeCount();
        _properties.putAll(network.getProperties());
        
        if (loadEverything)
        {
            for (IEdge edge : network.getNdexEdges())
                _edges.add(new Edge(edge));
            
            for (INode node : network.getNdexNodes())
                _nodes.add(new Node(node));
            
            for (ITerm term : network.getTerms())
                _terms.add(new Term(term));
            
            for (ICitation citation : network.getCitations())
                _citations.add(new Citation(citation));
            
            for (INamespace namespace : network.getNamespaces())
                _namespaces.add(new Namespace(namespace));
            
            for (ISupport support : network.getSupports())
                _supports.add(new Support(support));
        }
    }
      
    public List<Citation> getCitations()
    {
        return _citations;
    }

    public void setCitations(List<Citation> citations)
    {
        _citations = citations;
    }
    
    public String getFormat()
    {
        return _format;
    }
    
    public void setFormat(String format)
    {
        _format = format;
    }
    
    public List<Namespace> getNamespaces()
    {
        return _namespaces;
    }
    
    public void setNamespaces(List<Namespace> namespaces)
    {
        _namespaces = namespaces;
    }
    
    public List<Edge> getNdexEdges()
    {
        return _edges;
    }
    
    public void setNdexEdges(List<Edge> edges)
    {
        _edges = edges;
    }

    public List<Node> getNdexNodes()
    {
        return _nodes;
    }
    
    public void setNdexNodes(List<Node> nodes)
    {
        _nodes = nodes;
    }
    
    public Map<String, String> getProperties()
    {
        return _properties;
    }

    public void setProperties(Map<String, String> properties)
    {
        _properties = properties;
    }

    public List<Support> getSupports()
    {
        return _supports;
    }

    public void setSupports(List<Support> supports)
    {
        _supports = supports;
    }

    public List<Term> getTerms()
    {
        return _terms;
    }

    public void setTerms(List<Term> terms)
    {
        _terms = terms;
    }

	public Integer getEdgeCount() {
		return _edgeCount;
	}

	public void setEdgeCount(Integer _edgeCount) {
		this._edgeCount = _edgeCount;
	}

	public Integer getNodeCount() {
		return _nodeCount;
	}

	public void setNodeCount(Integer _nodeCount) {
		this._nodeCount = _nodeCount;
	}
    
}
