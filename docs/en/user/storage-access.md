# Storage access

MIO-KITCHEN runs on Android 5.0 (API 21) through Android 15+ (API 35+) with
a single APK. Storage access differs significantly across these versions,
so the app uses a typed `StorageGateway` + `FirmwareWorkspace` layer to
hide the differences.

## How the app reads your files

When you pick a file through the system file picker:

1. The picker returns a `content://` URI (on Android 10+) or a `file://`
   path (on older Android).
2. `AndroidStorageGateway.resolveUriForShell(uri, options)` decides:
   - `file://` → use the path directly (rare on modern Android).
   - `content://` → copy the file into `FirmwareWorkspace.importsDir/`,
     compute SHA-256 during the copy, and return the workspace path.
3. The shell tools then receive the workspace path, which is always a
   regular file path inside the app's external files dir.

This means **shell tools never see `content://` URIs**. They always get a
real filesystem path.

## Why a workspace

Shell tools (and especially root-shell tools) cannot read `content://` URIs
directly. The workspace bridges the SAF/SAF-storage boundary:

```text
user picks file
  → SAF returns content:// URI
  → AndroidStorageGateway copies into FirmwareWorkspace/imports/<unique-name>
  → shell tools receive /storage/emulated/0/Android/data/com.mio.kitchen/files/firmware-workspace/imports/<unique-name>
  → result is exported back through ExportPolicy
```

Benefits:
- Identical shell path on every Android version.
- Less reliance on the `_data` column of MediaProvider (broken on Android 11+).
- Easier to compute checksums, easier to roll back, easier to test.

## Workspace location

The workspace lives in the app-specific external storage:

```text
/storage/emulated/0/Android/data/com.mio.kitchen/files/firmware-workspace/
  imports/   <- files copied from user picks
  exports/   <- files produced by operations, ready to export
```

This directory is:
- Writable without `WRITE_EXTERNAL_STORAGE` (it's app-specific).
- Shell-accessible (rooted or non-rooted).
- Automatically cleaned up by the system when the app is uninstalled.

`FirmwareWorkspace.clearOldImports()` deletes import files older than 7 days
on every app start, so the workspace does not grow unbounded.

## Legacy direct-path mode

Some advanced users prefer direct path access (no workspace copy). This is
opt-in via `StorageResolveOptions.preferLegacyDirectPath = true`. When
enabled:
- `file://` URIs are used directly without copying.
- `content://` URIs that resolve to a known `_data` path are used directly.

This mode is **not recommended** on Android 11+ because:
- The `_data` column is no longer queryable for most providers.
- Direct paths outside the app-specific dir require `MANAGE_EXTERNAL_STORAGE`
  (which MIO-KITCHEN does not request).
- It breaks scoped storage enforcement.

## Export policies

After an operation, the result is in `exports/`. To make it visible to the
user, `AndroidWorkspaceExporter` supports four policies:

| Policy | When to use |
|--------|-------------|
| `AskPerFile` | Pick a destination per file (most control, most clicks) |
| `TreeFolder` | Pick a folder once, all exports go there (recommended) |
| `MediaStoreExport` | Save to `Download/MIO-KITCHEN/` (visible in Downloads) |
| `AppPrivate` | Keep inside the app (no export; user must use a file manager) |

All exports compute SHA-256 by default so you can verify integrity.

## Next steps

- [Quick start](quick-start.md) — the basic workflow.
- [Root mode](root-mode.md) — what changes when root is available.
- [Troubleshooting](troubleshooting.md) — common storage errors.
