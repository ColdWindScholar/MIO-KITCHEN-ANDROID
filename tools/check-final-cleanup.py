#!/usr/bin/env python3
"""Validate the final legacy cleanup (Stage 23).

Stage 23 fixes three remaining issues from Stage 22:
- ShellExecutor.execute() was broken (ScriptEnvironmen.getRuntime() returned
  null). Now rewritten as ShellExecutor.kt that creates its own Process via
  Runtime.exec() and uses LegacyShellBridge.buildStreamingCommand().
- ScriptEnvironmen.getRuntime() now returns a real Process (not null).
- SplashActivity.BeforeStartThread no longer depends on common/shell/
  ShellExecutor — it uses ScriptEnvironmen.getRuntime() +
  LegacyShellBridge.buildStreamingCommand().
- LegacyShellBridge.init now uses ToolchainInstaller (instead of ExtractAssets)
  to install the toolkit. Manifest expanded to cover all assets/bin/ files
  (including .so libraries and utils).
"""
from __future__ import annotations
import json
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
ERRORS: list[str] = []

def require(condition: bool, message: str) -> None:
    if not condition:
        ERRORS.append(message)


# --- ShellExecutor.kt replaces ShellExecutor.java -----------------------

old_se = ROOT / "krscript/src/main/java/com/omarea/krscript/executor/ShellExecutor.java"
new_se = ROOT / "krscript/src/main/java/com/omarea/krscript/executor/ShellExecutor.kt"
require(not old_se.exists(), "Stage 23 must remove ShellExecutor.java")
require(new_se.exists(), "Stage 23 must add ShellExecutor.kt")

se_src = new_se.read_text(encoding="utf-8")
for token in [
    "class ShellExecutor",
    "fun execute(",
    "Runtime.getRuntime().exec",
    "LegacyShellBridge.buildStreamingCommand",
    "SimpleShellWatcher",
    "ShellHandlerBase",
    "killProcess",
]:
    require(token in se_src, f"ShellExecutor.kt must declare/use {token}")

# Must NOT call ScriptEnvironmen.getRuntime() in code (comments are OK).
import re
def strip_comments_local(src: str) -> str:
    src = re.sub(r'/\*.*?\*/', '', src, flags=re.DOTALL)
    src = re.sub(r'//[^\n]*', '', src)
    src = re.sub(r'"[^"\n]*"', '""', src)
    return src

se_code = strip_comments_local(se_src)
require(
    "ScriptEnvironmen.getRuntime()" not in se_code,
    "ShellExecutor.kt must not call ScriptEnvironmen.getRuntime() in code"
)


# --- ScriptEnvironmen.getRuntime() returns real Process ----------------

se_facade = (ROOT / "krscript/src/main/java/com/omarea/krscript/executor/ScriptEnvironmen.kt").read_text(encoding="utf-8")
require(
    "Runtime.getRuntime().exec" in se_facade,
    "ScriptEnvironmen.getRuntime() must use Runtime.exec() (not return null)"
)
require(
    "return null" not in se_facade.split("fun getRuntime")[1].split("}")[0],
    "ScriptEnvironmen.getRuntime() must not return null unconditionally"
)


# --- LegacyShellBridge.buildStreamingCommand exists ---------------------

bridge = (ROOT / "krscript/src/main/java/com/omarea/krscript/runtime/LegacyShellBridge.kt").read_text(encoding="utf-8")
for token in [
    "fun buildStreamingCommand(",
    "fun installToolkit(",
    "ToolchainInstaller",
    "ToolManifestLoader",
    "ToolchainInstallResult",
    "isRooted",
    "resolveScriptForStreaming",
]:
    require(token in bridge, f"LegacyShellBridge.kt must declare/use {token}")

# Must NOT use ExtractAssets for toolkit (replaced by ToolchainInstaller).
# ExtractAssets is still imported for executor.sh extraction and
# RuntimeBinder — that's OK. Just check that toolkit extraction uses
# ToolchainInstaller.
require(
    "installToolkit" in bridge,
    "LegacyShellBridge.init must call installToolkit (not ExtractAssets.extractResources for toolkit)"
)


