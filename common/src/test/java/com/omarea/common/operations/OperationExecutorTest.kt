package com.omarea.common.operations

import com.omarea.common.firmware.AndroidVersionHint
import com.omarea.common.firmware.FirmwareCapabilities
import com.omarea.common.firmware.FirmwarePackageType
import com.omarea.common.firmware.FirmwareProfile
import com.omarea.common.firmware.FirmwareSource
import com.omarea.common.runtime.DeviceProfile
import com.omarea.common.runtime.SelinuxMode
import com.omarea.common.shell.runtime.DryRunShellRuntime
import com.omarea.common.shell.runtime.FakeShellRuntime
import com.omarea.common.shell.runtime.ScriptSource
import com.omarea.common.shell.runtime.ShellEvent
import com.omarea.common.shell.runtime.ShellResult
import com.omarea.common.toolchain.CapabilityBasedToolchainResolver
import com.omarea.common.toolchain.FirmwareOperation
import com.omarea.common.toolchain.ToolDescriptor
import com.omarea.common.toolchain.ToolPurpose
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * RU: Тесты для [OperationExecutor].
 * EN: Tests for [OperationExecutor].
 */
class OperationExecutorTest {

    private fun firmware(
        caps: FirmwareCapabilities = FirmwareCapabilities(hasBootImage = true),
        source: FirmwareSource = FirmwareSource.DirectPath("/tmp/rom.zip")
    ): FirmwareProfile = FirmwareProfile(
        source = source,
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
        ToolDescriptor("magiskboot", "27", listOf("arm64-v8a"), null, listOf(ToolPurpose.BOOT_IMAGE), "x", "y", true)
    )

    private fun planner() = OperationPlanner(CapabilityBasedToolchainResolver(manifest))

    @Test
    fun `prepare builds command with operation env`() {
        val executor = OperationExecutor(DryRunShellRuntime())
        val plan = planner().plan(
            FirmwareOperation.UNPACK_BOOT_IMAGE,
            firmware(),
            device()
        )
        val prepared = executor.prepare(plan)
        assertEquals(FirmwareOperation.UNPACK_BOOT_IMAGE, prepared.operation)
        assertTrue(prepared.command.env["OPERATION"] == "UNPACK_BOOT_IMAGE")
        assertTrue(prepared.command.env["FIRMWARE_PATH"] == "/tmp/rom.zip")
        assertTrue(prepared.command.env["HAS_BOOT_IMAGE"] == "1")
        assertNotNull(prepared.command.env["REQUIRED_TOOLS"])
    }

    @Test
    fun `prepare sets requiresRoot when safety requires it`() {
        val executor = OperationExecutor(DryRunShellRuntime())
        val plan = planner().plan(
            FirmwareOperation.FLASH_PREPARE,
            firmware(),
            device(hasRoot = true)
        )
        val prepared = executor.prepare(plan)
        assertTrue("Flash must require root", prepared.command.requiresRoot)
    }

    @Test
    fun `prepare with dryRun disables requiresRoot`() {
        val executor = OperationExecutor(DryRunShellRuntime())
        val plan = planner().plan(
            FirmwareOperation.FLASH_PREPARE,
            firmware(),
            device(hasRoot = true)
        )
        val prepared = executor.prepare(plan, dryRun = true)
        assertFalse("Dry-run must not require root", prepared.command.requiresRoot)
    }

    @Test
    fun `prepare throws when no script is registered for operation`() {
        val executor = OperationExecutor(
            shellRuntime = DryRunShellRuntime(),
            scriptLocator = { null }
        )
        val plan = planner().plan(
            FirmwareOperation.INSPECT,
            firmware(),
            device()
        )
        try {
            executor.prepare(plan)
            org.junit.Assert.fail("Expected OperationExecutionException")
        } catch (_: OperationExecutionException) {
            // expected
        }
    }

