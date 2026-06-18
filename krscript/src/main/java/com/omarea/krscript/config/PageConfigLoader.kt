package com.omarea.krscript.config

import android.content.Context
import com.omarea.krscript.model.NodeInfoBase
import com.omarea.krscript.parser.PageConfigParser
import com.omarea.krscript.parser.PageConfigRepository
import com.omarea.krscript.runtime.RuntimeBinder
import java.io.InputStream

/**
 * RU: Утилита для загрузки page-config с использованием нового
 *     [PageConfigRepository] + [RuntimeBinder] (Stage 5+22).
 *
 * Заменяет старый `PageConfigReader(Context, pageConfig, parentDir).readConfigXml()`.
 *
 * EN: Utility for loading a page config using the new
 *     [PageConfigRepository] + [RuntimeBinder] (Stage 5+22).
 *
 * Replaces the legacy `PageConfigReader(Context, pageConfig, parentDir).readConfigXml()`.
 */
object PageConfigLoader {

    /**
     * RU: Загружает конфигурацию по пути/имени. Возвращает `null`, если файл
     *     не найден или парсинг провалился.
     *
     * Совместимо с legacy `PageConfigReader(Context, pageConfig, parentDir).readConfigXml()`.
     *
     * EN: Loads a configuration by path/name. Returns `null` when the file
     *     is not found or parsing fails.
     *
     * Compatible with the legacy
     * `PageConfigReader(Context, pageConfig, parentDir).readConfigXml()`.
     */
    @JvmStatic
    fun load(
        context: Context,
        pageConfig: String,
        parentDir: String?
    ): ArrayList<NodeInfoBase>? {
        val source = AndroidPageConfigSource.open(context, pageConfig, parentDir)
            ?: return ArrayList()
        val binder = RuntimeBinder(context)
        val repository = PageConfigRepository(
            parser = PageConfigParser(binder)
        )
        val parsed = repository.load(source)
        return if (parsed.nodes.isNotEmpty()) ArrayList(parsed.nodes) else ArrayList()
    }

    /**
     * RU: Загружает конфигурацию из уже готового потока.
     *
     * Совместимо с legacy `PageConfigReader(Context, stream).readConfigXml()`.
     *
     * EN: Loads a configuration from an already-open stream.
     *
     * Compatible with the legacy `PageConfigReader(Context, stream).readConfigXml()`.
     */
    @JvmStatic
    fun loadFromStream(
        context: Context,
        stream: InputStream,
        absolutePath: String = ""
    ): ArrayList<NodeInfoBase>? {
        val source = AndroidPageConfigSource.fromStream(stream, absolutePath)
        val binder = RuntimeBinder(context)
        val repository = PageConfigRepository(
            parser = PageConfigParser(binder)
        )
        val parsed = repository.load(source)
        return if (parsed.nodes.isNotEmpty()) ArrayList(parsed.nodes) else ArrayList()
    }
}
