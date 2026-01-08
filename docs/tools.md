# JavaFX Debug Tools Reference

This document describes the MCP tools available for interacting with and debugging JavaFX applications.

## System Overview

You are connected to a JavaFX application via an MCP server. You can inspect the **Scene Graph** (UI tree), search for specific elements, and perform UI actions (clicks, typing, etc.).

### Core Concepts

1.  **Scene Graph**: Hierarchical tree of UI components (Nodes).
2.  **NodeRef**: A reference to a node. Includes:
    *   `uid`: A stable identifier (e.g., `u-123`). **Always prefer UIDs for actions.**
    *   `path`: A structural path (e.g., `/stages[0]/scene/root/VBox[0]/Button[2]`). Useful for debugging structure but less stable than UIDs.
3.  **Stages**: Top-level windows. By default, most tools use the `focused` stage.

---

## Available Tools

### 1. `ui_get_snapshot`
**Purpose**: Get the current state of the UI tree.
*   **Key Inputs**:
    *   `stage`: `focused` (default), `primary`, or `all`.
    *   `mode`: `compact` (default) or `full`.
    *   `depth`: Max traversal depth.
    *   `includeControlInternals`: `false` (default) to hide internal nodes of standard controls (e.g. `Button` skin), `true` to show everything.
    *   `include`: Object to toggle specific fields (`bounds`, `properties`, `localToScreen`, etc.).
*   **Output**:
    *   `content`: A concise **Text Tree** representation of the UI (optimized for LLM reading).
    *   `structuredContent`: A JSON object mirroring the tree structure (for programmatic use).
*   **Best Practice**: Rely on the text `content` for understanding the UI structure. It is token-efficient. Only request `includeControlInternals: true` if you are debugging custom control skins.

**Example Request:**
```json
{
  "tool": "ui_get_snapshot",
  "input": {
    "stage": "focused",
    "mode": "compact",
    "includeControlInternals": false,
    "include": {
      "bounds": true
    }
  }
}
```

### 2. `ui_query`
**Purpose**: Find specific elements without scanning the whole tree.
*   **Selectors**:
    *   `css`: standard JavaFX CSS selectors (e.g., `#myButton`, `.label`).
    *   `text`: Search by visible text (exact or contains).
    *   `predicate`: Complex filtering (id, type, visible, enabled).
*   **Best Practice**: Use `text` query to find buttons or labels by their visible names.

**Example (CSS):**
```json
{
  "tool": "ui_query",
  "input": {
    "scope": { "stage": "focused" },
    "selector": { "css": "#myButton" },
    "limit": 10
  }
}
```

**Example (Text):**
```json
{
  "tool": "ui_query",
  "input": {
    "selector": { "text": "Submit", "match": "contains" },
    "limit": 10
  }
}
```

### 3. `ui_get_node`
**Purpose**: Get details for a single node.
*   **Key Inputs**:
    *   `ref`: The node reference (`uid` or `path`).
    *   `fields`: List of specific fields to retrieve (e.g., `["bounds", "properties"]`).
    *   `properties`: List of specific JavaFX properties to retrieve (e.g., `["text", "visible"]`).
*   **Best Practice**: Use this when you have a `uid` from a snapshot and need specific details. Use `fields` and `properties` to keep the response small.

**Example:**
```json
{
  "tool": "ui_get_node",
  "input": {
    "ref": { "uid": "u-123" },
    "fields": ["bounds", "properties"],
    "properties": ["text", "disabled"]
  }
}
```

### 4. `ui_perform`
**Purpose**: Interact with the UI.
*   **Actions**: 
    *   `focus` - Focus a node
    *   `click` - Single click on a node
    *   `doubleClick` - Double-click with proper clickCount
    *   `typeText` - Type text into focused element
    *   `setText` - Set text directly on TextInputControl
    *   `pressKey` - Press a key with optional modifiers
    *   `scroll` - Scroll on a node
    *   `mousePressed` / `mouseReleased` - Granular mouse control
    *   `drag` - Drag from source to target
*   **Batching**: You can send multiple actions in one call.
*   **Best Practice**: Always set `awaitUiIdle: true` (default) to ensure the UI has processed your interaction before you take the next snapshot.

**Example (Basic):**
```json
{
  "tool": "ui_perform",
  "input": {
    "awaitUiIdle": true,
    "actions": [
      { "type": "focus", "target": { "ref": { "uid": "u-1" } } },
      { "type": "setText", "target": { "ref": { "uid": "u-2" } }, "text": "Hello" },
      { "type": "click", "target": { "ref": { "uid": "u-3" } } }
    ]
  }
}
```

**Example (Double-click):**
```json
{
  "actions": [
    { "type": "doubleClick", "target": { "ref": { "uid": "u-5" } } }
  ]
}
```

**Example (Press Key with Modifiers):**
```json
{
  "actions": [
    { "type": "pressKey", "key": "S", "modifiers": ["CONTROL"] }
  ]
}
```

**Example (Drag):**
```json
{
  "actions": [
    { 
      "type": "drag", 
      "from": { "ref": { "uid": "u-10" } },
      "to": { "x": 500, "y": 300 },
      "button": "PRIMARY"
    }
  ]
}
```

### 5. `ui_screenshot`
**Purpose**: Visual confirmation.
*   **Output**: Base64 encoded PNG.
*   **Best Practice**: Take a screenshot after a complex interaction to verify the UI state visually.

**Example:**
```json
{
  "tool": "ui_screenshot",
  "input": { "stageIndex": -1 }
}
```

---

## Interaction Strategies

### Finding a button and clicking it
1.  Call `ui_query` with `selector: { "text": "Login", "match": "contains" }`.
2.  Identify the `uid` from the match results.
3.  Call `ui_perform` with a `click` action targeting that `uid`.

### Entering text into a form
1.  Call `ui_query` with `selector: { "css": "#usernameField" }`.
2.  Call `ui_perform` with `setText` or `focus` + `typeText`.

### Debugging a missing element
If an element is missing from a `ui.getSnapshot` result:
1.  Check if `includeControlInternals` is `false` (default) and the element is inside a standard control.
2.  Call `ui_get_snapshot` with `stage: "all"` to see if it's in a different popup/window.
3.  Check visibility/opacity properties of parents.

---

## Environment Info
*   **Schema**: `mcp-javafx-ui/1.0`
*   **Platform**: JavaFX 21+ / Java 21+
*   **Transport**: HTTP (MCP)
