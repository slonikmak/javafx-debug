## ADDED Requirements

### Requirement: Double Click Action
The system SHALL provide a `doubleClick` action that generates a proper double-click event.

#### Scenario: Double click on node by UID
- **GIVEN** a valid node reference
- **WHEN** `doubleClick` action is performed
- **THEN** the target node receives two click events with `clickCount` incrementing to 2
- **AND** the interval between clicks is within the system's double-click threshold

#### Scenario: Double click respects system timing
- **GIVEN** a system with a specific double-click threshold
- **WHEN** `doubleClick` action is performed
- **THEN** the two clicks occur within the system's `awt.multiClickInterval`

---

### Requirement: Granular Mouse Event Actions
The system SHALL provide atomic mouse event actions for precise input simulation.

#### Scenario: Mouse pressed on element
- **GIVEN** a valid node reference
- **WHEN** `mousePressed` action is performed with `button: "PRIMARY"`
- **THEN** the system sends a `MouseEvent.MOUSE_PRESSED` to the target node

#### Scenario: Mouse released on element
- **GIVEN** a valid node reference
- **WHEN** `mouseReleased` action is performed
- **THEN** the system sends a `MouseEvent.MOUSE_RELEASED` to the target node

---

### Requirement: Drag Action
The system SHALL provide a composite `drag` action for moving elements.

#### Scenario: Drag from reference to coordinates
- **GIVEN** a source node reference and target coordinates (x, y)
- **WHEN** `drag` action is performed
- **THEN** the system executes: focus → mousePressed(source) → mouseMoved → mouseReleased(target)
- **AND** the source node receives `DRAG_DETECTED` event

#### Scenario: Drag from coordinates to coordinates
- **GIVEN** source coordinates (x1, y1) and target coordinates (x2, y2)
- **WHEN** `drag` action is performed
- **THEN** the system executes the drag sequence between the two points

---

## MODIFIED Requirements

### Requirement: Press Key Action
The system SHALL provide a `pressKey` action for keyboard input simulation.

#### Scenario: Press single key
- **GIVEN** a key code (e.g., `"ENTER"`)
- **WHEN** `pressKey` action is performed
- **THEN** the system sends the corresponding key event to the focused element

#### Scenario: Press key with modifiers
- **GIVEN** a key code and modifiers (e.g., `["CONTROL", "S"]`)
- **WHEN** `pressKey` action is performed
- **THEN** the system sends the key event with the specified modifiers held

#### Scenario: Press key error handling
- **GIVEN** an unrecognized key code
- **WHEN** `pressKey` action is performed
- **THEN** the system returns error `MCP_UI_ACTION_FAILED` with reason `INVALID_KEY_CODE`
