#!/usr/bin/env python3
"""Validate the legacy removal (Stage 22).

Stage 22 removes:
- `KeepShell.kt` — replaced by `KeepShellRuntime` using `Runtime.exec()` directly.
- `PageConfigReader.kt` — replaced by `PageConfigLoader` (PageConfigRepository +
  RuntimeBinder).
- `ScriptEnvironmen.java` — rewritten as `ScriptEnvironmen.kt` thin facade
  over `LegacyShellBridge`.

Stage 22 adds:
- `LegacyShellBridge.kt` — singleton bridge between legacy API and new
  ShellRuntime.
- `PageConfigLoader.kt` — replacement for `PageConfigReader` using the new
  PageConfigRepository.

This script statically verifies the architectural contract.
"""
from __future__ import annotations
import re
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
ERRORS: list[str] = []

def require(condition: bool, message: str) -> None:
    if not condition:
        ERRORS.append(message)


def strip_comments(src: str) -> str:
    """RU: убираем комментарии и строковые литералы для точного поиска в коде.
       EN: strip comments and string literals for precise code-level search."""
    src = re.sub(r'/\*.*?\*/', '', src, flags=re.DOTALL)
    src = re.sub(r'//[^\n]*', '', src)
    src = re.sub(r'"[^"\n]*"', '""', src)
    return src


# --- Removed legacy files -----------------------------------------------

removed_files = [
    "common/src/main/java/com/omarea/common/shell/KeepShell.kt",
    "krscript/src/main/java/com/omarea/krscript/config/PageConfigReader.kt",
    "krscript/src/main/java/com/omarea/krscript/executor/ScriptEnvironmen.java",
]
for rel in removed_files:
    require(
        not (ROOT / rel).exists(),
        f"Stage 22 must remove legacy file: {rel}"
    )


# --- New files added ----------------------------------------------------

new_files = [
    "krscript/src/main/java/com/omarea/krscript/runtime/LegacyShellBridge.kt",
    "krscript/src/main/java/com/omarea/krscript/executor/ScriptEnvironmen.kt",
    "krscript/src/main/java/com/omarea/krscript/config/PageConfigLoader.kt",
]
for rel in new_files:
    require((ROOT / rel).exists(), f"Stage 22 file is missing: {rel}")


# --- LegacyShellBridge contract -----------------------------------------

bridge_src = (ROOT / "krscript/src/main/java/com/omarea/krscript/runtime/LegacyShellBridge.kt").read_text(encoding="utf-8")
for token in [
    "object LegacyShellBridge",
    "fun isInited()",
    "fun refreshTranslations(",
    "fun init(",
    "fun executeResultRoot(",
    "fun doCmdSync(",
    "fun checkRoot()",
    "fun tryExit()",
    "fun getEnvironment(",
    "RootShellRuntime",
    "UserShellRuntime",
    "ShellRuntime",
    "ShellCommand",
    "ScriptSource",
    "ShellResult",
    "runBlocking",
    "probeRoot",
]:
    require(token in bridge_src, f"LegacyShellBridge.kt must declare/use {token}")

# Bridge must not import legacy KeepShell (only KeepShellRuntime which is OK).
require(
    "import com.omarea.common.shell.KeepShell\n" not in bridge_src and
    "import com.omarea.common.shell.KeepShell(" not in bridge_src,
    "LegacyShellBridge must not import legacy KeepShell"
)


# --- ScriptEnvironmen facade contract ----------------------------------

se_src = (ROOT / "krscript/src/main/java/com/omarea/krscript/executor/ScriptEnvironmen.kt").read_text(encoding="utf-8")
for token in [
    "object ScriptEnvironmen",
    "fun isInited()",
    "fun refreshTranslations(",
    "fun init(",
    "fun executeResultRoot(",
    "fun executeShell(",
    "LegacyShellBridge",
    "@JvmStatic",
]:
    require(token in se_src, f"ScriptEnvironmen.kt must declare/use {token}")

# ScriptEnvironmen must be a facade — it delegates to LegacyShellBridge.
require(
    "LegacyShellBridge.executeResultRoot" in se_src,
    "ScriptEnvironmen.executeResultRoot must delegate to LegacyShellBridge"
)
require(
    "LegacyShellBridge.init" in se_src,
    "ScriptEnvironmen.init must delegate to LegacyShellBridge"
)


# --- PageConfigLoader contract ------------------------------------------

loader_src = (ROOT / "krscript/src/main/java/com/omarea/krscript/config/PageConfigLoader.kt").read_text(encoding="utf-8")
for token in [
    "object PageConfigLoader",
    "fun load(",
    "fun loadFromStream(",
    "PageConfigRepository",
    "RuntimeBinder",
    "AndroidPageConfigSource",
    "PageConfigParser",
]:
    require(token in loader_src, f"PageConfigLoader.kt must declare/use {token}")


# --- KeepShellPublic is now a facade ------------------------------------

ksp_src_raw = (ROOT / "common/src/main/java/com/omarea/common/shell/KeepShellPublic.kt").read_text(encoding="utf-8")
ksp_src = strip_comments(ksp_src_raw)
require(
    "LegacyShellBridge" in ksp_src,
    "KeepShellPublic must delegate to LegacyShellBridge"
)
require(
    "import com.omarea.krscript.runtime.LegacyShellBridge" in ksp_src_raw,
    "KeepShellPublic must import LegacyShellBridge"
)
# KeepShellPublic must NOT own a KeepShell instance (in code, not comments).
require(
    "KeepShell(" not in ksp_src,
    "KeepShellPublic must not construct a KeepShell instance"
)


