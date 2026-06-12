package org.ndexbio.rest.services.v3;

import static org.junit.Assert.*;

import org.junit.Test;

public class TestNdexStatusV3OAuthUrls {

    private NdexStatusV3 s = new NdexStatusV3();

    @Test
    public void registerUrlContainsIssuerAndClientId() {
        String url = s.buildRegisterUrl("https://auth.example.org/realms/ndex", "myclient", "https://ndexbio.org");
        assertTrue(url.startsWith("https://auth.example.org/realms/ndex/protocol/openid-connect/registrations"));
        assertTrue(url.contains("client_id=myclient"));
    }

    @Test
    public void registerUrlContainsRedirectUri() {
        String url = s.buildRegisterUrl("https://auth.example.org/realms/ndex", "myclient", "https://ndexbio.org");
        assertTrue(url.contains("redirect_uri=https://ndexbio.org"));
    }

    @Test
    public void registerUrlContainsResponseType() {
        String url = s.buildRegisterUrl("https://auth.example.org/realms/ndex", "myclient", "https://ndexbio.org");
        assertTrue(url.contains("response_type=code"));
    }

    @Test
    public void resetUrlContainsIssuerAndClientId() {
        String url = s.buildResetUrl("https://auth.example.org/realms/ndex", "myclient");
        assertTrue(url.startsWith("https://auth.example.org/realms/ndex/login-actions/reset-credentials"));
        assertTrue(url.contains("client_id=myclient"));
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
