package org.ndexbio.rest.models;

public class Term
{
    private String _jdexId;
    private String _name;
    private String _namespace;
    
    
    
    public String getJdexId()
    {
        return _jdexId;
    }
    
    public void setJdexId(String jdexId)
    {
        _jdexId = jdexId;
    }
    
    public String getName()
    {
        return _name;
    }
    
    public void setName(String name)
    {
        _name = name;
    }
    
    public String getNamespace()
    {
        return _namespace;
    }
    
    public void setNamespace(String namespace)
    {
        _namespace = namespace;
    }

}
