# Stage 17 — Coverage reporting

> Date: 2026-06-18
> Status: completed
> Related stages: after Stage 16 (static analysis), before Stage 18 (instrumented tests)

---

Stage 17 wires [Kover](https://github.com/Kotlin/kotlinx-kover) into the build
to produce JVM unit-test coverage reports. Kover was chosen over Jacoco
because it is Kotlin-first and integrates better with the Kotlin/JVM test
tasks already added in Stages 5-8.

## What this stage does

- Applies `org.jetbrains.kotlinx.kover` 0.8.3 at the root level.
- Applies Kover in `common`, `krscript`, `pio` modules.
- Adds `runCoverageReport` aggregate task in the root `build.gradle` that
  depends on `:common:testDebugUnitTest`, `:krscript:testDebugUnitTest`,
  `:pio:testDebugUnitTest`.

## CI gate

```bash
python3 tools/check-coverage.py
```

Expected output:

```text
PASS: Kover plugin 0.8.3 declared at root level
PASS: Kover applied to common/krscript/pio modules
PASS: runCoverageReport aggregate task is declared
```

## Gradle invocation

```bash
# Run unit tests and produce coverage reports:
./gradlew runCoverageReport

# Per-module Kover HTML + XML reports:
./gradlew :common:koverHtmlReport :common:koverXmlReport
./gradlew :krscript:koverHtmlReport :krscript:koverXmlReport
./gradlew :pio:koverHtmlReport :pio:koverXmlReport
```

Reports land in `common/build/reports/kover/htmlDebug/index.html` etc.

## What is intentionally NOT done here

- Kover is applied with default configuration — no minimum coverage thresholds
  yet. Adding `kover { verify { rule { ... } } }` would fail the build below
  a coverage threshold; this will be added once the legacy code baseline is
  measured.
- The CI workflow does NOT run `./gradlew runCoverageReport` yet — it needs
  the Android SDK image. The `tools/check-coverage.py` script verifies the
  configuration statically.
- Coverage of instrumented tests (androidTest) is not configured — that's
  Stage 18.
- No codecov.io integration yet.
