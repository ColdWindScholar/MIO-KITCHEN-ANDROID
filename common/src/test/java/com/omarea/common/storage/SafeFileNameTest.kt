package com.omarea.common.storage

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SafeFileNameTest {
    @Test
    fun cleanKeepsSimpleFirmwareName() {
        assertEquals("firmware.zip", SafeFileName.clean("firmware.zip"))
    }

    @Test
    fun cleanRemovesPathSegmentsAndUnsafeCharacters() {
        assertEquals("payload_bin", SafeFileName.clean("/tmp/payload bin"))
        assertEquals("rom.zip", SafeFileName.clean("C:\\Downloads\\rom.zip"))
    }

    @Test
    fun cleanUsesDefaultForEmptyInput() {
        assertEquals("selected-file.bin", SafeFileName.clean(""))
        assertEquals("selected-file.bin", SafeFileName.clean(null))
    }

    @Test
    fun cleanKeepsExtensionWhenTrimmingLongNames() {
        val longName = "a".repeat(240) + ".zip"
        val cleaned = SafeFileName.clean(longName)
        assertTrue(cleaned.length <= 180)
        assertTrue(cleaned.endsWith(".zip"))
    }
}
