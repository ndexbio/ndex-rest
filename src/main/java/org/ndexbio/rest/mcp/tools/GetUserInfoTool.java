package org.ndexbio.rest.mcp.tools;

import java.sql.Timestamp;
import java.util.Map;
import java.util.UUID;

import jakarta.servlet.http.HttpServletRequest;

import org.ndexbio.model.exceptions.UnauthorizedOperationException;
import org.ndexbio.model.object.User;
import org.ndexbio.rest.mcp.McpSchema;
import org.ndexbio.rest.mcp.ToolsService;
import org.ndexbio.rest.services.UserServiceV2;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import io.modelcontextprotocol.server.McpServerFeatures;
import io.modelcontextprotocol.server.McpSyncServerExchange;
import io.modelcontextprotocol.spec.McpSchema.CallToolRequest;
import io.modelcontextprotocol.spec.McpSchema.CallToolResult;
import io.modelcontextprotocol.spec.McpSchema.JsonSchema;
import io.modelcontextprotocol.spec.McpSchema.Tool;

/**
 * MCP tool: get_user_info
 *
 * Returns the authenticated user's profile (all public fields except password) and
 * their total network count. Delegates the network count to UserServiceV2, which
 * enforces that the caller may only query their own account. Authentication is
 * required; anonymous callers receive a 401 Unauthorized error.
 */
public class GetUserInfoTool {

    private static final Logger logger = LoggerFactory.getLogger(GetUserInfoTool.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();

    static final String TOOL_NAME = "get_user_info";

    private static final String TOOL_DESCRIPTION =
        "Return the profile of the currently authenticated user and their total network count. " +
        "Authentication is required; anonymous callers receive a 401 Unauthorized error. " +
        "Use to inspect the caller's account details or to retrieve their user UUID for " +
        "other API operations such as network sharing.\n\n" +
        "## Examples\n\n" +
        "Example 1 — Get current user profile and network count:\n" +
        "Prompt: 'Show me my user info'\n" +
        "{}\n\n" +
        "Example 2 — Look up my user UUID:\n" +
        "Prompt: 'What is my user UUID?'\n" +
        "{}";

    static final String INPUT_SCHEMA = McpSchema.toJson(
        McpSchema.InputSchema.builder().build());

    static final String OUTPUT_SCHEMA = McpSchema.toSchemaJson(UserInfoResponse.class);

    private static final Tool TOOL;
    static {
        try {
            TOOL = Tool.builder()
                .name(TOOL_NAME)
                .description(TOOL_DESCRIPTION)
                .inputSchema(MAPPER.readValue(INPUT_SCHEMA, JsonSchema.class))
                .outputSchema(MAPPER.readValue(OUTPUT_SCHEMA,
                    new TypeReference<Map<String, Object>>() {}))
                .build();
        } catch (Exception e) {
            throw new ExceptionInInitializerError(e);
        }
    }

    private final ToolsService toolsService;

    public GetUserInfoTool(ToolsService toolsService) {
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

            String userId = user.getExternalId().toString();
            Map<String, Integer> counts =
                    new UserServiceV2(httpReq).getNumNetworksForMyAccountPage(userId);
            int networkCount = counts.getOrDefault("networkCount", 0);

            return CallToolResult.builder()
                    .structuredContent(new UserInfoResponse(new UserView(user), networkCount))
                    .build();

        } catch (UnauthorizedOperationException | SecurityException e) {
            return toolsService.unauthorizedResult();
        } catch (Throwable e) {
            logger.error("get_user_info failed", e);
            return CallToolResult.builder()
                    .isError(true)
                    .addTextContent("get_user_info failed: " + e.getMessage())
                    .build();
        }
    }

    // ── Nested response types ────────────────────────────────────────────────

    @JsonInclude(JsonInclude.Include.NON_NULL)
    record UserInfoResponse(

        @JsonPropertyDescription(
            "Profile of the authenticated user. Includes account identifiers, display fields, " +
            "and storage quota. The password field is never included.\n\n" +
            "Examples: see nested UserView schema")
        @JsonProperty("user")
        UserView user,

        @JsonPropertyDescription(
            "Total number of networks on the user's account page, including networks they own " +
            "and networks explicitly shared with them. " +
            "Returns 0 if the user has no networks.\n\n" +
            "Examples: 0, 5, 42")
        @JsonProperty("network_count")
        int networkCount

    ) {}

