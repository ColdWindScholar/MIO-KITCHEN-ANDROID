#!/usr/bin/env python3
"""Validate localization resources and localization architecture contracts."""

from __future__ import annotations

import re
import sys
import xml.etree.ElementTree as ET
from pathlib import Path
from typing import Dict, Iterable, List, Set, Tuple

PROJECT_ROOT = Path(__file__).resolve().parents[1]
MODULES = ("common", "krscript", "pio")

FORMAT_TOKEN_RE = re.compile(
    r"%(?!%)(?:\d+\$)?[-#+ 0,(<]*\d*(?:\.\d+)?[a-zA-Z]"
)
STRING_REF_RE = re.compile(r"@string[:/]([A-Za-z_][A-Za-z0-9_]*)")


def fail(message: str) -> None:
    print(f"FAIL: {message}")
    sys.exit(1)


def read_text(path: Path) -> str:
    return path.read_text(encoding="utf-8", errors="ignore")


def parse_supported_languages() -> List[str]:
    """Read the UI-supported language list from LanguageConfig.

    Translations live in Android resources. The common AppLanguage contract should
    not own product/UI language choices.
    """
    path = PROJECT_ROOT / "pio/src/main/java/com/mio/kitchen/LanguageConfig.kt"
    if not path.exists():
        fail(f"Missing UI localization config: {path}")

    text = read_text(path)
    languages = re.findall(r"LanguageOption\(\s*\"([a-z]{2})\"", text)

    if not languages:
        fail("No supported languages were detected in LanguageConfig.supportedLanguages")

    if len(languages) != len(set(languages)):
        fail(f"Duplicate supported languages: {languages}")

    if languages[0] != "en":
        fail("English must be the first/default UI language")

    return languages


def parse_strings(path: Path) -> Dict[str, str]:
    try:
        root = ET.parse(path).getroot()
    except ET.ParseError as exc:
        fail(f"Invalid XML in {path}: {exc}")

    values: Dict[str, str] = {}
    for element in root:
        if element.tag != "string":
            continue
        name = element.attrib.get("name")
        if not name:
            fail(f"String without name in {path}")
        if name in values:
            fail(f"Duplicate string key '{name}' in {path}")
        values[name] = "".join(element.itertext())

    return values


def resource_file_for(module: str, language: str) -> Path:
    folder = "values" if language == "base" else f"values-{language}"
    return PROJECT_ROOT / module / "src/main/res" / folder / "strings.xml"


def placeholder_signature(value: str) -> List[str]:
    return [token[-1] for token in FORMAT_TOKEN_RE.findall(value)]


def validate_resource_parity(supported_languages: Iterable[str]) -> Set[str]:
    all_keys: Set[str] = set()

    for module in MODULES:
        base_path = resource_file_for(module, "base")
        if not base_path.exists():
            fail(f"Missing base strings.xml: {base_path}")

        base_strings = parse_strings(base_path)
        base_keys = set(base_strings)
        all_keys.update(base_keys)

        for language in supported_languages:
            localized_path = resource_file_for(module, language)
            if not localized_path.exists():
                fail(f"Missing localized strings.xml: {localized_path}")

            localized_strings = parse_strings(localized_path)
            localized_keys = set(localized_strings)
            all_keys.update(localized_keys)

            missing = sorted(base_keys - localized_keys)
            extra = sorted(localized_keys - base_keys)
            if missing or extra:
                details = []
                if missing:
                    details.append(f"missing={missing[:20]}")
                if extra:
                    details.append(f"extra={extra[:20]}")
                fail(f"String key mismatch in {localized_path}: {'; '.join(details)}")

            for key, base_value in base_strings.items():
                base_signature = placeholder_signature(base_value)
                localized_signature = placeholder_signature(localized_strings[key])
                if base_signature != localized_signature:
                    fail(
                        f"Placeholder mismatch for '{key}' in {localized_path}: "
                        f"base={base_signature}, localized={localized_signature}"
                    )

    return all_keys


