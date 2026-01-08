package com.github.mcpjavafx.api;

/**
 * Handle to a running MCP JavaFX Debug server.
 * Implements AutoCloseable for proper resource management.
 */
public interface McpJavafxHandle extends AutoCloseable {

    /**
     * Returns the configuration used to create this handle.
     */
    McpJavafxConfig config();

    /**
     * Returns true if the server is currently running.
     */
    boolean isRunning();

    /**
     * Returns the endpoint URL (e.g., "http://127.0.0.1:49321").
     */
    String endpoint();

    /**
     * Returns the authentication token.
     */
    String token();

    /**
     * Stops the server and releases resources.
     */
    @Override
    void close();
}
