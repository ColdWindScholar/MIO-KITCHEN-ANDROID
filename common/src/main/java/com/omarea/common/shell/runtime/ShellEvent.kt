package com.omarea.common.shell.runtime

/**
 * RU: Потоковое событие выполнения shell-команды.
 *
 * События выпускаются реализацией [ShellRuntime.execute] в порядке возникновения.
 * Завершающее событие — [Completed] (успех или ненулевой exit code) или
 * [Error] (процесс не смог запуститься/упал).
 *
 * EN: Streaming event emitted while executing a shell command.
 *
 * Events are produced by [ShellRuntime.execute] in order. The terminal event is
 * either [Completed] (success or non-zero exit code) or [Error] (the process
 * failed to start/crashed).
 */
sealed class ShellEvent {
    /**
     * RU: Строка из stdout.
     * EN: A line from stdout.
     */
    data class Stdout(val line: String) : ShellEvent()

    /**
     * RU: Строка из stderr.
     * EN: A line from stderr.
     */
    data class Stderr(val line: String) : ShellEvent()

    /**
     * RU: Прогресс операции (если парсер runtime смог его извлечь).
     *
     * @param percent 0..100 или `null`, если неизвестен.
     * @param message произвольное текстовое сообщение.
     *
     * EN: Operation progress (if the runtime parser could extract it).
     *
     * @param percent 0..100 or `null` when unknown.
     * @param message arbitrary text message.
     */
    data class Progress(val percent: Int?, val message: String?) : ShellEvent() {
        init {
            require(percent == null || percent in 0..100) { "percent must be 0..100 or null" }
        }
    }

    /**
     * RU: Предупреждение, не прерывающее выполнение.
     * EN: A warning that does not interrupt execution.
     */
    data class Warning(val message: String) : ShellEvent()

    /**
     * RU: Фатальная ошибка — выполнение остановлено.
     * EN: Fatal error — execution stopped.
     */
    data class Error(val message: String, val cause: Throwable? = null) : ShellEvent()

    /**
     * RU: Команда завершилась с указанным кодом.
     *
     * Код 0 — успех, ненулевой — неуспех, но без исключения.
     *
     * EN: The command finished with the given exit code.
     *
     * Code 0 means success, non-zero means failure but without an exception.
     */
    data class Completed(val exitCode: Int) : ShellEvent()
}
