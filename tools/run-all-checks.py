#!/usr/bin/env python3
"""RU: Единый runner для всех статических проверок проекта MIO-KITCHEN.
   EN: Unified runner for all static checks of the MIO-KITCHEN project.

Запускает все инструменты из `tools/check-*.py` + `validate-localization.py`,
а также `bash -n` для shell-скриптов. Используется CI и локально.

Runs every `tools/check-*.py` + `validate-localization.py` and `bash -n` on
shell scripts. Used by CI and locally.
"""
from __future__ import annotations

import subprocess
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]

CHECKS = [
    "tools/validate-localization.py",
    "tools/check-known-regressions.py",
    "tools/check-build-modernization.py",
    "tools/check-storage-workspace.py",
    "tools/check-parser-split.py",
    "tools/check-shell-runtime.py",
    "tools/check-firmware-analyzer.py",
    "tools/check-ui-modernization.py",
    "tools/check-runtime-profiles.py",
    "tools/check-docs-monolingual.py",
    "tools/check-architecture.py",
    "tools/check-toolchain-installer.py",
    "tools/check-operation-executor.py",
    "tools/check-targetsdk-35.py",
    "tools/check-export-policy.py",
    "tools/check-ui-migration.py",
    "tools/check-static-analysis.py",
    "tools/check-coverage.py",
    "tools/check-instrumented-tests.py",
    "tools/check-user-docs.py",
    "tools/check-hybrid-migration.py",
    "tools/check-legacy-removal.py",
    "tools/check-final-cleanup.py",
    "tools/check-tool-hashes.py",
]

SHELL_SCRIPTS = [
    "pio/src/main/assets/script/tool.sh",
    "pio/src/main/assets/script/start.sh",
    "pio/src/main/assets/script2/executor.sh",
]

failures = []

print("=" * 60)
print("MIO-KITCHEN unified checks")
print("=" * 60)

for check in CHECKS:
    print(f"\n--- {check} ---")
    result = subprocess.run(
        ["python3", str(ROOT / check)],
        cwd=str(ROOT),
        capture_output=False,
    )
    if result.returncode != 0:
        failures.append(check)

for script in SHELL_SCRIPTS:
    path = ROOT / script
    if not path.exists():
        print(f"\n--- bash -n {script} ---\nSKIP: file not found")
        continue
    print(f"\n--- bash -n {script} ---")
    result = subprocess.run(
        ["bash", "-n", str(path)],
        cwd=str(ROOT),
    )
    if result.returncode != 0:
        failures.append(f"bash -n {script}")

print("\n" + "=" * 60)
if failures:
    print(f"FAILED ({len(failures)}):")
    for f in failures:
        print(f"  - {f}")
    sys.exit(1)
else:
    print("ALL CHECKS PASSED")
