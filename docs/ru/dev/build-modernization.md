# Build modernization baseline

## Цель этапа

Этап обновляет сборочную инфраструктуру проекта до современной Gradle/Android базы, но **не меняет runtime-поведение storage/root/firmware операций**. Это важно: build stack можно обновить безопасно, а переход `targetSdk` на современный уровень нужно делать только вместе с новым storage/workspace слоем.

## Что изменено

| Зона | Было | Стало |
| --- | --- | --- |
| Gradle Wrapper | `gradle-6.1.1-all.zip` с Tencent mirror | `gradle-8.13-bin.zip` с `services.gradle.org` |
| Root Gradle | `buildscript` + classpath | `plugins` DSL |
| Repositories | `jcenter()` в root/modules | `google()`, `mavenCentral()`, `jitpack.io` в `settings.gradle` |
| Repository policy | repositories в каждом модуле | `RepositoriesMode.FAIL_ON_PROJECT_REPOS` |
| AGP | `4.0.1` | `8.13.2` |
| Kotlin | `1.4.0` + `kotlin-android-extensions` | `2.3.21` + ViewBinding |
| compile SDK | 28 | 35 |
| target SDK | 28 | 28 временно сохранён |
| namespace | `package` в manifest | `namespace` в Gradle |
| release signing | debug signing для release | release signing через env/secrets |
| CI JDK | 8 | 17 |

## Почему `targetSdk` пока не поднят до 35

`targetSdk` меняет Android runtime behavior: permissions, storage, background behavior и совместимость с modern platform rules. Для MIO-KITCHEN это особенно чувствительно, потому что приложение работает с большими файлами прошивок, root/direct paths, shell и внешними источниками.

Поэтому текущая безопасная последовательность такая:

```text
1. Обновить build stack, убрать Kotlin synthetics, включить ViewBinding.
2. Ввести StorageGateway / Workspace / SAF / root-path adapters.
3. Покрыть storage сценарии тестами.
4. После этого поднять targetSdk до 35.
```

Иначе можно получить проект, который современно собирается, но ломает реальные firmware-сценарии на Android 13–15.

## ViewBinding migration

Удалён `kotlin-android-extensions`. Синтетический доступ к View заменён на ViewBinding в файлах:

```text
krscript/src/main/java/com/omarea/krscript/ui/DialogLogFragment.kt
pio/src/main/java/com/mio/kitchen/ActionPage.kt
pio/src/main/java/com/mio/kitchen/ActivityFileSelector.kt
pio/src/main/java/com/mio/kitchen/MainActivity.kt
pio/src/main/java/com/mio/kitchen/SplashActivity.kt
```

## Release signing

Release APK больше не подписывается debug key. Для release workflow нужны secrets:

```text
MIO_RELEASE_STORE_BASE64
MIO_RELEASE_STORE_PASSWORD
MIO_RELEASE_KEY_ALIAS
MIO_RELEASE_KEY_PASSWORD
```

Локально можно передать те же значения через environment variables или Gradle properties:

```bash
./gradlew :pio:assembleRelease \
  -PMIO_RELEASE_STORE_FILE=/path/to/release.jks \
  -PMIO_RELEASE_STORE_PASSWORD=... \
  -PMIO_RELEASE_KEY_ALIAS=... \
  -PMIO_RELEASE_KEY_PASSWORD=...
```

## Проверки

Быстрые проверки этапа:

```bash
python3 tools/validate-localization.py
python3 tools/check-known-regressions.py
python3 tools/check-build-modernization.py
bash -n pio/src/main/assets/script/tool.sh
```

Полная проверка в нормальной Android-среде:

```bash
./gradlew :pio:assembleDebug --no-daemon
```

## Что не входит в этап

Этот этап не закрывает:

- `targetSdk 35` runtime migration;
- storage/workspace architecture;
- Activity Result API migration;
- ViewModel/lifecycle migration;
- parser/runtime split;
- Shell runtime redesign;
- firmware toolchain capability resolver.
