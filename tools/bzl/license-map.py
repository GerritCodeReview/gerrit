#!/usr/bin/env python

# reads bazel query XML files, to join target names with their licenses.

from __future__ import print_function

import argparse
from collections import defaultdict
from shutil import copyfileobj
from sys import stdout, stderr
import xml.etree.ElementTree as ET


DO_NOT_DISTRIBUTE = "//lib:LICENSE-DO_NOT_DISTRIBUTE"

LICENSE_PREFIX = "//lib:LICENSE-"

parser = argparse.ArgumentParser()
parser.add_argument("--asciidoctor", action="store_true")
parser.add_argument("xmls", nargs="+")
args = parser.parse_args()

entries = defaultdict(list)
graph = defaultdict(list)
handled_rules = []

for xml in args.xmls:
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

for n in sorted(graph.keys()):
    if len(graph[n]) == 0:
        continue

    name = n[len(LICENSE_PREFIX):]
    safename = name.replace(".", "_")
    print()
    print("[[%s]]" % safename)
    print(name)
    print()
    for d in sorted(graph[n]):
        if d.startswith("//lib:") or d.startswith("//lib/"):
            p = d[len("//lib:"):]
        else:
            p = d[d.index(":")+1:].lower()
        if "__" in p:
            p = p[:p.index("__")]
        print("* " + p)
    print()
    print("[[%s_license]]" % safename)
    print("----")
    filename = n[2:].replace(":", "/")
    try:
        with open(filename, errors='ignore') as fd:
            copyfileobj(fd, stdout)
    except TypeError:
        with open(filename) as fd:
            copyfileobj(fd, stdout)
    print()
    print("----")
    print()

if args.asciidoctor:
    print("""
GERRIT
------
Part of link:index.html[Gerrit Code Review]
""")
