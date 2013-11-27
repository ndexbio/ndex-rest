package org.ndexbio.rest.models;

import java.util.List;
import org.ndexbio.rest.domain.IBaseTerm;
import org.ndexbio.rest.domain.INamespace;

public class BaseTerm extends Term
{
    private String _name;
    private List<String> _namespaces;
    
    /**************************************************************************
    * Default constructor.
    **************************************************************************/
    public BaseTerm()
    {
        super();
    }
    
    /**************************************************************************
    * Populates the class (from the database) and removes circular references.
    * 
    * @param baseTerm The Term with source data.
    **************************************************************************/
    public BaseTerm(IBaseTerm baseTerm)
    {
        _name = baseTerm.getName();
        
        if (baseTerm.getNamespaces() != null)
        {
            for (INamespace namespace : baseTerm.getNamespaces())
                _namespaces.add(namespace.getJdexId());
        }
    }
    
    public String getName()
    {
        return _name;
    }
    
    public void setName(String name)
    {
        _name = name;
    }
    
    public List<String> getNamespaces()
    {
        return _namespaces;
    }
    
    public void setNamespaces(List<String> namespaces)
    {
        _namespaces = namespaces;
    }
}
