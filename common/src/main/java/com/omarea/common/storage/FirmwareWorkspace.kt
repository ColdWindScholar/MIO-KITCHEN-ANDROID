package com.omarea.common.storage

import android.content.Context
import java.io.File

/**
 * RU: Управляет рабочей директорией приложения для firmware-файлов.
 *
 * Workspace нужен как мост между `content://` URI/SAF и shell-инструментами,
 * которым нужен обычный file path. По умолчанию используется app-specific
 * external storage, чтобы путь был пригоднее для shell/root-сценариев, чем
 * private `/data/data/.../cache`.
 *
 * EN: Manages the app workspace for firmware files.
 *
 * The workspace bridges `content://` URI/SAF input and shell tools that require a
 * regular file path. It prefers app-specific external storage because that path is
 * more suitable for shell/root workflows than private `/data/data/.../cache`.
 */
class FirmwareWorkspace(context: Context) {
    private val appContext = context.applicationContext

    val rootDir: File by lazy {
        File(appContext.getExternalFilesDir(null) ?: appContext.filesDir, ROOT_DIR_NAME).apply {
            ensureDirectory(this)
        }
    }

    val importsDir: File by lazy {
        File(rootDir, IMPORTS_DIR_NAME).apply {
            ensureDirectory(this)
        }
    }

    val exportsDir: File by lazy {
        File(rootDir, EXPORTS_DIR_NAME).apply {
            ensureDirectory(this)
        }
    }

    fun prepareImportFile(displayName: String?): File {
        return prepareUniqueFile(importsDir, SafeFileName.clean(displayName))
    }

    fun prepareExportFile(displayName: String?): File {
        return prepareUniqueFile(exportsDir, SafeFileName.clean(displayName, "firmware-output.bin"))
    }

    fun clearOldImports(maxAgeMs: Long = DEFAULT_IMPORT_RETENTION_MS): Int {
        return clearOldFiles(importsDir, maxAgeMs)
    }

    private fun prepareUniqueFile(directory: File, fileName: String): File {
        ensureDirectory(directory)

        val dotIndex = fileName.lastIndexOf('.')
        val baseName = if (dotIndex > 0) fileName.substring(0, dotIndex) else fileName
        val extension = if (dotIndex > 0) fileName.substring(dotIndex) else ""

        var candidate = File(directory, fileName)
        var index = 1
        while (candidate.exists()) {
            candidate = File(directory, "${baseName}_${index}${extension}")
            index++
        }

        return candidate
    }

    private fun clearOldFiles(directory: File, maxAgeMs: Long): Int {
        val now = System.currentTimeMillis()
        val files = directory.listFiles() ?: return 0
        var deleted = 0

        for (file in files) {
            if (file.isFile && now - file.lastModified() > maxAgeMs && file.delete()) {
                deleted++
            }
        }

        return deleted
    }

    private fun ensureDirectory(directory: File) {
        if (!directory.exists()) {
            directory.mkdirs()
        }
    }

    companion object {
        const val ROOT_DIR_NAME = "firmware-workspace"
        const val IMPORTS_DIR_NAME = "imports"
        const val EXPORTS_DIR_NAME = "exports"
        const val DEFAULT_IMPORT_RETENTION_MS = 7L * 24L * 60L * 60L * 1000L
    }
}
