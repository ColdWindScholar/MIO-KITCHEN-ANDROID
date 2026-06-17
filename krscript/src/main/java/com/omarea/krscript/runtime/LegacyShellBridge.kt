package com.omarea.krscript.runtime

import android.content.Context
import android.os.Build
import android.os.Environment
import com.omarea.common.shell.ShellTranslation
import com.omarea.common.shell.runtime.RootShellRuntime
import com.omarea.common.shell.runtime.ScriptSource
import com.omarea.common.shell.runtime.ShellCommand
import com.omarea.common.shell.runtime.ShellRuntime
import com.omarea.common.shell.runtime.ShellResult
import com.omarea.common.shell.runtime.UserShellRuntime
import com.omarea.common.toolchain.ToolManifestLoader
import com.omarea.common.toolchain.ToolchainInstallResult
import com.omarea.common.toolchain.ToolchainInstaller
import com.omarea.krscript.executor.ExtractAssets
import com.omarea.krscript.model.NodeInfoBase
import kotlinx.coroutines.runBlocking
import java.io.File

/**
 * RU: Единая точка моста между legacy-API (`ScriptEnvironmen`,
 * `KeepShellPublic`, `ShellExecutor`) и новой runtime-архитектурой
 * (`ShellRuntime`, `RuntimeBinder`).
 *
 * Stage 22: до этого этапа legacy-классы (`KeepShell`, `KeepShellPublic`,
 * `ScriptEnvironmen`) владели собственным shell-engine на базе `Process` +
 * `DataOutputStream` + `BufferedReader` + `GlobalScope`. Это создавало
 * глобальное состояние, утечки и невозможность тестирования.
 *
 * Теперь `LegacyShellBridge` — единственное место, где сохраняется
 * "session-style" API (для совместимости со старым UI-кодом KrScript).
 * Внутри он делегирует в `ShellRuntime` (Stage 6), который сам использует
 * `KeepShellRuntime` — переходную реализацию, оборачивающую старый
 * `KeepShell` engine (пока полностью не заменён).
 *
 * EN: Single bridge point between the legacy API (`ScriptEnvironmen`,
 * `KeepShellPublic`, `ShellExecutor`) and the new runtime architecture
 * (`ShellRuntime`, `RuntimeBinder`).
 *
 * Stage 22: before this stage, the legacy classes (`KeepShell`,
 * `KeepShellPublic`, `ScriptEnvironmen`) owned their own shell engine based
 * on `Process` + `DataOutputStream` + `BufferedReader` + `GlobalScope`. This
 * created global state, leaks, and untestable code.
 *
 * Now `LegacyShellBridge` is the single place that retains the session-style
 * API (for compatibility with the old KrScript UI code). Internally it
 * delegates to `ShellRuntime` (Stage 6), which itself uses
 * `KeepShellRuntime` — a transitional implementation that wraps the old
 * `KeepShell` engine (until fully replaced).
 */
object LegacyShellBridge {

    private const val ASSETS_FILE = "file:///android_asset/"
    private const val DEFAULT_EXECUTOR = "kr-script/executor.sh"
    private const val DEFAULT_TOOLKIT_DIR = "bin"

    /**
     * RU: Экземпляр [ShellTranslation] для разрешения `$({KEY})`-плейсхолдеров.
     *     Создаётся лениво и обновляется через [refreshTranslations].
     *
     * EN: [ShellTranslation] instance for resolving `$({KEY})` placeholders.
     *     Created lazily and refreshed via [refreshTranslations].
     */
    @Volatile
    private var shellTranslation: ShellTranslation? = null

    /**
     * RU: Текущий shell-runtime. Выбирается один раз при [init] на основе
     *     root-статуса.
     *
     * EN: Current shell-runtime. Picked once during [init] based on the
     *     root status.
     */
    @Volatile
    private var shellRuntime: ShellRuntime? = null

    /**
     * RU: Путь к извлечённому executor-скрипту (заполняется [init]).
     *
     * EN: Path to the extracted executor script (populated by [init]).
     */
    @Volatile
    private var environmentPath: String = ""

    /**
     * RU: Путь к извлечённому toolkit-директорию (заполняется [init]).
     *
     * EN: Path to the extracted toolkit directory (populated by [init]).
     */
    @Volatile
    private var toolkitDir: String = ""

