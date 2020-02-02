#!/usr/bin/env python
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
import argparse
from os import path, environ
from subprocess import check_output, CalledProcessError
from sys import stderr

opts = argparse.ArgumentParser()
opts.add_argument('--repository', help='maven repository id')
opts.add_argument('--url', help='maven repository url')
opts.add_argument('-o')
opts.add_argument('-a', help='action (valid actions are: install,deploy)')
opts.add_argument('-v', help='gerrit version')
opts.add_argument('-s', action='append', help='triplet of artifactId:type:path')

args = vars(opts.parse_args())
if not args['v']:
    print('version is empty', file=stderr)
    exit(1)

root = path.abspath(__file__)
while not path.exists(path.join(root, 'WORKSPACE')):
    root = path.dirname(root)

if 'install' == args['a']:
    cmd = [
        'mvn',
        'install:install-file',
        '-Dversion=%s' % args['v'],
    ]
elif 'deploy' == args['a']:
    cmd = [
        'mvn',
        'gpg:sign-and-deploy-file',
        '-Dversion=%s' % args['v'],
        '-DrepositoryId=%s' % args['repository'],
        '-Durl=%s' % args['url'],
    ]
else:
    print("unknown action -a %s" % args['a'], file=stderr)
    exit(1)

for spec in args['s']:
    artifact, packaging_type, src = spec.split(':')
    exe = cmd + [
        '-DpomFile=%s' % path.join(root, 'tools', 'maven',
                                   '%s_pom.xml' % artifact),
        '-Dpackaging=%s' % packaging_type,
        '-Dfile=%s' % src,
    ]
    try:
        if environ.get('VERBOSE'):
            print(' '.join(exe), file=stderr)
        check_output(exe)
    except Exception as e:
        print('%s command failed: %s\n%s' % (args['a'], ' '.join(exe), e),
              file=stderr)
        if environ.get('VERBOSE') and isinstance(e, CalledProcessError):
            print('Command output\n%s' % e.output, file=stderr)
        exit(1)


out = stderr
if args['o']:
    out = open(args['o'], 'w')

with out as fd:
    if args['repository']:
        print('Repository: %s' % args['repository'], file=fd)
    if args['url']:
        print('URL: %s' % args['url'], file=fd)
    print('Version: %s' % args['v'], file=fd)
