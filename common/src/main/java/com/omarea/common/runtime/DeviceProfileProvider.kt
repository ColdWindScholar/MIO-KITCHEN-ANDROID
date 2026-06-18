package com.omarea.common.runtime

import android.os.Build

/**
 * RU: Строит [DeviceProfile] из системных данных.
 *
 * Это единственное место, где new-runtime код читает `Build.*` напрямую.
 * В тестах вместо него можно подставить собственный [DeviceProfile].
 *
 * EN: Builds a [DeviceProfile] from system data.
 *
 * This is the single place where new-runtime code reads `Build.*` directly.
 * Tests can substitute their own [DeviceProfile] instead.
 */
class DeviceProfileProvider(
    private val rootChecker: () -> Boolean? = { null },
    private val selinuxProbe: () -> SelinuxMode = { SelinuxMode.UNKNOWN },
    private val sixteenKbProbe: () -> Boolean? = { null }
) {
    /**
     * RU: Возвращает профиль текущего устройства.
     *
     * @param rootChecker опциональная функция, возвращающая `true`/`false`
     *   если root-статус известен, или `null` если проверка не выполнялась.
     * @param selinuxProbe опциональная функция, возвращающая режим SELinux.
     * @param sixteenKbProbe опциональная функция, возвращающая поддержку
     *   16 KB page size или `null`, если неизвестно.
     *
     * EN: Returns the current device profile.
     */
    fun current(): DeviceProfile {
        val sdkInt = Build.VERSION.SDK_INT
        val manufacturer = Build.MANUFACTURER ?: "unknown"
        val model = Build.MODEL ?: "unknown"
        val abiList = buildList {
            if (Build.SUPPORTED_ABIS != null) {
                addAll(Build.SUPPORTED_ABIS.toList())
            }
        }
        val isEmulator = detectEmulator(manufacturer, model)
        return DeviceProfile(
            sdkInt = sdkInt,
            manufacturer = manufacturer,
            model = model,
            abiList = abiList,
            isEmulator = isEmulator,
            supports16KbPageSize = sixteenKbProbe(),
            hasRoot = rootChecker(),
            selinuxMode = selinuxProbe()
        )
    }

    private fun detectEmulator(manufacturer: String, model: String): Boolean {
        val mfr = manufacturer.lowercase()
        val mdl = model.lowercase()
        return mfr.contains("genymotion") ||
            mfr.contains("google") && mdl.contains("sdk gphone") ||
            mdl.startsWith(" emulator") ||
            mdl.contains("android sdk") ||
            Build.FINGERPRINT?.startsWith("generic") == true ||
            Build.HARDWARE?.contains("goldfish") == true ||
            Build.HARDWARE?.contains("ranchu") == true ||
            Build.PRODUCT?.contains("sdk") == true
    }

    companion object {
        /**
         * RU: Создаёт провайдер, который использует `KeepShellPublic.checkRoot()`
         *     для определения root-статуса.
         *
         * EN: Creates a provider that uses `KeepShellPublic.checkRoot()` to
         *     determine root status.
         */
        fun withRootCheck(rootChecker: () -> Boolean?): DeviceProfileProvider =
            DeviceProfileProvider(
                rootChecker = rootChecker,
                selinuxProbe = { probeSelinux() },
                sixteenKbProbe = { probe16KbPageSize() }
            )

        /**
         * RU: Простая эвристика SELinux через `getprop`.
         *
         * ВНИМАНИЕ: эта реализация НЕ вызывает shell — она возвращает UNKNOWN.
         * Реальный probe должен быть реализован через `ShellRuntime` в этапе,
         * который зависит от shell runtime.
         *
         * EN: Naive SELinux heuristic via `getprop`.
         *
         * WARNING: this implementation does NOT call shell — it returns UNKNOWN.
         * The real probe should be implemented via `ShellRuntime` in a stage
         * that depends on the shell runtime.
         */
        internal fun probeSelinux(): SelinuxMode = SelinuxMode.UNKNOWN

        /**
         * RU: Эвристика для 16 KB page size.
         *
         * На Android 15+ это можно определить через `Build.SUPPORTED_64_BIT_ABIS`
         * и чтение `/proc/sys/...`, но это требует shell. По умолчанию UNKNOWN.
         *
         * EN: Heuristic for 16 KB page size.
         *
         * On Android 15+ this can be determined via `Build.SUPPORTED_64_BIT_ABIS`
         * and reading `/proc/sys/...`, but that requires shell. Default UNKNOWN.
         */
        internal fun probe16KbPageSize(): Boolean? = null
    }
}
