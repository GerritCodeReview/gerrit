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

"""Mirror bazel_lib toolchain binaries to gs://gerrit-maven.

bazel_lib downloads its toolchain binaries from github release assets,
each via a single URL with no mirror fallback, so a cold build hits
github. Covered here: coreutils, zstd, copy_directory, copy_to_directory,
expand_template and the bats libraries (from the bazel_lib module); yq, jq
and tar (from the split-out yq.bzl / jq.bzl / tar.bzl modules, which pin
their own coords -- tar is hermeticbuild/bsdtar-prebuilt, not aspect-build).

Every asset is uploaded, path-for-path, under gs://gerrit-maven/<repo>/;
the gerrit-maven-mirror downloader config rewrites the github URLs there,
keyed by repo (org-agnostic). Only the platforms Gerrit builds on
(darwin/linux, amd64/arm64) are mirrored.

tools/bazel_lib_toolchains.sha256 is the upload content pin; Bazel enforces
bazel_lib's own integrity on fetch, so the mirror is trustless.

Reads the URLs from the checked-out modules in the bazel output_base, so
run a build first. Upstream mirror-fallback request:
bazel-contrib/bazel-lib#1295.

  tools/bzl/mirror_bazel_lib_toolchains.py                 # verify (default)
  tools/bzl/mirror_bazel_lib_toolchains.py --manifest-update
  tools/bzl/mirror_bazel_lib_toolchains.py --upload
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
MANIFEST = "tools/bazel_lib_toolchains.sha256"
# Arch/OS tokens Gerrit does not build on; their assets are not mirrored.
SKIP = ("windows", "s390x", "riscv64", "ppc64le", "ppc64", "freebsd",
        "i386", "i686", "mips")
MANIFEST_HEADER = """\
# SHA-256 of the bazel_lib toolchain binaries mirrored to gs://gerrit-maven.
# Format: <sha256>  <repo>/<github-path-after-org>.
# Regenerate: tools/bzl/mirror_bazel_lib_toolchains.py --manifest-update.
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


def output_base():
    return subprocess.check_output(
        ["bazelisk", "info", "output_base"], text=True,
        stderr=subprocess.DEVNULL,
    ).strip()


def module_root(ob, name):
    """Return the external/<name>+ module dir in the output_base."""
    root = module_root_opt(ob, name)
    if root is None:
        sys.exit(f"{name} not found in output_base; run a build first.")
    return root


def module_root_opt(ob, name):
    hits = glob.glob(f"{ob}/external/{name}+")
    return Path(hits[0]) if hits else None


def read(path):
    return Path(path).read_text(encoding="utf-8")


def default_version(text, var):
    return re.search(rf'{var}\s*=\s*"([^"]+)"', text).group(1)


def version_block(text, dict_name, version):
    """Return the text of dict_name[version] (a 4-space-indented entry)."""
    start = text.index(dict_name)
    m = re.search(
        rf'"{re.escape(version)}":\s*\{{(.*?)\n    \}}', text[start:], re.S
    )
    return m.group(1) if m else ""


def literal_github_urls(text):
    return re.findall(r'"(https://github\.com/[^"]+)"', text)


# --- per-tool URL producers -------------------------------------------

def coreutils_urls(abl):
    text = read(abl / "lib/private/coreutils_toolchain.bzl")
    ver = re.findall(r'\n\s+"(\d+\.\d+\.\d+)":', text)[0]  # first = default
    block = version_block(text, "COREUTILS_VERSIONS", ver)
    urls = []
    for plat in re.findall(r"\{[^{}]*\}", block):
        fn = re.search(r'"filename":\s*"([^"]+)"', plat)
        if not fn:
            continue
        ov = re.search(r'"version_override":\s*"([^"]+)"', plat)
        tag = ov.group(1) if ov else ver
        urls.append("https://github.com/uutils/coreutils/releases/"
                    f"download/{tag}/{fn.group(1)}")
    return urls


def yq_urls(ob):
    text = read(module_root(ob, "yq.bzl") / "yq/toolchain/versions.bzl")
    ver = default_version(text, "DEFAULT_YQ_VERSION")
    block = version_block(text, "YQ_VERSIONS", ver)
    return ["https://github.com/mikefarah/yq/releases/download/"
            f"v{ver}/yq_{p}"
            for p in re.findall(r'"([\w-]+)":\s*"sha', block)]


def jq_urls(ob):
    root = module_root(ob, "jq.bzl")
    ver = default_version(
        read(root / "jq/toolchain/toolchain.bzl"), "DEFAULT_JQ_VERSION")
    block = version_block(
        read(root / "jq/toolchain/versions.bzl"), "JQ_VERSIONS", ver)
    return ["https://github.com/stedolan/jq/releases/download/"
            f"jq-{ver}/jq-{p}"
            for p in re.findall(r'"([\w-]+)":\s*"sha', block)]


