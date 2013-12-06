package org.ndexbio.rest.models;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import org.ndexbio.rest.domain.INamespace;

@JsonIgnoreProperties(ignoreUnknown = true)
public class Namespace extends NdexObject
{
    private String _jdexId;
    private String _prefix;
    private String _uri;
    private List<BaseTerm> _baseTerms;
    
    
    
    /**************************************************************************
    * Default constructor.
    **************************************************************************/
    public Namespace()
    {
        super();
    }
    
    /**************************************************************************
    * Populates the class (from the database) and removes circular references.
    * 
    * @param namespace The Namespace with source data.
    **************************************************************************/
    public Namespace (INamespace namespace)
    {
        super(namespace);
        
        _jdexId = namespace.getJdexId();
        _prefix = namespace.getPrefix();
        _uri = namespace.getUri();
        
        // *Don't* add all the BaseTerm jdex ids to the Namespace in the serialization.  
        // The Terms will have the jdex ids of their namespace.  
        // If the recipient of the serialization wants to re-construct bidirectional connections, that is their decision
    }
    
    
    
    public List<BaseTerm> getTerms()
    {
        return _baseTerms;
    }
    
    public void setTerms(List<BaseTerm> terms)
    {
        _baseTerms = terms;
    }
    
    public String getJdexId()
    {
        return _jdexId;
    }
    
    public void setJdexId(String jdexId)
    {
        _jdexId = jdexId;
    }
    
    public String getPrefix()
    {
        return _prefix;
    }
    
    public void setPrefix(String prefix)
    {
        _prefix = prefix;
    }
    
    public String getUri()
    {
        return _uri;
    }
    
    public void setUri(String uri)
    {
        _uri = uri;
    }
}
