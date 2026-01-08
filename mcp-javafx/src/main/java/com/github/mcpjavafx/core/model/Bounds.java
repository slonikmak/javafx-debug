package com.github.mcpjavafx.core.model;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * Bounds information for a UI element.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record Bounds(
        double minX,
        double minY,
        double width,
        double height) {
    public static Bounds from(javafx.geometry.Bounds fxBounds) {
        return new Bounds(
                fxBounds.getMinX(),
                fxBounds.getMinY(),
                fxBounds.getWidth(),
                fxBounds.getHeight());
    }
}
