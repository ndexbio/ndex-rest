package org.ndexbio.rest.mcp.tools;

import java.util.List;

import jakarta.servlet.http.HttpServletRequest;

import org.ndexbio.model.exceptions.UnauthorizedOperationException;
import org.ndexbio.model.object.network.NetworkSummaryV3;
import org.ndexbio.rest.mcp.McpSchema;
import org.ndexbio.rest.mcp.ToolsService;
import org.ndexbio.rest.services.v3.NetworkServiceV3;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.ObjectMapper;

import io.modelcontextprotocol.server.McpServerFeatures;
import io.modelcontextprotocol.server.McpSyncServerExchange;
import io.modelcontextprotocol.spec.McpSchema.CallToolRequest;
import io.modelcontextprotocol.spec.McpSchema.CallToolResult;
import io.modelcontextprotocol.spec.McpSchema.JsonSchema;
import io.modelcontextprotocol.spec.McpSchema.Tool;

/**
 * MCP tool: get_network_summary
 *
 * Delegates to NetworkServiceV3.getNetworkSummaryV3() via direct in-process call. The
 * HttpServletRequest (with optional "User" attribute set by McpBasicAuthFilter) is retrieved
 * from the MCP transport context, so getLoggedInUser() works transparently for both
 * authenticated and anonymous callers.
 */
public class GetNetworkSummaryTool {

    private static final Logger logger = LoggerFactory.getLogger(GetNetworkSummaryTool.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();

    static final String TOOL_NAME = "get_network_summary";

    private static final String TOOL_DESCRIPTION =
        "Retrieve the full metadata summary for a single NDEx network identified by its UUID. " +
        "Use when a user or agent already knows a network's identifier and needs its name, " +
        "description, owner, node/edge counts, visibility, completion status, file format, " +
        "file sizes, or network properties. Returns the complete NetworkSummaryV3 JSON object; " +
        "if the format parameter is provided, returns only the subset of fields corresponding to " +
        "that format (UPDATE, COMPACT, PROPERTIES, or FULL). An optional access key allows " +
        "retrieval of networks that are not publicly visible. Returns an error response if the " +
        "network does not exist, the caller lacks read permission, or the format value is " +
        "unrecognized; a 401 Unauthorized error response is returned when authentication is required.\n\n" +
        "## Examples\n\n" +
        "Example 1 — Retrieve full summary for a known network:\n" +
        "Prompt: 'Get the summary for my NDEx network'\n" +
        "{\"networkId\": \"f93f402c-86d4-11e7-a10d-0ac135e8bacf\"}\n\n" +
        "Example 2 — Retrieve only compact metadata (name, stats, no properties):\n" +
        "Prompt: 'What are the basic stats for my NDEx network?'\n" +
        "{\"networkId\": \"f93f402c-86d4-11e7-a10d-0ac135e8bacf\", \"format\": \"COMPACT\"}\n\n" +
        "Example 3 — Retrieve a private network using an access key:\n" +
        "Prompt: 'Show me the details for my private NDEx network'\n" +
        "{\"networkId\": \"a1b2c3d4-0000-0000-0000-000000000001\", \"accessKey\": \"mySecretKey\"}\n\n" +
        "Example 4 — Retrieve only network properties:\n" +
        "Prompt: 'List the properties of my NDEx network'\n" +
        "{\"networkId\": \"f93f402c-86d4-11e7-a10d-0ac135e8bacf\", \"format\": \"PROPERTIES\"}";

    static final String INPUT_SCHEMA = McpSchema.toJson(
        McpSchema.InputSchema.builder()
            .required("networkId")
            .property("networkId", new McpSchema.InputProperty("string",
                "Required. UUID of the NDEx network to retrieve.\n\n" +
                "Examples: \"f93f402c-86d4-11e7-a10d-0ac135e8bacf\""))
            .property("accessKey", new McpSchema.InputProperty("string",
                "Optional. Access key granting read permission on non-public networks."))
            .property("format", new McpSchema.InputProperty("string",
                "Optional. Controls which fields are included. Values: UPDATE, COMPACT, " +
                "PROPERTIES, FULL (default).",
                List.of("FULL", "COMPACT", "PROPERTIES", "UPDATE")))
            .build());

    private static final Tool TOOL;
    static {
        try {
            TOOL = Tool.builder()
                .name(TOOL_NAME)
                .description(TOOL_DESCRIPTION)
                .inputSchema(MAPPER.readValue(INPUT_SCHEMA, JsonSchema.class))
                .build();
        } catch (Exception e) {
            throw new ExceptionInInitializerError(e);
        }
    }

    private final ToolsService toolsService;

    public GetNetworkSummaryTool(ToolsService toolsService) {
        this.toolsService = toolsService;
    }

    public McpServerFeatures.SyncToolSpecification toSpec() {
        return McpServerFeatures.SyncToolSpecification.builder()
                .tool(TOOL).callHandler(this::handle).build();
    }

    private CallToolResult handle(McpSyncServerExchange exchange, CallToolRequest req) {
        try {
            HttpServletRequest httpReq = (HttpServletRequest)
                    exchange.transportContext().get("ndexRequest");

            GetNetworkSummaryRequest input =
                    MAPPER.convertValue(req.arguments(), GetNetworkSummaryRequest.class);

            String format = input.format() != null ? input.format() : "FULL";

            NetworkSummaryV3 result =
                    new NetworkServiceV3(httpReq)
                            .getNetworkSummaryV3(input.networkId(), input.accessKey(), format);

            return CallToolResult.builder()
                    .addTextContent(MAPPER.writeValueAsString(result))
                    .build();

        } catch (UnauthorizedOperationException | SecurityException e) {
            return toolsService.unauthorizedResult();
        } catch (Throwable e) {
            logger.error("get_network_summary failed", e);
            return CallToolResult.builder()
                    .isError(true)
                    .addTextContent("get_network_summary failed: " + e.getMessage())
                    .build();
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record GetNetworkSummaryRequest(
        @JsonProperty("networkId")  String networkId,
        @JsonProperty("accessKey")  String accessKey,
        @JsonProperty("format")     String format
    ) {}
}
