package org.ndexbio.rest.mcp.tools;

import java.util.HashMap;
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

class TestManageFolderTool {

    private ToolsService toolsService;
    private ManageFolderTool tool;

    @BeforeEach
    void setUp() {
        toolsService = new ToolsService();
        tool = new ManageFolderTool(toolsService);
    }

    // --- Tool spec metadata ---

    @Test
    void toSpec_hasCorrectToolName() {
        assertEquals(ManageFolderTool.TOOL_NAME, tool.toSpec().tool().name());
    }

    @Test
    void toSpec_hasNonBlankDescription() {
        assertFalse(tool.toSpec().tool().description().isBlank());
    }

    @Test
    void inputSchema_requiresOnlyMode() {
        McpSchema.JsonSchema schema = tool.toSpec().tool().inputSchema();
        assertEquals(1, schema.required().size());
        assertTrue(schema.required().contains("mode"));
    }

    @Test
    void inputSchema_folderIdIsConditionalParam() {
        McpSchema.JsonSchema schema = tool.toSpec().tool().inputSchema();
        @SuppressWarnings("unchecked")
        Map<String, Object> folderIdEntry = (Map<String, Object>) schema.properties().get("folderId");
        assertNotNull(folderIdEntry, "folderId must be present in properties");
        assertEquals("object", folderIdEntry.get("type"),
                "folderId must be a conditional param wrapper (type=object)");
        @SuppressWarnings("unchecked")
        Map<String, Object> props = (Map<String, Object>) folderIdEntry.get("properties");
        assertTrue(props.containsKey("waived"));
        assertTrue(props.containsKey("parameter"));
    }

    @Test
    void inputSchema_nameIsConditionalParam() {
        McpSchema.JsonSchema schema = tool.toSpec().tool().inputSchema();
        @SuppressWarnings("unchecked")
        Map<String, Object> nameEntry = (Map<String, Object>) schema.properties().get("name");
        assertNotNull(nameEntry, "name must be present in properties");
        assertEquals("object", nameEntry.get("type"),
                "name must be a conditional param wrapper (type=object)");
        @SuppressWarnings("unchecked")
        Map<String, Object> props = (Map<String, Object>) nameEntry.get("properties");
        assertTrue(props.containsKey("waived"));
        assertTrue(props.containsKey("parameter"));
    }

    @Test
    void outputSchema_isPresent() {
        assertNotNull(tool.toSpec().tool().outputSchema(),
                "outputSchema should be set on the tool");
    }

    @Test
    void outputSchema_hasExpectedFields() {
        Map<String, Object> outputSchema = tool.toSpec().tool().outputSchema();
        @SuppressWarnings("unchecked")
        Map<String, Object> props = (Map<String, Object>) outputSchema.get("properties");
        assertNotNull(props);
        assertTrue(props.containsKey("mode"),         "output schema missing 'mode'");
        assertTrue(props.containsKey("folderId"),     "output schema missing 'folderId'");
        assertTrue(props.containsKey("message"),      "output schema missing 'message'");
        assertTrue(props.containsKey("accessKey"),    "output schema missing 'accessKey'");
        assertTrue(props.containsKey("createStatus"), "output schema missing 'createStatus'");
    }

    // --- Handler error routing ---

    @Test
    void handle_nullHttpRequest_returnsError() {
        McpSyncServerExchange exchange = EasyMock.mock(McpSyncServerExchange.class);
        EasyMock.expect(exchange.transportContext())
                .andReturn(McpTransportContext.EMPTY)
                .once();
        EasyMock.replay(exchange);

        McpSchema.CallToolResult result = invokeHandler(exchange, Map.of("mode", "create"));

        assertTrue(result.isError(), "null httpReq should produce isError=true");
        EasyMock.verify(exchange);
    }

