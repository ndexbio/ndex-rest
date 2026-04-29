package org.ndexbio.rest.filters;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ReadListener;
import jakarta.servlet.ServletInputStream;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.ws.rs.core.MultivaluedMap;

import org.easymock.EasyMock;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.ndexbio.model.exceptions.NdexException;
import org.ndexbio.model.exceptions.UnauthorizedOperationException;
import org.ndexbio.model.object.User;
import org.ndexbio.rest.Configuration;

import com.auth0.jwt.exceptions.TokenExpiredException;

import static org.easymock.EasyMock.*;
import static org.junit.jupiter.api.Assertions.*;

class TestMcpAuthFilter {

    private static Configuration savedInstance;

    @BeforeAll
    static void setupConfig() {
        savedInstance = Configuration.getInstance();
        Configuration config = EasyMock.mock(Configuration.class);
        expect(config.getUseADAuthentication()).andReturn(false).anyTimes();
        expect(config.getProperty("AUTHENTICATED_USER_ONLY")).andReturn(null).anyTimes();
        expect(config.getProperty("USE_KEYCLOAK_AUTHENTICATION")).andReturn(null).anyTimes();
        expect(config.getProperty("USE_GOOGLE_AUTHENTICATION")).andReturn(null).anyTimes();
        expect(config.getHostURI()).andReturn("https://ndexbio.org").anyTimes();
        Configuration.setInstance(config);
        EasyMock.replay(config);
    }

    @AfterAll
    static void teardownConfig() {
        Configuration.setInstance(savedInstance);
    }

    // ── inner stubs ──────────────────────────────────────────────────────────

    private static class TestableMcpFilter extends McpAuthFilter {
        private final User userToReturn;
        private final Exception exceptionToThrow;

        TestableMcpFilter(User user, Exception ex) throws NdexException {
            super();
            this.userToReturn = user;
            this.exceptionToThrow = ex;
        }

        @Override
        protected User handleFilter(MultivaluedMap<String, String> headers) throws Exception {
            if (exceptionToThrow instanceof RuntimeException re) throw re;
            if (exceptionToThrow instanceof NdexException ne) throw ne;
            if (exceptionToThrow instanceof IOException ioe) throw ioe;
            return userToReturn;
        }
    }

    private static final byte[] TOOL_CALL_BODY =
        "{\"jsonrpc\":\"2.0\",\"method\":\"tools/call\",\"params\":{\"name\":\"test_tool\"}}"
            .getBytes(StandardCharsets.UTF_8);

    private static class MockServletInputStream extends ServletInputStream {
        private final ByteArrayInputStream stream;
        MockServletInputStream(byte[] bytes) { this.stream = new ByteArrayInputStream(bytes); }
        public boolean isFinished()                          { return stream.available() == 0; }
        public boolean isReady()                             { return true; }
        public void    setReadListener(ReadListener listener) { /* no-op */ }
        public int     read() throws IOException             { return stream.read(); }
    }

    // ── helpers: mock the calls made inside doFilter / buildMcpLogString ─────

    /** Covers the 5 calls made by buildMcpLogString. */
    private static void expectLogCalls(HttpServletRequest req) {
        expect(req.getHeader("X-FORWARDED-FOR")).andReturn(null).once();
        expect(req.getRemoteAddr()).andReturn("127.0.0.1").once();
        expect(req.getHeader("User-Agent")).andReturn("").once();
        expect(req.getHeader("NDEx-application")).andReturn(null).once();
        expect(req.getMethod()).andReturn("GET").once();
        // getQueryString() is no longer called inside buildMcpLogString;
        // RPC-path tests add it explicitly via expect(req.getQueryString())
    }

    /** Covers the getInputStream() call when the filter buffers the body. */
    private static void expectBodyRead(HttpServletRequest req) throws IOException {
        expect(req.getInputStream())
            .andReturn(new MockServletInputStream(TOOL_CALL_BODY)).once();
    }

    /** Covers the getInputStream() call when the filter buffers an empty body. */
    private static void expectEmptyBodyRead(HttpServletRequest req) throws IOException {
        expect(req.getInputStream())
            .andReturn(new MockServletInputStream(new byte[0])).once();
    }

    // ── tests ────────────────────────────────────────────────────────────────

    @Test
    void doFilter_bypassesAuth_forDownloadPath() throws Exception {
        TestableMcpFilter filter = new TestableMcpFilter(null, new org.ndexbio.model.exceptions.UnauthorizedOperationException("should not reach auth"));

        HttpServletRequest req = mock(HttpServletRequest.class);
        HttpServletResponse resp = mock(HttpServletResponse.class);
        FilterChain chain = mock(FilterChain.class);

        expect(req.getRequestURI()).andReturn("/mcp/download").once();
        expect(req.getContextPath()).andReturn("").once();
        expectLogCalls(req);
        // original request passed — no body buffering for bypass paths
        chain.doFilter(req, resp);
        expectLastCall().once();

        replay(req, resp, chain);
        filter.doFilter(req, resp, chain);
        verify(req, resp, chain);
    }

