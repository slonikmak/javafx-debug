package com.github.mcpjavafx.core.model;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.Map;

/**
 * Additional JavaFX properties.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record FxProperties(
        String tooltip,
        Object userData,
        Map<String, Object> properties) {
}
