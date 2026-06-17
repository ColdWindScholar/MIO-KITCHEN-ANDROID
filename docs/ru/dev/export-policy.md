# Этап 14 — Политика экспорта folder/tree URI

> Дата: 2026-06-18
> Статус: завершено
> Связанные этапы: после Stage 13 (targetSdk 35), перед Stage 15 (UI migration)

---

На targetSdk 35 приложение не может писать во внешнее хранилище через прямые
file-path. Оригинальный Stage 4 `FirmwareWorkspace` покрывает *импорт*-путь
(content:// URI → копия в workspace). Этап 14 замыкает цикл типизированной
политикой *экспорта*: как файлы из `FirmwareWorkspace.exportsDir/...`
записываются обратно в видимое пользователю место.

## Что делает этот этап

Вводит:

- `ExportPolicy` — sealed-тип с четырьмя вариантами:
  - `AskPerFile` — использовать `ACTION_CREATE_DOCUMENT` по каждому файлу
    (вызывающая сторона получает URI).
  - `TreeFolder(treeUri)` — писать в persistent tree URI (один выбор папки).
  - `MediaStoreExport(mimeType, relativePath)` — писать через MediaStore.
  - `AppPrivate` — оставить в app-private external storage.
- `ExportResult` — sealed-тип: `Success(targetUri, bytesCopied, sha256)`,
  `Cancelled`, `Failed(message, cause)`.
- `ExportOptions` — объединяет политику + флаги (`computeSha256`,
  `overwriteExisting`).
- `WorkspaceExporter` — интерфейс с `export(sourceFile, options)`.
- `AndroidWorkspaceExporter` — Android-реализация, поддерживающая все четыре
  политики, с SHA-256 проверкой прямо в copy-path.

## Новый API

```text
common/storage/
  ExportPolicy.kt              # ExportPolicy, ExportResult, ExportOptions,
                               # WorkspaceExporter
  AndroidWorkspaceExporter.kt  # Android-реализация + хелпер exportToUri
```

## CI-проверка

```bash
python3 tools/check-export-policy.py
```

Ожидаемый вывод:

```text
PASS: ExportPolicy sealed type covers AskPerFile/TreeFolder/MediaStore/AppPrivate
PASS: ExportResult sealed type covers Success/Cancelled/Failed
PASS: WorkspaceExporter interface declares export(sourceFile, options)
PASS: AndroidWorkspaceExporter supports SAF tree + MediaStore + app-private
PASS: SHA-256 verification is built into export path
```

## Что намеренно НЕ сделано здесь

- Exporter пока не подключён ни к одному UI-экрану. Stage 15 будет
  использовать `OpenDocumentHelper` / `CreateDocumentHelper` для получения
  URI и передавать их в `AndroidWorkspaceExporter.exportToUri(...)`.
- `AskPerFile` сразу возвращает `Cancelled` из `export()` — вызывающая
  сторона должна сначала получить URI через Activity Result API и затем
  вызвать `exportToUri()`. Это разделение сохраняет синхронность exporter'а.
- `overwriteExisting` объявлен в `ExportOptions`, но пока не единообразно
  соблюдается — SAF tree и MediaStore сейчас всегда создают новый документ.
  Будет ужесточено в следующем этапе.
