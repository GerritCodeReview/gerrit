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

import hashlib
import json
import optparse
import os
import subprocess
import sys

CACHE_DIR = os.path.expanduser(os.path.join(
    '~', '.gerritcodereview', 'buck-cache', 'downloaded-artifacts'))


def hash_file(h, p):
  with open(p, 'rb') as f:
    while True:
      b = f.read(8192)
      if not b:
        break
      h.update(p)


def hash_dir(dir):
  # It's hard to get zipfiles to hash deterministically. Instead, do a sorted
  # walk and hash filenames and contents together.
  h = hashlib.sha1()

  for root, dirs, files in os.walk(dir):
    dirs.sort()
    for f in sorted(files):
      p = os.path.join(root, f)
      h.update(p)
      hash_file(h, p)

  return h.hexdigest()


def bower_cmd(bower, *args):
  cmd = bower.split(' ')
  cmd.extend(args)
  return cmd


def bower_info(name, package, version):
  cmd = bower_cmd('bower', '-l=error', '-j',
                  'info', '%s#%s' % (package, version))
  p = subprocess.Popen(cmd , stdout=subprocess.PIPE, stderr=subprocess.PIPE)
  out, err = p.communicate()
  if p.returncode:
    sys.stderr.write(err)
    raise OSError('Command failed: %s' % cmd)

  try:
    info = json.loads(out)
  except ValueError:
    raise ValueError('invalid JSON from %s:\n%s' % (cmd, out))
  info_name = info.get('name')
  if info_name != name:
    raise ValueError('expected package name %s, got: %s' % (name, info_name))
  return info


def ignore_deps(info):
  # Tell bower to ignore dependencies so we just download this component. This
  # is just an optimization, since we only pick out the component we need, but
  # it's important when downloading sizable dependency trees.
  #
  # As of 1.6.5 I don't think ignoredDependencies can be specified on the
  # command line with --config, so we have to create .bowerrc.
  deps = info.get('dependencies')
  if deps:
    with open(os.path.join('.bowerrc'), 'w') as f:
      json.dump({'ignoredDependencies': deps.keys()}, f)


def cache_entry(name, version, sha1):
  c = os.path.join(CACHE_DIR, '%s-%s.zip' % (name, version))
  if sha1:
    c += '-%s' % sha1
  return c


def main(args):
  opts = optparse.OptionParser()
  opts.add_option('-n', help='short name of component')
  opts.add_option('-p', help='full package name of component')
  opts.add_option('-v', help='version number')
  opts.add_option('-s', help='expected content sha1')
  opts.add_option('-o', help='output file location')
  opts, _ = opts.parse_args()

  outzip = os.path.join(os.getcwd(), opts.o)
  # TODO(dborowitz): match download_file behavior of pulling any old file from
  # the cache if there is no -s
  # Also don't double-append sha1.
  cached = cache_entry(opts.n, opts.v, opts.s)
  print('Looking for: %s\n' % cached, file=sys.stderr)
  if os.path.isfile(cached):
    print('Found, linking to: %s\n' % outzip, file=sys.stderr)
    os.link(cached, outzip)
    return 0

  info = bower_info(opts.n, opts.p, opts.v)
  ignore_deps(info)
  subprocess.check_call(
      bower_cmd('bower', '--quiet', 'install', '%s#%s' % (opts.p, opts.v)))
  name = info['name']
  sha1 = hash_dir(name)

  if opts.s and sha1 != opts.s:
    print((
      '%s#%s:\n'
      'expected %s\n'
      'received %s\n') % (opts.p, opts.v, opts.s, sha1), file=sys.stderr)
    return 1

  os.chdir('bower_components')
  cmd = ['zip', '-q', '-r', outzip, opts.n]
  subprocess.check_call(cmd)
  os.link(outzip, cache_entry(opts.n, opts.v, sha1))
  return 0


if __name__ == '__main__':
  sys.exit(main(sys.argv[1:]))
