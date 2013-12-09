package org.ndexbio.rest.models;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import org.ndexbio.rest.domain.INode;

@JsonIgnoreProperties(ignoreUnknown = true)
public class Node extends NdexObject
{
    private String _name;
    private String _represents;
    
    
    
    /**************************************************************************
    * Default constructor.
    **************************************************************************/
    public Node()
    {
        super();
    }
    
    /**************************************************************************
    * Populates the class (from the database) and removes circular references.
    * 
    * @param node The Node with source data.
    **************************************************************************/
    public Node(INode node)
    {
        super(node);

        
        _name = node.getName();
        
        if (node.getRepresents() != null)
            _represents = node.getRepresents().getJdexId();
    }
    
    
    
    public String getName()
    {
        return _name;
    }
    
    public void setName(String name)
    {
        _name = name;
    }
    
    public String getRepresents()
    {
        return _represents;
    }
    
    public void setRepresents(String representsId)
    {
        _represents = representsId;
    }
}
