# Build modernization baseline

## Stage goal

This stage updates the project build infrastructure to a modern Gradle/Android baseline, but it **does not change storage/root/firmware runtime behavior**. This separation matters: the build stack can be updated safely, while moving `targetSdk` to a modern level must happen together with a new storage/workspace layer.

## Changes

| Area | Before | After |
| --- | --- | --- |
| Gradle Wrapper | `gradle-6.1.1-all.zip` from Tencent mirror | `gradle-8.13-bin.zip` from `services.gradle.org` |
| Root Gradle | `buildscript` + classpath | `plugins` DSL |
| Repositories | `jcenter()` in root/modules | `google()`, `mavenCentral()`, `jitpack.io` in `settings.gradle` |
| Repository policy | repositories in every module | `RepositoriesMode.FAIL_ON_PROJECT_REPOS` |
| AGP | `4.0.1` | `8.13.2` |
| Kotlin | `1.4.0` + `kotlin-android-extensions` | `2.3.21` + ViewBinding |
| compile SDK | 28 | 35 |
| target SDK | 28 | 28 temporarily kept |
| namespace | manifest `package` | Gradle `namespace` |
| release signing | debug signing for release | release signing through env/secrets |
| CI JDK | 8 | 17 |

## Why `targetSdk` is not raised to 35 yet

`targetSdk` changes Android runtime behavior: permissions, storage, background behavior, and modern platform rules. This is especially sensitive for MIO-KITCHEN because the app works with large firmware files, root/direct paths, shell execution, and external sources.

The safe sequence is:

```text
1. Update the build stack, remove Kotlin synthetics, enable ViewBinding.
2. Introduce StorageGateway / Workspace / SAF / root-path adapters.
3. Cover storage scenarios with tests.
4. Then raise targetSdk to 35.
```

Otherwise the project may build with modern tools while breaking real firmware workflows on Android 13–15.

## ViewBinding migration

`kotlin-android-extensions` was removed. Synthetic View access was replaced with ViewBinding in:

```text
krscript/src/main/java/com/omarea/krscript/ui/DialogLogFragment.kt
pio/src/main/java/com/mio/kitchen/ActionPage.kt
pio/src/main/java/com/mio/kitchen/ActivityFileSelector.kt
pio/src/main/java/com/mio/kitchen/MainActivity.kt
pio/src/main/java/com/mio/kitchen/SplashActivity.kt
```

## Release signing

Release APKs are no longer signed with the debug key. The release workflow requires these secrets:

```text
MIO_RELEASE_STORE_BASE64
MIO_RELEASE_STORE_PASSWORD
MIO_RELEASE_KEY_ALIAS
MIO_RELEASE_KEY_PASSWORD
```

Local release builds can use the same values through environment variables or Gradle properties:

```bash
./gradlew :pio:assembleRelease \
  -PMIO_RELEASE_STORE_FILE=/path/to/release.jks \
  -PMIO_RELEASE_STORE_PASSWORD=... \
  -PMIO_RELEASE_KEY_ALIAS=... \
  -PMIO_RELEASE_KEY_PASSWORD=...
```

## Checks

Fast checks for this stage:

```bash
python3 tools/validate-localization.py
python3 tools/check-known-regressions.py
python3 tools/check-build-modernization.py
bash -n pio/src/main/assets/script/tool.sh
```

Full check in a normal Android environment:

```bash
./gradlew :pio:assembleDebug --no-daemon
```

## Out of scope

This stage does not complete:

- `targetSdk 35` runtime migration;
- storage/workspace architecture;
- Activity Result API migration;
- ViewModel/lifecycle migration;
- parser/runtime split;
- Shell runtime redesign;
- firmware toolchain capability resolver.
