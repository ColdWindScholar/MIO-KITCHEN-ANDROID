# Troubleshooting

Common issues and their fixes, organized by symptom.

## "Operation is not ready" / blockers

**Symptom**: The Execute button is disabled; the app shows one or more
blockers like `Missing required tools: lpunpack` or
`Operation requires root, but root is not available`.

**Cause**: `OperationPlan.canExecute` is false because the toolchain
resolver or safety profile reported a blocker.

**Fix**:
- For missing tools: open Settings → Toolchain → Reinstall. This runs
  `ToolchainInstaller` which extracts binaries from the manifest and
  verifies SHA-256.
- For root: grant root access via Magisk (or your superuser app) and retry.
- For ABI mismatch: your device ABI is not in the toolchain manifest. Check
  `DeviceProfile.abiList` in the About screen and ensure the manifest
  declares that ABI.

## "Cannot open output stream for URI"

**Symptom**: Export fails with this error.

**Cause**: The SAF picker returned a URI that the app does not have
persistable permission for. This can happen if the URI came from another
app's sharing intent rather than from `ACTION_OPEN_DOCUMENT`.

**Fix**: Pick the file through MIO-KITCHEN's own picker (which uses
`ActivityResultContracts.OpenDocument` and takes persistable permission),
not through a share sheet.

## "SHA-256 mismatch"

**Symptom**: `ToolchainInstaller` fails with `ChecksumMismatch` for a
specific tool.

**Cause**: The binary in `assets/bin/` does not match the SHA-256 declared
in `assets/toolchain/manifest.json`. Either the manifest is out of date, or
the binary was corrupted.

**Fix**: Rebuild the APK from source. If you are a developer, regenerate the
manifest SHA-256 values by running `python3 tools/compute-tool-hashes.py`.
The script walks every file in `assets/bin/` and updates
`assets/toolchain/manifest.json` with the real SHA-256. After that, rebuild
the APK so the new manifest is bundled.

## "Timeout after X ms"

**Symptom**: A long operation (e.g. `UNPACK_ROM`) is killed after the
configured timeout.

**Cause**: The operation took longer than `ShellCommand.timeoutMs`. The
default is no timeout, but if a timeout was set by the caller, it can be
exceeded for very large ROMs.

**Fix**: Increase the timeout in the operation's launch options. For very
large ROMs (5GB+), use `Long.MAX_VALUE` for no timeout. Ensure the device is
not in battery-saver mode, which can slow CPU-bound operations.

## "Plan not ready. Blockers: No compatible ABI between device and tools"

**Symptom**: The plan blocks because the device ABI list has no overlap with
the toolchain.

**Cause**: The toolchain manifest only declares `arm64-v8a` and
`armeabi-v7a`, but the device is x86_64 (emulator).

**Fix**: Run on an ARM device or ARM emulator. MIO-KITCHEN does not ship x86
binaries for the bundled tools because most firmware operations require
ARM-specific magiskboot / e2fsprogs.

## "vbmeta-bad-magic" warning

**Symptom**: The analyzer warns that a `vbmeta.img` does not start with
`AVB0` magic.

**Cause**: The file is not actually a vbmeta image. Common reasons:
- It is a `vbmeta_system.img` with a chained descriptor (still AVB0 magic —
  should not trigger this warning).
- It is a vendor-specific signature blob (rare).
- The file was renamed to `vbmeta.img` but is actually something else.

**Fix**: Verify the file with `avbtool info_image --image vbmeta.img`. If
that also fails, the file is not a vbmeta image.

## App crashes on Android 14+

**Symptom**: `ForegroundServiceTypeNotAllowed` or
`MissingForegroundServiceTypeException`.

**Cause**: On Android 14+ (API 34+), foreground services must declare a
type. MIO-KITCHEN uses `dataSync` (declared in the manifest). The crash
happens if you side-loaded an old APK that predates Stage 13.

**Fix**: Install the latest APK from the project's releases page. The
manifest now declares `FOREGROUND_SERVICE_DATA_SYNC` and the
`FirmwareOperationService` uses `FOREGROUND_SERVICE_TYPE_DATA_SYNC`.

## Notification permission not granted

**Symptom**: On Android 13+, the foreground notification does not appear.

**Cause**: `POST_NOTIFICATIONS` runtime permission was denied.

**Fix**: Long-press the app icon → App info → Notifications → Allow. Or
re-grant via `RuntimePermissionHelper.requestMissing(activity, ...)` which
the app calls on the next launch.

## Need more help

- Check the [developer docs](../dev/) for architecture details.
- Read the [quick start](quick-start.md) again to make sure you did not skip a step.
- See [flash safety](flash-safety.md) if the issue is a flash operation.
- See [root mode](root-mode.md) if the issue is about root.
- See [storage access](storage-access.md) if the issue is about file picking/export.
- File an issue at the project's bug tracker with:
  - The exact error message.
  - The `DeviceProfile` summary from the About screen.
  - The `FirmwareProfile` summary if the crash happened during an operation.
  - The Android version of your device.
