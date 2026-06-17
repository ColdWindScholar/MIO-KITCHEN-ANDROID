package com.omarea.common.storage

import android.content.Context
import android.content.Intent
import android.net.Uri
import com.omarea.common.shared.FilePathResolver
import java.io.BufferedOutputStream
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.security.MessageDigest
import java.util.Locale

/**
 * RU: Android-реализация [StorageGateway].
 *
 * Главная политика: `content://` не передаётся напрямую в shell. Для SAF/provider
 * URI создаётся копия в app workspace, после чего shell получает обычный путь.
 * Legacy direct-path режим доступен как опция для старых сценариев, но не является
 * поведением по умолчанию.
 *
 * EN: Android implementation of [StorageGateway].
 *
 * Main policy: `content://` is not passed directly to shell. SAF/provider URIs are
 * copied into the app workspace, then shell receives a regular file path. Legacy
 * direct-path resolution is available as an option for old flows, but it is not the
 * default behavior.
 */
class AndroidStorageGateway(
    context: Context,
    private val workspace: FirmwareWorkspace = FirmwareWorkspace(context)
) : StorageGateway {
    private val appContext = context.applicationContext
    private val filePathResolver = FilePathResolver()

    override fun resolveUriForShell(
        uri: Uri,
        options: StorageResolveOptions
    ): StorageResolveResult {
        return try {
            when (uri.scheme?.lowercase(Locale.ROOT)) {
                "file" -> resolvePathForShell(uri.path ?: return failed("Empty file:// path"))
                "content" -> resolveContentUri(uri, options)
                else -> failed("Unsupported URI scheme: ${uri.scheme}")
            }
        } catch (ex: Exception) {
            failed("Failed to resolve URI for shell: ${uri}", ex)
        }
    }

    override fun resolvePathForShell(path: String): StorageResolveResult {
        val trimmedPath = path.trim()
        if (trimmedPath.isEmpty()) {
            return failed("Empty file path")
        }

        return StorageResolveResult.Resolved(
            shellPath = trimmedPath,
            sourceKind = StorageSourceKind.DirectFile,
            displayName = File(trimmedPath).name,
            originalUri = null
        )
    }

    fun persistReadPermission(uri: Uri, intentFlags: Int): Boolean {
        val persistable = (intentFlags and Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION) != 0
        val readable = (intentFlags and Intent.FLAG_GRANT_READ_URI_PERMISSION) != 0
        if (!persistable || !readable) {
            return false
        }

        return try {
            appContext.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
            true
        } catch (ignored: Exception) {
            false
        }
    }

    private fun resolveContentUri(uri: Uri, options: StorageResolveOptions): StorageResolveResult {
        if (options.preferLegacyDirectPath) {
            val directPath = try {
                filePathResolver.getPath(appContext, uri)
            } catch (ignored: Exception) {
                null
            }

            if (!directPath.isNullOrEmpty()) {
                return StorageResolveResult.Resolved(
                    shellPath = directPath,
                    sourceKind = StorageSourceKind.LegacyDirectPath,
                    displayName = File(directPath).name,
                    originalUri = uri.toString()
                )
            }
        }

        if (!options.copyContentUriToWorkspace) {
            return failed("Direct path is unavailable and workspace copy is disabled")
        }

        return copyContentUriToWorkspace(uri, options)
    }

    private fun copyContentUriToWorkspace(uri: Uri, options: StorageResolveOptions): StorageResolveResult {
        val displayName = filePathResolver.getFileName(appContext, uri) ?: uri.lastPathSegment
        val destination = workspace.prepareImportFile(displayName)
        var inputStream: InputStream? = null
        var outputStream: BufferedOutputStream? = null

        return try {
            inputStream = appContext.contentResolver.openInputStream(uri)
                ?: return failed("Cannot open input stream for URI: $uri")
            outputStream = BufferedOutputStream(FileOutputStream(destination, false))

            val digest = if (options.computeSha256) MessageDigest.getInstance("SHA-256") else null
            val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
            var totalBytes = 0L

            while (true) {
                val bytesRead = inputStream.read(buffer)
                if (bytesRead == -1) {
                    break
                }

                outputStream.write(buffer, 0, bytesRead)
                digest?.update(buffer, 0, bytesRead)
                totalBytes += bytesRead.toLong()
            }
            outputStream.flush()

            StorageResolveResult.Resolved(
                shellPath = destination.absolutePath,
                sourceKind = StorageSourceKind.WorkspaceCopy,
                displayName = destination.name,
                originalUri = uri.toString(),
                copiedBytes = totalBytes,
                sha256 = digest?.digest()?.joinToString("") { "%02x".format(it.toInt() and 0xff) }
            )
        } catch (ex: Exception) {
            destination.delete()
            failed("Failed to copy URI into workspace: $uri", ex)
        } finally {
            try {
                inputStream?.close()
            } catch (ignored: Exception) {
            }
            try {
                outputStream?.close()
            } catch (ignored: Exception) {
            }
        }
    }

    private fun failed(message: String, cause: Throwable? = null): StorageResolveResult.Failed {
        return StorageResolveResult.Failed(message, cause)
    }

    companion object {
        private const val DEFAULT_BUFFER_SIZE = 1024 * 1024
    }
}
