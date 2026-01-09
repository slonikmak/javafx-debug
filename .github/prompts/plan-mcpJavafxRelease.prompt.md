## Plan: Первый релиз mcp-javafx

Подготовим репозиторий так, чтобы первый релиз был воспроизводимым и понятным: единая SemVer-версия без `-SNAPSHOT` на релиз, артефакт для пользователей через GitHub Releases (в первую очередь shaded agent jar), и автоматизация через GitHub Actions (CI на PR + workflow на тег `v*` для сборки и публикации). НЕ НУЖНО — публикация в GitHub Packages.

### Steps 1) Зафиксировать формат релиза и артефакты
1. Определить “главный артефакт” релиза: использовать shaded agent jar из модуля (см. [mcp-javafx/pom.xml](mcp-javafx/pom.xml) плагин `maven-shade-plugin`).
2. Привести документацию к фактическим именам файлов: README сейчас ожидает `mcp-javafx-agent.jar`, а Maven выдаёт `mcp-javafx-…-shaded.jar` (см. [README.md](README.md)).
3. Выбрать стартовую версию (например, `0.1.0` или `1.0.0`) и договориться о SemVer + теги `vX.Y.Z`.

### Steps 2) Подготовить метаданные Maven-проекта (минимум для публичного релиза)
1. Добавить/проверить `<name>`, `<description>`, `<url>`, `<licenses>`, `<developers>`, `<scm>` в parent POM (см. [pom.xml](pom.xml)).
2. Убедиться, что координаты и версия едины для всех модулей и релиз делается “одной версией на репозиторий” (root parent version влияет на [mcp-javafx/pom.xml](mcp-javafx/pom.xml)).

### Steps 3) Настроить GitHub Actions: CI (PR/push)
1. Создать workflow “CI” для `mvn -q -B clean test` на Windows/Linux (минимум), чтобы стабильно проходили тесты из [mcp-javafx/src/test](mcp-javafx/src/test).
2. Кэшировать Maven (`~/.m2`) и зафиксировать Java 21 как в POM (см. [pom.xml](pom.xml)).
3. Публиковать test reports/артефакты (опционально) для диагностики e2e/JavaFX-TestFX фейлов.

### Steps 4) Настроить GitHub Actions: Release по тегу
1. Создать workflow “Release” на push тега `v*`:
   - собрать `mvn -q -B -DskipTests=false clean verify`
   - забрать артефакты из `mcp-javafx/target/` (shaded jar + sources/javadoc при желании)
2. Автоматически создать GitHub Release и приложить артефакты (shaded jar как основной).

### Steps 5) Процесс версий и выпуск “первого релиза”
1. Перед тегом: заменить `1.0.0-SNAPSHOT` → `X.Y.Z` в parent POM; убедиться, что README и пример запуска актуальны (см. [README.md](README.md)).
2. Создать тег `vX.Y.Z`, запустить release workflow, проверить вложения релиза.
3. После релиза: поднять версию до следующей `X.Y.(Z+1)-SNAPSHOT` (или `X.(Y+1).0-SNAPSHOT`).

### Steps 6) Релиз-заметки и минимальная поддержка пользователей
1. Добавить шаблон release notes/CHANGELOG (минимум: “что это”, “как скачать”, “как запустить”, “совместимость: Java 21”).
2. В README явно указать “Download” (Releases), команду запуска `-javaagent`, и пример конфигурации/эндпоинтов, соответствующий текущей спецификации (см. [docs/spec.md](docs/spec.md)).

### Further Considerations 1) Выбор “первой версии”
1. контракт стабилен и мы готовы к SemVer-обязательствам: `1.0.0`.
2. Публикация в GitHub Packages: не нужно.
3. публикуем только `*-shaded.jar`.
