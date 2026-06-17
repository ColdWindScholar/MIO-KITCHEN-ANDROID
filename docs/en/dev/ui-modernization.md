# Stage 8 — UI modernization

> Date: 2026-06-17
> Status: completed
> Related stages: after Stage 7 (firmware analyzer), before Stage 9 (tests/CI)

---


Stage 3 already enabled ViewBinding and migrated the existing activities to it.
Stage 8 builds on that foundation and adds the remaining UI-modernization
pieces that the roadmap demands:

- A typed `UiState` / `UiStateHolder` reactive state container (sealed class +
  `StateFlow`), so new code does not sprinkle `Boolean isBusy` + `String? error`
  fields across ViewModels.
- Modern Activity Result API helpers (`OpenDocumentHelper`, `CreateDocumentHelper`,
  `UriPermissionPersistor`) so new code does not use the deprecated
  `startActivityForResult` + `onActivityResult` pair.
- `FirmwareAnalysisViewModel` — the first ViewModel that wires together the new
  layers: `FirmwareAnalyzer` (Stage 7) + `UiStateHolder` + `viewModelScope` +
  `Dispatchers.IO`.
- `FirmwareProfileFormatter` — a pure formatter that turns a `FirmwareProfile`
  into human-readable text. Pure = testable in JVM without Robolectric.

### New components

```text
common/ui/
  UiState.kt                    # UiState<T> sealed class + UiStateHolder<T>

pio/ui/modern/
  ActivityResultHelpers.kt      # OpenDocumentHelper, CreateDocumentHelper,
                                # UriPermissionPersistor
  FirmwareAnalysisViewModel.kt  # ViewModel for the firmware analysis screen
  FirmwareProfileFormatter.kt   # pure profile -> human-readable text

pio/src/test/java/.../ui/modern/
  FirmwareProfileFormatterTest.kt  # JVM tests
```

### Architectural rules

```text
UiState/UiStateHolder
  -> sealed class, no Context, no View
  -> StateFlow-based, reactive

FirmwareAnalysisViewModel
  -> extends ViewModel
  -> uses viewModelScope + Dispatchers.IO
  -> does NOT import android.app.Activity, android.os.Handler
  -> does NOT use activity!! / context!!

ActivityResultHelpers
  -> use ActivityResultCaller (modern API)
  -> do NOT use startActivityForResult / onActivityResult

FirmwareProfileFormatter
  -> pure, no View/widget imports
```

### CI gate

```bash
python3 tools/check-ui-modernization.py
```

Expected output:

```text
PASS: UiState/UiStateHolder typed reactive state is in place
PASS: OpenDocumentHelper/CreateDocumentHelper use modern Activity Result API
PASS: FirmwareAnalysisViewModel extends ViewModel and uses viewModelScope
PASS: FirmwareAnalysisViewModel has no Context/Handler/activity!! dependencies
PASS: FirmwareProfileFormatter is pure (no View/widget imports)
PASS: pio/build.gradle declares lifecycle + activity-ktx dependencies
PASS: UI modernization JVM unit tests are present
```

### What is intentionally NOT done here

- Existing activities (`MainActivity`, `ActionPage`, `ActivityFileSelector`,
  `SplashActivity`) are NOT migrated to the new ViewModel pattern — they still
  use `Handler`, `Runnable` and the legacy `PageConfigReader`. The migration is
  deferred to a focused UI-rewrite stage.
- The new `FirmwareAnalysisViewModel` is not yet wired into a real Activity —
  it is the recommended entry point for the future "unified firmware analysis
  screen" the roadmap mentions.
- Compose is not introduced yet.

---
