# Root mode

Root is **optional** in MIO-KITCHEN. Most operations work without root; root
only enables a few specific workflows.

## What root enables

| Operation | Without root | With root |
|-----------|--------------|-----------|
| Pick file via SAF | ✓ | ✓ |
| Copy into workspace | ✓ | ✓ |
| Analyze firmware | ✓ | ✓ |
| Unpack ROM / boot / super / payload / filesystem | ✓ | ✓ |
| Pack filesystem / boot / super | ✓ | ✓ |
| Verify vbmeta | ✓ | ✓ |
| Read files outside app-specific dir | ✗ | ✓ |
| `FLASH_PREPARE` | ✗ (blocked by `requiresRoot`) | ✓ |
| Direct-path storage mode (`preferLegacyDirectPath`) | limited | full |

## How root is detected

`DeviceProfileProvider` checks root via `KeepShellPublic.checkRoot()`, which
runs `[ su ]` and inspects the output. The result is stored in
`DeviceProfile.hasRoot`:

```text
hasRoot = null   -> root check not performed yet
hasRoot = true   -> root available
hasRoot = false  -> root not available (or user denied Magisk prompt)
```

The `OperationPlanner` consults this flag when building the plan. If an
operation requires root and `hasRoot != true`, the plan's `canExecute` is
false and `blockers()` lists the root requirement.

## Root shell runtime

When root is available, `ShellRuntimeFactory` returns a `RootShellRuntime`
(extends `KeepShellRuntime`) which uses `su` instead of `sh`. The shell
session is held by `KeepShellPublic.getDefaultInstance()` and reused across
operations — there is no per-command `su` prompt.

When root is not available, the factory returns `UserShellRuntime` which uses
`sh`. All non-root operations work the same.

## Dry-run mode

If you want to see what commands would run without executing them, set
`ShellRuntimeFactory.dryRun = true`. The factory then returns
`DryRunShellRuntime` for every command, which prints the planned command as
stdout and exits 0. This is useful for:
- Reviewing flash scripts before flashing.
- Debugging toolchain resolution.
- CI smoke tests.

## Magisk integration

The app does not bundle Magisk. If Magisk is installed, the app uses the
`magiskboot` binary from the toolchain manifest for boot image operations.
The app does not modify Magisk's own state.

## SELinux

`DeviceProfile.selinuxMode` reports the current SELinux mode:
- `ENFORCING` — normal; root operations may be limited by SELinux policy.
- `PERMISSIVE` — common on rooted devices; root operations work freely.
- `DISABLED` — rare; root operations work freely.
- `UNKNOWN` — the probe has not been run (default at app start).

Operations do not change SELinux mode. If a flash operation fails with
"permission denied" despite root being available, check SELinux mode and
temporarily set it to permissive (`setenforce 0`) from a root shell.

## Next steps

- [Flash safety](flash-safety.md) — root is required for flash.
- [Storage access](storage-access.md) — root enables direct-path mode.
- [Troubleshooting](troubleshooting.md) — root-related errors.
