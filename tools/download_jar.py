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

from hashlib import sha1
from optparse import OptionParser
from os import link, makedirs, path, symlink
import shutil
from subprocess import check_call, CalledProcessError
from sys import stderr
from zipfile import ZipFile, BadZipfile, LargeZipFile

def hashfile(p):
  d = sha1()
  with open(p, 'rb') as f:
    while True:
      b = f.read(8192)
      if not b:
        break
      d.update(b)
  return d.hexdigest()

def safe_mkdirs(d):
  if path.isdir(d):
    return
  try:
    makedirs(d)
  except OSError as err:
    if not path.isdir(d):
      raise err

opts = OptionParser()
opts.add_option('-o', help='local output file')
opts.add_option('-u', help='URL to download')
opts.add_option('-v', help='expected content SHA-1')
opts.add_option('-x', action='append', help='file to delete from ZIP')
opts.add_option('--exclude_java_sources', action='store_true')
args, _ = opts.parse_args()

root_dir = args.o
while root_dir:
  root_dir, n = path.split(root_dir)
  if n == 'buck-out':
    break

cache_ent = path.join(
    root_dir,
    'buck-cache',
    '%s-%s' % (path.basename(args.o), sha1(args.u).hexdigest()))

if not path.exists(cache_ent):
  try:
    safe_mkdirs(path.dirname(cache_ent))
    print('Download %s' % args.u, file=stderr)
    check_call(['curl', '--proxy-anyauth', '-sfo', cache_ent, args.u])
  except (OSError, CalledProcessError) as err:
    print('error using curl: %s' % str(err), file=stderr)
    exit(1)

if args.v:
  have = hashfile(cache_ent)
  if args.v != have:
    o = cache_ent[len(root_dir) + 1:]
    print((
      '%s:\n' +
      'expected %s\n' +
      'received %s\n' +
      '         %s\n') % (args.u, args.v, have, o), file=stderr)
    exit(1)

exclude = []
if args.x:
  exclude += args.x
if args.exclude_java_sources:
  try:
    zf = ZipFile(cache_ent, 'r')
    try:
      for n in zf.namelist():
        if n.endswith('.java'):
          exclude.append(n)
    finally:
      zf.close()
  except (BadZipfile, LargeZipFile) as err:
    print("error opening %s: %s"  % (cache_ent, str(err)), file=stderr)
    exit(1)

safe_mkdirs(path.dirname(args.o))
if exclude:
  shutil.copyfile(cache_ent, args.o)
  check_call(['zip', '-d', args.o] + exclude)
else:
  try:
    link(cache_ent, args.o)
  except OSError as err:
    symlink(cache_ent, args.o)
