# Critical hotfixes and regression checks

## Stage goal

This stage fixes concrete defects found during the architecture audit without a large build-stack, storage, parser/runtime, or UI migration. The goal is to stabilize risky areas before deeper refactoring.

## Fixed areas

| Area | File | Fix |
| --- | --- | --- |
| URI import | `common/src/main/java/com/omarea/common/shared/FilePathResolver.java` | `content://` copies now write only the number of bytes actually read. |
| MIME mapping | `krscript/src/main/java/com/omarea/krscript/config/Suffix2Mime.kt` | Extension groups such as `jpg/jpeg/jpe`, `tar/taz/tgz`, and `html/htm/shtml` now work as individual branches. |
| Dynamic menu | `krscript/src/main/java/com/omarea/krscript/ui/PageMenuLoader.kt` | Dynamic menu options are now appended to the list instead of being discarded after parsing. |
| Hidden actions | `krscript/src/main/java/com/omarea/krscript/ui/ActionListFragment.kt` | `shellModeHidden` now executes the shell command through `ShellExecutor` and clears `hiddenTaskRunning` when finished. |
| Param options | `krscript/src/main/java/com/omarea/krscript/ui/ActionListFragment.kt` | Dynamic `value|title` options no longer crash when the title part is empty or incomplete. |
| DocumentsProvider flags | `pio/src/main/java/com/mio/kitchen/MTDataFilesProvider.java` | Write/create/delete/rename flags are no longer reset to `0` before parent flags are added. |
| Flash image | `pio/src/main/assets/script/tool.sh` | `iMG` / `${i}MG` typos were replaced with the correct `IMG`; both symlink and block-device targets are accepted. |
| Pack image | `pio/src/main/assets/script/tool.sh` | `readsize` no longer overwrites manual size config with auto size; `mkerofs` uses the function argument. |
| Boot repack | `pio/src/main/assets/script/tool.sh` | `$mdir` paths, partition argument usage, and risky `break` behavior were fixed. |
| XML param | `pio/src/main/assets/script2/more.xml` | `requiret="true"` was corrected to `required="true"`. |

## Checks

Added script:

```bash
python3 tools/check-known-regressions.py
```

It verifies that these defects are not reintroduced. This script does not replace full unit/instrumented tests, but it provides a fast CI gate for known critical regressions.

Additional shell syntax check:

```bash
bash -n pio/src/main/assets/script/tool.sh
```

## CI

Added workflow:

```text
.github/workflows/regression-checks.yml
```

It runs:

```text
python3 tools/validate-localization.py
python3 tools/check-known-regressions.py
bash -n pio/src/main/assets/script/tool.sh
```

## Out of scope

This stage does not change:

- Gradle / AGP / Kotlin versions;
- `targetSdkVersion` / `compileSdkVersion`;
- storage architecture;
- KrScript parser architecture;
- Shell runtime architecture;
- ViewBinding/ViewModel migration;
- firmware toolchain capabilities.

Those changes belong to the next separate stages.
