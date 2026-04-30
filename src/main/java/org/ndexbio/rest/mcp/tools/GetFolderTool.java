package org.ndexbio.rest.mcp.tools;

import java.util.List;
import java.util.Map;

import jakarta.servlet.http.HttpServletRequest;

import org.ndexbio.model.exceptions.UnauthorizedOperationException;
import org.ndexbio.model.object.FileCount;
import org.ndexbio.model.object.FileItemSummary;
import org.ndexbio.model.object.NdexFolder;
import org.ndexbio.model.object.User;
import org.ndexbio.rest.mcp.McpSchema;
import org.ndexbio.rest.mcp.ToolsService;
import org.ndexbio.rest.mcp.ValidationService;
import org.ndexbio.rest.services.v3.files.FolderServiceV3;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.databind.ObjectMapper;

import io.modelcontextprotocol.server.McpServerFeatures;
import io.modelcontextprotocol.server.McpSyncServerExchange;
import io.modelcontextprotocol.spec.McpSchema.CallToolRequest;
import io.modelcontextprotocol.spec.McpSchema.CallToolResult;
import io.modelcontextprotocol.spec.McpSchema.JsonSchema;
import io.modelcontextprotocol.spec.McpSchema.Tool;

/**
 * MCP tool: get_folder
 *
 * Delegates to FolderServiceV3 via direct in-process call. Supports four modes:
 * list (all user folders), get (single folder metadata), browse (folder contents),
 * and count (item counts within a folder).
 *
 * The HttpServletRequest (with optional "User" attribute set by McpBasicAuthFilter) is
 * retrieved from the MCP transport context. List mode always requires authentication;
 * get/browse/count modes support anonymous access via accessKey.
 */
public class GetFolderTool {

