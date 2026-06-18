package com.omarea.krscript.parser

import com.omarea.krscript.model.NodeInfoBase
import com.omarea.krscript.validator.PageConfigValidator
import com.omarea.krscript.validator.ValidationReport

/**
 * RU: Оркестратор: источник XML → парсер → валидатор → готовая модель.
 *
 * Это высокоуровневая точка входа для использования парсера в production-коде.
 * Она НЕ знает про Android `Context`, shell или UI — всё это скрывается за
 * абстракциями [PageConfigSource] и [DynamicValueResolver].
 *
 * EN: Orchestrator: XML source → parser → validator → ready model.
 *
 * High-level entry point for using the parser in production. It does NOT know
 * about Android `Context`, shell or UI — those are hidden behind [PageConfigSource]
 * and [DynamicValueResolver].
 */
class PageConfigRepository(
    private val parser: PageConfigParser = PageConfigParser(),
    private val validator: PageConfigValidator = PageConfigValidator()
) {
    /**
     * RU: Загружает, разбирает и проверяет конфигурацию страницы.
     *
     * @return [ParsedPageConfig] с моделью и отчётом валидации даже при ошибках.
     *
     * EN: Loads, parses and validates a page configuration.
     *
     * @return [ParsedPageConfig] containing the model and validation report even on errors.
     */
    fun load(source: PageConfigSource): ParsedPageConfig {
        val nodes: List<NodeInfoBase> = try {
            source.openStream().use { stream ->
                parser.parse(stream, source.absolutePath)
            }
        } catch (e: Exception) {
            return ParsedPageConfig(
                nodes = emptyList(),
                report = ValidationReport(
                    errors = listOf(
                        com.omarea.krscript.validator.ValidationIssue(
                            level = com.omarea.krscript.validator.ValidationIssue.Level.ERROR,
                            code = "parse-failed",
                            message = "Failed to parse page config: ${e.message ?: e.javaClass.simpleName}"
                        )
                    )
                ),
                source = source
            )
        }
        val report = validator.validate(nodes)
        return ParsedPageConfig(nodes, report, source)
    }
}

/**
 * RU: Результат загрузки конфигурации страницы.
 * EN: Result of loading a page configuration.
 */
data class ParsedPageConfig(
    val nodes: List<NodeInfoBase>,
    val report: ValidationReport,
    val source: PageConfigSource
) {
    val isValid: Boolean get() = report.isValid && nodes.isNotEmpty()
}
