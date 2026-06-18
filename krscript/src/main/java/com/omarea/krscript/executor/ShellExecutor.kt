package com.omarea.krscript.executor

import android.content.Context
import android.os.Build
import android.util.Log
import android.widget.Toast
import com.omarea.krscript.R
import com.omarea.krscript.model.RunnableNode
import com.omarea.krscript.model.ShellHandlerBase
import com.omarea.krscript.runtime.LegacyShellBridge
import java.io.DataOutputStream

/**
 * RU: Streaming shell-executor для KrScript actions.
 *
 * Stage 23: ранее этот класс получал `Process` из `ScriptEnvironmen.getRuntime()`,
 * который после Stage 22 возвращает `null`. Теперь `ShellExecutor` создаёт
 * собственный `Process` через `Runtime.exec("su")` / `Runtime.exec("sh")` и
 * пишет команду, построенную `LegacyShellBridge.buildStreamingCommand()`.
 *
 * Контракт с callers (`DialogLogFragment`, `ActionListFragment.runHiddenAction`)
 * сохранён 1:1: метод `execute(...)` возвращает `Process?` (для force-stop),
 * streaming stdout/stderr обрабатывается через `SimpleShellWatcher`.
 *
 * EN: Streaming shell executor for KrScript actions.
 *
 * Stage 23: previously this class obtained a `Process` from
 * `ScriptEnvironmen.getRuntime()`, which returns `null` after Stage 22.
 * Now `ShellExecutor` creates its own `Process` via `Runtime.exec("su")` /
 * `Runtime.exec("sh")` and writes the command built by
 * `LegacyShellBridge.buildStreamingCommand()`.
 *
 * The contract with callers (`DialogLogFragment`,
 * `ActionListFragment.runHiddenAction`) is preserved 1:1: the `execute(...)`
 * method returns a `Process?` (for force-stop), streaming stdout/stderr is
 * handled through `SimpleShellWatcher`.
 */
class ShellExecutor {
    private var started = false
    private val sessionTag = "pio_" + System.currentTimeMillis()

    /**
     * RU: Запускает скрипт [cmds] в streaming-режиме.
     *
     * @param context Android context.
     * @param nodeInfo узел, для которого выполняется скрипт (для PAGE_CONFIG_*).
     * @param cmds скрипт (inline или `file:///android_asset/...`).
     * @param onExit callback, вызывается после завершения процесса.
     * @param params дополнительные env-переменные (ignored — LegacyShellBridge
     *   строит env из context + nodeInfo).
     * @param shellHandlerBase handler для streaming stdout/stderr.
     * @return `Process` для force-stop, или `null` при ошибке.
     *
     * EN: Runs the [cmds] script in streaming mode.
     */
    fun execute(
        context: Context,
        nodeInfo: RunnableNode,
        cmds: String,
        onExit: Runnable?,
        params: HashMap<String, String>?,
        shellHandlerBase: ShellHandlerBase
    ): Process? {
        if (started) {
            return null
        }

        // RU: создаём Process напрямую через Runtime.exec.
        // EN: create the Process directly via Runtime.exec.
        val process = try {
            val rooted = LegacyShellBridge.isRooted
            Runtime.getRuntime().exec(if (rooted) "su" else "sh")
        } catch (e: Throwable) {
            Log.e("ShellExecutor", "Could not start shell process", e)
            null
        }

        if (process == null) {
            Toast.makeText(context, R.string.shell_process_start_failed, Toast.LENGTH_SHORT).show()
            onExit?.run()
        } else {
            val forceStopRunnable = if (nodeInfo.interruptable || nodeInfo.shell == RunnableNode.shellModeBgTask) {
                Runnable {
                    killProcess(context)
                    try { process.inputStream.close() } catch (_: Throwable) {}
                    try { process.outputStream.close() } catch (_: Throwable) {}
                    try { process.errorStream.close() } catch (_: Throwable) {}
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                        try { process.destroyForcibly() } catch (ex: Throwable) {
                            Log.e("KrScriptError", ex.message ?: "")
                        }
                    } else {
                        try { process.destroy() } catch (ex: Throwable) {
                            Log.e("KrScriptError", ex.message ?: "")
                        }
                    }
                }
            } else {
                null
            }

            SimpleShellWatcher().setHandler(context, process, shellHandlerBase, onExit)

            try {
                shellHandlerBase.sendMessage(
                    shellHandlerBase.obtainMessage(ShellHandlerBase.EVENT_START, "shell@android:\n")
                )
                shellHandlerBase.sendMessage(
                    shellHandlerBase.obtainMessage(ShellHandlerBase.EVENT_START, "$cmds\n\n")
                )
                shellHandlerBase.onStart(forceStopRunnable)

                // RU: Stage 23 — строим full streaming command через
                //     LegacyShellBridge и пишем в Process.stdin.
                // EN: Stage 23 — build the full streaming command via
                //     LegacyShellBridge and write it into Process.stdin.
                val fullCommand = LegacyShellBridge.buildStreamingCommand(
                    context = context,
                    script = cmds,
                    nodeInfoBase = nodeInfo,
                    tag = sessionTag
                )
                val dataOutputStream = DataOutputStream(process.outputStream)
                dataOutputStream.write(fullCommand.toByteArray(Charsets.UTF_8))
                dataOutputStream.flush()
            } catch (e: Throwable) {
                Log.e("ShellExecutor", "Failed to write command to process", e)
                process.destroy()
            }
            started = true
        }
        return process
    }

    /**
     * RU: Убивает фоновые процессы с текущим sessionTag.
     *
     * EN: Kills background processes with the current sessionTag.
     */
    private fun killProcess(context: Context) {
        try {
            LegacyShellBridge.doCmdSync(
                String.format("kill -s 1 `pgrep -f %s`", sessionTag)
            )
        } catch (_: Throwable) {
            // best-effort
        }
    }
}
