#!/usr/bin/env python
# Copyright (C) 2013 The Android Open Source Project
#
# Licensed under the Apache License, Version 2.0 (the "License");
# you may not use this file except in compliance with the License.
# You may obtain a copy of the License at
#
# http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.
#
# TODO(sop): Be more detailed: version, link to Maven Central

from __future__ import print_function

import argparse
from collections import defaultdict, deque
import json
from os import chdir, path
from shutil import copyfileobj
from subprocess import Popen, PIPE
from sys import stdout, stderr

parser = argparse.ArgumentParser()
parser.add_argument('--asciidoc', action='store_true')
parser.add_argument('--partial', action='store_true')
parser.add_argument('targets', nargs='+')
args = parser.parse_args()

KNOWN_PROVIDED_DEPS = [
  '//lib/bouncycastle:bcpg',
  '//lib/bouncycastle:bcpkix',
  '//lib/bouncycastle:bcprov',
]

for target in args.targets:
  if not target.startswith('//'):
    print('Target must be absolute: %s' % target, file=stderr)

def parse_graph():
  graph = defaultdict(list)
  while not path.isfile('.buckconfig'):
    chdir('..')
  query = ' + '.join('deps(%s)' % t for t in args.targets)
  p = Popen([
      'buck', 'query', query,
      '--output-attributes=buck.direct_dependencies'], stdout=PIPE)
  obj = json.load(p.stdout)
  for target, attrs in obj.iteritems():
    for dep in attrs['buck.direct_dependencies']:

      if target in KNOWN_PROVIDED_DEPS:
        continue

      if (args.partial
          and dep == '//gerrit-gwtexpui:CSS'
          and target == '//gerrit-gwtui:ui_module'):
        continue

      graph[target].append(dep)
  r = p.wait()
  if r != 0:
    exit(r)
  return graph

graph = parse_graph()
licenses = defaultdict(set)

do_not_distribute = False
queue = deque(args.targets)
while queue:
  target = queue.popleft()
  for dep in graph[target]:
    if not dep.startswith('//lib:LICENSE-'):
      continue
    if 'DO_NOT_DISTRIBUTE' in dep:
      do_not_distribute = True
    licenses[dep].add(target)
  queue.extend(graph[target])

if do_not_distribute:
  print('DO_NOT_DISTRIBUTE license found', file=stderr)
  for target in args.targets:
    print('...via %s:' % target)
    Popen(['buck', 'query',
           'allpaths(%s, //lib:LICENSE-DO_NOT_DISTRIBUTE)' % target],
          stdout=stderr).communicate()
  exit(1)

used = sorted(licenses.keys())

if args.asciidoc:
  print("""\
Gerrit Code Review - Licenses
=============================

Gerrit open source software is licensed under the <<Apache2_0,Apache
License 2.0>>.  Executable distributions also include other software
components that are provided under additional licenses.

[[cryptography]]
Cryptography Notice
-------------------

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

For either feature to function, Gerrit requires the
link:http://java.sun.com/javase/technologies/security/[Java Cryptography extensions]
and/or the
link:http://www.bouncycastle.org/java.html[Bouncy Castle Crypto API]
to be installed by the end-user.

Licenses
--------
""")

for n in used:
  libs = sorted(licenses[n])
  name = n[len('//lib:LICENSE-'):]
  if args.asciidoc:
    print()
    print('[[%s]]' % name.replace('.', '_'))
    print(name)
    print('~' * len(name))
    print()
  else:
    print()
    print(name)
    print('--')
  for d in libs:
    if d.startswith('//lib:') or d.startswith('//lib/'):
      p = d[len('//lib:'):]
    else:
      p = d[d.index(':')+1:].lower()
    if '__' in p:
      p = p[:p.index('__')]
    print('* ' + p)
  if args.asciidoc:
    print()
    print('[[license]]')
    print('[verse]')
    print('--')
  with open(n[2:].replace(':', '/')) as fd:
    copyfileobj(fd, stdout)
  print('--')

if args.asciidoc:
  print("""
GERRIT
------
Part of link:index.html[Gerrit Code Review]
""")
