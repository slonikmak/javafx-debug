package com.github.mcpjavafx.api;

import com.github.mcpjavafx.transport.http.HttpMcpServer;
import com.github.mcpjavafx.transport.stdio.StdioMcpBridge;
import com.github.mcpjavafx.util.McpLogger;

import java.io.IOException;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Main entry point for MCP JavaFX Debug library.
 * 
 * <p>
 * Usage:
 * </p>
 * 
 * <pre>
 * // Option 1: Explicit configuration
 * var handle = McpJavafxDebug.install(McpJavafxConfig.builder()
 *         .enabled(true)
 *         .port(8080)
 *         .build());
 * 
 * // Option 2: System properties
 * // Run with -Dmcp.ui=true
 * var handle = McpJavafxDebug.startFromSystemProperties();
 * </pre>
 */
public final class McpJavafxDebug {

    private static final AtomicReference<McpJavafxHandle> INSTANCE = new AtomicReference<>();

    private McpJavafxDebug() {
    }

    /**
     * Installs MCP JavaFX Debug server with the given configuration.
     *
     * @param config configuration
     * @return handle to the running server
     * @throws IllegalStateException if a server is already running
     */
    public static McpJavafxHandle install(McpJavafxConfig config) {
        if (!config.enabled()) {
            McpLogger.info("MCP JavaFX Debug is disabled");
            return new DisabledHandle(config);
        }

        var existing = INSTANCE.get();
        if (existing != null && existing.isRunning()) {
            throw new IllegalStateException("MCP JavaFX Debug server is already running");
        }

        var token = config.effectiveToken();
        var handle = switch (config.transport()) {
            case HTTP_LOCAL -> startHttpServer(config, token);
            case STDIO -> startStdioServer(config);
        };

        INSTANCE.set(handle);

        if (config.transport() == Transport.HTTP_LOCAL) {
            McpLogger.info("MCP JavaFX Debug enabled");
            McpLogger.info("Endpoint: " + handle.endpoint());
            McpLogger.info("Token: " + token);
        } else {
            McpLogger.toStderr("MCP JavaFX Debug enabled (STDIO)");
        }

        return handle;
    }

    /**
     * Starts MCP JavaFX Debug server using system properties.
     *
     * <p>
     * System properties:
     * </p>
     * <ul>
     * <li>mcp.ui - true/false to enable/disable</li>
     * <li>mcp.transport - http (default)</li>
     * <li>mcp.bind - bind host (default: 127.0.0.1)</li>
     * <li>mcp.port - port (default: 0 = auto)</li>
     * <li>mcp.token - authentication token</li>
     * <li>mcp.allowActions - true/false</li>
     * </ul>
     *
     * @return handle to the running server
     */
    public static McpJavafxHandle startFromSystemProperties() {
        var config = McpJavafxConfig.fromSystemProperties();
        return install(config);
    }

    /**
     * Returns the current running instance, if any.
     */
    public static McpJavafxHandle getInstance() {
        return INSTANCE.get();
    }

    private static McpJavafxHandle startHttpServer(McpJavafxConfig config, String token) {
        try {
            var server = new HttpMcpServer(config, token);
            var port = server.start();
            return new HttpHandle(config, server, port, token);
        } catch (IOException e) {
            throw new RuntimeException("Failed to start MCP HTTP server", e);
        }
    }

    private static McpJavafxHandle startStdioServer(McpJavafxConfig config) {
        var bridge = new StdioMcpBridge(config);
        var handle = new StdioHandle(config, bridge);

        // Start STDIO server in background thread (it blocks on stdin)
        Thread.ofVirtual().name("mcp-stdio-server").start(() -> {
            try {
                bridge.start();
            } catch (Exception e) {
                McpLogger.toStderr("STDIO server error: " + e.getMessage());
            }
        });

        return handle;
    }

    /**
     * Handle implementation for HTTP transport.
     */
    private static class HttpHandle implements McpJavafxHandle {
        private final McpJavafxConfig config;
        private final HttpMcpServer server;
        private final int port;
        private final String token;
        private volatile boolean running = true;

        HttpHandle(McpJavafxConfig config, HttpMcpServer server, int port, String token) {
            this.config = config;
            this.server = server;
            this.port = port;
            this.token = token;
        }

        @Override
        public McpJavafxConfig config() {
            return config;
        }

        @Override
        public boolean isRunning() {
            return running;
        }

        @Override
        public String endpoint() {
            return "http://" + config.bindHost() + ":" + port;
        }

        @Override
        public String token() {
            return token;
        }

        @Override
        public void close() {
            if (running) {
                running = false;
                server.stop();
                INSTANCE.compareAndSet(this, null);
                McpLogger.info("MCP JavaFX Debug server stopped");
            }
        }
    }

    /**
     * Handle implementation when server is disabled.
     */
    private static class DisabledHandle implements McpJavafxHandle {
        private final McpJavafxConfig config;

        DisabledHandle(McpJavafxConfig config) {
            this.config = config;
        }

        @Override
        public McpJavafxConfig config() {
            return config;
        }

        @Override
        public boolean isRunning() {
            return false;
        }

        @Override
        public String endpoint() {
            return null;
        }

        @Override
        public String token() {
            return null;
        }

        @Override
        public void close() {
            // No-op
        }
    }

    /**
     * Handle implementation for STDIO transport.
     */
    private static class StdioHandle implements McpJavafxHandle {
        private final McpJavafxConfig config;
        private final StdioMcpBridge bridge;

        StdioHandle(McpJavafxConfig config, StdioMcpBridge bridge) {
            this.config = config;
            this.bridge = bridge;
        }

        @Override
        public McpJavafxConfig config() {
            return config;
        }

        @Override
        public boolean isRunning() {
            return bridge.isRunning();
        }

        @Override
        public String endpoint() {
            return "stdio";
        }

        @Override
        public String token() {
            return null; // STDIO does not use tokens
        }

        @Override
        public void close() {
            bridge.close();
            INSTANCE.compareAndSet(this, null);
        }
    }
}
