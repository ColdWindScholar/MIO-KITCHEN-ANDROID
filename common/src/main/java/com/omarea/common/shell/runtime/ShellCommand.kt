package com.omarea.common.shell.runtime

import java.io.File

/**
 * RU: Типизированная команда для [ShellRuntime].
 *
 * Содержит:
 *   - идентификатор сессии (`id`) для корреляции логов и отмены;
 *   - источник скрипта [ScriptSource];
 *   - переменные окружения (без глобального состояния);
 *   - рабочую директорию;
 *   - флаг `requiresRoot`;
 *   - опциональный таймаут;
 *   - опциональный тег для логирования.
 *
 * EN: Typed command for [ShellRuntime].
 *
 * Contains:
 *   - a session `id` for log correlation and cancellation;
 *   - the [ScriptSource];
 *   - environment variables (no global state);
 *   - the working directory;
 *   - a `requiresRoot` flag;
 *   - an optional timeout;
 *   - an optional tag for logging.
 */
data class ShellCommand(
    val id: String,
    val script: ScriptSource,
    val env: Map<String, String> = emptyMap(),
    val workingDir: File? = null,
    val requiresRoot: Boolean = false,
    val timeoutMs: Long? = null,
    val tag: String? = null
) {
    init {
        require(id.isNotEmpty()) { "ShellCommand.id must not be empty" }
        require(timeoutMs == null || timeoutMs > 0) { "timeoutMs must be positive" }
    }

    companion object {
        /**
         * RU: Создаёт команду с автоматически сгенерированным идентификатором.
         * EN: Creates a command with an auto-generated id.
         */
        fun create(
            script: ScriptSource,
            env: Map<String, String> = emptyMap(),
            workingDir: File? = null,
            requiresRoot: Boolean = false,
            timeoutMs: Long? = null,
            tag: String? = null
        ): ShellCommand = ShellCommand(
            id = java.util.UUID.randomUUID().toString(),
            script = script,
            env = env,
            workingDir = workingDir,
            requiresRoot = requiresRoot,
            timeoutMs = timeoutMs,
            tag = tag
        )
    }
}
