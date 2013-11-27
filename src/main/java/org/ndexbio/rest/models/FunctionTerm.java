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
	/*
	 * mod 25Nov2013
	 * change from BaseTerm object composition to BaseTerm id reference
	 */
    //private BaseTerm _termFunction;
    private String _termFunction;
    private Map<Integer, String> _textParameters;
    private Map<Integer, String> _termParameters; // ids, not Term objects
    
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
        
        for (Map.Entry<Integer,String> entry : iFunctionTerm.getTextParameters().entrySet())
            _textParameters.put(entry.getKey(), entry.getValue());
        
        for (Map.Entry<Integer, ITerm> entry : iFunctionTerm.getTermParameters().entrySet()){
        	ITerm parameterTerm = entry.getValue();
            _termParameters.put(entry.getKey(), parameterTerm.getJdexId());
        }
    }
    
    public String getTermFunction()
    {
        return _termFunction;
    }
    
    public void setTermFunction(String functionId)
    {
        _termFunction = functionId;
    }

	public Map<Integer, String> getTextParameters() {
		return _textParameters;
	}

	public void setTextParameters(Map<Integer, String> _textParameters) {
		this._textParameters = _textParameters;
	}

	public Map<Integer, String> getTermParameters() {
		return _termParameters;
	}

	public void setTermParameters(Map<Integer, String> _termParameters) {
		this._termParameters = _termParameters;
	}
    
}
