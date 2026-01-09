# Change: Enhance UI Actions for Complete Input Simulation

## Why
Текущий набор `ui_perform` действий недостаточен для полноценного E2E тестирования сложных пользовательских сценариев. Тестирование выявило отсутствие поддержки granular mouse events, неработающий `pressKey`, и невозможность симуляции drag-and-drop операций.

## What Changes
- **ADDED**: Новые типы действий в `ui_perform`:
  - `doubleClick` — гарантированный двойной клик с правильным `clickCount`
  - `mousePressed` / `mouseReleased` — для точной симуляции ввода мыши
  - `drag` — перетаскивание объектов (start → end coordinates)
- **FIXED**: Исправить реализацию `pressKey` (ошибка "Unknown action type")
- **ADDED**: Документация по ограничениям взаимодействия с кастомными контролами (MapView и подобные)

## Impact
- Affected specs: `ui-actions`
- Affected code:
  - `mcp-javafx/src/.../core/actions/` — ActionHandler реализации
  - `mcp-javafx/src/.../mcp/tools/UiPerformTool.java` — регистрация новых типов действий
  - `docs/tools.md` — обновление документации

## Breaking Changes
- Нет явных breaking changes. Все изменения являются расширениями существующего API.

## Scope
> [!NOTE]
> Scope сфокусирован на критичных и высокоприоритетных изменениях:
> 1. **Критично**: `pressKey` fix, `doubleClick`
> 2. **Высокий приоритет**: `drag`, `mousePressed/Released`
