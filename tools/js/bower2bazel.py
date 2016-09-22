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
import collections
import json
import hashlib
import optparse
import os
import shutil
import subprocess
import sys
import tempfile


def build_bower_json():
  """Generate bower JSON file, return its path."""
  bower_json = collections.OrderedDict()
  bower_json['name'] = 'bower2buck-output'
  bower_json['version'] = '0.0.0'
  bower_json['description'] = 'Auto-generated bower.json for dependency management'
  bower_json['private'] = True
  bower_json['dependencies'] = {}

  js = open('bazel-out/local-fastbuild/bin/polygerrit-ui/components-versions.json','r').read()
  bower_json['dependencies'] = json.load(js)

  tmpdir = tempfile.mkdtemp()
  atexit.register(lambda: shutil.rmtree(tmpdir))
  ret = os.path.join(tmpdir, 'bower.json')
  with open(ret, 'w') as f:
    json.dump(bower_json, f, indent=2)
  return ret



def main(args):
  opts = optparse.OptionParser()
  opts.add_option('-o', help='output file location')
  opts, args = opts.parse_args()

  if not opts.o or not all(a.startswith('//') for a in args):
    return usage()
  outfile = os.path.abspath(opts.o)

  subprocess.check_call(['bazel', 'build', '//polygerrit-ui:components-versions.json'])

  bower_json_path = build_bower_json()
  return



if __name__ == '__main__':
  main(sys.argv[1:])