    @Test
    void handle_securityException_returns401() {
        McpTransportContext ctx = EasyMock.mock(McpTransportContext.class);
        EasyMock.expect(ctx.get("ndexRequest"))
                .andThrow(new SecurityException("access denied"))
                .once();
        McpSyncServerExchange exchange = EasyMock.mock(McpSyncServerExchange.class);
        EasyMock.expect(exchange.transportContext()).andReturn(ctx).once();
        EasyMock.replay(ctx, exchange);

        McpSchema.CallToolResult result = invokeHandler(exchange, Map.of("mode", "create"));

        assertTrue(result.isError());
        assertEquals("401 Unauthorized",
                ((McpSchema.TextContent) result.content().get(0)).text());
        EasyMock.verify(ctx, exchange);
    }

    @Test
    void handle_noUser_returns401() {
        HttpServletRequest httpReq = EasyMock.mock(HttpServletRequest.class);
        EasyMock.expect(httpReq.getAttribute("User")).andReturn(null).once();
        McpTransportContext ctx = McpTransportContext.create(Map.of("ndexRequest", httpReq));
        McpSyncServerExchange exchange = EasyMock.mock(McpSyncServerExchange.class);
        EasyMock.expect(exchange.transportContext()).andReturn(ctx).once();
        EasyMock.replay(httpReq, exchange);

        McpSchema.CallToolResult result = invokeHandler(exchange, Map.of("mode", "create"));

        assertTrue(result.isError());
        assertEquals("401 Unauthorized",
                ((McpSchema.TextContent) result.content().get(0)).text());
        EasyMock.verify(httpReq, exchange);
    }

    @Test
    void handle_modeCreate_nameMissing_returnsValidationError() {
        HttpServletRequest httpReq = EasyMock.mock(HttpServletRequest.class);
        EasyMock.expect(httpReq.getAttribute("User")).andReturn(new User()).once();
        McpTransportContext ctx = McpTransportContext.create(Map.of("ndexRequest", httpReq));
        McpSyncServerExchange exchange = EasyMock.mock(McpSyncServerExchange.class);
        EasyMock.expect(exchange.transportContext()).andReturn(ctx).once();
        EasyMock.replay(httpReq, exchange);

        // mode=create but no name key → validation should fail
        McpSchema.CallToolResult result = invokeHandler(exchange, Map.of("mode", "create"));

        assertTrue(result.isError());
        String msg = ((McpSchema.TextContent) result.content().get(0)).text();
        assertTrue(msg.contains("name"), "error message should name the missing parameter");
        EasyMock.verify(httpReq, exchange);
    }

    @Test
    void handle_modeDelete_folderIdWaived_returnsValidationError() {
        HttpServletRequest httpReq = EasyMock.mock(HttpServletRequest.class);
        EasyMock.expect(httpReq.getAttribute("User")).andReturn(new User()).once();
        McpTransportContext ctx = McpTransportContext.create(Map.of("ndexRequest", httpReq));
        McpSyncServerExchange exchange = EasyMock.mock(McpSyncServerExchange.class);
        EasyMock.expect(exchange.transportContext()).andReturn(ctx).once();
        EasyMock.replay(httpReq, exchange);

        // folderId waived=true when mode=delete — must be rejected
        Map<String, Object> waivedFolderId = new HashMap<>();
        waivedFolderId.put("waived", true);
        waivedFolderId.put("parameter", null);
        Map<String, Object> args = Map.of("mode", "delete", "folderId", waivedFolderId);

        McpSchema.CallToolResult result = invokeHandler(exchange, args);

        assertTrue(result.isError());
        String msg = ((McpSchema.TextContent) result.content().get(0)).text();
        assertTrue(msg.contains("folderId"), "error should name the cannot-waive parameter");
        EasyMock.verify(httpReq, exchange);
    }

    // Helper
    private McpSchema.CallToolResult invokeHandler(McpSyncServerExchange exchange,
                                                    Map<String, Object> arguments) {
        McpServerFeatures.SyncToolSpecification spec = tool.toSpec();
        McpSchema.CallToolRequest request =
                new McpSchema.CallToolRequest(ManageFolderTool.TOOL_NAME, arguments);
        return spec.callHandler().apply(exchange, request);
    }
}
