package org.ndexbio.rest.mcp.tools;

import java.util.Map;

import jakarta.servlet.http.HttpServletRequest;

import org.ndexbio.model.exceptions.UnauthorizedOperationException;
import org.ndexbio.model.object.User;
import org.ndexbio.rest.mcp.McpSchema;
import org.ndexbio.rest.mcp.ToolsService;
import org.ndexbio.rest.services.NetworkServiceV2;
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
 * MCP tool: share_network
 *
 * Delegates to NetworkServiceV2.updateNetworkPermission() via direct in-process call.
 * Grants or updates a user's or group's access permission on an NDEx network.
 * The HttpServletRequest (with the "User" attribute set by McpBasicAuthFilter) is retrieved
 * from the MCP transport context, so getLoggedInUser() works transparently.
 * Authentication is required and the caller must be the network owner (ADMIN).
 * Exactly one of userId or groupId must be supplied.
 */
public class ShareNetworkTool {

    private static final Logger logger = LoggerFactory.getLogger(ShareNetworkTool.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();

    static final String TOOL_NAME = "share_network";

    private static final String TOOL_DESCRIPTION =
        "Grant or update a user's or group's access permission on an NDEx network identified " +
        "by its UUID. " +
        "Use when sharing a network with a specific collaborator (user) or making it " +
        "accessible to an entire group, or when changing an existing permission level. " +
        "Exactly one of userId or groupId must be supplied; both cannot be used together. " +
        "Authentication is required and the caller must be the network owner (ADMIN); returns " +
        "a 401 Unauthorized error response when unauthenticated or not the owner. " +
        "Returns an error response if the network does not exist, is invalid, or the subject " +
        "UUID is not a valid user or group.\n\n" +
        "## Examples\n\n" +
        "Example 1 — Grant a collaborator read access:\n" +
        "Prompt: 'Share my NDEx network with user abc123 so they can view it'\n" +
        "{\"networkId\": \"f93f402c-86d4-11e7-a10d-0ac135e8bacf\", " +
        "\"userId\": \"a1b2c3d4-1234-5678-abcd-000000000001\", \"permission\": \"READ\"}\n\n" +
        "Example 2 — Allow a user to edit the network:\n" +
        "Prompt: 'Give user xyz write access to my network'\n" +
        "{\"networkId\": \"f93f402c-86d4-11e7-a10d-0ac135e8bacf\", " +
        "\"userId\": \"d4c3b2a1-1234-5678-abcd-000000000002\", \"permission\": \"WRITE\"}\n\n" +
        "Example 3 — Make a group an admin of the network:\n" +
        "Prompt: 'Add my lab group as admin on network 9a8f...'\n" +
        "{\"networkId\": \"9a8f5ab1-3a5c-11e8-a935-0ac135e8bacf\", " +
        "\"groupId\": \"e5f6g7h8-1234-5678-abcd-000000000003\", \"permission\": \"ADMIN\"}\n\n" +
        "Example 4 — Share with a group at read level:\n" +
        "Prompt: 'Share this network read-only with group 00001111'\n" +
        "{\"networkId\": \"a1b2c3d4-0000-0000-0000-000000000001\", " +
        "\"groupId\": \"00001111-0000-0000-0000-000000000001\", \"permission\": \"READ\"}";

    static final String INPUT_SCHEMA = McpSchema.toJson(
        McpSchema.InputSchema.builder()
            .required("networkId")
            .required("permission")
            .property("networkId", new McpSchema.InputProperty("string",
                "Required. UUID of the NDEx network whose permission will be updated. " +
                "The caller must own (be ADMIN of) this network.\n\n" +
                "Examples: \"f93f402c-86d4-11e7-a10d-0ac135e8bacf\", " +
                "\"9a8f5ab1-3a5c-11e8-a935-0ac135e8bacf\""))
            .property("permission", new McpSchema.InputProperty("string",
                "Required. Permission level to grant to the specified user or group. " +
                "READ allows viewing the network. WRITE allows editing the network content. " +
                "ADMIN grants full ownership-level control, including the ability to share " +
                "and delete the network. Case-insensitive — \"read\" and \"READ\" are both accepted.\n\n" +
                "Examples: \"READ\", \"WRITE\", \"ADMIN\"",
                java.util.List.of("READ", "WRITE", "ADMIN")))
            .property("userId", new McpSchema.InputProperty("string",
                "Optional. Required when groupId is not provided. UUID of the NDEx user to " +
                "grant the permission to. Mutually exclusive with groupId — only one may be " +
                "supplied per call.\n\n" +
                "Examples: \"a1b2c3d4-1234-5678-abcd-000000000001\", " +
                "\"d4c3b2a1-1234-5678-abcd-000000000002\""))
            .property("groupId", new McpSchema.InputProperty("string",
                "Optional. Required when userId is not provided. UUID of the NDEx group to " +
                "grant the permission to. Mutually exclusive with userId — only one may be " +
                "supplied per call.\n\n" +
                "Examples: \"e5f6g7h8-1234-5678-abcd-000000000003\", " +
                "\"00001111-0000-0000-0000-000000000001\""))
            .build());

    static final String OUTPUT_SCHEMA = McpSchema.toSchemaJson(ShareNetworkResponse.class);

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

    public ShareNetworkTool(ToolsService toolsService) {
        this.toolsService = toolsService;
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

            ShareNetworkRequest input =
                    MAPPER.convertValue(req.arguments(), ShareNetworkRequest.class);

            if (input.userId() == null && input.groupId() == null) {
                return CallToolResult.builder()
                        .isError(true)
                        .addTextContent("share_network failed: " +
                            "either userId or groupId must be provided")
                        .build();
            }
            if (input.userId() != null && input.groupId() != null) {
                return CallToolResult.builder()
                        .isError(true)
                        .addTextContent("share_network failed: " +
                            "userId and groupId cannot both be provided")
                        .build();
            }

            String permissionUpper = input.permission().toUpperCase();
            String subjectType = input.userId() != null ? "user" : "group";
            String subjectId = input.userId() != null ? input.userId() : input.groupId();

            int count = new NetworkServiceV2(httpReq).updateNetworkPermission(
                    input.networkId(), input.userId(), input.groupId(), permissionUpper);

            return CallToolResult.builder()
                    .structuredContent(new ShareNetworkResponse(
                        input.networkId(),
                        subjectId,
                        subjectType,
                        permissionUpper,
                        count,
                        "Network permission updated."))
                    .build();

        } catch (UnauthorizedOperationException | SecurityException e) {
            return toolsService.unauthorizedResult();
        } catch (Throwable e) {
            logger.error("share_network failed", e);
            return CallToolResult.builder()
                    .isError(true)
                    .addTextContent("share_network failed: " + e.getMessage())
                    .build();
        }
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    record ShareNetworkResponse(

        @JsonPropertyDescription(
            "UUID of the NDEx network whose permission was updated.\n\n" +
            "Examples: \"f93f402c-86d4-11e7-a10d-0ac135e8bacf\", " +
            "\"9a8f5ab1-3a5c-11e8-a935-0ac135e8bacf\"")
        @JsonProperty("networkId")
        String networkId,

        @JsonPropertyDescription(
            "UUID of the user or group that received the permission update.\n\n" +
            "Examples: \"a1b2c3d4-1234-5678-abcd-000000000001\", " +
            "\"e5f6g7h8-1234-5678-abcd-000000000003\"")
        @JsonProperty("subjectId")
        String subjectId,

        @JsonPropertyDescription(
            "Whether the permission was granted to a user or a group.\n\n" +
            "Examples: \"user\", \"group\"")
        @JsonProperty("subjectType")
        String subjectType,

        @JsonPropertyDescription(
            "The permission level that was applied, always in uppercase.\n\n" +
            "Examples: \"READ\", \"WRITE\", \"ADMIN\"")
        @JsonProperty("permission")
        String permission,

        @JsonPropertyDescription(
            "Number of permission records affected in the database by this operation.\n\n" +
            "Examples: 1, 0")
        @JsonProperty("recordsAffected")
        Integer recordsAffected,

        @JsonPropertyDescription(
            "Human-readable confirmation that the permission update was applied successfully.\n\n" +
            "Examples: \"Network permission updated.\"")
        @JsonProperty("message")
        String message
    ) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    record ShareNetworkRequest(
        @JsonProperty("networkId")  String networkId,
        @JsonProperty("permission") String permission,
        @JsonProperty("userId")     String userId,
        @JsonProperty("groupId")    String groupId
    ) {}
}
