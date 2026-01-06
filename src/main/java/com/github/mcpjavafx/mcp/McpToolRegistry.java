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

import java.util.*;

/**
 * Registry and executor for MCP tools.
 */
public class McpToolRegistry {

    private final ObjectMapper mapper;
    private final McpJavafxConfig config;
    private final SceneGraphSnapshotter snapshotter;
    private final NodeQueryService queryService;
    private final ActionExecutor actionExecutor;

    public McpToolRegistry(McpJavafxConfig config) {
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
            return switch (tool) {
                case "ui.getSnapshot" -> executeGetSnapshot(input);
                case "ui.query" -> executeQuery(input);
                case "ui.getNode" -> executeGetNode(input);
                case "ui.perform" -> executePerform(input);
                case "ui.screenshot" -> executeScreenshot(input);
                default -> errorResponse(ErrorCode.MCP_UI_INTERNAL, "Unknown tool: " + tool);
            };
        } catch (Exception e) {
            return errorResponse(ErrorCode.MCP_UI_INTERNAL, e.getMessage());
        }
    }

    private String executeGetSnapshot(JsonNode input) throws Exception {
        var stageModeNode = input.get("stage");
        var stageMode = parseStageMode(stageModeNode != null ? stageModeNode.asText() : "focused");
        var stageIndex = input.path("stageIndex").asInt(0);
        var depth = input.path("depth").asInt(config.snapshotDefaults().depth());

        var includeNode = input.path("include");
        var options = SnapshotOptions.builder()
                .depth(depth)
                .includeBounds(includeNode.path("bounds").asBoolean(true))
                .includeLocalToScreen(includeNode.path("localToScreen").asBoolean(true))
                .includeProperties(includeNode.path("properties").asBoolean(false))
                .includeVirtualization(includeNode.path("virtualization").asBoolean(true))
                .includeAccessibility(includeNode.path("accessibility").asBoolean(false))
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
                .depth(includeChildren ? 1 : 0)
                .build();

        // Capture single node using snapshotter internally
        var snapshot = snapshotter.capture(
                SceneGraphSnapshotter.StageMode.ALL, null, options);

        // Find the node in the snapshot
        for (var stage : snapshot.stages()) {
            if (stage.scene() != null && stage.scene().root() != null) {
                var foundNode = findNodeInTree(stage.scene().root(), ref);
                if (foundNode != null) {
                    return successResponse(foundNode);
                }
            }
        }

        return errorResponse(ErrorCode.MCP_UI_STALE_REF, "Node reference is stale");
    }

    private UiNode findNodeInTree(UiNode root, NodeRef ref) {
        if (matches(root.ref(), ref)) {
            return root;
        }

        for (var child : root.children()) {
            var found = findNodeInTree(child, ref);
            if (found != null) {
                return found;
            }
        }

        return null;
    }

    private boolean matches(NodeRef a, NodeRef b) {
        if (a.uid() != null && b.uid() != null) {
            return a.uid().equals(b.uid());
        }
        if (a.path() != null && b.path() != null) {
            return a.path().equals(b.path());
        }
        return false;
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
                break; // Stop on first failure
            }
        }

        // Await UI idle if requested
        if (input.path("awaitUiIdle").asBoolean(true)) {
            Fx.awaitUiIdle(config.fxTimeoutMs());
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
}
