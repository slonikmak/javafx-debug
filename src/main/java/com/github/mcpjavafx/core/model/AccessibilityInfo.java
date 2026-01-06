package com.github.mcpjavafx.core.model;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * Accessibility information for a UI node.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record AccessibilityInfo(
        String role,
        String help) {
}
