#!/usr/bin/env python3
"""Validate the user-facing documentation (Stage 19)."""
from __future__ import annotations
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
ERRORS: list[str] = []

def require(condition: bool, message: str) -> None:
    if not condition:
        ERRORS.append(message)

required_en = [
    "docs/en/user/quick-start.md",
    "docs/en/user/choose-rom.md",
    "docs/en/user/unpack.md",
    "docs/en/user/modify.md",
    "docs/en/user/pack.md",
    "docs/en/user/flash-safety.md",
    "docs/en/user/storage-access.md",
    "docs/en/user/root-mode.md",
    "docs/en/user/troubleshooting.md",
]
required_ru = [
    "docs/ru/user/quick-start.md",
    "docs/ru/user/choose-rom.md",
    "docs/ru/user/unpack.md",
    "docs/ru/user/modify.md",
    "docs/ru/user/pack.md",
    "docs/ru/user/flash-safety.md",
    "docs/ru/user/storage-access.md",
    "docs/ru/user/root-mode.md",
    "docs/ru/user/troubleshooting.md",
]

for rel in required_en + required_ru:
    require((ROOT / rel).exists(), f"Stage 19 file is missing: {rel}")

# Each user doc must be non-trivial (>500 bytes — not a stub).
for rel in required_en + required_ru:
    path = ROOT / rel
    if path.exists():
        size = path.stat().st_size
        require(size > 500, f"{rel} is too small ({size} bytes) — must be a real doc")

# Each user doc must link to at least one other doc.
for rel in required_en + required_ru:
    text = (ROOT / rel).read_text(encoding="utf-8")
    require(
        ".md)" in text,
        f"{rel} must contain at least one .md link"
    )

# Check that docs are NOT byte-identical between en/ and ru/ (no bilingual duplicates).
for en_rel in required_en:
    ru_rel = en_rel.replace("docs/en/", "docs/ru/", 1)
    en_path = ROOT / en_rel
    ru_path = ROOT / ru_rel
    if en_path.exists() and ru_path.exists():
        require(
            en_path.read_bytes() != ru_path.read_bytes(),
            f"{en_rel} and {ru_rel} are byte-identical — must be monolingual"
        )

if ERRORS:
    for e in ERRORS:
        print(f"FAIL: {e}")
    sys.exit(1)

print("PASS: All 9 user-facing docs exist in both EN and RU")
print("PASS: All user docs are non-trivial (>500 bytes)")
print("PASS: All user docs contain cross-links to other docs")
print("PASS: EN/RU user docs are not byte-identical (no bilingual duplicates)")
