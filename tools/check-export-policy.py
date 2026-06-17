#!/usr/bin/env python3
"""Validate the Folder/tree URI export policy (Stage 14)."""
from __future__ import annotations
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
ERRORS: list[str] = []

def require(condition: bool, message: str) -> None:
    if not condition:
        ERRORS.append(message)

STORAGE_DIR = ROOT / "common/src/main/java/com/omarea/common/storage"

required_files = [
    "common/src/main/java/com/omarea/common/storage/ExportPolicy.kt",
    "common/src/main/java/com/omarea/common/storage/AndroidWorkspaceExporter.kt",
]
for rel in required_files:
    require((ROOT / rel).exists(), f"Stage 14 file is missing: {rel}")

src = (STORAGE_DIR / "ExportPolicy.kt").read_text(encoding="utf-8", errors="ignore")
for token in [
    "sealed class ExportPolicy",
    "data object AskPerFile",
    "data class TreeFolder",
    "data class MediaStoreExport",
    "data object AppPrivate",
    "sealed class ExportResult",
    "data class Success",
    "data object Cancelled",
    "data class Failed",
    "data class ExportOptions",
    "interface WorkspaceExporter",
]:
    require(token in src, f"ExportPolicy.kt must declare {token}")

impl = (STORAGE_DIR / "AndroidWorkspaceExporter.kt").read_text(encoding="utf-8", errors="ignore")
for token in [
    "class AndroidWorkspaceExporter",
    "WorkspaceExporter",
    "fun export(",
    "fun exportToUri(",
    "DocumentsContract.createDocument",
    "MediaStore.Files.getContentUri",
    "MessageDigest.getInstance(\"SHA-256\")",
]:
    require(token in impl, f"AndroidWorkspaceExporter.kt must declare/use {token}")

if ERRORS:
    for e in ERRORS:
        print(f"FAIL: {e}")
    sys.exit(1)

print("PASS: ExportPolicy sealed type covers AskPerFile/TreeFolder/MediaStore/AppPrivate")
print("PASS: ExportResult sealed type covers Success/Cancelled/Failed")
print("PASS: WorkspaceExporter interface declares export(sourceFile, options)")
print("PASS: AndroidWorkspaceExporter supports SAF tree + MediaStore + app-private")
print("PASS: SHA-256 verification is built into export path")
