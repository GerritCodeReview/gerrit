#!/usr/bin/python
# TODO(sop): Be more detailed: version, link to Maven Central

MAIN = ['//gerrit-pgm:pgm', '//gerrit-gwtui:ui_module']

from collections import deque
from subprocess import Popen, PIPE
from sys import exit
import re

def parse_graph():
  graph = {}
  p = Popen(
    ['buck', 'audit', 'classpath', '--dot'] + MAIN,
    stdout = PIPE)
  for line in p.stdout:
    m = re.search(r'"(//.*?)" -> "(//.*?)";', line)
    if not m:
      continue
    target, dep = m.group(1), m.group(2)
    if not target.endswith('__compile'):
      if target not in graph:
        graph[target] = []
      graph[target].append(dep)
  r = p.wait()
  if r != 0:
    exit(r)
  return graph

graph = parse_graph()
licenses = {}

queue = [] + MAIN
while queue:
  target = queue.pop()
  if target not in graph:
    continue
  for dep in graph[target]:
    if not dep.startswith('//lib:LICENSE-'):
      continue
    if dep not in licenses:
      licenses[dep] = set()
    licenses[dep].add(target)
  queue.extend(graph[target])

used = list(licenses.keys())
used.sort()

print """\
Gerrit Code Review - Licenses
=============================

Gerrit open source software licensed under the <<Apache2.0,Apache
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
"""

for n in used:
  libs = list(licenses[n])
  libs.sort()

  name = n[len('//lib:LICENSE-'):]
  print
  print '[[%s]]' % (name,)
  print '%s' % (name,)
  print '~' * len(name)
  print
  for d in libs:
    if d.startswith('//lib:') or d.startswith('//lib/'):
      p = d[len('//lib:'):]
    else:
      p = d[d.index(':')+1:].lower()
    print '* ' + p
  print
  print '----'
  with open(n[2:].replace(':', '/')) as fd:
    for line in fd:
      print line[:-1]
  print '----'

print """
GERRIT
------
Part of link:index.html[Gerrit Code Review]
"""
