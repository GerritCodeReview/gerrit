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

import argparse
import hashlib
import json
import os
import shutil
import subprocess
import sys

import bowerutil

CACHE_DIR = os.path.expanduser(os.path.join(
    '~', '.gerritcodereview', 'bazel-cache', 'downloaded-artifacts'))


def bower_cmd(bower, *args):
    cmd = bower.split(' ')
    cmd.extend(args)
    return cmd


def bower_info(bower, name, package, version):
    cmd = bower_cmd(bower, '-l=error', '-j',
                    'info', '%s#%s' % (package, version))
    try:
        p = subprocess.Popen(cmd, stdout=subprocess.PIPE,
                             stderr=subprocess.PIPE)
    except:
        sys.stderr.write("error executing: %s\n" % ' '.join(cmd))
        raise
    out, err = p.communicate()
    if p.returncode:
        # For python3 support we wrap str around err.
        sys.stderr.write(str(err))
        raise OSError('Command failed: %s' % ' '.join(cmd))

    try:
        info = json.loads(out)
    except ValueError:
        raise ValueError('invalid JSON from %s:\n%s' % (" ".join(cmd), out))
    info_name = info.get('name')
    if info_name != name:
        raise ValueError(
            'expected package name %s, got: %s' % (name, info_name))
    return info


def ignore_deps(info):
    # Tell bower to ignore dependencies so we just download this component.
    # This is just an optimization, since we only pick out the component we
    # need, but it's important when downloading sizable dependency trees.
    #
    # As of 1.6.5 I don't think ignoredDependencies can be specified on the
    # command line with --config, so we have to create .bowerrc.
    deps = info.get('dependencies')
    if deps:
        with open(os.path.join('.bowerrc'), 'w') as f:
            json.dump({'ignoredDependencies': list(deps.keys())}, f)


def cache_entry(name, package, version, sha1):
    if not sha1:
        sha1 = hashlib.sha1('%s#%s' % (package, version)).hexdigest()
    return os.path.join(CACHE_DIR, '%s-%s.zip-%s' % (name, version, sha1))


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument('-n', help='short name of component')
    parser.add_argument('-b', help='bower command')
    parser.add_argument('-p', help='full package name of component')
    parser.add_argument('-v', help='version number')
    parser.add_argument('-s', help='expected content sha1')
    parser.add_argument('-o', help='output file location')
    args = parser.parse_args()

    assert args.p
    assert args.v
    assert args.n

    cwd = os.getcwd()
    outzip = os.path.join(cwd, args.o)
    cached = cache_entry(args.n, args.p, args.v, args.s)

    if not os.path.exists(cached):
        info = bower_info(args.b, args.n, args.p, args.v)
        ignore_deps(info)
        subprocess.check_call(
            bower_cmd(
                args.b, '--quiet', 'install', '%s#%s' % (args.p, args.v)))
        bc = os.path.join(cwd, 'bower_components')
        subprocess.check_call(
            ['zip', '-q', '--exclude', '.bower.json', '-r', cached, args.n],
            cwd=bc)

        if args.s:
            path = os.path.join(bc, args.n)
            sha1 = bowerutil.hash_bower_component(
                hashlib.sha1(), path).hexdigest()
            if args.s != sha1:
                print((
                    '%s#%s:\n'
                    'expected %s\n'
                    'received %s\n') % (args.p, args.v, args.s, sha1),
                    file=sys.stderr)
                try:
                    os.remove(cached)
                except OSError as err:
                    if path.exists(cached):
                        print('error removing %s: %s' % (cached, err),
                              file=sys.stderr)
                return 1

    shutil.copyfile(cached, outzip)
    return 0


if __name__ == '__main__':
    sys.exit(main())
