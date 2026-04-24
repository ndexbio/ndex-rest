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

class TestSearchNetworkTool {

    private ToolsService toolsService;
    private SearchNetworkTool tool;

    @BeforeEach
    void setUp() {
        toolsService = new ToolsService();
        tool = new SearchNetworkTool(toolsService);
    }

    // --- Tool spec metadata ---

    @Test
    void toSpec_hasCorrectToolName() {
        assertEquals(SearchNetworkTool.TOOL_NAME, tool.toSpec().tool().name());
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
    void inputSchema_requiresSearchString() {
        McpSchema.JsonSchema schema = tool.toSpec().tool().inputSchema();
        assertTrue(schema.required().contains("searchString"),
                "searchString should be in required list");
    }

    @Test
    void inputSchema_hasAllFiveProperties() {
        Map<String, Object> props = tool.toSpec().tool().inputSchema().properties();
        assertTrue(props.containsKey("searchString"));
        assertTrue(props.containsKey("accountName"));
        assertTrue(props.containsKey("includeGroups"));
        assertTrue(props.containsKey("start"));
        assertTrue(props.containsKey("size"));
    }

    @Test
    void inputSchema_onlySearchStringIsRequired() {
        McpSchema.JsonSchema schema = tool.toSpec().tool().inputSchema();
        assertEquals(1, schema.required().size(),
                "only searchString should be required");
    }

    // --- Handler error routing ---

    @Test
    void handle_returnsErrorResult_whenHttpRequestMissingFromContext() {
        // Transport context has no "ndexRequest" entry — handler receives null httpReq,
        // which causes NPE in SearchServiceV2, caught as generic Exception.
        McpSyncServerExchange exchange = EasyMock.mock(McpSyncServerExchange.class);
        EasyMock.expect(exchange.transportContext())
                .andReturn(McpTransportContext.EMPTY)
                .once();
        EasyMock.replay(exchange);

        McpSchema.CallToolResult result = invokeHandler(exchange, Map.of("searchString", "BRCA1"));

        assertTrue(result.isError(), "should return isError=true when httpReq is null");
        EasyMock.verify(exchange);
    }

    @Test
    void handle_returnsErrorResult_withMessageWhenSearchFails() {
        McpSyncServerExchange exchange = EasyMock.mock(McpSyncServerExchange.class);
        EasyMock.expect(exchange.transportContext())
                .andReturn(McpTransportContext.EMPTY)
                .once();
        EasyMock.replay(exchange);

        McpSchema.CallToolResult result = invokeHandler(exchange, Map.of("searchString", "BRCA1"));

        assertFalse(result.content().isEmpty(), "error result should carry a message");
        String text = ((McpSchema.TextContent) result.content().get(0)).text();
        assertTrue(text.startsWith("Search failed:"), "error message should start with 'Search failed:'");
    }

    @Test
    void handle_securityExceptionFromContext_returnsUnauthorizedResult() {
        McpTransportContext ctx = EasyMock.mock(McpTransportContext.class);
        EasyMock.expect(ctx.get("ndexRequest")).andThrow(new SecurityException("access denied")).once();
        McpSyncServerExchange exchange = EasyMock.mock(McpSyncServerExchange.class);
        EasyMock.expect(exchange.transportContext()).andReturn(ctx).once();
        EasyMock.replay(ctx, exchange);

        McpSchema.CallToolResult result = invokeHandler(exchange, Map.of("searchString", "test"));

        assertTrue(result.isError());
        assertEquals("401 Unauthorized", ((McpSchema.TextContent) result.content().get(0)).text());
        EasyMock.verify(ctx, exchange);
    }

    @Test
    void handle_usesDefaultStartAndSize_whenNotProvided() {
        // Passing only searchString; handler must not throw for missing optional fields.
        // Delegates to SearchServiceV2 which will fail (no DB) → generic error result.
        McpSyncServerExchange exchange = EasyMock.mock(McpSyncServerExchange.class);
        EasyMock.expect(exchange.transportContext())
                .andReturn(McpTransportContext.EMPTY)
                .once();
        EasyMock.replay(exchange);

        // Should not throw NullPointerException for missing optional args
        assertDoesNotThrow(() -> invokeHandler(exchange, Map.of("searchString", "test")));
        EasyMock.verify(exchange);
    }

    // Helper: invokes the registered handler synchronously
    private McpSchema.CallToolResult invokeHandler(McpSyncServerExchange exchange,
                                                    Map<String, Object> arguments) {
        McpServerFeatures.SyncToolSpecification spec = tool.toSpec();
        McpSchema.CallToolRequest request =
                new McpSchema.CallToolRequest(SearchNetworkTool.TOOL_NAME, arguments);
        return spec.callHandler().apply(exchange, request);
    }
}
