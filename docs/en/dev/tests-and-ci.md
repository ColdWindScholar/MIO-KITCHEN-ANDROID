# Stage 9 — Tests and CI

> Date: 2026-06-17
> Status: completed
> Related stages: final stage after Stage 8 (UI modernization)

---


Stage 9 closes the refactoring cycle by:

1. Adding a unified check runner `tools/run-all-checks.py` that runs every
   `tools/check-*.py` and `bash -n` on every shell script in one shot.
2. Adding a new architecture test `tools/check-architecture.py` that enforces
   layer boundaries between the new packages (parser / validator / firmware /
   shell-runtime / ui-modern). This is the static equivalent of an "architecture
   test" that would otherwise require a Detekt or ArchUnit setup.
3. Adding a top-level CI workflow `.github/workflows/all-quality-gates.yml`
   that runs `run-all-checks.py` on every push and pull request.
4. Finalising the per-stage CI workflow matrix so each stage has its own
   focused workflow that only triggers when its files change.

### New components

```text
tools/
  run-all-checks.py             # unified runner — runs every check + bash -n
  check-architecture.py         # layer-boundary enforcement

.github/workflows/
  all-quality-gates.yml         # top-level workflow — runs run-all-checks.py
  parser-split.yml              # Stage 5 focused workflow
  shell-runtime.yml             # Stage 6 focused workflow
  firmware-analyzer.yml         # Stage 7 focused workflow
  ui-modernization.yml          # Stage 8 focused workflow
```

### Architecture rules enforced

```text
parser/         -> must NOT import shell/UI/Context
validator/      -> must NOT import shell/UI/Context
firmware/       -> must NOT import shell/UI/Context
shell/runtime/  -> must NOT import firmware/UI
pio/ui/modern/  -> FirmwareAnalysisViewModel must NOT use Activity/Handler
                  FirmwareProfileFormatter must be pure (no View/widget)
```

### How to run everything

```bash
python3 tools/run-all-checks.py
```

Expected output (truncated):

```text
============================================================
MIO-KITCHEN unified checks
============================================================

--- tools/validate-localization.py ---
PASS: localization contract is split correctly between common and UI
PASS: string resources are complete for languages: en, ru, zh, ja
PASS: placeholders match across localized resources
PASS: source @string references resolve to existing resources
PASS: runtime translation fallbacks are disabled
PASS: RU/EN localization documentation exists

--- tools/check-known-regressions.py ---
PASS: known critical regressions are fixed

--- tools/check-build-modernization.py ---
PASS: build modernization baseline is in place
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

--- bash -n pio/src/main/assets/script/tool.sh ---
--- bash -n pio/src/main/assets/script/start.sh ---
--- bash -n pio/src/main/assets/script2/executor.sh ---

============================================================
ALL CHECKS PASSED
```

### JVM unit tests added across stages 5–8

```text
krscript/src/test/java/.../parser/PageConfigParserTest.kt
  -> 11 tests covering parser purity, node types, validator rules

common/src/test/java/.../shell/runtime/ShellRuntimeTest.kt
  -> 15 tests covering ShellCommand/Event/Result, DryRun, Fake, Factory

common/src/test/java/.../firmware/FirmwareAnalyzerTest.kt
  -> 16 tests covering zip, boot, super, vbmeta, filesystem analyzers

pio/src/test/java/.../ui/modern/FirmwareProfileFormatterTest.kt
  -> 8 tests covering Formatter + UiStateHolder
```

### What is intentionally NOT done here

- Gradle `testDebugUnitTest` is not added to the CI workflow because running
  the Android Gradle build needs the Android SDK and a Gradle daemon. The
  workflows run only the static Python checks. A future stage with a
  proper Android CI image will wire up `./gradlew testDebugUnitTest`.
- Detekt/Ktlint/Android Lint are not added yet.
- Coverage reporting (Jacoco/Kover) is not added yet.
- Instrumented tests (`androidTest`) are not wired into CI yet.
- ShellCheck on `tool.sh` is not added — `bash -n` is the only shell check.

---