    /**
     * RU: Root-статус, определённый при [init].
     *
     * EN: Root status determined by [init].
     */
    @Volatile
    private var rooted: Boolean = false

    /**
     * RU: Флаг инициализации.
     *
     * EN: Initialisation flag.
     */
    @Volatile
    private var inited: Boolean = false

    fun isInited(): Boolean = inited

    fun refreshTranslations(context: Context) {
        shellTranslation = ShellTranslation(context)
    }

    /**
     * RU: Инициализирует bridge. Извлекает executor + toolkit из assets,
     *     выбирает root/user shell-runtime.
     *
     * EN: Initialises the bridge. Extracts the executor + toolkit from
     *     assets, picks root/user shell-runtime.
     */
    fun init(context: Context): Boolean {
        if (inited) return true
        refreshTranslations(context)

        // RU: определяем root-статус напрямую через Runtime.exec("su").
        //     Раньше использовали KeepShellPublic.checkRoot(), но
        //     KeepShellPublic теперь делегирует сюда — это вызвало бы
        //     рекурсию.
        // EN: determine root status directly via Runtime.exec("su").
        //     Previously used KeepShellPublic.checkRoot(), but
        //     KeepShellPublic now delegates here — that would recurse.
        rooted = probeRoot()

        // RU: Stage 23 — извлекаем toolkit через ToolchainInstaller (Stage 11)
        //     вместо ExtractAssets. Манифест теперь покрывает все файлы из
        //     assets/bin/ (включая .so библиотеки). Инсталляция идемпотентна —
        //     повторные вызовы пропускают уже установленные файлы.
        // EN: Stage 23 — extract the toolkit via ToolchainInstaller (Stage 11)
        //     instead of ExtractAssets. The manifest now covers every file in
        //     assets/bin/ (including .so libraries). Installation is idempotent —
        //     repeated calls skip already-installed files.
        toolkitDir = installToolkit(context)

        // RU: извлекаем executor.sh и подставляем env-переменные.
        // EN: extract executor.sh and substitute env variables.
        try {
            val executorBytes = context.assets.open(DEFAULT_EXECUTOR).use { it.readBytes() }
            val envShell = String(executorBytes, Charsets.UTF_8).replace("\r", "")
            val env = getEnvironment(context)
            var patched = envShell
            for ((key, value) in env) {
                patched = patched.replace("\$({$key})", value)
            }
            val outputPath =
                com.omarea.common.shared.FileWrite.getPrivateFilePath(context, DEFAULT_EXECUTOR)
            com.omarea.common.shared.FileWrite.writePrivateFile(
                patched.toByteArray(Charsets.UTF_8),
                DEFAULT_EXECUTOR,
                context
            )
            environmentPath = outputPath
        } catch (_: Throwable) {
            // Best-effort — если executor не извлечён, runtime всё равно работает.
        }

        // RU: выбираем shell-runtime. RootShellRuntime использует su, UserShellRuntime — sh.
        // EN: pick the shell-runtime. RootShellRuntime uses su, UserShellRuntime uses sh.
        shellRuntime = if (rooted) RootShellRuntime(shellTranslation) else UserShellRuntime(shellTranslation)
        inited = true
        return inited
    }

    /**
     * RU: Пробует запустить `su -c id` и проверяет, что вывод содержит
     *     `uid=0`. Возвращает `false` при любой ошибке.
     *
     * EN: Tries to run `su -c id` and verifies the output contains `uid=0`.
     *     Returns `false` on any error.
     */
    private fun probeRoot(): Boolean {
        return try {
            val process = Runtime.getRuntime().exec(arrayOf("su", "-c", "id"))
            val stdout = process.inputStream.bufferedReader().readText()
            val exitCode = process.waitFor()
            exitCode == 0 && stdout.contains("uid=0")
        } catch (_: Throwable) {
            false
        }
    }

