package com.omarea.common.operations

import com.omarea.common.firmware.FirmwareProfile
import com.omarea.common.firmware.FirmwareSource
import com.omarea.common.shell.runtime.ScriptSource
import com.omarea.common.shell.runtime.ShellCommand
import com.omarea.common.shell.runtime.ShellEvent
import com.omarea.common.shell.runtime.ShellRuntime
import com.omarea.common.shell.runtime.ShellResult
import com.omarea.common.toolchain.FirmwareOperation
import kotlinx.coroutines.flow.Flow

/**
 * RU: План выполнения операции, готовый к запуску через [ShellRuntime].
 *
 * Содержит уже построенный [ShellCommand] с правильным env, workingDir и
 * requiresRoot. Это результат работы [OperationExecutor.prepare].
 *
 * EN: Execution plan ready to be launched via [ShellRuntime].
 *
 * Contains a fully-built [ShellCommand] with the right env, workingDir, and
 * requiresRoot. This is the output of [OperationExecutor.prepare].
 */
data class PreparedExecution(
    val operation: FirmwareOperation,
    val plan: OperationPlan,
    val command: ShellCommand,
    val dryRun: Boolean
) {
    /**
     * RU: Возвращает `true`, если выполнение готово к запуску.
     * EN: Returns `true` when the execution is ready to be launched.
     */
    val ready: Boolean get() = plan.canExecute || dryRun

    /**
     * RU: Возвращает список человекочитаемых блокировок (пусто, если `ready`).
     * EN: Returns human-readable blockers (empty when `ready`).
     */
    fun blockers(): List<String> = if (dryRun) emptyList() else plan.blockers()
}

/**
 * RU: Executor, который превращает [OperationPlan] в [ShellCommand] и
 *     запускает его через [ShellRuntime].
 *
 * Контракт:
 *   - НЕ строит shell-скрипт вручную — использует скрипты из assets/script2/;
 *   - НЕ показывает UI;
 *   - НЕ хранит глобальное состояние;
 *   - делегирует выполнение в [ShellRuntime];
 *   - уважает dry-run (возвращает preview вместо реального запуска).
 *
 * EN: Executor that turns an [OperationPlan] into a [ShellCommand] and runs it
 *     via [ShellRuntime].
 *
 * Contract:
 *   - does NOT build shell scripts by hand — uses scripts from assets/script2/;
 *   - does NOT show UI;
 *   - does NOT hold global state;
 *   - delegates execution to [ShellRuntime];
 *   - respects dry-run (returns a preview instead of really running).
 */
