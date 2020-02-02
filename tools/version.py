#!/usr/bin/env python
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
import argparse
import os.path
import re
import sys

parser = argparse.ArgumentParser()
parser.add_argument('-v', '--version', required=True)
args = vars(parser.parse_args())

DEST_PATTERN = r'\g<1>%s\g<3>' % args['version']


def replace_in_file(filename, src_pattern):
    try:
        f = open(filename, "r")
        s = f.read()
        f.close()
        s = re.sub(src_pattern, DEST_PATTERN, s)
        f = open(filename, "w")
        f.write(s)
        f.close()
    except IOError as err:
        print('error updating %s: %s' % (filename, err), file=sys.stderr)


src_pattern = re.compile(r'^(\s*<version>)([-.\w]+)(</version>\s*)$',
                         re.MULTILINE)
for project in ['gerrit-acceptance-framework', 'gerrit-extension-api',
                'gerrit-plugin-api', 'gerrit-plugin-gwtui',
                'gerrit-war']:
    pom = os.path.join('tools', 'maven', '%s_pom.xml' % project)
    replace_in_file(pom, src_pattern)

src_pattern = re.compile(r'^(GERRIT_VERSION = ")([-.\w]+)(")$', re.MULTILINE)
replace_in_file('version.bzl', src_pattern)
