#!/usr/bin/env python3
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
from distutils import spawn
import hashlib
import os
import shutil
import subprocess
import sys
import tarfile
import tempfile


def extract(path, outdir, bin):
    if os.path.exists(os.path.join(outdir, bin)):
        return  # Another process finished extracting, ignore.

    # Use a temp directory adjacent to outdir so shutil.move can use the same
    # device atomically.
    tmpdir = tempfile.mkdtemp(dir=os.path.dirname(outdir))

    def cleanup():
        try:
            shutil.rmtree(tmpdir)
        except OSError:
            pass  # Too late now
    atexit.register(cleanup)

    def extract_one(mem):
        dest = os.path.join(outdir, mem.name)
        tar.extract(mem, path=tmpdir)
        try:
            os.makedirs(os.path.dirname(dest))
        except OSError:
            pass  # Either exists, or will fail on the next line.
        shutil.move(os.path.join(tmpdir, mem.name), dest)

    with tarfile.open(path, 'r:gz') as tar:
        for mem in tar.getmembers():
            if mem.name != bin:
                extract_one(mem)
        # Extract bin last so other processes only short circuit when
        # extraction is finished.
        if bin in tar.getnames():
            extract_one(tar.getmember(bin))


def main(args):
    path = args[0]
    suffix = '.npm_binary.tgz'
    tgz = os.path.basename(path)

    parts = tgz[:-len(suffix)].split('@')

    if not tgz.endswith(suffix) or len(parts) != 2:
        print('usage: %s <path/to/npm_binary>' % sys.argv[0], file=sys.stderr)
        return 1

    name, _ = parts

    # Avoid importing from gerrit because we don't want to depend on the right
    # working directory
    sha1 = hashlib.sha1(open(path, 'rb').read()).hexdigest()
    outdir = '%s-%s' % (path[:-len(suffix)], sha1)
    rel_bin = os.path.join('package', 'bin', name)
    rel_lib_bin = os.path.join('package', 'lib', 'bin', name + '.js')
    bin = os.path.join(outdir, rel_bin)
    libbin = os.path.join(outdir, rel_lib_bin)
    if not os.path.isfile(bin):
        extract(path, outdir, rel_bin)

    nodejs = spawn.find_executable('nodejs')
    if nodejs:
        # Debian installs Node.js as 'nodejs', due to a conflict with another
        # package.
        if not os.path.isfile(bin) and os.path.isfile(libbin):
            subprocess.check_call([nodejs, libbin] + args[1:])
        else:
            subprocess.check_call([nodejs, bin] + args[1:])
    elif not os.path.isfile(bin) and os.path.isfile(libbin):
        subprocess.check_call([libbin] + args[1:])
    else:
        subprocess.check_call([bin] + args[1:])


if __name__ == '__main__':
    sys.exit(main(sys.argv[1:]))
