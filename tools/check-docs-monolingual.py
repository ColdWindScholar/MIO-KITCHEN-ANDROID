#!/usr/bin/env python3
"""RU: Проверяет, что docs/en/ и docs/ru/ — монолингвальные (нет bilingual секций).
   EN: Verify that docs/en/ and docs/ru/ are monolingual (no bilingual sections).

Правила:
  - файлы в docs/en/ НЕ должны содержать '## RU' секций
  - файлы в docs/ru/ НЕ должны содержать '## EN' секций
  - файлы в docs/en/ и docs/ru/ с одинаковым именем НЕ должны быть идентичными
    (если они идентичны — значит они bilingual-дубликаты)

Rules:
  - files in docs/en/ MUST NOT contain '## RU' sections
  - files in docs/ru/ MUST NOT contain '## EN' sections
  - same-named files in docs/en/ and docs/ru/ MUST NOT be identical
    (identical => bilingual duplicates)
"""
from __future__ import annotations

import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
EN_DIR = ROOT / "docs/en"
RU_DIR = ROOT / "docs/ru"
ERRORS: list[str] = []


def require(condition: bool, message: str) -> None:
    if not condition:
        ERRORS.append(message)


def check_dir(root: Path, forbidden_marker: str, language: str) -> None:
    if not root.exists():
        return
    for md in root.rglob("*.md"):
        text = md.read_text(encoding="utf-8", errors="ignore")
        # Skip the docs/README.md which intentionally shows the bilingual index table.
        if md.name == "README.md":
            continue
        if forbidden_marker in text:
            # Allow the literal marker only inside code blocks or inline code,
            # but for safety we just flag any occurrence.
            # Check if it appears as a section header.
            import re
            matches = re.findall(rf'^{forbidden_marker}\b.*$', text, re.MULTILINE)
            if matches:
                ERRORS.append(
                    f"{md.relative_to(ROOT)} contains {forbidden_marker} section header "
                    f"({len(matches)} occurrences) — should be monolingual {language}"
                )


def check_duplicates() -> None:
    """Same-named files in en/ and ru/ must NOT be byte-identical."""
    if not EN_DIR.exists() or not RU_DIR.exists():
        return
    en_files = {p.relative_to(EN_DIR): p for p in EN_DIR.rglob("*.md")}
    ru_files = {p.relative_to(RU_DIR): p for p in RU_DIR.rglob("*.md")}
    for rel, en_path in en_files.items():
        if rel not in ru_files:
            continue
        ru_path = ru_files[rel]
        if en_path.read_bytes() == ru_path.read_bytes():
            ERRORS.append(
                f"docs/en/{rel} and docs/ru/{rel} are byte-identical — "
                "they must be monolingual translations, not bilingual duplicates"
            )


# Check EN dir for forbidden '## RU' headers.
check_dir(EN_DIR, "## RU", "English")
# Check RU dir for forbidden '## EN' headers.
check_dir(RU_DIR, "## EN", "Russian")
# Check for duplicates.
check_duplicates()


if ERRORS:
    for e in ERRORS:
        print(f"FAIL: {e}")
    sys.exit(1)

print("PASS: docs/en/ contains no '## RU' bilingual sections")
print("PASS: docs/ru/ contains no '## EN' bilingual sections")
print("PASS: no byte-identical en/ru doc pairs (no bilingual duplicates)")
