package org.ndexbio.rest.filters;

import java.lang.reflect.Field;
import java.util.Base64;

import jakarta.ws.rs.core.MultivaluedHashMap;

import org.easymock.EasyMock;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.ndexbio.model.exceptions.NdexException;
import org.ndexbio.model.exceptions.UnauthorizedOperationException;
import org.ndexbio.model.object.User;
import org.ndexbio.rest.Configuration;
import org.ndexbio.security.OAuthAuthenticator;

import static org.junit.jupiter.api.Assertions.*;

class TestBasicAuthFilterHandleFilter {

    @BeforeAll
    static void setupConfig() {
        Configuration config = EasyMock.mock(Configuration.class);
        EasyMock.expect(config.getUseADAuthentication()).andReturn(false).anyTimes();
        EasyMock.expect(config.getProperty("AUTHENTICATED_USER_ONLY")).andReturn(null).anyTimes();
        EasyMock.expect(config.getProperty("USE_KEYCLOAK_AUTHENTICATION")).andReturn(null).anyTimes();
        EasyMock.expect(config.getProperty("USE_GOOGLE_AUTHENTICATION")).andReturn(null).anyTimes();
        EasyMock.expect(config.getHostURI()).andReturn("https://ndexbio.org").anyTimes();
        Configuration.setInstance(config);
        EasyMock.replay(config);
    }

    private McpAuthFilter newFilter() throws NdexException {
        return new McpAuthFilter();
    }

    @Test
    void handleFilter_emptyHeaders_returnsNull() throws Exception {
        McpAuthFilter filter = newFilter();
        MultivaluedHashMap<String, String> headers = new MultivaluedHashMap<>();
        assertNull(filter.handleFilter(headers));
    }

    @Test
    void handleFilter_bearerWithNoOAuthConfigured_throwsNdexException() throws Exception {
        McpAuthFilter filter = newFilter();
        MultivaluedHashMap<String, String> headers = new MultivaluedHashMap<>();
        headers.add("Authorization", "Bearer sometoken");
        assertThrows(NdexException.class, () -> filter.handleFilter(headers));
    }

    @Test
    void handleFilter_basicMalformed_throwsUnauthorizedOperationException() throws Exception {
        McpAuthFilter filter = newFilter();
        MultivaluedHashMap<String, String> headers = new MultivaluedHashMap<>();
        // "nodot" has no colon, so it's malformed Basic auth
        String encoded = Base64.getEncoder().encodeToString("nodot".getBytes());
        headers.add("Authorization", "Basic " + encoded);
        assertThrows(UnauthorizedOperationException.class, () -> filter.handleFilter(headers));
    }

    @Test
    void handleFilter_unknownScheme_throwsUnauthorizedOperationException() throws Exception {
        McpAuthFilter filter = newFilter();
        MultivaluedHashMap<String, String> headers = new MultivaluedHashMap<>();
        headers.add("Authorization", "Digest xyz");
        assertThrows(UnauthorizedOperationException.class, () -> filter.handleFilter(headers));
    }

    @Test
    void handleFilter_bearerToken_withConfiguredOAuth_returnsUser() throws Exception {
        OAuthAuthenticator mockOAuth = EasyMock.mock(OAuthAuthenticator.class);
        User expected = new User();
        EasyMock.expect(mockOAuth.getUserByIdToken("sometoken")).andReturn(expected).once();
        EasyMock.replay(mockOAuth);

        Field f = BasicAuthenticationFilter.class.getDeclaredField("oAuthAuthenticator");
        f.setAccessible(true);
        OAuthAuthenticator prev = (OAuthAuthenticator) f.get(null);
        f.set(null, mockOAuth);
        try {
            McpAuthFilter filter = newFilter();
            MultivaluedHashMap<String, String> headers = new MultivaluedHashMap<>();
            headers.add("Authorization", "Bearer sometoken");
            User result = filter.handleFilter(headers);
            assertSame(expected, result);
        } finally {
            f.set(null, prev);
        }
        EasyMock.verify(mockOAuth);
    }

    @Test
    void handleFilter_bearerToken_oauthThrowsException_propagates() throws Exception {
        OAuthAuthenticator mockOAuth = EasyMock.mock(OAuthAuthenticator.class);
        EasyMock.expect(mockOAuth.getUserByIdToken("sometoken"))
                .andThrow(new NdexException("auth error")).once();
        EasyMock.replay(mockOAuth);

        Field f = BasicAuthenticationFilter.class.getDeclaredField("oAuthAuthenticator");
        f.setAccessible(true);
        OAuthAuthenticator prev = (OAuthAuthenticator) f.get(null);
        f.set(null, mockOAuth);
        try {
            McpAuthFilter filter = newFilter();
            MultivaluedHashMap<String, String> headers = new MultivaluedHashMap<>();
            headers.add("Authorization", "Bearer sometoken");
            assertThrows(NdexException.class, () -> filter.handleFilter(headers));
        } finally {
            f.set(null, prev);
        }
        EasyMock.verify(mockOAuth);
    }
}