def strip_comments(path: Path, text: str) -> str:
    suffix = path.suffix.lower()

    if suffix in {".kt", ".java"}:
        text = re.sub(r"/\*.*?\*/", "", text, flags=re.S)
        text = re.sub(r"//.*", "", text)
        return text

    if suffix == ".xml":
        return re.sub(r"<!--.*?-->", "", text, flags=re.S)

    if suffix == ".sh":
        return "\n".join(
            line for line in text.splitlines()
            if not line.lstrip().startswith("#")
        )

    return text


def iter_reference_files() -> Iterable[Path]:
    for module in MODULES:
        module_path = PROJECT_ROOT / module
        for path in module_path.rglob("*"):
            if not path.is_file():
                continue
            if any(part in {"build", ".gradle"} for part in path.parts):
                continue
            if path.suffix.lower() in {".xml", ".kt", ".java", ".sh"}:
                yield path


def validate_string_references(all_keys: Set[str]) -> None:
    missing: List[Tuple[str, Path]] = []

    for path in iter_reference_files():
        text = strip_comments(path, read_text(path))
        for match in STRING_REF_RE.finditer(text):
            key = match.group(1)
            if key not in all_keys:
                missing.append((key, path.relative_to(PROJECT_ROOT)))

    if missing:
        first = "\n".join(f"  {key}: {path}" for key, path in missing[:40])
        fail(f"Missing string resources referenced by source files:\n{first}")


