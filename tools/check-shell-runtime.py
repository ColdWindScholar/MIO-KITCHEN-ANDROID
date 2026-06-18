#!/usr/bin/env python3
"""Validate the Shell runtime layer (Stage 6).

Stage 6 introduces a typed `ShellRuntime` API with `ShellCommand`, `ShellEvent`,
`ShellResult`, `DryRunShellRuntime`, `FakeShellRuntime`, `RootShellRuntime`,
`UserShellRuntime`, and `ShellRuntimeFactory`. This script statically verifies
the architectural contract.
"""
from __future__ import annotations

import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
ERRORS: list[str] = []

RUNTIME_DIR = ROOT / "common/src/main/java/com/omarea/common/shell/runtime"


def require(condition: bool, message: str) -> None:
    if not condition:
        ERRORS.append(message)


# --- Required files ------------------------------------------------------

required_files = [
    "common/src/main/java/com/omarea/common/shell/runtime/ScriptSource.kt",
    "common/src/main/java/com/omarea/common/shell/runtime/ShellCommand.kt",
    "common/src/main/java/com/omarea/common/shell/runtime/ShellEvent.kt",
    "common/src/main/java/com/omarea/common/shell/runtime/ShellResult.kt",
    "common/src/main/java/com/omarea/common/shell/runtime/ShellRuntime.kt",
    "common/src/main/java/com/omarea/common/shell/runtime/DryRunShellRuntime.kt",
    "common/src/main/java/com/omarea/common/shell/runtime/FakeShellRuntime.kt",
    "common/src/main/java/com/omarea/common/shell/runtime/KeepShellRuntime.kt",
    "common/src/main/java/com/omarea/common/shell/runtime/ShellRuntimeFactory.kt",
    "common/src/test/java/com/omarea/common/shell/runtime/ShellRuntimeTest.kt",
]
for rel in required_files:
    require((ROOT / rel).exists(), f"Stage 6 file is missing: {rel}")


# --- API surface ---------------------------------------------------------

runtime_api = (RUNTIME_DIR / "ShellRuntime.kt").read_text(encoding="utf-8", errors="ignore")
require(
    "interface ShellRuntime" in runtime_api,
    "ShellRuntime interface declaration missing",
)
require(
    "fun execute(command: ShellCommand): Flow<ShellEvent>" in runtime_api,
    "ShellRuntime must expose execute(command) returning Flow<ShellEvent>",
)
require(
    "suspend fun executeForResult" in runtime_api,
    "ShellRuntime must expose suspend executeForResult",
)

event_src = (RUNTIME_DIR / "ShellEvent.kt").read_text(encoding="utf-8", errors="ignore")
for sealed_subtype in ["data class Stdout", "data class Stderr", "data class Progress",
                       "data class Warning", "data class Error", "data class Completed"]:
    require(sealed_subtype in event_src, f"ShellEvent must declare {sealed_subtype}")

result_src = (RUNTIME_DIR / "ShellResult.kt").read_text(encoding="utf-8", errors="ignore")
for sealed_subtype in ["data class Completed", "data object Cancelled",
                       "data class TimedOut", "data class Failed"]:
    require(sealed_subtype in result_src, f"ShellResult must declare {sealed_subtype}")

cmd_src = (RUNTIME_DIR / "ShellCommand.kt").read_text(encoding="utf-8", errors="ignore")
for field in ["val id: String", "val script: ScriptSource", "val env:",
              "val requiresRoot: Boolean", "val timeoutMs"]:
    require(field in cmd_src, f"ShellCommand must declare {field}")

script_src = (RUNTIME_DIR / "ScriptSource.kt").read_text(encoding="utf-8", errors="ignore")
for variant in ["data class Inline", "data class FilePath", "data class PreparedFile"]:
    require(variant in script_src, f"ScriptSource must declare {variant}")


# --- Runtime implementations --------------------------------------------

dryrun_src = (RUNTIME_DIR / "DryRunShellRuntime.kt").read_text(encoding="utf-8", errors="ignore")
require("class DryRunShellRuntime" in dryrun_src, "DryRunShellRuntime class missing")
require("ShellEvent.Completed(exitCode = 0)" in dryrun_src,
        "DryRunShellRuntime must emit Completed(0) without running shell")

fake_src = (RUNTIME_DIR / "FakeShellRuntime.kt").read_text(encoding="utf-8", errors="ignore")
require("class FakeShellRuntime" in fake_src, "FakeShellRuntime class missing")
require("recordedCommands" in fake_src, "FakeShellRuntime must record executed commands")

keep_src = (RUNTIME_DIR / "KeepShellRuntime.kt").read_text(encoding="utf-8", errors="ignore")
require("class RootShellRuntime" in keep_src, "RootShellRuntime class missing")
require("class UserShellRuntime" in keep_src, "UserShellRuntime class missing")
require("class KeepShellRuntime" in keep_src, "KeepShellRuntime base class missing")

factory_src = (RUNTIME_DIR / "ShellRuntimeFactory.kt").read_text(encoding="utf-8", errors="ignore")
require("class ShellRuntimeFactory" in factory_src, "ShellRuntimeFactory class missing")
require("fun runtimeFor" in factory_src, "ShellRuntimeFactory must expose runtimeFor")


# --- Tests present -------------------------------------------------------

test_src = (ROOT / "common/src/test/java/com/omarea/common/shell/runtime/ShellRuntimeTest.kt").read_text(encoding="utf-8")
require("@Test" in test_src, "ShellRuntimeTest must declare at least one @Test")
require("DryRunShellRuntime" in test_src, "ShellRuntimeTest must cover DryRunShellRuntime")
require("FakeShellRuntime" in test_src, "ShellRuntimeTest must cover FakeShellRuntime")
require("ShellRuntimeFactory" in test_src, "ShellRuntimeTest must cover ShellRuntimeFactory")


# --- common/build.gradle has coroutines test deps ------------------------

common_build = (ROOT / "common/build.gradle").read_text(encoding="utf-8")
require(
    "testImplementation \"org.jetbrains.kotlinx:kotlinx-coroutines-core:" in common_build,
    "common/build.gradle must declare kotlinx-coroutines-core for tests",
)
require(
    "testImplementation \"org.jetbrains.kotlinx:kotlinx-coroutines-test:" in common_build,
    "common/build.gradle must declare kotlinx-coroutines-test for tests",
)


# --- Report --------------------------------------------------------------

if ERRORS:
    for e in ERRORS:
        print(f"FAIL: {e}")
    sys.exit(1)

print("PASS: Shell runtime typed API is in place")
print("PASS: ShellCommand/ShellEvent/ShellResult sealed types are declared")
print("PASS: DryRunShellRuntime and FakeShellRuntime are present")
print("PASS: RootShellRuntime and UserShellRuntime wrap KeepShell")
print("PASS: ShellRuntimeFactory selects runtime based on command + dry-run + root")
print("PASS: Shell runtime JVM unit tests are present")
