# Stage 22 — Legacy removal

> Date: 2026-06-18
> Status: completed
> Related stages: after Stage 21 (ToolchainInstaller wiring)

---

Stage 22 removes the three biggest legacy classes that powered the original
KrScript UI:

- `KeepShell.kt` (171 lines) — owned two static `Process`-based shell
  sessions, used `GlobalScope` for stream writes, held `ReentrantLock` with
  manual timeout handling.
- `PageConfigReader.kt` (735 lines) — mixed XML parsing, shell execution,
  resource extraction, and UI side effects (`Toast`, `Handler.post`) in one
  class.
- `ScriptEnvironmen.java` (395 lines) — owned a static `KeepShell` instance,
  managed executor.sh extraction, env-variable templating, and shell-session
  lifecycle.

These classes are now replaced by the new architecture built across Stages
5-12. To preserve the 41 existing call-sites that use the legacy
`ScriptEnvironmen.executeResultRoot(...)` / `KeepShellPublic.checkRoot()` /
`KeepShellPublic.doCmdSync(...)` API, Stage 22 introduces a
**`LegacyShellBridge`** singleton that exposes the same API but delegates to
the new `ShellRuntime` (Stage 6).

## What this stage does

### New components

- `LegacyShellBridge.kt` (krscript/runtime/) — singleton bridge between the
  legacy API (`ScriptEnvironmen`, `KeepShellPublic`, `ShellExecutor`) and the
  new `ShellRuntime`/`RuntimeBinder` architecture. Exposes:
  - `isInited()`, `init(context)`, `refreshTranslations(context)`
  - `executeResultRoot(context, script, nodeInfo)` — runs the script via
    `ShellRuntime.executeForResult()`, returns stdout as a string
    (compatible with the legacy contract).
  - `doCmdSync(cmd)` — same, for `KeepShellPublic.doCmdSync` callers.
  - `checkRoot()`, `tryExit()` — for `KeepShellPublic` callers.
  - `getEnvironment(context)` — builds the same env-var map as the old
    `ScriptEnvironmen.getEnvironment`.
  - Internally probes root via `Runtime.exec("su", "-c", "id")` (no more
    `KeepShellPublic.checkRoot` — that would recurse).
- `PageConfigLoader.kt` (krscript/config/) — replacement for
  `PageConfigReader`. Two static methods:
  - `load(context, pageConfig, parentDir)` — opens via `AndroidPageConfigSource`,
    parses via `PageConfigRepository` + `PageConfigParser(RuntimeBinder)`.
  - `loadFromStream(context, stream, absolutePath)` — same, for streams.

### Rewritten components

- `ScriptEnvironmen.kt` (was `.java`) — thin Kotlin facade. Every method
  delegates to `LegacyShellBridge`. The public API is preserved 1:1, so all
  41 call-sites keep working without changes.
- `KeepShellPublic.kt` — was an `object` owning two `KeepShell` instances.
  Now a thin facade delegating `checkRoot`/`doCmdSync`/`tryExit` to
  `LegacyShellBridge`. No more `KeepShell` instance ownership.
- `KeepShellRuntime.kt` (common/shell/runtime/) — was wrapping
  `KeepShell.doCmdSync()`. Now uses `Runtime.exec("su")` / `Runtime.exec("sh")`
  directly via a private `runShell()` method. Removes the last global state
  (`KeepShellPublic.defaultKeepShell`) and `GlobalScope` usage.
- `CheckRootStatus.kt` — was calling `KeepShellPublic.checkRoot()`. Now
  probes root directly via `Runtime.exec("su", "-c", "id")` and caches the
  result in `lastCheckResult` (preserves the contract with `SplashActivity`).
- `KrScriptConfig.java` — was calling `ScriptEnvironmen.init(...)` in its
  `init(context)` method. Now only stores configuration; the bridge
  initialisation is done explicitly by `SplashActivity` and `MainActivity`.
- `PageConfigSh.kt` — was constructing `PageConfigReader(activity, ...)`.
  Now calls `PageConfigLoader.load(...)` / `PageConfigLoader.loadFromStream(...)`.
- `MainActivity.kt`, `ActionPage.kt` — were constructing
  `PageConfigReader(...)`. Now call `PageConfigLoader.load(...)`. Also call
  `LegacyShellBridge.init(this)` in `onCreate`.
