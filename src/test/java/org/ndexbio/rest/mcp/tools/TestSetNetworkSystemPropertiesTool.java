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

class TestSetNetworkSystemPropertiesTool {

    private ToolsService toolsService;
    private SetNetworkSystemPropertiesTool tool;

    @BeforeEach
    void setUp() {
        toolsService = new ToolsService();
        tool = new SetNetworkSystemPropertiesTool(toolsService);
    }

    // --- Tool spec metadata ---

    @Test
    void toSpec_hasCorrectToolName() {
        assertEquals(SetNetworkSystemPropertiesTool.TOOL_NAME, tool.toSpec().tool().name());
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
    void inputSchema_requiresOnlyNetworkId() {
        McpSchema.JsonSchema schema = tool.toSpec().tool().inputSchema();
        assertTrue(schema.required().contains("networkId"), "networkId should be required");
        assertFalse(schema.required().contains("visibility"), "visibility should not be required");
        assertFalse(schema.required().contains("readonly"),   "readonly should not be required");
    }

    @Test
    void inputSchema_exactlyOneRequiredField() {
        McpSchema.JsonSchema schema = tool.toSpec().tool().inputSchema();
        assertEquals(1, schema.required().size(), "only networkId should be required");
    }

    @Test
    void inputSchema_hasExactlyThreeProperties() {
        Map<String, Object> props = tool.toSpec().tool().inputSchema().properties();
        assertTrue(props.containsKey("networkId"));
        assertTrue(props.containsKey("visibility"));
        assertTrue(props.containsKey("readonly"));
        assertEquals(3, props.size());
    }

    @Test
    @SuppressWarnings("unchecked")
    void inputSchema_visibilityHasEnumConstraint() {
        Map<String, Object> props = tool.toSpec().tool().inputSchema().properties();
        Map<String, Object> visibilityProp = (Map<String, Object>) props.get("visibility");
        assertNotNull(visibilityProp, "visibility property should exist");
        java.util.List<String> enumValues = (java.util.List<String>) visibilityProp.get("enum");
        assertNotNull(enumValues, "visibility property should have an enum constraint");
        assertTrue(enumValues.contains("PUBLIC"),   "enum should include PUBLIC");
        assertTrue(enumValues.contains("PRIVATE"),  "enum should include PRIVATE");
        assertTrue(enumValues.contains("UNLISTED"), "enum should include UNLISTED");
        assertEquals(3, enumValues.size(), "enum should have exactly 3 values");
    }

    @Test
    @SuppressWarnings("unchecked")
    void inputSchema_readonlyIsBooleanType() {
        Map<String, Object> props = tool.toSpec().tool().inputSchema().properties();
        Map<String, Object> readonlyProp = (Map<String, Object>) props.get("readonly");
        assertNotNull(readonlyProp, "readonly property should exist");
        assertEquals("boolean", readonlyProp.get("type"), "readonly should have type boolean");
    }

    // --- Output schema ---

    @Test
    void outputSchema_isPresent() {
        McpServerFeatures.SyncToolSpecification spec = tool.toSpec();
        assertNotNull(spec.tool().outputSchema(), "outputSchema should be set on the tool");
    }

    @Test
    @SuppressWarnings("unchecked")
    void outputSchema_hasExpectedProperties() {
        Map<String, Object> outputSchema = tool.toSpec().tool().outputSchema();
        assertNotNull(outputSchema);
        Map<String, Object> props = (Map<String, Object>) outputSchema.get("properties");
        assertNotNull(props, "outputSchema should have a 'properties' entry");
        assertTrue(props.containsKey("networkId"),  "output schema missing 'networkId'");
        assertTrue(props.containsKey("visibility"), "output schema missing 'visibility'");
        assertTrue(props.containsKey("readonly"),   "output schema missing 'readonly'");
        assertTrue(props.containsKey("message"),    "output schema missing 'message'");
    }

    // --- Handler error routing ---

    @Test
    void handle_returnsErrorResult_whenHttpRequestMissingFromContext() {
        McpSyncServerExchange exchange = EasyMock.mock(McpSyncServerExchange.class);
        EasyMock.expect(exchange.transportContext())
                .andReturn(McpTransportContext.EMPTY)
                .once();
        EasyMock.replay(exchange);

        McpSchema.CallToolResult result = invokeHandler(exchange,
                Map.of("networkId", "f93f402c-86d4-11e7-a10d-0ac135e8bacf",
                       "visibility", "PUBLIC"));

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
                       "visibility", "PUBLIC"));

