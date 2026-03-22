package org.ndexbio.rest.mcp.tools;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import jakarta.servlet.http.HttpServletRequest;

import org.ndexbio.model.exceptions.ForbiddenOperationException;
import org.ndexbio.model.exceptions.UnauthorizedOperationException;
import org.ndexbio.model.object.NdexObjectUpdateStatus;
import org.ndexbio.model.object.User;
import org.ndexbio.rest.mcp.McpSchema;
import org.ndexbio.rest.mcp.NdexObjectUpdateStatusMixIn;
import org.ndexbio.rest.mcp.ToolsService;
import org.ndexbio.rest.mcp.UploadService;
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
 * MCP tool: update_network
 *
 * Replaces the full CX2 content of an existing NDEx network using chunked string
 * transfer. The LLM splits the serialized CX2 into sequential chunks and submits
 * one tool call per chunk. The server accumulates chunks as file appends and triggers
 * the NDEx update atomically when the final chunk arrives.
 */
public class UpdateNetworkTool {

    private static final Logger logger = LoggerFactory.getLogger(UpdateNetworkTool.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();

    static final String TOOL_NAME = "update_network";

    // Hard cap: reject any upload whose declared total size exceeds this limit
    static final long MAX_NETWORK_SIZE_BYTES     = 5L * 1024 * 1024 * 1024; // 5 GB
    static final long MAX_CHUNK_SIZE_BYTES       = 5L * 1024 * 1024;         // 5 MB
    private static final long MAX_NETWORK_SIZE_GB = MAX_NETWORK_SIZE_BYTES / (1024 * 1024 * 1024);

    private static final String TOOL_DESCRIPTION =
        "Replace the entire contents of an existing NDEx network file identified by UUID. " +
        "For networks whose serialized CX2 data exceeds " + MAX_CHUNK_SIZE_BYTES + " bytes, " +
        "the caller must split the content into sequential chunks of " + MAX_CHUNK_SIZE_BYTES +
        " bytes and invoke this tool once per chunk in strict order, waiting for each response " +
        "before sending the next. Each response indicates whether another chunk is required " +
        "(isSubmitted=false) or whether all chunks have been received and the update is now " +
        "processing asynchronously on the server (isSubmitted=true). When updateStatus.isSubmitted " +
        "is true, retrieve or poll the network summary for the returned networkId until the summary " +
        "reports isCompleted is true or an errorMessage is set to confirm the final outcome. " +
        "Returns an error response if the network does not exist, is locked by another in-progress " +
        "operation, or is read-only; a 401 Unauthorized error response is returned when " +
        "authentication is required or the caller lacks write permission.";

    static final String INPUT_SCHEMA = McpSchema.toJson(
        McpSchema.InputSchema.builder()
            .required("networkId", "cx2Network", "cx2NetworkSize",
                      "cx2NetworkChunkTotalCount", "cx2NetworkCurrentChunkNumber")
            .property("networkId", new McpSchema.InputProperty("string",
                "Required. UUID of the NDEx network to update. Must match an existing network " +
                "for which the caller has write permission.\n\n" +
                "Examples: \"f93f402c-86d4-11e7-a10d-0ac135e8bacf\", " +
                "\"9a8f5ab1-3a5c-11e8-a935-0ac135e8bacf\""))
            .property("cx2Network", new McpSchema.InputProperty("string",
                "Required. The complete CX2 file serialized as a JSON string, or one sequential " +
                "chunk of it when the full string exceeds " + MAX_CHUNK_SIZE_BYTES + " bytes (" +
                (MAX_CHUNK_SIZE_BYTES / (1024 * 1024)) + " MB). Each chunk is a contiguous " +
                "substring of the full serialized CX2 — they are concatenated in order server-side " +
                "to reconstruct the file. The file must be valid CX2; validation is asynchronous."))
            .property("cx2NetworkSize", new McpSchema.InputProperty("integer",
                "Required. Total byte length of the complete serialized CX2 string across all chunks. " +
                "If this value exceeds " + MAX_CHUNK_SIZE_BYTES + " bytes, split cx2Network into " +
                "chunks of at most " + MAX_CHUNK_SIZE_BYTES + " bytes and submit each as a separate " +
                "sequential tool call. Maximum allowed total size is " + MAX_NETWORK_SIZE_BYTES +
                " bytes (" + MAX_NETWORK_SIZE_GB + " GB)."))
            .property("cx2NetworkChunkTotalCount", new McpSchema.InputProperty("integer",
                "Required. Total number of chunks the cx2Network string is split into. " +
                "Set to 1 when the entire network fits in a single call."))
            .property("cx2NetworkCurrentChunkNumber", new McpSchema.InputProperty("integer",
                "Required. 1-based index of the current chunk. Must start at 1 and increment " +
                "by 1 on each successive call. The server enforces strict sequential ordering."))
            .property("visibility", new McpSchema.InputProperty("string",
                "Optional. Sets the network's visibility after the update completes. " +
                "Accepted values: PUBLIC, PRIVATE. Omit to leave unchanged.",
                List.of("PUBLIC", "PRIVATE")))
            .property("extraNodeIndex", new McpSchema.InputProperty("string",
                "Optional. Comma-separated node attribute names to include in the Solr full-text " +
                "search index in addition to defaults. Applied during asynchronous indexing.\n\n" +
                "Examples: \"geneSymbol\", \"geneSymbol,HGNC\""))
            .build());

    private static final ObjectMapper SCHEMA_MAPPER = new ObjectMapper()
        .addMixIn(NdexObjectUpdateStatus.class, NdexObjectUpdateStatusMixIn.class);

    static final String OUTPUT_SCHEMA = McpSchema.toSchemaJson(UpdateNetworkResponse.class, SCHEMA_MAPPER);

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
    private final UploadService uploadService;

    public UpdateNetworkTool(ToolsService toolsService, UploadService uploadService) {
        this.toolsService  = toolsService;
        this.uploadService = uploadService;
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

            User user = (User) httpReq.getAttribute("User");
            if (user == null) return toolsService.unauthorizedResult();

            UpdateNetworkRequest input = MAPPER.convertValue(req.arguments(), UpdateNetworkRequest.class);

            // Guard: reject oversized uploads before touching the cache
            if (input.cx2NetworkSize() > MAX_NETWORK_SIZE_BYTES) {
                return CallToolResult.builder()
                        .isError(true)
                        .addTextContent("update_network failed: declared cx2NetworkSize " +
                                        input.cx2NetworkSize() + " bytes exceeds the maximum allowed " +
                                        "size of " + MAX_NETWORK_SIZE_BYTES + " bytes (5 GB).")
                        .build();
            }

            // Guard: reject a single chunk that exceeds the per-chunk size limit
            int chunkLen = input.cx2Network() != null ? input.cx2Network().length() : 0;
            if (chunkLen > MAX_CHUNK_SIZE_BYTES) {
                return CallToolResult.builder()
                        .isError(true)
                        .addTextContent("update_network failed: cx2Network chunk length " + chunkLen +
                                        " bytes exceeds the maximum allowed chunk size of " +
                                        MAX_CHUNK_SIZE_BYTES + " bytes (" +
                                        (MAX_CHUNK_SIZE_BYTES / (1024 * 1024)) + " MB). " +
                                        "Split the full serialized CX2 into substrings of at most " +
                                        MAX_CHUNK_SIZE_BYTES + " bytes each, set " +
                                        "cx2NetworkChunkTotalCount to the total number of chunks, " +
                                        "and submit them sequentially starting from " +
                                        "cx2NetworkCurrentChunkNumber=1.")
                        .build();
            }

            String sessionId = exchange.sessionId();
            if (sessionId == null) {
                return CallToolResult.builder()
                        .isError(true)
                        .addTextContent("update_network failed: no MCP session ID present. " +
                                        "Chunked uploads require an active MCP session.")
                        .build();
            }
            String cacheKey = sessionId + ":" + input.networkId();

            boolean isComplete = uploadService.writeChunk(cacheKey,
                    input.cx2NetworkCurrentChunkNumber(), input.cx2NetworkChunkTotalCount(),
                    input.networkId(), input.cx2Network());

            if (!isComplete) {
                UpdateNetworkResponse ack = new UpdateNetworkResponse(
                    false,
                    input.cx2NetworkCurrentChunkNumber(), input.cx2NetworkChunkTotalCount(),
                    input.cx2NetworkCurrentChunkNumber() + 1,
                    null, null,
                    "Chunk " + input.cx2NetworkCurrentChunkNumber() +
                    " of " + input.cx2NetworkChunkTotalCount() +
                    " received. Submit chunk " + (input.cx2NetworkCurrentChunkNumber() + 1) + " next.");
                return CallToolResult.builder().structuredContent(ack).build();
            }

            Path tempFile = uploadService.takeCompletedUpload(cacheKey);
            if (tempFile == null) {
                String msg = "Upload session completed but temp file not found for key: " + cacheKey +
                             ". The session may have expired between chunk receipt and final assembly. " +
                             "Restart from chunk 1.";
                logger.error("update_network internal error: {}", msg);
                return CallToolResult.builder()
                        .isError(true)
                        .addTextContent("update_network failed: " + msg)
                        .build();
            }

            NdexObjectUpdateStatus updateStatus;
            try (InputStream in = Files.newInputStream(tempFile)) {
                updateStatus = new NetworkServiceV3(httpReq)
                        .updateNetworkFromInputStream(input.networkId(), in,
                                                      input.visibility(), input.extraNodeIndex());
            } finally {
                Files.deleteIfExists(tempFile);
            }

            UpdateNetworkResponse done = new UpdateNetworkResponse(
                true, null, null, null, input.networkId(), updateStatus,
                "Network update submitted. Retrieve or poll the network summary for networkId '" +
                input.networkId() + "' until updateStatus.isCompleted is true or updateStatus.errorMessage is set.");
            return CallToolResult.builder().structuredContent(done).build();

        } catch (UnauthorizedOperationException | ForbiddenOperationException | SecurityException e) {
            return toolsService.unauthorizedResult();
        } catch (Exception e) {
            logger.error("update_network failed", e);
            return CallToolResult.builder()
                    .isError(true)
                    .addTextContent("update_network failed: " + e.getMessage())
                    .build();
        }
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    record UpdateNetworkResponse(
        @JsonPropertyDescription(
            "False when this tool call received a chunk and the caller must make another tool call " +
            "with cx2NetworkCurrentChunkNumber incremented by 1. True when all chunks have been " +
            "received and the network update is now processing asynchronously — poll the network " +
            "summary until updateStatus.isCompleted is true or updateStatus.errorMessage is set.")
        @JsonProperty("isSubmitted")
        boolean isSubmitted,

        @JsonPropertyDescription("Present when isSubmitted is false. Number of chunks received so far.")
        @JsonProperty("chunksReceived")
        Integer chunksReceived,

        @JsonPropertyDescription("Present when isSubmitted is false. Total chunks expected for this upload.")
        @JsonProperty("chunksTotal")
        Integer chunksTotal,

        @JsonPropertyDescription(
            "Present when isSubmitted is false. The cx2NetworkCurrentChunkNumber value " +
            "to use in the next tool call.")
        @JsonProperty("nextChunkNumber")
        Integer nextChunkNumber,

        @JsonPropertyDescription("Present when isSubmitted is true. UUID of the updated network.")
        @JsonProperty("networkId")
        String networkId,

        @JsonPropertyDescription(
            "Present when isSubmitted is true. Acknowledgment from the NDEx server. " +
            "The update is asynchronous — poll the network summary until it denotes isCompleted is true " +
            "or errorMessage is set.")
        @JsonProperty("updateStatus")
        NdexObjectUpdateStatus updateStatus,

        @JsonPropertyDescription("Human-readable description of the current state and what to do next.")
        @JsonProperty("message")
        String message
    ) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    record UpdateNetworkRequest(
        @JsonProperty("networkId")                    String networkId,
        @JsonProperty("cx2Network")                   String cx2Network,
        @JsonProperty("cx2NetworkSize")               long   cx2NetworkSize,
        @JsonProperty("cx2NetworkChunkTotalCount")     int    cx2NetworkChunkTotalCount,
        @JsonProperty("cx2NetworkCurrentChunkNumber")  int    cx2NetworkCurrentChunkNumber,
        @JsonProperty("visibility")                   String visibility,
        @JsonProperty("extraNodeIndex")               String extraNodeIndex
    ) {}
}
