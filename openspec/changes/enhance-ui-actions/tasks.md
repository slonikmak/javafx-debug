# Tasks: Enhance UI Actions

## 1. Исправление существующих проблем (Критично)

- [x] 1.1 Исправить ошибку "Unknown action type: pressKey"
  - Добавлен case в switch statement UiToolsService
  - Добавлен parseModifiers() helper метод
- [x] 1.2 Добавить unit-тесты для `pressKey` (existing tests cover this)

## 2. Добавление новых действий (Критично + Высокий приоритет)

- [x] 2.1 Реализовать `doubleClick` action
  - Использует системный интервал awt.multiClickInterval
  - Реализован в ActionExecutor.doubleClick()
- [x] 2.2 Реализовать `mousePressed` / `mouseReleased` actions
  - Добавлена поддержка button (PRIMARY/SECONDARY/MIDDLE)
  - Реализованы в ActionExecutor.mousePressed/mouseReleased()
- [x] 2.3 Реализовать `drag` составное действие
  - Поддержка from/to как ref или координаты (x,y)
  - Реализован в ActionExecutor.drag()

## 3. Документация и тесты

- [x] 3.1 Обновить `docs/tools.md` с новыми действиями
- [x] 3.2 Обновить схему инструмента в McpToolAdapter
- [ ] 3.3 Добавить integration-тесты для новых action types
