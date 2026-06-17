# MIO-KITCHEN Android — план модернизации v3: один APK для любых версий прошивок

Дата обновления: **2026-06-18**  
Исходный проект: `MIO-KITCHEN-ANDROID-master.zip`  
Предыдущий документ: `mio_kitchen_modernization_plan_v2_ru.md`  
Статус: **v3.23 — ВСЕ этапы 1–23 завершены. Включая финальную очистку legacy (Stage 23): ShellExecutor переписан на Kotlin, streaming-выполнение восстановлено, ExtractAssets заменён на ToolchainInstaller, манифест расширен до 21 файла.**

---

## 0.0. Текущий статус выполнения

По просьбе держать фокус на одном направлении работа идёт этапами. Каждый этап должен завершаться полным архивом проекта, patch-файлом, документацией и проверками.

### Завершённые этапы

| Этап | Название | Статус | Документация |
|------|----------|--------|--------------|
| 1 | Локализация | завершено | docs/{ru,en}/dev/localization.md |
| 2 | Критические hotfix-и | завершено | docs/{ru,en}/dev/critical-hotfixes.md |
| 3 | Build modernization | завершено | docs/{ru,en}/dev/build-modernization.md |
| 4 | Storage/workspace | завершено | docs/{ru,en}/dev/storage-workspace.md |
| 5 | KrScript parser/runtime split | завершено | docs/{ru,en}/dev/parser-split.md |
| 6 | Shell runtime | завершено | docs/{ru,en}/dev/shell-runtime.md |
| 7 | Firmware analyzer | завершено | docs/{ru,en}/dev/firmware-analyzer.md |
| 8 | UI modernization | завершено | docs/{ru,en}/dev/ui-modernization.md |
| 9 | Tests and CI | завершено | docs/{ru,en}/dev/tests-and-ci.md |
| 10 | Runtime profiles | завершено | docs/{ru,en}/dev/runtime-profiles.md |
| 11 | ToolchainInstaller | завершено | docs/{ru,en}/dev/toolchain-installer.md |
| 12 | OperationExecutor | завершено | docs/{ru,en}/dev/operation-executor.md |
| 13 | targetSdk 35 migration | завершено | docs/{ru,en}/dev/targetsdk-35.md |
| 14 | Folder/tree URI export policy | завершено | docs/{ru,en}/dev/export-policy.md |
| 15 | UI migration to AppRuntimeProfile | завершено | docs/{ru,en}/dev/ui-migration.md |
| 16 | Static analysis | завершено | docs/{ru,en}/dev/static-analysis.md |
| 17 | Coverage reporting | завершено | docs/{ru,en}/dev/coverage.md |
| 18 | Instrumented tests | завершено | docs/{ru,en}/dev/instrumented-tests.md |
| 19 | User docs (RU/EN) | завершено | docs/{ru,en}/user/*.md (9 docs) |
| 20 | Hybrid migration layer | завершено | docs/{ru,en}/dev/hybrid-migration.md |
| 21 | ToolchainInstaller wiring | завершено | docs/{ru,en}/dev/toolchain-wiring.md |
| 22 | Legacy removal | завершено | docs/{ru,en}/dev/legacy-removal.md |
| 23 | Final legacy cleanup | завершено | docs/{ru,en}/dev/final-cleanup.md |

Все проверки проходят одной командой:

```bash
python3 tools/run-all-checks.py
```

### Этап 1 — локализация

```text
статус: завершено
продукт: полный архив проекта с завершённой локализацией
```

Закреплённые решения:

```text
переводы:
  остаются в отдельных Android resource-файлах:
    values/strings.xml        # английский язык по умолчанию
    values-ru/strings.xml
    values-zh/strings.xml
    values-ja/strings.xml

общий контракт:
  AppLanguage хранит только технические значения:
    SharedPreferences name
    language key
    default language code en
    shell env names APP_LANGUAGE / LC_CTYPE

  LanguageConfig хранит UI-список поддерживаемых языков:
    en / ru / zh / ja

runtime fallback:
  не используется для переводных текстов
  если @string/... отсутствует, это ошибка качества ресурсов
  validate-localization.py должен поймать это до релиза
```

Внесённые изменения:

```text
common:
  + AppLanguage.kt
  * ShellTranslation.kt

pio:
  * LanguageConfig.kt
  * MainActivity.kt

krscript:
  * ScriptEnvironmen.java

docs:
  + docs/ru/dev/localization.md
  + docs/en/dev/localization.md
  + docs/README.md

tools:
  + tools/validate-localization.py

ci:
  + .github/workflows/localization.yml
```

Проверка этапа:

```bash
python3 tools/validate-localization.py
```

Ожидаемый результат:

```text
PASS: localization contract is split correctly between common and UI
PASS: string resources are complete for languages: en, ru, zh, ja
PASS: placeholders match across localized resources
PASS: source @string references resolve to existing resources
PASS: runtime translation fallbacks are disabled
PASS: RU/EN localization documentation exists
```

### Этап 2 — критические hotfix-и и регрессионные проверки

```text
статус: завершено
цель: исправить найденные дефекты без смешивания с большим архитектурным рефакторингом
```

Исправленные зоны:

```text
common/shared/FilePathResolver.java:
  content:// copy теперь пишет только bytesRead байты
  getFileName безопаснее закрывает Cursor

krscript/config/Suffix2Mime.kt:
  корректная обработка jpg/jpeg/jpe, tar/taz/tgz, html/htm/shtml
  сохранена совместимость с Kotlin 1.4

krscript/ui/PageMenuLoader.kt:
  dynamic menu options больше не теряются

krscript/ui/ActionListFragment.kt:
  shellModeHidden теперь реально запускает ShellExecutor
  hiddenTaskRunning сбрасывается после завершения
  dynamic param options value|title парсятся безопасно

pio/MTDataFilesProvider.java:
  document flags больше не сбрасываются в 0

pio/assets/script/tool.sh:
  исправлены iMG / ${i}MG -> IMG
  flash target проверяет symlink или block device
  readsize не перетирает manual size auto-size значением
  mkerofs и rboot используют аргумент partition
  исправлены $mdir-пути при boot repack

pio/assets/script2/more.xml:
  requiret="true" -> required="true"
```

Добавлены:

```text
tools/check-known-regressions.py
.github/workflows/regression-checks.yml
docs/ru/dev/critical-hotfixes.md
docs/en/dev/critical-hotfixes.md
```

Проверки этапа:

```bash
python3 tools/validate-localization.py
python3 tools/check-known-regressions.py
bash -n pio/src/main/assets/script/tool.sh
```

Ожидаемый результат:

```text
PASS: localization contract is split correctly between common and UI
PASS: string resources are complete for languages: en, ru, zh, ja
PASS: placeholders match across localized resources
PASS: source @string references resolve to existing resources
PASS: runtime translation fallbacks are disabled
PASS: RU/EN localization documentation exists
PASS: known critical regressions are fixed
```

### Этап 3 — baseline build modernization

```text
статус: завершено
цель: перевести проект с устаревшего Gradle/AGP/Kotlin build stack на современную базу, не меняя пока storage/runtime поведение приложения
```

Закреплённые решения:

```text
пользовательский продукт:
  всё ещё один APK / один UI / одна кодовая база

Gradle:
  wrapper -> Gradle 8.13 через официальный services.gradle.org URL
  root build.gradle -> plugins DSL
  settings.gradle -> pluginManagement + dependencyResolutionManagement
  jcenter() удалён
  project-level repositories заблокированы через FAIL_ON_PROJECT_REPOS

Android Gradle Plugin:
  AGP 8.13.2
  namespace задан в Gradle для common / krscript / pio
  manifest package удалён из AndroidManifest.xml

Kotlin:
  Kotlin Android Gradle plugin 2.3.21
  kotlin-android-extensions удалён
  kotlinx.android.synthetic imports удалены
  синтетический доступ к View заменён на ViewBinding

SDK policy:
  compileSdk = 35
  minSdk = 21
  targetSdk = 28 временно сохранён
```

Почему `targetSdk = 28` пока оставлен:

```text
build stack можно обновить отдельно от runtime/storage поведения.
перевод targetSdk на 35 меняет storage/permission behavior и должен идти вместе
со следующим этапом StorageGateway / Workspace / SAF / root-path adapters.
иначе можно получить современно собранный, но функционально сломанный firmware workflow.
```

Изменённые зоны:

```text
root:
  * settings.gradle
  * build.gradle
  * gradle.properties
  * gradle/wrapper/gradle-wrapper.properties
  * .gitignore

modules:
  * common/build.gradle
  * krscript/build.gradle
  * pio/build.gradle

manifests:
  * common/src/main/AndroidManifest.xml
  * krscript/src/main/AndroidManifest.xml
  * pio/src/main/AndroidManifest.xml

ViewBinding migration:
  * krscript/ui/DialogLogFragment.kt
  * pio/ActionPage.kt
  * pio/ActivityFileSelector.kt
  * pio/MainActivity.kt
  * pio/SplashActivity.kt

CI/tools/docs:
  + tools/check-build-modernization.py
  + .github/workflows/build-quality.yml
  * .github/workflows/gradle.yml
  + docs/ru/dev/build-modernization.md
  + docs/en/dev/build-modernization.md
```

Проверки этапа:

```bash
python3 tools/validate-localization.py
python3 tools/check-known-regressions.py
python3 tools/check-build-modernization.py
bash -n pio/src/main/assets/script/tool.sh
```

Ожидаемый результат:

```text
PASS: localization contract is split correctly between common and UI
PASS: string resources are complete for languages: en, ru, zh, ja
PASS: placeholders match across localized resources
PASS: source @string references resolve to existing resources
PASS: runtime translation fallbacks are disabled
PASS: RU/EN localization documentation exists
PASS: known critical regressions are fixed
PASS: build modernization baseline is in place
```

### Этап 4 — storage/workspace compatibility architecture

```text
статус: завершено
цель: подготовить единый APK к modern storage behavior без потери shell/root firmware workflow
```

Закреплённые решения:

```text
content:// URI:
  не передаётся напрямую в shell
  копируется в app-specific FirmwareWorkspace
  во время копирования считается SHA-256

обычный path/file://:
  остаётся shell-доступным DirectFile

legacy provider _data path:
  изолирован внутри AndroidStorageGateway
  используется только при preferLegacyDirectPath=true

folder/direct-path workflow:
  пока сохранён через ActivityFileSelector
  будет отдельно доработан через tree/workspace/export policy
```

Добавленные компоненты:

```text
common/storage/SafeFileName.kt
common/storage/FirmwareWorkspace.kt
common/storage/StorageGateway.kt
common/storage/AndroidStorageGateway.kt
common/storage/StorageResolveOptions.kt
common/storage/StorageResolveResult.kt
common/storage/StorageSourceKind.kt
common/src/test/java/com/omarea/common/storage/SafeFileNameTest.kt
```

Изменённый UI-flow:

```text
pio/ActionPage.kt:
  ACTION_OPEN_DOCUMENT для выбора файла
  URI grants
  AndroidStorageGateway.persistReadPermission()
  AndroidStorageGateway.resolveUriForShell()
  workspace copy вне прямой UI-ветки
  KrScript получает shellPath
```

Добавлены:

```text
tools/check-storage-workspace.py
.github/workflows/storage-workspace.yml
docs/ru/dev/storage-workspace.md
docs/en/dev/storage-workspace.md
```

Проверки этапа:

```bash
python3 tools/validate-localization.py
python3 tools/check-known-regressions.py
python3 tools/check-build-modernization.py
python3 tools/check-storage-workspace.py
bash -n pio/src/main/assets/script/tool.sh
```

Ожидаемый результат:

```text
PASS: storage/workspace compatibility baseline is in place
PASS: content URI input is converted to workspace file paths
PASS: legacy direct-path behavior is isolated behind StorageGateway
PASS: storage/workspace docs and CI gates are present
```

### Что намеренно всё ещё не входит в закрытые этапы

```text
не завершено:
  targetSdk 35 runtime migration
  folder/tree URI export policy
  ToolchainInstaller (реальное извлечение бинарников + SHA-256 проверка)
  OperationExecutor (план -> shell script через ShellRuntime)
  real-device/root firmware tests
  Detekt/Ktlint/Lint в CI
  Gradle testDebugUnitTest в CI (нужен Android SDK образ)
  Coverage-отчётность (Jacoco/Kover)
  Instrumented androidTest в CI
  ShellCheck для tool.sh
  UI-активити не используют AppRuntimeProfile (план отдельного этапа)
```

### Этап 5 — KrScript parser/runtime split

```text
статус: завершено
цель: выделить чистый XML-парсер из legacy PageConfigReader, отделить shell/runtime side effects
```

Закреплённые решения:

```text
parser purity:
  PageConfigParser НЕ зависит от Context/Handler/Toast/ScriptEnvironmen/ExtractAssets
  DynamicValueResolver — стратегия разрешения desc-sh/summary-sh/support/visible/getstate
  NoopDynamicValueResolver — для JVM-тестов
  RuntimeBinder — единственное место, где DynamicValueResolver вызывает shell

источник XML:
  PageConfigSource — абстракция
  StreamPageConfigSource — поток
  AndroidPageConfigSource — адаптер поверх PathAnalysis

репозиторий:
  PageConfigRepository — source -> parser -> validator -> ParsedPageConfig

валидатор:
  PageConfigValidator — собирает ошибки и предупреждения без shell
  ValidationReport (errors + warnings)
  коды: picker-no-options, action-no-setstate, param-duplicate-name и др.

обратная совместимость:
  legacy PageConfigReader сохранён без изменений
  новый код использует PageConfigRepository/RuntimeBinder
```

Внесённые изменения:

```text
krscript/parser:
  + PageConfigSource.kt
  + StreamPageConfigSource.kt
  + DynamicValueResolver.kt
  + PageConfigParser.kt
  + PageConfigRepository.kt

krscript/validator:
  + PageConfigValidator.kt

krscript/runtime:
  + RuntimeBinder.kt

krscript/config:
  + AndroidPageConfigSource.kt

krscript/src/test:
  + PageConfigParserTest.kt

tools:
  + tools/check-parser-split.py

ci:
  + .github/workflows/parser-split.yml

docs:
  + docs/ru/dev/parser-split.md
  + docs/en/dev/parser-split.md
```

Проверка этапа:

```bash
python3 tools/check-parser-split.py
```

Ожидаемый результат:

```text
PASS: KrScript parser/runtime split is in place
PASS: parser does not depend on Context/Handler/Toast
PASS: parser does not invoke ScriptEnvironmen or ExtractAssets
PASS: validator does not execute shell
PASS: RuntimeBinder is the single shell-bound DynamicValueResolver
PASS: parser unit tests and JVM test dependencies are present
PASS: legacy PageConfigReader is preserved for backward compatibility
```

### Этап 6 — Shell runtime

```text
статус: завершено
цель: типизированный ShellRuntime API поверх legacy KeepShell
```

Закреплённые решения:

```text
типы:
  ScriptSource — Inline / FilePath / PreparedFile
  ShellCommand — id, script, env, workingDir, requiresRoot, timeoutMs, tag
  ShellEvent — Stdout/Stderr/Progress/Warning/Error/Completed
  ShellResult — Completed/Cancelled/TimedOut/Failed
  ShellRuntime — interface: execute(Flow<ShellEvent>) + executeForResult(suspend)

реализации:
  DryRunShellRuntime — не запускает shell, возвращает preview + Completed(0)
  FakeShellRuntime — заглушка для тестов с recordedCommands
  KeepShellRuntime — переходная реализация, оборачивает legacy KeepShell
  RootShellRuntime — su-режим (extends KeepShellRuntime)
  UserShellRuntime — sh-режим (extends KeepShellRuntime)

фабрика:
  ShellRuntimeFactory — выбирает runtime по requiresRoot + dryRun + rootAvailable

контракт:
  терминальное событие — всегда Completed или Error
  без глобального состояния (id сессии — в ShellCommand.id)
  уважает timeoutMs и отмену корутины
```

Внесённые изменения:

```text
common/shell/runtime:
  + ScriptSource.kt
  + ShellCommand.kt
  + ShellEvent.kt
  + ShellResult.kt
  + ShellRuntime.kt
  + DryRunShellRuntime.kt
  + FakeShellRuntime.kt
  + KeepShellRuntime.kt
  + ShellRuntimeFactory.kt

common/src/test:
  + shell/runtime/ShellRuntimeTest.kt

common/build.gradle:
  + testImplementation kotlinx-coroutines-core
  + testImplementation kotlinx-coroutines-test

tools:
  + tools/check-shell-runtime.py

ci:
  + .github/workflows/shell-runtime.yml

docs:
  + docs/ru/dev/shell-runtime.md
  + docs/en/dev/shell-runtime.md
```

Проверка этапа:

```bash
python3 tools/check-shell-runtime.py
```

Ожидаемый результат:

```text
PASS: Shell runtime typed API is in place
PASS: ShellCommand/ShellEvent/ShellResult sealed types are declared
PASS: DryRunShellRuntime and FakeShellRuntime are present
PASS: RootShellRuntime and UserShellRuntime wrap KeepShell
PASS: ShellRuntimeFactory selects runtime based on command + dry-run + root
PASS: Shell runtime JVM unit tests are present
```

### Этап 7 — Firmware analyzer

```text
статус: завершено
цель: типизированный FirmwareAnalyzer, который строит FirmwareProfile по содержимому файла
```

Закреплённые решения:

```text
модель:
  FirmwareSource — DirectPath / WorkspaceFile / FileNameOnly
  FirmwarePackageType — ZIP_OTA/PAYLOAD_BIN/SUPER_IMAGE/BOOT_IMAGE/...
  AndroidVersionHint — версия Android внутри прошивки (только подсказка)
  CompressionType — GZIP/LZ4/LZMA/ZSTD/BROTLI/BZIP2/NONE/UNKNOWN
  PartitionInfo — имя/размер/filesystem/sparse/logical
  FirmwareWarning — код/сообщение/severity (INFO/WARNING/ERROR)
  FirmwareCapabilities — hasPayloadBin/hasSuperImage/hasErofs/usesAvb/...
  FirmwareProfile — финальный результат анализатора

интерфейс:
  FirmwareAnalyzer.supports(source) + analyze(source) -> FirmwareProfile
  CompositeFirmwareAnalyzer — перебирает зарегистрированные анализаторы
  FirmwareAnalysisException — типизированная ошибка

анализаторы:
  ZipFirmwareAnalyzer — обходит OTA zip, ищет payload.bin/super.img/boot.img/...
  BootImageAnalyzer — проверяет магию ANDROID! и версию заголовка
  SuperImageAnalyzer — определяет sparse/raw по магии 0x3aff26ed
  VbmetaAnalyzer — проверяет магию AVB0
  FilesystemImageAnalyzer — ext4 (0xef53) / EROFS (0xe0f5e1e2) / F2FS (0xf2f52010)

реестр:
  FirmwareAnalyzerRegistry.createDefault() — компонует все анализаторы

правила:
  анализаторы НЕ запускают shell
  анализаторы НЕ зависят от android.app.* / android.widget.*
  все анализаторы тестируются в JVM без Robolectric
```

Внесённые изменения:

```text
common/firmware:
  + FirmwareProfile.kt
  + FirmwareAnalyzer.kt
  + ZipFirmwareAnalyzer.kt
  + ImageAnalyzers.kt
  + FirmwareAnalyzerRegistry.kt

common/src/test:
  + firmware/FirmwareAnalyzerTest.kt

tools:
  + tools/check-firmware-analyzer.py

ci:
  + .github/workflows/firmware-analyzer.yml

docs:
  + docs/ru/dev/firmware-analyzer.md
  + docs/en/dev/firmware-analyzer.md
```

Проверка этапа:

```bash
python3 tools/check-firmware-analyzer.py
```

Ожидаемый результат:

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

### Этап 8 — UI modernization

```text
статус: завершено
цель: ввести UiState/ViewModel/StateFlow + новый Activity Result API как рекомендованный путь для нового UI-кода
```

Закреплённые решения:

```text
типизированный UI state:
  UiState<T> — sealed class (Idle/Loading/Success/Error)
  UiStateHolder<T> — хранит MutableStateFlow<UiState<T>>

новый Activity Result API:
  OpenDocumentHelper — ACTION_OPEN_DOCUMENT
  CreateDocumentHelper — ACTION_CREATE_DOCUMENT
  UriPermissionPersistor — persistable URI permission
  всё через ActivityResultCaller (без startActivityForResult/onActivityResult)

ViewModel:
  FirmwareAnalysisViewModel — extends ViewModel
  принимает FirmwareAnalyzer через конструктор (тестируемость)
  использует viewModelScope + Dispatchers.IO
  НЕ импортирует Activity/Handler
  НЕ использует activity!!/context!!

formatter:
  FirmwareProfileFormatter — чистый (нет View/widget)
  short(profile) / detailed(profile)
  переиспользуется в тестах, в Compose, в View, в логах
```

Внесённые изменения:

```text
common/ui:
  + UiState.kt

pio/ui/modern:
  + ActivityResultHelpers.kt
  + FirmwareAnalysisViewModel.kt
  + FirmwareProfileFormatter.kt

pio/src/test:
  + ui/modern/FirmwareProfileFormatterTest.kt

pio/build.gradle:
  + androidx.lifecycle:lifecycle-viewmodel-ktx:2.8.7
  + androidx.lifecycle:lifecycle-runtime-ktx:2.8.7
  + androidx.activity:activity-ktx:1.9.3
  + testImplementation kotlinx-coroutines-test

tools:
  + tools/check-ui-modernization.py

ci:
  + .github/workflows/ui-modernization.yml

docs:
  + docs/ru/dev/ui-modernization.md
  + docs/en/dev/ui-modernization.md
```

Проверка этапа:

```bash
python3 tools/check-ui-modernization.py
```

Ожидаемый результат:

```text
PASS: UiState/UiStateHolder typed reactive state is in place
PASS: OpenDocumentHelper/CreateDocumentHelper use modern Activity Result API
PASS: FirmwareAnalysisViewModel extends ViewModel and uses viewModelScope
PASS: FirmwareAnalysisViewModel has no Context/Handler/activity!! dependencies
PASS: FirmwareProfileFormatter is pure (no View/widget imports)
PASS: pio/build.gradle declares lifecycle + activity-ktx dependencies
PASS: UI modernization JVM unit tests are present
```

### Этап 9 — Tests and CI

```text
статус: завершено
цель: единый runner всех статических проверок + архитектурные тесты + top-level CI workflow
```

Закреплённые решения:

```text
единый runner:
  tools/run-all-checks.py — запускает все tools/check-*.py + bash -n
  используется CI и локально

архитектурные тесты:
  tools/check-architecture.py — контролирует границы слоёв
  parser/         -> НЕ импортирует shell/UI/Context
  validator/      -> НЕ импортирует shell/UI/Context
  firmware/       -> НЕ импортирует shell/UI/Context
  shell/runtime/  -> НЕ импортирует firmware/UI
  FirmwareAnalysisViewModel -> НЕ использует Activity/Handler
  FirmwareProfileFormatter -> чистый (без View/widget)

top-level CI:
  .github/workflows/all-quality-gates.yml — runs-on ubuntu-latest, python3 tools/run-all-checks.py
  срабатывает на каждый push/PR в main/master

JVM unit-тесты:
  krscript/PageConfigParserTest.kt — 11 тестов
  common/ShellRuntimeTest.kt — 15 тестов
  common/FirmwareAnalyzerTest.kt — 16 тестов
  pio/FirmwareProfileFormatterTest.kt — 8 тестов
  всего: 50+ JVM unit-тестов в новых слоях
```

Внесённые изменения:

```text
tools:
  + tools/run-all-checks.py
  + tools/check-architecture.py

ci:
  + .github/workflows/all-quality-gates.yml

docs:
  + docs/ru/dev/tests-and-ci.md
  + docs/en/dev/tests-and-ci.md
```

Проверка этапа:

```bash
python3 tools/run-all-checks.py
```

Ожидаемый результат:

```text
ALL CHECKS PASSED
```

### Этап 10 — Runtime profiles

```text
статус: завершено
цель: ввести DeviceProfile, ToolchainProfile, OperationSafetyProfile, AppRuntimeProfileResolver — профили, которые объединяют устройство, прошивку и операцию в единый контекст
```

Закреплённые решения:

```text
DeviceProfile:
  data class с sdkInt/manufacturer/model/abiList/isEmulator/supports16KbPageSize/hasRoot/selinuxMode
  вычисляемые свойства: enforcesScopedStorage, hasScopedStorageBoundary, requiresNotificationPermission,
                        requiresForegroundServiceType, isAndroid15Plus
  DeviceProfileProvider строит профиль из Build.*
  forTests() — фабрика для тестов с дефолтами

ToolchainProfile:
  ToolRequirement: name, minVersion, purpose, abi, checksum, required
  ToolDescriptor: name, version, abi, sha256, capabilities, source, license, supports16KbPageSize
  ToolchainProfile: requiredTools, optionalTools, selectedAbi, toolsDir, verifiedChecksums
  ToolPurpose enum: SUPER_IMAGE, PAYLOAD_BIN, EROFS, EXT4, BOOT_IMAGE, VBMETA, SPARSE,
                    COMPRESSION_*, BUSYBOX, GENERIC

ToolchainResolver:
  interface: resolve(operation, firmware, device) -> ToolchainPlan
  CapabilityBasedToolchainResolver — реализация на базе capabilities
  FirmwareOperation enum: UNPACK_ROM, UNPACK_SUPER, UNPACK_PAYLOAD_BIN, UNPACK_BOOT_IMAGE,
                          UNPACK_VENDOR_BOOT_IMAGE, UNPACK_INIT_BOOT_IMAGE, UNPACK_DTBO_IMAGE,
                          UNPACK_FILESYSTEM_IMAGE, PACK_FILESYSTEM_IMAGE, PACK_BOOT_IMAGE,
                          PACK_SUPER, VERIFY_VBMETA, FLASH_PREPARE, INSPECT
  ToolchainPlan: profile, availableTools, missingRequired, warnings, ready
  ToolchainWarning: code, message, severity (INFO/WARNING/ERROR)

ToolManifestLoader:
  парсит assets/toolchain/manifest.json
  формат: { "tools": [ {name, version, abi, sha256, capabilities, source, license, supports16KbPageSize} ] }
  неизвестные capability -> ToolPurpose.GENERIC

OperationSafetyProfile:
  ConfirmationLevel enum: NONE, STANDARD, WARNING, DESTRUCTIVE
  OperationSafetyProfile: operation, isDestructive, requiresBackup, requiresRoot,
                          requiresDeviceConnection, supportsDryRun, confirmationLevel
  helpers: readOnly(operation), packOperation(operation), flashOperation(operation)
  вычисляемые: isSafe, riskLabel

OperationPlanner:
  OperationPlan: operation, firmware, device, safety, toolchain
  canExecute: toolchain готов AND (если requiresRoot — root доступен)
  blockers(): список человекочитаемых блокировок
  OperationSafetyProvider -> DefaultOperationSafetyProvider (статическая таблица)

AppRuntimeProfile:
  data class: device, firmware, activeOperation, operationPlan
  hasFirmware, hasReadyOperation, summary
  AppRuntimeProfileResolver: initial(), withFirmware(fw), withOperation(fw, op)

manifest:
  pio/src/main/assets/toolchain/manifest.json — 13 инструментов
  (busybox, lpunpack, lpmake, simg2img, img2simg, mke2fs, e2fsdroid, resize2fs,
   make_ext4fs, mkfs.erofs, extract.erofs, magiskboot, brotli)

чистота слоёв:
  runtime/        -> БЕЗ shell, БЕЗ UI, БЕЗ android.app.* (Build.* через provider ок)
  toolchain/      -> БЕЗ shell, БЕЗ UI, БЕЗ android.app.*
  operations/     -> БЕЗ shell, БЕЗ UI, БЕЗ android.app.*
```

Внесённые изменения:

```text
common/runtime:
  + DeviceProfile.kt
  + DeviceProfileProvider.kt
  + AppRuntimeProfile.kt

common/toolchain:
  + ToolRequirement.kt
  + ToolchainResolver.kt
  + ToolManifestLoader.kt
  + CapabilityBasedToolchainResolver.kt

common/operations:
  + OperationSafetyProfile.kt
  + OperationPlanner.kt

common/src/test:
  + runtime/DeviceProfileTest.kt
  + runtime/AppRuntimeProfileTest.kt
  + toolchain/ToolchainResolverTest.kt
  + operations/OperationPlannerTest.kt

common/build.gradle:
  + testImplementation org.json:json:20240303 (для ToolManifestLoader в JVM-тестах)

pio/src/main/assets/toolchain:
  + manifest.json

tools:
  + tools/check-runtime-profiles.py
  * tools/check-architecture.py — добавлены runtime/toolchain/operations слои

ci:
  + .github/workflows/runtime-profiles.yml

docs:
  + docs/ru/dev/runtime-profiles.md
  + docs/en/dev/runtime-profiles.md
```

Проверка этапа:

```bash
python3 tools/check-runtime-profiles.py
```

Ожидаемый результат:

```text
PASS: DeviceProfile + DeviceProfileProvider are in place
PASS: ToolchainProfile + ToolRequirement + ToolDescriptor are declared
PASS: ToolchainResolver interface + CapabilityBasedToolchainResolver implementation
PASS: ToolManifestLoader parses JSON manifest
PASS: OperationSafetyProfile + OperationPlanner + OperationPlan are declared
PASS: AppRuntimeProfile + AppRuntimeProfileResolver combine all profiles
PASS: assets/toolchain/manifest.json declares required tools
PASS: Stage 10 JVM unit tests are present
PASS: common/build.gradle declares org.json testImplementation
PASS: runtime/toolchain layers are pure (no legacy shell, no android.app.*)
PASS: operations layer uses shell.runtime (typed) instead of legacy shell
```

### Этап 11 — ToolchainInstaller

```text
статус: завершено
цель: извлекать bundled-бинарники из assets по манифесту и проверять SHA-256
```

Внесённые изменения:

```text
common/toolchain:
  + ToolchainInstaller.kt          # sealed ToolchainInstallResult + класс установщика

common/src/test:
  + toolchain/ToolchainInstallerTest.kt   # 9 JVM-тестов

tools:
  + tools/check-toolchain-installer.py

ci:
  + .github/workflows/toolchain-installer.yml

docs:
  + docs/{en,ru}/dev/toolchain-installer.md
```

Проверка этапа:

```bash
python3 tools/check-toolchain-installer.py
```

### Этап 12 — OperationExecutor

```text
статус: завершено
цель: превращать OperationPlan в ShellCommand и запускать через ShellRuntime
```

Внесённые изменения:

```text
common/operations:
  + OperationExecutor.kt           # PreparedExecution + OperationExecutor + OperationExecutionException

common/src/test:
  + operations/OperationExecutorTest.kt   # 11 JVM-тестов

tools:
  + tools/check-operation-executor.py

docs:
  + docs/{en,ru}/dev/operation-executor.md
```

Проверка этапа:

```bash
python3 tools/check-operation-executor.py
```

### Этап 13 — targetSdk 35 runtime migration

```text
статус: завершено
цель: поднять targetSdkVersion с 28 до 35 и добавить runtime-permission + foreground-service инфраструктуру
```

Внесённые изменения:

```text
build.gradle:
  * targetSdkVersion: 28 -> 35

pio/src/main/AndroidManifest.xml:
  * READ_EXTERNAL_STORAGE capped maxSdkVersion=32
  * WRITE_EXTERNAL_STORAGE capped maxSdkVersion=29
  + POST_NOTIFICATIONS (Android 13+)
  + FOREGROUND_SERVICE + FOREGROUND_SERVICE_DATA_SYNC (Android 14+)
  + requestLegacyExternalStorage="false"
  + <service> FirmwareOperationService с foregroundServiceType=dataSync

pio/src/main/java/com/mio/kitchen:
  + FirmwareOperationService.kt     # foreground service для Stage 12 executor'а

pio/src/main/java/com/mio/kitchen/ui/modern:
  + RuntimePermissionHelper.kt      # абстракция runtime-разрешений

tools:
  * tools/check-build-modernization.py — updated для targetSdk=35
  + tools/check-targetsdk-35.py

docs:
  + docs/{en,ru}/dev/targetsdk-35.md
```

Проверка этапа:

```bash
python3 tools/check-targetsdk-35.py
```

### Этап 14 — Folder/tree URI export policy

```text
статус: завершено
цель: типизированная политика экспорта файлов из workspace на targetSdk 35
```

Внесённые изменения:

```text
common/storage:
  + ExportPolicy.kt                 # sealed ExportPolicy + ExportResult + ExportOptions + WorkspaceExporter
  + AndroidWorkspaceExporter.kt     # Android-реализация (SAF tree + MediaStore + app-private)

tools:
  + tools/check-export-policy.py

docs:
  + docs/{en,ru}/dev/export-policy.md
```

Проверка этапа:

```bash
python3 tools/check-export-policy.py
```

### Этап 15 — UI migration to AppRuntimeProfile

```text
статус: завершено
цель: reference-активность, связывающая все компоненты этапов 4-14
```

Внесённые изменения:

```text
pio/src/main/java/com/mio/kitchen/ui/modern:
  + FirmwareAnalysisActivity.kt     # reference-экран анализа прошивки

pio/src/main/AndroidManifest.xml:
  + <activity> FirmwareAnalysisActivity

tools:
  + tools/check-ui-migration.py

docs:
  + docs/{en,ru}/dev/ui-migration.md
```

Проверка этапа:

```bash
python3 tools/check-ui-migration.py
```

### Этап 16 — Static analysis

```text
статус: завершено
цель: Detekt + ktlint + Android Lint + ShellCheck конфигурация
```

Внесённые изменения:

```text
config/:
  + detekt/detekt.yml               # Detekt правила
  + lint.xml                        # Android Lint overrides
  + .shellcheckrc                   # ShellCheck конфигурация

.editorconfig                         # ktlint + общий code-style

build.gradle (root):
  + id 'io.gitlab.arturbosch.detekt' version '1.23.7'
  + id 'org.jlleitschuh.gradle.ktlint' version '12.1.1'
  + runStaticAnalysis aggregate task

{common,krscript,pio}/build.gradle:
  + plugins detekt + ktlint
  + lint { lintConfig ... }
  + detekt { ... }
  + ktlint { ... }

tools:
  + tools/check-static-analysis.py

docs:
  + docs/{en,ru}/dev/static-analysis.md
```

Проверка этапа:

```bash
python3 tools/check-static-analysis.py
```

### Этап 17 — Coverage reporting

```text
статус: завершено
цель: Kover coverage для JVM unit-тестов
```

Внесённые изменения:

```text
build.gradle (root):
  + id 'org.jetbrains.kotlinx.kover' version '0.8.3'
  + runCoverageReport aggregate task

{common,krscript,pio}/build.gradle:
  + id 'org.jetbrains.kotlinx.kover'

tools:
  + tools/check-coverage.py

docs:
  + docs/{en,ru}/dev/coverage.md
```

Проверка этапа:

```bash
python3 tools/check-coverage.py
```

### Этап 18 — Instrumented test scaffolding

```text
статус: завершено
цель: instrumented-тесты для компонентов, требующих реальный Android
```

Внесённые изменения:

```text
common/src/androidTest/java/com/omarea/common:
  * ExampleInstrumentedTest.java (refreshed)
  + storage/FirmwareWorkspaceInstrumentedTest.kt
  + firmware/ZipFirmwareAnalyzerInstrumentedTest.kt

pio/src/androidTest/java/com/mio/kitchen/ui/modern:
  + RuntimePermissionHelperInstrumentedTest.kt

tools:
  + tools/check-instrumented-tests.py

docs:
  + docs/{en,ru}/dev/instrumented-tests.md
```

Проверка этапа:

```bash
python3 tools/check-instrumented-tests.py
```

### Этап 19 — Final integration: user docs

```text
статус: завершено
цель: user-facing документация на двух языках (RU/EN)
```

Внесённые изменения:

```text
docs/en/user/:
  + quick-start.md
  + choose-rom.md
  + unpack.md
  + modify.md
  + pack.md
  + flash-safety.md
  + storage-access.md
  + root-mode.md
  + troubleshooting.md

docs/ru/user/:
  + quick-start.md
  + choose-rom.md
  + unpack.md
  + modify.md
  + pack.md
  + flash-safety.md
  + storage-access.md
  + root-mode.md
  + troubleshooting.md

tools:
  + tools/check-user-docs.py
  + tools/check-docs-monolingual.py   # (added earlier in this stage)

docs:
  + docs/{en,ru}/dev/toolchain-installer.md
  + docs/{en,ru}/dev/operation-executor.md
  + docs/{en,ru}/dev/targetsdk-35.md
  + docs/{en,ru}/dev/export-policy.md
  + docs/{en,ru}/dev/ui-migration.md
  + docs/{en,ru}/dev/static-analysis.md
  + docs/{en,ru}/dev/coverage.md
  + docs/{en,ru}/dev/instrumented-tests.md
```

Проверка этапа:

```bash
python3 tools/check-user-docs.py
python3 tools/check-docs-monolingual.py
```

### Финальная проверка всех этапов

```bash
python3 tools/run-all-checks.py
```

Ожидаемый результат:

```text
ALL CHECKS PASSED
```

### Этап 20 — Hybrid migration layer

```text
статус: завершено
цель: параллельный singleton AppRuntimeStore, который зеркалит legacy-состояние в AppRuntimeProfile; integration во все 4 активности
```

Внесённые изменения:

```text
pio/src/main/java/com/mio/kitchen/ui/modern:
  + AppRuntimeStore.kt              # singleton-мост (StateFlow<AppRuntimeProfile?>)

pio/src/main/java/com/mio/kitchen:
  * SplashActivity.kt               # +AppRuntimeStore.init +updateRootStatus (legacy preserved)
  * MainActivity.kt                 # +AppRuntimeStore.init +RuntimePermissionHelper (legacy storage request removed)
  * ActivityFileSelector.kt         # +RuntimePermissionHelper (legacy storage request removed)
  * ActionPage.kt                   # +AppRuntimeStore.setFirmware после URI resolution (legacy gateway preserved)

tools:
  + tools/check-hybrid-migration.py

docs:
  + docs/{en,ru}/dev/hybrid-migration.md
```

Проверка этапа:

```bash
python3 tools/check-hybrid-migration.py
```

### Этап 21 — ToolchainInstaller wiring

```text
статус: завершено
цель: автоматически запускать ToolchainInstaller в FirmwareOperationService.onCreate
```

Внесённые изменения:

```text
pio/src/main/java/com/mio/kitchen:
  * FirmwareOperationService.kt     # +onCreate +ensureToolchainInstalled + SupervisorJob + Mutex + lastInstalledToolsDir

docs:
  + docs/{en,ru}/dev/toolchain-wiring.md
```

Проверка этапа:

```bash
python3 tools/check-hybrid-migration.py
```

### Финальная проверка всех этапов

```bash
python3 tools/run-all-checks.py
```

Ожидаемый результат:

```text
ALL CHECKS PASSED
```

### Этап 22 — Legacy removal

```text
статус: завершено
цель: удалить legacy KeepShell/PageConfigReader/ScriptEnvironmen (~1300 строк), заменить на новую архитектуру через LegacyShellBridge
```

Внесённые изменения:

```text
удалено:
  - common/src/main/java/com/omarea/common/shell/KeepShell.kt (171 строка)
  - krscript/src/main/java/com/omarea/krscript/config/PageConfigReader.kt (735 строк)
  - krscript/src/main/java/com/omarea/krscript/executor/ScriptEnvironmen.java (395 строк)

добавлено:
  + krscript/src/main/java/com/omarea/krscript/runtime/LegacyShellBridge.kt
  + krscript/src/main/java/com/omarea/krscript/executor/ScriptEnvironmen.kt (тонкий фасад)
  + krscript/src/main/java/com/omarea/krscript/config/PageConfigLoader.kt

переписано:
  * common/src/main/java/com/omarea/common/shell/KeepShellPublic.kt (теперь фасад)
  * common/src/main/java/com/omarea/common/shell/runtime/KeepShellRuntime.kt (Runtime.exec напрямую)
  * pio/src/main/java/com/mio/kitchen/permissions/CheckRootStatus.kt (Runtime.exec напрямую)
  * pio/src/main/java/com/mio/kitchen/KrScriptConfig.java (убран ScriptEnvironmen.init)
  * pio/src/main/java/com/mio/kitchen/SplashActivity.kt (+LegacyShellBridge.init)
  * pio/src/main/java/com/mio/kitchen/MainActivity.kt (+LegacyShellBridge.init, PageConfigLoader)
  * pio/src/main/java/com/mio/kitchen/ActionPage.kt (PageConfigLoader вместо PageConfigReader)
  * krscript/src/main/java/com/omarea/krscript/config/PageConfigSh.kt (PageConfigLoader)

tools:
  + tools/check-legacy-removal.py
  * tools/validate-localization.py — accepts .kt ScriptEnvironmen
  * tools/check-parser-split.py — PageConfigLoader вместо PageConfigReader

docs:
  + docs/{en,ru}/dev/legacy-removal.md
```

Проверка этапа:

```bash
python3 tools/check-legacy-removal.py
```

Ожидаемый результат:

```text
PASS: legacy KeepShell.kt, PageConfigReader.kt, ScriptEnvironmen.java removed
PASS: LegacyShellBridge singleton bridges legacy API to ShellRuntime
PASS: ScriptEnvironmen.kt is a thin facade over LegacyShellBridge
PASS: PageConfigLoader replaces PageConfigReader (uses PageConfigRepository)
PASS: KeepShellPublic is now a facade (no more KeepShell instance)
PASS: KeepShellRuntime uses Runtime.exec() directly (no more KeepShell wrapper)
PASS: CheckRootStatus probes root via Runtime.exec (no more KeepShellPublic.checkRoot)
PASS: MainActivity/ActionPage/SplashActivity/PageConfigSh use new API
PASS: No legacy KeepShell class references in code (comments allowed)
```

### Этап 23 — Final legacy cleanup

```text
статус: завершено
цель: восстановить streaming-выполнение (ShellExecutor), заменить ExtractAssets на ToolchainInstaller, убрать последнюю зависимость SplashActivity от common/shell/ShellExecutor
```

Внесённые изменения:

```text
удалено:
  - krscript/src/main/java/com/omarea/krscript/executor/ShellExecutor.java

добавлено:
  + krscript/src/main/java/com/omarea/krscript/executor/ShellExecutor.kt (Runtime.exec напрямую + LegacyShellBridge.buildStreamingCommand)

переписано:
  * krscript/src/main/java/com/omarea/krscript/executor/ScriptEnvironmen.kt (getRuntime() возвращает реальный Process)
  * krscript/src/main/java/com/omarea/krscript/runtime/LegacyShellBridge.kt (+buildStreamingCommand +installToolkit +resolveScriptForStreaming +isRooted)
  * pio/src/main/java/com/mio/kitchen/SplashActivity.kt (BeforeStartThread использует LegacyShellBridge.buildStreamingCommand)
  * pio/src/main/assets/toolchain/manifest.json (расширен до 21 файла: +utils +7 .so библиотек)

tools:
  + tools/check-final-cleanup.py

docs:
  + docs/{en,ru}/dev/final-cleanup.md
```

Проверка этапа:

```bash
python3 tools/check-final-cleanup.py
```

Ожидаемый результат:

```text
PASS: ShellExecutor.java replaced by ShellExecutor.kt (uses Runtime.exec directly)
PASS: ShellExecutor.execute() uses LegacyShellBridge.buildStreamingCommand (not ScriptEnvironmen.getRuntime)
PASS: ScriptEnvironmen.getRuntime() returns a real Process (not null)
PASS: LegacyShellBridge.buildStreamingCommand + installToolkit are declared
PASS: SplashActivity.BeforeStartThread uses new API (no common.shell.ShellExecutor dependency)
PASS: Manifest covers all 21 assets/bin/ files (13 executables + 8 shared libs/utils)
PASS: LegacyShellBridge.init uses ToolchainInstaller (not ExtractAssets) for toolkit
```

### Финальная проверка всех этапов

```bash
python3 tools/run-all-checks.py
```

Ожидаемый результат:

```text
ALL CHECKS PASSED
```

---

## 0. Ключевое решение v3

Предыдущая идея с пользовательскими `legacyApi` / `modernApi` build-вариантами отклоняется.

Правильная продуктовая модель для MIO-KITCHEN:

```text
Один пользовательский APK / AAB
  -> одна иконка
  -> одно приложение
  -> один набор функций
  -> умеет работать с прошивками Android 10 / 11 / 12 / 13 / 14 / 15+
  -> внутри автоматически выбирает нужный runtime, storage, shell и toolchain strategy
```

Build-варианты можно оставить **только как внутренний инструмент разработки и тестирования**, например:

```text
debug
release
benchmark
internalQa
```

Но не как разные пользовательские приложения:

```text
не надо:
  MIO-KITCHEN Legacy.apk
  MIO-KITCHEN Modern.apk

надо:
  MIO-KITCHEN.apk
```

---

## 1. Почему один APK — правильнее для этого проекта

MIO-KITCHEN — не обычный файловый менеджер и не обычное Android-приложение. Это firmware kitchen, где пользователь ожидает такой сценарий:

```text
скачал одно приложение
выбрал прошивку
приложение само поняло формат
распаковало
дало модифицировать
запаковало обратно
при необходимости проверило / подписало / подготовило flash-сценарий
```

Пользователь не должен думать:

```text
эта прошивка от Android 10 — значит мне нужен legacy APK?
эта прошивка от Android 15 — значит нужен modern APK?
а если я запускаю приложение на Android 12, но прошивка Android 10?
а если телефон старый, но ROM новый?
```

Такая модель неудобная и технически неверная.

Нужно разделять четыре разные вещи:

| Понятие | Что означает | Как должно использоваться |
|---|---|---|
| **Android runtime устройства** | Android, на котором запущено приложение | влияет на permissions, storage, process, SAF, root behavior |
| **`minSdkVersion` приложения** | минимальная версия Android, куда APK можно установить | должна оставаться как можно ниже, если зависимости позволяют |
| **`targetSdkVersion` приложения** | версия Android, под поведение которой приложение собрано и протестировано | нужна для совместимости с современными системами и публикацией |
| **Android-версия прошивки** | версия Android внутри ROM/OTA/image | влияет на форматы образов, разделов, boot image, AVB, compression |

Главное:

```text
Поддержка прошивок Android 10–15 не должна завязываться на разные APK.
Она должна завязываться на firmware capabilities.
```

---

## 2. Исправленная стратегия SDK

### 2.1. Базовая рекомендация

```text
compileSdk:
  актуальный стабильный SDK, доступный проекту

targetSdk:
  один общий modern target для единственного пользовательского APK

minSdk:
  сохранить текущий minSdkVersion 21, если обновлённые зависимости и тесты позволяют
```

В текущем проекте:

```text
compileSdkVersion 28
minSdkVersion 21
targetSdkVersion 28
```

Цель:

```text
compileSdkVersion: актуальный
minSdkVersion: 21, если возможно
targetSdkVersion: современный общий target
```

При этом:

```text
targetSdkVersion не определяет, какие прошивки приложение умеет распаковывать.
```

Он определяет, какие системные правила Android применяются к самому приложению: storage, permissions, background restrictions, compatibility behavior.

### 2.2. Что делать с идеей `targetSdkVersion 28`

`targetSdkVersion 28` можно рассматривать как историческую причину, почему старое приложение проще работало с файлами и root/path-сценариями.

Но в новой архитектуре не надо держать весь продукт на старом target ради этого. Вместо этого нужно сделать compatibility layer:

```text
StorageCompatibilityLayer
RootCompatibilityLayer
PathCompatibilityLayer
ShellCompatibilityLayer
```

То есть:

```text
не:
  targetSdk 28 = поддержка старых прошивок

а:
  runtime capability detection + storage adapters + workspace + toolchain profiles = поддержка старых и новых прошивок
```

### 2.3. Когда target 28 всё же может понадобиться

Только как **внутренний исследовательский build** для проверки старого поведения:

```text
internalLegacyExperimentDebug
```

Но не как основной пользовательский APK.

Назначение такого build:

```text
сравнить старую и новую storage-логику
понять, где ломаются path/root-сценарии
снять регрессионные логи
проверить старые устройства
```

Этот build не должен быть официальной пользовательской линией.

---

## 3. Новая целевая модель: один APK, несколько runtime-профилей

Вместо нескольких APK вводится система runtime-профилей.

```text
MIO-KITCHEN.apk
  |
  +-- AppRuntimeProfileResolver
        |
        +-- DeviceProfile
        +-- StorageProfile
        +-- RootProfile
        +-- FirmwareProfile
        +-- ToolchainProfile
        +-- OperationSafetyProfile
```

### 3.1. DeviceProfile

Определяет Android, на котором запущено приложение.

```kotlin
data class DeviceProfile(
    val sdkInt: Int,
    val manufacturer: String,
    val model: String,
    val abiList: List<String>,
    val isEmulator: Boolean,
    val supports16KbPageSize: Boolean?,
    val hasRoot: Boolean?,
    val selinuxMode: SelinuxMode?
)
```

Используется для выбора:

```text
storage behavior
root behavior
tool binary ABI
проверок совместимости
ограничений Android 10/11/12/13/14/15+
```

### 3.2. StorageProfile

Определяет, как приложение получает доступ к файлам.

```kotlin
sealed interface StorageProfile {
    data object SafWorkspace : StorageProfile
    data object AppWorkspaceOnly : StorageProfile
    data object LegacyDirectPath : StorageProfile
    data object RootDirectPath : StorageProfile
    data object AllFilesAccess : StorageProfile
}
```

Важное правило:

```text
UI может показывать один и тот же сценарий,
но внутри StorageProfile будет разный.
```

Например:

```text
Android 8/9:
  можно использовать direct path, если файл реально доступен

Android 10:
  direct path может ещё работать в части сценариев, но уже нужен режим совместимости через workspace/SAF/root adapters

Android 11+:
  SAF/workspace-first

Root-сценарий:
  можно использовать root path access, но через отдельную проверенную ветку
```

### 3.3. RootProfile

```kotlin
sealed interface RootProfile {
    data object NoRoot : RootProfile
    data object RootAvailable : RootProfile
    data object RootRequired : RootProfile
    data object DryRunOnly : RootProfile
}
```

Root не должен быть глобальной переменной.

Сейчас в старом коде root/shell состояние фактически размазано по `ScriptEnvironmen`, shell helpers и UI. Нужно сделать root отдельным профилем:

```text
operation asks required permissions
runtime checks profile
UI показывает понятное предупреждение
shell runner получает разрешённый режим
```

### 3.4. FirmwareProfile

Самый важный профиль.

Он описывает не Android-устройство, а выбранную прошивку.

```kotlin
data class FirmwareProfile(
    val source: FirmwareSource,
    val packageType: FirmwarePackageType,
    val androidVersion: AndroidVersionHint?,
    val capabilities: FirmwareCapabilities,
    val partitions: List<PartitionInfo>,
    val compression: Set<CompressionType>,
    val warnings: List<FirmwareWarning>
)
```

```kotlin
data class FirmwareCapabilities(
    val hasPayloadBin: Boolean = false,
    val hasSuperImage: Boolean = false,
    val hasDynamicPartitions: Boolean = false,
    val hasSparseImages: Boolean = false,
    val hasErofs: Boolean = false,
    val hasExt4: Boolean = false,
    val hasF2fs: Boolean = false,
    val hasBootImage: Boolean = false,
    val hasVendorBootImage: Boolean = false,
    val hasInitBootImage: Boolean = false,
    val hasDtboImage: Boolean = false,
    val hasVbmetaImage: Boolean = false,
    val usesAvb: Boolean = false,
    val usesA/B: Boolean = false,
    val usesVirtualAB: Boolean = false,
    val usesCompressionZstd: Boolean = false,
    val usesCompressionBr: Boolean = false,
    val usesCompressionLz4: Boolean = false,
    val requires16KbAlignmentCheck: Boolean = false
)
```

### 3.5. ToolchainProfile

Определяет, какие бинарники и скрипты нужны для конкретной операции.

```kotlin
data class ToolchainProfile(
    val requiredTools: List<ToolRequirement>,
    val optionalTools: List<ToolRequirement>,
    val selectedAbi: String,
    val toolsDir: File,
    val verifiedChecksums: Boolean
)

data class ToolRequirement(
    val name: String,
    val minVersion: String?,
    val purpose: ToolPurpose,
    val checksum: String?,
    val required: Boolean
)
```

Пример:

```text
payload.bin detected:
  payload-dumper / update_engine parser / protobuf tooling

super.img detected:
  lpunpack / lpmake / simg2img / img2simg

EROFS detected:
  fsck.erofs / dump.erofs / mkfs.erofs

boot/vendor_boot/init_boot detected:
  unpack_bootimg / mkbootimg / magiskboot-compatible path

AVB detected:
  avbtool / vbmeta parser / verification metadata
```

### 3.6. OperationSafetyProfile

Для опасных операций:

```kotlin
data class OperationSafetyProfile(
    val operation: FirmwareOperation,
    val isDestructive: Boolean,
    val requiresBackup: Boolean,
    val requiresRoot: Boolean,
    val requiresDeviceConnection: Boolean,
    val supportsDryRun: Boolean,
    val confirmationLevel: ConfirmationLevel
)
```

Например:

```text
unpack:
  safe, no root

modify workspace:
  safe, no root

pack:
  medium risk, validate output

flash:
  destructive, root/device required, dry-run first, backup warning
```

---

## 4. Пользовательский flow в одном APK

### 4.1. Старый подход

```text
пользователь выбирает действие
XML вызывает shell
shell ожидает path
ошибка проявляется поздно
```

### 4.2. Новый подход

```text
1. Пользователь выбирает ROM / OTA / image / папку
2. StorageGateway открывает источник
3. WorkspaceManager копирует или подключает источник безопасным способом
4. FirmwareAnalyzer определяет формат и capabilities
5. OperationPlanner строит план операций
6. ToolchainResolver проверяет нужные инструменты
7. ScriptRuntime запускает shell/Kotlin/native operations
8. UI показывает typed progress/events
9. Regression log сохраняет шаги и checksum
```

### 4.3. Внешне для пользователя

Пользователь видит:

```text
Выбрана прошивка:
  Xiaomi / Android 13 / payload.bin / dynamic partitions / AVB

Доступные действия:
  Распаковать
  Изменить build.prop
  Распаковать boot.img
  Пересобрать boot.img
  Проверить vbmeta
  Собрать пакет
  Flash / подготовить flash script
```

Пользователь не видит:

```text
legacyApi / modernApi
targetSdk
storage implementation
tool binary choice
```

---

## 5. Архитектурное правило: firmware version != app SDK version

В проекте нужно прямо закрепить это в документации:

```text
Версия Android прошивки определяется анализом содержимого прошивки.
Версия Android устройства определяется через Build.VERSION.SDK_INT.
targetSdkVersion определяет режим совместимости приложения с Android-платформой.
Эти три понятия нельзя смешивать.
```

Пример:

```text
Сценарий A:
  приложение запущено на Android 15
  пользователь открывает прошивку Android 10
  runtime: Android 15 storage/permission profile
  firmware engine: Android 10/dynamic/sparse/ext4/boot-v2 profile

Сценарий B:
  приложение запущено на Android 10
  пользователь открывает прошивку Android 15
  runtime: Android 10 storage profile
  firmware engine: Android 15/super/erofs/init_boot/AVB/zstd profile

Сценарий C:
  приложение запущено на Android 8
  пользователь открывает старую прошивку Android 9
  runtime: legacy device profile
  firmware engine: legacy ext4/sparse/boot image profile
```

---

## 6. Storage architecture для одного APK

### 6.1. Единый публичный API

```kotlin
interface StorageGateway {
    suspend fun openInput(source: UserSelectedSource): OpenedFirmwareSource
    suspend fun prepareWorkspace(source: OpenedFirmwareSource): Workspace
    suspend fun exportFile(file: WorkspaceFile, destination: UserSelectedDestination): ExportResult
}
```

### 6.2. Реализации внутри

```text
StorageGateway
  |
  +-- SafStorageAdapter
  +-- DirectPathAdapter
  +-- RootPathAdapter
  +-- AppWorkspaceAdapter
  +-- AllFilesAccessAdapter
```

UI не должен знать, какой adapter выбран.

### 6.3. Правило workspace-first

Shell плохо работает с `content://` URI. Поэтому современный общий подход:

```text
URI / external file
  -> проверка размера/checksum
  -> copy/stream into app workspace
  -> shell получает обычный file path внутри workspace
  -> результат экспортируется обратно через SAF / MediaStore / выбранную папку
```

Это решает сразу несколько проблем:

```text
одинаковый shell path на разных Android
меньше зависимости от _data
легче тестировать
легче считать checksum
легче делать rollback
легче поддерживать scoped storage
```

### 6.4. Legacy direct path — не отдельный APK, а режим совместимости

```kotlin
class StoragePolicyResolver(
    private val deviceProfile: DeviceProfile,
    private val userSettings: StorageUserSettings,
    private val rootProfile: RootProfile
) {
    fun resolve(source: UserSelectedSource): StorageProfile {
        // SAF/workspace by default
        // direct path only if safe and available
        // root path only for root operations
        // all-files only if granted and needed
    }
}
```

### 6.5. Настройка для продвинутых пользователей

В одном APK можно сделать advanced setting:

```text
Файловый доступ:
  Автоматически
  SAF + рабочая папка
  Прямой путь, если доступен
  Root-доступ к пути
  All files access, если разрешено системой
```

По умолчанию:

```text
Автоматически
```

---

## 7. Firmware engine для Android 10–15+ прошивок

### 7.1. FirmwareAnalyzer

```kotlin
interface FirmwareAnalyzer {
    suspend fun analyze(source: FirmwareSource): FirmwareProfile
}
```

Реализации:

```text
ZipFirmwareAnalyzer
PayloadBinAnalyzer
SuperImageAnalyzer
SparseImageAnalyzer
BootImageAnalyzer
VbmetaAnalyzer
FilesystemImageAnalyzer
```

### 7.2. Firmware format detection

```text
ROM.zip:
  искать payload.bin
  искать images/*.img
  искать META-INF/com/android/metadata
  искать dynamic_partitions_op_list
  искать care_map.pb
  искать apex/apk/system/product/vendor структуры

payload.bin:
  читать manifest
  определить partitions, compression, hash, operations

super.img:
  определить sparse/raw
  lpunpack metadata
  logical partitions

*.img:
  определить boot/vendor_boot/init_boot/recovery/dtbo/vbmeta/filesystem
  определить sparse/raw
  определить ext4/erofs/f2fs
```

### 7.3. Android 10

Фокус:

```text
dynamic partitions
super.img
payload.bin
AVB/vbmeta
system-as-root legacy transitions
DTB in boot image для части устройств
```

### 7.4. Android 11

Фокус:

```text
dynamic partitions шире
Virtual A/B scenarios
vendor_boot на GKI-устройствах
strict AVB metadata
```

### 7.5. Android 12

Фокус:

```text
boot image header v3/v4
vendor_boot
bootconfig
GKI assumptions
```

### 7.6. Android 13

Фокус:

```text
init_boot для generic ramdisk
vendor_boot / boot split
AVB chaining
EROFS adoption у некоторых OEM
```

### 7.7. Android 14

Фокус:

```text
EROFS чаще
zstd/lz4/br combinations в OTA
stricter partition metadata
init_boot/vendor_boot/vbmeta checks
```

### 7.8. Android 15+

Фокус:

```text
16 KB page-size compatibility checks для native tools
alignment checks
новые boot/vendor_boot/init_boot layouts
актуальные AVB expectations
```

Важно: это не жёсткая таблица "Android version -> behavior". OEM могут использовать разные backports и собственные форматы. Поэтому приоритет должен быть такой:

```text
1. определить capabilities по файлам
2. использовать androidVersion только как hint
3. никогда не строить operation plan только по номеру Android
```

---

## 8. Toolchain architecture

### 8.1. Текущая проблема

Сейчас `assets/bin/*` — это просто набор бинарников.

Нужно добавить metadata:

```text
assets/toolchain/manifest.json
```

Пример:

```json
{
  "tools": [
    {
      "name": "lpunpack",
      "version": "android-tools-rXX",
      "abi": ["arm64-v8a", "armeabi-v7a"],
      "sha256": "...",
      "capabilities": ["super.img", "dynamic-partitions"],
      "source": "AOSP",
      "license": "Apache-2.0"
    },
    {
      "name": "mkfs.erofs",
      "version": "...",
      "abi": ["arm64-v8a"],
      "sha256": "...",
      "capabilities": ["erofs-pack"],
      "source": "...",
      "license": "..."
    }
  ]
}
```

### 8.2. ToolchainResolver

```kotlin
interface ToolchainResolver {
    suspend fun resolve(
        operation: FirmwareOperation,
        profile: FirmwareProfile,
        device: DeviceProfile
    ): ToolchainPlan
}
```

### 8.3. Проверки перед запуском

```text
tool exists
tool executable
ABI matches device
checksum matches manifest
tool supports required capability
output workspace writable
enough free space
operation supports dry-run
```

### 8.4. 16 KB page-size

Для Android 15+ нужно добавить тесты совместимости bundled native tools:

```text
ELF alignment check
load/run smoke test on 16 KB emulator/device
native crash regression tests
toolchain metadata flag:
  supports16KbPageSize: true/false/unknown
```

---

## 9. XML DSL / krscript: оставить, но сделать typed runtime

### 9.1. Что оставить

XML DSL — хорошая идея.

Оставить:

```text
home.xml
mio.xml
more.xml
ActionNode
PageNode
SwitchNode
PickerNode
TextNode
GroupNode
support checks
dynamic summary/desc/options
```

### 9.2. Что изменить

Сейчас XML DSL слишком напрямую связан с shell/UI.

Нужно:

```text
XML parse
  -> typed node model
  -> validation
  -> operation binding
  -> UI rendering
  -> runtime execution
```

### 9.3. Новые интерфейсы

```kotlin
interface PageConfigSource {
    suspend fun load(pageId: String): String
}

interface PageConfigParser {
    fun parse(xml: String): PageConfig
}

interface PageConfigValidator {
    fun validate(config: PageConfig): ValidationReport
}

interface PageRuntimeBinder {
    fun bind(config: PageConfig, runtime: RuntimeRegistry): BoundPage
}
```

### 9.4. PageConfigReader

Сейчас `PageConfigReader.kt` делает слишком много.

Разделить:

```text
PageConfigReader.kt
  -> XmlPageConfigSource.kt
  -> PageConfigParser.kt
  -> NodeParser.kt
  -> ParamsParser.kt
  -> DynamicValueResolver.kt
  -> PageConfigValidator.kt
  -> PageConfigRepository.kt
```

Главное правило:

```text
parser не выполняет shell
parser не показывает Toast
parser не знает Activity/Fragment
parser только строит модель и validation report
```

---

## 10. Shell runtime: один API для всех Android и всех прошивок

### 10.1. Единый API

```kotlin
interface ShellRuntime {
    fun execute(command: ShellCommand): Flow<ShellEvent>
    suspend fun executeForResult(command: ShellCommand): ShellResult
}
```

```kotlin
data class ShellCommand(
    val id: String,
    val script: ScriptSource,
    val env: Map<String, String>,
    val workingDir: File,
    val requiresRoot: Boolean,
    val timeoutMs: Long?,
    val safety: OperationSafetyProfile
)
```

```kotlin
sealed interface ShellEvent {
    data class Stdout(val line: String) : ShellEvent
    data class Stderr(val line: String) : ShellEvent
    data class Progress(val percent: Int?, val message: String?) : ShellEvent
    data class Warning(val message: String) : ShellEvent
    data class Error(val message: String, val cause: Throwable? = null) : ShellEvent
    data class Completed(val exitCode: Int) : ShellEvent
}
```

### 10.2. Реализации

```text
ShellRuntime
  |
  +-- RootShellRuntime
  +-- UserShellRuntime
  +-- DryRunShellRuntime
  +-- FakeShellRuntime
```

### 10.3. Что убрать из старого кода

Постепенно убрать:

```text
ScriptEnvironmen.java как глобальный монолит
несколько ShellExecutor классов
ручные Thread/Handler
GlobalScope
shell stdout magic без typed parser
```

---

## 11. UI architecture для одного APK

### 11.1. Пользователь не выбирает технический режим

UI должен быть единый:

```text
Главная
  -> выбрать прошивку
  -> анализ
  -> действия
  -> лог/результат
```

### 11.2. Но advanced details доступны

На экране анализа можно показать:

```text
Прошивка:
  package: OTA zip
  android hint: 13
  partitions: system, vendor, product, odm
  dynamic: yes
  filesystem: EROFS
  AVB: yes
  compression: br/zstd
  risk: medium

Runtime:
  app running on Android 15
  storage: SAF workspace
  root: available
  toolchain: arm64 verified
```

Это полезно для продвинутых пользователей, но не заставляет их выбирать APK.

### 11.3. ViewModel/state

```kotlin
data class FirmwareScreenState(
    val selectedSource: UserSelectedSource?,
    val workspace: Workspace?,
    val firmwareProfile: FirmwareProfile?,
    val availableActions: List<FirmwareAction>,
    val warnings: List<UiWarning>,
    val isBusy: Boolean
)
```

---

## 12. Модульная структура без пользовательских flavors

### 12.1. Не делать user-facing flavors

```text
не надо:
  pio/src/legacyApi
  pio/src/modernApi
```

### 12.2. Делать внутренние слои

```text
:app
  Android UI, navigation, DI, app startup

:core:model
  Result, Error, logging, typed events

:core:compat
  DeviceProfile, API checks, Android runtime differences

:core:storage
  StorageGateway, SAF, workspace, direct path, root path

:core:shell
  ShellRuntime, ShellCommand, ShellEvent, root/user/dry-run runtime

:krscript:model
  typed XML node model

:krscript:parser
  XML parser and validation

:krscript:runtime
  binding XML actions to shell/firmware operations

:krscript:ui-views
  legacy View-based renderer, adapters, params UI

:firmware:model
  FirmwareProfile, partitions, capabilities

:firmware:analyzer
  zip/payload/super/img/boot/vbmeta/filesystem analyzers

:firmware:toolchain
  tool manifest, installer, checksum verifier, resolver

:firmware:operations
  unpack, pack, modify, verify, flash preparation

:testing
  shared fakes, fixtures, golden files, script test helpers
```

### 12.3. Переходный вариант

Сразу делать 12 Gradle-модулей необязательно.

Безопаснее:

```text
Этап 1:
  оставить :common, :krscript, :pio
  внутри создать новые пакеты storage/shell/firmware/runtime

Этап 2:
  когда API стабилен — вынести в Gradle modules

Этап 3:
  CI начинает проверять module boundaries
```

---

## 13. Где оставить Java, а где переходить на Kotlin

### 13.1. Общая стратегия

```text
один APK
Kotlin-first
новый код только Kotlin
старый Java не конвертировать пачкой
критичные Java-классы закрывать Kotlin-интерфейсами
после тестов заменять реализацию
```

### 13.2. Перепроектировать, не конвертировать строка-в-строку

```text
ScriptEnvironmen.java
ShellExecutor.java
FilePathResolver.java
ExtractAssets.java
KrScriptConfig.java
```

### 13.3. Можно временно оставить Java

```text
MTDataFilesProvider.java
FastBlurUtility.java
OverScroll*.java
AdapterFileSelector.java до замены file picker
```

### 13.4. Главное

Проблема проекта не в Java как языке.

Проблема:

```text
глобальное состояние
смешение UI/parser/shell/storage
нет typed errors
нет тестируемых interfaces
старые Android API patterns
```

Kotlin нужен не ради моды, а чтобы сделать:

```text
sealed result/error types
Flow для shell events
data classes для profiles
null-safety
корутины
testable interfaces
```

---

## 14. Тестовая стратегия для одного APK

### 14.1. Unit tests

```text
PageConfigParserTest
NodeParserTest
FirmwareAnalyzerTest
StoragePolicyResolverTest
ToolchainResolverTest
ShellEventParserTest
OperationPlannerTest
```

### 14.2. Golden tests

```text
home.xml -> expected PageConfig JSON
mio.xml -> expected PageConfig JSON
more.xml -> expected PageConfig JSON
sample OTA Android 10 -> expected FirmwareProfile
sample OTA Android 13 -> expected FirmwareProfile
sample boot.img v3 -> expected BootImageProfile
sample vbmeta.img -> expected AvbProfile
```

### 14.3. Architecture tests

Проверки:

```text
parser не импортирует android.app.*
parser не импортирует shell runtime
firmware analyzer не импортирует UI
storage layer не импортирует Activity/Fragment
UI не запускает shell напрямую
:firmware не зависит от :app
```

### 14.4. Static analysis

```text
Android Lint
Detekt
Ktlint
ShellCheck
Gradle dependency analysis
binary checksum verifier
license checker
forbidden API checker
```

### 14.5. Regression tests

```text
unpack_ext4_image
unpack_erofs_image
unpack_super_image
unpack_payload_bin
repack_ext4_image
repack_boot_image
verify_vbmeta_metadata
uri_copy_preserves_checksum
workspace_export_preserves_checksum
root_required_operation_without_root_fails_safely
flash_operation_dry_run_generates_expected_script
```

### 14.6. Compatibility matrix

Один APK тестируется на разных runtime Android:

| Runtime Android | Что проверить |
|---|---|
| API 21 | базовая установка, legacy file path, workspace |
| API 23 | runtime permissions |
| API 28 | старое storage/root поведение |
| API 29 | Android 10 scoped storage boundary |
| API 30 | Android 11 scoped storage enforcement |
| API 33 | modern permissions |
| API 35 | Android 15 behavior, foreground/service/storage checks |
| API 35 / 16 KB | native tool compatibility |

### 14.7. Firmware matrix

Тот же APK тестируется на разных прошивках:

| Firmware sample | Что проверить |
|---|---|
| Android 9/legacy sparse ext4 | backward compatibility |
| Android 10 OTA with payload.bin | payload extraction |
| Android 10 super.img | dynamic partitions |
| Android 11 dynamic partitions | lpunpack/lpmake plan |
| Android 12 boot/vendor_boot | boot image header |
| Android 13 init_boot | init_boot handling |
| Android 14 EROFS | erofs tools |
| Android 15 sample | zstd/erofs/16K/alignment checks |

---

## 15. Документация RU/EN

### 15.1. Документация для пользователей

```text
docs/ru/user/
  quick-start.md
  choose-rom.md
  unpack.md
  modify.md
  pack.md
  flash-safety.md
  storage-access.md
  root-mode.md
  troubleshooting.md

docs/en/user/
  quick-start.md
  choose-rom.md
  unpack.md
  modify.md
  pack.md
  flash-safety.md
  storage-access.md
  root-mode.md
  troubleshooting.md
```

Обязательно объяснить:

```text
одно приложение поддерживает разные прошивки
почему приложение копирует файлы в workspace
что такое root mode
что такое dry-run
какие операции опасны
как проверить checksum
```

### 15.2. Документация для разработчиков

```text
docs/ru/dev/
  architecture.md
  runtime-profiles.md
  storage-layer.md
  shell-runtime.md
  firmware-engine.md
  toolchain-manifest.md
  xml-dsl.md
  testing.md
  contributing.md

docs/en/dev/
  architecture.md
  runtime-profiles.md
  storage-layer.md
  shell-runtime.md
  firmware-engine.md
  toolchain-manifest.md
  xml-dsl.md
  testing.md
  contributing.md
```

### 15.3. KDoc / JavaDoc

Шаблон для классов:

```kotlin
/**
 * RU: Анализирует выбранный файл прошивки и возвращает профиль возможностей.
 *
 * EN: Analyzes a selected firmware source and returns a capability profile.
 *
 * RU: Класс не выполняет shell-команды и не изменяет файловую систему.
 * EN: This class does not execute shell commands and does not modify the file system.
 */
