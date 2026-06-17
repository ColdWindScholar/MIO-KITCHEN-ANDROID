#!/usr/bin/env python3
"""Validate the static analysis configuration (Stage 16)."""
from __future__ import annotations
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
ERRORS: list[str] = []

def require(condition: bool, message: str) -> None:
    if not condition:
        ERRORS.append(message)

required_files = [
    "config/detekt/detekt.yml",
    "config/lint.xml",
    "config/.shellcheckrc",
    ".editorconfig",
]
for rel in required_files:
    require((ROOT / rel).exists(), f"Stage 16 file is missing: {rel}")

# Detekt config must enable key rules.
detekt = (ROOT / "config/detekt/detekt.yml").read_text(encoding="utf-8")
for token in [
    "complexity:",
    "LongMethod:",
    "LongParameterList:",
    "NestedBlockDepth:",
    "coroutines:",
    "GlobalCoroutineUsage:",
    "empty-blocks:",
    "EmptyCatchBlock:",
    "exceptions:",
    "SwallowedException:",
    "potential-bugs:",
    "UnsafeCallOnNullableType:",
    "UnsafeCast:",
    "style:",
    "MaxLineLength:",
    "VarCouldBeVal:",
    "WildcardImport:",
]:
    require(token in detekt, f"detekt.yml must declare {token}")

# Lint config must exist with overrides.
lint = (ROOT / "config/lint.xml").read_text(encoding="utf-8")
for token in [
    "<lint>",
    "<issue ",
    "ScopedStorage",
    "OldTargetApi",
    "TrustAllX509TrustManager",
]:
    require(token in lint, f"lint.xml must declare {token}")

# EditorConfig must contain ktlint rules.
editorconfig = (ROOT / ".editorconfig").read_text(encoding="utf-8")
for token in [
    "root = true",
    "[*.{kt,kts}]",
    "ktlint_standard_no-wildcard-imports = enabled",
    "ktlint_standard_no-trailing-spaces = enabled",
    "ktlint_standard_final-newline = enabled",
    "ktlint_standard_modifier-ordering = enabled",
    "max_line_length = 140",
]:
    require(token in editorconfig, f".editorconfig must declare {token}")

# ShellCheck config must exist.
shellcheck = (ROOT / "config/.shellcheckrc").read_text(encoding="utf-8")
require("disable=SC2086" in shellcheck, "shellcheck must disable SC2086 (intentional word-splitting)")
require("external-sources" in shellcheck, "shellcheck must declare external-sources")

# Root build.gradle must apply detekt + ktlint plugins.
root_build = (ROOT / "build.gradle").read_text(encoding="utf-8")
require(
    "id 'io.gitlab.arturbosch.detekt' version '1.23.7' apply false" in root_build,
    "root build.gradle must declare detekt plugin 1.23.7"
)
require(
    "id 'org.jlleitschuh.gradle.ktlint' version '12.1.1' apply false" in root_build,
    "root build.gradle must declare ktlint plugin 12.1.1"
)
require(
    "runStaticAnalysis" in root_build,
    "root build.gradle must declare runStaticAnalysis aggregate task"
)

# All three module build.gradle files must apply detekt + ktlint.
for module in ("common", "krscript", "pio"):
    module_build = (ROOT / f"{module}/build.gradle").read_text(encoding="utf-8")
    require(
        "id 'io.gitlab.arturbosch.detekt'" in module_build,
        f"{module}/build.gradle must apply detekt plugin"
    )
    require(
        "id 'org.jlleitschuh.gradle.ktlint'" in module_build,
        f"{module}/build.gradle must apply ktlint plugin"
    )
    require(
        "detekt {" in module_build,
        f"{module}/build.gradle must configure detekt block"
    )
    require(
        "ktlint {" in module_build,
        f"{module}/build.gradle must configure ktlint block"
    )
    require(
        "lintConfig rootProject.file(rootProject.ext.lintConfigPath)" in module_build,
        f"{module}/build.gradle must use centralized lint config"
    )

if ERRORS:
    for e in ERRORS:
        print(f"FAIL: {e}")
    sys.exit(1)

print("PASS: Detekt configuration with key rules is in place")
print("PASS: Android Lint configuration overrides ScopedStorage/OldTargetApi/etc")
print("PASS: ktlint rules are declared in .editorconfig")
print("PASS: ShellCheck configuration is present (SC2086 disabled)")
print("PASS: root build.gradle applies detekt + ktlint plugins")
print("PASS: common/krscript/pio modules apply detekt + ktlint and use centralized config")
print("PASS: runStaticAnalysis aggregate task is declared")
