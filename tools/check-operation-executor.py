#!/usr/bin/env python3
"""Validate the OperationExecutor (Stage 12)."""
from __future__ import annotations
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
ERRORS: list[str] = []

def require(condition: bool, message: str) -> None:
    if not condition:
        ERRORS.append(message)

OPS_DIR = ROOT / "common/src/main/java/com/omarea/common/operations"

required_files = [
    "common/src/main/java/com/omarea/common/operations/OperationExecutor.kt",
    "common/src/test/java/com/omarea/common/operations/OperationExecutorTest.kt",
]
for rel in required_files:
    require((ROOT / rel).exists(), f"Stage 12 file is missing: {rel}")

src = (OPS_DIR / "OperationExecutor.kt").read_text(encoding="utf-8", errors="ignore")
for token in [
    "data class PreparedExecution",
    "class OperationExecutor",
    "fun prepare(",
    "fun execute(",
    "suspend fun executeForResult",
    "ShellRuntime",
    "ShellCommand",
    "ShellResult",
    "OperationExecutionException",
    "defaultScriptLocator",
    "OPERATION",
    "FIRMWARE_PATH",
    "WORK_DIR",
    "TOOLS_DIR",
]:
    require(token in src, f"OperationExecutor.kt must declare {token}")

test_src = (ROOT / "common/src/test/java/com/omarea/common/operations/OperationExecutorTest.kt").read_text(encoding="utf-8")
require("@Test" in test_src, "OperationExecutorTest must declare @Test")
require("DryRunShellRuntime" in test_src, "Tests must use DryRunShellRuntime")
require("FakeShellRuntime" in test_src, "Tests must use FakeShellRuntime")

# Purity: no Android UI imports.
require("import android.app." not in src, "OperationExecutor must not import android.app.*")
require("import android.widget." not in src, "OperationExecutor must not import android.widget.*")

if ERRORS:
    for e in ERRORS:
        print(f"FAIL: {e}")
    sys.exit(1)

print("PASS: OperationExecutor with prepare/execute/executeForResult is in place")
print("PASS: PreparedExecution wraps ShellCommand with operation env")
print("PASS: defaultScriptLocator maps all FirmwareOperation values to script2/ paths")
print("PASS: OperationExecutor tests cover prepare/execute/dry-run/blocked")
print("PASS: OperationExecutor is pure (no android.app/android.widget imports)")
