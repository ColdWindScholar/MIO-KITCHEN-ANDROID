package com.omarea.common.shell.runtime

/**
 * RU: Выбирает подходящий [ShellRuntime] для команды.
 *
 * Учитывает:
 *   - `requiresRoot` в [ShellCommand];
 *   - глобальный dry-run флаг;
 *   - доступность root.
 *
 * Это слой между UI/parcerом и конкретными реализациями. UI не должен знать,
 * какая реализация [ShellRuntime] будет использоваться.
 *
 * EN: Selects an appropriate [ShellRuntime] for a command.
 *
 * Considers:
 *   - `requiresRoot` in [ShellCommand];
 *   - a global dry-run flag;
 *   - root availability.
 *
 * This is the layer between UI/parser and concrete [ShellRuntime] implementations.
 * The UI should not know which [ShellRuntime] implementation will be used.
 */
class ShellRuntimeFactory(
    private val rootAvailable: () -> Boolean = { false },
    private val dryRun: () -> Boolean = { false },
    private val rootRuntime: RootShellRuntime = RootShellRuntime(),
    private val userRuntime: UserShellRuntime = UserShellRuntime(),
    private val dryRunRuntime: DryRunShellRuntime = DryRunShellRuntime()
) {
    /**
     * RU: Возвращает runtime для [command].
     *
     * Логика:
     *   1. Если включён dry-run — всегда [DryRunShellRuntime].
     *   2. Если `command.requiresRoot` и root доступен — [RootShellRuntime].
     *   3. Иначе — [UserShellRuntime].
     *
     * EN: Returns the runtime for [command].
     *
     * Logic:
     *   1. If dry-run is enabled — always [DryRunShellRuntime].
     *   2. If `command.requiresRoot` and root is available — [RootShellRuntime].
     *   3. Otherwise — [UserShellRuntime].
     */
    fun runtimeFor(command: ShellCommand): ShellRuntime {
        if (dryRun()) return dryRunRuntime
        return when {
            command.requiresRoot && rootAvailable() -> rootRuntime
            else -> userRuntime
        }
    }
}
