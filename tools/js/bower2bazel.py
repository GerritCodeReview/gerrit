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
import glob
import bowerutil

license_map = {
  "http://polymer.github.io/LICENSE.txt": "polymer",
}

package_licenses = {
  # if bower.json doesn't list a license, provide it here.
  "dummy": "DO_NOT_DISTRIBUTE",
  "jquery.scrollTo": "DO_NOT_DISTRIBUTE",
  "modernizr": "DO_NOT_DISTRIBUTE",
}


def build_bower_json(version_targets):
  """Generate bower JSON file, return its path."""
  bower_json = collections.OrderedDict()
  bower_json['name'] = 'bower2buck-output'
  bower_json['version'] = '0.0.0'
  bower_json['description'] = 'Auto-generated bower.json for dependency management'
  bower_json['private'] = True
  bower_json['dependencies'] = {}

  for v in version_targets:
    fn = os.path.join("bazel-out/local-fastbuild/bin", v.lstrip("/").replace(":", "/"))
    with open(fn) as f:
      j = json.load(f)
      if "dummy" in j:
        del j["dummy"]  # TODO(hanwen): fugly.
      bower_json['dependencies'].update(j)

  tmpdir = tempfile.mkdtemp()
#  atexit.register(lambda: shutil.rmtree(tmpdir))
  print(tmpdir)
  ret = os.path.join(tmpdir, 'bower.json')
  with open(ret, 'w') as f:
    json.dump(bower_json, f, indent=2)
  return ret


def bower_command(args):
  base = subprocess.check_output(["bazel", "info", "output_base"]).strip()
  exp = os.path.join(base, "external", "bower", "*npm_binary.tgz")
  fs = glob.glob(exp)
  assert fs, "bower tarball not found"
  return ["python", os.getcwd() + "/tools/js/run_npm_binary.py", sorted(fs)[0]] + args


def usage():
  sys.stderr.write("supply the -o flag\n")
  sys.exit(2)

def main(args):
  opts = optparse.OptionParser()
  opts.add_option('-o', help='output file location')
  opts, args = opts.parse_args()

  if not opts.o: #  or not all(a.startswith('//') for a in args):
    return usage()
  outfile = os.path.abspath(opts.o)

  target_str = subprocess.check_output([
    "bazel", "query", "kind(bower_component_bundle, //polygerrit-ui/...)"])
  targets = [s for s in target_str.split('\n') if s]

  version_targets = [t + "-versions.json" for t in targets]

  subprocess.check_call(['bazel', 'build'] + version_targets)
  bower_json_path = build_bower_json(version_targets)
  dir = os.path.dirname(bower_json_path)
  cmd = bower_command(["install"])

  os.chdir(dir)
  subprocess.check_call(cmd)
  interpret_bower_json(dir)


def dump_workspace(data, out):
  out.write('load("//tools/bzl:js.bzl", "bower_archive")\n')
  out.write('def load_bower_archives():\n')

  for d in data:
    out.write("""  bower_archive(
    name = "%(name)s",
    package = "%(normalized-name)s",
    version = "%(version)s",
    sha1 = "%(bazel-sha1)s")
""" % d)


def dump_build(data, out):
  out.write('# Generated. DO NOT EDIT.\n')
  out.write('load("//tools/bzl:js.bzl", "bower_component")\n')
  out.write('def define_bower_components():\n')
  for d in data:
    out.write("  bower_component(\n")
    out.write("    name = \"%s\",\n" % d["name"])
    out.write("    license = \"%s\",\n" % d["bazel-license"])
    deps = sorted(d.get("dependencies", {}).keys())
    if deps:
      if len(deps) == 1:
        out.write("    deps = [ \":%s\" ],\n" % deps[0])
      else:
        out.write("    deps = [\n")
        for d in deps:
          out.write("      \":%s\",\n" % d)
        out.write("    ],\n")
    out.write("  )\n")
  # done


def interpret_bower_json(d):
  os.chdir(d)
  out = subprocess.check_output(["find", "bower_components/", "-name", ".bower.json"])

  data = []
  for f in sorted(out.split('\n')):
    if not f:
      continue
    pkg = json.load(open(f))
    pkg["bazel-sha1"] = bowerutil.hash_bower_component(
      hashlib.sha1(), os.path.dirname(f)).hexdigest()
    license = pkg.get("license", None)
    if type(license) == type([]):
      # WTF?
      license = license[0]

    if license:
      license = license_map.get(license, license)
    else:
      license = package_licenses[pkg["name"]]

    pkg["bazel-license"] = license

    # TODO(hanwen): build normalized name map.
    pkg["normalized-name"] = pkg["name"]
    data.append(pkg)

  dump_workspace(data, sys.stdout)
  dump_build(data, sys.stdout)


if __name__ == '__main__':
  main(sys.argv[1:])
