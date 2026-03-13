package org.ndexbio.rest.filters;

import java.io.IOException;

import org.ndexbio.model.object.User;
import org.ndexbio.rest.services.NdexService;
import org.ndexbio.security.OAuthAuthenticator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import jakarta.servlet.Filter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.FilterConfig;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * Servlet filter that enforces Bearer JWT authentication exclusively on /mcp/* requests.
 * Registered in web.xml scoped to /mcp/* — never fires for any other path.
 * 
 * Ensures mcp requests are authenticated against the same Oauth Authorization servers as the rest of the NDEx REST API.     
 *
 * On success, sets request attribute "User" (org.ndexbio.model.object.User) so the
 * downstream MCP TransportProvider can read it via its contextExtractor.
 */
public class McpOAuthFilter implements Filter {

    private static final Logger logger = LoggerFactory.getLogger(McpOAuthFilter.class);
    private static final String BEARER_PREFIX = "Bearer ";

    private OAuthAuthenticator oAuthAuthenticator;

    @Override
    public void init(FilterConfig filterConfig) throws ServletException {
        // naieve for poc, use the singleton already initialised by BasicAuthenticationFilter
        oAuthAuthenticator = NdexService.getOAuthAuthenticator();
        if (oAuthAuthenticator != null) {
            logger.info("McpOAuthFilter: reusing existing OAuthAuthenticator singleton.");
            return;
        }
    }

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {

        HttpServletRequest  httpReq  = (HttpServletRequest)  request;
        HttpServletResponse httpResp = (HttpServletResponse) response;

        String authHeader = httpReq.getHeader("Authorization");

        if (authHeader == null || !authHeader.startsWith(BEARER_PREFIX)) {
            sendUnauthorized(httpResp, "Missing or non-Bearer Authorization header");
            return;
        }

        if (oAuthAuthenticator == null) {
            sendUnauthorized(httpResp, "No OAuth provider configured on this server");
            return;
        }

        String token = authHeader.substring(BEARER_PREFIX.length()).trim();
        User ndexUser;
        try {
            ndexUser = oAuthAuthenticator.getUserByIdToken(token);
        } catch (Exception e) {
            logger.debug("McpOAuthFilter: token validation failed — {}", e.getMessage());
            sendUnauthorized(httpResp, "Invalid or expired Bearer token");
            return;
        }

        if (ndexUser == null) {
            sendUnauthorized(httpResp, "Bearer token does not map to a known NDEx user");
            return;
        }

        // Propagate authenticated user — read by McpServletContextListener's contextExtractor
        httpReq.setAttribute("User", ndexUser);
        chain.doFilter(request, response);
    }

    private static void sendUnauthorized(HttpServletResponse resp, String reason) throws IOException {
        resp.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        resp.setContentType("application/json;charset=UTF-8");
        resp.getWriter().write("{\"error\":\"Unauthorized\",\"message\":\"" + reason + "\"}");
    }
}
