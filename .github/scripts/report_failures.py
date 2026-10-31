"""Turns Gradle build/test failures into GitHub annotations, so they're readable via the API."""
import glob
import re
import sys
import xml.etree.ElementTree as ET


def annotate(title, lines):
    if not lines:
        return
    msg = "\n".join(lines)[:6000]
    msg = msg.replace("%", "%25").replace("\r", "").replace("\n", "%0A")
    print(f"::error title={title}::{msg}")


log = open(sys.argv[1], errors="replace").read().splitlines() if len(sys.argv) > 1 else []

compile_errors = [l for l in log if re.match(r"^e: ", l) or "error:" in l and ".kt" in l][:40]
annotate("Compilation errors", compile_errors)

went_wrong = []
for i, line in enumerate(log):
    if "What went wrong" in line:
        went_wrong += log[i + 1 : i + 12]
annotate("What went wrong", went_wrong[:40])

failures = []
for path in glob.glob("**/build/test-results/**/*.xml", recursive=True):
    try:
        root = ET.parse(path).getroot()
    except ET.ParseError:
        continue
    for case in root.iter("testcase"):
        for f in list(case.findall("failure")) + list(case.findall("error")):
            text = (f.get("message") or "") + "\n" + "\n".join((f.text or "").splitlines()[:6])
            failures.append((f"{case.get('classname')}.{case.get('name')}", text))
for name, text in failures[:7]:
    annotate(f"Test failed: {name}"[:200], text.splitlines())
if len(failures) > 7:
    annotate("More failing tests", [n for n, _ in failures[7:40]])
