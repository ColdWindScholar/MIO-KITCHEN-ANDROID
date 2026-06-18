# Модификация

После распаковки вы обычно хотите отредактировать файлы внутри извлечённой
filesystem или boot image. MIO-KITCHEN не предоставляет встроенный
файловый редактор — изменения делаются через внешние инструменты или
shell-команды, вызываемые через KrScript-actions.

## Что безопасно менять

### Внутри распакованной filesystem (ext4 / EROFS)

- `/system/build.prop` — свойства устройства.
- `/system/etc/hosts` — для ad-blocking.
- `/system/app/*.apk` и `/system/priv-app/*.apk` — предустановленные приложения.
- `/system/fonts/*.ttf` — системные шрифты.
- `/system/media/audio/*.ogg` — системные звуки.

После редактирования пересоберите через [PACK_FILESYSTEM_IMAGE](pack.md) —
packer сохранит ownership, permissions и SELinux-контексты файлов.

### Внутри распакованного boot image

- `kernel` — сжатый kernel-бинарник (редко редактируется; обычно заменяют
  на известный-хороший).
- `ramdisk.cpio` — init ramdisk. Частые правки:
  - `init.rc` и Company — boot-скрипты.
  - `fstab.<device>` — таблица монтирования.
  - `default.prop` — debuggable, adb и т.д.
- `dtb` — device tree blob (продвинуто).

После редактирования пересоберите через [PACK_BOOT_IMAGE](pack.md). Packer
пересчитает заголовок и повторно подпишет образ, если доступен `magiskboot`.

### Внутри super.img

После `UNPACK_SUPER` каждый раздел (`system.img`, `vendor.img`, ...) —
отдельный filesystem-образ. Меняйте каждый по отдельности, затем пересоберите
через [PACK_SUPER](pack.md).

## Что НЕ безопасно менять

- **`vbmeta.img`** — изменение без переподписания ломает AVB, устройство не
  загрузится. Используйте [VERIFY_VBMETA](unpack.md) для проверки, но не
  редактируйте.
- **`payload.bin`** — непрозрачный protobuf-формат. Сначала распакуйте в
  разделы, меняйте их, и пере-упакуйте в новый OTA (отдельная операция,
  пока не реализована как типизированная `FirmwareOperation`).
- **Sparse-заголовки** — `simg2img` конвертирует sparse → raw; редактируйте
  только raw-форму, затем `img2simg` конвертирует обратно. Не редактируйте
  sparse-образы напрямую.

## Профиль безопасности

Modify-операции происходят внутри app workspace, поэтому они:
- `isDestructive = false` (меняются только workspace-файлы)
- `requiresRoot = false`
- `confirmationLevel = STANDARD` (yes/no перед запуском)
- `supportsDryRun = true`

## Что дальше

- [Упаковка](pack.md) — пересобрать образ после редактирования.
- [Безопасность flash](flash-safety.md) — прочитайте перед любой flash-операцией.
