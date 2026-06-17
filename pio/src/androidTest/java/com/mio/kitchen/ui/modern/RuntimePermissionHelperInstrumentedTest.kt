package com.mio.kitchen.ui.modern

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * RU: Instrumented-тест для [RuntimePermissionHelper].
 *
 * Проверяет, что метод `requiredPermissions()` возвращает список,
 * соответствующий версии Android.
 *
 * EN: Instrumented test for [RuntimePermissionHelper].
 *
 * Verifies that `requiredPermissions()` returns a list matching the Android
 * version running on the device.
 */
@RunWith(AndroidJUnit4::class)
class RuntimePermissionHelperInstrumentedTest {

    @Test
    fun requiredPermissionsMatchAndroidVersion() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val perms = RuntimePermissionHelper.requiredPermissions()
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            assertTrue(
                "POST_NOTIFICATIONS must be required on Android 13+",
                perms.contains(android.Manifest.permission.POST_NOTIFICATIONS)
            )
        }
        if (android.os.Build.VERSION.SDK_INT <= android.os.Build.VERSION_CODES.S_V2) {
            assertTrue(
                "READ_EXTERNAL_STORAGE must be required on Android ≤ 12",
                perms.contains(android.Manifest.permission.READ_EXTERNAL_STORAGE)
            )
        }
        // areAllGranted on a fresh test context — should not throw.
        // It may return true (granted) or false (denied), both are valid.
        RuntimePermissionHelper.areAllGranted(context)
        RuntimePermissionHelper.missingPermissions(context)
    }
}
