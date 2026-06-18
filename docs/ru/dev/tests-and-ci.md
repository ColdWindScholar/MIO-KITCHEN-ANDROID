# Этап 9 — Тесты и CI

> Дата: 2026-06-17
> Статус: завершено
> Связанные этапы: финальный этап после Stage 8 (UI modernization)

---


Этап 9 закрывает цикл рефакторинга:

1. Добавлен единый runner `tools/run-all-checks.py`, который за один запуск
   вызывает каждый `tools/check-*.py` и `bash -n` для каждого shell-скрипта.
2. Добавлен новый архитектурный тест `tools/check-architecture.py`, который
   следит за границами слоёв между новыми пакетами (parser / validator /
   firmware / shell-runtime / ui-modern). Это статический аналог
   "архитектурного теста", который иначе требовал бы Detekt или ArchUnit.
3. Добавлен top-level CI workflow `.github/workflows/all-quality-gates.yml`,
   который запускает `run-all-checks.py` на каждом push и pull request.
4. Финализирована матрица CI-воркфлоу по этапам: у каждого этапа свой
   целевой workflow, срабатывающий только при изменении его файлов.

### Новые компоненты

```text
tools/
  run-all-checks.py             # единый runner — запускает все проверки + bash -n
  check-architecture.py         # контроль границ слоёв

.github/workflows/
  all-quality-gates.yml         # top-level workflow — запускает run-all-checks.py
  parser-split.yml              # воркфлоу этапа 5
  shell-runtime.yml             # воркфлоу этапа 6
  firmware-analyzer.yml         # воркфлоу этапа 7
  ui-modernization.yml          # воркфлоу этапа 8
```

### Контролируемые архитектурные правила

```text
parser/         -> НЕ импортирует shell/UI/Context
validator/      -> НЕ импортирует shell/UI/Context
firmware/       -> НЕ импортирует shell/UI/Context
shell/runtime/  -> НЕ импортирует firmware/UI
pio/ui/modern/  -> FirmwareAnalysisViewModel НЕ использует Activity/Handler
                  FirmwareProfileFormatter — чистый (без View/widget)
```

### Как запустить всё

```bash
python3 tools/run-all-checks.py
```

Ожидаемый вывод (сокращённо):

```text
============================================================
MIO-KITCHEN unified checks
============================================================

--- tools/validate-localization.py ---
PASS: localization contract is split correctly between common and UI
...

--- tools/check-parser-split.py ---
PASS: KrScript parser/runtime split is in place
...

--- tools/check-shell-runtime.py ---
PASS: Shell runtime typed API is in place
...

--- tools/check-firmware-analyzer.py ---
PASS: Firmware analyzer typed API is in place
...

--- tools/check-ui-modernization.py ---
PASS: UiState/UiStateHolder typed reactive state is in place
...

--- tools/check-architecture.py ---
PASS: parser layer does not import shell/UI/Context
PASS: validator layer does not import shell/UI/Context
PASS: firmware layer does not import shell/UI/Context
PASS: shell/runtime layer does not import firmware/UI
PASS: FirmwareAnalysisViewModel has no Activity/Handler dependencies
PASS: FirmwareProfileFormatter is pure (no View/widget imports)

============================================================
ALL CHECKS PASSED
```

### JVM unit-тесты, добавленные в этапах 5–8

```text
krscript/src/test/java/.../parser/PageConfigParserTest.kt
  -> 11 тестов: чистота парсера, типы узлов, правила валидатора

common/src/test/java/.../shell/runtime/ShellRuntimeTest.kt
  -> 15 тестов: ShellCommand/Event/Result, DryRun, Fake, Factory

common/src/test/java/.../firmware/FirmwareAnalyzerTest.kt
  -> 16 тестов: анализаторы zip, boot, super, vbmeta, filesystem

pio/src/test/java/.../ui/modern/FirmwareProfileFormatterTest.kt
  -> 8 тестов: Formatter + UiStateHolder
```

### Что намеренно НЕ сделано здесь

- Gradle `testDebugUnitTest` не добавлен в CI-воркфлоу, т.к. запуск Android
  Gradle-сборки требует Android SDK и Gradle-демона. Воркфлоу запускают только
  статические Python-проверки. Отдельный этап с полноценным Android CI-образом
  подключит `./gradlew testDebugUnitTest`.
- Detekt/Ktlint/Android Lint пока не добавлены.
- Coverage-отчётность (Jacoco/Kover) пока не добавлена.
- Instrumented-тесты (`androidTest`) пока не подключены к CI.
- ShellCheck для `tool.sh` не добавлен — `bash -n` остаётся единственной
  shell-проверкой.
