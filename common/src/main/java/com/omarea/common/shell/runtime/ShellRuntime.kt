package com.omarea.common.shell.runtime

import kotlinx.coroutines.flow.Flow

/**
 * RU: Единый API выполнения shell-команд для всех Android и всех прошивок.
 *
 * Реализации:
 *   - [RootShellRuntime] — выполняет через `su`;
 *   - [UserShellRuntime] — выполняет через `sh`;
 *   - [DryRunShellRuntime] — не выполняет, возвращает предполагаемые события;
 *   - [FakeShellRuntime] — для тестов.
 *
 * Контракт:
 *   - метод [execute] выпускает поток [ShellEvent], завершающийся [ShellEvent.Completed]
 *     или [ShellEvent.Error];
 *   - метод [executeForResult] собирает поток в финальный [ShellResult];
 *   - реализация не имеет глобального состояния (сессия — в [ShellCommand.id]);
 *   - реализация должна уважать `timeoutMs` и отмену корутины.
 *
 * EN: Single shell-execution API for all Android versions and all firmwares.
 *
 * Implementations:
 *   - [RootShellRuntime] — runs via `su`;
 *   - [UserShellRuntime] — runs via `sh`;
 *   - [DryRunShellRuntime] — does not execute, returns simulated events;
 *   - [FakeShellRuntime] — for tests.
 *
 * Contract:
 *   - [execute] returns a [ShellEvent] flow that terminates with
 *     [ShellEvent.Completed] or [ShellEvent.Error];
 *   - [executeForResult] reduces the flow into a final [ShellResult];
 *   - implementations MUST NOT hold global state (session is in [ShellCommand.id]);
 *   - implementations MUST honor `timeoutMs` and coroutine cancellation.
 */
interface ShellRuntime {
    /**
     * RU: Возвращает имя реализации для логирования.
     * EN: Returns the implementation name for logging.
     */
    val name: String

    /**
     * RU: Выполняет [command] и возвращает поток событий.
     * EN: Executes [command] and returns the event flow.
     */
    fun execute(command: ShellCommand): Flow<ShellEvent>

    /**
     * RU: Выполняет [command] и возвращает финальный результат.
     *
     * По умолчанию собирает поток [execute]. Реализации могут переопределить
     * метод для более эффективного сбора.
     *
     * EN: Executes [command] and returns the final result.
     *
     * By default reduces the [execute] flow. Implementations may override
     * for a more efficient implementation.
     */
    suspend fun executeForResult(command: ShellCommand): ShellResult {
        val stdout = StringBuilder()
        val stderr = StringBuilder()
        var exitCode: Int? = null
        var error: ShellEvent.Error? = null
        var truncated = false
        val MAX_OUTPUT = 1_000_000 // 1 MB safety cap per stream

        execute(command).collect { event ->
            when (event) {
                is ShellEvent.Stdout -> {
                    if (stdout.length + event.line.length + 1 <= MAX_OUTPUT) {
                        stdout.append(event.line).append('\n')
                    } else {
                        truncated = true
                    }
                }
                is ShellEvent.Stderr -> {
                    if (stderr.length + event.line.length + 1 <= MAX_OUTPUT) {
                        stderr.append(event.line).append('\n')
                    } else {
                        truncated = true
                    }
                }
                is ShellEvent.Completed -> exitCode = event.exitCode
                is ShellEvent.Error -> error = event
                is ShellEvent.Progress, is ShellEvent.Warning -> {
                    // Ignored by the synchronous reduction.
                }
            }
        }

        return when {
            error != null -> ShellResult.Failed(error!!.message, error!!.cause)
            exitCode != null -> ShellResult.Completed(
                exitCode = exitCode!!,
                stdout = stdout.toString(),
                stderr = stderr.toString(),
                truncated = truncated
            )
            else -> ShellResult.Failed("Shell runtime terminated without Completed/Error event")
        }
    }
}
