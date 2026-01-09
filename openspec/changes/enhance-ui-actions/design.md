# Design: Enhanced UI Actions

## Context

Тестирование MCP JavaFX Debug через демо-приложение с MapView выявило ограничения текущего набора UI actions. Стандартные контролы (Button, CheckBox, TextField) работают корректно, но сложные сценарии (drag-and-drop на карте, двойной клик для зума, горячие клавиши) невозможны с текущим API.

### Constraints
- Все действия должны выполняться через `javafx.scene.robot.Robot`
- JavaFX threading model: все UI-операции на FX Application Thread
- Совместимость с JavaFX 17+ / Java 17+

## Goals / Non-Goals

### Goals
- Исправить нерабочий `pressKey` (критично)
- Обеспечить надёжный `doubleClick` (критично)
- Расширить набор mouse actions для полной симуляции ввода (высокий приоритет)
- Поддержать drag-and-drop операции (высокий приоритет)

### Non-Goals
- Поддержка multi-touch жестов
- Поддержка gestures на touchscreen
- Запись/воспроизведение макросов
- `mouseMoved` как отдельное действие (может быть добавлено позже)
- `autoFocus` опция для click (может быть добавлено позже)

## Decisions

### Decision 1: Использовать низкоуровневые mouse events

**What**: Добавить `mousePressed`, `mouseReleased` как атомарные действия.

**Why**: Это позволяет пользователям конструировать сложные взаимодействия (drag-and-drop), которые нельзя выразить через высокоуровневый `click`.

### Decision 2: Реализация doubleClick через Robot.mouseClick с интервалом

**What**: `doubleClick` будет отправлять два `Robot.mouseClick()` с интервалом меньше системного double-click threshold.

**Why**: JavaFX `Robot` не имеет нативного метода для двойного клика. Требуется симуляция.

**Risks**: Timing-зависимое поведение может варьироваться на разных системах.

**Mitigation**: Использовать `java.awt.Toolkit.getDefaultToolkit().getDesktopProperty("awt.multiClickInterval")` для получения системного порога.

### Decision 3: drag как составное действие

**What**: `drag` будет реализован как последовательность: focus → mousePressed(start) → mouseMoved(points...) → mouseReleased(end).

**Why**: Это точно соответствует тому, как JavaFX обрабатывает drag operations.

## Schema Extension

```json
{
  "type": "mousePressed",
  "target": { "ref": { "uid": "u-3" } },
  "button": "PRIMARY",
  "modifiers": ["SHIFT"]
}

{
  "type": "mouseReleased",
  "target": { "ref": { "uid": "u-3" } },
  "button": "PRIMARY"
}

{
  "type": "doubleClick",
  "target": { "ref": { "uid": "u-5" } }
}

{
  "type": "drag",
  "from": { "ref": { "uid": "u-3" } },
  "to": { "x": 500, "y": 300 },
  "button": "PRIMARY"
}
```

## Risks / Trade-offs

| Risk | Mitigation |
|------|------------|
| Timing issues with doubleClick | Use system threshold; make interval configurable |
| Platform differences | Document known limitations per OS |

## Open Questions

1. Какой интервал по умолчанию использовать для `doubleClick` если системный порог недоступен? (предложение: 200ms)
