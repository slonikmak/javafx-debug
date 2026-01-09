package com.github.mcpjavafx.transport.http;

import com.github.mcpjavafx.api.McpJavafxConfig;
import com.github.mcpjavafx.mcp.McpPromptAdapter;
import com.github.mcpjavafx.mcp.McpToolAdapter;
import com.github.mcpjavafx.util.JsonMapperFactory;
import io.modelcontextprotocol.json.jackson.JacksonMcpJsonMapper;
import io.modelcontextprotocol.server.McpServer;
import io.modelcontextprotocol.server.McpStatelessSyncServer;
import io.modelcontextprotocol.server.transport.HttpServletStatelessServerTransport;
import io.modelcontextprotocol.spec.McpSchema.ServerCapabilities;
import jakarta.servlet.DispatcherType;
import org.eclipse.jetty.ee10.servlet.FilterHolder;
import org.eclipse.jetty.ee10.servlet.ServletContextHandler;
import org.eclipse.jetty.ee10.servlet.ServletHolder;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.server.ServerConnector;

import java.util.EnumSet;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * HTTP-based MCP server using official MCP SDK with Jetty embedded server.
 * 
 * <p>
 * Implements Stateless Streamable HTTP transport as per spec.
 * </p>
 */
public class HttpMcpServer {

    private static final Logger LOG = Logger.getLogger(HttpMcpServer.class.getName());
    private static final String MCP_ENDPOINT = "/mcp";
    private static final String HEALTH_ENDPOINT = "/health";

    private final McpJavafxConfig config;
    private Server jettyServer;
    private McpStatelessSyncServer mcpServer;
    private int actualPort;

    public HttpMcpServer(McpJavafxConfig config) {
        this.config = config;
    }

    /**
     * Starts the HTTP server with MCP SDK transport.
     *
     * @return the actual port the server is listening on
     */
    public int start() throws Exception {
        // Use centralized mapper configuration
        var objectMapper = JsonMapperFactory.createDefault();
        var jsonMapper = new JacksonMcpJsonMapper(objectMapper);

        // Create MCP SDK Stateless Streamable HTTP transport (Servlet)
        var transport = HttpServletStatelessServerTransport.builder()
                .jsonMapper(jsonMapper)
                .messageEndpoint(MCP_ENDPOINT)
                .build();

        // Create MCP Server with SDK
        mcpServer = McpServer.sync(transport)
                .serverInfo("mcp-javafx-debug", "1.0.1")
                .capabilities(ServerCapabilities.builder()
                        .tools(true)
                        .prompts(true)
                        .logging()
                        .build())
                .build();

        // Register tools via adapter
        var toolAdapter = new McpToolAdapter(config);
        toolAdapter.registerTools(mcpServer);

        // Register prompts via adapter
        var promptAdapter = new McpPromptAdapter();
        promptAdapter.registerPrompts(mcpServer);

        // Create Jetty server
        jettyServer = new Server();
        var connector = new ServerConnector(jettyServer);
        connector.setHost(config.bindHost());
        connector.setPort(config.port());
        jettyServer.addConnector(connector);

        var context = new ServletContextHandler(ServletContextHandler.NO_SESSIONS);
        context.setContextPath("/");

        // Unified body caching filter for all subsequent filters
        var cachedBodyFilterHolder = new FilterHolder(new CachedBodyFilter());
        context.addFilter(cachedBodyFilterHolder, MCP_ENDPOINT + "/*", EnumSet.of(DispatcherType.REQUEST));

        // Add MCP servlet from SDK transport
        var mcpServletHolder = new ServletHolder("mcp", transport);
        context.addServlet(mcpServletHolder, MCP_ENDPOINT + "/*");

        // Add health endpoint servlet
        var healthServletHolder = new ServletHolder("health", new HealthServlet(config));
        context.addServlet(healthServletHolder, HEALTH_ENDPOINT);

        // Log incoming MCP requests
        var logFilterHolder = new FilterHolder(new RequestLoggingFilter(config));
        context.addFilter(logFilterHolder, MCP_ENDPOINT + "/*", EnumSet.of(DispatcherType.REQUEST));

        // Sanitize initialize payloads for older SDK compatibility
        var sanitizeFilterHolder = new FilterHolder(new RequestSanitizingFilter());
        context.addFilter(sanitizeFilterHolder, MCP_ENDPOINT + "/*", EnumSet.of(DispatcherType.REQUEST));

        // Handle logging/setLevel for clients before SDK transport
        var loggingFilterHolder = new FilterHolder(new LoggingSetLevelFilter());
        context.addFilter(loggingFilterHolder, MCP_ENDPOINT + "/*", EnumSet.of(DispatcherType.REQUEST));

        jettyServer.setHandler(context);
        jettyServer.start();

        actualPort = connector.getLocalPort();

        LOG.info("MCP JavaFX Debug HTTP server started on http://" +
                config.bindHost() + ":" + actualPort);

        return actualPort;
    }

    /**
     * Stops the HTTP server.
     */
    public void stop() {
        try {
            if (mcpServer != null) {
                mcpServer.close();
            }
            if (jettyServer != null) {
                jettyServer.stop();
                LOG.info("MCP JavaFX Debug HTTP server stopped");
            }
        } catch (Exception e) {
            LOG.log(Level.WARNING, "Error stopping MCP server", e);
        }
    }

    /**
     * Returns the actual port.
     */
    public int getPort() {
        return actualPort;
    }
}
