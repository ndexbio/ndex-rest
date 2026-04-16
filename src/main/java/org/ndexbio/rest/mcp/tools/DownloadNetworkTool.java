package org.ndexbio.rest.mcp.tools;

import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.RandomAccessFile;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import jakarta.servlet.http.HttpServletRequest;

import org.ndexbio.common.persistence.CX2NetworkLoader;
import org.ndexbio.cx2.aspect.element.core.Cx2Network;
import org.ndexbio.model.exceptions.UnauthorizedOperationException;
import org.ndexbio.model.object.network.NetworkSummaryV3;
import org.ndexbio.rest.Configuration;
import org.ndexbio.rest.mcp.DownloadService;
import org.ndexbio.rest.mcp.McpSchema;
import org.ndexbio.rest.mcp.ToolsService;
import org.ndexbio.rest.services.v3.NetworkServiceV3;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import com.fasterxml.jackson.databind.ObjectMapper;

import io.modelcontextprotocol.server.McpServerFeatures;
import io.modelcontextprotocol.server.McpSyncServerExchange;
import io.modelcontextprotocol.spec.McpSchema.CallToolRequest;
import io.modelcontextprotocol.spec.McpSchema.CallToolResult;
import io.modelcontextprotocol.spec.McpSchema.JsonSchema;
import io.modelcontextprotocol.spec.McpSchema.Tool;

/**
 * MCP tool: download_network
 *
 * Retrieves the CX2 content of an NDEx network in sequential fixed-size chunks.
 * Each call returns one chunk of up to 5 MB; the MCP client (LLM agent) accumulates
 * chunks into a local temp file and, when isComplete=true, moves the temp file to the
 * supplied file_path destination.
 *
 * <p>On chunk 1 a cache-hit pre-flight is performed: if the file at file_path already
 * exists, is valid CX2, and has an OS modification time &gt;= the network's
 * modificationTime from the required network_summary parameter, the tool returns
 * {@code alreadyCurrent=true} immediately without fetching any data.
 */
public class DownloadNetworkTool {

