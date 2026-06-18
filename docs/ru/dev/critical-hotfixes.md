# Критические hotfix-и и регрессионные проверки

## Цель этапа

Этот этап закрывает конкретные дефекты, найденные при архитектурном аудите, без крупной миграции build stack, storage, parser/runtime или UI. Цель — стабилизировать опасные места перед дальнейшим рефакторингом.

## Исправленные зоны

| Зона | Файл | Что исправлено |
| --- | --- | --- |
| URI import | `common/src/main/java/com/omarea/common/shared/FilePathResolver.java` | Копирование `content://` теперь пишет только фактически прочитанные байты. |
| MIME mapping | `krscript/src/main/java/com/omarea/krscript/config/Suffix2Mime.kt` | Группы расширений `jpg/jpeg/jpe`, `tar/taz/tgz`, `html/htm/shtml` теперь работают как отдельные варианты. |
| Dynamic menu | `krscript/src/main/java/com/omarea/krscript/ui/PageMenuLoader.kt` | Динамические menu options теперь добавляются в список, а не теряются после парсинга. |
| Hidden actions | `krscript/src/main/java/com/omarea/krscript/ui/ActionListFragment.kt` | `shellModeHidden` теперь реально запускает shell-команду через `ShellExecutor` и сбрасывает `hiddenTaskRunning` после завершения. |
| Param options | `krscript/src/main/java/com/omarea/krscript/ui/ActionListFragment.kt` | Динамические options формата `value|title` больше не падают при пустой/неполной title-части. |
| DocumentsProvider flags | `pio/src/main/java/com/mio/kitchen/MTDataFilesProvider.java` | Флаги write/create/delete/rename больше не сбрасываются в `0` перед добавлением parent flags. |
| Flash image | `pio/src/main/assets/script/tool.sh` | Опечатки `iMG` / `${i}MG` заменены на корректный `IMG`; поддержаны symlink и block-device target. |
| Pack image | `pio/src/main/assets/script/tool.sh` | `readsize` больше не перетирает ручной размер auto-значением; `mkerofs` использует параметр функции. |
| Boot repack | `pio/src/main/assets/script/tool.sh` | Исправлены `$mdir`-пути, параметр partition и `return 1` вместо рискованного `break`. |
| XML param | `pio/src/main/assets/script2/more.xml` | `requiret="true"` исправлено на `required="true"`. |

## Проверки

Добавлен скрипт:

```bash
python3 tools/check-known-regressions.py
```

Он проверяет, что эти ошибки не вернулись. Скрипт не заменяет полноценные unit/instrumented tests, но даёт быстрый CI-gate для известных критических регрессий.

Дополнительно выполняется shell syntax check:

```bash
bash -n pio/src/main/assets/script/tool.sh
```

## CI

Добавлен workflow:

```text
.github/workflows/regression-checks.yml
```

Он запускает:

```text
python3 tools/validate-localization.py
python3 tools/check-known-regressions.py
bash -n pio/src/main/assets/script/tool.sh
```

## Что не входило в этап

Этот этап не меняет:

- Gradle / AGP / Kotlin versions;
- `targetSdkVersion` / `compileSdkVersion`;
- storage architecture;
- KrScript parser architecture;
- Shell runtime architecture;
- ViewBinding/ViewModel migration;
- firmware toolchain capabilities.

Эти изменения идут следующими отдельными этапами.
