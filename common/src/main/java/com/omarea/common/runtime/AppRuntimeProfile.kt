package com.omarea.common.runtime

import com.omarea.common.firmware.FirmwareProfile
import com.omarea.common.operations.OperationPlan
import com.omarea.common.operations.OperationPlanner
import com.omarea.common.toolchain.FirmwareOperation

/**
 * RU: Единый runtime-профиль приложения.
 *
 * Объединяет:
 *   - [DeviceProfile] — устройство, на котором запущено приложение;
 *   - [FirmwareProfile] — выбранная прошивка (опционально);
 *   - активную операцию и её [OperationPlan].
 *
 * Это "контейнер контекста" для всего, что происходит в приложении в данный
 * момент. UI подписывается на изменения этого профиля и перерисовывается.
 *
 * EN: Unified runtime profile of the application.
 *
 * Combines:
 *   - [DeviceProfile] — the device the app is running on;
 *   - [FirmwareProfile] — the selected firmware (optional);
 *   - the active operation and its [OperationPlan].
 *
 * This is the "context container" for everything happening in the app at the
 * moment. The UI subscribes to changes of this profile and redraws.
 */
data class AppRuntimeProfile(
    val device: DeviceProfile,
    val firmware: FirmwareProfile? = null,
    val activeOperation: FirmwareOperation? = null,
    val operationPlan: OperationPlan? = null
) {
    /**
     * RU: Возвращает `true`, если прошивка выбрана и проанализирована.
     * EN: Returns `true` when a firmware is selected and analyzed.
     */
    val hasFirmware: Boolean get() = firmware != null

    /**
     * RU: Возвращает `true`, если есть активная готовая к выполнению операция.
     * EN: Returns `true` when there is an active operation ready to execute.
     */
    val hasReadyOperation: Boolean get() = operationPlan?.canExecute == true

    /**
     * RU: Возвращает человекочитаемую сводку для UI.
     * EN: Returns a human-readable summary for the UI.
     */
    val summary: String
        get() = buildString {
            append("device=").append(device.sdkInt)
            if (device.hasRoot == true) append("/root")
            if (firmware != null) {
                append(", firmware=").append(firmware.summary)
            }
            if (activeOperation != null) {
                append(", op=").append(activeOperation!!.name.lowercase())
                operationPlan?.let { plan ->
                    if (plan.canExecute) append(" (ready)") else append(" (blocked)")
                }
            }
        }
}

/**
 * RU: Резолвер, который строит [AppRuntimeProfile] из базовых компонентов.
 *
 * Это верхний уровень runtime-архитектуры. Он НЕ выполняет shell — он только
 * комбинирует уже построенные профили и планы.
 *
 * EN: Resolver that builds an [AppRuntimeProfile] from base components.
 *
 * This is the top of the runtime architecture. It does NOT run shell — it only
 * combines already-built profiles and plans.
 */
class AppRuntimeProfileResolver(
    private val deviceProvider: () -> DeviceProfile,
    private val operationPlanner: OperationPlanner
) {
    /**
     * RU: Возвращает профиль без выбранной прошивки.
     *
     * Используется на старте приложения, когда прошивка ещё не выбрана.
     *
     * EN: Returns the profile without a selected firmware.
     *
     * Used at app startup when no firmware has been picked yet.
     */
    fun initial(): AppRuntimeProfile = AppRuntimeProfile(
        device = deviceProvider()
    )

    /**
     * RU: Возвращает профиль с выбранной прошивкой [firmware], но без активной
     *     операции.
     *
     * EN: Returns the profile with the selected [firmware] but without an
     *     active operation.
     */
    fun withFirmware(firmware: FirmwareProfile): AppRuntimeProfile {
        return AppRuntimeProfile(
            device = deviceProvider(),
            firmware = firmware
        )
    }

    /**
     * RU: Возвращает профиль с активной операцией [operation] над [firmware].
     *
     * План операции строится через [OperationPlanner].
     *
     * EN: Returns the profile with an active [operation] on [firmware].
     *
     * The operation plan is built via [OperationPlanner].
     */
    fun withOperation(
        firmware: FirmwareProfile,
        operation: FirmwareOperation
    ): AppRuntimeProfile {
        val device = deviceProvider()
        val plan = operationPlanner.plan(operation, firmware, device)
        return AppRuntimeProfile(
            device = device,
            firmware = firmware,
            activeOperation = operation,
            operationPlan = plan
        )
    }
}
