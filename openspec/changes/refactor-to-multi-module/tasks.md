## 1. Project Restructuring
- [x] 1.1 Create directory structure for `mcp-javafx` and `mcp-javafx-demo` modules.
- [x] 1.2 Move library source code and tests to `mcp-javafx`.
- [x] 1.3 Move demo source code to `mcp-javafx-demo`.

## 2. Maven Configuration
- [x] 2.1 Refactor root `pom.xml` into a parent POM.
- [x] 2.2 Create `pom.xml` for `mcp-javafx` with library-specific dependencies.
- [x] 2.3 Create `pom.xml` for `mcp-javafx-demo` depending on `mcp-javafx`.
- [x] 2.4 Configure publication settings in the parent POM.

## 3. Validation
- [ ] 3.1 Verify that the project builds successfully with `mvn clean install`.
- [ ] 3.2 Verify that the demo application runs correctly from the new module.
- [ ] 3.3 Verify that tests in `mcp-javafx` pass.
