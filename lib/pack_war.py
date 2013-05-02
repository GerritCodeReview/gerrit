#!/usr/bin/python

from argparse import ArgumentParser
from os import environ, makedirs, path, symlink
from sys import exit
from subprocess import Popen, PIPE
try:
  from subprocess import check_output
except ImportError:
  def check_output(*cmd):
    return Popen(*cmd, stdout=PIPE).communicate()[0]

def call(cmd, cwd = None):
  p = Popen(cmd, cwd = cwd)
  p.communicate()
  if p.returncode != 0:
    exit(p.returncode)

parser = ArgumentParser()
parser.add_argument('-o')
parser.add_argument('--lib', nargs='*')
parser.add_argument('--pgmlib', nargs='*')
parser.add_argument('--war', nargs='*')
args = parser.parse_args()

war = environ['TMP']
root = war[0:war.index('buck-out/')]
libdir = path.join(war, 'WEB-INF', 'lib')
pgmdir = path.join(war, 'WEB-INF', 'pgm-lib')
jars = set()

if args.lib:
  makedirs(libdir)
  cp = check_output(['buck', 'audit', 'classpath'] + args.lib)
  for j in cp.strip().split('\n'):
    jars.add(j)
    if j.startswith('buck-out/gen/gerrit-'):
      n = j.split('/')[2] + '-' + path.basename(j)
    else:
      n = path.basename(j)
    symlink(
      path.join(root, j),
      path.join(libdir, n))

if args.pgmlib:
  makedirs(pgmdir)
  cp = check_output(['buck', 'audit', 'classpath'] + args.pgmlib)
  for j in cp.strip().split('\n'):
    if j in jars:
      continue
    if j.startswith('buck-out/gen/gerrit-'):
      n = j.split('/')[2] + '-' + path.basename(j)
    else:
      n = path.basename(j)
    symlink(
      path.join(root, j),
      path.join(pgmdir, n))

if args.war:
  for s in args.war:
    call(['unzip', '-q', '-d', war, s])

call(['zip', '-9qr', args.o, '.'], cwd = war)
