package org.ndexbio.rest.mcp;

import java.util.List;

import io.modelcontextprotocol.server.McpServerFeatures;

import org.ndexbio.rest.mcp.tools.DeleteNetworkTool;
import org.ndexbio.rest.mcp.tools.DownloadNetworkTool;
import org.ndexbio.rest.mcp.tools.GetConnectionStatusTool;
import org.ndexbio.rest.mcp.tools.GetFolderTool;
import org.ndexbio.rest.mcp.tools.GetNetworkSummaryTool;
import org.ndexbio.rest.mcp.tools.GetUserInfoTool;
import org.ndexbio.rest.mcp.tools.GetUserNetworksTool;
import org.ndexbio.rest.mcp.tools.ManageFolderTool;
import org.ndexbio.rest.mcp.tools.RequestNetworkUploadTool;
import org.ndexbio.rest.mcp.tools.SearchNetworkTool;
import org.ndexbio.rest.mcp.tools.SetNetworkPropertiesTool;
import org.ndexbio.rest.mcp.tools.SetNetworkSystemPropertiesTool;
import org.ndexbio.rest.mcp.tools.ShareNetworkTool;
import org.ndexbio.rest.mcp.tools.UpdateNetworkProfileTool;

/**
 * Single source of truth for all 14 registered MCP tool specifications.
 *
 * Used by McpServletContextListener (runtime registration) and McpManifest (build-time
 * schema generation) so both always reflect the same tool set. When adding a new tool,
 * add it here only — both consumers pick it up automatically.
 */
public class McpToolRegistry {

    public List<McpServerFeatures.SyncToolSpecification> buildSpecs() {
        return buildSpecs(new DownloadService());
    }

    public List<McpServerFeatures.SyncToolSpecification> buildSpecs(DownloadService downloadService) {
        ToolsService ts = new ToolsService();
        return List.of(
            new SearchNetworkTool(ts).toSpec(),
            new GetNetworkSummaryTool(ts).toSpec(),
            new RequestNetworkUploadTool(new DefaultConfigLocator()).toSpec(),
            new UpdateNetworkProfileTool(ts).toSpec(),
            new SetNetworkPropertiesTool(ts).toSpec(),
            new SetNetworkSystemPropertiesTool(ts).toSpec(),
            new DeleteNetworkTool(ts).toSpec(),
            new GetFolderTool(ts).toSpec(),
            new ManageFolderTool(ts).toSpec(),
            new DownloadNetworkTool(ts, downloadService).toSpec(),
            new ShareNetworkTool(ts).toSpec(),
            new GetUserNetworksTool(ts).toSpec(),
            new GetConnectionStatusTool(new DefaultConfigLocator()).toSpec(),
            new GetUserInfoTool(ts).toSpec()
        );
    }
}
