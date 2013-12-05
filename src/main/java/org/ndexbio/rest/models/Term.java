package org.ndexbio.rest.models;

import org.codehaus.jackson.annotate.JsonIgnoreProperties;
import org.ndexbio.rest.domain.ITerm;

@JsonIgnoreProperties(ignoreUnknown = true)
public class Term extends NdexObject
{
    private String _termType;

    
    
    public Term(ITerm term)
    {
        super(term);
    }

    public Term()
    {
        _termType = "BASE";
    }

    

    public String getTermType()
    {
        return _termType;
    }

    public void setTermType(String termType)
    {
        _termType = termType;
    }
}
