package org.ndexbio.rest.mcp.tools;

import jakarta.servlet.http.HttpServletRequest;

import org.ndexbio.model.exceptions.UnauthorizedOperationException;
import org.ndexbio.model.object.NetworkSearchResult;
import org.ndexbio.model.object.SimpleNetworkQuery;
import org.ndexbio.rest.mcp.McpSchema;
import org.ndexbio.rest.mcp.ToolsService;
import org.ndexbio.rest.services.SearchServiceV2;
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
        "Prompt: 'Search NDEx for networks related to BRCA1'\n" +
        "{\"searchString\": \"BRCA1\"}\n\n" +
        "Example 2 — Find networks owned by a specific account:\n" +
        "Prompt: 'Find NDEx signaling networks owned by my account'\n" +
        "{\"searchString\": \"signaling\", \"accountName\": \"ndexcurator\"}\n\n" +
        "Example 3 — Include group-owned networks and search in results:\n" +
        "Prompt: 'Find apoptosis ndex networks including ones owned by my groups'\n" +
        "{\"searchString\": \"apoptosis\", \"includeGroups\": true}\n\n" +
        "Example 4 — Paginate through a large result set:\n" +
        "Prompt: 'Show me the next page of NDEx search results for cancer networks'\n" +
        "{\"searchString\": \"cancer\", \"start\": 100, \"size\": 50}";

    static final String INPUT_SCHEMA = McpSchema.toJson(
        McpSchema.InputSchema.builder()
            .required("searchString")
            .property("searchString", new McpSchema.InputProperty("string",
                "Required. Text to search across network names, descriptions, and node/edge data.\n\n" +
                "Examples: \"BRCA1\", \"apoptosis signaling\", \"Homo sapiens\""))
            .property("accountName", new McpSchema.InputProperty("string",
                "Optional. Filter results to networks owned or shared by this NDEx account name.\n\n" +
                "Examples: \"ndexcurator\", \"mylab\""))
            .property("includeGroups", new McpSchema.InputProperty("boolean",
                "Optional. When true, includes networks owned by groups the caller belongs to. Default false."))
            .property("start", new McpSchema.InputProperty("integer",
                "Optional. Zero-based pagination offset. Default 0."))
            .property("size", new McpSchema.InputProperty("integer",
                "Optional. Maximum number of results to return. Default 100."))
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

    public SearchNetworkTool(ToolsService toolsService) {
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

            SearchNetworkRequest input = MAPPER.convertValue(req.arguments(), SearchNetworkRequest.class);

            SimpleNetworkQuery query = new SimpleNetworkQuery();
            query.setSearchString(input.searchString());
            if (input.accountName()   != null) query.setAccountName(input.accountName());
            if (input.includeGroups() != null) query.setIncludeGroups(input.includeGroups());

            int start = input.start() != null ? input.start() : 0;
            int size  = input.size()  != null ? input.size()  : 100;

            NetworkSearchResult result = new SearchServiceV2(httpReq).searchNetwork(query, start, size);

            String json = MAPPER.writeValueAsString(result);
            return CallToolResult.builder().addTextContent(json).build();

        } catch (UnauthorizedOperationException | SecurityException e) {
            return toolsService.unauthorizedResult();
        } catch (Exception e) {
            logger.error("search_network failed", e);
            return CallToolResult.builder()
                    .isError(true)
                    .addTextContent("Search failed: " + e.getMessage())
                    .build();
        }
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
