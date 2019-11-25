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

"""
Suggested call sequence:

python tools/js/bower2bazel.py -w lib/js/bower_archives.bzl \
  -b lib/js/bower_components.bzl
"""

from __future__ import print_function

import collections
import json
import hashlib
import optparse
import os
import subprocess
import sys
import tempfile
import glob
import bowerutil

# list of licenses for packages that don't specify one in their bower.json file
package_licenses = {
    "codemirror-minified": "codemirror-minified",
    "es6-promise": "es6-promise",
    "fetch": "fetch",
    "font-roboto-local": "polymer",
    "iron-a11y-announcer": "polymer",
    "iron-a11y-keys-behavior": "polymer",
    "iron-autogrow-textarea": "polymer",
    "iron-behaviors": "polymer",
    "iron-dropdown": "polymer",
    "iron-fit-behavior": "polymer",
    "iron-flex-layout": "polymer",
    "iron-form-element-behavior": "polymer",
    "iron-icon": "polymer",
    "iron-iconset-svg": "polymer",
    "iron-input": "polymer",
    "iron-menu-behavior": "polymer",
    "iron-meta": "polymer",
    "iron-overlay-behavior": "polymer",
    "iron-resizable-behavior": "polymer",
    "iron-selector": "polymer",
    "iron-validatable-behavior": "polymer",
    "moment": "moment",
    "neon-animation": "polymer",
    "page": "page.js",
    "paper-button": "polymer",
    "paper-icon-button": "polymer",
    "paper-input": "polymer",
    "paper-item": "polymer",
    "paper-listbox": "polymer",
    "paper-toggle-button": "polymer",
    "paper-styles": "polymer",
    "paper-tabs": "polymer",
    "polymer": "polymer",
    "polymer-resin": "polymer",
    "promise-polyfill": "promise-polyfill",
    "web-animations-js": "Apache2.0",
    "webcomponentsjs": "polymer",
    "paper-material": "polymer",
    "paper-styles": "polymer",
    "paper-behaviors": "polymer",
    "paper-ripple": "polymer",
    "iron-checked-element-behavior": "polymer",
    "font-roboto-local": "polymer",
}


def build_bower_json(version_targets, seeds):
    """Generate bower JSON file, return its path.

    Args:
      version_targets: bazel target names of the versions.json file.
      seeds: an iterable of bower package names of the seed packages, ie.
        the packages whose versions we control manually.
    """
    bower_json = collections.OrderedDict()
    bower_json['name'] = 'bower2bazel-output'
    bower_json['version'] = '0.0.0'
    bower_json['description'] = 'Auto-generated bower.json for dependency ' + \
                                'management'
    bower_json['private'] = True
    bower_json['dependencies'] = {}

    seeds = set(seeds)
    for v in version_targets:
        path = os.path.join("bazel-out/*-fastbuild/bin",
                            v.lstrip("/").replace(":", "/"))
        fs = glob.glob(path)
        err_msg = '%s: file not found or multiple files found: %s' % (path, fs)
        assert len(fs) == 1, err_msg
        with open(fs[0]) as f:
            j = json.load(f)
            if "" in j:
                # drop dummy entries.
                del j[""]

            trimmed = {}
            for k, v in j.items():
                if k in seeds:
                    trimmed[k] = v

            bower_json['dependencies'].update(trimmed)

    tmpdir = tempfile.mkdtemp()
    ret = os.path.join(tmpdir, 'bower.json')
    with open(ret, 'w') as f:
        json.dump(bower_json, f, indent=2)
    return ret


def decode(input):
    try:
        return input.decode("utf-8")
    except TypeError:
        return input


def bower_command(args):
    base = subprocess.check_output(["bazel", "info", "output_base"]).strip()
    exp = os.path.join(decode(base), "external", "bower", "*npm_binary.tgz")
    fs = sorted(glob.glob(exp))
    err_msg = "bower tarball not found or have multiple versions %s" % fs
    assert len(fs) == 1, err_msg
    return ["python",
            os.getcwd() + "/tools/js/run_npm_binary.py", sorted(fs)[0]] + args


def main(args):
    opts = optparse.OptionParser()
    opts.add_option('-w', help='.bzl output for WORKSPACE')
    opts.add_option('-b', help='.bzl output for //lib:BUILD')
    opts, args = opts.parse_args()

    target_str = subprocess.check_output([
        "bazel", "query", "kind(bower_component_bundle, //polygerrit-ui/...)"])
    seed_str = subprocess.check_output(
        ["bazel", "query",
         "attr(seed, 1, kind(bower_component, deps(//polygerrit-ui/...)))"])
    targets = [s for s in decode(target_str).split('\n') if s]
    seeds = [s for s in decode(seed_str).split('\n') if s]
    prefix = "//lib/js:"
    non_seeds = [s for s in seeds if not s.startswith(prefix)]
    assert not non_seeds, non_seeds
    seeds = set([s[len(prefix):] for s in seeds])

    version_targets = [t + "-versions.json" for t in targets]
    subprocess.check_call(['bazel', 'build'] + version_targets)
    bower_json_path = build_bower_json(version_targets, seeds)
    dir = os.path.dirname(bower_json_path)
    cmd = bower_command(["install"])

    build_out = sys.stdout
    if opts.b:
        build_out = open(opts.b + ".tmp", 'w')

    ws_out = sys.stdout
    if opts.b:
        ws_out = open(opts.w + ".tmp", 'w')

    header = """# DO NOT EDIT
# generated with the following command:
#
#   %s
#

""" % ' '.join(sys.argv)

    ws_out.write(header)
    build_out.write(header)

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
    out.write('load("//tools/bzl:js.bzl", "bower_archive")\n\n')
    out.write('def load_bower_archives():\n')

    for d in data:
        if d["name"] in seeds:
            continue
        out.write("""    bower_archive(
        name = "%(name)s",
        package = "%(normalized-name)s",
        version = "%(version)s",
        sha1 = "%(bazel-sha1)s",
    )
""" % d)


def dump_build(data, seeds, out):
    out.write('load("//tools/bzl:js.bzl", "bower_component")\n\n')
    out.write('def define_bower_components():\n')
    for d in data:
        out.write("    bower_component(\n")
        out.write("        name = \"%s\",\n" % d["name"])
        out.write("        license = \"//lib:LICENSE-%s\",\n" % d["bazel-license"])
        deps = sorted(d.get("dependencies", {}).keys())
        if deps:
            if len(deps) == 1:
                out.write("        deps = [\":%s\"],\n" % deps[0])
            else:
                out.write("        deps = [\n")
                for dep in deps:
                    out.write("            \":%s\",\n" % dep)
                out.write("        ],\n")
        if d["name"] in seeds:
            out.write("        seed = True,\n")
        out.write("    )\n")
    # done


def interpret_bower_json(seeds, ws_out, build_out):
    out = subprocess.check_output(["find", "bower_components/", "-name",
                                   ".bower.json"])

    data = []
    for f in sorted(decode(out).split('\n')):
        if not f:
            continue
        pkg = json.load(open(f))
        pkg_name = pkg["name"]

        pkg["bazel-sha1"] = bowerutil.hash_bower_component(
            hashlib.sha1(), os.path.dirname(f)).hexdigest()
        license = package_licenses.get(pkg_name, "DO_NOT_DISTRIBUTE")

        pkg["bazel-license"] = license
        pkg["normalized-name"] = pkg["_originalSource"]
        data.append(pkg)

    dump_workspace(data, seeds, ws_out)
    dump_build(data, seeds, build_out)


if __name__ == '__main__':
    main(sys.argv[1:])
