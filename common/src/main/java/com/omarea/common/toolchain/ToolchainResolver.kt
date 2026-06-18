package com.omarea.common.toolchain

import com.omarea.common.firmware.FirmwareProfile
import com.omarea.common.runtime.DeviceProfile

/**
 * RU: План toolchain для конкретной операции.
 *
 * Содержит как сам профиль [ToolchainProfile], так и диагностику: какие
 * инструменты доступны, какие отсутствуют, какие предупреждения возникли.
 *
 * EN: Toolchain plan for a specific operation.
 *
 * Contains both the [ToolchainProfile] itself and diagnostics: which tools are
 * available, which are missing, what warnings were raised.
 */
data class ToolchainPlan(
    val profile: ToolchainProfile,
    val availableTools: Set<String>,
    val missingRequired: List<String>,
    val warnings: List<ToolchainWarning>,
    val ready: Boolean
) {
    /**
     * RU: Возвращает `true`, если план готов к выполнению.
     *
     * План готов, если `ready == true` (обязательные инструменты присутствуют и
     * ABI совместим).
     *
     * EN: Returns `true` when the plan is ready to execute.
     */
    val canExecute: Boolean get() = ready && missingRequired.isEmpty()
}

/**
 * RU: Предупреждение toolchain resolver'а.
 *
 * EN: Toolchain resolver warning.
 */
data class ToolchainWarning(
    val code: String,
    val message: String,
    val severity: Severity = Severity.WARNING
) {
    enum class Severity { INFO, WARNING, ERROR }
}

/**
 * RU: Операция над прошивкой.
 *
 * Перечисление операций, для которых toolchain resolver умеет строить план.
 *
 * EN: Firmware operation.
 *
 * Enumeration of operations for which the toolchain resolver can build a plan.
 */
enum class FirmwareOperation {
    UNPACK_ROM,
    UNPACK_SUPER,
    UNPACK_PAYLOAD_BIN,
    UNPACK_BOOT_IMAGE,
    UNPACK_VENDOR_BOOT_IMAGE,
    UNPACK_INIT_BOOT_IMAGE,
    UNPACK_DTBO_IMAGE,
    UNPACK_FILESYSTEM_IMAGE,
    PACK_FILESYSTEM_IMAGE,
    PACK_BOOT_IMAGE,
    PACK_SUPER,
    VERIFY_VBMETA,
    FLASH_PREPARE,
    INSPECT
}

/**
 * RU: Резолвер toolchain.
 *
 * Контракт:
 *   - НЕ запускает shell (только читает манифест и метаданные устройства);
 *   - НЕ модифицирует файловую систему;
 *   - чистая функция от (operation, firmwareProfile, deviceProfile).
 *
 * EN: Toolchain resolver.
 *
 * Contract:
 *   - does NOT run shell (only reads the manifest and device metadata);
 *   - does NOT modify the filesystem;
 *   - pure function of (operation, firmwareProfile, deviceProfile).
 */
interface ToolchainResolver {
    /**
     * RU: Возвращает план toolchain для операции [operation] над прошивкой
     *     [firmware] на устройстве [device].
     *
     * EN: Returns the toolchain plan for [operation] on [firmware] running on
     *     [device].
     */
    fun resolve(
        operation: FirmwareOperation,
        firmware: FirmwareProfile,
        device: DeviceProfile
    ): ToolchainPlan
}
