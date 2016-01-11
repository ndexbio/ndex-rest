package org.ndexbio.security;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;


@JsonIgnoreProperties(ignoreUnknown = true)

public class OAuthTokenRenewRequest {

	private String _accessToken;
	private String _refreshToken;
	
	public OAuthTokenRenewRequest() {
	}

	public String getAccessToken() {
		return _accessToken;
	}

	public void setAccessToken(String accessToken) {
		this._accessToken = accessToken;
	}

	public String getRefreshToken() {
		return _refreshToken;
	}

	public void setRefreshToken(String refreshToken) {
		this._refreshToken = refreshToken;
	}

}
