package com.omarea.common.storage

/**
 * RU: Санитизирует имена файлов, полученные из URI, ZIP/OTA-пакетов или внешних
 * провайдеров. Имя должно быть безопасным для app workspace и shell-передачи.
 *
 * EN: Sanitizes file names received from URIs, ZIP/OTA packages, or external
 * providers. The result is safe for the app workspace and shell hand-off.
 */
object SafeFileName {
    private val unsafeChars = Regex("[^A-Za-z0-9._-]")

    fun clean(input: String?, defaultName: String = "selected-file.bin"): String {
        val candidate = input
            ?.substringAfterLast('/')
            ?.substringAfterLast('\\')
            ?.trim()
            ?.takeIf { it.isNotEmpty() }
            ?: defaultName

        val sanitized = candidate
            .replace(unsafeChars, "_")
            .trim('_', '.', ' ')
            .takeIf { it.isNotEmpty() }
            ?: defaultName

        if (sanitized.length <= 180) {
            return sanitized
        }

        val dotIndex = sanitized.lastIndexOf('.')
        val extension = if (dotIndex in 1 until sanitized.length - 1) sanitized.substring(dotIndex) else ""
        val baseLimit = 180 - extension.length
        val base = sanitized.substring(0, baseLimit.coerceAtLeast(1)).trim('_', '.', ' ')
        return (base.ifEmpty { "file" } + extension).take(180)
    }
}
