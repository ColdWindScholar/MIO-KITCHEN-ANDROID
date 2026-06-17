#!/usr/bin/env python3
"""Validate the build-modernization baseline.

The check is intentionally static so it can run even where the Android SDK or
network access is unavailable. It guards the Gradle/AGP migration contract before
full Gradle tasks are executed in CI.
"""
from __future__ import annotations

import re
import sys
import xml.etree.ElementTree as ET
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
ERRORS: list[str] = []


def read(rel: str) -> str:
    path = ROOT / rel
    if not path.exists():
        ERRORS.append(f"missing file: {rel}")
        return ""
    return path.read_text(encoding="utf-8", errors="ignore")


def require(condition: bool, message: str) -> None:
    if not condition:
        ERRORS.append(message)


ROOT_BUILD = read("build.gradle")
SETTINGS = read("settings.gradle")
WRAPPER = read("gradle/wrapper/gradle-wrapper.properties")
GRADLE_PROPERTIES = read("gradle.properties")
COMMON_BUILD = read("common/build.gradle")
KRSCRIPT_BUILD = read("krscript/build.gradle")
PIO_BUILD = read("pio/build.gradle")
ALL_BUILDS = "\n".join([ROOT_BUILD, SETTINGS, WRAPPER, GRADLE_PROPERTIES, COMMON_BUILD, KRSCRIPT_BUILD, PIO_BUILD])

# Root plugin and repository model.
require("id 'com.android.application' version '8.13.2' apply false" in ROOT_BUILD, "root build.gradle must pin AGP application plugin 8.13.2")
require("id 'com.android.library' version '8.13.2' apply false" in ROOT_BUILD, "root build.gradle must pin AGP library plugin 8.13.2")
require("id 'org.jetbrains.kotlin.android' version '2.3.21' apply false" in ROOT_BUILD, "root build.gradle must pin Kotlin Android plugin 2.3.21")
require(re.search(r"compileSdkVersion\s*=\s*35\b", ROOT_BUILD) is not None, "root build.gradle must centralize compileSdkVersion 35")
require(re.search(r"minSdkVersion\s*=\s*21\b", ROOT_BUILD) is not None, "root build.gradle must centralize minSdkVersion 21")
# Stage 13 raised targetSdkVersion from 28 to 35.
require(re.search(r"targetSdkVersion\s*=\s*35\b", ROOT_BUILD) is not None, "root build.gradle must centralize targetSdkVersion 35 (Stage 13)")
require("repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)" in SETTINGS, "settings.gradle must own repositories centrally")
for repo in ["google()", "mavenCentral()", "gradlePluginPortal()"]:
    require(repo in SETTINGS, f"settings.gradle must include {repo}")
require("jcenter()" not in ALL_BUILDS, "jcenter() must be removed from all Gradle files")
require("kotlin-android-extensions" not in ALL_BUILDS, "kotlin-android-extensions plugin must be removed")
require("kotlin-stdlib-jdk7" not in ALL_BUILDS, "explicit kotlin-stdlib-jdk7 dependency must be removed")
require("buildToolsVersion" not in ALL_BUILDS, "manual buildToolsVersion must be removed")
require("compileSdkVersion 28" not in ALL_BUILDS, "old compileSdkVersion 28 syntax/value must be removed")
require("targetSdkVersion 28" not in PIO_BUILD, "old targetSdkVersion DSL must be replaced by targetSdk")
require("lintOptions" not in ALL_BUILDS, "legacy lintOptions block must be replaced with lint {}")
require("signingConfigs.debug" not in ALL_BUILDS.replace("signingConfig signingConfigs.debug", "__KEEP_DEBUG__"), "release builds must not reference signingConfigs.debug")
require("distributionUrl=https\\://services.gradle.org/distributions/gradle-8.13-bin.zip" in WRAPPER, "Gradle wrapper must use official Gradle 8.13 distribution")
require("mirrors.cloud.tencent.com" not in WRAPPER, "Gradle wrapper must not use a regional mirror")
require("org.gradle.jvmargs=-Xmx4096m -Dfile.encoding=UTF-8" in GRADLE_PROPERTIES, "gradle.properties must set deterministic JVM args")
require("android.nonTransitiveRClass=false" in GRADLE_PROPERTIES, "AGP 8 migration must keep transitive R during baseline stage")
require("android.nonFinalResIds=false" in GRADLE_PROPERTIES, "AGP 8 migration must keep final resource IDs during baseline stage")

