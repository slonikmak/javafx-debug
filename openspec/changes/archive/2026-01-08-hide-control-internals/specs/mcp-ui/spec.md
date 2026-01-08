## ADDED Requirements
### Requirement: Hide Control Internals
The system SHALL hide internal implementation details of standard JavaFX controls in the snapshot.

#### Scenario: Standard controls are leaves
- **WHEN** a snapshot is captured for a `TextField` or `Button`
- **THEN** the snapshot does NOT include internal children (like `Text`, `Path`, `Pane`)
- **AND** the control appears as a leaf node in the tree

#### Scenario: Override internal hiding
- **WHEN** `ui_get_snapshot` is called with `includeControlInternals=true`
- **THEN** the snapshot includes all children, including internal implementation nodes
