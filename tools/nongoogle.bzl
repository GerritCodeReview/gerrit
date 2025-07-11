"""
Dependencies that are exempted from requiring a Library-Compliance approval
from a Googler.
"""

load("@bazel_tools//tools/build_defs/repo:http.bzl", "http_archive")
load("@bazel_tools//tools/build_defs/repo:utils.bzl", "maybe")
load("//tools/bzl:maven_jar.bzl", "maven_jar")

AUTO_COMMON_VERSION = "1.2.2"

AUTO_FACTORY_VERSION = "1.0.1"

AUTO_VALUE_VERSION = "1.11.0"

GUAVA_VERSION = "33.4.0-jre"

GUAVA_BIN_SHA1 = "03fcc0a259f724c7de54a6a55ea7e26d3d5c0cac"

GUAVA_TESTLIB_BIN_SHA1 = "e849ea71846b5ca96387d543c7ac862f18fe2513"

GUAVA_DOC_URL = "https://google.github.io/guava/releases/" + GUAVA_VERSION + "/api/docs/"

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
            "name": "bazel_features",
            "strip_prefix": "bazel_features-1.11.0",
            "urls": [
                "https://github.com/bazel-contrib/bazel_features/releases/download/v1.11.0/bazel_features-v1.11.0.tar.gz",
            ],
            "sha256": "2cd9e57d4c38675d321731d65c15258f3a66438ad531ae09cb8bb14217dc8572",
        },
        {
            "name": "rules_java",
            "urls": [
                "https://github.com/bazelbuild/rules_java/releases/download/7.6.1/rules_java-7.6.1.tar.gz",
            ],
            "sha256": "f8ae9ed3887df02f40de9f4f7ac3873e6dd7a471f9cddf63952538b94b59aeb3",
        },
        {
            "name": "rules_proto",
            "strip_prefix": "rules_proto-6.0.0",
            "urls": [
                "https://github.com/bazelbuild/rules_proto/releases/download/6.0.0/rules_proto-6.0.0.tar.gz",
            ],
            "sha256": "303e86e722a520f6f326a50b41cfc16b98fe6d1955ce46642a5b7a67c11c0f5d",
        },
        {
            "name": "toolchains_protoc",
            "strip_prefix": "toolchains_protoc-0.3.0",
            "urls": [
                "https://github.com/aspect-build/toolchains_protoc/releases/download/v0.3.0/toolchains_protoc-v0.3.0.tar.gz",
            ],
            "sha256": "117af61ee2f1b9b014dcac7c9146f374875551abb8a30e51d1b3c5946d25b142",
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

    

