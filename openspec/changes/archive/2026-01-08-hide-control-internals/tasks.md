## 1. Implementation
- [x] 1.1 Modify `SnapshotOptions` to add `includeControlInternals` (default false).
- [x] 1.2 Update `SceneGraphSnapshotter` to check if a node is a "black box" control.
- [x] 1.3 Implement logic to skip children for black box controls unless `includeControlInternals` is true.
- [x] 1.4 Verify `TextField` and `Button` no longer show internal children in default snapshot.
