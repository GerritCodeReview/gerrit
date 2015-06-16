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
from os import environ, path, remove
from subprocess import check_output
from sys import stderr
import json
import tempfile

LOCATION = {
  '//gerrit-extension-api:extension-api':'gerrit-extension-api/extension-api.jar',
  '//gerrit-plugin-gwtui:gwtui-api':'gerrit-plugin-gwtui/gwtui-api.jar',
  '//gerrit-plugin-api:plugin-api':'gerrit-plugin-api/plugin-api.jar',
  '//gerrit-extension-api:extension-api-src':'gerrit-extension-api/lib__extension-api-src__output/extension-api-src.jar',
  '//gerrit-plugin-gwtui:gwtui-api-src':'gerrit-plugin-gwtui/gwtui-api-src.jar',
  '//gerrit-plugin-api:plugin-api-src':'gerrit-plugin-api/plugin-api-src.jar',
  '//gerrit-extension-api:extension-api-javadoc':'gerrit-extension-api/extension-api-javadoc.jar',
  '//gerrit-plugin-gwtui:gwtui-api-javadoc':'gerrit-plugin-gwtui/gwtui-api-javadoc.jar',
  '//gerrit-plugin-api:plugin-api-javadoc':'gerrit-plugin-api/plugin-api-javadoc.jar',
}

opts = OptionParser()
opts.add_option('-a', help='action (valid actions are: install,deploy)')

args, ctx = opts.parse_args()
if 'install' != args.a and 'deploy' != args.a:
  print("unknown action -a %s" % args.a, file=stderr)
  exit(1)

root = path.abspath(__file__)
while not path.exists(path.join(root, '.buckconfig')):
  root = path.dirname(root)

t = 'api_%s' % args.a

# TODO(davido): Check the timestamp of output file from this target and
# prevent further script execution, when the timestamp was changed,
# because the deployment was done in context of buck `build`.
check_output(['buck', 'build', t])

target = check_output(['buck', 'targets', '--json', t])
json = json.loads(target)

# Target is json array with one element, pick cmd attribute
s = json[0][u'cmd']

fd, tempfile = tempfile.mkstemp()
s = s.replace('$(exe //tools/maven:mvn)', path.join(root, 'tools/maven/mvn.py'))
s = s.replace('-o $OUT', '-o %s' % tempfile)

# Buck doesn't resolve macros
while '$(location' in s:
  start = s.index('$(location')
  end = s.index(')', start)
  target = s[start+11:end]
  s = s.replace(s[start:end+1], 'buck-out/gen/%s' % LOCATION[target])

try:
  # Replay Buck command
  if environ.get('VERBOSE'):
    print(s, file=stderr)
  check_output(s.split())
finally:
  remove(tempfile)
