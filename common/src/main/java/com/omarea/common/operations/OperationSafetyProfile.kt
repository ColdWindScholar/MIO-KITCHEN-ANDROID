package com.omarea.common.operations

import com.omarea.common.toolchain.FirmwareOperation

/**
 * RU: Уровень подтверждения опасной операции.
 *
 * EN: Confirmation level for a dangerous operation.
 */
enum class ConfirmationLevel {
    /** RU: Операция безопасна, подтверждение не требуется.
     *  EN: The operation is safe, no confirmation needed. */
    NONE,

    /** RU: Простое yes/no подтверждение.
     *  EN: Simple yes/no confirmation. */
    STANDARD,

    /** RU: Подтверждение с предупреждением о риске.
     *  EN: Confirmation with a risk warning. */
    WARNING,

    /** RU: Подтверждение с явным вводом текста (например, "FLASH").
     *  EN: Confirmation requiring explicit text input (e.g. "FLASH"). */
    DESTRUCTIVE
}

/**
 * RU: Профиль безопасности операции.
 *
 * Определяет, насколько операция опасна и какие подтверждения/предусловия
 * требуются. Это типизированная замена россыпи `Boolean requiresRoot`,
 * `Boolean isDangerous`, `Boolean dryRunSupported`, разбросанной по UI.
 *
 * EN: Operation safety profile.
 *
 * Defines how dangerous an operation is and what confirmations/preconditions
 * are required. This is a typed replacement for the scatter of
 * `Boolean requiresRoot`, `Boolean isDangerous`, `Boolean dryRunSupported`
 * across the UI.
 */
data class OperationSafetyProfile(
    val operation: FirmwareOperation,
    val isDestructive: Boolean,
    val requiresBackup: Boolean,
    val requiresRoot: Boolean,
    val requiresDeviceConnection: Boolean,
    val supportsDryRun: Boolean,
    val confirmationLevel: ConfirmationLevel
) {
    /**
     * RU: Возвращает `true`, если операция безопасна для запуска без
     *     подтверждения пользователя.
     *
     * EN: Returns `true` when the operation is safe to run without user
     *     confirmation.
     */
    val isSafe: Boolean get() = !isDestructive && confirmationLevel == ConfirmationLevel.NONE

    /**
     * RU: Возвращает короткое человекочитаемое описание риска.
     * EN: Returns a short human-readable risk description.
     */
    val riskLabel: String
        get() = when (confirmationLevel) {
            ConfirmationLevel.NONE -> "safe"
            ConfirmationLevel.STANDARD -> "low risk"
            ConfirmationLevel.WARNING -> "medium risk"
            ConfirmationLevel.DESTRUCTIVE -> "high risk"
        }

    companion object {
        /**
         * RU: Создаёт профиль для операции, которая только читает данные.
         *
         * EN: Creates a profile for a read-only operation.
         */
        fun readOnly(operation: FirmwareOperation) = OperationSafetyProfile(
            operation = operation,
            isDestructive = false,
            requiresBackup = false,
            requiresRoot = false,
            requiresDeviceConnection = false,
            supportsDryRun = true,
            confirmationLevel = ConfirmationLevel.NONE
        )

        /**
         * RU: Создаёт профиль для операции упаковки (изменяет только workspace).
         *
         * EN: Creates a profile for a pack operation (only modifies the workspace).
         */
        fun packOperation(operation: FirmwareOperation) = OperationSafetyProfile(
            operation = operation,
            isDestructive = false,
            requiresBackup = false,
            requiresRoot = false,
            requiresDeviceConnection = false,
            supportsDryRun = true,
            confirmationLevel = ConfirmationLevel.STANDARD
        )

        /**
         * RU: Создаёт профиль для операции flash (деструктивная, требует root).
         *
         * EN: Creates a profile for a flash operation (destructive, requires root).
         */
        fun flashOperation(operation: FirmwareOperation) = OperationSafetyProfile(
            operation = operation,
            isDestructive = true,
            requiresBackup = true,
            requiresRoot = true,
            requiresDeviceConnection = true,
            supportsDryRun = true,
            confirmationLevel = ConfirmationLevel.DESTRUCTIVE
        )
    }
}
