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

"""Mirror the protobuf prebuilt protoc binaries to gs://gerrit-maven.

The protobuf module resolves fine through the BCR mirror, but its
prebuilt_protoc_repo rule then downloads a protoc binary from a github
release (bazel/private/oss/toolchains/prebuilt/protoc_toolchain.bzl), via
a single URL with no mirror fallback, so a cold build hits github. This
uploads the binaries, path-for-path, under gs://gerrit-maven/protobuf/;
the gerrit-maven-mirror downloader config rewrites the github URL there.
Only the platforms Gerrit builds on (osx/linux, x86_64/aarch64) are
mirrored.

tools/protoc.sha256 is the upload content pin; Bazel enforces protobuf's
own integrity on fetch, so the mirror is trustless.

Reads the version and filenames from the checked-out protobuf module in
the bazel output_base, so run a build first. Upstream mirror-fallback
request: protocolbuffers/protobuf#29572.

  tools/bzl/mirror_protobuf_toolchain.py                 # verify (default)
  tools/bzl/mirror_protobuf_toolchain.py --manifest-update
  tools/bzl/mirror_protobuf_toolchain.py --upload
"""

import argparse
import glob
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
MANIFEST = "tools/protoc.sha256"
GITHUB = "https://github.com/protocolbuffers/protobuf/releases/download"
# Arch/OS tokens Gerrit does not build on; their binaries are not mirrored.
SKIP = ("win", "ppcle", "s390", "x86_32")
MANIFEST_HEADER = """\
# SHA-256 of the protobuf protoc binaries mirrored to gs://gerrit-maven.
# Format: <sha256>  protobuf/releases/download/<tag>/<file>.
# Regenerate: tools/bzl/mirror_protobuf_toolchain.py --manifest-update.
"""


def repo_root():
    # bazel run sets BUILD_WORKSPACE_DIRECTORY; git rev-parse fails there.
    workspace = os.environ.get("BUILD_WORKSPACE_DIRECTORY")
    if workspace:
        return Path(workspace)
    out = subprocess.check_output(
        ["git", "rev-parse", "--show-toplevel"], text=True
    )
    return Path(out.strip())


def protobuf_integrity():
    """Return (release_version, [filename]) from the protobuf module."""
    ob = subprocess.check_output(
        ["bazelisk", "info", "output_base"], text=True,
        stderr=subprocess.DEVNULL,
    ).strip()
    hits = glob.glob(
        f"{ob}/external/protobuf+/bazel/private/oss/toolchains/"
        "prebuilt/tool_integrity.bzl"
    )
    if not hits:
        sys.exit("protobuf not found in output_base; run a build first.")
    text = Path(hits[0]).read_text(encoding="utf-8")
    ver = re.search(r'RELEASE_VERSION\s*=\s*"([^"]+)"', text).group(1)
    files = re.findall(r'"(protoc-[^"]+\.zip)":', text)
    return ver, [f for f in files if not any(t in f for t in SKIP)]


def entries():
    ver, files = protobuf_integrity()
    return [(f"{GITHUB}/{ver}/{fn}",
             f"protobuf/releases/download/{ver}/{fn}") for fn in files]


def download(url):
    with urllib.request.urlopen(url, timeout=60) as resp:
        return resp.read()


def sha256(data):
    return hashlib.sha256(data).hexdigest()


def load_manifest(path):
    expected = {}
    for line in Path(path).read_text(encoding="utf-8").splitlines():
        line = line.strip()
        if line and not line.startswith("#"):
            sha, rel = line.split(None, 1)
            expected[rel] = sha
    return expected


def object_exists(obj):
    return subprocess.run(
        ["gcloud", "storage", "ls", obj],
        stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL,
    ).returncode == 0


def object_upload(local, obj):
    # --no-clobber: never overwrite an existing mirrored object.
    subprocess.check_call(
        ["gcloud", "storage", "cp", "--no-clobber", local, obj]
    )


def write_manifest(root, items):
    lines = [f"{sha256(download(url))}  {rel}" for url, rel in items]
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
                   help="regenerate the manifest from protobuf")
    g.add_argument("--upload", action="store_true",
                   help="upload to the bucket (needs write access)")
    args = ap.parse_args()

    root = repo_root()
    items = entries()
    if args.manifest_update:
        write_manifest(root, items)
        return 0

    expected = load_manifest(root / MANIFEST)
    print(f"Verifying {len(items)} protoc binary(ies) against "
          f"{MANIFEST}:\n")
    rc = 0
    for url, rel in items:
        want = expected.get(rel)
        if want is None:
            print(f"  ERROR: {rel} is not in {MANIFEST}. Run "
                  f"--manifest-update and review.")
            rc = 1
            continue
        got = sha256(download(url))
        if got != want:
            print(f"  MISMATCH {rel}\n    manifest {want}\n    github   {got}")
            rc = 1
            continue
        print(f"  OK  {rel}")
        if args.upload:
            dest = f"{BUCKET}/{rel}"
            if object_exists(dest):
                have = sha256(download(f"https://{MIRROR_HOST}/{rel}"))
                if have != want:
                    print(f"  ERROR: {dest} exists but {have} != {want}")
                    rc = 1
                else:
                    print("    already mirrored, verified.")
                continue
            with tempfile.NamedTemporaryFile(suffix=".zip") as tmp:
                tmp.write(download(url))
                tmp.flush()
                object_upload(tmp.name, dest)
            print(f"    uploaded to {dest}")

    if rc == 0 and not args.upload:
        print("\nAll match the manifest. Re-run with --upload to mirror.")
    return rc


if __name__ == "__main__":
    sys.exit(main())
