# Root-режим

Root **опционален** в MIO-KITCHEN. Большинство операций работают без root;
root включает только несколько специфичных workflow.

## Что включает root

| Операция | Без root | С root |
|----------|----------|--------|
| Выбор файла через SAF | ✓ | ✓ |
| Копирование в workspace | ✓ | ✓ |
| Анализ прошивки | ✓ | ✓ |
| Распаковка ROM / boot / super / payload / filesystem | ✓ | ✓ |
| Упаковка filesystem / boot / super | ✓ | ✓ |
| Проверка vbmeta | ✓ | ✓ |
| Чтение файлов вне app-specific dir | ✗ | ✓ |
| `FLASH_PREPARE` | ✗ (блокируется `requiresRoot`) | ✓ |
| Direct-path storage-режим (`preferLegacyDirectPath`) | ограничено | полный |

## Как root определяется

`DeviceProfileProvider` проверяет root через `KeepShellPublic.checkRoot()`,
который запускает `[ su ]` и инспектирует вывод. Результат сохраняется в
`DeviceProfile.hasRoot`:

```text
hasRoot = null   -> root-проверка ещё не выполнялась
hasRoot = true   -> root доступен
hasRoot = false  -> root недоступен (или пользователь отклонил Magisk-промпт)
```

`OperationPlanner` консультируется с этим флагом при построении плана. Если
операция требует root, а `hasRoot != true`, то `canExecute` плана false и
`blockers()` перечисляет требование root.

## Root shell runtime

Когда root доступен, `ShellRuntimeFactory` возвращает `RootShellRuntime`
(расширяет `KeepShellRuntime`), который использует `su` вместо `sh`. Shell-
сессия удерживается `KeepShellPublic.getDefaultInstance()` и переиспользуется
между операциями — нет per-command `su`-промпта.

Когда root недоступен, фабрика возвращает `UserShellRuntime`, который
использует `sh`. Все non-root операции работают так же.

## Dry-run режим

Если вы хотите увидеть, какие команды будут запущены, без их выполнения,
установите `ShellRuntimeFactory.dryRun = true`. Фабрика вернёт
`DryRunShellRuntime` для каждой команды, который печатает запланированную
команду как stdout и выходит 0. Это полезно для:
- Просмотра flash-скриптов перед прошивкой.
- Отладки toolchain resolution.
- CI smoke-тестов.

## Magisk интеграция

Приложение не bundle-ит Magisk. Если Magisk установлен, приложение
использует бинарник `magiskboot` из toolchain-манифеста для boot image
операций. Приложение не модифицирует собственное состояние Magisk.

## SELinux

`DeviceProfile.selinuxMode` сообщает текущий режим SELinux:
- `ENFORCING` — норма; root-операции могут ограничиваться SELinux-политикой.
- `PERMISSIVE` — часто на rooted-устройствах; root-операции работают свободно.
- `DISABLED` — редко; root-операции работают свободно.
- `UNKNOWN` — probe не запускался (по умолчанию при старте приложения).

Операции не меняют режим SELinux. Если flash-операция проваливается с
"permission denied" несмотря на доступный root, проверьте режим SELinux и
временно установите permissive (`setenforce 0`) из root-shell.

## Что дальше

- [Безопасность flash](flash-safety.md) — root требуется для flash.
- [Доступ к хранилищу](storage-access.md) — root включает direct-path режим.
- [Устранение неполадок](troubleshooting.md) — root-связанные ошибки.
