package org.ndexbio.rest.mcp.tools;

import java.util.List;
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

class TestUpdateNetworkProfileTool {

    private ToolsService toolsService;
    private UpdateNetworkProfileTool tool;

    @BeforeEach
    void setUp() {
        toolsService = new ToolsService();
        tool = new UpdateNetworkProfileTool(toolsService);
    }

    // --- Tool spec metadata ---

    @Test
    void toSpec_hasCorrectToolName() {
        assertEquals(UpdateNetworkProfileTool.TOOL_NAME, tool.toSpec().tool().name());
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
    void inputSchema_requiredFieldsPresent() {
        McpSchema.JsonSchema schema = tool.toSpec().tool().inputSchema();
        assertTrue(schema.required().contains("networkId"), "networkId should be required");
        assertTrue(schema.required().contains("name"),      "name should be required");
        assertTrue(schema.required().contains("visibility"), "visibility should be required");
    }

    @Test
    void inputSchema_hasExpectedPropertyCount() {
        Map<String, Object> props = tool.toSpec().tool().inputSchema().properties();
        assertTrue(props.containsKey("networkId"));
        assertTrue(props.containsKey("name"));
        assertTrue(props.containsKey("visibility"));
        assertTrue(props.containsKey("description"));
        assertTrue(props.containsKey("version"));
        assertTrue(props.containsKey("properties"));
        assertEquals(6, props.size());
    }

    @Test
    void inputSchema_exactlyThreeRequiredFields() {
        McpSchema.JsonSchema schema = tool.toSpec().tool().inputSchema();
        assertEquals(3, schema.required().size(),
                "only networkId, name, visibility should be required");
    }

    // --- Output schema ---

    @Test
    void outputSchema_isPresent() {
        McpServerFeatures.SyncToolSpecification spec = tool.toSpec();
        assertNotNull(spec.tool().outputSchema(), "outputSchema should be set on the tool");
    }

    @Test
    void outputSchema_hasExpectedProperties() {
        Map<String, Object> outputSchema = tool.toSpec().tool().outputSchema();
        assertNotNull(outputSchema);
        @SuppressWarnings("unchecked")
        Map<String, Object> props = (Map<String, Object>) outputSchema.get("properties");
        assertNotNull(props, "outputSchema should have a 'properties' entry");
        assertTrue(props.containsKey("networkId"), "output schema missing 'networkId'");
        assertTrue(props.containsKey("message"),   "output schema missing 'message'");
    }

    // --- Handler error routing ---

    @Test
    void handle_returnsErrorResult_whenHttpRequestMissingFromContext() {
        // Transport context has no "ndexRequest" entry — handler receives null httpReq,
        // causing NPE in httpReq.getAttribute("User"), caught as generic Throwable.
        McpSyncServerExchange exchange = EasyMock.mock(McpSyncServerExchange.class);
        EasyMock.expect(exchange.transportContext())
                .andReturn(McpTransportContext.EMPTY)
                .once();
        EasyMock.replay(exchange);

        McpSchema.CallToolResult result = invokeHandler(exchange,
                Map.of("networkId", "f93f402c-86d4-11e7-a10d-0ac135e8bacf",
                       "name", "Test Network",
                       "visibility", "PUBLIC"));

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
                       "name", "Test Network",
                       "visibility", "PUBLIC"));

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
                Map.of("networkId", "f93f402c-86d4-11e7-a10d-0ac135e8bacf",
                       "name", "Test Network",
                       "visibility", "PUBLIC"));

        assertTrue(result.isError());
        assertEquals("401 Unauthorized", ((McpSchema.TextContent) result.content().get(0)).text());
        EasyMock.verify(httpReq, exchange);
    }

    @Test
    void handle_missingArguments_doesNotThrow() {
        // Omitting all arguments — handler must not propagate any exception.
        McpSyncServerExchange exchange = EasyMock.mock(McpSyncServerExchange.class);
        EasyMock.expect(exchange.transportContext())
                .andReturn(McpTransportContext.EMPTY)
                .once();
        EasyMock.replay(exchange);

        assertDoesNotThrow(() -> invokeHandler(exchange, Map.of()));
        EasyMock.verify(exchange);
    }

    @Test
    void handle_withValidUser_failsAtService_returnsError() {
        // Auth passes (non-null User), NetworkSummary is built (including properties mapping),
        // then the service call fails because there is no DB in the test environment.
        // The exception is caught as Throwable and the handler returns isError=true.
        User user = new User();

        HttpServletRequest httpReq = EasyMock.mock(HttpServletRequest.class);
        // getAttribute("User") is called twice: once in the tool handler and once internally
        // by NetworkServiceV2.getLoggedInUser().
        EasyMock.expect(httpReq.getAttribute("User")).andReturn(user).anyTimes();

        McpTransportContext ctx = McpTransportContext.create(Map.of("ndexRequest", httpReq));
        McpSyncServerExchange exchange = EasyMock.mock(McpSyncServerExchange.class);
        EasyMock.expect(exchange.transportContext()).andReturn(ctx).once();
        EasyMock.replay(httpReq, exchange);

        McpSchema.CallToolResult result = invokeHandler(exchange,
                Map.of("networkId",  "f93f402c-86d4-11e7-a10d-0ac135e8bacf",
                       "name",       "Wnt Signaling Pathway",
                       "visibility", "PUBLIC",
                       "description", "A curated pathway.",
                       "version",    "1.0.0",
                       "properties", List.of(
                           Map.of("predicateString", "author", "value", "Jane Smith"),
                           Map.of("predicateString", "organism", "value", "Homo sapiens",
                                  "dataType", "string"))));

        // Service call fails (no DB) — error result is expected
        assertTrue(result.isError(), "should return isError=true when service call fails");
        EasyMock.verify(httpReq, exchange);
    }

    // Helper: invokes the registered handler synchronously
    private McpSchema.CallToolResult invokeHandler(McpSyncServerExchange exchange,
                                                    Map<String, Object> arguments) {
        McpServerFeatures.SyncToolSpecification spec = tool.toSpec();
        McpSchema.CallToolRequest request =
                new McpSchema.CallToolRequest(UpdateNetworkProfileTool.TOOL_NAME, arguments);
        return spec.callHandler().apply(exchange, request);
    }
}
