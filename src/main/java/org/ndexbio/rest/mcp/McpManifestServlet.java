package org.ndexbio.rest.mcp;

import java.io.IOException;
import java.io.InputStream;

import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Serves the pre-generated MCP API manifest as Markdown.
 *
 * <p>{@code McpManifest.md} is generated during the Maven build by {@link McpManifest#main}
 * and bundled as a classpath resource at {@code /McpManifest.md}. This servlet streams it
 * as {@code text/markdown}.
 *
 * <p>Registered at exact path {@code /mcp/manifest} in {@link McpServletContextListener},
 * which takes precedence over the {@code /mcp/*} wildcard mapping used by the MCP transport
 * servlet per the Jakarta Servlet spec.
 *
 * <p>{@link McpAuthFilter} is mapped to {@code /mcp/*} but bypasses auth for
 * {@code /mcp/manifest} so the manifest is publicly accessible without credentials.
 */
public class McpManifestServlet extends HttpServlet {

    private static final Logger logger = LoggerFactory.getLogger(McpManifestServlet.class);

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        try (InputStream is = openManifestStream()) {
            if (is == null) {
                logger.warn("McpManifest.md not found on classpath");
                resp.setStatus(HttpServletResponse.SC_NOT_FOUND);
                resp.setContentType("text/plain;charset=UTF-8");
                resp.getWriter().write("McpManifest.md not found on classpath");
                return;
            }
            resp.setContentType("text/markdown;charset=UTF-8");
            resp.getOutputStream().write(is.readAllBytes());
        } catch (IOException e) {
            logger.error("Failed to read McpManifest.md", e);
            resp.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            resp.getWriter().write("Failed to read manifest: " + e.getMessage());
        }
    }

    /**
     * Opens the manifest classpath resource. Package-private to allow override in tests.
     */
    InputStream openManifestStream() {
        return McpManifestServlet.class.getResourceAsStream("/McpManifest.md");
    }
}