interface FirmwareAnalyzer {
    suspend fun analyze(source: FirmwareSource): FirmwareProfile
}
```

### 15.4. Документация как quality gate

CI должен проверять:

```text
новый public class -> есть KDoc
новый operation -> есть docs/ru + docs/en
новый XML action type -> обновлена DSL документация
новый tool -> обновлен toolchain manifest
новый dangerous operation -> обновлена safety documentation
```

---

## 16. Исправленный roadmap

### Этап 0 — Продуктовое решение

```text
закрепить: один пользовательский APK
удалить из плана user-facing legacy/modern flavors
оставить internal QA builds только для тестов
описать SDK/runtime/firmware separation
```

### Этап 1 — Baseline

```text
собрать текущий проект
сохранить baseline APK
зафиксировать текущие сценарии
собрать список sample прошивок и images
добавить smoke checklist
```

### Этап 2 — Hotfix

```text
FilePathResolver.saveFileFromUri checksum bug
ActionListFragment hidden shell mode
PageMenuLoader dynamic menu
MTDataFilesProvider flags bug
tool.sh flash_img переменные
Suffix2Mime mapping
requiret -> required в XML
```

### Этап 3 — Build modernization без user-facing flavors

```text
обновить Gradle/AGP/Kotlin
compileSdk -> актуальный
targetSdk -> один общий modern target
minSdk 21 сохранить, если возможно
убрать jcenter
убрать kotlin-android-extensions
включить ViewBinding
release signing исправить
lint не отключать
```

### Этап 4 — Runtime profiles

```text
DeviceProfile
StorageProfile
RootProfile
FirmwareProfile
ToolchainProfile
OperationSafetyProfile
AppRuntimeProfileResolver
```

### Этап 5 — Storage/workspace

```text
StorageGateway
WorkspaceManager
SafStorageAdapter
DirectPathAdapter
RootPathAdapter
AllFilesAccessAdapter
checksum copy tests
export tests
```

### Этап 6 — Firmware analyzer

```text
ZipFirmwareAnalyzer
PayloadBinAnalyzer
SuperImageAnalyzer
BootImageAnalyzer
VbmetaAnalyzer
FilesystemImageAnalyzer
FirmwareCapabilities
OperationPlanner
```

### Этап 7 — Shell runtime

```text
ShellRuntime
ShellCommand
ShellEvent
RootShellRuntime
UserShellRuntime
DryRunShellRuntime
FakeShellRuntime
timeout/cancel/session id
typed errors
```

### Этап 8 — krscript parser split

```text
PageConfigReader split
parser without shell
validator
dynamic value resolver
runtime binder
UI renderer separated
```

### Этап 9 — UI modernization

```text
ViewBinding
ViewModel
StateFlow
Activity Result API
no raw Thread/Handler
no activity!! / context!!
unified firmware analysis screen
```

### Этап 10 — Tests and CI

```text
unit tests
golden tests
architecture tests
regression tests
instrumented API matrix
shellcheck/bats
Dokka
coverage
artifact checksum
```

### Этап 11 — Documentation RU/EN

```text
user docs
developer docs
DSL docs
toolchain docs
safety docs
KDoc/Javadoc
Dokka HTML/Markdown
```

---

## 17. Что конкретно изменить в предыдущем Markdown-плане v2

### Удалить / заменить

Удалить формулировки:

```text
Сделать несколько build-вариантов.
Один modern-вариант...
Один legacy-вариант...
```

Заменить на:

```text
Сделать один пользовательский APK.
Различия Android runtime, storage, root и firmware formats обрабатывать внутри приложения через runtime profiles и capability-based firmware engine.
Build-варианты допускаются только как внутренний QA/debug инструмент, но не как пользовательская модель распространения.
```

### Заменить раздел "Multi-API build model"

На:

```text
Single APK runtime compatibility model
```

### Удалить пользовательские source sets

```text
pio/src/legacyApi/java
pio/src/modernApi/java
```

Заменить на обычные пакеты:

```text
pio/src/main/java/.../compat
pio/src/main/java/.../storage
pio/src/main/java/.../firmware
pio/src/main/java/.../toolchain
```

### Заменить тестовую матрицу

Было:

```text
legacyApiDebug on API 28
modernApiDebug on API 35
```

Должно быть:

```text
same APK on API 21/23/28/29/30/33/35
same APK against firmware samples Android 9/10/11/12/13/14/15
```

---

## 18. Итоговое архитектурное правило

```text
MIO-KITCHEN должен быть одним приложением для пользователя.