def tar_urls(ob):
    # tar.bzl module pins hermeticbuild/bsdtar-prebuilt, not aspect-build.
    text = read(module_root(ob, "tar.bzl") / "tar/toolchain/versions.bzl")
    return literal_github_urls(text)


def zstd_urls(abl):
    return literal_github_urls(read(abl / "lib/private/zstd_toolchain.bzl"))


def released_urls(abl, tool):
    """copy_*/expand_template: filenames in RELEASED_BINARY_INTEGRITY, at
    the module release tag (tools/version.bzl is a 0.0.0 placeholder)."""
    ver = re.search(
        r'module\([^)]*?version\s*=\s*"([^"]+)"',
        read(abl / "MODULE.bazel"), re.S,
    ).group(1)
    integ = read(abl / "tools/integrity.bzl")
    return ["https://github.com/bazel-contrib/bazel-lib/releases/"
            f"download/v{ver}/{fn}"
            for fn in re.findall(rf'"({tool}-[\w]+)":', integ)]


def bats_urls(abl):
    """bats libraries: platform-independent source archives, fetched at
    analysis to read the registered toolchain."""
    repos = read(abl / "lib/repositories.bzl")
    bats = read(abl / "lib/private/bats_toolchain.bzl")

    def first_key(dict_name):
        return re.search(rf'{dict_name}\s*=\s*\{{\s*"([^"]+)"', bats).group(1)

    versions = {
        "bats-core": default_version(repos, "DEFAULT_BATS_CORE_VERSION"),
        "bats-support": first_key("BATS_SUPPORT_VERSIONS"),
        "bats-assert": first_key("BATS_ASSERT_VERSIONS"),
        "bats-file": first_key("BATS_FILE_VERSIONS"),
    }
    return [f"https://github.com/bats-core/{repo}/archive/{ver}.tar.gz"
            for repo, ver in versions.items()]


def relpath_of(url):
    """Mirror path = github path after the org: <repo>/<rest>."""
    return url.split("github.com/", 1)[1].split("/", 1)[1]


def skip(url):
    # win(dows|\d) catches jq's "win64" alias without matching "darwin".
    return (url.endswith((".exe", ".zip"))
            or any(t in url for t in SKIP)
            or bool(re.search(r"win(dows|\d)", url)))


def all_entries(ob):
    # Both aspect_bazel_lib 2.x and the renamed bazel_lib 3.x register the
    # same toolchains; which one wins flips with cache state, so mirror both
    # modules' coords (identical ones dedup by path).
    urls = []
    for name in ("aspect_bazel_lib", "bazel_lib"):
        root = module_root_opt(ob, name)
        if root is None:
            continue
        urls += coreutils_urls(root) + zstd_urls(root) + bats_urls(root)
        for tool in ("copy_directory", "copy_to_directory",
                     "expand_template"):
            urls += released_urls(root, tool)
        embedded_tar = root / "lib/private/tar_toolchain.bzl"
        if embedded_tar.exists():
            urls += literal_github_urls(read(embedded_tar))
    urls += tar_urls(ob) + yq_urls(ob) + jq_urls(ob)
    seen, out = set(), []
    for url in urls:
        if skip(url):
            continue
        rel = relpath_of(url)
        if rel not in seen:
            seen.add(rel)
            out.append((url, rel))
    return sorted(out, key=lambda e: e[1])


# --- download / verify / upload ---------------------------------------

def download(url):
    with urllib.request.urlopen(url, timeout=60) as resp:
        return resp.read()


def sha256(data):
    return hashlib.sha256(data).hexdigest()


def load_manifest(path):
    expected = {}
    for line in read(path).splitlines():
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


def write_manifest(root, entries):
    lines = [f"{sha256(download(url))}  {rel}" for url, rel in entries]
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
                   help="regenerate the manifest from bazel_lib")
    g.add_argument("--upload", action="store_true",
                   help="upload to the bucket (needs write access)")
    args = ap.parse_args()

    root = repo_root()
    entries = all_entries(output_base())
    if args.manifest_update:
        write_manifest(root, entries)
        return 0

    expected = load_manifest(root / MANIFEST)
    print(f"Verifying {len(entries)} bazel_lib binary(ies) against "
          f"{MANIFEST}:\n")
    rc = 0
    for url, rel in entries:
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
            with tempfile.NamedTemporaryFile() as tmp:
                tmp.write(download(url))
                tmp.flush()
                object_upload(tmp.name, dest)
            print(f"    uploaded to {dest}")

    if rc == 0 and not args.upload:
        print("\nAll match the manifest. Re-run with --upload to mirror.")
    return rc


if __name__ == "__main__":
    sys.exit(main())
