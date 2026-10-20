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

"""Generate a CycloneDX 1.7 SBOM from a rules_jvm_external lockfile.

Reads the pinned maven_install.json lockfile (external_deps.lock.json,
format 3) and emits CycloneDX 1.7. Identities, SHA-256 hashes and the
dependency graph come from the lockfile, so no scan or network is needed
and the output is byte-reproducible.

Scope: third-party Maven only. First-party and source-built libraries
(JGit) and license enrichment come in later phases.
"""

import argparse
import hashlib
import json
import os
import sys
import uuid
from datetime import datetime, timezone

GENERATOR_NAME = "gerrit-sbom-from-lockfile"
GENERATOR_VERSION = "0.1.0"

# Fixed namespace so the serialNumber is deterministic, not random.
_SERIAL_NS = uuid.UUID("6ba7b811-9dad-11d1-80b4-00c04fd430c8")

# Classifier preference for the component hash: the shipped jar, not -sources.
_HASH_PREFERENCE = ("jar", "classes", "lib", "native")


def _maven_purl(group, artifact, version):
    return "pkg:maven/%s/%s@%s" % (group, artifact, version)


def _split_coordinate(key):
    # Artifact keys are "group:artifact", never with a classifier.
    group, artifact = key.split(":", 1)
    return group, artifact


def _repo_url_for(coordinate, repositories):
    for url, members in repositories.items():
        if coordinate in members:
            return url
    return None


def _component(key, entry, repositories):
    group, artifact = _split_coordinate(key)
    version = entry["version"]
    purl = _maven_purl(group, artifact, version)
    shasums = entry.get("shasums", {})

    comp = {
        "type": "library",
        "bom-ref": purl,
        "group": group,
        "name": artifact,
        "version": version,
        "purl": purl,
    }

    classifier = next((c for c in _HASH_PREFERENCE if c in shasums), None)
    if classifier:
        comp["hashes"] = [{"alg": "SHA-256", "content": shasums[classifier]}]

    repo = _repo_url_for(key, repositories)
    if repo:
        comp["externalReferences"] = [{
            "type": "distribution",
            "url": "%s%s/%s/%s/%s-%s.jar" % (
                repo, group.replace(".", "/"), artifact, version, artifact,
                version),
        }]

    properties = []
    if classifier and classifier != "jar":
        # Flag a non-default classifier so the hash is not read as the jar.
        properties.append({"name": "gerrit:sbom:hashClassifier",
                           "value": classifier})
    if "jar" not in shasums:
        properties.append({"name": "gerrit:sbom:note",
                           "value": "no jar classifier pinned in lockfile"})
    if properties:
        comp["properties"] = properties
    return comp


def _dependency_graph(artifacts, dependencies):
    known = {k: _maven_purl(*_split_coordinate(k), artifacts[k]["version"])
             for k in artifacts}
    edges = []
    for key in sorted(artifacts):
        depends_on = sorted(
            known[d] for d in dependencies.get(key, []) if d in known)
        edges.append({"ref": known[key], "dependsOn": depends_on})
    return edges


def _timestamp(source_date_epoch):
    if source_date_epoch is None:
        source_date_epoch = os.environ.get("SOURCE_DATE_EPOCH")
    # Default to epoch 0 for deterministic output. Release builds pass the
    # commit date.
    epoch = int(source_date_epoch) if source_date_epoch else 0
    return datetime.fromtimestamp(epoch, tz=timezone.utc).strftime(
        "%Y-%m-%dT%H:%M:%SZ")


def build_sbom(lockfile, product_name, product_version, source_date_epoch):
    with open(lockfile, encoding="utf-8") as fh:
        data = json.load(fh)
    # Lockfile "version" is the string "3".
    if str(data.get("version")) != "3":
        sys.stderr.write(
            "warning: lockfile format version %r, expected 3\n"
            % data.get("version"))
    artifacts = data["artifacts"]
    repositories = data.get("repositories", {})

    components = [_component(k, artifacts[k], repositories)
                  for k in sorted(artifacts)]

    # __RESOLVED_ARTIFACTS_HASH is a per-artifact map, so hash it to a stable
    # digest for the serial.
    resolved_digest = hashlib.sha256(
        json.dumps(data.get("__RESOLVED_ARTIFACTS_HASH", {}),
                   sort_keys=True).encode("utf-8")).hexdigest()
    serial = uuid.uuid5(_SERIAL_NS,
                        "%s:%s" % (product_version, resolved_digest))

    root_purl = _maven_purl("com.google.gerrit", product_name, product_version)
    return {
        "bomFormat": "CycloneDX",
        "specVersion": "1.7",
        "serialNumber": "urn:uuid:%s" % serial,
        "version": 1,
        "metadata": {
            "timestamp": _timestamp(source_date_epoch),
            "tools": {"components": [{
                "type": "application",
                "name": GENERATOR_NAME,
                "version": GENERATOR_VERSION,
            }]},
            "component": {
                "type": "application",
                "bom-ref": root_purl,
                "group": "com.google.gerrit",
                "name": product_name,
                "version": product_version,
                "purl": root_purl,
            },
            "properties": [
                {"name": "gerrit:sbom:scope",
                 "value": "resolved-maven-dependencies"},
                {"name": "gerrit:sbom:source", "value": os.path.basename(
                    lockfile)},
                {"name": "gerrit:sbom:lockfileFormat", "value": "3"},
                {"name": "gerrit:sbom:resolvedArtifactsHash",
                 "value": resolved_digest},
                {"name": "gerrit:sbom:completeness",
                 "value": ("third-party Maven only. First-party and "
                           "source-built components (e.g. JGit) and licenses "
                           "pending later phases")},
            ],
        },
        "components": components,
        "dependencies": _dependency_graph(
            artifacts, data.get("dependencies", {})),
    }


def main(argv):
    p = argparse.ArgumentParser(description=__doc__)
    p.add_argument("--lockfile", required=True,
                   help="path to maven_install.json (external_deps.lock.json)")
    p.add_argument("--output", required=True, help="CycloneDX JSON output path")
    p.add_argument("--product-name", default="gerrit-war")
    p.add_argument("--product-version", required=True,
                   help="Gerrit release version for the root component")
    p.add_argument("--source-date-epoch", default=None,
                   help="Unix epoch for the SBOM timestamp (defaults to "
                        "$SOURCE_DATE_EPOCH, then 0)")
    args = p.parse_args(argv)

    sbom = build_sbom(args.lockfile, args.product_name, args.product_version,
                      args.source_date_epoch)
    # Deterministic output: sorted keys and a trailing newline.
    with open(args.output, "w", encoding="utf-8") as fh:
        json.dump(sbom, fh, indent=2, sort_keys=True)
        fh.write("\n")
    return 0


if __name__ == "__main__":
    sys.exit(main(sys.argv[1:]))
