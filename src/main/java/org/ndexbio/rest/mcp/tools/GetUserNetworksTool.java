package org.ndexbio.rest.mcp.tools;

import java.util.List;
import java.util.Map;

import jakarta.servlet.http.HttpServletRequest;

import org.ndexbio.model.exceptions.UnauthorizedOperationException;
import org.ndexbio.model.object.User;
import org.ndexbio.model.object.network.NetworkSummary;
import org.ndexbio.rest.mcp.McpSchema;
import org.ndexbio.rest.mcp.ToolsService;
import org.ndexbio.rest.services.UserServiceV2;
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
 * MCP tool: get_user_networks
 *
 * Delegates to UserServiceV2.getNetworkSummariesForMyAccountPage() via direct in-process call.
 * The endpoint enforces that the requested user UUID matches the logged-in user, so the tool
 * derives the UUID from the authenticated User attribute — no userId input is accepted.
 * Authentication is required; the caller can only retrieve their own networks.
 */
public class GetUserNetworksTool {

    private static final Logger logger = LoggerFactory.getLogger(GetUserNetworksTool.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();

    static final String TOOL_NAME = "get_user_networks";

    private static final String TOOL_DESCRIPTION =
        "Retrieve the list of network summaries for the currently authenticated user's account page. " +
        "Use when enumerating the caller's owned and shortcut-linked networks — for example, to " +
        "discover a network UUID before performing an update, download, or sharing operation. " +
        "Read-only; authentication is required and returns a 401 Unauthorized error if the caller " +
        "is not authenticated. Results include shortcut-linked networks that do not appear in the " +
        "direct ownership list.\n\n" +
        "## Examples\n\n" +
        "Example 1 — List all networks (no pagination):\n" +
        "Prompt: 'Show me all my networks'\n" +
        "{}\n\n" +
        "Example 2 — First page of 20 networks:\n" +
        "Prompt: 'Get my first 20 networks'\n" +
        "{\"limit\": 20}\n\n" +
        "Example 3 — Second page of 20 networks:\n" +
        "Prompt: 'Get the next 20 networks after the first 20'\n" +
        "{\"offset\": 20, \"limit\": 20}";

    static final String INPUT_SCHEMA = McpSchema.toJson(
        McpSchema.InputSchema.builder()
            .property("offset", new McpSchema.InputProperty("integer",
                "Optional. Zero-based start index for paginating through the network list. " +
                "Pass 0 (or omit) to start from the beginning.\n\n" +
                "Examples: 0, 10, 50"))
            .property("limit", new McpSchema.InputProperty("integer",
                "Optional. Maximum number of network summaries to return. " +
                "Use 0 (or omit) to return all networks with no cap.\n\n" +
                "Examples: 0, 20, 100"))
            .build());

    static final String OUTPUT_SCHEMA = McpSchema.toSchemaJson(GetUserNetworksResponse.class);

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

    public GetUserNetworksTool(ToolsService toolsService) {
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

            GetUserNetworksRequest input =
                    MAPPER.convertValue(req.arguments(), GetUserNetworksRequest.class);

            int offset = input.offset() != null ? input.offset() : 0;
            int limit  = input.limit()  != null ? input.limit()  : 0;
            String userId = user.getExternalId().toString();

            List<NetworkSummary> result =
                    new UserServiceV2(httpReq)
                            .getNetworkSummariesForMyAccountPage(userId, offset, limit);

            return CallToolResult.builder()
                    .structuredContent(new GetUserNetworksResponse(result.size(), result))
                    .build();

        } catch (UnauthorizedOperationException | SecurityException e) {
            return toolsService.unauthorizedResult();
        } catch (Throwable e) {
            logger.error("get_user_networks failed", e);
            return CallToolResult.builder()
                    .isError(true)
                    .addTextContent("get_user_networks failed: " + e.getMessage())
                    .build();
        }
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    record GetUserNetworksResponse(

        @JsonPropertyDescription("Number of network summary objects returned in this response.")
        @JsonProperty("count")
        Integer count,

        @JsonPropertyDescription(
            "Array of NetworkSummary objects for the authenticated user's account page. " +
            "Each entry contains the network UUID (externalId), name, description, visibility, " +
            "owner, node/edge counts, creation/modification timestamps, and other metadata. " +
            "Also includes shortcut-linked networks that do not already appear in the direct results.")
        @JsonProperty("networks")
        List<NetworkSummary> networks
    ) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    record GetUserNetworksRequest(
        @JsonProperty("offset") Integer offset,
        @JsonProperty("limit")  Integer limit
    ) {}
}
