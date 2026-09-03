#!/usr/bin/env python3
# Copyright (C) 2026 The Android Open Source Project
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

"""Mirror the PolyGerrit github-hosted npm tarballs to gs://gerrit-maven.

Five PolyGerrit npm deps are commit-pinned codeload.github.com tarballs
(polygerrit-ui/app/pnpm-lock.yaml), so a cold build hits github. This
uploads them, path-for-path, under gs://gerrit-maven/npm/; the
gerrit-maven-mirror downloader config rewrites codeload to gerrit-maven at
fetch time. The lockfile entries have no integrity field, so
tools/npm_github_deps.sha256 is the content pin, checked on download and on
upload.

  tools/bzl/mirror_github_npm_deps.py                    # verify (default)
  tools/bzl/mirror_github_npm_deps.py --manifest-update
  tools/bzl/mirror_github_npm_deps.py --upload
"""

import argparse
import hashlib
import os
import re
import subprocess
import sys
import tempfile
import urllib.request
from pathlib import Path

BUCKET = "gs://gerrit-maven"
MIRROR_HOST = "gerrit-maven.storage.googleapis.com"
NPM_PREFIX = "npm"
LOCKFILE = "polygerrit-ui/app/pnpm-lock.yaml"
MANIFEST = "tools/npm_github_deps.sha256"
MANIFEST_HEADER = """\
# SHA-256 of the PolyGerrit npm tarballs mirrored to gs://gerrit-maven.
# These pnpm-lock.yaml entries have no integrity field; this is the pin.
# Regenerate: tools/bzl/mirror_github_npm_deps.py --manifest-update.
"""

CODELOAD_RE = re.compile(
    r"https://codeload\.github\.com/(?P<path>[^\s'\"}]+/tar\.gz/[0-9a-f]+)"
)


def repo_root():
    # bazel run sets BUILD_WORKSPACE_DIRECTORY; git rev-parse fails there.
    workspace = os.environ.get("BUILD_WORKSPACE_DIRECTORY")
    if workspace:
        return Path(workspace)
    out = subprocess.check_output(
        ["git", "rev-parse", "--show-toplevel"], text=True
    )
    return Path(out.strip())


def codeload_urls(lockfile):
    """Return the distinct codeload tarball URLs referenced by the lockfile."""
    text = Path(lockfile).read_text(encoding="utf-8")
    return sorted({m.group(0) for m in CODELOAD_RE.finditer(text)})


def npm_path(url):
    return f"{NPM_PREFIX}/{CODELOAD_RE.match(url).group('path')}"


def load_manifest(path):
    expected = {}
    for line in Path(path).read_text(encoding="utf-8").splitlines():
        line = line.strip()
        if line and not line.startswith("#"):
            sha, rel = line.split(None, 1)
            expected[rel] = sha
    return expected


def download(url):
    with urllib.request.urlopen(url, timeout=60) as resp:
        return resp.read()


def sha256(data):
    return hashlib.sha256(data).hexdigest()


def object_exists(obj):
    return subprocess.run(
        ["gcloud", "storage", "ls", obj],
        stdout=subprocess.DEVNULL,
        stderr=subprocess.DEVNULL,
    ).returncode == 0


def object_upload(local_path, obj):
    # --no-clobber: never overwrite an existing mirrored object.
    subprocess.check_call(
        ["gcloud", "storage", "cp", "--no-clobber", local_path, obj]
    )


def write_manifest(root, urls):
    lines = [f"{sha256(download(url))}  {npm_path(url)}" for url in urls]
    Path(root / MANIFEST).write_text(
        MANIFEST_HEADER + "\n".join(lines) + "\n", encoding="utf-8"
    )
    print(f"Wrote {len(lines)} entries to {MANIFEST}. Review the diff.")


def main():
    ap = argparse.ArgumentParser(description=__doc__)
    g = ap.add_mutually_exclusive_group()
    g.add_argument("--dry-run", action="store_true",
                   help="verify only (default)")
    g.add_argument("--manifest-update", action="store_true",
                   help="regenerate the manifest from codeload")
    g.add_argument("--upload", action="store_true",
                   help="upload to the bucket (needs write access)")
    args = ap.parse_args()

    root = repo_root()
    urls = codeload_urls(root / LOCKFILE)
    if not urls:
        print(f"No codeload.github.com tarballs found in {LOCKFILE}.")
        return 0

    if args.manifest_update:
        write_manifest(root, urls)
        return 0

    expected = load_manifest(root / MANIFEST)
    print(f"Verifying {len(urls)} github-hosted npm tarball(s) against "
          f"{MANIFEST}:\n")
    rc = 0
    for url in urls:
        rel = npm_path(url)
        want = expected.get(rel)
        if want is None:
            print(f"  ERROR: {rel} is not in {MANIFEST}. Run "
                  f"--manifest-update and review.")
            rc = 1
            continue
        got = sha256(download(url))
        if got != want:
            print(f"  MISMATCH {rel}\n    manifest {want}\n    codeload {got}")
            rc = 1
            continue
        print(f"  OK  {rel}  sha256:{got[:16]}...")

        if args.upload:
            dest = f"{BUCKET}/{rel}"
            if object_exists(dest):
                have = sha256(download(f"https://{MIRROR_HOST}/{rel}"))
                if have != want:
                    print(f"  ERROR: {dest} exists but sha256 {have} != "
                          f"manifest {want}; refusing to proceed.")
                    rc = 1
                else:
                    print("    already mirrored, verified.")
                continue
            with tempfile.NamedTemporaryFile(suffix=".tar.gz") as tmp:
                tmp.write(download(url))
                tmp.flush()
                object_upload(tmp.name, dest)
            print(f"    uploaded to {dest}")

    if rc == 0 and not args.upload:
        print("\nAll downloads match the manifest. Re-run with --upload (and "
              "gs://gerrit-maven write access) to mirror.")
    return rc


if __name__ == "__main__":
    sys.exit(main())
