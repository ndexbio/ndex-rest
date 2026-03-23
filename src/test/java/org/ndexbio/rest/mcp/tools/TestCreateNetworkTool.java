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

class TestCreateNetworkTool {

    private ToolsService toolsService;
    private CreateNetworkTool tool;

    @BeforeEach
    void setUp() {
        toolsService = new ToolsService();
        tool = new CreateNetworkTool(toolsService, new UploadService());
    }

    // --- Tool spec metadata ---

    @Test
    void toSpec_hasCorrectToolName() {
        assertEquals(CreateNetworkTool.TOOL_NAME, tool.toSpec().tool().name());
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
    void inputSchema_requiresCx2Network() {
        McpSchema.JsonSchema schema = tool.toSpec().tool().inputSchema();
        assertTrue(schema.required().contains("cx2Network"),
                "cx2Network should be in required list");
    }

    @Test
    void inputSchema_hasSevenProperties() {
        Map<String, Object> props = tool.toSpec().tool().inputSchema().properties();
        assertTrue(props.containsKey("cx2Network"));
        assertTrue(props.containsKey("cx2NetworkSize"));
        assertTrue(props.containsKey("cx2NetworkChunkTotalCount"));
        assertTrue(props.containsKey("cx2NetworkCurrentChunkNumber"));
        assertTrue(props.containsKey("visibility"));
        assertTrue(props.containsKey("extraNodeIndex"));
        assertTrue(props.containsKey("folderId"));
        assertEquals(7, props.size());
    }

    @Test
    void inputSchema_requiresFourParams() {
        McpSchema.JsonSchema schema = tool.toSpec().tool().inputSchema();
        assertEquals(4, schema.required().size(),
                "exactly 4 params should be required");
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
    void outputSchema_hasCreateStatusProperty() {
        Map<String, Object> outputSchema = tool.toSpec().tool().outputSchema();
        assertNotNull(outputSchema);
        @SuppressWarnings("unchecked")
        Map<String, Object> props = (Map<String, Object>) outputSchema.get("properties");
        assertNotNull(props, "outputSchema should have 'properties'");
        assertTrue(props.containsKey("createStatus"), "outputSchema should have 'createStatus' property");
    }

    // --- Handler error routing ---

    @Test
    void handle_returnsErrorResult_whenHttpRequestMissingFromContext() {
        McpSyncServerExchange exchange = EasyMock.mock(McpSyncServerExchange.class);
        EasyMock.expect(exchange.transportContext())
                .andReturn(McpTransportContext.EMPTY)
                .once();
        EasyMock.replay(exchange);

        McpSchema.CallToolResult result = invokeHandler(exchange, standardArgs());

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

        McpSchema.CallToolResult result = invokeHandler(exchange, standardArgs());

        assertTrue(result.isError());
        assertEquals("401 Unauthorized", ((McpSchema.TextContent) result.content().get(0)).text());
        EasyMock.verify(ctx, exchange);
    }

    @Test
    void handle_missingCx2NetworkArgument_doesNotThrow() {
        McpSyncServerExchange exchange = EasyMock.mock(McpSyncServerExchange.class);
        EasyMock.expect(exchange.transportContext())
                .andReturn(McpTransportContext.EMPTY)
                .once();
        EasyMock.replay(exchange);

        assertDoesNotThrow(() -> invokeHandler(exchange, Map.of()));
        EasyMock.verify(exchange);
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

        McpSchema.CallToolResult result = invokeHandler(exchange, standardArgs());

        assertTrue(result.isError());
        assertEquals("401 Unauthorized", ((McpSchema.TextContent) result.content().get(0)).text());
        EasyMock.verify(httpReq, exchange);
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

        String oversizedChunk = "x".repeat((int) CreateNetworkTool.MAX_CHUNK_SIZE_BYTES + 1);

        McpSchema.CallToolResult result = invokeHandler(exchange,
                Map.of("cx2Network", oversizedChunk,
                       "cx2NetworkSize", (long) CreateNetworkTool.MAX_CHUNK_SIZE_BYTES + 1,
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
    void handle_returnsError_whenNetworkSizeExceedsCap() {
        User user = new User();

        HttpServletRequest httpReq = EasyMock.mock(HttpServletRequest.class);
        EasyMock.expect(httpReq.getAttribute("User")).andReturn(user).once();

        McpTransportContext ctx = McpTransportContext.create(
                Map.of("ndexRequest", httpReq));

        McpSyncServerExchange exchange = EasyMock.mock(McpSyncServerExchange.class);
        EasyMock.expect(exchange.transportContext()).andReturn(ctx).once();
        EasyMock.replay(httpReq, exchange);

        long oversized = CreateNetworkTool.MAX_NETWORK_SIZE_BYTES + 1L;

        McpSchema.CallToolResult result = invokeHandler(exchange,
                Map.of("cx2Network", "[{}]",
                       "cx2NetworkSize", oversized,
                       "cx2NetworkChunkTotalCount", 1,
                       "cx2NetworkCurrentChunkNumber", 1));

        assertTrue(result.isError());
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

        McpSchema.CallToolResult result = invokeHandler(exchange, standardArgs());

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
                Map.of("cx2Network", "[{\"firstChunk\":true}]",
                       "cx2NetworkSize", 100,
                       "cx2NetworkChunkTotalCount", 2,
                       "cx2NetworkCurrentChunkNumber", 1));

        assertFalse(result.isError(), "chunk ack should not be an error");
        assertNotNull(result.structuredContent(), "structured content should be present");

        @SuppressWarnings("unchecked")
        var ack = (java.util.Map<?, ?>) new com.fasterxml.jackson.databind.ObjectMapper()
                .convertValue(result.structuredContent(), java.util.Map.class);
        assertEquals(false, ack.get("isSubmitted"));

        EasyMock.verify(httpReq, exchange);
    }

    // Helpers

    private static Map<String, Object> standardArgs() {
        return Map.of("cx2Network", "[{}]",
                      "cx2NetworkSize", 4,
                      "cx2NetworkChunkTotalCount", 1,
                      "cx2NetworkCurrentChunkNumber", 1);
    }

    private McpSchema.CallToolResult invokeHandler(McpSyncServerExchange exchange,
                                                    Map<String, Object> arguments) {
        McpServerFeatures.SyncToolSpecification spec = tool.toSpec();
        McpSchema.CallToolRequest request =
                new McpSchema.CallToolRequest(CreateNetworkTool.TOOL_NAME, arguments);
        return spec.callHandler().apply(exchange, request);
    }
}
