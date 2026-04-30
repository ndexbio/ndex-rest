package org.ndexbio.rest.mcp.tools;

import java.util.Map;

import io.modelcontextprotocol.common.McpTransportContext;
import io.modelcontextprotocol.server.McpServerFeatures;
import io.modelcontextprotocol.server.McpSyncServerExchange;
import io.modelcontextprotocol.spec.McpSchema;
import org.easymock.EasyMock;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.ndexbio.rest.mcp.ToolsService;

import static org.junit.jupiter.api.Assertions.*;

class TestGetNetworkSummaryTool {

    private ToolsService toolsService;
    private GetNetworkSummaryTool tool;

    @BeforeEach
    void setUp() {
        toolsService = new ToolsService();
        tool = new GetNetworkSummaryTool(toolsService);
    }

    // --- Tool spec metadata ---

    @Test
    void toSpec_hasCorrectToolName() {
        assertEquals(GetNetworkSummaryTool.TOOL_NAME, tool.toSpec().tool().name());
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
    void inputSchema_hasThreeProperties() {
        Map<String, Object> props = tool.toSpec().tool().inputSchema().properties();
        assertTrue(props.containsKey("networkId"));
        assertTrue(props.containsKey("accessKey"));
        assertTrue(props.containsKey("format"));
        assertEquals(3, props.size());
    }

    @Test
    void inputSchema_onlyNetworkIdIsRequired() {
        McpSchema.JsonSchema schema = tool.toSpec().tool().inputSchema();
        assertEquals(1, schema.required().size(),
                "only networkId should be required");
    }

    // --- Handler error routing ---

    @Test
    void handle_returnsErrorResult_whenHttpRequestMissingFromContext() {
        // Transport context has no "ndexRequest" entry — handler receives null httpReq,
        // which causes NPE in NetworkServiceV3, caught as generic Exception.
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
    void handle_missingNetworkIdArgument_doesNotThrow() {
        // Omitting networkId — handler must not throw NPE from test; error result expected.
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
                new McpSchema.CallToolRequest(GetNetworkSummaryTool.TOOL_NAME, arguments);
        return spec.callHandler().apply(exchange, request);
    }
}
