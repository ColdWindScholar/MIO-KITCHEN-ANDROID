# Stage 6 — Shell runtime

> Date: 2026-06-17
> Status: completed
> Related stages: after Stage 5 (parser split), before Stage 7 (firmware analyzer)

---


Before this stage the only way to execute shell was the static
`ScriptEnvironmen.executeResultRoot(context, script, nodeInfo)` API plus a few
`KeepShellPublic.doCmdSync()` helpers. That API had several problems:

- It owned global state (`static` fields, singleton `KeepShell` instances).
- It mixed shell execution with cache management, env-variable templating, and
  page-config metadata preparation.
- It returned a raw `String`. There were no typed errors, no progress events,
  no structured timeout handling, no dry-run mode.
- It was hard to test — every test that touched shell needed Robolectric or
  a real device.

Stage 6 introduces a typed, side-effect-bounded `ShellRuntime` API that all
new code should target.

### New API

```text
common/shell/runtime/
  ScriptSource.kt              # Inline / FilePath / PreparedFile
  ShellCommand.kt              # id, script, env, workingDir, requiresRoot, timeoutMs, tag
  ShellEvent.kt                # Stdout / Stderr / Progress / Warning / Error / Completed
  ShellResult.kt               # Completed / Cancelled / TimedOut / Failed
  ShellRuntime.kt              # interface: execute(Flow) + executeForResult(suspend)
  DryRunShellRuntime.kt        # does not run shell, returns preview + Completed(0)
  FakeShellRuntime.kt          # records commands, emits pre-set events/results
  KeepShellRuntime.kt          # transitional impl that wraps legacy KeepShell
                                # + RootShellRuntime and UserShellRuntime subclasses
  ShellRuntimeFactory.kt       # picks runtime based on requiresRoot + dryRun + rootAvailable
```

### Contracts

```text
ShellRuntime
  -> execute(command): Flow<ShellEvent>
  -> executeForResult(command): ShellResult
  -> terminal event is always Completed or Error
  -> no global state (session id is in ShellCommand.id)
  -> honours timeoutMs and coroutine cancellation

Implementations:
  DryRunShellRuntime   -> no shell, always exits 0
  FakeShellRuntime     -> test stub with recordedCommands
  RootShellRuntime     -> wraps KeepShellPublic (rooted mode)
  UserShellRuntime     -> wraps KeepShell (non-root mode)
  ShellRuntimeFactory  -> picks one based on command + environment
```

### Why transitional

The roadmap ultimately wants a brand-new shell engine that does not depend on
`KeepShell` and does not use `GlobalScope`. That larger rewrite is deferred to a
later stage. For now, `KeepShellRuntime` reuses the existing, working `KeepShell`
so that:

- New code can already target the typed `ShellRuntime` API.
- Existing `ScriptEnvironmen` and `KeepShell` keep working — they are still used
  by legacy `PageConfigReader`, `ActionListFragment`, `RuntimeBinder`, etc.
- The shell runtime can be incrementally migrated, class by class.

### CI gate

```bash
python3 tools/check-shell-runtime.py
```

Expected output:

```text
PASS: Shell runtime typed API is in place
PASS: ShellCommand/ShellEvent/ShellResult sealed types are declared
PASS: DryRunShellRuntime and FakeShellRuntime are present
PASS: RootShellRuntime and UserShellRuntime wrap KeepShell
PASS: ShellRuntimeFactory selects runtime based on command + dry-run + root
PASS: Shell runtime JVM unit tests are present
```

### What is intentionally NOT done here

- `ScriptEnvironmen` is preserved as a thin Kotlin facade over
  `LegacyShellBridge` (see Stage 22) — the legacy public API is still used by
  ~41 call-sites in the KrScript UI code.
- `KeepShell` is fully removed in Stage 22 — `KeepShellRuntime` now uses
  `Runtime.exec()` directly.
- `GlobalScope` usage is gone — `KeepShell.kt` (the only `GlobalScope` user)
  was removed in Stage 22.
- Cancellation support relies on coroutine cancellation; explicit process
  kill on cancel is deferred.

---
