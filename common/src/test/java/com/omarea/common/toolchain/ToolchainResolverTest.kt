package com.omarea.common.toolchain

import com.omarea.common.firmware.AndroidVersionHint
import com.omarea.common.firmware.FirmwareCapabilities
import com.omarea.common.firmware.FirmwarePackageType
import com.omarea.common.firmware.FirmwareProfile
import com.omarea.common.firmware.FirmwareSource
import com.omarea.common.runtime.DeviceProfile
import com.omarea.common.runtime.SelinuxMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * RU: Тесты для [CapabilityBasedToolchainResolver] и [ToolManifestLoader].
 * EN: Tests for [CapabilityBasedToolchainResolver] and [ToolManifestLoader].
 */
class ToolchainResolverTest {

    private fun firmware(
        caps: FirmwareCapabilities = FirmwareCapabilities(),
        packageType: FirmwarePackageType = FirmwarePackageType.ZIP_OTA,
        androidVersion: Int? = 13
    ): FirmwareProfile = FirmwareProfile(
        source = FirmwareSource.FileNameOnly("rom.zip"),
        packageType = packageType,
        androidVersion = AndroidVersionHint(androidVersion),
        capabilities = caps,
        partitions = emptyList(),
        compression = emptySet(),
        warnings = emptyList()
    )

    private fun device(
        sdkInt: Int = 33,
        abiList: List<String> = listOf("arm64-v8a", "armeabi-v7a"),
        hasRoot: Boolean? = false,
        supports16Kb: Boolean? = false
    ): DeviceProfile = DeviceProfile(
        sdkInt = sdkInt,
        manufacturer = "Google",
        model = "Pixel 7",
        abiList = abiList,
        isEmulator = false,
        supports16KbPageSize = supports16Kb,
        hasRoot = hasRoot,
        selinuxMode = SelinuxMode.ENFORCING
    )

    private val fullManifest = listOf(
        ToolDescriptor("busybox", "1.36", listOf("arm64-v8a", "armeabi-v7a"), null, listOf(ToolPurpose.BUSYBOX), "busybox", "GPL-2.0-or-later", true),
        ToolDescriptor("lpunpack", "r34", listOf("arm64-v8a", "armeabi-v7a"), null, listOf(ToolPurpose.SUPER_IMAGE), "AOSP", "Apache-2.0", true),
        ToolDescriptor("lpmake", "r34", listOf("arm64-v8a", "armeabi-v7a"), null, listOf(ToolPurpose.SUPER_IMAGE), "AOSP", "Apache-2.0", true),
        ToolDescriptor("magiskboot", "27", listOf("arm64-v8a", "armeabi-v7a"), null, listOf(ToolPurpose.BOOT_IMAGE), "magisk", "GPL-3.0-or-later", true),
        ToolDescriptor("mke2fs", "1.46", listOf("arm64-v8a", "armeabi-v7a"), null, listOf(ToolPurpose.EXT4), "e2fsprogs", "GPL-2.0-or-later", true),
        ToolDescriptor("mkfs.erofs", "1.7", listOf("arm64-v8a"), null, listOf(ToolPurpose.EROFS), "erofs-utils", "GPL-2.0-or-later", true),
        ToolDescriptor("avbtool", "r34", listOf("arm64-v8a", "armeabi-v7a"), null, listOf(ToolPurpose.VBMETA), "AOSP", "Apache-2.0", true),
        ToolDescriptor("brotli", "1.1", listOf("arm64-v8a", "armeabi-v7a"), null, listOf(ToolPurpose.COMPRESSION_BROTLI), "brotli", "MIT", true),
        ToolDescriptor("payload-dumper", "1.0", listOf("arm64-v8a"), null, listOf(ToolPurpose.PAYLOAD_BIN), "AOSP", "Apache-2.0", true)
    )

    @Test
    fun `resolve unpack_rom requires busybox and capability-specific tools`() {
        val resolver = CapabilityBasedToolchainResolver(fullManifest)
        val fw = firmware(
            caps = FirmwareCapabilities(
                hasPayloadBin = true,
                hasSuperImage = true,
                hasBootImage = true,
                usesAvb = true
            )
        )
        val plan = resolver.resolve(FirmwareOperation.UNPACK_ROM, fw, device())
        assertTrue(plan.canExecute)
        assertTrue(plan.profile.requiredTools.any { it.name == "busybox" })
        assertTrue(plan.profile.requiredTools.any { it.name == "lpunpack" })
        assertTrue(plan.profile.requiredTools.any { it.name == "magiskboot" })
        assertTrue(plan.profile.requiredTools.any { it.name == "payload-dumper" })
        assertTrue(plan.profile.optionalTools.any { it.name == "avbtool" })
    }

    @Test
    fun `resolve reports missing required tools when manifest is empty`() {
        val resolver = CapabilityBasedToolchainResolver(emptyList())
        val fw = firmware(caps = FirmwareCapabilities(hasSuperImage = true))
        val plan = resolver.resolve(FirmwareOperation.UNPACK_SUPER, fw, device())
        assertFalse(plan.canExecute)
        assertTrue(plan.missingRequired.contains("lpunpack"))
    }

    @Test
    fun `resolve selects first compatible device ABI`() {
        val resolver = CapabilityBasedToolchainResolver(fullManifest)
        val fw = firmware(caps = FirmwareCapabilities(hasBootImage = true))
        val dev = device(abiList = listOf("arm64-v8a"))
        val plan = resolver.resolve(FirmwareOperation.UNPACK_BOOT_IMAGE, fw, dev)
        assertEquals("arm64-v8a", plan.profile.selectedAbi)
    }

