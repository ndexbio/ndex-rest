package org.ndexbio.rest.filters;

import java.io.IOException;
import java.util.Collections;
import java.util.Enumeration;

import org.ndexbio.model.exceptions.NdexException;
import org.ndexbio.model.object.User;
import org.ndexbio.rest.Configuration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.auth0.jwt.exceptions.TokenExpiredException;

import jakarta.servlet.Filter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.ws.rs.core.MultivaluedHashMap;

/**
 * Servlet filter for /mcp/* — extends BasicAuthenticationFilter.
 *
 * doFilter() calls BasicAuthenticationFilter.handleFilter() for the shared auth core.
 * Handles its own 401 responses via private sendUnauthorizedResponse():
 *   - Basic credentials present and invalid → plain JSON 401
 *   - No/non-Basic auth header            → JSON 401 + WWW-Authenticate: Bearer resource_metadata="..."
 *   - Token expired (TokenExpiredException) → JSON 401 + WWW-Authenticate: Bearer resource_metadata="..." + error="invalid_token"
 *
 */
public class McpAuthFilter extends BasicAuthenticationFilter implements Filter {

    private static final Logger logger = LoggerFactory.getLogger(McpAuthFilter.class);

    public McpAuthFilter() throws NdexException { super(); }

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {
        HttpServletRequest  httpReq  = (HttpServletRequest)  request;
        HttpServletResponse httpResp = (HttpServletResponse) response;

        // Manifest is public documentation — no authentication required
        String uri = httpReq.getRequestURI();
        String contextPath = httpReq.getContextPath();
        String relPath = uri.startsWith(contextPath) ? uri.substring(contextPath.length()) : uri;
        if ("/mcp/manifest".equals(relPath)) {
            chain.doFilter(request, response);
            return;
        }

        // Adapt HttpServletRequest headers to MultivaluedMap for handleFilter().
        // parseCredentials() does case-sensitive map lookups (e.g. "Authorization"),
        // but Tomcat may normalize header names. Use the case-insensitive Servlet API
        // getHeader() to always populate the expected canonical key.
        MultivaluedHashMap<String, String> headers = new MultivaluedHashMap<>();
        Enumeration<String> names = httpReq.getHeaderNames();
        while (names != null && names.hasMoreElements()) {
            String name = names.nextElement();
            headers.addAll(name, Collections.list(httpReq.getHeaders(name)));
        }
        String authorizationValue = httpReq.getHeader("Authorization");
        if (authorizationValue != null) headers.putSingle("Authorization", authorizationValue);

        try {
            User user = handleFilter(headers);  // from BasicAuthenticationFilter; throws Exception broadly
            if (user != null) httpReq.setAttribute("User", user);
        } catch (Exception e) {
            sendUnauthorizedResponse(httpResp, e, httpReq.getHeader("Authorization"));
            return;
        }
        chain.doFilter(request, response);
    }

    private void sendUnauthorizedResponse(HttpServletResponse resp, Exception cause, String authHeader)
            throws IOException {
        if (authHeader == null || !authHeader.startsWith("Basic ")) {
            // No auth header or non-Basic auth → OAuth challenge with WWW-Authenticate header
            try {
                String hostUri = Configuration.getInstance().getHostURI();
                String wwwAuth = "Bearer resource_metadata=\"" + hostUri +
                                 "/.well-known/oauth-protected-resource\"";
                if (cause instanceof TokenExpiredException) {
                    wwwAuth += " error=\"invalid_token\" error_description=\"" + cause.getMessage() + "\"";
                }
                resp.setHeader("WWW-Authenticate", wwwAuth);
            } catch (Exception e) {
                logger.warn("McpAuthFilter: could not build WWW-Authenticate header — {}", e.getMessage());
            }
        }
        resp.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        resp.setContentType("application/json;charset=UTF-8");
        resp.getWriter().write("{\"error\":\"Unauthorized\",\"message\":\"" + cause.getMessage() + "\"}");
    }

}
