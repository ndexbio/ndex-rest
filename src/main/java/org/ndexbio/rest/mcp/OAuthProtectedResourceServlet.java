package org.ndexbio.rest.mcp;

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.ndexbio.rest.Configuration;

/**
 * Serves OAuth 2.0 Protected Resource Metadata (RFC 9728) at
 * /.well-known/oauth-protected-resource/mcp.
 *
 * MCP clients fetch this URL at handshake time to discover auth requirements.
 * NDEx validates credentials internally (Basic auth or pre-issued Bearer tokens
 * from Keycloak/Google) — external clients are not expected to perform the OAuth
 * Authorization Code Flow. Omitting authorization_servers signals this: per RFC 9728
 * the field is optional, and its absence prevents clients from attempting OAuth
 * discovery against internal auth servers.
 */
public class OAuthProtectedResourceServlet extends HttpServlet {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response) throws IOException {
        String hostUri = Configuration.getInstance().getHostURI();
        if (hostUri == null || hostUri.isBlank()) hostUri = "http://localhost";

        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("resource", hostUri + "/mcp");

        response.setContentType("application/json;charset=UTF-8");
        response.getWriter().write(MAPPER.writeValueAsString(metadata));
    }
}
