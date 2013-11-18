package org.ndexbio.rest.exceptions;

import com.orientechnologies.orient.server.network.protocol.http.OHttpUtils;

public class ValidationException extends NdexException
{
    private static final long serialVersionUID = 1L;

    
    
    public ValidationException(String message)
    {
        super(message);
    }

    public ValidationException(String message, Throwable cause)
    {
        super(message, cause);
    }

    
    
    @Override
    public String getHttpStatusDescription()
    {
        return OHttpUtils.STATUS_BADREQ_DESCRIPTION;
    }

    @Override
    public int getHttpStatus()
    {
        return OHttpUtils.STATUS_BADREQ_CODE;
    }
}
