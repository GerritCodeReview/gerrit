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

"""Manage the gerrit-maven mirror of RJE-locked Maven Central artifacts.

Mirrors the Maven Central artifacts pinned in Gerrit's rules_jvm_external
lock (`external_deps.lock.json`) to gs://gerrit-maven. There is no separate
sha256 manifest -- the lock is the content pin. Like the other mirror
scripts, it checks by default and writes only with --upload.

  default: report which locked artifacts are absent from gerrit-maven.
           Parallel HEAD requests, never downloads, does not touch Maven
           Central. Exit 0 = all mirrored, 1 = missing (404), 2 =
           inconclusive (lock unreadable, or a probe hit a network/mirror
           error such as timeout, 5xx or 429).

  --upload: mirror the missing artifacts' Maven Central directories
           verbatim into gs://gerrit-maven -- jar/sources/javadoc/pom and
           any classifier artifacts, each with its .asc/.md5/.sha1/.sha256/
           .sha512 sidecars -- matching the file layout of the existing
           mirrored artifacts. Every lock-pinned file is verified against
           the lock's sha256 before its directory is uploaded. A no-op when
           the mirror is already complete. Only a 404 counts as missing;
           any other status aborts.

  bazel run //tools/bzl:maven-mirror              # check (default)
  bazel run //tools/bzl:maven-mirror -- --upload  # populate
  tools/bzl/manage_maven_central_mirror.py [--upload] [options]
"""

from __future__ import annotations

import argparse
import concurrent.futures
import hashlib
import json
import os
import re
import subprocess
import sys
import tempfile
import urllib.error
import urllib.request
from dataclasses import dataclass
from pathlib import Path

DEFAULT_MIRROR = "https://gerrit-maven.storage.googleapis.com"
CENTRAL = "https://repo1.maven.org/maven2"
BUCKET = "gs://gerrit-maven"
LOCK_FILE_NAME = "external_deps.lock.json"


@dataclass(frozen=True)
class Probe:
    coordinate: str
    classifier: str
    version: str
    path: str
    sha256: str
    status: int | str = 0


def default_lock_file() -> str:
    workspace = os.environ.get("BUILD_WORKSPACE_DIRECTORY")
    if workspace:
        return os.path.join(workspace, LOCK_FILE_NAME)
    root = subprocess.check_output(
        ["git", "rev-parse", "--show-toplevel"], text=True
    ).strip()
    return os.path.join(root, LOCK_FILE_NAME)


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument(
        "--upload", action="store_true",
        help="mirror missing artifacts (default: check coverage only)",
    )
    parser.add_argument("--lock-file", default=default_lock_file(),
                        help=f"RJE v3 lock file. Default: {LOCK_FILE_NAME}")
    parser.add_argument("--mirror", default=DEFAULT_MIRROR,
                        help="Maven mirror base URL")
    parser.add_argument("--workers", type=int, default=32,
                        help="parallel HEAD checks (check). Default: 32")
    parser.add_argument("--timeout", type=float, default=10.0,
                        help="per-request timeout in seconds. Default: 10")
    parser.add_argument("--include-ok", action="store_true",
                        help="check: also print mirrored artifacts")
    return parser.parse_args()


def maven_path(coordinate: str, classifier: str, version: str) -> str:
    parts = coordinate.split(":")
    if len(parts) == 2:
        group, artifact, extension = parts[0], parts[1], "jar"
    elif len(parts) == 3:
        group, artifact, extension = parts
    else:
        raise ValueError(f"Unsupported RJE artifact key: {coordinate}")
    suffix = "" if classifier == "jar" else f"-{classifier}"
    filename = f"{artifact}-{version}{suffix}.{extension}"
    return "/".join(group.split(".") + [artifact, version, filename])


def artifact_dir(coordinate: str, version: str) -> str:
    parts = coordinate.split(":")
    return "/".join(parts[0].split(".") + [parts[1], version])


def locked_probes(lock_file: Path) -> list[Probe]:
    """Every lock-pinned artifact/classifier, parsed once."""
    data = json.loads(lock_file.read_text())
    probes = []
    for coordinate, artifact in sorted(data["artifacts"].items()):
        version = artifact["version"]
        for classifier, sha in sorted(artifact["shasums"].items()):
            probes.append(Probe(
                coordinate, classifier, version,
                maven_path(coordinate, classifier, version), sha,
            ))
    return probes


def head(url: str, timeout: float) -> int | str:
    request = urllib.request.Request(url, method="HEAD")
    try:
        with urllib.request.urlopen(request, timeout=timeout) as response:
            return response.status
    except urllib.error.HTTPError as err:
        return err.code
    except urllib.error.URLError as err:
        return str(err.reason)


