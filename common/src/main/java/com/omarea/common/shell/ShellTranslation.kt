package com.omarea.common.shell

import android.content.Context
import android.content.res.Resources
import java.util.Locale

/**
 * RU: Резолвер ссылок на Android-ресурсы в строках shell/XML DSL.
 *
 * Скрипты и XML-конфиги могут писать строки в виде `@string/name` или
 * `@string:name`. Этот класс переводит такие ссылки в локализованный текст из
 * текущих Android resources. Runtime fallback-тексты намеренно не поддерживаются:
 * английский является дефолтной локалью, а отсутствующие ключи должны ловиться
 * `tools/validate-localization.py`.
 *
 * EN: Android resource resolver for shell/XML DSL output.
 *
 * Scripts and XML configs can emit strings as `@string/name` or `@string:name`.
 * This class converts them into localized Android resource text. Runtime fallback
 * text is intentionally unsupported: English is the default locale and missing
 * keys must be caught by `tools/validate-localization.py`.
 */
class ShellTranslation(context: Context) {
    private data class ResourceKey(
        val type: String,
        val name: String
    )

    private val resources: Resources = context.resources
    private val packageName: String = context.packageName
    private val resourceIdCache = HashMap<ResourceKey, Int>()

    /**
     * RU: Резолвит одну строку или многострочный текст. Если строка не является
     * ссылкой на ресурс, она возвращается без изменений. Если ссылка указывает
     * на отсутствующий ресурс, она также возвращается как есть: это видимый
     * сигнал ошибки ресурсов, а не скрытая подмена текста.
     *
     * EN: Resolves a single line or multi-line text. Non-resource lines are
     * returned unchanged. Missing resource references are also returned unchanged
     * to keep resource errors visible instead of silently replacing text.
     */
    fun resolveRow(originRow: String): String {
        if (originRow.contains("\n")) {
            return originRow.split("\n").joinToString("\n") { resolveRow(it) }
        }

        val trimmedRow = originRow.trim()
        val reference = trimmedRow.substringBefore("|")
        val separator = getReferenceSeparator(reference) ?: return originRow
        val type = reference.substring(1, reference.indexOf(separator)).lowercase(Locale.ENGLISH)
        val name = reference.substring(reference.indexOf(separator) + 1)

        if (!isValidResourceName(name)) {
            return originRow
        }

        val args = if (trimmedRow.contains("|")) {
            trimmedRow.split("|").drop(1).toTypedArray()
        } else {
            emptyArray()
        }

        val resourceId = getResourceId(type, name)
        if (resourceId == 0) {
            return originRow
        }

        return try {
            when (type) {
                "string" -> if (args.isEmpty()) {
                    resources.getString(resourceId)
                } else {
                    resources.getString(resourceId, *args)
                }
                "dimen" -> resources.getDimension(resourceId).toString()
                else -> originRow
            }
        } catch (ignored: Exception) {
            originRow
        }
    }

    private fun getReferenceSeparator(reference: String): Char? {
        if (reference.startsWith("@string:", ignoreCase = true) ||
            reference.startsWith("@dimen:", ignoreCase = true)
        ) {
            return ':'
        }

        if (reference.startsWith("@string/", ignoreCase = true) ||
            reference.startsWith("@dimen/", ignoreCase = true)
        ) {
            return '/'
        }

        return null
    }

    private fun isValidResourceName(name: String): Boolean {
        if (name.isEmpty()) {
            return false
        }

        val first = name[0]
        if (first != '_' && !first.isLetter()) {
            return false
        }

        return name.all { it == '_' || it.isLetterOrDigit() }
    }

    private fun getResourceId(type: String, name: String): Int {
        val key = ResourceKey(type, name)
        return resourceIdCache.getOrPut(key) {
            resources.getIdentifier(name, type, packageName)
        }
    }
}
