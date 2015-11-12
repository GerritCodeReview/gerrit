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
import shutil
import subprocess
import sys


def cmd(vulcanize, *args):
  cmd = vulcanize.split(' ')
  cmd.extend(args)
  return cmd


def main(args):
  opts = optparse.OptionParser()
  opts.add_option('-c', help='vulcanize command')
  opts.add_option('-o', help='output file location')
  opts, srcs = opts.parse_args()

  out = os.path.join(os.getcwd(), opts.o)
  subprocess.check_call(
    cmd('/home/davido/projects/gerrit/polygerrit-ui/node_modules/vulcanize/bin/vulcanize',
    #cmd(opts.c,
        '--inline-scripts',
        '--inline-css',
        '--strip-comments',
        '--out-html',
        opts.o,
        ' '.join(srcs)))
  return 0


if __name__ == '__main__':
  sys.exit(main(sys.argv[1:]))