# --- KeepShellRuntime uses Runtime.exec directly ------------------------

ksr_src = (ROOT / "common/src/main/java/com/omarea/common/shell/runtime/KeepShellRuntime.kt").read_text(encoding="utf-8")
require(
    "Runtime.getRuntime().exec" in ksr_src,
    "KeepShellRuntime must use Runtime.exec() directly (no more KeepShell wrapper)"
)
require(
    "import com.omarea.common.shell.KeepShell" not in ksr_src,
    "KeepShellRuntime must not import legacy KeepShell"
)
# Must still have RootShellRuntime and UserShellRuntime subclasses.
require("class RootShellRuntime" in ksr_src, "RootShellRuntime subclass must remain")
require("class UserShellRuntime" in ksr_src, "UserShellRuntime subclass must remain")


# --- CheckRootStatus uses Runtime.exec directly -------------------------

crs_src = (ROOT / "pio/src/main/java/com/mio/kitchen/permissions/CheckRootStatus.kt").read_text(encoding="utf-8")
require(
    "Runtime.getRuntime().exec" in crs_src,
    "CheckRootStatus must probe root via Runtime.exec (no more KeepShellPublic.checkRoot)"
)
require(
    "import com.omarea.common.shell.KeepShellPublic" not in crs_src,
    "CheckRootStatus must not import KeepShellPublic"
)


# --- Call-sites use new API ---------------------------------------------

# MainActivity must use PageConfigLoader (not PageConfigReader).
main_src = (ROOT / "pio/src/main/java/com/mio/kitchen/MainActivity.kt").read_text(encoding="utf-8")
require(
    "PageConfigLoader.load(" in main_src,
    "MainActivity must call PageConfigLoader.load() (Stage 22)"
)
require(
    "PageConfigReader(" not in main_src,
    "MainActivity must not construct PageConfigReader (Stage 22)"
)
require(
    "LegacyShellBridge.init(" in main_src,
    "MainActivity must call LegacyShellBridge.init() (Stage 22)"
)

# ActionPage must use PageConfigLoader (not PageConfigReader).
ap_src = (ROOT / "pio/src/main/java/com/mio/kitchen/ActionPage.kt").read_text(encoding="utf-8")
require(
    "PageConfigLoader.load(" in ap_src,
    "ActionPage must call PageConfigLoader.load() (Stage 22)"
)
require(
    "PageConfigReader(" not in ap_src,
    "ActionPage must not construct PageConfigReader (Stage 22)"
)

# SplashActivity must call LegacyShellBridge.init().
sa_src = (ROOT / "pio/src/main/java/com/mio/kitchen/SplashActivity.kt").read_text(encoding="utf-8")
require(
    "LegacyShellBridge.init(" in sa_src,
    "SplashActivity must call LegacyShellBridge.init() (Stage 22)"
)

# PageConfigSh must use PageConfigLoader.
pcs_src = (ROOT / "krscript/src/main/java/com/omarea/krscript/config/PageConfigSh.kt").read_text(encoding="utf-8")
require(
    "PageConfigLoader.load(" in pcs_src,
    "PageConfigSh must call PageConfigLoader.load() (Stage 22)"
)
require(
    "PageConfigReader(" not in pcs_src,
    "PageConfigSh must not construct PageConfigReader (Stage 22)"
)


# --- No more KeepShell references (in code, not comments) ---------------

# Check all source files for actual code-level KeepShell references
# (excluding comments and string literals).
for kt in (ROOT / "common/src/main/java/com/omarea/common/shell").rglob("*.kt"):
    if kt.name == "KeepShellPublic.kt" or kt.name == "KeepShellRuntime.kt":
        continue  # these are the facade/wrapper, allowed to reference KeepShell names
    src = kt.read_text(encoding="utf-8", errors="ignore")
    code = strip_comments(src)
    require(
        "KeepShell(" not in code and "KeepShell." not in code,
        f"{kt.name} must not reference legacy KeepShell class in code"
    )


# --- Report --------------------------------------------------------------

if ERRORS:
    for e in ERRORS:
        print(f"FAIL: {e}")
    sys.exit(1)

print("PASS: legacy KeepShell.kt, PageConfigReader.kt, ScriptEnvironmen.java removed")
print("PASS: LegacyShellBridge singleton bridges legacy API to ShellRuntime")
print("PASS: ScriptEnvironmen.kt is a thin facade over LegacyShellBridge")
print("PASS: PageConfigLoader replaces PageConfigReader (uses PageConfigRepository)")
print("PASS: KeepShellPublic is now a facade (no more KeepShell instance)")
print("PASS: KeepShellRuntime uses Runtime.exec() directly (no more KeepShell wrapper)")
print("PASS: CheckRootStatus probes root via Runtime.exec (no more KeepShellPublic.checkRoot)")
print("PASS: MainActivity/ActionPage/SplashActivity/PageConfigSh use new API")
print("PASS: No legacy KeepShell class references in code (comments allowed)")
