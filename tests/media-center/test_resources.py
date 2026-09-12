"""Resource parity checks; not rendered Android QA."""
import pathlib
import re
import xml.etree.ElementTree as ET

root = pathlib.Path(__file__).resolve().parents[2] / "TMessagesProj/src/main"
base = {e.attrib.get("name"): e.text or "" for e in ET.parse(root / "res/values/strings.xml").getroot()}
hebrew = {e.attrib.get("name"): e.text or "" for e in ET.parse(root / "res/values-he/strings.xml").getroot()}
fallback = (root / "java/org/telegram/messenger/TjLocale.java").read_text()
keys = {k for k in base if k and k.startswith("TjMedia")}
for key in keys:
    assert key in hebrew, key
    assert 'm.put("' + key + '"' in fallback, key
    assert re.findall(r"%\d+\$[ds]", base[key]) == re.findall(r"%\d+\$[ds]", hebrew[key]), key
for code in [*(root / "java/org/telegram/messenger/tj").glob("TjMedia*.java"),
             root / "java/org/telegram/ui/TjMediaCenterActivity.java",
             root / "java/org/telegram/ui/Components/TjMediaHomeView.java",
             root / "java/org/telegram/ui/Cells/TjMediaCardCell.java"]:
    for key in re.findall(r"R\.string\.(TjMedia\w+)", code.read_text()):
        assert key in keys, (code.name, key)
logo = ET.parse(root / "res/drawable/tj_tmdb_logo.xml").getroot()
android = "{http://schemas.android.com/apk/res/android}"
assert logo.attrib[android + "viewportWidth"] == "273.42"
assert logo.attrib[android + "viewportHeight"] == "35.52"
assert [item.attrib[android + "color"] for item in logo.iter("item")] == ["#90cea1", "#3cbec9", "#00b3e5"]
print(f"{len(keys)} media resource keys: English/Hebrew/fallback and placeholder checks passed")
