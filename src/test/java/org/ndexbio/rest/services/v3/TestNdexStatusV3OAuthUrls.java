package org.ndexbio.rest.services.v3;

import static org.junit.Assert.*;

import org.junit.Test;

public class TestNdexStatusV3OAuthUrls {

    private NdexStatusV3 s = new NdexStatusV3();

    @Test
    public void registerUrlExactFormat() {
        // Keycloak requires all three params for the registrations endpoint to work.
        // redirect_uri points to the NDEx server's Swagger UI — a stable, always-published path.
        String url = s.buildRegisterUrl("https://auth.example.org/realms/ndex", "myclient", "https://ndexbio.org");
        assertEquals(
            "https://auth.example.org/realms/ndex/protocol/openid-connect/registrations" +
            "?client_id=myclient&response_type=code&redirect_uri=https://ndexbio.org/swagger/index.html",
            url);
    }

    @Test
    public void resetUrlContainsIssuerOnly() {
        // No client_id: after reset the identity server redirects to account management by default.
        String url = s.buildResetUrl("https://auth.example.org/realms/ndex");
        assertEquals("https://auth.example.org/realms/ndex/login-actions/reset-credentials", url);
    }

    @Test
    public void resetUrlDoesNotContainClientId() {
        String url = s.buildResetUrl("https://auth.example.org/realms/ndex");
        assertFalse(url.contains("client_id"));
    }

    @Test
    public void oauthFieldsOmittedFromJsonWhenNull() throws Exception {
        com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
        String json = mapper.writeValueAsString(new NdexStatusV3());
        assertFalse(json.contains("oauth_register_url"));
        assertFalse(json.contains("oauth_reset_url"));
    }

    @Test
    public void oauthFieldsPresentInJsonWhenSet() throws Exception {
        NdexStatusV3 status = new NdexStatusV3();
        status.setOauthRegisterUrl("https://auth.example.org/register");
        status.setOauthResetUrl("https://auth.example.org/reset");
        com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
        String json = mapper.writeValueAsString(status);
        assertTrue(json.contains("\"oauth_register_url\":\"https://auth.example.org/register\""));
        assertTrue(json.contains("\"oauth_reset_url\":\"https://auth.example.org/reset\""));
    }
}
