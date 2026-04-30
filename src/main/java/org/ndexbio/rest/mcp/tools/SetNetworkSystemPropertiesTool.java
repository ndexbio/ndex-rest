package org.ndexbio.rest.mcp.tools;

import java.util.HashMap;
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
 * MCP tool: set_network_systemproperties
 *
 * Delegates to NetworkServiceV2.setNetworkFlag() via direct in-process call. Supports setting
 * the "visibility" and/or "readonly" system properties in a single request. The HttpServletRequest
 * (with the "User" attribute set by McpBasicAuthFilter) is retrieved from the MCP transport
 * context, so getLoggedInUser() works transparently.
 * Authentication is required and the caller must own the network — callers without a valid
 * session or who are not the network owner receive a 401 Unauthorized error response.
 * At least one of visibility or readonly must be provided.
 */
public class SetNetworkSystemPropertiesTool {

    private static final Logger logger = LoggerFactory.getLogger(SetNetworkSystemPropertiesTool.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();

    static final String TOOL_NAME = "set_network_systemproperties";

    private static final String TOOL_DESCRIPTION =
        "Update the server-side system properties of an NDEx network identified by its UUID. " +
        "Use when a user or agent wants to control who can access a network (visibility) or " +
        "prevent further modifications to it (read-only lock). " +
        "Both properties are optional but at least one must be supplied in a single call; " +
        "both may be set together in one operation. " +
        "When visibility is changed and indexing is enabled on the server, a Solr search index " +
        "update is automatically re-queued. " +
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
        "Example 3 — Lock a network so no further edits can be made:\n" +
        "Prompt: 'Make my NDEx network read-only'\n" +
        "{\"networkId\": \"a1b2c3d4-0000-0000-0000-000000000001\", \"readonly\": true}\n\n" +
        "Example 4 — Unlock a network and make it public in one call:\n" +
        "Prompt: 'Remove the read-only lock from my network and make it public'\n" +
        "{\"networkId\": \"f93f402c-86d4-11e7-a10d-0ac135e8bacf\", \"visibility\": \"PUBLIC\", " +
        "\"readonly\": false}";

    static final String INPUT_SCHEMA = McpSchema.toJson(
        McpSchema.InputSchema.builder()
            .required("networkId")
            .property("networkId", new McpSchema.InputProperty("string",
                "Required. UUID of the NDEx network whose system properties will be updated. " +
                "The caller must own this network.\n\n" +
                "Examples: \"f93f402c-86d4-11e7-a10d-0ac135e8bacf\", " +
                "\"9a8f5ab1-3a5c-11e8-a935-0ac135e8bacf\""))
            .property("visibility", new McpSchema.InputProperty("string",
                "Optional. Target visibility level for the network. Required when readonly is " +
                "not provided. PUBLIC makes the network fully searchable by any user. PRIVATE " +
                "restricts access to the owner and explicitly shared users. UNLISTED allows " +
                "access by direct UUID only and hides the network from search results. " +
                "Case-insensitive — \"public\" and \"PUBLIC\" are both accepted.\n\n" +
                "Examples: \"PUBLIC\", \"PRIVATE\", \"UNLISTED\"",
                java.util.List.of("PUBLIC", "PRIVATE", "UNLISTED")))
            .property("readonly", new McpSchema.InputProperty("boolean",
                "Optional. When true, locks the network so no further edits can be made by " +
                "any user. When false, removes the read-only lock and allows modifications " +
                "again. Required when visibility is not provided.\n\n" +
                "Examples: true, false"))
            .build());

    static final String OUTPUT_SCHEMA = McpSchema.toSchemaJson(SetNetworkSystemPropertiesResponse.class);

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

    public SetNetworkSystemPropertiesTool(ToolsService toolsService) {
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

            SetNetworkSystemPropertiesRequest input =
                    MAPPER.convertValue(req.arguments(), SetNetworkSystemPropertiesRequest.class);

            if (input.visibility() == null && input.readonly() == null) {
                return CallToolResult.builder()
                        .isError(true)
                        .addTextContent("set_network_systemproperties failed: " +
                            "at least one of visibility or readonly must be provided")
                        .build();
            }

            HashMap<String, Object> params = new HashMap<>();
            String normalizedVisibility = null;
            if (input.visibility() != null) {
                normalizedVisibility = input.visibility().toUpperCase();
                params.put("visibility", normalizedVisibility);
            }
            if (input.readonly() != null) {
                params.put("readonly", input.readonly());
            }

            new NetworkServiceV2(httpReq).setNetworkFlag(input.networkId(), params);

            return CallToolResult.builder()
                    .structuredContent(new SetNetworkSystemPropertiesResponse(
                        input.networkId(),
                        normalizedVisibility,
                        input.readonly(),
                        "Network system properties updated."))
                    .build();

        } catch (UnauthorizedOperationException | SecurityException e) {
            return toolsService.unauthorizedResult();
        } catch (Throwable e) {
            logger.error("set_network_systemproperties failed", e);
            return CallToolResult.builder()
                    .isError(true)
                    .addTextContent("set_network_systemproperties failed: " + e.getMessage())
                    .build();
        }
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    record SetNetworkSystemPropertiesResponse(

        @JsonPropertyDescription(
            "UUID of the NDEx network whose system properties were updated.\n\n" +
            "Examples: \"f93f402c-86d4-11e7-a10d-0ac135e8bacf\", " +
            "\"9a8f5ab1-3a5c-11e8-a935-0ac135e8bacf\"")
        @JsonProperty("networkId")
        String networkId,

        @JsonPropertyDescription(
            "The visibility level that was applied. Present only when visibility was included " +
            "in the request; omitted otherwise. Always returned in uppercase.\n\n" +
            "Examples: \"PUBLIC\", \"PRIVATE\", \"UNLISTED\"")
        @JsonProperty("visibility")
        String visibility,

        @JsonPropertyDescription(
            "The read-only flag value that was applied. Present only when readonly was included " +
            "in the request; omitted otherwise.\n\n" +
            "Examples: true, false")
        @JsonProperty("readonly")
        Boolean readonly,

        @JsonPropertyDescription(
            "Human-readable confirmation that the system property update was applied successfully.\n\n" +
            "Examples: \"Network system properties updated.\"")
        @JsonProperty("message")
        String message
    ) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    record SetNetworkSystemPropertiesRequest(
        @JsonProperty("networkId")   String  networkId,
        @JsonProperty("visibility")  String  visibility,
        @JsonProperty("readonly")    Boolean readonly
    ) {}
}
