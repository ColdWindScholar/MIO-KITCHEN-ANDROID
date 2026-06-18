package com.omarea.common.operations

import com.omarea.common.firmware.AndroidVersionHint
import com.omarea.common.firmware.FirmwareCapabilities
import com.omarea.common.firmware.FirmwarePackageType
import com.omarea.common.firmware.FirmwareProfile
import com.omarea.common.firmware.FirmwareSource
import com.omarea.common.runtime.DeviceProfile
import com.omarea.common.runtime.SelinuxMode
import com.omarea.common.toolchain.CapabilityBasedToolchainResolver
import com.omarea.common.toolchain.FirmwareOperation
import com.omarea.common.toolchain.ToolDescriptor
import com.omarea.common.toolchain.ToolPurpose
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * RU: Тесты для [OperationPlanner], [OperationPlan] и [OperationSafetyProfile].
 * EN: Tests for [OperationPlanner], [OperationPlan] and [OperationSafetyProfile].
 */
class OperationPlannerTest {

    private fun firmware(
        caps: FirmwareCapabilities = FirmwareCapabilities(hasBootImage = true)
    ): FirmwareProfile = FirmwareProfile(
        source = FirmwareSource.FileNameOnly("rom.zip"),
        packageType = FirmwarePackageType.ZIP_OTA,
        androidVersion = AndroidVersionHint(13),
        capabilities = caps,
        partitions = emptyList(),
        compression = emptySet(),
        warnings = emptyList()
    )

    private fun device(hasRoot: Boolean? = true): DeviceProfile = DeviceProfile(
        sdkInt = 33,
        manufacturer = "Google",
        model = "Pixel 7",
        abiList = listOf("arm64-v8a"),
        isEmulator = false,
        supports16KbPageSize = false,
        hasRoot = hasRoot,
        selinuxMode = SelinuxMode.ENFORCING
    )

    private val manifest = listOf(
        ToolDescriptor("busybox", "1.36", listOf("arm64-v8a"), null, listOf(ToolPurpose.BUSYBOX), "x", "y", true),
        ToolDescriptor("magiskboot", "27", listOf("arm64-v8a"), null, listOf(ToolPurpose.BOOT_IMAGE), "x", "y", true),
        ToolDescriptor("mke2fs", "1.46", listOf("arm64-v8a"), null, listOf(ToolPurpose.EXT4), "x", "y", true),
        ToolDescriptor("e2fsdroid", "1.46", listOf("arm64-v8a"), null, listOf(ToolPurpose.EXT4), "x", "y", true)
    )

    @Test
    fun `unpack boot image is read-only and safe`() {
        val planner = OperationPlanner(CapabilityBasedToolchainResolver(manifest))
        val plan = planner.plan(
            FirmwareOperation.UNPACK_BOOT_IMAGE,
            firmware(),
            device()
        )
        assertTrue(plan.safety.isSafe)
        assertFalse(plan.safety.requiresRoot)
        assertEquals(ConfirmationLevel.NONE, plan.safety.confirmationLevel)
        assertTrue(plan.canExecute)
    }

    @Test
    fun `pack boot image is medium risk`() {
        val planner = OperationPlanner(CapabilityBasedToolchainResolver(manifest))
        val plan = planner.plan(
            FirmwareOperation.PACK_BOOT_IMAGE,
            firmware(),
            device()
        )
        assertEquals(ConfirmationLevel.STANDARD, plan.safety.confirmationLevel)
        assertFalse(plan.safety.isDestructive)
    }

    @Test
    fun `flash operation is destructive and requires root`() {
        val planner = OperationPlanner(CapabilityBasedToolchainResolver(manifest))
        val plan = planner.plan(
            FirmwareOperation.FLASH_PREPARE,
            firmware(),
            device()
        )
        assertTrue(plan.safety.isDestructive)
        assertTrue(plan.safety.requiresRoot)
        assertEquals(ConfirmationLevel.DESTRUCTIVE, plan.safety.confirmationLevel)
    }

    @Test
    fun `flash operation blocked when root missing`() {
        val planner = OperationPlanner(CapabilityBasedToolchainResolver(manifest))
        val plan = planner.plan(
            FirmwareOperation.FLASH_PREPARE,
            firmware(),
            device(hasRoot = false)
        )
        assertFalse(plan.canExecute)
        assertTrue(plan.blockers().any { it.contains("root") })
    }

    @Test
    fun `operation blocked when required tools missing`() {
        val planner = OperationPlanner(CapabilityBasedToolchainResolver(emptyList()))
        val plan = planner.plan(
            FirmwareOperation.UNPACK_BOOT_IMAGE,
            firmware(),
            device()
        )
        assertFalse(plan.canExecute)
        assertTrue(plan.blockers().any { it.contains("magiskboot") })
    }

    @Test
    fun `read-only safety profile helper sets all read-only fields`() {
        val profile = OperationSafetyProfile.readOnly(FirmwareOperation.INSPECT)
        assertFalse(profile.isDestructive)
        assertFalse(profile.requiresBackup)
        assertFalse(profile.requiresRoot)
        assertFalse(profile.requiresDeviceConnection)
        assertTrue(profile.supportsDryRun)
        assertEquals(ConfirmationLevel.NONE, profile.confirmationLevel)
    }

    @Test
    fun `pack operation safety profile sets standard confirmation`() {
        val profile = OperationSafetyProfile.packOperation(FirmwareOperation.PACK_SUPER)
        assertEquals(ConfirmationLevel.STANDARD, profile.confirmationLevel)
        assertFalse(profile.isDestructive)
        assertTrue(profile.supportsDryRun)
    }

    @Test
    fun `flash operation safety profile sets destructive confirmation`() {
        val profile = OperationSafetyProfile.flashOperation(FirmwareOperation.FLASH_PREPARE)
        assertEquals(ConfirmationLevel.DESTRUCTIVE, profile.confirmationLevel)
        assertTrue(profile.isDestructive)
        assertTrue(profile.requiresBackup)
        assertTrue(profile.requiresRoot)
        assertTrue(profile.requiresDeviceConnection)
    }

    @Test
    fun `riskLabel reflects confirmation level`() {
        assertEquals("safe", OperationSafetyProfile.readOnly(FirmwareOperation.INSPECT).riskLabel)
        assertEquals("low risk", OperationSafetyProfile.packOperation(FirmwareOperation.PACK_SUPER).riskLabel)
        assertEquals("high risk", OperationSafetyProfile.flashOperation(FirmwareOperation.FLASH_PREPARE).riskLabel)
    }
}
