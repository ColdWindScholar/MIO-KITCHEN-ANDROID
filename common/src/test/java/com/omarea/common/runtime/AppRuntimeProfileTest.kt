package com.omarea.common.runtime

import com.omarea.common.firmware.AndroidVersionHint
import com.omarea.common.firmware.FirmwareCapabilities
import com.omarea.common.firmware.FirmwarePackageType
import com.omarea.common.firmware.FirmwareProfile
import com.omarea.common.firmware.FirmwareSource
import com.omarea.common.operations.OperationPlanner
import com.omarea.common.toolchain.CapabilityBasedToolchainResolver
import com.omarea.common.toolchain.FirmwareOperation
import com.omarea.common.toolchain.ToolDescriptor
import com.omarea.common.toolchain.ToolPurpose
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * RU: Тесты для [AppRuntimeProfile] и [AppRuntimeProfileResolver].
 * EN: Tests for [AppRuntimeProfile] and [AppRuntimeProfileResolver].
 */
class AppRuntimeProfileTest {

    private val device = DeviceProfile.forTests(sdkInt = 33, hasRoot = true)
    private val manifest = listOf(
        ToolDescriptor("busybox", "1.36", listOf("arm64-v8a"), null, listOf(ToolPurpose.BUSYBOX), "x", "y", true),
        ToolDescriptor("magiskboot", "27", listOf("arm64-v8a"), null, listOf(ToolPurpose.BOOT_IMAGE), "x", "y", true)
    )

    private fun firmware(): FirmwareProfile = FirmwareProfile(
        source = FirmwareSource.FileNameOnly("rom.zip"),
        packageType = FirmwarePackageType.ZIP_OTA,
        androidVersion = AndroidVersionHint(13),
        capabilities = FirmwareCapabilities(hasBootImage = true),
        partitions = emptyList(),
        compression = emptySet(),
        warnings = emptyList()
    )

    private fun resolver(): AppRuntimeProfileResolver = AppRuntimeProfileResolver(
        deviceProvider = { device },
        operationPlanner = OperationPlanner(CapabilityBasedToolchainResolver(manifest))
    )

    @Test
    fun `initial profile has device but no firmware`() {
        val profile = resolver().initial()
        assertNotNull(profile.device)
        assertNull(profile.firmware)
        assertFalse(profile.hasFirmware)
        assertFalse(profile.hasReadyOperation)
    }

    @Test
    fun `withFirmware sets firmware but no operation`() {
        val profile = resolver().withFirmware(firmware())
        assertTrue(profile.hasFirmware)
        assertNull(profile.activeOperation)
        assertFalse(profile.hasReadyOperation)
    }

    @Test
    fun `withOperation builds an operation plan`() {
        val profile = resolver().withOperation(firmware(), FirmwareOperation.UNPACK_BOOT_IMAGE)
        assertEquals(FirmwareOperation.UNPACK_BOOT_IMAGE, profile.activeOperation)
        assertNotNull(profile.operationPlan)
        assertTrue(profile.hasReadyOperation)
    }

    @Test
    fun `summary includes device sdk and root status`() {
        val profile = resolver().initial()
        val s = profile.summary
        assertTrue(s.contains("device=33"))
        assertTrue(s.contains("root"))
    }

    @Test
    fun `summary includes firmware info when firmware is selected`() {
        val profile = resolver().withFirmware(firmware())
        val s = profile.summary
        assertTrue(s.contains("firmware="))
    }

    @Test
    fun `summary includes operation and ready state`() {
        val profile = resolver().withOperation(firmware(), FirmwareOperation.UNPACK_BOOT_IMAGE)
        val s = profile.summary
        assertTrue(s.contains("op="))
        assertTrue(s.contains("(ready)"))
    }

    @Test
    fun `withOperation with missing tools marks plan as blocked`() {
        val r = AppRuntimeProfileResolver(
            deviceProvider = { device },
            operationPlanner = OperationPlanner(CapabilityBasedToolchainResolver(emptyList()))
        )
        val profile = r.withOperation(firmware(), FirmwareOperation.UNPACK_BOOT_IMAGE)
        assertFalse(profile.hasReadyOperation)
        assertTrue(profile.summary.contains("(blocked)"))
    }
}
