package com.github.mcpjavafx.util;

import java.util.logging.Logger;

/**
 * Simple logging utility for MCP JavaFX Debug.
 */
public final class McpLogger {

    private static final Logger LOG = Logger.getLogger("mcp-javafx-debug");

    private McpLogger() {
    }

    public static void info(String message) {
        LOG.info(message);
        System.out.println("[MCP-JAVAFX] " + message);
    }

    public static void warn(String message) {
        LOG.warning(message);
        System.err.println("[MCP-JAVAFX WARN] " + message);
    }

    public static void error(String message, Throwable t) {
        LOG.severe(message + ": " + t.getMessage());
        System.err.println("[MCP-JAVAFX ERROR] " + message + ": " + t.getMessage());
    }

    /**
     * Logs to stderr only, for STDIO transport where stdout is used for JSON-RPC.
     */
    public static void toStderr(String message) {
        System.err.println("[MCP-JAVAFX] " + message);
    }
}
