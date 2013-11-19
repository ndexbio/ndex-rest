package org.ndexbio.rest.models;

import java.util.ArrayList;
import java.util.List;
import org.ndexbio.rest.domain.XEdge;
import org.ndexbio.rest.domain.XNode;
import org.ndexbio.rest.domain.XTerm;

public class Edge
{
    private List<Node> _object;
    private List<Term> _predicate;
    private List<Node> _subject;
    
    
    
    public Edge()
    {
        _object = new ArrayList<Node>();
        _predicate = new ArrayList<Term>();
        _subject = new ArrayList<Node>();
    }
    
    public Edge(XEdge edge)
    {
        this();
        
        for (XNode object : edge.getObject())
            _object.add(new Node(object));

        for (XTerm predicate : edge.getPredicate())
            _predicate.add(new Term(predicate));

        for (XNode subject: edge.getSubject())
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
