package com.mio.kitchen

import android.app.Application
import android.content.Context
import android.util.Log
import java.io.File
import java.io.FileWriter
import java.io.PrintWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class PIO : Application() {

    override fun attachBaseContext(base: Context) {
        Thread.setDefaultUncaughtExceptionHandler(CrashLogger(base))
        super.attachBaseContext(LanguageConfig.wrap(base))
    }

    override fun onCreate() {
        super.onCreate()
    }

    /**
     * RU: Глобальный обработчик необработанных исключений.
     *     Записывает краш-лог в файл crash.log в filesDir, чтобы
     *     пользователь мог прочитать его после перезапуска.
     *     После записи вызывает оригинальный handler (краш).
     *
     * EN: Global uncaught exception handler.
     *     Writes the crash log to crash.log in filesDir so the
     *     user can read it after restarting.
     *     After writing, delegates to the original handler (crash).
     */
    private class CrashLogger(context: Context) : Thread.UncaughtExceptionHandler {
        private val appContext = context.applicationContext
        private val defaultHandler = Thread.getDefaultUncaughtExceptionHandler()

        override fun uncaughtException(thread: Thread, throwable: Throwable) {
            try {
                val logFile = File(appContext.filesDir, "crash.log")
                val timestamp = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date())
                FileWriter(logFile, true).use { writer ->
                    writer.appendLine("=== Crash at $timestamp ===")
                    PrintWriter(writer).use { pw ->
                        throwable.printStackTrace(pw)
                    }
                    writer.appendLine()
                }
                Log.e("MIO-KITCHEN", "Uncaught exception", throwable)
            } catch (_: Throwable) {
                // If logging itself fails, just proceed to the default handler.
            }
            defaultHandler?.uncaughtException(thread, throwable)
        }
    }
}
