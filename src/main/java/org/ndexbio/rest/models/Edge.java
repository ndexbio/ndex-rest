package org.ndexbio.rest.models;

import java.util.ArrayList;
import java.util.List;
import org.ndexbio.rest.domain.IEdge;
import org.ndexbio.rest.domain.INode;
import org.ndexbio.rest.domain.ITerm;

public class Edge
{
    private List<Node> _object;
    private List<Term> _predicate;
    private List<Node> _subject;
    
    
    
    /**************************************************************************
    * Default constructor.
    **************************************************************************/
    public Edge()
    {
        _object = new ArrayList<Node>();
        _predicate = new ArrayList<Term>();
        _subject = new ArrayList<Node>();
    }
    
    /**************************************************************************
    * Populates the class (from the database) and removes circular references.
    * 
    * @param edge The Edge with source data.
    **************************************************************************/
    public Edge(IEdge edge)
    {
        this();
        
        for (INode object : edge.getObject())
            _object.add(new Node(object));

        for (ITerm predicate : edge.getPredicate())
            _predicate.add(new Term(predicate));

        for (INode subject: edge.getSubject())
            _subject.add(new Node(subject));
    }
    
    
    public List<Node> getObject()
    {
        return _object;
    }
    
    public void setObject(List<Node> object)
    {
        _object = object;
    }
    
    public List<Term> getPredicate()
    {
        return _predicate;
    }
    
    public void setPredicate(List<Term> predicate)
    {
        _predicate = predicate;
    }
    
    public List<Node> getSubject()
    {
        return _subject;
    }
    
    public void setSubject(List<Node> subject)
    {
        _subject = subject;
    }
}
