package com.mio.kitchen.ui.modern

import com.omarea.common.firmware.AndroidVersionHint
import com.omarea.common.firmware.FirmwareCapabilities
import com.omarea.common.firmware.FirmwarePackageType
import com.omarea.common.firmware.FirmwareProfile
import com.omarea.common.firmware.FirmwareSource
import com.omarea.common.firmware.FirmwareWarning
import com.omarea.common.firmware.PartitionInfo
import com.omarea.common.ui.UiState
import com.omarea.common.ui.UiStateHolder
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * RU: Тесты для [FirmwareProfileFormatter] и [UiStateHolder].
 * EN: Tests for [FirmwareProfileFormatter] and [UiStateHolder].
 */
class FirmwareProfileFormatterTest {

    @Test
    fun `short summary contains type and android version`() {
        val profile = FirmwareProfile(
            source = FirmwareSource.FileNameOnly("rom.zip"),
            packageType = FirmwarePackageType.PAYLOAD_BIN,
            androidVersion = AndroidVersionHint(13),
            capabilities = FirmwareCapabilities(hasPayloadBin = true, usesAB = true),
            partitions = emptyList(),
            compression = emptySet(),
            warnings = emptyList()
        )
        val s = FirmwareProfileFormatter.short(profile)
        assertTrue(s.contains("type=payload_bin"))
        assertTrue(s.contains("android=13"))
    }

    @Test
    fun `detailed output lists capabilities`() {
        val profile = FirmwareProfile(
            source = FirmwareSource.FileNameOnly("rom.zip"),
            packageType = FirmwarePackageType.ZIP_OTA,
            androidVersion = AndroidVersionHint(14),
            capabilities = FirmwareCapabilities(
                hasErofs = true,
                hasExt4 = false,
                usesAvb = true,
                usesVirtualAB = true,
                requires16KbAlignmentCheck = true
            ),
            partitions = listOf(
                PartitionInfo(name = "system", sizeBytes = 1024L * 1024L * 1024L, filesystem = "erofs")
            ),
            compression = emptySet(),
            warnings = listOf(
                FirmwareWarning("test-warn", "test message", FirmwareWarning.Severity.INFO)
            )
        )
        val out = FirmwareProfileFormatter.detailed(profile)
        assertTrue(out.contains("EROFS"))
        assertTrue(out.contains("AVB"))
        assertTrue(out.contains("Virtual A/B"))
        assertTrue(out.contains("16 KB page-size check"))
        assertTrue(out.contains("system"))
        assertTrue(out.contains("erofs"))
        assertTrue(out.contains("1.0 GB"))
        assertTrue(out.contains("[INFO]"))
        assertTrue(out.contains("test-warn"))
    }

    @Test
    fun `detailed output handles no capabilities gracefully`() {
        val profile = FirmwareProfile(
            source = FirmwareSource.FileNameOnly("empty.img"),
            packageType = FirmwarePackageType.UNKNOWN,
            androidVersion = AndroidVersionHint(null),
            capabilities = FirmwareCapabilities(),
            partitions = emptyList(),
            compression = emptySet(),
            warnings = emptyList()
        )
        val out = FirmwareProfileFormatter.detailed(profile)
        assertTrue(out.contains("(none)"))
        assertTrue(out.contains("unknown"))
    }
}

class UiStateHolderTest {

    @Test
    fun `starts in Idle`() {
        val holder = UiStateHolder<String>()
        assertEquals(UiState.Idle, holder.value)
    }

    @Test
    fun `setLoading transitions to Loading`() {
        val holder = UiStateHolder<String>()
        holder.setLoading("working")
        assertEquals(UiState.Loading("working"), holder.value)
    }

    @Test
    fun `setSuccess transitions to Success with data`() {
        val holder = UiStateHolder<String>()
        holder.setSuccess("done")
        assertEquals(UiState.Success("done"), holder.value)
    }

    @Test
    fun `setError transitions to Error with message`() {
        val holder = UiStateHolder<String>()
        holder.setError("boom")
        assertTrue(holder.value is UiState.Error)
        assertEquals("boom", (holder.value as UiState.Error).message)
    }

    @Test
    fun `setIdle resets to Idle`() {
        val holder = UiStateHolder<String>()
        holder.setSuccess("done")
        holder.setIdle()
        assertEquals(UiState.Idle, holder.value)
    }
}
