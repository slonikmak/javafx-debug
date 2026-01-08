package com.github.mcpjavafx.core.fx;

import com.github.mcpjavafx.core.model.NodeRef;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.stage.Stage;
import javafx.stage.Window;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Service for managing node references (UIDs and paths).
 */
public final class NodeRefService {

    private static final AtomicLong UID_COUNTER = new AtomicLong(0);

    /**
     * Creates or retrieves a NodeRef for the given node.
     */
    public NodeRef forNode(Node node) {
        var stages = getSortedStages();
        var path = buildPath(node, stages);
        var uid = getOrCreateUid(node);
        return new NodeRef(path, uid);
    }

    /**
     * Creates or retrieves a NodeRef for the given node within a known stage context.
     */
    public NodeRef forNode(Node node, int stageIndex) {
        var path = buildPath(node, stageIndex);
        var uid = getOrCreateUid(node);
        return new NodeRef(path, uid);
    }

    /**
     * Returns the UID for a node.
     */
    public String getOrCreateUid(Node node) {
        var props = node.getProperties();
        var existing = props.get(NodeRef.UID_PROPERTY_KEY);
        if (existing instanceof String uid) {
            return uid;
        }
        var newUid = "u-" + Long.toString(UID_COUNTER.incrementAndGet(), 36);
        props.put(NodeRef.UID_PROPERTY_KEY, newUid);
        return newUid;
    }

    /**
     * Returns all showing stages sorted deterministically.
     */
    public List<Stage> getSortedStages() {
        return Window.getWindows().stream()
                .filter(w -> w instanceof Stage stage && stage.isShowing())
                .map(w -> (Stage) w)
                .sorted(Comparator
                        .comparing((Stage s) -> s.getTitle() == null ? "" : s.getTitle())
                        .thenComparingInt(System::identityHashCode))
                .toList();
    }

    public String buildPath(Node node, List<Stage> stages) {
        if (node.getScene() == null) {
            return null;
        }

        var window = node.getScene().getWindow();
        if (!(window instanceof Stage stage)) {
            return null;
        }

        int stageIndex = stages.indexOf(stage);
        if (stageIndex < 0) {
            return null;
        }

        return buildPath(node, stageIndex);
    }

    public String buildPath(Node node, int stageIndex) {
        var parts = new ArrayList<String>();
        var current = node;

        while (current != null) {
            var parent = current.getParent();
            if (parent != null) {
                int index = getChildIndex(parent, current);
                parts.add(0, current.getClass().getSimpleName() + "[" + index + "]");
            } else {
                parts.add(0, "root");
            }
            current = parent;
        }

        parts.add(0, "scene");
        parts.add(0, "stages[" + stageIndex + "]");

        return "/" + String.join("/", parts);
    }

    private int getChildIndex(Parent parent, Node child) {
        var typeName = child.getClass().getSimpleName();
        int typeIndex = 0;

        for (var c : parent.getChildrenUnmodifiable()) {
            if (c == child) {
                return typeIndex;
            }
            if (c.getClass().getSimpleName().equals(typeName)) {
                typeIndex++;
            }
        }
        return 0;
    }
}
