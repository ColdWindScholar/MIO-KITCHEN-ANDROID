# Этап 7 — Анализатор прошивок

> Дата: 2026-06-17
> Статус: завершено
> Связанные этапы: после Stage 6 (shell runtime), перед Stage 8 (UI modernization)

---


До этого этапа в проекте не было типизированного описания "что это за
прошивка". Shell-скрипты в `tool.sh` делали ad-hoc-детекцию (`file`,
`hexdump`, `magiskboot -l`, …) каждый раз, а результаты оставались в строках
stdout. Из-за этого было невозможно:

- показать пользователю структурированную сводку по прошивке до запуска действий;
- решить, какие инструменты нужны (lpunpack? mkfs.erofs? avbtool?);
- выдать capability-driven предупреждения (16 KB page size, AVB chaining, …);
- тестировать детекцию прошивок в JVM-тестах без устройства.

Этап 7 вводит типизированный API `FirmwareAnalyzer`, который строит
`FirmwareProfile` — data-класс с capabilities, разделами, сжатием,
подсказкой версии Android и предупреждениями.

### Новый API

```text
common/firmware/
  FirmwareProfile.kt              # FirmwareSource, FirmwarePackageType,
                                  # AndroidVersionHint, CompressionType,
                                  # PartitionInfo, FirmwareWarning,
                                  # FirmwareCapabilities, FirmwareProfile
  FirmwareAnalyzer.kt             # интерфейс + CompositeFirmwareAnalyzer
                                  # + FirmwareAnalysisException
  ZipFirmwareAnalyzer.kt          # обходит OTA zip (payload.bin, super.img,
                                  # boot.img, vbmeta.img, META-INF/metadata, …)
  ImageAnalyzers.kt               # BootImageAnalyzer, SuperImageAnalyzer,
                                  # VbmetaAnalyzer, FilesystemImageAnalyzer
  FirmwareAnalyzerRegistry.kt     # компонует все анализаторы в правильном порядке
```

### Правила детекции

```text
имя файла заканчивается на .zip:
  -> ZipFirmwareAnalyzer
     найден payload.bin        -> packageType = PAYLOAD_BIN, usesAB = true,
                                  androidVersion >= 10
     найден super.img          -> packageType = SUPER_IMAGE, dynamic partitions
     найдены boot.img / vendor_boot.img / init_boot.img
                                -> boot images; init_boot намекает на Android 13+
     найдены vbmeta*.img       -> usesAvb = true
     найдены system.img / vendor.img / ...
                                -> ext4 filesystem image
     META-INF/com/android/metadata
                                -> пытается определить A/B и версию Android

имя файла == boot.img / vendor_boot.img / init_boot.img / recovery.img:
  -> BootImageAnalyzer
     читает первые 4 КБ
     магия ANDROID! по смещению 0, версия заголовка по смещению 40
     предупреждение, если версия заголовка 4 (GKI совместимость)
     предупреждение, если магии нет (проприетарный заголовок)

имя файла == super.img:
  -> SuperImageAnalyzer
     читает первые 4 байта
     0x3aff26ed -> sparse super image
     иначе      -> raw super image

имя файла начинается с vbmeta:
  -> VbmetaAnalyzer
     читает первые 4 байта
     магия AVB0 -> usesAvb = true
     иначе      -> предупреждение "vbmeta-bad-magic"

остальные .img-файлы:
  -> FilesystemImageAnalyzer
     читает первые 2 КБ
     0xef53 по смещению 1080 -> ext4
     0xe0f5e1e2 по смещению 1024 -> EROFS
     0xf2f52010 по смещению 1024 -> F2FS
     иначе -> предупреждение "unknown-filesystem"
```

### Архитектурные правила

```text
FirmwareAnalyzer
  -> fun supports(source): Boolean   -- дешёвая проверка (имя/магия)
  -> fun analyze(source): FirmwareProfile
  -> НЕ запускает shell
  -> НЕ зависит от android.app.* / android.widget.*
  -> безопасен для любого потока
  -> все анализаторы тестируются в чистой JVM
```

### CI-проверка

```bash
python3 tools/check-firmware-analyzer.py
```

Ожидаемый вывод:

```text
PASS: Firmware analyzer typed API is in place
PASS: FirmwareProfile/FirmwareCapabilities/PartitionInfo are declared
PASS: ZipFirmwareAnalyzer walks zip contents
PASS: BootImageAnalyzer detects ANDROID! magic and header version
PASS: SuperImageAnalyzer detects sparse format
PASS: VbmetaAnalyzer detects AVB0 magic
PASS: FilesystemImageAnalyzer detects ext4/EROFS/F2FS magic
PASS: FirmwareAnalyzerRegistry composes all analyzers
PASS: Firmware analyzer JVM unit tests are present
PASS: analyzers are pure — no shell, no Android imports
```

### Что намеренно НЕ сделано здесь

- Разбор манифеста `payload.bin` (update_engine protobuf) — отложен на поздний
  этап; сейчас мы только фиксируем его наличие.
- Разбор метаданных `lpunpack` для super.img — мы отличаем sparse от raw, но
  не перечисляем logical partitions.
- Разбор AVB chain descriptor — мы только проверяем магию AVB0.
- Интеграция с `ToolchainResolver` выполнена в Stage 10
  (`CapabilityBasedToolchainResolver` потребляет `FirmwareProfile.capabilities`
  для построения `ToolchainPlan`). Будущая работа — разбор internals
  payload.bin / super.img, перечисленных выше.
