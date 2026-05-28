package org.ndexbio.rest.mcp.tools;

import java.net.URI;
import java.util.Map;

import org.ndexbio.model.object.User;
import org.ndexbio.rest.mcp.ConfigLocator;
import org.ndexbio.rest.mcp.McpSchema;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

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
 * MCP tool: get_connection_status
 *
 * Returns the current connection context without modifying any server state.
 * Works for both anonymous and authenticated callers: anonymous callers receive
 * {@code authenticated: false} and {@code username: "anonymous"}; authenticated
 * callers receive their login username and {@code authenticated: true}.
 * Use this tool to confirm which NDEx server the agent is connected to and
 * whether valid credentials are present before issuing write operations.
 */
public class GetConnectionStatusTool {

    private static final Logger logger = LoggerFactory.getLogger(GetConnectionStatusTool.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();

    static final String TOOL_NAME = "get_connection_status";

    private static final String TOOL_DESCRIPTION =
        "Return the active connection context: the NDEx server hostname, the authenticated " +
        "user's account name, and whether the caller is authenticated. " +
        "Use before write operations to confirm the target server and that valid credentials " +
        "are present. Read-only; works for anonymous and authenticated callers alike.\n\n" +
        "## Examples\n\n" +
        "Example 1 — Confirm connection before a write:\n" +
        "Prompt: 'Am I connected and logged in?'\n" +
        "{}\n\n" +
        "Example 2 — Identify the target server:\n" +
        "Prompt: 'Which NDEx server am I talking to?'\n" +
        "{}\n\n" +
        "Example 3 — Check the current user:\n" +
        "Prompt: 'What account is active?'\n" +
        "{}";

    static final String INPUT_SCHEMA = McpSchema.toJson(
        McpSchema.InputSchema.builder().build());

    static final String OUTPUT_SCHEMA =
        McpSchema.toSchemaJson(ConnectionStatusResponse.class);

    private static final Tool TOOL;
    static {
        try {
            TOOL = Tool.builder()
                .name(TOOL_NAME)
                .description(TOOL_DESCRIPTION)
                .inputSchema(MAPPER.readValue(INPUT_SCHEMA, JsonSchema.class))
                .outputSchema(MAPPER.readValue(OUTPUT_SCHEMA, new TypeReference<Map<String, Object>>() {}))
                .build();
        } catch (Exception e) {
            throw new ExceptionInInitializerError(e);
        }
    }

    private final ConfigLocator configLocator;

    public GetConnectionStatusTool(ConfigLocator configLocator) {
        this.configLocator = configLocator;
    }

    public McpServerFeatures.SyncToolSpecification toSpec() {
        return McpServerFeatures.SyncToolSpecification.builder()
                .tool(TOOL)
                .callHandler(this::handle)
                .build();
    }

    private CallToolResult handle(McpSyncServerExchange exchange, CallToolRequest req) {
        try {
            User user = (User) exchange.transportContext().get("ndexUser");
            String server = extractHost(configLocator.getHostURI());
            boolean authenticated = user != null;
            String username = authenticated
                ? (user.getUserName() != null ? user.getUserName() : "anonymous")
                : "anonymous";
            return CallToolResult.builder()
                    .structuredContent(new ConnectionStatusResponse(server, username, authenticated))
                    .build();
        } catch (Throwable e) {
            logger.error("get_connection_status failed", e);
            return CallToolResult.builder()
                    .isError(true)
                    .addTextContent("get_connection_status failed: " + e.getMessage())
                    .build();
        }
    }

    /**
     * Extracts the hostname from a full URI string.
     * Returns {@code "localhost"} if {@code hostUri} is null, blank, malformed, or
     * has no host component (e.g. a relative path).
     * Package-private to allow direct unit testing.
     */
    static String extractHost(String hostUri) {
        if (hostUri == null) return "localhost";
        try {
            String h = new URI(hostUri).getHost();
            return (h != null && !h.isBlank()) ? h : "localhost";
        } catch (Exception e) {
            return "localhost";
        }
    }

    private record ConnectionStatusResponse(

        @JsonPropertyDescription(
            "Hostname of the NDEx server parsed from its configured base URI. " +
            "Defaults to \"localhost\" when no HostURI is configured.\n\n" +
            "Examples: \"www.ndexbio.org\", \"dev.ndexbio.org\", \"localhost\"")
        @JsonProperty("server")
        String server,

        @JsonPropertyDescription(
            "Account name of the authenticated caller. " +
            "Returns \"anonymous\" when no valid credentials are present or when the " +
            "authenticated user has no username set.\n\n" +
            "Examples: \"ndextest\", \"jsmith\", \"anonymous\"")
        @JsonProperty("username")
        String username,

        @JsonPropertyDescription(
            "True when the request carries credentials that have been validated by the server. " +
            "False for anonymous (unauthenticated) callers.\n\n" +
            "Examples: true, false")
        @JsonProperty("authenticated")
        boolean authenticated

    ) {}
}
