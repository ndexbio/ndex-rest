package org.ndexbio.rest.mcp.tools;

import java.util.Map;

import jakarta.servlet.http.HttpServletRequest;

import org.ndexbio.model.exceptions.UnauthorizedOperationException;
import org.ndexbio.model.object.User;
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
 * MCP tool: delete_network
 *
 * Delegates to NetworkServiceV3.deleteNetwork() via direct in-process call. The
 * HttpServletRequest (with the "User" attribute set by McpBasicAuthFilter) is retrieved
 * from the MCP transport context. Authentication is required — callers without a valid
 * session receive a 401 Unauthorized error response.
 */
public class DeleteNetworkTool {

    private static final Logger logger = LoggerFactory.getLogger(DeleteNetworkTool.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();

    static final String TOOL_NAME = "delete_network";

    private static final String TOOL_DESCRIPTION =
        "Delete an NDEx network identified by its UUID from the NDEx repository. " +
        "Use when a user or agent wants to remove a network they own from their NDEx account. " +
        "Supports soft deletion, which logically removes the network while retaining underlying data, " +
        "and permanent deletion, which irrecoverably removes the network record, all associated files, " +
        "and its search index entry from storage. " +
        "When the permanent option is omitted or false a soft deletion is performed; " +
        "when permanent is true the operation cannot be undone. " +
        "Authentication is required and the caller must own the network; returns a 401 Unauthorized " +
        "error response when the caller is unauthenticated or is not the network owner. " +
        "Returns an error response if the network does not exist, is locked by another process, " +
        "or is marked read-only.\n\n" +
        "## Examples\n\n" +
        "Example 1 — Soft-delete a network (default, reversible):\n" +
        "Prompt: 'Delete my NDEx network'\n" +
        "{\"networkId\": \"f93f402c-86d4-11e7-a10d-0ac135e8bacf\"}\n\n" +
        "Example 2 — Permanently delete a network and all its files:\n" +
        "Prompt: 'Permanently remove this network from NDEx'\n" +
        "{\"networkId\": \"f93f402c-86d4-11e7-a10d-0ac135e8bacf\", \"permanent\": true}\n\n" +
        "Example 3 — Delete a network after locating it by name via search:\n" +
        "Prompt: 'Find my network called Wnt Signaling and delete it'\n" +
        "{\"networkId\": \"9a8f5ab1-3a5c-11e8-a935-0ac135e8bacf\"}\n\n" +
        "Example 4 — Soft-delete a network explicitly:\n" +
        "Prompt: 'Remove this network from my NDEx account but keep it recoverable'\n" +
        "{\"networkId\": \"a1b2c3d4-0000-0000-0000-000000000001\", \"permanent\": false}";

    static final String INPUT_SCHEMA = McpSchema.toJson(
        McpSchema.InputSchema.builder()
            .required("networkId")
            .property("networkId", new McpSchema.InputProperty("string",
                "Required. UUID of the NDEx network to delete.\n\n" +
                "Examples: \"f93f402c-86d4-11e7-a10d-0ac135e8bacf\", " +
                "\"9a8f5ab1-3a5c-11e8-a935-0ac135e8bacf\""))
            .property("permanent", new McpSchema.InputProperty("boolean",
                "Optional. When true, permanently removes the network record, all associated files, " +
                "and its search index entry from storage — this operation cannot be undone. " +
                "When false or omitted, performs a soft deletion: the network is logically marked as " +
                "deleted but underlying data is retained. Defaults to false when omitted.\n\n" +
                "Examples: false, true"))
            .build());

    static final String OUTPUT_SCHEMA = McpSchema.toSchemaJson(DeleteNetworkResponse.class);

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

    public DeleteNetworkTool(ToolsService toolsService) {
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

            DeleteNetworkRequest input = MAPPER.convertValue(req.arguments(), DeleteNetworkRequest.class);
            boolean permanent = input.permanent() != null && input.permanent();

            new NetworkServiceV3(httpReq).deleteNetwork(input.networkId(), permanent);

            String action = permanent ? "permanently deleted" : "soft-deleted";
            DeleteNetworkResponse response = new DeleteNetworkResponse(
                input.networkId(),
                permanent,
                "Network " + input.networkId() + " was " + action + " successfully.");
            return CallToolResult.builder().structuredContent(response).build();

        } catch (UnauthorizedOperationException | SecurityException e) {
            return toolsService.unauthorizedResult();
        } catch (Throwable e) {
            logger.error("delete_network failed", e);
            return CallToolResult.builder()
                    .isError(true)
                    .addTextContent("delete_network failed: " + e.getMessage())
                    .build();
        }
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    record DeleteNetworkResponse(

        @JsonPropertyDescription(
            "UUID of the network that was deleted.\n\n" +
            "Examples: \"f93f402c-86d4-11e7-a10d-0ac135e8bacf\", " +
            "\"9a8f5ab1-3a5c-11e8-a935-0ac135e8bacf\"")
        @JsonProperty("networkId")
        String networkId,

        @JsonPropertyDescription(
            "Whether the deletion was permanent (true) or a soft deletion (false). " +
            "A permanent deletion irrecoverably removes all network data and files; " +
            "a soft deletion logically marks the network as deleted while retaining underlying data.\n\n" +
            "Examples: false, true")
        @JsonProperty("permanent")
        Boolean permanent,

        @JsonPropertyDescription(
            "Human-readable confirmation of the deletion outcome.\n\n" +
            "Examples: \"Network f93f402c-86d4-11e7-a10d-0ac135e8bacf was soft-deleted successfully.\", " +
            "\"Network f93f402c-86d4-11e7-a10d-0ac135e8bacf was permanently deleted successfully.\"")
        @JsonProperty("message")
        String message
    ) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    record DeleteNetworkRequest(
        @JsonProperty("networkId")  String  networkId,
        @JsonProperty("permanent")  Boolean permanent
    ) {}
}
