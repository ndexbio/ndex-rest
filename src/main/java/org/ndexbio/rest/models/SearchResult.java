package org.ndexbio.rest.models;

import java.util.Collection;
import org.codehaus.jackson.annotate.JsonIgnoreProperties;

//TODO: Remove this class, it's unnecessary
//TODO: Refactor KnockoutJS bindings to not use this class
@JsonIgnoreProperties(ignoreUnknown = true)
public class SearchResult<T> extends NdexObject
{
    private Integer _skip;
    private Integer _pageSize;
    private Collection<T> _results;



    /**************************************************************************
    * Default constructor.
    **************************************************************************/
    public SearchResult()
    {
        super();
    }



    public int getPageSize()
    {
        return _pageSize;
    }

    public void setPageSize(int pageSize)
    {
        _pageSize = pageSize;
    }

    public int getSkip()
    {
        return _skip;
    }

    public void setSkip(int skip)
    {
        _skip = skip;
    }

    public Collection<T> getResults()
    {
        return _results;
    }

    public void setResults(Collection<T> results)
    {
        _results = results;
    }
}
