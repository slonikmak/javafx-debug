package com.github.mcpjavafx.mcp;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.mcpjavafx.api.McpJavafxConfig;
import com.github.mcpjavafx.core.model.McpError;
import com.github.mcpjavafx.util.JsonMapperFactory;
import io.modelcontextprotocol.server.McpStatelessServerFeatures;
import io.modelcontextprotocol.server.McpStatelessSyncServer;
import io.modelcontextprotocol.spec.McpSchema.CallToolResult;
import io.modelcontextprotocol.spec.McpSchema.JsonSchema;
import io.modelcontextprotocol.spec.McpSchema.TextContent;
import io.modelcontextprotocol.spec.McpSchema.Tool;

import java.util.List;
import java.util.Map;
import java.util.logging.Logger;

/**
 * Adapter that registers MCP tools with the official MCP SDK server.
 * 
 * <p>
 * This class bridges the existing tool implementation with SDK's tool
 * registration API.
 * </p>
 */
public class McpToolAdapter {

        private static final Logger LOG = Logger.getLogger(McpToolAdapter.class.getName());

        private final McpJavafxConfig config;
        private final ObjectMapper mapper;
        private final UiToolsService toolsService;

        public McpToolAdapter(McpJavafxConfig config) {
                this.config = config;
                this.mapper = JsonMapperFactory.createDefault();
                this.toolsService = new UiToolsService(config, mapper);
        }

        /**
         * Registers all available tools with the MCP server.
         */
        public void registerTools(McpStatelessSyncServer server) {
                server.addTool(createGetSnapshotTool());
                server.addTool(createQueryTool());
                server.addTool(createGetNodeTool());

                if (config.allowActions()) {
                        server.addTool(createPerformTool());
                        server.addTool(createScreenshotTool());
                }
        }

        private JsonNode toArgumentsNode(Object raw) {
                var node = mapper.valueToTree(raw);

                // Some transports/SDK versions may pass the full `params` object to the tool
                // handler
                // (e.g. { name, arguments }) instead of passing the `arguments` object
                // directly.
                // Support both shapes.
                var argsNode = node.get("arguments");
                if (argsNode != null && argsNode.isObject()) {
                        return argsNode;
                }

                return node;
        }

        private McpStatelessServerFeatures.SyncToolSpecification createGetSnapshotTool() {
                var includeSchema = Map.<String, Object>of(
                                "type", "object",
                                "properties", Map.of(
                                                "bounds", Map.of("type", "boolean"),
                                                "localToScreen", Map.of("type", "boolean"),
                                                "properties", Map.of("type", "boolean"),
                                                "virtualization", Map.of("type", "boolean"),
                                                "accessibility", Map.of("type", "boolean")),
                                "additionalProperties", false);

                var inputSchema = objectSchema(
                                Map.of(
                                                "stage",
                                                Map.of("type", "string", "enum", List.of("focused", "primary", "all")),
                                                "stageIndex", Map.of("type", "integer"),
                                                "mode", Map.of("type", "string", "enum", List.of("full", "compact")),
                                                "depth", Map.of("type", "integer"),
                                                "include", includeSchema),
                                List.of());

                return new McpStatelessServerFeatures.SyncToolSpecification(
                                tool(
                                                "ui_get_snapshot",
                                                "Capture a UI scene graph snapshot. Use mode=compact to reduce payload; use include.* to opt into bounds/properties/accessibility.",
                                                inputSchema),
                                (exchange, arguments) -> {
                                        try {
                                                var input = toArgumentsNode(arguments);
                                                var result = toolsService.executeGetSnapshot(input);
                                                return toStructuredResult(result);
                                        } catch (Exception e) {
                                                return toStructuredResult(toolsService.wrapException(e));
                                        }
                                });
        }

