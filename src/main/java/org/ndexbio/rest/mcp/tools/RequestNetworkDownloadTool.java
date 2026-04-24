package org.ndexbio.rest.mcp.tools;

import java.util.Map;

import jakarta.servlet.http.HttpServletRequest;

import org.ndexbio.model.exceptions.UnauthorizedOperationException;
import org.ndexbio.model.object.User;
import org.ndexbio.rest.mcp.ConfigLocator;
import org.ndexbio.rest.services.v3.NetworkServiceV3;
import org.ndexbio.rest.mcp.DownloadFileRequest;
import org.ndexbio.rest.mcp.DownloadTokenService;
import org.ndexbio.rest.mcp.McpSchema;
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
 * MCP tool: request_network_download
 *
 * Issues a pre-signed single-use download token. The agent uses the returned download_url
 * to GET the CX2 file directly from /mcp/download outside the MCP channel, avoiding
 * in-protocol inline string transfer that causes agent hangs on large files.
 */
public class RequestNetworkDownloadTool {

    private static final Logger logger = LoggerFactory.getLogger(RequestNetworkDownloadTool.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();

    static final String TOOL_NAME = "request_network_download";

    private static final String TOOL_DESCRIPTION =
        "Obtain a time-limited download URL for transferring a CX2 network file directly from NDEx " +
        "to the agent's local machine over HTTP. Use when the CX2 content of an NDEx network needs " +
        "to be saved as a local file — for example before analysis, transformation, or archiving. " +
        "The returned URL accepts a standard HTTP GET request and streams the complete CX2 file in " +
        "a single response. After calling this tool, immediately perform an HTTP GET to the returned " +
        "download_url before the 2-minute token expires and save the response body to the desired " +
        "local path. Always wrap <file_path> in double-quotes so that spaces and special characters " +
        "in the path are handled safely by the shell. For example, using curl:\n" +
        "  curl -s -o \"<file_path>\" \"<download_url>\"\n" +
        "where <file_path> and <download_url> are taken from this tool's response. " +
        "Read-only: does not modify any server state. Public networks can be downloaded without " +
        "authentication. Private networks require the caller to be authenticated; if the network is " +
        "inaccessible an error response is returned identifying the cause.\n\n" +
        "## Examples\n\n" +
        "Example 1 — Download a network to a local path:\n" +
        "Prompt: 'Download NDEx network f93f402c-86d4-11e7-a10d-0ac135e8bacf as CX2 to /tmp/net.cx2'\n" +
        "{\"network_id\": \"f93f402c-86d4-11e7-a10d-0ac135e8bacf\", \"file_path\": \"/tmp/net.cx2\"}\n" +
        "Then immediately: curl -s -o \"/tmp/net.cx2\" \"<download_url from response>\"\n\n" +
        "Example 2 — Download a private network using an access key:\n" +
        "Prompt: 'Download my private network and save it at /home/user/mynet.cx2'\n" +
        "{\"network_id\": \"9a8f5ab1-3a5c-11e8-a935-0ac135e8bacf\",\n" +
        " \"file_path\": \"/home/user/mynet.cx2\",\n" +
        " \"access_key\": \"abc123xyz\"}\n" +
        "Then immediately: curl -s -o \"/home/user/mynet.cx2\" \"<download_url from response>\"\n\n" +
        "Example 3 — Download to a path with spaces in the filename:\n" +
        "Prompt: 'Download network abc123 and save it as /Users/jsmith/Downloads/My Network.cx2'\n" +
        "{\"network_id\": \"abc12345-0000-0000-0000-000000000000\",\n" +
        " \"file_path\": \"/Users/jsmith/Downloads/My Network.cx2\"}\n" +
        "Then immediately: curl -s -o \"/Users/jsmith/Downloads/My Network.cx2\" \"<download_url from response>\"";

    static final String INPUT_SCHEMA = McpSchema.toJson(
        McpSchema.InputSchema.builder()
            .required("network_id", "file_path")
            .property("network_id", new McpSchema.InputProperty("string",
                "Required. UUID of the NDEx network to download in CX2 format. The network must have " +
                "a CX2 representation available on the server.\n\n" +
                "Examples: \"f93f402c-86d4-11e7-a10d-0ac135e8bacf\", " +
                "\"9a8f5ab1-3a5c-11e8-a935-0ac135e8bacf\""))
            .property("file_path", new McpSchema.InputProperty("string",
                "Required. Absolute path on the agent's local machine where the downloaded CX2 file " +
                "should be saved.\n\n" +
                "IMPORTANT — before calling this tool the agent MUST:\n" +
                "1. Verify the parent directory exists (e.g. with a bash `test -d \"<dir>\" && echo exists` " +
                "check). If the directory does not exist, stop and inform the user; do NOT call this tool.\n" +
                "2. Check whether a file already exists at this path (e.g. `test -f \"<path>\" && echo exists`). " +
                "If a file is found, warn the user that it will be overwritten and ask them to confirm " +
                "before proceeding.\n\n" +
                "Pass the path exactly as provided by the user — as a plain JSON string with no " +
                "URL-encoding or shell escaping. Spaces and special characters are valid in the " +
                "JSON value. The path is echoed back in the response so the agent can use it " +
                "directly (double-quoted) in the follow-on curl command without reconstructing it.\n\n" +
                "Examples: \"/tmp/mynetwork.cx2\", \"/home/ndex/exports/net.cx2\", " +
                "\"/Users/jsmith/Downloads/My Network.cx2\""))
            .property("access_key", new McpSchema.InputProperty("string",
                "Optional. Access key granting read permission on networks that are not publicly " +
                "visible. Ignored for public networks.\n\n" +
                "Examples: \"abc123xyz\", \"private-key-99\""))
            .build());

    static final String OUTPUT_SCHEMA = McpSchema.toSchemaJson(DownloadTokenResponse.class);

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

    public RequestNetworkDownloadTool(ConfigLocator configLocator) {
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
            HttpServletRequest httpReq =
                    (HttpServletRequest) exchange.transportContext().get("ndexRequest");

            DownloadToolRequest input = MAPPER.convertValue(req.arguments(), DownloadToolRequest.class);

            // Visibility pre-check: public networks allow anonymous; private networks require auth.
            // NetworkServiceV3 uses the User attribute McpAuthFilter already set on httpReq
            // (null for anonymous), so isReadable(networkId, null) is used for unauthenticated callers.
            try {
                checkNetworkReadable(httpReq, input.networkId(), input.accessKey());
            } catch (Exception e) {
                boolean isAuthError = e instanceof UnauthorizedOperationException
                        || e instanceof SecurityException;
                if (isAuthError) {
                    return CallToolResult.builder()
                            .isError(true)
                            .addTextContent(user == null
                                    ? "401 Unauthorized: authentication required to access this network"
                                    : "403 Forbidden: " + e.getMessage())
                            .build();
                }
                throw e;
            }

            String token = DownloadTokenService.getInstance().createToken(
                new DownloadFileRequest(user, input.networkId(), input.accessKey(),
                                        System.currentTimeMillis()));

            String baseUrl = configLocator.getHostURI();
            if (baseUrl == null || baseUrl.isBlank()) baseUrl = "http://localhost";
            String downloadUrl = baseUrl + "/mcp/download?download_token=" + token;

            return CallToolResult.builder()
                    .structuredContent(new DownloadTokenResponse(downloadUrl, "GET", 120, input.filePath()))
                    .build();

        } catch (Exception e) {
            logger.error("request_network_download failed", e);
            return CallToolResult.builder()
                    .isError(true)
                    .addTextContent("request_network_download failed: " + e.getMessage())
                    .build();
        }
    }

