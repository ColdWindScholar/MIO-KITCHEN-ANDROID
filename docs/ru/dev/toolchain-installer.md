# Этап 11 — ToolchainInstaller

> Дата: 2026-06-18
> Статус: завершено
> Связанные этапы: после Stage 10 (runtime profiles), перед Stage 12 (OperationExecutor)

---

Манифест Stage 10 в `assets/toolchain/manifest.json` объявляет, какие нативные
инструменты поставляются с приложением, включая SHA-256 контрольные суммы.
Этап 11 замыкает цикл, вводя `ToolchainInstaller`, который реально извлекает
эти бинарники в доступную для записи директорию и проверяет их по манифесту.

## Что делает этот этап

До этого этапа не было типизированного способа:

- проверить, что инструмент, нужный плану операции, действительно есть на диске;
- убедиться, что bundled-бинарник не повреждён и не подменён;
- переизвлечь отсутствующий или повреждённый инструмент в рантайме;
- заполнить `ToolchainProfile.toolsDir` и `verifiedChecksums` реальными значениями.

Этап 11 вводит:

- `ToolchainInstallResult` — sealed-тип с вариантами `Success`, `Failed` и
  `ChecksumMismatch`.
- `ToolchainInstaller` — извлекает бинарники через `assetProvider`, проверяет
  SHA-256 (если манифест объявил сумму), выставляет executable-бит и
  сообщает per-tool успех/пропуск.

## Новый API

```text
common/toolchain/
  ToolchainInstaller.kt     # sealed ToolchainInstallResult + класс ToolchainInstaller

common/src/test/java/.../toolchain/
  ToolchainInstallerTest.kt  # 9 JVM-тестов с реальными временными файлами
```

## Контракт

```text
ToolchainInstaller
  -> install(toolsDir, verifyChecksums, overwrite): ToolchainInstallResult
  -> НЕ запускает shell
  -> НЕ зависит от Android Context
  -> использует лямбду assetProvider (тестируемо в JVM)
  -> использует лямбду sha256Verifier (переопределяемо в тестах)
  -> выставляет executable-бит на установленных файлах
  -> удаляет повреждённые файлы при ChecksumMismatch

ToolchainInstallResult
  -> Success(toolsDir, installedTools, skippedTools, verifiedChecksums)
  -> Failed(message, cause, partialDir)
  -> ChecksumMismatch(toolName, expected, actual)
```

## Алгоритм

1. Создать `toolsDir`, если не существует.
2. Для каждого `ToolDescriptor` в манифесте:
   - Если `toolsDir/<name>` уже существует и `overwrite=false`:
     - Проверить SHA-256, если объявлен (вернуть `ChecksumMismatch` при несовпадении).
     - Пометить как установленный; продолжить.
   - Открыть поток из `assetProvider(name)`.
   - Если поток `null`, пропустить и записать в `skippedTools`.
   - Скопировать поток в `toolsDir/<name>`.
   - Если `verifyChecksums=true` и `sha256` объявлен, проверить.
     - При несовпадении удалить файл и вернуть `ChecksumMismatch`.
   - Выставить executable-бит (`setExecutable(true, true)`).
3. Вернуть `Success` со списками установленного/пропущенного.

## CI-проверка

```bash
python3 tools/check-toolchain-installer.py
```

Ожидаемый вывод:

```text
PASS: ToolchainInstaller with SHA-256 verification is in place
PASS: ToolchainInstallResult sealed type covers Success/Failed/ChecksumMismatch
PASS: ToolchainInstaller tests cover install/skip/overwrite/checksum
PASS: ToolchainInstaller is pure (no shell/UI imports)
```

## Что намеренно НЕ сделано здесь

- Installer подключён к `FirmwareOperationService.onCreate` (Stage 21) и к
  `LegacyShellBridge.init` (Stage 23, `installToolkit`). Оба call-site
  извлекают toolkit из `assets/toolchain/manifest.json` в `<filesDir>/bin/`.
- Лямбда `assetProvider` — это test seam; в production она backed-на
  `AssetManager.open("bin/<name>")` из модуля `pio` — подключена в
  `LegacyShellBridge.installToolkit` и `FirmwareOperationService.openToolStream`.
- Installer не делает ABI-фильтрацию в момент извлечения — это задача
  resolver'а. Если манифест объявляет несколько ABI для одного имени
  инструмента, ожидается, что вызывающая сторона предоставит правильный
  бинарник через `assetProvider`.
- Манифест поставляется с реальными SHA-256 для каждого shipped-бинарника
  (генерируются `tools/compute-tool-hashes.py`). Две записи (`lpunpack`,
  `simg2img`) сохраняют `sha256=null`, т.к. бинарники не поставляются.
