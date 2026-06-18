package com.mio.kitchen.ui.modern

import com.omarea.common.firmware.FirmwareProfile
import com.omarea.common.firmware.FirmwareSource
import com.omarea.common.firmware.FirmwareAnalyzerRegistry
import com.omarea.common.runtime.AppRuntimeProfile
import com.omarea.common.runtime.DeviceProfile
import com.omarea.common.runtime.DeviceProfileProvider
import com.omarea.common.runtime.SelinuxMode
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * RU: Singleton-мост между legacy-активностями и новой runtime-архитектурой.
 *
 * Проблема: существующие активности (`MainActivity`, `ActionPage`,
 * `ActivityFileSelector`, `SplashActivity`) используют legacy-путь
 * `PageConfigReader` → `ScriptEnvironmen` → `KeepShell`. Полная замена
 * этого пути на `AppRuntimeProfileResolver` требует большого UI-rewrite.
 *
 * Решение (Stage 20): гибридный слой. [AppRuntimeStore] хранит текущий
 * [AppRuntimeProfile] в [StateFlow], который любая активность может
 * наблюдать. Legacy-активности обновляют профиль через методы этого объекта,
 * не отказываясь от существующего кода. Новые активности (например
 * `FirmwareAnalysisActivity`) читают профиль напрямую.
 *
 * EN: Singleton bridge between legacy activities and the new runtime
 * architecture.
 *
 * Problem: existing activities (`MainActivity`, `ActionPage`,
 * `ActivityFileSelector`, `SplashActivity`) use the legacy path
 * `PageConfigReader` → `ScriptEnvironmen` → `KeepShell`. Replacing this path
 * wholesale with `AppRuntimeProfileResolver` requires a large UI rewrite.
 *
 * Solution (Stage 20): a hybrid layer. [AppRuntimeStore] keeps the current
 * [AppRuntimeProfile] in a [StateFlow] that any activity can observe. Legacy
 * activities update the profile via this object's methods without abandoning
 * their existing code. New activities (e.g. `FirmwareAnalysisActivity`) read
 * the profile directly.
 */
object AppRuntimeStore {

    private val _profile = MutableStateFlow<AppRuntimeProfile?>(null)
    /**
     * RU: Текущий runtime-профиль. `null`, пока [init] не вызван.
     * EN: Current runtime profile. `null` until [init] is called.
     */
    val profile: StateFlow<AppRuntimeProfile?> = _profile.asStateFlow()

    /**
     * RU: Инициализирует профиль устройства. Безопасно вызывать повторно —
     *     повторные вызовы просто обновляют device-часть.
     *
     * EN: Initialises the device profile. Safe to call repeatedly —
     *     subsequent calls just refresh the device portion.
     */
    fun init(rootAvailable: Boolean? = null) {
        val provider = DeviceProfileProvider(
            rootChecker = { rootAvailable },
            selinuxProbe = { SelinuxMode.UNKNOWN },
            sixteenKbProbe = { null }
        )
        val device = provider.current()
        val current = _profile.value
        _profile.value = AppRuntimeProfile(
            device = device,
            firmware = current?.firmware,
            activeOperation = current?.activeOperation,
            operationPlan = current?.operationPlan
        )
    }

    /**
     * RU: Обновляет device-часть профиля с указанным root-статусом.
     *
     * Используется `SplashActivity` после завершения `CheckRootStatus` —
     * результат обновляет профиль без повторного чтения Build.*.
     *
     * EN: Refreshes the device portion of the profile with the given root
     * status.
     *
     * Used by `SplashActivity` after `CheckRootStatus` completes — the result
     * updates the profile without re-reading Build.*.
     */
    fun updateRootStatus(hasRoot: Boolean) {
        val current = _profile.value
        val device = (current?.device ?: DeviceProfileProvider(
            rootChecker = { hasRoot }
        ).current()).copy(hasRoot = hasRoot)
        _profile.value = AppRuntimeProfile(
            device = device,
            firmware = current?.firmware,
            activeOperation = current?.activeOperation,
            operationPlan = current?.operationPlan
        )
    }

    /**
     * RU: Регистрирует выбранную прошивку по shell-пути. Выполняет анализ
     *     синхронно (вызывающая сторона должна быть не на main-thread).
     *
     * EN: Registers the selected firmware by shell path. Runs analysis
     *     synchronously (the caller must not be on the main thread).
     */
    fun setFirmware(shellPath: String) {
        val source = FirmwareSource.DirectPath(shellPath)
        val firmware = try {
            FirmwareAnalyzerRegistry.analyze(source)
        } catch (e: Throwable) {
            android.util.Log.w("AppRuntimeStore", "Firmware analysis failed", e)
            null
        }
        val current = _profile.value
        val device = current?.device ?: DeviceProfileProvider().current()
        _profile.value = AppRuntimeProfile(
            device = device,
            firmware = firmware,
            activeOperation = null,
            operationPlan = null
        )
    }

    /**
     * RU: Сбрасывает firmware и активную операцию. Устройство сохраняется.
     *
     * EN: Resets the firmware and active operation. The device is preserved.
     */
    fun resetFirmware() {
        val current = _profile.value ?: return
        _profile.value = AppRuntimeProfile(
            device = current.device,
            firmware = null,
            activeOperation = null,
            operationPlan = null
        )
    }

    /**
     * RU: Возвращает текущий device-profile или `null`, если [init] не вызван.
     *
     * EN: Returns the current device profile, or `null` when [init] has not
     *     been called.
     */
    val device: DeviceProfile? get() = _profile.value?.device

    /**
     * RU: Возвращает текущий firmware-profile или `null`.
     *
     * EN: Returns the current firmware profile, or `null`.
     */
    val firmware: FirmwareProfile? get() = _profile.value?.firmware
}
