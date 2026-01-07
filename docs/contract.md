# MCP JavaFX Contract

Below is the compact but complete spec for the "MCP ⇄ JavaFX Introspection/Control" contract: data types (UI tree snapshot), node addressing, selector semantics, tool set, errors, threading guarantees, and minimal determinism requirements.

---

## 0) Goals and Principles

1. **Stability**: LLM must be able to re-request snapshot and match elements between snapshots.
2. **Security**: Server accessible locally by default; introspection and control under debug flag.
3. **FX-thread correctness**: All UI reads/writes execute on **JavaFX Application Thread**.
4. **Reproducibility**: Snapshot must be returned in deterministic order.

---

## 1) Identification and Node Addressing

### 1.1 `nodeRef` (Stable Node Reference)

Node must have a **stable identifier** within application lifecycle (or at least between two adjacent snapshots).

`nodeRef` = object:

* `path`: string — **Canonical path** in tree (see below)
* `uid`: string | null — Stable UID, if available (recommended)
* `hint`: object? — Hints for recovery (id, type, text)

**Recommendation for `uid`:**

* If `Node.getProperties()` has your key (e.g. `"mcp.uid"`), use it.
* Otherwise generate UID on first discovery and save to `node.getProperties()`.

### 1.2 Canonical `path`

String format:
`/stages[{stageIndex}]/scene/root/{type}[{n}]/...`

Where:

* `stageIndex` — Stage index in list sorted by `title`, then `hashCode` (determinism).
* `{type}` — Simple class name (`Button`, `VBox`, `TextField`).
* `{n}` — Ordinal index among **siblings of same type** (0-based) in `Parent.getChildrenUnmodifiable()`.

Example:
`/stages[0]/scene/root/VBox[0]/HBox[1]/Button[0]`

### 1.3 Selectors (Search)

Support minimum 2 modes:

* `css`: string — JavaFX selector (`#id`, `.class`, `Button`, `VBox > Button`, etc.), resolved via `Scene.lookupAll(...)`
* `text`: string — "Search by visible text" (Labeled/Text/Tab etc.)
* `predicate`: object — Extensible condition (see below)

---

## 2) UI Snapshot: Data Types

### 2.1 `UiSnapshot`

```json
{
  "schema": "mcp-javafx-ui/1.0",
  "capturedAt": "2026-01-05T12:34:56.789Z",
  "app": {
    "pid": 12345,
    "javaVersion": "21.0.2",
    "javafxVersion": "21.0.2",
    "mainClass": "com.example.App",
    "debugFlags": ["mcpEnabled"]
  },
  "focus": {
    "focusedNode": { "path": "...", "uid": "..." },
    "focusedWindow": { "stageIndex": 0 }
  },
  "stages": [
    {
      "stageIndex": 0,
      "title": "Main",
      "showing": true,
      "focused": true,
      "x": 100.0,
      "y": 50.0,
      "width": 1200.0,
      "height": 800.0,
      "scene": {
        "stylesheets": ["app.css"],
        "root": { "...UiNode..." }
      }
    }
  ]
}
```

### 2.2 `UiNode`

```json
{
  "ref": { "path": "...", "uid": "..." },
  "type": "Button",
  "module": "javafx.controls",
  "id": "okButton",
  "styleClass": ["button", "primary"],
  "pseudoClass": ["hover", "focused"],
  "visible": true,
  "managed": true,
  "disabled": false,
  "opacity": 1.0,
  "layout": {
    "boundsInParent": { "minX":0,"minY":0,"width":80,"height":32 },
    "boundsInScene":  { "minX":450,"minY":700,"width":80,"height":32 },
    "localToScreen":  { "x":900,"y":900,"width":80,"height":32 }
  },
  "text": {
    "label": "OK",
    "prompt": null
  },
  "value": {
    "text": null,
    "selected": null,
    "checked": null
  },
  "accessibility": {
    "role": "BUTTON",
    "help": null
  },
  "fx": {
    "properties": {
      "tooltip": "Confirm",
      "userData": null
    }
  },
  "children": []
}
```

