package org.ndexbio.rest.mcp;

import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.ndexbio.rest.Configuration;

/**
 * Serves OAuth 2.0 Protected Resource Metadata (RFC 9728) at
 * /.well-known/oauth-protected-resource/mcp and /.well-known/oauth-protected-resource.
 *
 * MCP clients fetch this URL at handshake time to discover auth requirements.
 * When Keycloak or Google OAuth is configured the issuer URL is included in
 * authorization_servers so MCP clients can perform token-based auth.
 * When neither is configured authorization_servers is an empty array, which
 * explicitly signals to MCP clients that no OAuth server is available and
 * prevents them from probing /.well-known/oauth-authorization-server/*.
 */
public class OAuthProtectedResourceServlet extends HttpServlet {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final String GOOGLE_OIDC_ISSUER = "https://accounts.google.com";

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response) throws IOException {
        Configuration config = Configuration.getInstance();
        String hostUri = config != null ? config.getHostURI() : null;
        if (hostUri == null || hostUri.isBlank()) hostUri = "http://localhost";

        List<String> authorizationServers = new ArrayList<>();
        if (config != null) {
            String useKeycloak = config.getProperty("USE_KEYCLOAK_AUTHENTICATION");
            if ("true".equalsIgnoreCase(useKeycloak)) {
                String issuer = config.getProperty("KEYCLOAK_ISSUER");
                if (issuer != null && !issuer.isBlank()) authorizationServers.add(issuer);
            } else {
                String useGoogle = config.getProperty("USE_GOOGLE_AUTHENTICATION");
                if ("true".equalsIgnoreCase(useGoogle)) authorizationServers.add(GOOGLE_OIDC_ISSUER);
            }
        }

        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("resource", hostUri + "/mcp");
        metadata.put("authorization_servers", authorizationServers);

        response.setContentType("application/json;charset=UTF-8");
        response.getWriter().write(MAPPER.writeValueAsString(metadata));
    }
}
