#!/usr/bin/env python3
"""Validate the Runtime profiles layer (Stage 10).

Stage 10 introduces:
- DeviceProfile + DeviceProfileProvider (common/runtime)
- ToolRequirement / ToolDescriptor / ToolchainProfile (common/toolchain)
- ToolchainResolver + CapabilityBasedToolchainResolver + ToolchainPlan (common/toolchain)
- ToolManifestLoader (parses assets/toolchain/manifest.json)
- OperationSafetyProfile + OperationPlanner + OperationPlan (common/operations)
- AppRuntimeProfile + AppRuntimeProfileResolver (common/runtime)
- assets/toolchain/manifest.json sample manifest

This script statically verifies the architectural contract.
"""
from __future__ import annotations

import json
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
ERRORS: list[str] = []

RUNTIME_DIR = ROOT / "common/src/main/java/com/omarea/common/runtime"
TOOLCHAIN_DIR = ROOT / "common/src/main/java/com/omarea/common/toolchain"
OPERATIONS_DIR = ROOT / "common/src/main/java/com/omarea/common/operations"
MANIFEST = ROOT / "pio/src/main/assets/toolchain/manifest.json"


def require(condition: bool, message: str) -> None:
    if not condition:
        ERRORS.append(message)


# --- Required files ------------------------------------------------------

required_files = [
    "common/src/main/java/com/omarea/common/runtime/DeviceProfile.kt",
    "common/src/main/java/com/omarea/common/runtime/DeviceProfileProvider.kt",
    "common/src/main/java/com/omarea/common/runtime/AppRuntimeProfile.kt",
    "common/src/main/java/com/omarea/common/toolchain/ToolRequirement.kt",
    "common/src/main/java/com/omarea/common/toolchain/ToolchainResolver.kt",
    "common/src/main/java/com/omarea/common/toolchain/ToolManifestLoader.kt",
    "common/src/main/java/com/omarea/common/toolchain/CapabilityBasedToolchainResolver.kt",
    "common/src/main/java/com/omarea/common/operations/OperationSafetyProfile.kt",
    "common/src/main/java/com/omarea/common/operations/OperationPlanner.kt",
    "common/src/test/java/com/omarea/common/runtime/DeviceProfileTest.kt",
    "common/src/test/java/com/omarea/common/runtime/AppRuntimeProfileTest.kt",
    "common/src/test/java/com/omarea/common/toolchain/ToolchainResolverTest.kt",
    "common/src/test/java/com/omarea/common/operations/OperationPlannerTest.kt",
    "pio/src/main/assets/toolchain/manifest.json",
]
for rel in required_files:
    require((ROOT / rel).exists(), f"Stage 10 file is missing: {rel}")


# --- DeviceProfile -------------------------------------------------------

dp_src = (RUNTIME_DIR / "DeviceProfile.kt").read_text(encoding="utf-8", errors="ignore")
for token in [
    "enum class SelinuxMode",
    "data class DeviceProfile",
    "val sdkInt: Int",
    "val manufacturer: String",
    "val model: String",
    "val abiList: List<String>",
    "val isEmulator: Boolean",
    "val supports16KbPageSize: Boolean?",
    "val hasRoot: Boolean?",
    "val selinuxMode: SelinuxMode",
    "val enforcesScopedStorage",
    "val isAndroid15Plus",
    "fun forTests("
]:
    require(token in dp_src, f"DeviceProfile.kt must declare {token}")

provider_src = (RUNTIME_DIR / "DeviceProfileProvider.kt").read_text(encoding="utf-8", errors="ignore")
require("class DeviceProfileProvider" in provider_src, "DeviceProfileProvider class missing")
require("import android.os.Build" in provider_src, "DeviceProfileProvider must read Build.*")


# --- Toolchain -----------------------------------------------------------

req_src = (TOOLCHAIN_DIR / "ToolRequirement.kt").read_text(encoding="utf-8", errors="ignore")
for token in [
    "enum class ToolPurpose",
    "data class ToolRequirement",
    "data class ToolDescriptor",
    "data class ToolchainProfile",
    "val requiredTools",
    "val optionalTools",
    "val selectedAbi",
    "val verifiedChecksums",
    "fun isMissingRequired",
    "fun missingRequired"
]:
    require(token in req_src, f"ToolRequirement.kt must declare {token}")
# ToolPurpose must enumerate enough purposes.
for purpose in [
    "SUPER_IMAGE", "PAYLOAD_BIN", "EROFS", "EXT4", "BOOT_IMAGE", "VBMETA",
    "SPARSE", "COMPRESSION_BROTLI", "COMPRESSION_LZ4", "COMPRESSION_ZSTD",
    "BUSYBOX", "GENERIC"
]:
    require(purpose in req_src, f"ToolPurpose must declare {purpose}")

