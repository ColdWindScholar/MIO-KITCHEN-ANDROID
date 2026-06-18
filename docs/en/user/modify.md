# Modify operations

After unpacking, you typically want to edit files inside the extracted
filesystem or boot image. MIO-KITCHEN does not provide a built-in file
editor — modifications happen through external tools or shell commands
invoked through KrScript actions.

## What is safe to modify

### Inside an unpacked filesystem (ext4 / EROFS)

- `/system/build.prop` — device properties.
- `/system/etc/hosts` — for ad-blocking.
- `/system/app/*.apk` and `/system/priv-app/*.apk` — pre-installed apps.
- `/system/fonts/*.ttf` — system fonts.
- `/system/media/audio/*.ogg` — system sounds.

After editing, repack with [PACK_FILESYSTEM_IMAGE](pack.md) — the packer
preserves file ownership, permissions, and SELinux contexts.

### Inside an unpacked boot image

- `kernel` — the compressed kernel binary (rarely edited; you'd usually
  replace it with a known-good one).
- `ramdisk.cpio` — the init ramdisk. Common edits:
  - `init.rc` and friends — boot scripts.
  - `fstab.<device>` — mount table.
  - `default.prop` — debuggable, adb, etc.
- `dtb` — device tree blob (advanced).

After editing, repack with [PACK_BOOT_IMAGE](pack.md). The packer re-computes
the header and re-signs the image if `magiskboot` is available.

### Inside super.img

After `UNPACK_SUPER`, each partition (`system.img`, `vendor.img`, ...) is a
separate filesystem image. Modify each individually, then repack with
[PACK_SUPER](pack.md).

## What is NOT safe to modify

- **`vbmeta.img`** — modifying it without re-signing breaks AVB and the device
  will not boot. Use [VERIFY_VBMETA](unpack.md) to inspect, but do not edit.
- **`payload.bin`** — opaque protobuf format. Always unpack to partitions
  first, modify those, and re-pack into a new OTA (separate operation, not yet
  implemented as a typed `FirmwareOperation`).
- **Sparse image headers** — `simg2img` converts sparse → raw; edit only the
  raw form, then `img2simg` converts back. Do not edit sparse images
  directly.

## Safety profile

Modify operations happen inside the app workspace, so they are:
- `isDestructive = false` (only workspace files change)
- `requiresRoot = false`
- `confirmationLevel = STANDARD` (yes/no confirmation before running)
- `supportsDryRun = true`

## Next steps

- [Pack operations](pack.md) — rebuild the image after editing.
- [Flash safety](flash-safety.md) — read this before any flash operation.
