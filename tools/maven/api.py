#!/usr/bin/python
# Copyright (C) 2015 The Android Open Source Project
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
from os import path, remove
from subprocess import check_output
from sys import stderr
import json
import tempfile

opts = OptionParser()
opts.add_option('-a', help='action (valid actions are: install,deploy)')

args, ctx = opts.parse_args()
if 'install' != args.a and 'deploy' != args.a:
  print("unknown action -a %s" % args.a, file=stderr)
  exit(1)

root = path.abspath(__file__)
while not path.exists(path.join(root, '.buckconfig')):
  root = path.dirname(root)

cmd = check_output(['buck', 'targets', '--json', 'api_%s' % args.a])
json = json.loads(cmd)
target_cmd = json[0]
s = target_cmd[u'cmd']

fd, tempfile = tempfile.mkstemp()
s = s.replace('$(exe //tools/maven:mvn)', path.join(root, 'tools/maven/mvn.py'))
s = s.replace('-o $OUT', '-o %s' % tempfile)

# Unfortunately Buck doesn't resolve $(location <target>) macros
# for us, so do it ourself

# TODO(davido): This should be optimized by passing all location targets
# at once to Buck. Buck returns resolved paths in the order they were
# passed. Well, still, Buck's targets command must support --resolve_macros
# option to start with
while '$(location' in s:
  start = s.index('$(location')
  end = s.index(')', start)
  target = s[start+11:end]
  out = check_output(['buck', 'targets', '--show_output', target])
  out_start = out.index('buck-out')
  out_replace = out[out_start:-1]
  s = s.replace(s[start:end+1], out_replace)

try:
  check_output(s.split())
finally:
  remove(tempfile)
