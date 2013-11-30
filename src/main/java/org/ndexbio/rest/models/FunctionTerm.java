package org.ndexbio.rest.models;

import java.util.Map;
import org.codehaus.jackson.annotate.JsonIgnoreProperties;
import org.ndexbio.rest.domain.IFunctionTerm;
import org.ndexbio.rest.domain.ITerm;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;

@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(Include.NON_NULL)
public class FunctionTerm extends Term
{
    private String _termFunction;
    private Map<Integer, String> _textParameters;
    private Map<Integer, String> _termParameters;



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
            _textParameters.put(entry.getKey(), entry.getValue());

        for (final Map.Entry<Integer, ITerm> entry : iFunctionTerm.getTermParameters().entrySet())
            _termParameters.put(entry.getKey(), entry.getValue().getJdexId());
    }



    public String getTermFunction()
    {
        return _termFunction;
    }

    public void setTermFunction(String functionId)
    {
        _termFunction = functionId;
    }

    public Map<Integer, String> getTextParameters()
    {
        return _textParameters;
    }

    public void setTextParameters(Map<Integer, String> _textParameters)
    {
        this._textParameters = _textParameters;
    }

    public Map<Integer, String> getTermParameters()
    {
        return _termParameters;
    }

    public void setTermParameters(Map<Integer, String> _termParameters)
    {
        this._termParameters = _termParameters;
    }
}
