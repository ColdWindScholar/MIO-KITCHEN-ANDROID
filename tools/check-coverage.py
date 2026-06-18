#!/usr/bin/env python3
"""Validate the coverage reporting configuration (Stage 17)."""
from __future__ import annotations
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
ERRORS: list[str] = []

def require(condition: bool, message: str) -> None:
    if not condition:
        ERRORS.append(message)

root_build = (ROOT / "build.gradle").read_text(encoding="utf-8")
require(
    "id 'org.jetbrains.kotlinx.kover' version '0.8.3' apply false" in root_build,
    "root build.gradle must declare Kover plugin 0.8.3"
)
require(
    "runCoverageReport" in root_build,
    "root build.gradle must declare runCoverageReport aggregate task"
)

for module in ("common", "krscript", "pio"):
    module_build = (ROOT / f"{module}/build.gradle").read_text(encoding="utf-8")
    require(
        "id 'org.jetbrains.kotlinx.kover'" in module_build,
        f"{module}/build.gradle must apply Kover plugin"
    )

if ERRORS:
    for e in ERRORS:
        print(f"FAIL: {e}")
    sys.exit(1)

print("PASS: Kover plugin 0.8.3 declared at root level")
print("PASS: Kover applied to common/krscript/pio modules")
print("PASS: runCoverageReport aggregate task is declared")
