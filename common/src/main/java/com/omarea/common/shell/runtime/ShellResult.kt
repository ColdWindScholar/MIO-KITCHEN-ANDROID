package com.omarea.common.shell.runtime

/**
 * RU: Финальный результат выполнения shell-команды.
 *
 * В отличие от потока [ShellEvent], это значение возвращает итоговое состояние
 * после завершения команды.
 *
 * EN: Final result of executing a shell command.
 *
 * Unlike the [ShellEvent] stream, this is the final state after the command
 * finishes.
 */
sealed class ShellResult {
    /**
     * RU: Команда завершилась.
     *
     * @param exitCode 0 = успех, ненулевой = неуспех.
     * @param stdout полный stdout (может быть обрезан при превышении лимита).
     * @param stderr полный stderr (может быть обрезан).
     * @param truncated `true`, если вывод был обрезан по достижении лимита.
     *
     * EN: The command finished.
     *
     * @param exitCode 0 = success, non-zero = failure.
     * @param stdout full stdout (may be truncated if a limit was reached).
     * @param stderr full stderr (may be truncated).
     * @param truncated `true` when output was truncated due to a limit.
     */
    data class Completed(
        val exitCode: Int,
        val stdout: String,
        val stderr: String,
        val truncated: Boolean = false
    ) : ShellResult() {
        val isSuccess: Boolean get() = exitCode == 0
    }

    /**
     * RU: Команда отменена вызывающей стороной.
     * EN: The command was cancelled by the caller.
     */
    data object Cancelled : ShellResult()

    /**
     * RU: Превышен таймаут.
     * EN: The command exceeded the configured timeout.
     */
    data class TimedOut(val timeoutMs: Long) : ShellResult()

    /**
     * RU: Команда не смогла запуститься (нет root, файл не найден, и т.п.).
     * EN: The command failed to start (no root, file not found, etc.).
     */
    data class Failed(val message: String, val cause: Throwable? = null) : ShellResult()
}
