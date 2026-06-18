package com.omarea.common.storage

/**
 * RU: Результат подготовки файла для shell/firmware-инструментов.
 *
 * EN: Result of preparing a file for shell/firmware tools.
 */
sealed class StorageResolveResult {
    data class Resolved(
        val shellPath: String,
        val sourceKind: StorageSourceKind,
        val displayName: String?,
        val originalUri: String?,
        val copiedBytes: Long = 0L,
        val sha256: String? = null
    ) : StorageResolveResult()

    data class Failed(
        val message: String,
        val cause: Throwable? = null
    ) : StorageResolveResult()
}
