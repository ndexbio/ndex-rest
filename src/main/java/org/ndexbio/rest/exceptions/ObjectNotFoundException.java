package org.ndexbio.rest.exceptions;

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
}
