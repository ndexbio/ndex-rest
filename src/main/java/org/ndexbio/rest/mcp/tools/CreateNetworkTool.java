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
 * MCP tool: create_network
 *
 * Publishes a brand-new CX2 network to NDEx using chunked string transfer.
 * The LLM splits the serialized CX2 into sequential chunks and submits one
 * tool call per chunk. The server accumulates chunks as file appends and
 * triggers the NDEx create atomically when the final chunk arrives.
 */
public class CreateNetworkTool {

    private static final Logger logger = LoggerFactory.getLogger(CreateNetworkTool.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();

    static final String TOOL_NAME = "create_network";

    // Hard cap: reject any upload whose declared total size exceeds this limit
    static final long MAX_NETWORK_SIZE_BYTES = 5L * 1024 * 1024 * 1024; // 5 GB
    static final long MAX_CHUNK_SIZE_BYTES   = 5L * 1024 * 1024;         // 5 MB

    private static final String TOOL_DESCRIPTION =
        "Create a new NDEx network by uploading CX2-formatted content to the NDEx repository. " +
        "Use when a user or agent wants to publish an entirely new network. " +
        "Has the serialized CX2 JSON content available, typically as a file on local file system. " +
        "For content that exceeds the per-call size limit, the caller must split the serialized CX2 " +
        "into sequential fixed-size chunks and invoke this tool once per chunk in strict ascending " +
        "order, waiting for each response before sending the next. " +
        "Each response signals whether another chunk is still needed (isSubmitted=false) or whether " +
        "all chunks have been received and the network is now being created asynchronously on the " +
        "server (isSubmitted=true); when isSubmitted is true, the network may not be immediately " +
        "accessible while validation and indexing complete. " +
        "Only one active upload is supported per MCP session at a time; beginning a new upload " +
        "before the current one finishes will abandon and restart it. " +
        "Returns an error response if the caller is unauthenticated, a disk quota is exceeded, the " +
        "chunk sequence is violated, or upload session constraints are not met; a 401 Unauthorized " +
        "error response is returned when authentication is required.\n\n" +
        "## Examples\n\n" +
        "Example 1 — Publish a small network in a single call:\n" +
        "Prompt: 'Upload this CX2 network to NDEx'\n" +
        "{\"cx2Network\": \"[{\\\"CXVersion\\\":\\\"2.0\\\",\\\"hasFragments\\\":false},...]\", " +
        "\"cx2NetworkSize\": 512, \"cx2NetworkChunkTotalCount\": 1, " +
        "\"cx2NetworkCurrentChunkNumber\": 1}\n\n" +
        "Example 2 — Create a network and make it publicly visible:\n" +
        "Prompt: 'Save this network to NDEx so anyone can find it'\n" +
        "{\"cx2Network\": \"[{\\\"CXVersion\\\":\\\"2.0\\\",...}]\", \"cx2NetworkSize\": 1024, " +
        "\"cx2NetworkChunkTotalCount\": 1, \"cx2NetworkCurrentChunkNumber\": 1, " +
        "\"visibility\": \"PUBLIC\"}\n\n" +
        "Example 3 — Submit the first chunk of a large multi-part upload:\n" +
        "Prompt: 'Upload this 15 MB CX2 network to NDEx'\n" +
        "{\"cx2Network\": \"<first 5 MB contiguous substring of serialized CX2>\", " +
        "\"cx2NetworkSize\": 15000000, \"cx2NetworkChunkTotalCount\": 3, " +
        "\"cx2NetworkCurrentChunkNumber\": 1}\n\n" +
        "Example 4 — Create a network inside a specific NDEx folder:\n" +
        "Prompt: 'Upload this network to NDEx and put it in my Signaling Pathways folder'\n" +
        "{\"cx2Network\": \"[{\\\"CXVersion\\\":\\\"2.0\\\",...}]\", \"cx2NetworkSize\": 800, " +
        "\"cx2NetworkChunkTotalCount\": 1, \"cx2NetworkCurrentChunkNumber\": 1, " +
        "\"folderId\": \"a1b2c3d4-0000-0000-0000-000000000099\"}";

    static final String INPUT_SCHEMA = McpSchema.toJson(
        McpSchema.InputSchema.builder()
            .required("cx2Network", "cx2NetworkSize",
                      "cx2NetworkChunkTotalCount", "cx2NetworkCurrentChunkNumber")
            .property("cx2Network", new McpSchema.InputProperty("string",
                "Required. The complete CX2 file serialized as a JSON string, or one sequential chunk of it " +
                "when the full string exceeds " + MAX_CHUNK_SIZE_BYTES + " bytes (5 MB). Each chunk must be a " +
                "contiguous substring of the complete serialized CX2 in strict sequential order — the server " +
                "concatenates them to reconstruct the full file. The file must be valid CX2; format validation " +
                "is performed asynchronously after all chunks are received.\n\n" +
                "Examples: \"[{\\\"CXVersion\\\":\\\"2.0\\\",\\\"hasFragments\\\":false},...]\", " +
                "\"<contiguous substring of serialized CX2>\""))
            .property("cx2NetworkSize", new McpSchema.InputProperty("integer",
                "Required. Total byte length of the complete serialized CX2 string across all chunks. " +
                "When this value exceeds " + MAX_CHUNK_SIZE_BYTES + " bytes, the caller must split the content " +
                "into sequential chunks of at most " + MAX_CHUNK_SIZE_BYTES + " bytes each and submit each as a " +
                "separate call. Maximum allowed total size is " + MAX_NETWORK_SIZE_BYTES + " bytes (5 GB).\n\n" +
                "Examples: 512, 5242880, 15000000"))
            .property("cx2NetworkChunkTotalCount", new McpSchema.InputProperty("integer",
                "Required. Total number of sequential chunks the CX2 content is split into. " +
                "Set to 1 when the entire network fits in a single call.\n\n" +
                "Examples: 1, 2, 3"))
            .property("cx2NetworkCurrentChunkNumber", new McpSchema.InputProperty("integer",
                "Required. 1-based index of the current chunk being submitted. Must start at 1 and increment " +
                "by exactly 1 on each successive call. The server enforces strict sequential ordering and will " +
                "reject out-of-order chunks.\n\n" +
                "Examples: 1, 2, 3"))
            .property("visibility", new McpSchema.InputProperty("string",
                "Optional. Controls who can discover and access the network on NDEx after creation. " +
                "PUBLIC makes the network visible and searchable by all NDEx users; PRIVATE restricts " +
                "access to the owning account only, and is the default when omitted. The choice has lasting " +
                "access-control implications — when the user has not expressed a preference for network " +
                "visibility, ask whether it should be PUBLIC or PRIVATE before uploading.\n\n" +
                "Examples: \"PUBLIC\", \"PRIVATE\"",
                List.of("PUBLIC", "PRIVATE")))
            .property("extraNodeIndex", new McpSchema.InputProperty("string",
                "Optional. Comma-separated list of node attribute names to include in the NDEx full-text " +
                "search index in addition to default indexed fields. Improves searchability when key " +
                "identifiers such as gene symbols or database accession numbers are stored in non-default " +
                "attributes. When the network contains domain-specific node attributes and the user has not " +
                "addressed search indexing, ask whether any attribute names should be added to the index.\n\n" +
                "Examples: \"geneSymbol\", \"geneSymbol,HGNC\""))
            .property("folderId", new McpSchema.InputProperty("string",
                "Optional. UUID of an existing NDEx folder in which to place the newly created network. " +
                "Omit to create the network at the root level of the authenticated user's NDEx storage. " +
                "When the user has not specified where to store the network and may have existing folders " +
                "for organizing their data, ask whether to place it in a specific folder — folder identifiers " +
                "can be retrieved by listing the user's NDEx folders before proceeding.\n\n" +
                "Examples: \"a1b2c3d4-0000-0000-0000-000000000099\", \"f93f402c-86d4-11e7-a10d-0ac135e8bacf\""))
            .build());

    private static final ObjectMapper SCHEMA_MAPPER = new ObjectMapper()
        .addMixIn(NdexObjectUpdateStatus.class, NdexObjectUpdateStatusMixIn.class);

    static final String OUTPUT_SCHEMA = McpSchema.toSchemaJson(CreateNetworkResponse.class, SCHEMA_MAPPER);

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

    public CreateNetworkTool(ToolsService toolsService, UploadService uploadService) {
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

            CreateNetworkRequest input = MAPPER.convertValue(req.arguments(), CreateNetworkRequest.class);

            // Guard: reject oversized uploads before touching the cache
            if (input.cx2NetworkSize() > MAX_NETWORK_SIZE_BYTES) {
                return CallToolResult.builder()
                        .isError(true)
                        .addTextContent("create_network failed: declared cx2NetworkSize " +
                                        input.cx2NetworkSize() + " bytes exceeds the maximum allowed " +
                                        "size of " + MAX_NETWORK_SIZE_BYTES + " bytes (5 GB).")
                        .build();
            }

            // Guard: reject a single chunk that exceeds the per-chunk size limit
            int chunkLen = input.cx2Network() != null ? input.cx2Network().length() : 0;
            if (chunkLen > MAX_CHUNK_SIZE_BYTES) {
                return CallToolResult.builder()
                        .isError(true)
                        .addTextContent("create_network failed: cx2Network chunk length " + chunkLen +
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
                        .addTextContent("create_network failed: no MCP session ID present. " +
                                        "Chunked uploads require an active MCP session.")
                        .build();
            }
            String cacheKey = sessionId + ":create";

            boolean isComplete = uploadService.writeChunk(cacheKey,
                    input.cx2NetworkCurrentChunkNumber(), input.cx2NetworkChunkTotalCount(),
                    "new", input.cx2Network());

            if (!isComplete) {
                CreateNetworkResponse ack = new CreateNetworkResponse(
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
                logger.error("create_network internal error: {}", msg);
                return CallToolResult.builder()
                        .isError(true)
                        .addTextContent("create_network failed: " + msg)
                        .build();
            }

            NdexObjectUpdateStatus createStatus;
            try (InputStream in = Files.newInputStream(tempFile)) {
                createStatus = new NetworkServiceV3(httpReq)
                        .createNetworkFromInputStream(in,
                                                      input.visibility(),
                                                      input.extraNodeIndex(),
                                                      input.folderId());
            } finally {
                Files.deleteIfExists(tempFile);
            }

            String networkId = createStatus.getUuid().toString();
            CreateNetworkResponse done = new CreateNetworkResponse(
                true, null, null, null, networkId, createStatus,
                "Network creation submitted. Retrieve or poll the network summary for networkId '" +
                networkId + "' until processing is complete.");
            return CallToolResult.builder().structuredContent(done).build();

        } catch (UnauthorizedOperationException | ForbiddenOperationException | SecurityException e) {
            return toolsService.unauthorizedResult();
        } catch (Exception e) {
            logger.error("create_network failed", e);
            return CallToolResult.builder()
                    .isError(true)
                    .addTextContent("create_network failed: " + e.getMessage())
                    .build();
        }
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    record CreateNetworkResponse(

        @JsonPropertyDescription(
            "False when this tool call received and stored a chunk and the caller must make another " +
            "tool call with the chunk number incremented by 1. True when all chunks have been received " +
            "and the network creation is now processing asynchronously on the server — retrieve or poll " +
            "the network summary for the returned networkId until it reports completion.\n\n" +
            "Examples: false, true")
        @JsonProperty("isSubmitted")
        boolean isSubmitted,

        @JsonPropertyDescription(
            "Present when isSubmitted is false. The 1-based number of chunks successfully received " +
            "for this upload session so far.\n\n" +
            "Examples: 1, 2")
        @JsonProperty("chunksReceived")
        Integer chunksReceived,

        @JsonPropertyDescription(
            "Present when isSubmitted is false. The total number of chunks declared for this upload " +
            "session.\n\n" +
            "Examples: 2, 3")
        @JsonProperty("chunksTotal")
        Integer chunksTotal,

        @JsonPropertyDescription(
            "Present when isSubmitted is false. The value to supply as cx2NetworkCurrentChunkNumber " +
            "in the next tool call.\n\n" +
            "Examples: 2, 3")
        @JsonProperty("nextChunkNumber")
        Integer nextChunkNumber,

        @JsonPropertyDescription(
            "Present when isSubmitted is true. UUID of the newly created network. Use this identifier " +
            "to retrieve the network summary and poll for completion status.\n\n" +
            "Examples: \"f93f402c-86d4-11e7-a10d-0ac135e8bacf\", \"9a8f5ab1-3a5c-11e8-a935-0ac135e8bacf\"")
        @JsonProperty("networkId")
        String networkId,

        @JsonPropertyDescription(
            "Present when isSubmitted is true. Server acknowledgment of the accepted creation request. " +
            "Creation and indexing are asynchronous — retrieve or poll the network summary using the " +
            "returned networkId until processing is complete.")
        @JsonProperty("createStatus")
        NdexObjectUpdateStatus createStatus,

        @JsonPropertyDescription(
            "Human-readable summary of the current state and the next required action.\n\n" +
            "Examples: \"Chunk 1 of 3 received. Submit chunk 2 next.\", " +
            "\"Network creation submitted. Retrieve or poll the network summary for networkId " +
            "'f93f402c-...' until processing is complete.\"")
        @JsonProperty("message")
        String message
    ) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    record CreateNetworkRequest(
        @JsonProperty("cx2Network")                   String cx2Network,
        @JsonProperty("cx2NetworkSize")               long   cx2NetworkSize,
        @JsonProperty("cx2NetworkChunkTotalCount")     int    cx2NetworkChunkTotalCount,
        @JsonProperty("cx2NetworkCurrentChunkNumber")  int    cx2NetworkCurrentChunkNumber,
        @JsonProperty("visibility")                   String visibility,
        @JsonProperty("extraNodeIndex")               String extraNodeIndex,
        @JsonProperty("folderId")                     String folderId
    ) {}
}
