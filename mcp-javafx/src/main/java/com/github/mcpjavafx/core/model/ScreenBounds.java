package com.github.mcpjavafx.core.model;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * Screen coordinates with position.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ScreenBounds(
        double x,
        double y,
        double width,
        double height) {
}
