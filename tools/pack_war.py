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

from __future__ import print_function
from optparse import OptionParser
from os import chdir, makedirs, path, symlink
from subprocess import check_call
import sys
import re

opts = OptionParser()
opts.add_option('-o', help='path to write WAR to')
opts.add_option('--lib', action='append', help='target for WEB-INF/lib')
opts.add_option('--pgmlib', action='append', help='target for WEB-INF/pgm-lib')
opts.add_option('--tmp', help='temporary directory')
args, ctx = opts.parse_args()

war = args.tmp
root = war[:war.index('buck-out')]
jars = set()

# TODO(davido): relocate from root directory of cell manually
# $(classpath :cell-members) macro is broken:
# https://github.com/facebook/buck/issues/544
#
regex = re.compile(r'(.*)/buck-out/gen/[^/]+[.]jar$')
def fix_cell_root(j):
  return j.replace('buck-out', 'lib/jgit/buck-out') if regex.match(j) else j

def prune(l):
  t = []
  for e in l:
    for j in e.split(':'):
       # TODO(davido): simplify this, when Buck bug is fixed
       j = fix_cell_root(j)
       if j.find('lib/jgit/buck-out/gen') > 0:
         f = j.find('lib/jgit/buck-out/gen')
         r = j[f:]
         t.append(r)
       elif j.find('buck-out'):
         t.append(j[j.find('buck-out'):])
  return t

def link_jars(libs, directory):
  makedirs(directory)
  while not path.isfile('.buckconfig'):
    chdir('..')
  for j in libs:
    if j not in jars:
      jars.add(j)
      n = path.basename(j)
      if j.startswith('buck-out/gen/gerrit-'):
        n = j.split('/')[2] + '-' + n
      symlink(path.join(root, j), path.join(directory, n))

if args.lib:
  link_jars(prune(args.lib), path.join(war, 'WEB-INF', 'lib'))
if args.pgmlib:
  link_jars(prune(args.pgmlib), path.join(war, 'WEB-INF', 'pgm-lib'))
try:
  for s in ctx:
    check_call(['unzip', '-q', '-d', war, s])
  check_call(['zip', '-9qr', args.o, '.'], cwd=war)
except KeyboardInterrupt:
  print('Interrupted by user', file=sys.stderr)
  exit(1)
