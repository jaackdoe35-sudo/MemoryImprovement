#!/usr/bin/env python3
"""Fail unless an expected label is present in the current Android UI hierarchy."""
import sys
import xml.etree.ElementTree as ET

if len(sys.argv) != 2:
    raise SystemExit("Usage: assert_text.py LABEL")

expected = sys.argv[1]
try:
    root = ET.parse("/tmp/window.xml").getroot()
except (OSError, ET.ParseError) as exc:
    raise SystemExit(f"Cannot read /tmp/window.xml: {exc}")

labels = {
    value.strip()
    for node in root.iter("node")
    for value in (node.attrib.get("text", ""), node.attrib.get("content-desc", ""))
    if value.strip()
}
if expected not in labels:
    print("Visible labels found:")
    for label in sorted(labels):
        print(f" - {label}")
    raise SystemExit(f"Expected UI label is not visible: {expected}")
print(f"Verified visible UI label: {expected}")
