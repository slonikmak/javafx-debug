package com.github.mcpjavafx.mcp;

/**
 * Return payload for ui_get_snapshot combining the text tree content
 * with the structured snapshot payload to place in structuredContent.
 */
public record SnapshotResult(String tree, Object structured) {
}
