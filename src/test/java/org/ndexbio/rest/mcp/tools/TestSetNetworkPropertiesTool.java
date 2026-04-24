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

class TestSetNetworkPropertiesTool {

    private ToolsService toolsService;
    private SetNetworkPropertiesTool tool;

    @BeforeEach
    void setUp() {
        toolsService = new ToolsService();
        tool = new SetNetworkPropertiesTool(toolsService);
    }

    // --- Tool spec metadata ---

    @Test
    void toSpec_hasCorrectToolName() {
        assertEquals(SetNetworkPropertiesTool.TOOL_NAME, tool.toSpec().tool().name());
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
    void inputSchema_requiresNetworkIdAndProperties() {
        McpSchema.JsonSchema schema = tool.toSpec().tool().inputSchema();
        assertTrue(schema.required().contains("networkId"),   "networkId should be required");
        assertTrue(schema.required().contains("properties"),  "properties should be required");
    }

    @Test
    void inputSchema_hasTwoProperties() {
        Map<String, Object> props = tool.toSpec().tool().inputSchema().properties();
        assertTrue(props.containsKey("networkId"));
        assertTrue(props.containsKey("properties"));
        assertEquals(2, props.size());
    }

    @Test
    void inputSchema_exactlyTwoRequiredFields() {
        McpSchema.JsonSchema schema = tool.toSpec().tool().inputSchema();
        assertEquals(2, schema.required().size(),
                "only networkId and properties should be required");
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
        assertTrue(props.containsKey("networkId"),       "output schema missing 'networkId'");
        assertTrue(props.containsKey("propertiesCount"), "output schema missing 'propertiesCount'");
        assertTrue(props.containsKey("message"),         "output schema missing 'message'");
    }

    // --- Handler error routing ---

    @Test
    void handle_returnsErrorResult_whenHttpRequestMissingFromContext() {
        // Transport context has no "ndexRequest" entry — handler receives null httpReq,
        // causing NPE on httpReq.getAttribute("User"), caught as generic Throwable.
        McpSyncServerExchange exchange = EasyMock.mock(McpSyncServerExchange.class);
        EasyMock.expect(exchange.transportContext())
                .andReturn(McpTransportContext.EMPTY)
                .once();
        EasyMock.replay(exchange);

        McpSchema.CallToolResult result = invokeHandler(exchange,
                Map.of("networkId", "f93f402c-86d4-11e7-a10d-0ac135e8bacf",
                       "properties", List.of()));

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

        McpSchema.CallToolResult result = invokeHandler(exchange,
                Map.of("networkId", "f93f402c-86d4-11e7-a10d-0ac135e8bacf",
                       "properties", List.of()));

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
                       "properties", List.of()));

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
    void handle_withValidUser_and_properties_failsAtService() {
        // Auth passes (non-null User); full property mapping exercised including
        // optional dataType and subNetworkId branches. Service call fails (no DB).
        // The exception is caught as Throwable and the handler returns isError=true.
        User user = new User();

        HttpServletRequest httpReq = EasyMock.mock(HttpServletRequest.class);
        EasyMock.expect(httpReq.getAttribute("User")).andReturn(user).anyTimes();

        McpTransportContext ctx = McpTransportContext.create(Map.of("ndexRequest", httpReq));
        McpSyncServerExchange exchange = EasyMock.mock(McpSyncServerExchange.class);
        EasyMock.expect(exchange.transportContext()).andReturn(ctx).once();
        EasyMock.replay(httpReq, exchange);

        McpSchema.CallToolResult result = invokeHandler(exchange,
                Map.of("networkId", "f93f402c-86d4-11e7-a10d-0ac135e8bacf",
                       "properties", List.of(
                           Map.of("predicateString", "author",
                                  "value", "Jane Smith"),
                           Map.of("predicateString", "publicationYear",
                                  "value", "2024",
                                  "dataType", "integer"),
                           Map.of("predicateString", "subnetProp",
                                  "value", "scopedValue",
                                  "dataType", "string",
                                  "subNetworkId", 42))));

        assertTrue(result.isError(), "should return isError=true when service call fails");
        EasyMock.verify(httpReq, exchange);
    }

    @Test
    void handle_withValidUser_emptyProperties_failsAtService() {
        // Exercises the null-safety path: properties field explicitly omitted by the LLM,
        // so input.properties() is null and defaults to List.of().
        // Service call still fails (no DB), returning isError=true.
        User user = new User();

        HttpServletRequest httpReq = EasyMock.mock(HttpServletRequest.class);
        EasyMock.expect(httpReq.getAttribute("User")).andReturn(user).anyTimes();

        McpTransportContext ctx = McpTransportContext.create(Map.of("ndexRequest", httpReq));
        McpSyncServerExchange exchange = EasyMock.mock(McpSyncServerExchange.class);
        EasyMock.expect(exchange.transportContext()).andReturn(ctx).once();
        EasyMock.replay(httpReq, exchange);

        // Only networkId provided — properties key absent so Jackson sets it to null
        McpSchema.CallToolResult result = invokeHandler(exchange,
                Map.of("networkId", "f93f402c-86d4-11e7-a10d-0ac135e8bacf"));

        assertTrue(result.isError(), "should return isError=true when service call fails");
        EasyMock.verify(httpReq, exchange);
    }

    // Helper: invokes the registered handler synchronously
    private McpSchema.CallToolResult invokeHandler(McpSyncServerExchange exchange,
                                                    Map<String, Object> arguments) {
        McpServerFeatures.SyncToolSpecification spec = tool.toSpec();
        McpSchema.CallToolRequest request =
                new McpSchema.CallToolRequest(SetNetworkPropertiesTool.TOOL_NAME, arguments);
        return spec.callHandler().apply(exchange, request);
    }
}
