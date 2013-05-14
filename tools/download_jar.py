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
from os import link, makedirs, path
import shutil
from subprocess import check_call, CalledProcessError
from sys import stderr
from zipfile import ZipFile, BadZipfile, LargeZipFile

REPO_ROOTS = {
  'GERRIT': 'http://gerrit-maven.commondatastorage.googleapis.com',
  'MAVEN_CENTRAL': 'http://repo1.maven.org/maven2',
}

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

def download_properties(root_dir):
  local_prop = path.join(root_dir, 'local.properties')
  p = {}
  if path.exists(local_prop):
    with open(local_prop) as fd:
      for line in fd:
        if line.startswith('download.'):
          d = [e.strip() for e in line.split('=', 1)]
          name, url = d[0], d[1]
          p[name[len('download.'):]] = url
  return p

def cache_entry(root_dir, args):
  if args.v:
    h = args.v
  else:
    h = sha1(args.u).hexdigest()
  name = '%s-%s' % (path.basename(args.o), h)
  return path.join(root_dir, 'buck-cache', name)

def resolve_url(url, redirects):
  s = url.index(':')
  scheme, rest = url[:s], url[s+1:]
  if scheme not in REPO_ROOTS:
    return url
  if scheme in redirects:
    root = redirects[scheme]
  else:
    root = REPO_ROOTS[scheme]
  while root.endswith('/'):
    root = root[:-1]
  while rest.startswith('/'):
    rest = rest[1:]
  return '/'.join([root, rest])

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

redirects = download_properties(root_dir)
cache_ent = cache_entry(root_dir, args)
src_url = resolve_url(args.u, redirects)

if not path.exists(cache_ent):
  try:
    safe_mkdirs(path.dirname(cache_ent))
    print('Download %s' % src_url, file=stderr)
    check_call(['curl', '-sfo', cache_ent, src_url])
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
      '         %s\n') % (src_url, args.v, have, o), file=stderr)
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
