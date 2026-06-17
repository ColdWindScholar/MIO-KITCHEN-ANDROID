# Storage / Workspace compatibility architecture

## Цель этапа

Этот этап готовит проект к будущему переходу `targetSdk` с 28 на современный уровень без разделения приложения на разные APK.

Пользовательская модель остаётся прежней:

```text
одно приложение
один UI
один APK/AAB
можно выбирать прошивки разных Android-версий
```

Внутри приложения добавлен слой, который переводит разные источники файлов в shell-доступные пути.

## Почему нужен workspace

KrScript и firmware-инструменты работают с обычными file path:

```text
/storage/emulated/0/.../rom.zip
/storage/emulated/0/Android/data/com.mio.kitchen/files/firmware-workspace/imports/rom.zip
```

Но современный Android file picker часто возвращает URI:

```text
content://...
```

Shell и bundled tools не умеют напрямую читать `content://`. Поэтому приложение должно:

```text
пользователь выбирает файл через SAF
  -> приложение получает content:// URI
  -> StorageGateway копирует файл в FirmwareWorkspace
  -> KrScript/shell получает обычный shellPath
```

## Новые компоненты

```text
common/storage/SafeFileName.kt
  безопасное имя файла для workspace

common/storage/FirmwareWorkspace.kt
  app-specific workspace:
    firmware-workspace/imports
    firmware-workspace/exports

common/storage/StorageGateway.kt
  интерфейс подготовки URI/path к shell-операциям

common/storage/AndroidStorageGateway.kt
  Android-реализация:
    file:// -> DirectFile
    content:// -> WorkspaceCopy
    legacy direct path -> только opt-in

common/storage/StorageResolveOptions.kt
  политика resolution:
    preferLegacyDirectPath=false
    copyContentUriToWorkspace=true
    computeSha256=true

common/storage/StorageResolveResult.kt
  typed result:
    Resolved(shellPath, sourceKind, copiedBytes, sha256)
    Failed(message, cause)
```

## Политика по умолчанию

```text
content:// URI:
  не передаётся напрямую в shell
  копируется в FirmwareWorkspace
  во время копирования считается SHA-256

file:// URI или обычный path:
  возвращается как DirectFile

legacy direct path:
  оставлен внутри AndroidStorageGateway
  используется только при явном preferLegacyDirectPath=true
```

Это сохраняет совместимость с текущими root/direct-path сценариями и одновременно готовит код к scoped-storage поведению новых Android.

## Изменения в ActionPage

`ActionPage` больше не вызывает `FilePathResolver` напрямую для URI из системного picker.

Теперь flow такой:

```text
ACTION_OPEN_DOCUMENT
  -> URI grant
  -> AndroidStorageGateway.persistReadPermission()
  -> AndroidStorageGateway.resolveUriForShell()
  -> workspace copy
  -> fileSelectedInterface.onFileSelected(shellPath)
```

Подготовка workspace-копии выполняется не в прямой UI-ветке, а через отдельный поток с progress dialog.

## Что пока осталось legacy

Выбор папки пока остаётся через встроенный `ActivityFileSelector` и прямой path. Это сделано осознанно: многие firmware-операции ожидают настоящую директорию, а не tree URI.

Следующий storage-этап должен решить:

```text
ACTION_OPEN_DOCUMENT_TREE
  -> workspace folder mapping
  -> export/copy-back policy
  -> root/direct-path fallback
```

## Проверки

```bash
python3 tools/check-storage-workspace.py
```

Проверка контролирует:

- наличие `StorageGateway` / `AndroidStorageGateway` / `FirmwareWorkspace`;
- что `content://` копируется в workspace;
- что legacy direct path является opt-in;
- что `ActionPage` использует SAF `ACTION_OPEN_DOCUMENT`;
- что UI получает shell-доступный path через `StorageResolveResult`;
- наличие RU/EN документации и CI gate.

## Unit tests

Добавлен быстрый JVM-тест:

```text
common/src/test/java/com/omarea/common/storage/SafeFileNameTest.kt
```

Он проверяет sanitizing имён файлов, удаление path-сегментов, default-name поведение и сохранение расширения при обрезке длинных имён.

## Что это даёт для будущего targetSdk

Этот этап ещё не поднимает `targetSdk` до 35. Он создаёт архитектурную точку, через которую можно будет менять storage policy без переписывания KrScript и UI.

Дальше можно безопаснее двигаться к:

```text
StorageGateway adapters
Workspace export policy
folder/tree URI support
permission matrix tests
после этого — targetSdk 35 migration
```
