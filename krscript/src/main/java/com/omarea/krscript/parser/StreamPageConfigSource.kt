package com.omarea.krscript.parser

import java.io.InputStream

/**
 * RU: Простейшая реализация источника конфигурации на основе готового потока.
 *
 * Используется в тестах и в тех случаях, когда XML уже получен (например, из кэша).
 *
 * EN: Trivial implementation of [PageConfigSource] backed by an [InputStream].
 *
 * Useful for tests or whenever the XML is already available (e.g. from cache).
 */
class StreamPageConfigSource(
    private val stream: InputStream,
    override val absolutePath: String = "",
    override val parentDir: String = ""
) : PageConfigSource {
    override fun openStream(): InputStream = stream
}
