# Этап 20 — Гибридный слой миграции

> Дата: 2026-06-18
> Статус: завершено
> Связанные этапы: после Stage 19 (user docs), перед Stage 21 (ToolchainInstaller wiring)

---

Оригинальный пункт дорожной карты #1 ("перевести существующие активности с
legacy PageConfigReader на новый AppRuntimeProfileResolver") — крупнейший
UI-rewrite в проекте: четыре активности, ~1200 строк UI-кода, которые зависят
от `PageConfigReader`/`ScriptEnvironmen`/`KrScriptConfig`/`KeepShell`.
Полная замена рискует сломать визуальное поведение, на которое полагаются
существующие пользователи.

Этап 20 идёт прагматичным **гибридным** путём: вместо замены legacy-пути мы
добавляем параллельный singleton **`AppRuntimeStore`**, который зеркалирует
legacy-состояние в новый data-класс `AppRuntimeProfile`. Legacy-активности
обновляют store в нужных lifecycle-точках; новые активности (читай:
`FirmwareAnalysisActivity`) читают store напрямую. Это даёт:

- Единый источник истины для device/firmware/operation state, наблюдаемый
  через `StateFlow`.
- Новый код можно писать против `AppRuntimeProfile`, не дожидаясь
  переписывания legacy-активностей.
- Legacy-активности продолжают работать без изменений — нет риска регрессий.
- Постепенная миграция: каждая активность позже может переносить больше
  логики на новый путь, не ломая остальные.

## Что делает этот этап

Добавляет `AppRuntimeStore` — singleton-object на базе
`MutableStateFlow<AppRuntimeProfile?>`. Экспонирует:

- `profile: StateFlow<AppRuntimeProfile?>` — наблюдаемый текущий профиль.
- `init(rootAvailable)` — инициализирует device-часть через
  `DeviceProfileProvider`. Безопасно вызывать повторно.
- `updateRootStatus(hasRoot)` — обновляет поле `hasRoot` устройства после
  завершения `CheckRootStatus`.
- `setFirmware(shellPath)` — запускает `FirmwareAnalyzerRegistry.analyze(...)`
  в background-потоке и обновляет firmware-часть.
- `resetFirmware()` — сбрасывает firmware + активную операцию.
- `device: DeviceProfile?` / `firmware: FirmwareProfile?` — convenience-
  аксессоры.

Затем подключает его во все существующие активности:

### SplashActivity

- Вызывает `AppRuntimeStore.init(rootAvailable = null)` в начале `onCreate`.
- Вызывает `AppRuntimeStore.updateRootStatus(hasRoot = true)` после успешного
  `CheckRootStatus`.
- Legacy-путь (`ScriptEnvironmen.isInited()`, `CheckRootStatus`,
  `KrScriptConfig`, `BeforeStartThread`) **сохранён без изменений**.

### MainActivity

- Вызывает `AppRuntimeStore.init()` в начале `onCreate`.
- Заменяет устаревший `ActivityCompat.requestPermissions(...,
  READ_EXTERNAL_STORAGE, WRITE_EXTERNAL_STORAGE, 111)` на
  `RuntimePermissionHelper.requestMissing(this, 111)`. Это обязательно на
  targetSdk 35 (Stage 13): legacy-разрешения capped на API 32, поэтому их
  запрос на Android 13+ молча проваливается.
- Legacy-логика `KrScriptConfig`, `PageConfigReader`, `ActionListFragment`
  **сохранена без изменений**.

### ActivityFileSelector

- `onRequestPermissionsResult` теперь делегирует в
  `RuntimePermissionHelper.areAllGranted(grantResults)`.
- `requestPermissions()` теперь вызывает `RuntimePermissionHelper.requestMissing`.
- `loadData()` сохраняет legacy-file-list flow, когда
  `READ_EXTERNAL_STORAGE`+`WRITE_EXTERNAL_STORAGE` реально выданы
  (Android ≤ 12). На Android 13+, где они capped, показывает Toast,
  направляющий пользователя в новый SAF-based `FirmwareAnalysisActivity`.

### ActionPage

- После успешного `storageGateway.resolveUriForShell(...)` запускает
  background-поток, который вызывает `AppRuntimeStore.setFirmware(shellPath)`.
- Legacy-callback `fileSelectedInterface?.onFileSelected(shellPath)`
  вызывается ровно как раньше — обновление AppRuntimeStore best-effort и
  никогда не блокирует UI.
- Legacy-пути `AndroidStorageGateway`, `ScriptEnvironmen`,
  `KrScriptActionHandler` **сохранены без изменений**.

## Новые компоненты

```text
pio/src/main/java/com/mio/kitchen/ui/modern/
  AppRuntimeStore.kt          # singleton-мост (StateFlow<AppRuntimeProfile?>)
```

## Изменённые файлы

```text
pio/src/main/java/com/mio/kitchen/
  SplashActivity.kt           # +AppRuntimeStore.init +updateRootStatus
  MainActivity.kt             # +AppRuntimeStore.init +RuntimePermissionHelper
  ActivityFileSelector.kt     # +RuntimePermissionHelper (legacy storage request удалён)
  ActionPage.kt               # +AppRuntimeStore.setFirmware после URI resolution
```

## CI-проверка

```bash
python3 tools/check-hybrid-migration.py
```

Ожидаемый вывод:

```text
PASS: AppRuntimeStore singleton bridge is in place
PASS: SplashActivity calls AppRuntimeStore.init + updateRootStatus (legacy path preserved)
PASS: MainActivity calls AppRuntimeStore.init + RuntimePermissionHelper (legacy storage request removed)
PASS: ActivityFileSelector uses RuntimePermissionHelper (legacy storage request removed)
PASS: ActionPage calls AppRuntimeStore.setFirmware after URI resolution (legacy gateway preserved)
PASS: FirmwareOperationService.onCreate invokes ToolchainInstaller
PASS: FirmwareOperationService handles all ToolchainInstallResult variants
PASS: FirmwareOperationService exposes lastInstalledToolsDir + uses SupervisorJob + mutex
```

## Что намеренно НЕ сделано здесь

- Legacy-классы `PageConfigReader`/`ScriptEnvironmen`/`KeepShell`/`KrScriptConfig`
  НЕ удалены. Они всё ещё питают реальный KrScript UI-rendering и shell-
  выполнение. Удаление потребовало бы миграции каждого call-site
  `ActionListFragment`/`PageLayoutRender` — гораздо больший объём работы,
  отложенный до того, как новый путь докажет себя в продакшене.
- Цепочка `KrScriptConfig`/`PageConfigReader`/`ActionListFragment` в
  `MainActivity` НЕ затронута. Store обновляется, но реальный UI-rendering
  идёт через legacy-путь.
- `ActionPage` НЕ использует `OperationExecutor` для запуска операций — он
  всё ещё использует legacy-callback `KrScriptActionHandler`. Подключение
  `OperationExecutor` — будущий этап.
- `AppRuntimeStore` — singleton. Это приемлемо, т.к. device-profile —
  process-global, но это усложняет тестирование. Будущий этап может
  инжектить store через `ViewModel`-scoped контейнер.
