package com.omarea.common.storage

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/**
 * RU: Instrumented-тест для [FirmwareWorkspace].
 *
 * Проверяет, что workspace-директории создаются в правильном месте и
 * `prepareImportFile`/`prepareExportFile` работают на реальном Android.
 *
 * EN: Instrumented test for [FirmwareWorkspace].
 *
 * Verifies that the workspace directories are created in the right place and
 * that `prepareImportFile`/`prepareExportFile` work on a real Android device.
 */
@RunWith(AndroidJUnit4::class)
class FirmwareWorkspaceInstrumentedTest {

    @Test
    fun workspaceDirectoriesAreCreated() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val workspace = FirmwareWorkspace(context)
        assertTrue("rootDir must exist", workspace.rootDir.exists())
        assertTrue("importsDir must exist", workspace.importsDir.exists())
        assertTrue("exportsDir must exist", workspace.exportsDir.exists())
        assertTrue(
            "rootDir must be inside external files dir",
            workspace.rootDir.absolutePath.contains("firmware-workspace")
        )
    }

    @Test
    fun prepareImportFileReturnsUniqueFiles() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val workspace = FirmwareWorkspace(context)
        val f1 = workspace.prepareImportFile("rom.zip")
        val f2 = workspace.prepareImportFile("rom.zip")
        assertEquals("imports/", f1.parentFile?.name)
        assertEquals("imports/", f2.parentFile?.name)
        assertTrue("names must be unique", f1.name != f2.name)
    }

    @Test
    fun prepareExportFileUsesDefaultName() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val workspace = FirmwareWorkspace(context)
        val f = workspace.prepareExportFile(null)
        assertNotNull(f)
        assertTrue("exports/", f.parentFile?.name == "exports")
        assertTrue("default name", f.name.startsWith("firmware-output"))
    }

    @Test
    fun clearOldImportsDeletesOldFiles() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val workspace = FirmwareWorkspace(context)
        val oldFile = File(workspace.importsDir, "old_file.bin")
        oldFile.writeText("old")
        oldFile.setLastModified(System.currentTimeMillis() - 8L * 24L * 60L * 60L * 1000L)
        val deleted = workspace.clearOldImports()
        assertTrue("at least one old file deleted", deleted >= 1)
        assertTrue("old file should be gone", !oldFile.exists())
    }
}
