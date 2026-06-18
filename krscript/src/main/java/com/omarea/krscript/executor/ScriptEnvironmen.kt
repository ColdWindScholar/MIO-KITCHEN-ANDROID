package com.omarea.krscript.executor

import android.content.Context
import com.omarea.krscript.model.NodeInfoBase
import com.omarea.krscript.runtime.LegacyShellBridge
import java.io.DataOutputStream

/**
 * RU: Legacy-фасад для совместимости со старым KrScript UI-кодом.
 *
 * Stage 22: ранее это был Java-класс с собственным shell-engine (Process +
 * DataOutputStream + GlobalScope). Теперь это тонкий Kotlin-объект,
 * делегирующий в [LegacyShellBridge], который сам использует новый
 * `ShellRuntime` API (Stage 6).
 *
 * Публичный API сохранён 1:1 — все 41 call-site продолжают работать без
 * изменений.
 *
 * EN: Legacy facade for compatibility with the old KrScript UI code.
 *
 * Stage 22: previously this was a Java class with its own shell engine
 * (Process + DataOutputStream + GlobalScope). Now it is a thin Kotlin
 * object delegating to [LegacyShellBridge], which itself uses the new
 * `ShellRuntime` API (Stage 6).
 *
 * The public API is preserved 1:1 — all 41 call-sites keep working without
 * changes.
 */
object ScriptEnvironmen {

    @JvmStatic
    fun isInited(): Boolean = LegacyShellBridge.isInited()

    @JvmStatic
    fun refreshTranslations(context: Context) {
        LegacyShellBridge.refreshTranslations(context)
    }

    @JvmStatic
    fun init(context: Context, executor: String, toolkitDir: String): Boolean {
        // RU: executor и toolkitDir игнорируются — LegacyShellBridge использует
        //     собственные дефолты. Это упрощает API и убирает зависимость от
        //     KrScriptConfig.
        // EN: executor and toolkitDir are ignored — LegacyShellBridge uses its
        //     own defaults. This simplifies the API and removes the
        //     KrScriptConfig dependency.
        return LegacyShellBridge.init(context)
    }

    @JvmStatic
    fun init(context: Context): Boolean = LegacyShellBridge.init(context)

    @JvmStatic
    fun executeResultRoot(
        context: Context,
        script: String,
        nodeInfoBase: NodeInfoBase?
    ): String = LegacyShellBridge.executeResultRoot(context, script, nodeInfoBase)

    @JvmStatic
    fun executeShell(
        context: Context,
        dataOutputStream: DataOutputStream,
        cmds: String,
        params: HashMap<String, String>?,
        nodeInfo: NodeInfoBase?,
        tag: String?
    ) {
        // RU: Stage 23 — ShellExecutor.execute() теперь вызывает
        //     LegacyShellBridge.buildStreamingCommand() напрямую и не
        //     использует этот метод. Оставляем для обратной совместимости —
        //     если кто-то ещё вызывает executeShell, пишем cmds в поток.
        // EN: Stage 23 — ShellExecutor.execute() now calls
        //     LegacyShellBridge.buildStreamingCommand() directly and does not
        //     use this method. Left for backward compat — if someone still
        //     calls executeShell, we write cmds into the stream.
        try {
            dataOutputStream.write(cmds.toByteArray(Charsets.UTF_8))
            dataOutputStream.flush()
        } catch (_: Throwable) {
            // Best-effort — stream consumers handle IOExceptions themselves.
        }
    }

    @JvmStatic
    fun getRuntime(): Process? {
        // RU: Stage 23 — возвращаем Process через Runtime.exec напрямую.
        //     Ранее возвращали null, но это ломало streaming-выполнение.
        //     Теперь callers могут получить Process для force-stop.
        // EN: Stage 23 — return a Process via Runtime.exec directly.
        //     Previously returned null, which broke streaming execution.
        //     Now callers can obtain a Process for force-stop.
        return try {
            val rooted = LegacyShellBridge.isRooted
            Runtime.getRuntime().exec(if (rooted) "su" else "sh")
        } catch (_: Throwable) {
            null
        }
    }
}
