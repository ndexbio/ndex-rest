package org.ndexbio.rest.models;

import java.util.List;
import java.util.Map;

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
    private String _title;

    

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
    
    public String getTitle()
    {
        return _title;
    }
    
    public void setTitle(String title)
    {
        _title = title;
    }
}