    /**
     * RU: Устанавливает toolkit из манифеста через [ToolchainInstaller].
     *
     * Загружает `assets/toolchain/manifest.json`, извлекает все инструменты
     * в `<filesDir>/bin/`, возвращает путь к директории. При ошибке
     * возвращает пустую строку.
     *
     * EN: Installs the toolkit from the manifest via [ToolchainInstaller].
     *
     * Loads `assets/toolchain/manifest.json`, extracts all tools into
     * `<filesDir>/bin/`, returns the directory path. On failure returns
     * an empty string.
     */
    private fun installToolkit(context: Context): String {
        return try {
            val manifestStream = context.assets.open("toolchain/manifest.json")
            val manifest = ToolManifestLoader().load(manifestStream)
            manifestStream.close()
            val toolsDir = File(context.filesDir, DEFAULT_TOOLKIT_DIR)
            if (!toolsDir.exists()) toolsDir.mkdirs()
            val installer = ToolchainInstaller(
                manifest = manifest,
                assetProvider = { name ->
                    try {
                        context.assets.open("$DEFAULT_TOOLKIT_DIR/$name")
                    } catch (_: Throwable) {
                        null
                    }
                }
            )
            val result = installer.install(
                toolsDir = toolsDir,
                verifyChecksums = false,
                overwrite = false
            )
            when (result) {
                is ToolchainInstallResult.Success -> result.toolsDir.absolutePath
                is ToolchainInstallResult.Failed -> {
                    android.util.Log.w("LegacyShellBridge", "Toolchain install failed: ${result.message}")
                    ""
                }
                is ToolchainInstallResult.ChecksumMismatch -> {
                    android.util.Log.w("LegacyShellBridge", "Checksum mismatch: ${result.toolName}")
                    ""
                }
            }
        } catch (e: Throwable) {
            android.util.Log.w("LegacyShellBridge", "Could not install toolkit", e)
            ""
        }
    }

    /**
     * RU: Выполняет shell-скрипт и возвращает результат как строку.
     *
     * Совместимо с legacy `ScriptEnvironmen.executeResultRoot(context, script, node)`.
     *
     * EN: Runs a shell script and returns the result as a string.
     *
     * Compatible with the legacy
     * `ScriptEnvironmen.executeResultRoot(context, script, node)`.
     */
    fun executeResultRoot(context: Context, script: String?, nodeInfoBase: NodeInfoBase?): String {
        if (!inited) init(context)
        if (script.isNullOrEmpty()) return ""

        val script2 = script.trim()
        val path = if (script2.startsWith(ASSETS_FILE)) {
            extractScript(context, script2)
        } else {
            createShellCache(context, script2)
        }

        if (path.isEmpty()) return "error"

        val env = buildEnvForNode(context, nodeInfoBase)
        val command = ShellCommand.create(
            script = ScriptSource.PreparedFile(File(path)),
            env = env,
            requiresRoot = rooted,
            tag = "krscript-executeResultRoot"
        )

        val runtime = shellRuntime ?: return "error"
        val result = runBlocking { runtime.executeForResult(command) }
        return when (result) {
            is ShellResult.Completed -> result.stdout.trim()
            is ShellResult.Failed -> "error"
            is ShellResult.Cancelled -> "error"
            is ShellResult.TimedOut -> "error"
        }
    }

    /**
     * RU: Запускает shell-скрипт синхронно и возвращает stdout как строку.
     *
     * Совместимо с legacy `KeepShellPublic.doCmdSync(cmd)`.
     *
     * EN: Runs a shell script synchronously and returns stdout as a string.
     *
     * Compatible with the legacy `KeepShellPublic.doCmdSync(cmd)`.
     */
    fun doCmdSync(cmd: String): String {
        val runtime = shellRuntime ?: return ""
        if (cmd.isEmpty()) return ""
        val command = ShellCommand.create(
            script = ScriptSource.Inline(cmd),
            requiresRoot = rooted,
            tag = "krscript-doCmdSync"
        )
        val result = runBlocking { runtime.executeForResult(command) }
        return when (result) {
            is ShellResult.Completed -> result.stdout.trim()
            else -> ""
        }
    }

    /**
     * RU: Проверяет root-доступность.
     *
     * Совместимо с legacy `KeepShellPublic.checkRoot()`.
     *
     * EN: Checks root availability.
     *
     * Compatible with the legacy `KeepShellPublic.checkRoot()`.
     */
    fun checkRoot(): Boolean {
        if (!inited) return false
        return rooted
    }

    /**
     * RU: Закрывает shell-сессию.
     *
     * Совместимо с legacy `KeepShellPublic.tryExit()`.
     *
     * EN: Closes the shell session.
     *
     * Compatible with the legacy `KeepShellPublic.tryExit()`.
     */
    fun tryExit() {
        // RU: ShellRuntime — stateless interface, session не持有. Это no-op.
        //     Реальная очистка происходит в KeepShellRuntime, когда
        //     он полностью заменён новым engine.
        // EN: ShellRuntime is a stateless interface, it does not hold a
        //     session. This is a no-op. Real cleanup happens in
        //     KeepShellRuntime when it is fully replaced by the new engine.
    }

