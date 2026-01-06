package com.github.mcpjavafx.mcp;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.github.mcpjavafx.api.McpJavafxConfig;
import com.github.mcpjavafx.api.SnapshotOptions;
import com.github.mcpjavafx.core.actions.ActionExecutor;
import com.github.mcpjavafx.core.capture.SceneGraphSnapshotter;
import com.github.mcpjavafx.core.fx.Fx;
import com.github.mcpjavafx.core.model.*;
import com.github.mcpjavafx.core.query.NodeQueryService;
import com.github.mcpjavafx.core.query.QueryPredicate;
import io.modelcontextprotocol.server.McpStatelessServerFeatures;
import io.modelcontextprotocol.server.McpStatelessSyncServer;
import io.modelcontextprotocol.spec.McpSchema.CallToolResult;
import io.modelcontextprotocol.spec.McpSchema.Content;
import io.modelcontextprotocol.spec.McpSchema.Tool;
import io.modelcontextprotocol.spec.McpSchema.JsonSchema;

import java.util.*;
import java.util.logging.Level;
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
    private final SceneGraphSnapshotter snapshotter;
    private final NodeQueryService queryService;
    private final ActionExecutor actionExecutor;

    private static final int COMPACT_DEFAULT_DEPTH = 8;

    public McpToolAdapter(McpJavafxConfig config) {
        this.config = config;
        this.mapper = createMapper();
        this.snapshotter = new SceneGraphSnapshotter(config.fxTimeoutMs());
        this.queryService = new NodeQueryService(config.fxTimeoutMs());
        this.actionExecutor = new ActionExecutor(config.fxTimeoutMs(), queryService);
    }

    private ObjectMapper createMapper() {
        var mapper = new ObjectMapper();
        mapper.registerModule(new JavaTimeModule());
        mapper.setSerializationInclusion(JsonInclude.Include.NON_NULL);
        mapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        return mapper;
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

        // Some transports/SDK versions may pass the full `params` object to the tool handler
        // (e.g. { name, arguments }) instead of passing the `arguments` object directly.
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
                        "stage", Map.of("type", "string", "enum", List.of("focused", "primary", "all")),
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
                        var result = executeGetSnapshot(input);
                        return toStructuredResult(result);
                    } catch (Exception e) {
                        LOG.log(Level.WARNING, "Error executing ui.getSnapshot", e);
                        return toStructuredResult(errorJson(e));
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
                "match", Map.of("type", "string", "enum", List.of("contains", "equals", "regex")),
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
                var result = executeQuery(input);
                return toStructuredResult(result);
                } catch (Exception e) {
                LOG.log(Level.WARNING, "Error executing ui.query", e);
                return toStructuredResult(errorJson(e));
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
                        "includeChildren", Map.of("type", "boolean")),
                List.of("ref"));

        return new McpStatelessServerFeatures.SyncToolSpecification(
                tool(
                        "ui_get_node",
                        "Get full details for a single node identified by ref.uid (preferred) or ref.path.",
                        inputSchema),
                (exchange, arguments) -> {
                    try {
                        var input = toArgumentsNode(arguments);
                        var result = executeGetNode(input);
                        return toStructuredResult(result);
                    } catch (Exception e) {
                        LOG.log(Level.WARNING, "Error executing ui.getNode", e);
                        return toStructuredResult(errorJson(e));
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

        var actionSchema = Map.<String, Object>of(
            "type", "object",
            "properties", Map.of(
                "type", Map.of("type", "string", "enum", List.of("focus", "click", "typeText", "setText", "pressKey", "scroll")),
                "target", targetSchema,
                "text", Map.of("type", "string"),
                "key", Map.of("type", "string"),
                "modifiers", Map.of("type", "array", "items", Map.of("type", "string")),
                "deltaY", Map.of("type", "number"),
                "x", Map.of("type", "number"),
                "y", Map.of("type", "number")),
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
                "Perform UI actions on the JavaFX application thread. Each action has {type, ...}. Most actions address nodes via target.ref.uid (preferred) or target.ref.path.",
                inputSchema),
            (exchange, arguments) -> {
                try {
                var input = toArgumentsNode(arguments);
                var result = executePerform(input);
                return toStructuredResult(result);
                } catch (Exception e) {
                LOG.log(Level.WARNING, "Error executing ui.perform", e);
                return toStructuredResult(errorJson(e));
                }
            });
        }

    private McpStatelessServerFeatures.SyncToolSpecification createScreenshotTool() {
        var inputSchema = objectSchema(
                Map.of("stageIndex", Map.of("type", "integer")),
                List.of());

        return new McpStatelessServerFeatures.SyncToolSpecification(
            tool("ui_screenshot", "Capture a PNG screenshot. stageIndex=-1 means focused stage.", inputSchema),
                (exchange, arguments) -> {
                    try {
                        var input = toArgumentsNode(arguments);
                        var result = executeScreenshot(input);
                        return toStructuredResult(result);
                    } catch (Exception e) {
                        LOG.log(Level.WARNING, "Error executing ui.screenshot", e);
                        return toStructuredResult(errorJson(e));
                    }
                });
    }

    private Tool tool(String name, String description, JsonSchema inputSchema) {
        // SDK 0.17.0 Tool is a record with full metadata; keep minimal fields populated.
        return Tool.builder()
                .name(name)
                .title(name)
                .description(description)
                .inputSchema(inputSchema)
                // Non-null outputSchema makes the SDK expect structuredContent in CallToolResult.
                // We always return structuredContent ({"output":...} or {"error":...}).
                .outputSchema(objectOutputSchema())
                .meta(Map.of("schemaVersion", "2"))
                .build();
    }

    private Map<String, Object> objectOutputSchema() {
        // Intentionally permissive schema:
        //   success: { "output": <any> }
        //   error:   { "error": { code, message, ... } }
        return Map.of(
                "type", "object",
                "properties", Map.of(
                        "output", Map.of(),
                        "error", Map.of()),
                "additionalProperties", true);
    }

    private CallToolResult toStructuredResult(String json) {
        try {
            @SuppressWarnings("unchecked")
            var structured = (Map<String, Object>) mapper.readValue(json, Map.class);
            var isError = structured.containsKey("error");
            return new CallToolResult(List.<Content>of(), isError, structured, Map.of());
        } catch (Exception e) {
            var error = McpError.of(ErrorCode.MCP_UI_INTERNAL,
                    e.getMessage() != null ? e.getMessage() : "Unknown error");
            return new CallToolResult(List.<Content>of(), true, Map.of("error", error), Map.of());
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

    // ============ Tool Implementations ============

    private String executeGetSnapshot(JsonNode input) throws Exception {
        var stageModeNode = input.get("stage");
        var stageMode = parseStageMode(stageModeNode != null ? stageModeNode.asText() : "focused");
        var stageIndex = input.path("stageIndex").asInt(0);
        var mode = input.path("mode").asText();
        if (mode == null || mode.isBlank()) {
            mode = "full";
        }
        mode = mode.toLowerCase();
        var compact = "compact".equals(mode);

        var defaultDepth = compact ? Math.min(config.snapshotDefaults().depth(), COMPACT_DEFAULT_DEPTH)
            : config.snapshotDefaults().depth();
        var depth = input.has("depth") ? input.path("depth").asInt(defaultDepth) : defaultDepth;

        var includeNode = input.path("include");
        var includeBounds = includeNode.has("bounds")
            ? includeNode.path("bounds").asBoolean()
            : (compact ? false : config.snapshotDefaults().includeBounds());
        var includeLocalToScreen = includeNode.has("localToScreen")
            ? includeNode.path("localToScreen").asBoolean()
            : (compact ? false : config.snapshotDefaults().includeLocalToScreen());
        var includeProperties = includeNode.has("properties")
            ? includeNode.path("properties").asBoolean()
            : (compact ? false : config.snapshotDefaults().includeProperties());
        var includeVirtualization = includeNode.has("virtualization")
            ? includeNode.path("virtualization").asBoolean()
            : (compact ? false : config.snapshotDefaults().includeVirtualization());
        var includeAccessibility = includeNode.has("accessibility")
            ? includeNode.path("accessibility").asBoolean()
            : (compact ? false : config.snapshotDefaults().includeAccessibility());

        var options = SnapshotOptions.builder()
            .depth(depth)
            .includeBounds(includeBounds)
            .includeLocalToScreen(includeLocalToScreen)
            .includeProperties(includeProperties)
            .includeVirtualization(includeVirtualization)
            .includeAccessibility(includeAccessibility)
            .build();

        var snapshot = snapshotter.capture(stageMode, stageIndex, options);

        if (snapshot.stages().isEmpty()) {
            return errorResponse(ErrorCode.MCP_UI_NO_STAGES);
        }

        return successResponse(snapshot);
    }

    private String executeQuery(JsonNode input) throws Exception {
        var scopeNode = input.path("scope");
        var stageIndex = scopeNode.path("stageIndex").asInt(-1);
        var scopeStage = scopeNode.path("stage").asText().toLowerCase();
        if ("focused".equals(scopeStage)) {
            stageIndex = -1;
        }
        var limit = input.path("limit").asInt(50);

        var selectorNode = input.path("selector");
        List<NodeQueryService.QueryMatch> matches;

        if (selectorNode.has("css")) {
            var css = selectorNode.path("css").asText();
            matches = queryService.queryCss(stageIndex, css, limit);
        } else if (selectorNode.has("text")) {
            var text = selectorNode.path("text").asText();
            var matchModeNode = selectorNode.get("match");
            var matchMode = matchModeNode != null ? matchModeNode.asText() : "contains";
            matches = queryService.queryText(stageIndex, text, matchMode, limit);
        } else if (selectorNode.has("predicate")) {
            var predicate = parsePredicate(selectorNode.path("predicate"));
            matches = queryService.queryPredicate(stageIndex, predicate, limit);
        } else {
            return errorResponse(ErrorCode.MCP_UI_INTERNAL, "No selector specified");
        }

        return successResponse(Map.of("matches", matches));
    }

    private String executeGetNode(JsonNode input) throws Exception {
        var refNode = input.path("ref");
        var ref = parseRef(refNode);
        var node = queryService.findByRef(ref);
        if (node == null) {
            return errorResponse(ErrorCode.MCP_UI_NODE_NOT_FOUND,
                    "Node not found: " + ref.path() + " / " + ref.uid());
        }

        var includeChildren = input.path("includeChildren").asBoolean(false);

        var options = SnapshotOptions.builder()
                .includeBounds(config.snapshotDefaults().includeBounds())
                .includeLocalToScreen(config.snapshotDefaults().includeLocalToScreen())
                .includeProperties(config.snapshotDefaults().includeProperties())
                .includeVirtualization(config.snapshotDefaults().includeVirtualization())
                .includeAccessibility(config.snapshotDefaults().includeAccessibility())
                .build();

        var details = snapshotter.captureNodeDetails(node, includeChildren, options);
        return successResponse(details);
    }

    private String executePerform(JsonNode input) throws Exception {
        if (!config.allowActions()) {
            return errorResponse(ErrorCode.MCP_UI_ACTION_FAILED, "Actions are disabled");
        }

        var actionsNode = input.path("actions");
        if (!actionsNode.isArray()) {
            return errorResponse(ErrorCode.MCP_UI_INTERNAL, "actions must be an array");
        }

        var results = new ArrayList<ActionExecutor.ActionResult>();

        for (var actionNode : actionsNode) {
            var type = actionNode.path("type").asText();
            var result = executeAction(type, actionNode);
            results.add(result);

            if (!result.ok()) {
                break;
            }
        }

        if (input.path("awaitUiIdle").asBoolean(true)) {
            var timeoutMs = input.path("timeoutMs").asInt(config.fxTimeoutMs());
            Fx.awaitUiIdle(timeoutMs);
        }

        return successResponse(Map.of("results", results));
    }

    private ActionExecutor.ActionResult executeAction(String type, JsonNode actionNode) {
        try {
            return switch (type) {
                case "focus" -> {
                    var ref = parseRef(actionNode.path("target").path("ref"));
                    yield actionExecutor.focus(ref);
                }
                case "click" -> {
                    if (actionNode.has("x") && actionNode.has("y")) {
                        yield actionExecutor.clickAt(
                                actionNode.path("x").asDouble(),
                                actionNode.path("y").asDouble());
                    }
                    var ref = parseRef(actionNode.path("target").path("ref"));
                    yield actionExecutor.click(ref);
                }
                case "typeText" -> {
                    var textNode = actionNode.get("text");
                    var text = textNode != null ? textNode.asText() : "";
                    yield actionExecutor.typeText(text);
                }
                case "setText" -> {
                    var ref = parseRef(actionNode.path("target").path("ref"));
                    var textNode = actionNode.get("text");
                    var text = textNode != null ? textNode.asText() : "";
                    yield actionExecutor.setText(ref, text);
                }
                case "pressKey" -> {
                    var key = actionNode.path("key").asText();
                    var modifiers = new ArrayList<String>();
                    var modsNode = actionNode.path("modifiers");
                    if (modsNode.isArray()) {
                        for (var m : modsNode) {
                            modifiers.add(m.asText());
                        }
                    }
                    yield actionExecutor.pressKey(key, modifiers);
                }
                case "scroll" -> {
                    var ref = parseRef(actionNode.path("target").path("ref"));
                    var deltaY = actionNode.path("deltaY").asInt(0);
                    yield actionExecutor.scroll(ref, deltaY);
                }
                default -> ActionExecutor.ActionResult.failure(type, "Unknown action type: " + type);
            };
        } catch (Exception e) {
            return ActionExecutor.ActionResult.failure(type, e.getMessage());
        }
    }

    private String executeScreenshot(JsonNode input) throws Exception {
        if (!config.allowActions()) {
            return errorResponse(ErrorCode.MCP_UI_ACTION_FAILED, "Screenshots are disabled");
        }

        var stageIndex = input.path("stageIndex").asInt(-1);
        var base64 = actionExecutor.screenshot(stageIndex);

        if (base64 == null) {
            return errorResponse(ErrorCode.MCP_UI_NO_STAGES, "Cannot take screenshot");
        }

        return successResponse(Map.of(
                "contentType", "image/png",
                "dataBase64", base64));
    }

    // ============ Helper methods ============

    private SceneGraphSnapshotter.StageMode parseStageMode(String mode) {
        return switch (mode.toLowerCase()) {
            case "primary" -> SceneGraphSnapshotter.StageMode.PRIMARY;
            case "all" -> SceneGraphSnapshotter.StageMode.ALL;
            default -> SceneGraphSnapshotter.StageMode.FOCUSED;
        };
    }

    private QueryPredicate parsePredicate(JsonNode node) {
        var builder = QueryPredicate.builder();

        if (node.has("typeIs") && node.path("typeIs").isArray()) {
            var types = new ArrayList<String>();
            for (var t : node.path("typeIs")) {
                types.add(t.asText());
            }
            builder.typeIs(types.toArray(new String[0]));
        }

        if (node.has("idEquals")) {
            builder.idEquals(node.path("idEquals").asText());
        }

        if (node.has("styleClassHas")) {
            builder.styleClassHas(node.path("styleClassHas").asText());
        }

        if (node.has("textContains")) {
            builder.textContains(node.path("textContains").asText());
        }

        if (node.has("visible")) {
            builder.visible(node.path("visible").asBoolean());
        }

        if (node.has("enabled")) {
            builder.enabled(node.path("enabled").asBoolean());
        }

        return builder.build();
    }

    private NodeRef parseRef(JsonNode node) {
        var pathNode = node.get("path");
        var uidNode = node.get("uid");
        return new NodeRef(
                pathNode != null ? pathNode.asText() : null,
                uidNode != null ? uidNode.asText() : null);
    }

    private String successResponse(Object output) throws Exception {
        return mapper.writeValueAsString(Map.of("output", output));
    }

    private String errorResponse(ErrorCode code) {
        return errorResponse(code, code.getDefaultMessage());
    }

    private String errorResponse(ErrorCode code, String message) {
        try {
            var error = McpError.of(code, message);
            return mapper.writeValueAsString(Map.of("error", error));
        } catch (Exception e) {
            return "{\"error\":{\"code\":\"MCP_UI_INTERNAL\",\"message\":\"" + e.getMessage() + "\"}}";
        }
    }

    private String errorJson(Exception e) {
        try {
            return mapper.writeValueAsString(Map.of("error", Map.of(
                    "code", "MCP_UI_INTERNAL",
                    "message", e.getMessage() != null ? e.getMessage() : "Unknown error")));
        } catch (Exception ex) {
            return "{\"error\":{\"code\":\"MCP_UI_INTERNAL\",\"message\":\"" + e.getMessage() + "\"}}";
        }
    }
}
