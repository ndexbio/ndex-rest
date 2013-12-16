package org.ndexbio.rest.models;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public class SearchParameters
{
    private String _searchString;
    private int _skip;
    private int _top;



    /**************************************************************************
    * Default constructor.
    **************************************************************************/
    public SearchParameters()
    {
        super();
    }




    public String getSearchString()
    {
        return _searchString;
    }

    public void setSearchString(String searchString)
    {
        _searchString = searchString;
    }

    public int getSkip()
    {
        return _skip;
    }

    public void setSkip(int skip)
    {
        _skip = skip;
    }

    public int getTop()
    {
        return _top;
    }

    public void setTop(int top)
    {
        _top = top;
    }
}