### 2.3 Serialization Norms/Rules

* `children`:

  * Only for `Parent` and `Skin`-visible nodes **at your choice** (see virtualization below).
  * Order: **As in `getChildrenUnmodifiable()`**, no sorting.
* `pseudoClass`: Cannot get enum of known pseudo-classes directly; acceptable:

  * Store only what you calculate/know (e.g. `focused`, `hover`, `pressed`, `selected`, `disabled`) via node/control API;
  * Or leave empty.
* `localToScreen`:

  * Optional, but very useful for coordinate clicks.
* `module`:

  * `node.getClass().getModule().getName()` if available.

---

## 3) Virtualized Controls (TableView/ListView/TreeView)

### 3.1 Minimal Contract

For following node types add `virtualization` section:

* `ListView`, `TableView`, `TreeView`, `TreeTableView`

Example:

```json
"virtualization": {
  "kind": "TableView",
  "itemsCount": 1000,
  "visibleRange": { "from": 20, "to": 45 },
  "selectedIndices": [22],
  "focusedIndex": 22,
  "columns": [
    { "id": "nameCol", "text": "Name" }
  ],
  "visibleCells": [
    {
      "index": 22,
      "rowRef": { "path": "...", "uid": "..." },
      "cells": [
        { "columnId": "nameCol", "text": "Alice" }
      ]
    }
  ]
}
```

**Meaning:** LLM sees "what is really on screen" and can refer to `index/columnId` for actions.

---

## 4) MCP Tools: Mandatory Set

Below are tool signatures (JSON input/output).

Note on tool names: In documentation it's often convenient to say "logically" (`ui.getSnapshot`),
but MCP server in this library exports names in `snake_case`:

* `ui_get_snapshot` (logically: `ui.getSnapshot`)
* `ui_query` (logically: `ui.query`)
* `ui_get_node` (logically: `ui.getNode`)
* `ui_perform` (logically: `ui.perform`)
* `ui_screenshot` (logically: `ui.screenshot`)

### 4.1 `ui_get_snapshot`

**Input**

```json
{
  "stage": "focused|primary|all",
  "stageIndex": 0,
  "mode": "full|compact",
  "depth": 50,
  "include": {
    "bounds": true,
    "localToScreen": true,
    "properties": false,
    "virtualization": true,
    "accessibility": false
  }
}
```

**Meaning of `mode`:**

* `mode="full"` — Detailed snapshot, defaults taken from server config (`snapshotDefaults`).
* `mode="compact"` — "LLM-friendly" mode: smaller depth (if `depth` not set) and heavy sections disabled by default (bounds/localToScreen/properties/virtualization/accessibility).

`include.*` and `depth` (if passed) override mode defaults.

**Output**

* `UiSnapshot`

**Errors**

* `MCP_UI_NOT_ENABLED`
* `MCP_UI_NO_STAGES`
* `MCP_UI_TIMEOUT`

### 4.2 `ui_query`

Searches nodes and returns "short" descriptions + references.

**Input**

```json
{
  "scope": { "stage": "focused|index", "stageIndex": 0 },
  "selector": {
    "css": "#okButton",
    "text": null,
    "match": "contains|equals|regex",
    "predicate": null
  },
  "limit": 50
}
```

`scope`:

* `stage: "focused"` (default) — Search in active window.
* `stage: "index"` + `stageIndex` — Search in specific stage.

`selector`:

* `css` — JavaFX CSS selector (via `Scene.lookupAll`).
* `text` — Search by displayed text (`Labeled`, `TextInputControl`, `Text`).
  `match` controls comparison (case-insensitive for `contains`, and `regex` — by pattern).
* `predicate` — Structured filter.

**Output**

```json
{
  "matches": [
    {
      "ref": { "path": "...", "uid": "..." },
      "type": "Button",
      "id": "okButton",
      "summary": "Button[text=OK]",
      "layout": { "boundsInScene": { "minX": 0, "minY": 0, "width": 0, "height": 0 } }
    }
  ]
}
```

