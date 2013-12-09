package org.ndexbio.rest.exceptions;

import java.io.Serializable;

public class NdexException extends Exception implements Serializable
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
}
