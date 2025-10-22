#!/usr/bin/env python3
#
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
#
# mvn_portal.py - Stage Bazel-produced artifacts and release via
#                 Maven Central Portal using JReleaser.
#
# Usage:
#   python tools/maven/mvn_portal.py \
#     -a deploy \
#     -v 3.10.9 \
#     -s gerrit-war:war:bazel-out/.../release.war \
#     -s gerrit-extension-api:jar:bazel-out/.../extension-api_deploy.jar \
#     -s gerrit-extension-api:java-source:bazel-out/.../libapi-src.jar \
#     -s gerrit-extension-api:javadoc:bazel-out/.../extension-api-javadoc.zip
#
# Notes:
# - 'deploy'  => JRELEASER_MAVENCENTRAL_STAGE=UPLOAD
# - 'publish' => JRELEASER_MAVENCENTRAL_STAGE=PUBLISH
# - POMs are taken from tools/maven/<artifact>_pom.xml
# - Artifacts are staged to tools/maven-central/staging-deploy in
#   standard Maven repo layout before JReleaser runs.
#
from __future__ import print_function
import argparse
import os
import shutil
import sys
from os import path, environ
from subprocess import run, CalledProcessError

# ---------- CLI ----------
parser = argparse.ArgumentParser()
parser.add_argument('--repository', help='(ignored) present for CLI compatibility')
parser.add_argument('--url', help='(ignored) present for CLI compatibility')
parser.add_argument('-o', help='optional output file (summary)')
parser.add_argument('-a', help='action: deploy (upload) or publish (upload+publish)', required=True)
parser.add_argument('-v', help='release version (no -SNAPSHOT)', required=True)
parser.add_argument('-s', action='append', help='triplet artifactId:type:path', required=True)
parser.add_argument('--group', default='com.google.gerrit', help='Maven groupId (default: com.google.gerrit)')
parser.add_argument('--staging-dir', default='tools/maven-central/staging-deploy')
parser.add_argument('--pom-dir', default='tools/maven')
parser.add_argument('--jreleaser-file', default='tools/maven-central/jreleaser.yml')
parser.add_argument('--dry-run', action='store_true', help='only stage files; skip JReleaser')
args = parser.parse_args()

if args.v.endswith('-SNAPSHOT'):
    print('ERROR: version must be a release (no -SNAPSHOT) for Maven Central.', file=sys.stderr)
    sys.exit(1)

# Find repo root (like original mvn.py)
root = path.abspath(__file__)
while not path.exists(path.join(root, 'WORKSPACE')) and root != '/':
    root = path.dirname(root)
if root == '/':
    print('ERROR: could not locate repository root (WORKSPACE not found)', file=sys.stderr)
    sys.exit(1)

# ---------- helpers ----------
def info(msg):  print(msg, file=sys.stderr)
def die(msg):   print(msg, file=sys.stderr); sys.exit(1)

TYPE_SUFFIX = {
    'jar':        '.jar',
    'war':        '.war',
    'java-source': '-sources.jar',
    'javadoc':     '-javadoc.jar',
    'pom':        '.pom',
    'json':       '.json',
    'sh':         '.sh',
}

def ensure_dir(d):
    os.makedirs(d, exist_ok=True)

def stage_file(src, dst_dir, artifact, version, suffix):
    ensure_dir(dst_dir)
    base = f'{artifact}-{version}{suffix}'
    dst = path.join(dst_dir, base)
    shutil.copy2(src, dst)
    return dst

def artifact_dir(group, artifact, version, staging_root):
    group_path = group.replace('.', '/')
    return path.join(staging_root, group_path, artifact, version)

# Track which POMs we already copied per artifact
copied_poms = set()

# Clean staging dir
staging_root = path.join(root, args.staging_dir)
if path.isdir(staging_root):
    shutil.rmtree(staging_root)
ensure_dir(staging_root)

# ---------- stage artifacts + POMs ----------
for spec in args.s:
    try:
        artifact, typ, src = spec.split(':', 2)
    except ValueError:
        die(f'Invalid -s spec (expected artifact:type:path): {spec}')

    if typ not in TYPE_SUFFIX:
        die(f'Unsupported type "{typ}" in {spec}')

    src_abs = src if path.isabs(src) else path.join(root, src)
    if not path.isfile(src_abs):
        die(f'File not found: {src_abs}')

    dst_dir = artifact_dir(args.group, artifact, args.v, staging_root)
    # Copy the payload
    dst = stage_file(src_abs, dst_dir, artifact, args.v, TYPE_SUFFIX[typ])
    if environ.get('VERBOSE'):
        info(f'Staged {src_abs} -> {dst}')

    # Ensure POM is present for the artifact (once)
    if artifact not in copied_poms:
        pom_src = path.join(root, args.pom_dir, f'{artifact}_pom.xml')
        if not path.isfile(pom_src):
            die(f'POM not found for {artifact}: {pom_src}')
        pom_dst = path.join(dst_dir, f'{artifact}-{args.v}.pom')
        shutil.copy2(pom_src, pom_dst)
        copied_poms.add(artifact)
        if environ.get('VERBOSE'):
            info(f'POM staged {pom_src} -> {pom_dst}')

# ---------- run JReleaser ----------
if args.dry_run:
    info('DRY RUN: staging complete; skipping jreleaser deploy.')
    sys.exit(0)

stage = args.a.lower()
if stage not in ('deploy', 'publish'):
    die(f'Unknown action -a {args.a}. Use "deploy" (UPLOAD) or "publish" (PUBLISH).')

env = os.environ.copy()
env_stage = 'UPLOAD' if stage == 'deploy' else 'PUBLISH'
env['JRELEASER_MAVENCENTRAL_STAGE'] = env_stage

cmd = [
    'jreleaser', 'deploy',
    '-f', path.join(root, args.jreleaser_file),
    '-P', f'version={args.v}'
]
try:
    if environ.get('VERBOSE'):
        info('Running: ' + ' '.join(cmd) + f' (JRELEASER_MAVENCENTRAL_STAGE={env_stage})')
    run(cmd, check=True, cwd=root, env=env)
except CalledProcessError as e:
    die(f'jreleaser deploy failed with exit code {e.returncode}')

# ---------- summary ----------
out = sys.stderr
if args.o:
    out = open(args.o, 'w')

with out as fd:
    print(f'Version: {args.v}', file=fd)
    print(f'GroupId: {args.group}', file=fd)
    print(f'JReleaser stage: {env_stage}', file=fd)
    print(f'Staging dir: {path.relpath(staging_root, root)}', file=fd)