### 4.3 `ui_get_node`

Get details for a node.

**Input**

```json
{ "ref": { "path": "...", "uid": "..." }, "includeChildren": false }
```

**Output**

* `UiNode`

**Errors**

* `MCP_UI_NODE_NOT_FOUND`
* `MCP_UI_STALE_REF` (if path/uid not resolved)

### 4.4 `ui_perform`

Unified actions (convenient for LLM).

**Input**

```json
{
  "actions": [
    { "type": "focus", "target": { "ref": { "path": "...", "uid": "..." } } },
    { "type": "click", "target": { "ref": { "path": "...", "uid": "..." } } },
    { "type": "click", "x": 100.0, "y": 200.0 },
    { "type": "typeText", "text": "Hello" },
    { "type": "setText", "target": { "ref": { "path": "...", "uid": "..." } }, "text": "Hello" },
    { "type": "pressKey", "key": "ENTER", "modifiers": ["CTRL"] },
    { "type": "scroll", "target": { "ref": { "path": "...", "uid": "..." } }, "deltaY": -400 }
  ],
  "awaitUiIdle": true,
  "timeoutMs": 5000
}
```

Node addressing: `target.ref.uid` preferred (more stable), `path` — fallback.

**Output**

```json
{
  "results": [
    { "ok": true, "type": "focus" },
    { "ok": true, "type": "click" },
    { "ok": true, "type": "typeText" }
  ]
}
```

**Errors**

* `MCP_UI_ACTION_FAILED` (+ `details`)
* `MCP_UI_TIMEOUT`

### 4.5 `ui_screenshot` (Optional but highly useful)

**Input**

```json
{ "stageIndex": 0 }
```

**Output**

```json
{ "contentType": "image/png", "dataBase64": "..." }
```

---

## 5) Error Model (Unified Format)

Any tool on error returns:

```json
{
  "error": {
    "code": "MCP_UI_NODE_NOT_FOUND",
    "message": "Node not found for uid=... path=...",
    "details": { "ref": { "path": "...", "uid": "..." } }
  }
}
```

Codes (minimum):

* `MCP_UI_NOT_ENABLED`
* `MCP_UI_NO_STAGES`
* `MCP_UI_NODE_NOT_FOUND`
* `MCP_UI_STALE_REF`
* `MCP_UI_ACTION_FAILED`
* `MCP_UI_TIMEOUT`
* `MCP_UI_INTERNAL`

---

## 6) Threading, "UI idle" Waiting, Timings

### 6.1 Guarantees

* All actions and UI reading execute on FX-thread.
* For `awaitUiIdle=true` server must wait for:

  * Execution of all `Platform.runLater` tasks you submitted,
  * And **pulse** of JavaFX, if possible (or equivalent of "next frame").

### 6.2 Practical "idle" Mechanism

Acceptable minimal implementation:

* After `perform` do:

  * `Platform.runLater` → `CompletableFuture.complete`
  * Then small delay 1 pulse: `AnimationTimer` for one tick or `Platform.runLater` twice.
    Not ideal, but sufficient for debugging.

---

## 7) Schema Versioning

* Field `schema`: `"mcp-javafx-ui/1.0"`
* Any incompatible change → `2.0`
* Add extensions via new fields, not breaking old ones.

---

## 8) Security and Modes

Recommended behavior:

* By default tools return `MCP_UI_NOT_ENABLED`.
* Enable only if:

  * `-Dmcp.ui=true` and/or `--add-opens` as needed,
  * Restriction to loopback,
  * Token/secret (at least "one-time" in logs on start).

---

## 9) Minimal "predicate" Conditions (If you decide to support)

```json
{
  "typeIs": ["Button", "TextField"],
  "idEquals": "okButton",
  "styleClassHas": "primary",
  "textContains": "OK",
  "visible": true,
  "enabled": true
}
```
