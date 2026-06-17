package com.omarea.common.firmware

/**
 * RU: Анализирует выбранный файл прошивки и возвращает профиль возможностей.
 *
 * Контракт:
 *   - НЕ запускает shell и НЕ показывает UI;
 *   - НЕ изменяет файловую систему;
 *   - читает только заголовки/сигнатуры файлов;
 *   - безопасен для вызова из любого потока.
 *
 * EN: Analyzes a selected firmware source and returns a capability profile.
 *
 * Contract:
 *   - does NOT run shell and does NOT show UI;
 *   - does NOT modify the filesystem;
 *   - reads only file headers/signatures;
 *   - safe to call from any thread.
 */
interface FirmwareAnalyzer {
    /**
     * RU: Возвращает true, если анализатор умеет работать с источником.
     *
     * Проверка должна быть дешёвой — обычно по имени файла или магическим байтам.
     *
     * EN: Returns true when this analyzer can handle the source.
     *
     * The check should be cheap — typically based on filename or magic bytes.
     */
    fun supports(source: FirmwareSource): Boolean

    /**
     * RU: Выполняет анализ источника и возвращает профиль.
     *
     * Бросает [FirmwareAnalysisException], если анализ не удался.
     *
     * EN: Analyzes the source and returns the profile.
     *
     * Throws [FirmwareAnalysisException] on failure.
     */
    fun analyze(source: FirmwareSource): FirmwareProfile
}

/**
 * RU: Ошибка анализа прошивки.
 * EN: Firmware analysis error.
 */
class FirmwareAnalysisException(
    message: String,
    cause: Throwable? = null
) : RuntimeException(message, cause)

/**
 * RU: Композитный анализатор: перебирает зарегистрированные анализаторы и
 *     использует первый подходящий.
 *
 * EN: Composite analyzer: iterates registered analyzers and uses the first one
 *     that supports the source.
 */
class CompositeFirmwareAnalyzer(
    private val analyzers: List<FirmwareAnalyzer>
) : FirmwareAnalyzer {
    override fun supports(source: FirmwareSource): Boolean =
        analyzers.any { it.supports(source) }

    override fun analyze(source: FirmwareSource): FirmwareProfile {
        for (analyzer in analyzers) {
            if (analyzer.supports(source)) {
                return analyzer.analyze(source)
            }
        }
        throw FirmwareAnalysisException(
            "No analyzer supports source: $source"
        )
    }
}
