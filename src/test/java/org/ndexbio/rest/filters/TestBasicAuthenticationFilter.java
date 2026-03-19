package org.ndexbio.rest.filters;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Base64;

import jakarta.annotation.security.PermitAll;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.core.MultivaluedHashMap;
import jakarta.ws.rs.core.MultivaluedMap;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.UriInfo;

import org.easymock.EasyMock;
import org.jboss.resteasy.core.ResourceMethodInvoker;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.ndexbio.model.exceptions.NdexException;
import org.ndexbio.model.exceptions.UnauthorizedOperationException;
import org.ndexbio.model.object.User;
import org.ndexbio.rest.Configuration;
import org.ndexbio.rest.services.AuthenticationNotRequired;
import org.ndexbio.rest.services.NdexOpenFunction;

import com.auth0.jwt.exceptions.TokenExpiredException;

import static org.easymock.EasyMock.*;

class TestBasicAuthenticationFilter {

    // ── Methods helper: one method per annotation variant ───────────────────
    static class Methods {
        @PermitAll
        public static void permitAll() {}
        @NdexOpenFunction
        public static void openFunction() {}
        @AuthenticationNotRequired
        public static void authNotRequired() {}
        public static void restricted() {}
        public static void getOpenApi() {}
    }

    // ── StubFilter: overrides handleFilter() to return a canned value ────────
    static class StubFilter extends McpAuthFilter {
        User userResult;
        Exception exceptionToThrow;

        StubFilter() throws NdexException { super(); }

        @Override
        protected User handleFilter(MultivaluedMap<String, String> headers) throws Exception {
            if (exceptionToThrow != null) throw exceptionToThrow;
            return userResult;
        }
    }

    // ── ContextMocks: groups the three mocks built per-test ──────────────────
    record ContextMocks(ContainerRequestContext ctx, ResourceMethodInvoker invoker, UriInfo uriInfo) {}

    // ── shared state ─────────────────────────────────────────────────────────
    private StubFilter stub;
    private HttpServletRequest mockHttpRequest;

    @BeforeAll
    static void setupConfig() {
        Configuration config = EasyMock.mock(Configuration.class);
        expect(config.getUseADAuthentication()).andReturn(false).anyTimes();
        expect(config.getProperty("AUTHENTICATED_USER_ONLY")).andReturn(null).anyTimes();
        expect(config.getProperty("USE_KEYCLOAK_AUTHENTICATION")).andReturn(null).anyTimes();
        expect(config.getProperty("USE_GOOGLE_AUTHENTICATION")).andReturn(null).anyTimes();
        expect(config.getHostURI()).andReturn("https://ndexbio.org").anyTimes();
        Configuration.setInstance(config);
        EasyMock.replay(config);
    }

    @BeforeEach
    void setUp() throws Exception {
        stub = new StubFilter();

        mockHttpRequest = EasyMock.mock(HttpServletRequest.class);
        expect(mockHttpRequest.getHeader("X-FORWARDED-FOR")).andReturn(null).anyTimes();
        expect(mockHttpRequest.getRemoteAddr()).andReturn("127.0.0.1").anyTimes();
        expect(mockHttpRequest.getHeader("User-Agent")).andReturn("test").anyTimes();
        expect(mockHttpRequest.getHeader("NDEx-application")).andReturn(null).anyTimes();
        EasyMock.replay(mockHttpRequest);

        Field f = BasicAuthenticationFilter.class.getDeclaredField("httpRequest");
        f.setAccessible(true);
        f.set(stub, mockHttpRequest);
    }

    /**
     * Builds the three mocks shared by every test. Does NOT call replay() —
     * callers add any remaining expectations then call replay() themselves.
     */
    private ContextMocks buildContext(Method method, String authHeader) {
        ResourceMethodInvoker invoker = EasyMock.mock(ResourceMethodInvoker.class);
        expect(invoker.getMethod()).andReturn(method).anyTimes();

        UriInfo uriInfo = EasyMock.mock(UriInfo.class);
        expect(uriInfo.getPath(true)).andReturn("/test").anyTimes();
        expect(uriInfo.getPath()).andReturn("/test").anyTimes();
        expect(uriInfo.getQueryParameters()).andReturn(new MultivaluedHashMap<>()).anyTimes();
        expect(uriInfo.getPathParameters()).andReturn(new MultivaluedHashMap<>()).anyTimes();

        MultivaluedHashMap<String, String> headers = new MultivaluedHashMap<>();
        if (authHeader != null) headers.add("Authorization", authHeader);

        ContainerRequestContext ctx = EasyMock.mock(ContainerRequestContext.class);
        expect(ctx.getProperty("org.jboss.resteasy.core.ResourceMethodInvoker")).andReturn(invoker).once();
        expect(ctx.getHeaders()).andReturn(headers).anyTimes();
        expect(ctx.getUriInfo()).andReturn(uriInfo).anyTimes();
        expect(ctx.getMethod()).andReturn("GET").anyTimes();

        return new ContextMocks(ctx, invoker, uriInfo);
    }

