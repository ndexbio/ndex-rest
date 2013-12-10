package org.ndexbio.rest.models;

import java.util.Map;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import org.ndexbio.rest.domain.IFunctionTerm;
import org.ndexbio.rest.domain.ITerm;

@JsonIgnoreProperties(ignoreUnknown = true)
public class FunctionTerm extends Term
{
    private String _termFunction;
    private Map<Integer, String> _parameters;

    
    
    /**************************************************************************
    * Default constructor.
    **************************************************************************/
    public FunctionTerm()
    {
        super();
        
        this.setTermType("Function");
    }

    /**************************************************************************
    * Populates the class (from the database) and removes circular references.
    * 
    * @param iFunctionTerm The Term with source data.
    **************************************************************************/
    public FunctionTerm(IFunctionTerm iFunctionTerm)
    {
        this.setTermFunction(iFunctionTerm.getTermFunc().getJdexId());

        for (final Map.Entry<Integer, String> entry : iFunctionTerm.getTextParameters().entrySet())
            this.getParameters().put(entry.getKey(), entry.getValue());

        for (final Map.Entry<Integer, ITerm> entry : iFunctionTerm.getTermParameters().entrySet())
        	this.getParameters().put(entry.getKey(), entry.getValue().getJdexId());
    }



    public Map<Integer, String> getParameters()
    {
        return _parameters;
    }

    public void setParameters(Map<Integer, String> parameters)
    {
        _parameters = parameters;
    }

    public String getTermFunction()
    {
        return _termFunction;
    }

    public void setTermFunction(String termFunction)
    {
        _termFunction = termFunction;
    }
}
