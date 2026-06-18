package com.omarea.krscript.parser

import java.io.InputStream

/**
 * RU: Источник конфигурации страницы KrScript.
 *
 * Абстракция над тем, откуда приходит XML: assets, файловая система, кэш или тестовый
 * ресурс. Реализация не должна выполнять shell-команды или показывать UI.
 *
 * EN: Source of a KrScript page configuration.
 *
 * Abstracts where the XML comes from (assets, filesystem, cache, test fixture).
 * Implementations MUST NOT execute shell commands or interact with the UI.
 */
interface PageConfigSource {
    /**
     * RU: Открывает поток XML для чтения.
     * EN: Opens the XML stream for reading.
     */
    fun openStream(): InputStream

    /**
     * RU: Возвращает абсолютный путь к конфигурации (используется только как метаданные).
     * EN: Returns the absolute config path (used as metadata only).
     */
    val absolutePath: String

    /**
     * RU: Возвращает родительскую директорию конфигурации или пустую строку.
     * EN: Returns the parent directory of the config or an empty string.
     */
    val parentDir: String
}