    @Test
    void doFilter_bypassesAuth_forUploadPath() throws Exception {
        TestableMcpFilter filter = new TestableMcpFilter(null, new org.ndexbio.model.exceptions.UnauthorizedOperationException("should not reach auth"));

        HttpServletRequest req = mock(HttpServletRequest.class);
        HttpServletResponse resp = mock(HttpServletResponse.class);
        FilterChain chain = mock(FilterChain.class);

        expect(req.getRequestURI()).andReturn("/mcp/upload").once();
        expect(req.getContextPath()).andReturn("").once();
        expectLogCalls(req);
        // original request passed — no body buffering for bypass paths
        chain.doFilter(req, resp);
        expectLastCall().once();
        // response must NOT receive a 401 — auth is bypassed for /mcp/upload

        replay(req, resp, chain);
        filter.doFilter(req, resp, chain);
        verify(req, resp, chain);
    }

    @Test
    void doFilter_anonymous_passesThrough() throws Exception {
        TestableMcpFilter filter = new TestableMcpFilter(null, null);

        HttpServletRequest req = mock(HttpServletRequest.class);
        HttpServletResponse resp = mock(HttpServletResponse.class);
        FilterChain chain = mock(FilterChain.class);

        expect(req.getRequestURI()).andReturn("/mcp/tool-call").once();
        expect(req.getContextPath()).andReturn("").once();
        expect(req.getHeader("Authorization")).andReturn(null).once();
        expect(req.getQueryString()).andReturn(null).once();
        expectBodyRead(req);
        expectLogCalls(req);
        // wrapped request passed downstream — use anyObject() since wrapper is created internally
        chain.doFilter(anyObject(), eq(resp));
        expectLastCall().once();

        replay(req, resp, chain);
        filter.doFilter(req, resp, chain);
        verify(req, resp, chain);
    }

    @Test
    void doFilter_validUser_setsAttributeAndPassesThrough() throws Exception {
        User mockUser = new User();
        TestableMcpFilter filter = new TestableMcpFilter(mockUser, null);

        HttpServletRequest req = mock(HttpServletRequest.class);
        HttpServletResponse resp = mock(HttpServletResponse.class);
        FilterChain chain = mock(FilterChain.class);

        expect(req.getRequestURI()).andReturn("/mcp/tool-call").once();
        expect(req.getContextPath()).andReturn("").once();
        expect(req.getHeader("Authorization")).andReturn("Basic dXNlcjpwYXNz").once();
        expect(req.getHeaderNames()).andReturn(java.util.Collections.emptyEnumeration()).once();
        expect(req.getQueryString()).andReturn(null).once();
        // setAttribute delegates through the wrapper to the underlying request mock
        req.setAttribute("User", mockUser);
        expectLastCall().once();
        expectBodyRead(req);
        expectLogCalls(req);
        // wrapped request passed downstream — use anyObject() since wrapper is created internally
        chain.doFilter(anyObject(), eq(resp));
        expectLastCall().once();

        replay(req, resp, chain);
        filter.doFilter(req, resp, chain);
        verify(req, resp, chain);
    }

    @Test
    void doFilter_basicAuthException_plain401_noWwwAuthenticate() throws Exception {
        TestableMcpFilter filter = new TestableMcpFilter(null, new UnauthorizedOperationException("bad creds"));

        HttpServletRequest req = mock(HttpServletRequest.class);
        HttpServletResponse resp = mock(HttpServletResponse.class);
        FilterChain chain = mock(FilterChain.class);
        PrintWriter writer = mock(PrintWriter.class);

        expect(req.getRequestURI()).andReturn("/mcp/tool-call").once();
        expect(req.getContextPath()).andReturn("").once();
        expect(req.getHeaderNames()).andReturn(java.util.Collections.emptyEnumeration()).once();
        expect(req.getHeader("Authorization")).andReturn("Basic dXNlcjpwYXNz").once();
        // auth-first: body is NOT buffered when auth fails — only getQueryString() is called
        expect(req.getQueryString()).andReturn(null).once();
        resp.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        expectLastCall().once();
        resp.setContentType("application/json;charset=UTF-8");
        expectLastCall().once();
        expect(resp.getWriter()).andReturn(writer).once();
        writer.write(anyString());
        expectLastCall().once();
        expectLogCalls(req);
        // chain must NOT be called

        replay(req, resp, chain, writer);
        filter.doFilter(req, resp, chain);
        verify(req, resp, chain, writer);
    }

