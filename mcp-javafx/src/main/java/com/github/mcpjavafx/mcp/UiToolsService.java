package com.github.mcpjavafx.mcp;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.mcpjavafx.api.McpJavafxConfig;
import com.github.mcpjavafx.api.SnapshotOptions;
import com.github.mcpjavafx.core.actions.ActionExecutor;
import com.github.mcpjavafx.core.capture.SceneGraphSnapshotter;
import com.github.mcpjavafx.core.capture.TreeFormatter;
import com.github.mcpjavafx.core.fx.FxTimeoutException;
import com.github.mcpjavafx.core.fx.NodeRefService;
import com.github.mcpjavafx.core.model.ErrorCode;
import com.github.mcpjavafx.core.model.McpError;
import com.github.mcpjavafx.core.model.NodeRef;
import com.github.mcpjavafx.core.query.NodeQueryService;
import com.github.mcpjavafx.core.query.QueryPredicate;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Common service for executing MCP tools, shared between Registry and Adapter.
 */
public class UiToolsService {

    private static final Logger LOG = Logger.getLogger(UiToolsService.class.getName());
    private static final int COMPACT_DEFAULT_DEPTH = 8;
    private static final int DEFAULT_QUERY_LIMIT = 50;
    private static final Set<String> SNAPSHOT_NODE_FIELDS = Set.of("ref", "type", "id", "text", "value", "children");

    private final McpJavafxConfig config;
    private final ObjectMapper mapper;
    private final SceneGraphSnapshotter snapshotter;
    private final NodeQueryService queryService;
    private final ActionExecutor actionExecutor;
    private final TreeFormatter treeFormatter;

    public UiToolsService(McpJavafxConfig config, ObjectMapper mapper) {
        this(config, mapper, new NodeRefService());
    }

    public UiToolsService(McpJavafxConfig config, ObjectMapper mapper, NodeRefService nodeRefService) {
        this.config = Objects.requireNonNull(config, "config");
        this.mapper = Objects.requireNonNull(mapper, "mapper");
        Objects.requireNonNull(nodeRefService, "nodeRefService");

        this.snapshotter = new SceneGraphSnapshotter(config.fxTimeoutMs());
        this.queryService = new NodeQueryService(config.fxTimeoutMs());
        this.actionExecutor = new ActionExecutor(config.fxTimeoutMs(), queryService, nodeRefService);
        this.treeFormatter = new TreeFormatter();
    }

    public Object executeGetSnapshot(JsonNode input) throws Exception {
        var stageModeStr = getTextOrDefault(input, "stage", "focused");
        var stageMode = parseStageMode(stageModeStr);
        var stageIndex = input.path("stageIndex").asInt(0);
        var mode = getTextOrDefault(input, "mode", "compact").toLowerCase();
        var compact = !"full".equals(mode);

        var options = buildSnapshotOptions(input, compact);
        var snapshot = snapshotter.capture(stageMode, stageIndex, options);

        if (snapshot.stages().isEmpty()) {
            return McpError.of(ErrorCode.MCP_UI_NO_STAGES, "No stages found");
        }

        var lightweight = TreeFormatter.lightweightFrom(snapshot);
        var tree = treeFormatter.format(lightweight);
        var structured = NodeFieldFilter.filterSnapshot(mapper, lightweight, SNAPSHOT_NODE_FIELDS);
        return new SnapshotResult(tree, structured);
    }

    private SnapshotOptions buildSnapshotOptions(JsonNode input, boolean compact) {
        var defaultDepth = compact
                ? Math.min(config.snapshotDefaults().depth(), COMPACT_DEFAULT_DEPTH)
                : config.snapshotDefaults().depth();
        var depth = input.has("depth") ? input.path("depth").asInt(defaultDepth) : defaultDepth;

        var includeNode = input.path("include");
        var includeBounds = getBooleanOrDefault(includeNode, "bounds",
                compact ? false : config.snapshotDefaults().includeBounds());
        var includeLocalToScreen = getBooleanOrDefault(includeNode, "localToScreen",
                compact ? false : config.snapshotDefaults().includeLocalToScreen());
        var includeProperties = getBooleanOrDefault(includeNode, "properties",
                compact ? false : config.snapshotDefaults().includeProperties());
        var includeVirtualization = getBooleanOrDefault(includeNode, "virtualization",
                compact ? false : config.snapshotDefaults().includeVirtualization());
        var includeAccessibility = getBooleanOrDefault(includeNode, "accessibility",
                compact ? false : config.snapshotDefaults().includeAccessibility());
        var includeControlInternals = getBooleanOrDefault(includeNode, "controlInternals", false);

        var skeleton = compact
                && !includeBounds
                && !includeLocalToScreen
                && !includeProperties
                && !includeVirtualization
                && !includeAccessibility;

        return SnapshotOptions.builder()
                .depth(depth)
                .includeBounds(includeBounds)
                .includeLocalToScreen(includeLocalToScreen)
                .includeProperties(includeProperties)
                .includeVirtualization(includeVirtualization)
                .includeAccessibility(includeAccessibility)
                .includeControlInternals(includeControlInternals)
                .skeleton(skeleton)
                .build();
    }

