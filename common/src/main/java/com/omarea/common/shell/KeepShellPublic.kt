package com.omarea.common.shell

import com.omarea.krscript.runtime.LegacyShellBridge

/**
 * RU: Legacy-фасад для совместимости со старым кодом, который использовал
 *     `KeepShellPublic.checkRoot()` / `KeepShellPublic.doCmdSync()` /
 *     `KeepShellPublic.tryExit()`.
 *
 * Stage 22: ранее `KeepShellPublic` владел двумя static `KeepShell`-
 * инстансами (default + secondary) и использовал `GlobalScope` для записи в
 * их streams. Теперь это тонкий объект, делегирующий в [LegacyShellBridge],
 * который сам использует новый `ShellRuntime` API.
 *
 * ВАЖНО: `KeepShell.kt` удалён. Старый код, который явно конструировал
 * `KeepShell(rootMode = ...)`, должен использовать `KeepShellRuntime`
 * напрямую (Stage 6).
 *
 * EN: Legacy facade for compatibility with old code that used
 *     `KeepShellPublic.checkRoot()` / `KeepShellPublic.doCmdSync()` /
 *     `KeepShellPublic.tryExit()`.
 *
 * Stage 22: previously `KeepShellPublic` owned two static `KeepShell`
 * instances (default + secondary) and used `GlobalScope` to write into their
 * streams. Now it is a thin object delegating to [LegacyShellBridge], which
 * itself uses the new `ShellRuntime` API.
 *
 * IMPORTANT: `KeepShell.kt` is removed. Old code that explicitly constructed
 * `KeepShell(rootMode = ...)` must use `KeepShellRuntime` directly (Stage 6).
 */
object KeepShellPublic {

    @JvmStatic
    fun checkRoot(): Boolean = LegacyShellBridge.checkRoot()

    @JvmStatic
    fun doCmdSync(cmd: String): String = LegacyShellBridge.doCmdSync(cmd)

    @JvmStatic
    fun tryExit() {
        LegacyShellBridge.tryExit()
    }
}
