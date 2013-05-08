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

from os import path, symlink
import re
from subprocess import Popen, PIPE
from sys import argv
from xml.dom import minidom

OUT = argv[1]
ROOT = path.dirname(path.dirname(path.abspath(__file__)))
MAIN = ['//:eclipse_classpath']
GWT = ['//gerrit-gwtui:ui_module']
JRE = '/'.join([
  'org.eclipse.jdt.launching.JRE_CONTAINER',
  'org.eclipse.jdt.internal.debug.ui.launcher.StandardVMType',
  'JavaSE-1.6',
])

def query_classpath(targets):
  deps = []
  p = Popen(['buck', 'audit', 'classpath'] + targets, stdout = PIPE)
  for line in p.stdout:
    deps.append(line.strip())
  s = p.wait()
  if s != 0:
    exit(s)
  return deps

def make_classpath():
  impl = minidom.getDOMImplementation()
  return impl.createDocument(None, 'classpath', None)

doc = make_classpath()
src = set()
lib = set()
gwt_src = set()
gwt_lib = set()

def classpathentry(kind, path, src = None):
  e = doc.createElement('classpathentry')
  e.setAttribute('kind', kind)
  e.setAttribute('path', path)
  if src:
    e.setAttribute('sourcepath', src)
  doc.documentElement.appendChild(e)

for p in query_classpath(MAIN):
  m = re.search(r'/(gerrit-[^/]+)/lib__[^/]+__output/[^/]+[.]jar', p)
  if m:
    src.add(m.group(1))
  elif '/lib/gwt/' in p or p.endswith('-src.jar'):
    gwt_lib.add(p)
  else:
    lib.add(p)

for p in query_classpath(GWT):
  m = re.search(r'/(gerrit-[^/]+)/lib__[^/]+__output/', p)
  if m:
    gwt_src.add(m.group(1))

for s in sorted(src):
  for env in ['main', 'test']:
    for type in ['java', 'resources']:
      p = path.join(s, 'src', env, type)
      if path.exists(p):
        classpathentry('src', p)

for libs in [lib, gwt_lib]:
  for j in sorted(libs):
    s = None
    if j.endswith('.jar'):
      s = j[:-4] + '-src.jar'
      if not path.exists(s):
        s = None
    classpathentry('lib', j, s)

for s in sorted(gwt_src):
  classpathentry('lib', path.join(ROOT, s, 'src', 'main', 'java'))

classpathentry('con', JRE)
classpathentry('output', 'buck-out/classes')

p = path.join(ROOT, '.classpath')
with open(p, 'w') as fd:
  doc.writexml(fd, addindent = '  ', newl = '\n', encoding='UTF-8')
symlink(p, OUT)
