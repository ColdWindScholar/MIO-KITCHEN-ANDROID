# Этап 6 — Shell runtime

> Дата: 2026-06-17
> Статус: завершено
> Связанные этапы: после Stage 5 (parser split), перед Stage 7 (firmware analyzer)

---


До этого этапа единственным способом запустить shell был статический
`ScriptEnvironmen.executeResultRoot(context, script, nodeInfo)` и пара хелперов
`KeepShellPublic.doCmdSync()`. У этого API было несколько проблем:

- Он владел глобальным состоянием (`static`-поля, синглтон-инстансы `KeepShell`).
- Он смешивал выполнение shell, управление кэшем, шаблонизацию env-переменных и
  подготовку метаданных конфигурации страницы.
- Он возвращал сырой `String`. Не было типизированных ошибок, событий прогресса,
  структурированной обработки таймаутов, режима dry-run.
- Его было сложно тестировать — каждый тест, затрагивающий shell, требовал
  Robolectric или реальное устройство.

Этап 6 вводит типизированный API `ShellRuntime` с ограниченными побочными
эффектами, который должен стать целью для всего нового кода.

### Новый API

```text
common/shell/runtime/
  ScriptSource.kt              # Inline / FilePath / PreparedFile
  ShellCommand.kt              # id, script, env, workingDir, requiresRoot, timeoutMs, tag
  ShellEvent.kt                # Stdout / Stderr / Progress / Warning / Error / Completed
  ShellResult.kt               # Completed / Cancelled / TimedOut / Failed
  ShellRuntime.kt              # interface: execute(Flow) + executeForResult(suspend)
  DryRunShellRuntime.kt        # не запускает shell, возвращает preview + Completed(0)
  FakeShellRuntime.kt          # заглушка для тестов, записывает команды
  KeepShellRuntime.kt          # переходная реализация, оборачивает старый KeepShell
                                # + подклассы RootShellRuntime и UserShellRuntime
  ShellRuntimeFactory.kt       # выбирает runtime по requiresRoot + dryRun + rootAvailable
```

### Контракты

```text
ShellRuntime
  -> execute(command): Flow<ShellEvent>
  -> executeForResult(command): ShellResult
  -> терминальное событие — всегда Completed или Error
  -> без глобального состояния (id сессии — в ShellCommand.id)
  -> уважает timeoutMs и отмену корутины

Реализации:
  DryRunShellRuntime   -> без shell, всегда exit 0
  FakeShellRuntime     -> заглушка для тестов с recordedCommands
  RootShellRuntime     -> оборачивает KeepShellPublic (root-режим)
  UserShellRuntime     -> оборачивает KeepShell (пользовательский режим)
  ShellRuntimeFactory  -> выбирает реализацию по command + окружению
```

### Почему переходная

Дорожная карта в конечном итоге хочет новый shell-движок, не зависящий от
`KeepShell` и не использующий `GlobalScope`. Этот большой рефакторинг отложен на
поздний этап. Сейчас `KeepShellRuntime` переиспользует существующий рабочий
`KeepShell`, чтобы:

- Новый код уже мог работать с типизированным API `ShellRuntime`.
- Существующие `ScriptEnvironmen` и `KeepShell` продолжали работать — их всё
  ещё используют старый `PageConfigReader`, `ActionListFragment`,
  `RuntimeBinder` и др.
- Shell runtime можно мигрировать поэтапно, класс за классом.

### CI-проверка

```bash
python3 tools/check-shell-runtime.py
```

Ожидаемый вывод:

```text
PASS: Shell runtime typed API is in place
PASS: ShellCommand/ShellEvent/ShellResult sealed types are declared
PASS: DryRunShellRuntime and FakeShellRuntime are present
PASS: RootShellRuntime and UserShellRuntime wrap KeepShell
PASS: ShellRuntimeFactory selects runtime based on command + dry-run + root
PASS: Shell runtime JVM unit tests are present
```

### Что намеренно НЕ сделано здесь

- `ScriptEnvironmen` сохранён как тонкий Kotlin-фасад над
  `LegacyShellBridge` (см. Stage 22) — legacy публичный API всё ещё
  используется ~41 call-site в KrScript UI-коде.
- `KeepShell` полностью удалён в Stage 22 — `KeepShellRuntime` теперь
  использует `Runtime.exec()` напрямую.
- Использование `GlobalScope` убрано — `KeepShell.kt` (единственный
  `GlobalScope` user) удалён в Stage 22.
- Поддержка отмены опирается на отмену корутины; явный kill процесса при
  отмене отложен.
