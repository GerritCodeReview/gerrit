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

for d in ['work', 'deploy', 'extra', 'war']:
  mkdir(TMP + '/' + d)

cmd = [
  'java', '-Xmx512m',
  '-classpath', ':'.join(cp),
  'com.google.gwt.dev.Compiler',
  '-workDir', TMP + '/work',
  '-deploy', TMP + '/deploy',
  '-extra', TMP + '/extra',
  '-war', TMP + '/war',
  '-localWorkers', str(cpu_count()),
] + opt + [module]

gwt = Popen(cmd, stdout = PIPE, stderr = PIPE)
out, err = gwt.communicate()
if gwt.returncode != 0:
  print >>stderr, out
  print >>stderr, err
  exit(gwt.returncode)

if not exists(dirname(outzip)):
  makedirs(dirname(outzip))
zip = Popen(['zip', '-9Dqr', outzip, '.'], cwd = TMP + '/war')
zip.communicate()
if zip.returncode != 0:
  exit(call(zip.returncode))