def validate_contract_usage() -> None:
    app_language = PROJECT_ROOT / "common/src/main/java/com/omarea/common/shared/AppLanguage.kt"
    language_config = PROJECT_ROOT / "pio/src/main/java/com/mio/kitchen/LanguageConfig.kt"
    # RU: Stage 22 — ScriptEnvironmen переписан с Java на Kotlin как тонкий
    #     фасад над LegacyShellBridge. Принимаем либо .java, либо .kt.
    # EN: Stage 22 — ScriptEnvironmen rewritten from Java to Kotlin as a thin
    #     facade over LegacyShellBridge. Accept either .java or .kt.
    script_environment_java = PROJECT_ROOT / "krscript/src/main/java/com/omarea/krscript/executor/ScriptEnvironmen.java"
    script_environment_kt = PROJECT_ROOT / "krscript/src/main/java/com/omarea/krscript/executor/ScriptEnvironmen.kt"
    if script_environment_kt.exists():
        script_environment = script_environment_kt
    else:
        script_environment = script_environment_java
    shell_translation = PROJECT_ROOT / "common/src/main/java/com/omarea/common/shell/ShellTranslation.kt"

    for path in (app_language, language_config, script_environment, shell_translation):
        if not path.exists():
            fail(f"Required localization contract file missing: {path}")

    app_language_text = read_text(app_language)
    for token in ('PREFS_NAME', 'PREFS_KEY_LANGUAGE', 'DEFAULT_LANGUAGE', 'ENV_APP_LANGUAGE', 'ENV_LC_CTYPE'):
        if token not in app_language_text:
            fail(f"AppLanguage is missing shared contract token: {token}")
    if 'SUPPORTED_LANGUAGE_CODES' in app_language_text or 'LANGUAGE_RUSSIAN' in app_language_text:
        fail("AppLanguage must not own the UI supported-language list")
    if 'const val DEFAULT_LANGUAGE: String = "en"' not in app_language_text:
        fail("English must remain the default language in AppLanguage")

    language_config_text = read_text(language_config)
    if "com.omarea.common.shared.AppLanguage" not in language_config_text:
        fail("LanguageConfig must delegate the shared contract to AppLanguage")
    if 'val supportedLanguages' not in language_config_text:
        fail("LanguageConfig must own the UI supported-language list")
    if '"app_language"' in language_config_text or '"language"' in language_config_text:
        fail("LanguageConfig must not duplicate SharedPreferences names or keys")

    script_environment_text = read_text(script_environment)
    forbidden_tokens = (
        "APP_LANGUAGE_PREFS",
        "APP_LANGUAGE_KEY",
        "DEFAULT_APP_LANGUAGE",
        "SUPPORTED_APP_LANGUAGES",
        "getAppLanguage(",
    )
    for token in forbidden_tokens:
        if token in script_environment_text:
            fail(f"ScriptEnvironmen still duplicates localization contract token: {token}")

    # RU: Stage 22 — ScriptEnvironmen теперь Kotlin-фасад, делегирующий в
    #     LegacyShellBridge. Прямые ссылки на AppLanguage убраны из
    #     ScriptEnvironmen — они живут в LegacyShellBridge. Проверяем, что
    #     AppLanguage используется в LegacyShellBridge (как основной потребитель
    #     локализационного контракта).
    # EN: Stage 22 — ScriptEnvironmen is now a Kotlin facade delegating to
    #     LegacyShellBridge. Direct AppLanguage references were removed from
    #     ScriptEnvironmen — they live in LegacyShellBridge. Verify that
    #     AppLanguage is used in LegacyShellBridge (the primary consumer of
    #     the localization contract).
    legacy_bridge = PROJECT_ROOT / "krscript/src/main/java/com/omarea/krscript/runtime/LegacyShellBridge.kt"
    if legacy_bridge.exists():
        legacy_bridge_text = read_text(legacy_bridge)
        required_tokens = (
            "com.omarea.common.shared.AppLanguage",
            "AppLanguage.ENV_APP_LANGUAGE",
            "AppLanguage.ENV_LC_CTYPE",
            "AppLanguage.SHELL_UTF8_LOCALE",
            "AppLanguage.get(",
        )
        for token in required_tokens:
            if token not in legacy_bridge_text:
                fail(f"LegacyShellBridge does not use shared localization token: {token}")
    else:
        # RU: если LegacyShellBridge не существует, проверяем старые токены в
        #     ScriptEnvironmen (обратная совместимость со старой версией).
        # EN: if LegacyShellBridge does not exist, check the old tokens in
        #     ScriptEnvironmen (backward compat with the old version).
        required_tokens = (
            "import com.omarea.common.shared.AppLanguage;",
            "AppLanguage.ENV_APP_LANGUAGE",
            "AppLanguage.ENV_LC_CTYPE",
            "AppLanguage.SHELL_UTF8_LOCALE",
            "AppLanguage.get(context)",
        )
        for token in required_tokens:
            if token not in script_environment_text:
                fail(f"ScriptEnvironmen does not use shared localization token: {token}")

    shell_translation_text = read_text(shell_translation)
    if 'private val resources: Resources' not in shell_translation_text or 'private val packageName: String' not in shell_translation_text:
        fail("ShellTranslation must not retain a full Context; store Resources and packageName instead")
    for forbidden in ('extractInlineFallback', '[(Fallback text)]', 'row.contains("[(")', 'row.contains("[('):
        if forbidden in shell_translation_text:
            fail("ShellTranslation must not support inline translated runtime fallbacks")


def validate_docs() -> None:
    required_docs = (
        PROJECT_ROOT / "docs/ru/dev/localization.md",
        PROJECT_ROOT / "docs/en/dev/localization.md",
    )

    for path in required_docs:
        if not path.exists():
            fail(f"Missing localization documentation: {path}")
        text = read_text(path)
        for token in ("AppLanguage", "LanguageConfig", "ShellTranslation", "APP_LANGUAGE", "validate-localization.py"):
            if token not in text:
                fail(f"Documentation {path} does not mention required token: {token}")
        if "runtime fallback" in text.lower() and "not" not in text.lower() and "не" not in text.lower():
            fail(f"Documentation {path} must not document runtime fallback translations")


def main() -> int:
    supported_languages = parse_supported_languages()
    all_keys = validate_resource_parity(supported_languages)
    validate_string_references(all_keys)
    validate_contract_usage()
    validate_docs()

    print("PASS: localization contract is split correctly between common and UI")
    print("PASS: string resources are complete for languages: " + ", ".join(supported_languages))
    print("PASS: placeholders match across localized resources")
    print("PASS: source @string references resolve to existing resources")
    print("PASS: runtime translation fallbacks are disabled")
    print("PASS: RU/EN localization documentation exists")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
