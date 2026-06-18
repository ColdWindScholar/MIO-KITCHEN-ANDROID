#!/usr/bin/env python3
"""RU: Генерирует SHA-256 хеши для всех бинарников в assets/bin/ и
       обновляет assets/toolchain/manifest.json с реальными значениями.

   EN: Generates SHA-256 hashes for every binary in assets/bin/ and
       updates assets/toolchain/manifest.json with real values.

Использование / Usage:
    python3 tools/compute-tool-hashes.py

После запуска все `"sha256": null` в манифесте заменяются на реальные хеши.
After running, every `"sha256": null` in the manifest is replaced with a real hash.
"""
from __future__ import annotations

import hashlib
import json
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
BIN_DIR = ROOT / "pio/src/main/assets/bin"
MANIFEST_PATH = ROOT / "pio/src/main/assets/toolchain/manifest.json"

CHUNK_SIZE = 64 * 1024


def compute_sha256(path: Path) -> str:
    """RU: Считает SHA-256 файла. EN: Compute the SHA-256 of a file."""
    digest = hashlib.sha256()
    with path.open("rb") as f:
        while True:
            chunk = f.read(CHUNK_SIZE)
            if not chunk:
                break
            digest.update(chunk)
    return digest.hexdigest()


def main() -> int:
    if not MANIFEST_PATH.exists():
        print(f"FAIL: manifest not found: {MANIFEST_PATH}", file=sys.stderr)
        return 1
    if not BIN_DIR.exists():
        print(f"FAIL: assets/bin not found: {BIN_DIR}", file=sys.stderr)
        return 1

    manifest_text = MANIFEST_PATH.read_text(encoding="utf-8")
    # Preserve top-of-file comment lines starting with "_comment".
    manifest = json.loads(manifest_text)
    tools = manifest.get("tools", [])
    updated = 0
    skipped = 0
    missing = 0

    for tool in tools:
        name = tool.get("name")
        if not name:
            continue
        bin_path = BIN_DIR / name
        if not bin_path.exists():
            print(f"WARN: binary not found in assets/bin/: {name}")
            missing += 1
            continue
        sha = compute_sha256(bin_path)
        tool["sha256"] = sha
        updated += 1
        print(f"  {name}: {sha}")

    # Write back, preserving the _comment keys via json.dump (they are regular keys).
    MANIFEST_PATH.write_text(
        json.dumps(manifest, indent=2, ensure_ascii=False) + "\n",
        encoding="utf-8"
    )

    print()
    print(f"updated: {updated}")
    print(f"skipped: {skipped}")
    print(f"missing: {missing}")
    if missing > 0:
        print()
        print("WARN: some binaries are missing — manifest may reference files")
        print("      that do not exist in assets/bin/. Run this script after")
        print("      adding the missing files, or remove the corresponding")
        print("      entries from the manifest.")
    return 0


if __name__ == "__main__":
    sys.exit(main())
