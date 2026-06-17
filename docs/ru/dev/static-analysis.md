# Этап 16 — Статический analysis

> Дата: 2026-06-18
> Статус: завершено
> Связанные этапы: после Stage 15 (UI migration), перед Stage 17 (coverage)

---

Этап 9 добавил `tools/check-architecture.py` как кастомную защиту границ слоёв.
Этап 16 приносит в проект стандартные инструменты статического analysis:
Detekt, ktlint, Android Lint и ShellCheck — все подключены к Gradle-сборке и
настраиваются через centralized-конфиги.

## Что делает этот этап

- Добавляет `config/detekt/detekt.yml` — конфигурацию Detekt, покрывающую
  complexity, coroutines, empty blocks, exceptions, naming, performance,
  potential bugs и style. Запрещает `GlobalCoroutineUsage` (без `GlobalScope`).
- Добавляет `config/lint.xml` — конфигурацию Android Lint, которая приглушает
  известные шумные категории (`ScopedStorage`, `OldTargetApi`,
  `TrustAllX509TrustManager` и др.) без полного отключения lint.
- Добавляет `.editorconfig` — правила ktlint (no-wildcard-imports,
  no-trailing-spaces, final-newline, modifier-ordering, max-line-length=140)
  плюс общий code-style для YAML/XML/Python/Gradle.
- Добавляет `config/.shellcheckrc` — конфигурацию ShellCheck, которая
  отключает `SC2086` (намеренный word-splitting в firmware-скриптах) и другие
  false-positive категории.
- Применяет плагины `io.gitlab.arturbosch.detekt` 1.23.7 и
  `org.jlleitschuh.gradle.ktlint` 12.1.1 на root-уровне.
- Применяет оба плагина в модулях `common`, `krscript`, `pio`.
- Добавляет aggregate-task `runStaticAnalysis` в root `build.gradle`.

## Новая конфигурация

```text
config/
  detekt/detekt.yml     # правила Detekt (complexity, coroutines, style, ...)
  lint.xml              # overrides для Android Lint
  .shellcheckrc         # конфигурация ShellCheck

.editorconfig           # ktlint + общий code-style
```

## CI-проверка

```bash
python3 tools/check-static-analysis.py
```

Ожидаемый вывод:

```text
PASS: Detekt configuration with key rules is in place
PASS: Android Lint configuration overrides ScopedStorage/OldTargetApi/etc
PASS: ktlint rules are declared in .editorconfig
PASS: ShellCheck configuration is present (SC2086 disabled)
PASS: root build.gradle applies detekt + ktlint plugins
PASS: common/krscript/pio modules apply detekt + ktlint and use centralized config
PASS: runStaticAnalysis aggregate task is declared
```

## Gradle-вызов

```bash
# Запустить весь статический analysis по модулям.
./gradlew runStaticAnalysis

# Detekt по модулям:
./gradlew :common:detekt :krscript:detekt :pio:detekt

# ktlint по модулям:
./gradlew :common:ktlintCheck :krscript:ktlintCheck :pio:ktlintCheck

# Android Lint:
./gradlew :pio:lint
```

## Что намеренно НЕ сделано здесь

- `maxIssues: 0` в Detekt установлен, но `ignoreFailures = true` для ktlint
  в module-build-файлах, чтобы сборка не ломалась на legacy-коде. Следующий
  этап ужесточит это после очистки legacy-кода.
- `abortOnError = false` в Lint сохранён (по той же причине). Baseline-файл
  (`lint-baseline.xml`) будет добавлен, чтобы отслеживать только новые issues.
- ShellCheck настроен, но пока не вызывается из Gradle-task — ожидается, что
  он запускается через `tools/run-all-checks.py` или напрямую.
- Нет custom-rules для Detekt — конфигурация использует только built-in.
- CI-воркфлоу пока НЕ запускает `./gradlew runStaticAnalysis` — это требует
  Android SDK образ. Скрипт `tools/check-static-analysis.py` статически
  проверяет конфигурацию, что и делает текущий CI.
