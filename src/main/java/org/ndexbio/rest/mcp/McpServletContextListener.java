package org.ndexbio.rest.mcp;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;

import jakarta.servlet.ServletContext;
import jakarta.servlet.ServletContextEvent;
import jakarta.servlet.ServletContextListener;
import jakarta.servlet.ServletRegistration;

import io.modelcontextprotocol.common.McpTransportContext;
import io.modelcontextprotocol.server.McpServer;
import io.modelcontextprotocol.server.McpServerFeatures;
import io.modelcontextprotocol.server.transport.HttpServletStreamableServerTransportProvider;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Programmatically builds and registers the MCP servlet at /mcp/*.
 *
 * Creates the servlet outside RESTEasy/JAX-RS to avoid streaming HTTP transport being wrapped
 * by RESTEasy's buffering servlet, which breaks streaming responses.
 *
 * Reuses the MCP SDK HttpServletStreamableServerTransportProvider for MCP protocol handling.
 * However, it has a private constructor (builder-only), so it cannot be declared as a
 * &lt;servlet-class&gt; in web.xml. This listener constructs it via its builder, wires it to an
 * McpServer, then registers it as a servlet using the programmatic ServletContext API.
 *
 * The contextExtractor lambda reads the HttpServletRequest (always) and the optional NDEx User
 * set by McpBasicAuthFilter from the servlet request attribute "User", propagating both into
 * the MCP transport context and making them available to every tool handler invocation.
 */
public class McpServletContextListener implements ServletContextListener {

    private static final Logger logger = LoggerFactory.getLogger(McpServletContextListener.class);

    @Override
    public void contextInitialized(ServletContextEvent sce) {
        ServletContext ctx = sce.getServletContext();

        HttpServletStreamableServerTransportProvider transport =
            HttpServletStreamableServerTransportProvider.builder()
                .mcpEndpoint("/mcp")
                .keepAliveInterval(Duration.ofSeconds(30))
                .contextExtractor(req -> {
                    Map<String, Object> context = new HashMap<>();
                    context.put("ndexRequest", req);  // always — needed by tool handlers
                    Object user = req.getAttribute("User");
                    if (user != null) context.put("ndexUser", user);
                    return McpTransportContext.create(context);
                })
                .build();

        UploadService uploadService = new UploadService();

        McpServerFeatures.SyncToolSpecification[] toolSpecs =
            new McpToolRegistry().buildSpecs(uploadService)
                .toArray(new McpServerFeatures.SyncToolSpecification[0]);

        McpServer.sync(transport)
            .serverInfo("ndex-mcp", "1.0.0")
            .tools(toolSpecs)
            .build();

        ServletRegistration.Dynamic manifestReg =
            ctx.addServlet("McpManifestServlet", new McpManifestServlet());
        if (manifestReg == null) {
            logger.warn("McpServletContextListener: 'McpManifestServlet' already registered; " +
                        "skipping duplicate registration.");
        } else {
            manifestReg.addMapping("/mcp/manifest");
        }

        ServletRegistration.Dynamic reg = ctx.addServlet("McpServlet", transport);
        if (reg == null) {
            logger.warn("McpServletContextListener: 'McpServlet' already registered; " +
                        "skipping duplicate registration.");
            return;
        }
        reg.addMapping("/mcp/*");
        reg.setAsyncSupported(true);

        logger.info("McpServletContextListener: MCP servlet registered at /mcp/*");
    }

    @Override
    public void contextDestroyed(ServletContextEvent sce) {
        // transport shuts down with the container; no explicit teardown needed
    }
}
