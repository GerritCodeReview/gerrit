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
import re
from subprocess import check_call, Popen, PIPE

MAIN = ['//tools/eclipse:classpath']
PAT = re.compile(r'"(//.*?__download_[^"]*)" -> "//tools:download_jar"')

opts = OptionParser()
opts.add_option('--src', action='store_true')
args, _ = opts.parse_args()

targets = set()

p = Popen(['buck', 'audit', 'classpath', '--dot'] + MAIN, stdout = PIPE)
for line in p.stdout:
  m = PAT.search(line)
  if m:
    n = m.group(1)
    if args.src and n.endswith('__download_bin'):
      n = n[:-4] + '_src'
    targets.add(n)
r = p.wait()
if r != 0:
  exit(r)
check_call(['buck', 'build'] + sorted(targets))
