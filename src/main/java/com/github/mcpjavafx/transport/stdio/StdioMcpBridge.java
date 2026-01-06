package com.github.mcpjavafx.transport.stdio;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.github.mcpjavafx.api.McpJavafxConfig;
import com.github.mcpjavafx.mcp.McpToolRegistry;
import com.github.mcpjavafx.util.McpLogger;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

/**
 * STDIO MCP server implementation using JSON-RPC 2.0.
 *
 * <p>
 * Reads JSON-RPC requests from stdin, processes them, and writes responses to
 * stdout.
 * All logging goes to stderr to avoid interfering with the JSON-RPC protocol.
 * </p>
 */
public class StdioMcpBridge {

    private static final String PROTOCOL_VERSION = "2024-11-05";
    private static final String SERVER_NAME = "mcp-javafx-debug";
    private static final String SERVER_VERSION = "1.0.0";

    private final McpJavafxConfig config;
    private final McpToolRegistry registry;
    private final ObjectMapper mapper;
    private final BufferedReader reader;
    private final PrintStream writer;
    private volatile boolean running = false;

    public StdioMcpBridge(McpJavafxConfig config) {
        this.config = config;
        this.registry = new McpToolRegistry(config);
        this.mapper = createMapper();
        this.reader = new BufferedReader(new InputStreamReader(System.in, StandardCharsets.UTF_8));
        this.writer = new PrintStream(System.out, true, StandardCharsets.UTF_8);
    }

    private ObjectMapper createMapper() {
        var mapper = new ObjectMapper();
        mapper.registerModule(new JavaTimeModule());
        mapper.setSerializationInclusion(JsonInclude.Include.NON_NULL);
        mapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        return mapper;
    }

    /**
     * Starts the STDIO MCP server. Blocks until stdin is closed or stop() is
     * called.
     */
    public void start() {
        if (running) {
            return;
        }
        running = true;
        McpLogger.toStderr("STDIO server started");

        try {
            String line;
            while (running && (line = reader.readLine()) != null) {
                if (line.isBlank()) {
                    continue;
                }
                processMessage(line);
            }
        } catch (Exception e) {
            if (running) {
                McpLogger.toStderr("Error reading stdin: " + e.getMessage());
            }
        } finally {
            running = false;
            McpLogger.toStderr("STDIO server stopped");
        }
    }

    /**
     * Stops the STDIO server.
     */
    public void close() {
        running = false;
    }

    /**
     * Returns whether the server is running.
     */
    public boolean isRunning() {
        return running;
    }

    private void processMessage(String line) {
        try {
            var request = mapper.readTree(line);
            var id = request.get("id");
            var method = request.path("method").asText();

            // Handle notifications (no id) - just log
            if (id == null || id.isNull()) {
                McpLogger.toStderr("Received notification: " + method);
                return;
            }

            // Process request and send response
            var response = handleRequest(method, request.path("params"), id);
            sendResponse(response);
        } catch (Exception e) {
            McpLogger.toStderr("Error processing message: " + e.getMessage());
            // Try to send error response if we have an id
            try {
                var errorResponse = mapper.createObjectNode();
                errorResponse.put("jsonrpc", "2.0");
                errorResponse.putNull("id");
                var error = errorResponse.putObject("error");
                error.put("code", -32700);
                error.put("message", "Parse error: " + e.getMessage());
                sendResponse(errorResponse);
            } catch (Exception ignored) {
                // Cannot send error response
            }
        }
    }

    private ObjectNode handleRequest(String method, JsonNode params, JsonNode id) {
        return switch (method) {
            case "initialize" -> handleInitialize(id);
            case "tools/list" -> handleToolsList(id);
            case "tools/call" -> handleToolsCall(params, id);
            case "ping" -> successResult(id, mapper.createObjectNode());
            default -> errorResponse(id, -32601, "Method not found: " + method);
        };
    }

    private ObjectNode handleInitialize(JsonNode id) {
        var result = mapper.createObjectNode();
        result.put("protocolVersion", PROTOCOL_VERSION);

        var serverInfo = result.putObject("serverInfo");
        serverInfo.put("name", SERVER_NAME);
        serverInfo.put("version", SERVER_VERSION);

        var capabilities = result.putObject("capabilities");
        var tools = capabilities.putObject("tools");
        tools.put("listChanged", false);

        return successResult(id, result);
    }

    private ObjectNode handleToolsList(JsonNode id) {
        var result = mapper.createObjectNode();
        var toolsArray = result.putArray("tools");

        // Add all tools with their schemas
        addTool(toolsArray, "ui.getSnapshot",
                "Captures the current UI tree (Scene Graph) as a JSON snapshot.",
                getSnapshotSchema());
        addTool(toolsArray, "ui.query",
                "Finds UI nodes matching a selector (CSS, text, or predicate).",
                getQuerySchema());
        addTool(toolsArray, "ui.getNode",
                "Gets detailed information about a specific node by reference.",
                getNodeSchema());

        if (config.allowActions()) {
            addTool(toolsArray, "ui.perform",
                    "Performs UI actions (click, focus, type, etc.).",
                    getPerformSchema());
            addTool(toolsArray, "ui.screenshot",
                    "Takes a screenshot of the application window.",
                    getScreenshotSchema());
        }

        return successResult(id, result);
    }

