# JavaFX Debug Specification

## 1) Scope and Goals

**Goal**: The `mcp-javafx-debug` library embeds into a JavaFX application and provides MCP tools for:

* UI Introspection (Scene Graph snapshot)
* Node Search
* Simple Actions (click/focus/type/scroll/pressKey)
* (Optional) Screenshot

**Non-Goals** (for "simple variant"):

* javaagent/attach
* Full CSS computed style dump
* Deep integration with Skin internals via reflection
* Remote network access (localhost only)

---

## 2) Public API

### 2.1 Entry points

Mandatory public methods:

```java
public final class McpJavafxDebug {
  public static McpJavafxHandle install(McpJavafxConfig config);
  public static McpJavafxHandle startFromSystemProperties();
}
```

* `install(config)` — Explicit start (preferred).
* `startFromSystemProperties()` — Convenient "one-liner" start.

### 2.2 Handle

```java
public interface McpJavafxHandle extends AutoCloseable {
  McpJavafxConfig config();
  boolean isRunning();
  String endpoint(); // e.g., "http://127.0.0.1:49321"
  @Override void close();
}
```

### 2.3 Configuration

```java
public record McpJavafxConfig(
  boolean enabled,
  Transport transport,     // HTTP_LOCAL default
  String bindHost,         // "127.0.0.1" default
  int port,                // 0 = auto
  String token,            // nullable => generate and log
  boolean allowActions,    // true default in debug, false for read-only
  SnapshotOptions snapshotDefaults,
  int fxTimeoutMs,         // 5000
  int serverShutdownMs     // 2000
) {}
```

`SnapshotOptions`:

```java
public record SnapshotOptions(
  int depth,
  boolean includeBounds,
  boolean includeLocalToScreen,
  boolean includeProperties,
  boolean includeVirtualization,
  boolean includeAccessibility,
  boolean includeControlInternals // Default false
) {}
```

**System Properties** (for `startFromSystemProperties()`):

* `mcp.ui` (`true/false`)
* `mcp.transport` (`http` default)
* `mcp.bind` (default `127.0.0.1`)
* `mcp.port` (`0` default)
* `mcp.token` (optional)
* `mcp.allowActions` (`true/false`)
* `mcp.snapshot.depth`, `mcp.snapshot.bounds`, `mcp.snapshot.internals`…

---

## 3) Module Architecture (Single Jar, Distinct Packages)

One artifact, but separated internally:

* `...core.model` — DTO (Snapshot/Node/Errors)
* `...core.capture` — Scene/tree collection
* `...core.query` — Query/lookup, indexing
* `...core.actions` — Actions
* `...core.fx` — FX-thread utils, idle waiting
* `...mcp` — MCP tool registry + protocol layer
* `...transport.http` — Local server (JDK HttpServer acceptable)
* `...util` — Logging/security/token generation

---

## 4) Runtime Requirements

* Java: 17+ (preferably 21, but 17 minimum).
* JavaFX: 17+.
* No additional native libraries required.
* Do not use `com.sun.*` and reflection for "simple variant" (except optional improvements with feature-flag).

---

## 5) FX-thread Contract

### 5.1 General Rule

Any tool that reads/modifies UI MUST execute on the FX Application Thread.

### 5.2 Execution Utility

Internal contract:

```java
<T> T Fx.exec(Callable<T> action, int timeoutMs) throws FxTimeoutException;
void Fx.run(Runnable action, int timeoutMs) throws FxTimeoutException;
```

Implementation:

* If already on FX-thread → execute immediately.
* Otherwise `Platform.runLater` + `CompletableFuture` + timeout.

### 5.3 "awaitUiIdle"

Minimal implementation (sufficient for debug):

* After executing actions, perform `Fx.run(() -> {}, timeout)` twice (double runLater).
* And/or "one pulse" via `AnimationTimer` for one tick.

---

## 6) Data Model (Strictly as in Tools Contract)

### 6.1 `UiSnapshot` / `UiNode` / virtualization

Use schema from contract with version:

* `schema: "mcp-javafx-ui/1.0"`

**Mandatory fields in `UiNode`:**

* `ref.path`
* `type`
* `id` (nullable)
* `styleClass` (list, can be empty)
* `visible/managed/disabled`
* `children` (list, can be empty)

**Mandatory layout fields (if includeBounds=true):**

* `boundsInScene` minimum (width/height can be 0)

**Stable uid**: Mandatory implementation, even if only within process:

* Key in `node.getProperties()` = `"mcp.uid"`
* Format: `u-<base36 counter>` or UUID (counter preferred for readability)

**Snapshot Structure**:
* `content`: Text-based tree representation (optimized for LLM).
* `structuredContent`: JSON object structure (for tools).

---

## 7) Tree Collection (Snapshotter)

### 7.1 Stage Selection

Support modes:

* `focused` — Window with `isFocused()==true`, otherwise stageIndex=0
* `primary` — stageIndex=0
* `all` — All showing stages

### 7.2 Stage List Source

Simple method:

* `Window.getWindows()` → filter `instanceof Stage`
* Sort: `(title nulls last)`, then `System.identityHashCode(stage)`

### 7.3 Tree Traversal

* Start: `stage.getScene().getRoot()`
* DFS traversal up to `depth`
* Children: If node `instanceof Parent` → `getChildrenUnmodifiable()`
* **Control Internals**: By default, standard controls (Button, TextField, etc.) are treated as leaves. Recursion into their skin implementation is skipped unless `includeControlInternals=true`.

