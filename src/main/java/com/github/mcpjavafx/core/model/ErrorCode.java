package com.github.mcpjavafx.core.model;

/**
 * Error codes for MCP UI operations.
 */
public enum ErrorCode {
    MCP_UI_NOT_ENABLED("MCP UI debugging is not enabled"),
    MCP_UI_NO_STAGES("No visible stages found"),
    MCP_UI_NODE_NOT_FOUND("Node not found"),
    MCP_UI_STALE_REF("Node reference is stale (node no longer exists)"),
    MCP_UI_ACTION_FAILED("Action execution failed"),
    MCP_UI_TIMEOUT("Operation timed out waiting for FX thread"),
    MCP_UI_INTERNAL("Internal error");

    private final String defaultMessage;

    ErrorCode(String defaultMessage) {
        this.defaultMessage = defaultMessage;
    }

    public String getDefaultMessage() {
        return defaultMessage;
    }
}
