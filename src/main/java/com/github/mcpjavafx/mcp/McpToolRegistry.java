package com.github.mcpjavafx.mcp;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.github.mcpjavafx.api.McpJavafxConfig;
import com.github.mcpjavafx.core.actions.ActionExecutor;
import com.github.mcpjavafx.core.capture.SceneGraphSnapshotter;
import com.github.mcpjavafx.core.fx.Fx;
import com.github.mcpjavafx.core.model.*;
import com.github.mcpjavafx.core.query.NodeQueryService;
import com.github.mcpjavafx.core.query.QueryPredicate;

import java.util.*;

/**
 * Registry and executor for MCP tools.
 */
public class McpToolRegistry {

    private final ObjectMapper mapper;
    private final McpJavafxConfig config;
    private final UiToolsService toolsService;

    public McpToolRegistry(McpJavafxConfig config) {
        this.config = config;
        this.mapper = createMapper();
        this.toolsService = new UiToolsService(config, mapper);
    }

    private ObjectMapper createMapper() {
        var mapper = new ObjectMapper();
        mapper.registerModule(new JavaTimeModule());
        mapper.setSerializationInclusion(JsonInclude.Include.NON_NULL);
        mapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        return mapper;
    }

    /**
     * Returns list of available tools.
     */
    public List<String> getToolNames() {
        var tools = new ArrayList<>(List.of(
                "ui.getSnapshot",
                "ui.query",
                "ui.getNode"));

        if (config.allowActions()) {
            tools.add("ui.perform");
            tools.add("ui.screenshot");
        }

        return tools;
    }

    /**
     * Executes a tool and returns the result as JSON.
     */
    public String execute(String tool, JsonNode input) {
        if (!config.enabled()) {
            return errorResponse(ErrorCode.MCP_UI_NOT_ENABLED);
        }

        try {
            Object result = switch (tool) {
                case "ui.getSnapshot" -> toolsService.executeGetSnapshot(input);
                case "ui.query" -> toolsService.executeQuery(input);
                case "ui.getNode" -> toolsService.executeGetNode(input);
                case "ui.perform" -> toolsService.executePerform(input);
                case "ui.screenshot" -> toolsService.executeScreenshot(input);
                default -> McpError.of(ErrorCode.MCP_UI_INTERNAL, "Unknown tool: " + tool);
            };

            if (result instanceof McpError err) {
                ErrorCode code;
                try {
                    code = ErrorCode.valueOf(err.code());
                } catch (Exception e) {
                    code = ErrorCode.MCP_UI_INTERNAL;
                }
                return errorResponse(code, err.message());
            }
            return successResponse(result);
        } catch (Exception e) {
            Object err = toolsService.wrapException(e);
            if (err instanceof McpError me) {
                ErrorCode code;
                try {
                    code = ErrorCode.valueOf(me.code());
                } catch (Exception ex) {
                    code = ErrorCode.MCP_UI_INTERNAL;
                }
                return errorResponse(code, me.message());
            }
            return errorResponse(ErrorCode.MCP_UI_INTERNAL, e.getMessage());
        }
    }

    private String successResponse(Object data) {
        try {
            var root = mapper.createObjectNode();
            root.set("result", mapper.valueToTree(data));
            return mapper.writeValueAsString(root);
        } catch (Exception e) {
            return errorResponse(ErrorCode.MCP_UI_INTERNAL, "Serialization error: " + e.getMessage());
        }
    }

    private String errorResponse(ErrorCode code) {
        return errorResponse(code, null);
    }

    private String errorResponse(ErrorCode code, String message) {
        try {
            var root = mapper.createObjectNode();
            var error = root.putObject("error");
            error.put("code", code.name());
            if (message != null) {
                error.put("message", message);
            }
            return mapper.writeValueAsString(root);
        } catch (Exception e) {
            return "{\"error\":{\"code\":\"MCP_UI_INTERNAL\"}}";
        }
    }
}
