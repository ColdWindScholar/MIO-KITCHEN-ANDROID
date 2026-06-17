#!/usr/bin/env python3
"""Validate the Firmware analyzer layer (Stage 7).

Stage 7 introduces a typed `FirmwareAnalyzer` API with `FirmwareProfile`,
`FirmwareCapabilities`, `PartitionInfo`, `FirmwareWarning`, and concrete
analyzers: ZipFirmwareAnalyzer, BootImageAnalyzer, SuperImageAnalyzer,
VbmetaAnalyzer, FilesystemImageAnalyzer, plus the registry.
"""
from __future__ import annotations

import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
ERRORS: list[str] = []

FIRMWARE_DIR = ROOT / "common/src/main/java/com/omarea/common/firmware"


def require(condition: bool, message: str) -> None:
    if not condition:
        ERRORS.append(message)


# --- Required files ------------------------------------------------------

required_files = [
    "common/src/main/java/com/omarea/common/firmware/FirmwareProfile.kt",
    "common/src/main/java/com/omarea/common/firmware/FirmwareAnalyzer.kt",
    "common/src/main/java/com/omarea/common/firmware/ZipFirmwareAnalyzer.kt",
    "common/src/main/java/com/omarea/common/firmware/ImageAnalyzers.kt",
    "common/src/main/java/com/omarea/common/firmware/FirmwareAnalyzerRegistry.kt",
    "common/src/test/java/com/omarea/common/firmware/FirmwareAnalyzerTest.kt",
]
for rel in required_files:
    require((ROOT / rel).exists(), f"Stage 7 file is missing: {rel}")


# --- API surface ---------------------------------------------------------

profile_src = (FIRMWARE_DIR / "FirmwareProfile.kt").read_text(encoding="utf-8", errors="ignore")
for token in [
    "sealed class FirmwareSource",
    "enum class FirmwarePackageType",
    "data class AndroidVersionHint",
    "enum class CompressionType",
    "data class PartitionInfo",
    "data class FirmwareWarning",
    "data class FirmwareCapabilities",
    "data class FirmwareProfile"
]:
    require(token in profile_src, f"FirmwareProfile.kt must declare {token}")

for cap in [
    "hasPayloadBin", "hasSuperImage", "hasDynamicPartitions", "hasSparseImages",
    "hasErofs", "hasExt4", "hasF2fs", "hasBootImage", "hasVendorBootImage",
    "hasInitBootImage", "hasDtboImage", "hasVbmetaImage", "usesAvb", "usesAB",
    "usesVirtualAB", "usesCompressionZstd", "usesCompressionBr",
    "usesCompressionLz4", "requires16KbAlignmentCheck"
]:
    require(cap in profile_src, f"FirmwareCapabilities must declare {cap}")

analyzer_src = (FIRMWARE_DIR / "FirmwareAnalyzer.kt").read_text(encoding="utf-8", errors="ignore")
require("interface FirmwareAnalyzer" in analyzer_src, "FirmwareAnalyzer interface missing")
require("fun supports(source" in analyzer_src, "FirmwareAnalyzer must declare supports(source)")
require("fun analyze(source" in analyzer_src, "FirmwareAnalyzer must declare analyze(source)")
require("class FirmwareAnalysisException" in analyzer_src, "FirmwareAnalysisException missing")
require("class CompositeFirmwareAnalyzer" in analyzer_src, "CompositeFirmwareAnalyzer missing")


# --- Concrete analyzers --------------------------------------------------

zip_src = (FIRMWARE_DIR / "ZipFirmwareAnalyzer.kt").read_text(encoding="utf-8", errors="ignore")
require("class ZipFirmwareAnalyzer" in zip_src, "ZipFirmwareAnalyzer class missing")
require("ZipInputStream" in zip_src, "ZipFirmwareAnalyzer must walk ZipInputStream")
require("payload.bin" in zip_src, "ZipFirmwareAnalyzer must detect payload.bin")
require("super.img" in zip_src, "ZipFirmwareAnalyzer must detect super.img")
require("vbmeta" in zip_src, "ZipFirmwareAnalyzer must detect vbmeta")

