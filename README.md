# MCP JavaFX Debug

A library for debugging JavaFX applications via MCP (Model Context Protocol). Allows LLMs to connect to your JavaFX application, inspect the UI structure, and perform actions.

## Features

- **UI Introspection**: Capture the scene graph as a JSON snapshot
- **Node Search**: Find nodes by CSS selector, text content, or predicates
- **UI Actions**: Click, type, scroll, and interact with UI elements
- **Screenshots**: Capture PNG screenshots of the application

## Quick Start

### 1. Add Dependency

```xml
<dependency>
    <groupId>com.github.mcpjavafx</groupId>
    <artifactId>mcp-javafx-debug</artifactId>
    <version>1.0.0-SNAPSHOT</version>
</dependency>
```

### 2. Enable in Your Application

**Option A: System Properties**

Run with `-Dmcp.ui=true`:

```java
public class MyApp extends Application {
    @Override
    public void start(Stage stage) {
        McpJavafxDebug.startFromSystemProperties();
        // ... your app code
    }
}
```

```bash
java -Dmcp.ui=true -jar myapp.jar
```

**Option B: Programmatic Configuration**

```java
var handle = McpJavafxDebug.install(McpJavafxConfig.builder()
    .enabled(true)
    .port(8080)
    .token("my-secret-token")
    .build());
```

### 3. Connect

After starting, you'll see the endpoint and token in the console:

```
[MCP-JAVAFX] MCP JavaFX Debug enabled
[MCP-JAVAFX] Endpoint: http://127.0.0.1:49321
[MCP-JAVAFX] Token: abc123def456
```

## Testing & Connection

### MCP Streamable HTTP (Stateless) Profile

This server exposes the official MCP protocol over **Stateless Streamable HTTP** (POST-only) using the **official MCP Java SDK**.

- **Endpoint**: `http://127.0.0.1:<PORT>/mcp`
- **Method**: `POST` only (`GET /mcp` returns `405` — SSE is disabled)
- **Auth**: `Authorization: Bearer <TOKEN>` is required for `/mcp` unless `mcp.auth=false`
- **Accept**: MCP SDK transport requires `Accept: application/json, text/event-stream` on `/mcp` requests
- **Sessions**: `Mcp-Session-Id` is ignored (stateless profile)
- **Origin protection**: if an `Origin` header is present, only `localhost` / `127.0.0.1` origins are accepted
- **Health**: `GET /health` is not an MCP endpoint (no auth)

### VS Code / IntelliJ (Recommended)
Use the provided [requests.http](requests/requests.http) file to test the API directly from your IDE.

1.  **Variables**: Update `@port` and `@token` at the top of the file using values from your console.
2.  **IntelliJ Environments**: We've provided [http-client.private.env.json](requests/http-client.private.env.json). Select the `dev` environment in the IDE to automatically use your port/token.

### Command Line (curl)

**Health Check:**
```bash
curl http://127.0.0.1:<PORT>/health
```

**Get Snapshot (requires Token):**
```bash
curl -X POST http://127.0.0.1:<PORT>/mcp \
     -H "Authorization: Bearer <TOKEN>" \
     -H "Content-Type: application/json" \
  -H "Accept: application/json, text/event-stream" \
     -d '{"jsonrpc":"2.0","id":1,"method":"tools/call","params":{"name":"ui_get_snapshot","arguments":{}}}'
```

If you are sending MCP messages manually, follow the MCP lifecycle:

1) `initialize` request
```json
{
  "jsonrpc": "2.0",
  "id": 1,
  "method": "initialize",
  "params": {
    "protocolVersion": "2025-03-26",
    "capabilities": {
      "roots": { "listChanged": true },
      "sampling": {}
    },
    "clientInfo": {
      "name": "manual-http-client",
      "version": "0.0"
    }
  }
}
```

2) `notifications/initialized` notification
```json
{ "jsonrpc": "2.0", "method": "notifications/initialized" }
```

3) Call tools via `tools/call` (example: `ui_get_snapshot`)
```json
{
  "jsonrpc": "2.0",
  "id": 2,
  "method": "tools/call",
  "params": {
    "name": "ui_get_snapshot",
    "arguments": {
      "stage": "focused",
      "depth": 50,
      "include": {
        "bounds": true,
        "localToScreen": true
      }
    }
  }
}
```

## Connecting to LLM Agents

This library uses **HTTP** transport with a secure token.

### 1. Claude Desktop (via HTTP Bridge)

Since Claude Desktop uses STDIO by default, the recommended way to connect is via the MCP bridge:

1.  Start your JavaFX application with `mcp.ui=true`:
    ```bash
    java -Dmcp.ui=true -jar your-app.jar
    ```
2.  Note the **Endpoint** and **Token** from console output
3.  Add to your `claude_desktop_config.json`:

```json
{
  "mcpServers": {
    "javafx-debug": {
      "command": "npx",
      "args": [
        "-y",
        "@thefoot/mcp-stdio-http-bridge",
        "--endpoint", "http://127.0.0.1:<PORT>/mcp",
        "--header", "Authorization: Bearer <TOKEN>"
      ]
    }
  }
}
```

> Replace `<PORT>` and `<TOKEN>` with values from step 2.

### 2. Custom Agents / SDKs

For custom agents using MCP SDKs:
- **HTTP**: Use `HttpClientTransport` with `Authorization: Bearer <TOKEN>` header

---

## MCP Tools

### `ui_get_snapshot`

Captures the scene graph.

Tip: prefer `mode: "compact"` for LLM agents to reduce payload size; opt into expensive sections via `include.*`.

```json
{
  "tool": "ui_get_snapshot",
  "input": {
    "stage": "focused",
    "mode": "compact",
    "depth": 50,
    "include": {
      "bounds": true,
      "localToScreen": true
    }
  }
}
```

### `ui_query`

Finds nodes by selector.

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

Text query example:

```json
{
  "tool": "ui_query",
  "input": {
    "selector": { "text": "Submit", "match": "contains" },
    "limit": 10
  }
}
```

### `ui_perform`

Executes UI actions.

```json
{
  "tool": "ui_perform",
  "input": {
    "awaitUiIdle": true,
    "timeoutMs": 5000,
    "actions": [
      { "type": "focus", "target": { "ref": { "uid": "u-1" } } },
      { "type": "setText", "target": { "ref": { "uid": "u-2" } }, "text": "Hello" },
      { "type": "click", "target": { "ref": { "uid": "u-3" } } }
    ]
  }
}
```

### `ui_screenshot`

Takes a screenshot.

```json
{
  "tool": "ui_screenshot",
  "input": { "stageIndex": -1 }
}
```

## System Properties

| Property | Default | Description |
|----------|---------|-------------|
| `mcp.ui` | `false` | Enable/disable |
| `mcp.transport` | `http` | Transport mode: `http` |
| `mcp.bind` | `127.0.0.1` | Bind address (HTTP only) |
| `mcp.port` | `0` (auto) | Port (HTTP only) |
| `mcp.token` | (generated) | Auth token (HTTP only) |
| `mcp.allowActions` | `true` | Allow UI actions |
| `mcp.auth` | `true` | Require `Authorization: Bearer` for `/mcp` |

## Requirements

- Java 21+
- JavaFX 21+

## Building

```bash
mvn clean install
```

## License

MIT