# Module contracts.
MODULES = {
    "common": (COMMON_BUILD, "com.omarea.common", False),
    "krscript": (KRSCRIPT_BUILD, "com.omarea.krscript", False),
    "pio": (PIO_BUILD, "com.mio.kitchen", True),
}
for module, (text, namespace, is_app) in MODULES.items():
    require(f"namespace '{namespace}'" in text, f"{module} must declare namespace {namespace}")
    require("compileSdk rootProject.ext.compileSdkVersion" in text, f"{module} must use centralized compileSdk")
    require("minSdk rootProject.ext.minSdkVersion" in text, f"{module} must use centralized minSdk")
    require("viewBinding true" in text, f"{module} must enable ViewBinding")
    require("sourceCompatibility rootProject.ext.javaLanguageVersion" in text, f"{module} must use centralized Java sourceCompatibility")
    require("targetCompatibility rootProject.ext.javaLanguageVersion" in text, f"{module} must use centralized Java targetCompatibility")
    require("jvmTarget = rootProject.ext.kotlinCompilerJvmTarget" in text, f"{module} must use centralized Kotlin JVM target")
    if is_app:
        require("applicationId 'com.mio.kitchen'" in text, "pio must declare applicationId")
        require("targetSdk rootProject.ext.targetSdkVersion" in text, "pio must use centralized targetSdk")
        require("lint {" in text, "pio must use the modern lint {} DSL")
        require("MIO_RELEASE_STORE_FILE" in text, "pio release signing must be configured through CI/environment variables")
        require("project.findProperty('MIO_RELEASE_STORE_FILE')" in text, "pio release signing must accept Gradle properties")

# Source should not depend on Kotlin Android synthetic views anymore.
for path in ROOT.rglob("*.kt"):
    rel = path.relative_to(ROOT)
    if any(part in {"build", ".gradle"} for part in rel.parts):
        continue
    text = path.read_text(encoding="utf-8", errors="ignore")
    if "kotlinx.android.synthetic" in text:
        ERRORS.append(f"synthetic view import remains in {rel}")

# ViewBinding migration checkpoints for files that previously used synthetics.
EXPECTED_BINDINGS = {
    "pio/src/main/java/com/mio/kitchen/ActionPage.kt": "ActivityActionPageBinding",
    "pio/src/main/java/com/mio/kitchen/ActivityFileSelector.kt": "ActivityFileSelectorBinding",
    "pio/src/main/java/com/mio/kitchen/MainActivity.kt": "ActivityMainBinding",
    "pio/src/main/java/com/mio/kitchen/SplashActivity.kt": "ActivitySplashBinding",
    "krscript/src/main/java/com/omarea/krscript/ui/DialogLogFragment.kt": "KrDialogLogBinding",
}
for rel, binding_name in EXPECTED_BINDINGS.items():
    text = read(rel)
    require(binding_name in text and "binding." in text, f"{rel} must use ViewBinding via {binding_name}")
    imports = [line for line in text.splitlines() if line.startswith("import ")]
    duplicates = sorted({line for line in imports if imports.count(line) > 1})
    require(not duplicates, f"{rel} has duplicate imports: {duplicates}")

# Manifest packages must move to Gradle namespace for AGP 8+.
for rel in [
    "common/src/main/AndroidManifest.xml",
    "krscript/src/main/AndroidManifest.xml",
    "pio/src/main/AndroidManifest.xml",
]:
    text = read(rel)
    if re.search(r"<manifest[^>]*\spackage=", text):
        ERRORS.append(f"manifest package attribute must be removed: {rel}")
    try:
        ET.fromstring(text)
    except ET.ParseError as exc:
        ERRORS.append(f"invalid manifest XML in {rel}: {exc}")

# CI and documentation gates.
BUILD_QUALITY = read(".github/workflows/build-quality.yml")
RELEASE_WORKFLOW = read(".github/workflows/gradle.yml")
require("java-version: '17'" in BUILD_QUALITY and "java-version: '17'" in RELEASE_WORKFLOW, "CI must use JDK 17")
require("android-actions/setup-android@v3" in BUILD_QUALITY and "platforms;android-35" in BUILD_QUALITY, "debug CI must install Android platform 35")
require("python3 tools/check-build-modernization.py" in BUILD_QUALITY, "build-quality workflow must run the modernization checker")
require("MIO_RELEASE_STORE_BASE64" in RELEASE_WORKFLOW, "release workflow must use external signing secrets")
for rel in ["docs/ru/dev/build-modernization.md", "docs/en/dev/build-modernization.md"]:
    require((ROOT / rel).exists(), f"missing build modernization documentation: {rel}")

# .gitignore must not hide the Gradle wrapper directory.
GITIGNORE = read(".gitignore").splitlines()
require("gradle" not in [line.strip() for line in GITIGNORE], ".gitignore must not ignore Gradle wrapper directory")
require("gradle/" not in [line.strip() for line in GITIGNORE], ".gitignore must not ignore Gradle wrapper directory")
for expected in ["*.jks", "*.keystore", "release-keystore.jks"]:
    require(expected in [line.strip() for line in GITIGNORE], f".gitignore must ignore {expected}")

if ERRORS:
    print("FAIL: build modernization checks failed")
    for error in ERRORS:
        print(f" - {error}")
    sys.exit(1)

print("PASS: build modernization baseline is in place")
print("PASS: AGP/Kotlin/Gradle versions are centralized")
print("PASS: Kotlin Android Extensions was removed")
print("PASS: ViewBinding migration checkpoints are present")
print("PASS: manifest namespaces are controlled by Gradle")
