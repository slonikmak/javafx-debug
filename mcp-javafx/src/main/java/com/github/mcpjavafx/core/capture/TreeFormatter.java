package com.github.mcpjavafx.core.capture;

import com.github.mcpjavafx.core.model.UiNode;
import com.github.mcpjavafx.core.model.UiSnapshot;

import java.util.List;
import java.util.Objects;

/**
 * Renders a UiSnapshot as a compact indented text tree for LLM-friendly output.
 */
public class TreeFormatter {

    private static final int MAX_LABEL_LENGTH = 120;

    public String format(UiSnapshot snapshot) {
        Objects.requireNonNull(snapshot, "snapshot");
        var stages = snapshot.stages();
        if (stages == null || stages.isEmpty()) {
            return "";
        }

        var sb = new StringBuilder();
        for (var stage : stages) {
            appendStage(sb, stage);
        }
        return sb.toString().stripTrailing();
    }

    private void appendStage(StringBuilder sb, UiSnapshot.StageInfo stage) {
        sb.append("Stage[").append(stage.stageIndex()).append("]");
        if (stage.title() != null && !stage.title().isEmpty()) {
            sb.append(" ").append(stage.title());
        }
        if (stage.focused()) {
            sb.append(" (focused)");
        }
        sb.append(System.lineSeparator());

        if (stage.scene() != null && stage.scene().root() != null) {
            appendNode(sb, stage.scene().root(), 1);
        }
    }

    private void appendNode(StringBuilder sb, UiNode node, int depth) {
        indent(sb, depth);
        sb.append(node.type() != null ? node.type() : "Node");

        if (node.id() != null && !node.id().isEmpty()) {
            sb.append('#').append(node.id());
        }
        if (node.ref() != null && node.ref().uid() != null) {
            sb.append(" (uid=").append(node.ref().uid()).append(')');
        }

        var label = primaryLabel(node);
        if (label != null && !label.isEmpty()) {
            sb.append(' ').append('"').append(truncate(label)).append('"');
        }
        sb.append(System.lineSeparator());

        var children = node.children();
        if (children != null) {
            for (var child : children) {
                appendNode(sb, child, depth + 1);
            }
        }
    }

    private String primaryLabel(UiNode node) {
        var text = node.text();
        if (text != null) {
            if (text.label() != null && !text.label().isEmpty()) {
                return text.label();
            }
            if (text.prompt() != null && !text.prompt().isEmpty()) {
                return text.prompt();
            }
        }
        var value = node.value();
        if (value != null && value.text() != null && !value.text().isEmpty()) {
            return value.text();
        }
        return null;
    }

    private String truncate(String label) {
        if (label.length() <= MAX_LABEL_LENGTH) {
            return label;
        }
        return label.substring(0, MAX_LABEL_LENGTH - 3) + "...";
    }

    private void indent(StringBuilder sb, int depth) {
        sb.append("  ".repeat(Math.max(depth, 0)));
    }

    public static UiSnapshot lightweightFrom(UiSnapshot snapshot) {
        Objects.requireNonNull(snapshot, "snapshot");
        if (snapshot.stages() == null) {
            return snapshot;
        }

        var stageInfos = snapshot.stages().stream()
                .map(stage -> new UiSnapshot.StageInfo(
                        stage.stageIndex(),
                        stage.title(),
                        stage.showing(),
                        stage.focused(),
                        stage.x(),
                        stage.y(),
                        stage.width(),
                        stage.height(),
                        stage.scene() != null ? new UiSnapshot.SceneInfo(
                                stage.scene().stylesheets(),
                                stripNode(stage.scene().root())
                        ) : null))
                .toList();

        return new UiSnapshot(
                snapshot.schema(),
                snapshot.capturedAt(),
                snapshot.app(),
                snapshot.focus(),
                stageInfos);
    }

    private static UiNode stripNode(UiNode node) {
        if (node == null) {
            return null;
        }
        List<UiNode> children = null;
        if (node.children() != null) {
            children = node.children().stream()
                    .map(TreeFormatter::stripNode)
                    .toList();
        }
        return new UiNode(
                node.ref(),
                node.type(),
                null,
                node.id(),
                null,
                null,
                false,
                false,
                false,
                1.0,
                null,
                node.text(),
                node.value(),
                null,
                null,
                null,
                children);
    }
}
