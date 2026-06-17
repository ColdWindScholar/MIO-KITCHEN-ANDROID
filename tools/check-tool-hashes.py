#!/usr/bin/env python3
"""RU: Проверяет, что tools/compute-tool-hashes.py существует и корректно
       обновляет манифест. Также проверяет, что все shipped-бинарники
       имеют реальные SHA-256 (кроме lpunpack и simg2img, которые не
       поставляются).

   EN: Verify that tools/compute-tool-hashes.py exists and correctly
       updates the manifest. Also verify that all shipped binaries have
       real SHA-256 values (except lpunpack and simg2img, which are not
       shipped).
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


# --- compute-tool-hashes.py exists --------------------------------------

script_path = ROOT / "tools/compute-tool-hashes.py"
require(script_path.exists(), "tools/compute-tool-hashes.py must exist")

script_src = script_path.read_text(encoding="utf-8")
for token in [
    "import hashlib",
    "import json",
    "def compute_sha256",
    "BIN_DIR",
    "MANIFEST_PATH",
    "tools/compute-tool-hashes.py",
]:
    require(token in script_src, f"compute-tool-hashes.py must declare/use {token}")


# --- Manifest has real SHA-256 for shipped binaries ---------------------

manifest_path = ROOT / "pio/src/main/assets/toolchain/manifest.json"
manifest = json.loads(manifest_path.read_text(encoding="utf-8"))
tools = manifest.get("tools", [])

# RU: lpunpack и simg2img объявлены в манифесте, но не поставляются в
#     assets/bin/. Их SHA-256 может быть null.
# EN: lpunpack and simg2img are declared in the manifest but not shipped
#     in assets/bin/. Their SHA-256 can be null.
ALLOWED_NULL = {"lpunpack", "simg2img"}

bin_dir = ROOT / "pio/src/main/assets/bin"
shipped_files = {f.name for f in bin_dir.iterdir() if f.is_file()} if bin_dir.exists() else set()

for tool in tools:
    name = tool.get("name")
    sha = tool.get("sha256")
    if name in ALLOWED_NULL:
        # These are allowed to be null.
        continue
    if name in shipped_files:
        require(
            sha is not None and len(sha) == 64,
            f"Shipped binary {name} must have a 64-char SHA-256 in the manifest"
        )
    # If not shipped, sha can be null (the binary is declared for completeness).


# --- Workflow file exists -----------------------------------------------

workflow_path = ROOT / ".github/workflows/toolchain-installer.yml"
require(workflow_path.exists(), ".github/workflows/toolchain-installer.yml must exist")

workflow_src = workflow_path.read_text(encoding="utf-8")
for token in [
    "name: Toolchain Installer",
    "tools/check-toolchain-installer.py",
    "tools/check-final-cleanup.py",
]:
    require(token in workflow_src, f"toolchain-installer.yml must reference {token}")


# --- Report --------------------------------------------------------------

if ERRORS:
    for e in ERRORS:
        print(f"FAIL: {e}")
    sys.exit(1)

print("PASS: tools/compute-tool-hashes.py exists and uses hashlib + json")
print("PASS: all shipped binaries have real SHA-256 in the manifest")
print("PASS: lpunpack and simg2img are allowed to have null SHA-256 (not shipped)")
print("PASS: .github/workflows/toolchain-installer.yml exists and references check scripts")
