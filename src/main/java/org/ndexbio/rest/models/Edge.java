package org.ndexbio.rest.models;

import java.util.ArrayList;
import java.util.List;

import org.ndexbio.rest.domain.IEdge;
import org.ndexbio.rest.domain.INode;
import org.ndexbio.rest.domain.ITerm;

public class Edge extends NdexObject
{
    private Node _object;
    private BaseTerm _predicate;
    private Node _subject;
    private String _jdexId;
   
    /**************************************************************************
    * Default constructor.
    **************************************************************************/
    public Edge()
    {
        super();
    }
    
    /**************************************************************************
    * Populates the class (from the database) and removes circular references.
    * 
    * @param edge The Edge with source data.
    **************************************************************************/
    public Edge(IEdge edge)
    {
        super(edge);
        
        _subject = new Node(edge.getSubject());
        _predicate = new BaseTerm(edge.getPredicate());
        _object = new Node(edge.getObject());
    }
 
    public String getJdexId()
    {
        return _jdexId;
    }
    
    public void setJdexId(String jdexId)
    {
        _jdexId = jdexId;
    }
    
    public Node getObject()
    {
        return _object;
    }
    
    public void setObject(Node object)
    {
        _object = object;
    }
    
    public BaseTerm getPredicate()
    {
        return _predicate;
    }
    
    public void setPredicate(BaseTerm predicate)
    {
        _predicate = predicate;
    }
    
    public Node getSubject()
    {
        return _subject;
    }
    
    public void setSubject(Node subject)
    {
        _subject = subject;
    }
}
