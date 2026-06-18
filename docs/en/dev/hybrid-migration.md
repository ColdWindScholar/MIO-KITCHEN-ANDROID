# Stage 20 — Hybrid migration layer

> Date: 2026-06-18
> Status: completed
> Related stages: after Stage 19 (user docs), before Stage 21 (ToolchainInstaller wiring)

---

The original roadmap item #1 ("перевести существующие активности с legacy
PageConfigReader на новый AppRuntimeProfileResolver") is the largest UI
rewrite in the project — four activities, ~1200 lines of UI code that depends
on `PageConfigReader`/`ScriptEnvironmen`/`KrScriptConfig`/`KeepShell`. A
wholesale replacement would risk breaking the visual behavior that existing
users rely on.

Stage 20 takes a pragmatic **hybrid** approach: instead of replacing the
legacy path, we add a parallel **`AppRuntimeStore`** singleton that mirrors
the legacy state into the new `AppRuntimeProfile` data class. Legacy
activities update the store at the right lifecycle points; new activities
(read: `FirmwareAnalysisActivity`) read the store directly. This gives us:

- One source of truth for device/firmware/operation state, observable via
  `StateFlow`.
- New code can be written against `AppRuntimeProfile` without waiting for
  the legacy activities to be rewritten.
- Legacy activities keep working unchanged — no regression risk.
- Gradual migration path: each activity can later move more logic to the new
  path without breaking others.

## What this stage does

Adds `AppRuntimeStore` — a singleton object backed by
`MutableStateFlow<AppRuntimeProfile?>`. It exposes:

- `profile: StateFlow<AppRuntimeProfile?>` — the observable current profile.
- `init(rootAvailable)` — initialises the device portion via
  `DeviceProfileProvider`. Safe to call repeatedly.
- `updateRootStatus(hasRoot)` — refreshes the device's `hasRoot` field after
  `CheckRootStatus` completes.
- `setFirmware(shellPath)` — runs `FirmwareAnalyzerRegistry.analyze(...)` on
  a background thread and updates the firmware portion.
- `resetFirmware()` — clears firmware + active operation.
- `device: DeviceProfile?` / `firmware: FirmwareProfile?` — convenience
  accessors.

Then wires it into every existing activity:

### SplashActivity

- Calls `AppRuntimeStore.init(rootAvailable = null)` at the top of `onCreate`.
- Calls `AppRuntimeStore.updateRootStatus(hasRoot = true)` after
  `CheckRootStatus` succeeds.
- Legacy path (`ScriptEnvironmen.isInited()`, `CheckRootStatus`,
  `KrScriptConfig`, `BeforeStartThread`) is **preserved unchanged**.

### MainActivity

- Calls `AppRuntimeStore.init()` at the top of `onCreate`.
- Replaces the legacy `ActivityCompat.requestPermissions(...,
  READ_EXTERNAL_STORAGE, WRITE_EXTERNAL_STORAGE, 111)` call with
  `RuntimePermissionHelper.requestMissing(this, 111)`. This is required on
  targetSdk 35 (Stage 13): the legacy permissions are capped at API 32, so
  requesting them on Android 13+ silently fails.
- Legacy `KrScriptConfig`, `PageConfigReader`, `ActionListFragment` logic is
  **preserved unchanged**.

### ActivityFileSelector

- `onRequestPermissionsResult` now delegates to
  `RuntimePermissionHelper.areAllGranted(grantResults)`.
- `requestPermissions()` now calls `RuntimePermissionHelper.requestMissing`.
- `loadData()` keeps the legacy file-list flow when
  `READ_EXTERNAL_STORAGE`+`WRITE_EXTERNAL_STORAGE` are actually granted
  (Android ≤ 12). On Android 13+, where those are capped, it shows a Toast
  directing the user to the new SAF-based `FirmwareAnalysisActivity`.

### ActionPage

- After `storageGateway.resolveUriForShell(...)` succeeds, kicks off a
  background thread that calls `AppRuntimeStore.setFirmware(shellPath)`.
- The legacy `fileSelectedInterface?.onFileSelected(shellPath)` callback is
  invoked exactly as before — the AppRuntimeStore update is best-effort and
  never blocks the UI.
- Legacy `AndroidStorageGateway`, `ScriptEnvironmen`, `KrScriptActionHandler`
  paths are **preserved unchanged**.

## New components

```text
pio/src/main/java/com/mio/kitchen/ui/modern/
  AppRuntimeStore.kt          # singleton bridge (StateFlow<AppRuntimeProfile?>)
```

## Modified files

```text
pio/src/main/java/com/mio/kitchen/
  SplashActivity.kt           # +AppRuntimeStore.init +updateRootStatus
  MainActivity.kt             # +AppRuntimeStore.init +RuntimePermissionHelper
  ActivityFileSelector.kt     # +RuntimePermissionHelper (legacy storage request removed)
  ActionPage.kt               # +AppRuntimeStore.setFirmware after URI resolution
```

## CI gate

```bash
python3 tools/check-hybrid-migration.py
```

Expected output:

```text
PASS: AppRuntimeStore singleton bridge is in place
PASS: SplashActivity calls AppRuntimeStore.init + updateRootStatus (legacy path preserved)
PASS: MainActivity calls AppRuntimeStore.init + RuntimePermissionHelper (legacy storage request removed)
PASS: ActivityFileSelector uses RuntimePermissionHelper (legacy storage request removed)
PASS: ActionPage calls AppRuntimeStore.setFirmware after URI resolution (legacy gateway preserved)
PASS: FirmwareOperationService.onCreate invokes ToolchainInstaller
PASS: FirmwareOperationService handles all ToolchainInstallResult variants
PASS: FirmwareOperationService exposes lastInstalledToolsDir + uses SupervisorJob + mutex
```

## What is intentionally NOT done here

- The legacy `PageConfigReader`/`ScriptEnvironmen`/`KeepShell`/`KrScriptConfig`
  classes are NOT removed. They still power the actual KrScript UI rendering
  and shell execution. Removing them would require migrating every
  `ActionListFragment`/`PageLayoutRender` call site — a much larger effort
  that is deferred until the new path has proven itself in production.
- `MainActivity`'s `KrScriptConfig`/`PageConfigReader`/`ActionListFragment`
  chain is NOT touched. The store is updated, but the actual UI rendering
  still goes through the legacy path.
- `ActionPage` does NOT use `OperationExecutor` to launch operations — it
  still uses the legacy `KrScriptActionHandler` callback. Wiring
  `OperationExecutor` in is a future stage.
- `AppRuntimeStore` is a singleton. This is acceptable because the device
  profile is process-global, but it makes testing harder. A future stage may
  inject the store via a `ViewModel`-scoped container.
