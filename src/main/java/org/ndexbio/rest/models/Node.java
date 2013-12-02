package org.ndexbio.rest.models;

import org.codehaus.jackson.annotate.JsonIgnoreProperties;
import org.ndexbio.rest.domain.INode;

@JsonIgnoreProperties(ignoreUnknown = true)
public class Node extends NdexObject
{
    private String name;
    private String represents;
    
    
    
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

        
        name = node.getName();
        
        if (node.getRepresents() != null)
            represents = node.getRepresents().getJdexId();
    }
    
    
    
    public String getName()
    {
        return name;
    }
    
    public void setName(String name)
    {
        this.name = name;
    }
    
    public String getRepresents()
    {
        return represents;
    }
    
    public void setRepresents(String representsId)
    {
        represents = representsId;
    }
}
