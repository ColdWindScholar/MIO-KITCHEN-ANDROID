# Stage 23 — Final legacy cleanup

> Date: 2026-06-18
> Status: completed
> Related stages: after Stage 22 (legacy removal)

---

Stage 22 removed the three big legacy classes but left three issues:

1. **ShellExecutor.execute() was broken** — it called
   `ScriptEnvironmen.getRuntime()` which returned `null` after Stage 22.
   This broke the core KrScript action execution path (DialogLogFragment +
   ActionListFragment.runHiddenAction).
2. **ExtractAssets was still used** in `LegacyShellBridge.init` to extract
   the toolkit directory. The manifest did not cover `.so` shared libraries.
3. **SplashActivity.BeforeStartThread** depended on `common/shell/
   ShellExecutor` (a different class) for `getSuperUserRuntime()`/`getRuntime()`.

Stage 23 fixes all three.

## What this stage does

### ShellExecutor.java → ShellExecutor.kt

Rewrites `krscript/executor/ShellExecutor` from Java to Kotlin. The new
implementation:

- Creates its own `Process` via `Runtime.exec("su")` / `Runtime.exec("sh")`
  (based on `LegacyShellBridge.isRooted`).
- Builds the full streaming command (env exports + executor path + script
  path) via `LegacyShellBridge.buildStreamingCommand()`.
- Writes the command to the Process's stdin.
- Keeps `SimpleShellWatcher` for streaming stdout/stderr to the
  `ShellHandlerBase`.
- Returns the `Process` for force-stop (used by DialogLogFragment's stop
  button).

The contract with callers (`DialogLogFragment`,
`ActionListFragment.runHiddenAction`) is preserved 1:1.

### ScriptEnvironmen.getRuntime() returns a real Process

Previously `getRuntime()` returned `null` (Stage 22 placeholder). Now it
returns a `Process` via `Runtime.exec("su")` / `Runtime.exec("sh")` — used
by `SplashActivity.BeforeStartThread`.

### LegacyShellBridge.buildStreamingCommand()

New method that builds a full shell command string for streaming execution:

```text
export TOOLKIT='<path>'
export START_DIR='<path>'
export PAGE_CONFIG_DIR='<path>'
...
<executorPath> "<scriptPath>" "<tag>"
exit
exit
```

This replaces the old `ScriptEnvironmen.executeShell()` logic that wrote env
+ executor + script into a `DataOutputStream`.

### LegacyShellBridge.installToolkit()

New private method that replaces `ExtractAssets.extractResources()` for
toolkit extraction:

1. Loads `assets/toolchain/manifest.json` via `ToolManifestLoader`.
2. Constructs `ToolchainInstaller` with an `assetProvider` that opens
   `assets/bin/<name>` streams.
3. Runs `installer.install(<filesDir>/bin/, verifyChecksums=false)`.
4. Returns the tools directory path.

This uses the same `ToolchainInstaller` (Stage 11) that
`FirmwareOperationService.onCreate` uses (Stage 21) — unified tool
installation path.

### Manifest expanded

Added 8 entries to `assets/toolchain/manifest.json`:
- `utils` (shell utility script)
- 7 shared libraries: `libandroid-posix-semaphore.so`, `libandroid-support.so`,
  `libbz2.so.1.0`, `libffi.so`, `liblz4.so`, `liblzma.so.5`, `libz.so.1`

The manifest now declares all 21 files in `assets/bin/` — 13 executables +
8 shared libs/utils. This allows `ToolchainInstaller` to extract everything.

### SplashActivity.BeforeStartThread updated

- No longer imports `com.omarea.common.shell.ShellExecutor`.
- Uses `ScriptEnvironmen.getRuntime()` (now returns a real Process) instead
  of `ShellExecutor.getSuperUserRuntime()` / `ShellExecutor.getRuntime()`.
- Uses `LegacyShellBridge.buildStreamingCommand()` instead of
  `ScriptEnvironmen.executeShell()`.

## CI gate

```bash
python3 tools/check-final-cleanup.py
```

Expected output:

```text
PASS: ShellExecutor.java replaced by ShellExecutor.kt (uses Runtime.exec directly)
PASS: ShellExecutor.execute() uses LegacyShellBridge.buildStreamingCommand (not ScriptEnvironmen.getRuntime)
PASS: ScriptEnvironmen.getRuntime() returns a real Process (not null)
PASS: LegacyShellBridge.buildStreamingCommand + installToolkit are declared
PASS: SplashActivity.BeforeStartThread uses new API (no common.shell.ShellExecutor dependency)
PASS: Manifest covers all 21 assets/bin/ files (13 executables + 8 shared libs/utils)
PASS: LegacyShellBridge.init uses ToolchainInstaller (not ExtractAssets) for toolkit
```

## What is intentionally NOT done here

- `ExtractAssets.java` is NOT removed — it is still used by `RuntimeBinder`
  for `<resource file="...">` extraction (KrScript XML resource tags) and by
  `LegacyShellBridge` for `PAGE_WORK_DIR`/`PAGE_WORK_FILE` env-var paths.
  That is a different use case (individual file extraction, not toolkit
  installation). A future stage may replace it with a typed
  `ResourceExtractor`.
- `common/shell/ShellExecutor.java` is REMOVED — it was a utility class with
  `getSuperUserRuntime()`/`getRuntime()` that creates Process objects. After
  Stage 23, no caller imports it (SplashActivity uses
  `ScriptEnvironmen.getRuntime()` instead). Dead code, deleted.
- `ShellTranslation.kt` is NOT removed — it is the translation table for
  `$({KEY})` placeholders, used by `LegacyShellBridge`,
  `KeepShellRuntime`, `RuntimeBinder`, `SimpleShellWatcher`, and
  `SplashActivity`. Not legacy.
- The `ScriptEnvironmen.executeShell()` method is kept as a no-op facade —
  it writes `cmds` to the `DataOutputStream` for backward compat, but the
  primary execution path now goes through `ShellExecutor.execute()` →
  `LegacyShellBridge.buildStreamingCommand()`.
