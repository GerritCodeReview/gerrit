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
from os import environ
try:
  from subprocess import check_output
except ImportError:
  from subprocess import Popen, PIPE
  def check_output(*cmd):
    return Popen(*cmd, stdout=PIPE).communicate()[0]

deploy_api = environ['TMP']
root = deploy_api[:deploy_api.index('buck-out')]

opts = OptionParser()
opts.add_option('-v', help='version')
args, ctx = opts.parse_args()

check_output(['mvn',
            'install:install-file',
            '-DgroupId=com.google.gerrit',
            '-DartifactId=gerrit-plugin-api',
            '-Dversion=%s' % args.v,
            '-Dpackaging=jar',
            '-Dfile=%sbuck-out/gen/plugin-api.jar' % root])

#TODO(davido): install plugin-api-sources.jar

check_output(['mvn',
            'install:install-file',
            '-DgroupId=com.google.gerrit',
            '-DartifactId=gerrit-extension-api',
            '-Dversion=%s' % args.v,
            '-Dpackaging=jar',
            '-Dfile=%sbuck-out/gen/extension-api.jar' % root])

#TODO(davido): install extension-api-sources.jar
