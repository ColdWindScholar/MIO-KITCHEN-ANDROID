# Stage 10 — Runtime profiles

> Date: 2026-06-18
> Status: completed
> Related stages: after Stage 9 (tests/CI), before Stage 11 (targetSdk 35 runtime migration)

---


The v3 roadmap §3 describes a system of runtime profiles that let the single
APK adapt its behavior to the device it runs on AND to the firmware the user
selected. Stage 7 introduced `FirmwareProfile`. Stage 10 introduces the
remaining profiles:

- `DeviceProfile` — describes the device the app runs on (Android version,
  manufacturer, ABI list, root status, SELinux mode, 16 KB page-size support).
- `ToolchainProfile` + `ToolRequirement` + `ToolDescriptor` — describes which
  native tools an operation needs and which are available in the manifest.
- `OperationSafetyProfile` — describes how dangerous an operation is
  (destructive, requires backup, requires root, supports dry-run,
  confirmation level).
- `AppRuntimeProfile` + `AppRuntimeProfileResolver` — the top-level container
  that combines all of the above into one immutable value the UI can subscribe
  to.

The stage also ships a JSON tool manifest at `assets/toolchain/manifest.json`
and a `ToolManifestLoader` that parses it.

### New API

```text
common/runtime/
  DeviceProfile.kt              # data class + SelinuxMode enum + forTests() factory
  DeviceProfileProvider.kt      # builds DeviceProfile from Build.*
  AppRuntimeProfile.kt          # data class + AppRuntimeProfileResolver

common/toolchain/
  ToolRequirement.kt            # ToolPurpose enum + ToolRequirement +
                                # ToolDescriptor + ToolchainProfile
  ToolchainResolver.kt          # interface + FirmwareOperation enum +
                                # ToolchainPlan + ToolchainWarning
  ToolManifestLoader.kt         # JSON parser for assets/toolchain/manifest.json
  CapabilityBasedToolchainResolver.kt
                                # capability-driven resolver implementation

common/operations/
  OperationSafetyProfile.kt     # ConfirmationLevel + OperationSafetyProfile
                                # + readOnly/packOperation/flashOperation helpers
  OperationPlanner.kt           # OperationPlan + OperationPlanner +
                                # OperationSafetyProvider + DefaultOperationSafetyProvider

pio/src/main/assets/toolchain/
  manifest.json                 # sample manifest declaring 13 bundled tools
```

### Architectural rules

```text
runtime/        -> NO shell, NO UI, NO android.app.* (Build.* via provider is OK)
toolchain/      -> NO shell, NO UI, NO android.app.*
operations/     -> NO shell, NO UI, NO android.app.*
DeviceProfileProvider
                -> reads Build.* only; does not call shell
CapabilityBasedToolchainResolver
                -> pure function of (operation, firmware, device) -> plan
ToolManifestLoader
                -> pure JSON parser; does not touch filesystem
AppRuntimeProfileResolver
                -> combines profiles; does not execute shell
```

### Manifest format

```json
{
  "tools": [
    {
      "name": "lpunpack",
      "version": "android-tools-r34",
      "abi": ["arm64-v8a", "armeabi-v7a"],
      "sha256": null,
      "capabilities": ["super_image"],
      "source": "AOSP",
      "license": "Apache-2.0",
      "supports16KbPageSize": true
    }
  ]
}
```

### Capability → tool mapping

```text
UNPACK_ROM         -> busybox + (payload-dumper if payload.bin)
                            + (lpunpack if super.img)
                            + (magiskboot if boot.img)
                            + (avbtool? if AVB)
UNPACK_SUPER       -> lpunpack + simg2img?
PACK_SUPER         -> lpmake + img2simg?
UNPACK_BOOT_IMAGE  -> magiskboot
PACK_BOOT_IMAGE    -> magiskboot + mkbootimg
UNPACK_FILESYSTEM_IMAGE
                   -> dump.erofs (if EROFS) | e2fsdroid? (if ext4)
PACK_FILESYSTEM_IMAGE
                   -> mkfs.erofs (if EROFS) | mke2fs + e2fsdroid (if ext4)
VERIFY_VBMETA      -> avbtool
FLASH_PREPARE      -> busybox + magiskboot?
+ compression tools (brotli/lz4/zstd) when capabilities require them
```

### Safety profile defaults

```text
read-only operations (UNPACK_*, INSPECT, VERIFY_VBMETA)
  -> isDestructive=false, requiresRoot=false, confirmationLevel=NONE
pack operations (PACK_*)
  -> isDestructive=false, requiresRoot=false, confirmationLevel=STANDARD
flash operations (FLASH_PREPARE)
  -> isDestructive=true, requiresRoot=true, requiresBackup=true,
     requiresDeviceConnection=true, confirmationLevel=DESTRUCTIVE
```

### CI gate

```bash
python3 tools/check-runtime-profiles.py
```

Expected output:

```text
PASS: DeviceProfile + DeviceProfileProvider are in place
PASS: ToolchainProfile + ToolRequirement + ToolDescriptor are declared
PASS: ToolchainResolver interface + CapabilityBasedToolchainResolver implementation
PASS: ToolManifestLoader parses JSON manifest
PASS: OperationSafetyProfile + OperationPlanner + OperationPlan are declared
PASS: AppRuntimeProfile + AppRuntimeProfileResolver combine all profiles
PASS: assets/toolchain/manifest.json declares required tools
PASS: Stage 10 JVM unit tests are present
PASS: common/build.gradle declares org.json testImplementation
PASS: runtime/toolchain/operations layers are pure (no shell/UI imports)
```

### What is intentionally NOT done here

- `ToolchainInstaller` is implemented in Stage 11; it is auto-invoked by
  `FirmwareOperationService.onCreate` (Stage 21) and by
  `LegacyShellBridge.init` (Stage 23) to populate `<filesDir>/bin/` from
  `assets/toolchain/manifest.json`. The manifest SHA-256 values are generated
  by `tools/compute-tool-hashes.py`.
- `ToolchainProfile.toolsDir` is populated by `ToolchainInstaller` and
  exposed via `FirmwareOperationService.lastInstalledToolsDir` (Stage 21).
- `DeviceProfileProvider.withRootCheck()` returns `SelinuxMode.UNKNOWN` and
  `null` for 16 KB page size — the real probes go through `ShellRuntime` and
  will be wired in a future stage.
- The existing UI activities now consume `AppRuntimeProfile` via
  `AppRuntimeStore` (Stage 20 hybrid migration). The legacy
  `PageConfigReader`/`ScriptEnvironmen` classes were removed in Stage 22 and
  replaced with `PageConfigLoader` (over `PageConfigRepository`) and a thin
  `ScriptEnvironmen.kt` facade over `LegacyShellBridge`.
- `OperationPlanner` builds an `OperationPlan` (Stage 10); the plan is
  executed by `OperationExecutor` (Stage 12), which turns the plan into a
  `ShellCommand` and runs it via `ShellRuntime`.

---
