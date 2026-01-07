# MCP JavaFX Debug

A library for debugging JavaFX applications via MCP (Model Context Protocol). Allows LLMs to connect to your JavaFX application, inspect the UI structure, and perform actions.

For detailed documentation, see:
- [User Manual](docs/manual.md) (Features, Building, Configuration)
- [Tools Reference](docs/tools.md) (API, Tools, Strategies)

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


## License

MIT