def classify(status: int | str) -> str:
    """Bucket a probe result: only a definitive 404 is a coverage miss."""
    if status == 200:
        return "ok"
    if status == 404:
        return "missing"
    return "error"


def probe_mirror(probes, mirror, workers, timeout) -> list[Probe]:
    mirror = mirror.rstrip("/")

    def check(p: Probe) -> Probe:
        status = head(f"{mirror}/{p.path}", timeout)
        return Probe(p.coordinate, p.classifier, p.version, p.path,
                     p.sha256, status)

    with concurrent.futures.ThreadPoolExecutor(max_workers=workers) as pool:
        return list(pool.map(check, probes))


def get(url: str, timeout: float = 60) -> bytes:
    with urllib.request.urlopen(url, timeout=timeout) as resp:
        return resp.read()


def sha256(data: bytes) -> str:
    return hashlib.sha256(data).hexdigest()


def central_files(rel: str) -> list[str]:
    """Files served by Central's directory index for an artifact version."""
    html = get(f"{CENTRAL}/{rel}/").decode("utf-8", "replace")
    names = re.findall(r'href="([^"?]+)"', html)
    return sorted({
        n for n in names
        if not n.endswith("/") and ".." not in n and "/" not in n
    })


def gcs_upload(local: str, obj: str) -> None:
    # --no-clobber: never overwrite an existing mirrored object.
    subprocess.check_call(
        ["gcloud", "storage", "cp", "--no-clobber", local, obj]
    )


def run_check(results: list[Probe], include_ok: bool, mirror: str) -> int:
    mirror = mirror.rstrip("/")
    results.sort(key=lambda r: (str(r.status), r.coordinate, r.classifier))
    missing = [r for r in results if classify(r.status) == "missing"]
    errors = [r for r in results if classify(r.status) == "error"]
    mirrored = len(results) - len(missing) - len(errors)

    printed = results if include_ok else missing + errors
    for r in printed:
        print(f"{r.status}\t{r.coordinate}:{r.classifier}"
              f"\t{mirror}/{r.path}")
    print(f"checked={len(results)} mirrored={mirrored} "
          f"missing={len(missing)} errors={len(errors)}", file=sys.stderr)
    # Errors take precedence: an inconclusive run must not read as a miss.
    if errors:
        return 2
    if missing:
        return 1
    return 0


def run_upload(results: list[Probe]) -> int:
    errors = [r for r in results if classify(r.status) == "error"]
    if errors:
        for r in errors:
            print(f"  ABORT: {r.status} for {r.coordinate}:{r.classifier}")
        return 2

    missing_dirs = sorted({
        artifact_dir(r.coordinate, r.version)
        for r in results if classify(r.status) == "missing"
    })
    if not missing_dirs:
        print("All locked artifacts are mirrored in gerrit-maven.")
        return 0

    by_dir: dict[str, list[Probe]] = {}
    for r in results:
        by_dir.setdefault(artifact_dir(r.coordinate, r.version), []).append(r)

    print(f"Mirroring {len(missing_dirs)} artifact(s):\n")
    for rel in missing_dirs:
        # Verify every lock-pinned file against Central before uploading.
        for p in by_dir[rel]:
            got = sha256(get(f"{CENTRAL}/{p.path}"))
            if got != p.sha256:
                sys.exit(f"  MISMATCH {p.path}: lock {p.sha256} != {got}")
        files = central_files(rel)
        print(f"  {rel}: {len(files)} files "
              f"(verified {len(by_dir[rel])} pinned)")
        for name in files:
            with tempfile.NamedTemporaryFile() as tmp:
                tmp.write(get(f"{CENTRAL}/{rel}/{name}"))
                tmp.flush()
                gcs_upload(tmp.name, f"{BUCKET}/{rel}/{name}")
        print(f"    uploaded {len(files)} files to {BUCKET}/{rel}/")
    return 0


def main() -> int:
    args = parse_args()
    lock_file = Path(args.lock_file)
    if not lock_file.is_file():
        print(f"error: lock file not found: {lock_file}", file=sys.stderr)
        return 2
    try:
        probes = locked_probes(lock_file)
    except (json.JSONDecodeError, KeyError, ValueError, OSError) as err:
        print(f"error: cannot read lock file: {err}", file=sys.stderr)
        return 2

    results = probe_mirror(probes, args.mirror, args.workers, args.timeout)
    if args.upload:
        return run_upload(results)
    return run_check(results, args.include_ok, args.mirror)


if __name__ == "__main__":
    sys.exit(main())
