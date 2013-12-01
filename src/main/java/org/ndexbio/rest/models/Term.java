package org.ndexbio.rest.models;

import java.util.Map;

import org.codehaus.jackson.annotate.JsonIgnoreProperties;
import org.ndexbio.rest.domain.ITerm;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;

@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(Include.NON_NULL)
public class Term extends NdexObject
{
    private String _name;
    private String _namespace;
    private String _termFunction;
    private Map<Integer, String> _parameters;
    private String _termType;
    
    public Term(ITerm term){
    	super(term);
    }

    public Term()
    {
        this._termType = "BASE";
    }
    
    public String getName()
    {
        return _name;
    }

    public void setName(String termName)
    {
        _name = termName;
    }

    public String getNamespace()
    {
        return _namespace;
    }

    public void setNamespace(String namespace)
    {
        _namespace = namespace;
    }

    public String getTermFunction()
    {

        return _termFunction;
    }

    public void setTermFunction(String termFunction)
    {
        setTermType("FUNCTION");
        _termFunction = termFunction;
    }

    public Map<Integer, String> getParameters()
    {
        return _parameters;
    }

    public void setParameters(Map<Integer, String> parameters)
    {
        setTermType("FUNCTION");
        _parameters = parameters;
    }

    public String getTermType()
    {
        return _termType;
    }

    private void setTermType(String termType)
    {
        _termType = termType;
    }
}
