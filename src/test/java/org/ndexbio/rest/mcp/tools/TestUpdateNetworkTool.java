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
import org.ndexbio.rest.mcp.UploadService;

import static org.junit.jupiter.api.Assertions.*;

class TestUpdateNetworkTool {

    private ToolsService toolsService;
    private UpdateNetworkTool tool;

    @BeforeEach
    void setUp() {
        toolsService = new ToolsService();
        tool = new UpdateNetworkTool(toolsService, new UploadService());
    }

    // --- Tool spec metadata ---

    @Test
    void toSpec_hasCorrectToolName() {
        assertEquals(UpdateNetworkTool.TOOL_NAME, tool.toSpec().tool().name());
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
    void inputSchema_hasSevenProperties() {
        Map<String, Object> props = tool.toSpec().tool().inputSchema().properties();
        assertTrue(props.containsKey("networkId"));
        assertTrue(props.containsKey("cx2Network"));
        assertTrue(props.containsKey("cx2NetworkSize"));
        assertTrue(props.containsKey("cx2NetworkChunkTotalCount"));
        assertTrue(props.containsKey("cx2NetworkCurrentChunkNumber"));
        assertTrue(props.containsKey("visibility"));
        assertTrue(props.containsKey("extraNodeIndex"));
        assertEquals(7, props.size());
    }

    @Test
    void inputSchema_requiresFiveParams() {
        McpSchema.JsonSchema schema = tool.toSpec().tool().inputSchema();
        assertEquals(5, schema.required().size(),
                "exactly 5 params should be required");
        assertTrue(schema.required().contains("networkId"));
        assertTrue(schema.required().contains("cx2Network"));
        assertTrue(schema.required().contains("cx2NetworkSize"));
        assertTrue(schema.required().contains("cx2NetworkChunkTotalCount"));
        assertTrue(schema.required().contains("cx2NetworkCurrentChunkNumber"));
    }

    @Test
    void outputSchema_isNotNull() {
        assertNotNull(tool.toSpec().tool().outputSchema());
    }

    @Test
    void outputSchema_hasUpdateStatusProperty() {
        Map<String, Object> outputSchema = tool.toSpec().tool().outputSchema();
        assertNotNull(outputSchema);
        @SuppressWarnings("unchecked")
        Map<String, Object> props = (Map<String, Object>) outputSchema.get("properties");
        assertNotNull(props, "outputSchema should have 'properties'");
        assertTrue(props.containsKey("updateStatus"), "outputSchema should have 'updateStatus' property");
    }

    // --- Handler error routing ---

    @Test
    void handle_returnsErrorResult_whenHttpRequestMissingFromContext() {
        // Transport context has no "ndexRequest" entry — handler receives null httpReq,
        // which causes NPE in httpReq.getAttribute("User"), caught by generic handler.
        McpSyncServerExchange exchange = EasyMock.mock(McpSyncServerExchange.class);
        EasyMock.expect(exchange.transportContext())
                .andReturn(McpTransportContext.EMPTY)
                .once();
        EasyMock.replay(exchange);

        McpSchema.CallToolResult result = invokeHandler(exchange,
                Map.of("networkId", "f93f402c-86d4-11e7-a10d-0ac135e8bacf",
                       "cx2Network", "[{}]",
                       "cx2NetworkSize", 4,
                       "cx2NetworkChunkTotalCount", 1,
                       "cx2NetworkCurrentChunkNumber", 1));

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
                Map.of("networkId", "f93f402c-86d4-11e7-a10d-0ac135e8bacf",
                       "cx2Network", "[{}]",
                       "cx2NetworkSize", 4,
                       "cx2NetworkChunkTotalCount", 1,
                       "cx2NetworkCurrentChunkNumber", 1));

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

        assertDoesNotThrow(() -> invokeHandler(exchange,
                Map.of("cx2Network", "[{}]",
                       "cx2NetworkSize", 4,
                       "cx2NetworkChunkTotalCount", 1,
                       "cx2NetworkCurrentChunkNumber", 1)));
        EasyMock.verify(exchange);
    }

    @Test
    void handle_returnsError_whenChunkExceedsMaxChunkSize() {
        User user = new User();

        HttpServletRequest httpReq = EasyMock.mock(HttpServletRequest.class);
        EasyMock.expect(httpReq.getAttribute("User")).andReturn(user).once();

        McpTransportContext ctx = McpTransportContext.create(
                Map.of("ndexRequest", httpReq));

        McpSyncServerExchange exchange = EasyMock.mock(McpSyncServerExchange.class);
        EasyMock.expect(exchange.transportContext()).andReturn(ctx).once();
        EasyMock.replay(httpReq, exchange);

        // Build a string that is 1 byte over the chunk limit
        String oversizedChunk = "x".repeat((int) UpdateNetworkTool.MAX_CHUNK_SIZE_BYTES + 1);

        McpSchema.CallToolResult result = invokeHandler(exchange,
                Map.of("networkId", "f93f402c-86d4-11e7-a10d-0ac135e8bacf",
                       "cx2Network", oversizedChunk,
                       "cx2NetworkSize", (long) UpdateNetworkTool.MAX_CHUNK_SIZE_BYTES + 1,
                       "cx2NetworkChunkTotalCount", 1,
                       "cx2NetworkCurrentChunkNumber", 1));

        assertTrue(result.isError());
        String msg = ((McpSchema.TextContent) result.content().get(0)).text();
        assertTrue(msg.contains("exceeds the maximum allowed chunk size"),
                "error should mention chunk size limit");
        assertTrue(msg.contains("Split"), "error should instruct caller to split into chunks");
        EasyMock.verify(httpReq, exchange);
    }

