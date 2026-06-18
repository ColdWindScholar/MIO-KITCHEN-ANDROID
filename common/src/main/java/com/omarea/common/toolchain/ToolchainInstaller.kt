package com.omarea.common.toolchain

import java.io.File
import java.io.InputStream
import java.security.MessageDigest

/**
 * RU: Результат установки toolchain.
 *
 * EN: Toolchain installation result.
 */
sealed class ToolchainInstallResult {
    /**
     * RU: Установка прошла успешно.
     *
     * @param toolsDir директория с распакованными инструментами.
     * @param installedTools список успешно установленных инструментов.
     * @param skippedTools список инструментов, которые были пропущены (необязательные).
     * @param verifiedChecksums `true`, если все SHA-256 проверены.
     *
     * EN: Installation succeeded.
     */
    data class Success(
        val toolsDir: File,
        val installedTools: List<String>,
        val skippedTools: List<String>,
        val verifiedChecksums: Boolean
    ) : ToolchainInstallResult()

    /**
     * RU: Установка провалилась.
     *
     * EN: Installation failed.
     */
    data class Failed(
        val message: String,
        val cause: Throwable? = null,
        val partialDir: File? = null
    ) : ToolchainInstallResult()

    /**
     * RU: Проверка SHA-256 не прошла для одного из инструментов.
     *
     * EN: SHA-256 verification failed for one of the tools.
     */
    data class ChecksumMismatch(
        val toolName: String,
        val expected: String,
        val actual: String
    ) : ToolchainInstallResult()
}

/**
 * RU: Устанавливает инструменты из манифеста в локальную директорию.
 *
 * Алгоритм:
 *   1. Создать `toolsDir` если не существует.
 *   2. Для каждого инструмента из [manifest]:
 *      - открыть поток из [assetProvider] (по имени инструмента);
 *      - скопировать в `toolsDir/<name>`;
 *      - если в манифесте указан `sha256`, проверить;
 *      - выставить executable бит.
 *   3. Вернуть [ToolchainInstallResult] с установленными инструментами.
 *
 * Контракт:
 *   - НЕ запускает shell;
 *   - НЕ зависит от Android Context (использует [assetProvider]);
 *   - безопасен для вызова из любого потока.
 *
 * EN: Installs tools from a manifest into a local directory.
 *
 * Algorithm:
 *   1. Create `toolsDir` if it does not exist.
 *   2. For each tool in [manifest]:
 *      - open a stream from [assetProvider] (by tool name);
 *      - copy to `toolsDir/<name>`;
 *      - if the manifest declares `sha256`, verify it;
 *      - set the executable bit.
 *   3. Return a [ToolchainInstallResult] with the installed tools.
 *
 * Contract:
 *   - does NOT run shell;
 *   - does NOT depend on Android Context (uses [assetProvider]);
 *   - safe to call from any thread.
 */
class ToolchainInstaller(
    private val manifest: List<ToolDescriptor>,
    private val assetProvider: (String) -> InputStream?,
    private val sha256Verifier: (File, String) -> Boolean = ::defaultSha256Verifier
) {

    /**
     * RU: Устанавливает все инструменты из манифеста в [toolsDir].
     *
     * @param toolsDir целевая директория (будет создана при необходимости).
     * @param verifyChecksums `true`, чтобы проверять SHA-256 (по умолчанию `true`).
     * @param overwrite `true`, чтобы перезаписывать существующие файлы (по умолчанию `false`).
     *
     * EN: Installs every tool from the manifest into [toolsDir].
     */
    fun install(
        toolsDir: File,
        verifyChecksums: Boolean = true,
        overwrite: Boolean = false
    ): ToolchainInstallResult {
        try {
            if (!toolsDir.exists() && !toolsDir.mkdirs()) {
                return ToolchainInstallResult.Failed(
                    message = "Could not create tools directory: ${toolsDir.absolutePath}",
                    partialDir = null
                )
            }
            val installed = mutableListOf<String>()
            val skipped = mutableListOf<String>()
            var allChecksumsVerified = true

            for (descriptor in manifest) {
                val targetFile = File(toolsDir, descriptor.name)
                if (targetFile.exists() && !overwrite) {
                    // Already installed; verify checksum if requested.
                    if (verifyChecksums && descriptor.sha256 != null) {
                        if (!sha256Verifier(targetFile, descriptor.sha256)) {
                            return ToolchainInstallResult.ChecksumMismatch(
                                toolName = descriptor.name,
                                expected = descriptor.sha256,
                                actual = "<mismatch>"
                            )
                        }
                    }
                    installed.add(descriptor.name)
                    continue
                }

                val stream = try {
                    assetProvider(descriptor.name)
                } catch (e: Exception) {
                    null
                }
                if (stream == null) {
                    skipped.add(descriptor.name)
                    continue
                }

                stream.use { input ->
                    targetFile.outputStream().use { output ->
                        input.copyTo(output)
                    }
                }

                // Verify SHA-256 if declared.
                if (verifyChecksums && descriptor.sha256 != null) {
                    if (!sha256Verifier(targetFile, descriptor.sha256)) {
                        // Delete the bad file and fail.
                        targetFile.delete()
                        return ToolchainInstallResult.ChecksumMismatch(
                            toolName = descriptor.name,
                            expected = descriptor.sha256,
                            actual = "<mismatch>"
                        )
                    }
                } else if (descriptor.sha256 == null) {
                    allChecksumsVerified = false
                }

                // Set executable bit.
                if (!targetFile.setExecutable(true, true)) {
                    // Not fatal — some filesystems don't support it.
                }
                installed.add(descriptor.name)
            }

            return ToolchainInstallResult.Success(
                toolsDir = toolsDir,
                installedTools = installed,
                skippedTools = skipped,
                verifiedChecksums = allChecksumsVerified && verifyChecksums
            )
        } catch (e: Throwable) {
            return ToolchainInstallResult.Failed(
                message = e.message ?: e.javaClass.simpleName,
                cause = e,
                partialDir = toolsDir.takeIf { it.exists() }
            )
        }
    }

    companion object {
        /**
         * RU: Дефолтный SHA-256 верификатор: читает файл и сравнивает хэш.
         * EN: Default SHA-256 verifier: reads the file and compares the hash.
         */
        fun defaultSha256Verifier(file: File, expected: String): Boolean {
            val actual = computeSha256(file)
            return actual.equals(expected, ignoreCase = true)
        }

        /**
         * RU: Считает SHA-256 файла в виде hex-строки.
         * EN: Computes the SHA-256 of a file as a hex string.
         */
        fun computeSha256(file: File): String {
            val digest = MessageDigest.getInstance("SHA-256")
            file.inputStream().use { input ->
                val buffer = ByteArray(64 * 1024)
                while (true) {
                    val read = input.read(buffer)
                    if (read <= 0) break
                    digest.update(buffer, 0, read)
                }
            }
            return digest.digest().joinToString("") { "%02x".format(it) }
        }
    }
}
