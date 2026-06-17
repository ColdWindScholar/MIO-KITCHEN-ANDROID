# Документация проекта

Эта папка намеренно лежит внутри архива проекта, чтобы архитектурные заметки,
правила для разработчиков и команды валидации поставлялись вместе с исходным
кодом.

## Дорожная карта

- `roadmap/mio_kitchen_modernization_plan_v3_single_apk_ru.md` — полная дорожная карта (RU).

## Разработческие документы

| Этап | Тема | Ссылка |
|------|------|--------|
| 1 | Локализация | [dev/localization.md](dev/localization.md) |
| 2 | Критические hotfix-ы | [dev/critical-hotfixes.md](dev/critical-hotfixes.md) |
| 3 | Build modernization | [dev/build-modernization.md](dev/build-modernization.md) |
| 4 | Storage / workspace | [dev/storage-workspace.md](dev/storage-workspace.md) |
| 5 | KrScript parser/runtime split | [dev/parser-split.md](dev/parser-split.md) |
| 6 | Shell runtime | [dev/shell-runtime.md](dev/shell-runtime.md) |
| 7 | Анализатор прошивок | [dev/firmware-analyzer.md](dev/firmware-analyzer.md) |
| 8 | Модернизация UI | [dev/ui-modernization.md](dev/ui-modernization.md) |
| 9 | Тесты и CI | [dev/tests-and-ci.md](dev/tests-and-ci.md) |
| 10 | Runtime-профили | [dev/runtime-profiles.md](dev/runtime-profiles.md) |
| 11 | ToolchainInstaller | [dev/toolchain-installer.md](dev/toolchain-installer.md) |
| 12 | OperationExecutor | [dev/operation-executor.md](dev/operation-executor.md) |
| 13 | Миграция targetSdk 35 | [dev/targetsdk-35.md](dev/targetsdk-35.md) |
| 14 | Политика экспорта folder/tree URI | [dev/export-policy.md](dev/export-policy.md) |
| 15 | UI migration to AppRuntimeProfile | [dev/ui-migration.md](dev/ui-migration.md) |
| 16 | Статический analysis | [dev/static-analysis.md](dev/static-analysis.md) |
| 17 | Отчётность покрытия | [dev/coverage.md](dev/coverage.md) |
| 18 | Instrumented-тесты | [dev/instrumented-tests.md](dev/instrumented-tests.md) |
| 20 | Гибридная миграция | [dev/hybrid-migration.md](dev/hybrid-migration.md) |
| 21 | Подключение ToolchainInstaller | [dev/toolchain-wiring.md](dev/toolchain-wiring.md) |
| 22 | Удаление legacy | [dev/legacy-removal.md](dev/legacy-removal.md) |
| 23 | Финальная очистка legacy | [dev/final-cleanup.md](dev/final-cleanup.md) |

## Пользовательские документы

| Тема | Ссылка |
|------|--------|
| Быстрый старт | [user/quick-start.md](user/quick-start.md) |
| Выбор ROM | [user/choose-rom.md](user/choose-rom.md) |
| Распаковка | [user/unpack.md](user/unpack.md) |
| Модификация | [user/modify.md](user/modify.md) |
| Упаковка | [user/pack.md](user/pack.md) |
| Безопасность flash | [user/flash-safety.md](user/flash-safety.md) |
| Доступ к хранилищу | [user/storage-access.md](user/storage-access.md) |
| Root-режим | [user/root-mode.md](user/root-mode.md) |
| Устранение неполадок | [user/troubleshooting.md](user/troubleshooting.md) |

## Запуск всех проверок

```bash
python3 tools/run-all-checks.py
```
