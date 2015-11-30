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

from tools import util


# This script is run with `buck run`, but needs to shell out to buck; this is
# only possible if we avoid buckd.
BUCK_ENV = dict(os.environ)
BUCK_ENV['NO_BUCKD'] = '1'


def usage():
  print('Usage: %s -o <outfile> //path/to:bower_components_rule' % sys.argv[0],
        file=sys.stderr)
  return 1


class Rule(object):
  def __init__(self, bower_json_path):
    with open(bower_json_path) as f:
      bower_json = json.load(f)
    self.name = bower_json['name']
    self.version = bower_json['version']
    self.deps = bower_json.get('dependencies', {})
    self.license = bower_json['license']
    self.sha1 = util.hash_bower_component(
        hashlib.sha1(), os.path.dirname(bower_json_path)).hexdigest()

  def to_rule(self, packages):
    if self.name not in packages:
      raise ValueError('No package name found for %s' % self.name)

    lines = [
        'bower_component(',
        "  name = '%s'," % self.name,
        "  package = '%s'," % packages[self.name],
        "  version = '%s'," % self.version,
        ]
    if self.deps:
      if len(self.deps) == 1:
        lines.append("  deps = [':%s']," % next(self.deps.iterkeys()))
      else:
        lines.append('  deps = [')
        lines.extend("    ':%s'," % d for d in sorted(self.deps.iterkeys()))
        lines.append('  ],')
    lines.extend([
        "  license = 'TODO: %s'," % self.license,
        "  sha1 = '%s'," % self.sha1,
        ')'])
    return '\n'.join(lines)


def build_bower_json(target, buck_out):
  name = target[target.find(':')+1:]
  bower_json = collections.OrderedDict()
  bower_json['name'] = name
  bower_json['version'] = '0.0.0'
  bower_json['description'] = 'Auto-generated bower.json for dependency management'
  bower_json['private'] = True
  bower_json['dependencies'] = {}

  deps = subprocess.check_output([
      'buck', 'query', '-v', '0',
      "filter('__download_bower', deps(%s))" % target], env=BUCK_ENV)
  deps = deps.replace('__download_bower', '__bower_version').split()
  subprocess.check_call(['buck', 'build'] + deps, env=BUCK_ENV)

  for dep in deps:
    dep = dep.replace(':', '/').lstrip('/')
    depout = os.path.basename(dep)
    version_json = os.path.join(buck_out, 'gen', dep, depout)
    with open(version_json) as f:
      bower_json['dependencies'].update(json.load(f))

  tmpdir = tempfile.mkdtemp()
  atexit.register(lambda: shutil.rmtree(tmpdir))
  ret = os.path.join(tmpdir, 'bower.json')
  with open(ret, 'w') as f:
    json.dump(bower_json, f, indent=2)
  return ret


def get_package_name(name, package_version):
  v = package_version.lower()
  if '#' in v:
    return v[:v.find('#')]
  return name


def get_packages(path):
  with open(path) as f:
    bower_json = json.load(f)
  return dict((n, get_package_name(n, v))
              for n, v in bower_json.get('dependencies', {}).iteritems())


def collect_rules(packages):
  # TODO(dborowitz): Use run_npm_binary instead of system bower.
  rules = {}
  subprocess.check_call(['bower', 'install'])
  for dirpath, dirnames, filenames in os.walk('.', topdown=True):
    if '.bower.json' not in filenames:
      continue
    del dirnames[:]
    rule = Rule(os.path.join(dirpath, '.bower.json'))
    rules[rule.name] = rule

    # Oddly, the package name referred to in the deps section of dependents,
    # e.g. 'PolymerElements/iron-ajax', is not found anywhere in this
    # bower.json, which only contains 'iron-ajax'. Build up a map of short name
    # to package name so we can resolve them later.
    for n, v in rule.deps.iteritems():
      p = get_package_name(n, v)
      old = packages.get(n)
      if old is not None and old != p:
        raise ValueError('multiple packages named %s: %s != %s' % (n, p, old))
      packages[n] = p

  return rules


def find_buck_out():
  dir = os.getcwd()
  while not os.path.isfile(os.path.join(dir, '.buckconfig')):
    dir = os.path.dirname(dir)
  return os.path.join(dir, 'buck-out')


def main(args):
  opts = optparse.OptionParser()
  opts.add_option('-o', help='output file location')
  opts, args = opts.parse_args()

  if len(args) != 1 or not args[0].startswith('//') or not opts.o:
    return usage()
  outfile = os.path.abspath(opts.o)
  buck_out = find_buck_out()

  target = args[0]
  bower_json_path = build_bower_json(target, buck_out)
  os.chdir(os.path.dirname(bower_json_path))
  packages = get_packages(bower_json_path)
  rules = collect_rules(packages)

  with open(outfile, 'w') as f:
    for _, r in sorted(rules.iteritems()):
      f.write('\n\n%s' % r.to_rule(packages))

  print('Wrote bower_components rules to:\n  %s' % outfile)


if __name__ == '__main__':
  main(sys.argv[1:])