    @Test
    fun `resolve warns when no common ABI exists`() {
        val resolver = CapabilityBasedToolchainResolver(fullManifest)
        val fw = firmware(caps = FirmwareCapabilities(hasBootImage = true))
        // device has only x86 ABI but magiskboot is arm-only in our manifest
        val dev = device(abiList = listOf("x86"))
        val plan = resolver.resolve(FirmwareOperation.UNPACK_BOOT_IMAGE, fw, dev)
        // arm-only tools can't run on x86 device
        assertTrue(plan.warnings.any { it.code == CapabilityBasedToolchainResolver.CODE_NO_COMPATIBLE_ABI } ||
            plan.profile.selectedAbi.isEmpty())
    }

    @Test
    fun `resolve emits 16KB warning when device requires 16K and tool lacks support`() {
        val manifest = listOf(
            ToolDescriptor("busybox", "1.36", listOf("arm64-v8a"), null, listOf(ToolPurpose.BUSYBOX), "busybox", "GPL-2.0-or-later", false)
        )
        val resolver = CapabilityBasedToolchainResolver(manifest)
        val fw = firmware()
        val dev = device(sdkInt = 35, supports16Kb = true)
        val plan = resolver.resolve(FirmwareOperation.UNPACK_ROM, fw, dev)
        assertTrue(plan.warnings.any { it.code == CapabilityBasedToolchainResolver.CODE_16KB_INCOMPATIBLE })
    }

    @Test
    fun `resolve emits compression warning for missing zstd tool`() {
        val resolver = CapabilityBasedToolchainResolver(emptyList())
        val fw = firmware(caps = FirmwareCapabilities(usesCompressionZstd = true))
        val plan = resolver.resolve(FirmwareOperation.UNPACK_ROM, fw, device())
        assertTrue(plan.warnings.any { it.code == CapabilityBasedToolchainResolver.CODE_MISSING_COMPRESSION_TOOL })
    }

    @Test
    fun `pack filesystem requires mkfs erofs for erofs firmware`() {
        val resolver = CapabilityBasedToolchainResolver(fullManifest)
        val fw = firmware(caps = FirmwareCapabilities(hasErofs = true))
        val plan = resolver.resolve(FirmwareOperation.PACK_FILESYSTEM_IMAGE, fw, device())
        assertTrue(plan.profile.requiredTools.any { it.name == "mkfs.erofs" })
    }

    @Test
    fun `pack filesystem requires mke2fs and e2fsdroid for ext4 firmware`() {
        val resolver = CapabilityBasedToolchainResolver(fullManifest)
        val fw = firmware(caps = FirmwareCapabilities(hasExt4 = true))
        val plan = resolver.resolve(FirmwareOperation.PACK_FILESYSTEM_IMAGE, fw, device())
        assertTrue(plan.profile.requiredTools.any { it.name == "mke2fs" })
        assertTrue(plan.profile.requiredTools.any { it.name == "e2fsdroid" })
    }

    @Test
    fun `verify vbmeta requires avbtool`() {
        val resolver = CapabilityBasedToolchainResolver(fullManifest)
        val fw = firmware(caps = FirmwareCapabilities(usesAvb = true))
        val plan = resolver.resolve(FirmwareOperation.VERIFY_VBMETA, fw, device())
        assertTrue(plan.profile.requiredTools.any { it.name == "avbtool" })
        assertTrue(plan.canExecute)
    }

    @Test
    fun `ToolManifestLoader parses valid JSON`() {
        val json = """
            {
              "tools": [
                {
                  "name": "busybox",
                  "version": "1.36",
                  "abi": ["arm64-v8a", "armeabi-v7a"],
                  "sha256": "abc",
                  "capabilities": ["busybox"],
                  "source": "busybox",
                  "license": "GPL-2.0-or-later",
                  "supports16KbPageSize": true
                },
                {
                  "name": "lpunpack",
                  "version": "r34",
                  "abi": ["arm64-v8a"],
                  "capabilities": ["super_image"],
                  "source": "AOSP",
                  "license": "Apache-2.0"
                }
              ]
            }
        """.trimIndent()
        val descriptors = ToolManifestLoader().parse(json)
        assertEquals(2, descriptors.size)
        val busybox = descriptors[0]
        assertEquals("busybox", busybox.name)
        assertEquals("1.36", busybox.version)
        assertEquals(listOf("arm64-v8a", "armeabi-v7a"), busybox.abi)
        assertEquals("abc", busybox.sha256)
        assertEquals(listOf(ToolPurpose.BUSYBOX), busybox.capabilities)
        assertEquals(true, busybox.supports16KbPageSize)
        val lpunpack = descriptors[1]
        assertEquals(null, lpunpack.sha256)
        assertEquals(null, lpunpack.supports16KbPageSize)
    }

    @Test
    fun `ToolManifestLoader handles empty input`() {
        val descriptors = ToolManifestLoader().parse("")
        assertTrue(descriptors.isEmpty())
    }

    @Test
    fun `ToolManifestLoader maps unknown capability to GENERIC`() {
        val json = """
            {
              "tools": [
                {
                  "name": "weird",
                  "version": "1.0",
                  "abi": [],
                  "capabilities": ["definitely_unknown"],
                  "source": "x",
                  "license": "y"
                }
              ]
            }
        """.trimIndent()
        val descriptors = ToolManifestLoader().parse(json)
        assertEquals(1, descriptors.size)
        assertEquals(ToolPurpose.GENERIC, descriptors[0].capabilities[0])
    }

    @Test
    fun `ToolRequirement rejects empty name`() {
        try {
            ToolRequirement(name = "", purpose = ToolPurpose.GENERIC)
            org.junit.Assert.fail("Expected IllegalArgumentException for empty name")
        } catch (_: IllegalArgumentException) {
            // expected
        }
    }
}
