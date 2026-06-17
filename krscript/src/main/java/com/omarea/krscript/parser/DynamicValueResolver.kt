package com.omarea.krscript.parser

/**
 * RU: Стратегия разрешения динамических значений во время разбора XML.
 *
 * Парсер НЕ выполняет shell напрямую — он вызывает эту стратегию, когда встречает
 * атрибуты `desc-sh`, `summary-sh`, `support`/`visible` или элементы `<getstate>`.
 * Это позволяет:
 *   - в production использовать реальную shell-реализацию;
 *   - в тестах подставлять заглушку с предсказуемыми ответами;
 *   - в dry-run возвращать значения без выполнения shell.
 *
 * EN: Strategy for resolving dynamic values while parsing XML.
 *
 * The parser does NOT execute shell directly. Instead it calls this strategy when
 * it encounters `desc-sh`, `summary-sh`, `support`/`visible` attributes or
 * `<getstate>` elements. This lets us:
 *   - use the real shell implementation in production;
 *   - substitute a stub with deterministic answers in tests;
 *   - return values without running shell in dry-run mode.
 */
interface DynamicValueResolver {
    /**
     * RU: Возвращает текстовый результат shell-команды или пустую строку при ошибке.
     * EN: Returns the textual shell result or an empty string on error.
     */
    fun resolveShellValue(shellScript: String): String

    /**
     * RU: Возвращает `true`, если shell-команда в `supportScript` вернула "1".
     *
     * Используется для атрибутов `support`/`visible`.
     *
     * EN: Returns `true` when the shell command in [supportScript] returns "1".
     *
     * Used for `support`/`visible` attributes.
     */
    fun isSupported(supportScript: String): Boolean

    /**
     * RU: Разрешает переводимые строки (например, `$({KEY})` или простые тексты).
     *
     * Это не shell-вызов, а обращение к таблице переводов.
     *
     * EN: Resolves translatable strings (for example `$({KEY})` or plain text).
     *
     * This is not a shell call, it looks up the translation table.
     */
    fun resolveText(value: String): String
}

/**
 * RU: Реализация по умолчанию, которая ничего не выполняет и возвращает значения как есть.
 *
 * Используется в тестах и как safe default, когда shell недоступен.
 *
 * EN: Default implementation that does nothing and returns values as-is.
 *
 * Used in tests and as a safe default when shell is unavailable.
 */
class NoopDynamicValueResolver : DynamicValueResolver {
    override fun resolveShellValue(shellScript: String): String = ""
    override fun isSupported(supportScript: String): Boolean = true
    override fun resolveText(value: String): String = value
}
