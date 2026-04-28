package org.ndexbio.rest.filters;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.Enumeration;

import org.ndexbio.model.exceptions.NdexException;
import org.ndexbio.model.object.User;
import org.ndexbio.rest.Configuration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;

import com.auth0.jwt.exceptions.TokenExpiredException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import jakarta.servlet.Filter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ReadListener;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletInputStream;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.ws.rs.core.MultivaluedHashMap;

/**
 * Servlet filter for /mcp/* — extends BasicAuthenticationFilter.
 *
 * doFilter() skips handleFilter() entirely when no Authorization header is present, guaranteeing
 * anonymous pass-through. Tools enforce per-resource auth via their own user-null checks.
 * When credentials ARE present, doFilter() calls BasicAuthenticationFilter.handleFilter() and
 * handles 401 responses via private sendUnauthorizedResponse():
 *   - Basic credentials present and invalid → plain JSON 401
 *   - No Authorization header              → anonymous pass-through (handleFilter not called)
 *   - Bearer token invalid/expired         → JSON 401 + WWW-Authenticate: Bearer resource_metadata="..."
 *   - Bearer token expired                 → JSON 401 + WWW-Authenticate: Bearer ... error="invalid_token"
 *
 */
public class McpAuthFilter extends BasicAuthenticationFilter implements Filter {

