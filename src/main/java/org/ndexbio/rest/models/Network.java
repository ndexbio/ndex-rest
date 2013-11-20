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
import org.ndexbio.rest.helpers.RidConverter;
import com.orientechnologies.orient.core.id.ORID;

public class Network
{
    private List<Citation> _citations;
    private List<Edge> _edges;
    private String _format;
    private String _id;
    private List<Namespace> _namespaces;
    private List<Node> _nodes;
    private Map<String, String> _properties;
    private List<Support> _supports;
    private List<Term> _terms;

    

    /**************************************************************************
    * Default constructor.
    **************************************************************************/
    public Network()
    {
        _citations = new ArrayList<Citation>();
        _edges = new ArrayList<Edge>();
        _namespaces = new ArrayList<Namespace>();
        _nodes = new ArrayList<Node>();
        _properties = new HashMap<String, String>();
        _supports = new ArrayList<Support>();
        _terms = new ArrayList<Term>();
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
        this();
        
        _format = network.getFormat();
        _id = RidConverter.convertToJid((ORID)network.asVertex().getId());
        _properties.putAll(network.getProperties());
        
        for (ICitation citation : network.getCitations())
            _citations.add(new Citation(citation));
        
        for (INamespace namespace : network.getNamespaces())
            _namespaces.add(new Namespace(namespace));
        
        for (ISupport support : network.getSupports())
            _supports.add(new Support(support));

        if (loadEverything)
        {
            for (IEdge edge : network.getNdexEdges())
                _edges.add(new Edge(edge));
            
            for (INode node : network.getNodes())
                _nodes.add(new Node(node));
            
            for (ITerm term : network.getTerms())
                _terms.add(new Term(term));
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
    
    public List<Edge> getEdges()
    {
        return _edges;
    }
    
    public void setEdges(List<Edge> edges)
    {
        _edges = edges;
    }
    
    public String getFormat()
    {
        return _format;
    }
    
    public void setFormat(String format)
    {
        _format = format;
    }
    
    public String getId()
    {
        return _id;
    }
    
    public void setId(String id)
    {
        _id = id;
    }
    
    public List<Namespace> getNamespaces()
    {
        return _namespaces;
    }
    
    public void setNamespaces(List<Namespace> namespaces)
    {
        _namespaces = namespaces;
    }
    
    public List<Node> getNodes()
    {
        return _nodes;
    }
    
    public void setNodes(List<Node> nodes)
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
}
