#!/usr/bin/env python3
"""RU: Архитектурные тесты: границы между слоями.
   EN: Architecture tests: layer boundaries.

Правила:
  - parser/ не импортирует shell/UI/Context
  - validator/ не импортирует shell/UI/Context
  - firmware/ не импортирует shell/UI/Context
  - shell/runtime/ не импортирует firmware/UI
  - pio/ui/modern/ FirmwareAnalysisViewModel не импортирует Activity/Handler

Rules:
  - parser/ does not import shell/UI/Context
  - validator/ does not import shell/UI/Context
  - firmware/ does not import shell/UI/Context
  - shell/runtime/ does not import firmware/UI
  - pio/ui/modern/ FirmwareAnalysisViewModel does not import Activity/Handler
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


def strip_comments_and_strings(src: str) -> str:
    src = re.sub(r'/\*.*?\*/', '', src, flags=re.DOTALL)
    src = re.sub(r'//[^\n]*', '', src)
    src = re.sub(r'"[^"\n]*"', '""', src)
    return src


def check_layer(layer_dir: Path, forbidden_imports: list[str]) -> None:
    if not layer_dir.exists():
        return
    for kt in layer_dir.rglob("*.kt"):
        src = kt.read_text(encoding="utf-8", errors="ignore")
        code = strip_comments_and_strings(src)
        for token in forbidden_imports:
            if token in code:
                ERRORS.append(
                    f"{kt.relative_to(ROOT)} imports forbidden token '{token}' "
                    f"(layer: {layer_dir.relative_to(ROOT)})"
                )


# Layer: parser — must NOT import shell/UI/Context.
check_layer(
    ROOT / "krscript/src/main/java/com/omarea/krscript/parser",
    [
        "import com.omarea.krscript.executor.ScriptEnvironmen",
        "import com.omarea.krscript.executor.ExtractAssets",
        "import android.content.Context",
        "import android.app.",
        "import android.widget.",
        "import android.os.Handler",
        "import android.os.Looper",
    ],
)

# Layer: validator — must NOT import shell/UI/Context.
check_layer(
    ROOT / "krscript/src/main/java/com/omarea/krscript/validator",
    [
        "import com.omarea.krscript.executor.ScriptEnvironmen",
        "import android.content.Context",
        "import android.app.",
        "import android.widget.",
    ],
)

# Layer: firmware — must NOT import shell/UI/Context.
check_layer(
    ROOT / "common/src/main/java/com/omarea/common/firmware",
    [
        "import com.omarea.common.shell",
        "import android.content.Context",
        "import android.app.",
        "import android.widget.",
    ],
)

# Layer: shell/runtime — must NOT import firmware/UI.
check_layer(
    ROOT / "common/src/main/java/com/omarea/common/shell/runtime",
    [
        "import com.omarea.common.firmware",
        "import android.app.Activity",
        "import android.widget.",
    ],
)

# Layer: runtime — must NOT import shell/UI (DeviceProfileProvider reads Build only).
check_layer(
    ROOT / "common/src/main/java/com/omarea/common/runtime",
    [
        "import com.omarea.common.shell",
        "import android.app.",
        "import android.widget.",
    ],
)

# Layer: toolchain — must NOT import shell/UI.
check_layer(
    ROOT / "common/src/main/java/com/omarea/common/toolchain",
    [
        "import com.omarea.common.shell",
        "import android.app.",
        "import android.widget.",
    ],
)

# Layer: operations — may use shell.runtime (typed API, Stage 12) but NOT
# legacy shell (KeepShell/KeepShellPublic/ShellTranslation/ShellExecutor) and
# NOT android.app.*/android.widget.*.
LEGACY_SHELL_IMPORTS_FOR_OPS = [
    "import com.omarea.common.shell.KeepShell",
    "import com.omarea.common.shell.KeepShellPublic",
    "import com.omarea.common.shell.ShellTranslation",
    "import com.omarea.common.shell.ShellExecutor",
]
check_layer(
    ROOT / "common/src/main/java/com/omarea/common/operations",
    LEGACY_SHELL_IMPORTS_FOR_OPS + [
        "import android.app.",
        "import android.widget.",
    ]
)


# Specific file: FirmwareAnalysisViewModel must NOT use Activity/Handler.
vm_file = ROOT / "pio/src/main/java/com/mio/kitchen/ui/modern/FirmwareAnalysisViewModel.kt"
if vm_file.exists():
    src = vm_file.read_text(encoding="utf-8")
    code = strip_comments_and_strings(src)
    for token in [
        "import android.app.Activity",
        "import android.os.Handler",
        "import android.os.Looper",
        "activity!!",
        "context!!",
    ]:
        require(token not in code, f"FirmwareAnalysisViewModel must not use {token}")


# Specific file: FirmwareProfileFormatter must be pure.
fmt_file = ROOT / "pio/src/main/java/com/mio/kitchen/ui/modern/FirmwareProfileFormatter.kt"
if fmt_file.exists():
    src = fmt_file.read_text(encoding="utf-8")
    code = strip_comments_and_strings(src)
    for token in [
        "import android.view",
        "import android.widget",
        "import android.app.Activity",
    ]:
        require(token not in code, f"FirmwareProfileFormatter must not use {token}")


# Report

if ERRORS:
    for e in ERRORS:
        print(f"FAIL: {e}")
    sys.exit(1)

print("PASS: parser layer does not import shell/UI/Context")
print("PASS: validator layer does not import shell/UI/Context")
print("PASS: firmware layer does not import shell/UI/Context")
print("PASS: shell/runtime layer does not import firmware/UI")
print("PASS: runtime layer does not import legacy shell/UI (Build-only provider allowed)")
print("PASS: toolchain layer does not import legacy shell/UI")
print("PASS: operations layer uses shell.runtime (typed) instead of legacy shell")
print("PASS: FirmwareAnalysisViewModel has no Activity/Handler dependencies")
print("PASS: FirmwareProfileFormatter is pure (no View/widget imports)")
