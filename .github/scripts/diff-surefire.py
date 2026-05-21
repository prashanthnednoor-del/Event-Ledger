"""
Parses current (target/surefire-reports/) and previous (previous-results/)
Surefire XML reports. Prints a diff of tests that changed status.
Exits 1 if any PASS->FAIL regression is found.
"""
import sys
import os
import glob
from xml.etree import ElementTree


def parse_results(directory):
    results = {}
    for f in glob.glob(os.path.join(directory, "TEST-*.xml")):
        tree = ElementTree.parse(f)
        for tc in tree.findall(".//testcase"):
            name = f"{tc.get('classname')}.{tc.get('name')}"
            failed = tc.find("failure") is not None or tc.find("error") is not None
            results[name] = "FAIL" if failed else "PASS"
    return results


current = parse_results("target/surefire-reports")
previous = parse_results("previous-results") if os.path.isdir("previous-results") else {}

if not previous:
    print("No previous results found — baseline established.")
    print(f"Tests recorded: {len(current)}")
    sys.exit(0)

regressions, fixes, new_tests = [], [], []

for name, status in current.items():
    prev = previous.get(name)
    if prev is None:
        new_tests.append(name)
    elif prev == "PASS" and status == "FAIL":
        regressions.append(name)
    elif prev == "FAIL" and status == "PASS":
        fixes.append(name)

summary_lines = []

if regressions:
    summary_lines.append(f"## REGRESSIONS ({len(regressions)})")
    summary_lines.extend(f"- {t}" for t in regressions)

if fixes:
    summary_lines.append(f"## Fixed ({len(fixes)})")
    summary_lines.extend(f"- {t}" for t in fixes)

if new_tests:
    summary_lines.append(f"## New tests ({len(new_tests)})")
    summary_lines.extend(f"- {t}" for t in new_tests)

if not summary_lines:
    print("No test status changes detected.")
else:
    output = "\n".join(summary_lines)
    print(output)
    step_summary = os.environ.get("GITHUB_STEP_SUMMARY")
    if step_summary:
        with open(step_summary, "a") as f:
            f.write(output + "\n")

sys.exit(1 if regressions else 0)
