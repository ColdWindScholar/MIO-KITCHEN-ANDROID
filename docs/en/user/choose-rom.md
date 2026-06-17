# Choosing a ROM

MIO-KITCHEN understands several firmware file types. The analyzer
(`FirmwareAnalyzerRegistry`) auto-detects the type from the file's magic
bytes — never from the file extension alone.

## Supported input types

| Type | Detection rule | What it means |
|------|----------------|---------------|
| `ZIP_OTA` | `.zip` containing `META-INF/com/android/metadata` | Standard AOSP OTA package |
| `PAYLOAD_BIN` | `.zip` containing `payload.bin` | Android 10+ A/B OTA — payload.bin carries partitions |
| `SUPER_IMAGE` | file named `super.img` or zip containing it | Dynamic partitions image |
| `BOOT_IMAGE` | file named `boot.img` with `ANDROID!` magic | Boot image (any header version) |
| `VENDOR_BOOT_IMAGE` | `vendor_boot.img` with `ANDROID!` magic | Vendor boot (GKI devices) |
| `INIT_BOOT_IMAGE` | `init_boot.img` (Android 13+) | Generic ramdisk split |
| `RECOVERY_IMAGE` | `recovery.img` with `ANDROID!` magic | Standalone recovery |
| `VBMETA_IMAGE` | `vbmeta*.img` with `AVB0` magic | Android Verified Boot metadata |
| `FILESYSTEM_IMAGE` | other `.img` with ext4 / EROFS / F2FS magic | Single partition image |

## How detection works

1. The app picks a file through SAF (`ACTION_OPEN_DOCUMENT`).
2. `AndroidStorageGateway` resolves the URI to a shell-accessible workspace path.
3. `FirmwareAnalyzerRegistry.analyze(source)` runs each analyzer in order:
   - `ZipFirmwareAnalyzer` (handles any `.zip`)
   - `BootImageAnalyzer` (handles `boot*.img`)
   - `SuperImageAnalyzer` (handles `super.img`)
   - `VbmetaAnalyzer` (handles `vbmeta*.img`)
   - `FilesystemImageAnalyzer` (handles any other `.img`)
4. The first analyzer whose `supports(source)` returns true wins.
5. The result is a `FirmwareProfile` with capabilities, partitions, warnings.

## What "Android version" means

The profile may include an `androidVersion` hint. This is the Android version
**inside the firmware**, NOT the Android version your device is running. The
two are independent — you can flash an Android 13 firmware from an Android 15
device, and vice versa. See the [developer docs](../dev/runtime-profiles.md)
for the formal distinction.

## Unsupported formats

If the analyzer returns `FirmwarePackageType.UNKNOWN`, the file is not a
recognized firmware. Common reasons:

- The file is a sparse image without a sparse header (rare).
- The file uses a proprietary boot magic (some MediaTek / Qualcomm devices).
- The file is a fastboot bundle, not a firmware image.

For these cases, the app shows a warning but does not crash — you can still
attempt generic operations, but the toolchain resolver will likely report
missing tools.

## Next steps

- [Unpack operations](unpack.md) — what each unpacker does.
- [Storage access](storage-access.md) — how the workspace handles your file.
