package org.ndexbio.rest.models;

import org.ndexbio.rest.domain.INode;
import org.ndexbio.rest.domain.ITerm;

public class Node extends NdexObject
{
    //private String _jdexId;
    private String _name;
    private String _representsId;
    
    
    
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
        
        ITerm termRepresented = node.getRepresents();
        if (termRepresented != null){
            _representsId = termRepresented.getJdexId();
        }
    }
    
    
    public String getName()
    {
        return _name;
    }
    
    public void setName(String name)
    {
        _name = name;
    }
    
    public String getRepresentsId()
    {
        return _representsId;
    }
    
    public void setRepresentsId(String representsId)
    {
        _representsId = representsId;
    }
}
