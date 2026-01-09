## Context
LLMs have limited context windows and charge per token. Sending megabytes of JSON for a simple UI lookup is inefficient. We need a "drill-down" approach similar to browser dev tools or accessibility trees.

## Goals / Non-Goals
- **Goals**:
  - Reduce `ui_get_snapshot` token count by >50%.
  - Maintain ability to inspect any node's full details via `ui_get_node`.
  - Keep `structuredContent` valid for programmatic consumers (MCP Inspector).
- **Non-Goals**:
  - Changing the underlying MCP protocol.
  - optimizing the JavaFX scene graph itself.

## Decisions
- **Decision**: Use a custom Text Tree format for `content`.
  - **Rationale**: JSON syntax (`{`, `}`, `"`) adds significant token overhead. Indentation-based text is token-efficient and naturally understood by LLMs.
- **Decision**: Split `content` and `structuredContent`.
  - **Rationale**: LLMs read `content`; tools read `structuredContent`. They have different optimization targets (readability vs. structure).

## Risks / Trade-offs
- **Risk**: LLM might hallucinate `uid`s if the tree is too deep.
  - **Mitigation**: Keep the tree depth configurable (default to reasonable limit) and encourage `ui_query` for deep searches.
- **Risk**: `structuredContent` might desync from `content`.
  - **Mitigation**: Both are derived from the same snapshot object, just formatted differently.

## Migration Plan
- This is a breaking change for the *format* of `ui_get_snapshot`'s text output. Agents relying on parsing the JSON string in `content` will break.
- `structuredContent` will remain JSON but with fewer fields by default.
