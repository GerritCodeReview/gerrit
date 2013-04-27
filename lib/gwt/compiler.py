#!/usr/bin/python

from sys import argv, exit, stderr
from os import environ as env
from subprocess import call

TMP = env['TMP']
module = argv[1]
cp, opt, end = [], [], False

for a in argv[2:]:
  if end:
    if a.endswith('.jar'):
      cp.append(a)
  elif a == '--':
    end = True
  else:
    opt.append(a)

cmd = [
  'java', '-Xmx512m',
  '-classpath', ':'.join(cp),
  'com.google.gwt.dev.Compiler',
  '-workDir', TMP + '/work',
  '-deploy', TMP + '/deploy',
  '-extra', TMP + '/extra',
  '-war', TMP + '/war',
] + opt + [module]
exit(call(cmd, stdout = stderr))
