package com.github.mcpjavafx.core.model;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * Value information for input controls.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ValueInfo(
        String text,
        Boolean selected,
        Boolean checked) {
}
