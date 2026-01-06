package com.github.mcpjavafx.core.model;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.List;

/**
 * Serializable snapshot of the JavaFX scene graph.
 *
 * @param schema     schema version
 * @param capturedAt timestamp of capture (ISO-8601 string)
 * @param app        application information
 * @param focus      focus information
 * @param stages     information about visible stages
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record UiSnapshot(
        String schema,
        String capturedAt,
        AppInfo app,
        FocusInfo focus,
        List<StageInfo> stages) {

    public static final String SCHEMA_VERSION = "1.0";

    /**
     * Application metadata.
     */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record AppInfo(
            long pid,
            String javaVersion,
            String javafxVersion,
            String mainClass,
            List<String> debugFlags) {
    }

    /**
     * Current focus state.
     */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record FocusInfo(
            NodeRef focusedNode,
            FocusedWindow focusedWindow) {
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record FocusedWindow(int stageIndex) {
    }

    /**
     * Information about a single Stage.
     */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record StageInfo(
            int stageIndex,
            String title,
            boolean showing,
            boolean focused,
            double x,
            double y,
            double width,
            double height,
            SceneInfo scene) {
    }

    /**
     * Scene information including root node.
     */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record SceneInfo(
            List<String> stylesheets,
            UiNode root) {
    }
}
