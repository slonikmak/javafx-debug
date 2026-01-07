# Project Context

## Purpose
A library for debugging JavaFX applications via MCP (Model Context Protocol). It allows Large Language Models (LLMs) to connect to a running JavaFX application, inspect the UI structure (Scene Graph), query nodes, and perform actions (click, type, scroll) for debugging and testing purposes.

## Tech Stack
- **Language**: Java 21
- **Build Tool**: Maven
- **UI Framework**: JavaFX 21.0.2
- **Protocol**: MCP SDK (Model Context Protocol) 0.17.0
- **Serialization**: Jackson 2.17.0
- **Transport**: Jetty 12.0.14
- **Testing**: JUnit 5, TestFX 4.0.18, Monocle

## Project Conventions

### Code Style
- Follow standard Java coding conventions.
- Principles: KISS, DRY, SOLID, Separation of Concerns.
- Clean package structure: `api`, `transport`, `mcp`, `core`, `model`, `util`.

### Architecture Patterns
- **Embeddable Library**: Designed as a library to be included in the host application, not a standalone agent.
- **Layered Architecture**:
    - **API**: Public facing entry points (`McpJavafxDebug`).
    - **Transport**: Handles HTTP/SSE communication.
    - **MCP**: Adapters for the Model Context Protocol.
    - **Core**: Business logic for UI introspection and interaction.
- **Thread Safety**: All JavaFX UI interactions must be executed on the JavaFX Application Thread (`Platform.runLater`).
- **Service Pattern**: Core logic encapsulated in services (e.g., `UiToolsService`).

### Testing Strategy
- **Unit Tests**: JUnit 5 for logic.
- **Integration/E2E Tests**: TestFX for spinning up actual JavaFX stages and testing interactions.
- **Headless Testing**: Monocle support for running UI tests in headless environments.

### Git Workflow
- Standard feature branching workflow.

## Domain Context
- **JavaFX Scene Graph**: The hierarchical structure of the UI.
- **Node Identification**: Nodes are identified by stable UIDs or CSS-like paths/selectors.
- **MCP Tools**: The library exposes specific tools (`ui_get_snapshot`, `ui_query`, `ui_get_node`, `ui_perform`, `ui_screenshot`) to the LLM.

## Important Constraints
- **Security**: Localhost access only. Token-based authentication recommended.
- **Performance**: Snapshotting large scene graphs should be optimized.
- **concurrency**: Strict adherence to JavaFX threading rules to avoid exceptions.

## External Dependencies
- **JavaFX Runtime**: Required for the application to run.
- **MCP Ecosystem**: Compatible MCP clients (e.g., Claude Desktop, IDE extensions).
