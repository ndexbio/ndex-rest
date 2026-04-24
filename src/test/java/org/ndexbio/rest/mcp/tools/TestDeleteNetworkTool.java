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
import org.ndexbio.rest.mcp.ToolsService;

import static org.junit.jupiter.api.Assertions.*;

class TestDeleteNetworkTool {

    private ToolsService toolsService;
    private DeleteNetworkTool tool;

    @BeforeEach
    void setUp() {
        toolsService = new ToolsService();
        tool = new DeleteNetworkTool(toolsService);
    }

    // --- Tool spec metadata ---

    @Test
    void toSpec_hasCorrectToolName() {
        assertEquals(DeleteNetworkTool.TOOL_NAME, tool.toSpec().tool().name());
    }

    @Test
    void toSpec_hasNonBlankDescription() {
        assertFalse(tool.toSpec().tool().description().isBlank());
    }

    @Test
    void inputSchema_typeIsObject() {
        McpSchema.JsonSchema schema = tool.toSpec().tool().inputSchema();
        assertNotNull(schema);
        assertEquals("object", schema.type());
    }

    @Test
    void inputSchema_requiresNetworkId() {
        McpSchema.JsonSchema schema = tool.toSpec().tool().inputSchema();
        assertTrue(schema.required().contains("networkId"),
                "networkId should be in required list");
    }

    @Test
    void inputSchema_hasTwoProperties() {
        Map<String, Object> props = tool.toSpec().tool().inputSchema().properties();
        assertTrue(props.containsKey("networkId"));
        assertTrue(props.containsKey("permanent"));
        assertEquals(2, props.size());
    }

    @Test
    void inputSchema_onlyNetworkIdIsRequired() {
        McpSchema.JsonSchema schema = tool.toSpec().tool().inputSchema();
        assertEquals(1, schema.required().size(),
                "only networkId should be required");
    }

    @Test
    void outputSchema_isPresent() {
        McpServerFeatures.SyncToolSpecification spec = tool.toSpec();
        assertNotNull(spec.tool().outputSchema(),
                "outputSchema should be set on the tool");
    }

    @Test
    void outputSchema_hasExpectedProperties() {
        Map<String, Object> outputSchema = tool.toSpec().tool().outputSchema();
        assertNotNull(outputSchema);
        Object props = outputSchema.get("properties");
        assertNotNull(props, "outputSchema should have a 'properties' entry");
        @SuppressWarnings("unchecked")
        Map<String, Object> propsMap = (Map<String, Object>) props;
        assertTrue(propsMap.containsKey("networkId"), "output schema missing 'networkId'");
        assertTrue(propsMap.containsKey("permanent"), "output schema missing 'permanent'");
        assertTrue(propsMap.containsKey("message"),   "output schema missing 'message'");
    }

    // --- Handler error routing ---

    @Test
    void handle_returnsErrorResult_whenHttpRequestMissingFromContext() {
        // Transport context has no "ndexRequest" — handler receives null httpReq,
        // causing NPE in getAttribute("User"), caught as generic Throwable.
        McpSyncServerExchange exchange = EasyMock.mock(McpSyncServerExchange.class);
        EasyMock.expect(exchange.transportContext())
                .andReturn(McpTransportContext.EMPTY)
                .once();
        EasyMock.replay(exchange);

        McpSchema.CallToolResult result = invokeHandler(exchange,
                Map.of("networkId", "f93f402c-86d4-11e7-a10d-0ac135e8bacf"));

        assertTrue(result.isError(), "should return isError=true when httpReq is null");
        EasyMock.verify(exchange);
    }

    @Test
    void handle_securityExceptionFromContext_returnsUnauthorizedResult() {
        McpTransportContext ctx = EasyMock.mock(McpTransportContext.class);
        EasyMock.expect(ctx.get("ndexRequest")).andThrow(new SecurityException("access denied")).once();
        McpSyncServerExchange exchange = EasyMock.mock(McpSyncServerExchange.class);
        EasyMock.expect(exchange.transportContext()).andReturn(ctx).once();
        EasyMock.replay(ctx, exchange);

        McpSchema.CallToolResult result = invokeHandler(exchange,
                Map.of("networkId", "f93f402c-86d4-11e7-a10d-0ac135e8bacf"));

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

        McpSchema.CallToolResult result = invokeHandler(exchange,
                Map.of("networkId", "f93f402c-86d4-11e7-a10d-0ac135e8bacf"));

        assertTrue(result.isError());
        assertEquals("401 Unauthorized", ((McpSchema.TextContent) result.content().get(0)).text());
        EasyMock.verify(httpReq, exchange);
    }

    @Test
    void handle_missingArguments_doesNotThrow() {
        // Omitting all arguments — handler must not propagate any exception from test.
        McpSyncServerExchange exchange = EasyMock.mock(McpSyncServerExchange.class);
        EasyMock.expect(exchange.transportContext())
                .andReturn(McpTransportContext.EMPTY)
                .once();
        EasyMock.replay(exchange);

        assertDoesNotThrow(() -> invokeHandler(exchange, Map.of()));
        EasyMock.verify(exchange);
    }

    // Helper: invokes the registered handler synchronously
    private McpSchema.CallToolResult invokeHandler(McpSyncServerExchange exchange,
                                                    Map<String, Object> arguments) {
        McpServerFeatures.SyncToolSpecification spec = tool.toSpec();
        McpSchema.CallToolRequest request =
                new McpSchema.CallToolRequest(DeleteNetworkTool.TOOL_NAME, arguments);
        return spec.callHandler().apply(exchange, request);
    }
}
