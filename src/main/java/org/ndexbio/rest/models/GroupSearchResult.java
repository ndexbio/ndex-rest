package org.ndexbio.rest.models;

import java.util.Collection;

import org.codehaus.jackson.annotate.JsonIgnoreProperties;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;

@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(Include.NON_NULL)
public class GroupSearchResult extends NdexObject
{
    private Integer _skip;
    private Integer _pageSize;
    private Collection<Group> _groups;

    

    /**************************************************************************
    * Default constructor.
    **************************************************************************/
    public GroupSearchResult()
    {
        super();
    }




	public Integer getSkip() {
		return _skip;
	}




	public void setSkip(Integer _skip) {
		this._skip = _skip;
	}




	public Integer getPageSize() {
		return _pageSize;
	}




	public void setPageSize(Integer _pageSize) {
		this._pageSize = _pageSize;
	}




	public Collection<Group> getGroups() {
		return _groups;
	}

	public void setGroups(Collection<Group> foundGroups) {
		this._groups = foundGroups;
	}
   
}
