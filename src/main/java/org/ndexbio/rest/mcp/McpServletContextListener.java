package org.ndexbio.rest.mcp;

import java.time.Duration;
import java.util.Map;

import jakarta.servlet.ServletContext;
import jakarta.servlet.ServletContextEvent;
import jakarta.servlet.ServletContextListener;
import jakarta.servlet.ServletRegistration;

import io.modelcontextprotocol.common.McpTransportContext;
import io.modelcontextprotocol.server.McpServer;
import io.modelcontextprotocol.server.McpServerFeatures;
import io.modelcontextprotocol.server.transport.HttpServletStreamableServerTransportProvider;
import io.modelcontextprotocol.spec.McpSchema;

import org.ndexbio.model.object.User;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Programmatically builds and registers the MCP servlet at /mcp/*.
 * 
 * Creates servlet outside of RestEasy/Jax-Rs to avoid streaming http transport getting wrapped by RestEasy's buffering servlet, which breaks streaming responses.
 *
 * Reuses the MCP SDK HttpServletStreamableServerTransportProvider for MCP protocol handling. 
 * However, it has a private constructor (builder-only),
 * so it cannot be declared as a &lt;servlet-class&gt; in web.xml. This listener constructs
 * it via its builder, wires it to an McpServer, then registers it as a servlet using
 * the programmatic ServletContext API.
 *
 * The contextExtractor lambda reads the authenticated NDEx User set by McpOAuthFilter
 * from the servlet request attribute "User" and propagates it into the MCP transport
 * context, making it available to every tool/resource handler invocation.
 */
public class McpServletContextListener implements ServletContextListener {

    private static final Logger logger = LoggerFactory.getLogger(McpServletContextListener.class);

    @Override
    public void contextInitialized(ServletContextEvent sce) {
        ServletContext ctx = sce.getServletContext();

        HttpServletStreamableServerTransportProvider transport =
            HttpServletStreamableServerTransportProvider.builder()
                .mcpEndpoint("/mcp")
                .jsonMapper(new Jackson2McpJsonMapper())
                .keepAliveInterval(Duration.ofSeconds(30))
                .contextExtractor(req -> {
                    // this will enable passing the authenticateduser context to each tool invocation
                    Object user = req.getAttribute("User");
                    return user != null
                        ? McpTransportContext.create(Map.of("ndexUser", user))
                        : McpTransportContext.EMPTY;
                })
                .build();

        McpServer.sync(transport)
            .serverInfo("ndex-mcp", "1.0.0")
            
            .tools(
                // TODO: register NDEx MCP tools here.
                // Example of a tool registration with inline handler function to illustrate
                // how tools can access the authenticated user context during their invocation.
                new McpServerFeatures.SyncToolSpecification(
                    McpSchema.Tool.builder()
                        .name("example-search-networks-tool")
                        .description("Example of a tool like Search NDEx networks")
                        .build(),
                    (exchange, req) -> {
                        // inline example of tool invocation handler function
                        // shows how tool handlers can access the authenticated ndex user from the transport context of a request
                        McpTransportContext transportCtx = exchange.transportContext();
                        User user = (User) transportCtx.get("ndexUser");
                        logger.info("Handling tool request with ndex user: {}", user != null ? user.getUserName() : "anonymous");
                        return McpSchema.CallToolResult.builder().addTextContent("result payload here ...").build();
                    }
                )
            )
            .build();

        ServletRegistration.Dynamic reg = ctx.addServlet("McpServlet", transport);
        if (reg == null) {
            // servlet name already registered — log and continue
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
