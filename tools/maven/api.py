#!/usr/bin/env python
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
from argparse import ArgumentParser
from json import loads
from os import environ, path, remove
from subprocess import check_call, check_output, Popen, PIPE
from sys import stderr
from tempfile import mkstemp


def locations():
  d = Popen('buck audit dependencies api'.split(),
            stdin=None, stdout=PIPE, stderr=PIPE)
  t = Popen('xargs buck targets --show_output'.split(),
            stdin=d.stdout, stdout=PIPE, stderr=PIPE)
  out = t.communicate()[0]
  d.wait()
  targets = []
  outs = []
  for e in out.strip().split('\n'):
    t, o = e.split()
    targets.append(t)
    outs.append(o)
  return dict(zip(targets, outs))

parser = ArgumentParser()
parser.add_argument('-n', '--dryrun', action='store_true')
parser.add_argument('-v', '--verbose', action='store_true')

subparsers = parser.add_subparsers(help='action', dest='action')
subparsers.add_parser('deploy', help='Deploy to Maven (remote)')
subparsers.add_parser('install', help='Install to Maven (local)')

args = parser.parse_args()

root = path.abspath(__file__)
while not path.exists(path.join(root, '.buckconfig')):
  root = path.dirname(root)

if not args.dryrun:
  check_call('buck build api'.split())
target = check_output(('buck targets --json api_%s' % args.action).split())

s = loads(target)[0]['cmd']

fd, tempfile = mkstemp()
s = s.replace('$(exe //tools/maven:mvn)', path.join(root, 'tools/maven/mvn.py'))
s = s.replace('-o $OUT', '-o %s' % tempfile)

locations = locations()

while '$(location' in s:
  start = s.index('$(location')
  end = s.index(')', start)
  target = s[start+11:end]
  s = s.replace(s[start:end+1], locations[target])

try:
  if args.verbose or args.dryrun or environ.get('VERBOSE'):
    print(s, file=stderr)
  if not args.dryrun:
    check_call(s.split())
finally:
  remove(tempfile)
