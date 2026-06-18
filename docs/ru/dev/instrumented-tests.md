# Этап 18 — Instrumented-тесты

> Дата: 2026-06-18
> Статус: завершено
> Связанные этапы: после Stage 17 (coverage), перед Stage 19 (final integration)

---

Этапы 5-8 добавили JVM unit-тесты для всех новых компонентов. Этап 18 добавляет
соответствующие **instrumented**-тесты, которые проверяют те же компоненты на
реальном устройстве (или эмуляторе). Эти тесты необходимы, потому что некоторые
поведения — `Context.getExternalFilesDir(...)`, `AssetManager.open(...)`,
`ContentResolver`, реальные значения `Build.*` — можно проверить только на
Android.

## Что делает этот этап

Добавляет три instrumented-теста:

1. `FirmwareWorkspaceInstrumentedTest` — проверяет, что workspace-директории
   создаются внутри external-files-dir приложения, что `prepareImportFile`
   возвращает уникальные пути, что `prepareExportFile` использует default-имя,
   и что `clearOldImports` реально удаляет старые файлы.
2. `ZipFirmwareAnalyzerInstrumentedTest` — записывает настоящий zip-файл с
   `payload.bin` и `boot.img` во внешний кэш устройства, затем проверяет, что
   `ZipFirmwareAnalyzer` определяет `PAYLOAD_BIN` + `usesAB` + `hasBootImage`
   + подсказку Android 10.
3. `RuntimePermissionHelperInstrumentedTest` — проверяет, что
   `requiredPermissions()` возвращает `POST_NOTIFICATIONS` на Android 13+ и
   `READ_EXTERNAL_STORAGE` на Android ≤ 12, и что
   `areAllGranted` / `missingPermissions` не падают на свежем контексте.

Также обновлён пустой `ExampleInstrumentedTest.java` — теперь он проверяет
package name.

## Новые тесты

```text
common/src/androidTest/java/com/omarea/common/
  ExampleInstrumentedTest.java                              (обновлён)
  storage/FirmwareWorkspaceInstrumentedTest.kt              (4 теста)
  firmware/ZipFirmwareAnalyzerInstrumentedTest.kt           (1 тест)

pio/src/androidTest/java/com/mio/kitchen/ui/modern/
  RuntimePermissionHelperInstrumentedTest.kt                (1 тест)
```

## CI-проверка

```bash
python3 tools/check-instrumented-tests.py
```

Ожидаемый вывод:

```text
PASS: FirmwareWorkspaceInstrumentedTest covers workspace creation on device
PASS: ZipFirmwareAnalyzerInstrumentedTest covers real zip parsing on device
PASS: RuntimePermissionHelperInstrumentedTest covers permission list per Android version
PASS: All instrumented tests use AndroidJUnit4 + ApplicationProvider
PASS: common + pio modules declare androidTest dependencies
```

## Gradle-вызов

```bash
# Запустить instrumented-тесты на подключённом устройстве/эмуляторе:
./gradlew :common:connectedDebugAndroidTest
./gradlew :pio:connectedDebugAndroidTest
```

## Что намеренно НЕ сделано здесь

- CI-воркфлоу пока НЕ запускает `./gradlew connectedDebugAndroidTest` —
  требуется образ устройства/эмулятора. Скрипт
  `tools/check-instrumented-tests.py` статически проверяет конфигурацию.
- Нет coverage-instrumentation для instrumented-тестов — это требует
  follow-up для подключения Kover к `connectedDebugAndroidTest`.
- Нет UI-automation-тестов (Espresso) для `FirmwareAnalysisActivity` —
  активность является reference-реализацией, и её UI-автоматизация будет
  добавлена, когда layout будет переведён на XML.
- assertion package-name в `ExampleInstrumentedTest`
  (`com.omarea.common.test`) — это sanity-check; реальный applicationId
  определяется конфигурацией test-runner и может отличаться в release-сборках.
