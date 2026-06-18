# Этап 13 — Миграция targetSdk 35

> Дата: 2026-06-18
> Статус: завершено
> Связанные этапы: после Stage 12 (OperationExecutor), перед Stage 14 (Folder/tree URI export policy)

---

Этап 3 намеренно оставил `targetSdkVersion = 28`, чтобы сохранить legacy-
поведение storage и root, пока строился layer storage/workspace. Теперь, когда
этапы 4, 10, 11, 12 на месте — `StorageGateway` + `FirmwareWorkspace` +
`OperationExecutor` покрывают весь runtime-path — больше нет причин
оставаться на старом target. Этап 13 поднимает `targetSdkVersion` до 35 и
добавляет runtime-permission и foreground-service инфраструктуру, требуемую
современным Android.

## Что делает этот этап

- Поднимает `targetSdkVersion` с 28 до 35 в `build.gradle`.
- Ограничивает legacy storage-разрешения:
  - `READ_EXTERNAL_STORAGE` → `maxSdkVersion=32` (Android 12 и ниже).
  - `WRITE_EXTERNAL_STORAGE` → `maxSdkVersion=29` (Android 9 и ниже).
- Объявляет `POST_NOTIFICATIONS` для Android 13+.
- Объявляет `FOREGROUND_SERVICE` и `FOREGROUND_SERVICE_DATA_SYNC` для Android 14+.
- Устанавливает `requestLegacyExternalStorage="false"` для соблюдения scoped storage.
- Добавляет foreground-service `FirmwareOperationService` с типом `dataSync`.
- Добавляет `RuntimePermissionHelper` для абстракции permission-flow под targetSdk 35.

## Новые компоненты

```text
pio/src/main/java/com/mio/kitchen/
  FirmwareOperationService.kt       # foreground service с типом dataSync

pio/src/main/java/com/mio/kitchen/ui/modern/
  RuntimePermissionHelper.kt         # абстрагирует permission-flow по версиям Android
```

## Permission-flow

```text
RuntimePermissionHelper.requiredPermissions()
  -> POST_NOTIFICATIONS  на Android 13+
  -> READ_EXTERNAL_STORAGE на Android ≤ 12 (capped maxSdk=32 в манифесте)

RuntimePermissionHelper.areAllGranted(context)
  -> true если все требуемые разрешения выданы

RuntimePermissionHelper.requestMissing(activity, requestCode)
  -> запрашивает недостающие разрешения; возвращает true если запрос отправлен

RuntimePermissionHelper.areAllGranted(grantResults: IntArray)
  -> проверить результат onRequestPermissionsResult
```

## Foreground-service flow

```text
FirmwareOperationService
  -> onStartCommand строит low-priority notification
  -> на Android 14+ (UPSIDE_DOWN_CAKE): startForeground с FOREGROUND_SERVICE_TYPE_DATA_SYNC
  -> на старом Android: startForeground без типа
  -> возвращает START_NOT_STICKY (operation-driven, не восстанавливается после краша)
```

Сервис НЕ запускает саму firmware-операцию — он только держит foreground-state.
Сама операция запускается вызывающей стороной через `OperationExecutor`
(Stage 12) в корутине.

## CI-проверка

```bash
python3 tools/check-targetsdk-35.py
```

Ожидаемый вывод:

```text
PASS: targetSdkVersion raised from 28 to 35
PASS: legacy storage permissions capped (READ maxSdk=32, WRITE maxSdk=29)
PASS: POST_NOTIFICATIONS runtime permission declared for Android 13+
PASS: FOREGROUND_SERVICE_DATA_SYNC declared for Android 14+
PASS: requestLegacyExternalStorage is false (scoped storage enforced)
PASS: FirmwareOperationService uses dataSync foregroundServiceType
PASS: RuntimePermissionHelper abstracts targetSdk-35 permissions
```

## Что намеренно НЕ сделано здесь

- Существующие активности (`MainActivity`, `ActionPage`, `ActivityFileSelector`,
  `SplashActivity`) пока НЕ мигрированы, чтобы вызывать
  `RuntimePermissionHelper` перед запуском операций. Миграция — часть
  Stage 15 (UI migration to AppRuntimeProfile).
- `FirmwareOperationService` объявлен, но пока не запускается ни одной
  активностью. Stage 15 подключит его через `Context.startForegroundService(...)`.
- Notification использует generic download-иконку и имя приложения как title;
  будущий этап добавит локализованные строки и progress bar.
- All Files Access (`MANAGE_EXTERNAL_STORAGE`) намеренно НЕ запрашивается —
  приложение использует SAF + workspace, что его не требует.
