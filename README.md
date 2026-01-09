# MCP JavaFX Debug

A library for debugging JavaFX applications via MCP (Model Context Protocol). Allows LLMs to connect to your JavaFX application, inspect the UI structure, and perform actions.

For detailed documentation, see:
- [User Manual](docs/manual.md)
- [Tools Reference](docs/tools.md)

## Quick Start

### Option A: Download JAR (Recommended)

1. Download `mcp-javafx-X.X.X.jar` from [Releases](https://github.com/mcp-javafx/mcp-javafx-debug/releases)
2. Place it in your project (e.g., `libs/mcp-javafx.jar`)
3. Run your application with the agent:

```bash
java -Dmcp.ui=true -Dmcp.port=55667 -javaagent:libs/mcp-javafx.jar -jar your-app.jar
```

Or with `javafx-maven-plugin`:

```xml
<plugin>
    <groupId>org.openjfx</groupId>
    <artifactId>javafx-maven-plugin</artifactId>
    <configuration>
        <options>
            <option>-Dmcp.ui=true</option>
            <option>-Dmcp.port=55667</option>
            <option>-javaagent:libs/mcp-javafx.jar</option>
        </options>
    </configuration>
</plugin>
```

```bash
mvn javafx:run
```

### Option B: Maven Dependency

Add dependency to `pom.xml`:

```xml
<dependency>
    <groupId>com.github.mcpjavafx</groupId>
    <artifactId>mcp-javafx</artifactId>
    <version>1.0.0-SNAPSHOT</version>
</dependency>
```

Add Maven profile for auto-resolving JAR path:

```xml
<profiles>
    <profile>
        <id>agent</id>
        <!-- Required for screenshots if not already in your project -->
        <dependencies>
            <dependency>
                <groupId>org.openjfx</groupId>
                <artifactId>javafx-swing</artifactId>
                <version>${javafx.version}</version>
            </dependency>
        </dependencies>
        <build>
            <plugins>
                <plugin>
                    <groupId>org.apache.maven.plugins</groupId>
                    <artifactId>maven-dependency-plugin</artifactId>
                    <version>3.6.1</version>
                    <executions>
                        <execution>
                            <goals><goal>properties</goal></goals>
                        </execution>
                    </executions>
                </plugin>
                <plugin>
                    <groupId>org.openjfx</groupId>
                    <artifactId>javafx-maven-plugin</artifactId>
                    <configuration>
                        <options>
                            <option>-Dmcp.ui=true</option>
                            <option>-Dmcp.port=55667</option>
                            <option>"-javaagent:${com.github.mcpjavafx:mcp-javafx:jar}"</option>
                        </options>
                    </configuration>
                </plugin>
            </plugins>
        </build>
    </profile>
</profiles>
```

> **Note:** `javafx-swing` is required for the `ui_screenshot` tool. If your project doesn't have it, add it to the profile as shown above.

Run:
```bash
mvn initialize javafx:run -P agent
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
