package org.ndexbio.rest.models;

import java.util.ArrayList;
import java.util.List;
import org.ndexbio.rest.domain.INode;
import org.ndexbio.rest.domain.ITerm;

public class Node extends NdexObject
{
    private String _jdexId;
    private String _name;
    private List<Term> _represents;
    
    
    
    /**************************************************************************
    * Default constructor.
    **************************************************************************/
    public Node()
    {
        super();
        
        _represents = new ArrayList<Term>();
    }
    
    /**************************************************************************
    * Populates the class (from the database) and removes circular references.
    * 
    * @param node The Node with source data.
    **************************************************************************/
    public Node(INode node)
    {
        super(node);
        
        _represents = new ArrayList<Term>();

        _jdexId = node.getJdexId();
        _name = node.getName();
        
        for (ITerm term : node.getRepresents())
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
