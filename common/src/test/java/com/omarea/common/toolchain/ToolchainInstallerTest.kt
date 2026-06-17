package com.omarea.common.toolchain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.ByteArrayInputStream
import java.io.File

/**
 * RU: Тесты для [ToolchainInstaller].
 * EN: Tests for [ToolchainInstaller].
 */
class ToolchainInstallerTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private fun toolDescriptor(
        name: String,
        sha256: String? = null,
        version: String = "1.0"
    ): ToolDescriptor = ToolDescriptor(
        name = name,
        version = version,
        abi = listOf("arm64-v8a"),
        sha256 = sha256,
        capabilities = listOf(ToolPurpose.GENERIC),
        source = "test",
        license = "test",
        supports16KbPageSize = true
    )

    @Test
    fun `install copies tools into target directory`() {
        val manifest = listOf(
            toolDescriptor("tool_a"),
            toolDescriptor("tool_b"),
            toolDescriptor("tool_c")
        )
        val assets = mapOf(
            "tool_a" to "binary_a_content",
            "tool_b" to "binary_b_content",
            "tool_c" to "binary_c_content"
        )
        val installer = ToolchainInstaller(
            manifest = manifest,
            assetProvider = { name -> assets[name]?.let { ByteArrayInputStream(it.toByteArray()) } }
        )
        val target = tempFolder.newFolder("tools")
        val result = installer.install(target, verifyChecksums = false)
        assertTrue(result is ToolchainInstallResult.Success)
        val success = result as ToolchainInstallResult.Success
        assertEquals(3, success.installedTools.size)
        assertTrue(File(target, "tool_a").exists())
        assertTrue(File(target, "tool_b").exists())
        assertTrue(File(target, "tool_c").exists())
    }

    @Test
    fun `install skips missing assets`() {
        val manifest = listOf(
            toolDescriptor("present"),
            toolDescriptor("absent")
        )
        val assets = mapOf(
            "present" to "binary_content"
        )
        val installer = ToolchainInstaller(
            manifest = manifest,
            assetProvider = { name -> assets[name]?.let { ByteArrayInputStream(it.toByteArray()) } }
        )
        val target = tempFolder.newFolder("tools")
        val result = installer.install(target, verifyChecksums = false)
        assertTrue(result is ToolchainInstallResult.Success)
        val success = result as ToolchainInstallResult.Success
        assertEquals(1, success.installedTools.size)
        assertEquals(1, success.skippedTools.size)
        assertEquals("absent", success.skippedTools[0])
    }

    @Test
    fun `install verifies SHA-256 and rejects mismatch`() {
        // "hello" -> 2cf24dba5fb0a30e26e83b2ac5b9e29e1b161e5c1fa7425e73043362938b9824
        val content = "hello"
        val wrongSha = "0000000000000000000000000000000000000000000000000000000000000000"
        val manifest = listOf(
            toolDescriptor(name = "tool", sha256 = wrongSha)
        )
        val installer = ToolchainInstaller(
            manifest = manifest,
            assetProvider = { ByteArrayInputStream(content.toByteArray()) }
        )
        val target = tempFolder.newFolder("tools")
        val result = installer.install(target, verifyChecksums = true)
        assertTrue("expected ChecksumMismatch, got $result", result is ToolchainInstallResult.ChecksumMismatch)
        val mismatch = result as ToolchainInstallResult.ChecksumMismatch
        assertEquals("tool", mismatch.toolName)
        assertEquals(wrongSha, mismatch.expected)
        // The bad file should have been deleted.
        assertTrue(!File(target, "tool").exists())
    }

    @Test
    fun `install accepts matching SHA-256`() {
        // "hello" -> 2cf24dba5fb0a30e26e83b2ac5b9e29e1b161e5c1fa7425e73043362938b9824
        val content = "hello"
        val correctSha = "2cf24dba5fb0a30e26e83b2ac5b9e29e1b161e5c1fa7425e73043362938b9824"
        val manifest = listOf(
            toolDescriptor(name = "tool", sha256 = correctSha)
        )
        val installer = ToolchainInstaller(
            manifest = manifest,
            assetProvider = { ByteArrayInputStream(content.toByteArray()) }
        )
        val target = tempFolder.newFolder("tools")
        val result = installer.install(target, verifyChecksums = true)
        assertTrue("expected Success, got $result", result is ToolchainInstallResult.Success)
        val success = result as ToolchainInstallResult.Success
        assertTrue(success.verifiedChecksums)
        assertTrue(File(target, "tool").exists())
    }

    @Test
    fun `install skips existing files by default`() {
        val manifest = listOf(toolDescriptor("tool"))
        val installer = ToolchainInstaller(
            manifest = manifest,
            assetProvider = { ByteArrayInputStream("new_content".toByteArray()) }
        )
        val target = tempFolder.newFolder("tools")
        val existing = File(target, "tool")
        existing.writeText("old_content")

        val result = installer.install(target, verifyChecksums = false, overwrite = false)
        assertTrue(result is ToolchainInstallResult.Success)
        // The old content should be preserved.
        assertEquals("old_content", existing.readText())
    }

    @Test
    fun `install overwrites existing files when overwrite=true`() {
        val manifest = listOf(toolDescriptor("tool"))
        val installer = ToolchainInstaller(
            manifest = manifest,
            assetProvider = { ByteArrayInputStream("new_content".toByteArray()) }
        )
        val target = tempFolder.newFolder("tools")
        val existing = File(target, "tool")
        existing.writeText("old_content")

        val result = installer.install(target, verifyChecksums = false, overwrite = true)
        assertTrue(result is ToolchainInstallResult.Success)
        assertEquals("new_content", existing.readText())
    }

    @Test
    fun `install creates target directory if missing`() {
        val manifest = listOf(toolDescriptor("tool"))
        val installer = ToolchainInstaller(
            manifest = manifest,
            assetProvider = { ByteArrayInputStream("content".toByteArray()) }
        )
        val target = File(tempFolder.root, "new_dir/sub_dir")
        val result = installer.install(target, verifyChecksums = false)
        assertTrue(result is ToolchainInstallResult.Success)
        assertTrue(target.exists())
    }

    @Test
    fun `install sets executable bit`() {
        val manifest = listOf(toolDescriptor("tool"))
        val installer = ToolchainInstaller(
            manifest = manifest,
            assetProvider = { ByteArrayInputStream("content".toByteArray()) }
        )
        val target = tempFolder.newFolder("tools")
        val result = installer.install(target, verifyChecksums = false)
        assertTrue(result is ToolchainInstallResult.Success)
        val installed = File(target, "tool")
        assertTrue(installed.canExecute())
    }

    @Test
    fun `computeSha256 matches known value`() {
        val file = tempFolder.newFile("hash_test")
        file.writeText("hello")
        val sha = ToolchainInstaller.computeSha256(file)
        assertEquals("2cf24dba5fb0a30e26e83b2ac5b9e29e1b161e5c1fa7425e73043362938b9824", sha)
    }

    @Test
    fun `install with empty manifest succeeds with empty result`() {
        val installer = ToolchainInstaller(
            manifest = emptyList(),
            assetProvider = { null }
        )
        val target = tempFolder.newFolder("tools")
        val result = installer.install(target, verifyChecksums = false)
        assertTrue(result is ToolchainInstallResult.Success)
        val success = result as ToolchainInstallResult.Success
        assertEquals(0, success.installedTools.size)
        assertEquals(0, success.skippedTools.size)
    }
}
