#!/usr/bin/python
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
# TODO(sop): Remove hack after Buck supports Eclipse

from os import path
import re
from subprocess import Popen, PIPE
from xml.dom import minidom

MAIN = [
  '//gerrit-gwtui:ui_module',
  '//gerrit-httpd:httpd_tests',
  '//gerrit-launcher:launcher',
  '//gerrit-pgm:pgm',
  '//gerrit-server:server__compile',
]
JAVA = 'org.eclipse.jdt.launching.JRE_CONTAINER/org.eclipse.jdt.internal.debug.ui.launcher.StandardVMType/JavaSE-1.6'

src = set()
lib = set()
p = Popen(['buck', 'audit', 'classpath'] + MAIN, stdout = PIPE)
for line in p.stdout:
  line = line.strip()
  m = re.search(r'/(gerrit-[^/]+)/lib__[^/]+__output/', line)
  if m:
    src.add(m.group(1))
  else:
    lib.add(line)
r = p.wait()
if r != 0:
  exit(r)

if not path.exists('.project'):
  fd = open('.project', 'w')
  print >>fd, """\
<?xml version="1.0" encoding="UTF-8"?>
<projectDescription>
  <name>gerrit</name>
  <buildSpec>
    <buildCommand>
      <name>org.eclipse.jdt.core.javabuilder</name>
    </buildCommand>
  </buildSpec>
  <natures>
    <nature>org.eclipse.jdt.core.javanature</nature>
  </natures>
</projectDescription>\
"""
  fd.close()

doc = minidom.getDOMImplementation().createDocument(None, 'classpath', None)
def classpathentry(kind, path):
  e = doc.createElement('classpathentry')
  e.setAttribute('kind', kind)
  e.setAttribute('path', path)
  doc.documentElement.appendChild(e)

for s in sorted(src):
  for n in [
      'src/main/java',
      'src/main/resources',
      'src/test/java',
      'src/test/resources']:
    p = path.join(s, n)
    if path.exists(p):
      classpathentry('src', p)
for j in sorted(lib):
  classpathentry('lib', j)
classpathentry('con', JAVA)
classpathentry('output', 'buck-out/classes')

fd = open('.classpath', 'w')
doc.writexml(fd, addindent = '  ', newl = '\n', encoding='UTF-8')
fd.close()