class OperationExecutor(
    private val shellRuntime: ShellRuntime,
    private val scriptLocator: (FirmwareOperation) -> String? = ::defaultScriptLocator,
    private val workspacePathProvider: (FirmwareProfile) -> String? = { null },
    private val toolsDirProvider: () -> String? = { null }
) {

    /**
     * RU: Готовит [PreparedExecution] для [plan].
     *
     * Шаги:
     *   1. Проверить, что план готов (`canExecute` или dry-run).
     *   2. Найти shell-скрипт для операции через [scriptLocator].
     *   3. Построить env (TOOLS_DIR, WORK_DIR, FIRMWARE_PATH, OPERATION).
     *   4. Определить requiresRoot из safety-профиля.
     *   5. Вернуть [PreparedExecution] без запуска.
     *
     * EN: Prepares a [PreparedExecution] for [plan].
     */
    fun prepare(
        plan: OperationPlan,
        dryRun: Boolean = false
    ): PreparedExecution {
        val scriptPath = scriptLocator(plan.operation)
            ?: throw OperationExecutionException(
                "No shell script is registered for operation ${plan.operation}"
            )
        val env = buildEnv(plan)
        val requiresRoot = plan.safety.requiresRoot && !dryRun
        val command = ShellCommand.create(
            script = ScriptSource.FilePath(path = scriptPath, inAssets = true),
            env = env,
            requiresRoot = requiresRoot,
            timeoutMs = null,
            tag = plan.operation.name
        )
        return PreparedExecution(
            operation = plan.operation,
            plan = plan,
            command = command,
            dryRun = dryRun
        )
    }

    /**
     * RU: Запускает [prepared] через [ShellRuntime] и возвращает поток событий.
     *
     * В dry-run режиме используется [DryRunShellRuntime]-подобное поведение
     * shellRuntime (вызывающая сторона должна передать DryRun runtime).
     *
     * EN: Launches [prepared] via [ShellRuntime] and returns the event flow.
     */
    fun execute(prepared: PreparedExecution): Flow<ShellEvent> {
        if (!prepared.ready) {
            throw OperationExecutionException(
                "Plan is not ready. Blockers: ${prepared.blockers().joinToString("; ")}"
            )
        }
        return shellRuntime.execute(prepared.command)
    }

    /**
     * RU: Запускает [prepared] и ждёт финальный [ShellResult].
     *
     * EN: Launches [prepared] and awaits the final [ShellResult].
     */
    suspend fun executeForResult(prepared: PreparedExecution): ShellResult {
        if (!prepared.ready) {
            return ShellResult.Failed(
                "Plan is not ready. Blockers: ${prepared.blockers().joinToString("; ")}"
            )
        }
        return shellRuntime.executeForResult(prepared.command)
    }

    private fun buildEnv(plan: OperationPlan): Map<String, String> {
        val env = mutableMapOf<String, String>()
        env["OPERATION"] = plan.operation.name
        env["FIRMWARE_TYPE"] = plan.firmware.packageType.name
        plan.firmware.androidVersion.version?.let { env["FIRMWARE_ANDROID"] = it.toString() }

        // Workspace path (where the firmware has been copied).
        workspacePathProvider(plan.firmware)?.let { env["WORK_DIR"] = it }

        // Firmware source path (only DirectPath / WorkspaceFile).
        when (val src = plan.firmware.source) {
            is FirmwareSource.DirectPath -> env["FIRMWARE_PATH"] = src.path
            is FirmwareSource.WorkspaceFile -> env["FIRMWARE_PATH"] = src.file.absolutePath
            is FirmwareSource.FileNameOnly -> { /* no path available */ }
        }

        // Tools dir.
        toolsDirProvider()?.let { env["TOOLS_DIR"] = it }

        // Capabilities as env flags (1/0).
        val caps = plan.firmware.capabilities
        env["HAS_PAYLOAD_BIN"] = if (caps.hasPayloadBin) "1" else "0"
        env["HAS_SUPER_IMAGE"] = if (caps.hasSuperImage) "1" else "0"
        env["HAS_DYNAMIC_PARTITIONS"] = if (caps.hasDynamicPartitions) "1" else "0"
        env["HAS_EROFS"] = if (caps.hasErofs) "1" else "0"
        env["HAS_EXT4"] = if (caps.hasExt4) "1" else "0"
        env["HAS_BOOT_IMAGE"] = if (caps.hasBootImage) "1" else "0"
        env["HAS_VBMETA"] = if (caps.hasVbmetaImage) "1" else "0"
        env["USES_AVB"] = if (caps.usesAvb) "1" else "0"
        env["USES_AB"] = if (caps.usesAB) "1" else "0"
        env["REQUIRES_16KB_CHECK"] = if (caps.requires16KbAlignmentCheck) "1" else "0"

        // Required tools as space-separated list.
        if (plan.toolchain.profile.requiredTools.isNotEmpty()) {
            env["REQUIRED_TOOLS"] = plan.toolchain.profile.requiredTools
                .joinToString(" ") { it.name }
        }

        return env
    }

    companion object {
        /**
         * RU: Дефолтная таблица соответствия операции → shell-скрипт.
         *
         * Эти скрипты живут в `pio/src/main/assets/script2/` и вызываются
         * через `executor.sh` (legacy path).
         *
         * EN: Default mapping of operation -> shell script.
         *
         * These scripts live in `pio/src/main/assets/script2/` and are invoked
         * through `executor.sh` (legacy path).
         */
        fun defaultScriptLocator(operation: FirmwareOperation): String? = when (operation) {
            FirmwareOperation.UNPACK_ROM -> "script2/executor.sh"
            FirmwareOperation.UNPACK_SUPER -> "script2/executor.sh"
            FirmwareOperation.UNPACK_PAYLOAD_BIN -> "script2/executor.sh"
            FirmwareOperation.UNPACK_BOOT_IMAGE -> "script2/executor.sh"
            FirmwareOperation.UNPACK_VENDOR_BOOT_IMAGE -> "script2/executor.sh"
            FirmwareOperation.UNPACK_INIT_BOOT_IMAGE -> "script2/executor.sh"
            FirmwareOperation.UNPACK_DTBO_IMAGE -> "script2/executor.sh"
            FirmwareOperation.UNPACK_FILESYSTEM_IMAGE -> "script2/executor.sh"
            FirmwareOperation.PACK_FILESYSTEM_IMAGE -> "script2/executor.sh"
            FirmwareOperation.PACK_BOOT_IMAGE -> "script2/executor.sh"
            FirmwareOperation.PACK_SUPER -> "script2/executor.sh"
            FirmwareOperation.VERIFY_VBMETA -> "script2/executor.sh"
            FirmwareOperation.FLASH_PREPARE -> "script2/executor.sh"
            FirmwareOperation.INSPECT -> "script2/executor.sh"
        }
    }
}

/**
 * RU: Ошибка выполнения операции.
 * EN: Operation execution error.
 */
class OperationExecutionException(
    message: String,
    cause: Throwable? = null
) : RuntimeException(message, cause)
