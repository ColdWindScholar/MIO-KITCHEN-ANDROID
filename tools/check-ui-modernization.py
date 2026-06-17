#!/usr/bin/env python3
"""Validate the UI modernization layer (Stage 8).

Stage 8 introduces:
- `UiState` / `UiStateHolder` typed reactive state (in :common).
- `OpenDocumentHelper` / `CreateDocumentHelper` / `UriPermissionPersistor`
  using the modern Activity Result API.
- `FirmwareAnalysisViewModel` — first ViewModel that wires shell runtime +
  firmware analyzer + UI state together.
- `FirmwareProfileFormatter` — pure formatter reusable from tests.

This script statically verifies the architectural contract.
"""
from __future__ import annotations

import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
ERRORS: list[str] = []


def require(condition: bool, message: str) -> None:
    if not condition:
        ERRORS.append(message)


# --- Required files ------------------------------------------------------

required_files = [
    "common/src/main/java/com/omarea/common/ui/UiState.kt",
    "pio/src/main/java/com/mio/kitchen/ui/modern/ActivityResultHelpers.kt",
    "pio/src/main/java/com/mio/kitchen/ui/modern/FirmwareAnalysisViewModel.kt",
    "pio/src/main/java/com/mio/kitchen/ui/modern/FirmwareProfileFormatter.kt",
    "pio/src/test/java/com/mio/kitchen/ui/modern/FirmwareProfileFormatterTest.kt",
]
for rel in required_files:
    require((ROOT / rel).exists(), f"Stage 8 file is missing: {rel}")


# --- UiState contracts ---------------------------------------------------

uistate_src = (ROOT / "common/src/main/java/com/omarea/common/ui/UiState.kt").read_text(encoding="utf-8")
for token in ["sealed class UiState", "data object Idle", "data class Loading",
              "data class Success", "data class Error", "class UiStateHolder"]:
    require(token in uistate_src, f"UiState.kt must declare {token}")
require("MutableStateFlow" in uistate_src, "UiStateHolder must use MutableStateFlow")
require("StateFlow" in uistate_src, "UiStateHolder must expose StateFlow")


# --- Activity Result helpers --------------------------------------------

ar_src = (ROOT / "pio/src/main/java/com/mio/kitchen/ui/modern/ActivityResultHelpers.kt").read_text(encoding="utf-8")
require("class OpenDocumentHelper" in ar_src, "OpenDocumentHelper missing")
require("class CreateDocumentHelper" in ar_src, "CreateDocumentHelper missing")
require("class UriPermissionPersistor" in ar_src, "UriPermissionPersistor missing")
require("ActivityResultContracts.OpenDocument" in ar_src,
        "OpenDocumentHelper must use ActivityResultContracts.OpenDocument")
require("ActivityResultContracts.CreateDocument" in ar_src,
        "CreateDocumentHelper must use ActivityResultContracts.CreateDocument")
require("ActivityResultCaller" in ar_src,
        "Helpers must accept ActivityResultCaller (modern API)")
# Must NOT use deprecated APIs (only check non-comment lines).
import re
def strip_comments_and_strings(src: str) -> str:
    # RU: простейший strip комментариев и строковых литералов.
    # EN: naive strip of comments and string literals.
    src = re.sub(r'/\*.*?\*/', '', src, flags=re.DOTALL)  # block comments
    src = re.sub(r'//[^\n]*', '', src)                     # line comments
    src = re.sub(r'"[^"\n]*"', '""', src)                  # string literals
    return src

ar_code = strip_comments_and_strings(ar_src)
require("startActivityForResult" not in ar_code,
        "Modern helpers must not use deprecated startActivityForResult")
require("onActivityResult" not in ar_code,
        "Modern helpers must not use deprecated onActivityResult")


# --- ViewModel contract --------------------------------------------------

vm_src = (ROOT / "pio/src/main/java/com/mio/kitchen/ui/modern/FirmwareAnalysisViewModel.kt").read_text(encoding="utf-8")
require("class FirmwareAnalysisViewModel" in vm_src, "FirmwareAnalysisViewModel missing")
require(": ViewModel()" in vm_src, "FirmwareAnalysisViewModel must extend ViewModel")
require("viewModelScope" in vm_src, "ViewModel must use viewModelScope")
require("Dispatchers.IO" in vm_src, "ViewModel must dispatch to IO for analysis")
require("UiStateHolder" in vm_src, "ViewModel must use UiStateHolder")
require("FirmwareAnalyzer" in vm_src, "ViewModel must accept FirmwareAnalyzer")
# Must NOT depend on Context, Handler, activity!!
require("import android.app.Activity" not in vm_src,
        "FirmwareAnalysisViewModel must not import android.app.Activity")
require("import android.os.Handler" not in vm_src,
        "FirmwareAnalysisViewModel must not import android.os.Handler")
require("activity!!" not in vm_src,
        "FirmwareAnalysisViewModel must not use activity!!")
require("context!!" not in vm_src,
        "FirmwareAnalysisViewModel must not use context!!")


# --- Formatter contract --------------------------------------------------

fmt_src = (ROOT / "pio/src/main/java/com/mio/kitchen/ui/modern/FirmwareProfileFormatter.kt").read_text(encoding="utf-8")
require("object FirmwareProfileFormatter" in fmt_src, "FirmwareProfileFormatter missing")
require("fun short(" in fmt_src, "Formatter must expose short()")
require("fun detailed(" in fmt_src, "Formatter must expose detailed()")
# Formatter must not depend on View API.
require("import android.view" not in fmt_src, "Formatter must not import android.view.*")
require("import android.widget" not in fmt_src, "Formatter must not import android.widget.*")


# --- build.gradle has lifecycle deps -------------------------------------

pio_build = (ROOT / "pio/build.gradle").read_text(encoding="utf-8")
require(
    "androidx.lifecycle:lifecycle-viewmodel-ktx" in pio_build,
    "pio/build.gradle must declare lifecycle-viewmodel-ktx",
)
require(
    "androidx.lifecycle:lifecycle-runtime-ktx" in pio_build,
    "pio/build.gradle must declare lifecycle-runtime-ktx",
)
require(
    "androidx.activity:activity-ktx" in pio_build,
    "pio/build.gradle must declare activity-ktx for Activity Result API",
)


# --- Tests present -------------------------------------------------------

test_src = (ROOT / "pio/src/test/java/com/mio/kitchen/ui/modern/FirmwareProfileFormatterTest.kt").read_text(encoding="utf-8")
require("@Test" in test_src, "FirmwareProfileFormatterTest must declare @Test")
require("FirmwareProfileFormatter" in test_src, "Tests must cover FirmwareProfileFormatter")
require("UiStateHolder" in test_src, "Tests must cover UiStateHolder")


# --- Report --------------------------------------------------------------

if ERRORS:
    for e in ERRORS:
        print(f"FAIL: {e}")
    sys.exit(1)

print("PASS: UiState/UiStateHolder typed reactive state is in place")
print("PASS: OpenDocumentHelper/CreateDocumentHelper use modern Activity Result API")
print("PASS: FirmwareAnalysisViewModel extends ViewModel and uses viewModelScope")
print("PASS: FirmwareAnalysisViewModel has no Context/Handler/activity!! dependencies")
print("PASS: FirmwareProfileFormatter is pure (no View/widget imports)")
print("PASS: pio/build.gradle declares lifecycle + activity-ktx dependencies")
print("PASS: UI modernization JVM unit tests are present")
