package org.ndexbio.rest.mcp;

import java.util.List;

import io.modelcontextprotocol.server.McpServerFeatures;

import org.ndexbio.rest.mcp.tools.GetNetworkSummaryTool;
import org.ndexbio.rest.mcp.tools.SearchNetworkTool;

/**
 * Single source of truth for all registered MCP tool specifications.
 *
 * Used by McpServletContextListener (runtime registration) and McpManifest (build-time
 * schema generation) so both always reflect the same tool set. When adding a new tool,
 * add it here only — both consumers pick it up automatically.
 */
public class McpToolRegistry {

    public List<McpServerFeatures.SyncToolSpecification> buildSpecs() {
        ToolsService ts = new ToolsService();
        return List.of(
            new SearchNetworkTool(ts).toSpec(),
            new GetNetworkSummaryTool(ts).toSpec()
        );
    }
}
