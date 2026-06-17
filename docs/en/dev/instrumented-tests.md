# Stage 18 — Instrumented test scaffolding

> Date: 2026-06-18
> Status: completed
> Related stages: after Stage 17 (coverage), before Stage 19 (final integration)

---

Stages 5-8 added JVM unit tests for all the new components. Stage 18 adds the
matching **instrumented** tests that verify the same components on a real
Android device (or emulator). These tests are necessary because some
behaviors — `Context.getExternalFilesDir(...)`, `AssetManager.open(...)`,
`ContentResolver`, real `Build.*` values — can only be exercised on Android.

## What this stage does

Adds three instrumented tests:

1. `FirmwareWorkspaceInstrumentedTest` — verifies that workspace directories
   are created inside the app's external files dir, that `prepareImportFile`
   returns unique paths, that `prepareExportFile` falls back to a default name,
   and that `clearOldImports` actually deletes aged files.
2. `ZipFirmwareAnalyzerInstrumentedTest` — writes a real zip file with
   `payload.bin` and `boot.img` entries to the device's external cache, then
   verifies that `ZipFirmwareAnalyzer` detects `PAYLOAD_BIN` + `usesAB` +
   `hasBootImage` + Android 10 hint.
3. `RuntimePermissionHelperInstrumentedTest` — verifies that
   `requiredPermissions()` returns `POST_NOTIFICATIONS` on Android 13+ and
   `READ_EXTERNAL_STORAGE` on Android ≤ 12, and that
   `areAllGranted` / `missingPermissions` do not throw on a fresh context.

Also refreshes the legacy empty `ExampleInstrumentedTest.java` to assert the
package name.

## New tests

```text
common/src/androidTest/java/com/omarea/common/
  ExampleInstrumentedTest.java                              (refreshed)
  storage/FirmwareWorkspaceInstrumentedTest.kt              (4 tests)
  firmware/ZipFirmwareAnalyzerInstrumentedTest.kt           (1 test)

pio/src/androidTest/java/com/mio/kitchen/ui/modern/
  RuntimePermissionHelperInstrumentedTest.kt                (1 test)
```

## CI gate

```bash
python3 tools/check-instrumented-tests.py
```

Expected output:

```text
PASS: FirmwareWorkspaceInstrumentedTest covers workspace creation on device
PASS: ZipFirmwareAnalyzerInstrumentedTest covers real zip parsing on device
PASS: RuntimePermissionHelperInstrumentedTest covers permission list per Android version
PASS: All instrumented tests use AndroidJUnit4 + ApplicationProvider
PASS: common + pio modules declare androidTest dependencies
```

## Gradle invocation

```bash
# Run instrumented tests on a connected device/emulator:
./gradlew :common:connectedDebugAndroidTest
./gradlew :pio:connectedDebugAndroidTest
```

## What is intentionally NOT done here

- The CI workflow does NOT run `./gradlew connectedDebugAndroidTest` yet —
  it requires a device/emulator image. The `tools/check-instrumented-tests.py`
  script verifies the configuration statically.
- No coverage instrumentation for instrumented tests — that requires a
  follow-up to wire Kover into `connectedDebugAndroidTest`.
- No UI automation tests (Espresso) for `FirmwareAnalysisActivity` yet —
  the activity is a reference implementation and its UI automation will be
  added when the layout is promoted to XML.
- The `ExampleInstrumentedTest` package-name assertion (`com.omarea.common.test`)
  is a sanity check; the actual applicationId comes from the test runner
  configuration and may differ in release builds.
