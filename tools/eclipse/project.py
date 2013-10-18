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

from __future__ import print_function
from optparse import OptionParser
from os import path
from subprocess import Popen, PIPE, CalledProcessError, check_call
from xml.dom import minidom
import re
import sys

MAIN = ['//tools/eclipse:classpath']
GWT = ['//gerrit-gwtui:ui_module']
JRE = '/'.join([
  'org.eclipse.jdt.launching.JRE_CONTAINER',
  'org.eclipse.jdt.internal.debug.ui.launcher.StandardVMType',
  'JavaSE-1.6',
])

ROOT = path.abspath(__file__)
for _ in range(0, 3):
  ROOT = path.dirname(ROOT)

opts = OptionParser()
opts.add_option('--src', action='store_true')
args, _ = opts.parse_args()

def gen_project():
  p = path.join(ROOT, '.project')
  with open(p, 'w') as fd:
    print("""\
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
""", file=fd)

def gen_classpath():
  def query_classpath(targets):
    deps = []
    p = Popen(['buck', 'audit', 'classpath'] + targets, stdout=PIPE)
    for line in p.stdout:
      deps.append(line.strip())
    s = p.wait()
    if s != 0:
      exit(s)
    return deps

  def make_classpath():
    impl = minidom.getDOMImplementation()
    return impl.createDocument(None, 'classpath', None)

  def classpathentry(kind, path, src=None):
    e = doc.createElement('classpathentry')
    e.setAttribute('kind', kind)
    e.setAttribute('path', path)
    if src:
      e.setAttribute('sourcepath', src)
    doc.documentElement.appendChild(e)

  doc = make_classpath()
  src = set()
  lib = set()
  gwt_src = set()
  gwt_lib = set()

  java_library = re.compile(r'[^/]+/gen/(.*)/lib__[^/]+__output/[^/]+[.]jar$')
  for p in query_classpath(MAIN):
    if p.endswith('-src.jar'):
      # gwt_module() depends on -src.jar for Java to JavaScript compiles.
      gwt_lib.add(p)
      continue

    if p.startswith('buck-out/gen/lib/gwt/'):
      # gwt_module() depends on huge shaded GWT JARs that import
      # incorrect versions of classes for Gerrit. Collect into
      # a private grouping for later use.
      gwt_lib.add(p)
      continue

    m = java_library.match(p)
    if m:
      src.add(m.group(1))
    else:
      lib.add(p)

  for p in query_classpath(GWT):
    m = java_library.match(p)
    if m:
      gwt_src.add(m.group(1))

  for s in sorted(src):
    p = path.join(s, 'java')
    if path.exists(p):
      classpathentry('src', p)
      continue

    for env in ['main', 'test']:
      for srctype in ['java', 'resources']:
        p = path.join(s, 'src', env, srctype)
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
    doc.writexml(fd, addindent='  ', newl='\n', encoding='UTF-8')

try:
  if args.src:
    try:
      check_call([path.join(ROOT, 'tools', 'download_all.py'), '--src'])
    except CalledProcessError as err:
      exit(1)

  gen_project()
  gen_classpath()

  try:
    targets = ['//tools:buck.properties'] + MAIN + GWT
    check_call(['buck', 'build'] + targets)
  except CalledProcessError as err:
    exit(1)
except KeyboardInterrupt:
  print('Interrupted by user', file=sys.stderr)
  exit(1)
