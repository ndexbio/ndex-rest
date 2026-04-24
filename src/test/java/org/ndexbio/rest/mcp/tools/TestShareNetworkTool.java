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

class TestShareNetworkTool {

    private ToolsService toolsService;
    private ShareNetworkTool tool;

    @BeforeEach
    void setUp() {
        toolsService = new ToolsService();
        tool = new ShareNetworkTool(toolsService);
    }

    // --- Tool spec metadata ---

    @Test
    void toSpec_hasCorrectToolName() {
        assertEquals(ShareNetworkTool.TOOL_NAME, tool.toSpec().tool().name());
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
    void inputSchema_networkIdAndPermissionAreRequired() {
        McpSchema.JsonSchema schema = tool.toSpec().tool().inputSchema();
        assertTrue(schema.required().contains("networkId"),  "networkId should be required");
        assertTrue(schema.required().contains("permission"), "permission should be required");
        assertFalse(schema.required().contains("userId"),    "userId should not be required");
        assertFalse(schema.required().contains("groupId"),   "groupId should not be required");
    }

    @Test
    void inputSchema_exactlyTwoRequiredFields() {
        McpSchema.JsonSchema schema = tool.toSpec().tool().inputSchema();
        assertEquals(2, schema.required().size(), "only networkId and permission should be required");
    }

    @Test
    void inputSchema_hasExactlyFourProperties() {
        Map<String, Object> props = tool.toSpec().tool().inputSchema().properties();
        assertTrue(props.containsKey("networkId"));
        assertTrue(props.containsKey("permission"));
        assertTrue(props.containsKey("userId"));
        assertTrue(props.containsKey("groupId"));
        assertEquals(4, props.size());
    }

    @Test
    @SuppressWarnings("unchecked")
    void inputSchema_permissionHasEnumConstraint() {
        Map<String, Object> props = tool.toSpec().tool().inputSchema().properties();
        Map<String, Object> permProp = (Map<String, Object>) props.get("permission");
        assertNotNull(permProp, "permission property should exist");
        java.util.List<String> enumValues = (java.util.List<String>) permProp.get("enum");
        assertNotNull(enumValues, "permission should have an enum constraint");
        assertTrue(enumValues.contains("READ"),  "enum should include READ");
        assertTrue(enumValues.contains("WRITE"), "enum should include WRITE");
        assertTrue(enumValues.contains("ADMIN"), "enum should include ADMIN");
        assertEquals(3, enumValues.size(), "enum should have exactly 3 values");
    }

    @Test
    @SuppressWarnings("unchecked")
    void inputSchema_userIdAndGroupIdAreStringType() {
        Map<String, Object> props = tool.toSpec().tool().inputSchema().properties();
        Map<String, Object> userIdProp  = (Map<String, Object>) props.get("userId");
        Map<String, Object> groupIdProp = (Map<String, Object>) props.get("groupId");
        assertNotNull(userIdProp,  "userId property should exist");
        assertNotNull(groupIdProp, "groupId property should exist");
        assertEquals("string", userIdProp.get("type"),  "userId should have type string");
        assertEquals("string", groupIdProp.get("type"), "groupId should have type string");
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
        assertTrue(props.containsKey("networkId"),       "output schema missing 'networkId'");
        assertTrue(props.containsKey("subjectId"),       "output schema missing 'subjectId'");
        assertTrue(props.containsKey("subjectType"),     "output schema missing 'subjectType'");
        assertTrue(props.containsKey("permission"),      "output schema missing 'permission'");
        assertTrue(props.containsKey("recordsAffected"), "output schema missing 'recordsAffected'");
        assertTrue(props.containsKey("message"),         "output schema missing 'message'");
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
                       "permission", "READ",
                       "userId", "a1b2c3d4-1234-5678-abcd-000000000001"));

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
                       "permission", "READ",
                       "userId", "a1b2c3d4-1234-5678-abcd-000000000001"));

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
                       "permission", "READ",
                       "userId", "a1b2c3d4-1234-5678-abcd-000000000001"));

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
    void handle_withValidUser_neitherUserIdNorGroupId_returnsError() {
        User user = new User();
        HttpServletRequest httpReq = EasyMock.mock(HttpServletRequest.class);
        EasyMock.expect(httpReq.getAttribute("User")).andReturn(user).once();
        McpTransportContext ctx = McpTransportContext.create(Map.of("ndexRequest", httpReq));
        McpSyncServerExchange exchange = EasyMock.mock(McpSyncServerExchange.class);
        EasyMock.expect(exchange.transportContext()).andReturn(ctx).once();
        EasyMock.replay(httpReq, exchange);

        McpSchema.CallToolResult result = invokeHandler(exchange,
                Map.of("networkId", "f93f402c-86d4-11e7-a10d-0ac135e8bacf",
                       "permission", "READ"));

        assertTrue(result.isError(), "should return isError=true when neither userId nor groupId provided");
        String msg = ((McpSchema.TextContent) result.content().get(0)).text();
        assertTrue(msg.contains("userId or groupId"), "error message should mention userId or groupId");
        EasyMock.verify(httpReq, exchange);
    }

    @Test
    void handle_withValidUser_bothUserIdAndGroupId_returnsError() {
        User user = new User();
        HttpServletRequest httpReq = EasyMock.mock(HttpServletRequest.class);
        EasyMock.expect(httpReq.getAttribute("User")).andReturn(user).once();
        McpTransportContext ctx = McpTransportContext.create(Map.of("ndexRequest", httpReq));
        McpSyncServerExchange exchange = EasyMock.mock(McpSyncServerExchange.class);
        EasyMock.expect(exchange.transportContext()).andReturn(ctx).once();
        EasyMock.replay(httpReq, exchange);

        McpSchema.CallToolResult result = invokeHandler(exchange,
                Map.of("networkId",  "f93f402c-86d4-11e7-a10d-0ac135e8bacf",
                       "permission", "READ",
                       "userId",     "a1b2c3d4-1234-5678-abcd-000000000001",
                       "groupId",    "e5f6g7h8-1234-5678-abcd-000000000003"));

        assertTrue(result.isError(), "should return isError=true when both userId and groupId provided");
        String msg = ((McpSchema.TextContent) result.content().get(0)).text();
        assertTrue(msg.contains("cannot both"), "error message should mention cannot both");
        EasyMock.verify(httpReq, exchange);
    }

    @Test
    void handle_withValidUser_userIdOnly_failsAtService() {
        User user = new User();
        HttpServletRequest httpReq = EasyMock.mock(HttpServletRequest.class);
        EasyMock.expect(httpReq.getAttribute("User")).andReturn(user).anyTimes();
        McpTransportContext ctx = McpTransportContext.create(Map.of("ndexRequest", httpReq));
        McpSyncServerExchange exchange = EasyMock.mock(McpSyncServerExchange.class);
        EasyMock.expect(exchange.transportContext()).andReturn(ctx).once();
        EasyMock.replay(httpReq, exchange);

        McpSchema.CallToolResult result = invokeHandler(exchange,
                Map.of("networkId",  "f93f402c-86d4-11e7-a10d-0ac135e8bacf",
                       "permission", "READ",
                       "userId",     "a1b2c3d4-1234-5678-abcd-000000000001"));

        assertTrue(result.isError(), "should return isError=true when service call fails (no DB)");
        EasyMock.verify(httpReq, exchange);
    }

    @Test
    void handle_withValidUser_groupIdOnly_failsAtService() {
        User user = new User();
        HttpServletRequest httpReq = EasyMock.mock(HttpServletRequest.class);
        EasyMock.expect(httpReq.getAttribute("User")).andReturn(user).anyTimes();
        McpTransportContext ctx = McpTransportContext.create(Map.of("ndexRequest", httpReq));
        McpSyncServerExchange exchange = EasyMock.mock(McpSyncServerExchange.class);
        EasyMock.expect(exchange.transportContext()).andReturn(ctx).once();
        EasyMock.replay(httpReq, exchange);

        McpSchema.CallToolResult result = invokeHandler(exchange,
                Map.of("networkId",  "f93f402c-86d4-11e7-a10d-0ac135e8bacf",
                       "permission", "WRITE",
                       "groupId",    "e5f6g7h8-1234-5678-abcd-000000000003"));

        assertTrue(result.isError(), "should return isError=true when service call fails (no DB)");
        EasyMock.verify(httpReq, exchange);
    }

    @Test
    void handle_withValidUser_lowercasePermission_failsAtService() {
        // Exercises the toUpperCase() normalization branch with lowercase "read".
        User user = new User();
        HttpServletRequest httpReq = EasyMock.mock(HttpServletRequest.class);
        EasyMock.expect(httpReq.getAttribute("User")).andReturn(user).anyTimes();
        McpTransportContext ctx = McpTransportContext.create(Map.of("ndexRequest", httpReq));
        McpSyncServerExchange exchange = EasyMock.mock(McpSyncServerExchange.class);
        EasyMock.expect(exchange.transportContext()).andReturn(ctx).once();
        EasyMock.replay(httpReq, exchange);

        McpSchema.CallToolResult result = invokeHandler(exchange,
                Map.of("networkId",  "f93f402c-86d4-11e7-a10d-0ac135e8bacf",
                       "permission", "read",
                       "userId",     "a1b2c3d4-1234-5678-abcd-000000000001"));

        assertTrue(result.isError(), "should return isError=true when service call fails (no DB)");
        EasyMock.verify(httpReq, exchange);
    }

    // Helper: invokes the registered handler synchronously
    private McpSchema.CallToolResult invokeHandler(McpSyncServerExchange exchange,
                                                    Map<String, Object> arguments) {
        McpServerFeatures.SyncToolSpecification spec = tool.toSpec();
        McpSchema.CallToolRequest request =
                new McpSchema.CallToolRequest(ShareNetworkTool.TOOL_NAME, arguments);
        return spec.callHandler().apply(exchange, request);
    }
}
