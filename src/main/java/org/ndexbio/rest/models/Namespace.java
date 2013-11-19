package org.ndexbio.rest.models;

public class Namespace
{
    private String _jdexId;
    private String _prefix;
    private String _uri;
    
    
    
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
