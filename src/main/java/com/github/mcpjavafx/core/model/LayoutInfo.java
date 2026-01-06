package com.github.mcpjavafx.core.model;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * Layout information for a UI node.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record LayoutInfo(
                Bounds boundsInParent,
                Bounds boundsInScene,
                ScreenBounds localToScreen) {
}
