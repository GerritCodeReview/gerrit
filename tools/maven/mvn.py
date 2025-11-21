#!/usr/bin/env python3
# Copyright (C) 2013 The Android Open Source Project
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

# Usage:
#   python tools/maven/mvn.py \
#     -a deploy \
#     -v 3.10.9 \
#     -s gerrit-war:war:bazel-out/.../release.war \
#     -s gerrit-extension-api:jar:bazel-out/.../extension-api_deploy.jar \
#     -s gerrit-extension-api:java-source:bazel-out/.../libapi-sources.jar \
#     -s gerrit-extension-api:javadoc:bazel-out/.../extension-api-javadoc.jar
#
# Notes:
# - 'deploy'  => JRELEASER_MAVENCENTRAL_STAGE=UPLOAD
# - POMs are taken from tools/maven/<artifact>_pom.xml
# - Artifacts are staged to tools/maven-central/staging-deploy in
#   standard Maven repo layout before JReleaser runs.
#
# Environment;
# JReleaser expects the following environment variables:
# * Portal auth credentials:
#   - JRELEASER_MAVENCENTRAL_USERNAME="<portal-username>"
#   - JRELEASER_MAVENCENTRAL_TOKEN="<portal-user-token>"
#
# * Non-interactive GPG settings
#   - JRELEASER_GPG_PASSPHRASE="<gpg-passphrase>"
#   - JRELEASER_GPG_PUBLIC_KEY=<public-key>
#   - JRELEASER_GPG_SECRET_KEY=<private-key>

from __future__ import print_function
import argparse
from os import path, environ
from subprocess import check_output, CalledProcessError
from sys import stderr

parser = argparse.ArgumentParser()
parser.add_argument('--repository', help='maven repository id')
parser.add_argument('--url', help='maven repository url')
parser.add_argument('-o')
parser.add_argument('-a', help='action (valid actions are: install,deploy)')
parser.add_argument('-v', help='gerrit version')
parser.add_argument('-s', action='append', help='triplet of artifactId:type:path')
parser.add_argument('--dry-run', action='store_true', help='only stage files; skip JReleaser')
args = parser.parse_args()

if not args.v:
    print('version is empty', file=stderr)
    exit(1)

root = path.abspath(__file__)
while not path.exists(path.join(root, 'WORKSPACE')):
    root = path.dirname(root)

GROUP_ID = 'com.google.gerrit'
JRELEASER_FILE = path.join(root, 'tools', 'maven-central', 'jreleaser.yml')
STAGING_DIR = path.join(root, 'tools', 'maven-central', 'staging-deploy')
TYPE_SUFFIX = {
    'jar': '.jar',
    'war': '.war',
    'java-source': '-sources.jar',
    'javadoc': '-javadoc.jar',
    'pom': '.pom',
    'json': '.json',
}

if 'install' == args.a:
    cmd = [
        'mvn',
        'install:install-file',
        '-Dversion=%s' % args.v,
    ]
elif 'deploy' == args.a:
    # Ensure staging dir exists and its empty
    check_output(['rm', '-rf', STAGING_DIR])
    check_output(['mkdir', '-p', STAGING_DIR])
else:
    print("unknown action -a %s" % args.a, file=stderr)
    exit(1)

group_path = GROUP_ID.replace('.', '/')
copied_poms = set()
for spec in args.s:
    artifact, packaging_type, src = spec.split(':')
    if args.a == 'install':
      exe = cmd + [
          '-DpomFile=%s' % path.join(root, 'tools', 'maven',
                                     '%s_pom.xml' % artifact),
          '-Dpackaging=%s' % packaging_type,
          '-Dfile=%s' % src,
          ]
      try:
          if environ.get('VERBOSE'):
              print(' '.join(exe), file=stderr)
          check_output(exe)
      except Exception as e:
          print('%s command failed: %s\n%s' % (args.a, ' '.join(exe), e),
                file=stderr)
          if environ.get('VERBOSE') and isinstance(e, CalledProcessError):
              print('Command output\n%s' % e.output, file=stderr)
          exit(1)
    elif args.a == 'deploy':
        if packaging_type not in TYPE_SUFFIX:
          print('unsupported type "%s" in %s' % (packaging_type, spec), file=stderr)
          exit(1)
        # Stage source file
        dst_dir = path.join(STAGING_DIR, group_path, artifact, args.v)
        check_output(['mkdir', '-p', dst_dir])
        dst_file = path.join(dst_dir, '%s-%s%s' % (artifact, args.v, TYPE_SUFFIX[packaging_type]))
        check_output(['cp', src, dst_file])
        if artifact not in copied_poms:
          pom_src = path.join(root, 'tools', 'maven', '%s_pom.xml' % artifact)
          pom_dst = path.join(dst_dir, '%s-%s.pom' % (artifact, args.v))
          check_output(['cp', pom_src, pom_dst])
          copied_poms.add(artifact)

if 'deploy' == args.a:
    cmd = [
        'env', 'JRELEASER_MAVENCENTRAL_STAGE=UPLOAD',
        'jreleaser', 'deploy',
        '-c', JRELEASER_FILE,
        '-D', 'jreleaser.project.version=%s' % args.v
    ]
    if args.dry_run:
        print('DRY RUN: Skipping remote operations.', file=stderr)
        cmd.append('--dry-run')

    if environ.get('VERBOSE'):
        print(' '.join(cmd), file=stderr)
    check_output(cmd)

out = stderr
if args.o:
    out = open(args.o, 'w')

with out as fd:
    if args.repository:
        print('Repository: %s' % args.repository, file=fd)
    if args.url:
        print('URL: %s' % args.url, file=fd)
    print('Version: %s' % args.v, file=fd)
