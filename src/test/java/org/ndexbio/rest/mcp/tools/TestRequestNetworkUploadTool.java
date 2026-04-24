package org.ndexbio.rest.mcp.tools;

import java.util.HashMap;
import java.util.Map;

import org.easymock.EasyMock;
import org.junit.jupiter.api.Test;
import org.ndexbio.model.object.User;
import org.ndexbio.rest.mcp.ConfigLocator;
import org.ndexbio.rest.mcp.UploadService;

import com.fasterxml.jackson.databind.ObjectMapper;

import io.modelcontextprotocol.common.McpTransportContext;
import io.modelcontextprotocol.server.McpSyncServerExchange;
import io.modelcontextprotocol.spec.McpSchema;
import io.modelcontextprotocol.spec.McpSchema.CallToolResult;

import static org.easymock.EasyMock.*;
import static org.junit.jupiter.api.Assertions.*;

class TestRequestNetworkUploadTool {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private User mockUser = EasyMock.createMock(User.class);

    private McpSyncServerExchange exchangeWith(User user) {
        Map<String, Object> ctxMap = new HashMap<>();
        if (user != null) ctxMap.put("ndexUser", user);
        McpTransportContext ctx = McpTransportContext.create(ctxMap);
        McpSyncServerExchange exchange = EasyMock.createMock(McpSyncServerExchange.class);
        expect(exchange.transportContext()).andReturn(ctx).anyTimes();
        replay(exchange);
        return exchange;
    }

    private McpSchema.CallToolRequest requestWith(Map<String, Object> args) {
        return new McpSchema.CallToolRequest(RequestNetworkUploadTool.TOOL_NAME, args);
    }

    private Map<String, Object> parseStructuredContent(CallToolResult result) throws Exception {
        assertNotNull(result.structuredContent());
        return MAPPER.convertValue(result.structuredContent(), new com.fasterxml.jackson.core.type.TypeReference<>() {});
    }

    // ── schema ───────────────────────────────────────────────────────────────

    @Test
    void inputSchema_hasRequiredParams() throws Exception {
        RequestNetworkUploadTool tool = new RequestNetworkUploadTool(() -> "http://host:8080");
        String schema = RequestNetworkUploadTool.INPUT_SCHEMA;
        Map<?,?> parsed = MAPPER.readValue(schema, Map.class);
        @SuppressWarnings("unchecked")
        java.util.List<String> required = (java.util.List<String>) parsed.get("required");
        assertEquals(1, required.size());
        assertTrue(required.contains("file_path"), "only file_path must be in required list");
    }

    @Test
    void inputSchema_networkIdNotRequired() throws Exception {
        String schema = RequestNetworkUploadTool.INPUT_SCHEMA;
        Map<?,?> parsed = MAPPER.readValue(schema, Map.class);
        @SuppressWarnings("unchecked")
        java.util.List<String> required = (java.util.List<String>) parsed.get("required");
        assertFalse(required.contains("network_id"));
    }

    // ── auth ─────────────────────────────────────────────────────────────────

    @Test
    void handle_returnsUnauthorized_whenUserMissing() {
        RequestNetworkUploadTool tool = new RequestNetworkUploadTool(() -> "http://host");
        CallToolResult result = tool.toSpec().callHandler()
                .apply(exchangeWith(null), requestWith(Map.of("file_path", "/tmp/f.cx2")));
        assertTrue(result.isError());
        assertTrue(result.content().get(0).toString().contains("401"));
    }

    // ── create ────────────────────────────────────────────────────────────────

    @Test
    void handle_returnsUploadTokenResponse_forCreate() throws Exception {
        RequestNetworkUploadTool tool = new RequestNetworkUploadTool(() -> "http://test-host:8080");
        CallToolResult result = tool.toSpec().callHandler()
                .apply(exchangeWith(mockUser),
                       requestWith(Map.of("file_path", "/tmp/net.cx2")));

        assertFalse(result.isError());
        Map<String, Object> response = parseStructuredContent(result);

        String uploadUrl = (String) response.get("upload_url");
        assertTrue(uploadUrl.startsWith("http://test-host:8080/mcp/upload?upload_token="),
                   "upload_url should start with configured host + /mcp/upload?upload_token=");
        assertEquals("POST", response.get("method"));
        assertEquals("CXNetworkStream", response.get("field"));
        assertEquals("application/json", response.get("content_type"));
        assertEquals(120, response.get("expires_in_seconds"));
        assertEquals("/tmp/net.cx2", response.get("file_path"), "file_path should be echoed in response");
    }

