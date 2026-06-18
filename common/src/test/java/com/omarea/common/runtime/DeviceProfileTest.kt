package com.omarea.common.runtime

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * RU: Тесты для [DeviceProfile] и [DeviceProfileProvider].
 * EN: Tests for [DeviceProfile] and [DeviceProfileProvider].
 */
class DeviceProfileTest {

    @Test
    fun `scoped storage enforced on API 30+`() {
        assertTrue(DeviceProfile.forTests(sdkInt = 30).enforcesScopedStorage)
        assertTrue(DeviceProfile.forTests(sdkInt = 33).enforcesScopedStorage)
        assertTrue(DeviceProfile.forTests(sdkInt = 35).enforcesScopedStorage)
        assertFalse(DeviceProfile.forTests(sdkInt = 29).enforcesScopedStorage)
        assertFalse(DeviceProfile.forTests(sdkInt = 28).enforcesScopedStorage)
    }

    @Test
    fun `scoped storage boundary on API 29+`() {
        assertTrue(DeviceProfile.forTests(sdkInt = 29).hasScopedStorageBoundary)
        assertFalse(DeviceProfile.forTests(sdkInt = 28).hasScopedStorageBoundary)
    }

    @Test
    fun `notification permission required on API 33+`() {
        assertTrue(DeviceProfile.forTests(sdkInt = 33).requiresNotificationPermission)
        assertFalse(DeviceProfile.forTests(sdkInt = 32).requiresNotificationPermission)
    }

    @Test
    fun `foreground service type required on API 34+`() {
        assertTrue(DeviceProfile.forTests(sdkInt = 34).requiresForegroundServiceType)
        assertFalse(DeviceProfile.forTests(sdkInt = 33).requiresForegroundServiceType)
    }

    @Test
    fun `Android 15+ detection`() {
        assertTrue(DeviceProfile.forTests(sdkInt = 35).isAndroid15Plus)
        assertTrue(DeviceProfile.forTests(sdkInt = 36).isAndroid15Plus)
        assertFalse(DeviceProfile.forTests(sdkInt = 34).isAndroid15Plus)
    }

    @Test
    fun `label includes manufacturer model and root status`() {
        val p = DeviceProfile.forTests(
            sdkInt = 33,
            manufacturer = "Google",
            model = "Pixel 7",
            hasRoot = true,
            selinuxMode = SelinuxMode.ENFORCING
        )
        val label = p.label
        assertTrue(label.contains("Android 33"))
        assertTrue(label.contains("Google Pixel 7"))
        assertTrue(label.contains("rooted"))
        assertTrue(label.contains("enforcing"))
    }

    @Test
    fun `emulator flag is shown in label`() {
        val p = DeviceProfile.forTests(isEmulator = true)
        assertTrue(p.label.contains("emulator"))
    }

    @Test
    fun `unknown selinux is hidden from label`() {
        val p = DeviceProfile.forTests(selinuxMode = SelinuxMode.UNKNOWN)
        assertFalse(p.label.contains("selinux"))
    }

    @Test
    fun `forTests defaults are sensible`() {
        val p = DeviceProfile.forTests()
        assertEquals(33, p.sdkInt)
        assertEquals("Google", p.manufacturer)
        assertEquals(listOf("arm64-v8a", "armeabi-v7a"), p.abiList)
        assertFalse(p.isEmulator)
        assertFalse(p.hasRoot!!)
        assertEquals(SelinuxMode.ENFORCING, p.selinuxMode)
    }
}