    // ── tests ─────────────────────────────────────────────────────────────────

    @Test
    void filter_getOpenApi_bypassesAuth() throws Exception {
        Method method = Methods.class.getMethod("getOpenApi");
        ContextMocks m = buildContext(method, null);
        replay(m.ctx(), m.invoker(), m.uriInfo());

        stub.filter(m.ctx());

        verify(m.ctx(), m.invoker(), m.uriInfo());
    }

    @Test
    void filter_anonymous_permitAll_passesThrough() throws Exception {
        stub.userResult = null;
        Method method = Methods.class.getMethod("permitAll");
        ContextMocks m = buildContext(method, null);
        replay(m.ctx(), m.invoker(), m.uriInfo());

        stub.filter(m.ctx());

        verify(m.ctx(), m.invoker(), m.uriInfo());
    }

    @Test
    void filter_anonymous_openFunction_passesThrough() throws Exception {
        stub.userResult = null;
        Method method = Methods.class.getMethod("openFunction");
        ContextMocks m = buildContext(method, null);
        replay(m.ctx(), m.invoker(), m.uriInfo());

        stub.filter(m.ctx());

        verify(m.ctx(), m.invoker(), m.uriInfo());
    }

    @Test
    void filter_anonymous_authNotRequired_passesThrough() throws Exception {
        stub.userResult = null;
        Method method = Methods.class.getMethod("authNotRequired");
        ContextMocks m = buildContext(method, null);
        replay(m.ctx(), m.invoker(), m.uriInfo());

        stub.filter(m.ctx());

        verify(m.ctx(), m.invoker(), m.uriInfo());
    }

    @Test
    void filter_anonymous_restrictedMethod_abortsWith401() throws Exception {
        stub.userResult = null;
        Method method = Methods.class.getMethod("restricted");
        ContextMocks m = buildContext(method, null);
        m.ctx().abortWith(anyObject(Response.class));
        expectLastCall().once();
        replay(m.ctx(), m.invoker(), m.uriInfo());

        stub.filter(m.ctx());

        verify(m.ctx(), m.invoker(), m.uriInfo());
    }

    @Test
    void filter_authenticatedUser_setsPropertyAndPassesThrough() throws Exception {
        User user = new User();
        stub.userResult = user;
        Method method = Methods.class.getMethod("restricted");
        String basicHeader = "Basic " + Base64.getEncoder().encodeToString("user:pass".getBytes());
        ContextMocks m = buildContext(method, basicHeader);
        m.ctx().setProperty(eq("User"), same(user));
        expectLastCall().once();
        replay(m.ctx(), m.invoker(), m.uriInfo());

        stub.filter(m.ctx());

        verify(m.ctx(), m.invoker(), m.uriInfo());
    }

    @Test
    void filter_tokenExpiredException_abortsWith401() throws Exception {
        stub.exceptionToThrow = new TokenExpiredException("expired", null);
        Method method = Methods.class.getMethod("restricted");
        String bearerHeader = "Bearer sometoken";
        ContextMocks m = buildContext(method, bearerHeader);
        m.ctx().abortWith(anyObject(Response.class));
        expectLastCall().once();
        replay(m.ctx(), m.invoker(), m.uriInfo());

        stub.filter(m.ctx());

        verify(m.ctx(), m.invoker(), m.uriInfo());
    }

    @Test
    void filter_unauthorizedOperationException_abortsWith401() throws Exception {
        stub.exceptionToThrow = new UnauthorizedOperationException("invalid password");
        Method method = Methods.class.getMethod("restricted");
        String basicHeader = "Basic " + Base64.getEncoder().encodeToString("user:pass".getBytes());
        ContextMocks m = buildContext(method, basicHeader);
        m.ctx().abortWith(anyObject(Response.class));
        expectLastCall().once();
        replay(m.ctx(), m.invoker(), m.uriInfo());

        stub.filter(m.ctx());

        verify(m.ctx(), m.invoker(), m.uriInfo());
    }

    @Test
    void filter_nullUserWithCredentials_abortsWith401() throws Exception {
        stub.userResult = null;
        Method method = Methods.class.getMethod("restricted");
        String basicHeader = "Basic " + Base64.getEncoder().encodeToString("user:pass".getBytes());
        ContextMocks m = buildContext(method, basicHeader);
        m.ctx().abortWith(anyObject(Response.class));
        expectLastCall().once();
        replay(m.ctx(), m.invoker(), m.uriInfo());

        stub.filter(m.ctx());

        verify(m.ctx(), m.invoker(), m.uriInfo());
    }
}
