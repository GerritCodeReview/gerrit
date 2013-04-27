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

from hashlib import sha1
from optparse import OptionParser
from subprocess import check_call
import sys
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

opts = OptionParser()
opts.add_option('-o', help='local output file')
opts.add_option('-u', help='URL to download')
opts.add_option('-v', help='expected content SHA-1')
opts.add_option('-x', action='append', help='file to delete from ZIP')
opts.add_option('--exclude_java_sources', action='store_true')
args, _ = opts.parse_args()

try:
  check_call(['curl', '-sfo', args.o, args.u])
except OSError as err:
  print >>sys.stderr, "error using curl: %s" % str(err)
  sys.exit(1)

if args.v:
  have = hashfile(args.o)
  if args.v != have:
    o = args.o[args.o.index('buck-out/'):]
    print >>sys.stderr, (
      '%s:\n' +
      'expected %s\n' +
      'received %s\n' +
      '         %s\n') % (args.u, args.v, have, o)
    sys.exit(1)

exclude = []
try:
  zf = ZipFile(args.o, 'r')
  try:
    for n in zf.namelist():
      if n.startswith('META-INF/maven/'):
        exclude.append(n)
      elif args.exclude_java_sources and n.endswith('.java'):
        exclude.append(n)
  finally:
    zf.close()
except (BadZipfile, LargeZipFile) as err:
  print >>sys.stderr, "error opening %s: %s"  % (args.o, str(err))
  sys.exit(1)

if args.x:
  exclude += args.x
if exclude:
  check_call(['zip', '-d', args.o] + exclude)
