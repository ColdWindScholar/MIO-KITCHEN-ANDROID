# Выбор ROM

MIO-KITCHEN понимает несколько типов firmware-файлов. Анализатор
(`FirmwareAnalyzerRegistry`) автоматически определяет тип по magic-байтам
файла — никогда по расширению.

## Поддерживаемые типы входных файлов

| Тип | Правило детекции | Что означает |
|------|------------------|--------------|
| `ZIP_OTA` | `.zip`, содержащий `META-INF/com/android/metadata` | Стандартный AOSP OTA-пакет |
| `PAYLOAD_BIN` | `.zip`, содержащий `payload.bin` | Android 10+ A/B OTA — payload.bin хранит разделы |
| `SUPER_IMAGE` | файл с именем `super.img` или zip с ним | Образ dynamic partitions |
| `BOOT_IMAGE` | файл `boot.img` с магией `ANDROID!` | Boot image (любая версия заголовка) |
| `VENDOR_BOOT_IMAGE` | `vendor_boot.img` с магией `ANDROID!` | Vendor boot (GKI-устройства) |
| `INIT_BOOT_IMAGE` | `init_boot.img` (Android 13+) | Выделенный generic ramdisk |
| `RECOVERY_IMAGE` | `recovery.img` с магией `ANDROID!` | Standalone recovery |
| `VBMETA_IMAGE` | `vbmeta*.img` с магией `AVB0` | Метаданные Android Verified Boot |
| `FILESYSTEM_IMAGE` | прочие `.img` с магией ext4 / EROFS / F2FS | Образ одного раздела |

## Как работает детекция

1. Приложение выбирает файл через SAF (`ACTION_OPEN_DOCUMENT`).
2. `AndroidStorageGateway` разрешает URI в shell-доступный workspace-путь.
3. `FirmwareAnalyzerRegistry.analyze(source)` запускает анализаторы по порядку:
   - `ZipFirmwareAnalyzer` (обрабатывает любой `.zip`)
   - `BootImageAnalyzer` (обрабатывает `boot*.img`)
   - `SuperImageAnalyzer` (обрабатывает `super.img`)
   - `VbmetaAnalyzer` (обрабатывает `vbmeta*.img`)
   - `FilesystemImageAnalyzer` (обрабатывает прочие `.img`)
4. Побеждает первый анализатор, у которого `supports(source)` вернул true.
5. Результат — `FirmwareProfile` с capabilities, разделами, предупреждениями.

## Что значит "версия Android"

Профиль может содержать подсказку `androidVersion`. Это версия Android
**внутри прошивки**, а НЕ версия Android, на которой запущено ваше устройство.
Это разные понятия — можно прошить Android 13 прошивку с Android 15
устройства, и наоборот. Формальное различие см. в
[developer docs](../dev/runtime-profiles.md).

## Неподдерживаемые форматы

Если анализатор вернул `FirmwarePackageType.UNKNOWN`, файл не распознан как
firmware. Частые причины:

- Файл — sparse image без sparse-заголовка (редко).
- Файл использует проприетарную boot-магию (некоторые MediaTek / Qualcomm).
- Файл — fastboot-bundle, а не firmware-образ.

В этих случаях приложение показывает предупреждение, но не падает — можно
попытаться запустить generic-операции, но toolchain-resolver скорее всего
сообщит об отсутствующих инструментах.

## Что дальше

- [Распаковка](unpack.md) — что делает каждый unpacker.
- [Доступ к хранилищу](storage-access.md) — как workspace обрабатывает ваш файл.
