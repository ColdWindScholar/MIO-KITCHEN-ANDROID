# Доступ к хранилищу

MIO-KITCHEN работает на Android 5.0 (API 21) — Android 15+ (API 35+) с одним
APK. Доступ к хранилищу значительно различается между этими версиями, поэтому
приложение использует типизированный layer `StorageGateway` +
`FirmwareWorkspace`, чтобы скрыть различия.

## Как приложение читает ваши файлы

Когда вы выбираете файл через системный выбор файлов:

1. Picker возвращает `content://` URI (на Android 10+) или `file://`-путь
   (на старом Android).
2. `AndroidStorageGateway.resolveUriForShell(uri, options)` решает:
   - `file://` → использовать путь напрямую (редко на современном Android).
   - `content://` → скопировать файл в `FirmwareWorkspace.importsDir/`,
     посчитать SHA-256 во время копирования и вернуть workspace-путь.
3. Shell-инструменты получают workspace-путь, который всегда является обычным
   file-путем внутри external-files-dir приложения.

Это значит, **shell-инструменты никогда не видят `content://` URI**. Они
всегда получают реальный filesystem-путь.

## Зачем нужен workspace

Shell-инструменты (особенно root-shell-инструменты) не могут читать
`content://` URI напрямую. Workspace — мост между границей SAF / scoped
storage:

```text
пользователь выбирает файл
  → SAF возвращает content:// URI
  → AndroidStorageGateway копирует в FirmwareWorkspace/imports/<unique-name>
  → shell-инструменты получают /storage/emulated/0/Android/data/com.mio.kitchen/files/firmware-workspace/imports/<unique-name>
  → результат экспортируется обратно через ExportPolicy
```

Преимущества:
- Одинаковый shell-путь на любой версии Android.
- Меньше зависимости от колонки `_data` MediaProvider (сломана на Android 11+).
- Проще считать checksums, проще откатить, проще тестировать.

## Расположение workspace

Workspace лежит в app-specific external storage:

```text
/storage/emulated/0/Android/data/com.mio.kitchen/files/firmware-workspace/
  imports/   <- файлы, скопированные из picks пользователя
  exports/   <- файлы, созданные операциями, готовые к экспорту
```

Эта директория:
- Записываемая без `WRITE_EXTERNAL_STORAGE` (она app-specific).
- Shell-доступная (rooted или non-rooted).
- Автоматически очищается системой при удалении приложения.

`FirmwareWorkspace.clearOldImports()` удаляет import-файлы старше 7 дней при
каждом старте приложения, чтобы workspace не рос бесконтрольно.

## Legacy direct-path режим

Некоторым продвинутым пользователям нравится прямой path-доступ (без копии
в workspace). Это opt-in через `StorageResolveOptions.preferLegacyDirectPath = true`.
Когда включено:
- `file://` URI используются напрямую без копирования.
- `content://` URI, которые разрешаются в известный `_data`-путь,
  используются напрямую.

Этот режим **не рекомендуется** на Android 11+, потому что:
- Колонка `_data` больше не запрашивается у большинства providers.
- Прямые пути вне app-specific dir требуют `MANAGE_EXTERNAL_STORAGE`, которое
  MIO-KITCHEN не запрашивает.
- Это нарушает scoped storage enforcement.

## Политики экспорта

После операции результат лежит в `exports/`. Чтобы сделать его видимым
пользователю, `AndroidWorkspaceExporter` поддерживает четыре политики:

| Политика | Когда использовать |
|----------|---------------------|
| `AskPerFile` | Выбирать назначение для каждого файла (максимум контроля, максимум кликов) |
| `TreeFolder` | Выбрать папку один раз, все экспорты туда (рекомендуется) |
| `MediaStoreExport` | Сохранить в `Download/MIO-KITCHEN/` (видно в Загрузках) |
| `AppPrivate` | Оставить внутри приложения (без экспорта; пользователь должен использовать файловый менеджер) |

Все экспорты по умолчанию считают SHA-256, чтобы можно было проверить
целостность.

## Что дальше

- [Быстрый старт](quick-start.md) — базовый workflow.
- [Root-режим](root-mode.md) — что меняется при наличии root.
- [Устранение неполадок](troubleshooting.md) — типичные storage-ошибки.