    @Test
    fun `execute returns shell events from runtime`() = runBlocking {
        val fake = FakeShellRuntime()
        fake.nextEvents = listOf(
            ShellEvent.Stdout("line1"),
            ShellEvent.Stdout("line2"),
            ShellEvent.Completed(0)
        )
        val executor = OperationExecutor(fake)
        val plan = planner().plan(
            FirmwareOperation.UNPACK_BOOT_IMAGE,
            firmware(),
            device()
        )
        val prepared = executor.prepare(plan)
        val events = executor.execute(prepared).toList()
        assertEquals(3, events.size)
        assertTrue(events[2] is ShellEvent.Completed)
    }

    @Test
    fun `executeForResult returns Completed on success`() = runBlocking {
        val fake = FakeShellRuntime()
        fake.nextResult = ShellResult.Completed(
            exitCode = 0,
            stdout = "ok",
            stderr = ""
        )
        val executor = OperationExecutor(fake)
        val plan = planner().plan(
            FirmwareOperation.UNPACK_BOOT_IMAGE,
            firmware(),
            device()
        )
        val prepared = executor.prepare(plan)
        val result = executor.executeForResult(prepared)
        assertTrue(result is ShellResult.Completed)
        assertEquals(0, (result as ShellResult.Completed).exitCode)
    }

    @Test
    fun `executeForResult returns Failed when plan is blocked`() = runBlocking {
        val executor = OperationExecutor(DryRunShellRuntime())
        // Plan with missing tools (empty manifest).
        val blockedPlan = OperationPlanner(CapabilityBasedToolchainResolver(emptyList()))
            .plan(FirmwareOperation.UNPACK_BOOT_IMAGE, firmware(), device())
        val prepared = executor.prepare(blockedPlan, dryRun = false)
        val result = executor.executeForResult(prepared)
        assertTrue(result is ShellResult.Failed)
    }

    @Test
    fun `prepared blockers reflect plan blockers when not dry-run`() {
        val executor = OperationExecutor(DryRunShellRuntime())
        val blockedPlan = OperationPlanner(CapabilityBasedToolchainResolver(emptyList()))
            .plan(FirmwareOperation.UNPACK_BOOT_IMAGE, firmware(), device())
        val prepared = executor.prepare(blockedPlan, dryRun = false)
        assertFalse(prepared.blockers().isEmpty())
        assertFalse(prepared.ready)
    }

    @Test
    fun `prepared blockers are empty in dry-run even if plan is blocked`() {
        val executor = OperationExecutor(DryRunShellRuntime())
        val blockedPlan = OperationPlanner(CapabilityBasedToolchainResolver(emptyList()))
            .plan(FirmwareOperation.UNPACK_BOOT_IMAGE, firmware(), device())
        val prepared = executor.prepare(blockedPlan, dryRun = true)
        assertTrue(prepared.blockers().isEmpty())
        assertTrue(prepared.ready)
    }

    @Test
    fun `env includes capabilities flags`() {
        val executor = OperationExecutor(DryRunShellRuntime())
        val fw = firmware(
            caps = FirmwareCapabilities(
                hasPayloadBin = true,
                hasSuperImage = true,
                hasDynamicPartitions = true,
                hasErofs = true,
                usesAvb = true,
                usesAB = true,
                requires16KbAlignmentCheck = true
            )
        )
        val plan = planner().plan(FirmwareOperation.UNPACK_ROM, fw, device())
        val prepared = executor.prepare(plan)
        val env = prepared.command.env
        assertEquals("1", env["HAS_PAYLOAD_BIN"])
        assertEquals("1", env["HAS_SUPER_IMAGE"])
        assertEquals("1", env["HAS_DYNAMIC_PARTITIONS"])
        assertEquals("1", env["HAS_EROFS"])
        assertEquals("1", env["USES_AVB"])
        assertEquals("1", env["USES_AB"])
        assertEquals("1", env["REQUIRES_16KB_CHECK"])
    }

    @Test
    fun `default script locator returns script2 path for all operations`() {
        for (op in FirmwareOperation.values()) {
            val path = OperationExecutor.defaultScriptLocator(op)
            assertNotNull("No script for $op", path)
            assertTrue("Script path must be script2/*: $path", path!!.startsWith("script2/"))
        }
    }
}
