package org.ndexbio.rest.models;

public class Edge
{
    private String _object;
    private String _predicate;
    private String _subject;
    
    
    
    public String getObject()
    {
        return _object;
    }
    
    public void setObject(String object)
    {
        _object = object;
    }
    
    public String getPredicate()
    {
        return _predicate;
    }
    
    public void setPredicate(String predicate)
    {
        _predicate = predicate;
    }
    
    public String getSubject()
    {
        return _subject;
    }
    
    public void setSubject(String subject)
    {
        _subject = subject;
    }
}