    public Object executeQuery(JsonNode input) throws Exception {
        var scopeNode = input.path("scope");
        var stageIndex = scopeNode.path("stageIndex").asInt(-1);
        var scopeStage = scopeNode.path("stage").asText().toLowerCase();
        if ("focused".equals(scopeStage)) {
            stageIndex = -1;
        }
        var limit = input.path("limit").asInt(DEFAULT_QUERY_LIMIT);

        var selectorNode = input.path("selector");
        List<NodeQueryService.QueryMatch> matches;

        if (selectorNode.has("css")) {
            var css = selectorNode.path("css").asText();
            matches = queryService.queryCss(stageIndex, css, limit);
        } else if (selectorNode.has("text")) {
            var text = selectorNode.path("text").asText();
            var matchMode = getTextOrDefault(selectorNode, "match", "contains");
            matches = queryService.queryText(stageIndex, text, matchMode, limit);
        } else if (selectorNode.has("predicate")) {
            var predicate = mapper.treeToValue(selectorNode.path("predicate"), QueryPredicate.class);
            matches = queryService.queryPredicate(stageIndex, predicate, limit);
        } else {
            return McpError.of(ErrorCode.MCP_UI_INTERNAL, "No selector specified");
        }

        return Map.of("matches", matches);
    }

    public Object executeGetNode(JsonNode input) throws Exception {
        var refNode = input.path("ref");
        var ref = mapper.treeToValue(refNode, NodeRef.class);
        var node = queryService.findByRef(ref);
        if (node == null) {
            return McpError.of(ErrorCode.MCP_UI_NODE_NOT_FOUND,
                    "Node not found: " + ref.path() + " / " + ref.uid());
        }

        var includeChildren = input.path("includeChildren").asBoolean(false);
        var fields = parseStringSet(input.path("fields"));
        var properties = parseStringSet(input.path("properties"));

        var options = SnapshotOptions.builder()
                .includeBounds(config.snapshotDefaults().includeBounds())
                .includeLocalToScreen(config.snapshotDefaults().includeLocalToScreen())
                .includeProperties(config.snapshotDefaults().includeProperties())
                .includeVirtualization(config.snapshotDefaults().includeVirtualization())
                .includeAccessibility(config.snapshotDefaults().includeAccessibility())
                .build();

        var captured = snapshotter.captureNodeDetails(node, includeChildren, options);
        return NodeFieldFilter.filterNode(mapper, captured, fields, properties);
    }

    public Object executePerform(JsonNode input) throws Exception {
        if (!config.allowActions()) {
            return McpError.of(ErrorCode.MCP_UI_ACTION_FAILED, "Actions are disabled");
        }

        var actionsNode = input.path("actions");
        if (!actionsNode.isArray()) {
            return McpError.of(ErrorCode.MCP_UI_INTERNAL, "actions must be an array");
        }

        var results = new ArrayList<ActionExecutor.ActionResult>();
        for (var actionNode : actionsNode) {
            var type = actionNode.path("type").asText();
            var res = executeAction(type, actionNode);
            results.add(res);
        }

        return Map.of("results", results);
    }

