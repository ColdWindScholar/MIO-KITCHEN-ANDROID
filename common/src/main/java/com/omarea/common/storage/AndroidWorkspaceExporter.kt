package com.omarea.common.storage

import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import java.io.File
import java.io.OutputStream
import java.security.MessageDigest

/**
 * RU: Android-реализация [WorkspaceExporter].
 *
 * Поддерживает все варианты [ExportPolicy]:
 *   - [ExportPolicy.AskPerFile] — копирует в URI, предоставленный вызывающей
 *     стороной (UI получает URI через `ActivityResultContracts.CreateDocument`);
 *   - [ExportPolicy.TreeFolder] — копирует в выбранную папку (через
 *     `DocumentsContract.createDocument`);
 *   - [ExportPolicy.MediaStoreExport] — копирует через MediaStore (Android 10+);
 *   - [ExportPolicy.AppPrivate] — копирует в `getExternalFilesDir(...)`.
 *
 * EN: Android implementation of [WorkspaceExporter].
 *
 * Supports every [ExportPolicy] variant:
 *   - [ExportPolicy.AskPerFile] — copies to a URI supplied by the caller
 *     (the UI obtains the URI via `ActivityResultContracts.CreateDocument`);
 *   - [ExportPolicy.TreeFolder] — copies into a picked folder (via
 *     `DocumentsContract.createDocument`);
 *   - [ExportPolicy.MediaStoreExport] — copies through MediaStore (Android 10+);
 *   - [ExportPolicy.AppPrivate] — copies to `getExternalFilesDir(...)`.
 */
class AndroidWorkspaceExporter(
    private val context: Context
) : WorkspaceExporter {

    override fun export(sourceFile: File, options: ExportOptions): ExportResult {
        if (!sourceFile.exists()) {
            return ExportResult.Failed("Source file does not exist: ${sourceFile.absolutePath}")
        }
        return try {
            when (options.policy) {
                is ExportPolicy.AskPerFile -> {
                    // Caller must supply a target URI via a separate helper.
                    // If we got here without one, treat as cancelled.
                    ExportResult.Cancelled
                }
                is ExportPolicy.TreeFolder -> {
                    exportToTree(sourceFile, options.policy.treeUri, options)
                }
                is ExportPolicy.MediaStoreExport -> {
                    exportViaMediaStore(sourceFile, options.policy, options)
                }
                ExportPolicy.AppPrivate -> {
                    exportToAppPrivate(sourceFile, options)
                }
            }
        } catch (e: Throwable) {
            ExportResult.Failed(
                message = e.message ?: e.javaClass.simpleName,
                cause = e
            )
        }
    }

    /**
     * RU: Экспортирует файл в конкретный URI, полученный из `ACTION_CREATE_DOCUMENT`.
     *
     * Этот метод используется вместе с [ExportPolicy.AskPerFile] — UI получает
     * URI через Activity Result API и передаёт его сюда.
     *
     * EN: Exports a file to a specific URI obtained from `ACTION_CREATE_DOCUMENT`.
     *
     * Used together with [ExportPolicy.AskPerFile] — the UI obtains the URI
     * through the Activity Result API and passes it here.
     */
    fun exportToUri(sourceFile: File, targetUri: Uri, options: ExportOptions): ExportResult {
        if (!sourceFile.exists()) {
            return ExportResult.Failed("Source file does not exist: ${sourceFile.absolutePath}")
        }
        return try {
            val (copied, sha) = copyWithOptionalHash(sourceFile) { output ->
                context.contentResolver.openOutputStream(targetUri, "w")?.use { out ->
                    copyStream(sourceFile.inputStream(), out)
                } ?: throw java.io.IOException("Cannot open output stream for $targetUri")
            }
            ExportResult.Success(
                targetUri = targetUri,
                bytesCopied = copied,
                sha256 = sha
            )
        } catch (e: Throwable) {
            ExportResult.Failed(
                message = e.message ?: e.javaClass.simpleName,
                cause = e
            )
        }
    }

    private fun exportToTree(
        sourceFile: File,
        treeUri: Uri,
        options: ExportOptions
    ): ExportResult {
        val displayName = sourceFile.name
        val targetUri = android.provider.DocumentsContract.createDocument(
            context.contentResolver,
            treeUri,
            "application/octet-stream",
            displayName
        ) ?: return ExportResult.Failed("Could not create document in tree: $treeUri")

        return exportToUri(sourceFile, targetUri, options)
    }

    private fun exportViaMediaStore(
        sourceFile: File,
        policy: ExportPolicy.MediaStoreExport,
        options: ExportOptions
    ): ExportResult {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
            return ExportResult.Failed("MediaStore export requires Android 10+")
        }
        val values = android.content.ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, sourceFile.name)
            put(MediaStore.MediaColumns.MIME_TYPE, policy.mimeType)
            put(MediaStore.MediaColumns.RELATIVE_PATH, policy.relativePath)
        }
        val collection = MediaStore.Files.getContentUri("external")
        val targetUri = context.contentResolver.insert(collection, values)
            ?: return ExportResult.Failed("Could not insert MediaStore entry")
        return exportToUri(sourceFile, targetUri, options)
    }

    private fun exportToAppPrivate(
        sourceFile: File,
        options: ExportOptions
    ): ExportResult {
        val targetDir = context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS)
            ?: context.filesDir
        if (!targetDir.exists()) targetDir.mkdirs()
        val targetFile = File(targetDir, sourceFile.name)
        return try {
            val (copied, sha) = copyWithOptionalHash(sourceFile) { output ->
                targetFile.outputStream().use { out ->
                    copyStream(sourceFile.inputStream(), out)
                }
            }
            ExportResult.Success(
                targetUri = Uri.fromFile(targetFile),
                bytesCopied = copied,
                sha256 = sha
            )
        } catch (e: Throwable) {
            ExportResult.Failed(
                message = e.message ?: e.javaClass.simpleName,
                cause = e
            )
        }
    }

    private fun copyWithOptionalHash(
        source: File,
        writer: ((OutputStream) -> Unit)
    ): Pair<Long, String?> {
        val digest = if (computeSha256Enabled) MessageDigest.getInstance("SHA-256") else null
        var bytesCopied = 0L
        source.inputStream().use { input ->
            val tempDigest = digest
            val counter = longArrayOf(0L)
            writer { out ->
                copyStream(input, out, tempDigest, counter)
            }
            bytesCopied = counter[0]
        }
        val sha = digest?.digest()?.joinToString("") { "%02x".format(it) }
        return bytesCopied to sha
    }

    /**
     * RU: Копирует InputStream в OutputStream с опциональным подсчётом SHA-256
     *     и количества байтов.
     * EN: Copies an InputStream to an OutputStream with optional SHA-256 and
     *     byte-count tracking.
     */
    private fun copyStream(
        input: java.io.InputStream,
        output: OutputStream,
        digest: MessageDigest? = null,
        counter: LongArray? = null
    ) {
        val buffer = ByteArray(64 * 1024)
        while (true) {
            val read = input.read(buffer)
            if (read <= 0) break
            output.write(buffer, 0, read)
            digest?.update(buffer, 0, read)
            if (counter != null) counter[0] += read
        }
    }

    private val computeSha256Enabled: Boolean = true
}
