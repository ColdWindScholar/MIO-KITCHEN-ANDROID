package com.omarea.krscript.config

import android.content.Context
import com.omarea.krscript.parser.PageConfigSource
import java.io.InputStream

/**
 * RU: Адаптер, превращающий старый [PathAnalysis] в [PageConfigSource].
 *
 * Используется новым `PageConfigRepository` для совместимости со старым кодом,
 * который уже умеет открывать assets/файлы через `PathAnalysis`.
 *
 * EN: Adapter that exposes the legacy [PathAnalysis] as a [PageConfigSource].
 *
 * Used by the new `PageConfigRepository` for compatibility with existing code
 * that already knows how to open assets/files via `PathAnalysis`.
 */
class AndroidPageConfigSource private constructor(
    private val stream: InputStream,
    override val absolutePath: String,
    override val parentDir: String
) : PageConfigSource {
    override fun openStream(): InputStream = stream

    companion object {
        /**
         * RU: Открывает конфигурацию по имени/path так же, как это делал
         * старый `PageConfigReader(Context, pageConfig, parentDir)`.
         *
         * @return `null`, если файл не найден.
         *
         * EN: Opens a configuration by name/path exactly like the legacy
         * `PageConfigReader(Context, pageConfig, parentDir)` did.
         *
         * @return `null` when the file cannot be located.
         */
        fun open(context: Context, pageConfig: String, parentDir: String?): AndroidPageConfigSource? {
            val analysis = PathAnalysis(context, parentDir ?: "")
            val stream = analysis.parsePath(pageConfig) ?: return null
            return AndroidPageConfigSource(
                stream = stream,
                absolutePath = analysis.getCurrentAbsPath(),
                parentDir = parentDir ?: ""
            )
        }

        /**
         * RU: Создаёт источник из уже готового потока (например, из кэша).
         * EN: Creates a source from an already-open stream (for example, cache).
         */
        fun fromStream(stream: InputStream, absolutePath: String = ""): AndroidPageConfigSource =
            AndroidPageConfigSource(stream, absolutePath, "")
    }
}