    private void addTool(com.fasterxml.jackson.databind.node.ArrayNode tools,
            String name, String description, Map<String, Object> inputSchema) {
        var tool = tools.addObject();
        tool.put("name", name);
        tool.put("description", description);
        tool.set("inputSchema", mapper.valueToTree(inputSchema));
    }

    private ObjectNode handleToolsCall(JsonNode params, JsonNode id) {
        var toolName = params.path("name").asText();
        var arguments = params.path("arguments");

        if (toolName.isEmpty()) {
            return errorResponse(id, -32602, "Missing tool name");
        }

        try {
            // Execute tool using existing registry
            var resultJson = registry.execute(toolName, arguments);
            var resultNode = mapper.readTree(resultJson);

            var result = mapper.createObjectNode();
            var contentArray = result.putArray("content");

            if (resultNode.has("error")) {
                // Return error as text content with isError flag
                var error = resultNode.get("error");
                var message = error.has("message") ? error.get("message").asText() : "Unknown error";

                var textContent = contentArray.addObject();
                textContent.put("type", "text");
                textContent.put("text", message);
                result.put("isError", true);
            } else {
                // Return output as text content
                var output = resultNode.has("output") ? resultNode.get("output") : resultNode;
                var outputText = mapper.writerWithDefaultPrettyPrinter().writeValueAsString(output);

                var textContent = contentArray.addObject();
                textContent.put("type", "text");
                textContent.put("text", outputText);
            }

            return successResult(id, result);
        } catch (Exception e) {
            McpLogger.toStderr("Error executing tool " + toolName + ": " + e.getMessage());
            return errorResponse(id, -32000, "Tool execution error: " + e.getMessage());
        }
    }

    private ObjectNode successResult(JsonNode id, JsonNode result) {
        var response = mapper.createObjectNode();
        response.put("jsonrpc", "2.0");
        response.set("id", id);
        response.set("result", result);
        return response;
    }

    private ObjectNode errorResponse(JsonNode id, int code, String message) {
        var response = mapper.createObjectNode();
        response.put("jsonrpc", "2.0");
        response.set("id", id);
        var error = response.putObject("error");
        error.put("code", code);
        error.put("message", message);
        return response;
    }

    private void sendResponse(ObjectNode response) {
        try {
            var json = mapper.writeValueAsString(response);
            writer.println(json);
        } catch (Exception e) {
            McpLogger.toStderr("Error sending response: " + e.getMessage());
        }
    }

    // JSON Schema definitions
    private Map<String, Object> getSnapshotSchema() {
        return Map.of(
                "type", "object",
                "properties", Map.of(
                        "stage",
                        Map.of("type", "string", "description", "Stage selection: focused, primary, all", "default",
                                "focused"),
                        "stageIndex", Map.of("type", "integer", "description", "Specific stage index", "default", 0),
                        "depth", Map.of("type", "integer", "description", "Maximum tree depth", "default", 50),
                        "include", Map.of(
                                "type", "object",
                                "properties", Map.of(
                                        "bounds", Map.of("type", "boolean", "default", true),
                                        "localToScreen", Map.of("type", "boolean", "default", true),
                                        "properties", Map.of("type", "boolean", "default", false),
                                        "virtualization", Map.of("type", "boolean", "default", true)))));
    }

    private Map<String, Object> getQuerySchema() {
        return Map.of(
                "type", "object",
                "properties", Map.of(
                        "selector", Map.of(
                                "type", "object",
                                "description", "Query selector",
                                "properties", Map.of(
                                        "css",
                                        Map.of("type", "string", "description",
                                                "CSS selector (e.g., #myButton, .label)"),
                                        "text", Map.of("type", "string", "description", "Search by visible text"))),
                        "limit", Map.of("type", "integer", "default", 50)),
                "required", List.of("selector"));
    }

    private Map<String, Object> getNodeSchema() {
        return Map.of(
                "type", "object",
                "properties", Map.of(
                        "ref", Map.of(
                                "type", "object",
                                "description", "Node reference",
                                "properties", Map.of(
                                        "uid", Map.of("type", "string", "description", "Unique node ID"),
                                        "path", Map.of("type", "string", "description", "Node path"))),
                        "includeChildren", Map.of("type", "boolean", "default", false)),
                "required", List.of("ref"));
    }

    private Map<String, Object> getPerformSchema() {
        return Map.of(
                "type", "object",
                "properties", Map.of(
                        "actions", Map.of(
                                "type", "array",
                                "description", "List of actions to perform",
                                "items", Map.of(
                                        "type", "object",
                                        "properties", Map.of(
                                                "type",
                                                Map.of("type", "string", "enum",
                                                        List.of("focus", "click", "typeText", "setText", "pressKey",
                                                                "scroll")),
                                                "target", Map.of("type", "object"),
                                                "text", Map.of("type", "string")),
                                        "required", List.of("type"))),
                        "awaitUiIdle", Map.of("type", "boolean", "default", true)),
                "required", List.of("actions"));
    }

    private Map<String, Object> getScreenshotSchema() {
        return Map.of(
                "type", "object",
                "properties", Map.of(
                        "stage", Map.of("type", "string", "default", "focused"),
                        "stageIndex", Map.of("type", "integer", "default", 0)));
    }
}
