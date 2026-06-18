# Quick start

MIO-KITCHEN is a single-APK Android firmware kitchen. It supports unpacking,
modifying, repacking, and flashing firmware for Android 10 through Android 15+
devices — all from one app, with no separate "legacy" or "modern" builds.

## Install

1. Install `MIO-KITCHEN-<date>-<version>.apk` on any Android 5.0+ (API 21+) device.
2. On first launch, grant the runtime permissions the app requests:
   - On Android 13+: **Notifications** (so the foreground service can post progress).
   - On Android ≤ 12: **Storage access** (legacy permission, capped at API 32).

Root is **optional** — most unpack/pack operations run without root. Root is
only required for flash operations and for reading files outside the
app-specific workspace.

## Pick a firmware

1. Tap **Pick firmware file** on the home screen.
2. The system file picker (SAF) opens. Pick a `.zip` OTA, `.img` file, or any
   firmware package.
3. The app copies the file into its workspace, computes SHA-256, and runs
   `FirmwareAnalyzer` to detect the package type, capabilities, and Android
   version hint.
4. The detected profile is shown on screen: `type=payload_bin, android=13,
   dynamic, erofs, avb`.

## Run an operation

1. After analysis, pick an operation from the available list:
   - **Unpack ROM** — extract everything from an OTA zip.
   - **Unpack boot.img** — split boot into kernel + ramdisk.
   - **Unpack super.img** — split dynamic partitions.
   - **Pack** — rebuild an image you modified.
   - **Verify vbmeta** — check AVB chain.
   - **Flash prepare** — generate a flash script (destructive, requires root).
2. The app builds an `OperationPlan` and shows blockers (missing tools, no
   root for flash, etc.). Fix any blockers before continuing.
3. Tap **Execute**. The `FirmwareOperationService` foreground notification
   appears; `OperationExecutor` runs the operation through `ShellRuntime`.

## Export the result

1. After a successful operation, the output is in the app workspace.
2. Tap **Export** and choose where to save it:
   - Pick a folder once (TreeFolder) for bulk export, or
   - Pick a destination per file (AskPerFile).
3. SHA-256 is computed during export so you can verify the result matches.

## Next steps

- [Choosing a ROM](choose-rom.md) — what file types MIO-KITCHEN understands.
- [Unpack operations](unpack.md) — details on each unpacker.
- [Pack operations](pack.md) — details on each packer.
- [Flash safety](flash-safety.md) — read this before any flash operation.
- [Storage access](storage-access.md) — how the app handles SAF + workspace.
- [Root mode](root-mode.md) — what root enables and how it is detected.
- [Troubleshooting](troubleshooting.md) — common issues and fixes.
