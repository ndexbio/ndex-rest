package org.ndexbio.rest.exceptions;

import com.orientechnologies.orient.server.network.protocol.http.OHttpUtils;

public class NdexException extends RuntimeException
{
    private static final long serialVersionUID = 1L;

    
    
    public NdexException(String message)
    {
        super(message);
    }

    public NdexException(String message, Throwable cause)
    {
        super(message, cause);
    }

    
    /**************************************************************************
    * Gets the HTTP status code to return for the exception.
    **************************************************************************/
    public int getHttpStatus()
    {
        return OHttpUtils.STATUS_INTERNALERROR_CODE;
    }

    /**************************************************************************
    * Gets the HTTP status description to return for the exception.
    **************************************************************************/
    public String getHttpStatusDescription()
    {
        return OHttpUtils.STATUS_INTERNALERROR_DESCRIPTION;
    }
}
