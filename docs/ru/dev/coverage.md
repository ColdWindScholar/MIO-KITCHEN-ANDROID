# Этап 17 — Отчётность покрытия

> Дата: 2026-06-18
> Статус: завершено
> Связанные этапы: после Stage 16 (static analysis), перед Stage 18 (instrumented tests)

---

Этап 17 подключает [Kover](https://github.com/Kotlin/kotlinx-kover) к сборке
для отчётов покрытия JVM unit-тестов. Kover выбран вместо Jacoco, т.к. он
Kotlin-first и лучше интегрируется с Kotlin/JVM test-tasks, добавленными в
этапах 5-8.

## Что делает этот этап

- Применяет `org.jetbrains.kotlinx.kover` 0.8.3 на root-уровне.
- Применяет Kover в модулях `common`, `krscript`, `pio`.
- Добавляет aggregate-task `runCoverageReport` в root `build.gradle`,
  зависящий от `:common:testDebugUnitTest`, `:krscript:testDebugUnitTest`,
  `:pio:testDebugUnitTest`.

## CI-проверка

```bash
python3 tools/check-coverage.py
```

Ожидаемый вывод:

```text
PASS: Kover plugin 0.8.3 declared at root level
PASS: Kover applied to common/krscript/pio modules
PASS: runCoverageReport aggregate task is declared
```

## Gradle-вызов

```bash
# Запустить unit-тесты и построить отчёты покрытия:
./gradlew runCoverageReport

# Per-module HTML + XML отчёты Kover:
./gradlew :common:koverHtmlReport :common:koverXmlReport
./gradlew :krscript:koverHtmlReport :krscript:koverXmlReport
./gradlew :pio:koverHtmlReport :pio:koverXmlReport
```

Отчёты сохраняются в `common/build/reports/kover/htmlDebug/index.html` и т.д.

## Что намеренно НЕ сделано здесь

- Kover применён с конфигурацией по умолчанию — пока без порогов минимального
  покрытия. Добавление `kover { verify { rule { ... } } }` ломало бы сборку
  ниже порога; это будет добавлено после измерения baseline-покрытия legacy-кода.
- CI-воркфлоу пока НЕ запускает `./gradlew runCoverageReport` — нужен Android
  SDK образ. Скрипт `tools/check-coverage.py` статически проверяет конфигурацию.
- Покрытие instrumented-тестов (androidTest) не настроено — это Stage 18.
- Интеграции с codecov.io пока нет.
