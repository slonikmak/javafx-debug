Ниже спека “простого, но полнофункционального” варианта: одна подключаемая библиотека, которая по флагу поднимает MCP-сервер, даёт tools `ui_get_snapshot/ui_query/ui_get_node/ui_perform/ui_screenshot`, гарантирует FX-thread корректность, детерминизм снимков и базовую безопасность (localhost+token). Всё — без javaagent и без магии: один вызов `install()` или автозапуск через `startFromSystemProperties()`.

---

## 1) Область и цели

**Цель**: библиотека `mcp-javafx-debug` встраивается в JavaFX приложение и предоставляет MCP tools для:

* интроспекции UI (Scene Graph snapshot),
* поиска узлов,
* простых действий (клик/фокус/ввод/скролл/нажатия клавиш),
* (опционально) скриншота.

**Не цель** (для “простого варианта”):

* javaagent/attach,
* полный дамп CSS computed style,
* глубокая интеграция со Skin internals через reflection,
* поддержка удалённого доступа по сети (только localhost).

---

## 2) Публичный API библиотеки

### 2.1 Entry points

Обязательные публичные методы:

```java
public final class McpJavafxDebug {
  public static McpJavafxHandle install(McpJavafxConfig config);
  public static McpJavafxHandle startFromSystemProperties();
}
```

* `install(config)` — явный запуск (предпочтительно).
* `startFromSystemProperties()` — удобный “1 строкой” запуск.

### 2.2 Handle

```java
public interface McpJavafxHandle extends AutoCloseable {
  McpJavafxConfig config();
  boolean isRunning();
  String endpoint(); // например "http://127.0.0.1:49321"
  @Override void close();
}
```

### 2.3 Конфигурация

```java
public record McpJavafxConfig(
  boolean enabled,
  Transport transport,     // HTTP_LOCAL по умолчанию
  String bindHost,         // "127.0.0.1" по умолчанию
  int port,                // 0 = auto
  String token,            // nullable => сгенерировать и залогировать
  boolean allowActions,    // true по умолчанию в debug, false можно для read-only
  SnapshotOptions snapshotDefaults,
  int fxTimeoutMs,         // 5000
  int serverShutdownMs     // 2000
) {}
```

`SnapshotOptions`:

```java
public record SnapshotOptions(
  int depth,
  boolean includeBounds,
  boolean includeLocalToScreen,
  boolean includeProperties,
  boolean includeVirtualization,
  boolean includeAccessibility
) {}
```

**Системные свойства** (для `startFromSystemProperties()`):

* `mcp.ui` (`true/false`)
* `mcp.transport` (`http` default)
* `mcp.bind` (default `127.0.0.1`)
* `mcp.port` (`0` default)
* `mcp.token` (optional)
* `mcp.allowActions` (`true/false`)
* `mcp.snapshot.depth`, `mcp.snapshot.bounds`, …

---

## 3) Архитектура модулей (один jar, но чёткие пакеты)

Один артефакт, но внутри разделение:

* `...core.model` — DTO (Snapshot/Node/Errors)
* `...core.capture` — сбор сцены/дерева
* `...core.query` — query/lookup, индексирование
* `...core.actions` — действия
* `...core.fx` — утилиты FX-thread, ожидание idle
* `...mcp` — MCP tool registry + протокол слой
* `...transport.http` — локальный сервер (JDK HttpServer допустим)
* `...util` — логирование/безопасность/генерация токена

---

## 4) Runtime требования

* Java: 17+ (лучше 21, но 17 минимум).
* JavaFX: 17+.
* Не требовать дополнительных native библиотек.
* Не использовать `com.sun.*` и reflection для “простого варианта” (кроме необязательных улучшений с feature-flag).

---

## 5) FX-thread контракт

### 5.1 Общее правило

Любой tool, который читает/меняет UI, обязан выполняться на FX Application Thread.