        assertTrue(result.isError());
        assertEquals("401 Unauthorized", ((McpSchema.TextContent) result.content().get(0)).text());
        EasyMock.verify(httpReq, exchange);
    }

    @Test
    void handle_missingArguments_doesNotThrow() {
        McpSyncServerExchange exchange = EasyMock.mock(McpSyncServerExchange.class);
        EasyMock.expect(exchange.transportContext())
                .andReturn(McpTransportContext.EMPTY)
                .once();
        EasyMock.replay(exchange);

        assertDoesNotThrow(() -> invokeHandler(exchange, Map.of()));
        EasyMock.verify(exchange);
    }

    @Test
    void handle_withValidUser_neitherParam_returnsError() {
        // Auth passes but neither visibility nor readonly is provided — handler returns
        // isError=true without reaching the service layer.
        User user = new User();
        HttpServletRequest httpReq = EasyMock.mock(HttpServletRequest.class);
        EasyMock.expect(httpReq.getAttribute("User")).andReturn(user).once();
        McpTransportContext ctx = McpTransportContext.create(Map.of("ndexRequest", httpReq));
        McpSyncServerExchange exchange = EasyMock.mock(McpSyncServerExchange.class);
        EasyMock.expect(exchange.transportContext()).andReturn(ctx).once();
        EasyMock.replay(httpReq, exchange);

        McpSchema.CallToolResult result = invokeHandler(exchange,
                Map.of("networkId", "f93f402c-86d4-11e7-a10d-0ac135e8bacf"));

        assertTrue(result.isError(), "should return isError=true when neither param provided");
        String msg = ((McpSchema.TextContent) result.content().get(0)).text();
        assertTrue(msg.contains("at least one"), "error message should mention at-least-one requirement");
        EasyMock.verify(httpReq, exchange);
    }

    @Test
    void handle_withValidUser_visibilityOnly_failsAtService() {
        // Auth passes; only visibility provided; service call fails (no DB) → isError=true.
        User user = new User();
        HttpServletRequest httpReq = EasyMock.mock(HttpServletRequest.class);
        EasyMock.expect(httpReq.getAttribute("User")).andReturn(user).anyTimes();
        McpTransportContext ctx = McpTransportContext.create(Map.of("ndexRequest", httpReq));
        McpSyncServerExchange exchange = EasyMock.mock(McpSyncServerExchange.class);
        EasyMock.expect(exchange.transportContext()).andReturn(ctx).once();
        EasyMock.replay(httpReq, exchange);

        McpSchema.CallToolResult result = invokeHandler(exchange,
                Map.of("networkId", "f93f402c-86d4-11e7-a10d-0ac135e8bacf",
                       "visibility", "PUBLIC"));

        assertTrue(result.isError(), "should return isError=true when service call fails");
        EasyMock.verify(httpReq, exchange);
    }

    @Test
    void handle_withValidUser_lowercaseVisibility_failsAtService() {
        // Exercises the toUpperCase() normalization branch with lowercase "private".
        User user = new User();
        HttpServletRequest httpReq = EasyMock.mock(HttpServletRequest.class);
        EasyMock.expect(httpReq.getAttribute("User")).andReturn(user).anyTimes();
        McpTransportContext ctx = McpTransportContext.create(Map.of("ndexRequest", httpReq));
        McpSyncServerExchange exchange = EasyMock.mock(McpSyncServerExchange.class);
        EasyMock.expect(exchange.transportContext()).andReturn(ctx).once();
        EasyMock.replay(httpReq, exchange);

        McpSchema.CallToolResult result = invokeHandler(exchange,
                Map.of("networkId", "f93f402c-86d4-11e7-a10d-0ac135e8bacf",
                       "visibility", "private"));

        assertTrue(result.isError(), "should return isError=true when service call fails");
        EasyMock.verify(httpReq, exchange);
    }

    @Test
    void handle_withValidUser_readonlyTrue_failsAtService() {
        // Only readonly=true provided; service call fails (no DB) → isError=true.
        User user = new User();
        HttpServletRequest httpReq = EasyMock.mock(HttpServletRequest.class);
        EasyMock.expect(httpReq.getAttribute("User")).andReturn(user).anyTimes();
        McpTransportContext ctx = McpTransportContext.create(Map.of("ndexRequest", httpReq));
        McpSyncServerExchange exchange = EasyMock.mock(McpSyncServerExchange.class);
        EasyMock.expect(exchange.transportContext()).andReturn(ctx).once();
        EasyMock.replay(httpReq, exchange);

        McpSchema.CallToolResult result = invokeHandler(exchange,
                Map.of("networkId", "f93f402c-86d4-11e7-a10d-0ac135e8bacf",
                       "readonly", true));

        assertTrue(result.isError(), "should return isError=true when service call fails");
        EasyMock.verify(httpReq, exchange);
    }

    @Test
    void handle_withValidUser_bothParams_failsAtService() {
        // Both visibility and readonly provided in one call; service fails (no DB) → isError=true.
        User user = new User();
        HttpServletRequest httpReq = EasyMock.mock(HttpServletRequest.class);
        EasyMock.expect(httpReq.getAttribute("User")).andReturn(user).anyTimes();
        McpTransportContext ctx = McpTransportContext.create(Map.of("ndexRequest", httpReq));
        McpSyncServerExchange exchange = EasyMock.mock(McpSyncServerExchange.class);
        EasyMock.expect(exchange.transportContext()).andReturn(ctx).once();
        EasyMock.replay(httpReq, exchange);

        McpSchema.CallToolResult result = invokeHandler(exchange,
                Map.of("networkId",  "f93f402c-86d4-11e7-a10d-0ac135e8bacf",
                       "visibility", "PUBLIC",
                       "readonly",   false));

        assertTrue(result.isError(), "should return isError=true when service call fails");
        EasyMock.verify(httpReq, exchange);
    }

    // Helper: invokes the registered handler synchronously
    private McpSchema.CallToolResult invokeHandler(McpSyncServerExchange exchange,
                                                    Map<String, Object> arguments) {
        McpServerFeatures.SyncToolSpecification spec = tool.toSpec();
        McpSchema.CallToolRequest request =
                new McpSchema.CallToolRequest(SetNetworkSystemPropertiesTool.TOOL_NAME, arguments);
        return spec.callHandler().apply(exchange, request);
    }
}
