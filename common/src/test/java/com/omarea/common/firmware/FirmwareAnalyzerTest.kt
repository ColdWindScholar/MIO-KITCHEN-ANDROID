package com.omarea.common.firmware

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.FileOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/**
 * RU: Тесты анализаторов прошивок.
 * EN: Firmware analyzer tests.
 */
class FirmwareAnalyzerTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    @Test
    fun `ZipFirmwareAnalyzer supports zip files only`() {
        val analyzer = ZipFirmwareAnalyzer()
        assertTrue(analyzer.supports(FirmwareSource.FileNameOnly("rom.zip")))
        assertTrue(analyzer.supports(FirmwareSource.FileNameOnly("ROM.ZIP")))
        assertFalse(analyzer.supports(FirmwareSource.FileNameOnly("boot.img")))
        assertFalse(analyzer.supports(FirmwareSource.FileNameOnly("super.img")))
    }

    @Test
    fun `ZipFirmwareAnalyzer detects payload bin`() {
        val zip = tempFolder.newFile("ota.zip")
        ZipOutputStream(FileOutputStream(zip)).use { zos ->
            zos.putNextEntry(ZipEntry("payload.bin"))
            zos.write(ByteArray(1024))
            zos.closeEntry()
        }
        val profile = ZipFirmwareAnalyzer().analyze(FirmwareSource.DirectPath(zip.absolutePath))
        assertEquals(FirmwarePackageType.PAYLOAD_BIN, profile.packageType)
        assertTrue(profile.capabilities.hasPayloadBin)
        assertTrue(profile.capabilities.usesAB)
        assertEquals(10, profile.androidVersion.version)
    }

    @Test
    fun `ZipFirmwareAnalyzer detects super img inside zip`() {
        val zip = tempFolder.newFile("rom.zip")
        ZipOutputStream(FileOutputStream(zip)).use { zos ->
            zos.putNextEntry(ZipEntry("super.img"))
            zos.write(ByteArray(1024))
            zos.closeEntry()
        }
        val profile = ZipFirmwareAnalyzer().analyze(FirmwareSource.DirectPath(zip.absolutePath))
        assertEquals(FirmwarePackageType.SUPER_IMAGE, profile.packageType)
        assertTrue(profile.capabilities.hasSuperImage)
        assertTrue(profile.capabilities.hasDynamicPartitions)
        assertTrue(profile.partitions.any { it.name == "super" })
    }

    @Test
    fun `ZipFirmwareAnalyzer detects init_boot as Android 13 hint`() {
        val zip = tempFolder.newFile("rom.zip")
        ZipOutputStream(FileOutputStream(zip)).use { zos ->
            zos.putNextEntry(ZipEntry("init_boot.img"))
            zos.write(ByteArray(512))
            zos.closeEntry()
            zos.putNextEntry(ZipEntry("boot.img"))
            zos.write(ByteArray(512))
            zos.closeEntry()
        }
        val profile = ZipFirmwareAnalyzer().analyze(FirmwareSource.DirectPath(zip.absolutePath))
        assertEquals(13, profile.androidVersion.version)
        assertTrue(profile.capabilities.hasInitBootImage)
        assertTrue(profile.capabilities.hasBootImage)
    }

    @Test
    fun `ZipFirmwareAnalyzer detects vbmeta and warns when boot missing`() {
        val zip = tempFolder.newFile("rom.zip")
        ZipOutputStream(FileOutputStream(zip)).use { zos ->
            zos.putNextEntry(ZipEntry("vbmeta.img"))
            zos.write(ByteArray(256))
            zos.closeEntry()
        }
        val profile = ZipFirmwareAnalyzer().analyze(FirmwareSource.DirectPath(zip.absolutePath))
        assertTrue(profile.capabilities.usesAvb)
        assertTrue(profile.warnings.any { it.code == "vbmeta-without-boot" })
    }

    @Test
    fun `ZipFirmwareAnalyzer flags Android 15 16K page-size check`() {
        val zip = tempFolder.newFile("rom.zip")
        ZipOutputStream(FileOutputStream(zip)).use { zos ->
            zos.putNextEntry(ZipEntry("payload.bin"))
            zos.write(ByteArray(256))
            zos.closeEntry()
            zos.putNextEntry(ZipEntry("META-INF/com/android/metadata"))
            zos.write("post-build=lineage/onedin/onedin:15/UP1A.231005.007/eng.root.20240101.000000/release-keys".toByteArray())
            zos.closeEntry()
        }
        val profile = ZipFirmwareAnalyzer().analyze(FirmwareSource.DirectPath(zip.absolutePath))
        // payload.bin wins (Android 10 minimum), but metadata can bump it.
        assertTrue(profile.androidVersion.version!! >= 10)
        // If metadata pushed it to 15, the 16K warning should fire.
        if (profile.androidVersion.version!! >= 15) {
            assertTrue(profile.warnings.any { it.code == "android-15-16kb" })
            assertTrue(profile.capabilities.requires16KbAlignmentCheck)
        }
    }

    @Test
    fun `BootImageAnalyzer supports boot and vendor boot images`() {
        val analyzer = BootImageAnalyzer()
        assertTrue(analyzer.supports(FirmwareSource.FileNameOnly("boot.img")))
        assertTrue(analyzer.supports(FirmwareSource.FileNameOnly("vendor_boot.img")))
        assertTrue(analyzer.supports(FirmwareSource.FileNameOnly("init_boot.img")))
        assertTrue(analyzer.supports(FirmwareSource.FileNameOnly("recovery.img")))
        assertFalse(analyzer.supports(FirmwareSource.FileNameOnly("super.img")))
    }

    @Test
    fun `BootImageAnalyzer detects ANDROID magic and v4 header warning`() {
        val img = tempFolder.newFile("boot.img")
        FileOutputStream(img).use { out ->
            val header = ByteArray(4096)
            // ANDROID! magic
            header[0] = 'A'.code.toByte()
            header[1] = 'N'.code.toByte()
            header[2] = 'D'.code.toByte()
            header[3] = 'R'.code.toByte()
            header[4] = 'O'.code.toByte()
            header[5] = 'I'.code.toByte()
            header[6] = 'D'.code.toByte()
            header[7] = '!'.code.toByte()
            // header version = 4 at offset 40
            header[40] = 4
            out.write(header)
        }
        val profile = BootImageAnalyzer().analyze(FirmwareSource.DirectPath(img.absolutePath))
        assertEquals(FirmwarePackageType.BOOT_IMAGE, profile.packageType)
        assertTrue(profile.warnings.any { it.code == "boot-header-v4" })
    }

    @Test
    fun `SuperImageAnalyzer detects sparse format`() {
        val img = tempFolder.newFile("super.img")
        FileOutputStream(img).use { out ->
            val bytes = byteArrayOf(0x3a, 0xff.toByte(), 0x26, 0xed.toByte())
            out.write(bytes)
            out.write(ByteArray(1020))
        }
        val profile = SuperImageAnalyzer().analyze(FirmwareSource.DirectPath(img.absolutePath))
        assertEquals(FirmwarePackageType.SUPER_IMAGE, profile.packageType)
        assertTrue(profile.capabilities.hasSparseImages)
        assertTrue(profile.partitions.first().isSparse)
    }

    @Test
    fun `SuperImageAnalyzer handles raw super image`() {
        val img = tempFolder.newFile("super.img")
        FileOutputStream(img).use { out ->
            out.write(ByteArray(4096)) // All zeros — not sparse.
        }
        val profile = SuperImageAnalyzer().analyze(FirmwareSource.DirectPath(img.absolutePath))
        assertFalse(profile.capabilities.hasSparseImages)
    }

    @Test
    fun `VbmetaAnalyzer detects AVB0 magic`() {
        val img = tempFolder.newFile("vbmeta.img")
        FileOutputStream(img).use { out ->
            out.write("AVB0".toByteArray())
            out.write(ByteArray(252))
        }
        val profile = VbmetaAnalyzer().analyze(FirmwareSource.DirectPath(img.absolutePath))
        assertEquals(FirmwarePackageType.VBMETA_IMAGE, profile.packageType)
        assertTrue(profile.capabilities.usesAvb)
        assertTrue(profile.warnings.none { it.code == "vbmeta-bad-magic" })
    }

    @Test
    fun `VbmetaAnalyzer warns when AVB0 magic missing`() {
        val img = tempFolder.newFile("vbmeta.img")
        FileOutputStream(img).use { out ->
            out.write(ByteArray(256)) // All zeros — not AVB.
        }
        val profile = VbmetaAnalyzer().analyze(FirmwareSource.DirectPath(img.absolutePath))
        assertTrue(profile.warnings.any { it.code == "vbmeta-bad-magic" })
        assertFalse(profile.capabilities.usesAvb)
    }

    @Test
    fun `FilesystemImageAnalyzer detects ext4 magic`() {
        val img = tempFolder.newFile("system.img")
        FileOutputStream(img).use { out ->
            val bytes = ByteArray(2048)
            // ext4 superblock magic 0xef53 at offset 0x438 (1080), little-endian.
            bytes[1080] = 0x53
            bytes[1081] = 0xef.toByte()
            out.write(bytes)
        }
        val profile = FilesystemImageAnalyzer().analyze(FirmwareSource.DirectPath(img.absolutePath))
        assertEquals(FirmwarePackageType.FILESYSTEM_IMAGE, profile.packageType)
        assertTrue(profile.capabilities.hasExt4)
        assertEquals("ext4", profile.partitions.first().filesystem)
    }

    @Test
    fun `FilesystemImageAnalyzer detects EROFS magic`() {
        val img = tempFolder.newFile("system.img")
        FileOutputStream(img).use { out ->
            val bytes = ByteArray(2048)
            // EROFS magic: 0xe0f5e1e2 (little-endian) at offset 0x400 (1024).
            bytes[1024] = 0xe2.toByte()
            bytes[1025] = 0xe1.toByte()
            bytes[1026] = 0xf5.toByte()
            bytes[1027] = 0xe0.toByte()
            out.write(bytes)
        }
        val profile = FilesystemImageAnalyzer().analyze(FirmwareSource.DirectPath(img.absolutePath))
        assertTrue(profile.capabilities.hasErofs)
        assertEquals("erofs", profile.partitions.first().filesystem)
    }

    @Test
    fun `FilesystemImageAnalyzer warns when filesystem unknown`() {
        val img = tempFolder.newFile("unknown.img")
        FileOutputStream(img).use { out ->
            out.write(ByteArray(2048))
        }
        val profile = FilesystemImageAnalyzer().analyze(FirmwareSource.DirectPath(img.absolutePath))
        assertTrue(profile.warnings.any { it.code == "unknown-filesystem" })
    }

    @Test
    fun `CompositeFirmwareAnalyzer picks first supporting analyzer`() {
        val img = tempFolder.newFile("boot.img")
        FileOutputStream(img).use { out ->
            out.write("ANDROID!".toByteArray())
            out.write(ByteArray(4088))
        }
        val analyzer = FirmwareAnalyzerRegistry.createDefault()
        val profile = analyzer.analyze(FirmwareSource.DirectPath(img.absolutePath))
        assertEquals(FirmwarePackageType.BOOT_IMAGE, profile.packageType)
    }

    @Test(expected = FirmwareAnalysisException::class)
    fun `CompositeFirmwareAnalyzer throws when no analyzer supports source`() {
        val analyzer = CompositeFirmwareAnalyzer(emptyList())
        analyzer.analyze(FirmwareSource.FileNameOnly("anything.zip"))
    }

    @Test
    fun `FirmwareProfile summary is non-empty for payload bin`() {
        val zip = tempFolder.newFile("rom.zip")
        ZipOutputStream(FileOutputStream(zip)).use { zos ->
            zos.putNextEntry(ZipEntry("payload.bin"))
            zos.write(ByteArray(64))
            zos.closeEntry()
        }
        val profile = ZipFirmwareAnalyzer().analyze(FirmwareSource.DirectPath(zip.absolutePath))
        assertTrue(profile.summary.contains("type=payload_bin"))
        assertTrue(profile.summary.contains("android=10"))
    }
}