    @Test
    void handle_returnsUnauthorized_whenNoUser() {
        HttpServletRequest httpReq = EasyMock.mock(HttpServletRequest.class);
        EasyMock.expect(httpReq.getAttribute("User")).andReturn(null).once();

        McpTransportContext ctx = McpTransportContext.create(
                Map.of("ndexRequest", httpReq));

        McpSyncServerExchange exchange = EasyMock.mock(McpSyncServerExchange.class);
        EasyMock.expect(exchange.transportContext()).andReturn(ctx).once();
        EasyMock.replay(httpReq, exchange);

        McpSchema.CallToolResult result = invokeHandler(exchange,
                Map.of("networkId", "f93f402c-86d4-11e7-a10d-0ac135e8bacf",
                       "cx2Network", "[{}]",
                       "cx2NetworkSize", 4,
                       "cx2NetworkChunkTotalCount", 1,
                       "cx2NetworkCurrentChunkNumber", 1));

        assertTrue(result.isError());
        assertEquals("401 Unauthorized", ((McpSchema.TextContent) result.content().get(0)).text());
        EasyMock.verify(httpReq, exchange);
    }

    @Test
    void handle_returnsError_whenNoSessionId() {
        User user = new User();

        HttpServletRequest httpReq = EasyMock.mock(HttpServletRequest.class);
        EasyMock.expect(httpReq.getAttribute("User")).andReturn(user).once();

        McpTransportContext ctx = McpTransportContext.create(
                Map.of("ndexRequest", httpReq));

        McpSyncServerExchange exchange = EasyMock.mock(McpSyncServerExchange.class);
        EasyMock.expect(exchange.transportContext()).andReturn(ctx).once();
        EasyMock.expect(exchange.sessionId()).andReturn(null).once();
        EasyMock.replay(httpReq, exchange);

        McpSchema.CallToolResult result = invokeHandler(exchange,
                Map.of("networkId", "f93f402c-86d4-11e7-a10d-0ac135e8bacf",
                       "cx2Network", "[{}]",
                       "cx2NetworkSize", 4,
                       "cx2NetworkChunkTotalCount", 1,
                       "cx2NetworkCurrentChunkNumber", 1));

        assertTrue(result.isError());
        EasyMock.verify(httpReq, exchange);
    }

    @Test
    void handle_returnsError_whenNetworkSizeExceedsCap() {
        User user = new User();

        HttpServletRequest httpReq = EasyMock.mock(HttpServletRequest.class);
        EasyMock.expect(httpReq.getAttribute("User")).andReturn(user).once();

        McpTransportContext ctx = McpTransportContext.create(
                Map.of("ndexRequest", httpReq));

        McpSyncServerExchange exchange = EasyMock.mock(McpSyncServerExchange.class);
        EasyMock.expect(exchange.transportContext()).andReturn(ctx).once();
        EasyMock.replay(httpReq, exchange);

        long oversized = UpdateNetworkTool.MAX_NETWORK_SIZE_BYTES + 1L;

        McpSchema.CallToolResult result = invokeHandler(exchange,
                Map.of("networkId", "f93f402c-86d4-11e7-a10d-0ac135e8bacf",
                       "cx2Network", "[{}]",
                       "cx2NetworkSize", oversized,
                       "cx2NetworkChunkTotalCount", 1,
                       "cx2NetworkCurrentChunkNumber", 1));

        assertTrue(result.isError());
        EasyMock.verify(httpReq, exchange);
    }

    @Test
    void handle_returnsAck_whenNotFinalChunk() {
        User user = new User();

        HttpServletRequest httpReq = EasyMock.mock(HttpServletRequest.class);
        EasyMock.expect(httpReq.getAttribute("User")).andReturn(user).once();

        McpTransportContext ctx = McpTransportContext.create(
                Map.of("ndexRequest", httpReq));

        McpSyncServerExchange exchange = EasyMock.mock(McpSyncServerExchange.class);
        EasyMock.expect(exchange.transportContext()).andReturn(ctx).once();
        EasyMock.expect(exchange.sessionId()).andReturn("test-session-id").once();
        EasyMock.replay(httpReq, exchange);

        // Chunk 1 of 2
        McpSchema.CallToolResult result = invokeHandler(exchange,
                Map.of("networkId", "f93f402c-86d4-11e7-a10d-0ac135e8bacf",
                       "cx2Network", "[{\"firstChunk\":true}]",
                       "cx2NetworkSize", 100,
                       "cx2NetworkChunkTotalCount", 2,
                       "cx2NetworkCurrentChunkNumber", 1));

        assertFalse(result.isError(), "chunk ack should not be an error");
        assertNotNull(result.structuredContent(), "structured content should be present");

        // Verify isSubmitted=false
        @SuppressWarnings("unchecked")
        var ack = (java.util.Map<?, ?>) new com.fasterxml.jackson.databind.ObjectMapper()
                .convertValue(result.structuredContent(), java.util.Map.class);
        assertEquals(false, ack.get("isSubmitted"));

        EasyMock.verify(httpReq, exchange);
    }

    // Helper: invokes the registered handler synchronously
    private McpSchema.CallToolResult invokeHandler(McpSyncServerExchange exchange,
                                                    Map<String, Object> arguments) {
        McpServerFeatures.SyncToolSpecification spec = tool.toSpec();
        McpSchema.CallToolRequest request =
                new McpSchema.CallToolRequest(UpdateNetworkTool.TOOL_NAME, arguments);
        return spec.callHandler().apply(exchange, request);
    }
}
