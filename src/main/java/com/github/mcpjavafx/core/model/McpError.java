package com.github.mcpjavafx.core.model;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * Standard error response format for MCP operations.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record McpError(
        String code,
        String message,
        Object details) {
    public static McpError of(ErrorCode code) {
        return new McpError(code.name(), code.getDefaultMessage(), null);
    }

    public static McpError of(ErrorCode code, String message) {
        return new McpError(code.name(), message, null);
    }

    public static McpError of(ErrorCode code, String message, Object details) {
        return new McpError(code.name(), message, details);
    }
}
