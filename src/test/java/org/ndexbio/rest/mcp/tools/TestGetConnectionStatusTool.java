package org.ndexbio.rest.mcp.tools;

import java.util.Map;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.modelcontextprotocol.common.McpTransportContext;
import io.modelcontextprotocol.server.McpServerFeatures;
import io.modelcontextprotocol.server.McpSyncServerExchange;
import io.modelcontextprotocol.spec.McpSchema;
import org.easymock.EasyMock;
import org.junit.jupiter.api.Test;
import org.ndexbio.model.object.User;

import static org.junit.jupiter.api.Assertions.*;

class TestGetConnectionStatusTool {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private GetConnectionStatusTool tool(String hostUri) {
        return new GetConnectionStatusTool(() -> hostUri);
    }

    // --- Tool spec metadata ---

    @Test
    void toSpec_hasCorrectToolName() {
        assertEquals(GetConnectionStatusTool.TOOL_NAME, tool(null).toSpec().tool().name());
    }

    @Test
    void toSpec_hasNonBlankDescription() {
        assertFalse(tool(null).toSpec().tool().description().isBlank());
    }

    // --- Input schema ---

    @Test
    void inputSchema_typeIsObject() {
        McpSchema.JsonSchema schema = tool(null).toSpec().tool().inputSchema();
        assertNotNull(schema);
        assertEquals("object", schema.type());
    }

    @Test
    void inputSchema_hasNoRequiredFields() {
        McpSchema.JsonSchema schema = tool(null).toSpec().tool().inputSchema();
        assertTrue(schema.required() == null || schema.required().isEmpty(),
                "get_connection_status should have no required fields");
    }

    @Test
    void inputSchema_hasNoProperties() {
        McpSchema.JsonSchema schema = tool(null).toSpec().tool().inputSchema();
        assertTrue(schema.properties() == null || schema.properties().isEmpty(),
                "get_connection_status should have no input properties");
    }

    // --- Output schema ---

    @Test
    void outputSchema_isNotNull() {
        assertNotNull(tool(null).toSpec().tool().outputSchema(),
                "tool should declare an output schema");
    }

    // --- extractHost (package-private static, tested directly) ---

    @Test
    void extractHost_httpUrl_returnsHost() {
        assertEquals("dev.ndexbio.org",
                GetConnectionStatusTool.extractHost("http://dev.ndexbio.org:8080"));
    }

    @Test
    void extractHost_httpsUrl_returnsHost() {
        assertEquals("www.ndexbio.org",
                GetConnectionStatusTool.extractHost("https://www.ndexbio.org"));
    }

    @Test
    void extractHost_malformedUri_returnsLocalhost() {
        assertEquals("localhost",
                GetConnectionStatusTool.extractHost("not a uri :// !!!"));
    }

    @Test
    void extractHost_nullInput_returnsLocalhost() {
        assertEquals("localhost", GetConnectionStatusTool.extractHost(null));
    }

    @Test
    void extractHost_relativeUriNullHost_returnsLocalhost() {
        // "relative/path" parses without exception but URI.getHost() returns null
        assertEquals("localhost", GetConnectionStatusTool.extractHost("relative/path"));
    }

    // --- Handler paths ---

    @Test
    void handle_anonymousUser_returnsUnauthenticated() throws Exception {
        McpSyncServerExchange exchange = EasyMock.mock(McpSyncServerExchange.class);
        EasyMock.expect(exchange.transportContext()).andReturn(McpTransportContext.EMPTY).once();
        EasyMock.replay(exchange);

        McpSchema.CallToolResult result = invokeHandler(tool(null), exchange);

        assertFalse(result.isError());
        String json = toJson(result);
        assertTrue(json.contains("\"authenticated\":false"), "expected authenticated:false in: " + json);
        assertTrue(json.contains("\"username\":\"anonymous\""), "expected username:anonymous in: " + json);
        assertTrue(json.contains("\"server\":\"localhost\""), "expected server:localhost in: " + json);
        EasyMock.verify(exchange);
    }

    @Test
    void handle_authenticatedUser_returnsAuthenticatedWithUsername() throws Exception {
        User user = new User();
        user.setUserName("testuser");
        McpTransportContext ctx = McpTransportContext.create(Map.of("ndexUser", user));
        McpSyncServerExchange exchange = EasyMock.mock(McpSyncServerExchange.class);
        EasyMock.expect(exchange.transportContext()).andReturn(ctx).once();
        EasyMock.replay(exchange);

        McpSchema.CallToolResult result = invokeHandler(tool(null), exchange);

        assertFalse(result.isError());
        String json = toJson(result);
        assertTrue(json.contains("\"authenticated\":true"), "expected authenticated:true in: " + json);
        assertTrue(json.contains("\"username\":\"testuser\""), "expected username:testuser in: " + json);
        EasyMock.verify(exchange);
    }

    @Test
    void handle_configLocatorReturnsHostUri_serverParsedCorrectly() throws Exception {
        McpSyncServerExchange exchange = EasyMock.mock(McpSyncServerExchange.class);
        EasyMock.expect(exchange.transportContext()).andReturn(McpTransportContext.EMPTY).once();
        EasyMock.replay(exchange);

        McpSchema.CallToolResult result =
                invokeHandler(tool("https://demo.ndexbio.org"), exchange);

        assertFalse(result.isError());
        String json = toJson(result);
        assertTrue(json.contains("\"server\":\"demo.ndexbio.org\""),
                "expected server:demo.ndexbio.org in: " + json);
        EasyMock.verify(exchange);
    }

    @Test
    void handle_securityExceptionFromContext_returnsErrorResult() {
        McpTransportContext ctx = EasyMock.mock(McpTransportContext.class);
        EasyMock.expect(ctx.get("ndexUser"))
                .andThrow(new SecurityException("access denied"))
                .once();
        McpSyncServerExchange exchange = EasyMock.mock(McpSyncServerExchange.class);
        EasyMock.expect(exchange.transportContext()).andReturn(ctx).once();
        EasyMock.replay(ctx, exchange);

        McpSchema.CallToolResult result = invokeHandler(tool(null), exchange);

        assertTrue(result.isError());
        EasyMock.verify(ctx, exchange);
    }

    // --- Helpers ---

    private McpSchema.CallToolResult invokeHandler(GetConnectionStatusTool t,
                                                    McpSyncServerExchange exchange) {
        McpServerFeatures.SyncToolSpecification spec = t.toSpec();
        McpSchema.CallToolRequest request =
                new McpSchema.CallToolRequest(GetConnectionStatusTool.TOOL_NAME, Map.of());
        return spec.callHandler().apply(exchange, request);
    }

    private String toJson(McpSchema.CallToolResult result) throws Exception {
        return MAPPER.writeValueAsString(result.structuredContent());
    }
}