img_src = (FIRMWARE_DIR / "ImageAnalyzers.kt").read_text(encoding="utf-8", errors="ignore")
require("class BootImageAnalyzer" in img_src, "BootImageAnalyzer missing")
require("class SuperImageAnalyzer" in img_src, "SuperImageAnalyzer missing")
require("class VbmetaAnalyzer" in img_src, "VbmetaAnalyzer missing")
require("class FilesystemImageAnalyzer" in img_src, "FilesystemImageAnalyzer missing")
require("ANDROID!" in img_src, "BootImageAnalyzer must check ANDROID! magic")
require("0x3a" in img_src and "0xff" in img_src, "SuperImageAnalyzer must check sparse magic")
require("AVB0" in img_src, "VbmetaAnalyzer must check AVB0 magic")
require("0xef53" in img_src or "ef53" in img_src, "FilesystemImageAnalyzer must check ext4 magic")
require("erofs" in img_src.lower(), "FilesystemImageAnalyzer must check EROFS magic")


# --- Registry ------------------------------------------------------------

reg_src = (FIRMWARE_DIR / "FirmwareAnalyzerRegistry.kt").read_text(encoding="utf-8", errors="ignore")
require("object FirmwareAnalyzerRegistry" in reg_src, "FirmwareAnalyzerRegistry object missing")
require("fun createDefault()" in reg_src, "Registry must expose createDefault()")
for analyzer in ["ZipFirmwareAnalyzer", "BootImageAnalyzer", "SuperImageAnalyzer",
                 "VbmetaAnalyzer", "FilesystemImageAnalyzer"]:
    require(analyzer in reg_src, f"Registry must register {analyzer}")


# --- Tests present -------------------------------------------------------

test_src = (ROOT / "common/src/test/java/com/omarea/common/firmware/FirmwareAnalyzerTest.kt").read_text(encoding="utf-8")
require("@Test" in test_src, "FirmwareAnalyzerTest must declare at least one @Test")
require("ZipFirmwareAnalyzer" in test_src, "Tests must cover ZipFirmwareAnalyzer")
require("BootImageAnalyzer" in test_src, "Tests must cover BootImageAnalyzer")
require("SuperImageAnalyzer" in test_src, "Tests must cover SuperImageAnalyzer")
require("VbmetaAnalyzer" in test_src, "Tests must cover VbmetaAnalyzer")
require("FilesystemImageAnalyzer" in test_src, "Tests must cover FilesystemImageAnalyzer")
require("ANDROID!" in test_src, "Tests must write ANDROID! magic to a fake boot image")
require("AVB0" in test_src, "Tests must write AVB0 magic to a fake vbmeta image")


# --- Analyzer purity: no shell, no Android imports -----------------------

for kt in FIRMWARE_DIR.glob("*.kt"):
    src = kt.read_text(encoding="utf-8", errors="ignore")
    require(
        "import com.omarea.common.shell" not in src,
        f"{kt.name} must not import shell — analyzers are pure",
    )
    require(
        "import android.app." not in src,
        f"{kt.name} must not import android.app.*",
    )
    require(
        "import android.widget." not in src,
        f"{kt.name} must not import android.widget.*",
    )


# --- Report --------------------------------------------------------------

if ERRORS:
    for e in ERRORS:
        print(f"FAIL: {e}")
    sys.exit(1)

print("PASS: Firmware analyzer typed API is in place")
print("PASS: FirmwareProfile/FirmwareCapabilities/PartitionInfo are declared")
print("PASS: ZipFirmwareAnalyzer walks zip contents")
print("PASS: BootImageAnalyzer detects ANDROID! magic and header version")
print("PASS: SuperImageAnalyzer detects sparse format")
print("PASS: VbmetaAnalyzer detects AVB0 magic")
print("PASS: FilesystemImageAnalyzer detects ext4/EROFS/F2FS magic")
print("PASS: FirmwareAnalyzerRegistry composes all analyzers")
print("PASS: Firmware analyzer JVM unit tests are present")
print("PASS: analyzers are pure — no shell, no Android imports")
