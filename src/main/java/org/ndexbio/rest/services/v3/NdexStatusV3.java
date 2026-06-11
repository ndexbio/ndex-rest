package org.ndexbio.rest.services.v3;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import org.ndexbio.model.object.NdexStatus;

public class NdexStatusV3 extends NdexStatus {

    @JsonProperty("oauth_register_url")
    @JsonInclude(JsonInclude.Include.NON_NULL)
    private String oauthRegisterUrl;

    @JsonProperty("oauth_reset_url")
    @JsonInclude(JsonInclude.Include.NON_NULL)
    private String oauthResetUrl;

    public NdexStatusV3() {
        super();
    }

    public NdexStatusV3(NdexStatus base) {
        super();
        setNetworkCount(base.getNetworkCount());
        setUserCount(base.getUserCount());
        setGroupCount(base.getGroupCount());
        setProperties(base.getProperties());
        setMessage(base.getMessage());
    }

    public String getOauthRegisterUrl() { return oauthRegisterUrl; }
    public void setOauthRegisterUrl(String url) { this.oauthRegisterUrl = url; }

    public String getOauthResetUrl() { return oauthResetUrl; }
    public void setOauthResetUrl(String url) { this.oauthResetUrl = url; }

    String buildRegisterUrl(String issuer, String clientId, String hostUri) {
        return issuer + "/protocol/openid-connect/registrations"
                + "?client_id=" + clientId
                + "&response_type=code"
                + "&redirect_uri=" + hostUri;
    }

    String buildResetUrl(String issuer, String clientId) {
        return issuer + "/login-actions/reset-credentials"
                + "?client_id=" + clientId;
    }
}
