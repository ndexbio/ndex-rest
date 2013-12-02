package org.ndexbio.rest.models;

import java.util.Map;
import org.codehaus.jackson.annotate.JsonIgnoreProperties;
import org.ndexbio.rest.domain.IFunctionTerm;
import org.ndexbio.rest.domain.ITerm;

@JsonIgnoreProperties(ignoreUnknown = true)
public class FunctionTerm extends Term
{

    /**************************************************************************
    * Default constructor.
    **************************************************************************/
    public FunctionTerm()
    {
        super();
    }

    /**************************************************************************
    * Populates the class (from the database) and removes circular references.
    * 
    * @param iFunctionTerm The Term with source data.
    **************************************************************************/
    public FunctionTerm(IFunctionTerm iFunctionTerm)
    {

        this.setTermFunction(iFunctionTerm.getTermFunction().getJdexId());

        for (final Map.Entry<Integer, String> entry : iFunctionTerm.getTextParameters().entrySet())
            this.getParameters().put(entry.getKey(), entry.getValue());

        for (final Map.Entry<Integer, ITerm> entry : iFunctionTerm.getTermParameters().entrySet())
        	this.getParameters().put(entry.getKey(), entry.getValue().getJdexId());
    }


}
