package org.ndexbio.rest.mcp.tools;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Timestamp;
import java.util.Collections;
import java.util.Map;

import io.modelcontextprotocol.common.McpTransportContext;
import io.modelcontextprotocol.server.McpServerFeatures;
import io.modelcontextprotocol.server.McpSyncServerExchange;
import io.modelcontextprotocol.spec.McpSchema;
import jakarta.servlet.http.HttpServletRequest;
import org.easymock.EasyMock;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.ndexbio.model.object.network.NetworkSummaryV3;
import org.ndexbio.rest.mcp.DownloadService;
import org.ndexbio.rest.mcp.ToolsService;

import static org.junit.jupiter.api.Assertions.*;

class TestDownloadNetworkTool {

    private static final String NETWORK_ID = "f93f402c-86d4-11e7-a10d-0ac135e8bacf";
    // Minimal valid CX2 document (verified against Cx2Network(InputStream))
    private static final String MINIMAL_CX2 =
        "[{\"CXVersion\":\"2.0\",\"hasErrors\":false},{\"status\":[{\"success\":true}]}]";

    private ToolsService   toolsService;
    private DownloadService downloadService;
    private DownloadNetworkTool tool;

    @TempDir
    Path tempDir;

    @BeforeEach
    void setUp() {
        toolsService    = new ToolsService();
        downloadService = new DownloadService();
        tool            = new DownloadNetworkTool(toolsService, downloadService);
    }

    // ── Tool spec / schema tests ────────────────────────────────────────────────────────