    private static final Logger logger = LoggerFactory.getLogger(GetFolderTool.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();

    static final String TOOL_NAME = "get_folder";

    private static final String TOOL_DESCRIPTION =
        "Retrieve folder metadata, list owned folders, browse folder contents, or count items " +
        "within a folder in the NDEx repository. " +
        "Use when a user or agent needs to discover, inspect, or navigate the folder hierarchy " +
        "of an NDEx account. " +
        "Supports four operations selected by the required mode parameter: listing all folders " +
        "owned by the authenticated user, fetching a single folder's metadata, enumerating items " +
        "inside a specific folder (networks, subfolders, and shortcuts with optional type filter), " +
        "and counting those items by type. " +
        "Anonymous read access to non-private folders is possible via an access key; " +
        "the list operation always requires authentication. " +
        "Returns an error response if the folder is not found, the caller lacks read permission, " +
        "or the mode value is unrecognized; a 401 Unauthorized error response is returned when " +
        "authentication is required but not present.\n\n" +
        "## Examples\n\n" +
        "Example 1 — List all folders owned by the current user:\n" +
        "Prompt: 'Show me my NDEx folders'\n" +
        "{\"mode\": \"list\"}\n\n" +
        "Example 2 — Get metadata for a specific folder:\n" +
        "Prompt: 'Get details for my Signaling Networks folder'\n" +
        "{\"mode\": \"get\", \"folderId\": {\"waived\": false, \"parameter\": \"f93f402c-86d4-11e7-a10d-0ac135e8bacf\"}}\n\n" +
        "Example 3 — Browse only networks inside a folder:\n" +
        "Prompt: 'List the networks in my Wnt folder'\n" +
        "{\"mode\": \"browse\", \"folderId\": {\"waived\": false, \"parameter\": \"f93f402c-86d4-11e7-a10d-0ac135e8bacf\"}, \"type\": \"network\"}\n\n" +
        "Example 4 — Count items inside a folder:\n" +
        "Prompt: 'How many items are in my Signaling Pathways folder?'\n" +
        "{\"mode\": \"count\", \"folderId\": {\"waived\": false, \"parameter\": \"a1b2c3d4-0000-0000-0000-000000000099\"}}";

    static final String INPUT_SCHEMA = McpSchema.toJson(
        McpSchema.InputSchema.builder()
            .required("mode")
            .property("mode", new McpSchema.InputProperty("string",
                "Required. Selects the operation to perform. " +
                "list: returns all folders owned by the authenticated user. " +
                "get: returns metadata for a single folder identified by folderId. " +
                "browse: returns items (networks, subfolders, shortcuts) inside a folder; " +
                "accepts \"home\" as folderId to list all root-level items. " +
                "count: returns item counts (networks, subfolders, shortcuts) inside a folder.\n\n" +
                "Examples: \"list\", \"get\", \"browse\", \"count\"",
                List.of("list", "get", "browse", "count")))
            .conditionalParam("folderId", "string",
                "Conditional on mode='get', 'browse', or 'count'. " +
                "UUID of the NDEx folder to retrieve, browse, or count. " +
                "Also accepts the literal string \"home\" when mode='browse' to list all " +
                "root-level items owned by the authenticated user. " +
                "Required when mode='get', 'browse', or 'count'. " +
                "Provide {waived:true} when mode='list' (not applicable).\n\n" +
                "Examples: \"f93f402c-86d4-11e7-a10d-0ac135e8bacf\", \"home\"")
            .property("limit", new McpSchema.InputProperty("integer",
                "Optional. Maximum number of folders to return when mode='list'. " +
                "Ignored for all other modes. Defaults to 100 when omitted.\n\n" +
                "Examples: 10, 50, 100"))
            .property("format", new McpSchema.InputProperty("string",
                "Optional. Controls response detail level when mode='browse'. " +
                "compact returns basic metadata only; update (default) includes full metadata " +
                "such as description, edge counts, and visibility. " +
                "Ignored for all other modes.\n\n" +
                "Examples: \"update\", \"compact\"",
                List.of("update", "compact")))
            .property("type", new McpSchema.InputProperty("string",
                "Optional. Filters items by type when mode='browse'. " +
                "When omitted, all item types are returned. " +
                "Ignored for all other modes.\n\n" +
                "Examples: \"network\", \"folder\", \"shortcut\"",
                List.of("network", "folder", "shortcut")))
            .property("accessKey", new McpSchema.InputProperty("string",
                "Optional. Access key granting read permission on non-public folders. " +
                "Applicable when mode='get', 'browse', or 'count'. " +
                "Ignored when mode='list'.\n\n" +
                "Examples: \"mySecretKey123\""))
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
    private final ValidationService validationService;

    public GetFolderTool(ToolsService toolsService) {
        this.toolsService = toolsService;
        this.validationService = new ValidationService();
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
            Map<String, Object> args = req.arguments() != null ? req.arguments() : Map.of();
            String mode = MAPPER.convertValue(args.get("mode"), String.class);

            switch (mode != null ? mode : "") {

                case "list": {
                    User user = (User) httpReq.getAttribute("User");
                    if (user == null) return toolsService.unauthorizedResult();
                    Integer limit = MAPPER.convertValue(args.get("limit"), Integer.class);
                    int effectiveLimit = (limit != null) ? limit : 100;
                    List<NdexFolder> folders =
                            new FolderServiceV3(httpReq).listMyFolders(effectiveLimit);
                    return CallToolResult.builder()
                            .addTextContent(MAPPER.writeValueAsString(folders))
                            .build();
                }

                case "get": {
                    CallToolResult err = validationService.validateConditionalParams(
                            "mode", "get", args,
                            List.of(new ValidationService.ConditionalParam(
                                    "folderId", "the folder to retrieve", false)));
                    if (err != null) return err;
                    String folderId = validationService.unwrapToolInputValue(
                            args.get("folderId"), String.class);
                    String accessKey = MAPPER.convertValue(args.get("accessKey"), String.class);
                    NdexFolder folder =
                            new FolderServiceV3(httpReq).getFolder(folderId, accessKey, null, null);
                    return CallToolResult.builder()
                            .addTextContent(MAPPER.writeValueAsString(folder))
                            .build();
                }

                case "browse": {
                    CallToolResult err = validationService.validateConditionalParams(
                            "mode", "browse", args,
                            List.of(new ValidationService.ConditionalParam(
                                    "folderId", "the folder whose contents to list", false)));
                    if (err != null) return err;
                    String folderId = validationService.unwrapToolInputValue(
                            args.get("folderId"), String.class);
                    String format = MAPPER.convertValue(args.get("format"), String.class);
                    if (format == null) format = "update";
                    String type = MAPPER.convertValue(args.get("type"), String.class);
                    String accessKey = MAPPER.convertValue(args.get("accessKey"), String.class);
                    List<FileItemSummary> items =
                            new FolderServiceV3(httpReq)
                                    .listItemsInFolder(folderId, format, type, accessKey);
                    return CallToolResult.builder()
                            .addTextContent(MAPPER.writeValueAsString(items))
                            .build();
                }

                case "count": {
                    CallToolResult err = validationService.validateConditionalParams(
                            "mode", "count", args,
                            List.of(new ValidationService.ConditionalParam(
                                    "folderId", "the folder to count items in", false)));
                    if (err != null) return err;
                    String folderId = validationService.unwrapToolInputValue(
                            args.get("folderId"), String.class);
                    String accessKey = MAPPER.convertValue(args.get("accessKey"), String.class);
                    FileCount count =
                            new FolderServiceV3(httpReq).getChildCount(folderId, accessKey);
                    return CallToolResult.builder()
                            .addTextContent(MAPPER.writeValueAsString(count))
                            .build();
                }

                default:
                    return CallToolResult.builder()
                            .isError(true)
                            .addTextContent("get_folder failed: unknown mode '" + mode +
                                    "'. Must be one of: list, get, browse, count.")
                            .build();
            }

        } catch (UnauthorizedOperationException | SecurityException e) {
            return toolsService.unauthorizedResult();
        } catch (Throwable e) {
            logger.error("get_folder failed", e);
            return CallToolResult.builder()
                    .isError(true)
                    .addTextContent("get_folder failed: " + e.getMessage())
                    .build();
        }
    }
}