    private static final Logger logger = LoggerFactory.getLogger(DownloadNetworkTool.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();

    static final String TOOL_NAME  = "download_network";
    static final long   CHUNK_SIZE = 5L * 1024 * 1024;  // 5 MB — matches UpdateNetworkTool

    private static final String TOOL_DESCRIPTION =
        "Retrieve the CX2 representation of an NDEx network identified by UUID and save it to the " +
        "local file system. Because CX2 files can be arbitrarily large, the server delivers the " +
        "content in sequential fixed-size chunks of up to 5 MB each; the caller invokes the tool " +
        "repeatedly until the response signals the download is complete, then assembles the " +
        "accumulated chunks into the final destination file on the local file system.\n\n" +
        "Before initiating retrieval, the tool performs a cache-hit check on the first call: if " +
        "the file at the destination path already exists, is valid CX2, and its modification time " +
        "is at least as recent as the network's server-side modification time from the provided " +
        "network metadata summary, the tool returns an immediate success response and skips all " +
        "data transfer.\n\n" +
        "Public networks are accessible without authentication; private networks require an active " +
        "authenticated session or a caller-supplied access key. Returns an error response if the " +
        "network does not exist, has no CX2 representation on the server, the caller lacks read " +
        "permission, or a download session expires mid-transfer (the caller must restart from the " +
        "first chunk).\n\n" +
        "## Examples\n\n" +
        "Example 1 — Download a small public network in one call:\n" +
        "Prompt: 'Download NDEx network f93f402c-86d4-11e7-a10d-0ac135e8bacf as CX2 to /tmp/net.cx2'\n" +
        "{\"networkId\": \"f93f402c-86d4-11e7-a10d-0ac135e8bacf\",\n" +
        " \"network_summary\": {...},\n" +
        " \"file_path\": \"/tmp/net.cx2\"}\n\n" +
        "Example 2 — Download a large network; continue from chunk 2:\n" +
        "Prompt: 'Retrieve the next chunk of the network download in progress'\n" +
        "{\"networkId\": \"f93f402c-86d4-11e7-a10d-0ac135e8bacf\",\n" +
        " \"network_summary\": {...},\n" +
        " \"file_path\": \"/tmp/net.cx2\",\n" +
        " \"cx2NetworkCurrentChunkNumber\": 2}\n\n" +
        "Example 3 — Download a private network with an access key:\n" +
        "Prompt: 'Download my private network and save it as a CX2 file at /tmp/mynet.cx2'\n" +
        "{\"networkId\": \"9a8f5ab1-3a5c-11e8-a935-0ac135e8bacf\",\n" +
        " \"network_summary\": {...},\n" +
        " \"accessKey\": \"abc123xyz\",\n" +
        " \"file_path\": \"/tmp/mynet.cx2\"}";

    static final String INPUT_SCHEMA = McpSchema.toJson(
        McpSchema.InputSchema.builder()
            .required("networkId", "network_summary", "file_path")
            .property("networkId", new McpSchema.InputProperty("string",
                "Required. UUID of the NDEx network to download in CX2 format. The network must have " +
                "a CX2 representation available on the server; an error is returned for networks that " +
                "were never converted to CX2.\n\n" +
                "Examples: \"f93f402c-86d4-11e7-a10d-0ac135e8bacf\", " +
                "\"9a8f5ab1-3a5c-11e8-a935-0ac135e8bacf\""))
            .property("network_summary", new McpSchema.InputProperty("object",
                "Required. The full metadata summary for the NDEx hosted network identified by the " +
                "networkId parameter. Obtain this by calling the network summary lookup with the same " +
                "networkId before invoking this tool. The modificationTime field from this summary is " +
                "compared against the local file at the destination path to determine whether the " +
                "cached copy is already current.\n\n" +
                "Example: the full JSON object returned by network summary lookup for the target networkId"))
            .property("file_path", new McpSchema.InputProperty("string",
                "Required. The final destination path for the complete assembled CX2 file. On the " +
                "first call, this path is checked for a cache-hit: if the file exists, is valid CX2, " +
                "and has a modification time >= the network's modificationTime from network_summary, " +
                "the tool returns an immediate success and skips retrieval. The tool itself never " +
                "writes to this path; the caller is responsible for assembling chunk data into this " +
                "file after the last chunk is received.\n\n" +
                "Examples: \"/tmp/mynetwork.cx2\", \"/home/ndex/exports/net.cx2\""))
            .property("cx2NetworkCurrentChunkNumber", new McpSchema.InputProperty("integer",
                "Optional. 1-based index of the chunk to retrieve in the current download sequence. " +
                "Defaults to 1 when omitted; omit on the very first call. After each non-final " +
                "response, increment this value by exactly 1 and call again. Do not skip or repeat " +
                "chunk numbers; the server enforces strict sequential ordering within a download " +
                "session.\n\n" +
                "Examples: 1, 2, 3"))
            .property("accessKey", new McpSchema.InputProperty("string",
                "Optional. Access key granting read permission on networks that are not publicly " +
                "visible. Supply on the first call only; not needed on subsequent chunk calls. " +
                "Ignored for public networks.\n\n" +
                "Examples: \"abc123xyz\", \"private-key-99\""))
            .build());

    static final String OUTPUT_SCHEMA = McpSchema.toSchemaJson(DownloadNetworkResponse.class);

    private static final Tool TOOL;
    static {
        try {
            TOOL = Tool.builder()
                .name(TOOL_NAME)
                .description(TOOL_DESCRIPTION)
                .inputSchema(MAPPER.readValue(INPUT_SCHEMA, JsonSchema.class))
                .outputSchema(MAPPER.readValue(OUTPUT_SCHEMA,
                    new com.fasterxml.jackson.core.type.TypeReference<Map<String, Object>>() {}))
                .build();
        } catch (Exception e) {
            throw new ExceptionInInitializerError(e);
        }
    }

    private final ToolsService toolsService;
    private final DownloadService downloadService;

    public DownloadNetworkTool(ToolsService toolsService, DownloadService downloadService) {
        this.toolsService    = toolsService;
        this.downloadService = downloadService;
    }

    public McpServerFeatures.SyncToolSpecification toSpec() {
        return McpServerFeatures.SyncToolSpecification.builder()
                .tool(TOOL)
                .callHandler(this::handle)
                .build();
    }

    private CallToolResult handle(McpSyncServerExchange exchange, CallToolRequest req) {
        try {
            HttpServletRequest httpReq = (HttpServletRequest)
                    exchange.transportContext().get("ndexRequest");

            DownloadNetworkRequest input = MAPPER.convertValue(req.arguments(),
                    DownloadNetworkRequest.class);
            NetworkSummaryV3 networkSummary = req.arguments() != null
                    ? MAPPER.convertValue(req.arguments().get("network_summary"), NetworkSummaryV3.class)
                    : null;

            String networkId    = input.networkId();
            String filePath     = input.filePath();
            int    chunkNumber  = input.cx2NetworkCurrentChunkNumber() != null
                                  ? input.cx2NetworkCurrentChunkNumber() : 1;

            String sessionId = exchange.sessionId();
            if (sessionId == null) {
                return errorResult("download_network failed: no MCP session ID present. " +
                                   "Chunked downloads require an active MCP session.");
            }
            String cacheKey = sessionId + ":download:" + networkId;

            // ── Cache-hit pre-flight (chunk 1 only) ──────────────────────────────────────
            if (chunkNumber == 1 && filePath != null && !filePath.isBlank()) {
                Path localPath = Path.of(filePath);
                if (Files.exists(localPath)) {
                    try {
                        try (InputStream localIn = new FileInputStream(localPath.toFile())) {
                            new Cx2Network(localIn);  // validate — throws if corrupt/invalid CX2
                        }
                        long fileModMillis    = Files.getLastModifiedTime(localPath).toMillis();
                        long networkModMillis = networkSummary != null
                                && networkSummary.getModificationTime() != null
                                ? networkSummary.getModificationTime().getTime() : Long.MAX_VALUE;
                        if (fileModMillis >= networkModMillis) {
                            return structuredResult(new DownloadNetworkResponse(
                                true, null, 1, 1, null, true, filePath,
                                "Network file at file_path is already current (local mtime " +
                                fileModMillis + " >= network mtime " + networkModMillis +
                                "). No retrieval needed."));
                        }
                    } catch (Exception ignored) {
                        // Corrupt, unreadable, or invalid format — treat as cache miss
                    }
                }
            }

            // ── Chunk routing ─────────────────────────────────────────────────────────────
            DownloadService.DownloadSessionState session;
            String chunkData;
            long   totalFileSize;
            int    totalChunks;

            if (chunkNumber == 1) {
                // Auth + CX2 existence check via getCX2Network (throws on auth/not-found)
                jakarta.ws.rs.core.Response response = new NetworkServiceV3(httpReq)
                        .getCX2Network(networkId, false, input.accessKey(), null, null);

                byte[] bytes;
                try (InputStream in = (InputStream) response.getEntity()) {
                    bytes = in.readNBytes((int) CHUNK_SIZE);
                }
                chunkData = new String(bytes, StandardCharsets.UTF_8);

                Path sourcePath = Path.of(Configuration.getInstance().getNdexRoot(),
                        "data", networkId, CX2NetworkLoader.cx2NetworkFileName);
                totalFileSize = Files.size(sourcePath);

                session     = downloadService.initSession(cacheKey, networkId, sourcePath,
                                                          totalFileSize, CHUNK_SIZE);
                totalChunks = session.totalChunks();
                boolean isLast = downloadService.recordChunkServed(cacheKey, 1);
                if (isLast) downloadService.closeSession(cacheKey);

            } else {
                session = downloadService.getSession(cacheKey);
                if (session == null) {
                    return errorResult("download_network failed: download session not found or " +
                                       "expired for network '" + networkId + "'. " +
                                       "Restart from cx2NetworkCurrentChunkNumber=1.");
                }
                long offset = (long)(chunkNumber - 1) * session.chunkSize();
                chunkData     = readChunkFromFile(session.sourcePath(), offset, session.chunkSize());
                totalFileSize = session.fileSize();
                totalChunks   = session.totalChunks();
                boolean isLast = downloadService.recordChunkServed(cacheKey, chunkNumber);
                if (isLast) downloadService.closeSession(cacheKey);
            }

            boolean isComplete = (chunkNumber == totalChunks);
            String message;
            if (isComplete) {
                message = "Download complete. " + totalChunks + " chunk(s) delivered. " +
                          "Append this chunkData to file_path+'.tmp', then move file_path+'.tmp' to file_path.";
            } else if (chunkNumber == 1) {
                message = "Chunk 1 of " + totalChunks + " delivered. " +
                          "Write chunkData to file_path+'.tmp' (CREATE/TRUNCATE). " +
                          "Call again with cx2NetworkCurrentChunkNumber=2.";
            } else {
                message = "Chunk " + chunkNumber + " of " + totalChunks + " delivered. " +
                          "Append chunkData to file_path+'.tmp'. " +
                          "Call again with cx2NetworkCurrentChunkNumber=" + (chunkNumber + 1) + ".";
            }

            return structuredResult(new DownloadNetworkResponse(
                    isComplete, chunkData, chunkNumber, totalChunks, totalFileSize,
                    null, null, message));

        } catch (UnauthorizedOperationException | SecurityException e) {
            return toolsService.unauthorizedResult();
        } catch (Throwable e) {
            logger.error("download_network failed", e);
            return errorResult("download_network failed: " + e.getMessage());
        }
    }

    /**
     * Read a fixed-size slice from a file using random access, starting at {@code offset}.
     * Returns an empty string if the offset is at or past the end of file.
     */
    static String readChunkFromFile(Path sourcePath, long offset, long chunkSize) throws IOException {
        try (RandomAccessFile raf = new RandomAccessFile(sourcePath.toFile(), "r")) {
            raf.seek(offset);
            long remaining = raf.length() - offset;
            int  toRead    = (int) Math.min(chunkSize, remaining);
            if (toRead <= 0) return "";
            byte[] buf = new byte[toRead];
            raf.readFully(buf);
            return new String(buf, StandardCharsets.UTF_8);
        }
    }

    private static CallToolResult structuredResult(DownloadNetworkResponse response) {
        return CallToolResult.builder().structuredContent(response).build();
    }

    private static CallToolResult errorResult(String message) {
        return CallToolResult.builder().isError(true).addTextContent(message).build();
    }

    // ── Inner records ────────────────────────────────────────────────────────────────────

    @JsonInclude(JsonInclude.Include.NON_NULL)
    record DownloadNetworkResponse(
        @JsonPropertyDescription(
            "false while more chunks remain in the download sequence; true when this response " +
            "delivers the final chunk. Follow the per-chunk instructions in the message field " +
            "to accumulate and finalise the destination file.\n\n" +
            "Examples: true, false")
        @JsonProperty("isComplete")
        boolean isComplete,

        @JsonPropertyDescription(
            "The CX2 content for this chunk as a UTF-8 string. Each chunkData value is a contiguous " +
            "byte slice of the full CX2 JSON array. Write chunk 1 to file_path+'.tmp' " +
            "(CREATE/TRUNCATE), APPEND all subsequent chunks. On the final chunk, append chunkData " +
            "to file_path+'.tmp' then move file_path+'.tmp' to file_path. " +
            "null on cache-hit responses (alreadyCurrent=true).")
        @JsonProperty("chunkData")
        String chunkData,

        @JsonPropertyDescription(
            "1-based index of this chunk in the download sequence. Matches the " +
            "cx2NetworkCurrentChunkNumber value supplied in the request.\n\n" +
            "Examples: 1, 2, 3")
        @JsonProperty("chunkNumber")
        Integer chunkNumber,

        @JsonPropertyDescription(
            "Total number of chunks in the complete download sequence for this network. Determined " +
            "from the file size on the first call and included in every response. Use this together " +
            "with chunkNumber to track download progress.\n\n" +
            "Examples: 1, 5, 20")
        @JsonProperty("totalChunks")
        Integer totalChunks,

        @JsonPropertyDescription(
            "Total size of the network's CX2 file in bytes. Present on every response. Use this " +
            "to display download progress or to pre-allocate storage.\n\n" +
            "Examples: 1048576, 8388608, 52428800")
        @JsonProperty("cx2NetworkSizeBytes")
        Long cx2NetworkSizeBytes,

        @JsonPropertyDescription(
            "true when the file at the destination path already contains the current version of the " +
            "network and no retrieval was performed. The local file modification time was >= the " +
            "network modification time from network_summary. Present only on cache-hit responses.\n\n" +
            "Examples: true")
        @JsonProperty("alreadyCurrent")
        Boolean alreadyCurrent,

        @JsonPropertyDescription(
            "Absolute path of the final destination file as supplied in the file_path input. " +
            "Present only on cache-hit responses (alreadyCurrent=true) to confirm the location " +
            "of the already-current file.\n\n" +
            "Examples: \"/tmp/network.cx2\", \"/home/ndex/exports/net.cx2\"")
        @JsonProperty("filePath")
        String filePath,

        @JsonPropertyDescription(
            "Human-readable per-chunk instruction for the caller. On non-final chunks: instructs " +
            "the caller to write/append chunkData to file_path+'.tmp' and call again with the next " +
            "chunk number. On the final chunk: instructs the caller to append the final chunkData " +
            "and move file_path+'.tmp' to file_path. On cache-hit: confirms no action is needed.")
        @JsonProperty("message")
        String message
    ) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    record DownloadNetworkRequest(
        @JsonProperty("networkId")                    String  networkId,
        @JsonProperty("file_path")                    String  filePath,
        @JsonProperty("cx2NetworkCurrentChunkNumber") Integer cx2NetworkCurrentChunkNumber,
        @JsonProperty("accessKey")                    String  accessKey
    ) {}
}
