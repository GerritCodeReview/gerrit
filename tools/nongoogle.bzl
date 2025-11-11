"""
Dependencies that are exempted from requiring a Library-Compliance approval
from a Googler.
"""

load("@bazel_tools//tools/build_defs/repo:http.bzl", "http_archive")
load("@bazel_tools//tools/build_defs/repo:utils.bzl", "maybe")
load("@com_googlesource_gerrit_bazlets//maven:repositories.bzl", "maven_jar")


def archive_dependencies():
    return [
        {
            "name": "platforms",
            "urls": [
                "https://mirror.bazel.build/github.com/bazelbuild/platforms/releases/download/0.0.10/platforms-0.0.10.tar.gz",
                "https://github.com/bazelbuild/platforms/releases/download/0.0.10/platforms-0.0.10.tar.gz",
            ],
            "sha256": "218efe8ee736d26a3572663b374a253c012b716d8af0c07e842e82f238a0a7ee",
        },
        {
            "name": "ubuntu2204_jdk21",
            "strip_prefix": "rbe_autoconfig-5.2.0",
            "urls": [
                "https://gerrit-bazel.storage.googleapis.com/rbe_autoconfig/v5.2.0.tar.gz",
                "https://github.com/davido/rbe_autoconfig/releases/download/v5.2.0/v5.2.0.tar.gz",
            ],
            "sha256": "294e5d4adea036da243f3c007b098d97229cc02a14bf10d256bd82d5b62a56d9",
        },
    ]

def declare_nongoogle_deps():
    """loads dependencies that are not used at Google.

    Changes to versions are exempt from library compliance review. New
    dependencies must pass through library compliance review. This is
    enforced by //lib:nongoogle_test.
    """

    for dependency in archive_dependencies():
        params = {}
        params.update(**dependency)
        maybe(http_archive, params.pop("name"), **params)
