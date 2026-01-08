package com.github.mcpjavafx.core.model;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * Reference to a UI node for stable identification across snapshots.
 *
 * @param path canonical path in tree, e.g. "/stages[0]/scene/root/VBox[0]/Button[1]"
 * @param uid  stable UID stored in node properties
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record NodeRef(
    String path,
    String uid
) {
    public static final String UID_PROPERTY_KEY = "mcp.uid";
}
