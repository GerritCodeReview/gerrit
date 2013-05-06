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

from optparse import OptionParser
from os import environ, makedirs, path, symlink
from subprocess import Popen, PIPE
try:
  from subprocess import check_output
except ImportError:
  def check_output(*cmd):
    return Popen(*cmd, stdout=PIPE).communicate()[0]

def call(cmd, cwd = None):
  p = Popen(cmd, cwd = cwd)
  p.communicate()
  if p.returncode != 0:
    exit(p.returncode)

opts = OptionParser()
opts.add_option('-o', help='path to write WAR to')
opts.add_option('--lib', action='append', help='target for WEB-INF/lib')
opts.add_option('--pgmlib', action='append', help='target for WEB-INF/pgm-lib')
args, ctx = opts.parse_args()

war = environ['TMP']
root = war[:war.index('buck-out')]
libdir = path.join(war, 'WEB-INF', 'lib')
pgmdir = path.join(war, 'WEB-INF', 'pgm-lib')
jars = set()

if args.lib:
  makedirs(libdir)
  cp = check_output(['buck', 'audit', 'classpath'] + args.lib)
  for j in cp.strip().splitlines():
    jars.add(j)
    if j.startswith('buck-out/gen/gerrit-'):
      n = j.split('/')[2] + '-' + path.basename(j)
    else:
      n = path.basename(j)
    symlink(
      path.join(root, j),
      path.join(libdir, n))

if args.pgmlib:
  makedirs(pgmdir)
  cp = check_output(['buck', 'audit', 'classpath'] + args.pgmlib)
  for j in cp.strip().splitlines():
    if j in jars:
      continue
    if j.startswith('buck-out/gen/gerrit-'):
      n = j.split('/')[2] + '-' + path.basename(j)
    else:
      n = path.basename(j)
    symlink(
      path.join(root, j),
      path.join(pgmdir, n))

if ctx:
  for s in ctx:
    call(['unzip', '-q', '-d', war, s])

call(['zip', '-9qr', args.o, '.'], cwd = war)
