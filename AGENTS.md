<!-- OPENSPEC:START -->
# OpenSpec Instructions

These instructions are for AI assistants working in this project.

Always open `@/openspec/AGENTS.md` when the request:
- Mentions planning or proposals (words like proposal, spec, change, plan)
- Introduces new capabilities, breaking changes, architecture shifts, or big performance/security work
- Sounds ambiguous and you need the authoritative spec before coding

Use `@/openspec/AGENTS.md` to learn:
- How to create and apply change proposals
- Spec format and conventions
- Project structure and guidelines

Keep this managed block so 'openspec update' can refresh the instructions.

<!-- OPENSPEC:END -->

# AGENTS.md — MCP JavaFX Debug Server

This document is for LLM Agents that will use the `mcp-javafx-debug` tools to interact with and debug JavaFX applications.

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

### 1. `ui_get_snapshot` (legacy alias: `ui.getSnapshot`)
**Purpose**: Get the current state of the UI tree.
*   **Key Inputs**: `stage` (focused/primary/all), `mode` (full/compact), `depth`, `include` (bounds, properties, etc.).
*   **Best Practice**: Prefer `mode: "compact"` first; only request full bounds/properties when needed.

### 2. `ui_query` (legacy alias: `ui.query`)
**Purpose**: Find specific elements without scanning the whole tree.
*   **Selectors**:
    *   `css`: standard JavaFX CSS selectors (e.g., `#myButton`, `.label`).
    *   `text`: Search by visible text (exact or contains).
    *   `predicate`: Complex filtering (id, type, visible, enabled).
*   **Best Practice**: Use `text` query to find buttons or labels by their visible names.

### 3. `ui_get_node` (legacy alias: `ui.getNode`)
**Purpose**: Get full details for a single node.
*   **Best Practice**: Use this when you have a `uid` from a previous snapshot and need deeper info (like all FX properties) that wasn't included in the summary snapshot.

### 4. `ui_perform` (legacy alias: `ui.perform`)
**Purpose**: Interact with the UI.
*   **Actions**: `focus`, `click`, `typeText` (into focused), `setText` (direct value), `pressKey`, `scroll`.
*   **Batching**: You can send multiple actions in one call.
*   **Best Practice**: Always set `awaitUiIdle: true` (default) to ensure the UI has processed your interaction before you take the next snapshot.

### 5. `ui_screenshot` (legacy alias: `ui.screenshot`)
**Purpose**: Visual confirmation.
*   **Output**: Base64 encoded PNG.
*   **Best Practice**: Take a screenshot after a complex interaction to verify the UI state visually.

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
1.  Check if `depth` was too low.
2.  Call `ui_get_snapshot` with `stage: "all"` to see if it's in a different popup/window.
3.  Check visibility/opacity properties of parents.

---

## Environment Info
*   **Schema**: `mcp-javafx-ui/1.0`
*   **Platform**: JavaFX 21+ / Java 21+
*   **Transport**: HTTP (MCP)
