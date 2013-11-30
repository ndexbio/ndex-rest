package org.ndexbio.rest.models;

import org.codehaus.jackson.annotate.JsonIgnoreProperties;
import org.ndexbio.rest.domain.IGroupInvitationRequest;
import org.ndexbio.rest.domain.IJoinGroupRequest;
import org.ndexbio.rest.domain.INetworkAccessRequest;
import org.ndexbio.rest.domain.IRequest;
import org.ndexbio.rest.helpers.RidConverter;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.orientechnologies.orient.core.id.ORID;

@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(Include.NON_NULL)
public class SearchParameters extends NdexObject
{
    private String _searchString;
    private String _skip;
    private String _limit;

    

    /**************************************************************************
    * Default constructor.
    **************************************************************************/
    public SearchParameters()
    {
        super();
    }



	public String getSearchString() {
		return _searchString;
	}



	public void setSearchString(String _searchString) {
		this._searchString = _searchString;
	}



	public String getSkip() {
		return _skip;
	}



	public void setSkip(String _skip) {
		this._skip = _skip;
	}



	public String getLimit() {
		return _limit;
	}



	public void setLimit(String _limit) {
		this._limit = _limit;
	}
    
 
    
   
}
