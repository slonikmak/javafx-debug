# mcp-ui Specification

## Purpose
TBD - created by archiving change optimize-snapshot-payload. Update Purpose after archive.
## Requirements
### Requirement: Compact Snapshot
The system SHALL provide a token-efficient snapshot of the UI scene graph.

#### Scenario: Default snapshot
- **WHEN** `ui_get_snapshot` is called without arguments
- **THEN** the `content` field contains a text-based tree representation of the scene graph
- **AND** the tree includes only `type`, `uid`, and essential labels (e.g. text content)
- **AND** the `structuredContent` field contains a lightweight JSON object structure matching the tree

#### Scenario: Text Tree Format
- **WHEN** the snapshot is formatted as text
- **THEN** it uses indentation to represent hierarchy
- **AND** it avoids JSON syntax characters (`{`, `}`, `"`) where possible
- **AND** each line represents one node

### Requirement: Selective Node Details
The system SHALL allow retrieving specific details for a node to minimize data transfer.

#### Scenario: Fetch specific fields
- **WHEN** `ui_get_node` is called with a `fields` argument (e.g., `["bounds", "properties"]`)
- **THEN** the response includes only the requested fields for the target node
- **AND** omitted fields are not present in the output

#### Scenario: Fetch specific properties
- **WHEN** `ui_get_node` is called with a `properties` list (e.g., `["text", "visible"]`)
- **THEN** the `properties` object in the response contains only the requested keys

### Requirement: Hide Control Internals
The system SHALL hide internal implementation details of standard JavaFX controls in the snapshot.

#### Scenario: Standard controls are leaves
- **WHEN** a snapshot is captured for a `TextField` or `Button`
- **THEN** the snapshot does NOT include internal children (like `Text`, `Path`, `Pane`)
- **AND** the control appears as a leaf node in the tree

#### Scenario: Override internal hiding
- **WHEN** `ui_get_snapshot` is called with `includeControlInternals=true`
- **THEN** the snapshot includes all children, including internal implementation nodes

