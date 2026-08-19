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

"""Check whether Gerrit's RJE Maven lock is mirrored in gerrit-maven.

The script reads the rules_jvm_external v3 lock file used by Gerrit
(`external_deps.lock.json` by default), derives the Maven repository path for
each locked artifact/classifier, and checks whether that file exists under
Gerrit's Maven mirror.
"""

from __future__ import annotations

import argparse
import concurrent.futures
import json
import os
import sys
import urllib.error
import urllib.request
from dataclasses import dataclass
from pathlib import Path


DEFAULT_MIRROR = "https://gerrit-maven.storage.googleapis.com"

# Where contributors request that a Maven artifact be added to the mirror.
# Documented in Documentation/dev-processes.txt, "Upgrading Libraries".
MIRROR_REQUEST_URL = (
    "https://issues.gerritcodereview.com/issues/new?component=1371020&template=1834212"
)

LOCK_FILE_NAME = "external_deps.lock.json"


def default_lock_file() -> str:
    """Locate the lock file relative to the workspace root under `bazel run`.

    `bazel run` sets BUILD_WORKSPACE_DIRECTORY to the workspace root, but runs
    the binary from its runfiles tree, so the plain relative name would not
    resolve. Fall back to the relative name for direct CLI use from the repo
    root (env unset).
    """
    workspace = os.environ.get("BUILD_WORKSPACE_DIRECTORY")
    return os.path.join(workspace, LOCK_FILE_NAME) if workspace else LOCK_FILE_NAME


@dataclass(frozen=True)
class ArtifactCheck:
    coordinate: str
    classifier: str
    url: str
    status: int | str


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(
        description="Check Gerrit's Maven mirror coverage for an RJE lock file."
    )
    parser.add_argument(
        "--lock-file",
        default=default_lock_file(),
        help=f"RJE v3 lock file to scan. Default: {LOCK_FILE_NAME}",
    )
    parser.add_argument(
        "--mirror",
        default=DEFAULT_MIRROR,
        help=f"Maven mirror base URL. Default: {DEFAULT_MIRROR}",
    )
    parser.add_argument(
        "--workers",
        type=int,
        default=32,
        help="Number of parallel HTTP checks. Default: 32",
    )
    parser.add_argument(
        "--timeout",
        type=float,
        default=10.0,
        help="Per-request timeout in seconds. Default: 10",
    )
    parser.add_argument(
        "--include-ok",
        action="store_true",
        help="Print mirrored artifacts too, not only missing artifacts.",
    )
    return parser.parse_args()


def maven_path(coordinate: str, classifier: str, version: str) -> str:
    parts = coordinate.split(":")
    if len(parts) == 2:
        group, artifact = parts
        extension = "jar"
    elif len(parts) == 3:
        group, artifact, extension = parts
    else:
        raise ValueError(f"Unsupported RJE artifact key: {coordinate}")

    suffix = "" if classifier == "jar" else f"-{classifier}"
    filename = f"{artifact}-{version}{suffix}.{extension}"
    return "/".join(group.split(".") + [artifact, version, filename])


def locked_artifact_urls(lock_file: Path, mirror: str) -> list[tuple[str, str, str]]:
    data = json.loads(lock_file.read_text())
    mirror = mirror.rstrip("/")
    urls = []

    for coordinate, artifact in sorted(data["artifacts"].items()):
        version = artifact["version"]
        for classifier in sorted(artifact["shasums"]):
            path = maven_path(coordinate, classifier, version)
            urls.append((coordinate, classifier, f"{mirror}/{path}"))

    return urls


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
    """Bucket a probe result into ok / missing / error.

    Only a definitive HTTP 404 counts as a coverage miss. A transport
    failure (a str reason from URLError) or any other HTTP status (403,
    429, 5xx, ...) is an infrastructure/tool error: the probe was
    inconclusive, so the run must not be reported as a coverage miss.
    """
    if status == 200:
        return "ok"
    if status == 404:
        return "missing"
    return "error"


def check_one(item: tuple[str, str, str], timeout: float) -> ArtifactCheck:
    coordinate, classifier, url = item
    return ArtifactCheck(coordinate, classifier, url, head(url, timeout))


def main() -> int:
    args = parse_args()
    lock_file = Path(args.lock_file)
    if not lock_file.is_file():
        print(f"error: lock file not found: {lock_file}", file=sys.stderr)
        return 2

    try:
        items = locked_artifact_urls(lock_file, args.mirror)
    except json.JSONDecodeError as err:
        print(f"error: invalid lock file JSON: {err}", file=sys.stderr)
        return 2
    except (KeyError, ValueError) as err:
        print(f"error: unsupported lock file shape: {err}", file=sys.stderr)
        return 2
    except OSError as err:
        print(f"error: cannot read lock file: {err}", file=sys.stderr)
        return 2

    with concurrent.futures.ThreadPoolExecutor(max_workers=args.workers) as pool:
        futures = [pool.submit(check_one, item, args.timeout) for item in items]
        results = [future.result() for future in concurrent.futures.as_completed(futures)]

    results.sort(key=lambda r: (str(r.status), r.coordinate, r.classifier))
    missing = [r for r in results if classify(r.status) == "missing"]
    errors = [r for r in results if classify(r.status) == "error"]
    mirrored = len(results) - len(missing) - len(errors)

    printed = results if args.include_ok else [r for r in results if classify(r.status) != "ok"]
    for result in printed:
        print(f"{result.status}\t{result.coordinate}:{result.classifier}\t{result.url}")

    # stdout is block-buffered when piped; flush the rows so they precede the
    # stderr summary/hint and "listed above" stays correct when streams merge.
    sys.stdout.flush()

    print(
        f"checked={len(results)} mirrored={mirrored} "
        f"missing={len(missing)} errors={len(errors)}",
        file=sys.stderr,
    )
    # Errors take precedence over misses: a run with any inconclusive probe
    # cannot reliably report coverage, so signal infra (2) rather than a miss (1).
    if errors:
        return 2
    if missing:
        print(
            f"\n{len(missing)} artifact(s) are not on the gerrit-maven mirror. "
            "Contributors cannot upload directly; request that the coordinates "
            'listed above be added to the mirror (see Documentation/'
            'dev-processes.txt, "Upgrading Libraries"):\n'
            f"  {MIRROR_REQUEST_URL}",
            file=sys.stderr,
        )
        return 1
    return 0


if __name__ == "__main__":
    sys.exit(main())
