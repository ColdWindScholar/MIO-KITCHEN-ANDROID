package com.omarea.common.shell.runtime

import java.io.File

/**
 * RU: Источник скрипта для [ShellCommand].
 *
 * Может быть либо инлайн-скриптом (текстом), либо путём к shell-файлу в assets
 * или в файловой системе приложения. Различение нужно, чтобы shell runtime
 * правильно обработал копирование/extract перед запуском.
 *
 * EN: Source of a script for [ShellCommand].
 *
 * Either an inline script (raw text) or a path to a shell file in assets or in
 * the app filesystem. The runtime needs to know which one it is to handle
 * extraction/copying correctly before execution.
 */
sealed class ScriptSource {
    /**
     * RU: Готовый shell-скрипт в виде строки.
     * EN: Inline shell script as a string.
     */
    data class Inline(val script: String) : ScriptSource() {
        init {
            require(script.isNotEmpty()) { "Inline script must not be empty" }
        }
    }

    /**
     * RU: Путь к shell-файлу.
     *
     * @param path путь внутри assets (например, `script/tool.sh`) или абсолютный
     *   путь в файловой системе.
     * @param inAssets `true`, если [path] — путь внутри assets.
     *
     * EN: Path to a shell file.
     *
     * @param path path inside assets (e.g. `script/tool.sh`) or absolute path
     *   in the filesystem.
     * @param inAssets `true` when [path] is an asset path.
     */
    data class FilePath(val path: String, val inAssets: Boolean = false) : ScriptSource() {
        init {
            require(path.isNotEmpty()) { "FilePath must not be empty" }
        }
    }

    /**
     * RU: Уже подготовленный [File] в файловой системе (например, в кэше).
     * EN: Already-prepared [File] in the filesystem (e.g. in the cache).
     */
    data class PreparedFile(val file: File) : ScriptSource()
}
