package com.omarea.common.shell.runtime

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

/**
 * RU: Реализация [ShellRuntime], которая НЕ выполняет shell.
 *
 * Используется:
 *   - в dry-run режиме (пользователь хочет увидеть, какие команды будут запущены);
 *   - в тестах, где нужна только структура [ShellEvent] без побочных эффектов;
 *   - в средах без shell (например, preview/CI smoke).
 *
 * Поведение:
 *   - выпускает одно [ShellEvent.Stdout] с текстом скрипта (чтобы видеть, что
 *     планировалось запустить);
 *   - выпускает [ShellEvent.Completed] с exitCode = 0.
 *
 * EN: [ShellRuntime] implementation that does NOT execute shell.
 *
 * Used for:
 *   - dry-run mode (the user wants to see what would be executed);
 *   - tests that need only the [ShellEvent] structure without side effects;
 *   - shell-less environments (preview, CI smoke).
 *
 * Behavior:
 *   - emits a single [ShellEvent.Stdout] with the script text (so the caller
 *     can see what was planned);
 *   - emits [ShellEvent.Completed] with exitCode = 0.
 */
class DryRunShellRuntime : ShellRuntime {
    override val name: String = "dry-run"

    override fun execute(command: ShellCommand): Flow<ShellEvent> = flow {
        val preview = when (val s = command.script) {
            is ScriptSource.Inline -> s.script
            is ScriptSource.FilePath -> "# would execute: ${s.path}${if (s.inAssets) " (asset)" else ""}"
            is ScriptSource.PreparedFile -> "# would execute: ${s.file.absolutePath}"
        }
        emit(ShellEvent.Stdout(preview))
        emit(ShellEvent.Completed(exitCode = 0))
    }
}
