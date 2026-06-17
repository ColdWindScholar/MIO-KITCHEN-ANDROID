#!/usr/bin/env python3
"""Validate the hybrid migration layer (Stage 20) + ToolchainInstaller wiring (Stage 21)."""
from __future__ import annotations
import re
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
ERRORS: list[str] = []

def require(condition: bool, message: str) -> None:
    if not condition:
        ERRORS.append(message)


# --- Stage 20: AppRuntimeStore + activity integration --------------------

required_files = [
    "pio/src/main/java/com/mio/kitchen/ui/modern/AppRuntimeStore.kt",
]
for rel in required_files:
    require((ROOT / rel).exists(), f"Stage 20 file is missing: {rel}")

store_src = (ROOT / "pio/src/main/java/com/mio/kitchen/ui/modern/AppRuntimeStore.kt").read_text(encoding="utf-8")
for token in [
    "object AppRuntimeStore",
    "val profile: StateFlow",
    "fun init(",
    "fun updateRootStatus(",
    "fun setFirmware(",
    "fun resetFirmware(",
    "val device:",
    "val firmware:",
    "MutableStateFlow",
    "FirmwareAnalyzerRegistry",
    "DeviceProfileProvider",
]:
    require(token in store_src, f"AppRuntimeStore.kt must declare {token}")


# --- SplashActivity integration -----------------------------------------

splash_src = (ROOT / "pio/src/main/java/com/mio/kitchen/SplashActivity.kt").read_text(encoding="utf-8")
require(
    "AppRuntimeStore.init(" in splash_src,
    "SplashActivity must call AppRuntimeStore.init()"
)
require(
    "AppRuntimeStore.updateRootStatus(" in splash_src,
    "SplashActivity must call AppRuntimeStore.updateRootStatus() after root check"
)
# Legacy path preserved (not removed).
require(
    "ScriptEnvironmen.isInited()" in splash_src,
    "SplashActivity must preserve legacy ScriptEnvironmen.isInited() check"
)
require(
    "CheckRootStatus" in splash_src,
    "SplashActivity must preserve legacy CheckRootStatus flow"
)


# --- MainActivity integration -------------------------------------------

main_src = (ROOT / "pio/src/main/java/com/mio/kitchen/MainActivity.kt").read_text(encoding="utf-8")
require(
    "AppRuntimeStore.init(" in main_src,
    "MainActivity must call AppRuntimeStore.init()"
)
require(
    "RuntimePermissionHelper" in main_src,
    "MainActivity must use RuntimePermissionHelper"
)
# Legacy permission request must be removed.
require(
    "ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE, Manifest.permission.WRITE_EXTERNAL_STORAGE), 111)" not in main_src,
    "MainActivity must NOT use legacy direct storage permission request"
)


# --- ActivityFileSelector integration -----------------------------------

selector_src = (ROOT / "pio/src/main/java/com/mio/kitchen/ActivityFileSelector.kt").read_text(encoding="utf-8")
require(
    "RuntimePermissionHelper" in selector_src,
    "ActivityFileSelector must use RuntimePermissionHelper"
)
require(
    "RuntimePermissionHelper.areAllGranted(grantResults)" in selector_src,
    "ActivityFileSelector.onRequestPermissionsResult must use RuntimePermissionHelper.areAllGranted"
)
# Legacy permission request must be removed.
require(
    "ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE, Manifest.permission.WRITE_EXTERNAL_STORAGE), 111)" not in selector_src,
    "ActivityFileSelector must NOT use legacy direct storage permission request"
)


# --- ActionPage integration ---------------------------------------------

action_src = (ROOT / "pio/src/main/java/com/mio/kitchen/ActionPage.kt").read_text(encoding="utf-8")
require(
    "AppRuntimeStore" in action_src and "setFirmware" in action_src,
    "ActionPage must call AppRuntimeStore.setFirmware() after resolving URI"
)
# Legacy storage gateway path preserved.
require(
    "AndroidStorageGateway" in action_src,
    "ActionPage must preserve legacy AndroidStorageGateway usage"
)


# --- Stage 21: ToolchainInstaller in FirmwareOperationService.onCreate --

service_src = (ROOT / "pio/src/main/java/com/mio/kitchen/FirmwareOperationService.kt").read_text(encoding="utf-8")
require(
    "override fun onCreate()" in service_src,
    "FirmwareOperationService must override onCreate()"
)
require(
    "ensureToolchainInstalled" in service_src,
    "FirmwareOperationService must call ensureToolchainInstalled() in onCreate"
)
require(
    "ToolchainInstaller(" in service_src,
    "FirmwareOperationService must construct ToolchainInstaller"
)
require(
    "ToolManifestLoader" in service_src,
    "FirmwareOperationService must load toolchain manifest"
)
require(
    "assets.open(\"bin/\" + name)" in service_src or 'assets.open("bin/$name")' in service_src,
    "FirmwareOperationService must provide assets/bin/<name> stream"
)
require(
    "ToolchainInstallResult.Success" in service_src,
    "FirmwareOperationService must handle ToolchainInstallResult.Success"
)
require(
    "ToolchainInstallResult.ChecksumMismatch" in service_src,
    "FirmwareOperationService must handle ToolchainInstallResult.ChecksumMismatch"
)
require(
    "ToolchainInstallResult.Failed" in service_src,
    "FirmwareOperationService must handle ToolchainInstallResult.Failed"
)
require(
    "lastInstalledToolsDir" in service_src,
    "FirmwareOperationService must expose lastInstalledToolsDir"
)
require(
    "SupervisorJob" in service_src,
    "FirmwareOperationService must use SupervisorJob for install scope"
)
require(
    "installMutex" in service_src,
    "FirmwareOperationService must use a mutex to serialize installs"
)


# --- Report --------------------------------------------------------------

if ERRORS:
    for e in ERRORS:
        print(f"FAIL: {e}")
    sys.exit(1)

print("PASS: AppRuntimeStore singleton bridge is in place")
print("PASS: SplashActivity calls AppRuntimeStore.init + updateRootStatus (legacy path preserved)")
print("PASS: MainActivity calls AppRuntimeStore.init + RuntimePermissionHelper (legacy storage request removed)")
print("PASS: ActivityFileSelector uses RuntimePermissionHelper (legacy storage request removed)")
print("PASS: ActionPage calls AppRuntimeStore.setFirmware after URI resolution (legacy gateway preserved)")
print("PASS: FirmwareOperationService.onCreate invokes ToolchainInstaller")
print("PASS: FirmwareOperationService handles all ToolchainInstallResult variants")
print("PASS: FirmwareOperationService exposes lastInstalledToolsDir + uses SupervisorJob + mutex")
