package org.ndexbio.rest.models;

import org.codehaus.jackson.annotate.JsonIgnoreProperties;
import org.ndexbio.rest.domain.INamespace;

@JsonIgnoreProperties(ignoreUnknown = true)
public class Namespace extends NdexObject
{
    private String _jdexId;
    private String _prefix;
    private String _uri;
    private BaseTerm _baseTerm;
    
    
    
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
        _baseTerm = new BaseTerm(namespace.getTerm());
    }
    
    
    
    public BaseTerm getTerm()
    {
        return _baseTerm;
    }
    
    public void setTerm(BaseTerm term)
    {
        _baseTerm = term;
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
