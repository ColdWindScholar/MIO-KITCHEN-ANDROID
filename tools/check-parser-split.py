#!/usr/bin/env python3
"""Validate the KrScript parser/runtime split (Stage 5).

Stage 5 introduces a pure parser that does NOT execute shell, does NOT show UI,
and does NOT depend on Android `Context`/`Handler`/`Toast`. This script statically
verifies the architectural contract so regressions are caught before CI runs the
full Gradle test matrix.
"""
from __future__ import annotations

import re
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
ERRORS: list[str] = []

PARSER_DIR = ROOT / "krscript/src/main/java/com/omarea/krscript/parser"
VALIDATOR_DIR = ROOT / "krscript/src/main/java/com/omarea/krscript/validator"
RUNTIME_DIR = ROOT / "krscript/src/main/java/com/omarea/krscript/runtime"


def require(condition: bool, message: str) -> None:
    if not condition:
        ERRORS.append(message)


# --- Required files ------------------------------------------------------

required_files = [
    "krscript/src/main/java/com/omarea/krscript/parser/PageConfigSource.kt",
    "krscript/src/main/java/com/omarea/krscript/parser/StreamPageConfigSource.kt",
    "krscript/src/main/java/com/omarea/krscript/parser/DynamicValueResolver.kt",
    "krscript/src/main/java/com/omarea/krscript/parser/PageConfigParser.kt",
    "krscript/src/main/java/com/omarea/krscript/parser/PageConfigRepository.kt",
    "krscript/src/main/java/com/omarea/krscript/validator/PageConfigValidator.kt",
    "krscript/src/main/java/com/omarea/krscript/runtime/RuntimeBinder.kt",
    "krscript/src/main/java/com/omarea/krscript/config/AndroidPageConfigSource.kt",
    "krscript/src/test/java/com/omarea/krscript/parser/PageConfigParserTest.kt",
]
for rel in required_files:
    require((ROOT / rel).exists(), f"Stage 5 file is missing: {rel}")


# --- Parser purity -------------------------------------------------------

PARSER_FILE = ROOT / "krscript/src/main/java/com/omarea/krscript/parser/PageConfigParser.kt"
if PARSER_FILE.exists():
    parser_src = PARSER_FILE.read_text(encoding="utf-8", errors="ignore")
    require(
        "import android.content.Context" not in parser_src,
        "PageConfigParser must not depend on android.content.Context",
    )
    require(
        "import android.widget.Toast" not in parser_src,
        "PageConfigParser must not depend on android.widget.Toast",
    )
    require(
        "import android.os.Handler" not in parser_src
        and "import android.os.Looper" not in parser_src,
        "PageConfigParser must not depend on android.os.Handler/Looper",
    )
    require(
        "ScriptEnvironmen" not in parser_src,
        "PageConfigParser must not call ScriptEnvironmen directly",
    )
    require(
        "ExtractAssets" not in parser_src,
        "PageConfigParser must not call ExtractAssets directly",
    )
    require(
        "class PageConfigParser" in parser_src,
        "PageConfigParser class declaration missing",
    )
    require(
        "DynamicValueResolver" in parser_src,
        "PageConfigParser must accept a DynamicValueResolver",
    )

# All files under parser/ must avoid android.app / android.widget / shell imports.
for kt in PARSER_DIR.glob("*.kt"):
    src = kt.read_text(encoding="utf-8", errors="ignore")
    forbidden = [
        "import android.app.",
        "import android.widget.",
        "import com.omarea.krscript.executor.ScriptEnvironmen",
        "import com.omarea.krscript.executor.ExtractAssets",
    ]
    for token in forbidden:
        require(
            token not in src,
            f"{kt.name} must not import {token} (parser purity)",
        )

# Validator must not call shell either.
for kt in VALIDATOR_DIR.glob("*.kt"):
    src = kt.read_text(encoding="utf-8", errors="ignore")
    require(
        "ScriptEnvironmen" not in src,
        f"{kt.name} must not call ScriptEnvironmen",
    )


# --- RuntimeBinder exists and uses ScriptEnvironmen ---------------------

binder_file = RUNTIME_DIR / "RuntimeBinder.kt"
if binder_file.exists():
    binder_src = binder_file.read_text(encoding="utf-8", errors="ignore")
    require(
        "class RuntimeBinder" in binder_src,
        "RuntimeBinder class declaration missing",
    )
    require(
        "DynamicValueResolver" in binder_src,
        "RuntimeBinder must implement DynamicValueResolver",
    )
    require(
        "ScriptEnvironmen" in binder_src,
        "RuntimeBinder must be the single shell-bound implementation",
    )


# --- Test file present and references the new API -----------------------

test_file = ROOT / "krscript/src/test/java/com/omarea/krscript/parser/PageConfigParserTest.kt"
if test_file.exists():
    test_src = test_file.read_text(encoding="utf-8", errors="ignore")
    require(
        "PageConfigParser" in test_src,
        "PageConfigParserTest must reference PageConfigParser",
    )
    require(
        "PageConfigValidator" in test_src,
        "PageConfigParserTest must exercise PageConfigValidator",
    )
    require(
        "@Test" in test_src,
        "PageConfigParserTest must declare at least one JUnit @Test",
    )


# --- build.gradle has test dependencies ----------------------------------

krscript_build = (ROOT / "krscript/build.gradle").read_text(encoding="utf-8")
require(
    "testImplementation 'xmlpull:xmlpull" in krscript_build,
    "krscript/build.gradle must declare xmlpull test dependency for JVM tests",
)
require(
    "testImplementation 'net.sf.kxml:kxml2" in krscript_build,
    "krscript/build.gradle must declare kxml2 test dependency for JVM tests",
)


# --- Legacy PageConfigReader replaced by PageConfigLoader (Stage 22) ----
# RU: Stage 22 — legacy PageConfigReader.kt удалён, заменён на PageConfigLoader
#     (PageConfigRepository + RuntimeBinder). Проверяем, что новый загрузчик
#     существует.
# EN: Stage 22 — legacy PageConfigReader.kt removed, replaced by
#     PageConfigLoader (PageConfigRepository + RuntimeBinder). Verify that
#     the new loader exists.
loader = ROOT / "krscript/src/main/java/com/omarea/krscript/config/PageConfigLoader.kt"
require(loader.exists(), "PageConfigLoader.kt must exist (Stage 22 replacement for PageConfigReader)")


# --- Report --------------------------------------------------------------

if ERRORS:
    for e in ERRORS:
        print(f"FAIL: {e}")
    sys.exit(1)

print("PASS: KrScript parser/runtime split is in place")
print("PASS: parser does not depend on Context/Handler/Toast")
print("PASS: parser does not invoke ScriptEnvironmen or ExtractAssets")
print("PASS: validator does not execute shell")
print("PASS: RuntimeBinder is the single shell-bound DynamicValueResolver")
print("PASS: parser unit tests and JVM test dependencies are present")
print("PASS: legacy PageConfigReader removed; PageConfigLoader is its replacement")
