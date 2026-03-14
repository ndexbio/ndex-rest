package org.ndexbio.rest.services;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.Context;

import org.ndexbio.rest.Configuration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Serves the OAuth 2.0 Protected Resource Metadata document (RFC 9728).
 *
 * MCP clients that receive a 401 from /mcp fetch this endpoint to discover the
 * public-facing authorization server URL, then retrieve its OpenID Connect
 * discovery document to bootstrap the PKCE authorization code flow.
 *
 * Config keys read from ndex.properties:
 *   HostURI              — public base URL of this NDEx server
 *   KEYCLOAK_PUBLIC_AUTHN — public-facing Keycloak realm URL
 *                           (e.g. https://www.ndexbio.org/auth2/realms/ndex)
 *                           NOTE: do NOT use KEYCLOAK_ISSUER here — that is an
 *                           internal host not reachable by external MCP clients.
 */
@Path("/.well-known/oauth-protected-resource")
public class OAuthProtectedResourceMetadata extends NdexService {

    private static final Logger logger = LoggerFactory.getLogger(OAuthProtectedResourceMetadata.class);

    public OAuthProtectedResourceMetadata(@Context HttpServletRequest httpRequest) {
        super(httpRequest);
    }

    @GET
    @NdexOpenFunction
    @Produces("application/json")
    public String getMetadata() {
        Configuration config = Configuration.getInstance();
        String hostUri = config.getHostURI();
        String publicAuthn = config.getProperty("KEYCLOAK_PUBLIC_AUTHN");

        StringBuilder sb = new StringBuilder();
        sb.append("{");
        sb.append("\"resource\":\"").append(hostUri).append("/mcp\"");

        if (publicAuthn != null && !publicAuthn.isBlank()) {
            sb.append(",\"authorization_servers\":[\"").append(publicAuthn).append("\"]");
        } else {
            logger.warn("OAuthProtectedResourceMetadata: KEYCLOAK_PUBLIC_AUTHN not configured — " +
                        "authorization_servers field omitted from /.well-known/oauth-protected-resource");
        }

        sb.append("}");
        return sb.toString();
    }
}
