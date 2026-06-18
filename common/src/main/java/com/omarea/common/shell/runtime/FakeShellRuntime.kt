package com.omarea.common.shell.runtime

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

/**
 * RU: Реализация [ShellRuntime] для тестов.
 *
 * Позволяет вызывающей стороне (тесту) заранее задать:
 *   - какие события выпустить;
 *   - какой [ShellResult] вернуть;
 *   - сколько команд было выполнено и с какими параметрами.
 *
 * EN: [ShellRuntime] implementation for tests.
 *
 * Lets the caller (a test) pre-define:
 *   - which events to emit;
 *   - which [ShellResult] to return;
 *   - how many commands were executed and with which parameters.
 */
class FakeShellRuntime : ShellRuntime {
    override val name: String = "fake"

    /** RU: Список всех команд, переданных в [execute].
     *  EN: All commands passed to [execute]. */
    val recordedCommands: MutableList<ShellCommand> = mutableListOf()

    /** RU: События, которые выпустит следующая команда.
     *  EN: Events that the next command will emit. */
    var nextEvents: List<ShellEvent> = listOf(ShellEvent.Completed(0))

    /** RU: Произвольный ответ для [executeForResult].
     *  EN: Custom response for [executeForResult]. */
    var nextResult: ShellResult? = null

    override fun execute(command: ShellCommand): Flow<ShellEvent> {
        recordedCommands.add(command)
        val events = nextEvents
        return flow {
            for (event in events) emit(event)
        }
    }

    override suspend fun executeForResult(command: ShellCommand): ShellResult {
        recordedCommands.add(command)
        return nextResult ?: super.executeForResult(command)
    }

    /**
     * RU: Сбрасывает записанные команды и предустановленные ответы.
     * EN: Resets recorded commands and pre-set responses.
     */
    fun reset() {
        recordedCommands.clear()
        nextEvents = listOf(ShellEvent.Completed(0))
        nextResult = null
    }
}
