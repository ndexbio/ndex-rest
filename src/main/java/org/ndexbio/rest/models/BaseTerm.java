package org.ndexbio.rest.models;

import org.codehaus.jackson.annotate.JsonIgnoreProperties;
import org.ndexbio.rest.domain.IBaseTerm;

@JsonIgnoreProperties(ignoreUnknown = true)
public class BaseTerm extends Term
{

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
     * @param baseTerm
     *            The Term with source data.
     **************************************************************************/
    public BaseTerm(IBaseTerm baseTerm)
    {
        super(baseTerm);
        
        this.setName(baseTerm.getName());
        
        if (baseTerm.getNamespace() != null)
            this.setNamespace(baseTerm.getNamespace().getJdexId());
    }
}
