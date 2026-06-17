package com.omarea.common.firmware

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.io.FileOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/**
 * RU: Instrumented-тест для [ZipFirmwareAnalyzer].
 *
 * Создаёт настоящий zip-файл с payload.bin во внешнем кэше устройства и
 * проверяет, что анализатор корректно его разбирает.
 *
 * EN: Instrumented test for [ZipFirmwareAnalyzer].
 *
 * Creates a real zip file with a payload.bin in the device external cache and
 * verifies that the analyzer parses it correctly.
 */
@RunWith(AndroidJUnit4::class)
class ZipFirmwareAnalyzerInstrumentedTest {

    @Test
    fun detectsPayloadBinOnDevice() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val zipFile = File(context.externalCacheDir, "test_ota.zip")
        ZipOutputStream(FileOutputStream(zipFile)).use { zos ->
            zos.putNextEntry(ZipEntry("payload.bin"))
            zos.write(ByteArray(1024))
            zos.closeEntry()
            zos.putNextEntry(ZipEntry("boot.img"))
            zos.write("ANDROID!".toByteArray())
            zos.write(ByteArray(4088))
            zos.closeEntry()
        }
        val analyzer = ZipFirmwareAnalyzer()
        val profile = analyzer.analyze(FirmwareSource.DirectPath(zipFile.absolutePath))
        assertEquals(FirmwarePackageType.PAYLOAD_BIN, profile.packageType)
        assertTrue(profile.capabilities.hasPayloadBin)
        assertTrue(profile.capabilities.usesAB)
        assertEquals(10, profile.androidVersion.version)
        assertTrue(profile.capabilities.hasBootImage)
        zipFile.delete()
    }
}
