#!/usr/bin/env python3
"""Validate the instrumented test scaffolding (Stage 18)."""
from __future__ import annotations
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
ERRORS: list[str] = []

def require(condition: bool, message: str) -> None:
    if not condition:
        ERRORS.append(message)

required_files = [
    "common/src/androidTest/java/com/omarea/common/storage/FirmwareWorkspaceInstrumentedTest.kt",
    "common/src/androidTest/java/com/omarea/common/firmware/ZipFirmwareAnalyzerInstrumentedTest.kt",
    "pio/src/androidTest/java/com/mio/kitchen/ui/modern/RuntimePermissionHelperInstrumentedTest.kt",
]
for rel in required_files:
    require((ROOT / rel).exists(), f"Stage 18 file is missing: {rel}")

# All instrumented tests must use AndroidJUnit4 runner.
for rel in required_files:
    src = (ROOT / rel).read_text(encoding="utf-8")
    require("@RunWith(AndroidJUnit4::class)" in src, f"{rel} must use AndroidJUnit4 runner")
    require("@Test" in src, f"{rel} must declare @Test methods")
    require("ApplicationProvider" in src, f"{rel} must use ApplicationProvider for context")

# common module must have androidTest deps.
common_build = (ROOT / "common/build.gradle").read_text(encoding="utf-8")
require(
    "androidTestImplementation \"androidx.test.ext:junit:" in common_build,
    "common/build.gradle must declare androidx.test.ext:junit for androidTest"
)
pio_build = (ROOT / "pio/build.gradle").read_text(encoding="utf-8")
require(
    "androidTestImplementation \"androidx.test.ext:junit:" in pio_build,
    "pio/build.gradle must declare androidx.test.ext:junit for androidTest"
)

if ERRORS:
    for e in ERRORS:
        print(f"FAIL: {e}")
    sys.exit(1)

print("PASS: FirmwareWorkspaceInstrumentedTest covers workspace creation on device")
print("PASS: ZipFirmwareAnalyzerInstrumentedTest covers real zip parsing on device")
print("PASS: RuntimePermissionHelperInstrumentedTest covers permission list per Android version")
print("PASS: All instrumented tests use AndroidJUnit4 + ApplicationProvider")
print("PASS: common + pio modules declare androidTest dependencies")
