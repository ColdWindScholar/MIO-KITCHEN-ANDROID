package com.mio.kitchen.ui.modern

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat

/**
 * RU: Помощник для runtime-permissions, нужных на targetSdk 35.
 *
 * Скрывает логику "на каких Android нужно какое разрешение", чтобы UI не
 * разбрасывал `Build.VERSION.SDK_INT` проверки по всем активностям.
 *
 * EN: Helper for runtime permissions needed at targetSdk 35.
 *
 * Hides the "which Android needs which permission" logic so the UI does not
 * sprinkle `Build.VERSION.SDK_INT` checks across activities.
 */
object RuntimePermissionHelper {

    /**
     * RU: Список разрешений, которые нужно запросить перед запуском firmware-операций.
     *
     * Порядок важен — UI запрашивает их последовательно.
     *
     * EN: List of permissions to request before launching firmware operations.
     *
     * Order matters — the UI requests them sequentially.
     */
    fun requiredPermissions(): List<String> = buildList {
        // Android 13+ requires POST_NOTIFICATIONS for foreground-service notifications.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            add(Manifest.permission.POST_NOTIFICATIONS)
        }
        // Legacy READ_EXTERNAL_STORAGE for Android ≤ 12 (capped at maxSdk=32 in manifest).
        if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.S_V2) {
            add(Manifest.permission.READ_EXTERNAL_STORAGE)
        }
    }

    /**
     * RU: Возвращает true, если все [requiredPermissions] выданы.
     * EN: Returns true when all [requiredPermissions] are granted.
     */
    fun areAllGranted(context: Context): Boolean {
        return requiredPermissions().all {
            ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED
        }
    }

    /**
     * RU: Возвращает список разрешений, которые ещё не выданы.
     * EN: Returns the list of permissions that are not yet granted.
     */
    fun missingPermissions(context: Context): List<String> {
        return requiredPermissions().filter {
            ContextCompat.checkSelfPermission(context, it) != PackageManager.PERMISSION_GRANTED
        }
    }

    /**
     * RU: Запрашивает недостающие разрешения у пользователя.
     *
     * @param activity активность, инициирующая запрос.
     * @param requestCode код запроса для `onRequestPermissionsResult`.
     * @return `true`, если запрос был отправлен; `false`, если все разрешения уже выданы.
     *
     * EN: Requests missing permissions from the user.
     */
    fun requestMissing(activity: Activity, requestCode: Int): Boolean {
        val missing = missingPermissions(activity)
        if (missing.isEmpty()) return false
        ActivityCompat.requestPermissions(activity, missing.toTypedArray(), requestCode)
        return true
    }

    /**
     * RU: Проверяет результат запроса разрешений.
     *
     * @return `true`, если ВСЕ запрошенные разрешения выданы.
     *
     * EN: Checks the permission-request result.
     */
    fun areAllGranted(grantResults: IntArray): Boolean {
        return grantResults.isNotEmpty() &&
            grantResults.all { it == PackageManager.PERMISSION_GRANTED }
    }
}
