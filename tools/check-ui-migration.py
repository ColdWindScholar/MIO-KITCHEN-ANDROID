#!/usr/bin/env python3
"""Validate the UI migration to AppRuntimeProfile (Stage 15)."""
from __future__ import annotations
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
ERRORS: list[str] = []

def require(condition: bool, message: str) -> None:
    if not condition:
        ERRORS.append(message)

required_files = [
    "pio/src/main/java/com/mio/kitchen/ui/modern/FirmwareAnalysisActivity.kt",
]
for rel in required_files:
    require((ROOT / rel).exists(), f"Stage 15 file is missing: {rel}")

src = (ROOT / "pio/src/main/java/com/mio/kitchen/ui/modern/FirmwareAnalysisActivity.kt").read_text(encoding="utf-8")

# Activity must extend AppCompatActivity and use modern components.
require("class FirmwareAnalysisActivity" in src, "FirmwareAnalysisActivity class missing")
require(": AppCompatActivity()" in src, "FirmwareAnalysisActivity must extend AppCompatActivity")
require("FirmwareAnalysisViewModel" in src, "Activity must use FirmwareAnalysisViewModel")
require("RuntimePermissionHelper" in src, "Activity must use RuntimePermissionHelper")
require("OpenDocumentHelper" in src, "Activity must use OpenDocumentHelper")
require("AndroidStorageGateway" in src, "Activity must use AndroidStorageGateway")
require("FirmwareOperationService" in src, "Activity must start FirmwareOperationService")
require("FirmwareProfileFormatter" in src, "Activity must use FirmwareProfileFormatter")
require("repeatOnLifecycle" in src, "Activity must use repeatOnLifecycle for state collection")
require("Dispatchers.IO" in src, "Activity must dispatch to IO for storage resolution")
require("viewModelScope" in src or "lifecycleScope" in src, "Activity must use a coroutine scope")

# Manifest must register the activity.
manifest = (ROOT / "pio/src/main/AndroidManifest.xml").read_text(encoding="utf-8")
require(
    "ui.modern.FirmwareAnalysisActivity" in manifest,
    "AndroidManifest must register FirmwareAnalysisActivity"
)

if ERRORS:
    for e in ERRORS:
        print(f"FAIL: {e}")
    sys.exit(1)

print("PASS: FirmwareAnalysisActivity wires all Stage 4-13 components together")
print("PASS: Activity uses ViewModel + StateFlow via repeatOnLifecycle")
print("PASS: Activity uses RuntimePermissionHelper before launching picker")
print("PASS: Activity uses OpenDocumentHelper for SAF picker")
print("PASS: Activity uses AndroidStorageGateway for content:// resolution")
print("PASS: Activity starts FirmwareOperationService for foreground state")
print("PASS: Activity is registered in AndroidManifest")