        private McpStatelessServerFeatures.SyncToolSpecification createQueryTool() {
                var scopeSchema = Map.<String, Object>of(
                                "type", "object",
                                "properties", Map.of(
                                                "stage", Map.of("type", "string", "enum", List.of("focused", "index")),
                                                "stageIndex", Map.of("type", "integer")),
                                "additionalProperties", false);

                var selectorSchema = Map.<String, Object>of(
                                "type", "object",
                                "properties", Map.of(
                                                "css", Map.of("type", "string"),
                                                "text", Map.of("type", "string"),
                                                "match",
                                                Map.of("type", "string", "enum",
                                                                List.of("contains", "equals", "regex")),
                                                "predicate", Map.of("type", "object")),
                                "additionalProperties", true);

                var inputSchema = objectSchema(
                                Map.of(
                                                "scope", scopeSchema,
                                                "selector", selectorSchema,
                                                "limit", Map.of("type", "integer")),
                                List.of("selector"));

                return new McpStatelessServerFeatures.SyncToolSpecification(
                                tool(
                                                "ui_query",
                                                "Find UI nodes by selector. selector.css uses Scene.lookupAll; selector.text searches visible text with match=contains|equals|regex; selector.predicate supports structured filters.",
                                                inputSchema),
                                (exchange, arguments) -> {
                                        try {
                                                var input = toArgumentsNode(arguments);
                                                var result = toolsService.executeQuery(input);
                                                return toStructuredResult(result);
                                        } catch (Exception e) {
                                                return toStructuredResult(toolsService.wrapException(e));
                                        }
                                });
        }

        private McpStatelessServerFeatures.SyncToolSpecification createGetNodeTool() {
                var refSchema = Map.<String, Object>of(
                                "type", "object",
                                "properties", Map.of(
                                                "uid", Map.of("type", "string"),
                                                "path", Map.of("type", "string")),
                                "additionalProperties", false);

                var inputSchema = objectSchema(
                                Map.of(
                                                "ref", refSchema,
                                                "includeChildren", Map.of("type", "boolean"),
                                                "fields", Map.of("type", "array", "items", Map.of("type", "string")),
                                                "properties",
                                                Map.of("type", "array", "items", Map.of("type", "string"))),
                                List.of("ref"));

                return new McpStatelessServerFeatures.SyncToolSpecification(
                                tool(
                                                "ui_get_node",
                                                "Get full details for a single node identified by ref.uid (preferred) or ref.path.",
                                                inputSchema),
                                (exchange, arguments) -> {
                                        try {
                                                var input = toArgumentsNode(arguments);
                                                var result = toolsService.executeGetNode(input);
                                                return toStructuredResult(result);
                                        } catch (Exception e) {
                                                return toStructuredResult(toolsService.wrapException(e));
                                        }
                                });
        }

        private McpStatelessServerFeatures.SyncToolSpecification createPerformTool() {
                var refSchema = Map.<String, Object>of(
                                "type", "object",
                                "properties", Map.of(
                                                "uid", Map.of("type", "string"),
                                                "path", Map.of("type", "string")),
                                "additionalProperties", false);

                var targetSchema = Map.<String, Object>of(
                                "type", "object",
                                "properties", Map.of(
                                                "ref", refSchema),
                                "additionalProperties", false);

                var positionSchema = Map.<String, Object>of(
                                "type", "object",
                                "properties", Map.of(
                                                "ref", refSchema,
                                                "x", Map.of("type", "number"),
                                                "y", Map.of("type", "number")),
                                "additionalProperties", false);

                var actionProperties = new java.util.HashMap<String, Object>();
                actionProperties.put("type", Map.of("type", "string", "enum", List.of(
                                "focus", "click", "doubleClick", "typeText", "setText",
                                "pressKey", "scroll", "mousePressed", "mouseReleased", "drag")));
                actionProperties.put("target", targetSchema);
                actionProperties.put("text", Map.of("type", "string"));
                actionProperties.put("key", Map.of("type", "string"));
                actionProperties.put("modifiers", Map.of("type", "array", "items", Map.of("type", "string")));
                actionProperties.put("button",
                                Map.of("type", "string", "enum", List.of("PRIMARY", "SECONDARY", "MIDDLE")));
                actionProperties.put("deltaY", Map.of("type", "number"));
                actionProperties.put("from", positionSchema);
                actionProperties.put("to", positionSchema);
                actionProperties.put("x", Map.of("type", "number"));
                actionProperties.put("y", Map.of("type", "number"));

                var actionSchema = Map.<String, Object>of(
                                "type", "object",
                                "properties", actionProperties,
                                "required", List.of("type"),
                                "additionalProperties", true);

                var inputSchema = objectSchema(
                                Map.of(
                                                "actions", Map.of("type", "array", "items", actionSchema),
                                                "awaitUiIdle", Map.of("type", "boolean"),
                                                "timeoutMs", Map.of("type", "integer")),
                                List.of("actions"));

                return new McpStatelessServerFeatures.SyncToolSpecification(
                                tool(
                                                "ui_perform",
                                                "Execute a sequence of UI actions. Supports: click, doubleClick, focus, setText, typeText, pressKey, scroll, mousePressed, mouseReleased, drag.",
                                                inputSchema),
                                (exchange, arguments) -> {
                                        try {
                                                var input = toArgumentsNode(arguments);
                                                var result = toolsService.executePerform(input);
                                                return toStructuredResult(result);
                                        } catch (Exception e) {
                                                return toStructuredResult(toolsService.wrapException(e));
                                        }
                                });
        }

