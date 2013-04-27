#!/usr/bin/python

from multiprocessing import cpu_count
from os import environ, makedirs, mkdir
from os.path import dirname, exists
from subprocess import Popen, PIPE
from sys import argv, exit, stderr

TMP = environ['TMP']
cp, opt, end = [], [], False
module = argv[1]
outzip = argv[2]
for a in argv[3:]:
  if end:
    if a.endswith('.jar'):
      cp.append(a)
  elif a == '--':
    end = True
  else:
    opt.append(a)

for d in ['deploy', 'unit_cache', 'work']:
  mkdir(TMP + '/' + d)
if not exists(dirname(outzip)):
  makedirs(dirname(outzip))

cmd = [
  'java', '-Xmx512m',
  '-Djava.io.tmpdir=' + TMP,
  '-Dgwt.normalizeTimestamps=true',
  '-Dgwt.persistentunitcachedir=' + TMP + '/unit_cache',
  '-classpath', ':'.join(cp),
  'com.google.gwt.dev.Compiler',
  '-workDir', TMP + '/work',
  '-deploy', TMP + '/deploy',
  '-war', outzip,
  '-localWorkers', str(cpu_count()),
] + opt + [module]

gwt = Popen(cmd, stdout = PIPE, stderr = PIPE)
out, err = gwt.communicate()
if gwt.returncode != 0:
  print >>stderr, out
  print >>stderr, err
  exit(gwt.returncode)
