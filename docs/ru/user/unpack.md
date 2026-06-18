# Распаковка

MIO-KITCHEN умеет распаковывать все распространённые типы firmware-образов.
Каждый unpacker — это read-only, не-деструктивная операция, которая пишет
результат в workspace приложения.

## UNPACK_ROM

Вход: OTA `.zip` с `payload.bin` и/или отдельными `.img`-файлами.

Executor выставляет:
- `OPERATION=UNPACK_ROM`
- `FIRMWARE_PATH=<workspace-путь к OTA zip>`
- `HAS_PAYLOAD_BIN`, `HAS_SUPER_IMAGE`, `HAS_BOOT_IMAGE` и т.д.

Обязательные инструменты (по `CapabilityBasedToolchainResolver`):
- `busybox` (всегда)
- `payload-dumper` (когда `HAS_PAYLOAD_BIN`)
- `lpunpack` (когда `HAS_SUPER_IMAGE`)
- `magiskboot` (когда `HAS_BOOT_IMAGE`)
- `avbtool` (опционально, когда `USES_AVB`)

## UNPACK_SUPER

Вход: `super.img` (raw или sparse).

Обязательные инструменты: `lpunpack`, `simg2img` (если sparse).

Результат: по файлу на каждый logical partition (`system.img`, `vendor.img`,
`product.img`, ...).

## UNPACK_PAYLOAD_BIN

Вход: `payload.bin`, извлечённый из OTA.

Обязательные инструменты: `payload-dumper`.

Результат: разделы, перечисленные в манифесте payload.

## UNPACK_BOOT_IMAGE / UNPACK_VENDOR_BOOT_IMAGE / UNPACK_INIT_BOOT_IMAGE

Вход: `boot.img` (или `vendor_boot.img` / `init_boot.img`).

Обязательные инструменты: `magiskboot`.

Результат: `kernel`, `ramdisk.cpio`, `dtb`, информация о заголовке.

Версия boot image header определяется автоматически по магии `ANDROID!`.
Header v3/v4 (GKI) поддерживается; для v4 выдаётся предупреждение о GKI
совместимости.

## UNPACK_DTBO_IMAGE

Вход: `dtbo.img`.

Обязательные инструменты: `magiskboot`.

Результат: отдельные DTBO-записи.

## UNPACK_FILESYSTEM_IMAGE

Вход: отдельный `system.img` / `vendor.img` / и т.д.

Обязательные инструменты зависят от обнаруженной filesystem:
- EROFS → `dump.erofs` (обязателен), `fsck.erofs` (опционально)
- ext4 → `e2fsdroid` (опционально), `resize2fs` (опционально)

Результат: дерево директорий с содержимым filesystem.

## Профиль безопасности

Все unpack-операции **read-only**:
- `isDestructive = false`
- `requiresRoot = false`
- `confirmationLevel = NONE`
- `supportsDryRun = true`

Любой unpacker можно безопасно запускать без root — исходный файл никогда
не изменяется, а результат пишется в private workspace приложения.

## Что дальше

- [Упаковка](pack.md) — как пересобрать изменённые образы.
- [Модификация](modify.md) — какие изменения безопасны между unpack и pack.
