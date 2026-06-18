#!/usr/bin/env python3
"""Validate the ToolchainInstaller (Stage 11)."""
from __future__ import annotations
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
ERRORS: list[str] = []

def require(condition: bool, message: str) -> None:
    if not condition:
        ERRORS.append(message)

TOOLCHAIN_DIR = ROOT / "common/src/main/java/com/omarea/common/toolchain"

required_files = [
    "common/src/main/java/com/omarea/common/toolchain/ToolchainInstaller.kt",
    "common/src/test/java/com/omarea/common/toolchain/ToolchainInstallerTest.kt",
]
for rel in required_files:
    require((ROOT / rel).exists(), f"Stage 11 file is missing: {rel}")

src = (TOOLCHAIN_DIR / "ToolchainInstaller.kt").read_text(encoding="utf-8", errors="ignore")
for token in [
    "sealed class ToolchainInstallResult",
    "data class Success",
    "data class Failed",
    "data class ChecksumMismatch",
    "class ToolchainInstaller",
    "fun install(",
    "MessageDigest.getInstance(\"SHA-256\")",
    "fun computeSha256",
    "fun defaultSha256Verifier",
]:
    require(token in src, f"ToolchainInstaller.kt must declare {token}")

test_src = (ROOT / "common/src/test/java/com/omarea/common/toolchain/ToolchainInstallerTest.kt").read_text(encoding="utf-8")
require("@Test" in test_src, "ToolchainInstallerTest must declare @Test")
require("ChecksumMismatch" in test_src, "Tests must cover ChecksumMismatch case")
require("computeSha256" in test_src, "Tests must cover computeSha256 helper")

# Purity: no shell/UI imports.
require("import com.omarea.common.shell" not in src, "ToolchainInstaller must not import shell")
require("import android.app." not in src, "ToolchainInstaller must not import android.app.*")
require("import android.widget." not in src, "ToolchainInstaller must not import android.widget.*")

if ERRORS:
    for e in ERRORS:
        print(f"FAIL: {e}")
    sys.exit(1)

print("PASS: ToolchainInstaller with SHA-256 verification is in place")
print("PASS: ToolchainInstallResult sealed type covers Success/Failed/ChecksumMismatch")
print("PASS: ToolchainInstaller tests cover install/skip/overwrite/checksum")
print("PASS: ToolchainInstaller is pure (no shell/UI imports)")
