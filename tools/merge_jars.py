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
import collections
import sys
import zipfile


if len(sys.argv) < 3:
  print('usage: %s <out.zip> <in.zip>...' % sys.argv[0], file=sys.stderr)
  exit(1)

outfile = sys.argv[1]
infiles = sys.argv[2:]
seen = set()
SERVICES = 'META-INF/services/'

try:
  with zipfile.ZipFile(outfile, 'w') as outzip:
    services = collections.defaultdict(lambda: '')
    for infile in infiles:
      with zipfile.ZipFile(infile) as inzip:
        for info in inzip.infolist():
          n = info.filename
          if n in seen:
            continue
          elif n.startswith(SERVICES):
            # Concatenate all provider configuration files.
            services[n] += inzip.read(n).decode("UTF-8")
            continue
          outzip.writestr(info, inzip.read(n))
          seen.add(n)

    for n, v in list(services.items()):
      outzip.writestr(n, v)
except Exception as err:
  exit('Failed to merge jars: %s' % err)
