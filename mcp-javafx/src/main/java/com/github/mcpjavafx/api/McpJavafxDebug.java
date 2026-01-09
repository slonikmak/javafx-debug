package com.github.mcpjavafx.api;

import com.github.mcpjavafx.transport.http.HttpMcpServer;
import com.github.mcpjavafx.util.McpLogger;

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

        var handle = switch (config.transport()) {
            case HTTP_LOCAL -> startHttpServer(config);
        };

        INSTANCE.set(handle);

        McpLogger.info("MCP JavaFX Debug enabled");
        McpLogger.info("Endpoint: " + handle.endpoint());

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

    private static McpJavafxHandle startHttpServer(McpJavafxConfig config) {
        try {
            var server = new HttpMcpServer(config);
            var port = server.start();
            return new HttpHandle(config, server, port);
        } catch (Exception e) {
            throw new RuntimeException("Failed to start MCP HTTP server", e);
        }
    }

    /**
     * Handle implementation for HTTP transport.
     */
    private static class HttpHandle implements McpJavafxHandle {
        private final McpJavafxConfig config;
        private final HttpMcpServer server;
        private final int port;
        private volatile boolean running = true;

        HttpHandle(McpJavafxConfig config, HttpMcpServer server, int port) {
            this.config = config;
            this.server = server;
            this.port = port;
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
        public void close() {
            // No-op
        }
    }
}
