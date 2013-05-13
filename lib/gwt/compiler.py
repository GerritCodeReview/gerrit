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

from multiprocessing import cpu_count
from os import environ, fchmod, makedirs, mkdir, path
from subprocess import Popen, PIPE
from sys import argv, stderr

cp, opt, end, TMP = [], [], False, environ['TMP']
module, outzip = argv[1], argv[2]

for a in argv[3:]:
  if end:
    if a.endswith('.jar'):
      cp.append(a)
  elif a == '--':
    end = True
  else:
    opt.append(a)

if not outzip.endswith('.zip'):
  print("%s must end with .zip" % outzip, file=stderr)
  exit(1)

rebuild = outzip[:-4] + '.rebuild'
for d in ['deploy', 'unit_cache', 'work']:
  mkdir(path.join(TMP, d))
if not path.exists(path.dirname(outzip)):
  makedirs(path.dirname(outzip))

cmd = [
  'java', '-Xmx512m',
  '-Djava.io.tmpdir=' + TMP,
  '-Dgwt.normalizeTimestamps=true',
  '-Dgwt.persistentunitcachedir=' + path.join(TMP, 'unit_cache'),
  '-classpath', ':'.join(cp),
  'com.google.gwt.dev.Compiler',
  '-deploy', path.join(TMP, 'deploy'),
  '-workDir', path.join(TMP, 'work'),
  '-war', outzip,
  '-localWorkers', str(cpu_count()),
] + opt + [module]

gwt = Popen(cmd, stdout = PIPE, stderr = PIPE)
out, err = gwt.communicate()
if gwt.returncode != 0:
  print(out + err, file=stderr)
  exit(gwt.returncode)

with open(rebuild, 'w') as fd:
  def shquote(s):
    return s.replace("'", "'\\''")
  print('#!/bin/sh', file=fd)
  print("PATH='%s'" % shquote(environ['PATH']), file=fd)
  print('buck build "$1" || exit', file=fd)
  fchmod(fd.fileno(), 0o755)