    @JsonInclude(JsonInclude.Include.NON_NULL)
    record UserView(

        @JsonPropertyDescription(
            "Unique UUID that identifies this user account across the NDEx platform. " +
            "Use this value when sharing networks or constructing REST API calls that require a userId.\n\n" +
            "Examples: \"3f0adbe2-8d45-11e9-b56a-0660b7976219\"")
        @JsonProperty("externalId")
        UUID externalId,

        @JsonPropertyDescription(
            "Server timestamp when this account was created, as Unix epoch milliseconds.\n\n" +
            "Examples: 1560000000000")
        @JsonProperty("creationTime")
        Timestamp creationTime,

        @JsonPropertyDescription(
            "Server timestamp of the most recent modification to this account, as Unix epoch milliseconds.\n\n" +
            "Examples: 1700000000000")
        @JsonProperty("modificationTime")
        Timestamp modificationTime,

        @JsonPropertyDescription(
            "True if this account has been soft-deleted. Active accounts always return false.")
        @JsonProperty("isDeleted")
        Boolean isDeleted,

        @JsonPropertyDescription(
            "URL of the user's profile image, or null if not set.\n\n" +
            "Examples: \"https://cdn.example.com/avatars/alice.png\"")
        @JsonProperty("image")
        String image,

        @JsonPropertyDescription(
            "Free-text description or biography provided by the user, or null if not set.")
        @JsonProperty("description")
        String description,

        @JsonPropertyDescription(
            "User's personal or institutional website URL, or null if not set.\n\n" +
            "Examples: \"https://www.example.org/lab\"")
        @JsonProperty("website")
        String website,

        @JsonPropertyDescription(
            "Arbitrary key-value properties attached to this account. May be empty.")
        @JsonProperty("properties")
        Map<String, Object> properties,

        @JsonPropertyDescription(
            "NDEx login username for this account. Used for Basic Auth and display purposes.\n\n" +
            "Examples: \"jsmith\", \"ndextest\"")
        @JsonProperty("userName")
        String userName,

        @JsonPropertyDescription(
            "User's first name, or null if not provided.\n\n" +
            "Examples: \"Jane\"")
        @JsonProperty("firstName")
        String firstName,

        @JsonPropertyDescription(
            "User's last name, or null if not provided.\n\n" +
            "Examples: \"Smith\"")
        @JsonProperty("lastName")
        String lastName,

        @JsonPropertyDescription(
            "User's email address. Used for notifications and account recovery.\n\n" +
            "Examples: \"jsmith@example.com\"")
        @JsonProperty("emailAddress")
        String emailAddress,

        @JsonPropertyDescription(
            "Display name shown in the NDEx UI, or null if not set. May differ from userName.\n\n" +
            "Examples: \"Jane Smith\"")
        @JsonProperty("displayName")
        String displayName,

        @JsonPropertyDescription(
            "True if this is an individual user account; false for organization accounts.")
        @JsonProperty("isIndividual")
        Boolean isIndividual,

        @JsonPropertyDescription(
            "True if the user's email address has been verified.")
        @JsonProperty("isVerified")
        Boolean isVerified,

        @JsonPropertyDescription(
            "Total disk storage quota allocated to this account, in bytes. Null if not set.\n\n" +
            "Examples: 10737418240")
        @JsonProperty("diskQuota")
        Long diskQuota,

        @JsonPropertyDescription(
            "Disk storage currently used by this account, in bytes. Null if not set.\n\n" +
            "Examples: 524288000")
        @JsonProperty("diskUsed")
        Long diskUsed

    ) {
        UserView(User user) {
            this(
                user.getExternalId(),
                user.getCreationTime(),
                user.getModificationTime(),
                user.getIsDeleted(),
                user.getImage(),
                user.getDescription(),
                user.getWebsite(),
                user.getProperties(),
                user.getUserName(),
                user.getFirstName(),
                user.getLastName(),
                user.getEmailAddress(),
                user.getDisplayName(),
                user.getIsIndividual(),
                user.getIsVerified(),
                user.getDiskQuota(),
                user.getDiskUsed()
            );
        }
    }
}
