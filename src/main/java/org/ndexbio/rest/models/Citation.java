package org.ndexbio.rest.models;

public class Citation
{
    private String _contributors;
    private String _identifier;
    private String _jdexId;
    private String _title;
    private String _type;
    
    
    
    public String getContributors()
    {
        return _contributors;
    }
    
    public void setContributors(String contributors)
    {
        _contributors = contributors;
    }
    
    public String getIdentifier()
    {
        return _identifier;
    }
    
    public void setIdentifier(String identifier)
    {
        _jdexId = identifier;
    }
    
    public String getJdexId()
    {
        return _jdexId;
    }
    
    public void setJdexId(String jdexId)
    {
        _jdexId = jdexId;
    }
    
    public String getTitle()
    {
        return _title;
    }
    
    public void setTitle(String title)
    {
        _title = title;
    }
    
    public String getType()
    {
        return _type;
    }
    
    public void setType(String type)
    {
        _type = type;
    }
}