    @Test
    void toSpec_hasCorrectToolName() {
        assertEquals(DownloadNetworkTool.TOOL_NAME, tool.toSpec().tool().name());
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
    void inputSchema_hasThreeRequiredFields() {
        McpSchema.JsonSchema schema = tool.toSpec().tool().inputSchema();
        assertEquals(3, schema.required().size(), "exactly 3 fields should be required");
        assertTrue(schema.required().contains("networkId"),       "networkId should be required");
        assertTrue(schema.required().contains("network_summary"), "network_summary should be required");
        assertTrue(schema.required().contains("file_path"),       "file_path should be required");
    }

    @Test
    void inputSchema_hasFiveProperties() {
        Map<String, Object> props = tool.toSpec().tool().inputSchema().properties();
        assertTrue(props.containsKey("networkId"),                    "missing networkId");
        assertTrue(props.containsKey("network_summary"),              "missing network_summary");
        assertTrue(props.containsKey("file_path"),                    "missing file_path");
        assertTrue(props.containsKey("cx2NetworkCurrentChunkNumber"), "missing chunkNumber");
        assertTrue(props.containsKey("accessKey"),                    "missing accessKey");
        assertEquals(5, props.size());
    }

    @Test
    void outputSchema_hasExpectedFields() {
        Map<String, Object> outputSchema = tool.toSpec().tool().outputSchema();
        assertNotNull(outputSchema);
        @SuppressWarnings("unchecked")
        Map<String, Object> props = (Map<String, Object>) outputSchema.get("properties");
        assertNotNull(props, "outputSchema should have 'properties'");
        assertTrue(props.containsKey("isComplete"),         "missing isComplete");
        assertTrue(props.containsKey("chunkData"),          "missing chunkData");
        assertTrue(props.containsKey("chunkNumber"),        "missing chunkNumber");
        assertTrue(props.containsKey("totalChunks"),        "missing totalChunks");
        assertTrue(props.containsKey("cx2NetworkSizeBytes"),"missing cx2NetworkSizeBytes");
        assertTrue(props.containsKey("message"),            "missing message");
        assertTrue(props.containsKey("alreadyCurrent"),     "missing alreadyCurrent");
    }

    // ── Handler error-path tests ───────────────────────────────────────────────────────

    @Test
    void handle_nullHttpRequest_returnsError() {
        // Transport context empty → httpReq = null. sessionId is still mocked so execution
        // reaches getCX2Network(null,...) which NPEs and is caught as a Throwable.
        McpSyncServerExchange exchange = EasyMock.mock(McpSyncServerExchange.class);
        EasyMock.expect(exchange.transportContext()).andReturn(McpTransportContext.EMPTY).once();
        EasyMock.expect(exchange.sessionId()).andReturn("session-123").once();
        EasyMock.replay(exchange);

        McpSchema.CallToolResult result = invokeHandler(exchange,
                Map.of("networkId", NETWORK_ID,
                       "file_path", "/nonexistent/path.cx2"));

        assertTrue(result.isError(), "should return isError=true when httpReq is null");
        EasyMock.verify(exchange);
    }

    @Test
    void handle_securityExceptionFromContext_returnsUnauthorized() {
        McpTransportContext ctx = EasyMock.mock(McpTransportContext.class);
        EasyMock.expect(ctx.get("ndexRequest"))
                .andThrow(new SecurityException("access denied"))
                .once();
        McpSyncServerExchange exchange = EasyMock.mock(McpSyncServerExchange.class);
        EasyMock.expect(exchange.transportContext()).andReturn(ctx).once();
        EasyMock.replay(ctx, exchange);

        McpSchema.CallToolResult result = invokeHandler(exchange,
                Map.of("networkId", NETWORK_ID,
                       "file_path", "/tmp/net.cx2"));

        assertTrue(result.isError());
        assertEquals("401 Unauthorized",
                ((McpSchema.TextContent) result.content().get(0)).text());
        EasyMock.verify(ctx, exchange);
    }

    @Test
    void handle_nullSessionId_returnsError() {
        HttpServletRequest httpReq = EasyMock.mock(HttpServletRequest.class);
        McpTransportContext ctx = McpTransportContext.create(Map.of("ndexRequest", httpReq));
        McpSyncServerExchange exchange = EasyMock.mock(McpSyncServerExchange.class);
        EasyMock.expect(exchange.transportContext()).andReturn(ctx).once();
        EasyMock.expect(exchange.sessionId()).andReturn(null).once();
        EasyMock.replay(httpReq, exchange);

        McpSchema.CallToolResult result = invokeHandler(exchange,
                Map.of("networkId", NETWORK_ID,
                       "file_path", "/tmp/net.cx2"));

        assertTrue(result.isError(), "null sessionId should produce an error");
        String msg = ((McpSchema.TextContent) result.content().get(0)).text();
        assertTrue(msg.contains("session"), "error should mention 'session'");
        EasyMock.verify(httpReq, exchange);
    }

    @Test
    void handle_missingNetworkId_doesNotThrow() {
        // Omitting networkId — handler must not propagate any exception.
        McpSyncServerExchange exchange = EasyMock.mock(McpSyncServerExchange.class);
        EasyMock.expect(exchange.transportContext()).andReturn(McpTransportContext.EMPTY).once();
        EasyMock.expect(exchange.sessionId()).andReturn("session-123").once();
        EasyMock.replay(exchange);

        assertDoesNotThrow(() -> invokeHandler(exchange,
                Map.of("file_path", "/tmp/net.cx2")));
        EasyMock.verify(exchange);
    }

    @Test
    void handle_chunk2_withNoSession_returnsError() {
        // Chunk 2 with no active download session → should return error, not throw.
        HttpServletRequest httpReq = EasyMock.mock(HttpServletRequest.class);
        McpTransportContext ctx = McpTransportContext.create(Map.of("ndexRequest", httpReq));
        McpSyncServerExchange exchange = EasyMock.mock(McpSyncServerExchange.class);
        EasyMock.expect(exchange.transportContext()).andReturn(ctx).once();
        EasyMock.expect(exchange.sessionId()).andReturn("session-456").once();
        EasyMock.replay(httpReq, exchange);

        McpSchema.CallToolResult result = invokeHandler(exchange,
                Map.of("networkId", NETWORK_ID,
                       "file_path", "/tmp/net.cx2",
                       "cx2NetworkCurrentChunkNumber", 2));

        assertTrue(result.isError(), "missing session should produce an error");
        String msg = ((McpSchema.TextContent) result.content().get(0)).text();
        assertTrue(msg.contains("session"), "error should mention 'session'");
        EasyMock.verify(httpReq, exchange);
    }

    @Test
    void handle_outOfOrderChunk_returnsError() throws Exception {
        // Pre-seed a download session at chunk 1, then request chunk 3 (skipping 2).
        // recordChunkServed throws IllegalArgumentException → handler wraps it as error.
        String sessionId = "session-789";
        String cacheKey  = sessionId + ":download:" + NETWORK_ID;

        Path fakeSource = tempDir.resolve("network.cx2");
        Files.writeString(fakeSource, MINIMAL_CX2);

        long fileSize = Files.size(fakeSource);
        // 3 chunks: chunkSize chosen so file spans 3 chunks
        long chunkSize = fileSize == 0 ? 1 : (long) Math.ceil((double) fileSize / 3);
        downloadService.initSession(cacheKey, NETWORK_ID, fakeSource, fileSize * 3, chunkSize);
        downloadService.recordChunkServed(cacheKey, 1);  // advance to chunk 1

        HttpServletRequest httpReq = EasyMock.mock(HttpServletRequest.class);
        McpTransportContext ctx = McpTransportContext.create(Map.of("ndexRequest", httpReq));
        McpSyncServerExchange exchange = EasyMock.mock(McpSyncServerExchange.class);
        EasyMock.expect(exchange.transportContext()).andReturn(ctx).once();
        EasyMock.expect(exchange.sessionId()).andReturn(sessionId).once();
        EasyMock.replay(httpReq, exchange);

        // Skip chunk 2 — request chunk 3 directly
        McpSchema.CallToolResult result = invokeHandler(exchange,
                Map.of("networkId", NETWORK_ID,
                       "file_path", "/tmp/net.cx2",
                       "cx2NetworkCurrentChunkNumber", 3));

        assertTrue(result.isError(), "out-of-order chunk should produce an error");
        EasyMock.verify(httpReq, exchange);
    }

    @Test
    void handle_invalidUuid_returnsError() {
        // malformed UUID → passed to getCX2Network → IllegalArgumentException → error
        McpSyncServerExchange exchange = EasyMock.mock(McpSyncServerExchange.class);
        EasyMock.expect(exchange.transportContext()).andReturn(McpTransportContext.EMPTY).once();
        EasyMock.expect(exchange.sessionId()).andReturn("session-abc").once();
        EasyMock.replay(exchange);

        McpSchema.CallToolResult result = invokeHandler(exchange,
                Map.of("networkId", "not-a-valid-uuid",
                       "file_path", "/tmp/net.cx2"));

        assertTrue(result.isError(), "invalid UUID should produce an error");
        EasyMock.verify(exchange);
    }

    // ── readChunkFromFile helper ───────────────────────────────────────────────────────

    @Test
    void readChunkFromFile_readsFromCorrectOffset() throws Exception {
        // Write known content to a temp file and assert correct slice is returned.
        String content = "AAABBBCCC";
        Path source = tempDir.resolve("test.cx2");
        Files.writeString(source, content, StandardCharsets.UTF_8);

        // Read 3 bytes starting at offset 3 → should be "BBB"
        String chunk = DownloadNetworkTool.readChunkFromFile(source, 3, 3);
        assertEquals("BBB", chunk);
    }

    @Test
    void readChunkFromFile_offsetAtEof_returnsEmptyString() throws Exception {
        Path source = tempDir.resolve("empty.cx2");
        Files.writeString(source, "ABC", StandardCharsets.UTF_8);

        // Offset at or past end of file → empty string
        String chunk = DownloadNetworkTool.readChunkFromFile(source, 10, 5);
        assertEquals("", chunk);
    }

    // ── Cache-hit pre-flight tests ─────────────────────────────────────────────────────

    @Test
    void handle_fileDoesNotExist_skipesCacheCheck_proceedsToDownload() {
        // If file_path does not exist on disk, cache-hit pre-flight is skipped entirely.
        // getCX2Network is attempted (httpReq is null → NPE → caught → error result).
        McpSyncServerExchange exchange = EasyMock.mock(McpSyncServerExchange.class);
        EasyMock.expect(exchange.transportContext()).andReturn(McpTransportContext.EMPTY).once();
        EasyMock.expect(exchange.sessionId()).andReturn("session-cachecheck").once();
        EasyMock.replay(exchange);

        String nonExistentPath = tempDir.resolve("does-not-exist.cx2").toString();
        McpSchema.CallToolResult result = invokeHandler(exchange,
                Map.of("networkId", NETWORK_ID,
                       "file_path", nonExistentPath));

        // Should NOT see alreadyCurrent=true — file doesn't exist so no cache hit.
        // Error comes from null httpReq in getCX2Network, NOT from cache-hit logic.
        assertTrue(result.isError());
        EasyMock.verify(exchange);
    }

    @Test
    void handle_corruptLocalFile_skipsCache_proceedsToDownload() throws Exception {
        // If file_path exists but is NOT valid CX2, the exception is silently caught
        // and download proceeds (null httpReq → error result, but NOT alreadyCurrent=true).
        Path corruptFile = tempDir.resolve("corrupt.cx2");
        Files.writeString(corruptFile, "this is not valid CX2 JSON at all!");

        McpSyncServerExchange exchange = EasyMock.mock(McpSyncServerExchange.class);
        EasyMock.expect(exchange.transportContext()).andReturn(McpTransportContext.EMPTY).once();
        EasyMock.expect(exchange.sessionId()).andReturn("session-corrupt").once();
        EasyMock.replay(exchange);

        McpSchema.CallToolResult result = invokeHandler(exchange,
                Map.of("networkId", NETWORK_ID,
                       "file_path", corruptFile.toString()));

        // Not a cache-hit — corrupt file treated as cache miss → proceed to download → error
        assertTrue(result.isError());
        // Verify no "alreadyCurrent" leakage in the content
        assertNull(result.structuredContent(), "structuredContent should be null on error result");
        EasyMock.verify(exchange);
    }

    @Test
    void handle_validLocalFileCurrentVersion_returnsAlreadyCurrent() throws Exception {
        // Valid CX2 file with OS mtime >= networkSummary.modificationTime → cache hit.
        Path localFile = tempDir.resolve("current.cx2");
        Files.writeString(localFile, MINIMAL_CX2);

        // Ensure the file's mtime appears AFTER the network modification time
        long pastModTime = System.currentTimeMillis() - 10_000;  // 10 seconds ago
        new File(localFile.toString()).setLastModified(pastModTime - 5_000);
        // Network modificationTime = 15 seconds ago (older than file)
        long networkModTime = pastModTime - 5_000;

        NetworkSummaryV3 summary = new NetworkSummaryV3();
        summary.setModificationTime(new Timestamp(networkModTime));

        com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
        Map<String, Object> summaryMap = mapper.convertValue(summary,
                new com.fasterxml.jackson.core.type.TypeReference<Map<String, Object>>() {});

        // No HTTP exchange needed — cache hit returns before any service call
        McpSyncServerExchange exchange = EasyMock.mock(McpSyncServerExchange.class);
        EasyMock.expect(exchange.transportContext()).andReturn(McpTransportContext.EMPTY).once();
        EasyMock.expect(exchange.sessionId()).andReturn("session-current").once();
        EasyMock.replay(exchange);

        McpSchema.CallToolResult result = invokeHandler(exchange,
                Map.of("networkId", NETWORK_ID,
                       "file_path", localFile.toString(),
                       "network_summary", summaryMap));

        assertFalse(result.isError(), "cache-hit should not produce an error result");
        assertNotNull(result.structuredContent(), "should have structured content on cache-hit");

        @SuppressWarnings("unchecked")
        Map<String, Object> resp = (Map<String, Object>)
                mapper.convertValue(result.structuredContent(), Map.class);
        assertEquals(true, resp.get("alreadyCurrent"),
                "alreadyCurrent should be true on cache-hit");
        assertEquals(true,  resp.get("isComplete"),
                "isComplete should be true on cache-hit");
        assertNull(resp.get("chunkData"), "chunkData should be null on cache-hit");

        EasyMock.verify(exchange);
    }

    // ── Coverage-gap tests ─────────────────────────────────────────────────────────────

    @Test
    void handle_validLocalFileStale_proceedsToDownload() throws Exception {
        // Valid CX2 file whose OS mtime is OLDER than networkSummary.modificationTime
        // → fileModMillis < networkModMillis → no cache hit → falls through to getCX2Network.
        // httpReq is null (EMPTY context) → error result, but must NOT be alreadyCurrent.
        Path localFile = tempDir.resolve("stale.cx2");
        Files.writeString(localFile, MINIMAL_CX2);

        long networkModTime = System.currentTimeMillis() - 10_000;  // 10 s ago (server newer)
        long fileMtime      = networkModTime - 20_000;               // 30 s ago (file older)
        new File(localFile.toString()).setLastModified(fileMtime);

        NetworkSummaryV3 summary = new NetworkSummaryV3();
        summary.setModificationTime(new Timestamp(networkModTime));

        com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
        Map<String, Object> summaryMap = mapper.convertValue(summary,
                new com.fasterxml.jackson.core.type.TypeReference<Map<String, Object>>() {});

        McpSyncServerExchange exchange = EasyMock.mock(McpSyncServerExchange.class);
        EasyMock.expect(exchange.transportContext()).andReturn(McpTransportContext.EMPTY).once();
        EasyMock.expect(exchange.sessionId()).andReturn("session-stale").once();
        EasyMock.replay(exchange);

        McpSchema.CallToolResult result = invokeHandler(exchange,
                Map.of("networkId", NETWORK_ID,
                       "file_path", localFile.toString(),
                       "network_summary", summaryMap));

        // Must NOT be a cache-hit: stale local file must proceed to download (fails on null httpReq)
        assertTrue(result.isError(), "stale local file must NOT produce a cache-hit");
        assertNull(result.structuredContent(), "structuredContent should be null on error");
        EasyMock.verify(exchange);
    }

    @Test
    void handle_nullNetworkSummaryModificationTime_proceedsToDownload() throws Exception {
        // Valid CX2 file but network_summary has no modificationTime
        // → networkModMillis defaults to Long.MAX_VALUE
        // → fileModMillis >= Long.MAX_VALUE is always false → no cache hit
        Path localFile = tempDir.resolve("nullmod.cx2");
        Files.writeString(localFile, MINIMAL_CX2);

        // Empty map → NetworkSummaryV3 with null modificationTime
        McpSyncServerExchange exchange = EasyMock.mock(McpSyncServerExchange.class);
        EasyMock.expect(exchange.transportContext()).andReturn(McpTransportContext.EMPTY).once();
        EasyMock.expect(exchange.sessionId()).andReturn("session-nullmod").once();
        EasyMock.replay(exchange);

        McpSchema.CallToolResult result = invokeHandler(exchange,
                Map.of("networkId", NETWORK_ID,
                       "file_path", localFile.toString(),
                       "network_summary", Collections.emptyMap()));

        // null modificationTime → networkModMillis = MAX_VALUE → no cache hit
        assertTrue(result.isError(), "null modificationTime must not produce a cache-hit");
        assertNull(result.structuredContent(), "structuredContent should be null on error");
        EasyMock.verify(exchange);
    }

    @Test
    void handle_blankFilePath_skipsCacheCheck() {
        // file_path is blank → guard (!filePath.isBlank()) fails → cache-hit block skipped entirely.
        // Falls through to getCX2Network (null httpReq → error result, not alreadyCurrent).
        McpSyncServerExchange exchange = EasyMock.mock(McpSyncServerExchange.class);
        EasyMock.expect(exchange.transportContext()).andReturn(McpTransportContext.EMPTY).once();
        EasyMock.expect(exchange.sessionId()).andReturn("session-blank").once();
        EasyMock.replay(exchange);

        McpSchema.CallToolResult result = invokeHandler(exchange,
                Map.of("networkId", NETWORK_ID,
                       "file_path", ""));

        assertTrue(result.isError(), "blank file_path must skip cache check and proceed to download");
        assertNull(result.structuredContent());
        EasyMock.verify(exchange);
    }

    @Test
    void handle_chunkNumberZero_returnsSessionError() {
        // chunkNumber = 0 → falls to else branch (0 != 1) → getSession returns null → session error.
        HttpServletRequest httpReq = EasyMock.mock(HttpServletRequest.class);
        McpTransportContext ctx = McpTransportContext.create(Map.of("ndexRequest", httpReq));
        McpSyncServerExchange exchange = EasyMock.mock(McpSyncServerExchange.class);
        EasyMock.expect(exchange.transportContext()).andReturn(ctx).once();
        EasyMock.expect(exchange.sessionId()).andReturn("session-zero").once();
        EasyMock.replay(httpReq, exchange);

        McpSchema.CallToolResult result = invokeHandler(exchange,
                Map.of("networkId", NETWORK_ID,
                       "file_path", "/tmp/net.cx2",
                       "cx2NetworkCurrentChunkNumber", 0));

        assertTrue(result.isError(), "chunkNumber=0 must produce a session-not-found error");
        String msg = ((McpSchema.TextContent) result.content().get(0)).text();
        assertTrue(msg.contains("session"), "error should mention 'session'");
        EasyMock.verify(httpReq, exchange);
    }

    @Test
    void readChunkFromFile_lastPartialChunk_returnsOnlyRemainingBytes() throws Exception {
        // 10-byte file, chunkSize=6:  chunk 1 = bytes[0..6), chunk 2 = bytes[6..10) → 4 bytes.
        // Verifies Math.min(chunkSize, remaining) returns the correct partial count.
        byte[] content = "AAAAAABBBB".getBytes(StandardCharsets.UTF_8);
        Path source = tempDir.resolve("partial.cx2");
        Files.write(source, content);

        String chunk = DownloadNetworkTool.readChunkFromFile(source, 6, 6);
        assertEquals(4, chunk.length(), "last partial chunk should return only the 4 remaining bytes");
        assertEquals("BBBB", chunk);
    }

    // Helper: invokes the registered handler synchronously
    private McpSchema.CallToolResult invokeHandler(McpSyncServerExchange exchange,
                                                    Map<String, Object> arguments) {
        McpServerFeatures.SyncToolSpecification spec = tool.toSpec();
        McpSchema.CallToolRequest request =
                new McpSchema.CallToolRequest(DownloadNetworkTool.TOOL_NAME, arguments);
        return spec.callHandler().apply(exchange, request);
    }
}
