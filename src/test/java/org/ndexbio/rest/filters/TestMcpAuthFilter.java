package org.ndexbio.rest.filters;

import java.io.IOException;
import java.io.PrintWriter;

import jakarta.servlet.FilterChain;
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

    // ── inner stub ──────────────────────────────────────────────────────────

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

    // ── tests ────────────────────────────────────────────────────────────────

    @Test
    void doFilter_anonymous_passesThrough() throws Exception {
        TestableMcpFilter filter = new TestableMcpFilter(null, null);

        HttpServletRequest req = mock(HttpServletRequest.class);
        HttpServletResponse resp = mock(HttpServletResponse.class);
        FilterChain chain = mock(FilterChain.class);

        expect(req.getRequestURI()).andReturn("/mcp/tool-call").once();
        expect(req.getContextPath()).andReturn("").once();
        expect(req.getHeaderNames()).andReturn(java.util.Collections.emptyEnumeration()).once();
        chain.doFilter(req, resp);
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
        expect(req.getHeaderNames()).andReturn(java.util.Collections.emptyEnumeration()).once();
        req.setAttribute("User", mockUser);
        expectLastCall().once();
        chain.doFilter(req, resp);
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
        resp.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        expectLastCall().once();
        resp.setContentType("application/json;charset=UTF-8");
        expectLastCall().once();
        expect(resp.getWriter()).andReturn(writer).once();
        writer.write(anyString());
        expectLastCall().once();
        // chain must NOT be called

        replay(req, resp, chain, writer);
        filter.doFilter(req, resp, chain);
        verify(req, resp, chain, writer);
    }

    @Test
    void doFilter_noAuthException_401_withWwwAuthenticate() throws Exception {
        TestableMcpFilter filter = new TestableMcpFilter(null, new UnauthorizedOperationException("no auth"));

        HttpServletRequest req = mock(HttpServletRequest.class);
        HttpServletResponse resp = mock(HttpServletResponse.class);
        FilterChain chain = mock(FilterChain.class);
        PrintWriter writer = mock(PrintWriter.class);

        expect(req.getRequestURI()).andReturn("/mcp/tool-call").once();
        expect(req.getContextPath()).andReturn("").once();
        expect(req.getHeaderNames()).andReturn(java.util.Collections.emptyEnumeration()).once();
        expect(req.getHeader("Authorization")).andReturn(null).once();
        resp.setHeader(eq("WWW-Authenticate"), contains("Bearer resource_metadata="));
        expectLastCall().once();
        resp.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        expectLastCall().once();
        resp.setContentType("application/json;charset=UTF-8");
        expectLastCall().once();
        expect(resp.getWriter()).andReturn(writer).once();
        writer.write(anyString());
        expectLastCall().once();
        // chain must NOT be called

        replay(req, resp, chain, writer);
        filter.doFilter(req, resp, chain);
        verify(req, resp, chain, writer);
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
        resp.setHeader(eq("WWW-Authenticate"), and(
                contains("Bearer resource_metadata="),
                and(contains("error=\"invalid_token\""), contains("session expired"))));
        expectLastCall().once();
        resp.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        expectLastCall().once();
        resp.setContentType("application/json;charset=UTF-8");
        expectLastCall().once();
        expect(resp.getWriter()).andReturn(writer).once();
        writer.write(anyString());
        expectLastCall().once();

        replay(req, resp, chain, writer);
        filter.doFilter(req, resp, chain);
        verify(req, resp, chain, writer);
    }
}
