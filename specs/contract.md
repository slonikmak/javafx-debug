Ниже — компактная, но полная спека контракта “MCP ⇄ JavaFX Introspection/Control”: типы данных (UI tree snapshot), адресация узлов, семантика селекторов, набор tools, ошибки, гарантии по потокам, и минимальные требования к детерминизму.

---

## 0) Цели и принципы

1. **Стабильность**: LLM должна уметь повторно запрашивать снимок и сопоставлять элементы между снимками.
2. **Безопасность**: сервер по умолчанию доступен только локально; интроспекция и управление — под флагом debug.
3. **FX-thread correctness**: все чтения/изменения UI выполняются на **JavaFX Application Thread**.
4. **Воспроизводимость**: снимок должен возвращаться в детерминированном порядке.

---

## 1) Идентификация и адресация узлов

### 1.1 `nodeRef` (стабильная ссылка на узел)

Узел должен иметь **стабильный идентификатор** в пределах жизненного цикла приложения (или хотя бы между двумя соседними снимками).

`nodeRef` = объект:

* `path`: string — **канонический путь** в дереве (см. ниже)
* `uid`: string | null — стабильный UID, если доступен (рекомендовано)
* `hint`: object? — подсказки для восстановления (id, type, text)

**Рекомендация по `uid`:**

* Если у `Node.getProperties()` есть ваш ключ (например `"mcp.uid"`), используйте его.
* Иначе можно генерировать UID при первом обнаружении узла и сохранять в `node.getProperties()`.

### 1.2 Канонический `path`

Строка формата:
`/stages[{stageIndex}]/scene/root/{type}[{n}]/...`

Где:

* `stageIndex` — индекс Stage в списке отсортированных по `title`, затем по `hashCode` (детерминизм).
* `{type}` — простое имя класса (`Button`, `VBox`, `TextField`).
* `{n}` — порядковый индекс среди **siblings одного типа** (0-based) в `Parent.getChildrenUnmodifiable()`.

Пример:
`/stages[0]/scene/root/VBox[0]/HBox[1]/Button[0]`

### 1.3 Селекторы (поиск)

Поддержать минимум 2 режима:

* `css`: string — селектор JavaFX (`#id`, `.class`, `Button`, `VBox > Button`, и т.п.), резолвится через `Scene.lookupAll(...)`
* `text`: string — “поиск по отображаемому тексту” (Labeled/Text/Tab и т.п.)
* `predicate`: object — расширяемое условие (см. ниже)

---

## 2) Снимок UI: типы данных

### 2.1 `UiSnapshot`

```json
{
  "schema": "mcp-javafx-ui/1.0",
  "capturedAt": "2026-01-05T12:34:56.789Z",
  "app": {
    "pid": 12345,
    "javaVersion": "21.0.2",
    "javafxVersion": "21.0.2",
    "mainClass": "com.example.App",
    "debugFlags": ["mcpEnabled"]
  },
  "focus": {
    "focusedNode": { "path": "...", "uid": "..." },
    "focusedWindow": { "stageIndex": 0 }
  },
  "stages": [
    {
      "stageIndex": 0,
      "title": "Main",
      "showing": true,
      "focused": true,
      "x": 100.0,
      "y": 50.0,
      "width": 1200.0,
      "height": 800.0,
      "scene": {
        "stylesheets": ["app.css"],
        "root": { "...UiNode..." }
      }
    }
  ]
}
```

### 2.2 `UiNode`

```json
{
  "ref": { "path": "...", "uid": "..." },
  "type": "Button",
  "module": "javafx.controls",
  "id": "okButton",
  "styleClass": ["button", "primary"],
  "pseudoClass": ["hover", "focused"],
  "visible": true,
  "managed": true,
  "disabled": false,
  "opacity": 1.0,
  "layout": {
    "boundsInParent": { "minX":0,"minY":0,"width":80,"height":32 },
    "boundsInScene":  { "minX":450,"minY":700,"width":80,"height":32 },
    "localToScreen":  { "x":900,"y":900,"width":80,"height":32 }
  },
  "text": {
    "label": "OK",
    "prompt": null
  },
  "value": {
    "text": null,
    "selected": null,
    "checked": null
  },
  "accessibility": {
    "role": "BUTTON",
    "help": null
  },
  "fx": {
    "properties": {
      "tooltip": "Confirm",
      "userData": null
    }
  },
  "children": []
}
```

### 2.3 Нормы/правила сериализации

* `children`:

  * Только для `Parent` и `Skin`-видимых узлов **по вашему выбору** (см. virtualization ниже).
  * Порядок: **как в `getChildrenUnmodifiable()`**, без сортировок.
* `pseudoClass`: получить через перечисление известных псевдоклассов нельзя напрямую; допустимо:

  * хранить только то, что вы вычисляете/знаете (например `focused`, `hover`, `pressed`, `selected`, `disabled`) через API узла/контрола;
  * или оставить пустым.
* `localToScreen`:

  * опционально, но очень полезно для кликов по координатам.
* `module`:

  * `node.getClass().getModule().getName()` если доступно.

---

## 3) Виртуализированные контролы (TableView/ListView/TreeView)

### 3.1 Минимальный контракт

Для следующих типов узлов добавлять секцию `virtualization`:

* `ListView`, `TableView`, `TreeView`, `TreeTableView`

Пример:

```json
"virtualization": {
  "kind": "TableView",
  "itemsCount": 1000,
  "visibleRange": { "from": 20, "to": 45 },
  "selectedIndices": [22],
  "focusedIndex": 22,
  "columns": [
    { "id": "nameCol", "text": "Name" }
  ],
  "visibleCells": [
    {
      "index": 22,
      "rowRef": { "path": "...", "uid": "..." },
      "cells": [
        { "columnId": "nameCol", "text": "Alice" }
      ]
    }
  ]
}
```

**Смысл:** LLM видит “что реально на экране” и может ссылаться на `index/columnId` для действий.

---

## 4) MCP tools: обязательный набор

Ниже — “логические” сигнатуры. В MCP это оформляется как tools с JSON input/output.

### 4.1 `ui.getSnapshot`

**Input**

```json
{
  "stage": "focused|primary|all|index",
  "stageIndex": 0,
  "depth": 50,
  "include": {
    "bounds": true,
    "localToScreen": true,
    "styles": false,
    "properties": false,
    "virtualization": true
  }
}
```

**Output**

* `UiSnapshot`

**Ошибки**

* `MCP_UI_NOT_ENABLED`
* `MCP_UI_NO_STAGES`
* `MCP_UI_TIMEOUT`

### 4.2 `ui.query`

Ищет узлы и возвращает “короткие” описания + ссылки.

**Input**

```json
{
  "scope": { "stage": "focused", "stageIndex": 0 },
  "selector": {
    "css": "#okButton",
    "text": null,
    "predicate": null
  },
  "limit": 50
}
```

**Output**

```json
{
  "matches": [
    {
      "ref": { "path": "...", "uid": "..." },
      "type": "Button",
      "id": "okButton",
      "summary": "Button[text=OK]",
      "layout": { "boundsInScene": { "minX": 0, "minY": 0, "width": 0, "height": 0 } }
    }
  ]
}
```

### 4.3 `ui.getNode`

Получить подробности по узлу.

**Input**

```json
{ "ref": { "path": "...", "uid": "..." }, "includeChildren": false }
```

**Output**

* `UiNode`

**Ошибки**

* `MCP_UI_NODE_NOT_FOUND`
* `MCP_UI_STALE_REF` (если path/uid не резолвится)

### 4.4 `ui.perform`

Унифицированные действия (удобно для LLM).

**Input**

```json
{
  "actions": [
    { "type": "focus", "target": { "ref": { "path": "...", "uid": "..." } } },
    { "type": "click", "target": { "ref": { "path": "...", "uid": "..." } } },
    { "type": "typeText", "text": "Hello" },
    { "type": "setText", "target": { "ref": { "path": "...", "uid": "..." } }, "text": "Hello" },
    { "type": "pressKey", "key": "ENTER", "modifiers": ["CTRL"] },
    { "type": "scroll", "target": { "ref": { "path": "...", "uid": "..." } }, "deltaY": -400 }
  ],
  "awaitUiIdle": true,
  "timeoutMs": 5000
}
```

**Output**

```json
{
  "results": [
    { "ok": true, "type": "focus" },
    { "ok": true, "type": "click" },
    { "ok": true, "type": "typeText" }
  ]
}
```

**Ошибки**

* `MCP_UI_ACTION_FAILED` (+ `details`)
* `MCP_UI_TIMEOUT`

### 4.5 `ui.screenshot` (опционально, но крайне полезно)

**Input**

```json
{ "stage": "focused", "format": "png", "scale": 1.0 }
```

**Output**

```json
{ "contentType": "image/png", "dataBase64": "..." }
```

---

## 5) Модель ошибок (единый формат)

Любой tool при ошибке возвращает:

```json
{
  "error": {
    "code": "MCP_UI_NODE_NOT_FOUND",
    "message": "Node not found for uid=... path=...",
    "details": { "ref": { "path": "...", "uid": "..." } }
  }
}
```

Коды (минимум):

* `MCP_UI_NOT_ENABLED`
* `MCP_UI_NO_STAGES`
* `MCP_UI_NODE_NOT_FOUND`
* `MCP_UI_STALE_REF`
* `MCP_UI_ACTION_FAILED`
* `MCP_UI_TIMEOUT`
* `MCP_UI_INTERNAL`

---

## 6) Потоки, ожидание “UI idle”, тайминги

### 6.1 Гарантии

* Все действия и чтение UI выполняются на FX-thread.
* Для `awaitUiIdle=true` сервер должен дождаться:

  * выполнения всех `Platform.runLater` задач, которые вы поставили,
  * и **пульса** (pulse) JavaFX, если возможно (или эквивалента “следующего кадра”).

### 6.2 Практический механизм “idle”

Допустимая минимальная реализация:

* после `perform` сделать:

  * `Platform.runLater` → `CompletableFuture.complete`
  * затем маленькая задержка 1 pulse: `AnimationTimer` на один тик или `Platform.runLater` дважды
    Это не идеально, но достаточно для отладки.

---

## 7) Версионирование схемы

* Поле `schema`: `"mcp-javafx-ui/1.0"`
* Любое несовместимое изменение → `2.0`
* Расширения добавлять через новые поля, не ломая старые.

---

## 8) Безопасность и режимы

Рекомендуемое поведение:

* По умолчанию tools возвращают `MCP_UI_NOT_ENABLED`.
* Включение только при:

  * `-Dmcp.ui=true` и/или `--add-opens` по необходимости,
  * ограничение на loopback,
  * токен/секрет (хотя бы “одноразовый” в логах при старте).

---

## 9) Минимальные “predicate” условия (если решите поддержать)

```json
{
  "typeIs": ["Button", "TextField"],
  "idEquals": "okButton",
  "styleClassHas": "primary",
  "textContains": "OK",
  "visible": true,
  "enabled": true
}
```
