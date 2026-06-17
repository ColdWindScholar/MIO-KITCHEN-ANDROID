package com.omarea.common.storage

import android.net.Uri

/**
 * RU: Единая точка входа для подготовки пользовательских файлов к firmware-операциям.
 *
 * UI получает URI/путь, а KrScript/shell требуют обычный file path. Реализации
 * этого интерфейса решают, можно ли использовать прямой путь или нужно создать
 * workspace-копию.
 *
 * EN: Single entry point for preparing user-selected files for firmware operations.
 *
 * UI receives URI/path input while KrScript/shell need regular file paths.
 * Implementations decide whether a direct path is safe or a workspace copy is required.
 */
interface StorageGateway {
    fun resolveUriForShell(uri: Uri, options: StorageResolveOptions = StorageResolveOptions()): StorageResolveResult

    fun resolvePathForShell(path: String): StorageResolveResult
}
