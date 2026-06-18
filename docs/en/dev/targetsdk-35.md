# Stage 13 — targetSdk 35 runtime migration

> Date: 2026-06-18
> Status: completed
> Related stages: after Stage 12 (OperationExecutor), before Stage 14 (Folder/tree URI export policy)

---

Stage 3 deliberately left `targetSdkVersion = 28` to preserve legacy storage
and root behavior while the storage/workspace layer was being built. Now that
Stages 4, 10, 11, 12 are in place — `StorageGateway` + `FirmwareWorkspace` +
`OperationExecutor` cover the entire runtime path — there is no longer any
reason to stay on the old target. Stage 13 raises `targetSdkVersion` to 35
and adds the runtime-permission and foreground-service infrastructure required
by modern Android.

## What this stage does

- Raises `targetSdkVersion` from 28 to 35 in `build.gradle`.
- Caps legacy storage permissions:
  - `READ_EXTERNAL_STORAGE` → `maxSdkVersion=32` (Android 12 and below).
  - `WRITE_EXTERNAL_STORAGE` → `maxSdkVersion=29` (Android 9 and below).
- Declares `POST_NOTIFICATIONS` for Android 13+.
- Declares `FOREGROUND_SERVICE` and `FOREGROUND_SERVICE_DATA_SYNC` for Android 14+.
- Sets `requestLegacyExternalStorage="false"` to enforce scoped storage.
- Adds a `FirmwareOperationService` foreground service with `dataSync` type.
- Adds `RuntimePermissionHelper` to abstract targetSdk-35 permission flow.

## New components

```text
pio/src/main/java/com/mio/kitchen/
  FirmwareOperationService.kt       # foreground service with dataSync type

pio/src/main/java/com/mio/kitchen/ui/modern/
  RuntimePermissionHelper.kt         # abstracts permission flow per Android version
```

## Permission flow

```text
RuntimePermissionHelper.requiredPermissions()
  -> POST_NOTIFICATIONS  on Android 13+
  -> READ_EXTERNAL_STORAGE on Android ≤ 12 (capped at maxSdk=32 in manifest)

RuntimePermissionHelper.areAllGranted(context)
  -> true if all required permissions are granted

RuntimePermissionHelper.requestMissing(activity, requestCode)
  -> requests missing permissions; returns true if a request was launched

RuntimePermissionHelper.areAllGranted(grantResults: IntArray)
  -> check result of onRequestPermissionsResult
```

## Foreground service flow

```text
FirmwareOperationService
  -> onStartCommand builds a low-priority notification
  -> on Android 14+ (UPSIDE_DOWN_CAKE): startForeground with FOREGROUND_SERVICE_TYPE_DATA_SYNC
  -> on older Android: startForeground without type
  -> returns START_NOT_STICKY (operation-driven, not crash-restored)
```

The service does NOT run the firmware operation itself — it only holds the
foreground state. The actual operation is launched by the caller via
`OperationExecutor` (Stage 12) in a coroutine.

## CI gate

```bash
python3 tools/check-targetsdk-35.py
```

Expected output:

```text
PASS: targetSdkVersion raised from 28 to 35
PASS: legacy storage permissions capped (READ maxSdk=32, WRITE maxSdk=29)
PASS: POST_NOTIFICATIONS runtime permission declared for Android 13+
PASS: FOREGROUND_SERVICE_DATA_SYNC declared for Android 14+
PASS: requestLegacyExternalStorage is false (scoped storage enforced)
PASS: FirmwareOperationService uses dataSync foregroundServiceType
PASS: RuntimePermissionHelper abstracts targetSdk-35 permissions
```

## What is intentionally NOT done here

- Existing activities (`MainActivity`, `ActionPage`, `ActivityFileSelector`,
  `SplashActivity`) are NOT yet migrated to call `RuntimePermissionHelper`
  before launching operations. The migration is part of Stage 15 (UI
  migration to AppRuntimeProfile).
- `FirmwareOperationService` is declared but not yet started by any activity.
  Stage 15 will wire it up via `Context.startForegroundService(...)`.
- The notification uses a generic download icon and the app name as title;
  a future stage will add localized strings and a progress bar.
- All Files Access (`MANAGE_EXTERNAL_STORAGE`) is intentionally NOT requested
  — the app uses SAF + workspace exclusively, which does not require it.