    private static final Logger       logger = LoggerFactory.getLogger(McpAuthFilter.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();

    public McpAuthFilter() throws NdexException { super(); }

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {
        HttpServletRequest  httpReq  = (HttpServletRequest)  request;
        HttpServletResponse httpResp = (HttpServletResponse) response;

        // Always upfront — same contract as BasicAuthenticationFilter.filter()
        MDC.put("RequestsUniqueId", "tid:" + System.currentTimeMillis() + "-" + getCounter());

        String uri         = httpReq.getRequestURI();
        String contextPath = httpReq.getContextPath();
        String relPath     = uri.startsWith(contextPath) ? uri.substring(contextPath.length()) : uri;

        // Manifest is public documentation — no authentication, no access log
        if ("/mcp/manifest".equals(relPath)) {
            chain.doFilter(request, response);
            return;
        }

        // Upload and download bypass — log with fixed label, but NEVER include query string
        // (presigned upload_token / download_token are in the query and must not reach access logs)
        if ("/mcp/upload".equals(relPath)) {
            accessLogger.info("[start]\t" + buildMcpLogString(httpReq, relPath, null, "", "upload"));
            chain.doFilter(request, response);
            return;
        }
        if ("/mcp/download".equals(relPath)) {
            accessLogger.info("[start]\t" + buildMcpLogString(httpReq, relPath, null, "", "download"));
            chain.doFilter(request, response);
            return;
        }

        // RPC paths — compute fullPath (with optional query string) for all log entries below
        String query    = httpReq.getQueryString();
        String fullPath = query != null ? relPath + "?" + query : relPath;

        String authHeader = httpReq.getHeader("Authorization");
        String authType   = authHeader == null ? "" : (authHeader.startsWith("Bearer ") ? "G" : "B");

        if (authHeader != null) {
            // Authenticate BEFORE buffering body so that auth failures never read the request body
            // (avoids a DoS vector where unauthenticated callers exhaust heap with large bodies)
            MultivaluedHashMap<String, String> headers = new MultivaluedHashMap<>();
            Enumeration<String> names = httpReq.getHeaderNames();
            while (names != null && names.hasMoreElements()) {
                String name = names.nextElement();
                // Normalize Authorization to canonical capitalization — Tomcat may lowercase header names
                String canonicalName = "authorization".equalsIgnoreCase(name) ? "Authorization" : name;
                headers.addAll(canonicalName, Collections.list(httpReq.getHeaders(name)));
            }
            User user = null;
            try {
                user = handleFilter(headers);
            } catch (Exception e) {
                String msg = e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName();
                sendUnauthorizedResponse(httpResp, e, authHeader);
                MDC.put("error", msg);
                // Auth failed — body not read; tool label is empty
                accessLogger.info("[start]\t" + buildMcpLogString(httpReq, fullPath, null, authType, "")
                        + "\t[Unauthorized exception: " + msg + "]");
                return;
            }
            // Auth succeeded — buffer body, extract tool label, pass wrapped request downstream
            CachedBodyRequestWrapper wrapped = new CachedBodyRequestWrapper(httpReq);
            if (user != null) wrapped.setAttribute("User", user);
            String toolLabel = extractToolLabel(wrapped.getBody());
            if (!toolLabel.isEmpty())
                accessLogger.info("[start]\t" + buildMcpLogString(httpReq, fullPath, user, authType, toolLabel));
            chain.doFilter(wrapped, response);
            return;
        }

        // Anonymous pass-through: skip handleFilter() entirely when no credentials are present.
        // Tools enforce per-resource auth via their own user-null checks.
        CachedBodyRequestWrapper wrapped = new CachedBodyRequestWrapper(httpReq);
        String toolLabel = extractToolLabel(wrapped.getBody());
        if (!toolLabel.isEmpty())
            accessLogger.info("[start]\t" + buildMcpLogString(httpReq, fullPath, null, "", toolLabel));
        chain.doFilter(wrapped, response);
    }

    /**
     * Extracts a human-readable label for the JSON-RPC call from the buffered body.
     * For {@code tools/call} requests returns {@code params.name} (e.g. {@code search_network}).
     * For other methods returns the method name itself (e.g. {@code tools/list}).
     * Returns {@code ""} on any parse failure or missing fields.
     */
    private static String extractToolLabel(byte[] body) {
        if (body == null || body.length == 0) return "";
        try {
            JsonNode node   = MAPPER.readTree(body);
            String   method = node.path("method").asText(null);
            if ("tools/call".equals(method)) {
                String name = node.path("params").path("name").asText(null);
                return name != null ? name : "tools/call";
            }
            return method != null ? method : "";
        } catch (Exception e) {
            return "";
        }
    }

    /**
     * Builds the access-log line fragment (everything after "[start]\t").
     * {@code fullPath} is pre-computed by the caller; upload/download callers pass {@code relPath}
     * directly to avoid logging presigned query-string tokens.
     */
    private String buildMcpLogString(HttpServletRequest req, String fullPath,
                                     User user, String authType, String toolLabel) {
        String forwarded = req.getHeader("X-FORWARDED-FOR");
        String clientIPs = forwarded != null ? forwarded : req.getRemoteAddr();
        String userAgent = req.getHeader("User-Agent");
        String ndexApp   = req.getHeader("NDEx-application");
        if (userAgent == null) userAgent = "";
        if (ndexApp != null)   userAgent += " " + ndexApp;
        String userPart  = user == null ? "" : (authType + ":" + user.getUserName());
        return "[" + req.getMethod() + "]\t[" + userPart + "]\t[" + clientIPs
                + "]\t[" + userAgent + "]\t[" + toolLabel + "]\t[" + fullPath + "]\t{}";
    }

    private void sendUnauthorizedResponse(HttpServletResponse resp, Exception cause, String authHeader)
            throws IOException {
        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            // Bearer token was attempted but failed → OAuth challenge with WWW-Authenticate header
            try {
                String hostUri = Configuration.getInstance().getHostURI();
                String wwwAuth = "Bearer resource_metadata=\"" + hostUri +
                                 "/.well-known/oauth-protected-resource/mcp\"";
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

    /**
     * Wraps an {@link HttpServletRequest} and caches the body bytes so that
     * {@link #getInputStream()} and {@link #getReader()} can be called multiple times.
     * Required because the MCP SDK also reads the body via {@code getInputStream()} downstream.
     */
    private static final class CachedBodyRequestWrapper extends HttpServletRequestWrapper {
        private final byte[] body;

        CachedBodyRequestWrapper(HttpServletRequest req) throws IOException {
            super(req);
            this.body = req.getInputStream().readAllBytes();
        }

        @Override
        public ServletInputStream getInputStream() {
            ByteArrayInputStream bais = new ByteArrayInputStream(body);
            return new ServletInputStream() {
                public boolean isFinished()                           { return bais.available() == 0; }
                public boolean isReady()                             { return true; }
                // Body is already fully buffered; no-op is safe for non-blocking async reads
                public void    setReadListener(ReadListener listener) { /* no-op */ }
                public int     read()                                { return bais.read(); }
            };
        }

        @Override
        public BufferedReader getReader() {
            return new BufferedReader(new InputStreamReader(
                    new ByteArrayInputStream(body), StandardCharsets.UTF_8));
        }

        byte[] getBody() { return body; }
    }

}
