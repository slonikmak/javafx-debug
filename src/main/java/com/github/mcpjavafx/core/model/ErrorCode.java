package com.github.mcpjavafx.core.model;

/**
 * Error codes for MCP UI operations.
 */
public enum ErrorCode {
    MCP_UI_INTERNAL("Internal error"),
    MCP_UI_NOT_ENABLED("MCP UI not enabled"),
    MCP_UI_NO_STAGES("No stages found"),
    MCP_UI_NODE_NOT_FOUND("Node not found"),
    MCP_UI_STALE_REF("Stale node reference"),
    MCP_UI_ACTION_FAILED("Action failed"),
    MCP_UI_TIMEOUT("Operation timed out");

    private final String defaultMessage;

    ErrorCode(String defaultMessage) {
        this.defaultMessage = defaultMessage;
    }

    public String getDefaultMessage() {
        return defaultMessage;
    }
}
