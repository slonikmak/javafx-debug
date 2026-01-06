package com.github.mcpjavafx.core.model;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * Text-related information for labeled controls.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record TextInfo(
        String label,
        String prompt) {
}
