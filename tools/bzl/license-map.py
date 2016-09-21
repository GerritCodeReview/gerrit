#!/usr/bin/env python

# reads a bazel query XML file, to join target names with their licenses.

import sys
import xml.etree.ElementTree as ET

tree = ET.parse(sys.argv[1])
root = tree.getroot()

entries = {}

for child in root:
  rule_name = child.attrib["name"]
  for c in child.getchildren():
    if c.tag != "rule-input":
      continue

    license_name = c.attrib["name"]
    if "//lib:LICENSE" in license_name:
      assert rule_name not in entries, (license_name, entries[rule_name])
      entries[rule_name] = license_name

for k, v in sorted(entries.items()):
  print k, v
