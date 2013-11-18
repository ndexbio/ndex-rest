package org.ndexbio.rest.exceptions;

import com.orientechnologies.orient.server.network.protocol.http.OHttpUtils;

public class ObjectNotFoundException extends NdexException
{
    private static final long serialVersionUID = 1L;

    
    
    public ObjectNotFoundException(String message)
    {
        super(message);
    }

    public ObjectNotFoundException(String message, Throwable cause)
    {
        super(message, cause);
    }
    
    public ObjectNotFoundException(String objectType, Object objectId)
    {
        super(objectType + " with ID: " + objectId.toString() + " doesn't exist.");
    }

    
    
    @Override
    public int getHttpStatus()
    {
        return OHttpUtils.STATUS_BADREQ_CODE;
    }

    @Override
    public String getHttpStatusDescription()
    {
        
        return OHttpUtils.STATUS_BADREQ_DESCRIPTION;
    }
}
