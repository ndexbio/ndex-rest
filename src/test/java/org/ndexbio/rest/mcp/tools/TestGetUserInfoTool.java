package org.ndexbio.rest.mcp.tools;

import java.util.Map;
import java.util.UUID;

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

class TestGetUserInfoTool {

    private ToolsService toolsService;
    private GetUserInfoTool tool;

    @BeforeEach
    void setUp() {
        toolsService = new ToolsService();
        tool = new GetUserInfoTool(toolsService);
    }

    // --- Tool spec metadata ---

    @Test
    void toSpec_hasCorrectToolName() {
        assertEquals(GetUserInfoTool.TOOL_NAME, tool.toSpec().tool().name());
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
                "get_user_info should have no required input fields");
    }

    @Test
    void inputSchema_hasNoProperties() {
        McpSchema.JsonSchema schema = tool.toSpec().tool().inputSchema();
        assertTrue(schema.properties() == null || schema.properties().isEmpty(),
                "get_user_info should have no input properties");
    }

    // --- Output schema (Map traversal) ---

    @Test
    void outputSchema_isNotNull() {
        assertNotNull(tool.toSpec().tool().outputSchema(),
                "tool should declare an output schema");
    }

    @Test
    @SuppressWarnings("unchecked")
    void outputSchema_hasUserAndNetworkCountProperties() {
        Map<String, Object> schema = (Map<String, Object>) tool.toSpec().tool().outputSchema();
        Map<String, Object> properties = (Map<String, Object>) schema.get("properties");
        assertNotNull(properties, "outputSchema should have a properties map");
        assertTrue(properties.containsKey("user"),
                "outputSchema properties should contain 'user'");
        assertTrue(properties.containsKey("network_count"),
                "outputSchema properties should contain 'network_count'");
    }

    @Test
    @SuppressWarnings("unchecked")
    void outputSchema_userProperty_doesNotHavePasswordField() {
        Map<String, Object> schema = (Map<String, Object>) tool.toSpec().tool().outputSchema();
        Map<String, Object> topProps = (Map<String, Object>) schema.get("properties");
        Map<String, Object> userProp = (Map<String, Object>) topProps.get("user");
        assertNotNull(userProp, "outputSchema 'user' property should exist");
        Map<String, Object> userFields = (Map<String, Object>) userProp.get("properties");
        assertNotNull(userFields, "outputSchema 'user' should have nested properties");
        assertFalse(userFields.containsKey("password"),
                "outputSchema user properties must NOT contain 'password'");
    }

    // --- UserView constructor field mapping ---

    @Test
    void userView_correctlyMapsUserFields() {
        User user = new User();
        UUID uuid = UUID.randomUUID();
        user.setExternalId(uuid);
        user.setUserName("alice");
        user.setFirstName("Alice");
        user.setLastName("Wonderland");
        user.setEmailAddress("alice@example.com");
        user.setDisplayName("Alice W.");
        user.setDiskQuota(1073741824L);
        user.setDiskUsed(512L);

        GetUserInfoTool.UserView view = new GetUserInfoTool.UserView(user);

        assertEquals(uuid, view.externalId());
        assertEquals("alice", view.userName());
        assertEquals("Alice", view.firstName());
        assertEquals("Wonderland", view.lastName());
        assertEquals("alice@example.com", view.emailAddress());
        assertEquals("Alice W.", view.displayName());
        assertEquals(1073741824L, view.diskQuota());
        assertEquals(512L, view.diskUsed());
        // password field does not exist on UserView
    }

    // --- Handler error routing ---

    @Test
    void handle_returnsError_whenHttpRequestMissingFromContext() {
        // EMPTY context → httpReq is null → NPE in getAttribute caught as Throwable → isError
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
        assertEquals("401 Unauthorized",
                ((McpSchema.TextContent) result.content().get(0)).text());
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
        assertEquals("401 Unauthorized",
                ((McpSchema.TextContent) result.content().get(0)).text());
        EasyMock.verify(httpReq, exchange);
    }

    @Test
    void handle_withValidUser_failsAtService() {
        // User present with null externalId → NPE at getExternalId().toString()
        // → caught by Throwable handler → isError=true
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

    // --- Helper ---

    private McpSchema.CallToolResult invokeHandler(McpSyncServerExchange exchange,
                                                    Map<String, Object> arguments) {
        McpServerFeatures.SyncToolSpecification spec = tool.toSpec();
        McpSchema.CallToolRequest request =
                new McpSchema.CallToolRequest(GetUserInfoTool.TOOL_NAME, arguments);
        return spec.callHandler().apply(exchange, request);
    }
}
