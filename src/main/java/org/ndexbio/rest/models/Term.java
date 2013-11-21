package org.ndexbio.rest.models;

import org.ndexbio.rest.domain.ITerm;

public class Term extends NdexObject
{
    private String _jdexId;
    private String _name;
    private Namespace _namespace;
    
    
    
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
        _name = term.getName();
        
        if (term.getNamespace() != null)
            _namespace = new Namespace(term.getNamespace());
    }
    
    
    
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
    
    public Namespace getNamespace()
    {
        return _namespace;
    }
    
    public void setNamespace(Namespace namespace)
    {
        _namespace = namespace;
    }
}