    @Test
    void handle_returnsUploadTokenResponse_forUpdate() throws Exception {
        RequestNetworkUploadTool tool = new RequestNetworkUploadTool(() -> "http://test-host:8080");
        String networkId = "f93f402c-86d4-11e7-a10d-0ac135e8bacf";
        CallToolResult result = tool.toSpec().callHandler()
                .apply(exchangeWith(mockUser),
                       requestWith(Map.of("file_path", "/tmp/net.cx2", "network_id", networkId)));

        assertFalse(result.isError());
        Map<String, Object> response = parseStructuredContent(result);

        String uploadUrl = (String) response.get("upload_url");
        String token = uploadUrl.substring(uploadUrl.indexOf("upload_token=") + "upload_token=".length());
        assertFalse(token.isBlank(), "upload URL should contain a token");

        // Verify token carries the networkId
        var uploadReq = UploadService.getInstance().resolveToken(token);
        assertNotNull(uploadReq);
        assertEquals(networkId, uploadReq.networkId());
    }

    @Test
    void handle_usesLocalhostFallback_whenHostURIisNull() throws Exception {
        RequestNetworkUploadTool tool = new RequestNetworkUploadTool(() -> null);
        CallToolResult result = tool.toSpec().callHandler()
                .apply(exchangeWith(mockUser),
                       requestWith(Map.of("file_path", "/tmp/net.cx2")));

        assertFalse(result.isError());
        Map<String, Object> response = parseStructuredContent(result);
        assertTrue(((String) response.get("upload_url")).startsWith("http://localhost/mcp/upload"));
    }

    @Test
    void handle_usesLocalhostFallback_whenHostURIisBlank() throws Exception {
        RequestNetworkUploadTool tool = new RequestNetworkUploadTool(() -> "   ");
        CallToolResult result = tool.toSpec().callHandler()
                .apply(exchangeWith(mockUser),
                       requestWith(Map.of("file_path", "/tmp/net.cx2")));

        assertFalse(result.isError());
        Map<String, Object> response = parseStructuredContent(result);
        assertTrue(((String) response.get("upload_url")).startsWith("http://localhost/mcp/upload"));
    }

    @Test
    void handle_includesVisibilityAndFolderInToken() throws Exception {
        RequestNetworkUploadTool tool = new RequestNetworkUploadTool(() -> "http://host");
        Map<String, Object> folderIdArg = Map.of("waived", false, "parameter", "folder-uuid-123");
        CallToolResult result = tool.toSpec().callHandler()
                .apply(exchangeWith(mockUser),
                       requestWith(Map.of(
                           "file_path", "/tmp/net.cx2",
                           "visibility", "PUBLIC",
                           "folder_id", folderIdArg,
                           "extra_node_index", "geneSymbol")));

        assertFalse(result.isError());
        Map<String, Object> response = parseStructuredContent(result);
        String uploadUrl = (String) response.get("upload_url");
        String token = uploadUrl.substring(uploadUrl.indexOf("upload_token=") + "upload_token=".length());

        var uploadReq = UploadService.getInstance().resolveToken(token);
        assertNotNull(uploadReq);
        assertEquals("PUBLIC", uploadReq.visibility());
        assertEquals("folder-uuid-123", uploadReq.folderId());
        assertEquals("geneSymbol", uploadReq.extraNodeIndex());
        assertNull(uploadReq.networkId());
    }

    @Test
    void handle_waivedFolderIdYieldsNullFolderId() throws Exception {
        RequestNetworkUploadTool tool = new RequestNetworkUploadTool(() -> "http://host");
        Map<String, Object> folderIdArg = Map.of("waived", true);
        CallToolResult result = tool.toSpec().callHandler()
                .apply(exchangeWith(mockUser),
                       requestWith(Map.of("file_path", "/tmp/net.cx2", "folder_id", folderIdArg)));

        assertFalse(result.isError());
        Map<String, Object> response = parseStructuredContent(result);
        String uploadUrl = (String) response.get("upload_url");
        String token = uploadUrl.substring(uploadUrl.indexOf("upload_token=") + "upload_token=".length());

        var uploadReq = UploadService.getInstance().resolveToken(token);
        assertNotNull(uploadReq);
        assertNull(uploadReq.folderId(), "waived folder_id should resolve to null");
    }

    @Test
    void handle_echosFilePathWithSpaces() throws Exception {
        RequestNetworkUploadTool tool = new RequestNetworkUploadTool(() -> "http://host");
        String pathWithSpaces = "/Users/jsmith/Downloads/My Network File.cx2";
        CallToolResult result = tool.toSpec().callHandler()
                .apply(exchangeWith(mockUser),
                       requestWith(Map.of("file_path", pathWithSpaces)));

        assertFalse(result.isError());
        Map<String, Object> response = parseStructuredContent(result);
        assertEquals(pathWithSpaces, response.get("file_path"),
                     "file_path with spaces must be echoed verbatim in the response");
    }
}
