package org.ndexbio.rest.models;

import java.util.List;
import org.ndexbio.rest.domain.XNode;
import org.ndexbio.rest.domain.XTerm;

public class Node
{
    private String _jdexId;
    private String _name;
    private List<Term> _represents;
    
    
    
    public Node()
    {
    }
    
    public Node(XNode node)
    {
        _jdexId = node.getJdexId();
        _name = node.getName();
        
        for (XTerm term : node.getRepresents())
            _represents.add(new Term(term));
    }
    
    
    
    public String getJdexId()
    {
        return _jdexId;
    }
    
    public void setJdexId(String jdexId)
    {
        _jdexId = jdexId;
    }
    
    public String getName()
    {
        return _name;
    }
    
    public void setName(String name)
    {
        _name = name;
    }
    
    public List<Term> getRepresents()
    {
        return _represents;
    }
    
    public void setRepresents(List<Term> represents)
    {
        _represents = represents;
    }
}