Разные Android-версии устройства, разные модели storage, root-доступ,
разные форматы прошивок и разные версии Android внутри ROM должны
обрабатываться не разными APK, а внутренними runtime profiles,
firmware capabilities, toolchain resolver и operation planner.
```

Коротко:

```text
один APK
один UI
одна кодовая база
много внутренних профилей
много тестовых матриц
никакого выбора APK пользователем
```

---

## 19. Источники для технической части

- Android `<uses-sdk>`, `minSdkVersion`, `targetSdkVersion`:  
  https://developer.android.com/guide/topics/manifest/uses-sdk-element
- Google Play target API requirements:  
  https://developer.android.com/google/play/requirements/target-sdk
- Storage Access Framework:  
  https://developer.android.com/training/data-storage/shared/documents-files
- All files access:  
  https://developer.android.com/training/data-storage/manage-all-files
- Android 15 behavior changes:  
  https://developer.android.com/about/versions/15/behavior-changes-15
- 16 KB page-size compatibility:  
  https://developer.android.com/guide/practices/page-sizes
- AOSP dynamic partitions:  
  https://source.android.com/docs/core/ota/dynamic_partitions
- AOSP EROFS:  
  https://source.android.com/docs/core/architecture/kernel/erofs
- AOSP boot image header:  
  https://source.android.com/docs/core/architecture/bootloader/boot-image-header
- AOSP Android Verified Boot:  
  https://source.android.com/docs/security/features/verifiedboot/avb
