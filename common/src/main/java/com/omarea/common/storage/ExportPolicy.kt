package com.omarea.common.storage

import android.net.Uri

/**
 * RU: Политика экспорта файлов из workspace во внешнее хранилище.
 *
 * Определяет, как `FirmwareWorkspace.exportsDir/...` будет экспортирован
 * обратно пользователю. На targetSdk 35 прямой path во внешнее хранилище
 * недоступен — нужно использовать SAF (`ACTION_CREATE_DOCUMENT`), MediaStore
 * или persistent tree URI.
 *
 * EN: Policy for exporting files from the workspace to external storage.
 *
 * Defines how `FirmwareWorkspace.exportsDir/...` is exported back to the user.
 * On targetSdk 35 a direct path to external storage is not available — SAF
 * (`ACTION_CREATE_DOCUMENT`), MediaStore, or a persistent tree URI must be used.
 */
sealed class ExportPolicy {
    /**
     * RU: Использовать `ACTION_CREATE_DOCUMENT` для каждого файла.
     *
     * Пользователь выбирает, куда сохранить каждый файл по отдельности.
     *
     * EN: Use `ACTION_CREATE_DOCUMENT` for each file.
     *
     * The user picks where to save each file individually.
     */
    data object AskPerFile : ExportPolicy()

    /**
     * RU: Использовать persistent tree URI (выбранную папку).
     *
     * Пользователь один раз выбирает папку через `ACTION_OPEN_DOCUMENT_TREE`,
     * после чего все файлы сохраняются в неё без дополнительных диалогов.
     *
     * @param treeUri persistent URI выбранной папки.
     *
     * EN: Use a persistent tree URI (a picked folder).
     *
     * The user picks a folder once via `ACTION_OPEN_DOCUMENT_TREE`, then all
     * files are saved into it without further dialogs.
     *
     * @param treeUri persistent URI of the picked folder.
     */
    data class TreeFolder(val treeUri: Uri) : ExportPolicy()

    /**
     * RU: Использовать MediaStore для конкретного MIME-типа.
     *
     * Подходит для медиа-файлов; для firmware-образов обычно не используется.
     *
     * @param mimeType MIME-тип, например `application/octet-stream`.
     * @param relativePath относительный путь внутри MediaStore, например
     *   `Download/MIO-KITCHEN`.
     *
     * EN: Use MediaStore for a specific MIME type.
     *
     * Suitable for media files; not typically used for firmware images.
     *
     * @param mimeType MIME type, e.g. `application/octet-stream`.
     * @param relativePath relative path inside MediaStore, e.g.
     *   `Download/MIO-KITCHEN`.
     */
    data class MediaStoreExport(
        val mimeType: String,
        val relativePath: String
    ) : ExportPolicy()

    /**
     * RU: Использовать app-specific external storage (без экспорта).
     *
     * Файл остаётся внутри приложения и не виден пользователю напрямую.
     * Эквивалент "no export".
     *
     * EN: Use app-specific external storage (no export).
     *
     * The file stays inside the app and is not directly visible to the user.
     * Equivalent to "no export".
     */
    data object AppPrivate : ExportPolicy()
}

/**
 * RU: Результат экспорта файла.
 *
 * EN: File export result.
 */
sealed class ExportResult {
    /**
     * RU: Экспорт успешен.
     *
     * @param targetUri URI, по которому файл доступен пользователю (может быть
     *   `null`, если экспорт выполнен в app-private storage).
     * @param bytesCopied количество скопированных байтов.
     * @param sha256 SHA-256 экспортированного файла или `null`, если не считался.
     *
     * EN: Export succeeded.
     */
    data class Success(
        val targetUri: Uri?,
        val bytesCopied: Long,
        val sha256: String? = null
    ) : ExportResult()

    /**
     * RU: Пользователь отменил выбор файла.
     * EN: The user cancelled the file picker.
     */
    data object Cancelled : ExportResult()

    /**
     * RU: Экспорт провалился.
     * EN: Export failed.
     */
    data class Failed(val message: String, val cause: Throwable? = null) : ExportResult()
}

/**
 * RU: Опции экспорта.
 *
 * EN: Export options.
 */
data class ExportOptions(
    val policy: ExportPolicy,
    val computeSha256: Boolean = true,
    val overwriteExisting: Boolean = false
)

/**
 * RU: Интерфейс экспортёра файлов из workspace.
 *
 * Реализация зависит от выбранной [ExportPolicy]. UI вызывает
 * [export] с готовым [ExportOptions] и получает типизированный [ExportResult].
 *
 * EN: Interface for exporting files from the workspace.
 *
 * Implementation depends on the chosen [ExportPolicy]. The UI calls [export]
 * with a ready [ExportOptions] and receives a typed [ExportResult].
 */
interface WorkspaceExporter {
    /**
     * RU: Экспортирует файл [sourceFile] согласно [options].
     *
     * Этот метод блокирующий — вызывающая сторона должна запускать его вне
     * main-thread (например, в `Dispatchers.IO`).
     *
     * EN: Exports [sourceFile] according to [options].
     *
     * This method is blocking — the caller must run it off the main thread
     * (e.g. on `Dispatchers.IO`).
     */
    fun export(sourceFile: java.io.File, options: ExportOptions): ExportResult
}
