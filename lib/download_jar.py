#!/usr/bin/python

from argparse import ArgumentParser
from hashlib import sha1
from sys import exit, stderr
from zipfile import ZipFile, BadZipfile, LargeZipFile
import subprocess

def hashfile(p):
  d = sha1()
  with open(p, 'rb') as f:
    while True:
      b = f.read(8192)
      if b == '':
        break
      d.update(b)
  return d.hexdigest()

def call(*cmd):
  r = subprocess.call(*cmd)
  if r != 0:
    exit(r)

parser = ArgumentParser()
parser.add_argument('-o')
parser.add_argument('-u')
parser.add_argument('-v')
parser.add_argument('-x', nargs='*')
parser.add_argument('--exclude_java_sources', action='store_true')
args = parser.parse_args()

call(['curl', '-sfo', args.o, args.u])

if args.v:
  have = hashfile(args.o)
  if args.v != have:
    o = args.o[args.o.index('buck-out/'):]
    print >>stderr, (
      '%s:\n' +
      'expected %s\n' +
      'received %s\n' +
      '         %s\n') % (args.u, args.v, have, o)
    exit(1)

exclude = []
try:
  zip_file = ZipFile(args.o, 'r')
  for n in zip_file.namelist():
    if n.startswith('META-INF/maven/'):
      exclude.append(n)
    elif args.exclude_java_sources and n.endswith('.java'):
      exclude.append(n)
except (BadZipfile, LargeZipFile) as err:
  print >> stderr, "Error opening zip file %s: %s"  % (args.o, str(err))
  exit(1)

if args.x:
  exclude += args.x

if exclude:
  call(['zip', '-d', args.o] + exclude)
