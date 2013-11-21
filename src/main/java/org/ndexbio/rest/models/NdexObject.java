package org.ndexbio.rest.models;

import java.util.Date;
import org.ndexbio.rest.helpers.RidConverter;
import com.orientechnologies.orient.core.id.ORID;
import com.tinkerpop.frames.VertexFrame;

public abstract class NdexObject
{
    private String _id;
    private Date _createdDate;

    
    
    /**************************************************************************
    * Default constructor - initializes the created date. 
    **************************************************************************/
    public NdexObject()
    {
        _createdDate = new Date();
    }

    /**************************************************************************
    * Default constructor - initializes the created date. 
    **************************************************************************/
    public NdexObject(VertexFrame vf)
    {
        this();
        
        this.setId(resolveVertexId(vf));
    }

    
    
    public Date getCreatedDate()
    {
        return _createdDate;
    }

    public void setCreatedDate(Date createdDate)
    {
        this._createdDate = createdDate;
    }

    public String getId()
    {
        return _id;
    }

    public void setId(String id)
    {
        this._id = id;
    }

    
    
    
    /**************************************************************************
    * Converts the RID (OrientDB's ID) into the JID (which is safe for the
    * web). 
    **************************************************************************/
    protected String resolveVertexId(VertexFrame vf)
    {
        if (null == vf)
            return null;

        return RidConverter.convertToJid((ORID)vf.asVertex().getId());
    }
}