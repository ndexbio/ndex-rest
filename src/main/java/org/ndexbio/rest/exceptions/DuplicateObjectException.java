package org.ndexbio.rest.exceptions;

import com.orientechnologies.orient.server.network.protocol.http.OHttpUtils;

public class DuplicateObjectException extends NdexException
{
    private static final long serialVersionUID = 1L;

    
    
    public DuplicateObjectException(String message)
    {
        super(message);
    }

    public DuplicateObjectException(String message, Throwable cause)
    {
        super(message, cause);
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
