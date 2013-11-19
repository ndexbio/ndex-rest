package org.ndexbio.rest.models;

import org.ndexbio.rest.domain.XSupport;

public class Support
{
    private String _jdexId;
    private String _text;
    
    
    
    public Support()
    {
    }
    
    public Support(XSupport support)
    {
        _jdexId = support.getJdexId();
        _text = support.getText();
    }
    
    
    
    public String getJdexId()
    {
        return _jdexId;
    }
    
    public void setJdexId(String jdexId)
    {
        _jdexId = jdexId;
    }
    
    public String getText()
    {
        return _text;
    }
    
    public void setText(String text)
    {
        _text = text;
    }
}
