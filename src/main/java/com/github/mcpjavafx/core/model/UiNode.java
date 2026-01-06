package com.github.mcpjavafx.core.model;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.List;

/**
 * Representation of a single UI node in the scene graph.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record UiNode(
        NodeRef ref,
        String type,
        String module,
        String id,
        List<String> styleClass,
        List<String> pseudoClass,
        boolean visible,
        boolean managed,
        boolean disabled,
        double opacity,
        LayoutInfo layout,
        TextInfo text,
        ValueInfo value,
        AccessibilityInfo accessibility,
        FxProperties fx,
        VirtualizationInfo virtualization,
        List<UiNode> children) {
    /**
     * Creates a short summary for query results.
     * Examples: "Button[text=OK]", "TextField[prompt=Enter name]"
     */
    public String summary() {
        var sb = new StringBuilder(type);
        var hasDetails = false;

        if (text != null && text.label() != null && !text.label().isEmpty()) {
            sb.append("[text=").append(truncate(text.label(), 20));
            hasDetails = true;
        }
        if (text != null && text.prompt() != null && !text.prompt().isEmpty()) {
            if (hasDetails) {
                sb.append(", ");
            } else {
                sb.append("[");
            }
            sb.append("prompt=").append(truncate(text.prompt(), 20));
            hasDetails = true;
        }
        if (virtualization != null) {
            if (hasDetails) {
                sb.append(", ");
            } else {
                sb.append("[");
            }
            sb.append("items=").append(virtualization.itemsCount());
            hasDetails = true;
        }
        if (hasDetails) {
            sb.append("]");
        }

        return sb.toString();
    }

    private static String truncate(String s, int maxLen) {
        if (s.length() <= maxLen) {
            return s;
        }
        return s.substring(0, maxLen - 3) + "...";
    }
}
