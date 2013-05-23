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
from os.path import dirname,abspath,exists
from os.path import join as pathjoin
from sys import stderr

try:
  from subprocess import check_output
except ImportError:
  from subprocess import Popen, PIPE
  def check_output(*cmd):
    return Popen(*cmd, stdout=PIPE).communicate()[0]

#TODO(davido): differentiate dynamically between snapshot and release repo
# induce the type from the version name?
REPO_TYPE = 'snapshot'
URL = 's3://gerrit-api@commondatastorage.googleapis.com/%s' % REPO_TYPE

opts = OptionParser()
opts.add_option('-a', help='action (valid actions are: install,deploy)')
opts.add_option('-s', help='source directory')

args, ctx = opts.parse_args()
action = args.a
if action not in ['deploy', 'install']:
  print("unknown api utility action : %s" % action, file=stderr)
  exit(1)

gendir = dirname(args.s)
version_file = abspath(pathjoin(gendir, '../../VERSION'))
if not exists(version_file):
  print("version file not found: %s" % version_file, file=stderr)
  exit(1)

try:
  version = open(version_file).read().strip()
except IOError as err:
  print('error reading version file %s: %s' %
        (version_file, err), file=stderr)
  exit(1)

cmd = {
  'deploy': ['mvn',
             'deploy:deploy-file',
             '-DrepositoryId=gerrit-api-repository',
             '-Durl=%s' % URL],
  'install': ['mvn',
              'install:install-file'],
  'plugin': ['-DartifactId=gerrit-plugin-api'],
  'extension': ['-DartifactId=gerrit-extension-api'],
  'common': ['-DgroupId=com.google.gerrit',
             '-Dversion=%s' % version],
  'jar': ['-Dpackaging=jar'],
  'src': ['-Dpackaging=java-source'],
  }

try:
  check_output(cmd[action] +
               cmd['plugin'] +
               cmd['common'] +
               cmd['jar'] +
               ['-Dfile=%s/plugin-api.jar' % gendir])
  check_output(cmd[action] +
               cmd['plugin'] +
               cmd['common'] +
               cmd['src'] +
               ['-Dfile=%s/plugin-api-src.jar' % gendir])
  check_output(cmd[action] +
               cmd['extension'] +
               cmd['common'] +
               cmd['jar'] +
               ['-Dfile=%s/extension-api.jar' % gendir])
  check_output(cmd[action] +
               cmd['extension'] +
               cmd['common'] +
               cmd['src'] +
               ['-Dfile=%s/extension-api-src.jar' % gendir])
except Exception,e:
  print('%s command failed: %s' % (action, e), file=stderr)
  exit(1)