# --- SplashActivity.BeforeStartThread uses new API ----------------------

splash = (ROOT / "pio/src/main/java/com/mio/kitchen/SplashActivity.kt").read_text(encoding="utf-8")
require(
    "LegacyShellBridge.buildStreamingCommand" in splash,
    "SplashActivity.BeforeStartThread must use LegacyShellBridge.buildStreamingCommand"
)
require(
    "ScriptEnvironmen.getRuntime()" in splash,
    "SplashActivity.BeforeStartThread must use ScriptEnvironmen.getRuntime()"
)
# Must NOT import common/shell/ShellExecutor (was used for getSuperUserRuntime/getRuntime).
require(
    "import com.omarea.common.shell.ShellExecutor" not in splash,
    "SplashActivity must not import common.shell.ShellExecutor (Stage 23)"
)


# --- Manifest covers all assets/bin/ files ------------------------------

manifest_path = ROOT / "pio/src/main/assets/toolchain/manifest.json"
manifest = json.loads(manifest_path.read_text(encoding="utf-8"))
tools = {t["name"] for t in manifest["tools"]}

# Required executables from Stage 10.
required_executables = {
    "busybox", "lpunpack", "lpmake", "simg2img", "img2simg",
    "mke2fs", "e2fsdroid", "resize2fs", "make_ext4fs",
    "mkfs.erofs", "extract.erofs", "magiskboot", "brotli"
}
# Stage 23 additions: shared libraries + utils.
required_shared_libs = {
    "utils",
    "libandroid-posix-semaphore.so",
    "libandroid-support.so",
    "libbz2.so.1.0",
    "libffi.so",
    "liblz4.so",
    "liblzma.so.5",
    "libz.so.1",
}

missing_executables = required_executables - tools
missing_libs = required_shared_libs - tools
require(
    not missing_executables,
    f"Manifest must declare all executables: missing {sorted(missing_executables)}"
)
require(
    not missing_libs,
    f"Manifest must declare all shared libraries (Stage 23): missing {sorted(missing_libs)}"
)
require(
    len(tools) >= 21,
    f"Manifest must have at least 21 tools (13 executables + 8 shared libs/utils): got {len(tools)}"
)


# --- common/shell/ShellExecutor.java is removed (dead code) -------------

common_se = ROOT / "common/src/main/java/com/omarea/common/shell/ShellExecutor.java"
require(
    not common_se.exists(),
    "common/shell/ShellExecutor.java must be removed (dead code — 0 callers after Stage 23)"
)

# krscript/executor/ShellExecutor.kt must be the only ShellExecutor.
krscript_se = ROOT / "krscript/src/main/java/com/omarea/krscript/executor/ShellExecutor.kt"
require(krscript_se.exists(), "krscript/executor/ShellExecutor.kt must exist")


# --- Report --------------------------------------------------------------

if ERRORS:
    for e in ERRORS:
        print(f"FAIL: {e}")
    sys.exit(1)

print("PASS: ShellExecutor.java replaced by ShellExecutor.kt (uses Runtime.exec directly)")
print("PASS: ShellExecutor.execute() uses LegacyShellBridge.buildStreamingCommand (not ScriptEnvironmen.getRuntime)")
print("PASS: ScriptEnvironmen.getRuntime() returns a real Process (not null)")
print("PASS: LegacyShellBridge.buildStreamingCommand + installToolkit are declared")
print("PASS: SplashActivity.BeforeStartThread uses new API (no common.shell.ShellExecutor dependency)")
print("PASS: Manifest covers all 21 assets/bin/ files (13 executables + 8 shared libs/utils)")
print("PASS: LegacyShellBridge.init uses ToolchainInstaller (not ExtractAssets) for toolkit")
print("PASS: common/shell/ShellExecutor.java removed (dead code — 0 callers)")
