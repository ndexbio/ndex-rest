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
import org.ndexbio.rest.mcp.ToolsService;

import static org.junit.jupiter.api.Assertions.*;

class TestGetFolderTool {

    private ToolsService toolsService;
    private GetFolderTool tool;

    @BeforeEach
    void setUp() {
        toolsService = new ToolsService();
        tool = new GetFolderTool(toolsService);
    }

    // --- Tool spec metadata ---

    @Test
    void toSpec_hasCorrectToolName() {
        assertEquals(GetFolderTool.TOOL_NAME, tool.toSpec().tool().name());
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
    void inputSchema_requiresOnlyMode() {
        McpSchema.JsonSchema schema = tool.toSpec().tool().inputSchema();
        assertEquals(1, schema.required().size());
        assertTrue(schema.required().contains("mode"));
    }

    @Test
    void inputSchema_modeHasEnumValues() {
        McpSchema.JsonSchema schema = tool.toSpec().tool().inputSchema();
        @SuppressWarnings("unchecked")
        Map<String, Object> modeEntry = (Map<String, Object>) schema.properties().get("mode");
        assertNotNull(modeEntry);
        @SuppressWarnings("unchecked")
        List<String> enumVals = (List<String>) modeEntry.get("enum");
        assertNotNull(enumVals);
        assertTrue(enumVals.containsAll(List.of("list", "get", "browse", "count")));
    }

    @Test
    void inputSchema_folderIdIsConditionalParam() {
        // folderId should be emitted as a wrapper object with waived + parameter sub-fields
        McpSchema.JsonSchema schema = tool.toSpec().tool().inputSchema();
        @SuppressWarnings("unchecked")
        Map<String, Object> folderIdEntry = (Map<String, Object>) schema.properties().get("folderId");
        assertNotNull(folderIdEntry, "folderId should be present in properties");
        assertEquals("object", folderIdEntry.get("type"),
                "folderId should be a conditional param wrapper (type=object)");
        @SuppressWarnings("unchecked")
        Map<String, Object> props = (Map<String, Object>) folderIdEntry.get("properties");
        assertNotNull(props);
        assertTrue(props.containsKey("waived"), "conditional wrapper must have 'waived' sub-field");
        assertTrue(props.containsKey("parameter"), "conditional wrapper must have 'parameter' sub-field");
    }

    @Test
    void inputSchema_hasExpectedPropertyCount() {
        // mode, folderId (conditional), limit, format, type, accessKey = 6 total
        Map<String, Object> props = tool.toSpec().tool().inputSchema().properties();
        assertEquals(6, props.size());
        assertTrue(props.containsKey("mode"));
        assertTrue(props.containsKey("folderId"));
        assertTrue(props.containsKey("limit"));
        assertTrue(props.containsKey("format"));
        assertTrue(props.containsKey("type"));
        assertTrue(props.containsKey("accessKey"));
    }

    // --- Handler error routing ---

    @Test
    void handle_nullHttpRequest_returnsError() {
        McpSyncServerExchange exchange = EasyMock.mock(McpSyncServerExchange.class);
        EasyMock.expect(exchange.transportContext())
                .andReturn(McpTransportContext.EMPTY)
                .once();
        EasyMock.replay(exchange);

        McpSchema.CallToolResult result = invokeHandler(exchange, Map.of("mode", "list"));

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

        McpSchema.CallToolResult result = invokeHandler(exchange, Map.of("mode", "list"));

        assertTrue(result.isError());
        assertEquals("401 Unauthorized",
                ((McpSchema.TextContent) result.content().get(0)).text());
        EasyMock.verify(ctx, exchange);
    }

    @Test
    void handle_modeList_noUser_returns401() {
        HttpServletRequest httpReq = EasyMock.mock(HttpServletRequest.class);
        EasyMock.expect(httpReq.getAttribute("User")).andReturn(null).once();
        McpTransportContext ctx = McpTransportContext.create(Map.of("ndexRequest", httpReq));
        McpSyncServerExchange exchange = EasyMock.mock(McpSyncServerExchange.class);
        EasyMock.expect(exchange.transportContext()).andReturn(ctx).once();
        EasyMock.replay(httpReq, exchange);

        McpSchema.CallToolResult result = invokeHandler(exchange, Map.of("mode", "list"));

        assertTrue(result.isError());
        assertEquals("401 Unauthorized",
                ((McpSchema.TextContent) result.content().get(0)).text());
        EasyMock.verify(httpReq, exchange);
    }

    @Test
    void handle_modeGet_missingFolderId_returnsValidationError() {
        HttpServletRequest httpReq = EasyMock.mock(HttpServletRequest.class);
        McpTransportContext ctx = McpTransportContext.create(Map.of("ndexRequest", httpReq));
        McpSyncServerExchange exchange = EasyMock.mock(McpSyncServerExchange.class);
        EasyMock.expect(exchange.transportContext()).andReturn(ctx).once();
        EasyMock.replay(httpReq, exchange);

        // mode=get but no folderId key → validation should fail
        McpSchema.CallToolResult result = invokeHandler(exchange, Map.of("mode", "get"));

        assertTrue(result.isError());
        String msg = ((McpSchema.TextContent) result.content().get(0)).text();
        assertTrue(msg.contains("folderId"), "error message should name the missing parameter");
        EasyMock.verify(httpReq, exchange);
    }

    @Test
    void handle_missingMode_doesNotThrow() {
        McpSyncServerExchange exchange = EasyMock.mock(McpSyncServerExchange.class);
        EasyMock.expect(exchange.transportContext())
                .andReturn(McpTransportContext.EMPTY)
                .once();
        EasyMock.replay(exchange);

        // No mode key — must not throw; should return error result
        assertDoesNotThrow(() -> invokeHandler(exchange, Map.of()));
        EasyMock.verify(exchange);
    }

    // Helper
    private McpSchema.CallToolResult invokeHandler(McpSyncServerExchange exchange,
                                                    Map<String, Object> arguments) {
        McpServerFeatures.SyncToolSpecification spec = tool.toSpec();
        McpSchema.CallToolRequest request =
                new McpSchema.CallToolRequest(GetFolderTool.TOOL_NAME, arguments);
        return spec.callHandler().apply(exchange, request);
    }
}
