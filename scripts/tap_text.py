#!/usr/bin/env python3
"""Find a visible Android UI node by label and print its center coordinates."""
import re
import sys
import xml.etree.ElementTree as ET

if len(sys.argv) != 2:
    raise SystemExit("Usage: tap_text.py LABEL")

label = sys.argv[1]
try:
    root = ET.parse("/tmp/window.xml").getroot()
except (OSError, ET.ParseError) as exc:
    raise SystemExit(f"Cannot read /tmp/window.xml: {exc}")

for node in root.iter("node"):
    text = node.attrib.get("text", "").strip()
    description = node.attrib.get("content-desc", "").strip()
    if text == label or description == label or (label == "Add memory" and label in text):
        bounds = node.attrib.get("bounds", "")
        match = re.fullmatch(r"\[(\d+),(\d+)\]\[(\d+),(\d+)\]", bounds)
        if match:
            x1, y1, x2, y2 = map(int, match.groups())
            print((x1 + x2) // 2, (y1 + y2) // 2)
            raise SystemExit(0)

raise SystemExit(f"Could not find visible UI control: {label}")