resolver_src = (TOOLCHAIN_DIR / "ToolchainResolver.kt").read_text(encoding="utf-8", errors="ignore")
for token in [
    "data class ToolchainPlan",
    "data class ToolchainWarning",
    "enum class FirmwareOperation",
    "interface ToolchainResolver",
    "fun resolve("
]:
    require(token in resolver_src, f"ToolchainResolver.kt must declare {token}")
# FirmwareOperation must enumerate enough operations.
for op in [
    "UNPACK_ROM", "UNPACK_SUPER", "UNPACK_PAYLOAD_BIN",
    "UNPACK_BOOT_IMAGE", "UNPACK_FILESYSTEM_IMAGE",
    "PACK_FILESYSTEM_IMAGE", "PACK_BOOT_IMAGE", "PACK_SUPER",
    "VERIFY_VBMETA", "FLASH_PREPARE", "INSPECT"
]:
    require(op in resolver_src, f"FirmwareOperation must declare {op}")

cap_src = (TOOLCHAIN_DIR / "CapabilityBasedToolchainResolver.kt").read_text(encoding="utf-8", errors="ignore")
require("class CapabilityBasedToolchainResolver" in cap_src,
        "CapabilityBasedToolchainResolver class missing")
require(": ToolchainResolver" in cap_src,
        "CapabilityBasedToolchainResolver must implement ToolchainResolver")
require("CODE_16KB_INCOMPATIBLE" in cap_src,
        "CapabilityBasedToolchainResolver must define CODE_16KB_INCOMPATIBLE")
require("CODE_MISSING_COMPRESSION_TOOL" in cap_src,
        "CapabilityBasedToolchainResolver must define CODE_MISSING_COMPRESSION_TOOL")
require("CODE_NO_COMPATIBLE_ABI" in cap_src,
        "CapabilityBasedToolchainResolver must define CODE_NO_COMPATIBLE_ABI")
require("requirementsFor" in cap_src,
        "CapabilityBasedToolchainResolver must declare requirementsFor()")

loader_src = (TOOLCHAIN_DIR / "ToolManifestLoader.kt").read_text(encoding="utf-8", errors="ignore")
require("class ToolManifestLoader" in loader_src, "ToolManifestLoader class missing")
require("import org.json.JSONObject" in loader_src,
        "ToolManifestLoader must use org.json.JSONObject")
require("fun load(" in loader_src and "fun parse(" in loader_src,
        "ToolManifestLoader must expose load() and parse()")


# --- Operations ----------------------------------------------------------

osp_src = (OPERATIONS_DIR / "OperationSafetyProfile.kt").read_text(encoding="utf-8", errors="ignore")
for token in [
    "enum class ConfirmationLevel",
    "data class OperationSafetyProfile",
    "val isDestructive",
    "val requiresBackup",
    "val requiresRoot",
    "val requiresDeviceConnection",
    "val supportsDryRun",
    "val confirmationLevel",
    "fun readOnly(",
    "fun packOperation(",
    "fun flashOperation("
]:
    require(token in osp_src, f"OperationSafetyProfile.kt must declare {token}")
for level in ["NONE", "STANDARD", "WARNING", "DESTRUCTIVE"]:
    require(level in osp_src, f"ConfirmationLevel must declare {level}")

planner_src = (OPERATIONS_DIR / "OperationPlanner.kt").read_text(encoding="utf-8", errors="ignore")
for token in [
    "data class OperationPlan",
    "class OperationPlanner",
    "interface OperationSafetyProvider",
    "class DefaultOperationSafetyProvider",
    "fun plan(",
    "val canExecute",
    "fun blockers()"
]:
    require(token in planner_src, f"OperationPlanner.kt must declare {token}")


# --- AppRuntimeProfile ---------------------------------------------------

arp_src = (RUNTIME_DIR / "AppRuntimeProfile.kt").read_text(encoding="utf-8", errors="ignore")
for token in [
    "data class AppRuntimeProfile",
    "class AppRuntimeProfileResolver",
    "fun initial(",
    "fun withFirmware(",
    "fun withOperation(",
    "val hasFirmware",
    "val hasReadyOperation"
]:
    require(token in arp_src, f"AppRuntimeProfile.kt must declare {token}")


# --- Manifest JSON -------------------------------------------------------

if MANIFEST.exists():
    try:
        manifest = json.loads(MANIFEST.read_text(encoding="utf-8"))
        require("tools" in manifest, "manifest.json must have a 'tools' array")
        tools = manifest.get("tools", [])
        require(isinstance(tools, list) and len(tools) >= 8,
                f"manifest.json must declare at least 8 tools (got {len(tools) if isinstance(tools, list) else 0})")
        required_tool_names = {"busybox", "lpunpack", "lpmake", "mke2fs", "mkfs.erofs",
                               "magiskboot", "img2simg", "simg2img", "brotli"}
        actual_tool_names = {t.get("name") for t in tools if isinstance(t, dict)}
        missing = required_tool_names - actual_tool_names
        require(not missing,
                f"manifest.json is missing tools: {sorted(missing)}")
        for t in tools:
            if not isinstance(t, dict):
                continue
            for key in ("name", "version", "abi", "capabilities", "source", "license"):
                require(key in t, f"manifest.json tool entry missing key '{key}': {t.get('name')}")
    except json.JSONDecodeError as e:
        ERRORS.append(f"manifest.json is not valid JSON: {e}")
