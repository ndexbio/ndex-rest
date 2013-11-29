package org.ndexbio.rest.models;

import java.util.Collection;
import java.util.List;

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
public class NetworkSearchResult extends NdexObject
{
    private Integer _skip;
    private Integer _pageSize;
    private Collection<Network> _networks;

    

    /**************************************************************************
    * Default constructor.
    **************************************************************************/
    public NetworkSearchResult()
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




	public Collection<Network> getNetworks() {
		return _networks;
	}

	public void setNetworks(Collection<Network> foundNetworks) {
		this._networks = foundNetworks;
	}
   
}
