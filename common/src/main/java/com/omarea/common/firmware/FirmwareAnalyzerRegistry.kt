package com.omarea.common.firmware

/**
 * RU: Реестр известных анализаторов прошивок.
 *
 * Содержит композицию всех встроенных анализаторов в правильном порядке:
 *   - `ZipFirmwareAnalyzer` (пробует первым, т.к. zip может содержать другие образы);
 *   - `BootImageAnalyzer`;
 *   - `SuperImageAnalyzer`;
 *   - `VbmetaAnalyzer`;
 *   - `FilesystemImageAnalyzer`.
 *
 * EN: Registry of known firmware analyzers.
 *
 * Composes all built-in analyzers in the correct order:
 *   - `ZipFirmwareAnalyzer` (tried first because a zip may contain other images);
 *   - `BootImageAnalyzer`;
 *   - `SuperImageAnalyzer`;
 *   - `VbmetaAnalyzer`;
 *   - `FilesystemImageAnalyzer`.
 */
object FirmwareAnalyzerRegistry {
    /**
     * RU: Создаёт [CompositeFirmwareAnalyzer] со всеми встроенными анализаторами.
     * EN: Creates a [CompositeFirmwareAnalyzer] with all built-in analyzers.
     */
    fun createDefault(): CompositeFirmwareAnalyzer = CompositeFirmwareAnalyzer(
        listOf(
            ZipFirmwareAnalyzer(),
            BootImageAnalyzer(),
            SuperImageAnalyzer(),
            VbmetaAnalyzer(),
            FilesystemImageAnalyzer()
        )
    )

    /**
     * RU: Удобный метод — анализирует источник с помощью реестра по умолчанию.
     * EN: Convenience: analyzes a source using the default registry.
     */
    fun analyze(source: FirmwareSource): FirmwareProfile = createDefault().analyze(source)
}
