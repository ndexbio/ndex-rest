package org.ndexbio.rest.services.v3;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import org.ndexbio.model.object.NdexStatus;

public class NdexStatusV3 extends NdexStatus {

    @Schema(description = "Fully qualified URL to the OAuth identity server's user registration page. " +
            "Only present when the server is configured to use OAuth authentication. " +
            "Absent from the response when OAuth is not enabled. " +
            "This URL is intended solely for account creation; after registration the identity server " +
            "follows a default browser flow to redirect the user to the NDEx server's Swagger UI (/swagger/index.html).",
            nullable = true)
    @JsonProperty("oauth_register_url")
    @JsonInclude(JsonInclude.Include.NON_NULL)
    private String oauthRegisterUrl;

    @Schema(description = "Fully qualified URL to the OAuth identity server's password reset page. " +
            "Only present when the server is configured to use OAuth authentication. " +
            "Absent from the response when OAuth is not enabled. " +
            "This URL is intended solely for initiating browser based password reset flow; after the reset flow concludes the identity server " +
            "follows a default browser flow to redirect the user to their account management page on the identity server.",
            nullable = true)
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
                + "&redirect_uri=" + hostUri + "/swagger/index.html";
    }

    String buildResetUrl(String issuer) {
        return issuer + "/login-actions/reset-credentials";
    }
}
