package org.ndexbio.rest.mcp.tools;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import jakarta.servlet.http.HttpServletRequest;

import org.ndexbio.model.exceptions.UnauthorizedOperationException;
import org.ndexbio.model.object.network.NetworkSummaryV3;
import org.ndexbio.rest.mcp.ToolsService;
import org.ndexbio.rest.services.v3.NetworkServiceV3;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.ObjectMapper;

import io.modelcontextprotocol.server.McpServerFeatures;
import io.modelcontextprotocol.server.McpSyncServerExchange;
import io.modelcontextprotocol.spec.McpSchema;

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
        "{\"networkId\": \"f93f402c-86d4-11e7-a10d-0ac135e8bacf\"}\n\n" +
        "Example 2 — Retrieve only compact metadata (name, stats, no properties):\n" +
        "{\"networkId\": \"f93f402c-86d4-11e7-a10d-0ac135e8bacf\", \"format\": \"COMPACT\"}\n\n" +
        "Example 3 — Retrieve a private network using an access key:\n" +
        "{\"networkId\": \"a1b2c3d4-0000-0000-0000-000000000001\", \"accessKey\": \"mySecretKey\"}\n\n" +
        "Example 4 — Retrieve only network properties:\n" +
        "{\"networkId\": \"f93f402c-86d4-11e7-a10d-0ac135e8bacf\", \"format\": \"PROPERTIES\"}";

    private static final McpSchema.Tool TOOL = McpSchema.Tool.builder()
        .name(TOOL_NAME)
        .description(TOOL_DESCRIPTION)
        .inputSchema(buildInputSchema())
        .build();

    private final ToolsService toolsService;

    public GetNetworkSummaryTool(ToolsService toolsService) {
        this.toolsService = toolsService;
    }

    public McpServerFeatures.SyncToolSpecification toSpec() {
        return new McpServerFeatures.SyncToolSpecification(TOOL, this::handle);
    }

    private McpSchema.CallToolResult handle(McpSyncServerExchange exchange,
                                            McpSchema.CallToolRequest req) {
        try {
            HttpServletRequest httpReq = (HttpServletRequest)
                    exchange.transportContext().get("ndexRequest");

            GetNetworkSummaryRequest input =
                    MAPPER.convertValue(req.arguments(), GetNetworkSummaryRequest.class);

            String format = input.format() != null ? input.format() : "FULL";

            NetworkSummaryV3 result =
                    new NetworkServiceV3(httpReq)
                            .getNetworkSummaryV3(input.networkId(), input.accessKey(), format);

            return McpSchema.CallToolResult.builder()
                    .addTextContent(MAPPER.writeValueAsString(result))
                    .build();

        } catch (UnauthorizedOperationException | SecurityException e) {
            return toolsService.unauthorizedResult();
        } catch (Throwable e) {
            logger.error("get_network_summary failed", e);
            return McpSchema.CallToolResult.builder()
                    .isError(true)
                    .addTextContent("get_network_summary failed: " + e.getMessage())
                    .build();
        }
    }

    private static McpSchema.JsonSchema buildInputSchema() {
        Map<String, Object> properties = new LinkedHashMap<>();

        properties.put("networkId", Map.of(
            "type", "string",
            "description", "Required. UUID of the NDEx network to retrieve. " +
                           "Must be a valid UUID string.\n\n" +
                           "Examples: \"f93f402c-86d4-11e7-a10d-0ac135e8bacf\", " +
                           "\"a1b2c3d4-0000-0000-0000-000000000001\", " +
                           "\"9a8f5ab1-3a5c-11e8-a935-0ac135e8bacf\""
        ));
        properties.put("accessKey", Map.of(
            "type", "string",
            "description", "Optional. Access key granting read permission on non-public networks. " +
                           "Omit for publicly visible networks.\n\n" +
                           "Examples: \"mySecretKey\", \"sharedAccessToken123\", \"lab-private-key\""
        ));
        properties.put("format", Map.of(
            "type", "string",
            "description", "Optional. Controls which fields are included in the response. " +
                           "Accepted values: UPDATE (uuid, modificationTime, updatedBy), " +
                           "COMPACT (name, stats, no properties), " +
                           "PROPERTIES (uuid, modificationTime, name, description, properties), " +
                           "FULL (all fields, default). Omit for complete summary.\n\n" +
                           "Examples: \"FULL\", \"COMPACT\", \"PROPERTIES\", \"UPDATE\""
        ));

        return new McpSchema.JsonSchema("object", properties, List.of("networkId"), null, null, null);
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record GetNetworkSummaryRequest(
        @JsonProperty("networkId")  String networkId,
        @JsonProperty("accessKey")  String accessKey,
        @JsonProperty("format")     String format
    ) {}
}
