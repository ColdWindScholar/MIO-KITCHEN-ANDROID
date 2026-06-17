package com.mio.kitchen

import android.content.Context
import android.content.res.Configuration
import android.os.Build
import com.omarea.common.shared.AppLanguage
import java.util.Locale

/**
 * RU: Android/UI-слой локализации приложения.
 *
 * Переводы остаются в отдельных Android resource-файлах `values*/strings.xml`.
 * Этот объект хранит только список языков, показанных пользователю, связывает
 * код языка с [Locale] и создаёт локализованный [Context]. Низкоуровневые ключи
 * SharedPreferences и shell-переменные находятся в [AppLanguage].
 *
 * EN: Android/UI localization layer.
 *
 * Translations stay in separate Android `values*/strings.xml` resource files.
 * This object only owns the language list shown to the user, maps language codes
 * to [Locale], and creates a localized [Context]. Low-level SharedPreferences
 * keys and shell environment variables are owned by [AppLanguage].
 */
object LanguageConfig {
    data class LanguageOption(
        val code: String,
        val labelRes: Int,
        val locale: Locale
    )

    val supportedLanguages: List<LanguageOption> = listOf(
        LanguageOption("en", R.string.language_english, Locale.ENGLISH),
        LanguageOption("ru", R.string.language_russian, Locale("ru")),
        LanguageOption("zh", R.string.language_chinese, Locale.SIMPLIFIED_CHINESE),
        LanguageOption("ja", R.string.language_japanese, Locale.JAPANESE)
    )

    private fun normalizeLanguage(code: String?): String {
        val normalized = code
            ?.trim()
            ?.replace('_', '-')
            ?.toLowerCase(Locale.ROOT)
            ?.substringBefore('-')
            ?.takeIf { it.isNotEmpty() }
            ?: AppLanguage.DEFAULT_LANGUAGE

        return supportedLanguages.firstOrNull { it.code == normalized }?.code
            ?: AppLanguage.DEFAULT_LANGUAGE
    }

    fun getLanguage(context: Context): String {
        return normalizeLanguage(AppLanguage.get(context))
    }

    fun setLanguage(context: Context, code: String): Boolean {
        val normalized = normalizeLanguage(code)
        if (getLanguage(context) == normalized) {
            return false
        }

        AppLanguage.set(context, normalized)
        return true
    }

    fun getSelectedIndex(context: Context): Int {
        val language = getLanguage(context)
        return supportedLanguages.indexOfFirst { it.code == language }.coerceAtLeast(0)
    }

    fun wrap(context: Context): Context {
        val language = getLanguage(context)
        val locale = supportedLanguages.firstOrNull { it.code == language }?.locale ?: Locale.ENGLISH
        Locale.setDefault(locale)

        val config = Configuration(context.resources.configuration)
        config.setLocale(locale)
        config.setLayoutDirection(locale)

        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            context.createConfigurationContext(config)
        } else {
            @Suppress("DEPRECATION")
            context.resources.updateConfiguration(config, context.resources.displayMetrics)
            context
        }
    }
}
