# Этап 21 — Подключение ToolchainInstaller

> Дата: 2026-06-18
> Статус: завершено
> Связанные этапы: после Stage 20 (hybrid migration), перед Stage 22 (final integration)

---

Этап 11 ввёл `ToolchainInstaller` с SHA-256 проверкой, но оставил его
неподключённым — ожидалось, что вызывающая сторона вызовет его вручную перед
любой firmware-операцией. Этап 21 закрывает этот пробел, автоматически
запуская installer при создании `FirmwareOperationService`.

## Что делает этот этап

Обновляет `FirmwareOperationService.onCreate()`:

1. Загружает манифест toolchain из `assets/toolchain/manifest.json` через
   `ToolManifestLoader`.
2. Конструирует `ToolchainInstaller` с лямбдой `assetProvider`, открывающей
   потоки `assets/bin/<name>`.
3. Запускает installer против `<filesDir>/toolchain-bin/` в background-
   корутине (с `SupervisorJob`, чтобы установка не отменялась при завершении
   отдельной операции).
4. Сериализует параллельные установки через `Mutex` — множественные вызовы
   `startForegroundService` не могут запустить параллельные установки.
5. Сохраняет полученный `toolsDir` в
   `FirmwareOperationService.lastInstalledToolsDir`, чтобы `OperationExecutor`
   мог заполнить env-переменную `TOOLS_DIR`.
6. Обрабатывает все три варианта `ToolchainInstallResult`:
   - `Success` — установить `lastInstalledToolsDir`.
   - `Failed` — залогировать сообщение, продолжить работу сервиса
     (операция может сработать, если инструменты уже на диске).
   - `ChecksumMismatch` — залогировать несовпадение; плохой файл уже удалён
     installer'ом.

## Почему onCreate (не onStartCommand)

`onCreate` вызывается ровно один раз за lifetime сервиса. `onStartCommand`
может срабатывать несколько раз (по одному на каждый `startForegroundService(...)`
вызов). Помещение установки в `onCreate` гарантирует:

- Установка запускается ровно один раз за process-life сервиса.
- Последующие вызовы `startForegroundService` не перезапускают установку
  (они идут через `onStartCommand`, который просто строит notification и
  возвращает `START_NOT_STICKY`).

Сама установка **идемпотентна** — `ToolchainInstaller` пропускает уже
установленные файлы после SHA-256 проверки (когда checksum объявлена).
Манифест сейчас поставляется с `sha256=null` для всех записей, поэтому
проверка выключена и установка — это быстрый existence-check.

## Новое поведение

```text
FirmwareOperationService.onCreate()
  -> ensureToolchainInstalled()
       -> serviceScope.launch (SupervisorJob + Dispatchers.IO)
            -> installMutex.withLock
                 -> runInstallerIfNeeded()
                      -> loadManifest() из assets/toolchain/manifest.json
                      -> ToolchainInstaller(manifest, assetProvider).install(toolsDir)
                      -> при Success: lastInstalledToolsDir = toolsDir
                      -> при Failed/ChecksumMismatch: залогировать и продолжить
```

## CI-проверка

```bash
python3 tools/check-hybrid-migration.py
```

Проверка убеждается, что:

- `FirmwareOperationService.onCreate` переопределён и вызывает
  `ensureToolchainInstalled`.
- Обрабатываются все три варианта `ToolchainInstallResult`.
- Экспонируется companion-поле `lastInstalledToolsDir`.
- Используются `SupervisorJob` + `installMutex` для безопасности при
  параллельных запусках сервиса.

## Что намеренно НЕ сделано здесь

- `OperationExecutor` пока НЕ читает `FirmwareOperationService.lastInstalledToolsDir`
  для заполнения env-переменной `TOOLS_DIR`. Executor принимает tools-dir
  через `toolsDirProvider`, поэтому подключение — это one-liner в вызывающей
  активности — отложено на будущий этап.
- Манифест теперь поставляется с реальными SHA-256 для каждого shipped-
  бинарника (генерируются `tools/compute-tool-hashes.py`). Две записи
  (`lpunpack`, `simg2img`) сохраняют `sha256=null`, т.к. эти бинарники не
  поставляются в `assets/bin/` — они объявлены в манифесте для полноты.
- Установка запускается с `verifyChecksums=false`, т.к. legacy-путь
  `LegacyShellBridge.installToolkit` не требует проверки checksum (ему нужны
  только бинарники на диске). `FirmwareOperationService` также использует
  `verifyChecksums=false` по той же причине. Когда `ToolchainInstaller`
  станет единственным путём установки, флаг можно переключить на `true`.
- Установка НЕ блокирует `onStartCommand` — первая операция может
  стартовать до завершения установки. Это приемлемо, т.к. legacy-путь
  `LegacyShellBridge.installToolkit` (Stage 23) извлекает бинарники для
  legacy-операций; installer только заполняет новый `toolsDir`
  для использования `OperationExecutor`.
