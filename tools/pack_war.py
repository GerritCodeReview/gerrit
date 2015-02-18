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

from __future__ import print_function
from optparse import OptionParser
from os import chdir, makedirs, path
from subprocess import check_call, check_output
import sys

if sys.platform == 'win32':
  import shutil
else:
  import symlink

opts = OptionParser()
opts.add_option('-o', help='path to write WAR to')
opts.add_option('--lib', action='append', help='target for WEB-INF/lib')
opts.add_option('--pgmlib', action='append', help='target for WEB-INF/pgm-lib')
opts.add_option('--tmp', help='temporary directory')
args, ctx = opts.parse_args()

war = args.tmp
root = war[:war.index('buck-out')]
jars = set()


def link_jars(libs, directory):
  makedirs(directory)
  while not path.isfile('.buckconfig'):
    chdir('..')
  buck = 'buck'
  if sys.platform == 'win32':
    buck += '.cmd'
  try:
    cp = check_output([buck, 'audit', 'classpath'] + libs)
  except Exception as e:
    print('call to buck audit failed: %s' % e, file=sys.stderr)
    exit(1)
  for j in cp.strip().splitlines():
    if sys.platform == 'win32':
      j = j.replace('\\', '/')
    if j not in jars:
      jars.add(j)
      n = path.basename(j)
      if j.startswith('buck-out/gen/gerrit-'):
        n = j.split('/')[2] + '-' + n
      src = path.join(root, j)
      dst = path.join(directory, n)
      if sys.platform == 'win32':
        shutil.copy(src, dst)
      else:
        symlink(src, dst)

if args.lib:
  link_jars(args.lib, path.join(war, 'WEB-INF', 'lib'))
if args.pgmlib:
  link_jars(args.pgmlib, path.join(war, 'WEB-INF', 'pgm-lib'))
try:
  for s in ctx:
    check_call(['unzip', '-q', '-d', war, s])
  check_call(['zip', '-9qr', args.o, '.'], cwd=war)
except KeyboardInterrupt:
  print('Interrupted by user', file=sys.stderr)
  exit(1)