### 5.2 Утилита вызова

Внутренний контракт:

```java
<T> T Fx.exec(Callable<T> action, int timeoutMs) throws FxTimeoutException;
void Fx.run(Runnable action, int timeoutMs) throws FxTimeoutException;
```

Реализация:

* если уже на FX-thread → выполнить сразу,
* иначе `Platform.runLater` + `CompletableFuture` + timeout.

### 5.3 “awaitUiIdle”

Минимальная реализация (достаточно для дебага):

* после выполнения действий сделать `Fx.run(() -> {}, timeout)` два раза (двойной runLater),
* и/или “один pulse” через `AnimationTimer` на один тик.

---

## 6) Модель данных (строго как в контракте tools)

### 6.1 `UiSnapshot` / `UiNode` / virtualization

Использовать схему из предыдущего контракта с версией:

* `schema: "mcp-javafx-ui/1.0"`

**Обязательные поля у `UiNode`:**

* `ref.path`
* `type`
* `id` (nullable)
* `styleClass` (list, может быть пустым)
* `visible/managed/disabled`
* `children` (list, может быть пустым)

**Обязательные поля layout (если includeBounds=true):**

* `boundsInScene` минимум (width/height могут быть 0)

**Стабильный uid**: обязателен к реализации, даже если только внутри процесса:

* ключ в `node.getProperties()` = `"mcp.uid"`
* формат: `u-<base36 counter>` либо UUID (counter предпочтительнее для читаемости)

---

## 7) Сбор дерева (snapshotter)

### 7.1 Выбор Stage

Поддержать режимы:

* `focused` — окно с `isFocused()==true`, иначе stageIndex=0
* `primary` — stageIndex=0
* `all` — все showing stages

### 7.2 Источник перечня stages

Простой способ:

* `Window.getWindows()` → фильтр `instanceof Stage`
* сортировка: `(title nulls last)`, затем `System.identityHashCode(stage)`

### 7.3 Обход дерева

* старт: `stage.getScene().getRoot()`
* обход DFS до `depth`
* children: если node `instanceof Parent` → `getChildrenUnmodifiable()`

**Детерминизм**: порядок children строго как отдаёт JavaFX.

### 7.4 “Короткое описание” (summary)

Для query results:

* `Button[text=OK]`
* `TextField[text=..., prompt=...]`
* `TableView[items=1000, selected=...]`

---

## 8) Query

### 8.1 `css` query

* использовать `Scene.lookupAll(css)` в рамках выбранного stage.
* вернуть до `limit`.

### 8.2 `text` query

* проход по snapshot-дереву (или live дереву на FX-thread) и матч по:

  * `Labeled.getText()`
  * `TextInputControl.getText()`
  * `Text.getText()`
  * `Tab.getText()` (если сможете добраться через TabPane — опционально)

Нормализация текста:

* trim
* null-safe
* contains / equals (по умолчанию contains), режим задаётся полем `match: "contains|equals|regex"` (regex можно опустить в простом варианте).

### 8.3 “predicate” (опционально, но легко)

Поддержать минимум:

* `typeIs[]`
* `idEquals`
* `styleClassHas`
* `visible/enabled`
* `textContains`

---

## 9) Actions (perform)

### 9.1 Набор действий (минимум)

* `focus(target)`
* `click(target | x,y)`
* `typeText(text)` (в активный фокус)
* `setText(target, text)` (для `TextInputControl`)
* `pressKey(key, modifiers[])`
* `scroll(target, deltaY)`

### 9.2 Механизм клика/ввода

Простой и “встроенный”:

* `javafx.scene.robot.Robot`

Координаты:

* если `target` по ref → вычислить `localToScreen` bounds, клик в центр
* если bounds недоступны → ошибка `MCP_UI_ACTION_FAILED` с причиной `NO_SCREEN_BOUNDS`

Скролл:

* `Robot.scroll(amount)` или имитация через события (зависит от версии JavaFX; допускается best-effort).

### 9.3 setText

Если `target` — `TextInputControl`:

* `setText(text)`
* `positionCaret(text.length())`
  Иначе:
* ошибка `UNSUPPORTED_TARGET_TYPE`

---

## 10) Screenshot (опционально, но включить в “полнофункциональный”)

Использовать:

* `Node.snapshot(...)` для `scene.getRoot()` или `Scene.snapshot` (если доступно)
* вернуть PNG base64

Ограничения:

* только для showing stage
* scale = 1.0 (масштаб можно игнорировать или реализовать позже)

---

## 11) MCP tool layer

### 11.1 Инструменты (обязательные)

* `ui_get_snapshot`
* `ui_query`
* `ui_get_node`
* `ui_perform`
* `ui_screenshot`

### 11.2 Ошибки

Единый формат:

```json
{ "error": { "code": "...", "message": "...", "details": {...} } }
```

Минимальные коды:

* `MCP_UI_NOT_ENABLED`
* `MCP_UI_NO_STAGES`
* `MCP_UI_NODE_NOT_FOUND`
* `MCP_UI_STALE_REF`
* `MCP_UI_ACTION_FAILED`
* `MCP_UI_TIMEOUT`
* `MCP_UI_INTERNAL`

---

## 12) Transport (простой локальный HTTP)

Чтобы не усложнять MCP-часть, зафиксируй один транспорт: **локальный HTTP** (127.0.0.1) с токеном.

### 12.1 Endpoint’ы

* `GET /health` → `{ok:true, schema:"...", tools:[...]}`
* `POST /mcp` → принимает JSON `{tool:"...", input:{...}}` → возвращает `{output:{...}}` или `{error:{...}}`
* (опционально) `GET /events` SSE для логов/событий (можно пропустить)

### 12.2 Авторизация

* заголовок `Authorization: Bearer <token>`
* если токен не задан → сгенерировать и вывести в лог один раз.

### 12.3 Лимиты

* max body size: 2–5 MB (чтобы snapshot не убил память)
* rate limit не обязателен, но хотя бы “один запрос за раз” можно сделать mutex’ом.

---

## 13) Логирование и наблюдаемость

* при старте писать:

  * `MCP JavaFX Debug enabled`
  * `Endpoint: http://127.0.0.1:<port>`
  * `Token: <token>` (в debug это норм, но можно маскировать частично)
* (опционально) tool-call audit: имя tool, время, успех/ошибка.

---

## 14) Тестирование (минимальный план)

1. Unit: сериализация `UiNode` (id/styleClass/children order).
2. Integration: мини-приложение JavaFX:

   * Button + TextField + VBox
   * старт сервера
   * вызов `getSnapshot` проверка структуры
   * `query` по `#id`
   * `perform setText` + повторный snapshot
3. (если screenshot) сравнение размеров изображения > 0.

---

## 15) Ограничения и известные компромиссы (явно задокументировать)

* Virtualization: в простом варианте можно отдавать только метаданные (itemsCount/selected), а `visibleCells` — best-effort.
* MenuBar/ContextMenu/Dialogs могут жить в отдельных windows — поддержка best-effort через `Window.getWindows()`.
* Сложные кастомные контролы: `text/value` могут быть пустыми, зато дерево и bounds будут.

---

## 16) Definition of Done

Библиотека считается “готовой”, если:

* поднимается по `install()` и по `startFromSystemProperties()`,
* `ui_get_snapshot` возвращает детерминированное дерево с `uid` (предпочитать `mode=compact` для LLM),
* `ui_query` находит по `#id` и по тексту,
* `ui_perform` умеет `click`, `setText`, `typeText`, `pressKey`,
* (опционально) `ui_screenshot` возвращает png base64,
* все операции UI выполняются на FX-thread и имеют timeout’ы,
* доступ ограничен localhost + token.