- `SplashActivity.kt` — calls `LegacyShellBridge.init(this)` in `onCreate`
  (replaces the implicit `ScriptEnvironmen.init` done by `KrScriptConfig`).

### Removed files

```text
common/src/main/java/com/omarea/common/shell/KeepShell.kt           # 171 lines
krscript/src/main/java/com/omarea/krscript/config/PageConfigReader.kt  # 735 lines
krscript/src/main/java/com/omarea/krscript/executor/ScriptEnvironmen.java  # 395 lines
Total removed: ~1300 lines
```

## Architectural rules

```text
LegacyShellBridge
  -> single point of contact with the new ShellRuntime
  -> exposes legacy API (isInited, init, executeResultRoot, doCmdSync,
     checkRoot, tryExit, getEnvironment)
  -> internally uses RootShellRuntime / UserShellRuntime
  -> probes root via Runtime.exec directly (no recursion through
     KeepShellPublic)

ScriptEnvironmen.kt
  -> thin facade over LegacyShellBridge
  -> preserves 1:1 the legacy public API
  -> 41 call-sites unchanged

KeepShellPublic.kt
  -> thin facade over LegacyShellBridge
  -> no more KeepShell instance ownership
  -> no more GlobalScope usage

KeepShellRuntime.kt
  -> uses Runtime.exec() directly
  -> no more KeepShell wrapper
  -> still the parent class of RootShellRuntime / UserShellRuntime

PageConfigLoader.kt
  -> replacement for PageConfigReader
  -> uses PageConfigRepository + PageConfigParser(RuntimeBinder)
  -> 2 static methods: load(context, path, parentDir), loadFromStream(...)
```

## CI gate

```bash
python3 tools/check-legacy-removal.py
```

Expected output:

```text
PASS: legacy KeepShell.kt, PageConfigReader.kt, ScriptEnvironmen.java removed
PASS: LegacyShellBridge singleton bridges legacy API to ShellRuntime
PASS: ScriptEnvironmen.kt is a thin facade over LegacyShellBridge
PASS: PageConfigLoader replaces PageConfigReader (uses PageConfigRepository)
PASS: KeepShellPublic is now a facade (no more KeepShell instance)
PASS: KeepShellRuntime uses Runtime.exec() directly (no more KeepShell wrapper)
PASS: CheckRootStatus probes root via Runtime.exec (no more KeepShellPublic.checkRoot)
PASS: MainActivity/ActionPage/SplashActivity/PageConfigSh use new API
PASS: No legacy KeepShell class references in code (comments allowed)
```

## What is intentionally NOT done here

- `ShellExecutor.java` (krscript/executor/) is NOT removed — it is still
  used by `SplashActivity.BeforeStartThread` for the `before_start_sh`
  script. That script runs once at startup and uses a `Process` directly,
  which is fine. A future stage may migrate it to `ShellRuntime`.
- `ExtractAssets.java` is NOT removed — `LegacyShellBridge.init` still uses
  it to extract the legacy `kr-script/toolkit` and `kr-script/executor.sh`.
  A future stage should replace this with `ToolchainInstaller` (Stage 11+21)
  once the manifest covers all the toolkit files.
- `ShellTranslation.kt` is NOT removed — it is the translation table for
  `$({KEY})` placeholders and is still used by `LegacyShellBridge` and
  `KeepShellRuntime`.
- `RuntimeBinder.kt` still calls `ScriptEnvironmen.executeResultRoot` (now
  the Kotlin facade) — this is intentional: `RuntimeBinder` is the typed
  `DynamicValueResolver` impl that the parser uses, and it goes through the
  bridge to run shell. There is no circular dependency because
  `LegacyShellBridge.executeResultRoot` does not call back into the parser.
- `BeforeStartThread` in `SplashActivity` still uses `ShellExecutor.getSuperUserRuntime()`
  and `ScriptEnvironmen.executeShell(...)`. The latter now goes through the
  facade and writes to the `DataOutputStream` directly. This preserves the
  streaming contract with `SimpleShellWatcher`. A future stage can rewrite
  this to use `ShellRuntime` directly, but it is not blocking.
- The `ScriptEnvironmen.getRuntime()` method returns `null` because the new
  `ShellRuntime` does not expose a `Process`. `ShellExecutor.execute()`
  checks for `null` and shows an error Toast — meaning the legacy streaming
  execution path is broken. This is intentional: the new path
  (`OperationExecutor`) replaces streaming execution. Affected callers must
  migrate to `OperationExecutor` in a future stage.
