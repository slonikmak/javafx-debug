package com.github.mcpjavafx.mcp;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.mcpjavafx.api.McpJavafxConfig;
import com.github.mcpjavafx.api.SnapshotOptions;
import com.github.mcpjavafx.core.actions.ActionExecutor;
import com.github.mcpjavafx.core.capture.SceneGraphSnapshotter;
import com.github.mcpjavafx.core.capture.TreeFormatter;
import com.github.mcpjavafx.core.fx.FxTimeoutException;
import com.github.mcpjavafx.core.model.ErrorCode;
import com.github.mcpjavafx.core.model.McpError;
import com.github.mcpjavafx.core.model.NodeRef;
import com.github.mcpjavafx.core.query.NodeQueryService;
import com.github.mcpjavafx.core.query.QueryPredicate;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Common service for executing MCP tools, shared between Registry and Adapter.
 */
public class UiToolsService {

    private static final Logger LOG = Logger.getLogger(UiToolsService.class.getName());
    private static final int COMPACT_DEFAULT_DEPTH = 8;
    private static final Set<String> SNAPSHOT_NODE_FIELDS = Set.of("ref", "type", "id", "text", "value", "children");

    private final McpJavafxConfig config;
    private final ObjectMapper mapper;
    private final SceneGraphSnapshotter snapshotter;
    private final NodeQueryService queryService;
    private final ActionExecutor actionExecutor;
    private final TreeFormatter treeFormatter;

    public UiToolsService(McpJavafxConfig config, ObjectMapper mapper) {
        this.config = config;
        this.mapper = mapper;
        this.snapshotter = new SceneGraphSnapshotter(config.fxTimeoutMs());
        this.queryService = new NodeQueryService(config.fxTimeoutMs());
        this.actionExecutor = new ActionExecutor(config.fxTimeoutMs(), queryService);
        this.treeFormatter = new TreeFormatter();
    }

    public Object executeGetSnapshot(JsonNode input) throws Exception {
        var stageModeStr = input.path("stage").textValue() != null ? input.path("stage").textValue() : "focused";
        var stageMode = parseStageMode(stageModeStr);
        var stageIndex = input.path("stageIndex").asInt(0);
        var mode = (input.path("mode").textValue() != null ? input.path("mode").textValue() : "compact").toLowerCase();
        var compact = !"full".equals(mode);

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
        var includeControlInternals = includeNode.has("controlInternals")
                ? includeNode.path("controlInternals").asBoolean()
                : (compact ? false : false);

        var skeleton = compact
                && !includeBounds
                && !includeLocalToScreen
                && !includeProperties
                && !includeVirtualization
                && !includeAccessibility;

        var options = SnapshotOptions.builder()
                .depth(depth)
                .includeBounds(includeBounds)
                .includeLocalToScreen(includeLocalToScreen)
                .includeProperties(includeProperties)
                .includeVirtualization(includeVirtualization)
                .includeAccessibility(includeAccessibility)
                .includeControlInternals(includeControlInternals)
                .skeleton(skeleton)
                .build();

        var snapshot = snapshotter.capture(stageMode, stageIndex, options);

        if (snapshot.stages().isEmpty()) {
            return McpError.of(ErrorCode.MCP_UI_NO_STAGES, "No stages found");
        }

        var lightweight = TreeFormatter.lightweightFrom(snapshot);
        var tree = treeFormatter.format(lightweight);
        var structured = NodeFieldFilter.filterSnapshot(mapper, lightweight, SNAPSHOT_NODE_FIELDS);
        return new SnapshotResult(tree, structured);
    }

    public Object executeQuery(JsonNode input) throws Exception {
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
            var matchMode = selectorNode.path("match").textValue() != null ? selectorNode.path("match").textValue() : "contains";
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

            ActionExecutor.ActionResult res = switch (type) {
                case "click" -> actionExecutor.click(extractTargetRef(actionNode));
                case "focus" -> actionExecutor.focus(extractTargetRef(actionNode));
                case "setText" -> actionExecutor.setText(extractTargetRef(actionNode), actionNode.path("text").asText());
                case "typeText" -> actionExecutor.typeText(actionNode.path("text").asText());
                case "scroll" -> actionExecutor.scroll(extractTargetRef(actionNode), actionNode.path("deltaY").asInt());
                default -> ActionExecutor.ActionResult.failure(type, "Unknown action type: " + type);
            };
            results.add(res);
        }

        return Map.of("results", results);
    }

    private NodeRef extractTargetRef(JsonNode actionNode) throws com.fasterxml.jackson.core.JsonProcessingException {
        var refNode = actionNode.path("target").path("ref");
        if (refNode.isMissingNode() || refNode.isNull()) {
            return null;
        }
        return mapper.treeToValue(refNode, NodeRef.class);
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
}
