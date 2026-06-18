package com.omarea.common.shared

import android.content.Context

/**
 * RU: Низкоуровневый контракт хранения выбранного языка приложения.
 *
 * Этот объект не хранит переводы и не решает, какие языки поддерживает UI. Он
 * содержит только стабильные ключи SharedPreferences, язык по умолчанию и имена
 * переменных окружения, которые KrScript передаёт shell-скриптам.
 *
 * EN: Low-level contract for storing the selected application language.
 *
 * This object does not store translations and does not decide which languages are
 * exposed by the UI. It only owns stable SharedPreferences keys, the default
 * language, and environment-variable names passed from KrScript to shell scripts.
 */
object AppLanguage {
    /** RU: Имя SharedPreferences с пользовательским выбором языка.
     *  EN: SharedPreferences file that stores the user-selected language. */
    const val PREFS_NAME: String = "app_language"

    /** RU: Ключ значения языка внутри [PREFS_NAME].
     *  EN: Language value key inside [PREFS_NAME]. */
    const val PREFS_KEY_LANGUAGE: String = "language"

    /** RU: Английский язык по умолчанию до явного выбора пользователя.
     *  EN: English is the default language until the user explicitly changes it. */
    const val DEFAULT_LANGUAGE: String = "en"

    /** RU: Имя переменной окружения, передаваемой shell-скриптам.
     *  EN: Environment-variable name passed to shell scripts. */
    const val ENV_APP_LANGUAGE: String = "APP_LANGUAGE"

    /** RU: Имя переменной окружения для UTF-8 locale в shell.
     *  EN: Environment-variable name used for shell UTF-8 locale. */
    const val ENV_LC_CTYPE: String = "LC_CTYPE"

    /** RU: Значение locale, которое заставляет shell работать с UTF-8 строками.
     *  EN: Locale value that keeps shell string handling in UTF-8 mode. */
    const val SHELL_UTF8_LOCALE: String = "en_US.UTF-8"

    /**
     * RU: Возвращает сохранённый код языка или [DEFAULT_LANGUAGE].
     * Проверка поддержки конкретного языка выполняется в UI-слое LanguageConfig
     * приложения, а не в общем модуле.
     *
     * EN: Returns the stored language code or [DEFAULT_LANGUAGE]. Supported-language
     * validation is handled by the app UI layer, not by the shared common module.
     */
    @JvmStatic
    fun get(context: Context?): String {
        if (context == null) {
            return DEFAULT_LANGUAGE
        }

        val savedLanguage = context.applicationContext
            .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getString(PREFS_KEY_LANGUAGE, DEFAULT_LANGUAGE)

        return savedLanguage?.trim()?.takeIf { it.isNotEmpty() } ?: DEFAULT_LANGUAGE
    }

    /**
     * RU: Сохраняет выбранный код языка. Вызывать после проверки в UI-слое.
     *
     * EN: Stores the selected language code. Call after app/UI-level validation.
     */
    @JvmStatic
    fun set(context: Context, language: String) {
        val safeLanguage = language.trim().takeIf { it.isNotEmpty() } ?: DEFAULT_LANGUAGE
        context.applicationContext
            .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(PREFS_KEY_LANGUAGE, safeLanguage)
            .apply()
    }
}
