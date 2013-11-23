package org.ndexbio.rest.models;

import org.ndexbio.rest.domain.ITerm;

public class Term extends NdexObject
{
    private String _jdexId;
 
    /**************************************************************************
    * Default constructor.
    **************************************************************************/
    public Term()
    {
        super();
    }
    
    /**************************************************************************
    * Populates the class (from the database) and removes circular references.
    * 
    * @param term The Term with source data.
    **************************************************************************/
    public Term(ITerm term)
    {
        super(term);
        
        _jdexId = term.getJdexId();

    }
        
    public String getJdexId()
    {
        return _jdexId;
    }
    
    public void setJdexId(String jdexId)
    {
        _jdexId = jdexId;
    }
    
}
