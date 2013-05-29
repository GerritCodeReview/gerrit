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
opts.add_option('-g', help='gen directory')

args, ctx = opts.parse_args()
action = args.a
if action not in ['deploy', 'install']:
  print("unknown action : %s" % action, file=stderr)
  exit(1)

gendir = args.g
if not exists(gendir):
  print("gen directory doesn't exist: %s" % gendir, file=stderr)
  exit(1)

version = args.v
if not version:
  print('version is empty')
  exit(1)

REPO_TYPE = 'snapshot' if version.endswith("SNAPSHOT") else 'release'
URL = 's3://gerrit-api@commondatastorage.googleapis.com/%s' % REPO_TYPE

PLUGIN = 'plugin'
EXTENSION = 'extension'
COMMON = 'common'
JAR = 'jar'
SRC = 'src'

cmd = {
  'deploy': ['mvn',
             'deploy:deploy-file',
             '-DrepositoryId=gerrit-api-repository',
             '-Durl=%s' % URL],
  'install': ['mvn',
              'install:install-file'],
  PLUGIN: ['-DartifactId=gerrit-plugin-api'],
  EXTENSION: ['-DartifactId=gerrit-extension-api'],
  COMMON: ['-DgroupId=com.google.gerrit',
           '-Dversion=%s' % version],
  JAR: ['-Dpackaging=jar'],
  SRC: ['-Dpackaging=java-source'],
  }

try:
  check_output(cmd[action] +
               cmd[PLUGIN] +
               cmd[COMMON] +
               cmd[JAR] +
               ['-Dfile=%s/plugin-api.jar' % gendir])
  check_output(cmd[action] +
               cmd[PLUGIN] +
               cmd[COMMON] +
               cmd[SRC] +
               ['-Dfile=%s/gerrit-plugin-api/plugin-api-src.jar' % gendir])
  check_output(cmd[action] +
               cmd[EXTENSION] +
               cmd[COMMON] +
               cmd[JAR] +
               ['-Dfile=%s/extension-api.jar' % gendir])
  check_output(cmd[action] +
               cmd[EXTENSION] +
               cmd[COMMON] +
               cmd[SRC] +
               ['-Dfile=%s/gerrit-extension-api/extension-api-src.jar' % gendir])
except Exception as e:
  print('%s command failed: %s' % (action, e), file=stderr)
  exit(1)