        private McpStatelessServerFeatures.SyncToolSpecification createScreenshotTool() {
                var inputSchema = objectSchema(
                                Map.of("stageIndex", Map.of("type", "integer")),
                                List.of());

                return new McpStatelessServerFeatures.SyncToolSpecification(
                                tool(
                                                "ui_screenshot",
                                                "Capture a PNG screenshot of a stage.",
                                                inputSchema),
                                (exchange, arguments) -> {
                                        try {
                                                var input = toArgumentsNode(arguments);
                                                var result = toolsService.executeScreenshot(input);
                                                return toStructuredResult(result);
                                        } catch (Exception e) {
                                                return toStructuredResult(toolsService.wrapException(e));
                                        }
                                });
        }

        private Tool tool(String name, String description, JsonSchema inputSchema) {
                // SDK 0.17.0 Tool is a record with full metadata; keep minimal fields
                // populated.
                return Tool.builder()
                                .name(name)
                                .title(name)
                                .description(description)
                                .inputSchema(inputSchema)
                                // Non-null outputSchema makes the SDK expect structuredContent in
                                // CallToolResult.
                                // We always return structuredContent ({"output":...} or {"error":...}).
                                .outputSchema(objectOutputSchema())
                                .meta(Map.of("schemaVersion", "2"))
                                .build();
        }

        private Map<String, Object> objectOutputSchema() {
                // Intentionally permissive schema:
                // success: { "output": <any> }
                // error: { "error": { code, message, ... } }
                return Map.of(
                                "type", "object",
                                "properties", Map.of(
                                                "output", Map.of(),
                                                "error", Map.of()),
                                "additionalProperties", true);
        }

        private CallToolResult toStructuredResult(Object result) {
                if (result instanceof McpError error) {
                        return new CallToolResult(
                                        List.of(new TextContent(error.message() + " (" + error.code() + ")")),
                                        true,
                                        Map.of("error", error),
                                        null);
                }

                if (result instanceof SnapshotResult snapshot) {
                        return new CallToolResult(
                                        List.of(new TextContent(snapshot.tree())),
                                        false,
                                        Map.of("output", snapshot.structured()),
                                        null);
                }

                try {
                        return new CallToolResult(
                                        List.of(new TextContent(mapper.writeValueAsString(result))),
                                        false,
                                        Map.of("output", result),
                                        null);
                } catch (Exception e) {
                        return new CallToolResult(
                                        List.of(new TextContent("Result serialization error: " + e.getMessage())),
                                        true,
                                        null,
                                        null);
                }
        }

        private JsonSchema objectSchema(Map<String, Object> properties, List<String> required) {
                return new JsonSchema(
                                "object",
                                properties,
                                required != null ? required : List.of(),
                                true,
                                null,
                                null);
        }
}
