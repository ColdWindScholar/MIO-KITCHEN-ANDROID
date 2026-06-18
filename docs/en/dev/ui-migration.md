# Stage 15 — UI migration to AppRuntimeProfile

> Date: 2026-06-18
> Status: completed
> Related stages: after Stage 14 (export policy), before Stage 16 (static analysis)

---

Stage 8 introduced `FirmwareAnalysisViewModel` and `UiState` as the recommended
path for new UI code, but no activity actually consumed them. Stage 15 closes
that gap by adding `FirmwareAnalysisActivity` — a reference implementation that
wires together every component from Stages 4-14.

## What this stage does

Adds `FirmwareAnalysisActivity` that, in one screen:

1. Requests runtime permissions via `RuntimePermissionHelper` (Stage 13).
2. Launches SAF picker via `OpenDocumentHelper` (Stage 8).
3. Resolves the picked `content://` URI to a shell path via
   `AndroidStorageGateway` (Stage 4) on `Dispatchers.IO`.
4. Feeds the shell path to `FirmwareAnalysisViewModel.analyzeFile()` (Stage 8),
   which uses `FirmwareAnalyzerRegistry` (Stage 7).
5. Observes `UiState<FirmwareProfile>` via `repeatOnLifecycle` (Stage 8).
6. Renders the result via `FirmwareProfileFormatter.detailed()` (Stage 8).
7. Starts `FirmwareOperationService` (Stage 13) for foreground state during
   the analysis.

The existing activities (`MainActivity`, `ActionPage`, `ActivityFileSelector`,
`SplashActivity`) are NOT touched. They keep using the legacy
`PageConfigReader` / `ScriptEnvironmen` path. This activity is the recommended
entry point for new UI work and the template for migrating existing activities.

## New components

```text
pio/src/main/java/com/mio/kitchen/ui/modern/
  FirmwareAnalysisActivity.kt    # reference activity wiring all stages
```

## Manifest registration

```xml
<activity
    android:name=".ui.modern.FirmwareAnalysisActivity"
    android:label="@string/app_name"
    android:configChanges="keyboardHidden|orientation|uiMode|..."
    android:screenOrientation="portrait"
    android:exported="false" />
```

## CI gate

```bash
python3 tools/check-ui-migration.py
```

Expected output:

```text
PASS: FirmwareAnalysisActivity wires all Stage 4-13 components together
PASS: Activity uses ViewModel + StateFlow via repeatOnLifecycle
PASS: Activity uses RuntimePermissionHelper before launching picker
PASS: Activity uses OpenDocumentHelper for SAF picker
PASS: Activity uses AndroidStorageGateway for content:// resolution
PASS: Activity starts FirmwareOperationService for foreground state
PASS: Activity is registered in AndroidManifest
```

## What is intentionally NOT done here

- The activity uses a minimal programmatically-built layout (no XML). A
  proper layout XML with Material Design components will be added in a
  follow-up stage.
- The activity does NOT yet launch an `OperationExecutor` — it only analyzes
  the firmware. A follow-up "operations" screen will pick an operation,
  build an `OperationPlan`, and call `OperationExecutor.executeForResult`.
- The existing `MainActivity` is NOT replaced. The launcher icon still goes
  to `SplashActivity` → `MainActivity`. A future stage may add a settings
  toggle to switch between legacy and modern UI.
- The activity has no localized strings of its own (uses `R.string.app_name`
  and hardcoded English). Localization will be added when the layout is
  promoted to XML.
