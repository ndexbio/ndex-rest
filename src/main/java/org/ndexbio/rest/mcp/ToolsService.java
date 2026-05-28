package org.ndexbio.rest.mcp;

import io.modelcontextprotocol.spec.McpSchema;

/**
 * Injectable service providing common helpers shared across MCP tool handlers.
 * Non-static for mockability in unit tests.
 */
public class ToolsService {

    /** Returns a standardised 401 Unauthorized CallToolResult. No stack trace exposed. */
    public McpSchema.CallToolResult unauthorizedResult() {
        return McpSchema.CallToolResult.builder()
                .isError(true)
                .addTextContent("401 Unauthorized")
                .build();
    }
}
