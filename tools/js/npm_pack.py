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

import atexit
import json
import os
import shutil
import subprocess
import sys
import tarfile
import tempfile


def is_bundled(tar):
  # No entries for directories, so scan for a matching prefix.
  for entry in tar.getmembers():
    if entry.name.startswith('package/node_modules/'):
      return True
  return False


def bundle_dependencies():
  with open('package.json') as f:
    package = json.load(f)
  package['bundledDependencies'] = list(package['dependencies'].keys())
  with open('package.json', 'w') as f:
    json.dump(package, f)


def main(args):
  if len(args) != 2:
    print('Usage: %s <package> <version>' % sys.argv[0], file=sys.stderr)
    return 1

  name, version = args
  filename = '%s-%s.tgz' % (name, version)
  url = 'https://registry.npmjs.org/%s/-/%s' % (name, filename)

  tmpdir = tempfile.mkdtemp();
  tgz = os.path.join(tmpdir, filename)
  atexit.register(lambda: shutil.rmtree(tmpdir))

  subprocess.check_call(['curl', '--proxy-anyauth', '-ksfo', tgz, url])
  with tarfile.open(tgz, 'r:gz') as tar:
    if is_bundled(tar):
      print('%s already has bundled node_modules' % filename)
      return 1
    tar.extractall(path=tmpdir)

  oldpwd = os.getcwd()
  os.chdir(os.path.join(tmpdir, 'package'))
  bundle_dependencies()
  subprocess.check_call(['npm', 'install'])
  subprocess.check_call(['npm', 'pack'])
  shutil.copy(filename, os.path.join(oldpwd, filename))
  return 0


if __name__ == '__main__':
  sys.exit(main(sys.argv[1:]))
