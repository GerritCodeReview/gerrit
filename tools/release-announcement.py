#!/usr/bin/env python
# Copyright (C) 2017 The Android Open Source Project
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
import os
import sys
from gnupg import GPG
from jinja2 import Template


class Version:
    def __init__(self, version):
        self.version = version
        parts = version.split('.')
        if len(parts) > 2:
            self.major = ".".join(parts[:2])
            self.patch = version
        else:
            self.major = version
            self.patch = None

    def __str__(self):
        return self.version


def _main():
    descr = 'Generate Gerrit release announcement email text'
    parser = argparse.ArgumentParser(
        description=descr,
        formatter_class=argparse.ArgumentDefaultsHelpFormatter)
    parser.add_argument('-v', '--version', dest='version',
                        required=True,
                        help='gerrit version to release')
    parser.add_argument('-p', '--previous', dest='previous',
                        help='previous gerrit version (optional)')
    parser.add_argument('-s', '--summary', dest='summary',
                        help='summary of the release content (optional)')
    options = parser.parse_args()

    summary = options.summary
    if summary and not summary.endswith("."):
        summary = summary + "."

    data = {
         "version": Version(options.version),
         "previous": options.previous,
         "summary": summary
    }

    war = os.path.join(
        os.path.expanduser("~/.m2/repository/com/google/gerrit/gerrit-war/"),
        "%(version)s/gerrit-war-%(version)s.war" % data)
    if not os.path.isfile(war):
        print("Could not find war file for Gerrit %s in local Maven repository"
              % data["version"], file=sys.stderr)
        sys.exit(1)

    md5 = hashlib.md5()
    sha1 = hashlib.sha1()
    sha256 = hashlib.sha256()
    BUF_SIZE = 65536  # Read data in 64kb chunks
    with open(war, 'rb') as f:
        while True:
            d = f.read(BUF_SIZE)
            if not d:
                break
            md5.update(d)
            sha1.update(d)
            sha256.update(d)

    data["sha1"] = sha1.hexdigest()
    data["sha256"] = sha256.hexdigest()
    data["md5"] = md5.hexdigest()

    template = Template(open("tools/release-announcement-template.txt").read())
    output = template.render(data=data)

    filename = "release-announcement-gerrit-%s.txt" % data["version"]
    with open(filename, "w") as f:
        f.write(output)

    gpghome = os.path.abspath(os.path.expanduser("~/.gnupg"))
    if not os.path.isdir(gpghome):
        print("Skipping signing due to missing gnupg home folder")
    else:
        gpg = GPG(homedir=gpghome)
        signed = gpg.sign(output)
        filename = filename + ".asc"
        with open(filename, "w") as f:
            f.write(str(signed))


if __name__ == "__main__":
    _main()
