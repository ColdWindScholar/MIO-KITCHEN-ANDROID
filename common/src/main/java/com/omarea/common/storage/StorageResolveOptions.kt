package com.omarea.common.storage

/**
 * RU: Опции преобразования внешнего файла/URI в shell-доступный путь.
 *
 * EN: Options for converting external file/URI input into a shell-accessible path.
 */
data class StorageResolveOptions(
    /** RU: Пытаться ли сначала использовать старый прямой путь из provider `_data`.
     *  EN: Whether to try the legacy provider `_data` direct path first. */
    val preferLegacyDirectPath: Boolean = false,

    /** RU: Копировать `content://` в workspace, если прямой путь недоступен или выключен.
     *  EN: Copy `content://` input into workspace when direct path is unavailable or disabled. */
    val copyContentUriToWorkspace: Boolean = true,

    /** RU: Считать SHA-256 во время копирования без второго прохода по файлу.
     *  EN: Compute SHA-256 during copy without reading the file a second time. */
    val computeSha256: Boolean = true
)
