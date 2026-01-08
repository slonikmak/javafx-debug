# Change: Refactor to Multi-Module Project and Prepare for Publication

## Why
The current project is a single-module project containing both the library and the demo application. To publish the library to GitHub Packages, we need to separate the core library from the demo application to avoid including unnecessary dependencies and code in the library artifact. We also need to establish a versioning strategy and publication configuration.

## What Changes
- **Project Structure**: Convert the project to a multi-module Maven project.
- **Core Module**: Create `mcp-javafx` module for the library code.
- **Demo Module**: Create `mcp-javafx-demo` module for the example application.
- **Versioning**: Implement a centralized versioning strategy using Maven properties.
- **Publication**: Configure `distributionManagement` for GitHub Packages.
- **Build Configuration**: Add source and javadoc plugins for the library module.

## Impact
- Affected code: All source files will be moved to their respective modules.
- Build system: `pom.xml` will be split into a parent POM and module-specific POMs.
- CI/CD: Future workflows will need to handle multiple modules.
