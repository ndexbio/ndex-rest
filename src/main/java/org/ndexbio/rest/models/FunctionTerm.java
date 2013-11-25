package org.ndexbio.rest.models;

import java.util.Map;

import org.ndexbio.rest.domain.IFunctionTerm;
import org.ndexbio.rest.domain.ITerm;
import org.ndexbio.rest.domain.IBaseTerm;
import org.ndexbio.rest.models.BaseTerm;

public class FunctionTerm extends Term
{
    private BaseTerm _termFunction;
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
        super(iFunctionTerm);
        
        _termFunction = new BaseTerm((IBaseTerm)iFunctionTerm.getTermFunction());
        
        for (Map.Entry entry : iFunctionTerm.getTextParameters().entrySet())
            _textParameters.put((Integer)entry.getKey(), (String)entry.getValue());
        
        for (Map.Entry entry : iFunctionTerm.getTermParameters().entrySet()){
        	ITerm parameterTerm = (ITerm)entry.getValue();
            _termParameters.put((Integer)entry.getKey(), parameterTerm.getJdexId());
        }
    }
    
    public BaseTerm getTermFunction()
    {
        return _termFunction;
    }
    
    public void setTermFunction(BaseTerm function)
    {
        _termFunction = function;
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
