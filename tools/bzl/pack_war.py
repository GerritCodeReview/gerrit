#!/usr/bin/env python
# Copyright (C) 2016 The Android Open Source Project
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
from os import makedirs, path, symlink
from subprocess import check_call
import sys
import os

opts = OptionParser()
opts.add_option('-o', help='path to write WAR to')
opts.add_option('--lib', action='append', help='target for WEB-INF/lib')
opts.add_option('--pgmlib', action='append', help='target for WEB-INF/pgm-lib')
opts.add_option('--tmp', help='temporary directory')
args, ctx = opts.parse_args()

war = args.tmp
cwd = os.getcwd()
jars = set()

def link_jars(libs, directory):
  seen = set()
  makedirs(directory)
  for j in libs:
    # Skip bouncycastle
    if j.find('-jdk15on-') != -1:
      continue;
    # TODO(davido): Figure out why ijars are included in the first place
    if j.find('ijar.jar') != -1:
      continue;
    if j not in jars:
      jars.add(j)
      n = path.basename(j)
      # TODO(davido): The children of bazel-out are not fixed like this.
      # For example, when build with -c opt, the artifacts go in bazel-out/local-opt.
      if j.find('bazel-out/local-fastbuild/bin/gerrit-') == 0:
        n = j[len('bazel-out/local-fastbuild/bin/'):].split('/')[0] + '-' + n
      z = path.join(directory, n)
      # TODO(davido): Check again, why we need second filter here
      if z in seen:
        continue
      else:
        seen.add(z)
      symlink(cwd + '/' + j, z)

if args.lib:
  link_jars(args.lib, path.join(war, 'WEB-INF', 'lib'))
if args.pgmlib:
  link_jars(args.pgmlib, path.join(war, 'WEB-INF', 'pgm-lib'))
try:
  for s in ctx:
    check_call(['unzip', '-q', '-d', war, s])
  check_call(['zip', '-9qr', cwd + '/' + args.o, '.'], cwd=cwd + '/' + war)
except KeyboardInterrupt:
  print('Interrupted by user', file=sys.stderr)
  exit(1)
