package org.ndexbio.rest.mcp.tools;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.ws.rs.core.Response;

import org.ndexbio.model.exceptions.UnauthorizedOperationException;
import org.ndexbio.model.object.FolderRequest;
import org.ndexbio.model.object.NdexObjectUpdateStatus;
import org.ndexbio.model.object.User;
import org.ndexbio.rest.mcp.McpSchema;
import org.ndexbio.rest.mcp.NdexObjectUpdateStatusMixIn;
import org.ndexbio.rest.mcp.ToolsService;
import org.ndexbio.rest.mcp.ValidationService;
import org.ndexbio.rest.services.v3.files.FolderServiceV3;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

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
 * MCP tool: manage_folder
 *
 * Delegates to FolderServiceV3 via direct in-process call. Supports four modes:
 * create (new folder), update (rename/move/redescribe), delete (soft or permanent),
 * and get_access_key (retrieve sharing key).
 *
 * Authentication is always required. The HttpServletRequest (with "User" attribute set by
 * McpBasicAuthFilter) is retrieved from the MCP transport context.
 */
public class ManageFolderTool {

    private static final Logger logger = LoggerFactory.getLogger(ManageFolderTool.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();

    static final String TOOL_NAME = "manage_folder";

    private static final String TOOL_DESCRIPTION =
        "Create, rename, move, or delete a folder in the NDEx repository, and retrieve a " +
        "folder's access key for sharing. " +
        "Use when a user or agent needs to organize networks into a folder hierarchy or " +
        "modify an existing folder's structure. " +
        "Supports four operations selected by the required mode parameter: creating a new folder, " +
        "updating a folder's name, parent location, or description, deleting a folder (soft or " +
        "permanent, optionally forcing non-empty deletion), and retrieving the folder's access key. " +
        "Authentication is always required; returns a 401 Unauthorized error response when the " +
        "caller is not authenticated or does not own the folder. " +
        "Returns an error response if the folder is not found, if a delete of a non-empty folder " +
        "is attempted without the force option, or if a folder update would create a cycle in the " +
        "hierarchy.\n\n" +
        "## Examples\n\n" +
        "Example 1 — Create a new folder at root level:\n" +
        "Prompt: 'Create a folder called Signaling Networks in my NDEx account'\n" +
        "{\"mode\": \"create\", \"name\": {\"waived\": false, \"parameter\": \"Signaling Networks\"}, " +
        "\"folderId\": {\"waived\": true, \"parameter\": null}}\n\n" +
        "Example 2 — Rename an existing folder:\n" +
        "Prompt: 'Rename my NDEx folder to Cancer Pathways'\n" +
        "{\"mode\": \"update\", \"folderId\": {\"waived\": false, \"parameter\": \"f93f402c-86d4-11e7-a10d-0ac135e8bacf\"}, " +
        "\"name\": {\"waived\": false, \"parameter\": \"Cancer Pathways\"}}\n\n" +
        "Example 3 — Soft-delete a folder:\n" +
        "Prompt: 'Delete my old Wnt Signaling folder from NDEx'\n" +
        "{\"mode\": \"delete\", \"folderId\": {\"waived\": false, \"parameter\": \"a1b2c3d4-0000-0000-0000-000000000099\"}, " +
        "\"name\": {\"waived\": true, \"parameter\": null}}\n\n" +
        "Example 4 — Retrieve the access key for a folder:\n" +
        "Prompt: 'Get the access key for my shared NDEx folder'\n" +
        "{\"mode\": \"get_access_key\", \"folderId\": {\"waived\": false, \"parameter\": \"f93f402c-86d4-11e7-a10d-0ac135e8bacf\"}, " +
        "\"name\": {\"waived\": true, \"parameter\": null}}";

    static final String INPUT_SCHEMA = McpSchema.toJson(
        McpSchema.InputSchema.builder()
            .required("mode")
            .property("mode", new McpSchema.InputProperty("string",
                "Required. Selects the operation to perform. " +
                "create: creates a new folder (name required). " +
                "update: modifies an existing folder's name, parent, or description (folderId required). " +
                "delete: removes a folder, optionally with all contents and/or permanently (folderId required). " +
                "get_access_key: retrieves the sharing access key for a folder (folderId required).\n\n" +
                "Examples: \"create\", \"update\", \"delete\", \"get_access_key\"",
                List.of("create", "update", "delete", "get_access_key")))
            .conditionalParam("folderId", "string",
                "Conditional on mode='update', 'delete', or 'get_access_key'. " +
                "UUID of the folder to update, delete, or retrieve the access key for. " +
                "Required when mode='update', 'delete', or 'get_access_key'. " +
                "Provide {waived:true} when mode='create' (not applicable).\n\n" +
                "Examples: \"f93f402c-86d4-11e7-a10d-0ac135e8bacf\", \"a1b2c3d4-0000-0000-0000-000000000099\"")
            .conditionalParam("name", "string",
                "Conditional on mode='create' or 'update'. " +
                "Folder name. Required when mode='create' — cannot be waived. " +
                "Optional when mode='update' — provide {waived:true} if the name should not change. " +
                "Ignored for mode='delete' and 'get_access_key' — provide {waived:true}.\n\n" +
                "Examples: \"Signaling Networks\", \"Cancer Pathways\", \"Shared Datasets\"")
            .property("parent", new McpSchema.InputProperty("string",
                "Optional. UUID of the parent folder under which to create or move this folder. " +
                "Omit to place the folder at root level. " +
                "Applicable when mode='create' or 'update'. " +
                "Cannot form a cycle — moving a folder under one of its own descendants is rejected. " +
                "Ignored for mode='delete' and 'get_access_key'.\n\n" +
                "Examples: \"a1b2c3d4-0000-0000-0000-000000000099\""))
            .property("description", new McpSchema.InputProperty("string",
                "Optional. Human-readable description of the folder. " +
                "Applicable when mode='create' or 'update'. " +
                "Ignored for mode='delete' and 'get_access_key'.\n\n" +
                "Examples: \"Networks related to the Wnt signaling pathway\""))
            .property("force", new McpSchema.InputProperty("boolean",
                "Optional. When true, deletes the folder and all its contents recursively. " +
                "When false or omitted, deletion is rejected if the folder is non-empty. " +
                "Applicable only when mode='delete'. Defaults to false.\n\n" +
                "Examples: false, true"))
            .property("permanent", new McpSchema.InputProperty("boolean",
                "Optional. When true, permanently removes the folder record and all descendant " +
                "records from storage — this cannot be undone. When false or omitted, performs a " +
                "soft deletion (folder is logically marked deleted, data retained). " +
                "Applicable only when mode='delete'. Defaults to false.\n\n" +
                "Examples: false, true"))
            .build());

    private static final ObjectMapper SCHEMA_MAPPER = new ObjectMapper()
            .addMixIn(NdexObjectUpdateStatus.class, NdexObjectUpdateStatusMixIn.class);

    static final String OUTPUT_SCHEMA = McpSchema.toSchemaJson(ManageFolderResponse.class, SCHEMA_MAPPER);

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
    private final ValidationService validationService;

    public ManageFolderTool(ToolsService toolsService) {
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

            User user = (User) httpReq.getAttribute("User");
            if (user == null) return toolsService.unauthorizedResult();

            Map<String, Object> args = req.arguments() != null ? req.arguments() : Map.of();
            String mode = MAPPER.convertValue(args.get("mode"), String.class);

            switch (mode != null ? mode : "") {

                case "create": {
                    CallToolResult err = validationService.validateConditionalParams(
                            "mode", "create", args,
                            List.of(new ValidationService.ConditionalParam(
                                    "name", "the name of the new folder", false)));
                    if (err != null) return err;
                    String name = validationService.unwrapToolInputValue(
                            args.get("name"), String.class);
                    String parent = MAPPER.convertValue(args.get("parent"), String.class);
                    String description = MAPPER.convertValue(args.get("description"), String.class);
                    FolderRequest request = new FolderRequest();
                    request.setName(name);
                    if (parent != null) request.setParent(UUID.fromString(parent));
                    if (description != null) request.setDescription(description);
                    Response response = new FolderServiceV3(httpReq).createFolder(request);
                    NdexObjectUpdateStatus status = MAPPER.readValue(
                            (String) response.getEntity(), NdexObjectUpdateStatus.class);
                    String folderId = status.getUuid().toString();
                    return CallToolResult.builder()
                            .structuredContent(new ManageFolderResponse(
                                    "create", folderId, "Folder created successfully.", null, status))
                            .build();
                }

                case "update": {
                    CallToolResult err = validationService.validateConditionalParams(
                            "mode", "update", args,
                            List.of(
                                new ValidationService.ConditionalParam(
                                        "folderId", "the folder to update", false),
                                new ValidationService.ConditionalParam(
                                        "name", "new folder name", true)));
                    if (err != null) return err;
                    String folderId = validationService.unwrapToolInputValue(
                            args.get("folderId"), String.class);
                    String name = validationService.unwrapToolInputValue(
                            args.get("name"), String.class);
                    String parent = MAPPER.convertValue(args.get("parent"), String.class);
                    String description = MAPPER.convertValue(args.get("description"), String.class);
                    FolderRequest request = new FolderRequest();
                    if (name != null) request.setName(name);
                    if (parent != null) request.setParent(UUID.fromString(parent));
                    if (description != null) request.setDescription(description);
                    new FolderServiceV3(httpReq).updateFolder(request, folderId);
                    return CallToolResult.builder()
                            .structuredContent(new ManageFolderResponse(
                                    "update", folderId, "Folder updated successfully.", null, null))
                            .build();
                }

                case "delete": {
                    CallToolResult err = validationService.validateConditionalParams(
                            "mode", "delete", args,
                            List.of(new ValidationService.ConditionalParam(
                                    "folderId", "the folder to delete", false)));
                    if (err != null) return err;
                    String folderId = validationService.unwrapToolInputValue(
                            args.get("folderId"), String.class);
                    Boolean force = MAPPER.convertValue(args.get("force"), Boolean.class);
                    Boolean permanent = MAPPER.convertValue(args.get("permanent"), Boolean.class);
                    new FolderServiceV3(httpReq).deleteFolder(
                            folderId,
                            force != null && force,
                            permanent != null && permanent);
                    String action = (permanent != null && permanent) ?
                            "permanently deleted" : "soft-deleted";
                    return CallToolResult.builder()
                            .structuredContent(new ManageFolderResponse(
                                    "delete", folderId,
                                    "Folder " + action + " successfully.", null, null))
                            .build();
                }

                case "get_access_key": {
                    CallToolResult err = validationService.validateConditionalParams(
                            "mode", "get_access_key", args,
                            List.of(new ValidationService.ConditionalParam(
                                    "folderId", "the folder whose access key to retrieve", false)));
                    if (err != null) return err;
                    String folderId = validationService.unwrapToolInputValue(
                            args.get("folderId"), String.class);
                    Map<String, String> result =
                            new FolderServiceV3(httpReq).getFolderAccessKey(folderId);
                    String accessKey = result != null ? result.get("accessKey") : null;
                    return CallToolResult.builder()
                            .structuredContent(new ManageFolderResponse(
                                    "get_access_key", folderId, "Access key retrieved.",
                                    accessKey, null))
                            .build();
                }

                default:
                    return CallToolResult.builder()
                            .isError(true)
                            .addTextContent("manage_folder failed: unknown mode '" + mode +
                                    "'. Must be one of: create, update, delete, get_access_key.")
                            .build();
            }

        } catch (UnauthorizedOperationException | SecurityException e) {
            return toolsService.unauthorizedResult();
        } catch (Throwable e) {
            logger.error("manage_folder failed", e);
            return CallToolResult.builder()
                    .isError(true)
                    .addTextContent("manage_folder failed: " + e.getMessage())
                    .build();
        }
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    record ManageFolderResponse(

        @JsonPropertyDescription(
            "The mode that was executed.\n\n" +
            "Examples: \"create\", \"update\", \"delete\", \"get_access_key\"")
        @JsonProperty("mode")
        String mode,

        @JsonPropertyDescription(
            "UUID of the folder that was created, updated, deleted, or whose access key was " +
            "retrieved. Present in all successful responses.\n\n" +
            "Examples: \"f93f402c-86d4-11e7-a10d-0ac135e8bacf\", \"a1b2c3d4-0000-0000-0000-000000000099\"")
        @JsonProperty("folderId")
        String folderId,

        @JsonPropertyDescription(
            "Human-readable confirmation of the operation outcome.\n\n" +
            "Examples: \"Folder created successfully.\", \"Folder soft-deleted successfully.\"")
        @JsonProperty("message")
        String message,

        @JsonPropertyDescription(
            "Present only when mode='get_access_key'. The access key for sharing read access " +
            "to the folder. May be null if access key sharing is not enabled for this folder.")
        @JsonProperty("accessKey")
        String accessKey,

        @JsonPropertyDescription(
            "Present only when mode='create'. Server acknowledgment of the folder creation " +
            "request, including the new folder's UUID.")
        @JsonProperty("createStatus")
        NdexObjectUpdateStatus createStatus
    ) {}
}
