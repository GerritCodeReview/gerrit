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
  'JavaSE-1.7',
])

ROOT = path.abspath(__file__)
while not path.exists(path.join(ROOT, '.buckconfig')):
  ROOT = path.dirname(ROOT)

opts = OptionParser()
opts.add_option('--src', action='store_true')
opts.add_option('--plugins', help='create eclipse projects for plugins',
                action='store_true')
args, _ = opts.parse_args()

def _query_classpath(targets):
  deps = []
  p = Popen(['buck', 'audit', 'classpath'] + targets, stdout=PIPE)
  for line in p.stdout:
    deps.append(line.strip())
  s = p.wait()
  if s != 0:
    exit(s)
  return deps


def gen_project(name='gerrit', root=ROOT):
  p = path.join(root, '.project')
  with open(p, 'w') as fd:
    print("""\
<?xml version="1.0" encoding="UTF-8"?>
<projectDescription>
  <name>""" + name + """</name>
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

def gen_plugin_classpath(root):
  p = path.join(root, '.classpath')
  with open(p, 'w') as fd:
    if path.exists(path.join(root, 'src', 'test', 'java')):
      testpath = """
  <classpathentry kind="src" path="src/test/java"\
 out="eclipse-out/test"/>"""
    else:
      testpath = ""
    print("""\
<?xml version="1.0" encoding="UTF-8"?>
<classpath>
  <classpathentry kind="src" path="src/main/java"/>%(testpath)s
  <classpathentry kind="con" path="org.eclipse.jdt.launching.JRE_CONTAINER"/>
  <classpathentry combineaccessrules="false" kind="src" path="/gerrit"/>
  <classpathentry kind="output" path="eclipse-out/classes"/>
</classpath>""" % {"testpath": testpath}, file=fd)

def gen_classpath():
  def make_classpath():
    impl = minidom.getDOMImplementation()
    return impl.createDocument(None, 'classpath', None)

  def classpathentry(kind, path, src=None, out=None, exported=None):
    e = doc.createElement('classpathentry')
    e.setAttribute('kind', kind)
    e.setAttribute('path', path)
    if src:
      e.setAttribute('sourcepath', src)
    if out:
      e.setAttribute('output', out)
    if exported:
      e.setAttribute('exported', 'true')
    doc.documentElement.appendChild(e)

  doc = make_classpath()
  src = set()
  lib = set()
  gwt_src = set()
  gwt_lib = set()
  plugins = set()

  java_library = re.compile(r'[^/]+/gen/(.*)/lib__[^/]+__output/[^/]+[.]jar$')
  for p in _query_classpath(MAIN):
    if p.endswith('-src.jar'):
      # gwt_module() depends on -src.jar for Java to JavaScript compiles.
      gwt_lib.add(p)
      continue

    if 'buck-out/gen/lib/gwt/' in p:
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

  for p in _query_classpath(GWT):
    m = java_library.match(p)
    if m:
      gwt_src.add(m.group(1))

  for s in sorted(src):
    out = None

    if s.startswith('lib/'):
      out = 'eclipse-out/lib'
    elif s.startswith('plugins/'):
      if args.plugins:
        plugins.add(s)
        continue
      out = 'eclipse-out/' + s

    p = path.join(s, 'java')
    if path.exists(p):
      classpathentry('src', p, out=out)
      continue

    for env in ['main', 'test']:
      o = None
      if out:
        o = out + '/' + env
      elif env == 'test':
        o = 'eclipse-out/test'

      for srctype in ['java', 'resources']:
        p = path.join(s, 'src', env, srctype)
        if path.exists(p):
          classpathentry('src', p, out=o)

  for libs in [lib, gwt_lib]:
    for j in sorted(libs):
      s = None
      if j.endswith('.jar'):
        s = j[:-4] + '_src.jar'
        if not path.exists(s):
          s = None
      if args.plugins:
        classpathentry('lib', j, s, exported=True)
      else:
        classpathentry('lib', j, s)
  for s in sorted(gwt_src):
    p = path.join(ROOT, s, 'src', 'main', 'java')
    if path.exists(p):
      classpathentry('lib', p, out='eclipse-out/gwtsrc')

  classpathentry('con', JRE)
  classpathentry('output', 'eclipse-out/classes')

  p = path.join(ROOT, '.classpath')
  with open(p, 'w') as fd:
    doc.writexml(fd, addindent='\t', newl='\n', encoding='UTF-8')

  if args.plugins:
    for plugin in plugins:
      plugindir = path.join(ROOT, plugin)
      try:
        gen_project(plugin.replace('plugins/', ""), plugindir)
        gen_plugin_classpath(plugindir)
      except (IOError, OSError) as err:
        print('error generating project for %s: %s' % (plugin, err),
              file=sys.stderr)

def gen_factorypath():
  doc = minidom.getDOMImplementation().createDocument(None, 'factorypath', None)
  for jar in _query_classpath(['//lib/auto:auto-value']):
    e = doc.createElement('factorypathentry')
    e.setAttribute('kind', 'EXTJAR')
    e.setAttribute('id', path.join(ROOT, jar))
    e.setAttribute('enabled', 'true')
    e.setAttribute('runInBatchMode', 'false')
    doc.documentElement.appendChild(e)

  p = path.join(ROOT, '.factorypath')
  with open(p, 'w') as fd:
    doc.writexml(fd, addindent='\t', newl='\n', encoding='UTF-8')

try:
  if args.src:
    try:
      check_call([path.join(ROOT, 'tools', 'download_all.py'), '--src'])
    except CalledProcessError as err:
      exit(1)

  gen_project()
  gen_classpath()
  gen_factorypath()

  try:
    targets = ['//tools:buck'] + MAIN + GWT
    check_call(['buck', 'build', '--deep'] + targets)
  except CalledProcessError as err:
    exit(1)
except KeyboardInterrupt:
  print('Interrupted by user', file=sys.stderr)
  exit(1)
