# Pack operations

Pack operations rebuild firmware images from workspace contents. They are the
inverse of the corresponding unpack operations.

## PACK_FILESYSTEM_IMAGE

Input: a directory tree in the workspace.

Required tools depend on the detected filesystem:
- EROFS → `mkfs.erofs`
- ext4 → `mke2fs` + `e2fsdroid`

Output: a single `<name>.img` file in the workspace exports dir.

The packer:
1. Walks the input directory.
2. Builds a filesystem image with correct ownership, permissions, and
   SELinux contexts (read from `file_contexts` if present).
3. Computes SHA-256 of the output for verification.

## PACK_BOOT_IMAGE

Input: `kernel`, `ramdisk.cpio`, `dtb`, header info (from
`UNPACK_BOOT_IMAGE`).

Required tools: `magiskboot`, `mkbootimg`.

Output: a new `boot.img`.

The packer:
1. Reassembles the boot image using the original header version.
2. Recomputes the header fields (size, CRC, etc.).
3. If `magiskboot` is available, re-signs the image for Magisk compatibility.

## PACK_SUPER

Input: a set of partition images (`system.img`, `vendor.img`, ...).

Required tools: `lpmake`, `img2simg` (if sparse output is desired).

Output: a single `super.img` containing all logical partitions.

The packer uses the original `super.img` metadata (partition sizes, groups,
flags) and substitutes the new partition images. If the new images are larger
than the originals, the pack fails — you must shrink them or grow the super
partition.

## Safety profile

All pack operations are non-destructive to your device:
- `isDestructive = false` (only workspace files change)
- `requiresRoot = false`
- `confirmationLevel = STANDARD` (yes/no before running)
- `supportsDryRun = true`

Pack operations do **not** touch the device — they only produce new image
files in the workspace. To put the new images on a device, you must explicitly
export them and run [FLASH_PREPARE](flash-safety.md).

## Toolchain readiness

Before any pack operation, the `OperationPlanner` checks the toolchain plan
and refuses to run if any required tool is missing. The on-screen blockers
list tells you exactly what's missing. Use `ToolchainInstaller` (run
automatically on app start, or manually from settings) to extract missing
binaries from the manifest.

## Export

After a successful pack, use the **Export** button to write the result to a
user-visible location. The exporter supports three policies:
- `AskPerFile` — pick a destination per file.
- `TreeFolder` — pick a folder once, all files go there.
- `MediaStoreExport` — save to `Download/MIO-KITCHEN/`.

SHA-256 is computed during export so you can verify the result matches the
workspace file.

## Next steps

- [Unpack operations](unpack.md) — the inverse of pack.
- [Flash safety](flash-safety.md) — what to do with the packed image.
