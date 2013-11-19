package org.ndexbio.rest.models;

import java.util.ArrayList;
import java.util.List;
import org.ndexbio.rest.domain.XCitation;

public class Citation
{
    private List<String> _contributors;
    private String _identifier;
    private String _jdexId;
    private String _title;
    private String _type;
    

    
    public Citation()
    {
        _contributors = new ArrayList<String>();
    }
    
    public Citation(XCitation citation)
    {
        _identifier = citation.getIdentifier();
        _jdexId = citation.getJdexId();
        _title = citation.getTitle();
        _type = citation.getType();
        
        _contributors = new ArrayList<String>();
        for (String contributor : citation.getContributors())
            _contributors.add(contributor);
    }

    
    
    public List<String> getContributors()
    {
        return _contributors;
    }
    
    public void setContributors(List<String> contributors)
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
