# Change: Optimize Snapshot Payload

## Why
The current `ui_get_snapshot` tool returns a full JSON dump of the scene graph, including bounds, properties, and other details for every node. For large applications, this payload is enormous, consuming excessive tokens and slowing down LLM interactions.

## What Changes
- **Refactor `ui_get_snapshot`**:
  - Change the `content` output to a compact **Text Tree** format (e.g., indented tree with `type` and `uid`).
  - Retain structured data in `structuredContent` but strip heavy fields (bounds, properties) by default.
  - This allows the LLM to "see" the structure cheaply and drill down later.
- **Enhance `ui_get_node`**:
  - Add support for selective field retrieval (e.g., `fields: ["bounds", "text"]`).
  - Allow fetching specific properties only when needed.
- **Update `McpToolAdapter`**:
  - Implement dual-format output: concise text for LLM consumption, structured JSON for tool consumers.

## Impact
- **Affected Specs**: `mcp-ui` (new capability)
- **Affected Code**:
  - `UiToolsService.java`: Update snapshot logic to support lightweight mode.
  - `McpToolAdapter.java`: Add text tree formatting and response construction.
  - `SceneGraphSnapshotter.java`: Optimize traversal for lightweight snapshots.
