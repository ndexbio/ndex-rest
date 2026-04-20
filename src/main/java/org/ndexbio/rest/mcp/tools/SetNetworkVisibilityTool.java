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
 * MCP tool: set_network_visibility
 *
 * Delegates to NetworkServiceV2.setNetworkFlag() via direct in-process call, passing only
 * the "visibility" key in the system-property map. The HttpServletRequest (with the "User"
 * attribute set by McpBasicAuthFilter) is retrieved from the MCP transport context, so
 * getLoggedInUser() works transparently.
 * Authentication is required and the caller must own the network — callers without a valid
 * session or who are not the network owner receive a 401 Unauthorized error response.
 */
public class SetNetworkVisibilityTool {

    private static final Logger logger = LoggerFactory.getLogger(SetNetworkVisibilityTool.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();

    static final String TOOL_NAME = "set_network_visibility";

    private static final String TOOL_DESCRIPTION =
        "Set the visibility of an NDEx network identified by its UUID to PUBLIC, PRIVATE, or UNLISTED. " +
        "Use when a user or agent wants to control who can discover or access a network: " +
        "PUBLIC makes it fully searchable by any user, PRIVATE restricts access to the owner and " +
        "explicitly shared users, and UNLISTED hides it from search results while still allowing " +
        "access via direct UUID. " +
        "When indexing is enabled on the server, a Solr search index update is automatically " +
        "re-queued after the visibility change. " +
        "Authentication is required and the caller must be the network owner; returns a 401 " +
        "Unauthorized error response when unauthenticated or not the owner. " +
        "Returns an error response if the network does not exist, is invalid, or has a DOI " +
        "that prevents modification.\n\n" +
        "## Examples\n\n" +
        "Example 1 — Make a network publicly searchable:\n" +
        "Prompt: 'Make my NDEx network public'\n" +
        "{\"networkId\": \"f93f402c-86d4-11e7-a10d-0ac135e8bacf\", \"visibility\": \"PUBLIC\"}\n\n" +
        "Example 2 — Restrict a network to the owner and shared users:\n" +
        "Prompt: 'Set my NDEx network to private'\n" +
        "{\"networkId\": \"9a8f5ab1-3a5c-11e8-a935-0ac135e8bacf\", \"visibility\": \"PRIVATE\"}\n\n" +
        "Example 3 — Hide a network from search while keeping it link-accessible:\n" +
        "Prompt: 'Make my NDEx network unlisted so only people with the link can find it'\n" +
        "{\"networkId\": \"a1b2c3d4-0000-0000-0000-000000000001\", \"visibility\": \"UNLISTED\"}\n\n" +
        "Example 4 — Change visibility after retrieving the network summary:\n" +
        "Prompt: 'Find my Wnt signaling network and make it public'\n" +
        "{\"networkId\": \"f93f402c-86d4-11e7-a10d-0ac135e8bacf\", \"visibility\": \"PUBLIC\"}";

    static final String INPUT_SCHEMA = McpSchema.toJson(
        McpSchema.InputSchema.builder()
            .required("networkId", "visibility")
            .property("networkId", new McpSchema.InputProperty("string",
                "Required. UUID of the NDEx network whose visibility will be changed. " +
                "The caller must own this network.\n\n" +
                "Examples: \"f93f402c-86d4-11e7-a10d-0ac135e8bacf\", " +
                "\"9a8f5ab1-3a5c-11e8-a935-0ac135e8bacf\""))
            .property("visibility", new McpSchema.InputProperty("string",
                "Required. Target visibility level for the network. PUBLIC makes the network " +
                "fully searchable by any user. PRIVATE restricts access to the owner and " +
                "explicitly shared users. UNLISTED allows access by direct UUID only and hides " +
                "the network from search results. Case-insensitive — \"public\" and \"PUBLIC\" " +
                "are both accepted.\n\n" +
                "Examples: \"PUBLIC\", \"PRIVATE\", \"UNLISTED\"",
                java.util.List.of("PUBLIC", "PRIVATE", "UNLISTED")))
            .build());

    static final String OUTPUT_SCHEMA = McpSchema.toSchemaJson(SetNetworkVisibilityResponse.class);

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

    public SetNetworkVisibilityTool(ToolsService toolsService) {
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

            SetNetworkVisibilityRequest input =
                    MAPPER.convertValue(req.arguments(), SetNetworkVisibilityRequest.class);

            String normalizedVisibility = input.visibility().toUpperCase();
            Map<String, Object> params = Map.of("visibility", normalizedVisibility);

            new NetworkServiceV2(httpReq).setNetworkFlag(input.networkId(), params);

            return CallToolResult.builder()
                    .structuredContent(new SetNetworkVisibilityResponse(
                        input.networkId(),
                        normalizedVisibility,
                        "Network visibility set to " + normalizedVisibility + "."))
                    .build();

        } catch (UnauthorizedOperationException | SecurityException e) {
            return toolsService.unauthorizedResult();
        } catch (Throwable e) {
            logger.error("set_network_visibility failed", e);
            return CallToolResult.builder()
                    .isError(true)
                    .addTextContent("set_network_visibility failed: " + e.getMessage())
                    .build();
        }
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    record SetNetworkVisibilityResponse(

        @JsonPropertyDescription(
            "UUID of the NDEx network whose visibility was changed.\n\n" +
            "Examples: \"f93f402c-86d4-11e7-a10d-0ac135e8bacf\", " +
            "\"9a8f5ab1-3a5c-11e8-a935-0ac135e8bacf\"")
        @JsonProperty("networkId")
        String networkId,

        @JsonPropertyDescription(
            "The visibility level that was applied to the network. Always returned in " +
            "uppercase regardless of how the input was supplied.\n\n" +
            "Examples: \"PUBLIC\", \"PRIVATE\", \"UNLISTED\"")
        @JsonProperty("visibility")
        String visibility,

        @JsonPropertyDescription(
            "Human-readable confirmation that the visibility change was applied successfully.\n\n" +
            "Examples: \"Network visibility set to PUBLIC.\", " +
            "\"Network visibility set to PRIVATE.\", " +
            "\"Network visibility set to UNLISTED.\"")
        @JsonProperty("message")
        String message
    ) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    record SetNetworkVisibilityRequest(
        @JsonProperty("networkId")   String networkId,
        @JsonProperty("visibility")  String visibility
    ) {}
}
