package org.ndexbio.rest.mcp.tools;

import java.util.List;
import java.util.Map;

import jakarta.servlet.http.HttpServletRequest;

import org.ndexbio.model.exceptions.UnauthorizedOperationException;
import org.ndexbio.model.object.NdexPropertyValuePair;
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
 * MCP tool: set_network_properties
 *
 * Delegates to NetworkServiceV2.setNetworkProperties() via direct in-process call. The
 * HttpServletRequest (with the "User" attribute set by McpBasicAuthFilter) is retrieved
 * from the MCP transport context, so getLoggedInUser() works transparently.
 * Authentication is required — callers without a valid session receive a 401 Unauthorized
 * error response.
 *
 * This is a full-replacement operation: the supplied list becomes the complete set of
 * network properties. Passing an empty list clears all existing properties.
 */
public class SetNetworkPropertiesTool {

    private static final Logger logger = LoggerFactory.getLogger(SetNetworkPropertiesTool.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();

    static final String TOOL_NAME = "set_network_properties";

    private static final String TOOL_DESCRIPTION =
        "Replace the custom attribute metadata — network properties — of an NDEx network " +
        "identified by its UUID with a caller-supplied list of name-value pairs. " +
        "Use when a user or agent wants to annotate a network with structured metadata such " +
        "as author, organism, publication reference, or any domain-specific key-value attribute. " +
        "The operation fully replaces the existing NetworkAttributes aspect: the provided list " +
        "becomes the complete set of properties, so any property absent from the list is " +
        "removed; supplying an empty list clears all properties. " +
        "The network's CX and CX2 files are rebuilt and a Solr search index update is " +
        "re-queued when indexing is enabled on the server. " +
        "Property predicate names must not be \"name\", \"description\", or \"version\" — " +
        "those fields are managed through the separate network profile update operation; " +
        "supplying a reserved name returns a bad-request error response. " +
        "Authentication is required and the caller must hold write permission on the network; " +
        "callers without a valid session or with insufficient rights receive a 401 Unauthorized " +
        "error response. " +
        "Returns an error response if the network does not exist, is currently locked by " +
        "another operation, is marked read-only, or is otherwise invalid — the error message " +
        "identifies the specific cause.\n\n" +
        "## Examples\n\n" +
        "Example 1 — Annotate a network with author and organism metadata:\n" +
        "Prompt: 'Add author and organism annotations to my NDEx network'\n" +
        "{\"networkId\": \"f93f402c-86d4-11e7-a10d-0ac135e8bacf\", " +
        "\"properties\": [{\"predicateString\": \"author\", \"value\": \"Jane Smith\"}, " +
        "{\"predicateString\": \"organism\", \"value\": \"Homo sapiens\", " +
        "\"dataType\": \"string\"}]}\n\n" +
        "Example 2 — Clear all properties by passing an empty list:\n" +
        "Prompt: 'Remove all metadata annotations from my NDEx network'\n" +
        "{\"networkId\": \"f93f402c-86d4-11e7-a10d-0ac135e8bacf\", \"properties\": []}\n\n" +
        "Example 3 — Set a typed integer property for publication year:\n" +
        "Prompt: 'Tag my NDEx network with a publication year of 2024'\n" +
        "{\"networkId\": \"9a8f5ab1-3a5c-11e8-a935-0ac135e8bacf\", " +
        "\"properties\": [{\"predicateString\": \"publicationYear\", \"value\": \"2024\", " +
        "\"dataType\": \"integer\"}]}\n\n" +
        "Example 4 — Replace all annotations with a new mixed-type set:\n" +
        "Prompt: 'Update all property annotations on my NDEx network to a new set'\n" +
        "{\"networkId\": \"a1b2c3d4-0000-0000-0000-000000000001\", " +
        "\"properties\": [{\"predicateString\": \"source\", \"value\": \"KEGG\"}, " +
        "{\"predicateString\": \"rights\", \"value\": \"CC BY 4.0\"}, " +
        "{\"predicateString\": \"keywords\", " +
        "\"value\": \"[\\\"signaling\\\",\\\"cancer\\\"]\", " +
        "\"dataType\": \"list_of_string\"}]}";

    static final String INPUT_SCHEMA = McpSchema.toJson(
        McpSchema.InputSchema.builder()
            .required("networkId", "properties")
            .property("networkId", new McpSchema.InputProperty("string",
                "Required. UUID of the NDEx network whose properties will be fully replaced. " +
                "The caller must have write permission on the network identified by this UUID.\n\n" +
                "Examples: \"f93f402c-86d4-11e7-a10d-0ac135e8bacf\", " +
                "\"9a8f5ab1-3a5c-11e8-a935-0ac135e8bacf\""))
            .property("properties", new McpSchema.InputProperty("array",
                "Required. Complete replacement list of network-level key-value metadata " +
                "properties. This list fully replaces the existing NetworkAttributes aspect — " +
                "any property not present in this list is removed. Pass an empty array [] to " +
                "clear all existing properties. Each item is a JSON object with: " +
                "predicateString (string — the property name; must not be \"name\", " +
                "\"description\", or \"version\", which are reserved for the network profile " +
                "update operation), value (string — the property value), dataType (string, " +
                "optional — a CX attribute data type label; valid values are \"string\", " +
                "\"boolean\", \"integer\", \"long\", \"double\", \"list_of_string\", " +
                "\"list_of_boolean\", \"list_of_integer\", \"list_of_long\", " +
                "\"list_of_double\"; defaults to \"string\" when omitted), and subNetworkId " +
                "(integer, optional — scopes the property to a specific sub-network element " +
                "when present).\n\n" +
                "Examples: [{\"predicateString\": \"author\", \"value\": \"Jane Smith\"}], []",
                new McpSchema.InputProperty("object",
                    "A single network property entry. Required fields: predicateString " +
                    "(the property name — must not be \"name\", \"description\", or " +
                    "\"version\") and value (the property value as a string). Optional fields: " +
                    "dataType (CX type label such as \"string\", \"integer\", " +
                    "\"list_of_string\"; defaults to \"string\") and subNetworkId (integer " +
                    "sub-network scope)."),
                null))
            .build());

    static final String OUTPUT_SCHEMA = McpSchema.toSchemaJson(SetNetworkPropertiesResponse.class);

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

    public SetNetworkPropertiesTool(ToolsService toolsService) {
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

            SetNetworkPropertiesRequest input =
                    MAPPER.convertValue(req.arguments(), SetNetworkPropertiesRequest.class);

            List<NetworkPropertyInput> inputProps =
                    input.properties() != null ? input.properties() : List.of();

            List<NdexPropertyValuePair> props = inputProps.stream()
                .map(p -> {
                    NdexPropertyValuePair nvp =
                            new NdexPropertyValuePair(p.predicateString(), p.value());
                    if (p.dataType() != null && !p.dataType().isBlank())
                        nvp.setDataType(p.dataType());
                    if (p.subNetworkId() != null)
                        nvp.setSubNetworkId(p.subNetworkId());
                    return nvp;
                })
                .toList();

            int count = new NetworkServiceV2(httpReq).setNetworkProperties(input.networkId(), props);

            return CallToolResult.builder()
                    .structuredContent(new SetNetworkPropertiesResponse(
                        input.networkId(),
                        count,
                        "Network properties set successfully. " + count + " properties written."))
                    .build();

        } catch (UnauthorizedOperationException | SecurityException e) {
            return toolsService.unauthorizedResult();
        } catch (Throwable e) {
            logger.error("set_network_properties failed", e);
            return CallToolResult.builder()
                    .isError(true)
                    .addTextContent("set_network_properties failed: " + e.getMessage())
                    .build();
        }
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    record SetNetworkPropertiesResponse(

        @JsonPropertyDescription(
            "UUID of the NDEx network whose properties were replaced.\n\n" +
            "Examples: \"f93f402c-86d4-11e7-a10d-0ac135e8bacf\", " +
            "\"9a8f5ab1-3a5c-11e8-a935-0ac135e8bacf\"")
        @JsonProperty("networkId")
        String networkId,

        @JsonPropertyDescription(
            "Number of network property entries written to the database in this operation. " +
            "A value of zero indicates all previous properties were cleared by passing an " +
            "empty list.\n\n" +
            "Examples: 0, 3, 12")
        @JsonProperty("propertiesCount")
        Integer propertiesCount,

        @JsonPropertyDescription(
            "Human-readable confirmation that the properties replacement was applied " +
            "successfully.\n\n" +
            "Examples: \"Network properties set successfully. 3 properties written.\", " +
            "\"Network properties set successfully. 0 properties written.\"")
        @JsonProperty("message")
        String message
    ) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    record SetNetworkPropertiesRequest(
        @JsonProperty("networkId")   String                   networkId,
        @JsonProperty("properties")  List<NetworkPropertyInput> properties
    ) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    record NetworkPropertyInput(
        @JsonProperty("predicateString") String predicateString,
        @JsonProperty("value")           String value,
        @JsonProperty("dataType")        String dataType,
        @JsonProperty("subNetworkId")    Long   subNetworkId
    ) {}
}
