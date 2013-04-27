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
# TODO(sop): Both servlet 2.5 and 3.0 are in classpath.
#            comes from servlet-2_5, gwt-user, gwt-dev.

from os import path
import re
from subprocess import Popen, PIPE
from xml.dom import minidom

MAIN = [
  '//gerrit-acceptance-tests:acceptance_tests',
  '//gerrit-gwtui:ui_module',
  '//gerrit-httpd:httpd_tests',
  '//gerrit-launcher:launcher',
  '//gerrit-main:main_lib',
  '//gerrit-pgm:pgm',
  '//gerrit-server:server__compile',
  '//gerrit-war:init',
  '//lib:postgresql',
  '//lib/log:impl_log4j',
]
JAVA = 'org.eclipse.jdt.launching.JRE_CONTAINER/org.eclipse.jdt.internal.debug.ui.launcher.StandardVMType/JavaSE-1.6'

src, lib1, lib2 = set(), set(), set()
p = Popen(['buck', 'audit', 'classpath'] + MAIN, stdout = PIPE)
for line in p.stdout:
  line = line.strip()
  m = re.search(r'/(gerrit-[^/]+)/lib__[^/]+__output/', line)
  if m:
    src.add(m.group(1))
  elif '/lib/servlet-api-2_5' in line:
    pass
  elif '/lib/gwt/' in line:
    lib2.add(line)
  else:
    lib1.add(line)
r = p.wait()
if r != 0:
  exit(r)

if not path.exists('.project'):
  with open('.project', 'w') as fd:
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

doc = minidom.getDOMImplementation().createDocument(None, 'classpath', None)
def classpathentry(kind, path, src = None):
  e = doc.createElement('classpathentry')
  e.setAttribute('kind', kind)
  e.setAttribute('path', path)
  if src:
    e.setAttribute('sourcepath', src)
  doc.documentElement.appendChild(e)

for s in sorted(src):
  for mode in ['main', 'test']:
    for type in ['java', 'resources']:
      p = path.join(s, 'src', mode, type)
      if path.exists(p):
        classpathentry('src', p)
for libs in [lib1, lib2]:
  for j in sorted(libs):
    s = None
    if j.endswith('.jar'):
      s = j[:-4] + '-src.jar'
      if not path.exists(s):
        s = None
    classpathentry('lib', j, s)
classpathentry('con', JAVA)
classpathentry('output', 'buck-out/classes')

with open('.classpath', 'w') as fd:
  doc.writexml(fd, addindent = '  ', newl = '\n', encoding='UTF-8')
