package com.omarea.common.shell.runtime

import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * RU: Тесты для [DryRunShellRuntime], [FakeShellRuntime] и базового
 *     контракта [ShellRuntime].
 *
 * EN: Tests for [DryRunShellRuntime], [FakeShellRuntime] and the basic
 *     [ShellRuntime] contract.
 */
class ShellRuntimeTest {

    @Test
    fun `DryRunShellRuntime emits stdout preview and completed event`() = runBlocking {
        val runtime = DryRunShellRuntime()
        val command = ShellCommand.create(
            script = ScriptSource.Inline("echo hello")
        )
        val events = runtime.execute(command).toList()
        assertEquals(2, events.size)
        assertTrue(events[0] is ShellEvent.Stdout)
        assertTrue(events[1] is ShellEvent.Completed)
        assertEquals(0, (events[1] as ShellEvent.Completed).exitCode)
    }

    @Test
    fun `DryRunShellRuntime preview includes inline script text`() = runBlocking {
        val runtime = DryRunShellRuntime()
        val command = ShellCommand.create(
            script = ScriptSource.Inline("ls -la /tmp")
        )
        val events = runtime.execute(command).toList()
        val stdout = events[0] as ShellEvent.Stdout
        assertTrue(stdout.line.contains("ls -la /tmp"))
    }

    @Test
    fun `DryRunShellRuntime preview includes asset path`() = runBlocking {
        val runtime = DryRunShellRuntime()
        val command = ShellCommand.create(
            script = ScriptSource.FilePath("script/tool.sh", inAssets = true)
        )
        val events = runtime.execute(command).toList()
        val stdout = events[0] as ShellEvent.Stdout
        assertTrue(stdout.line.contains("script/tool.sh"))
        assertTrue(stdout.line.contains("asset"))
    }

    @Test
    fun `executeForResult returns Completed with exit code 0 for dry run`() = runBlocking {
        val runtime = DryRunShellRuntime()
        val command = ShellCommand.create(script = ScriptSource.Inline("echo ok"))
        val result = runtime.executeForResult(command)
        assertTrue(result is ShellResult.Completed)
        val completed = result as ShellResult.Completed
        assertEquals(0, completed.exitCode)
        assertTrue(completed.isSuccess)
    }

    @Test
    fun `FakeShellRuntime records executed commands`() = runBlocking {
        val runtime = FakeShellRuntime()
        val command = ShellCommand.create(
            script = ScriptSource.Inline("echo hi"),
            requiresRoot = true,
            tag = "test"
        )
        runtime.execute(command).toList()
        assertEquals(1, runtime.recordedCommands.size)
        val recorded = runtime.recordedCommands[0]
        assertEquals(command.id, recorded.id)
        assertTrue(recorded.requiresRoot)
        assertEquals("test", recorded.tag)
    }

    @Test
    fun `FakeShellRuntime emits pre-set events`() = runBlocking {
        val runtime = FakeShellRuntime()
        runtime.nextEvents = listOf(
            ShellEvent.Stdout("line1"),
            ShellEvent.Stdout("line2"),
            ShellEvent.Warning("careful"),
            ShellEvent.Completed(0)
        )
        val events = runtime.execute(
            ShellCommand.create(script = ScriptSource.Inline("x"))
        ).toList()
        assertEquals(4, events.size)
        assertTrue(events[2] is ShellEvent.Warning)
    }

    @Test
    fun `FakeShellRuntime returns pre-set result without running flow`() = runBlocking {
        val runtime = FakeShellRuntime()
        runtime.nextResult = ShellResult.Failed("boom")
        val result = runtime.executeForResult(
            ShellCommand.create(script = ScriptSource.Inline("x"))
        )
        assertTrue(result is ShellResult.Failed)
        assertEquals("boom", (result as ShellResult.Failed).message)
    }

    @Test
    fun `ShellCommand create assigns unique ids`() {
        val c1 = ShellCommand.create(script = ScriptSource.Inline("a"))
        val c2 = ShellCommand.create(script = ScriptSource.Inline("a"))
        assertFalse(c1.id == c2.id)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `ShellCommand rejects empty id`() {
        ShellCommand(id = "", script = ScriptSource.Inline("echo"))
    }

    @Test(expected = IllegalArgumentException::class)
    fun `ShellCommand rejects non-positive timeout`() {
        ShellCommand.create(
            script = ScriptSource.Inline("echo"),
            timeoutMs = 0L
        )
    }

    @Test
    fun `ShellEvent Progress percent must be 0..100 or null`() {
        ShellEvent.Progress(percent = null, message = null)
        ShellEvent.Progress(percent = 0, message = "starting")
        ShellEvent.Progress(percent = 100, message = "done")
    }

    @Test(expected = IllegalArgumentException::class)
    fun `ShellEvent Progress rejects negative percent`() {
        ShellEvent.Progress(percent = -1, message = null)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `ShellEvent Progress rejects percent over 100`() {
        ShellEvent.Progress(percent = 101, message = null)
    }

    @Test
    fun `ShellRuntimeFactory prefers dry-run when enabled`() {
        val factory = ShellRuntimeFactory(
            rootAvailable = { true },
            dryRun = { true }
        )
        val command = ShellCommand.create(
            script = ScriptSource.Inline("echo"),
            requiresRoot = true
        )
        val runtime = factory.runtimeFor(command)
        assertEquals("dry-run", runtime.name)
    }

    @Test
    fun `ShellRuntimeFactory returns root runtime when required and available`() {
        val factory = ShellRuntimeFactory(
            rootAvailable = { true },
            dryRun = { false }
        )
        val command = ShellCommand.create(
            script = ScriptSource.Inline("echo"),
            requiresRoot = true
        )
        assertEquals("root", factory.runtimeFor(command).name)
    }

    @Test
    fun `ShellRuntimeFactory falls back to user runtime when root missing`() {
        val factory = ShellRuntimeFactory(
            rootAvailable = { false },
            dryRun = { false }
        )
        val command = ShellCommand.create(
            script = ScriptSource.Inline("echo"),
            requiresRoot = true
        )
        assertEquals("user", factory.runtimeFor(command).name)
    }

    @Test
    fun `ShellRuntimeFactory returns user runtime for non-root command`() {
        val factory = ShellRuntimeFactory(
            rootAvailable = { true },
            dryRun = { false }
        )
        val command = ShellCommand.create(
            script = ScriptSource.Inline("echo"),
            requiresRoot = false
        )
        assertEquals("user", factory.runtimeFor(command).name)
    }
}
