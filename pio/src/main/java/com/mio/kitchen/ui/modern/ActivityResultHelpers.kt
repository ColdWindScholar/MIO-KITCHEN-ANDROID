package com.mio.kitchen.ui.modern

import android.content.Context
import android.net.Uri
import androidx.activity.result.ActivityResultCaller
import androidx.activity.result.contract.ActivityResultContracts

/**
 * RU: Помощник для `ACTION_OPEN_DOCUMENT` через новый Activity Result API.
 *
 * Заменяет устаревший `startActivityForResult` + `onActivityResult`. Новый
 * код должен использовать этот помощник вместо ручного управления request codes.
 *
 * Пример использования во Fragment/Activity:
 *
 * ```kotlin
 * private val openDocument = OpenDocumentHelper(this) { uri ->
 *     // обработать uri
 * }
 *
 * fun onPickFileClicked() {
 *     openDocument.launch(arrayOf("application/zip", "application/octet-stream"))
 * }
 * ```
 *
 * EN: Helper for `ACTION_OPEN_DOCUMENT` using the modern Activity Result API.
 *
 * Replaces the deprecated `startActivityForResult` + `onActivityResult` pair.
 * New code should use this helper instead of managing request codes manually.
 *
 * Usage in a Fragment/Activity:
 *
 * ```kotlin
 * private val openDocument = OpenDocumentHelper(this) { uri ->
 *     // handle uri
 * }
 *
 * fun onPickFileClicked() {
 *     openDocument.launch(arrayOf("application/zip", "application/octet-stream"))
 * }
 * ```
 */
class OpenDocumentHelper(
    caller: ActivityResultCaller,
    private val onResult: (Uri?) -> Unit
) {
    private val launcher = caller.registerForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        onResult(uri)
    }

    /**
     * RU: Запускает picker с указанными MIME-типами.
     * @param mimeTypes массив MIME-типов, например `arrayOf("application/zip")`.
     *
     * EN: Launches the picker with the given MIME types.
     * @param mimeTypes array of MIME types, e.g. `arrayOf("application/zip")`.
     */
    fun launch(mimeTypes: Array<String>) {
        launcher.launch(mimeTypes)
    }
}

/**
 * RU: Помощник для `ACTION_CREATE_DOCUMENT` через новый Activity Result API.
 *
 * Используется для export-операций: пользователь выбирает, куда сохранить
 * результат.
 *
 * EN: Helper for `ACTION_CREATE_DOCUMENT` using the modern Activity Result API.
 *
 * Used for export operations: the user picks where to save the result.
 */
class CreateDocumentHelper(
    caller: ActivityResultCaller,
    private val onResult: (Uri?) -> Unit
) {
    private val launcher = caller.registerForActivityResult(
        ActivityResultContracts.CreateDocument(mimeType = "application/octet-stream")
    ) { uri ->
        onResult(uri)
    }

    /**
     * RU: Запускает picker для создания файла с именем [suggestedName].
     *
     * EN: Launches the picker to create a file named [suggestedName].
     */
    fun launch(suggestedName: String) {
        launcher.launch(suggestedName)
    }
}

/**
 * RU: Помощник для persistent URI permission.
 *
 * Удобно комбинировать с [OpenDocumentHelper]: после выбора файла нужно
 * "persist" разрешение, чтобы URI оставался доступным после перезапуска.
 *
 * EN: Helper for persistent URI permission.
 *
 * Convenient to combine with [OpenDocumentHelper]: after a file is picked,
 * you typically want to persist the permission so the URI remains usable
 * after process restart.
 */
class UriPermissionPersistor(private val context: Context) {
    fun persistReadPermission(uri: Uri) {
        try {
            context.contentResolver.takePersistableUriPermission(
                uri,
                android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION
            )
        } catch (_: SecurityException) {
            // The caller did not request persistable permission — ignore.
        }
    }

    fun releaseReadPermission(uri: Uri) {
        try {
            context.contentResolver.releasePersistableUriPermission(
                uri,
                android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION
            )
        } catch (_: SecurityException) {
            // Already released or never granted — ignore.
        }
    }
}
