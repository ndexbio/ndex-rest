package org.ndexbio.rest.models;

import org.ndexbio.rest.domain.IBaseTerm;
import org.ndexbio.rest.domain.ITerm;

public class BaseTerm extends Term
{
    private String _name;
    private Namespace _namespace;
    
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
    * @param iBaseTerm The Term with source data.
    **************************************************************************/
    public BaseTerm(IBaseTerm iBaseTerm)
    {
        super(iBaseTerm);
        
        _name = iBaseTerm.getName();
        
        if (iBaseTerm.getNamespace() != null)
            _namespace = new Namespace(iBaseTerm.getNamespace());
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
