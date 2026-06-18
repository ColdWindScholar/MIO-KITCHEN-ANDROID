# Storage / Workspace compatibility architecture

## Stage goal

This stage prepares the project for a future `targetSdk` migration from 28 to a modern level without splitting the app into multiple APKs.

The user-facing model stays the same:

```text
one app
one UI
one APK/AAB
firmware files from different Android versions can be selected
```

Internally, the app now has a layer that converts different file sources into shell-accessible paths.

## Why a workspace is needed

KrScript and firmware tools operate on regular file paths:

```text
/storage/emulated/0/.../rom.zip
/storage/emulated/0/Android/data/com.mio.kitchen/files/firmware-workspace/imports/rom.zip
```

Modern Android file pickers often return URIs:

```text
content://...
```

Shell and bundled tools cannot read `content://` directly. The app therefore needs this flow:

```text
user selects a file through SAF
  -> app receives a content:// URI
  -> StorageGateway copies the file into FirmwareWorkspace
  -> KrScript/shell receives a regular shellPath
```

## New components

```text
common/storage/SafeFileName.kt
  safe workspace file names

common/storage/FirmwareWorkspace.kt
  app-specific workspace:
    firmware-workspace/imports
    firmware-workspace/exports

common/storage/StorageGateway.kt
  interface for preparing URI/path input for shell operations

common/storage/AndroidStorageGateway.kt
  Android implementation:
    file:// -> DirectFile
    content:// -> WorkspaceCopy
    legacy direct path -> opt-in only

common/storage/StorageResolveOptions.kt
  resolution policy:
    preferLegacyDirectPath=false
    copyContentUriToWorkspace=true
    computeSha256=true

common/storage/StorageResolveResult.kt
  typed result:
    Resolved(shellPath, sourceKind, copiedBytes, sha256)
    Failed(message, cause)
```

## Default policy

```text
content:// URI:
  never passed directly to shell
  copied into FirmwareWorkspace
  SHA-256 is computed during copy

file:// URI or regular path:
  returned as DirectFile

legacy direct path:
  kept inside AndroidStorageGateway
  used only when preferLegacyDirectPath=true
```

This keeps current root/direct-path workflows possible while preparing the code for scoped-storage behavior on modern Android versions.

## ActionPage changes

`ActionPage` no longer calls `FilePathResolver` directly for URIs returned by the system picker.

The flow is now:

```text
ACTION_OPEN_DOCUMENT
  -> URI grant
  -> AndroidStorageGateway.persistReadPermission()
  -> AndroidStorageGateway.resolveUriForShell()
  -> workspace copy
  -> fileSelectedInterface.onFileSelected(shellPath)
```

Workspace copy preparation does not run in the immediate UI branch; it uses a separate thread with progress feedback.

## What remains legacy

Folder selection still uses the built-in `ActivityFileSelector` and direct paths. This is intentional: many firmware operations expect a real directory, not a tree URI.

The next storage stage should handle:

```text
ACTION_OPEN_DOCUMENT_TREE
  -> workspace folder mapping
  -> export/copy-back policy
  -> root/direct-path fallback
```

## Checks

```bash
python3 tools/check-storage-workspace.py
```

The check verifies:

- `StorageGateway` / `AndroidStorageGateway` / `FirmwareWorkspace` presence;
- `content://` input is copied into workspace;
- legacy direct path is opt-in;
- `ActionPage` uses SAF `ACTION_OPEN_DOCUMENT`;
- UI receives a shell-accessible path through `StorageResolveResult`;
- RU/EN documentation and CI gate are present.

## Unit tests

A quick JVM test was added:

```text
common/src/test/java/com/omarea/common/storage/SafeFileNameTest.kt
```

It checks file-name sanitizing, path-segment removal, default-name behavior, and extension preservation when long names are trimmed.

## How this helps future targetSdk migration

This stage does not raise `targetSdk` to 35 yet. It creates the architecture point where storage policy can change without rewriting KrScript and UI.

Next steps can move toward:

```text
StorageGateway adapters
Workspace export policy
folder/tree URI support
permission matrix tests
then targetSdk 35 migration
```
