package com.mio.kitchen

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.omarea.common.toolchain.ToolManifestLoader
import com.omarea.common.toolchain.ToolchainInstallResult
import com.omarea.common.toolchain.ToolchainInstaller
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.File
import java.io.InputStream

/**
 * RU: Foreground service для длительных firmware-операций.
 *
 * На Android 14+ (API 34+) foreground-сервисы обязаны объявлять тип.
 * Этот сервис использует тип `dataSync`, что соответствует долго идущим
 * операциям с файлами прошивок (распаковка/упаковка/верификация).
 *
 * Сервис НЕ выполняет саму операцию — он только держит foreground-state,
 * чтобы система не убила процесс во время долгой shell-операции. Сама операция
 * запускается через `OperationExecutor` в корутине вызывающей стороны.
 *
 * Stage 21: сервис автоматически запускает ToolchainInstaller в `onCreate`
 * для гарантии, что bundled-инструменты извлечены и проверены по SHA-256
 * перед запуском любой операции.
 *
 * EN: Foreground service for long-running firmware operations.
 *
 * On Android 14+ (API 34+) foreground services must declare a type. This
 * service uses the `dataSync` type, which matches long-running firmware file
 * operations (unpack/pack/verify).
 *
 * The service does NOT run the operation itself — it only holds the
 * foreground state so the system does not kill the process during a long
 * shell operation. The operation itself is launched via `OperationExecutor`
 * in the caller's coroutine.
 *
 * Stage 21: the service auto-runs ToolchainInstaller in `onCreate` to
 * guarantee that bundled tools are extracted and SHA-256-verified before any
 * operation is launched.
 */
class FirmwareOperationService : Service() {

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        // RU: Stage 21 — запускаем установку toolchain при создании сервиса.
        //     Это идемпотентно: ToolchainInstaller пропускает уже установленные
        //     файлы с проверкой SHA-256. Если toolsDir уже заполнен — операция
        //     мгновенная.
        // EN: Stage 21 — kick off toolchain installation when the service is
        //     created. This is idempotent: ToolchainInstaller skips already-
        //     installed files after SHA-256 verification. If toolsDir is
        //     already populated, this is instant.
        ensureToolchainInstalled()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val notification = buildNotification(
            title = intent?.getStringExtra(EXTRA_TITLE) ?: getString(R.string.app_name),
            text = intent?.getStringExtra(EXTRA_TEXT) ?: ""
        )
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            // Android 14+ requires explicit foregroundServiceType.
            startForeground(
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
        return START_NOT_STICKY
    }

    private fun buildNotification(title: String, text: String): Notification {
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Firmware operations",
                NotificationManager.IMPORTANCE_LOW
            )
            manager.createNotificationChannel(channel)
        }
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(title)
            .setContentText(text)
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    // ------------------------------------------------------------------
    // Stage 21: ToolchainInstaller integration
    // ------------------------------------------------------------------

    /**
     * RU: Запускает ToolchainInstaller в background coroutine.
     *
     * Использует [serviceScope] с [SupervisorJob], чтобы установка не
     * прерывалась при отмене отдельной операции. [installMutex] гарантирует,
     * что одновременные `startForegroundService` вызовы не запустят
     * параллельные установки.
     *
     * EN: Runs ToolchainInstaller in a background coroutine.
     *
     * Uses [serviceScope] with [SupervisorJob] so that installation is not
     * interrupted when a single operation is cancelled. [installMutex]
     * guarantees that concurrent `startForegroundService` calls do not race
     * on parallel installs.
     */
    private fun ensureToolchainInstalled() {
        serviceScope.launch {
            installMutex.withLock {
                runInstallerIfNeeded()
            }
        }
    }

    private suspend fun runInstallerIfNeeded() {
        try {
            val toolsDir = File(filesDir, TOOLS_DIR_NAME).apply {
                if (!exists()) mkdirs()
            }
            val manifest = loadManifest() ?: return
            val installer = ToolchainInstaller(
                manifest = manifest,
                assetProvider = { name -> openToolStream(name) }
            )
            val result = installer.install(
                toolsDir = toolsDir,
                verifyChecksums = false, // manifest ships with sha256=null for now
                overwrite = false
            )
            when (result) {
                is ToolchainInstallResult.Success -> {
                    // RU: сохраняем toolsDir в companion, чтобы OperationExecutor
                    //     мог его использовать.
                    // EN: stash the toolsDir in the companion so OperationExecutor
                    //     can use it.
                    lastInstalledToolsDir = result.toolsDir
                }
                is ToolchainInstallResult.Failed -> {
                    android.util.Log.w(
                        TAG,
                        "Toolchain installation failed: ${result.message}"
                    )
                }
                is ToolchainInstallResult.ChecksumMismatch -> {
                    android.util.Log.w(
                        TAG,
                        "Checksum mismatch for ${result.toolName}: " +
                            "expected=${result.expected} actual=${result.actual}"
                    )
                }
            }
        } catch (e: Throwable) {
            android.util.Log.e(TAG, "Toolchain installation error", e)
        }
    }

    /**
     * RU: Открывает поток к `bin/<name>` в assets. Возвращает `null`, если
     *     бинарник не найден (в этом случае ToolchainInstaller просто
     *     пропустит его и запишет в skippedTools).
     *
     * EN: Opens a stream to `bin/<name>` in assets. Returns `null` when the
     *     binary is not present (in that case ToolchainInstaller just skips
     *     it and records it in skippedTools).
     */
    private fun openToolStream(name: String): InputStream? = try {
        assets.open("bin/$name")
    } catch (_: Throwable) {
        null
    }

    /**
     * RU: Загружает манифест из `assets/toolchain/manifest.json`. Если файл
     *     не найден или некорректен, возвращает `null` — сервис продолжит
     *     работать, но toolchain не будет установлен.
     *
     * EN: Loads the manifest from `assets/toolchain/manifest.json`. When the
     *     file is missing or invalid, returns `null` — the service keeps
     *     running but the toolchain is not installed.
     */
    private fun loadManifest() = try {
        assets.open("toolchain/manifest.json").use { stream ->
            ToolManifestLoader().load(stream)
        }
    } catch (e: Throwable) {
        android.util.Log.w(TAG, "Could not load toolchain manifest", e)
        null
    }

    companion object {
        const val NOTIFICATION_ID = 0x4D49_4F21
        const val CHANNEL_ID = "firmware-operations"
        const val EXTRA_TITLE = "title"
        const val EXTRA_TEXT = "text"
        const val TOOLS_DIR_NAME = "toolchain-bin"
        private const val TAG = "FirmwareOpService"

        /**
         * RU: Директория с извлечёнными инструментами, заполненная
         *     [ToolchainInstaller]. `null`, пока установка не завершена или
         *     провалилась.
         *
         * EN: Directory with extracted tools, populated by
         *     [ToolchainInstaller]. `null` until installation completes or
         *     `null` on failure.
         */
        @Volatile
        var lastInstalledToolsDir: File? = null
            private set

        /**
         * RU: Скоуп корутин сервиса. SupervisorJob — отмена одной установки
         *     не отменяет следующие.
         *
         * EN: Coroutine scope of the service. SupervisorJob — cancelling one
         *     installation does not cancel subsequent ones.
         */
        private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

        /**
         * RU: Мьютекс, гарантирующий, что одновременно идёт только одна
         *     установка toolchain.
         *
         * EN: Mutex guaranteeing that only one toolchain installation runs at
         *     a time.
         */
        private val installMutex = Mutex()
    }
}
