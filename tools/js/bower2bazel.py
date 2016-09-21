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

"""Suggested call sequence:

python tools/js/bower2bazel.py -w lib/js/bower_archives.bzl -b lib/js/bower_components.bzl
"""

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
  "Apache-2.0": "Apache2.0",

  "BSD": "polymer",
  "MIT": "polymer",
}

package_licenses = {
  # if bower.json doesn't list a license, provide it here.
  "jquery.scrollTo": "DO_NOT_DISTRIBUTE",
  "modernizr": "DO_NOT_DISTRIBUTE",
  "webcomponentsjs": "polymer",   # self-identifies as BSD.
  "fetch": "DO_NOT_DISTRIBUTE",
  "moment": "DO_NOT_DISTRIBUTE",
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
      if "" in j:
        del j[""]  # TODO(hanwen): fugly.
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
  sys.exit(2)

def main(args):
  opts = optparse.OptionParser()
  opts.add_option('-w', help='.bzl output for WORKSPACE')
  opts.add_option('-b', help='.bzl output for //lib:BUILD')
  opts, args = opts.parse_args()

  target_str = subprocess.check_output([
    "bazel", "query", "kind(bower_component_bundle, //polygerrit-ui/...)"])
  seed_str = subprocess.check_output([
    "bazel", "query", "attr(seed, 1, kind(bower_component, deps(//polygerrit-ui/...)))"])
  targets = [s for s in target_str.split('\n') if s]
  seeds = [s for s in seed_str.split('\n') if s]
  prefix = "//lib/js:"
  non_seeds = [s for s in seeds if not s.startswith(prefix)]
  assert not non_seeds, non_seeds
  seeds = set([s[len(prefix):] for s in seeds])

  version_targets = [t + "-versions.json" for t in targets]

  subprocess.check_call(['bazel', 'build'] + version_targets)
  bower_json_path = build_bower_json(version_targets)
  dir = os.path.dirname(bower_json_path)
  cmd = bower_command(["install"])

  build_out = sys.stdout
  if opts.b:
    build_out = open(opts.b + ".tmp", 'w')

  ws_out = sys.stdout
  if opts.b:
    ws_out = open(opts.w + ".tmp", 'w')

  oldwd = os.getcwd()
  os.chdir(dir)
  subprocess.check_call(cmd)

  interpret_bower_json(seeds, ws_out, build_out)
  ws_out.close()
  build_out.close()

  os.chdir(oldwd)
  os.rename(opts.w + ".tmp", opts.w)
  os.rename(opts.b + ".tmp", opts.b)


def dump_workspace(data, seeds, out):
  out.write('# generated. DO NOT EDIT\n')
  out.write('load("//tools/bzl:js.bzl", "bower_archive")\n')
  out.write('def load_bower_archives():\n')

  for d in data:
    if d["name"] in seeds:
      continue
    out.write("""  bower_archive(
    name = "%(name)s",
    package = "%(normalized-name)s",
    version = "%(version)s",
    sha1 = "%(bazel-sha1)s")
""" % d)


def dump_build(data, seeds, out):
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
        for dep in deps:
          out.write("      \":%s\",\n" % dep)
        out.write("    ],\n")
    if d["name"] in seeds:
      out.write("    seed = True,\n")
    out.write("  )\n")
  # done


def interpret_bower_json(seeds, ws_out, build_out):
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
      pkg_name = pkg["name"]
      if pkg_name not in package_licenses:
        msg = "package %s does not specify license." % pkg_name
        sys.stderr.write(msg)
        raise Exception(msg)
      license = package_licenses[pkg_name]

    pkg["bazel-license"] = license

    # TODO(hanwen): build normalized name map.
    pkg["normalized-name"] = pkg["name"]
    data.append(pkg)

  dump_workspace(data, seeds, ws_out)
  dump_build(data, seeds, build_out)


if __name__ == '__main__':
  main(sys.argv[1:])
