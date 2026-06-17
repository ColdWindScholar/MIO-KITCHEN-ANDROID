# Project documentation

This folder is intentionally kept inside the project archive so that architecture notes,
developer rules, and validation commands travel together with the source code.

## Roadmap

- `ru/roadmap/mio_kitchen_modernization_plan_v3_single_apk_ru.md` — full roadmap (RU).

## Development documents

| Stage | Topic | Link |
|-------|-------|------|
| 1 | Localization | [dev/localization.md](dev/localization.md) |
| 2 | Critical hotfixes | [dev/critical-hotfixes.md](dev/critical-hotfixes.md) |
| 3 | Build modernization | [dev/build-modernization.md](dev/build-modernization.md) |
| 4 | Storage / workspace | [dev/storage-workspace.md](dev/storage-workspace.md) |
| 5 | KrScript parser/runtime split | [dev/parser-split.md](dev/parser-split.md) |
| 6 | Shell runtime | [dev/shell-runtime.md](dev/shell-runtime.md) |
| 7 | Firmware analyzer | [dev/firmware-analyzer.md](dev/firmware-analyzer.md) |
| 8 | UI modernization | [dev/ui-modernization.md](dev/ui-modernization.md) |
| 9 | Tests and CI | [dev/tests-and-ci.md](dev/tests-and-ci.md) |
| 10 | Runtime profiles | [dev/runtime-profiles.md](dev/runtime-profiles.md) |
| 11 | ToolchainInstaller | [dev/toolchain-installer.md](dev/toolchain-installer.md) |
| 12 | OperationExecutor | [dev/operation-executor.md](dev/operation-executor.md) |
| 13 | targetSdk 35 migration | [dev/targetsdk-35.md](dev/targetsdk-35.md) |
| 14 | Folder/tree URI export policy | [dev/export-policy.md](dev/export-policy.md) |
| 15 | UI migration to AppRuntimeProfile | [dev/ui-migration.md](dev/ui-migration.md) |
| 16 | Static analysis | [dev/static-analysis.md](dev/static-analysis.md) |
| 17 | Coverage reporting | [dev/coverage.md](dev/coverage.md) |
| 18 | Instrumented tests | [dev/instrumented-tests.md](dev/instrumented-tests.md) |
| 20 | Hybrid migration layer | [dev/hybrid-migration.md](dev/hybrid-migration.md) |
| 21 | ToolchainInstaller wiring | [dev/toolchain-wiring.md](dev/toolchain-wiring.md) |
| 22 | Legacy removal | [dev/legacy-removal.md](dev/legacy-removal.md) |
| 23 | Final legacy cleanup | [dev/final-cleanup.md](dev/final-cleanup.md) |

## User documents

| Topic | Link |
|-------|------|
| Quick start | [user/quick-start.md](user/quick-start.md) |
| Choosing a ROM | [user/choose-rom.md](user/choose-rom.md) |
| Unpack operations | [user/unpack.md](user/unpack.md) |
| Modify operations | [user/modify.md](user/modify.md) |
| Pack operations | [user/pack.md](user/pack.md) |
| Flash safety | [user/flash-safety.md](user/flash-safety.md) |
| Storage access | [user/storage-access.md](user/storage-access.md) |
| Root mode | [user/root-mode.md](user/root-mode.md) |
| Troubleshooting | [user/troubleshooting.md](user/troubleshooting.md) |

## Running all checks

```bash
python3 tools/run-all-checks.py
```
