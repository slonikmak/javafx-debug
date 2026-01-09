Ниже — обновлённая и зафиксированная спека Streamable HTTP **с обязательным требованием использовать официальный MCP SDK** (Java). Спека явно запрещает “самописный JSON-RPC”, фиксирует слой SDK как нормативный, и описывает, **где именно SDK используется и где заканчивается его ответственность**.

---

# Спецификация интеграции MCP Streamable HTTP

## (Simple but Full, **SDK-mandatory**)

### Статус

**Normative / MUST-level**
Любая совместимая реализация **обязана** использовать официальный MCP SDK. Реализации без SDK считаются **несовместимыми**.

---

## 1) Обязательное требование: использование MCP SDK

### 1.1 Нормативное требование

Реализация MCP-сервера **MUST**:

1. Использовать **официальный MCP SDK** для:

   * JSON-RPC обработки,
   * Streamable HTTP транспорта,
   * регистрации tools,
   * генерации MCP-capabilities.
2. **MUST NOT**:

   * реализовывать собственный JSON-RPC парсер,
   * реализовывать собственный MCP-протокол поверх HTTP,
   * отклоняться от семантики MCP tools/resources/capabilities.

> Цель: гарантировать полную совместимость с MCP-клиентами (IDE, Inspector, LLM-хосты) и корректную эволюцию протокола.

---

## 2) Допустимый SDK и версия

### 2.1 SDK

* Язык: **Java**
* SDK: **Official MCP Java SDK**
* Тип сервера: **Server (sync или async)**

### 2.2 Минимальные требования к SDK

Реализация **MUST** использовать SDK, который поддерживает:

* MCP Server
* Tool registration
* Streamable HTTP transport
* Stateless Streamable HTTP profile

> Версия SDK фиксируется в `mcp-javafx-debug` BOM или gradle version catalog.
> Повышение версии SDK считается совместимым, если не нарушает MCP protocol version.

---

## 3) Архитектурный контракт (обновлён)

```
┌──────────────────────────────────────────┐
│ JavaFX Application                       │
│                                          │
│  ┌────────────────────────────────────┐  │
│  │ mcp-javafx-core                     │  │
│  │  - Snapshotter                     │  │
│  │  - QueryEngine                     │  │
│  │  - ActionEngine                    │  │
│  │  - FxThread utils                  │  │
│  └──────────────▲─────────────────────┘  │
│                 │ calls on FX thread     │
│  ┌──────────────┴─────────────────────┐  │
│  │ MCP Adapter (SDK-bound)             │  │
│  │  - Tool handlers                   │  │
│  │  - Input validation                │  │
│  │  - Error mapping                   │  │
│  └──────────────▲─────────────────────┘  │
│                 │ MCP SDK API             │
│  ┌──────────────┴─────────────────────┐  │
│  │ MCP Java SDK                        │  │
│  │  - McpServer                       │  │
│  │  - ToolRegistry                    │  │
│  │  - JSON-RPC                        │  │
│  │  - Streamable HTTP (Stateless)     │  │
│  └──────────────▲─────────────────────┘  │
│                 │ HTTP                   │
│  ┌──────────────┴─────────────────────┐  │
│  │ Embedded HTTP server               │  │
│  │ (Jetty / Undertow / equivalent)    │  │
│  └────────────────────────────────────┘  │
└──────────────────────────────────────────┘
```

---

## 4) Transport: Streamable HTTP (SDK-bound)

### 4.1 Обязательное правило

Streamable HTTP **MUST** быть реализован **через транспорт SDK**, а не вручную.

Допустимые варианты:

* SDK Servlet-based Streamable HTTP (stateless)
* SDK WebFlux/WebMVC Streamable HTTP (если приложение уже использует Spring)

Запрещено:

* “ручное” чтение POST body и самостоятельная маршрутизация MCP.

---

## 5) Профиль транспорта (фиксированный)

### 5.1 Профиль

* **Stateless Streamable HTTP**
* Без SSE (`GET /mcp` → `405`)
* Без session management (`Mcp-Session-Id` игнорируется)

### 5.2 Endpoint

* `/mcp` — MCP endpoint (через SDK transport)
* `/health` — не-MCP, реализуется вручную

---

## 6) Security (SDK + внешний слой)

### 6.1 Что делает SDK

SDK **обязан**:

* валидировать MCP JSON-RPC структуру,
* корректно обрабатывать batching,
* корректно формировать JSON-RPC errors.

### 6.2 Что делает библиотека (вне SDK)

Реализация **MUST** добавить внешний HTTP-фильтр / middleware **до SDK transport**, который выполняет:

1. **Origin validation** (DNS rebinding protection)
2. **Authorization: Bearer token**
3. **Bind restriction** (localhost)

SDK **не должен** модифицироваться для этого.

---

## 7) MCP Server configuration (SDK)

### 7.1 Обязательные параметры сервера

При создании MCP server через SDK реализация **MUST**:

* задать `serverInfo(name, version)`
* включить capability `tools`
* включить capability `logging`
* включить **immediate execution**, если SDK это поддерживает

Причина:

* tool handlers сами управляют переходом на FX-thread.

---

## 8) Tools registration (SDK-level)

### 8.1 Обязательные tools

Через SDK tool registry **MUST** быть зарегистрированы:

* `ui.getSnapshot`
* `ui.query`
* `ui.getNode`
* `ui.perform`
* `ui.screenshot` (optional, но рекомендуется)

Каждый tool:

* имеет description,
* имеет JSON Schema input,
* возвращает structured JSON output,
* не выбрасывает исключения за пределы SDK handler.

---

## 9) Error handling (SDK-compliant)

### 9.1 Mapping

Все внутренние ошибки (`MCP_UI_*`) **MUST**:

* быть преобразованы в **JSON-RPC error** через SDK API,
* иметь стабильный `code` и `message`,
* не приводить к HTTP 500, если ошибка логическая (например, node not found).

HTTP 500 допустим **только** для:

* SDK internal failure,
* неперехваченного runtime error.

---

## 10) Запрещённые реализации (explicit non-goals)

Следующие варианты **запрещены** данной спецификацией:

* ❌ Самописный MCP сервер без SDK
* ❌ Самописный JSON-RPC поверх HTTP
* ❌ Обработка MCP tools вне SDK abstraction
* ❌ Использование MCP только “логически”, но не протокольно

Такие реализации **не считаются MCP-серверами** в рамках этой библиотеки.

---

## 11) Definition of Done (обновлённый)

Реализация считается соответствующей спеке, если:

* [ ] MCP сервер создан **через официальный MCP SDK**
* [ ] Streamable HTTP реализован **через SDK transport**
* [ ] Нет собственного JSON-RPC кода
* [ ] Tools зарегистрированы через SDK registry
* [ ] Security-фильтры стоят **перед** SDK transport
* [ ] FX-thread correctness обеспечена внутри tool handlers
* [ ] Сервер корректно работает с MCP Inspector / IDE-клиентами

---

## 12) Рекомендованное дальнейшее расширение (не часть MUST)

* Включить SSE (`GET /mcp`) через SDK
* Добавить stateful Streamable HTTP
* Добавить stdio transport через SDK
* Добавить resource endpoints (`ui.tree`, `ui.focusedNode`)
