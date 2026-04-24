package org.ndexbio.rest.mcp.tools;

import java.util.HashMap;
import java.util.Map;

import org.easymock.EasyMock;
import org.junit.jupiter.api.Test;
import org.ndexbio.model.object.User;
import org.ndexbio.rest.mcp.DownloadTokenService;

import com.fasterxml.jackson.databind.ObjectMapper;

import io.modelcontextprotocol.common.McpTransportContext;
import io.modelcontextprotocol.server.McpSyncServerExchange;
import io.modelcontextprotocol.spec.McpSchema;
import io.modelcontextprotocol.spec.McpSchema.CallToolResult;

import static org.easymock.EasyMock.*;
import static org.junit.jupiter.api.Assertions.*;

class TestRequestNetworkDownloadTool {

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
        return new McpSchema.CallToolRequest(RequestNetworkDownloadTool.TOOL_NAME, args);
    }

    private Map<String, Object> parseStructuredContent(CallToolResult result) throws Exception {
        assertNotNull(result.structuredContent());
        return MAPPER.convertValue(result.structuredContent(),
                new com.fasterxml.jackson.core.type.TypeReference<>() {});
    }

    // ── schema ───────────────────────────────────────────────────────────────

    @Test
    void inputSchema_hasRequiredParams() throws Exception {
        String schema = RequestNetworkDownloadTool.INPUT_SCHEMA;
        Map<?,?> parsed = MAPPER.readValue(schema, Map.class);
        @SuppressWarnings("unchecked")
        java.util.List<String> required = (java.util.List<String>) parsed.get("required");
        assertEquals(2, required.size());
        assertTrue(required.contains("network_id"), "network_id must be required");
        assertTrue(required.contains("file_path"),  "file_path must be required");
    }

    @Test
    void inputSchema_accessKeyNotRequired() throws Exception {
        String schema = RequestNetworkDownloadTool.INPUT_SCHEMA;
        Map<?,?> parsed = MAPPER.readValue(schema, Map.class);
        @SuppressWarnings("unchecked")
        java.util.List<String> required = (java.util.List<String>) parsed.get("required");
        assertFalse(required.contains("access_key"));
    }

    // ── auth ─────────────────────────────────────────────────────────────────

    @Test
    void handle_returnsUnauthorized_whenUserMissing() {
        RequestNetworkDownloadTool tool = new RequestNetworkDownloadTool(() -> "http://host");
        CallToolResult result = tool.toSpec().callHandler()
                .apply(exchangeWith(null),
                       requestWith(Map.of("network_id", "f93f402c-86d4-11e7-a10d-0ac135e8bacf",
                                          "file_path", "/tmp/net.cx2")));
        assertTrue(result.isError());
        assertTrue(result.content().get(0).toString().contains("401"));
    }

    // ── token response ────────────────────────────────────────────────────────

    @Test
    void handle_returnsDownloadTokenResponse() throws Exception {
        RequestNetworkDownloadTool tool = new RequestNetworkDownloadTool(() -> "http://test-host:8080");
        CallToolResult result = tool.toSpec().callHandler()
                .apply(exchangeWith(mockUser),
                       requestWith(Map.of("network_id", "f93f402c-86d4-11e7-a10d-0ac135e8bacf",
                                          "file_path", "/tmp/net.cx2")));

        assertFalse(result.isError());
        Map<String, Object> response = parseStructuredContent(result);

        String downloadUrl = (String) response.get("download_url");
        assertTrue(downloadUrl.startsWith("http://test-host:8080/mcp/download?download_token="),
                   "download_url should start with configured host + /mcp/download?download_token=");
        assertEquals("GET", response.get("method"));
        assertEquals(120, response.get("expires_in_seconds"));
        assertEquals("/tmp/net.cx2", response.get("file_path"), "file_path should be echoed in response");
    }

    @Test
    void handle_usesLocalhostFallback_whenHostURIisNull() throws Exception {
        RequestNetworkDownloadTool tool = new RequestNetworkDownloadTool(() -> null);
        CallToolResult result = tool.toSpec().callHandler()
                .apply(exchangeWith(mockUser),
                       requestWith(Map.of("network_id", "f93f402c-86d4-11e7-a10d-0ac135e8bacf",
                                          "file_path", "/tmp/net.cx2")));

        assertFalse(result.isError());
        Map<String, Object> response = parseStructuredContent(result);
        assertTrue(((String) response.get("download_url")).startsWith("http://localhost/mcp/download"));
        assertEquals("/tmp/net.cx2", response.get("file_path"), "file_path should be echoed in response");
    }

    @Test
    void handle_usesLocalhostFallback_whenHostURIisBlank() throws Exception {
        RequestNetworkDownloadTool tool = new RequestNetworkDownloadTool(() -> "   ");
        CallToolResult result = tool.toSpec().callHandler()
                .apply(exchangeWith(mockUser),
                       requestWith(Map.of("network_id", "f93f402c-86d4-11e7-a10d-0ac135e8bacf",
                                          "file_path", "/tmp/net.cx2")));

        assertFalse(result.isError());
        Map<String, Object> response = parseStructuredContent(result);
        assertTrue(((String) response.get("download_url")).startsWith("http://localhost/mcp/download"));
        assertEquals("/tmp/net.cx2", response.get("file_path"), "file_path should be echoed in response");
    }

    @Test
    void handle_storesNetworkIdAndAccessKeyInToken() throws Exception {
        RequestNetworkDownloadTool tool = new RequestNetworkDownloadTool(() -> "http://host");
        String networkId = "f93f402c-86d4-11e7-a10d-0ac135e8bacf";
        String accessKey = "secret-key-xyz";
        CallToolResult result = tool.toSpec().callHandler()
                .apply(exchangeWith(mockUser),
                       requestWith(Map.of("network_id", networkId,
                                          "file_path", "/tmp/net.cx2",
                                          "access_key", accessKey)));

        assertFalse(result.isError());
        Map<String, Object> response = parseStructuredContent(result);
        String downloadUrl = (String) response.get("download_url");
        String token = downloadUrl.substring(downloadUrl.indexOf("download_token=") + "download_token=".length());

        var downloadReq = DownloadTokenService.getInstance().resolveToken(token);
        assertNotNull(downloadReq);
        assertEquals(networkId, downloadReq.networkId());
        assertEquals(accessKey, downloadReq.accessKey());
    }

    @Test
    void handle_echosFilePathWithSpaces() throws Exception {
        RequestNetworkDownloadTool tool = new RequestNetworkDownloadTool(() -> "http://host");
        String pathWithSpaces = "/Users/jsmith/Downloads/My Network.cx2";
        CallToolResult result = tool.toSpec().callHandler()
                .apply(exchangeWith(mockUser),
                       requestWith(Map.of("network_id", "f93f402c-86d4-11e7-a10d-0ac135e8bacf",
                                          "file_path", pathWithSpaces)));

        assertFalse(result.isError());
        Map<String, Object> response = parseStructuredContent(result);
        assertEquals(pathWithSpaces, response.get("file_path"),
                     "file_path with spaces must be echoed verbatim in the response");
    }
}
