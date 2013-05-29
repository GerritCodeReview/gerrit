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
from os.path import exists
from sys import stderr
from util import check_output

opts = OptionParser()
opts.add_option('-a', help='action (valid actions are: install,deploy)')
opts.add_option('-v', help='gerrit version')
opts.add_option('-d', help='dependencies (jars artifacts)')

args, ctx = opts.parse_args()
action = args.a
if action not in ['deploy', 'install']:
  print("unknown action : %s" % action, file=stderr)
  exit(1)

deps = args.d.split()
if not deps:
  print('dependencies are empty')
  exit(1)

extension_jar = [x for x in deps if "extension-api.jar" in x][0]
extension_src = [x for x in deps if "extension-api-src.jar" in x][0]
plugin_jar = [x for x in deps if "plugin-api.jar" in x][0]
plugin_src = [x for x in deps if "plugin-api-src.jar" in x][0]

version = args.v
if not version:
  print('version is empty')
  exit(1)

REPO_TYPE = 'snapshot' if version.endswith("SNAPSHOT") else 'release'
URL = 's3://gerrit-api@commondatastorage.googleapis.com/%s' % REPO_TYPE

plugin = ['-DartifactId=gerrit-plugin-api']
extension = ['-DartifactId=gerrit-extension-api']
common = [
  '-DgroupId=com.google.gerrit',
  '-Dversion=%s' % version,
]
jar = ['-Dpackaging=jar']
src = ['-Dpackaging=java-source']

cmd = {
  'deploy': ['mvn',
             'deploy:deploy-file',
             '-DrepositoryId=gerrit-api-repository',
             '-Durl=%s' % URL],
  'install': ['mvn',
              'install:install-file'],
  }

try:
  check_output(cmd[action] +
               plugin +
               common +
               jar +
               ['-Dfile=%s' % plugin_jar])
  check_output(cmd[action] +
               plugin +
               common +
               src +
               ['-Dfile=%s' % plugin_src])
  check_output(cmd[action] +
               extension +
               common +
               jar +
               ['-Dfile=%s' % extension_jar])
  check_output(cmd[action] +
               extension +
               common +
               src +
               ['-Dfile=%s' % extension_src])
except Exception as e:
  print('%s command failed: %s' % (action, e), file=stderr)
  exit(1)
