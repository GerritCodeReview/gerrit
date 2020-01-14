#!/usr/bin/env python

# reads bazel query XML files, to join target names with their licenses.

from __future__ import print_function
from collections import namedtuple

import argparse
import json
from collections import defaultdict
from sys import stdout, stderr
import xml.etree.ElementTree as ET


DO_NOT_DISTRIBUTE = "//lib:LICENSE-DO_NOT_DISTRIBUTE"

LICENSE_PREFIX = "//lib:LICENSE-"

parser = argparse.ArgumentParser()
parser.add_argument("--asciidoctor", action="store_true")
parser.add_argument("--json-map", action="append", dest="json_maps")
parser.add_argument("xmls", nargs="+")
args = parser.parse_args()

def load_file_content(filename):
  try:
    with open(filename, errors='ignore') as fd:
      return fd.read()
  except TypeError:
    with open(filename) as fd:
      return fd.read()

LicensedFiles = namedtuple("LicensedFiles", ["kind", "files"])
PackageInfo = namedtuple("PackageInfo", ["name", "version", "licensed_files"])

LicenseMapItem = namedtuple("LicenseMapItem", ["name", "safename", "packages", "license_text"])

def load_xmls(xmls):
  entries = defaultdict(list)
  graph = defaultdict(list)
  handled_rules = []
  for xml in xmls:
    tree = ET.parse(xml)
    root = tree.getroot()

    for child in root:
      rule_name = child.attrib["name"]
      if rule_name in handled_rules:
        # already handled in other xml files
        continue

      handled_rules.append(rule_name)
      for c in list(child):
        if c.tag != "rule-input":
          continue

        license_name = c.attrib["name"]
        if LICENSE_PREFIX in license_name:
          entries[rule_name].append(license_name)
          graph[license_name].append(rule_name)

  if len(graph[DO_NOT_DISTRIBUTE]):
    print("DO_NOT_DISTRIBUTE license found in:", file=stderr)
    for target in graph[DO_NOT_DISTRIBUTE]:
      print(target, file=stderr)
    exit(1)

  result = []
  for n in sorted(graph.keys()):
    if len(graph[n]) == 0:
      continue

    name = n[len(LICENSE_PREFIX):]
    safename = name.replace(".", "_")
    packages_names = []
    for d in sorted(graph[n]):
      if d.startswith("//lib:") or d.startswith("//lib/"):
        p = d[len("//lib:"):]
      else:
        p = d[d.index(":")+1:].lower()
      if "__" in p:
        p = p[:p.index("__")]
      packages_names.append(p)

    filename = n[2:].replace(":", "/")
    content = load_file_content(filename)
    result.append(LicenseMapItem(
        name = name,
        safename = safename,
        license_text = content,
        packages = [PackageInfo(name = name, version = None, licensed_files = LicensedFiles(kind = "All", files = [])) for name in packages_names]
        )
    )

  return result

def get_licensed_files(jsonLicensedFiles):
  kind = jsonLicensedFiles["kind"]
  if kind == "AllFiles":
    return LicensedFiles(kind = "All", files = [])
  if kind == "OnlySpecificFiles" or kind == "AllFilesExceptSpecific":
    return LicensedFiles(kind = kind, files = jsonLicensedFiles["files"])
  raise Exception("Invalid licensed files kind: %s".format(kind))

def get_package_display_name(package):
  if package.version:
    return package.name + " - " + package.version
  else:
    return package.name

def can_merge_packages(packages):
  # Can merge if licensed_files are the same for all packages
  first = packages[0]
  for package in packages:
    if package.licensed_files != first.licensed_files:
      return False
  return True

def remove_duplicated_packages(all_packages):
  name_to_package = defaultdict(list)
  for package in all_packages:
    name_to_package[package.name].append(package)

  result = []
  for package_name, packages in name_to_package.items():
    if can_merge_packages(packages):
      package = packages[0]
      result.append(PackageInfo(name = package.name, version = None, licensed_files = package.licensed_files))
    else:
      result.extend(packages)
  return result

def load_jsons(jsons):
  if not jsons:
    return []
  result = []
  for json_map in jsons:
    with open(json_map, 'r') as f:
      licenses_list = json.load(f)
    for licenseId, license in licenses_list.items():
      name = license["licenseName"]
      safename = name.replace(".", "_")
      packages = []
      for p in license["packages"]:
        package = PackageInfo(name = p["name"], version = p["version"], licensed_files = get_licensed_files(p["licensedFiles"]))
        packages.append(package)
      result.append(LicenseMapItem(
          name = name,
          safename = safename,
          license_text = license["licenseText"],
          packages = sorted(remove_duplicated_packages(packages), key=lambda package: get_package_display_name(package)),
      ))
  return result

xml_data = load_xmls(args.xmls)
json_map_data = load_jsons(args.json_maps)

if args.asciidoctor:
  # We don't want any blank line before "= Gerrit Code Review - Licenses"
  print("""= Gerrit Code Review - Licenses

// DO NOT EDIT - GENERATED AUTOMATICALLY.

Gerrit open source software is licensed under the <<Apache2_0,Apache
License 2.0>>.  Executable distributions also include other software
components that are provided under additional licenses.

[[cryptography]]
== Cryptography Notice

This distribution includes cryptographic software.  The country
in which you currently reside may have restrictions on the import,
possession, use, and/or re-export to another country, of encryption
software.  BEFORE using any encryption software, please check
your country's laws, regulations and policies concerning the
import, possession, or use, and re-export of encryption software,
to see if this is permitted.  See the
link:http://www.wassenaar.org/[Wassenaar Arrangement]
for more information.

The U.S. Government Department of Commerce, Bureau of Industry
and Security (BIS), has classified this software as Export
Commodity Control Number (ECCN) 5D002.C.1, which includes
information security software using or performing cryptographic
functions with asymmetric algorithms.  The form and manner of
this distribution makes it eligible for export under the License
Exception ENC Technology Software Unrestricted (TSU) exception
(see the BIS Export Administration Regulations, Section 740.13)
for both object code and source code.

Gerrit includes an SSH daemon (Apache SSHD), to support authenticated
uploads of changes directly from `git push` command line clients.

Gerrit includes an SSH client (JSch), to support authenticated
replication of changes to remote systems, such as for automatic
updates of mirror servers, or realtime backups.

== Licenses
""")

for data in xml_data + json_map_data:
  name = data.name
  safename = data.safename
  print()
  print("[[%s]]" % safename)
  print(name)
  print()
  for p in data.packages:
    package_notice = ""
    if p.licensed_files.kind == "OnlySpecificFiles":
      package_notice = " - only the following file(s):"
    elif p.licensed_files.kind == "AllFilesExceptSpecific":
      package_notice = " - except the following file(s):"

    print("* " + get_package_display_name(p) + package_notice)
    for file in p.licensed_files.files:
      print("** " + file)
  print()
  print("[[%s_license]]" % safename)
  print("----")
  license_text = data.license_text
  print(data.license_text.rstrip("\r\n"))
  print()
  print("----")
  print()

if args.asciidoctor:
  print("""
GERRIT
------
Part of link:index.html[Gerrit Code Review]
""")
