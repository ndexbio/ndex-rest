package org.ndexbio.rest.models;

import java.util.ArrayList;
import java.util.List;

import org.ndexbio.rest.domain.IEdge;
import org.ndexbio.rest.domain.INode;
import org.ndexbio.rest.domain.ITerm;

public class Edge extends NdexObject
{
	/*
	 * mod 25Nov2013 
	 * change from Node and BaseTerm aggregation to Node and BaseTerm references
	 */
    private String _objectId;
    private String _predicateId;
    private String _subjectId;
    //private String _jdexId;
   
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
        
        _subjectId = edge.getSubject().getJdexId();
        _predicateId = edge.getPredicate().getJdexId();
        _objectId = edge.getObject().getJdexId();
    }
 
    
    
    public String getObjectId()
    {
        return _objectId;
    }
    
    public void setObjectId(String  objectId)
    {
        _objectId = objectId;
    }
    
    public String getPredicateId()
    {
        return _predicateId;
    }
    
    public void setPredicateId(String predicateId)
    {
        _predicateId = predicateId;
    }
    
    public String getSubjectId()
    {
        return _subjectId;
    }
    
    public void setSubjectId(String subjectId)
    {
        _subjectId = subjectId;
    }
}
