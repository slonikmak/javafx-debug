# MCP Streamable HTTP Integration Specification

## (Simple but Full, **SDK-mandatory**)

### Status

**Normative / MUST-level**
Any compatible implementation **MUST** use the official MCP SDK. Implementations without SDK are considered **incompatible**.

---

## 1) Mandatory Requirement: Use MCP SDK

### 1.1 Normative Requirement

MCP Server implementation **MUST**:

1. Use **official MCP Java SDK** for:

   * JSON-RPC processing,
   * Streamable HTTP transport,
   * Tool registration,
   * MCP-capabilities generation.
2. **MUST NOT**:

   * Implement own JSON-RPC parser,
   * Implement own MCP protocol over HTTP,
   * Deviate from MCP tools/resources/capabilities semantics.

> Goal: Guarantee full compatibility with MCP clients (IDE, Inspector, LLM hosts) and correct protocol evolution.

---

## 2) Permissible SDK and Version

### 2.1 SDK

* Language: **Java**
* SDK: **Official MCP Java SDK**
* Server Type: **Server (sync or async)**

### 2.2 Minimum SDK Requirements

Implementation **MUST** use SDK that supports:

* MCP Server
* Tool registration
* Streamable HTTP transport
* Stateless Streamable HTTP profile

> SDK version is fixed in `mcp-javafx-debug` BOM or gradle version catalog.
> Upgrading SDK version is considered compatible if it does not violate MCP protocol version.

---

## 3) Architectural Contract (Updated)

```
┌──────────────────────────────────────────┐
│ JavaFX Application                       │
│                                          │
│  ┌────────────────────────────────────┐  │
│  │ mcp-javafx-core                     │  │
│  │  - Snapshotter                     │  │
│  │  - QueryEngine                     │  │
│  │  - ActionEngine                    │  │
│  │  - FxThread utils                  │  │
│  └──────────────▲─────────────────────┘  │
│                 │ calls on FX thread     │
│  ┌──────────────┴─────────────────────┐  │
│  │ MCP Adapter (SDK-bound)             │  │
│  │  - Tool handlers                   │  │
│  │  - Input validation                │  │
│  │  - Error mapping                   │  │
│  └──────────────▲─────────────────────┘  │
│                 │ MCP SDK API             │
│  ┌──────────────┴─────────────────────┐  │
│  │ MCP Java SDK                        │  │
│  │  - McpServer                       │  │
│  │  - ToolRegistry                    │  │
│  │  - JSON-RPC                        │  │
│  │  - Streamable HTTP (Stateless)     │  │
│  └──────────────▲─────────────────────┘  │
│                 │ HTTP                   │
│  ┌──────────────┴─────────────────────┐  │
│  │ Embedded HTTP server               │  │
│  │ (Jetty / Undertow / equivalent)    │  │
│  └────────────────────────────────────┘  │
└──────────────────────────────────────────┘
```

---

## 4) Transport: Streamable HTTP (SDK-bound)

### 4.1 Mandatory Rule

Streamable HTTP **MUST** be implemented **via SDK transport**, not manually.

Permissible variants:

* SDK Servlet-based Streamable HTTP (stateless)
* SDK WebFlux/WebMVC Streamable HTTP (if application already uses Spring)

Forbidden:

* "Manual" reading of POST body and self-routing of MCP.

---

## 5) Transport Profile (Fixed)

### 5.1 Profile

* **Stateless Streamable HTTP**
* No SSE (`GET /mcp` → `405`)
* No session management (`Mcp-Session-Id` ignored)

### 5.2 Endpoint

* `/mcp` — MCP endpoint (via SDK transport)
* `/health` — non-MCP, implemented manually

---

## 6) Security (SDK + External Layer)

### 6.1 What SDK Does

SDK **MUST**:

* Validate MCP JSON-RPC structure,
* Correctly handle batching,
* Correctly form JSON-RPC errors.

### 6.2 What Library Does (Outside SDK)

Implementation **MUST** add external HTTP filter / middleware **before SDK transport**, which performs:

1. **Origin validation** (DNS rebinding protection)
2. **Authorization: Bearer token**
3. **Bind restriction** (localhost)

SDK **must not** be modified for this.

---

## 7) MCP Server Configuration (SDK)

### 7.1 Mandatory Server Parameters

When creating MCP server via SDK implementation **MUST**:

* Set `serverInfo(name, version)`
* Enable capability `tools`
* Enable capability `logging`
* Enable **immediate execution**, if SDK supports it

Reason:

* Tool handlers manage transition to FX-thread themselves.

---

## 8) Tools Registration (SDK-level)

### 8.1 Mandatory Tools

Via SDK tool registry **MUST** be registered:

* `ui.getSnapshot`
* `ui.query`
* `ui.getNode`
* `ui.perform`
* `ui.screenshot` (optional, but recommended)

Each tool:

* Has description,
* Has JSON Schema input,
* Returns structured JSON output,
* Does not throw exceptions beyond SDK handler.

---

## 9) Error Handling (SDK-compliant)

### 9.1 Mapping

All internal errors (`MCP_UI_*`) **MUST**:

* Be converted to **JSON-RPC error** via SDK API,
* Have stable `code` and `message`,
* Not lead to HTTP 500, if error is logical (e.g., node not found).

HTTP 500 permissible **only** for:

* SDK internal failure,
* Uncaught runtime error.

---

## 10) Forbidden Implementations (Explicit Non-Goals)

The following variants are **forbidden** by this specification:

* ❌ Self-written MCP server without SDK
* ❌ Self-written JSON-RPC over HTTP
* ❌ Handling MCP tools outside SDK abstraction
* ❌ Using MCP only "logically", but not protocol-wise

Such implementations are **not considered MCP servers** within this library.

---

## 11) Definition of Done (Updated)

Implementation is considered compliant if:

* [ ] MCP server created **via official MCP SDK**
* [ ] Streamable HTTP implemented **via SDK transport**
* [ ] No own JSON-RPC code
* [ ] Tools registered via SDK registry
* [ ] Security filters placed **before** SDK transport
* [ ] FX-thread correctness ensured inside tool handlers
* [ ] Server works correctly with MCP Inspector / IDE clients

---

## 12) Recommended Future Extensions (Not part of MUST)

* Enable SSE (`GET /mcp`) via SDK
* Add stateful Streamable HTTP
* Add stdio transport via SDK
* Add resource endpoints (`ui.tree`, `ui.focusedNode`)
