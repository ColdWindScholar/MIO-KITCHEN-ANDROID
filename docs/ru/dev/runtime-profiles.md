# Этап 10 — Runtime-профили

> Дата: 2026-06-18
> Статус: завершено
> Связанные этапы: после Stage 9 (tests/CI), перед Stage 11 (targetSdk 35 runtime migration)

---


Дорожная карта v3 §3 описывает систему runtime-профилей, которая позволяет
единому APK адаптировать поведение к устройству, на котором он запущен, И к
прошивке, которую выбрал пользователь. Этап 7 ввёл `FirmwareProfile`. Этап 10
вводит оставшиеся профили:

- `DeviceProfile` — описывает устройство, на котором запущено приложение
  (версия Android, производитель, список ABI, root-статус, режим SELinux,
  поддержка 16 KB page size).
- `ToolchainProfile` + `ToolRequirement` + `ToolDescriptor` — описывает, какие
  нативные инструменты нужны операции и какие доступны в манифесте.
- `OperationSafetyProfile` — описывает, насколько операция опасна
  (деструктивная, требует backup, требует root, поддерживает dry-run,
  уровень подтверждения).
- `AppRuntimeProfile` + `AppRuntimeProfileResolver` — контейнер верхнего
  уровня, который объединяет всё перечисленное в одно иммутабельное значение,
  на которое может подписаться UI.

Этап также поставляет JSON-манифест инструментов в
`assets/toolchain/manifest.json` и парсер `ToolManifestLoader`.

### Новый API

```text
common/runtime/
  DeviceProfile.kt              # data class + enum SelinuxMode + forTests()
  DeviceProfileProvider.kt      # строит DeviceProfile из Build.*
  AppRuntimeProfile.kt          # data class + AppRuntimeProfileResolver

common/toolchain/
  ToolRequirement.kt            # enum ToolPurpose + ToolRequirement +
                                # ToolDescriptor + ToolchainProfile
  ToolchainResolver.kt          # interface + enum FirmwareOperation +
                                # ToolchainPlan + ToolchainWarning
  ToolManifestLoader.kt         # JSON-парсер для assets/toolchain/manifest.json
  CapabilityBasedToolchainResolver.kt
                                # реализация резолвера на базе capabilities

common/operations/
  OperationSafetyProfile.kt     # ConfirmationLevel + OperationSafetyProfile
                                # + хелперы readOnly/packOperation/flashOperation
  OperationPlanner.kt           # OperationPlan + OperationPlanner +
                                # OperationSafetyProvider + DefaultOperationSafetyProvider

pio/src/main/assets/toolchain/
  manifest.json                 # пример манифеста с 13 bundled-инструментами
```

### Архитектурные правила

```text
runtime/        -> БЕЗ shell, БЕЗ UI, БЕЗ android.app.* (Build.* через provider допустим)
toolchain/      -> БЕЗ shell, БЕЗ UI, БЕЗ android.app.*
operations/     -> БЕЗ shell, БЕЗ UI, БЕЗ android.app.*
DeviceProfileProvider
                -> читает только Build.*; НЕ вызывает shell
CapabilityBasedToolchainResolver
                -> чистая функция (operation, firmware, device) -> plan
ToolManifestLoader
                -> чистый JSON-парсер; не трогает файловую систему
AppRuntimeProfileResolver
                -> объединяет профили; НЕ выполняет shell
```

### Формат манифеста

```json
{
  "tools": [
    {
      "name": "lpunpack",
      "version": "android-tools-r34",
      "abi": ["arm64-v8a", "armeabi-v7a"],
      "sha256": null,
      "capabilities": ["super_image"],
      "source": "AOSP",
      "license": "Apache-2.0",
      "supports16KbPageSize": true
    }
  ]
}
```

### Соответствие capabilities → инструменты

```text
UNPACK_ROM         -> busybox + (payload-dumper если payload.bin)
                            + (lpunpack если super.img)
                            + (magiskboot если boot.img)
                            + (avbtool? если AVB)
UNPACK_SUPER       -> lpunpack + simg2img?
PACK_SUPER         -> lpmake + img2simg?
UNPACK_BOOT_IMAGE  -> magiskboot
PACK_BOOT_IMAGE    -> magiskboot + mkbootimg
UNPACK_FILESYSTEM_IMAGE
                   -> dump.erofs (если EROFS) | e2fsdroid? (если ext4)
PACK_FILESYSTEM_IMAGE
                   -> mkfs.erofs (если EROFS) | mke2fs + e2fsdroid (если ext4)
VERIFY_VBMETA      -> avbtool
FLASH_PREPARE      -> busybox + magiskboot?
+ инструменты сжатия (brotli/lz4/zstd) когда capabilities требуют
```

### Дефолтные профили безопасности

```text
read-only операции (UNPACK_*, INSPECT, VERIFY_VBMETA)
  -> isDestructive=false, requiresRoot=false, confirmationLevel=NONE
pack-операции (PACK_*)
  -> isDestructive=false, requiresRoot=false, confirmationLevel=STANDARD
flash-операции (FLASH_PREPARE)
  -> isDestructive=true, requiresRoot=true, requiresBackup=true,
     requiresDeviceConnection=true, confirmationLevel=DESTRUCTIVE
```

### CI-проверка

```bash
python3 tools/check-runtime-profiles.py
```

Ожидаемый вывод:

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
PASS: runtime/toolchain/operations layers are pure (no shell/UI imports)
```

### Что намеренно НЕ сделано здесь

- `ToolchainInstaller` реализован в Stage 11; он автоматически вызывается
  `FirmwareOperationService.onCreate` (Stage 21) и `LegacyShellBridge.init`
  (Stage 23) для заполнения `<filesDir>/bin/` из
  `assets/toolchain/manifest.json`. SHA-256 значения в манифесте
  генерируются `tools/compute-tool-hashes.py`.
- `ToolchainProfile.toolsDir` заполняется `ToolchainInstaller` и доступен
  через `FirmwareOperationService.lastInstalledToolsDir` (Stage 21).
- `DeviceProfileProvider.withRootCheck()` возвращает `SelinuxMode.UNKNOWN` и
  `null` для 16 KB page size — реальные probe'ы идут через `ShellRuntime` и
  будут подключены на будущем этапе.
- Существующие UI-активности теперь используют `AppRuntimeProfile` через
  `AppRuntimeStore` (Stage 20 гибридная миграция). Legacy-классы
  `PageConfigReader`/`ScriptEnvironmen` удалены в Stage 22 и заменены на
  `PageConfigLoader` (через `PageConfigRepository`) и тонкий фасад
  `ScriptEnvironmen.kt` над `LegacyShellBridge`.
- `OperationPlanner` строит `OperationPlan` (Stage 10); план выполняется
  `OperationExecutor` (Stage 12), который превращает план в `ShellCommand`
  и запускает через `ShellRuntime`.
