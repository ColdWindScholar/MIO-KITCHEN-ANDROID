#!/usr/bin/env python3
"""Validate the targetSdk 35 runtime migration (Stage 13)."""
from __future__ import annotations
import re
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
ERRORS: list[str] = []

def require(condition: bool, message: str) -> None:
    if not condition:
        ERRORS.append(message)

ROOT_BUILD = (ROOT / "build.gradle").read_text(encoding="utf-8")
PIO_MANIFEST = (ROOT / "pio/src/main/AndroidManifest.xml").read_text(encoding="utf-8")

# targetSdk raised to 35.
require(
    re.search(r"targetSdkVersion\s*=\s*35\b", ROOT_BUILD) is not None,
    "root build.gradle must set targetSdkVersion = 35"
)

# Legacy storage permissions capped.
require(
    'android:name="android.permission.READ_EXTERNAL_STORAGE"' in PIO_MANIFEST and
    'android:maxSdkVersion="32"' in PIO_MANIFEST,
    "READ_EXTERNAL_STORAGE must be capped at maxSdkVersion=32"
)
require(
    'android:name="android.permission.WRITE_EXTERNAL_STORAGE"' in PIO_MANIFEST and
    'android:maxSdkVersion="29"' in PIO_MANIFEST,
    "WRITE_EXTERNAL_STORAGE must be capped at maxSdkVersion=29"
)

# Android 13+ runtime permission declared.
require(
    'android:name="android.permission.POST_NOTIFICATIONS"' in PIO_MANIFEST,
    "POST_NOTIFICATIONS must be declared for Android 13+"
)

# Android 14+ foreground service type declared.
require(
    'android:name="android.permission.FOREGROUND_SERVICE_DATA_SYNC"' in PIO_MANIFEST,
    "FOREGROUND_SERVICE_DATA_SYNC must be declared for Android 14+"
)
require(
    'android:foregroundServiceType="dataSync"' in PIO_MANIFEST,
    "FirmwareOperationService must declare foregroundServiceType=dataSync"
)

# requestLegacyExternalStorage must be false (scoped storage enforced).
require(
    'android:requestLegacyExternalStorage="false"' in PIO_MANIFEST,
    "requestLegacyExternalStorage must be false on targetSdk 35"
)

# Service class exists.
require(
    (ROOT / "pio/src/main/java/com/mio/kitchen/FirmwareOperationService.kt").exists(),
    "FirmwareOperationService.kt is missing"
)

# RuntimePermissionHelper exists.
require(
    (ROOT / "pio/src/main/java/com/mio/kitchen/ui/modern/RuntimePermissionHelper.kt").exists(),
    "RuntimePermissionHelper.kt is missing"
)

# Check FirmwareOperationService handles Android 14+ type.
svc_src = (ROOT / "pio/src/main/java/com/mio/kitchen/FirmwareOperationService.kt").read_text(encoding="utf-8")
require(
    "FOREGROUND_SERVICE_TYPE_DATA_SYNC" in svc_src,
    "FirmwareOperationService must use FOREGROUND_SERVICE_TYPE_DATA_SYNC on Android 14+"
)
require(
    "Build.VERSION_CODES.UPSIDE_DOWN_CAKE" in svc_src,
    "FirmwareOperationService must branch on UPSIDE_DOWN_CAKE (API 34)"
)

# Check RuntimePermissionHelper.
perm_src = (ROOT / "pio/src/main/java/com/mio/kitchen/ui/modern/RuntimePermissionHelper.kt").read_text(encoding="utf-8")
require(
    "POST_NOTIFICATIONS" in perm_src,
    "RuntimePermissionHelper must reference POST_NOTIFICATIONS"
)
require(
    "fun requiredPermissions" in perm_src,
    "RuntimePermissionHelper must expose requiredPermissions()"
)
require(
    "fun areAllGranted" in perm_src,
    "RuntimePermissionHelper must expose areAllGranted()"
)

if ERRORS:
    for e in ERRORS:
        print(f"FAIL: {e}")
    sys.exit(1)

print("PASS: targetSdkVersion raised from 28 to 35")
print("PASS: legacy storage permissions capped (READ maxSdk=32, WRITE maxSdk=29)")
print("PASS: POST_NOTIFICATIONS runtime permission declared for Android 13+")
print("PASS: FOREGROUND_SERVICE_DATA_SYNC declared for Android 14+")
print("PASS: requestLegacyExternalStorage is false (scoped storage enforced)")
print("PASS: FirmwareOperationService uses dataSync foregroundServiceType")
print("PASS: RuntimePermissionHelper abstracts targetSdk-35 permissions")