    private ActionExecutor.ActionResult executeAction(String type, JsonNode actionNode) throws Exception {
        return switch (type) {
            case "click" -> actionExecutor.click(
                    extractRef(actionNode, "target", "ref"),
                    extractDouble(actionNode.path("x")),
                    extractDouble(actionNode.path("y")));
            case "focus" -> actionExecutor.focus(extractRef(actionNode, "target", "ref"));
            case "setText" -> actionExecutor.setText(
                    extractRef(actionNode, "target", "ref"),
                    actionNode.path("text").asText());
            case "typeText" -> actionExecutor.typeText(actionNode.path("text").asText());
            case "pressKey" -> actionExecutor.pressKey(
                    actionNode.path("key").asText(),
                    parseModifiers(actionNode.path("modifiers")));
            case "scroll" -> actionExecutor.scroll(
                    extractRef(actionNode, "target", "ref"),
                    actionNode.path("deltaY").asInt());
            case "doubleClick" -> actionExecutor.doubleClick(
                    extractRef(actionNode, "target", "ref"),
                    extractDouble(actionNode.path("x")),
                    extractDouble(actionNode.path("y")));
            case "mousePressed" -> actionExecutor.mousePressed(
                    extractRef(actionNode, "target", "ref"),
                    actionNode.path("button").asText("PRIMARY"),
                    extractDouble(actionNode.path("x")),
                    extractDouble(actionNode.path("y")));
            case "mouseReleased" -> actionExecutor.mouseReleased(
                    extractRef(actionNode, "target", "ref"),
                    actionNode.path("button").asText("PRIMARY"),
                    extractDouble(actionNode.path("x")),
                    extractDouble(actionNode.path("y")));
            case "drag" -> actionExecutor.drag(
                    extractRef(actionNode, "from", "ref"),
                    extractDouble(actionNode.path("from").path("x")),
                    extractDouble(actionNode.path("from").path("y")),
                    extractRef(actionNode, "to", "ref"),
                    extractDouble(actionNode.path("to").path("x")),
                    extractDouble(actionNode.path("to").path("y")),
                    actionNode.path("button").asText("PRIMARY"));
            default -> ActionExecutor.ActionResult.failure(type, "Unknown action type: " + type);
        };
    }

    /**
     * Extracts a NodeRef from a nested path in the JSON node.
     * 
     * @param node      the root node
     * @param pathParts the path segments to navigate (e.g., "target", "ref")
     * @return the NodeRef or null if not found
     */
    private NodeRef extractRef(JsonNode node, String... pathParts)
            throws com.fasterxml.jackson.core.JsonProcessingException {
        JsonNode current = node;
        for (String part : pathParts) {
            current = current.path(part);
        }
        if (current.isMissingNode() || current.isNull()) {
            return null;
        }
        return mapper.treeToValue(current, NodeRef.class);
    }

    private Double extractDouble(JsonNode node) {
        if (node == null || node.isMissingNode() || node.isNull()) {
            return null;
        }
        return node.asDouble();
    }

    public Object executeScreenshot(JsonNode input) throws Exception {
        var stageIndex = input.path("stageIndex").asInt(-1);
        var base64 = actionExecutor.screenshot(stageIndex);
        if (base64 == null) {
            return McpError.of(ErrorCode.MCP_UI_INTERNAL, "Failed to capture screenshot");
        }
        return Map.of("contentType", "image/png", "dataBase64", base64);
    }

    public Object wrapException(Exception e) {
        if (e instanceof FxTimeoutException) {
            return McpError.of(ErrorCode.MCP_UI_TIMEOUT, "UI operation timed out: " + e.getMessage());
        }
        LOG.log(Level.WARNING, "Error executing UI tool", e);
        return McpError.of(ErrorCode.MCP_UI_INTERNAL, e.getMessage());
    }

    // --- Helper methods for cleaner JSON parsing ---

    private String getTextOrDefault(JsonNode node, String field, String defaultValue) {
        var value = node.path(field).textValue();
        return value != null ? value : defaultValue;
    }

    private boolean getBooleanOrDefault(JsonNode node, String field, boolean defaultValue) {
        if (node.has(field)) {
            return node.path(field).asBoolean();
        }
        return defaultValue;
    }

    private SceneGraphSnapshotter.StageMode parseStageMode(String mode) {
        return switch (mode.toLowerCase()) {
            case "primary" -> SceneGraphSnapshotter.StageMode.PRIMARY;
            case "all" -> SceneGraphSnapshotter.StageMode.ALL;
            default -> SceneGraphSnapshotter.StageMode.FOCUSED;
        };
    }

    private Set<String> parseStringSet(JsonNode node) {
        if (node == null || !node.isArray()) {
            return Set.of();
        }
        var result = new LinkedHashSet<String>();
        for (var val : node) {
            if (val.isTextual() && !val.asText().isBlank()) {
                result.add(val.asText());
            }
        }
        return result;
    }

    private List<String> parseModifiers(JsonNode node) {
        if (node == null || !node.isArray()) {
            return List.of();
        }
        var result = new ArrayList<String>();
        for (var val : node) {
            if (val.isTextual() && !val.asText().isBlank()) {
                result.add(val.asText());
            }
        }
        return result;
    }
}