**Determinism**: Children order strictly as returned by JavaFX.

### 7.4 "Short Description" (Summary)

For query results:

* `Button[text=OK]`
* `TextField[text=..., prompt=...]`
* `TableView[items=1000, selected=...]`

---

## 8) Query

### 8.1 `css` query

* Use `Scene.lookupAll(css)` within selected stage.
* Return up to `limit`.

### 8.2 `text` query

* Traverse snapshot tree (or live tree on FX-thread) and match by:

  * `Labeled.getText()`
  * `TextInputControl.getText()`
  * `Text.getText()`
  * `Tab.getText()` (if accessible via TabPane — optional)

Text Normalization:

* trim
* null-safe
* contains / equals (default contains), mode set by field `match: "contains|equals|regex"` (regex can be omitted in simple variant).

### 8.3 "predicate" (Optional but Easy)

Support minimum:

* `typeIs[]`
* `idEquals`
* `styleClassHas`
* `visible/enabled`
* `textContains`

---

## 9) Actions (perform)

### 9.1 Action Set (Minimum)

* `focus(target)`
* `click(target | x,y)`
* `typeText(text)` (into active focus)
* `setText(target, text)` (for `TextInputControl`)
* `pressKey(key, modifiers[])`
* `scroll(target, deltaY)`

### 9.2 Click/Input Mechanism

Simple and "built-in":

* `javafx.scene.robot.Robot`

Coordinates:

* If `target` by ref → calculate `localToScreen` bounds, click center.
* If bounds unavailable → error `MCP_UI_ACTION_FAILED` with reason `NO_SCREEN_BOUNDS`.

Scroll:

* `Robot.scroll(amount)` or simulation via events (depends on JavaFX version; best-effort allowed).

### 9.3 setText

If `target` is `TextInputControl`:

* `setText(text)`
* `positionCaret(text.length())`
  Otherwise:
* Error `UNSUPPORTED_TARGET_TYPE`

---

## 10) Screenshot (Optional but included in "Full Functional")

Use:

* `Node.snapshot(...)` for `scene.getRoot()` or `Scene.snapshot` (if available)
* Return PNG base64

Limitations:

* Only for showing stage
* scale = 1.0 (scale can be ignored or implemented later)

---

## 11) MCP Tool Layer

### 11.1 Tools (Mandatory)

* `ui_get_snapshot`
  * Supports `mode` (full/compact)
  * Supports `includeControlInternals`
  * Returns `content` (text) and `structuredContent` (JSON)
* `ui_query`
* `ui_get_node`
  * Supports `fields` and `properties` filtering
* `ui_perform`
* `ui_screenshot`

### 11.2 Errors

Unified format:

```json
{ "error": { "code": "...", "message": "...", "details": {...} } }
```

Minimum codes:

* `MCP_UI_NOT_ENABLED`
* `MCP_UI_NO_STAGES`
* `MCP_UI_NODE_NOT_FOUND`
* `MCP_UI_STALE_REF`
* `MCP_UI_ACTION_FAILED`
* `MCP_UI_TIMEOUT`
* `MCP_UI_INTERNAL`

---

## 12) Transport (Simple Local HTTP)

To avoid complicating MCP part, fix one transport: **Local HTTP** (127.0.0.1) with token.

### 12.1 Endpoints

* `GET /health` → `{ok:true, schema:"...", tools:[...]}`
* `POST /mcp` → accepts JSON `{tool:"...", input:{...}}` → returns `{output:{...}}` or `{error:{...}}`
* (Optional) `GET /events` SSE for logs/events (can be skipped)

### 12.2 Authorization

* Header `Authorization: Bearer <token>`
* If token not set → generate and log once.

### 12.3 Limits

* Max body size: 2–5 MB (so snapshot doesn't kill memory)
* Rate limit not mandatory, but at least "one request at a time" can be done with mutex.

---

## 13) Logging and Observability

* On start write:

  * `MCP JavaFX Debug enabled`
  * `Endpoint: http://127.0.0.1:<port>`
  * `Token: <token>` (fine in debug, but can be partially masked)
* (Optional) Tool-call audit: tool name, time, success/error.

---

## 14) Testing (Minimal Plan)

1. Unit: Serialization `UiNode` (id/styleClass/children order).
2. Integration: Mini JavaFX app:

   * Button + TextField + VBox
   * Start server
   * Call `getSnapshot` verify structure
   * `query` by `#id`
   * `perform setText` + repeat snapshot
3. (If screenshot) Compare image size > 0.

---

## 15) Limitations and Known Trade-offs (Document Explicitly)

* Virtualization: In simple variant, return only metadata (itemsCount/selected), `visibleCells` is best-effort.
* MenuBar/ContextMenu/Dialogs may live in separate windows — support best-effort via `Window.getWindows()`.
* Complex custom controls: `text/value` might be empty, but tree and bounds will be present.

---

## 16) Definition of Done

Library is considered "ready" if:

* Starts via `install()` and `startFromSystemProperties()`.
* `ui_get_snapshot` returns deterministic tree with `uid` (prefer `mode=compact` for LLM).
* `ui_query` finds by `#id` and text.
* `ui_perform` supports `click`, `setText`, `typeText`, `pressKey`.
* (Optional) `ui_screenshot` returns png base64.
* All UI operations execute on FX-thread and have timeouts.
* Access restricted to localhost + token.
