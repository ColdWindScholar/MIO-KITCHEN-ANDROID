# Stage 12 — OperationExecutor

> Date: 2026-06-18
> Status: completed
> Related stages: after Stage 11 (ToolchainInstaller), before Stage 13 (targetSdk 35 runtime migration)

---

Stage 10 produces an `OperationPlan` (a typed description of what we want to
do). Stage 6 gives us `ShellRuntime` (a typed way to run shell). Stage 12 is
the bridge between the two: an `OperationExecutor` that turns a plan into a
`ShellCommand` and runs it through `ShellRuntime`.

## What this stage does

Before this stage there was no typed way to:

- turn an `OperationPlan` into an actual shell command with the right env
  variables (`OPERATION`, `FIRMWARE_PATH`, `WORK_DIR`, `TOOLS_DIR`, capability
  flags);
- enforce the safety profile (refuse to run a destructive operation without
  root, or in dry-run mode);
- collect `ShellEvent`s from an operation into a typed `ShellResult`;
- preview a command without actually running it.

Stage 12 introduces:

- `PreparedExecution` — data class wrapping `OperationPlan` + `ShellCommand` +
  `dryRun` flag, with `ready` and `blockers()`.
- `OperationExecutor` — converts `OperationPlan` to `PreparedExecution`, then
  runs via `ShellRuntime.execute()` (Flow) or `ShellRuntime.executeForResult()`
  (suspend).
- `OperationExecutionException` — typed error for "no script registered" /
  "plan not ready".

## New API

```text
common/operations/
  OperationExecutor.kt       # PreparedExecution + OperationExecutor +
                             # OperationExecutionException

common/src/test/java/.../operations/
  OperationExecutorTest.kt   # 11 JVM tests using DryRun + Fake shell runtime
```

## Contract

```text
OperationExecutor
  -> prepare(plan, dryRun): PreparedExecution
  -> execute(prepared): Flow<ShellEvent>
  -> executeForResult(prepared): ShellResult (suspend)
  -> does NOT show UI
  -> does NOT hold global state
  -> delegates shell execution to ShellRuntime
  -> refuses to run when plan.canExecute == false (unless dryRun=true)
  -> disables requiresRoot in dry-run mode

PreparedExecution
  -> operation: FirmwareOperation
  -> plan: OperationPlan
  -> command: ShellCommand
  -> dryRun: Boolean
  -> ready: Boolean (plan.canExecute OR dryRun)
  -> blockers(): List<String> (empty in dry-run)
```

## Environment variables set by the executor

```text
OPERATION             -> operation name (e.g. UNPACK_BOOT_IMAGE)
FIRMWARE_TYPE         -> firmware package type
FIRMWARE_ANDROID      -> android version hint (if known)
FIRMWARE_PATH         -> path to the firmware (DirectPath/WorkspaceFile sources)
WORK_DIR              -> workspace path (from workspacePathProvider)
TOOLS_DIR             -> toolchain directory (from toolsDirProvider)
HAS_PAYLOAD_BIN       -> 1/0 capability flag
HAS_SUPER_IMAGE       -> 1/0 capability flag
HAS_DYNAMIC_PARTITIONS-> 1/0 capability flag
HAS_EROFS             -> 1/0 capability flag
HAS_EXT4              -> 1/0 capability flag
HAS_BOOT_IMAGE        -> 1/0 capability flag
HAS_VBMETA            -> 1/0 capability flag
USES_AVB              -> 1/0 capability flag
USES_AB               -> 1/0 capability flag
REQUIRES_16KB_CHECK   -> 1/0 capability flag
REQUIRED_TOOLS        -> space-separated list of required tool names
```

## CI gate

```bash
python3 tools/check-operation-executor.py
```

Expected output:

```text
PASS: OperationExecutor with prepare/execute/executeForResult is in place
PASS: PreparedExecution wraps ShellCommand with operation env
PASS: defaultScriptLocator maps all FirmwareOperation values to script2/ paths
PASS: OperationExecutor tests cover prepare/execute/dry-run/blocked
PASS: OperationExecutor is pure (no android.app/android.widget imports)
```

## What is intentionally NOT done here

- The executor does NOT yet call `ToolchainInstaller` from Stage 11 to ensure
  the tools are present. The caller is expected to run the installer first.
  A higher-level facade that chains installer + executor will be added in a
  later stage.
- The executor delegates shell execution entirely to `ShellRuntime`. When the
  `pio` module wires this into a real Activity, it will pass a
  `KeepShellRuntime` (Stage 6 transitional impl).
- The executor does NOT parse shell output to update progress; that's the job
  of a future `ShellEventParser` that maps stdout lines to typed progress
  events.
