package com.github.mcpjavafx.mcp;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.mcpjavafx.core.capture.TreeFormatter;
import com.github.mcpjavafx.core.model.*;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class SnapshotFormattingTest {

    @Test
    void treeFormatterProducesCompactTree() {
        var child = new UiNode(
                new NodeRef("/root/Button[0]", "btn-1"),
                "Button",
                null,
                "submit",
                null,
                null,
                true,
                true,
                false,
                1.0,
                null,
                new TextInfo("Save", null),
                null,
                null,
                null,
                null,
                List.of());
        var root = new UiNode(
                new NodeRef("/root", "root-uid"),
                "VBox",
                null,
                null,
                null,
                null,
                true,
                true,
                false,
                1.0,
                null,
                null,
                null,
                null,
                null,
                null,
                List.of(child));
        var snapshot = new UiSnapshot(
                UiSnapshot.SCHEMA_VERSION,
                "now",
                null,
                null,
                List.of(new UiSnapshot.StageInfo(0, "Main", true, true, 0, 0, 800, 600,
                        new UiSnapshot.SceneInfo(List.of(), root))));

        var formatted = new TreeFormatter().format(snapshot);
        assertTrue(formatted.contains("Stage[0] Main (focused)"));
        assertTrue(formatted.contains("VBox (uid=root-uid)"));
        assertTrue(formatted.contains("Button#submit (uid=btn-1) \"Save\""));
        assertFalse(formatted.contains("styleClass"));
    }

    @Test
    void nodeFieldFilterKeepsOnlyRequestedFields() {
        var node = new UiNode(
                new NodeRef("/root/Button[0]", "btn-2"),
                "Button",
                "moduleA",
                "ok",
                List.of("primary"),
                List.of("hover"),
                true,
                true,
                false,
                0.9,
                null,
                new TextInfo("OK", ""),
                null,
                null,
                new FxProperties("tip", "data", Map.of("text", "hello", "visible", true)),
                null,
                List.of());

        var mapper = new ObjectMapper();
        var filtered = NodeFieldFilter.filterNode(mapper, node, Set.of("text", "fx"), Set.of("visible"));

        assertEquals("Button", filtered.get("type"));
        assertTrue(filtered.containsKey("ref"));
        assertTrue(filtered.containsKey("text"));
        assertFalse(filtered.containsKey("styleClass"));
        assertFalse(filtered.containsKey("visible"));

        @SuppressWarnings("unchecked")
        var fx = (Map<String, Object>) filtered.get("fx");
        assertNotNull(fx);
        @SuppressWarnings("unchecked")
        var props = (Map<String, Object>) fx.get("properties");
        assertEquals(1, props.size());
        assertTrue(props.containsKey("visible"));
        assertFalse(props.containsKey("text"));
    }

    @Test
    void snapshotFilterProducesLightweightStructuredContent() {
        var node = new UiNode(
                new NodeRef("/root/Label[0]", "lbl-1"),
                "Label",
                "mod",
                "labelId",
                List.of("style"),
                List.of(),
                true,
                true,
                false,
                1.0,
                null,
                new TextInfo("Hello", null),
                null,
                null,
                null,
                null,
                List.of());
        var snapshot = new UiSnapshot(
                UiSnapshot.SCHEMA_VERSION,
                "now",
                null,
                null,
                List.of(new UiSnapshot.StageInfo(0, "Main", true, false, 0, 0, 800, 600,
                        new UiSnapshot.SceneInfo(List.of(), node))));

        var mapper = new ObjectMapper();
        var structured = NodeFieldFilter.filterSnapshot(mapper, snapshot, Set.of("type", "text", "children", "ref"));

        @SuppressWarnings("unchecked")
        var stages = (List<Map<String, Object>>) structured.get("stages");
        @SuppressWarnings("unchecked")
        var scene = (Map<String, Object>) stages.get(0).get("scene");
        @SuppressWarnings("unchecked")
        var root = (Map<String, Object>) scene.get("root");

        assertTrue(root.containsKey("type"));
        assertTrue(root.containsKey("text"));
        assertFalse(root.containsKey("styleClass"));
        assertFalse(root.containsKey("visible"));
    }
}
