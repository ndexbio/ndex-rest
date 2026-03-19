package org.ndexbio.rest.mcp.tools;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import jakarta.servlet.http.HttpServletRequest;

import org.ndexbio.model.exceptions.UnauthorizedOperationException;
import org.ndexbio.model.object.NetworkSearchResult;
import org.ndexbio.model.object.SimpleNetworkQuery;
import org.ndexbio.rest.mcp.ToolsService;
import org.ndexbio.rest.services.SearchServiceV2;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.ObjectMapper;

import io.modelcontextprotocol.server.McpServerFeatures;
import io.modelcontextprotocol.server.McpSyncServerExchange;
import io.modelcontextprotocol.spec.McpSchema;

/**
 * MCP tool: search_network
 *
 * Delegates to SearchServiceV2.searchNetwork() via direct in-process call. The HttpServletRequest
 * (with optional "User" attribute set by McpBasicAuthFilter) is retrieved from the MCP transport
 * context, so getLoggedInUser() works transparently for both authenticated and anonymous callers.
 */
public class SearchNetworkTool {

    private static final Logger logger = LoggerFactory.getLogger(SearchNetworkTool.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();

    static final String TOOL_NAME = "search_network";

    private static final String TOOL_DESCRIPTION =
        "Search the NDEx network repository and return a ranked list of network summaries matching a " +
        "text query. Use when a user asks to find, discover, or look up biological networks or pathways " +
        "by name, keyword, or owner account. Optionally filter results to networks owned or shared by " +
        "a specific account, or include group-owned networks in the result set. Supports pagination to " +
        "browse large result sets. Returns an error response when search execution fails, with a message " +
        "indicating the cause; if authentication is required for a restricted search scope, a 401 " +
        "Unauthorized error response is returned.\n\n" +
        "## Examples\n\n" +
        "Example 1 — Search for networks matching a keyword:\n" +
        "{\"searchString\": \"BRCA1\"}\n\n" +
        "Example 2 — Find networks owned by a specific account:\n" +
        "{\"searchString\": \"signaling\", \"accountName\": \"ndexcurator\"}\n\n" +
        "Example 3 — Include group-owned networks in results:\n" +
        "{\"searchString\": \"apoptosis\", \"includeGroups\": true}\n\n" +
        "Example 4 — Paginate through a large result set:\n" +
        "{\"searchString\": \"cancer\", \"start\": 100, \"size\": 50}";

    private static final McpSchema.Tool TOOL = McpSchema.Tool.builder()
        .name(TOOL_NAME)
        .description(TOOL_DESCRIPTION)
        .inputSchema(buildInputSchema())
        .build();

    private final ToolsService toolsService;

    public SearchNetworkTool(ToolsService toolsService) {
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

            SearchNetworkRequest input = MAPPER.convertValue(req.arguments(), SearchNetworkRequest.class);

            SimpleNetworkQuery query = new SimpleNetworkQuery();
            query.setSearchString(input.searchString());
            if (input.accountName()   != null) query.setAccountName(input.accountName());
            if (input.includeGroups() != null) query.setIncludeGroups(input.includeGroups());

            int start = input.start() != null ? input.start() : 0;
            int size  = input.size()  != null ? input.size()  : 100;

            NetworkSearchResult result = new SearchServiceV2(httpReq).searchNetwork(query, start, size);

            String json = MAPPER.writeValueAsString(result);
            return McpSchema.CallToolResult.builder().addTextContent(json).build();

        } catch (UnauthorizedOperationException | SecurityException e) {
            return toolsService.unauthorizedResult();
        } catch (Exception e) {
            logger.error("search_network failed", e);
            return McpSchema.CallToolResult.builder()
                    .isError(true)
                    .addTextContent("Search failed: " + e.getMessage())
                    .build();
        }
    }

    private static McpSchema.JsonSchema buildInputSchema() {
        Map<String, Object> properties = new LinkedHashMap<>();

        properties.put("searchString", Map.of(
            "type", "string",
            "description", "Required. Text to search across network names, descriptions, and node/edge data. " +
                           "Supports keyword and phrase queries.\n\nExamples: \"BRCA1\", \"apoptosis signaling\", \"Homo sapiens\""
        ));
        properties.put("accountName", Map.of(
            "type", "string",
            "description", "Optional. Filter results to networks owned or shared by this NDEx account name " +
                           "(exact, case-insensitive). Omit to search all public networks.\n\nExamples: \"ndexcurator\", \"mylab\", \"biopax\""
        ));
        properties.put("includeGroups", Map.of(
            "type", "boolean",
            "description", "Optional. When true, includes networks owned by groups the caller belongs to. " +
                           "Default false.\n\nExamples: true, false"
        ));
        properties.put("start", Map.of(
            "type", "integer",
            "description", "Optional. Zero-based offset into the result set for pagination. " +
                           "Default 0.\n\nExamples: 0, 50, 100"
        ));
        properties.put("size", Map.of(
            "type", "integer",
            "description", "Optional. Maximum number of network summaries to return. " +
                           "Default 100.\n\nExamples: 25, 100, 200"
        ));

        return new McpSchema.JsonSchema("object", properties, List.of("searchString"), null, null, null);
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record SearchNetworkRequest(
        @JsonProperty("searchString")  String  searchString,
        @JsonProperty("accountName")   String  accountName,
        @JsonProperty("includeGroups") Boolean includeGroups,
        @JsonProperty("start")         Integer start,
        @JsonProperty("size")          Integer size
    ) {}
}
