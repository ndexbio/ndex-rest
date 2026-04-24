package org.ndexbio.rest.mcp.tools;

import java.util.List;
import java.util.Map;

import jakarta.servlet.http.HttpServletRequest;

import org.ndexbio.model.exceptions.UnauthorizedOperationException;
import org.ndexbio.model.object.NdexPropertyValuePair;
import org.ndexbio.model.object.User;
import org.ndexbio.model.object.network.NetworkSummary;
import org.ndexbio.model.object.network.VisibilityType;
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
 * MCP tool: update_network_profile
 *
 * Delegates to NetworkServiceV2.updateNetworkSummary() via direct in-process call. The
 * HttpServletRequest (with the "User" attribute set by McpBasicAuthFilter) is retrieved
 * from the MCP transport context, so getLoggedInUser() works transparently.
 * Authentication is required — callers without a valid session receive a 401 Unauthorized
 * error response.
 *
 * This is a full-overwrite operation: all five profile fields (name, description, version,
 * visibility, properties) are replaced atomically. Callers that only want to update a subset
 * of fields should first retrieve the current network metadata and include the unchanged
 * values in the request.
 */
public class UpdateNetworkProfileTool {

    private static final Logger logger = LoggerFactory.getLogger(UpdateNetworkProfileTool.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();

    static final String TOOL_NAME = "update_network_profile";

    private static final String TOOL_DESCRIPTION =
        "Overwrite the metadata profile of an existing NDEx network identified by its UUID. " +
        "Replaces the network name, description, version, visibility, and network properties " +
        "atomically in a single operation. This is a full replacement — all five fields are " +
        "written regardless of which fields change, so callers that intend to update only a " +
        "subset of fields should first retrieve the current network metadata to preserve " +
        "existing values. The operation rebuilds the network's CX and CX2 files and " +
        "re-queues a Solr search index update when indexing is enabled. Authentication is " +
        "required and the caller must have write permission on the network; returns a 401 " +
        "Unauthorized error response when unauthenticated or unauthorized. Returns an error " +
        "response if the network does not exist, is locked by another in-progress operation, " +
        "is marked read-only, or visibility is not one of the accepted values.\n\n" +
        "## Examples\n\n" +
        "Example 1 — Rename a network and update its description:\n" +
        "Prompt: 'Rename my NDEx network and update its description'\n" +
        "{\"networkId\": \"f93f402c-86d4-11e7-a10d-0ac135e8bacf\", " +
        "\"name\": \"Wnt Signaling Pathway v2\", \"visibility\": \"PUBLIC\", " +
        "\"description\": \"Updated Wnt signaling interactions from 2024 literature.\"}\n\n" +
        "Example 2 — Make a network private and set a version label:\n" +
        "Prompt: 'Make my NDEx network private and tag it with a version'\n" +
        "{\"networkId\": \"f93f402c-86d4-11e7-a10d-0ac135e8bacf\", " +
        "\"name\": \"Wnt Signaling Pathway\", \"visibility\": \"PRIVATE\", " +
        "\"version\": \"3.1.0\", \"description\": \"Draft revision.\"}\n\n" +
        "Example 3 — Add network properties (key-value metadata) to an existing network:\n" +
        "Prompt: 'Add author and organism metadata to my NDEx network'\n" +
        "{\"networkId\": \"f93f402c-86d4-11e7-a10d-0ac135e8bacf\", " +
        "\"name\": \"Wnt Signaling Pathway\", \"visibility\": \"PUBLIC\", " +
        "\"properties\": [{\"predicateString\": \"author\", \"value\": \"Jane Smith\"}, " +
        "{\"predicateString\": \"organism\", \"value\": \"Homo sapiens\", " +
        "\"dataType\": \"string\"}]}\n\n" +
        "Example 4 — Clear all network properties while updating visibility:\n" +
        "Prompt: 'Make my NDEx network unlisted and clear its properties'\n" +
        "{\"networkId\": \"a1b2c3d4-0000-0000-0000-000000000001\", " +
        "\"name\": \"Internal Pathway Model\", \"visibility\": \"UNLISTED\"}";

    static final String INPUT_SCHEMA = McpSchema.toJson(
        McpSchema.InputSchema.builder()
            .required("networkId", "name", "visibility")
            .property("networkId", new McpSchema.InputProperty("string",
                "Required. UUID of the NDEx network whose profile will be updated. " +
                "The caller must have write permission on this network.\n\n" +
                "Examples: \"f93f402c-86d4-11e7-a10d-0ac135e8bacf\", " +
                "\"9a8f5ab1-3a5c-11e8-a935-0ac135e8bacf\""))
            .property("name", new McpSchema.InputProperty("string",
                "Required. Display name to set for the network. This field is always " +
                "overwritten; provide the existing name to leave it unchanged.\n\n" +
                "Examples: \"Wnt Signaling Pathway v2\", \"EGFR Interaction Network\", " +
                "\"Human Protein Atlas — Kidney\""))
            .property("visibility", new McpSchema.InputProperty("string",
                "Required. Visibility level for the network after the update. PUBLIC makes " +
                "the network fully searchable by any user. PRIVATE restricts access to the " +
                "owner and explicitly shared users. UNLISTED allows access by direct UUID " +
                "only and hides the network from search results.\n\n" +
                "Examples: \"PUBLIC\", \"PRIVATE\", \"UNLISTED\"",
                List.of("PUBLIC", "PRIVATE", "UNLISTED")))
            .property("description", new McpSchema.InputProperty("string",
                "Optional. Free-text description or abstract for the network. Pass null or " +
                "omit to clear the current description. Provide the existing description to " +
                "leave it unchanged.\n\n" +
                "Examples: \"Curated Wnt signaling interactions from KEGG 2024.\", " +
                "\"Protein-protein interactions in Homo sapiens kidney tissue.\""))
            .property("version", new McpSchema.InputProperty("string",
                "Optional. Semantic version or free-form version label for the network. " +
                "Pass null or omit to clear the current version. Provide the existing version " +
                "to leave it unchanged.\n\n" +
                "Examples: \"1.0.0\", \"3.1.2\", \"2024-03\""))
            .property("properties", new McpSchema.InputProperty("array",
                "Optional. Replacement list of network-level key-value metadata properties. " +
                "Supplying this field replaces all existing properties; omit or pass null to " +
                "clear all properties. Each item is a JSON object with: predicateString " +
                "(string, required — the property name), value (string, required — the " +
                "property value), and dataType (string, optional — a CX attribute data type " +
                "label such as \\\"string\\\", \\\"integer\\\", \\\"boolean\\\", \\\"double\\\"; " +
                "defaults to \\\"string\\\" when omitted).\n\n" +
                "Examples: [{\"predicateString\": \"author\", \"value\": \"Jane Smith\"}], " +
                "[{\"predicateString\": \"organism\", \"value\": \"Homo sapiens\", " +
                "\"dataType\": \"string\"}]",
                new McpSchema.InputProperty("object",
                    "A single network property key-value pair with an optional data type label."),
                null))
            .build());

    static final String OUTPUT_SCHEMA = McpSchema.toSchemaJson(UpdateNetworkProfileResponse.class);

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

    public UpdateNetworkProfileTool(ToolsService toolsService) {
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

            UpdateNetworkProfileRequest input =
                    MAPPER.convertValue(req.arguments(), UpdateNetworkProfileRequest.class);

            NetworkSummary summary = new NetworkSummary();
            summary.setName(input.name());
            summary.setDescription(input.description());
            summary.setVersion(input.version());
            summary.setVisibility(VisibilityType.valueOf(input.visibility().toUpperCase()));

            if (input.properties() != null && !input.properties().isEmpty()) {
                List<NdexPropertyValuePair> props = input.properties().stream()
                    .map(p -> {
                        NdexPropertyValuePair nvp =
                                new NdexPropertyValuePair(p.predicateString(), p.value());
                        if (p.dataType() != null && !p.dataType().isBlank())
                            nvp.setDataType(p.dataType());
                        return nvp;
                    })
                    .toList();
                summary.setProperties(props);
            }

            new NetworkServiceV2(httpReq).updateNetworkSummary(input.networkId(), summary);

            return CallToolResult.builder()
                    .structuredContent(new UpdateNetworkProfileResponse(
                        input.networkId(),
                        "Network profile updated successfully."))
                    .build();

        } catch (UnauthorizedOperationException | SecurityException e) {
            return toolsService.unauthorizedResult();
        } catch (Throwable e) {
            logger.error("update_network_profile failed", e);
            return CallToolResult.builder()
                    .isError(true)
                    .addTextContent("update_network_profile failed: " + e.getMessage())
                    .build();
        }
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    record UpdateNetworkProfileResponse(

        @JsonPropertyDescription(
            "UUID of the NDEx network whose profile was updated.\n\n" +
            "Examples: \"f93f402c-86d4-11e7-a10d-0ac135e8bacf\", " +
            "\"9a8f5ab1-3a5c-11e8-a935-0ac135e8bacf\"")
        @JsonProperty("networkId")
        String networkId,

        @JsonPropertyDescription(
            "Human-readable confirmation that the profile update was applied successfully.\n\n" +
            "Examples: \"Network profile updated successfully.\"")
        @JsonProperty("message")
        String message
    ) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    record UpdateNetworkProfileRequest(
        @JsonProperty("networkId")   String networkId,
        @JsonProperty("name")        String name,
        @JsonProperty("description") String description,
        @JsonProperty("version")     String version,
        @JsonProperty("visibility")  String visibility,
        @JsonProperty("properties")  List<NetworkPropertyInput> properties
    ) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    record NetworkPropertyInput(
        @JsonProperty("predicateString") String predicateString,
        @JsonProperty("value")           String value,
        @JsonProperty("dataType")        String dataType
    ) {}
}
