# MIO-KITCHEN-SOURCE-ANDROID #
#### MIO-KITCHEN Android версия
> [!CAUTION]
> Коммерческое использование без разрешения запрещено
***
## Этот инструмент использует множество open-source проектов. Огромное спасибо разработчикам!
***
## Локализация
### English: [README.md](README.md)
### 日本語: [README_ja-JP.md](README_ja-JP.md)
### 中文: [README_zh-CN.md](README_zh-CN.md)
***
## Документация для разработчиков
* [Архитектура локализации и валидация (RU)](docs/ru/dev/localization.md)
* [Архитектура локализации и валидация (EN)](docs/en/dev/localization.md)
* [Критические hotfix-ы и регрессионные проверки (RU)](docs/ru/dev/critical-hotfixes.md)
* [Критические hotfix-ы и регрессионные проверки (EN)](docs/en/dev/critical-hotfixes.md)
* [Базовая модернизация сборки (RU)](docs/ru/dev/build-modernization.md)
* [Базовая модернизация сборки (EN)](docs/en/dev/build-modernization.md)
* [Архитектура storage/workspace совместимости (RU)](docs/ru/dev/storage-workspace.md)
* [Архитектура storage/workspace совместимости (EN)](docs/en/dev/storage-workspace.md)
* [Разделение парсера и runtime KrScript (RU)](docs/ru/dev/parser-split.md)
* [Разделение парсера и runtime KrScript (EN)](docs/en/dev/parser-split.md)
* [Типизированный API shell runtime (RU)](docs/ru/dev/shell-runtime.md)
* [Типизированный API shell runtime (EN)](docs/en/dev/shell-runtime.md)
* [Анализатор прошивок (RU)](docs/ru/dev/firmware-analyzer.md)
* [Анализатор прошивок (EN)](docs/en/dev/firmware-analyzer.md)
* [Модернизация UI (RU)](docs/ru/dev/ui-modernization.md)
* [Модернизация UI (EN)](docs/en/dev/ui-modernization.md)
* [Тесты и CI (RU)](docs/ru/dev/tests-and-ci.md)
* [Тесты и CI (EN)](docs/en/dev/tests-and-ci.md)
* [Runtime-профили (RU)](docs/ru/dev/runtime-profiles.md)
* [Runtime-профили (EN)](docs/en/dev/runtime-profiles.md)
* [ToolchainInstaller (RU)](docs/ru/dev/toolchain-installer.md)
* [ToolchainInstaller (EN)](docs/en/dev/toolchain-installer.md)
* [OperationExecutor (RU)](docs/ru/dev/operation-executor.md)
* [OperationExecutor (EN)](docs/en/dev/operation-executor.md)
* [Миграция targetSdk 35 (RU)](docs/ru/dev/targetsdk-35.md)
* [Миграция targetSdk 35 (EN)](docs/en/dev/targetsdk-35.md)
* [Политика экспорта folder/tree URI (RU)](docs/ru/dev/export-policy.md)
* [Политика экспорта folder/tree URI (EN)](docs/en/dev/export-policy.md)
* [Миграция UI на AppRuntimeProfile (RU)](docs/ru/dev/ui-migration.md)
* [Миграция UI на AppRuntimeProfile (EN)](docs/en/dev/ui-migration.md)
* [Статический analysis (RU)](docs/ru/dev/static-analysis.md)
* [Статический analysis (EN)](docs/en/dev/static-analysis.md)
* [Отчётность покрытия (RU)](docs/ru/dev/coverage.md)
* [Отчётность покрытия (EN)](docs/en/dev/coverage.md)
* [Instrumented-тесты (RU)](docs/ru/dev/instrumented-tests.md)
* [Instrumented-тесты (EN)](docs/en/dev/instrumented-tests.md)
* [Гибридная миграция (RU)](docs/ru/dev/hybrid-migration.md)
* [Гибридная миграция (EN)](docs/en/dev/hybrid-migration.md)
* [Подключение ToolchainInstaller (RU)](docs/ru/dev/toolchain-wiring.md)
* [Подключение ToolchainInstaller (EN)](docs/en/dev/toolchain-wiring.md)
* [Удаление legacy (RU)](docs/ru/dev/legacy-removal.md)
* [Удаление legacy (EN)](docs/en/dev/legacy-removal.md)
* [Финальная очистка legacy (RU)](docs/ru/dev/final-cleanup.md)
* [Финальная очистка legacy (EN)](docs/en/dev/final-cleanup.md)
* [Дорожная карта модернизации v3 (RU)](docs/ru/roadmap/mio_kitchen_modernization_plan_v3_single_apk_ru.md)

## Пользовательская документация
* [Быстрый старт (RU)](docs/ru/user/quick-start.md) | [(EN)](docs/en/user/quick-start.md)
* [Выбор ROM (RU)](docs/ru/user/choose-rom.md) | [(EN)](docs/en/user/choose-rom.md)
* [Распаковка (RU)](docs/ru/user/unpack.md) | [(EN)](docs/en/user/unpack.md)
* [Модификация (RU)](docs/ru/user/modify.md) | [(EN)](docs/en/user/modify.md)
* [Упаковка (RU)](docs/ru/user/pack.md) | [(EN)](docs/en/user/pack.md)
* [Безопасность flash (RU)](docs/ru/user/flash-safety.md) | [(EN)](docs/en/user/flash-safety.md)
* [Доступ к хранилищу (RU)](docs/ru/user/storage-access.md) | [(EN)](docs/en/user/storage-access.md)
* [Root-режим (RU)](docs/ru/user/root-mode.md) | [(EN)](docs/en/user/root-mode.md)
* [Устранение неполадок (RU)](docs/ru/user/troubleshooting.md) | [(EN)](docs/en/user/troubleshooting.md)

## Быстрая валидация

```bash
python3 tools/run-all-checks.py
```
***
## Возможности
* Распаковка boot, dtbo, ext4, erofs, payload, logo и т.д.
* Упаковка boot, dtbo, ext4, erofs, payload, logo и т.д.
***
## На базе [Mio Android Kitchen](https://github.com/ColdWindScholar/MIO-KITCHEN-SOURCE) и [Kr-Scripts](https://github.com/ColdWindScholar/kr-scripts)
***
## Преимущества
* Автоматическая модификация fs_config и fs_context
* Графический интерфейс
* Поддержка кастомных плагинов, плюс менеджер для их установки и управления
* Быстрые обновления, безопасность, стабильность и скорость
* Уникальный интерпретатор MSH с поддержкой выполнения MSH-скриптов
* Обратная совместимость с Android 8 и ниже, создание .img для этих версий
***
***
# Связь с нами
***
### Email разработчика: 3590361911@qq.com
### QQ-группа: 836898509
### Telegram-группа: [Mio Android Kitchen Chat](https://t.me/mio_android_kitchen_group)
### Telegram-канал: [Mio Android Kitchen Updates](https://t.me/mio_android_kitchen)
***
# Участники:
### Английская и японская локали: [ookiineko](https://github.com/ookiineko)
### Перевод README на японский: [reindex-ot](https://github.com/reindex-ot)
***
### Спасибо всем, кто помогает!!
***
# О проекте
***
### MIO-KITCHEN
```
Всегда бесплатно, пользователи на первом месте
Качественные инструменты, здесь!
От команды MIO-KITCHEN-TEAM
```
#### ColdWindScholar(3590361911@qq.com) Все права защищены. ####
