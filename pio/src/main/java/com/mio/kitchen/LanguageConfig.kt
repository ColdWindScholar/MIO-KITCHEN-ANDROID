package com.mio.kitchen

import android.content.Context
import android.content.res.Configuration
import android.os.Build
import java.util.Locale

object LanguageConfig {
    private const val PREFS_NAME = "app_language"
    private const val KEY_LANGUAGE = "language"
    private const val DEFAULT_LANGUAGE = "en"

    data class LanguageOption(
        val code: String,
        val labelRes: Int,
        val locale: Locale
    )

    val supportedLanguages = listOf(
        LanguageOption("en", R.string.language_english, Locale.ENGLISH),
        LanguageOption("ru", R.string.language_russian, Locale("ru")),
        LanguageOption("zh", R.string.language_chinese, Locale.SIMPLIFIED_CHINESE),
        LanguageOption("ja", R.string.language_japanese, Locale.JAPANESE)
    )

    fun getLanguage(context: Context): String {
        val saved = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getString(KEY_LANGUAGE, DEFAULT_LANGUAGE) ?: DEFAULT_LANGUAGE
        return supportedLanguages.firstOrNull { it.code == saved }?.code ?: DEFAULT_LANGUAGE
    }

    fun setLanguage(context: Context, code: String): Boolean {
        if (getLanguage(context) == code || supportedLanguages.none { it.code == code }) {
            return false
        }
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_LANGUAGE, code)
            .apply()
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
