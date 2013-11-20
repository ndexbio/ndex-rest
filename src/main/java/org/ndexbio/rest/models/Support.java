package org.ndexbio.rest.models;

import org.ndexbio.rest.domain.ISupport;

public class Support
{
    private String _jdexId;
    private String _text;
    
    
    
    /**************************************************************************
    * Default constructor.
    **************************************************************************/
    public Support()
    {
    }
    
    /**************************************************************************
    * Populates the class (from the database) and removes circular references.
    * 
    * @param support The Support with source data.
    **************************************************************************/
    public Support(ISupport support)
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
