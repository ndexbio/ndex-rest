package org.ndexbio.rest.models;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
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
    private Map<String, Citation> _citations;
    private Map<String, Edge> _edges;
    private String _format;
    private Map<String,Namespace> _namespaces;
    private Map<String,Node> _nodes;
    private Map<String, String> _properties;
    private Map<String, Support> _supports;
    private Map<String, Term> _terms;
    private Integer _edgeCount;
    private Integer _nodeCount;

    /**************************************************************************
    * Default constructor.
    **************************************************************************/
    public Network()
    {
        super();
        this.initMaps();
        
        _edgeCount = 0;
        _nodeCount = 0;
    }
    
    private void initMaps() {
    	_citations = new HashMap<String, Citation>();
        _edges = new HashMap<String,Edge>();
        _namespaces = new HashMap<String,Namespace>();
        _nodes = new HashMap<String,Node>();
        _properties = new HashMap<String, String>();
        _supports = new HashMap<String, Support>();
        _terms = new HashMap<String,Term>();
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
        _format = network.getFormat();
        _edgeCount = network.getNdexEdgeCount();
        _nodeCount = network.getNdexNodeCount();
        _properties.putAll(network.getProperties());
        
        if (loadEverything)
        {
            for (IEdge edge : network.getNdexEdges())
                _edges.put(edge.getJdexId(), new Edge(edge));
            
            for (INode node : network.getNdexNodes())
                _nodes.put(node.getJdexId(), new Node(node));
            
            for (ITerm term : network.getTerms())
            	if( term instanceof IBaseTerm) {
            		_terms.put(term.getJdexId(), new BaseTerm((IBaseTerm)term));
            	} else if ( term instanceof IFunctionTerm) {
            		_terms.put(term.getJdexId(), new FunctionTerm((IFunctionTerm) term));
            	}
            
            for (ICitation citation : network.getCitations())
                _citations.put(citation.getJdexId(), new Citation(citation));
            
            for (INamespace namespace : network.getNamespaces())
                _namespaces.put(namespace.getJdexId(), new Namespace(namespace));
            
            for (ISupport support : network.getSupports())
                _supports.put(support.getJdexId(), new Support(support));
        }
    }
      
    public Map<String,Citation> getCitations()
    {
        return _citations;
    }

    public void setCitations(Map<String,Citation> citations)
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
    
    public Map<String,Namespace> getNamespaces()
    {
        return _namespaces;
    }
    
    public void setNamespaces(Map<String,Namespace> namespaces)
    {
        _namespaces = namespaces;
    }
    
    public Map<String,Edge> getNdexEdges()
    {
        return _edges;
    }
    
    public void setNdexEdges(Map<String,Edge> edges)
    {
        _edges = edges;
    }

    public Map<String,Node> getNdexNodes()
    {
        return _nodes;
    }
    
    public void setNdexNodes(Map<String,Node> nodes)
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

    public Map<String,Support> getSupports()
    {
        return _supports;
    }

    public void setSupports(Map<String,Support> supports)
    {
        _supports = supports;
    }

    public Map<String,Term> getTerms()
    {
        return _terms;
    }

    public void setTerms(Map<String,Term> terms)
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
