package org.ndexbio.rest.mcp.tools;

import java.util.Map;

import io.modelcontextprotocol.common.McpTransportContext;
import io.modelcontextprotocol.server.McpServerFeatures;
import io.modelcontextprotocol.server.McpSyncServerExchange;
import io.modelcontextprotocol.spec.McpSchema;
import jakarta.servlet.http.HttpServletRequest;
import org.easymock.EasyMock;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.ndexbio.model.object.User;
import org.ndexbio.rest.mcp.ToolsService;

import static org.junit.jupiter.api.Assertions.*;

class TestGetUserNetworksTool {

    private ToolsService toolsService;
    private GetUserNetworksTool tool;

    @BeforeEach
    void setUp() {
        toolsService = new ToolsService();
        tool = new GetUserNetworksTool(toolsService);
    }

    // --- Tool spec metadata ---

    @Test
    void toSpec_hasCorrectToolName() {
        assertEquals(GetUserNetworksTool.TOOL_NAME, tool.toSpec().tool().name());
    }

    @Test
    void toSpec_hasNonBlankDescription() {
        assertFalse(tool.toSpec().tool().description().isBlank());
    }

    // --- Input schema ---

    @Test
    void inputSchema_typeIsObject() {
        McpSchema.JsonSchema schema = tool.toSpec().tool().inputSchema();
        assertNotNull(schema);
        assertEquals("object", schema.type());
    }

    @Test
    void inputSchema_hasNoRequiredFields() {
        McpSchema.JsonSchema schema = tool.toSpec().tool().inputSchema();
        assertTrue(schema.required() == null || schema.required().isEmpty(),
                "get_user_networks should have no required fields");
    }

    @Test
    void inputSchema_hasExactlyTwoProperties() {
        Map<String, Object> props = tool.toSpec().tool().inputSchema().properties();
        assertTrue(props.containsKey("offset"), "schema should have 'offset' property");
        assertTrue(props.containsKey("limit"),  "schema should have 'limit' property");
        assertEquals(2, props.size(), "schema should have exactly 2 properties");
    }

    // --- Handler error routing ---

    @Test
    void handle_returnsErrorResult_whenHttpRequestMissingFromContext() {
        // EMPTY context → httpReq is null → NPE in UserServiceV2 caught as Throwable → isError
        McpSyncServerExchange exchange = EasyMock.mock(McpSyncServerExchange.class);
        EasyMock.expect(exchange.transportContext())
                .andReturn(McpTransportContext.EMPTY)
                .once();
        EasyMock.replay(exchange);

        McpSchema.CallToolResult result = invokeHandler(exchange, Map.of());

        assertTrue(result.isError(), "should return isError=true when httpReq is null");
        EasyMock.verify(exchange);
    }

    @Test
    void handle_securityExceptionFromContext_returnsUnauthorizedResult() {
        McpTransportContext ctx = EasyMock.mock(McpTransportContext.class);
        EasyMock.expect(ctx.get("ndexRequest"))
                .andThrow(new SecurityException("access denied"))
                .once();
        McpSyncServerExchange exchange = EasyMock.mock(McpSyncServerExchange.class);
        EasyMock.expect(exchange.transportContext()).andReturn(ctx).once();
        EasyMock.replay(ctx, exchange);

        McpSchema.CallToolResult result = invokeHandler(exchange, Map.of());

        assertTrue(result.isError());
        assertEquals("401 Unauthorized", ((McpSchema.TextContent) result.content().get(0)).text());
        EasyMock.verify(ctx, exchange);
    }

    @Test
    void handle_noUser_returnsUnauthorizedResult() {
        HttpServletRequest httpReq = EasyMock.mock(HttpServletRequest.class);
        EasyMock.expect(httpReq.getAttribute("User")).andReturn(null).once();
        McpTransportContext ctx = McpTransportContext.create(Map.of("ndexRequest", httpReq));
        McpSyncServerExchange exchange = EasyMock.mock(McpSyncServerExchange.class);
        EasyMock.expect(exchange.transportContext()).andReturn(ctx).once();
        EasyMock.replay(httpReq, exchange);

        McpSchema.CallToolResult result = invokeHandler(exchange, Map.of());

        assertTrue(result.isError());
        assertEquals("401 Unauthorized", ((McpSchema.TextContent) result.content().get(0)).text());
        EasyMock.verify(httpReq, exchange);
    }

    @Test
    void handle_missingArguments_doesNotThrow() {
        // EMPTY context, empty args — handler must not throw; error result expected.
        McpSyncServerExchange exchange = EasyMock.mock(McpSyncServerExchange.class);
        EasyMock.expect(exchange.transportContext())
                .andReturn(McpTransportContext.EMPTY)
                .once();
        EasyMock.replay(exchange);

        assertDoesNotThrow(() -> invokeHandler(exchange, Map.of()));
        EasyMock.verify(exchange);
    }

    @Test
    void handle_withValidUser_failsAtService() {
        // Valid User present, but no DB → service call throws → isError=true
        // Also exercises the offset/limit == null → default 0 branch.
        User user = new User();
        HttpServletRequest httpReq = EasyMock.mock(HttpServletRequest.class);
        EasyMock.expect(httpReq.getAttribute("User")).andReturn(user).anyTimes();
        McpTransportContext ctx = McpTransportContext.create(Map.of("ndexRequest", httpReq));
        McpSyncServerExchange exchange = EasyMock.mock(McpSyncServerExchange.class);
        EasyMock.expect(exchange.transportContext()).andReturn(ctx).once();
        EasyMock.replay(httpReq, exchange);

        McpSchema.CallToolResult result = invokeHandler(exchange, Map.of());

        assertTrue(result.isError(), "should return isError=true when service call fails (no DB)");
        EasyMock.verify(httpReq, exchange);
    }

    // Helper: invokes the registered handler synchronously
    private McpSchema.CallToolResult invokeHandler(McpSyncServerExchange exchange,
                                                    Map<String, Object> arguments) {
        McpServerFeatures.SyncToolSpecification spec = tool.toSpec();
        McpSchema.CallToolRequest request =
                new McpSchema.CallToolRequest(GetUserNetworksTool.TOOL_NAME, arguments);
        return spec.callHandler().apply(exchange, request);
    }
}
