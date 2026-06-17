package com.omarea.common.shell

import java.io.BufferedReader
import java.io.InputStreamReader

/**
 * RU: Legacy-фасад для совместимости со старым кодом, который использовал
 *     `KeepShellPublic.checkRoot()` / `KeepShellPublic.doCmdSync()` /
 *     `KeepShellPublic.tryExit()`.
 *
 * Stage 22-23: ранее `KeepShellPublic` владел двумя static `KeepShell`-
 * инстансами. Теперь это self-contained объект, использующий `Runtime.exec()`
 * напрямую. Не зависит от модуля `krscript` (нет циклической зависимости).
 *
 * Root-статус устанавливается извне через [setRooted] — обычно вызывается
 * из `LegacyShellBridge.init()`.
 *
 * EN: Legacy facade for compatibility with old code that used
 *     `KeepShellPublic.checkRoot()` / `KeepShellPublic.doCmdSync()` /
 *     `KeepShellPublic.tryExit()`.
 *
 * Stage 22-23: previously `KeepShellPublic` owned two static `KeepShell`
 * instances. Now it is a self-contained object using `Runtime.exec()`
 * directly. Does not depend on the `krscript` module (no circular dependency).
 *
 * Root status is set externally via [setRooted] — typically called from
 * `LegacyShellBridge.init()`.
 */
object KeepShellPublic {

    @Volatile
    private var rooted: Boolean = false

    /**
     * RU: Устанавливает root-статус. Вызывается из `LegacyShellBridge.init()`
     *     после проверки root.
     *
     * EN: Sets the root status. Called from `LegacyShellBridge.init()` after
     *     the root check.
     */
    @JvmStatic
    fun setRooted(value: Boolean) {
        rooted = value
    }

    @JvmStatic
    fun checkRoot(): Boolean {
        return rooted
    }

    @JvmStatic
    fun doCmdSync(cmd: String): String {
        if (cmd.isEmpty()) return ""
        return try {
            val process = Runtime.getRuntime().exec(if (rooted) "su" else "sh")
            val stdin = process.outputStream
            stdin.write(cmd.toByteArray(Charsets.UTF_8))
            stdin.write("\nexit\n".toByteArray(Charsets.UTF_8))
            stdin.flush()
            stdin.close()

            val reader = BufferedReader(InputStreamReader(process.inputStream, Charsets.UTF_8))
            val sb = StringBuilder()
            var line: String?
            while (reader.readLine().also { line = it } != null) {
                sb.append(line).append('\n')
            }
            process.waitFor()
            sb.toString().trim()
        } catch (_: Throwable) {
            ""
        }
    }

    @JvmStatic
    fun tryExit() {
        // RU: ShellRuntime — stateless interface, сессия не удерживается.
        //     Это no-op.
        // EN: ShellRuntime is a stateless interface, no session is held.
        //     This is a no-op.
    }
}
