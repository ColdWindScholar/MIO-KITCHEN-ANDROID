package com.omarea.common.operations

import com.omarea.common.firmware.FirmwareProfile
import com.omarea.common.runtime.DeviceProfile
import com.omarea.common.toolchain.FirmwareOperation
import com.omarea.common.toolchain.ToolchainPlan
import com.omarea.common.toolchain.ToolchainResolver

/**
 * RU: План выполнения операции над прошивкой.
 *
 * Содержит:
 *   - целевую операцию;
 *   - профиль прошивки;
 *   - профиль устройства;
 *   - профиль безопасности;
 *   - план toolchain;
 *   - итоговое решение `canExecute`.
 *
 * Это финальный артефакт, который UI показывает пользователю перед запуском.
 *
 * EN: Execution plan for a firmware operation.
 *
 * Contains:
 *   - the target operation;
 *   - the firmware profile;
 *   - the device profile;
 *   - the safety profile;
 *   - the toolchain plan;
 *   - the final `canExecute` decision.
 *
 * This is the final artifact the UI shows to the user before launching.
 */
data class OperationPlan(
    val operation: FirmwareOperation,
    val firmware: FirmwareProfile,
    val device: DeviceProfile,
    val safety: OperationSafetyProfile,
    val toolchain: ToolchainPlan
) {
    /**
     * RU: Возвращает `true`, если план можно выполнить.
     *
     * Условия:
     *   - toolchain готов (обязательные инструменты присутствуют, ABI совместим);
     *   - если операция требует root, root доступен;
     *   - нет критических предупреждений.
     *
     * EN: Returns `true` when the plan can be executed.
     *
     * Conditions:
     *   - toolchain is ready (required tools present, ABI compatible);
     *   - if the operation requires root, root is available;
     *   - no critical warnings.
     */
    val canExecute: Boolean
        get() {
            if (!toolchain.canExecute) return false
            if (safety.requiresRoot && device.hasRoot != true) return false
            return true
        }

    /**
     * RU: Возвращает список человекочитаемых блокеровок.
     *
     * EN: Returns a list of human-readable blockers.
     */
    fun blockers(): List<String> {
        val result = mutableListOf<String>()
        if (toolchain.missingRequired.isNotEmpty()) {
            result += "Missing required tools: ${toolchain.missingRequired.joinToString(", ")}"
        }
        if (toolchain.profile.selectedAbi.isEmpty()) {
            result += "No compatible ABI between device and tools"
        }
        if (safety.requiresRoot && device.hasRoot != true) {
            result += "Operation requires root, but root is not available"
        }
        toolchain.warnings
            .filter { it.severity == com.omarea.common.toolchain.ToolchainWarning.Severity.ERROR }
            .forEach { result += "${it.code}: ${it.message}" }
        return result
    }
}

/**
 * RU: Строит [OperationPlan] для пары (операция, прошивка) на устройстве.
 *
 * Это высокоуровневый фасад, который объединяет:
 *   - [ToolchainResolver] — какие инструменты нужны;
 *   - [OperationSafetyProfile] — насколько операция опасна;
 *   - проверки предусловий (root, device).
 *
 * EN: Builds an [OperationPlan] for an (operation, firmware) pair on a device.
 *
 * High-level facade that combines:
 *   - [ToolchainResolver] — which tools are needed;
 *   - [OperationSafetyProfile] — how dangerous the operation is;
 *   - precondition checks (root, device).
 */
class OperationPlanner(
    private val toolchainResolver: ToolchainResolver,
    private val safetyProvider: OperationSafetyProvider = DefaultOperationSafetyProvider()
) {
    /**
     * RU: Строит план для [operation] над [firmware] на [device].
     * EN: Builds the plan for [operation] on [firmware] running on [device].
     */
    fun plan(
        operation: FirmwareOperation,
        firmware: FirmwareProfile,
        device: DeviceProfile
    ): OperationPlan {
        val safety = safetyProvider.profileFor(operation)
        val toolchain = toolchainResolver.resolve(operation, firmware, device)
        return OperationPlan(
            operation = operation,
            firmware = firmware,
            device = device,
            safety = safety,
            toolchain = toolchain
        )
    }
}

/**
 * RU: Провайдер профилей безопасности.
 *
 * EN: Provider of safety profiles.
 */
interface OperationSafetyProvider {
    fun profileFor(operation: FirmwareOperation): OperationSafetyProfile
}

/**
 * RU: Реализация по умолчанию, использующая статическую таблицу соответствий.
 *
 * EN: Default implementation using a static mapping table.
 */
class DefaultOperationSafetyProvider : OperationSafetyProvider {
    override fun profileFor(operation: FirmwareOperation): OperationSafetyProfile = when (operation) {
        FirmwareOperation.UNPACK_ROM,
        FirmwareOperation.UNPACK_SUPER,
        FirmwareOperation.UNPACK_PAYLOAD_BIN,
        FirmwareOperation.UNPACK_BOOT_IMAGE,
        FirmwareOperation.UNPACK_VENDOR_BOOT_IMAGE,
        FirmwareOperation.UNPACK_INIT_BOOT_IMAGE,
        FirmwareOperation.UNPACK_DTBO_IMAGE,
        FirmwareOperation.UNPACK_FILESYSTEM_IMAGE,
        FirmwareOperation.INSPECT,
        FirmwareOperation.VERIFY_VBMETA ->
            OperationSafetyProfile.readOnly(operation)

        FirmwareOperation.PACK_FILESYSTEM_IMAGE,
        FirmwareOperation.PACK_BOOT_IMAGE,
        FirmwareOperation.PACK_SUPER ->
            OperationSafetyProfile.packOperation(operation)

        FirmwareOperation.FLASH_PREPARE ->
            OperationSafetyProfile.flashOperation(operation)
    }
}
