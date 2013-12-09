package org.ndexbio.rest.models;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
//import com.fasterxml.jackson.annotation.JsonSubTypes;
//import com.fasterxml.jackson.annotation.JsonTypeInfo;
//import com.fasterxml.jackson.annotation.JsonSubTypes.Type;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import org.ndexbio.rest.helpers.TermDeserializer;
import org.ndexbio.rest.domain.ITerm;

@JsonIgnoreProperties(ignoreUnknown = true)
@JsonDeserialize(using = TermDeserializer.class)
public abstract class Term extends NdexObject
{
    private String _termType;

    
    
    public Term(ITerm term)
    {
        super(term);
    }

    public Term()
    {
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
