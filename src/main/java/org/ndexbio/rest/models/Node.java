package org.ndexbio.rest.models;

public class Node
{
    private String _jdexId;
    private String _name;
    private String _represents;
    
    
    
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
    
    public String getRepresents()
    {
        return _represents;
    }
    
    public void setRepresents(String represents)
    {
        _represents = represents;
    }
}
