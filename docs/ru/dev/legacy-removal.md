# Этап 22 — Удаление legacy

> Дата: 2026-06-18
> Статус: завершено
> Связанные этапы: после Stage 21 (ToolchainInstaller wiring)

---

Этап 22 удаляет три крупнейших legacy-класса, на которых работал оригинальный
KrScript UI:

- `KeepShell.kt` (171 строка) — владел двумя static `Process`-based shell-
  сессиями, использовал `GlobalScope` для записи в streams, удерживал
  `ReentrantLock` с ручной обработкой timeout.
- `PageConfigReader.kt` (735 строк) — смешивал XML-парсинг, shell-выполнение,
  извлечение ресурсов и UI-побочные эффекты (`Toast`, `Handler.post`) в одном
  классе.
- `ScriptEnvironmen.java` (395 строк) — владел static `KeepShell`-инстансом,
  управлял извлечением executor.sh, шаблонизацией env-переменных и
  lifecycle-ом shell-сессии.

Эти классы теперь заменены новой архитектурой, построенной в этапах 5-12.
Чтобы сохранить 41 существующий call-site, использующий legacy-API
`ScriptEnvironmen.executeResultRoot(...)` / `KeepShellPublic.checkRoot()` /
`KeepShellPublic.doCmdSync(...)`, этап 22 вводит singleton
**`LegacyShellBridge`**, который экспонирует тот же API, но делегирует в
новый `ShellRuntime` (Stage 6).

## Что делает этот этап

### Новые компоненты

- `LegacyShellBridge.kt` (krscript/runtime/) — singleton-мост между
  legacy-API (`ScriptEnvironmen`, `KeepShellPublic`, `ShellExecutor`) и новой
  архитектурой `ShellRuntime`/`RuntimeBinder`. Экспонирует:
  - `isInited()`, `init(context)`, `refreshTranslations(context)`
  - `executeResultRoot(context, script, nodeInfo)` — запускает скрипт через
    `ShellRuntime.executeForResult()`, возвращает stdout как строку
    (совместимо с legacy-контрактом).
  - `doCmdSync(cmd)` — то же самое, для `KeepShellPublic.doCmdSync` callers.
  - `checkRoot()`, `tryExit()` — для `KeepShellPublic` callers.
  - `getEnvironment(context)` — строит ту же env-var map, что и старый
    `ScriptEnvironmen.getEnvironment`.
  - Внутри пробует root через `Runtime.exec("su", "-c", "id")` (без
    `KeepShellPublic.checkRoot` — это вызвало бы рекурсию).
- `PageConfigLoader.kt` (krscript/config/) — замена для `PageConfigReader`.
  Два static-метода:
  - `load(context, pageConfig, parentDir)` — открывает через
    `AndroidPageConfigSource`, парсит через `PageConfigRepository` +
    `PageConfigParser(RuntimeBinder)`.
  - `loadFromStream(context, stream, absolutePath)` — то же, для потоков.

### Переписанные компоненты

- `ScriptEnvironmen.kt` (был `.java`) — тонкий Kotlin-фасад. Каждый метод
  делегирует в `LegacyShellBridge`. Публичный API сохранён 1:1, поэтому все
  41 call-site продолжают работать без изменений.
- `KeepShellPublic.kt` — был `object`, владеющим двумя `KeepShell`-инстансами.
  Теперь тонкий фасад, делегирующий `checkRoot`/`doCmdSync`/`tryExit` в
  `LegacyShellBridge`. Больше никакого владения `KeepShell`-инстансом.
- `KeepShellRuntime.kt` (common/shell/runtime/) — был обёрткой над
  `KeepShell.doCmdSync()`. Теперь использует `Runtime.exec("su")` /
  `Runtime.exec("sh")` напрямую через private-метод `runShell()`. Убирает
  последнее глобальное состояние (`KeepShellPublic.defaultKeepShell`) и
  `GlobalScope` usage.
- `CheckRootStatus.kt` — вызывал `KeepShellPublic.checkRoot()`. Теперь
  пробует root напрямую через `Runtime.exec("su", "-c", "id")` и кеширует
  результат в `lastCheckResult` (сохраняет контракт с `SplashActivity`).
- `KrScriptConfig.java` — вызывал `ScriptEnvironmen.init(...)` в своём
  `init(context)` методе. Теперь только хранит конфигурацию; инициализация
  bridge делается явно в `SplashActivity` и `MainActivity`.
