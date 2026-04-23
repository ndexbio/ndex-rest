package org.ndexbio.rest.mcp.tools;

import java.util.List;
import java.util.Map;

import org.ndexbio.model.object.User;
import org.ndexbio.rest.mcp.ConfigLocator;
import org.ndexbio.rest.mcp.McpSchema;
import org.ndexbio.rest.mcp.UploadFileRequest;
import org.ndexbio.rest.mcp.UploadService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import io.modelcontextprotocol.server.McpServerFeatures;
import io.modelcontextprotocol.server.McpSyncServerExchange;
import io.modelcontextprotocol.spec.McpSchema.CallToolRequest;
import io.modelcontextprotocol.spec.McpSchema.CallToolResult;
import io.modelcontextprotocol.spec.McpSchema.JsonSchema;
import io.modelcontextprotocol.spec.McpSchema.Tool;

/**
 * MCP tool: request_network_upload
 *
 * Issues a pre-signed single-use upload token. The agent uses the returned upload_url
 * to POST the CX2 file directly to /mcp/upload outside the MCP channel, avoiding
 * in-protocol inline string transfer that causes agent hangs on large files.
 */
public class RequestNetworkUploadTool {

    private static final Logger logger = LoggerFactory.getLogger(RequestNetworkUploadTool.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();

    static final String TOOL_NAME = "request_network_upload";

    private static final String TOOL_DESCRIPTION =
        "Obtain a time-limited upload URL for transferring a local CX2 network file directly to NDEx " +
        "over HTTP. Use when a local CX2 file on the agent's machine needs to be published as a new " +
        "network or used to replace the content of an existing one — providing a target network identifier " +
        "signals an update; omitting it creates a new network. The returned URL accepts a standard " +
        "multipart HTTP POST with the file attached to the 'CXNetworkStream' field with content-type " +
        "'application/json'. The caller must perform this HTTP POST immediately after calling this tool, " +
        "before the 2-minute token embedded in the URL expires; any HTTP client capable of issuing a " +
        "multipart POST request can perform the transfer — for example, using curl:\n" +
        "  curl -s -X POST -F \"CXNetworkStream=@/path/to/file.cx2;type=application/json\" \"<upload_url>\"\n" +
        "State-mutating when the follow-on HTTP POST is executed: creates a new network or replaces " +
        "the CX2 content of an existing one, then triggers asynchronous validation and search indexing " +
        "on the server side. Returns an error response when the caller is not authenticated; the error " +
        "message identifies the specific cause.\n\n" +
        "## Examples\n\n" +
        "Example 1 — Upload a CX2 file as a new private network:\n" +
        "Prompt: 'Upload this CX2 file to NDEx'\n" +
        "{\"file_path\": \"/Users/jsmith/Downloads/network.cx2\"}\n\n" +
        "Example 2 — Upload and make the new network publicly searchable:\n" +
        "Prompt: 'Publish my CX2 file to NDEx so anyone can find it'\n" +
        "{\"file_path\": \"/home/user/export.cx2\", \"visibility\": \"PUBLIC\"}\n\n" +
        "Example 3 — Replace the CX2 content of an existing network:\n" +
        "Prompt: 'Update my NDEx network with the new version of this CX2 file'\n" +
        "{\"file_path\": \"/tmp/revised_network.cx2\",\n" +
        " \"network_id\": \"f93f402c-86d4-11e7-a10d-0ac135e8bacf\"}\n\n" +
        "Example 4 — Upload a new network into a specific folder:\n" +
        "Prompt: 'Save this CX2 file to NDEx inside my Signaling Pathways folder'\n" +
        "{\"file_path\": \"/home/user/signaling.cx2\",\n" +
        " \"folder_id\": {\"waived\": false, \"parameter\": \"a1b2c3d4-0000-0000-0000-000000000099\"}}";

    static final String INPUT_SCHEMA = McpSchema.toJson(
        McpSchema.InputSchema.builder()
            .required("file_path")
            .property("file_path", new McpSchema.InputProperty("string",
                "Required. Absolute path to the CX2 file on the agent's local machine. This path is " +
                "embedded in the returned upload metadata so the agent can supply it directly to an HTTP " +
                "client when executing the follow-on file transfer; the path itself is never read or " +
                "transmitted to the NDEx server by this tool.\n\n" +
                "Examples: \"/home/user/networks/my_network.cx2\", " +
                "\"/Users/jsmith/Downloads/test_export.cx2\", " +
                "\"C:\\\\Users\\\\jsmith\\\\Downloads\\\\test_export.cx2\""))
            .property("network_id", new McpSchema.InputProperty("string",
                "Optional. UUID of the existing NDEx network whose CX2 content should be replaced. " +
                "When provided, the upload replaces that network's content (update). When absent, a new " +
                "network is created. Retrieve the target network's identifier using the network search or " +
                "listing functionality before invoking this tool if the identifier is not already known.\n\n" +
                "Examples: \"f93f402c-86d4-11e7-a10d-0ac135e8bacf\", " +
                "\"9a8f5ab1-3a5c-11e8-a935-0ac135e8bacf\""))
            .property("visibility", new McpSchema.InputProperty("string",
                "Optional. Visibility level to assign to the network after it is created. PUBLIC makes " +
                "the network searchable by all NDEx users; PRIVATE restricts access to the owning account " +
                "and explicitly shared users. Applies only when creating a new network; ignored when " +
                "network_id is provided. Defaults to PRIVATE when omitted. When the user has not expressed " +
                "a visibility preference, confirm before uploading.\n\n" +
                "Examples: \"PUBLIC\", \"PRIVATE\"",
                List.of("PUBLIC", "PRIVATE")))
            .conditionalParam("folder_id", "string",
                "Conditional on network_id being absent. UUID of an existing NDEx folder in which to " +
                "place the newly created network. Required only when network_id is absent and the user " +
                "wants to place the network in a specific folder; provide {waived:true} when network_id " +
                "is present (folder placement is not applicable for updates) or when creating at root " +
                "level. Omit or waive to create the network at the root level of the authenticated user's " +
                "NDEx storage. The folder identifier can be retrieved using the folder listing " +
                "functionality before invoking this tool.\n\n" +
                "Examples: {\"waived\": false, \"parameter\": \"a1b2c3d4-0000-0000-0000-000000000099\"}, " +
                "{\"waived\": true}")
            .property("extra_node_index", new McpSchema.InputProperty("string",
                "Optional. Comma-separated list of node attribute names to include in the NDEx full-text " +
                "search index in addition to the default indexed fields. Improves searchability when key " +
                "identifiers such as gene symbols or database accession numbers are stored in non-default " +
                "node attributes. Applies only when creating a new network; ignored when network_id is " +
                "provided.\n\n" +
                "Examples: \"geneSymbol\", \"geneSymbol,HGNC\""))
            .build());

    static final String OUTPUT_SCHEMA = McpSchema.toSchemaJson(UploadTokenResponse.class);

    private static final Tool TOOL;
    static {
        try {
            TOOL = Tool.builder()
                .name(TOOL_NAME)
                .description(TOOL_DESCRIPTION)
                .inputSchema(MAPPER.readValue(INPUT_SCHEMA, JsonSchema.class))
                .outputSchema(MAPPER.readValue(OUTPUT_SCHEMA, new TypeReference<Map<String, Object>>() {}))
                .build();
        } catch (Exception e) {
            throw new ExceptionInInitializerError(e);
        }
    }

    private final ConfigLocator configLocator;

    public RequestNetworkUploadTool(ConfigLocator configLocator) {
        this.configLocator = configLocator;
    }

    public McpServerFeatures.SyncToolSpecification toSpec() {
        return McpServerFeatures.SyncToolSpecification.builder()
                .tool(TOOL)
                .callHandler(this::handle)
                .build();
    }

    private CallToolResult handle(McpSyncServerExchange exchange, CallToolRequest req) {
        try {
            User user = (User) exchange.transportContext().get("ndexUser");
            if (user == null) {
                return CallToolResult.builder()
                        .isError(true)
                        .addTextContent("401 Unauthorized")
                        .build();
            }

            UploadToolRequest input = MAPPER.convertValue(req.arguments(), UploadToolRequest.class);
            String folderId = unwrapConditionalString(input.folderId());

            String token = UploadService.getInstance().createToken(
                new UploadFileRequest(user, input.networkId(), input.visibility(),
                                      input.extraNodeIndex(), folderId,
                                      System.currentTimeMillis()));

            String baseUrl = configLocator.getHostURI();
            if (baseUrl == null || baseUrl.isBlank()) baseUrl = "http://localhost";
            String uploadUrl = baseUrl + "/mcp/upload?upload_token=" + token;

            return CallToolResult.builder()
                    .structuredContent(new UploadTokenResponse(uploadUrl, "POST",
                                                               "CXNetworkStream", "application/json", 120))
                    .build();

        } catch (Exception e) {
            logger.error("request_network_upload failed", e);
            return CallToolResult.builder()
                    .isError(true)
                    .addTextContent("request_network_upload failed: " + e.getMessage())
                    .build();
        }
    }

    private static String unwrapConditionalString(Object raw) {
        if (raw == null) return null;
        if (raw instanceof Map<?,?> m) {
            if (Boolean.TRUE.equals(m.get("waived"))) return null;
            Object param = m.get("parameter");
            return param != null ? param.toString() : null;
        }
        return raw.toString();
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record UploadToolRequest(
        @JsonProperty("file_path")        String filePath,
        @JsonProperty("network_id")       String networkId,
        @JsonProperty("visibility")       String visibility,
        @JsonProperty("folder_id")        Object folderId,
        @JsonProperty("extra_node_index") String extraNodeIndex
    ) {}

    static record UploadTokenResponse(

        @JsonPropertyDescription(
            "Time-limited pre-signed URL for uploading the CX2 file to NDEx. Execute an HTTP POST " +
            "to this URL immediately after calling this tool, attaching the CX2 file as a multipart " +
            "form field named 'CXNetworkStream' with content-type 'application/json'. The token " +
            "embedded in this URL is single-use and expires after the number of seconds indicated by " +
            "expires_in_seconds; the server returns a 401 Unauthorized response if the URL is " +
            "submitted after expiry or reused. Example using curl: " +
            "  curl -s -X POST -F \\\"CXNetworkStream=@<file_path>;type=application/json\\\" \\\"<upload_url>\\\"\n\n" +
            "Examples: \\\"http://www.ndexbio.org/mcp/upload?upload_token=550e8400-e29b-41d4-a716-446655440000\\\", " +
            "\\\"http://localhost:8080/mcp/upload?upload_token=6ba7b810-9dad-11d1-80b4-00c04fd430c8\\\"")
        @JsonProperty("upload_url") String uploadUrl,

        @JsonPropertyDescription(
            "HTTP method to use when posting the CX2 file to the upload URL. Always 'POST'.\n\n" +
            "Examples: \"POST\"")
        @JsonProperty("method") String method,

        @JsonPropertyDescription(
            "Name of the multipart form field to which the CX2 file must be attached in the HTTP " +
            "POST request. The file's content-type must be set to 'application/json' within the " +
            "multipart part.\n\n" +
            "Examples: \"CXNetworkStream\"")
        @JsonProperty("field") String field,

        @JsonPropertyDescription(
            "MIME type that must be declared for the CX2 file part in the multipart HTTP POST. " +
            "Set this as the content-type of the 'CXNetworkStream' part, not of the overall request.\n\n" +
            "Examples: \"application/json\"")
        @JsonProperty("content_type") String contentType,

        @JsonPropertyDescription(
            "Number of seconds from the time this tool was called until the upload_url token expires. " +
            "The HTTP POST to upload_url must be submitted before this duration elapses. After expiry " +
            "the server rejects the request with a 401 Unauthorized response.\n\n" +
            "Examples: 120")
        @JsonProperty("expires_in_seconds") int expiresInSeconds

    ) {}
}
