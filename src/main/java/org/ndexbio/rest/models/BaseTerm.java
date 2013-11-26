package org.ndexbio.rest.models;

import org.ndexbio.rest.domain.IBaseTerm;

public class BaseTerm extends Term
{
	/*
	 * mod 25Nov2013
	 * change from Namespace object composition to Namespace id reference
	 */
    private String _name;
    //private Namespace _namespace;
    private String _namespace;
    
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
       
        
        _name = iBaseTerm.getName();
        
        if (iBaseTerm.getNamespace() != null)
            this.setNamespace(iBaseTerm.getNamespace().getJdexId());
    }
    
    public String getName()
    {
        return _name;
    }
    
    public void setName(String name)
    {
        _name = name;
    }
    
    public String getNamespace()
    {
        return _namespace;
    }
    
    public void setNamespace(String  jdexId)
    {
        _namespace = jdexId;
    }
}
