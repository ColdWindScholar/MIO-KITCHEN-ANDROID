# Устранение неполадок

Типичные проблемы и их решения, организованные по симптому.

## "Operation is not ready" / блокировки

**Симптом**: Кнопка Execute отключена; приложение показывает одну или
несколько блокировок вида `Missing required tools: lpunpack` или
`Operation requires root, but root is not available`.

**Причина**: `OperationPlan.canExecute` false, потому что toolchain-resolver
или safety-профиль сообщили блокировку.

**Решение**:
- Для отсутствующих инструментов: откройте Settings → Toolchain → Reinstall.
  Это запустит `ToolchainInstaller`, который извлекает бинарники по манифесту
  и проверяет SHA-256.
- Для root: выдайте root-доступ через Magisk (или ваш superuser-app) и
  повторите.
- Для ABI-несовпадения: ABI вашего устройства не в toolchain-манифесте.
  Проверьте `DeviceProfile.abiList` на экране About и убедитесь, что
  манифест объявляет этот ABI.

## "Cannot open output stream for URI"

**Симптом**: Экспорт проваливается с этой ошибкой.

**Причина**: SAF-picker вернул URI, на который у приложения нет persistable
разрешения. Это бывает, если URI пришёл из sharing-intent другого приложения,
а не из `ACTION_OPEN_DOCUMENT`.

**Решение**: Выбирайте файл через собственный picker MIO-KITCHEN (использует
`ActivityResultContracts.OpenDocument` и берёт persistable разрешение), а не
через share-sheet.

## "SHA-256 mismatch"

**Симптом**: `ToolchainInstaller` проваливается с `ChecksumMismatch` для
конкретного инструмента.

**Причина**: Бинарник в `assets/bin/` не соответствует SHA-256, объявленному
в `assets/toolchain/manifest.json`. Либо манифест устарел, либо бинарник
повреждён.

**Решение**: Пересоберите APK из исходников. Если вы разработчик,
перегенерируйте SHA-256 в манифесте, запустив
`python3 tools/compute-tool-hashes.py`. Скрипт обходит все файлы в
`assets/bin/` и обновляет `assets/toolchain/manifest.json` реальными
SHA-256. После этого пересоберите APK, чтобы новый манифест попал в сборку.

## "Timeout after X ms"

**Симптом**: Долгая операция (например, `UNPACK_ROM`) убивается после
настроенного таймаута.

**Причина**: Операция заняла дольше `ShellCommand.timeoutMs`. По умолчанию
таймаута нет, но если вызывающая сторона установила, он может быть превышен
для очень больших ROM.

**Решение**: Увеличьте таймаут в опциях запуска операции. Для очень больших
ROM (5GB+) используйте `Long.MAX_VALUE` для отсутствия таймаута. Убедитесь,
что устройство не в режиме battery-saver, который замедляет CPU-bound
операции.

## "Plan not ready. Blockers: No compatible ABI between device and tools"

**Симптом**: План блокируется, потому что ABI устройства не пересекается с
toolchain.

**Причина**: Toolchain-манифест объявляет только `arm64-v8a` и
`armeabi-v7a`, а устройство x86_64 (эмулятор).

**Решение**: Запускайтесь на ARM-устройстве или ARM-эмуляторе. MIO-KITCHEN
не поставляет x86-бинарники для bundled-инструментов, потому что большинство
firmware-операций требуют ARM-specific magiskboot / e2fsprogs.

## Предупреждение "vbmeta-bad-magic"

**Симптом**: Анализатор предупреждает, что `vbmeta.img` не начинается с магии
`AVB0`.

**Причина**: Файл на самом деле не vbmeta-образ. Частые причины:
- Это `vbmeta_system.img` с chained descriptor (всё ещё AVB0-магия — не должно
  вызывать это предупреждение).
- Это vendor-specific signature blob (редко).
- Файл переименован в `vbmeta.img`, но на самом деле это что-то другое.

**Решение**: Проверьте файл через `avbtool info_image --image vbmeta.img`.
Если это тоже падает, файл не vbmeta-образ.

## Приложение падает на Android 14+

**Симптом**: `ForegroundServiceTypeNotAllowed` или
`MissingForegroundServiceTypeException`.

**Причина**: На Android 14+ (API 34+) foreground-сервисы обязаны объявлять
тип. MIO-KITCHEN использует `dataSync` (объявлено в манифесте). Краш
происходит, если вы side-load-нули старый APK до Stage 13.

**Решение**: Установите последний APK со страницы релизов проекта. Манифест
теперь объявляет `FOREGROUND_SERVICE_DATA_SYNC`, а
`FirmwareOperationService` использует
`FOREGROUND_SERVICE_TYPE_DATA_SYNC`.

## Разрешение на уведомления не выдано

**Симптом**: На Android 13+ foreground-notification не появляется.

**Причина**: runtime-разрешение `POST_NOTIFICATIONS` отклонено.

**Решение**: Долгое нажатие на иконку приложения → App info → Уведомления →
Разрешить. Или перевыдайте через `RuntimePermissionHelper.requestMissing(activity, ...)`,
которое приложение вызывает при следующем запуске.

## Нужна дополнительная помощь

- Загляните в [developer docs](../dev/) для архитектурных деталей.
- Перечитайте [быстрый старт](quick-start.md), чтобы убедиться, что вы не
  пропустили шаг.
- См. [безопасность flash](flash-safety.md), если проблема в flash-операции.
- См. [root-режим](root-mode.md), если проблема в root.
- См. [доступ к хранилищу](storage-access.md), если проблема в выборе/экспорте
  файлов.
- Создайте issue в баг-трекере проекта с:
  - Точным сообщением об ошибке.
  - Сводкой `DeviceProfile` с экрана About.
  - Сводкой `FirmwareProfile`, если краш произошёл во время операции.
  - Вершиной Android вашего устройства.