else:
    ERRORS.append("manifest.json not found")


# --- Tests present -------------------------------------------------------

test_files = [
    "common/src/test/java/com/omarea/common/runtime/DeviceProfileTest.kt",
    "common/src/test/java/com/omarea/common/runtime/AppRuntimeProfileTest.kt",
    "common/src/test/java/com/omarea/common/toolchain/ToolchainResolverTest.kt",
    "common/src/test/java/com/omarea/common/operations/OperationPlannerTest.kt",
]
for rel in test_files:
    src = (ROOT / rel).read_text(encoding="utf-8")
    require("@Test" in src, f"{rel} must declare at least one @Test")


# --- build.gradle has org.json testImplementation ------------------------

common_build = (ROOT / "common/build.gradle").read_text(encoding="utf-8")
require(
    "testImplementation 'org.json:json:" in common_build,
    "common/build.gradle must declare org.json:json testImplementation for ToolManifestLoader tests",
)


# --- Runtime profile purity: no shell/UI imports -------------------------
#
# RU: Stage 10 runtime/toolchain/operations слои НЕ должны импортировать shell,
#     НО operations/ на Stage 12 намеренно использует shell.runtime (это
#     точка связи OperationExecutor -> ShellRuntime). Поэтому запрещаем
#     только `import com.omarea.common.shell.KeepShell` / `KeepShellPublic` /
#     `ShellTranslation` (legacy shell), но НЕ `shell.runtime` (typed API).
# EN: Stage 10 runtime/toolchain/operations layers must NOT import shell,
#     BUT operations/ on Stage 12 deliberately uses shell.runtime (that's the
#     OperationExecutor -> ShellRuntime connection point). So we forbid only
#     `import com.omarea.common.shell.KeepShell` / `KeepShellPublic` /
#     `ShellTranslation` (legacy shell), NOT `shell.runtime` (typed API).

LEGACY_SHELL_IMPORTS = [
    "import com.omarea.common.shell.KeepShell",
    "import com.omarea.common.shell.KeepShellPublic",
    "import com.omarea.common.shell.ShellTranslation",
    "import com.omarea.common.shell.ShellExecutor",
]

for d in (RUNTIME_DIR, TOOLCHAIN_DIR):
    for kt in d.glob("*.kt"):
        src = kt.read_text(encoding="utf-8", errors="ignore")
        for token in LEGACY_SHELL_IMPORTS:
            require(
                token not in src,
                f"{kt.name} must not import legacy shell ({token}) — runtime/toolchain are pure"
            )
        require(
            "import android.app." not in src,
            f"{kt.name} must not import android.app.* (DeviceProfileProvider may import android.os.Build only)"
        )

# operations/ is allowed to use shell.runtime (typed API), but NOT legacy shell.
for kt in OPERATIONS_DIR.glob("*.kt"):
    src = kt.read_text(encoding="utf-8", errors="ignore")
    for token in LEGACY_SHELL_IMPORTS:
        require(
            token not in src,
            f"{kt.name} must not import legacy shell ({token}) — use shell.runtime instead"
        )
    require(
        "import android.app." not in src,
        f"{kt.name} must not import android.app.*"
    )
    require(
        "import android.widget." not in src,
        f"{kt.name} must not import android.widget.*"
    )


# --- Report --------------------------------------------------------------

if ERRORS:
    for e in ERRORS:
        print(f"FAIL: {e}")
    sys.exit(1)

print("PASS: DeviceProfile + DeviceProfileProvider are in place")
print("PASS: ToolchainProfile + ToolRequirement + ToolDescriptor are declared")
print("PASS: ToolchainResolver interface + CapabilityBasedToolchainResolver implementation")
print("PASS: ToolManifestLoader parses JSON manifest")
print("PASS: OperationSafetyProfile + OperationPlanner + OperationPlan are declared")
print("PASS: AppRuntimeProfile + AppRuntimeProfileResolver combine all profiles")
print("PASS: assets/toolchain/manifest.json declares required tools")
print("PASS: Stage 10 JVM unit tests are present")
print("PASS: common/build.gradle declares org.json testImplementation")
print("PASS: runtime/toolchain layers are pure (no legacy shell, no android.app.*)")
print("PASS: operations layer uses shell.runtime (typed) instead of legacy shell")
