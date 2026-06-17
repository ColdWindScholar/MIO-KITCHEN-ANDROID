# Stage 14 — Folder/tree URI export policy

> Date: 2026-06-18
> Status: completed
> Related stages: after Stage 13 (targetSdk 35), before Stage 15 (UI migration)

---

On targetSdk 35 the app cannot write to external storage using direct file
paths. The original Stage 4 `FirmwareWorkspace` handles the *import* path
(content:// URI → workspace copy). Stage 14 closes the loop with a typed
*export* policy: how files in `FirmwareWorkspace.exportsDir/...` are written
back to a user-visible location.

## What this stage does

Introduces:

- `ExportPolicy` — sealed type with four variants:
  - `AskPerFile` — use `ACTION_CREATE_DOCUMENT` per file (caller obtains URI).
  - `TreeFolder(treeUri)` — write into a persistent tree URI (one folder pick).
  - `MediaStoreExport(mimeType, relativePath)` — write through MediaStore.
  - `AppPrivate` — keep inside app-private external storage.
- `ExportResult` — sealed type: `Success(targetUri, bytesCopied, sha256)`,
  `Cancelled`, `Failed(message, cause)`.
- `ExportOptions` — combines policy + flags (`computeSha256`,
  `overwriteExisting`).
- `WorkspaceExporter` — interface with `export(sourceFile, options)`.
- `AndroidWorkspaceExporter` — Android implementation supporting all four
  policies, with SHA-256 verification built into the copy path.

## New API

```text
common/storage/
  ExportPolicy.kt              # ExportPolicy, ExportResult, ExportOptions,
                               # WorkspaceExporter
  AndroidWorkspaceExporter.kt  # Android implementation + exportToUri helper
```

## CI gate

```bash
python3 tools/check-export-policy.py
```

Expected output:

```text
PASS: ExportPolicy sealed type covers AskPerFile/TreeFolder/MediaStore/AppPrivate
PASS: ExportResult sealed type covers Success/Cancelled/Failed
PASS: WorkspaceExporter interface declares export(sourceFile, options)
PASS: AndroidWorkspaceExporter supports SAF tree + MediaStore + app-private
PASS: SHA-256 verification is built into export path
```

## What is intentionally NOT done here

- The exporter is not yet wired into any UI screen. Stage 15 will use
  `OpenDocumentHelper` / `CreateDocumentHelper` to obtain URIs and pass them
  to `AndroidWorkspaceExporter.exportToUri(...)`.
- `AskPerFile` returns `Cancelled` from `export()` directly — the caller must
  obtain a URI via Activity Result API first and then call `exportToUri()`.
  This split keeps the exporter synchronous.
- `overwriteExisting` is declared in `ExportOptions` but not yet honored
  uniformly across all policies — SAF tree and MediaStore currently always
  create a new document. This will be tightened in a follow-up.