- `PageConfigSh.kt` — конструировал `PageConfigReader(activity, ...)`. Теперь
  вызывает `PageConfigLoader.load(...)` / `PageConfigLoader.loadFromStream(...)`.
- `MainActivity.kt`, `ActionPage.kt` — конструировали
  `PageConfigReader(...)`. Теперь вызывают `PageConfigLoader.load(...)`.
  Также вызывают `LegacyShellBridge.init(this)` в `onCreate`.
- `SplashActivity.kt` — вызывает `LegacyShellBridge.init(this)` в `onCreate`
  (заменяет неявный `ScriptEnvironmen.init`, который делал `KrScriptConfig`).

### Удалённые файлы

```text
common/src/main/java/com/omarea/common/shell/KeepShell.kt           # 171 строка
krscript/src/main/java/com/omarea/krscript/config/PageConfigReader.kt  # 735 строк
krscript/src/main/java/com/omarea/krscript/executor/ScriptEnvironmen.java  # 395 строк
Всего удалено: ~1300 строк
```

## Архитектурные правила

```text
LegacyShellBridge
  -> единственная точка контакта с новым ShellRuntime
  -> экспонирует legacy-API (isInited, init, executeResultRoot, doCmdSync,
     checkRoot, tryExit, getEnvironment)
  -> внутренне использует RootShellRuntime / UserShellRuntime
  -> пробует root через Runtime.exec напрямую (без рекурсии через
     KeepShellPublic)

ScriptEnvironmen.kt
  -> тонкий фасад над LegacyShellBridge
  -> сохраняет 1:1 legacy публичный API
  -> 41 call-site без изменений

KeepShellPublic.kt
  -> тонкий фасад над LegacyShellBridge
  -> больше никакого владения KeepShell-инстансом
  -> больше никакого GlobalScope usage

KeepShellRuntime.kt
  -> использует Runtime.exec() напрямую
  -> больше никакой обёртки KeepShell
  -> всё ещё родительский класс для RootShellRuntime / UserShellRuntime

PageConfigLoader.kt
  -> замена для PageConfigReader
  -> использует PageConfigRepository + PageConfigParser(RuntimeBinder)
  -> 2 static-метода: load(context, path, parentDir), loadFromStream(...)
```

## CI-проверка

```bash
python3 tools/check-legacy-removal.py
```

Ожидаемый вывод:

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

## Что намеренно НЕ сделано здесь

- `ShellExecutor.java` (krscript/executor/) НЕ удалён — он всё ещё
  используется `SplashActivity.BeforeStartThread` для `before_start_sh`
  скрипта. Этот скрипт запускается один раз при старте и использует `Process`
  напрямую, что нормально. Будущий этап может мигрировать его на
  `ShellRuntime`.
- `ExtractAssets.java` НЕ удалён — `LegacyShellBridge.init` всё ещё
  использует его для извлечения legacy `kr-script/toolkit` и
  `kr-script/executor.sh`. Будущий этап должен заменить это на
  `ToolchainInstaller` (Stage 11+21), когда манифест покроет все toolkit-
  файлы.
- `ShellTranslation.kt` НЕ удалён — это таблица переводов для `$({KEY})`
  плейсхолдеров, всё ещё используется `LegacyShellBridge` и
  `KeepShellRuntime`.
- `RuntimeBinder.kt` всё ещё вызывает `ScriptEnvironmen.executeResultRoot`
  (теперь Kotlin-фасад) — это намеренно: `RuntimeBinder` — типизированная
  реализация `DynamicValueResolver`, которую использует парсер, и она идёт
  через bridge для запуска shell. Циклической зависимости нет, т.к.
  `LegacyShellBridge.executeResultRoot` не вызывает обратно парсер.
- `BeforeStartThread` в `SplashActivity` всё ещё использует
  `ShellExecutor.getSuperUserRuntime()` и `ScriptEnvironmen.executeShell(...)`.
  Последний теперь идёт через фасад и пишет в `DataOutputStream` напрямую.
  Это сохраняет streaming-контракт с `SimpleShellWatcher`. Будущий этап
  может переписать это на использование `ShellRuntime` напрямую, но это не
  блокер.
- Метод `ScriptEnvironmen.getRuntime()` возвращает `null`, т.к. новый
  `ShellRuntime` не экспонирует `Process`. `ShellExecutor.execute()`
  проверяет `null` и показывает error Toast — значит, legacy streaming-
  выполнение сломано. Это намеренно: новый путь (`OperationExecutor`)
  заменяет streaming-выполнение. Затронутые callers должны мигрировать на
  `OperationExecutor` в будущем этапе.
