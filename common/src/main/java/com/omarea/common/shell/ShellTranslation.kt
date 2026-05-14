package com.omarea.common.shell

import android.content.Context
import java.util.Locale

class ShellTranslation(val context: Context) {
    private val regex1 = Regex("^@(string|dimen):[_a-z]+.*", RegexOption.IGNORE_CASE)
    private val regex2 = Regex("^@(string|dimen)/[_a-z]+.*", RegexOption.IGNORE_CASE)

    fun resolveRow(originRow: String): String {
        if (originRow.contains("\n")) {
            return originRow.split("\n").joinToString("\n") { resolveRow(it) }
        }

        val row = originRow.trim()
        val reference = row.substringBefore("|")
        val separator = if (regex1.matches(reference)) {
            ':'
        } else if (regex2.matches(reference)) {
            '/'
        } else {
            null
        }
        if (separator != null) {
            val resources = context.resources
            val type = reference.substring(1, reference.indexOf(separator)).toLowerCase(Locale.ENGLISH)
            val name = reference.substring(reference.indexOf(separator) + 1)
            val args = row.split("|").drop(1).toTypedArray()

            try {
                val id = resources.getIdentifier(name, type, context.packageName)
                when (type) {
                    "string" -> {
                        return if (args.isEmpty()) resources.getString(id) else resources.getString(id, *args)
                    }
                    "dimen" -> {
                        return resources.getDimension(id).toString()
                    }
                }
            } catch (ex: Exception) {
                if (row.contains("[(") && row.contains(")]")) {
                    return row.substring(row.indexOf("[(") + 2, row.indexOf(")]"))
                }
            }
        }

        return originRow
    }
}
