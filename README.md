# MCP JavaFX Debug

A library for debugging JavaFX applications via MCP (Model Context Protocol). Allows LLMs to connect to your JavaFX application, inspect the UI structure, and perform actions.

For detailed documentation, see:
- [User Manual](docs/manual.md)
- [Tools Reference](docs/tools.md)

## Quick Start

### 1. Download Agent

1. Download `mcp-javafx-X.X.X.jar` from [Releases](https://github.com/mcp-javafx/mcp-javafx-debug/releases).
2. Place it in your project directory (e.g., `libs/mcp-javafx.jar`).

### 2. Run Application

You can run your application with the agent using either the command line or Maven.

#### Method A: Command Line

```bash
java -Dmcp.ui=true -Dmcp.port=55667 -javaagent:libs/mcp-javafx.jar -jar your-app.jar
```

#### Method B: Maven Profile (Recommended)

Using a profile is convenient for repeated runs and ensures missing dependencies are included.

1. Add the following profile to your `pom.xml`:

```xml
<profiles>
    <profile>
        <id>mcp</id>
        <dependencies>
            <!-- Required for screenshots (ui_screenshot tool) -->
            <dependency>
                <groupId>org.openjfx</groupId>
                <artifactId>javafx-swing</artifactId>
                <version>${javafx.version}</version> <!-- Or specify explicit version like 21.0.1 -->
            </dependency>
        </dependencies>
        <build>
            <plugins>
                <plugin>
                    <groupId>org.openjfx</groupId>
                    <artifactId>javafx-maven-plugin</artifactId>
                    <configuration>
                        <options>
                            <option>-Dmcp.ui=true</option>
                            <option>-Dmcp.port=55667</option>
                            <!-- Point to your local jar path -->
                            <option>-javaagent:libs/mcp-javafx.jar</option>
                        </options>
                    </configuration>
                </plugin>
            </plugins>
        </build>
    </profile>
</profiles>
```

2. Run with the profile:

```bash
mvn javafx:run -P mcp
```

## Running the Demo

To run the included demo application:

```bash
mvn -pl mcp-javafx-demo initialize -P agent javafx:run
```

## Connect to LLM

Console output after start:
```
[MCP-JAVAFX] MCP JavaFX Debug enabled
[MCP-JAVAFX] Endpoint: http://127.0.0.1:55667
```

Add to your IDE/Agent MCP configuration:

```json
{
  "javafx-debugger": {
    "serverUrl": "http://127.0.0.1:55667/mcp"
  }
}
```

## System Properties

| Property | Default | Description |
|----------|---------|-------------|
| `mcp.ui` | `false` | Enable/disable the debug server |
| `mcp.port` | `0` (auto) | HTTP port to bind |
| `mcp.allowActions` | `true` | Allow UI actions (click, type, etc.) |
| `mcp.bind` | `127.0.0.1` | Bind address |

## License

MIT
