# JavaFX Debug User Manual

## Features

- **UI Introspection**: Capture the scene graph as a JSON snapshot
- **Node Search**: Find nodes by CSS selector, text content, or predicates
- **UI Actions**: Click, type, scroll, and interact with UI elements
- **Screenshots**: Capture PNG screenshots of the application

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
Use the provided [requests.http](../requests/requests.http) file to test the API directly from your IDE.

1.  **Variables**: Update `@port` and `@token` at the top of the file using values from your console.
2.  **IntelliJ Environments**: We've provided [http-client.private.env.json](../requests/http-client.private.env.json). Select the `dev` environment in the IDE to automatically use your port/token.

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
