package com.omarea.common.runtime

/**
 * RU: Режим SELinux на устройстве.
 *
 * EN: SELinux mode on the device.
 */
enum class SelinuxMode {
    ENFORCING,
    PERMISSIVE,
    DISABLED,
    UNKNOWN
}

/**
 * RU: Профиль устройства, на котором запущено приложение.
 *
 * Определяет Android, на котором запущено приложение (НЕ версию прошивки!).
 * Используется для выбора:
 *   - storage behavior (scoped storage, legacy direct path);
 *   - root behavior;
 *   - tool binary ABI;
 *   - проверок совместимости (16 KB page size и т.п.);
 *   - ограничений Android 10/11/12/13/14/15+.
 *
 * ВАЖНО: `androidVersion` здесь — это версия Android устройства, а НЕ версия
 * Android внутри прошивки. Это разные понятия (см. roadmap §5).
 *
 * EN: Profile of the device the app is running on.
 *
 * Defines the Android the app is running on (NOT the firmware's Android!).
 * Used to select:
 *   - storage behavior (scoped storage, legacy direct path);
 *   - root behavior;
 *   - tool binary ABI;
 *   - compatibility checks (16 KB page size, etc.);
 *   - Android 10/11/12/13/14/15+ restrictions.
 *
 * IMPORTANT: `androidVersion` here is the device's Android, NOT the firmware's
 * Android. These are different concepts (see roadmap §5).
 */
data class DeviceProfile(
    val sdkInt: Int,
    val manufacturer: String,
    val model: String,
    val abiList: List<String>,
    val isEmulator: Boolean,
    val supports16KbPageSize: Boolean?,
    val hasRoot: Boolean?,
    val selinuxMode: SelinuxMode
) {
    /**
     * RU: Возвращает `true`, если устройство работает под Android 11+ (scoped storage enforcement).
     * EN: Returns `true` when the device runs Android 11+ (scoped storage enforcement).
     */
    val enforcesScopedStorage: Boolean get() = sdkInt >= 30

    /**
     * RU: Возвращает `true`, если устройство работает под Android 10+ (scoped storage boundary).
     * EN: Returns `true` when the device runs Android 10+ (scoped storage boundary).
     */
    val hasScopedStorageBoundary: Boolean get() = sdkInt >= 29

    /**
     * RU: Возвращает `true`, если устройство работает под Android 13+ (notification runtime permissions).
     * EN: Returns `true` when the device runs Android 13+ (notification runtime permissions).
     */
    val requiresNotificationPermission: Boolean get() = sdkInt >= 33

    /**
     * RU: Возвращает `true`, если устройство работает под Android 14+ (foreground service types required).
     * EN: Returns `true` when the device runs Android 14+ (foreground service types required).
     */
    val requiresForegroundServiceType: Boolean get() = sdkInt >= 34

    /**
     * RU: Возвращает `true`, если устройство работает под Android 15+ (16 KB page size checks).
     * EN: Returns `true` when the device runs Android 15+ (16 KB page size checks).
     */
    val isAndroid15Plus: Boolean get() = sdkInt >= 35

    /**
     * RU: Возвращает человекочитаемую метку для UI.
     * EN: Returns a human-readable label for UI display.
     */
    val label: String
        get() = buildString {
            append("Android ").append(sdkInt)
            append(" / ").append(manufacturer).append(" ").append(model)
            if (isEmulator) append(" (emulator)")
            if (hasRoot == true) append(" / rooted")
            if (selinuxMode != SelinuxMode.UNKNOWN) append(" / selinux=").append(selinuxMode.name.lowercase())
        }

    companion object {
        /**
         * RU: Создаёт профиль для тестов со значениями по умолчанию.
         * EN: Creates a profile for tests with default values.
         */
        fun forTests(
            sdkInt: Int = 33,
            manufacturer: String = "Google",
            model: String = "Pixel 7",
            abiList: List<String> = listOf("arm64-v8a", "armeabi-v7a"),
            isEmulator: Boolean = false,
            supports16KbPageSize: Boolean? = false,
            hasRoot: Boolean? = false,
            selinuxMode: SelinuxMode = SelinuxMode.ENFORCING
        ): DeviceProfile = DeviceProfile(
            sdkInt = sdkInt,
            manufacturer = manufacturer,
            model = model,
            abiList = abiList,
            isEmulator = isEmulator,
            supports16KbPageSize = supports16KbPageSize,
            hasRoot = hasRoot,
            selinuxMode = selinuxMode
        )
    }
}
