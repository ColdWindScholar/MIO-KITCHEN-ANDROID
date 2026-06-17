# Unpack operations

MIO-KITCHEN can unpack every common firmware image type. Each unpacker is a
read-only, non-destructive operation that writes results into the app
workspace.

## UNPACK_ROM

Input: an OTA `.zip` containing `payload.bin` and/or individual `.img` files.

The executor sets:
- `OPERATION=UNPACK_ROM`
- `FIRMWARE_PATH=<workspace path to the OTA zip>`
- `HAS_PAYLOAD_BIN`, `HAS_SUPER_IMAGE`, `HAS_BOOT_IMAGE`, etc.

Required tools (per `CapabilityBasedToolchainResolver`):
- `busybox` (always)
- `payload-dumper` (when `HAS_PAYLOAD_BIN`)
- `lpunpack` (when `HAS_SUPER_IMAGE`)
- `magiskboot` (when `HAS_BOOT_IMAGE`)
- `avbtool` (optional, when `USES_AVB`)

## UNPACK_SUPER

Input: `super.img` (raw or sparse).

Required tools: `lpunpack`, `simg2img` (if sparse).

Output: one file per logical partition (`system.img`, `vendor.img`,
`product.img`, ...).

## UNPACK_PAYLOAD_BIN

Input: `payload.bin` extracted from an OTA.

Required tools: `payload-dumper`.

Output: partitions as listed in the payload manifest.

## UNPACK_BOOT_IMAGE / UNPACK_VENDOR_BOOT_IMAGE / UNPACK_INIT_BOOT_IMAGE

Input: `boot.img` (or `vendor_boot.img` / `init_boot.img`).

Required tools: `magiskboot`.

Output: `kernel`, `ramdisk.cpio`, `dtb`, header info.

The boot image header version is auto-detected from the `ANDROID!` magic.
Header v3/v4 (GKI) is supported; warnings are emitted for v4 due to GKI
compatibility requirements.

## UNPACK_DTBO_IMAGE

Input: `dtbo.img`.

Required tools: `magiskboot`.

Output: individual DTBO entries.

## UNPACK_FILESYSTEM_IMAGE

Input: a single `system.img` / `vendor.img` / etc.

Required tools depend on the detected filesystem:
- EROFS → `dump.erofs` (required), `fsck.erofs` (optional)
- ext4 → `e2fsdroid` (optional), `resize2fs` (optional)

Output: a directory tree with the filesystem contents.

## Safety profile

All unpack operations are **read-only**:
- `isDestructive = false`
- `requiresRoot = false`
- `confirmationLevel = NONE`
- `supportsDryRun = true`

You can safely run any unpacker without root — the source file is never
modified, and output goes into the app's private workspace.

## Next steps

- [Pack operations](pack.md) — how to rebuild images you modified.
- [Modify operations](modify.md) — what edits are safe between unpack and pack.
