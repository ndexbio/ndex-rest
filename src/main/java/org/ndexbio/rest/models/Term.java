package org.ndexbio.rest.models;

import org.ndexbio.rest.domain.XTerm;

public class Term
{
    private String _jdexId;
    private String _name;
    private Namespace _namespace;
    
    
    
    public Term()
    {
    }
    
    public Term(XTerm term)
    {
        _jdexId = term.getJdexId();
        _name = term.getName();
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
