package org.ndexbio.rest.models;

import org.ndexbio.rest.domain.IRequest;

public class Request extends NdexObject
{
    private String fromId;
    private String toId;
    private String aboutId;
    private String message;
    private String requestType;

    

    /**************************************************************************
    * Default constructor.
    **************************************************************************/
    public Request()
    {
        super();
    }
    
    /**************************************************************************
    * Populates the class (from the database) and removes circular references.
    * 
    * @param request The Request with source data.
    **************************************************************************/
    public Request(IRequest request)
    {
        super(request);
        
        this.setFromId(resolveVertexId(request.getFromAccount()));
        this.setToId(resolveVertexId(request.getToAccount()));
        this.setAboutId(resolveVertexId(request.getAbout()));
        this.setMessage(request.getMessage());
        this.setRequestType(request.getRequestType());
        this.setCreatedDate(request.getRequestTime());
    }

    
    
    public String getFromId()
    {
        return fromId;
    }

    public void setFromId(String fromId)
    {
        this.fromId = fromId;
    }

    public String getToId()
    {
        return toId;
    }

    public void setToId(String toId)
    {
        this.toId = toId;
    }

    public String getAboutId()
    {
        return aboutId;
    }

    public void setAboutId(String aboutId)
    {
        this.aboutId = aboutId;
    }

    public String getMessage()
    {
        return message;
    }

    public void setMessage(String message)
    {
        this.message = message;
    }

    public String getRequestType()
    {
        return requestType;
    }

    public void setRequestType(String requestType)
    {
        this.requestType = requestType;
    }
}