    /**
     * RU: Возвращает environment-переменные для shell-сессии.
     *
     * Совместимо с legacy `ScriptEnvironmen.getEnvironment(context)`.
     *
     * EN: Returns environment variables for the shell session.
     *
     * Compatible with the legacy `ScriptEnvironmen.getEnvironment(context)`.
     */
    fun getEnvironment(context: Context): Map<String, String> {
        val params = LinkedHashMap<String, String>()
        params["TOOLKIT"] = toolkitDir
        params["START_DIR"] = getStartPath(context)
        params["TEMP_DIR"] = context.cacheDir.absolutePath
        val fileOwner = com.omarea.krscript.FileOwner(context)
        params["ANDROID_UID"] = fileOwner.getUserId().toString()
        try {
            params["APP_USER_ID"] = fileOwner.fileOwner
        } catch (_: Throwable) {
            // ignored
        }
        params["ANDROID_SDK"] = Build.VERSION.SDK_INT.toString()
        params["ROOT_PERMISSION"] = if (rooted) "true" else "false"
        params["SDCARD_PATH"] = Environment.getExternalStorageDirectory().absolutePath
        val busyboxPath =
            com.omarea.common.shared.FileWrite.getPrivateFilePath(context, "busybox")
        params["BUSYBOX"] = if (File(busyboxPath).exists()) busyboxPath else "busybox"
        try {
            val packageInfo = context.packageManager.getPackageInfo(context.packageName, 0)
            val versionCode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                packageInfo.longVersionCode
            } else {
                packageInfo.versionCode.toLong()
            }
            params["PACKAGE_NAME"] = context.packageName
            params["PACKAGE_VERSION_NAME"] = packageInfo.versionName ?: ""
            params["PACKAGE_VERSION_CODE"] = versionCode.toString()
        } catch (_: Throwable) {
            // ignored
        }
        return params
    }

    /**
     * RU: Возвращает объект [ShellTranslation] (или `null`, если bridge не
     *     инициализирован).
     *
     * EN: Returns the [ShellTranslation] instance (or `null` when the bridge
     *     has not been initialised).
     */
    fun translation(): ShellTranslation? = shellTranslation

    /**
     * RU: Строит full shell-команду для streaming-выполнения.
     *
     * Используется [com.omarea.krscript.executor.ShellExecutor] для записи в
     * `Process`-stdin. Команда содержит:
     *   - export env-переменных (TOOLKIT, START_DIR, PAGE_CONFIG_*, etc.);
     *   - путь к executor.sh;
     *   - путь к скрипту (asset или cached);
     *   - `exit\n`.
     *
     * @param context Android context.
     * @param script скрипт для выполнения (inline или `file:///android_asset/...`).
     * @param nodeInfoBase узел, для которого выполняется скрипт (для PAGE_CONFIG_*).
     * @param tag тег сессии (для логирования).
     * @return full shell-команда для записи в `Process.outputStream`.
     *
     * EN: Builds a full shell command for streaming execution.
     *
     * Used by [com.omarea.krscript.executor.ShellExecutor] to write into a
     * `Process`-stdin. The command contains:
     *   - env exports (TOOLKIT, START_DIR, PAGE_CONFIG_*, etc.);
     *   - the executor.sh path;
     *   - the script path (asset or cached);
     *   - `exit\n`.
     */
    fun buildStreamingCommand(
        context: Context,
        script: String,
        nodeInfoBase: NodeInfoBase?,
        tag: String? = null
    ): String {
        if (!inited) init(context)
        val env = buildEnvForNode(context, nodeInfoBase)
        val sb = StringBuilder()
        for ((key, value) in env) {
            val escaped = value.replace("'", "'\\''")
            sb.append("export ").append(key).append("='").append(escaped).append("'\n")
        }
        sb.append("\n")
        val scriptPath = resolveScriptForStreaming(context, script)
        sb.append(environmentPath).append(" \"").append(scriptPath).append("\"")
        if (!tag.isNullOrEmpty()) {
            sb.append(" \"").append(tag).append("\"")
        }
        sb.append("\n\nexit\nexit\n")
        return sb.toString()
    }

    /**
     * RU: Возвращает `true`, если bridge определён root-статус.
     *
     * EN: Returns `true` when the bridge has determined root status.
     */
    val isRooted: Boolean get() = rooted

    // ------------------------------------------------------------------
    // Internal helpers
    // ------------------------------------------------------------------

    private fun buildEnvForNode(
        context: Context,
        nodeInfoBase: NodeInfoBase?
    ): Map<String, String> {
        val env = LinkedHashMap<String, String>()
        env.putAll(getEnvironment(context))
        env[com.omarea.common.shared.AppLanguage.ENV_LC_CTYPE] =
            com.omarea.common.shared.AppLanguage.SHELL_UTF8_LOCALE
        env[com.omarea.common.shared.AppLanguage.ENV_APP_LANGUAGE] =
            com.omarea.common.shared.AppLanguage.get(context)
        if (nodeInfoBase != null && nodeInfoBase.currentPageConfigPath.isNotEmpty()) {
            val parentDir = nodeInfoBase.pageConfigDir
            val currentPath = nodeInfoBase.currentPageConfigPath
            env["PAGE_CONFIG_DIR"] = parentDir
            env["PAGE_CONFIG_FILE"] = currentPath
            if (currentPath.startsWith("file:///android_asset/")) {
                env["PAGE_WORK_DIR"] = ExtractAssets(context).getExtractPath(parentDir)
                env["PAGE_WORK_FILE"] = ExtractAssets(context).getExtractPath(currentPath)
            } else {
                env["PAGE_WORK_DIR"] = parentDir
                env["PAGE_WORK_FILE"] = currentPath
            }
        } else {
            env["PAGE_CONFIG_DIR"] = ""
            env["PAGE_CONFIG_FILE"] = ""
            env["PAGE_WORK_DIR"] = ""
            env["PAGE_WORK_FILE"] = ""
        }
        env["EXECUTOR_PATH"] = environmentPath
        return env
    }

    private fun getStartPath(context: Context): String {
        val dir = com.omarea.common.shared.FileWrite.getPrivateFileDir(context)
        return if (dir.endsWith("/")) dir.substring(0, dir.length - 1) else dir
    }

    private fun extractScript(context: Context, fileName: String): String {
        val name = if (fileName.startsWith(ASSETS_FILE)) {
            fileName.substring(ASSETS_FILE.length)
        } else {
            fileName
        }
        return com.omarea.common.shared.FileWrite.writePrivateShellFile(name, name, context) ?: ""
    }

    /**
     * RU: Разрешает путь к скрипту для streaming-выполнения.
     *
     * Если скрипт начинается с `file:///android_asset/`, извлекает его из assets.
     * Иначе создаёт кэш-файл с содержимым скрипта.
     *
     * EN: Resolves the script path for streaming execution.
     *
     * If the script starts with `file:///android_asset/`, extracts it from
     * assets. Otherwise, creates a cache file with the script content.
     */
    private fun resolveScriptForStreaming(context: Context, script: String): String {
        val script2 = script.trim()
        return if (script2.startsWith(ASSETS_FILE)) {
            extractScript(context, script2)
        } else {
            createShellCache(context, script2)
        }
    }

    private fun createShellCache(context: Context, script: String): String {
        val outputPath = "kr-script/cache/" + md5(script) + ".sh"
        val file = File(com.omarea.common.shared.FileWrite.getPrivateFilePath(context, outputPath))
        if (file.exists()) return file.absolutePath
        val bytes = ((if (script.startsWith("#!/")) "#!/system/bin/sh\n\n" else "") + script)
            .replace("\r\n", "\n")
            .replace("\r\t", "\t")
            .replace("\r", "\n")
            .toByteArray(Charsets.UTF_8)
        return if (com.omarea.common.shared.FileWrite.writePrivateFile(bytes, outputPath, context)) {
            com.omarea.common.shared.FileWrite.getPrivateFilePath(context, outputPath)
        } else {
            ""
        }
    }

    private fun md5(string: String): String {
        if (string.isEmpty()) return ""
        return try {
            val md5 = java.security.MessageDigest.getInstance("MD5")
            val bytes = md5.digest(string.toByteArray())
            bytes.joinToString("") { "%02x".format(it) }
        } catch (_: Throwable) {
            ""
        }
    }
}