    @Test
    void doFilter_noAuthHeader_passesThrough_evenWhenHandleFilterWouldThrow() throws Exception {
        // handleFilter is configured to throw, but must never be called when no header present
        TestableMcpFilter filter = new TestableMcpFilter(null, new UnauthorizedOperationException("should not reach auth"));

        HttpServletRequest req = mock(HttpServletRequest.class);
        HttpServletResponse resp = mock(HttpServletResponse.class);
        FilterChain chain = mock(FilterChain.class);

        expect(req.getRequestURI()).andReturn("/mcp/tool-call").once();
        expect(req.getContextPath()).andReturn("").once();
        expect(req.getHeader("Authorization")).andReturn(null).once();
        expect(req.getQueryString()).andReturn(null).once();
        expectBodyRead(req);
        expectLogCalls(req);
        // chain MUST be called — anonymous pass-through guaranteed
        // resp must NOT receive setStatus — no 401 generated for missing auth header
        chain.doFilter(anyObject(), eq(resp));
        expectLastCall().once();

        replay(req, resp, chain);
        filter.doFilter(req, resp, chain);
        verify(req, resp, chain);
    }

    @Test
    void doFilter_tokenExpiredException_wwwAuthIncludesInvalidToken() throws Exception {
        TokenExpiredException expired = new TokenExpiredException("session expired", null);
        TestableMcpFilter filter = new TestableMcpFilter(null, expired);

        HttpServletRequest req = mock(HttpServletRequest.class);
        HttpServletResponse resp = mock(HttpServletResponse.class);
        FilterChain chain = mock(FilterChain.class);
        PrintWriter writer = mock(PrintWriter.class);

        expect(req.getRequestURI()).andReturn("/mcp/tool-call").once();
        expect(req.getContextPath()).andReturn("").once();
        expect(req.getHeaderNames()).andReturn(java.util.Collections.emptyEnumeration()).once();
        expect(req.getHeader("Authorization")).andReturn("Bearer old.token").once();
        // auth-first: body is NOT buffered when auth fails — only getQueryString() is called
        expect(req.getQueryString()).andReturn(null).once();
        resp.setHeader(eq("WWW-Authenticate"), and(
                contains("Bearer resource_metadata="),
                and(contains("/.well-known/oauth-protected-resource/mcp"),
                    and(contains("error=\"invalid_token\""), contains("session expired")))));
        expectLastCall().once();
        resp.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        expectLastCall().once();
        resp.setContentType("application/json;charset=UTF-8");
        expectLastCall().once();
        expect(resp.getWriter()).andReturn(writer).once();
        writer.write(anyString());
        expectLastCall().once();
        expectLogCalls(req);

        replay(req, resp, chain, writer);
        filter.doFilter(req, resp, chain);
        verify(req, resp, chain, writer);
    }

    @Test
    void doFilter_anonymous_emptyBody_suppressesLog() throws Exception {
        TestableMcpFilter filter = new TestableMcpFilter(null, null);

        HttpServletRequest req = mock(HttpServletRequest.class);
        HttpServletResponse resp = mock(HttpServletResponse.class);
        FilterChain chain = mock(FilterChain.class);

        expect(req.getRequestURI()).andReturn("/mcp/tool-call").once();
        expect(req.getContextPath()).andReturn("").once();
        expect(req.getHeader("Authorization")).andReturn(null).once();
        expect(req.getQueryString()).andReturn(null).once();
        expectEmptyBodyRead(req);
        // toolLabel is "" — accessLogger must NOT be called; no expectLogCalls here
        chain.doFilter(anyObject(), eq(resp));
        expectLastCall().once();

        replay(req, resp, chain);
        filter.doFilter(req, resp, chain);
        verify(req, resp, chain);
    }

    @Test
    void doFilter_validUser_emptyBody_suppressesLog() throws Exception {
        User mockUser = new User();
        TestableMcpFilter filter = new TestableMcpFilter(mockUser, null);

        HttpServletRequest req = mock(HttpServletRequest.class);
        HttpServletResponse resp = mock(HttpServletResponse.class);
        FilterChain chain = mock(FilterChain.class);

        expect(req.getRequestURI()).andReturn("/mcp/tool-call").once();
        expect(req.getContextPath()).andReturn("").once();
        expect(req.getHeader("Authorization")).andReturn("Basic dXNlcjpwYXNz").once();
        expect(req.getHeaderNames()).andReturn(java.util.Collections.emptyEnumeration()).once();
        expect(req.getQueryString()).andReturn(null).once();
        req.setAttribute("User", mockUser);
        expectLastCall().once();
        expectEmptyBodyRead(req);
        // toolLabel is "" — accessLogger must NOT be called; no expectLogCalls here
        chain.doFilter(anyObject(), eq(resp));
        expectLastCall().once();

        replay(req, resp, chain);
        filter.doFilter(req, resp, chain);
        verify(req, resp, chain);
    }
}
