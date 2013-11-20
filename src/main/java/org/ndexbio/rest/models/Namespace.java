package org.ndexbio.rest.models;

import org.ndexbio.rest.domain.INamespace;

public class Namespace
{
    private String _jdexId;
    private String _prefix;
    private String _uri;
    
    
    
    /**************************************************************************
    * Default constructor.
    **************************************************************************/
    public Namespace()
    {
    }
    
    /**************************************************************************
    * Populates the class (from the database) and removes circular references.
    * 
    * @param namespace The Namespace with source data.
    **************************************************************************/
    public Namespace (INamespace namespace)
    {
        _jdexId = namespace.getJdexId();
        _prefix = namespace.getPrefix();
        _uri = namespace.getUri();
    }
    
    
    
    public String getJdexId()
    {
        return _jdexId;
    }
    
    public void setJdexId(String jdexId)
    {
        _jdexId = jdexId;
    }
    
    public String getPrefix()
    {
        return _prefix;
    }
    
    public void setName(String prefix)
    {
        _prefix = prefix;
    }
    
    public String getUri()
    {
        return _uri;
    }
    
    public void setUri(String uri)
    {
        _uri = uri;
    }
}
