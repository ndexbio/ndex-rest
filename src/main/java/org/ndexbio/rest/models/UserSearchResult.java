package org.ndexbio.rest.models;

import java.util.Collection;

import org.codehaus.jackson.annotate.JsonIgnoreProperties;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;

@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(Include.NON_NULL)
public class UserSearchResult extends NdexObject
{
    private Integer _skip;
    private Integer _pageSize;
    private Collection<User> _users;

    

    /**************************************************************************
    * Default constructor.
    **************************************************************************/
    public UserSearchResult()
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




	public Collection<User> getUsers() {
		return _users;
	}

	public void setUsers(Collection<User> foundUsers) {
		this._users = foundUsers;
	}
   
}
