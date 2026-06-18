#!/usr/bin/env python3
"""Validate the storage/workspace compatibility baseline."""

from pathlib import Path
import sys

ROOT = Path(__file__).resolve().parents[1]
ERRORS: list[str] = []


def read(rel: str) -> str:
    path = ROOT / rel
    if not path.exists():
        ERRORS.append(f"missing file: {rel}")
        return ""
    return path.read_text(encoding="utf-8", errors="ignore")


def require(condition: bool, message: str) -> None:
    if not condition:
        ERRORS.append(message)


required_files = [
    "common/src/main/java/com/omarea/common/storage/SafeFileName.kt",
    "common/src/main/java/com/omarea/common/storage/FirmwareWorkspace.kt",
    "common/src/main/java/com/omarea/common/storage/StorageGateway.kt",
    "common/src/main/java/com/omarea/common/storage/StorageResolveOptions.kt",
    "common/src/main/java/com/omarea/common/storage/StorageResolveResult.kt",
    "common/src/main/java/com/omarea/common/storage/StorageSourceKind.kt",
    "common/src/main/java/com/omarea/common/storage/AndroidStorageGateway.kt",
    "common/src/test/java/com/omarea/common/storage/SafeFileNameTest.kt",
]
for rel in required_files:
    require((ROOT / rel).exists(), f"storage/workspace file is missing: {rel}")

workspace = read("common/src/main/java/com/omarea/common/storage/FirmwareWorkspace.kt")
require("firmware-workspace" in workspace, "FirmwareWorkspace must use a dedicated workspace root")
require("imports" in workspace and "exports" in workspace, "FirmwareWorkspace must separate imports and exports")
require("getExternalFilesDir" in workspace, "FirmwareWorkspace must prefer app-specific external storage for shell compatibility")
require("prepareImportFile" in workspace and "prepareExportFile" in workspace, "FirmwareWorkspace must prepare import/export files")

safe = read("common/src/main/java/com/omarea/common/storage/SafeFileName.kt")
require("unsafeChars" in safe and "selected-file.bin" in safe, "SafeFileName must sanitize provider names and provide defaults")
require("substringAfterLast('/')" in safe and "substringAfterLast('\\\\')" in safe, "SafeFileName must strip path segments")

storage_gateway = read("common/src/main/java/com/omarea/common/storage/StorageGateway.kt")
require("interface StorageGateway" in storage_gateway, "StorageGateway interface is required")
require("resolveUriForShell" in storage_gateway and "resolvePathForShell" in storage_gateway, "StorageGateway must expose URI and path resolution")

android_gateway = read("common/src/main/java/com/omarea/common/storage/AndroidStorageGateway.kt")
for token in [
    "class AndroidStorageGateway",
    "openInputStream(uri)",
    "workspace.prepareImportFile",
    "MessageDigest.getInstance(\"SHA-256\")",
    "outputStream.write(buffer, 0, bytesRead)",
    "StorageSourceKind.WorkspaceCopy",
    "persistReadPermission",
    "preferLegacyDirectPath",
]:
    require(token in android_gateway, f"AndroidStorageGateway must contain: {token}")
require("content://" in android_gateway, "AndroidStorageGateway docs must document content URI policy")
require("FilePathResolver" in android_gateway, "AndroidStorageGateway may bridge legacy direct-path behavior internally")

result = read("common/src/main/java/com/omarea/common/storage/StorageResolveResult.kt")
require("sealed class StorageResolveResult" in result, "StorageResolveResult must be sealed")
require("data class Resolved" in result and "data class Failed" in result, "StorageResolveResult must model success and failure")
require("sha256" in result and "copiedBytes" in result, "Resolved storage results must include copy metadata")

options = read("common/src/main/java/com/omarea/common/storage/StorageResolveOptions.kt")
require("preferLegacyDirectPath: Boolean = false" in options, "legacy direct path must be opt-in")
require("copyContentUriToWorkspace: Boolean = true" in options, "content URI workspace copy must be enabled by default")
require("computeSha256: Boolean = true" in options, "checksum should be computed during copy by default")

for activity_rel in ["pio/src/main/java/com/mio/kitchen/ActionPage.kt", "pio/src/main/java/com/mio/kitchen/MainActivity.kt"]:
    activity_text = read(activity_rel)
    activity_name = Path(activity_rel).name
    require("AndroidStorageGateway" in activity_text, f"{activity_name} must use AndroidStorageGateway")
    require("FilePathResolver" not in activity_text, f"{activity_name} must not resolve content URI paths directly")
    require("Intent.ACTION_OPEN_DOCUMENT" in activity_text, f"{activity_name} must use SAF ACTION_OPEN_DOCUMENT for file selection")
    require("Intent.FLAG_GRANT_READ_URI_PERMISSION" in activity_text, f"{activity_name} must request read URI grants")
    require("Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION" in activity_text, f"{activity_name} must request persistable URI grants")
    require("resolveSelectedUri" in activity_text, f"{activity_name} must resolve selected URI through a dedicated method")
    require("StorageResolveOptions(" in activity_text, f"{activity_name} must pass explicit storage resolve options")
    require("Thread {" in activity_text and "file_workspace_prepare" in activity_text, f"{activity_name} must resolve workspace files off the immediate UI path with progress feedback")

strings = read("pio/src/main/res/values/strings.xml")
require("file_workspace_prepare" in strings and "file_workspace_resolve_failed" in strings, "workspace user-facing strings are required")

for rel in ["docs/ru/dev/storage-workspace.md", "docs/en/dev/storage-workspace.md"]:
    text = read(rel)
    require("StorageGateway" in text, f"{rel} must document StorageGateway")
    require("FirmwareWorkspace" in text, f"{rel} must document FirmwareWorkspace")
    require("targetSdk" in text, f"{rel} must explain targetSdk migration relevance")

build_quality = read(".github/workflows/build-quality.yml")
release_workflow = read(".github/workflows/gradle.yml")
require("python3 tools/check-storage-workspace.py" in build_quality, "build-quality workflow must run storage/workspace checker")
require("python3 tools/check-storage-workspace.py" in release_workflow, "release workflow must run storage/workspace checker")

if ERRORS:
    print("FAIL: storage/workspace checks failed")
    for error in ERRORS:
        print(f" - {error}")
    sys.exit(1)

print("PASS: storage/workspace compatibility baseline is in place")
print("PASS: content URI input is converted to workspace file paths")
print("PASS: legacy direct-path behavior is isolated behind StorageGateway")
print("PASS: storage/workspace docs and CI gates are present")
