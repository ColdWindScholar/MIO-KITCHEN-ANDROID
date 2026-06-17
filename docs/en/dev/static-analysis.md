# Stage 16 — Static analysis

> Date: 2026-06-18
> Status: completed
> Related stages: after Stage 15 (UI migration), before Stage 17 (coverage)

---

Stage 9 added `tools/check-architecture.py` as a custom layer-boundary guard.
Stage 16 brings industry-standard static analysis tooling to the project:
Detekt, ktlint, Android Lint, and ShellCheck — all wired into the Gradle build
and configurable through centralized config files.

## What this stage does

- Adds `config/detekt/detekt.yml` — Detekt configuration covering complexity,
  coroutines, empty blocks, exceptions, naming, performance, potential bugs,
  and style. Enforces `GlobalCoroutineUsage` (no `GlobalScope`).
- Adds `config/lint.xml` — Android Lint configuration that quiets known
  noisy categories (`ScopedStorage`, `OldTargetApi`, `TrustAllX509TrustManager`,
  etc.) without disabling lint entirely.
- Adds `.editorconfig` — ktlint rules (no-wildcard-imports, no-trailing-spaces,
  final-newline, modifier-ordering, max-line-length=140) plus general code
  style for YAML/XML/Python/Gradle.
- Adds `config/.shellcheckrc` — ShellCheck configuration that disables
  `SC2086` (intentional word-splitting in firmware scripts) and other
  false-positive categories.
- Applies `io.gitlab.arturbosch.detekt` 1.23.7 and
  `org.jlleitschuh.gradle.ktlint` 12.1.1 plugins at the root level.
- Applies both plugins in `common`, `krscript`, `pio` modules.
- Adds `runStaticAnalysis` aggregate task in the root `build.gradle`.

## New configuration

```text
config/
  detekt/detekt.yml     # Detekt rules (complexity, coroutines, style, ...)
  lint.xml              # Android Lint overrides
  .shellcheckrc         # ShellCheck configuration

.editorconfig           # ktlint + general code style
```

## CI gate

```bash
python3 tools/check-static-analysis.py
```

Expected output:

```text
PASS: Detekt configuration with key rules is in place
PASS: Android Lint configuration overrides ScopedStorage/OldTargetApi/etc
PASS: ktlint rules are declared in .editorconfig
PASS: ShellCheck configuration is present (SC2086 disabled)
PASS: root build.gradle applies detekt + ktlint plugins
PASS: common/krscript/pio modules apply detekt + ktlint and use centralized config
PASS: runStaticAnalysis aggregate task is declared
```

## Gradle invocation

```bash
# Run all static analysis across modules.
./gradlew runStaticAnalysis

# Per-module detekt:
./gradlew :common:detekt :krscript:detekt :pio:detekt

# Per-module ktlint:
./gradlew :common:ktlintCheck :krscript:ktlintCheck :pio:ktlintCheck

# Android Lint:
./gradlew :pio:lint
```

## What is intentionally NOT done here

- Detekt's `maxIssues: 0` is set, but `ignoreFailures = true` is set for ktlint
  in the module build files so the build does not break on the legacy code.
  A follow-up stage will tighten this once the legacy code is cleaned up.
- Lint's `abortOnError = false` is kept (same reason). A baseline file
  (`lint-baseline.xml`) will be added in a follow-up to track new issues only.
- ShellCheck is configured but not yet invoked from a Gradle task — it is
  expected to run via the `tools/run-all-checks.py` script or directly.
- No Detekt custom rules — the configuration uses only built-in rules.
- No CI workflow runs `./gradlew runStaticAnalysis` yet — it requires the
  Android SDK image. The `tools/check-static-analysis.py` script verifies the
  configuration statically, which is what the current CI can do.
