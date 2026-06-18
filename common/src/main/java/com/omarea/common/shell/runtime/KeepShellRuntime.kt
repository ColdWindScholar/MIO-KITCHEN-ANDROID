package com.omarea.common.shell.runtime

import com.omarea.common.shell.ShellTranslation
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withTimeoutOrNull
import java.io.BufferedReader
import java.io.InputStreamReader

/**
 * RU: Реализация [ShellRuntime] через `Runtime.exec("su")` / `Runtime.exec("sh")`.
 *
 * Stage 22: ранее эта реализация оборачивала legacy `KeepShell`-движок. Теперь
 * `KeepShell` удалён, и `KeepShellRuntime` использует `Runtime.exec()` напрямую.
 * Это убирает последнее глобальное состояние (`KeepShellPublic.defaultKeepShell`)
 * и `GlobalScope` usage.
 *
 * Параметр [rootMode] выбирает между root (`su`) и user (`sh`) shell.
 *
 * EN: [ShellRuntime] implementation via `Runtime.exec("su")` /
 *     `Runtime.exec("sh")`.
 *
 * Stage 22: previously this implementation wrapped the legacy `KeepShell`
 * engine. Now `KeepShell` is removed and `KeepShellRuntime` uses
 * `Runtime.exec()` directly. This removes the last global state
 * (`KeepShellPublic.defaultKeepShell`) and `GlobalScope` usage.
 *
 * [rootMode] selects between root (`su`) and user (`sh`) shell.
 */
open class KeepShellRuntime(
    private val rootMode: Boolean,
    private val shellTranslation: ShellTranslation? = null
) : ShellRuntime {

    override val name: String = if (rootMode) "keep-shell-root" else "keep-shell-user"

    override fun execute(command: com.omarea.common.shell.runtime.ShellCommand): Flow<com.omarea.common.shell.runtime.ShellEvent> = flow {
        val scriptText = resolveScriptText(command)
        if (scriptText.isEmpty()) {
            emit(com.omarea.common.shell.runtime.ShellEvent.Error("Empty script"))
            return@flow
        }
        try {
            val envPrefix = buildEnvPrefix(command)
            val fullCommand = envPrefix + scriptText

            val raw = if (command.timeoutMs != null) {
                withTimeoutOrNull(command.timeoutMs) {
                    runShell(fullCommand)
                }
            } else {
                runShell(fullCommand)
            }

            if (raw == null) {
                emit(com.omarea.common.shell.runtime.ShellEvent.Error("Timeout after ${command.timeoutMs} ms"))
                return@flow
            }

            val translated = shellTranslation?.resolveRow(raw) ?: raw
            if (translated.isNotEmpty()) {
                translated.split('\n').forEach { line ->
                    if (line.isNotEmpty()) emit(com.omarea.common.shell.runtime.ShellEvent.Stdout(line))
                }
            }
            emit(com.omarea.common.shell.runtime.ShellEvent.Completed(exitCode = 0))
        } catch (t: Throwable) {
            emit(com.omarea.common.shell.runtime.ShellEvent.Error(t.message ?: t.javaClass.simpleName, t))
        }
    }.flowOn(Dispatchers.IO)

    /**
     * RU: Запускает shell-команду через `Runtime.exec()`. Блокирующий вызов —
     *     должен запускаться на `Dispatchers.IO` (обеспечивается `flowOn`
     *     выше).
     *
     * EN: Runs a shell command via `Runtime.exec()`. Blocking — must be run
     *     on `Dispatchers.IO` (ensured by `flowOn` above).
     */
    private fun runShell(command: String): String {
        val process = try {
            Runtime.getRuntime().exec(if (rootMode) arrayOf("su") else arrayOf("sh"))
        } catch (e: Throwable) {
            return "error"
        }
        try {
            val stdin = process.outputStream
            stdin.write(command.toByteArray(Charsets.UTF_8))
            stdin.write("\nexit\n".toByteArray(Charsets.UTF_8))
            stdin.flush()
            stdin.close()

            val stdoutReader = BufferedReader(InputStreamReader(process.inputStream, Charsets.UTF_8))
            val stderrReader = BufferedReader(InputStreamReader(process.errorStream, Charsets.UTF_8))

            val stdoutBuilder = StringBuilder()
            val stderrBuilder = StringBuilder()
            var line: String?
            while (stdoutReader.readLine().also { line = it } != null) {
                stdoutBuilder.append(line).append('\n')
            }
            while (stderrReader.readLine().also { line = it } != null) {
                stderrBuilder.append(line).append('\n')
            }

            process.waitFor()

            // RU: если stderr непустой и stdout пустой — возвращаем "error"
            //     для совместимости с legacy KeepShell behavior.
            // EN: if stderr is non-empty and stdout is empty — return "error"
            //     for compatibility with the legacy KeepShell behavior.
            val stdout = stdoutBuilder.toString().trim()
            val stderr = stderrBuilder.toString().trim()
            return if (stdout.isEmpty() && stderr.isNotEmpty()) {
                "error"
            } else {
                stdout
            }
        } catch (e: Throwable) {
            try { process.destroy() } catch (_: Throwable) {}
            return "error"
        }
    }

    private fun resolveScriptText(command: com.omarea.common.shell.runtime.ShellCommand): String =
        when (val s = command.script) {
            is com.omarea.common.shell.runtime.ScriptSource.Inline -> s.script
            is com.omarea.common.shell.runtime.ScriptSource.FilePath -> {
                if (s.inAssets) {
                    "# asset path: ${s.path}"
                } else {
                    "sh '${s.path}'"
                }
            }
            is com.omarea.common.shell.runtime.ScriptSource.PreparedFile -> "sh '${s.file.absolutePath}'"
        }

    private fun buildEnvPrefix(command: com.omarea.common.shell.runtime.ShellCommand): String {
        if (command.env.isEmpty()) return ""
        val sb = StringBuilder()
        for ((key, value) in command.env) {
            val escaped = value.replace("'", "'\\''")
            sb.append("export ").append(key).append("='").append(escaped).append("'\n")
        }
        return sb.toString()
    }
}

/**
 * RU: [ShellRuntime] через root-сессию (`su`).
 * EN: [ShellRuntime] backed by a root (`su`) session.
 */
class RootShellRuntime(
    shellTranslation: ShellTranslation? = null
) : KeepShellRuntime(rootMode = true, shellTranslation = shellTranslation) {
    override val name: String = "root"
}

/**
 * RU: [ShellRuntime] через обычную user-сессию (`sh`).
 * EN: [ShellRuntime] backed by a user (`sh`) session.
 */
class UserShellRuntime(
    shellTranslation: ShellTranslation? = null
) : KeepShellRuntime(rootMode = false, shellTranslation = shellTranslation) {
    override val name: String = "user"
}
