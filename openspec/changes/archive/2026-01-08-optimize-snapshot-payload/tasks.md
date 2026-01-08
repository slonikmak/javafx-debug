## 1. Implementation
- [x] 1.1 Implement `TreeFormatter` class to convert scene graph to compact text tree.
- [x] 1.2 Refactor `SceneGraphSnapshotter` to support a "skeleton" mode (uid + type only).
- [x] 1.3 Update `UiToolsService.executeGetSnapshot` to use the new skeleton mode and formatter.
- [x] 1.4 Update `UiToolsService.executeGetNode` to accept a list of fields/properties to return.
- [x] 1.5 Update `McpToolAdapter` to populate `content` with text tree and `structuredContent` with lightweight JSON.
- [x] 1.6 Add unit tests for `TreeFormatter` and the new snapshot modes.
- [x] 1.7 Verify `ui_get_snapshot` output size reduction with a demo app.
