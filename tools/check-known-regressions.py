#!/usr/bin/env python3
"""
Checks for known regressions fixed during the critical hotfix stage.

The script intentionally uses text-level assertions because this repository still
uses an old Android/Gradle stack that may not be available in every CI/runtime.
It is not a substitute for Gradle tests; it is a fast guard against reintroducing
high-risk bugs that were found during the architecture audit.
"""
from pathlib import Path
import re
import sys
import xml.etree.ElementTree as ET

ROOT = Path(__file__).resolve().parents[1]
errors: list[str] = []


def read(rel: str) -> str:
    path = ROOT / rel
    if not path.exists():
        errors.append(f"missing file: {rel}")
        return ""
    return path.read_text(encoding="utf-8")


def require(condition: bool, message: str) -> None:
    if not condition:
        errors.append(message)


# FilePathResolver: content:// copy must preserve the real number of bytes read.
fpr = read("common/src/main/java/com/omarea/common/shared/FilePathResolver.java")
require("bos.write(buffer, 0, bytesRead);" in fpr, "FilePathResolver must write only bytesRead bytes")
require("bos.write(buf);" not in fpr, "FilePathResolver must not write the full stale buffer")
require("while ((bytesRead = is.read(buffer)) != -1)" in fpr, "FilePathResolver must loop on bytesRead")

# Suffix2Mime: grouped extensions must be individual branches and Kotlin 1.4 compatible.
suffix = read("krscript/src/main/java/com/omarea/krscript/config/Suffix2Mime.kt")
for grouped in ["tar,taz,tgz", "jpg,jpeg,jpe", "html,htm,shtml"]:
    require(grouped not in suffix, f"Suffix2Mime must not use grouped string literal {grouped}")
for ext in ['"tar", "taz", "tgz"', '"jpg", "jpeg", "jpe"', '"html", "htm", "shtml"']:
    require(ext in suffix, f"Suffix2Mime missing branch: {ext}")
require("lowercase()" not in suffix, "Suffix2Mime must stay compatible with Kotlin 1.4")

# PageMenuLoader: dynamic options must be added, not just parsed and discarded.
menu = read("krscript/src/main/java/com/omarea/krscript/ui/PageMenuLoader.kt")
require("menuOptions = ArrayList()" in menu, "PageMenuLoader must initialize menuOptions")
require("menuOptions?.add(option)" in menu, "PageMenuLoader must append dynamic menu options")
require("split(\"|\", limit = 2)" in menu, "PageMenuLoader must parse key/title safely")

# ActionListFragment: hidden shell mode must actually execute and must clear running flag.
action = read("krscript/src/main/java/com/omarea/krscript/ui/ActionListFragment.kt")
require("runHiddenAction(nodeInfo, script, onExit, params)" in action, "hidden shell mode must delegate to runHiddenAction")
require("ShellExecutor().execute(context, nodeInfo, script, onCompleted, params, shellHandler)" in action, "hidden shell mode must execute shell script")
require("hiddenTaskRunning = false" in action, "hidden shell mode must clear hiddenTaskRunning")
require("override fun updateLog(msg: SpannableString?)" in action, "hidden shell mode needs a no-op ShellHandlerBase")
require("item.split(\"|\", limit = 2)" in action, "dynamic param options must parse key/title safely")
require("itemSplit[1]" not in action, "dynamic param options must not blindly access itemSplit[1]")

# MTDataFilesProvider: flags must not be reset after write/create detection.
provider = read("pio/src/main/java/com/mio/kitchen/MTDataFilesProvider.java")
require("int i = 0;" in provider, "MTDataFilesProvider flags must be initialized explicitly")
require("i |= 8;" in provider, "directory create flag must be preserved")
require("i |= 2;" in provider, "file write flag must be preserved")
require("parentFile != null && parentFile.canWrite()" in provider, "parent write check must be null-safe")
require(not re.search(r"\n\s*i\s*=\s*0;\s*\n\s*if \(file\.getParentFile\(\)\.canWrite\(\)\)", provider), "MTDataFilesProvider must not reset flags before parent checks")

# tool.sh: high-risk shell typos in flash/pack must stay fixed.
tool = read("pio/src/main/assets/script/tool.sh")
for bad in ["iMG", "${i}MG", "$$mdir"]:
    require(bad not in tool, f"tool.sh must not contain typo: {bad}")
require('[[ -z ${IMG} ]] && fail_key shell_flash_no_target' in tool, "flash_img must validate IMG")
require('dd if="$Brush_in" of="${IMG}"' in tool, "flash_img must write to IMG")
require('partition="$1"' in tool, "mkerofs/rboot must use the function argument")
require('"$zml/$xm/${partition}.img"' in tool, "mkerofs must use the partition argument")
require('return 1' in tool, "rboot must not use break outside its own loop")
require('size=$(utils rsize $mdir/$xm/${1} 1 $list)' not in tool, "readsize must not overwrite manual size config with unquoted auto size")

# XML typo: required must be spelled correctly.
more = read("pio/src/main/assets/script2/more.xml")
require('requiret=' not in more, "more.xml must not contain requiret typo")
require('required="true"' in more, "more.xml must keep required=true")

# ShellTranslation: no inline translated fallback policy.
translation = read("common/src/main/java/com/omarea/common/shell/ShellTranslation.kt")
require("extractInlineFallback" not in translation, "ShellTranslation must not use inline translated fallbacks")
require("[(Fallback text)]" not in read("docs/en/dev/localization.md"), "English localization docs must not document inline fallback")
require("[(Fallback text)]" not in read("docs/ru/dev/localization.md"), "Russian localization docs must not document inline fallback")

# XML assets may be full pages or KrScript fragments with multiple top-level nodes.
# Parse them under a synthetic root so both forms are validated.
for xml_file in sorted((ROOT / "pio/src/main/assets/script2").glob("*.xml")):
    try:
        xml_text = xml_file.read_text(encoding="utf-8")
        xml_text = re.sub(r"^\s*<\?xml[^>]*\?>", "", xml_text)
        ET.fromstring("<synthetic-root>" + xml_text + "</synthetic-root>")
    except ET.ParseError as exc:
        errors.append(f"invalid XML {xml_file.relative_to(ROOT)}: {exc}")

if errors:
    print("FAIL: known regression checks failed")
    for error in errors:
        print(f" - {error}")
    sys.exit(1)

print("PASS: known critical regressions are fixed")