    protected void checkNetworkReadable(HttpServletRequest httpReq, String networkId, String accessKey)
            throws Exception {
        new NetworkServiceV3(httpReq).getNetworkSummaryV3(networkId, accessKey, "COMPACT");
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record DownloadToolRequest(
        @JsonProperty("network_id")  String networkId,
        @JsonProperty("file_path")   String filePath,
        @JsonProperty("access_key")  String accessKey
    ) {}

    static record DownloadTokenResponse(

        @JsonPropertyDescription(
            "Time-limited pre-signed URL for downloading the CX2 file from NDEx. Retrieve this URL " +
            "using any HTTP GET client immediately after calling this tool, before the token expires. " +
            "The token is single-use: the server returns 401 Unauthorized if the URL is used after " +
            "expiry or reused. Use the file_path field from this response (double-quoted) as the " +
            "curl -o destination so that spaces and special characters are handled safely:\n" +
            "  curl -s -o \"<file_path>\" \"<download_url>\"\n\n" +
            "Examples: \"http://www.ndexbio.org/mcp/download?download_token=550e8400-e29b-41d4-a716-446655440000\", " +
            "\"http://localhost:8080/mcp/download?download_token=6ba7b810-9dad-11d1-80b4-00c04fd430c8\"")
        @JsonProperty("download_url") String downloadUrl,

        @JsonPropertyDescription(
            "HTTP method to use when retrieving the CX2 file. Always 'GET'.\n\n" +
            "Examples: \"GET\"")
        @JsonProperty("method") String method,

        @JsonPropertyDescription(
            "Number of seconds from the time this tool was called until the download_url token expires. " +
            "The HTTP GET must be submitted before this duration elapses. After expiry the server rejects " +
            "the request with a 401 Unauthorized response.\n\n" +
            "Examples: 120")
        @JsonProperty("expires_in_seconds") int expiresInSeconds,

        @JsonPropertyDescription(
            "The exact local file path provided by the caller, echoed back verbatim. Use this value " +
            "directly (double-quoted) as the -o argument to curl so the agent never has to reconstruct " +
            "the path from memory — this ensures paths containing spaces or special characters are " +
            "handled correctly.\n\n" +
            "Examples: \"/tmp/net.cx2\", \"/Users/jsmith/Downloads/My Network.cx2\"")
        @JsonProperty("file_path") String filePath

    ) {}
}
