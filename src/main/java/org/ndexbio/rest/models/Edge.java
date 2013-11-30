package org.ndexbio.rest.models;

import org.codehaus.jackson.annotate.JsonIgnoreProperties;
import org.ndexbio.rest.domain.IEdge;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;

@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(Include.NON_NULL)
public class Edge extends NdexObject
{
    private String _objectId;
    private String _predicateId;
    private String _subjectId;



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

    

    public void setS(String s)
    {
        this._subjectId = s;
    }

    public String getS()
    {
        return this._subjectId;
    }

    public void setP(String p)
    {
        this._predicateId = p;
    }

    public String getP()
    {
        return this._predicateId;
    }

    public void setO(String o)
    {
        this._objectId = o;
    }

    public String getO()
    {
        return this._objectId;
    }

    public String getObjectId()
    {
        return _objectId;
    }

    public void setObjectId(String objectId)
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
