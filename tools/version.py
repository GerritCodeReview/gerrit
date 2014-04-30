#!/usr/bin/python
# Copyright (C) 2014 The Android Open Source Project
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
import os.path
import re
import sys

parser = OptionParser()
opts, args = parser.parse_args()

if not len(args):
  parser.error('not enough arguments')
elif len(args) > 1:
  parser.error('too many arguments')

new_version = args[0]
pattern = re.compile(r'(\s*)<version>[-.\w]+</version>')

for project in ['gerrit-plugin-archetype',
                'gerrit-plugin-gwt-archetype',
                'gerrit-plugin-js-archetype']:
  pom = os.path.join(project, 'pom.xml')
  try:
    outxml = ""
    found = False
    for line in open(pom, "r"):
      m = pattern.match(line)
      if m and not found:
        outxml += "%s<version>%s</version>\n" % (m.group(1), new_version)
        found = True
      else:
        outxml += line
    with open(pom, "w") as outfile:
      outfile.write(outxml)
  except IOError as err:
    print('error updating %s: %s' % (pom, err), file=sys.stderr)
